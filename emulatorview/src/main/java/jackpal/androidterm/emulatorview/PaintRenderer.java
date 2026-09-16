/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2022 Roumen Petrov.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jackpal.androidterm.emulatorview;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.annotation.RequiresApi;

import java.text.Bidi;


class PaintRenderer extends BaseTextRenderer {
    private static final char[] EXAMPLE_CHAR = {'X'};
    private final Paint mTextPaint;
    private final float mCharWidth;
    private final int mCharHeight;
    private final int mCharAscent;
    private final int mCharDescent;

    public PaintRenderer(Typeface typeface, int fontSize, ColorScheme scheme) {
        super(scheme);
        mTextPaint = new Paint();
        mTextPaint.setTypeface(typeface);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(fontSize);

        mCharHeight = (int) Math.ceil(mTextPaint.getFontSpacing());
        mCharAscent = (int) Math.ceil(mTextPaint.ascent());
        mCharDescent = mCharHeight + mCharAscent;
        mCharWidth = mTextPaint.measureText(EXAMPLE_CHAR, 0, 1);
    }

    /** Map a displayed cell back to its logical terminal-buffer column. */
    static int logicalCellForVisual(char[] text, int visualCell) {
        if (text == null || visualCell < 0) return visualCell;
        BidiLayout layout = BidiLayout.create(text);
        return layout == null ? visualCell : layout.logicalCell(visualCell);
    }

