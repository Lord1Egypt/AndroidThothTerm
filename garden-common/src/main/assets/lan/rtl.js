/*
 * ThothTerm Ubuntu -- LAN Terminal: right-to-left text on terminal rows.
 *
 * Copyright (C) 2026 ThothTerm. Licensed under the Apache License, Version 2.0.
 *
 * xterm.js lays every row out left to right in inline-block spans, which
 * isolates each span from the browser's bidirectional handling, so Arabic
 * and Hebrew come out reversed and unjoined. This is a display-only overlay
 * that mirrors the phone terminal's renderer (emulatorview PaintRenderer):
 *
 *  - Only rows containing right-to-left text are touched; every other row,
 *    and everything on the alternate screen (vim, less, htop...), keeps
 *    xterm.js's own rendering.
 *  - The row's logical text comes from the terminal buffer. Embedding levels
 *    and the visual order come from bidi-js (UAX #9, left-to-right paragraph,
 *    as the phone's java.text.Bidi with DIRECTION_LEFT_TO_RIGHT).
 *  - Each directional run is drawn as one complete string so the browser
 *    shapes it (Arabic joining, tashkeel, mirrored brackets), scaled to the
 *    exact cells it occupies so no column moves. A run with several ANSI
 *    styles is drawn once per style, each copy clipped to its own cells, so
 *    joining survives colour changes.
 *  - The cursor is drawn over its visual cell without splitting the run.
 *
 * The PTY stream, the buffer, selection and copy/paste stay in logical order.
 */
