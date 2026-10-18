'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { TextDecoder } = require('node:util');

const LIMIT = 2 * 1024 * 1024;
const PURPOSE = 'runab-server-preparation-20261019';
const SEED_HASH = '2012fb83362ba98f5d3d79dae608d0b108461a70dd2b16f8fa9659839e647266';
const KEYS = ['GOOGLE_CLIENT_ID', 'MAIL_USERNAME', 'MAIL_APP_PASSWORD',
  'OPENAI_API_KEY', 'OPENAI_MODEL', 'BIZINFO_API_KEY'];

function requireValid(condition) {
  if (!condition) throw new Error('rejected');
}

function exactObject(value, keys) {
  requireValid(value !== null && typeof value === 'object' && !Array.isArray(value));
  const actual = Object.keys(value);
  requireValid(actual.length === keys.length && keys.every(key => Object.hasOwn(value, key)));
}

function base64(value, bytes) {
  requireValid(typeof value === 'string' && value.length > 0 && value.length <= LIMIT);
  const decoded = Buffer.from(value, 'base64');
  requireValid(decoded.toString('base64') === value && decoded.length <= LIMIT);
  if (bytes !== undefined) requireValid(decoded.length === bytes);
  return decoded;
}

function readEnvelope(filename) {
  const descriptor = fs.openSync(filename, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
  try {
    const info = fs.fstatSync(descriptor);
    requireValid(info.isFile() && info.size > 0 && info.size <= LIMIT);
    const buffer = Buffer.alloc(LIMIT + 1);
    let length = 0;
    while (length < buffer.length) {
      const count = fs.readSync(descriptor, buffer, length, buffer.length - length, null);
      if (!count) break;
      length += count;
    }
    requireValid(length > 0 && length <= LIMIT);
    return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(buffer.subarray(0, length)));
  } finally {
    fs.closeSync(descriptor);
  }
}

function decrypt(envelope, key) {
  exactObject(envelope, ['version', 'algorithm', 'purpose', 'iv', 'tag', 'ciphertext']);
  requireValid(envelope.version === 1 && envelope.algorithm === 'aes-256-gcm' && envelope.purpose === PURPOSE);
  const iv = base64(envelope.iv, 12);
  const tag = base64(envelope.tag, 16);
  const ciphertext = base64(envelope.ciphertext);
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
  decipher.setAAD(Buffer.from(PURPOSE, 'utf8'));
  decipher.setAuthTag(tag);
  const plaintext = Buffer.concat([decipher.update(ciphertext), decipher.final()]);
  try {
    requireValid(plaintext.length <= LIMIT);
    return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(plaintext));
  } finally {
    plaintext.fill(0);
  }
}

function validate(payload) {
  exactObject(payload, ['integrations', 'policySeedBase64']);
  exactObject(payload.integrations, KEYS);
  for (const key of KEYS) {
    const value = payload.integrations[key];
    requireValid(typeof value === 'string' && value.trim().length > 0 && value.length <= 4096
      && !/[\u0000-\u001f\u007f\u2028\u2029]/u.test(value));
  }
  const seed = base64(payload.policySeedBase64);
  requireValid(crypto.createHash('sha256').update(seed).digest('hex') === SEED_HASH);
  return { integrations: Buffer.from(JSON.stringify(payload.integrations) + '\n'), seed };
}

function privateDirectory(directory) {
  const info = fs.lstatSync(directory);
  requireValid(info.isDirectory() && !info.isSymbolicLink() && info.uid === process.getuid()
    && (info.mode & 0o777) === 0o700);
}

function writeOutputs(directory, values) {
  privateDirectory(directory);
  const outputs = [['integrations.json', values.integrations], ['policy.sql.gz', values.seed]];
  for (const [name] of outputs) {
    try {
      fs.lstatSync(path.join(directory, name));
    } catch (error) {
      if (error.code === 'ENOENT') continue;
      throw error;
    }
    throw new Error('rejected');
  }
  const created = [];
  let complete = false;
  try {
    for (const [name] of outputs) {
      privateDirectory(directory);
      const filename = path.join(directory, name);
      const fd = fs.openSync(filename, fs.constants.O_WRONLY | fs.constants.O_CREAT
        | fs.constants.O_EXCL | fs.constants.O_NOFOLLOW, 0o600);
      created.push({ filename, fd, info: fs.fstatSync(fd) });
      fs.fchmodSync(fd, 0o600);
    }
    for (let index = 0; index < created.length; index++) fs.writeFileSync(created[index].fd, outputs[index][1]);
    complete = true;
  } finally {
    for (const file of created) {
      try { fs.closeSync(file.fd); } catch { /* Preserve the original generic failure. */ }
      if (!complete) {
        try {
          const info = fs.lstatSync(file.filename);
          if (info.dev === file.info.dev && info.ino === file.info.ino && info.isFile()) fs.unlinkSync(file.filename);
        } catch { /* Never remove an output that another process replaced. */ }
      }
    }
  }
}

function main() {
  let key;
  let values;
  try {
    requireValid(process.argv.length === 4 && typeof process.getuid === 'function');
    key = base64(process.env.RUNAB_PREP_KEY, 32);
    delete process.env.RUNAB_PREP_KEY;
    values = validate(decrypt(readEnvelope(process.argv[2]), key));
    writeOutputs(process.argv[3], values);
    process.stdout.write('{"payload_ready":true}\n');
    return 0;
  } catch {
    process.stderr.write('ERROR: preparation payload rejected\n');
    return 1;
  } finally {
    if (key) key.fill(0);
    if (values) {
      values.integrations.fill(0);
      values.seed.fill(0);
    }
    delete process.env.RUNAB_PREP_KEY;
  }
}

if (require.main === module) process.exitCode = main();