    public void drawTextRun(Canvas canvas, float x, float y, int lineOffset,
                            int runWidth, char[] text, int index, int count,
                            boolean selectionStyle, int textStyle,
                            int cursorOffset, int cursorIndex, int cursorIncr, int cursorWidth, int cursorMode) {
        int foreColor = TextStyle.decodeForeColor(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        int effect = TextStyle.decodeEffect(textStyle);

        boolean inverse = mReverseVideo ^
                (effect & (TextStyle.fxInverse | TextStyle.fxItalic)) != 0;
        if (inverse) {
            int temp = foreColor;
            foreColor = backColor;
            backColor = temp;
        }

        if (selectionStyle) {
            backColor = TextStyle.ciCursorBackground;
        }

        boolean blink = (effect & TextStyle.fxBlink) != 0;
        if (blink && backColor < 8) {
            backColor += 8;
        }
        mTextPaint.setColor(mPalette[backColor]);

        BidiLayout bidiLayout = BidiLayout.create(text);
        int bidiBaseColumn = bidiLayout == null ? 0
                : lineOffset - bidiLayout.cellAt(index);
        float left = x + lineOffset * mCharWidth;
        if (bidiLayout == null) {
            canvas.drawRect(left, y + mCharAscent - mCharDescent,
                    left + runWidth * mCharWidth, y, mTextPaint);
        } else {
            drawBidiBackground(canvas, x, y, text, index, count,
                    bidiBaseColumn, bidiLayout);
        }

        boolean cursorVisible = lineOffset <= cursorOffset && cursorOffset < (lineOffset + runWidth);
        float cursorX = 0;
        if (cursorVisible) {
            if (bidiLayout == null) {
                cursorX = x + cursorOffset * mCharWidth;
            } else {
                cursorX = bidiCursorX(text, bidiLayout, x, bidiBaseColumn, cursorOffset);
            }
            drawCursorImp(canvas, (int) cursorX, y, cursorWidth * mCharWidth, mCharHeight, cursorMode);
        }

        boolean invisible = (effect & TextStyle.fxInvisible) != 0;
        if (!invisible) {
            boolean bold = (effect & TextStyle.fxBold) != 0;
            boolean underline = (effect & TextStyle.fxUnderline) != 0;
            if (bold) {
                mTextPaint.setFakeBoldText(true);
            }
            if (underline) {
                mTextPaint.setUnderlineText(true);
            }
            int textPaintColor;
            if (foreColor < 8 && bold) {
                // In 16-color mode, bold also implies bright foreground colors
                textPaintColor = mPalette[foreColor + 8];
            } else {
                textPaintColor = mPalette[foreColor];
            }
            mTextPaint.setColor(textPaintColor);

            float textOriginY = y - mCharDescent;

            if (bidiLayout != null) {
                drawBidiText(canvas, text, index, count, x, textOriginY,
                        bidiBaseColumn, bidiLayout);
                if (cursorVisible) {
                    int save = canvas.save();
                    canvas.clipRect(cursorX, y - mCharHeight,
                            cursorX + cursorWidth * mCharWidth, y);
                    mTextPaint.setColor(mPalette[TextStyle.ciCursorForeground]);
                    drawBidiText(canvas, text, index, count, x, textOriginY,
                            bidiBaseColumn, bidiLayout);
                    canvas.restoreToCount(save);
                    mTextPaint.setColor(textPaintColor);
                }
            } else if (cursorVisible) {
                // Text before cursor
                int countBeforeCursor = cursorIndex - index;
                int countAfterCursor = count - (countBeforeCursor + cursorIncr);
                if (countBeforeCursor > 0) {
                    drawTextInCells(canvas, text, index, countBeforeCursor,
                            left, textOriginY);
                }
                // Text at cursor
                mTextPaint.setColor(mPalette[TextStyle.ciCursorForeground]);
                drawTextInCells(canvas, text, cursorIndex, cursorIncr,
                        cursorX, textOriginY);
                // Text after cursor
                if (countAfterCursor > 0) {
                    mTextPaint.setColor(textPaintColor);
                    drawTextInCells(canvas, text, cursorIndex + cursorIncr,
                            countAfterCursor, cursorX + cursorWidth * mCharWidth,
                            textOriginY);
                }
            } else {
                drawTextInCells(canvas, text, index, count, left, textOriginY);
            }
            if (bold) {
                mTextPaint.setFakeBoldText(false);
            }
            if (underline) {
                mTextPaint.setUnderlineText(false);
            }
        }
    }

    private void drawBidiBackground(Canvas canvas, float x, float y, char[] text,
                                    int index, int count, int baseColumn,
                                    BidiLayout layout) {
        for (BidiRun run : layout.visualRuns) {
            BidiSegment segment = run.intersect(index, index + count, layout);
            if (segment == null || segment.cellWidth == 0) continue;
            float left = x + (baseColumn + segment.visualCell) * mCharWidth;
            canvas.drawRect(left, y + mCharAscent - mCharDescent,
                    left + segment.cellWidth * mCharWidth, y, mTextPaint);
        }
    }

    private void drawBidiText(Canvas canvas, char[] text, int index, int count,
                              float x, float y, int baseColumn, BidiLayout layout) {
        for (BidiRun run : layout.visualRuns) {
            BidiSegment segment = run.intersect(index, index + count, layout);
            if (segment == null || segment.cellWidth == 0) continue;
            float left = x + (baseColumn + segment.visualCell) * mCharWidth;
            if (run.rtl) {
                // Shape the complete directional run so joining survives ANSI
                // style, selection, and cursor boundaries, then reveal only
                // the cells owned by this styled segment.
                int save = canvas.save();
                canvas.clipRect(left, y + mCharAscent,
                        left + segment.cellWidth * mCharWidth, y + mCharDescent);
                float runLeft = x + (baseColumn + run.visualStartCell) * mCharWidth;
                drawRtlText(canvas, text, run, runLeft,
                        run.width() * mCharWidth, y);
                canvas.restoreToCount(save);
            } else {
                drawTextInCells(canvas, text, segment.start,
                        segment.end - segment.start, left, y);
            }
        }
    }

    /**
     * Horizontal position of the cursor for a logical cell on a bidi row.
     *
     * LTR runs live on the fixed cell grid. RTL runs are shaped as connected
     * runs and placed at their natural advance, so their cursor position is
     * measured with the same glyph advances instead of the cell grid to keep
     * the cursor on the actual glyph.
     */
    private float bidiCursorX(char[] text, BidiLayout layout, float x,
                              int baseColumn, int logicalCell) {
        BidiRun run = layout.runForLogicalCell(logicalCell);
        if (run == null || !run.rtl) {
            int visualCell = layout.visualCell(logicalCell);
            if (visualCell < 0) visualCell = logicalCell;
            return x + (baseColumn + visualCell) * mCharWidth;
        }

        float boxLeft = x + (baseColumn + run.visualStartCell) * mCharWidth;
        float targetWidth = run.width() * mCharWidth;
        float advance = rtlRunAdvance(text, run.start, run.limit);
        float scale = (advance > targetWidth && advance > 0f)
                ? targetWidth / advance : 1f;
        float right = boxLeft + targetWidth;

        int index = run.start;
        while (index < run.limit && layout.cellAt(index) < logicalCell) {
            index += charCountAt(text, index, run.limit);
        }
        int after = index;
        if (index < run.limit) {
            after = Math.min(run.limit, index + charCountAt(text, index, run.limit));
        }
        float prefix = rtlPrefixAdvance(text, run.start, run.limit, after);
        return right - prefix * scale;
    }

    private static int charCountAt(char[] text, int index, int end) {
        return (Character.isHighSurrogate(text[index]) && index + 1 < end) ? 2 : 1;
    }

    /** Natural advance of a complete shaped right-to-left run. */
    private float rtlRunAdvance(char[] text, int start, int end) {
        if (end <= start) return 0f;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            float advance = mTextPaint.getRunAdvance(text, start, end,
                    start, end, true, end);
            if (advance > 0f) return advance;
        }
        return Math.max(1f, mTextPaint.measureText(new String(text, start, end - start)));
    }

