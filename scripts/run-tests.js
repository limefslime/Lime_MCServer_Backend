import { readdirSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// PGlite's WASM startup can exit an isolated Node test child before its tests run.
// Execute each node:test file directly in its own process; this also
// keeps each file's DB hooks and environment settings separate.
const directory = new URL('../tests/', import.meta.url);
for (const file of readdirSync(directory).filter(name => name.endsWith('.test.js')).sort()) {
  const result = spawnSync(process.execPath, [fileURLToPath(new URL(file, directory))], { stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}
