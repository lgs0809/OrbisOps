import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const result = spawnSync('python3', ['../scripts/release-hygiene.py'], {
  cwd: webRoot,
  stdio: 'inherit',
});
process.exit(result.status ?? 1);
