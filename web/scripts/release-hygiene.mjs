import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const self = fileURLToPath(import.meta.url);
const skip = new Set(['node_modules', 'dist', 'build', 'test-results', '.git']);
const forbidden = new Map([
  ['legacy package', 'cn.' + 'bugstack'],
  ['legacy author', 'xiao' + 'fuge'],
  ['legacy author handle', 'fuzheng' + 'wei'],
  ['unrelated business fixture', 'group' + '-buy'],
  ['unrelated business fixture id', 'group' + '_buy'],
  ['unrelated business fixture zh', '拼' + '团'],
  ['unrelated payment fixture', 's' + '-pay'],
  ['legacy product name', 'AI Agent ' + 'Station'],
  ['legacy database prefix', 'agent' + '_station'],
  ['legacy console name', 'Ops AI ' + 'Console'],
  ['private local home', '/workspace/' + 'developer'],
  ['private model endpoint', 'api.' + '0-0.pro'],
]);
const extensions = new Set(['.ts', '.tsx', '.js', '.mjs', '.json', '.md', '.html', '.yml', '.yaml', '.sh', '.conf']);
const violations = [];

function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (skip.has(entry.name)) continue;
    const current = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(current);
      continue;
    }
    if (current === self) continue;
    if (!extensions.has(path.extname(entry.name)) && !['Dockerfile'].includes(entry.name)) continue;
    const text = fs.readFileSync(current, 'utf8');
    for (const [label, marker] of forbidden) {
      if (text.includes(marker)) violations.push(`${path.relative(root, current)}: ${label}`);
    }
  }
}

walk(root);
if (violations.length) {
  console.error('OrbisOps Web release hygiene failed:');
  for (const violation of [...new Set(violations)].sort()) console.error(`  - ${violation}`);
  process.exit(1);
}
console.log('OrbisOps Web release hygiene passed.');
