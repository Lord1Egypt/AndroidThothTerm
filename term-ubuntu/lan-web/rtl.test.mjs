// node --test rtl.test.mjs -- visual order of terminal rows (assets/lan/rtl.js),
// computed with the pinned bidi-js exactly as the page does.
import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import * as Bidi from 'bidi-js/src/index.js';

const rtl = createRequire(import.meta.url)('../src/main/assets/lan/rtl.js');

/** Visual string of a row, one cell per entry of {@code cells}. */
function visual(cells) {
  const { order } = rtl.cellOrder(Bidi, cells);
  return order.map((i) => cells[i]).join('');
}
const cellsOf = (s) => Array.from(s);

test('pure English rows are not touched and keep logical order', () => {
  assert.equal(rtl.hasRtl('thoth@thothterm:~$ ls -la /home'), false);
  assert.equal(visual(cellsOf('thoth@thothterm:~$ ls')), 'thoth@thothterm:~$ ls');
});

test('سلام عليكم is displayed right to left, words and letters', () => {
  const s = 'سلام عليكم';
  assert.equal(rtl.hasRtl(s), true);
  // Visual left-to-right: the second word first, each word's letters reversed.
  assert.equal(visual(cellsOf(s)), 'مكيلع مالس');
});

test('mixed Arabic and English after a prompt', () => {
  const s = '$ echo سلام world';
  assert.equal(visual(cellsOf(s)), '$ echo مالس world');
});

test('Arabic with numbers and brackets', () => {
  // "العدد (123) هنا": digits keep LTR order; the bracket pair resolves RTL.
  const s = 'العدد (123) هنا';
  const v = visual(cellsOf(s));
  assert.ok(v.includes('123'), v);
  // Code points in visual order. The brackets resolve to the RTL runs, where
  // the browser draws their mirrored glyphs (UAX #9 L4): on screen "(123)".
  assert.equal(v, 'انه )123( ددعلا');
  const { levels } = rtl.cellOrder(Bidi, cellsOf(s));
  assert.equal(levels[6] & 1, 1, 'opening bracket is RTL, so the browser mirrors it');
});

test('tashkeel stays attached to its letter cell', () => {
  // xterm.js stores a combining mark in its base character's cell.
  const cells = ['م', 'َ', 'ر', 'ح', 'ب', 'ا'];
  const merged = ['مَ', 'ر', 'ح', 'ب', 'ا'];
  const { order, levels } = rtl.cellOrder(Bidi, merged);
  assert.deepEqual(order, [4, 3, 2, 1, 0]);
  assert.ok(levels.every((l) => l === 1));
  assert.equal(cells.length, 6);
});

test('trailing blank cells stay at the right of the row', () => {
  const cells = cellsOf('سلام   ');
  const { order } = rtl.cellOrder(Bidi, cells);
  assert.deepEqual(order.slice(-3), [4, 5, 6]);
});

test('the order is a permutation of the cells', () => {
  const cells = cellsOf('a سلام 12 b (نص) c');
  const { order, levels } = rtl.cellOrder(Bidi, cells);
  assert.equal(levels.length, cells.length);
  assert.deepEqual([...order].sort((x, y) => x - y), cells.map((_, i) => i));
});
