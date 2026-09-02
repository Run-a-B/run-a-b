# Local Development Setup Specification

## Goal

Make a fresh clone of Run-a-B reproducibly installable and runnable on macOS with the repository's existing Java 21, Spring Boot, React/Vite, and MySQL architecture.

## Success criteria

- A developer can create local configuration from a committed example without committing credentials.
- MySQL starts with the `runab_db` schema through Docker Compose on a port that does not collide with a typical local MySQL installation.
- One documented command installs dependencies, and one documented command starts the database, API, and frontend.
- The API starts even when optional Google, Gmail, and OpenAI credentials are absent; only their corresponding external features remain unavailable.
- Backend tests do not require a developer's MySQL server.
- Backend tests, frontend lint, and frontend production build pass.
- Frontend runtime dependencies have no known high or critical advisories after non-breaking updates.
- Local HTTP checks confirm the API on port 8082 and frontend on port 5173 are reachable without disturbing services already using ports 8080 and 8081.

## Constraints

- Keep production behavior and deployed defaults compatible with the existing repository.
- Do not commit real credentials.
- Use Java 21 through the Gradle toolchain, Node.js 22+, Docker Compose v2, and MySQL 8.4.
- Keep changes limited to local setup, test isolation, and pre-existing verification drift discovered during setup.
