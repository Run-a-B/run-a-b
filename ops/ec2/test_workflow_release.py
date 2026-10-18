"""Run workflow shell boundaries locally; Ruby's standard YAML library parses YAML."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def workflow(name):
    result = subprocess.run(
        ["ruby", "-rjson", "-ryaml", "-e", "puts JSON.generate(YAML.load_file(ARGV[0]))",
         str(ROOT / ".github/workflows" / name)], capture_output=True, text=True, check=True)
    return json.loads(result.stdout)


class ReleaseWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.bin = self.base / "bin"
        self.bin.mkdir()
        self.trace = self.base / "trace"
        self.env = dict(os.environ, HOME=str(self.base), PATH=str(self.bin) + os.pathsep + os.environ["PATH"],
                        GITHUB_EVENT_NAME="push", GITHUB_REF="refs/heads/main", GITHUB_SHA="a" * 40,
                        GITHUB_RUN_ID="123", GITHUB_RUN_ATTEMPT="1", SSHPASS="never-log-password",
                        EC2_KNOWN_HOSTS="43.201.103.134 ssh-ed25519 AAAA_TEST_PIN", TRACE=str(self.trace))

    def fake(self, name, body):
        path = self.bin / name
        path.write_text("#!/bin/bash\nset -euo pipefail\n" + body)
        path.chmod(0o700)

    def release_step(self):
        config = workflow("release.yml")
        return next(step["run"] for step in config["jobs"]["deploy"]["steps"] if step.get("id") == "activate")

    def run_release(self, **env):
        return subprocess.run(["bash", "-c", self.release_step()], env=dict(self.env, **env),
                              cwd=self.base, capture_output=True, text=True)

    def test_release_job_guards_only_allow_main_push_or_main_manual_run(self):
        config = workflow("release.yml")
        for job in config["jobs"].values():
            for event, ref, allowed in (("push", "refs/heads/main", True),
                                        ("workflow_dispatch", "refs/heads/main", True),
                                        ("workflow_dispatch", "refs/heads/feature", False),
                                        ("pull_request", "refs/pull/1/merge", False),
                                        ("pull_request_target", "refs/heads/main", False)):
                expression = job["if"].replace("github.ref", repr(ref)).replace("github.event_name", repr(event))
                expression = expression.replace("&&", "and").replace("||", "or")
                self.assertEqual(allowed, eval(expression, {"__builtins__": {}}, {}))

    def test_package_contains_both_builds_and_rejects_environment_files(self):
        config = workflow("release.yml")
        script = next(step["run"] for step in config["jobs"]["build"]["steps"] if step.get("id") == "package")
        for name, content in (("api/build/libs/api-0.0.1-SNAPSHOT.jar", "compiled-api"),
                              ("apps/run-a-b-fe/dist/index.html", "compiled-frontend"),
                              ("ops/ec2/ec2_ops.py", "reviewed-main-ops"),
                              ("ops/ec2/bootstrap.py", "reviewed-main-bootstrap"),
                              (".env", "never-bundle-this-secret")):
            path = self.base / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
        result = subprocess.run(["bash", "-c", script], cwd=self.base, capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("compiled-api", (self.base / "release-bundle/api.jar").read_text())
        self.assertEqual("compiled-frontend", (self.base / "release-bundle/dist/index.html").read_text())
        self.assertFalse((self.base / "release-bundle/.env").exists())
        # A public build accidentally containing environment files must fail packaging.
        import shutil
        shutil.rmtree(self.base / "release-bundle")
        (self.base / "apps/run-a-b-fe/dist/.env.local").write_text("do-not-publish")
        result = subprocess.run(["bash", "-c", script], cwd=self.base, capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)

    def test_pr_validation_workflows_cannot_connect_to_production(self):
        self.fake("sshpass", 'echo production-access >> "$TRACE"; exit 90\n')
        for name in ("deploy.yml", "deploy-frontend.yml"):
            config = workflow(name)
            for job in config["jobs"].values():
                for step in job["steps"]:
                    if "run" in step:
                        # Build tools are separate boundaries; only remote access is observed.
                        for tool in ("npm", "chmod", "ls", "python3"):
                            self.fake(tool, "exit 0\n")
                        (self.base / "gradlew").write_text("#!/bin/bash\nexit 0\n")
                        (self.base / "gradlew").chmod(0o700)
                        result = subprocess.run(["bash", "-c", step["run"]], cwd=self.base,
                                                env=dict(self.env, GITHUB_EVENT_NAME="pull_request"),
                                                capture_output=True, text=True)
                        self.assertEqual(0, result.returncode, result.stderr)
        self.assertFalse(self.trace.exists(), "PR validation attempted a production connection")

    def test_release_rejects_nonmain_and_pr_before_any_connection(self):
        self.fake("sshpass", 'echo connection >> "$TRACE"\n')
        for event, ref in (("pull_request", "refs/pull/1/merge"), ("workflow_dispatch", "refs/heads/feature"),
                           ("pull_request_target", "refs/heads/main")):
            result = self.run_release(GITHUB_EVENT_NAME=event, GITHUB_REF=ref)
            self.assertNotEqual(0, result.returncode)
        self.assertFalse(self.trace.exists())

    def test_missing_pinned_key_or_password_fails_before_connection(self):
        self.fake("sshpass", 'echo connection >> "$TRACE"\n')
        for missing in ("EC2_KNOWN_HOSTS", "SSHPASS"):
            result = self.run_release(**{missing: ""})
            self.assertNotEqual(0, result.returncode)
            self.assertNotIn("never-log-password", result.stdout + result.stderr)
        self.assertFalse(self.trace.exists())

    def remote_boundaries(self):
        self.fake("ssh-keygen", "exit 0\n")
        self.fake("sshpass", 'test "$1" = -e; shift; exec "$@"\n')
        self.fake("scp", 'echo upload >> "$TRACE"\n')
        self.fake("ssh", '''
if [[ "$*" == *"bash -s --"* ]]; then
  exec bash -s -- "${GITHUB_SHA}-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"
fi
''')
        self.fake("sudo", '''
case "$*" in
  *"bootstrap.py preflight"*) echo preflight >> "$TRACE"; exit "${FAIL_PREFLIGHT:-0}" ;;
  *"ec2_ops.py stage"*) echo stage >> "$TRACE" ;;
  *"ec2_ops.py activate"*) echo activate >> "$TRACE" ;;
  *) exit 91 ;;
esac
''')

    def test_failed_tls_env_or_schema_preflight_never_stages_or_activates(self):
        self.remote_boundaries()
        result = self.run_release(FAIL_PREFLIGHT="1")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(["upload", "preflight"], self.trace.read_text().splitlines())
        self.assertNotIn("never-log-password", result.stdout + result.stderr)

    def test_combined_release_stages_then_activates_only_after_preflight(self):
        self.remote_boundaries()
        result = self.run_release()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["upload", "preflight", "stage", "activate"], self.trace.read_text().splitlines())


if __name__ == "__main__":
    unittest.main()
