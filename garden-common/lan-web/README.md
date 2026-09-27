# LAN Mode web terminal — sources and provenance

LAN Mode serves the page in `term-ubuntu/src/main/assets/lan/`. Nothing there
comes from a CDN and nothing is fetched at runtime.

## Files

| Asset | Origin |
|---|---|
| `index.html`, `app.js`, `app.css`, `rtl.js` | ThothTerm's own code (Apache-2.0), edited by hand |
| `xterm.js` | Generated: xterm.js, its fit addon and bidi-js, bundled **unminified from their sources** by `build.mjs` |
| `xterm.css` | Generated: copied verbatim from `@xterm/xterm` |
| `fonts.css`, `fonts/*.woff2` | Generated: Cascadia Mono subsets from `@fontsource/cascadia-mono`, unmodified files, registered as `ThothTerm Mono` |
| `licenses.txt` | Generated: the notices of everything above |

## Pinned inputs

Every input comes from npm and is pinned by sha512 in `package-lock.json`.

| Package | Version | Licence | Upstream | sha512 (npm integrity) |
|---|---|---|---|---|
| `@xterm/xterm` | 6.0.0 | MIT | github.com/xtermjs/xterm.js, tag `6.0.0` = `f447274f430fd22513f6adbf9862d19524471c04` | `TQwDdQGtwwDt+2cgKDLn0IRaSxYu1tSUjgKarSDkUM0ZNiSRXFpjxEsvc/Zgc5kq5omJ+V0a8/kIM2WD3sMOYg==` |
| `@xterm/addon-fit` | 0.11.0 | MIT | same repository and commit | `jYcgT6xtVYhnhgxh3QgYDnnNMYTcf8ElbxxFzX0IZo+vabQqSPAjC3c1wJrKB5E19VwQei89QCiZZP86DCPF7g==` |
| `bidi-js` | 1.0.3 | MIT | github.com/lojjic/bidi-js | `RKshQI1R3YQ+n9YJz2QQ147P66ELpa1FQEg20Dk8oW9t2KgLbpDLLp9aGZ7y8WHSshDknG0bknqGw5/tyCs5tw==` |
| `@fontsource/cascadia-mono` | 5.3.0 | SIL OFL 1.1 | github.com/microsoft/cascadia-code, packaged by Fontsource | `50GR+UT8w92Gzg5QWHQWe7arIfogm6VrpEqUx9K/YK/MgbwS2GaBx/Dfd+sE8uAQu6btnbDFq9ezrOC41fgo0g==` |
| `esbuild` (build tool only, not shipped) | 0.28.2 | MIT | github.com/evanw/esbuild | `HKVLS8dvII+xoKW9kmqxbRKrnWEXfJJr/FZhhJmiqIB0e053QNYFqOBouTMO/k5sID4MvCiUCvv8b9M4h32wIA==` |

The VS Code base library that xterm.js vendors in `src/vs` is MIT. Its
package carries no licence text, so `licenses/vscode-LICENSE.txt` is
microsoft/vscode `LICENSE.txt` at commit
`529ee19061e6723e0a640fe432e57c69d50a4f4f` (sha256
`9480271317925265e806a9a196aaa33410a962fa9d4d1e248a4a5187bc8c9df9`).

**Why bidi-js 1.0.3 and not 1.1.0.** 1.0.3 has been unchanged since July 2023
and is in very wide use. 1.1.0 (September 2026) carries identical Unicode data
and embedding-level code but rewrites the character-type parser. The older
release is the stable choice. bidi-js states conformance to UAX #9 clause C1,
verified against the Unicode conformance test suites.

## Build and verify

    npm ci --ignore-scripts
    node build.mjs            # regenerate the assets
    node build.mjs --check    # exit 1 unless the committed assets match
    npm test                  # RTL row ordering tests (rtl.test.mjs)

The output is deterministic. A clean `npm ci` followed by `--check`
reproduces every committed generated file byte for byte. The Gradle build
never runs Node: it packages the committed assets.

## Right-to-left text

xterm.js has no bidirectional text support. It draws every row left to
right in inline-block spans, so Arabic and Hebrew appear reversed and
unjoined. `rtl.js` is a display-only overlay that follows the phone
terminal's renderer:

- It touches only rows containing RTL text. The alternate screen (vim, less,
  htop) is never touched.
- bidi-js computes the levels and visual order: UAX #9 with a left-to-right
  paragraph.
- Each directional run is drawn as one shaped string, scaled to exactly its
  cells.
- ANSI colours, attributes and the cursor use xterm.js's own classes.

The PTY stream, the buffer, and selection and copy/paste all stay in logical
order.

Known limitation: on a row that mixes directions, the selection highlight and
mouse hit-testing follow logical columns, as xterm.js knows them. The copied
text is always correct logical text.
