// Builds term-ubuntu/src/main/assets/lan/{xterm.js,xterm.css,fonts.css,
// fonts/*.woff2,licenses.txt} from the npm packages pinned (with integrity
// hashes) in package-lock.json.
//
//   npm ci --ignore-scripts && node build.mjs          # write the assets
//   npm ci --ignore-scripts && node build.mjs --check  # verify the committed ones
//
// The output is deterministic: the same lockfile gives the same bytes.
import * as esbuild from 'esbuild';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
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
 * (https://github.com/xtermjs/xterm.js, tag ${pinned['@xterm/xterm']}) and
 * bidi-js ${pinned['bidi-js']} (https://github.com/lojjic/bidi-js),
 * bundled unminified from their sources by term-ubuntu/lan-web/build.mjs
 * with esbuild ${pinned.esbuild}. MIT licensed: the xterm.js authors,
 * SourceLair, Christopher Jeffrey, Microsoft Corporation (src/vs) and
 * Jason Johnston (bidi-js). Full notices in licenses.txt.
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
  `bidi-js ${pinned['bidi-js']} -- https://github.com/lojjic/bidi-js (MIT)`,
  '-----------------------------------------------------------------',
  read(path.join(modules, 'bidi-js/LICENSE.txt')).trim(),
  '',
  `Cascadia Mono, via @fontsource/cascadia-mono ${pinned['@fontsource/cascadia-mono']} -- https://github.com/microsoft/cascadia-code (SIL OFL 1.1)`,
  'Served to the browser under the family name "ThothTerm Mono"; the font files are unmodified.',
  '-----------------------------------------------------------------',
  read(path.join(modules, '@fontsource/cascadia-mono/LICENSE')).trim(),
  '',
].join('\n');

// The terminal font: Cascadia Mono, which covers Arabic and Hebrew, in the
// scripts a shell needs. It is registered as "ThothTerm Mono" so a copy
// installed on the viewer's machine can never be picked instead, and every
// browser renders with these exact files.
const FONT_SUBSETS = ['latin', 'latin-ext', 'arabic', 'hebrew', 'greek', 'cyrillic',
  'cyrillic-ext', 'symbols2'];
const FONT_WEIGHTS = [400, 700];
const fontDir = path.join(modules, '@fontsource/cascadia-mono');
const fontFiles = {};
const faces = [];
for (const weight of FONT_WEIGHTS) {
  const css = read(path.join(fontDir, `${weight}.css`));
  for (const block of css.split('@font-face').slice(1)) {
    const subset = FONT_SUBSETS.find((s) => block.includes(`cascadia-mono-${s}-${weight}-normal.woff2`));
    if (!subset) continue;
    const file = `cascadia-mono-${subset}-${weight}-normal.woff2`;
    const range = /unicode-range:\s*([^;]+);/.exec(block)[1].trim();
    fontFiles[file] = readFileSync(path.join(fontDir, 'files', file));
    faces.push(`@font-face {\n  font-family: 'ThothTerm Mono';\n  font-style: normal;\n`
      + `  font-weight: ${weight};\n  font-display: block;\n`
      + `  src: url(/fonts/${file}) format('woff2');\n  unicode-range: ${range};\n}\n`);
  }
}
if (Object.keys(fontFiles).length !== FONT_SUBSETS.length * FONT_WEIGHTS.length) {
  throw new Error('font subsets missing from @fontsource/cascadia-mono');
}
const fontsCss = `/* Cascadia Mono (SIL OFL 1.1) as "ThothTerm Mono"; generated by lan-web/build.mjs. */\n`
  + faces.join('\n');

const outputs = {
  'xterm.js': result.outputFiles[0].text,
  'xterm.css': read(path.join(modules, '@xterm/xterm/css/xterm.css')),
  'licenses.txt': licenses,
  'fonts.css': fontsCss,
};
for (const [file, data] of Object.entries(fontFiles)) outputs['fonts/' + file] = data;

let stale = [];
for (const [name, text] of Object.entries(outputs)) {
  const target = path.join(assets, name);
  if (check) {
    let current = null;
    try { current = readFileSync(target); } catch { /* missing */ }
    if (current === null || !current.equals(Buffer.from(text))) stale.push(name);
  } else {
    mkdirSync(path.dirname(target), { recursive: true });
    writeFileSync(target, text);
  }
}
if (check && stale.length) {
  console.error('Out of date: ' + stale.join(', ') + ' -- run node build.mjs');
  process.exit(1);
}
console.log(check ? 'LAN web assets match their pinned sources.' : 'LAN web assets written.');
