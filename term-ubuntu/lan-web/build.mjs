// Builds term-ubuntu/src/main/assets/lan/{xterm.js,xterm.css,licenses.txt}
// from the npm packages pinned (with integrity hashes) in package-lock.json.
//
//   npm ci --ignore-scripts && node build.mjs          # write the assets
//   npm ci --ignore-scripts && node build.mjs --check  # verify the committed ones
//
// The output is deterministic: the same lockfile gives the same bytes.
import * as esbuild from 'esbuild';
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const assets = path.resolve(here, '../src/main/assets/lan');
const modules = path.join(here, 'node_modules');
const check = process.argv.includes('--check');

const pinned = JSON.parse(readFileSync(path.join(here, 'package.json'), 'utf8')).devDependencies;
const versionOf = (name) =>
  JSON.parse(readFileSync(path.join(modules, name, 'package.json'), 'utf8')).version;
for (const name of Object.keys(pinned)) {
  if (versionOf(name) !== pinned[name]) {
    throw new Error(`${name} is ${versionOf(name)}, pinned ${pinned[name]}; run npm ci`);
  }
}

const src = path.join(modules, '@xterm/xterm/src');
const banner = `/*!
 * xterm.js ${pinned['@xterm/xterm']} and @xterm/addon-fit ${pinned['@xterm/addon-fit']}
 * (https://github.com/xtermjs/xterm.js, tag ${pinned['@xterm/xterm']}),
 * bundled unminified from their TypeScript sources by
 * term-ubuntu/lan-web/build.mjs with esbuild ${pinned.esbuild}.
 * MIT licensed: the xterm.js authors, SourceLair, Christopher Jeffrey and,
 * for src/vs, Microsoft Corporation. Full notices in licenses.txt.
 */`;

const result = await esbuild.build({
  absWorkingDir: here,
  entryPoints: ['entry.ts'],
  bundle: true,
  write: false,
  format: 'iife',
  globalName: 'ThothXterm',
  target: 'es2020',
  platform: 'browser',
  minify: false,
  charset: 'utf8',
  legalComments: 'none',
  banner: { js: banner },
  alias: {
    common: path.join(src, 'common'),
    browser: path.join(src, 'browser'),
    vs: path.join(src, 'vs'),
  },
  tsconfigRaw: { compilerOptions: { experimentalDecorators: true, useDefineForClassFields: false } },
  logLevel: 'warning',
});

const read = (p) => readFileSync(p, 'utf8');
const licenses = [
  'Third-party software in ThothTerm Ubuntu\'s LAN Mode web terminal',
  '=================================================================',
  '',
  `xterm.js ${pinned['@xterm/xterm']} -- https://github.com/xtermjs/xterm.js (MIT)`,
  '-----------------------------------------------------------------',
  read(path.join(modules, '@xterm/xterm/LICENSE')).trim(),
  '',
  `@xterm/addon-fit ${pinned['@xterm/addon-fit']} -- https://github.com/xtermjs/xterm.js (MIT)`,
  '-----------------------------------------------------------------',
  read(path.join(modules, '@xterm/addon-fit/LICENSE')).trim(),
  '',
  'Visual Studio Code base library, vendored by xterm.js in src/vs -- https://github.com/microsoft/vscode (MIT)',
  '-----------------------------------------------------------------',
  read(path.join(here, 'licenses/vscode-LICENSE.txt')).trim(),
  '',
].join('\n');

const outputs = {
  'xterm.js': result.outputFiles[0].text,
  'xterm.css': read(path.join(modules, '@xterm/xterm/css/xterm.css')),
  'licenses.txt': licenses,
};

let stale = [];
for (const [name, text] of Object.entries(outputs)) {
  const target = path.join(assets, name);
  if (check) {
    let current = null;
    try { current = readFileSync(target, 'utf8'); } catch { /* missing */ }
    if (current !== text) stale.push(name);
  } else {
    writeFileSync(target, text);
  }
}
if (check && stale.length) {
  console.error('Out of date: ' + stale.join(', ') + ' -- run node build.mjs');
  process.exit(1);
}
console.log(check ? 'LAN web assets match their pinned sources.' : 'LAN web assets written.');