(function (root) {
  'use strict';

  /** Strong right-to-left characters and RTL marks/embeddings. */
  var RTL = /[֐-ࣿיִ-﷿ﹰ-﻿‏‫‮⁧]|[\uD802-\uD803\uD83A-\uD83B][\uDC00-\uDFFF]/;
  var INVERTED_DEFAULT = 257;

  function hasRtl(text) {
    return RTL.test(text);
  }

  /**
   * Per-cell embedding levels and visual order of a row whose cells hold
   * {@code chars} (a cell may hold several UTF-16 units: a surrogate pair or
   * a base character with combining marks).
   */
  function cellOrder(Bidi, chars) {
    var text = chars.join('');
    var cellOf = new Array(text.length);
    var starts = new Array(chars.length);
    var at = 0;
    for (var c = 0; c < chars.length; ++c) {
      starts[c] = at;
      for (var k = 0; k < chars[c].length; ++k) cellOf[at++] = c;
    }
    var embedding = Bidi.getEmbeddingLevels(text, 'ltr');
    var visual = Bidi.getReorderedIndices(text, embedding);
    var seen = new Array(chars.length);
    var order = [];
    for (var i = 0; i < visual.length; ++i) {
      var cell = cellOf[visual[i]];
      if (!seen[cell]) {
        seen[cell] = true;
        order.push(cell);
      }
    }
    var levels = starts.map(function (s) { return embedding.levels[s]; });
    return { levels: levels, order: order };
  }

  function attach(term, Bidi) {
    var canvas = document.createElement('canvas').getContext('2d');

    function fontFor(bold) {
      var o = term.options;
      return (bold ? o.fontWeightBold : o.fontWeight) + ' ' + o.fontSize + 'px ' + o.fontFamily;
    }

    term.onRender(function (range) {
      var buffer = term.buffer.active;
      // Full-screen programs position every cell themselves; leave them alone.
      if (buffer.type === 'alternate') return;
      var rowsEl = term.element && term.element.querySelector('.xterm-rows');
      var screen = term.element && term.element.querySelector('.xterm-screen');
      if (!rowsEl || !screen) return;
      var cellWidth = screen.getBoundingClientRect().width / term.cols;
      if (!(cellWidth > 0)) return;
      for (var y = range.start; y <= range.end; ++y) {
        var rowEl = rowsEl.children[y];
        var line = buffer.getLine(buffer.viewportY + y);
        if (rowEl && line && hasRtl(line.translateToString(true))) {
          rebuildRow(rowEl, line, cellWidth);
        }
      }
    });

    function rebuildRow(rowEl, line, cellWidth) {
      var cursorEl = rowEl.querySelector('.xterm-cursor');
      var cursorClasses = cursorEl ? cursorEl.className : null;
      var cursorX = cursorEl ? term.buffer.active.cursorX : -1;

      var cells = [];
      var cell;
      for (var x = 0; x < term.cols; ++x) {
        cell = line.getCell(x, cell);
        if (!cell) break;
        var width = cell.getWidth();
        if (width === 0) continue;
        cells.push({ x: x, chars: cell.getChars() || ' ', width: width, style: cellStyle(cell) });
      }
      var bidi = cellOrder(Bidi, cells.map(function (c) { return c.chars; }));

      var parts = [];
      var visualCell = 0;
      var cursorLeft = -1;
      var cursorWidth = 1;
      var i = 0;
      while (i < bidi.order.length) {
        // One directional run: consecutive visual cells at the same level.
        var level = bidi.levels[bidi.order[i]];
        var run = [];
        while (i < bidi.order.length && bidi.levels[bidi.order[i]] === level) {
          run.push(cells[bidi.order[i]]);
          ++i;
        }
        var rtl = (level & 1) === 1;
        var logical = rtl ? run.slice().reverse() : run;
        var text = logical.map(function (c) { return c.chars; }).join('');
        var runCells = run.reduce(function (n, c) { return n + c.width; }, 0);
        var runStart = visualCell;
        var scale = scaleFor(text, runCells * cellWidth, run[0].style.bold);

        // Split the run by style; each piece shows its own cells of the whole run.
        var j = 0;
        var offset = 0;
        while (j < run.length) {
          var style = run[j].style;
          var pieceCells = 0;
          var k = j;
          while (k < run.length && run[k].style.key === style.key) {
            if (run[k].x === cursorX) {
              cursorLeft = runStart + offset + pieceCells;
              cursorWidth = run[k].width;
            }
            pieceCells += run[k].width;
            ++k;
          }
          parts.push(piece(text, style, rtl, scale, runCells, offset, pieceCells, cellWidth));
          offset += pieceCells;
          j = k;
        }
        visualCell += runCells;
      }
      if (cursorLeft >= 0) {
        var caret = document.createElement('span');
        caret.className = cursorClasses;
        caret.style.cssText = 'position:absolute;top:0;height:100%;pointer-events:none;opacity:.6;'
          + 'left:' + (cursorLeft * cellWidth) + 'px;width:' + (cursorWidth * cellWidth) + 'px;';
        parts.push(caret);
      }
      rowEl.style.position = 'relative';
      rowEl.replaceChildren.apply(rowEl, parts);
    }

    /** Horizontal scale that makes {@code text} fill exactly {@code width} px. */
    function scaleFor(text, width, bold) {
      canvas.font = fontFor(bold);
      var natural = canvas.measureText(text).width;
      return natural > 0 ? width / natural : 1;
    }

    /** The cells [offset, offset + cells) of a run drawn as one shaped string. */
    function piece(text, style, rtl, scale, runCells, offset, cells, cellWidth) {
      var outer = document.createElement('span');
      outer.className = style.classes;
      outer.style.cssText = style.css + 'position:relative;overflow:hidden;letter-spacing:0;'
        + 'width:' + (cells * cellWidth) + 'px;';
      var inner = document.createElement('span');
      inner.style.cssText = 'position:absolute;top:0;white-space:pre;letter-spacing:0;'
        + 'unicode-bidi:bidi-override;direction:' + (rtl ? 'rtl' : 'ltr') + ';'
        + 'transform-origin:0 0;transform:scaleX(' + scale + ');'
        + 'left:' + (-offset * cellWidth) + 'px;';
      inner.textContent = style.invisible ? text.replace(/[^]/g, ' ') : text;
      outer.appendChild(inner);
      return outer;
    }

    function hex(rgb) {
      return '#' + ('00000' + rgb.toString(16)).slice(-6);
    }

    /** The classes and inline colours xterm.js's DOM renderer gives a cell. */
    function cellStyle(cell) {
      var classes = [];
      var css = '';
      var bold = cell.isBold() !== 0;
      var fg = cell.isFgPalette() ? cell.getFgColor() : -1;
      if (fg >= 0 && fg < 8 && bold && term.options.drawBoldTextInBrightColors) fg += 8;
      var bg = cell.isBgPalette() ? cell.getBgColor() : -1;
      var fgRgb = cell.isFgRGB() ? hex(cell.getFgColor()) : null;
      var bgRgb = cell.isBgRGB() ? hex(cell.getBgColor()) : null;
      if (cell.isInverse() !== 0) {
        var t = fg; fg = bg; bg = t;
        t = fgRgb; fgRgb = bgRgb; bgRgb = t;
        if (fg < 0 && !fgRgb) fg = INVERTED_DEFAULT;
        if (bg < 0 && !bgRgb) bg = INVERTED_DEFAULT;
      }
      if (fg >= 0) classes.push('xterm-fg-' + fg);
      if (bg >= 0) classes.push('xterm-bg-' + bg);
      if (fgRgb) css += 'color:' + fgRgb + ';';
      if (bgRgb) css += 'background-color:' + bgRgb + ';';
      if (bold) classes.push('xterm-bold');
      if (cell.isItalic() !== 0) classes.push('xterm-italic');
      if (cell.isDim() !== 0) classes.push('xterm-dim');
      if (cell.isUnderline() !== 0) classes.push('xterm-underline-1');
      if (cell.isStrikethrough() !== 0) classes.push('xterm-strikethrough');
      var joined = classes.join(' ');
      var invisible = cell.isInvisible() !== 0;
      return { classes: joined, css: css, bold: bold, invisible: invisible,
               key: joined + '|' + css + '|' + invisible };
    }
  }

  var api = { hasRtl: hasRtl, cellOrder: cellOrder, attach: attach };
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.ThothRtl = api;
})(this);