    /** Natural advance of the first {code offset} characters of an RTL run. */
    private float rtlPrefixAdvance(char[] text, int start, int end, int offset) {
        if (offset <= start) return 0f;
        if (offset >= end) return rtlRunAdvance(text, start, end);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            float advance = mTextPaint.getRunAdvance(text, start, end,
                    start, end, true, offset);
            if (advance > 0f) return advance;
        }
        return Math.max(1f, mTextPaint.measureText(new String(text, start, offset - start)));
    }

    private void drawRtlText(Canvas canvas, char[] text, BidiRun run,
                             float boxLeft, float targetWidth, float y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            drawRtlText23(canvas, text, run, boxLeft, targetWidth, y);
        } else {
            drawRtlTextLegacy(canvas, text, run, boxLeft, targetWidth, y);
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private void drawRtlText23(Canvas canvas, char[] text, BidiRun run,
                               float boxLeft, float targetWidth, float y) {
        float advance = rtlRunAdvance(text, run.start, run.limit);
        if (advance <= 0f) return;
        // Draw the shaped run at its natural advance. Only compress when the
        // run would otherwise overflow its cells; never stretch it, which
        // would introduce artificial tracking between joined Arabic glyphs.
        float scale = advance > targetWidth ? targetWidth / advance : 1f;
        float origin = boxLeft + targetWidth - advance * scale;
        int save = canvas.save();
        canvas.translate(origin, 0);
        if (scale != 1f) {
            canvas.scale(scale, 1);
        }
        canvas.drawTextRun(text, run.start, run.limit - run.start,
                run.start, run.limit - run.start,
                0, y, true, mTextPaint);
        canvas.restoreToCount(save);
    }

    @SuppressWarnings("deprecation")
    private void drawRtlTextLegacy(Canvas canvas, char[] text, BidiRun run,
                                   float boxLeft, float targetWidth, float y) {
        String value = new String(text, run.start, run.limit - run.start);
        TextPaint paint = new TextPaint(mTextPaint);
        float measured = Math.max(1, paint.measureText(value));
        int layoutWidth = Math.max(1, (int) Math.ceil(measured) + 2);
        StaticLayout layout = new StaticLayout(value, paint, layoutWidth,
                Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
        float lineWidth = Math.max(1, layout.getLineWidth(0));
        float scale = lineWidth > targetWidth ? targetWidth / lineWidth : 1f;
        float origin = boxLeft + targetWidth - lineWidth * scale;
        float leadingSpace = layoutWidth - lineWidth;
        int save = canvas.save();
        canvas.translate(origin, y - layout.getLineBaseline(0));
        if (scale != 1f) {
            canvas.scale(scale, 1);
        }
        canvas.translate(-leadingSpace, 0);
        layout.draw(canvas);
        canvas.restoreToCount(save);
    }

    /**
     * Draw a terminal run on the same fixed cell grid used for the cursor.
     *
     * Android's drawText(char[]) shapes a complete run using the typeface's
     * fractional glyph advances. Those advances are not guaranteed to add up
     * to the measured terminal cell width, even for a monospace typeface. The
     * resulting drift leaves the grid-aligned cursor visibly separated from
     * the text. Positioning each base character (and its combining marks) at
     * an explicit cell boundary keeps text, selection and cursor geometry in
     * one coordinate system.
     */
    private void drawTextInCells(Canvas canvas, char[] text, int index, int count,
                                 float x, float y) {
        int end = index + count;
        int clusterStart = index;
        int clusterLength = 0;
        int clusterWidth = 0;
        float cellX = x;

        while (index < end) {
            int charCount = Character.isHighSurrogate(text[index])
                    && index + 1 < end ? 2 : 1;
            int width = UnicodeTranscript.charWidth(text, index);

            if (width > 0 && clusterLength > 0) {
                canvas.drawText(text, clusterStart, clusterLength, cellX, y, mTextPaint);
                cellX += clusterWidth * mCharWidth;
                clusterStart = index;
                clusterLength = 0;
            }

            clusterLength += charCount;
            if (width > 0) {
                clusterWidth = width;
            }
            index += charCount;
        }

        if (clusterLength > 0) {
            canvas.drawText(text, clusterStart, clusterLength, cellX, y, mTextPaint);
        }
    }

    /**
     * Visual-only Unicode bidi plan. The source array and terminal cells stay
     * logical.
     *
     * The paragraph base direction is forced left-to-right. A terminal row is
     * written in logical order, so letting the first strong character choose
     * the base direction (for example an Arabic-first prompt) would reorder a
     * trailing ASCII command to the opposite side of the row and break editing
     * after a wrap. Only genuine RTL runs are reversed, in place.
     */
    private static final class BidiLayout {
        final int[] cells;
        final BidiRun[] visualRuns;

        private BidiLayout(int[] cells, BidiRun[] visualRuns) {
            this.cells = cells;
            this.visualRuns = visualRuns;
        }

        static BidiLayout create(char[] text) {
            int length = 0;
            while (length < text.length && text[length] != '\0') length++;
            if (length == 0 || !Bidi.requiresBidi(text, 0, length)) return null;

            int[] cells = buildCellMap(text, length);
            Bidi bidi = new Bidi(new String(text, 0, length),
                    Bidi.DIRECTION_LEFT_TO_RIGHT);
            int count = bidi.getRunCount();
            BidiRun[] runs = new BidiRun[count];
            byte[] levels = new byte[count];
            for (int i = 0; i < count; i++) {
                int start = bidi.getRunStart(i);
                int limit = bidi.getRunLimit(i);
                levels[i] = (byte) bidi.getRunLevel(i);
                runs[i] = new BidiRun(start, limit, levels[i],
                        cells[start], cells[limit]);
            }
            Bidi.reorderVisually(levels, 0, runs, 0, count);
            int visualCell = 0;
            for (BidiRun run : runs) {
                run.visualStartCell = visualCell;
                visualCell += run.logicalEndCell - run.logicalStartCell;
            }
            return new BidiLayout(cells, runs);
        }

        int cellAt(int charIndex) {
            return cells[Math.max(0, Math.min(charIndex, cells.length - 1))];
        }

        int visualCell(int logicalCell) {
            for (BidiRun run : visualRuns) {
                if (logicalCell >= run.logicalStartCell
                        && logicalCell < run.logicalEndCell) {
                    int offset = logicalCell - run.logicalStartCell;
                    return run.rtl
                            ? run.visualStartCell + run.width() - offset - 1
                            : run.visualStartCell + offset;
                }
            }
            return -1;
        }

        int logicalCell(int visualCell) {
            for (BidiRun run : visualRuns) {
                int offset = visualCell - run.visualStartCell;
                if (offset >= 0 && offset < run.width()) {
                    return run.rtl
                            ? run.logicalEndCell - offset - 1
                            : run.logicalStartCell + offset;
                }
            }
            return visualCell;
        }

        BidiRun runForLogicalCell(int logicalCell) {
            for (BidiRun run : visualRuns) {
                if (logicalCell >= run.logicalStartCell
                        && logicalCell < run.logicalEndCell) {
                    return run;
                }
            }
            return null;
        }

        private static int[] buildCellMap(char[] text, int length) {
            int[] result = new int[length + 1];
            int cell = 0;
            int clusterCell = 0;
            int index = 0;
            while (index < length) {
                int charCount = Character.isHighSurrogate(text[index])
                        && index + 1 < length ? 2 : 1;
                int width = UnicodeTranscript.charWidth(text, index);
                if (width > 0) clusterCell = cell;
                for (int i = 0; i < charCount; i++) result[index + i] = clusterCell;
                if (width > 0) cell += width;
                index += charCount;
                result[index] = cell;
            }
            return result;
        }
    }

    private static final class BidiRun {
        final int start;
        final int limit;
        final boolean rtl;
        final int logicalStartCell;
        final int logicalEndCell;
        int visualStartCell;

        BidiRun(int start, int limit, byte level,
                int logicalStartCell, int logicalEndCell) {
            this.start = start;
            this.limit = limit;
            this.rtl = (level & 1) != 0;
            this.logicalStartCell = logicalStartCell;
            this.logicalEndCell = logicalEndCell;
        }

        int width() {
            return logicalEndCell - logicalStartCell;
        }

        BidiSegment intersect(int requestedStart, int requestedEnd, BidiLayout layout) {
            int segmentStart = Math.max(start, requestedStart);
            int segmentEnd = Math.min(limit, requestedEnd);
            if (segmentStart >= segmentEnd) return null;
            int startCell = layout.cellAt(segmentStart);
            int endCell = layout.cellAt(segmentEnd);
            int cellWidth = endCell - startCell;
            int visualCell = rtl
                    ? visualStartCell + logicalEndCell - endCell
                    : visualStartCell + startCell - logicalStartCell;
            return new BidiSegment(segmentStart, segmentEnd, visualCell, cellWidth);
        }
    }

    private static final class BidiSegment {
        final int start;
        final int end;
        final int visualCell;
        final int cellWidth;

        BidiSegment(int start, int end, int visualCell, int cellWidth) {
            this.start = start;
            this.end = end;
            this.visualCell = visualCell;
            this.cellWidth = cellWidth;
        }
    }

    public int getCharacterHeight() {
        return mCharHeight;
    }

    public float getCharacterWidth() {
        return mCharWidth;
    }

    public int getTopMargin() {
        return mCharDescent;
    }
}
