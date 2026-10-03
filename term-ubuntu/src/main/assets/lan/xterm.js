/*!
 * xterm.js 6.0.0 and @xterm/addon-fit 0.11.0
 * (https://github.com/xtermjs/xterm.js, tag 6.0.0) and
 * bidi-js 1.0.3 (https://github.com/lojjic/bidi-js),
 * bundled unminified from their sources by term-ubuntu/lan-web/build.mjs
 * with esbuild 0.28.2. MIT licensed: the xterm.js authors,
 * SourceLair, Christopher Jeffrey, Microsoft Corporation (src/vs) and
 * Jason Johnston (bidi-js). Full notices in licenses.txt.
 */
var ThothXterm = (() => {
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __export = (target, all) => {
    for (var name in all)
      __defProp(target, name, { get: all[name], enumerable: true });
  };
  var __copyProps = (to, from, except, desc) => {
    if (from && typeof from === "object" || typeof from === "function") {
      for (let key of __getOwnPropNames(from))
        if (!__hasOwnProp.call(to, key) && key !== except)
          __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
    }
    return to;
  };
  var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);
  var __decorateClass = (decorators, target, key, kind) => {
    var result = kind > 1 ? void 0 : kind ? __getOwnPropDesc(target, key) : target;
    for (var i2 = decorators.length - 1, decorator; i2 >= 0; i2--)
      if (decorator = decorators[i2])
        result = (kind ? decorator(target, key, result) : decorator(result)) || result;
    if (kind && result) __defProp(target, key, result);
    return result;
  };
  var __decorateParam = (index, decorator) => (target, key) => decorator(target, key, index);

  // entry.ts
  var entry_exports = {};
  __export(entry_exports, {
    Bidi: () => src_exports,
    FitAddon: () => FitAddon,
    Terminal: () => Terminal
  });

  // node_modules/@xterm/xterm/src/browser/LocalizableStrings.ts
  var promptLabelInternal = "Terminal input";
  var promptLabel = {
    get: () => promptLabelInternal,
    set: (value) => promptLabelInternal = value
  };
  var tooMuchOutputInternal = "Too much output to announce, navigate to rows manually to read";
  var tooMuchOutput = {
    get: () => tooMuchOutputInternal,
    set: (value) => tooMuchOutputInternal = value
  };

  // node_modules/@xterm/xterm/src/browser/Clipboard.ts
  function prepareTextForTerminal(text) {
    return text.replace(/\r?\n/g, "\r");
  }
  function bracketTextForPaste(text, bracketedPasteMode) {
    if (bracketedPasteMode) {
      return "\x1B[200~" + text + "\x1B[201~";
    }
    return text;
  }
  function copyHandler(ev, selectionService) {
    if (ev.clipboardData) {
      ev.clipboardData.setData("text/plain", selectionService.selectionText);
    }
    ev.preventDefault();
  }
  function handlePasteEvent(ev, textarea, coreService, optionsService) {
    ev.stopPropagation();
    if (ev.clipboardData) {
      const text = ev.clipboardData.getData("text/plain");
      paste(text, textarea, coreService, optionsService);
    }
  }
  function paste(text, textarea, coreService, optionsService) {
    text = prepareTextForTerminal(text);
    text = bracketTextForPaste(text, coreService.decPrivateModes.bracketedPasteMode && optionsService.rawOptions.ignoreBracketedPasteMode !== true);
    coreService.triggerDataEvent(text, true);
    textarea.value = "";
  }
  function moveTextAreaUnderMouseCursor(ev, textarea, screenElement) {
    const pos = screenElement.getBoundingClientRect();
    const left = ev.clientX - pos.left - 10;
    const top = ev.clientY - pos.top - 10;
    textarea.style.width = "20px";
    textarea.style.height = "20px";
    textarea.style.left = `${left}px`;
    textarea.style.top = `${top}px`;
    textarea.style.zIndex = "1000";
    textarea.focus();
  }
  function rightClickHandler(ev, textarea, screenElement, selectionService, shouldSelectWord) {
    moveTextAreaUnderMouseCursor(ev, textarea, screenElement);
    if (shouldSelectWord) {
      selectionService.rightClickSelect(ev);
    }
    textarea.value = selectionService.selectionText;
    textarea.select();
  }

  // node_modules/@xterm/xterm/src/common/input/TextDecoder.ts
  function stringFromCodePoint(codePoint) {
    if (codePoint > 65535) {
      codePoint -= 65536;
      return String.fromCharCode((codePoint >> 10) + 55296) + String.fromCharCode(codePoint % 1024 + 56320);
    }
    return String.fromCharCode(codePoint);
  }
  function utf32ToString(data, start = 0, end = data.length) {
    let result = "";
    for (let i2 = start; i2 < end; ++i2) {
      let codepoint = data[i2];
      if (codepoint > 65535) {
        codepoint -= 65536;
        result += String.fromCharCode((codepoint >> 10) + 55296) + String.fromCharCode(codepoint % 1024 + 56320);
      } else {
        result += String.fromCharCode(codepoint);
      }
    }
    return result;
  }
  var StringToUtf32 = class {
    constructor() {
      this._interim = 0;
    }
    /**
     * Clears interim and resets decoder to clean state.
     */
    clear() {
      this._interim = 0;
    }
    /**
     * Decode JS string to UTF32 codepoints.
     * The methods assumes stream input and will store partly transmitted
     * surrogate pairs and decode them with the next data chunk.
     * Note: The method does no bound checks for target, therefore make sure
     * the provided input data does not exceed the size of `target`.
     * Returns the number of written codepoints in `target`.
     */
    decode(input, target) {
      const length = input.length;
      if (!length) {
        return 0;
      }
      let size = 0;
      let startPos = 0;
      if (this._interim) {
        const second = input.charCodeAt(startPos++);
        if (56320 <= second && second <= 57343) {
          target[size++] = (this._interim - 55296) * 1024 + second - 56320 + 65536;
        } else {
          target[size++] = this._interim;
          target[size++] = second;
        }
        this._interim = 0;
      }
      for (let i2 = startPos; i2 < length; ++i2) {
        const code = input.charCodeAt(i2);
        if (55296 <= code && code <= 56319) {
          if (++i2 >= length) {
            this._interim = code;
            return size;
          }
          const second = input.charCodeAt(i2);
          if (56320 <= second && second <= 57343) {
            target[size++] = (code - 55296) * 1024 + second - 56320 + 65536;
          } else {
            target[size++] = code;
            target[size++] = second;
          }
          continue;
        }
        if (code === 65279) {
          continue;
        }
        target[size++] = code;
      }
      return size;
    }
  };
  var Utf8ToUtf32 = class {
    constructor() {
      this.interim = new Uint8Array(3);
    }
    /**
     * Clears interim bytes and resets decoder to clean state.
     */
    clear() {
      this.interim.fill(0);
    }
    /**
     * Decodes UTF8 byte sequences in `input` to UTF32 codepoints in `target`.
     * The methods assumes stream input and will store partly transmitted bytes
     * and decode them with the next data chunk.
     * Note: The method does no bound checks for target, therefore make sure
     * the provided data chunk does not exceed the size of `target`.
     * Returns the number of written codepoints in `target`.
     */
    decode(input, target) {
      const length = input.length;
      if (!length) {
        return 0;
      }
      let size = 0;
      let byte1;
      let byte2;
      let byte3;
      let byte4;
      let codepoint = 0;
      let startPos = 0;
      if (this.interim[0]) {
        let discardInterim = false;
        let cp = this.interim[0];
        cp &= (cp & 224) === 192 ? 31 : (cp & 240) === 224 ? 15 : 7;
        let pos = 0;
        let tmp;
        while ((tmp = this.interim[++pos] & 63) && pos < 4) {
          cp <<= 6;
          cp |= tmp;
        }
        const type = (this.interim[0] & 224) === 192 ? 2 : (this.interim[0] & 240) === 224 ? 3 : 4;
        const missing = type - pos;
        while (startPos < missing) {
          if (startPos >= length) {
            return 0;
          }
          tmp = input[startPos++];
          if ((tmp & 192) !== 128) {
            startPos--;
            discardInterim = true;
            break;
          } else {
            this.interim[pos++] = tmp;
            cp <<= 6;
            cp |= tmp & 63;
          }
        }
        if (!discardInterim) {
          if (type === 2) {
            if (cp < 128) {
              startPos--;
            } else {
              target[size++] = cp;
            }
          } else if (type === 3) {
            if (cp < 2048 || cp >= 55296 && cp <= 57343 || cp === 65279) {
            } else {
              target[size++] = cp;
            }
          } else {
            if (cp < 65536 || cp > 1114111) {
            } else {
              target[size++] = cp;
            }
          }
        }
        this.interim.fill(0);
      }
      const fourStop = length - 4;
      let i2 = startPos;
      while (i2 < length) {
        while (i2 < fourStop && !((byte1 = input[i2]) & 128) && !((byte2 = input[i2 + 1]) & 128) && !((byte3 = input[i2 + 2]) & 128) && !((byte4 = input[i2 + 3]) & 128)) {
          target[size++] = byte1;
          target[size++] = byte2;
          target[size++] = byte3;
          target[size++] = byte4;
          i2 += 4;
        }
        byte1 = input[i2++];
        if (byte1 < 128) {
          target[size++] = byte1;
        } else if ((byte1 & 224) === 192) {
          if (i2 >= length) {
            this.interim[0] = byte1;
            return size;
          }
          byte2 = input[i2++];
          if ((byte2 & 192) !== 128) {
            i2--;
            continue;
          }
          codepoint = (byte1 & 31) << 6 | byte2 & 63;
          if (codepoint < 128) {
            i2--;
            continue;
          }
          target[size++] = codepoint;
        } else if ((byte1 & 240) === 224) {
          if (i2 >= length) {
            this.interim[0] = byte1;
            return size;
          }
          byte2 = input[i2++];
          if ((byte2 & 192) !== 128) {
            i2--;
            continue;
          }
          if (i2 >= length) {
            this.interim[0] = byte1;
            this.interim[1] = byte2;
            return size;
          }
          byte3 = input[i2++];
          if ((byte3 & 192) !== 128) {
            i2--;
            continue;
          }
          codepoint = (byte1 & 15) << 12 | (byte2 & 63) << 6 | byte3 & 63;
          if (codepoint < 2048 || codepoint >= 55296 && codepoint <= 57343 || codepoint === 65279) {
            continue;
          }
          target[size++] = codepoint;
        } else if ((byte1 & 248) === 240) {
          if (i2 >= length) {
            this.interim[0] = byte1;
            return size;
          }
          byte2 = input[i2++];
          if ((byte2 & 192) !== 128) {
            i2--;
            continue;
          }
          if (i2 >= length) {
            this.interim[0] = byte1;
            this.interim[1] = byte2;
            return size;
          }
          byte3 = input[i2++];
          if ((byte3 & 192) !== 128) {
            i2--;
            continue;
          }
          if (i2 >= length) {
            this.interim[0] = byte1;
            this.interim[1] = byte2;
            this.interim[2] = byte3;
            return size;
          }
          byte4 = input[i2++];
          if ((byte4 & 192) !== 128) {
            i2--;
            continue;
          }
          codepoint = (byte1 & 7) << 18 | (byte2 & 63) << 12 | (byte3 & 63) << 6 | byte4 & 63;
          if (codepoint < 65536 || codepoint > 1114111) {
            continue;
          }
          target[size++] = codepoint;
        } else {
        }
      }
      return size;
    }
  };

  // node_modules/@xterm/xterm/src/common/buffer/Constants.ts
  var DEFAULT_COLOR = 0;
  var DEFAULT_ATTR = 0 << 18 | DEFAULT_COLOR << 9 | 256 << 0;
  var CHAR_DATA_ATTR_INDEX = 0;
  var CHAR_DATA_CHAR_INDEX = 1;
  var CHAR_DATA_WIDTH_INDEX = 2;
  var CHAR_DATA_CODE_INDEX = 3;
  var NULL_CELL_CHAR = "";
  var NULL_CELL_WIDTH = 1;
  var NULL_CELL_CODE = 0;
  var WHITESPACE_CELL_CHAR = " ";
  var WHITESPACE_CELL_WIDTH = 1;
  var WHITESPACE_CELL_CODE = 32;

  // node_modules/@xterm/xterm/src/common/buffer/AttributeData.ts
  var AttributeData = class _AttributeData {
    constructor() {
      // data
      this.fg = 0;
      this.bg = 0;
      this.extended = new ExtendedAttrs();
    }
    static toColorRGB(value) {
      return [
        value >>> 16 /* RED_SHIFT */ & 255,
        value >>> 8 /* GREEN_SHIFT */ & 255,
        value & 255
      ];
    }
    static fromColorRGB(value) {
      return (value[0] & 255) << 16 /* RED_SHIFT */ | (value[1] & 255) << 8 /* GREEN_SHIFT */ | value[2] & 255;
    }
    clone() {
      const newObj = new _AttributeData();
      newObj.fg = this.fg;
      newObj.bg = this.bg;
      newObj.extended = this.extended.clone();
      return newObj;
    }
    // flags
    isInverse() {
      return this.fg & 67108864 /* INVERSE */;
    }
    isBold() {
      return this.fg & 134217728 /* BOLD */;
    }
    isUnderline() {
      if (this.hasExtendedAttrs() && this.extended.underlineStyle !== 0 /* NONE */) {
        return 1;
      }
      return this.fg & 268435456 /* UNDERLINE */;
    }
    isBlink() {
      return this.fg & 536870912 /* BLINK */;
    }
    isInvisible() {
      return this.fg & 1073741824 /* INVISIBLE */;
    }
    isItalic() {
      return this.bg & 67108864 /* ITALIC */;
    }
    isDim() {
      return this.bg & 134217728 /* DIM */;
    }
    isStrikethrough() {
      return this.fg & 2147483648 /* STRIKETHROUGH */;
    }
    isProtected() {
      return this.bg & 536870912 /* PROTECTED */;
    }
    isOverline() {
      return this.bg & 1073741824 /* OVERLINE */;
    }
    // color modes
    getFgColorMode() {
      return this.fg & 50331648 /* CM_MASK */;
    }
    getBgColorMode() {
      return this.bg & 50331648 /* CM_MASK */;
    }
    isFgRGB() {
      return (this.fg & 50331648 /* CM_MASK */) === 50331648 /* CM_RGB */;
    }
    isBgRGB() {
      return (this.bg & 50331648 /* CM_MASK */) === 50331648 /* CM_RGB */;
    }
    isFgPalette() {
      return (this.fg & 50331648 /* CM_MASK */) === 16777216 /* CM_P16 */ || (this.fg & 50331648 /* CM_MASK */) === 33554432 /* CM_P256 */;
    }
    isBgPalette() {
      return (this.bg & 50331648 /* CM_MASK */) === 16777216 /* CM_P16 */ || (this.bg & 50331648 /* CM_MASK */) === 33554432 /* CM_P256 */;
    }
    isFgDefault() {
      return (this.fg & 50331648 /* CM_MASK */) === 0;
    }
    isBgDefault() {
      return (this.bg & 50331648 /* CM_MASK */) === 0;
    }
    isAttributeDefault() {
      return this.fg === 0 && this.bg === 0;
    }
    // colors
    getFgColor() {
      switch (this.fg & 50331648 /* CM_MASK */) {
        case 16777216 /* CM_P16 */:
        case 33554432 /* CM_P256 */:
          return this.fg & 255 /* PCOLOR_MASK */;
        case 50331648 /* CM_RGB */:
          return this.fg & 16777215 /* RGB_MASK */;
        default:
          return -1;
      }
    }
    getBgColor() {
      switch (this.bg & 50331648 /* CM_MASK */) {
        case 16777216 /* CM_P16 */:
        case 33554432 /* CM_P256 */:
          return this.bg & 255 /* PCOLOR_MASK */;
        case 50331648 /* CM_RGB */:
          return this.bg & 16777215 /* RGB_MASK */;
        default:
          return -1;
      }
    }
    // extended attrs
    hasExtendedAttrs() {
      return this.bg & 268435456 /* HAS_EXTENDED */;
    }
    updateExtended() {
      if (this.extended.isEmpty()) {
        this.bg &= ~268435456 /* HAS_EXTENDED */;
      } else {
        this.bg |= 268435456 /* HAS_EXTENDED */;
      }
    }
    getUnderlineColor() {
      if (this.bg & 268435456 /* HAS_EXTENDED */ && ~this.extended.underlineColor) {
        switch (this.extended.underlineColor & 50331648 /* CM_MASK */) {
          case 16777216 /* CM_P16 */:
          case 33554432 /* CM_P256 */:
            return this.extended.underlineColor & 255 /* PCOLOR_MASK */;
          case 50331648 /* CM_RGB */:
            return this.extended.underlineColor & 16777215 /* RGB_MASK */;
          default:
            return this.getFgColor();
        }
      }
      return this.getFgColor();
    }
    getUnderlineColorMode() {
      return this.bg & 268435456 /* HAS_EXTENDED */ && ~this.extended.underlineColor ? this.extended.underlineColor & 50331648 /* CM_MASK */ : this.getFgColorMode();
    }
    isUnderlineColorRGB() {
      return this.bg & 268435456 /* HAS_EXTENDED */ && ~this.extended.underlineColor ? (this.extended.underlineColor & 50331648 /* CM_MASK */) === 50331648 /* CM_RGB */ : this.isFgRGB();
    }
    isUnderlineColorPalette() {
      return this.bg & 268435456 /* HAS_EXTENDED */ && ~this.extended.underlineColor ? (this.extended.underlineColor & 50331648 /* CM_MASK */) === 16777216 /* CM_P16 */ || (this.extended.underlineColor & 50331648 /* CM_MASK */) === 33554432 /* CM_P256 */ : this.isFgPalette();
    }
    isUnderlineColorDefault() {
      return this.bg & 268435456 /* HAS_EXTENDED */ && ~this.extended.underlineColor ? (this.extended.underlineColor & 50331648 /* CM_MASK */) === 0 : this.isFgDefault();
    }
    getUnderlineStyle() {
      return this.fg & 268435456 /* UNDERLINE */ ? this.bg & 268435456 /* HAS_EXTENDED */ ? this.extended.underlineStyle : 1 /* SINGLE */ : 0 /* NONE */;
    }
    getUnderlineVariantOffset() {
      return this.extended.underlineVariantOffset;
    }
  };
  var ExtendedAttrs = class _ExtendedAttrs {
    constructor(ext = 0, urlId = 0) {
      this._ext = 0;
      this._urlId = 0;
      this._ext = ext;
      this._urlId = urlId;
    }
    get ext() {
      if (this._urlId) {
        return this._ext & ~469762048 /* UNDERLINE_STYLE */ | this.underlineStyle << 26;
      }
      return this._ext;
    }
    set ext(value) {
      this._ext = value;
    }
    get underlineStyle() {
      if (this._urlId) {
        return 5 /* DASHED */;
      }
      return (this._ext & 469762048 /* UNDERLINE_STYLE */) >> 26;
    }
    set underlineStyle(value) {
      this._ext &= ~469762048 /* UNDERLINE_STYLE */;
      this._ext |= value << 26 & 469762048 /* UNDERLINE_STYLE */;
    }
    get underlineColor() {
      return this._ext & (50331648 /* CM_MASK */ | 16777215 /* RGB_MASK */);
    }
    set underlineColor(value) {
      this._ext &= ~(50331648 /* CM_MASK */ | 16777215 /* RGB_MASK */);
      this._ext |= value & (50331648 /* CM_MASK */ | 16777215 /* RGB_MASK */);
    }
    get urlId() {
      return this._urlId;
    }
    set urlId(value) {
      this._urlId = value;
    }
    get underlineVariantOffset() {
      const val = (this._ext & 3758096384 /* VARIANT_OFFSET */) >> 29;
      if (val < 0) {
        return val ^ 4294967288;
      }
      return val;
    }
    set underlineVariantOffset(value) {
      this._ext &= ~3758096384 /* VARIANT_OFFSET */;
      this._ext |= value << 29 & 3758096384 /* VARIANT_OFFSET */;
    }
    clone() {
      return new _ExtendedAttrs(this._ext, this._urlId);
    }
    /**
     * Convenient method to indicate whether the object holds no additional information,
     * that needs to be persistant in the buffer.
     */
    isEmpty() {
      return this.underlineStyle === 0 /* NONE */ && this._urlId === 0;
    }
  };

  // node_modules/@xterm/xterm/src/common/buffer/CellData.ts
  var CellData = class _CellData extends AttributeData {
    constructor() {
      super(...arguments);
      /** Primitives from terminal buffer. */
      this.content = 0;
      this.fg = 0;
      this.bg = 0;
      this.extended = new ExtendedAttrs();
      this.combinedData = "";
    }
    /** Helper to create CellData from CharData. */
    static fromCharData(value) {
      const obj = new _CellData();
      obj.setFromCharData(value);
      return obj;
    }
    /** Whether cell contains a combined string. */
    isCombined() {
      return this.content & 2097152 /* IS_COMBINED_MASK */;
    }
    /** Width of the cell. */
    getWidth() {
      return this.content >> 22 /* WIDTH_SHIFT */;
    }
    /** JS string of the content. */
    getChars() {
      if (this.content & 2097152 /* IS_COMBINED_MASK */) {
        return this.combinedData;
      }
      if (this.content & 2097151 /* CODEPOINT_MASK */) {
        return stringFromCodePoint(this.content & 2097151 /* CODEPOINT_MASK */);
      }
      return "";
    }
    /**
     * Codepoint of cell
     * Note this returns the UTF32 codepoint of single chars,
     * if content is a combined string it returns the codepoint
     * of the last char in string to be in line with code in CharData.
     */
    getCode() {
      return this.isCombined() ? this.combinedData.charCodeAt(this.combinedData.length - 1) : this.content & 2097151 /* CODEPOINT_MASK */;
    }
    /** Set data from CharData */
    setFromCharData(value) {
      this.fg = value[CHAR_DATA_ATTR_INDEX];
      this.bg = 0;
      let combined = false;
      if (value[CHAR_DATA_CHAR_INDEX].length > 2) {
        combined = true;
      } else if (value[CHAR_DATA_CHAR_INDEX].length === 2) {
        const code = value[CHAR_DATA_CHAR_INDEX].charCodeAt(0);
        if (55296 <= code && code <= 56319) {
          const second = value[CHAR_DATA_CHAR_INDEX].charCodeAt(1);
          if (56320 <= second && second <= 57343) {
            this.content = (code - 55296) * 1024 + second - 56320 + 65536 | value[CHAR_DATA_WIDTH_INDEX] << 22 /* WIDTH_SHIFT */;
          } else {
            combined = true;
          }
        } else {
          combined = true;
        }
      } else {
        this.content = value[CHAR_DATA_CHAR_INDEX].charCodeAt(0) | value[CHAR_DATA_WIDTH_INDEX] << 22 /* WIDTH_SHIFT */;
      }
      if (combined) {
        this.combinedData = value[CHAR_DATA_CHAR_INDEX];
        this.content = 2097152 /* IS_COMBINED_MASK */ | value[CHAR_DATA_WIDTH_INDEX] << 22 /* WIDTH_SHIFT */;
      }
    }
    /** Get data as CharData. */
    getAsCharData() {
      return [this.fg, this.getChars(), this.getWidth(), this.getCode()];
    }
  };

  // node_modules/@xterm/xterm/src/common/services/ServiceRegistry.ts
  var DI_TARGET = "di$target";
  var DI_DEPENDENCIES = "di$dependencies";
  var serviceRegistry = /* @__PURE__ */ new Map();
  function getServiceDependencies(ctor) {
    return ctor[DI_DEPENDENCIES] || [];
  }
  function createDecorator(id2) {
    if (serviceRegistry.has(id2)) {
      return serviceRegistry.get(id2);
    }
    const decorator = function(target, key, index) {
      if (arguments.length !== 3) {
        throw new Error("@IServiceName-decorator can only be used to decorate a parameter");
      }
      storeServiceDependency(decorator, target, index);
    };
    decorator._id = id2;
    serviceRegistry.set(id2, decorator);
    return decorator;
  }
  function storeServiceDependency(id2, target, index) {
    if (target[DI_TARGET] === target) {
      target[DI_DEPENDENCIES].push({ id: id2, index });
    } else {
      target[DI_DEPENDENCIES] = [{ id: id2, index }];
      target[DI_TARGET] = target;
    }
  }

  // node_modules/@xterm/xterm/src/common/services/Services.ts
  var IBufferService = createDecorator("BufferService");
  var ICoreMouseService = createDecorator("CoreMouseService");
  var ICoreService = createDecorator("CoreService");
  var ICharsetService = createDecorator("CharsetService");
  var IInstantiationService = createDecorator("InstantiationService");
  var ILogService = createDecorator("LogService");
  var IOptionsService = createDecorator("OptionsService");
  var IOscLinkService = createDecorator("OscLinkService");
  var IUnicodeService = createDecorator("UnicodeService");
  var IDecorationService = createDecorator("DecorationService");

  // node_modules/@xterm/xterm/src/browser/OscLinkProvider.ts
  var OscLinkProvider = class {
    constructor(_bufferService, _optionsService, _oscLinkService) {
      this._bufferService = _bufferService;
      this._optionsService = _optionsService;
      this._oscLinkService = _oscLinkService;
    }
    provideLinks(y, callback) {
      const line = this._bufferService.buffer.lines.get(y - 1);
      if (!line) {
        callback(void 0);
        return;
      }
      const result = [];
      const linkHandler = this._optionsService.rawOptions.linkHandler;
      const cell = new CellData();
      const lineLength = line.getTrimmedLength();
      let currentLinkId = -1;
      let currentStart = -1;
      let finishLink = false;
      for (let x = 0; x < lineLength; x++) {
        if (currentStart === -1 && !line.hasContent(x)) {
          continue;
        }
        line.loadCell(x, cell);
        if (cell.hasExtendedAttrs() && cell.extended.urlId) {
          if (currentStart === -1) {
            currentStart = x;
            currentLinkId = cell.extended.urlId;
            continue;
          } else {
            finishLink = cell.extended.urlId !== currentLinkId;
          }
        } else {
          if (currentStart !== -1) {
            finishLink = true;
          }
        }
        if (finishLink || currentStart !== -1 && x === lineLength - 1) {
          const text = this._oscLinkService.getLinkData(currentLinkId)?.uri;
          if (text) {
            const range = {
              start: {
                x: currentStart + 1,
                y
              },
              end: {
                // Offset end x if it's a link that ends on the last cell in the line
                x: x + (!finishLink && x === lineLength - 1 ? 1 : 0),
                y
              }
            };
            let ignoreLink = false;
            if (!linkHandler?.allowNonHttpProtocols) {
              try {
                const parsed = new URL(text);
                if (!["http:", "https:"].includes(parsed.protocol)) {
                  ignoreLink = true;
                }
              } catch (e) {
                ignoreLink = true;
              }
            }
            if (!ignoreLink) {
              result.push({
                text,
                range,
                activate: (e, text2) => linkHandler ? linkHandler.activate(e, text2, range) : defaultActivate(e, text2),
                hover: (e, text2) => linkHandler?.hover?.(e, text2, range),
                leave: (e, text2) => linkHandler?.leave?.(e, text2, range)
              });
            }
          }
          finishLink = false;
          if (cell.hasExtendedAttrs() && cell.extended.urlId) {
            currentStart = x;
            currentLinkId = cell.extended.urlId;
          } else {
            currentStart = -1;
            currentLinkId = -1;
          }
        }
      }
      callback(result);
    }
  };
  OscLinkProvider = __decorateClass([
    __decorateParam(0, IBufferService),
    __decorateParam(1, IOptionsService),
    __decorateParam(2, IOscLinkService)
  ], OscLinkProvider);
  function defaultActivate(e, uri) {
    const answer = confirm(`Do you want to navigate to ${uri}?

WARNING: This link could potentially be dangerous`);
    if (answer) {
      const newWindow = window.open();
      if (newWindow) {
        try {
          newWindow.opener = null;
        } catch {
        }
        newWindow.location.href = uri;
      } else {
        console.warn("Opening link blocked as opener could not be cleared");
      }
    }
  }

  // node_modules/@xterm/xterm/src/browser/services/Services.ts
  var ICharSizeService = createDecorator("CharSizeService");
  var ICoreBrowserService = createDecorator("CoreBrowserService");
  var IMouseService = createDecorator("MouseService");
  var IRenderService = createDecorator("RenderService");
  var ISelectionService = createDecorator("SelectionService");
  var ICharacterJoinerService = createDecorator("CharacterJoinerService");
  var IThemeService = createDecorator("ThemeService");
  var ILinkProviderService = createDecorator("LinkProviderService");

  // node_modules/@xterm/xterm/src/vs/base/common/errors.ts
  var ErrorHandler = class {
    constructor() {
      this.listeners = [];
      this.unexpectedErrorHandler = function(e) {
        setTimeout(() => {
          if (e.stack) {
            if (ErrorNoTelemetry.isErrorNoTelemetry(e)) {
              throw new ErrorNoTelemetry(e.message + "\n\n" + e.stack);
            }
            throw new Error(e.message + "\n\n" + e.stack);
          }
          throw e;
        }, 0);
      };
    }
    addListener(listener) {
      this.listeners.push(listener);
      return () => {
        this._removeListener(listener);
      };
    }
    emit(e) {
      this.listeners.forEach((listener) => {
        listener(e);
      });
    }
    _removeListener(listener) {
      this.listeners.splice(this.listeners.indexOf(listener), 1);
    }
    setUnexpectedErrorHandler(newUnexpectedErrorHandler) {
      this.unexpectedErrorHandler = newUnexpectedErrorHandler;
    }
    getUnexpectedErrorHandler() {
      return this.unexpectedErrorHandler;
    }
    onUnexpectedError(e) {
      this.unexpectedErrorHandler(e);
      this.emit(e);
    }
    // For external errors, we don't want the listeners to be called
    onUnexpectedExternalError(e) {
      this.unexpectedErrorHandler(e);
    }
  };
  var errorHandler = new ErrorHandler();
  function onUnexpectedError(e) {
    if (!isCancellationError(e)) {
      errorHandler.onUnexpectedError(e);
    }
    return void 0;
  }
  var canceledName = "Canceled";
  function isCancellationError(error) {
    if (error instanceof CancellationError) {
      return true;
    }
    return error instanceof Error && error.name === canceledName && error.message === canceledName;
  }
  var CancellationError = class extends Error {
    constructor() {
      super(canceledName);
      this.name = this.message;
    }
  };
  function illegalArgument(name) {
    if (name) {
      return new Error(`Illegal argument: ${name}`);
    } else {
      return new Error("Illegal argument");
    }
  }
  var ErrorNoTelemetry = class _ErrorNoTelemetry extends Error {
    constructor(msg) {
      super(msg);
      this.name = "CodeExpectedError";
    }
    static fromError(err) {
      if (err instanceof _ErrorNoTelemetry) {
        return err;
      }
      const result = new _ErrorNoTelemetry();
      result.message = err.message;
      result.stack = err.stack;
      return result;
    }
    static isErrorNoTelemetry(err) {
      return err.name === "CodeExpectedError";
    }
  };
  var BugIndicatingError = class _BugIndicatingError extends Error {
    constructor(message) {
      super(message || "An unexpected bug occurred.");
      Object.setPrototypeOf(this, _BugIndicatingError.prototype);
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/common/arraysFind.ts
  function findLastIdxMonotonous(array, predicate, startIdx = 0, endIdxEx = array.length) {
    let i2 = startIdx;
    let j = endIdxEx;
    while (i2 < j) {
      const k = Math.floor((i2 + j) / 2);
      if (predicate(array[k])) {
        i2 = k + 1;
      } else {
        j = k;
      }
    }
    return i2 - 1;
  }
  var _MonotonousArray = class _MonotonousArray {
    constructor(_array) {
      this._array = _array;
      this._findLastMonotonousLastIdx = 0;
    }
    /**
     * The predicate must be monotonous, i.e. `arr.map(predicate)` must be like `[true, ..., true, false, ..., false]`!
     * For subsequent calls, current predicate must be weaker than (or equal to) the previous predicate, i.e. more entries must be `true`.
     */
    findLastMonotonous(predicate) {
      if (_MonotonousArray.assertInvariants) {
        if (this._prevFindLastPredicate) {
          for (const item of this._array) {
            if (this._prevFindLastPredicate(item) && !predicate(item)) {
              throw new Error("MonotonousArray: current predicate must be weaker than (or equal to) the previous predicate.");
            }
          }
        }
        this._prevFindLastPredicate = predicate;
      }
      const idx = findLastIdxMonotonous(this._array, predicate, this._findLastMonotonousLastIdx);
      this._findLastMonotonousLastIdx = idx + 1;
      return idx === -1 ? void 0 : this._array[idx];
    }
  };
  _MonotonousArray.assertInvariants = false;
  var MonotonousArray = _MonotonousArray;

  // node_modules/@xterm/xterm/src/vs/base/common/arrays.ts
  function tail(array, n = 0) {
    return array[array.length - (1 + n)];
  }
  var CompareResult;
  ((CompareResult2) => {
    function isLessThan(result) {
      return result < 0;
    }
    CompareResult2.isLessThan = isLessThan;
    function isLessThanOrEqual(result) {
      return result <= 0;
    }
    CompareResult2.isLessThanOrEqual = isLessThanOrEqual;
    function isGreaterThan(result) {
      return result > 0;
    }
    CompareResult2.isGreaterThan = isGreaterThan;
    function isNeitherLessOrGreaterThan(result) {
      return result === 0;
    }
    CompareResult2.isNeitherLessOrGreaterThan = isNeitherLessOrGreaterThan;
    CompareResult2.greaterThan = 1;
    CompareResult2.lessThan = -1;
    CompareResult2.neitherLessOrGreaterThan = 0;
  })(CompareResult || (CompareResult = {}));
  function compareBy(selector, comparator) {
    return (a, b) => comparator(selector(a), selector(b));
  }
  var numberComparator = (a, b) => a - b;
  var _CallbackIterable = class _CallbackIterable {
    constructor(iterate) {
      this.iterate = iterate;
    }
    forEach(handler) {
      this.iterate((item) => {
        handler(item);
        return true;
      });
    }
    toArray() {
      const result = [];
      this.iterate((item) => {
        result.push(item);
        return true;
      });
      return result;
    }
    filter(predicate) {
      return new _CallbackIterable((cb) => this.iterate((item) => predicate(item) ? cb(item) : true));
    }
    map(mapFn) {
      return new _CallbackIterable((cb) => this.iterate((item) => cb(mapFn(item))));
    }
    some(predicate) {
      let result = false;
      this.iterate((item) => {
        result = predicate(item);
        return !result;
      });
      return result;
    }
    findFirst(predicate) {
      let result;
      this.iterate((item) => {
        if (predicate(item)) {
          result = item;
          return false;
        }
        return true;
      });
      return result;
    }
    findLast(predicate) {
      let result;
      this.iterate((item) => {
        if (predicate(item)) {
          result = item;
        }
        return true;
      });
      return result;
    }
    findLastMaxBy(comparator) {
      let result;
      let first = true;
      this.iterate((item) => {
        if (first || CompareResult.isGreaterThan(comparator(item, result))) {
          first = false;
          result = item;
        }
        return true;
      });
      return result;
    }
  };
  _CallbackIterable.empty = new _CallbackIterable((_callback) => {
  });
  var CallbackIterable = _CallbackIterable;

  // node_modules/@xterm/xterm/src/vs/base/common/collections.ts
  function groupBy(data, groupFn) {
    const result = /* @__PURE__ */ Object.create(null);
    for (const element of data) {
      const key = groupFn(element);
      let target = result[key];
      if (!target) {
        target = result[key] = [];
      }
      target.push(element);
    }
    return result;
  }
  var _a, _b;
  var SetWithKey = class {
    constructor(values, toKey) {
      this.toKey = toKey;
      this._map = /* @__PURE__ */ new Map();
      this[_a] = "SetWithKey";
      for (const value of values) {
        this.add(value);
      }
    }
    get size() {
      return this._map.size;
    }
    add(value) {
      const key = this.toKey(value);
      this._map.set(key, value);
      return this;
    }
    delete(value) {
      return this._map.delete(this.toKey(value));
    }
    has(value) {
      return this._map.has(this.toKey(value));
    }
    *entries() {
      for (const entry of this._map.values()) {
        yield [entry, entry];
      }
    }
    keys() {
      return this.values();
    }
    *values() {
      for (const entry of this._map.values()) {
        yield entry;
      }
    }
    clear() {
      this._map.clear();
    }
    forEach(callbackfn, thisArg) {
      this._map.forEach((entry) => callbackfn.call(thisArg, entry, entry, this));
    }
    [(_b = Symbol.iterator, _a = Symbol.toStringTag, _b)]() {
      return this.values();
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/common/map.ts
  var SetMap = class {
    constructor() {
      this.map = /* @__PURE__ */ new Map();
    }
    add(key, value) {
      let values = this.map.get(key);
      if (!values) {
        values = /* @__PURE__ */ new Set();
        this.map.set(key, values);
      }
      values.add(value);
    }
    delete(key, value) {
      const values = this.map.get(key);
      if (!values) {
        return;
      }
      values.delete(value);
      if (values.size === 0) {
        this.map.delete(key);
      }
    }
    forEach(key, fn) {
      const values = this.map.get(key);
      if (!values) {
        return;
      }
      values.forEach(fn);
    }
    get(key) {
      const values = this.map.get(key);
      if (!values) {
        return /* @__PURE__ */ new Set();
      }
      return values;
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/common/functional.ts
  function createSingleCallFunction(fn, fnDidRunCallback) {
    const _this = this;
    let didCall = false;
    let result;
    return function() {
      if (didCall) {
        return result;
      }
      didCall = true;
      if (fnDidRunCallback) {
        try {
          result = fn.apply(_this, arguments);
        } finally {
          fnDidRunCallback();
        }
      } else {
        result = fn.apply(_this, arguments);
      }
      return result;
    };
  }

  // node_modules/@xterm/xterm/src/vs/base/common/iterator.ts
  var Iterable;
  ((Iterable2) => {
    function is(thing) {
      return thing && typeof thing === "object" && typeof thing[Symbol.iterator] === "function";
    }
    Iterable2.is = is;
    const _empty = Object.freeze([]);
    function empty() {
      return _empty;
    }
    Iterable2.empty = empty;
    function* single(element) {
      yield element;
    }
    Iterable2.single = single;
    function wrap(iterableOrElement) {
      if (is(iterableOrElement)) {
        return iterableOrElement;
      } else {
        return single(iterableOrElement);
      }
    }
    Iterable2.wrap = wrap;
    function from(iterable) {
      return iterable || _empty;
    }
    Iterable2.from = from;
    function* reverse(array) {
      for (let i2 = array.length - 1; i2 >= 0; i2--) {
        yield array[i2];
      }
    }
    Iterable2.reverse = reverse;
    function isEmpty(iterable) {
      return !iterable || iterable[Symbol.iterator]().next().done === true;
    }
    Iterable2.isEmpty = isEmpty;
    function first(iterable) {
      return iterable[Symbol.iterator]().next().value;
    }
    Iterable2.first = first;
    function some(iterable, predicate) {
      let i2 = 0;
      for (const element of iterable) {
        if (predicate(element, i2++)) {
          return true;
        }
      }
      return false;
    }
    Iterable2.some = some;
    function find(iterable, predicate) {
      for (const element of iterable) {
        if (predicate(element)) {
          return element;
        }
      }
      return void 0;
    }
    Iterable2.find = find;
    function* filter(iterable, predicate) {
      for (const element of iterable) {
        if (predicate(element)) {
          yield element;
        }
      }
    }
    Iterable2.filter = filter;
    function* map2(iterable, fn) {
      let index = 0;
      for (const element of iterable) {
        yield fn(element, index++);
      }
    }
    Iterable2.map = map2;
    function* flatMap(iterable, fn) {
      let index = 0;
      for (const element of iterable) {
        yield* fn(element, index++);
      }
    }
    Iterable2.flatMap = flatMap;
    function* concat(...iterables) {
      for (const iterable of iterables) {
        yield* iterable;
      }
    }
    Iterable2.concat = concat;
    function reduce(iterable, reducer, initialValue) {
      let value = initialValue;
      for (const element of iterable) {
        value = reducer(value, element);
      }
      return value;
    }
    Iterable2.reduce = reduce;
    function* slice(arr, from2, to = arr.length) {
      if (from2 < 0) {
        from2 += arr.length;
      }
      if (to < 0) {
        to += arr.length;
      } else if (to > arr.length) {
        to = arr.length;
      }
      for (; from2 < to; from2++) {
        yield arr[from2];
      }
    }
    Iterable2.slice = slice;
    function consume(iterable, atMost = Number.POSITIVE_INFINITY) {
      const consumed = [];
      if (atMost === 0) {
        return [consumed, iterable];
      }
      const iterator = iterable[Symbol.iterator]();
      for (let i2 = 0; i2 < atMost; i2++) {
        const next = iterator.next();
        if (next.done) {
          return [consumed, Iterable2.empty()];
        }
        consumed.push(next.value);
      }
      return [consumed, { [Symbol.iterator]() {
        return iterator;
      } }];
    }
    Iterable2.consume = consume;
    async function asyncToArray(iterable) {
      const result = [];
      for await (const item of iterable) {
        result.push(item);
      }
      return Promise.resolve(result);
    }
    Iterable2.asyncToArray = asyncToArray;
  })(Iterable || (Iterable = {}));

  // node_modules/@xterm/xterm/src/vs/base/common/lifecycle.ts
  var TRACK_DISPOSABLES = false;
  var disposableTracker = null;
  var _DisposableTracker = class _DisposableTracker {
    constructor() {
      this.livingDisposables = /* @__PURE__ */ new Map();
    }
    getDisposableData(d) {
      let val = this.livingDisposables.get(d);
      if (!val) {
        val = { parent: null, source: null, isSingleton: false, value: d, idx: _DisposableTracker.idx++ };
        this.livingDisposables.set(d, val);
      }
      return val;
    }
    trackDisposable(d) {
      const data = this.getDisposableData(d);
      if (!data.source) {
        data.source = new Error().stack;
      }
    }
    setParent(child, parent) {
      const data = this.getDisposableData(child);
      data.parent = parent;
    }
    markAsDisposed(x) {
      this.livingDisposables.delete(x);
    }
    markAsSingleton(disposable) {
      this.getDisposableData(disposable).isSingleton = true;
    }
    getRootParent(data, cache) {
      const cacheValue = cache.get(data);
      if (cacheValue) {
        return cacheValue;
      }
      const result = data.parent ? this.getRootParent(this.getDisposableData(data.parent), cache) : data;
      cache.set(data, result);
      return result;
    }
    getTrackedDisposables() {
      const rootParentCache = /* @__PURE__ */ new Map();
      const leaking = [...this.livingDisposables.entries()].filter(([, v]) => v.source !== null && !this.getRootParent(v, rootParentCache).isSingleton).flatMap(([k]) => k);
      return leaking;
    }
    computeLeakingDisposables(maxReported = 10, preComputedLeaks) {
      let uncoveredLeakingObjs;
      if (preComputedLeaks) {
        uncoveredLeakingObjs = preComputedLeaks;
      } else {
        const rootParentCache = /* @__PURE__ */ new Map();
        const leakingObjects = [...this.livingDisposables.values()].filter((info) => info.source !== null && !this.getRootParent(info, rootParentCache).isSingleton);
        if (leakingObjects.length === 0) {
          return;
        }
        const leakingObjsSet = new Set(leakingObjects.map((o) => o.value));
        uncoveredLeakingObjs = leakingObjects.filter((l) => {
          return !(l.parent && leakingObjsSet.has(l.parent));
        });
        if (uncoveredLeakingObjs.length === 0) {
          throw new Error("There are cyclic diposable chains!");
        }
      }
      if (!uncoveredLeakingObjs) {
        return void 0;
      }
      function getStackTracePath(leaking) {
        function removePrefix(array, linesToRemove) {
          while (array.length > 0 && linesToRemove.some((regexp) => typeof regexp === "string" ? regexp === array[0] : array[0].match(regexp))) {
            array.shift();
          }
        }
        const lines = leaking.source.split("\n").map((p) => p.trim().replace("at ", "")).filter((l) => l !== "");
        removePrefix(lines, ["Error", /^trackDisposable \(.*\)$/, /^DisposableTracker.trackDisposable \(.*\)$/]);
        return lines.reverse();
      }
      const stackTraceStarts = new SetMap();
      for (const leaking of uncoveredLeakingObjs) {
        const stackTracePath = getStackTracePath(leaking);
        for (let i3 = 0; i3 <= stackTracePath.length; i3++) {
          stackTraceStarts.add(stackTracePath.slice(0, i3).join("\n"), leaking);
        }
      }
      uncoveredLeakingObjs.sort(compareBy((l) => l.idx, numberComparator));
      let message = "";
      let i2 = 0;
      for (const leaking of uncoveredLeakingObjs.slice(0, maxReported)) {
        i2++;
        const stackTracePath = getStackTracePath(leaking);
        const stackTraceFormattedLines = [];
        for (let i3 = 0; i3 < stackTracePath.length; i3++) {
          let line = stackTracePath[i3];
          const starts = stackTraceStarts.get(stackTracePath.slice(0, i3 + 1).join("\n"));
          line = `(shared with ${starts.size}/${uncoveredLeakingObjs.length} leaks) at ${line}`;
          const prevStarts = stackTraceStarts.get(stackTracePath.slice(0, i3).join("\n"));
          const continuations = groupBy([...prevStarts].map((d) => getStackTracePath(d)[i3]), (v) => v);
          delete continuations[stackTracePath[i3]];
          for (const [cont, set] of Object.entries(continuations)) {
            stackTraceFormattedLines.unshift(`    - stacktraces of ${set.length} other leaks continue with ${cont}`);
          }
          stackTraceFormattedLines.unshift(line);
        }
        message += `


==================== Leaking disposable ${i2}/${uncoveredLeakingObjs.length}: ${leaking.value.constructor.name} ====================
${stackTraceFormattedLines.join("\n")}
============================================================

`;
      }
      if (uncoveredLeakingObjs.length > maxReported) {
        message += `


... and ${uncoveredLeakingObjs.length - maxReported} more leaking disposables

`;
      }
      return { leaks: uncoveredLeakingObjs, details: message };
    }
  };
  _DisposableTracker.idx = 0;
  var DisposableTracker = _DisposableTracker;
  function setDisposableTracker(tracker) {
    disposableTracker = tracker;
  }
  if (TRACK_DISPOSABLES) {
    const __is_disposable_tracked__ = "__is_disposable_tracked__";
    setDisposableTracker(new class {
      trackDisposable(x) {
        const stack = new Error("Potentially leaked disposable").stack;
        setTimeout(() => {
          if (!x[__is_disposable_tracked__]) {
            console.log(stack);
          }
        }, 3e3);
      }
      setParent(child, parent) {
        if (child && child !== Disposable.None) {
          try {
            child[__is_disposable_tracked__] = true;
          } catch {
          }
        }
      }
      markAsDisposed(disposable) {
        if (disposable && disposable !== Disposable.None) {
          try {
            disposable[__is_disposable_tracked__] = true;
          } catch {
          }
        }
      }
      markAsSingleton(disposable) {
      }
    }());
  }
  function trackDisposable(x) {
    disposableTracker?.trackDisposable(x);
    return x;
  }
  function markAsDisposed(disposable) {
    disposableTracker?.markAsDisposed(disposable);
  }
  function setParentOfDisposable(child, parent) {
    disposableTracker?.setParent(child, parent);
  }
  function setParentOfDisposables(children, parent) {
    if (!disposableTracker) {
      return;
    }
    for (const child of children) {
      disposableTracker.setParent(child, parent);
    }
  }
  function markAsSingleton(singleton) {
    disposableTracker?.markAsSingleton(singleton);
    return singleton;
  }
  function dispose(arg) {
    if (Iterable.is(arg)) {
      const errors = [];
      for (const d of arg) {
        if (d) {
          try {
            d.dispose();
          } catch (e) {
            errors.push(e);
          }
        }
      }
      if (errors.length === 1) {
        throw errors[0];
      } else if (errors.length > 1) {
        throw new AggregateError(errors, "Encountered errors while disposing of store");
      }
      return Array.isArray(arg) ? [] : arg;
    } else if (arg) {
      arg.dispose();
      return arg;
    }
  }
  function combinedDisposable(...disposables) {
    const parent = toDisposable(() => dispose(disposables));
    setParentOfDisposables(disposables, parent);
    return parent;
  }
  function toDisposable(fn) {
    const self = trackDisposable({
      dispose: createSingleCallFunction(() => {
        markAsDisposed(self);
        fn();
      })
    });
    return self;
  }
  var _DisposableStore = class _DisposableStore {
    constructor() {
      this._toDispose = /* @__PURE__ */ new Set();
      this._isDisposed = false;
      trackDisposable(this);
    }
    /**
     * Dispose of all registered disposables and mark this object as disposed.
     *
     * Any future disposables added to this object will be disposed of on `add`.
     */
    dispose() {
      if (this._isDisposed) {
        return;
      }
      markAsDisposed(this);
      this._isDisposed = true;
      this.clear();
    }
    /**
     * @return `true` if this object has been disposed of.
     */
    get isDisposed() {
      return this._isDisposed;
    }
    /**
     * Dispose of all registered disposables but do not mark this object as disposed.
     */
    clear() {
      if (this._toDispose.size === 0) {
        return;
      }
      try {
        dispose(this._toDispose);
      } finally {
        this._toDispose.clear();
      }
    }
    /**
     * Add a new {@link IDisposable disposable} to the collection.
     */
    add(o) {
      if (!o) {
        return o;
      }
      if (o === this) {
        throw new Error("Cannot register a disposable on itself!");
      }
      setParentOfDisposable(o, this);
      if (this._isDisposed) {
        if (!_DisposableStore.DISABLE_DISPOSED_WARNING) {
          console.warn(new Error("Trying to add a disposable to a DisposableStore that has already been disposed of. The added object will be leaked!").stack);
        }
      } else {
        this._toDispose.add(o);
      }
      return o;
    }
    /**
     * Deletes a disposable from store and disposes of it. This will not throw or warn and proceed to dispose the
     * disposable even when the disposable is not part in the store.
     */
    delete(o) {
      if (!o) {
        return;
      }
      if (o === this) {
        throw new Error("Cannot dispose a disposable on itself!");
      }
      this._toDispose.delete(o);
      o.dispose();
    }
    /**
     * Deletes the value from the store, but does not dispose it.
     */
    deleteAndLeak(o) {
      if (!o) {
        return;
      }
      if (this._toDispose.has(o)) {
        this._toDispose.delete(o);
        setParentOfDisposable(o, null);
      }
    }
  };
  _DisposableStore.DISABLE_DISPOSED_WARNING = false;
  var DisposableStore = _DisposableStore;
  var Disposable = class {
    constructor() {
      this._store = new DisposableStore();
      trackDisposable(this);
      setParentOfDisposable(this._store, this);
    }
    dispose() {
      markAsDisposed(this);
      this._store.dispose();
    }
    /**
     * Adds `o` to the collection of disposables managed by this object.
     */
    _register(o) {
      if (o === this) {
        throw new Error("Cannot register a disposable on itself!");
      }
      return this._store.add(o);
    }
  };
  /**
   * A disposable that does nothing when it is disposed of.
   *
   * TODO: This should not be a static property.
   */
  Disposable.None = Object.freeze({ dispose() {
  } });
  var MutableDisposable = class {
    constructor() {
      this._isDisposed = false;
      trackDisposable(this);
    }
    get value() {
      return this._isDisposed ? void 0 : this._value;
    }
    set value(value) {
      if (this._isDisposed || value === this._value) {
        return;
      }
      this._value?.dispose();
      if (value) {
        setParentOfDisposable(value, this);
      }
      this._value = value;
    }
    /**
     * Resets the stored value and disposed of the previously stored value.
     */
    clear() {
      this.value = void 0;
    }
    dispose() {
      this._isDisposed = true;
      markAsDisposed(this);
      this._value?.dispose();
      this._value = void 0;
    }
    /**
     * Clears the value, but does not dispose it.
     * The old value is returned.
    */
    clearAndLeak() {
      const oldValue = this._value;
      this._value = void 0;
      if (oldValue) {
        setParentOfDisposable(oldValue, null);
      }
      return oldValue;
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/browser/window.ts
  function ensureCodeWindow(targetWindow, fallbackWindowId) {
  }
  var mainWindow = typeof window === "object" ? window : globalThis;

  // node_modules/@xterm/xterm/src/vs/base/common/linkedList.ts
  var _Node = class _Node {
    constructor(element) {
      this.element = element;
      this.next = _Node.Undefined;
      this.prev = _Node.Undefined;
    }
  };
  _Node.Undefined = new _Node(void 0);
  var Node2 = _Node;
  var LinkedList = class {
    constructor() {
      this._first = Node2.Undefined;
      this._last = Node2.Undefined;
      this._size = 0;
    }
    get size() {
      return this._size;
    }
    isEmpty() {
      return this._first === Node2.Undefined;
    }
    clear() {
      let node = this._first;
      while (node !== Node2.Undefined) {
        const next = node.next;
        node.prev = Node2.Undefined;
        node.next = Node2.Undefined;
        node = next;
      }
      this._first = Node2.Undefined;
      this._last = Node2.Undefined;
      this._size = 0;
    }
    unshift(element) {
      return this._insert(element, false);
    }
    push(element) {
      return this._insert(element, true);
    }
    _insert(element, atTheEnd) {
      const newNode = new Node2(element);
      if (this._first === Node2.Undefined) {
        this._first = newNode;
        this._last = newNode;
      } else if (atTheEnd) {
        const oldLast = this._last;
        this._last = newNode;
        newNode.prev = oldLast;
        oldLast.next = newNode;
      } else {
        const oldFirst = this._first;
        this._first = newNode;
        newNode.next = oldFirst;
        oldFirst.prev = newNode;
      }
      this._size += 1;
      let didRemove = false;
      return () => {
        if (!didRemove) {
          didRemove = true;
          this._remove(newNode);
        }
      };
    }
    shift() {
      if (this._first === Node2.Undefined) {
        return void 0;
      } else {
        const res = this._first.element;
        this._remove(this._first);
        return res;
      }
    }
    pop() {
      if (this._last === Node2.Undefined) {
        return void 0;
      } else {
        const res = this._last.element;
        this._remove(this._last);
        return res;
      }
    }
    _remove(node) {
      if (node.prev !== Node2.Undefined && node.next !== Node2.Undefined) {
        const anchor = node.prev;
        anchor.next = node.next;
        node.next.prev = anchor;
      } else if (node.prev === Node2.Undefined && node.next === Node2.Undefined) {
        this._first = Node2.Undefined;
        this._last = Node2.Undefined;
      } else if (node.next === Node2.Undefined) {
        this._last = this._last.prev;
        this._last.next = Node2.Undefined;
      } else if (node.prev === Node2.Undefined) {
        this._first = this._first.next;
        this._first.prev = Node2.Undefined;
      }
      this._size -= 1;
    }
    *[Symbol.iterator]() {
      let node = this._first;
      while (node !== Node2.Undefined) {
        yield node.element;
        node = node.next;
      }
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/common/stopwatch.ts
  var hasPerformanceNow = globalThis.performance && typeof globalThis.performance.now === "function";
  var StopWatch = class _StopWatch {
    static create(highResolution) {
      return new _StopWatch(highResolution);
    }
    constructor(highResolution) {
      this._now = hasPerformanceNow && highResolution === false ? Date.now : globalThis.performance.now.bind(globalThis.performance);
      this._startTime = this._now();
      this._stopTime = -1;
    }
    stop() {
      this._stopTime = this._now();
    }
    reset() {
      this._startTime = this._now();
      this._stopTime = -1;
    }
    elapsed() {
      if (this._stopTime !== -1) {
        return this._stopTime - this._startTime;
      }
      return this._now() - this._startTime;
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/common/event.ts
  var _enableListenerGCedWarning = false;
  var _enableDisposeWithListenerWarning = false;
  var _enableSnapshotPotentialLeakWarning = false;
  var Event;
  ((Event4) => {
    Event4.None = () => Disposable.None;
    function _addLeakageTraceLogic(options) {
      if (_enableSnapshotPotentialLeakWarning) {
        const { onDidAddListener: origListenerDidAdd } = options;
        const stack = Stacktrace.create();
        let count = 0;
        options.onDidAddListener = () => {
          if (++count === 2) {
            console.warn("snapshotted emitter LIKELY used public and SHOULD HAVE BEEN created with DisposableStore. snapshotted here");
            stack.print();
          }
          origListenerDidAdd?.();
        };
      }
    }
    function defer(event, disposable) {
      return debounce(event, () => void 0, 0, void 0, true, void 0, disposable);
    }
    Event4.defer = defer;
    function once(event) {
      return (listener, thisArgs = null, disposables) => {
        let didFire = false;
        let result = void 0;
        result = event((e) => {
          if (didFire) {
            return;
          } else if (result) {
            result.dispose();
          } else {
            didFire = true;
          }
          return listener.call(thisArgs, e);
        }, null, disposables);
        if (didFire) {
          result.dispose();
        }
        return result;
      };
    }
    Event4.once = once;
    function map2(event, map3, disposable) {
      return snapshot((listener, thisArgs = null, disposables) => event((i2) => listener.call(thisArgs, map3(i2)), null, disposables), disposable);
    }
    Event4.map = map2;
    function forEach(event, each, disposable) {
      return snapshot((listener, thisArgs = null, disposables) => event((i2) => {
        each(i2);
        listener.call(thisArgs, i2);
      }, null, disposables), disposable);
    }
    Event4.forEach = forEach;
    function filter(event, filter2, disposable) {
      return snapshot((listener, thisArgs = null, disposables) => event((e) => filter2(e) && listener.call(thisArgs, e), null, disposables), disposable);
    }
    Event4.filter = filter;
    function signal(event) {
      return event;
    }
    Event4.signal = signal;
    function any(...events) {
      return (listener, thisArgs = null, disposables) => {
        const disposable = combinedDisposable(...events.map((event) => event((e) => listener.call(thisArgs, e))));
        return addAndReturnDisposable(disposable, disposables);
      };
    }
    Event4.any = any;
    function reduce(event, merge, initial, disposable) {
      let output = initial;
      return map2(event, (e) => {
        output = merge(output, e);
        return output;
      }, disposable);
    }
    Event4.reduce = reduce;
    function snapshot(event, disposable) {
      let listener;
      const options = {
        onWillAddFirstListener() {
          listener = event(emitter.fire, emitter);
        },
        onDidRemoveLastListener() {
          listener?.dispose();
        }
      };
      if (!disposable) {
        _addLeakageTraceLogic(options);
      }
      const emitter = new Emitter(options);
      disposable?.add(emitter);
      return emitter.event;
    }
    function addAndReturnDisposable(d, store) {
      if (store instanceof Array) {
        store.push(d);
      } else if (store) {
        store.add(d);
      }
      return d;
    }
    function debounce(event, merge, delay = 100, leading = false, flushOnListenerRemove = false, leakWarningThreshold, disposable) {
      let subscription;
      let output = void 0;
      let handle = void 0;
      let numDebouncedCalls = 0;
      let doFire;
      const options = {
        leakWarningThreshold,
        onWillAddFirstListener() {
          subscription = event((cur) => {
            numDebouncedCalls++;
            output = merge(output, cur);
            if (leading && !handle) {
              emitter.fire(output);
              output = void 0;
            }
            doFire = () => {
              const _output = output;
              output = void 0;
              handle = void 0;
              if (!leading || numDebouncedCalls > 1) {
                emitter.fire(_output);
              }
              numDebouncedCalls = 0;
            };
            if (typeof delay === "number") {
              clearTimeout(handle);
              handle = setTimeout(doFire, delay);
            } else {
              if (handle === void 0) {
                handle = 0;
                queueMicrotask(doFire);
              }
            }
          });
        },
        onWillRemoveListener() {
          if (flushOnListenerRemove && numDebouncedCalls > 0) {
            doFire?.();
          }
        },
        onDidRemoveLastListener() {
          doFire = void 0;
          subscription.dispose();
        }
      };
      if (!disposable) {
        _addLeakageTraceLogic(options);
      }
      const emitter = new Emitter(options);
      disposable?.add(emitter);
      return emitter.event;
    }
    Event4.debounce = debounce;
    function accumulate(event, delay = 0, disposable) {
      return Event4.debounce(event, (last, e) => {
        if (!last) {
          return [e];
        }
        last.push(e);
        return last;
      }, delay, void 0, true, void 0, disposable);
    }
    Event4.accumulate = accumulate;
    function latch(event, equals = (a, b) => a === b, disposable) {
      let firstCall = true;
      let cache;
      return filter(event, (value) => {
        const shouldEmit = firstCall || !equals(value, cache);
        firstCall = false;
        cache = value;
        return shouldEmit;
      }, disposable);
    }
    Event4.latch = latch;
    function split(event, isT, disposable) {
      return [
        Event4.filter(event, isT, disposable),
        Event4.filter(event, (e) => !isT(e), disposable)
      ];
    }
    Event4.split = split;
    function buffer(event, flushAfterTimeout = false, _buffer = [], disposable) {
      let buffer2 = _buffer.slice();
      let listener = event((e) => {
        if (buffer2) {
          buffer2.push(e);
        } else {
          emitter.fire(e);
        }
      });
      if (disposable) {
        disposable.add(listener);
      }
      const flush = () => {
        buffer2?.forEach((e) => emitter.fire(e));
        buffer2 = null;
      };
      const emitter = new Emitter({
        onWillAddFirstListener() {
          if (!listener) {
            listener = event((e) => emitter.fire(e));
            if (disposable) {
              disposable.add(listener);
            }
          }
        },
        onDidAddFirstListener() {
          if (buffer2) {
            if (flushAfterTimeout) {
              setTimeout(flush);
            } else {
              flush();
            }
          }
        },
        onDidRemoveLastListener() {
          if (listener) {
            listener.dispose();
          }
          listener = null;
        }
      });
      if (disposable) {
        disposable.add(emitter);
      }
      return emitter.event;
    }
    Event4.buffer = buffer;
    function chain(event, sythensize) {
      const fn = (listener, thisArgs, disposables) => {
        const cs = sythensize(new ChainableSynthesis());
        return event(function(value) {
          const result = cs.evaluate(value);
          if (result !== HaltChainable) {
            listener.call(thisArgs, result);
          }
        }, void 0, disposables);
      };
      return fn;
    }
    Event4.chain = chain;
    const HaltChainable = /* @__PURE__ */ Symbol("HaltChainable");
    class ChainableSynthesis {
      constructor() {
        this.steps = [];
      }
      map(fn) {
        this.steps.push(fn);
        return this;
      }
      forEach(fn) {
        this.steps.push((v) => {
          fn(v);
          return v;
        });
        return this;
      }
      filter(fn) {
        this.steps.push((v) => fn(v) ? v : HaltChainable);
        return this;
      }
      reduce(merge, initial) {
        let last = initial;
        this.steps.push((v) => {
          last = merge(last, v);
          return last;
        });
        return this;
      }
      latch(equals = (a, b) => a === b) {
        let firstCall = true;
        let cache;
        this.steps.push((value) => {
          const shouldEmit = firstCall || !equals(value, cache);
          firstCall = false;
          cache = value;
          return shouldEmit ? value : HaltChainable;
        });
        return this;
      }
      evaluate(value) {
        for (const step of this.steps) {
          value = step(value);
          if (value === HaltChainable) {
            break;
          }
        }
        return value;
      }
    }
    function fromNodeEventEmitter(emitter, eventName, map3 = (id2) => id2) {
      const fn = (...args) => result.fire(map3(...args));
      const onFirstListenerAdd = () => emitter.on(eventName, fn);
      const onLastListenerRemove = () => emitter.removeListener(eventName, fn);
      const result = new Emitter({ onWillAddFirstListener: onFirstListenerAdd, onDidRemoveLastListener: onLastListenerRemove });
      return result.event;
    }
    Event4.fromNodeEventEmitter = fromNodeEventEmitter;
    function fromDOMEventEmitter(emitter, eventName, map3 = (id2) => id2) {
      const fn = (...args) => result.fire(map3(...args));
      const onFirstListenerAdd = () => emitter.addEventListener(eventName, fn);
      const onLastListenerRemove = () => emitter.removeEventListener(eventName, fn);
      const result = new Emitter({ onWillAddFirstListener: onFirstListenerAdd, onDidRemoveLastListener: onLastListenerRemove });
      return result.event;
    }
    Event4.fromDOMEventEmitter = fromDOMEventEmitter;
    function toPromise(event) {
      return new Promise((resolve) => once(event)(resolve));
    }
    Event4.toPromise = toPromise;
    function fromPromise(promise) {
      const result = new Emitter();
      promise.then((res) => {
        result.fire(res);
      }, () => {
        result.fire(void 0);
      }).finally(() => {
        result.dispose();
      });
      return result.event;
    }
    Event4.fromPromise = fromPromise;
    function forward(from, to) {
      return from((e) => to.fire(e));
    }
    Event4.forward = forward;
    function runAndSubscribe(event, handler, initial) {
      handler(initial);
      return event((e) => handler(e));
    }
    Event4.runAndSubscribe = runAndSubscribe;
    class EmitterObserver {
      constructor(_observable, store) {
        this._observable = _observable;
        this._counter = 0;
        this._hasChanged = false;
        const options = {
          onWillAddFirstListener: () => {
            _observable.addObserver(this);
          },
          onDidRemoveLastListener: () => {
            _observable.removeObserver(this);
          }
        };
        if (!store) {
          _addLeakageTraceLogic(options);
        }
        this.emitter = new Emitter(options);
        if (store) {
          store.add(this.emitter);
        }
      }
      beginUpdate(_observable) {
        this._counter++;
      }
      handlePossibleChange(_observable) {
      }
      handleChange(_observable, _change) {
        this._hasChanged = true;
      }
      endUpdate(_observable) {
        this._counter--;
        if (this._counter === 0) {
          this._observable.reportChanges();
          if (this._hasChanged) {
            this._hasChanged = false;
            this.emitter.fire(this._observable.get());
          }
        }
      }
    }
    function fromObservable(obs, store) {
      const observer = new EmitterObserver(obs, store);
      return observer.emitter.event;
    }
    Event4.fromObservable = fromObservable;
    function fromObservableLight(observable) {
      return (listener, thisArgs, disposables) => {
        let count = 0;
        let didChange = false;
        const observer = {
          beginUpdate() {
            count++;
          },
          endUpdate() {
            count--;
            if (count === 0) {
              observable.reportChanges();
              if (didChange) {
                didChange = false;
                listener.call(thisArgs);
              }
            }
          },
          handlePossibleChange() {
          },
          handleChange() {
            didChange = true;
          }
        };
        observable.addObserver(observer);
        observable.reportChanges();
        const disposable = {
          dispose() {
            observable.removeObserver(observer);
          }
        };
        if (disposables instanceof DisposableStore) {
          disposables.add(disposable);
        } else if (Array.isArray(disposables)) {
          disposables.push(disposable);
        }
        return disposable;
      };
    }
    Event4.fromObservableLight = fromObservableLight;
  })(Event || (Event = {}));
  var _EventProfiling = class _EventProfiling {
    constructor(name) {
      this.listenerCount = 0;
      this.invocationCount = 0;
      this.elapsedOverall = 0;
      this.durations = [];
      this.name = `${name}_${_EventProfiling._idPool++}`;
      _EventProfiling.all.add(this);
    }
    start(listenerCount) {
      this._stopWatch = new StopWatch();
      this.listenerCount = listenerCount;
    }
    stop() {
      if (this._stopWatch) {
        const elapsed = this._stopWatch.elapsed();
        this.durations.push(elapsed);
        this.elapsedOverall += elapsed;
        this.invocationCount += 1;
        this._stopWatch = void 0;
      }
    }
  };
  _EventProfiling.all = /* @__PURE__ */ new Set();
  _EventProfiling._idPool = 0;
  var EventProfiling = _EventProfiling;
  var _globalLeakWarningThreshold = -1;
  var _LeakageMonitor = class _LeakageMonitor {
    constructor(_errorHandler, threshold, name = (_LeakageMonitor._idPool++).toString(16).padStart(3, "0")) {
      this._errorHandler = _errorHandler;
      this.threshold = threshold;
      this.name = name;
      this._warnCountdown = 0;
    }
    dispose() {
      this._stacks?.clear();
    }
    check(stack, listenerCount) {
      const threshold = this.threshold;
      if (threshold <= 0 || listenerCount < threshold) {
        return void 0;
      }
      if (!this._stacks) {
        this._stacks = /* @__PURE__ */ new Map();
      }
      const count = this._stacks.get(stack.value) || 0;
      this._stacks.set(stack.value, count + 1);
      this._warnCountdown -= 1;
      if (this._warnCountdown <= 0) {
        this._warnCountdown = threshold * 0.5;
        const [topStack, topCount] = this.getMostFrequentStack();
        const message = `[${this.name}] potential listener LEAK detected, having ${listenerCount} listeners already. MOST frequent listener (${topCount}):`;
        console.warn(message);
        console.warn(topStack);
        const error = new ListenerLeakError(message, topStack);
        this._errorHandler(error);
      }
      return () => {
        const count2 = this._stacks.get(stack.value) || 0;
        this._stacks.set(stack.value, count2 - 1);
      };
    }
    getMostFrequentStack() {
      if (!this._stacks) {
        return void 0;
      }
      let topStack;
      let topCount = 0;
      for (const [stack, count] of this._stacks) {
        if (!topStack || topCount < count) {
          topStack = [stack, count];
          topCount = count;
        }
      }
      return topStack;
    }
  };
  _LeakageMonitor._idPool = 1;
  var LeakageMonitor = _LeakageMonitor;
  var Stacktrace = class _Stacktrace {
    constructor(value) {
      this.value = value;
    }
    static create() {
      const err = new Error();
      return new _Stacktrace(err.stack ?? "");
    }
    print() {
      console.warn(this.value.split("\n").slice(2).join("\n"));
    }
  };
  var ListenerLeakError = class extends Error {
    constructor(message, stack) {
      super(message);
      this.name = "ListenerLeakError";
      this.stack = stack;
    }
  };
  var ListenerRefusalError = class extends Error {
    constructor(message, stack) {
      super(message);
      this.name = "ListenerRefusalError";
      this.stack = stack;
    }
  };
  var id = 0;
  var UniqueContainer = class {
    constructor(value) {
      this.value = value;
      this.id = id++;
    }
  };
  var compactionThreshold = 2;
  var forEachListener = (listeners, fn) => {
    if (listeners instanceof UniqueContainer) {
      fn(listeners);
    } else {
      for (let i2 = 0; i2 < listeners.length; i2++) {
        const l = listeners[i2];
        if (l) {
          fn(l);
        }
      }
    }
  };
  var _listenerFinalizers;
  if (_enableListenerGCedWarning) {
    const leaks = [];
    setInterval(() => {
      if (leaks.length === 0) {
        return;
      }
      console.warn("[LEAKING LISTENERS] GC'ed these listeners that were NOT yet disposed:");
      console.warn(leaks.join("\n"));
      leaks.length = 0;
    }, 3e3);
    _listenerFinalizers = new FinalizationRegistry((heldValue) => {
      if (typeof heldValue === "string") {
        leaks.push(heldValue);
      }
    });
  }
  var Emitter = class {
    constructor(options) {
      this._size = 0;
      this._options = options;
      this._leakageMon = _globalLeakWarningThreshold > 0 || this._options?.leakWarningThreshold ? new LeakageMonitor(options?.onListenerError ?? onUnexpectedError, this._options?.leakWarningThreshold ?? _globalLeakWarningThreshold) : void 0;
      this._perfMon = this._options?._profName ? new EventProfiling(this._options._profName) : void 0;
      this._deliveryQueue = this._options?.deliveryQueue;
    }
    dispose() {
      if (!this._disposed) {
        this._disposed = true;
        if (this._deliveryQueue?.current === this) {
          this._deliveryQueue.reset();
        }
        if (this._listeners) {
          if (_enableDisposeWithListenerWarning) {
            const listeners = this._listeners;
            queueMicrotask(() => {
              forEachListener(listeners, (l) => l.stack?.print());
            });
          }
          this._listeners = void 0;
          this._size = 0;
        }
        this._options?.onDidRemoveLastListener?.();
        this._leakageMon?.dispose();
      }
    }
    /**
     * For the public to allow to subscribe
     * to events from this Emitter
     */
    get event() {
      this._event ?? (this._event = (callback, thisArgs, disposables) => {
        if (this._leakageMon && this._size > this._leakageMon.threshold ** 2) {
          const message = `[${this._leakageMon.name}] REFUSES to accept new listeners because it exceeded its threshold by far (${this._size} vs ${this._leakageMon.threshold})`;
          console.warn(message);
          const tuple = this._leakageMon.getMostFrequentStack() ?? ["UNKNOWN stack", -1];
          const error = new ListenerRefusalError(`${message}. HINT: Stack shows most frequent listener (${tuple[1]}-times)`, tuple[0]);
          const errorHandler2 = this._options?.onListenerError || onUnexpectedError;
          errorHandler2(error);
          return Disposable.None;
        }
        if (this._disposed) {
          return Disposable.None;
        }
        if (thisArgs) {
          callback = callback.bind(thisArgs);
        }
        const contained = new UniqueContainer(callback);
        let removeMonitor;
        let stack;
        if (this._leakageMon && this._size >= Math.ceil(this._leakageMon.threshold * 0.2)) {
          contained.stack = Stacktrace.create();
          removeMonitor = this._leakageMon.check(contained.stack, this._size + 1);
        }
        if (_enableDisposeWithListenerWarning) {
          contained.stack = stack ?? Stacktrace.create();
        }
        if (!this._listeners) {
          this._options?.onWillAddFirstListener?.(this);
          this._listeners = contained;
          this._options?.onDidAddFirstListener?.(this);
        } else if (this._listeners instanceof UniqueContainer) {
          this._deliveryQueue ?? (this._deliveryQueue = new EventDeliveryQueuePrivate());
          this._listeners = [this._listeners, contained];
        } else {
          this._listeners.push(contained);
        }
        this._size++;
        const result = toDisposable(() => {
          _listenerFinalizers?.unregister(result);
          removeMonitor?.();
          this._removeListener(contained);
        });
        if (disposables instanceof DisposableStore) {
          disposables.add(result);
        } else if (Array.isArray(disposables)) {
          disposables.push(result);
        }
        if (_listenerFinalizers) {
          const stack2 = new Error().stack.split("\n").slice(2, 3).join("\n").trim();
          const match = /(file:|vscode-file:\/\/vscode-app)?(\/[^:]*:\d+:\d+)/.exec(stack2);
          _listenerFinalizers.register(result, match?.[2] ?? stack2, result);
        }
        return result;
      });
      return this._event;
    }
    _removeListener(listener) {
      this._options?.onWillRemoveListener?.(this);
      if (!this._listeners) {
        return;
      }
      if (this._size === 1) {
        this._listeners = void 0;
        this._options?.onDidRemoveLastListener?.(this);
        this._size = 0;
        return;
      }
      const listeners = this._listeners;
      const index = listeners.indexOf(listener);
      if (index === -1) {
        console.log("disposed?", this._disposed);
        console.log("size?", this._size);
        console.log("arr?", JSON.stringify(this._listeners));
        throw new Error("Attempted to dispose unknown listener");
      }
      this._size--;
      listeners[index] = void 0;
      const adjustDeliveryQueue = this._deliveryQueue.current === this;
      if (this._size * compactionThreshold <= listeners.length) {
        let n = 0;
        for (let i2 = 0; i2 < listeners.length; i2++) {
          if (listeners[i2]) {
            listeners[n++] = listeners[i2];
          } else if (adjustDeliveryQueue) {
            this._deliveryQueue.end--;
            if (n < this._deliveryQueue.i) {
              this._deliveryQueue.i--;
            }
          }
        }
        listeners.length = n;
      }
    }
    _deliver(listener, value) {
      if (!listener) {
        return;
      }
      const errorHandler2 = this._options?.onListenerError || onUnexpectedError;
      if (!errorHandler2) {
        listener.value(value);
        return;
      }
      try {
        listener.value(value);
      } catch (e) {
        errorHandler2(e);
      }
    }
    /** Delivers items in the queue. Assumes the queue is ready to go. */
    _deliverQueue(dq) {
      const listeners = dq.current._listeners;
      while (dq.i < dq.end) {
        this._deliver(listeners[dq.i++], dq.value);
      }
      dq.reset();
    }
    /**
     * To be kept private to fire an event to
     * subscribers
     */
    fire(event) {
      if (this._deliveryQueue?.current) {
        this._deliverQueue(this._deliveryQueue);
        this._perfMon?.stop();
      }
      this._perfMon?.start(this._size);
      if (!this._listeners) {
      } else if (this._listeners instanceof UniqueContainer) {
        this._deliver(this._listeners, event);
      } else {
        const dq = this._deliveryQueue;
        dq.enqueue(this, event, this._listeners.length);
        this._deliverQueue(dq);
      }
      this._perfMon?.stop();
    }
    hasListeners() {
      return this._size > 0;
    }
  };
  var EventDeliveryQueuePrivate = class {
    constructor() {
      /**
       * Index in current's listener list.
       */
      this.i = -1;
      /**
       * The last index in the listener's list to deliver.
       */
      this.end = 0;
    }
    enqueue(emitter, value, end) {
      this.i = 0;
      this.end = end;
      this.current = emitter;
      this.value = value;
    }
    reset() {
      this.i = this.end;
      this.current = void 0;
      this.value = void 0;
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/browser/browser.ts
  var _WindowManager = class _WindowManager {
    constructor() {
      // --- Zoom Level
      this.mapWindowIdToZoomLevel = /* @__PURE__ */ new Map();
      this._onDidChangeZoomLevel = new Emitter();
      this.onDidChangeZoomLevel = this._onDidChangeZoomLevel.event;
      // --- Zoom Factor
      this.mapWindowIdToZoomFactor = /* @__PURE__ */ new Map();
      // --- Fullscreen
      this._onDidChangeFullscreen = new Emitter();
      this.onDidChangeFullscreen = this._onDidChangeFullscreen.event;
      this.mapWindowIdToFullScreen = /* @__PURE__ */ new Map();
    }
    getZoomLevel(targetWindow) {
      return this.mapWindowIdToZoomLevel.get(this.getWindowId(targetWindow)) ?? 0;
    }
    setZoomLevel(zoomLevel, targetWindow) {
      if (this.getZoomLevel(targetWindow) === zoomLevel) {
        return;
      }
      const targetWindowId = this.getWindowId(targetWindow);
      this.mapWindowIdToZoomLevel.set(targetWindowId, zoomLevel);
      this._onDidChangeZoomLevel.fire(targetWindowId);
    }
    getZoomFactor(targetWindow) {
      return this.mapWindowIdToZoomFactor.get(this.getWindowId(targetWindow)) ?? 1;
    }
    setZoomFactor(zoomFactor, targetWindow) {
      this.mapWindowIdToZoomFactor.set(this.getWindowId(targetWindow), zoomFactor);
    }
    setFullscreen(fullscreen, targetWindow) {
      if (this.isFullscreen(targetWindow) === fullscreen) {
        return;
      }
      const windowId = this.getWindowId(targetWindow);
      this.mapWindowIdToFullScreen.set(windowId, fullscreen);
      this._onDidChangeFullscreen.fire(windowId);
    }
    isFullscreen(targetWindow) {
      return !!this.mapWindowIdToFullScreen.get(this.getWindowId(targetWindow));
    }
    getWindowId(targetWindow) {
      return targetWindow.vscodeWindowId;
    }
  };
  _WindowManager.INSTANCE = new _WindowManager();
  var WindowManager = _WindowManager;
  function addMatchMediaChangeListener(targetWindow, query, callback) {
    if (typeof query === "string") {
      query = targetWindow.matchMedia(query);
    }
    query.addEventListener("change", callback);
  }
  var onDidChangeZoomLevel = WindowManager.INSTANCE.onDidChangeZoomLevel;
  function getZoomFactor(targetWindow) {
    return WindowManager.INSTANCE.getZoomFactor(targetWindow);
  }
  var onDidChangeFullscreen = WindowManager.INSTANCE.onDidChangeFullscreen;
  var userAgent = typeof navigator === "object" ? navigator.userAgent : "";
  var isFirefox = userAgent.indexOf("Firefox") >= 0;
  var isWebKit = userAgent.indexOf("AppleWebKit") >= 0;
  var isChrome = userAgent.indexOf("Chrome") >= 0;
  var isSafari = !isChrome && userAgent.indexOf("Safari") >= 0;
  var isElectron = userAgent.indexOf("Electron/") >= 0;
  var isAndroid = userAgent.indexOf("Android") >= 0;
  var standalone = false;
  if (typeof mainWindow.matchMedia === "function") {
    const standaloneMatchMedia = mainWindow.matchMedia("(display-mode: standalone) or (display-mode: window-controls-overlay)");
    const fullScreenMatchMedia = mainWindow.matchMedia("(display-mode: fullscreen)");
    standalone = standaloneMatchMedia.matches;
    addMatchMediaChangeListener(mainWindow, standaloneMatchMedia, ({ matches }) => {
      if (standalone && fullScreenMatchMedia.matches) {
        return;
      }
      standalone = matches;
    });
  }
  function isStandalone() {
    return standalone;
  }

  // node_modules/@xterm/xterm/src/vs/base/common/platform.ts
  var LANGUAGE_DEFAULT = "en";
  var _isWindows = false;
  var _isMacintosh = false;
  var _isLinux = false;
  var _isLinuxSnap = false;
  var _isNative = false;
  var _isWeb = false;
  var _isElectron = false;
  var _isIOS = false;
  var _isCI = false;
  var _isMobile = false;
  var _locale = void 0;
  var _language = LANGUAGE_DEFAULT;
  var _platformLocale = LANGUAGE_DEFAULT;
  var _translationsConfigFile = void 0;
  var _userAgent = void 0;
  var $globalThis = globalThis;
  var nodeProcess = void 0;
  if (typeof $globalThis.vscode !== "undefined" && typeof $globalThis.vscode.process !== "undefined") {
    nodeProcess = $globalThis.vscode.process;
  } else if (typeof process !== "undefined" && typeof process?.versions?.node === "string") {
    nodeProcess = process;
  }
  var isElectronProcess = typeof nodeProcess?.versions?.electron === "string";
  var isElectronRenderer = isElectronProcess && nodeProcess?.type === "renderer";
  if (typeof nodeProcess === "object") {
    _isWindows = nodeProcess.platform === "win32";
    _isMacintosh = nodeProcess.platform === "darwin";
    _isLinux = nodeProcess.platform === "linux";
    _isLinuxSnap = _isLinux && !!nodeProcess.env["SNAP"] && !!nodeProcess.env["SNAP_REVISION"];
    _isElectron = isElectronProcess;
    _isCI = !!nodeProcess.env["CI"] || !!nodeProcess.env["BUILD_ARTIFACTSTAGINGDIRECTORY"];
    _locale = LANGUAGE_DEFAULT;
    _language = LANGUAGE_DEFAULT;
    const rawNlsConfig = nodeProcess.env["VSCODE_NLS_CONFIG"];
    if (rawNlsConfig) {
      try {
        const nlsConfig = JSON.parse(rawNlsConfig);
        _locale = nlsConfig.userLocale;
        _platformLocale = nlsConfig.osLocale;
        _language = nlsConfig.resolvedLanguage || LANGUAGE_DEFAULT;
        _translationsConfigFile = nlsConfig.languagePack?.translationsConfigFile;
      } catch (e) {
      }
    }
    _isNative = true;
  } else if (typeof navigator === "object" && !isElectronRenderer) {
    _userAgent = navigator.userAgent;
    _isWindows = _userAgent.indexOf("Windows") >= 0;
    _isMacintosh = _userAgent.indexOf("Macintosh") >= 0;
    _isIOS = (_userAgent.indexOf("Macintosh") >= 0 || _userAgent.indexOf("iPad") >= 0 || _userAgent.indexOf("iPhone") >= 0) && !!navigator.maxTouchPoints && navigator.maxTouchPoints > 0;
    _isLinux = _userAgent.indexOf("Linux") >= 0;
    _isMobile = _userAgent?.indexOf("Mobi") >= 0;
    _isWeb = true;
    _language = globalThis._VSCODE_NLS_LANGUAGE || LANGUAGE_DEFAULT;
    _locale = navigator.language.toLowerCase();
    _platformLocale = _locale;
  } else {
    console.error("Unable to resolve platform.");
  }
  var _platform = 0 /* Web */;
  if (_isMacintosh) {
    _platform = 1 /* Mac */;
  } else if (_isWindows) {
    _platform = 3 /* Windows */;
  } else if (_isLinux) {
    _platform = 2 /* Linux */;
  }
  var isWindows = _isWindows;
  var isMacintosh = _isMacintosh;
  var isLinux = _isLinux;
  var isNative = _isNative;
  var isWebWorker = _isWeb && typeof $globalThis.importScripts === "function";
  var webWorkerOrigin = isWebWorker ? $globalThis.origin : void 0;
  var userAgent2 = _userAgent;
  var language = _language;
  var Language;
  ((Language2) => {
    function value() {
      return language;
    }
    Language2.value = value;
    function isDefaultVariant() {
      if (language.length === 2) {
        return language === "en";
      } else if (language.length >= 3) {
        return language[0] === "e" && language[1] === "n" && language[2] === "-";
      } else {
        return false;
      }
    }
    Language2.isDefaultVariant = isDefaultVariant;
    function isDefault() {
      return language === "en";
    }
    Language2.isDefault = isDefault;
  })(Language || (Language = {}));
  var setTimeout0IsFaster = typeof $globalThis.postMessage === "function" && !$globalThis.importScripts;
  var setTimeout0 = (() => {
    if (setTimeout0IsFaster) {
      const pending = [];
      $globalThis.addEventListener("message", (e) => {
        if (e.data && e.data.vscodeScheduleAsyncWork) {
          for (let i2 = 0, len = pending.length; i2 < len; i2++) {
            const candidate = pending[i2];
            if (candidate.id === e.data.vscodeScheduleAsyncWork) {
              pending.splice(i2, 1);
              candidate.callback();
              return;
            }
          }
        }
      });
      let lastId = 0;
      return (callback) => {
        const myId = ++lastId;
        pending.push({
          id: myId,
          callback
        });
        $globalThis.postMessage({ vscodeScheduleAsyncWork: myId }, "*");
      };
    }
    return (callback) => setTimeout(callback);
  })();
  var isChrome2 = !!(userAgent2 && userAgent2.indexOf("Chrome") >= 0);
  var isFirefox2 = !!(userAgent2 && userAgent2.indexOf("Firefox") >= 0);
  var isSafari2 = !!(!isChrome2 && (userAgent2 && userAgent2.indexOf("Safari") >= 0));
  var isEdge = !!(userAgent2 && userAgent2.indexOf("Edg/") >= 0);
  var isAndroid2 = !!(userAgent2 && userAgent2.indexOf("Android") >= 0);

  // node_modules/@xterm/xterm/src/vs/base/browser/canIUse.ts
  var safeNavigator = typeof navigator === "object" ? navigator : {};
  var BrowserFeatures = {
    clipboard: {
      writeText: isNative || document.queryCommandSupported && document.queryCommandSupported("copy") || !!(safeNavigator && safeNavigator.clipboard && safeNavigator.clipboard.writeText),
      readText: isNative || !!(safeNavigator && safeNavigator.clipboard && safeNavigator.clipboard.readText)
    },
    keyboard: (() => {
      if (isNative || isStandalone()) {
        return 0 /* Always */;
      }
      if (safeNavigator.keyboard || isSafari) {
        return 1 /* FullScreen */;
      }
      return 2 /* None */;
    })(),
    // 'ontouchstart' in window always evaluates to true with typescript's modern typings. This causes `window` to be
    // `never` later in `window.navigator`. That's why we need the explicit `window as Window` cast
    touch: "ontouchstart" in mainWindow || safeNavigator.maxTouchPoints > 0,
    pointerEvents: mainWindow.PointerEvent && ("ontouchstart" in mainWindow || navigator.maxTouchPoints > 0)
  };

  // node_modules/@xterm/xterm/src/vs/base/common/keyCodes.ts
  var KeyCodeStrMap = class {
    constructor() {
      this._keyCodeToStr = [];
      this._strToKeyCode = /* @__PURE__ */ Object.create(null);
    }
    define(keyCode, str) {
      this._keyCodeToStr[keyCode] = str;
      this._strToKeyCode[str.toLowerCase()] = keyCode;
    }
    keyCodeToStr(keyCode) {
      return this._keyCodeToStr[keyCode];
    }
    strToKeyCode(str) {
      return this._strToKeyCode[str.toLowerCase()] || 0 /* Unknown */;
    }
  };
  var uiMap = new KeyCodeStrMap();
  var userSettingsUSMap = new KeyCodeStrMap();
  var userSettingsGeneralMap = new KeyCodeStrMap();
  var EVENT_KEY_CODE_MAP = new Array(230);
  var KeyCodeUtils;
  ((KeyCodeUtils2) => {
    function toString(keyCode) {
      return uiMap.keyCodeToStr(keyCode);
    }
    KeyCodeUtils2.toString = toString;
    function fromString(key) {
      return uiMap.strToKeyCode(key);
    }
    KeyCodeUtils2.fromString = fromString;
    function toUserSettingsUS(keyCode) {
      return userSettingsUSMap.keyCodeToStr(keyCode);
    }
    KeyCodeUtils2.toUserSettingsUS = toUserSettingsUS;
    function toUserSettingsGeneral(keyCode) {
      return userSettingsGeneralMap.keyCodeToStr(keyCode);
    }
    KeyCodeUtils2.toUserSettingsGeneral = toUserSettingsGeneral;
    function fromUserSettings(key) {
      return userSettingsUSMap.strToKeyCode(key) || userSettingsGeneralMap.strToKeyCode(key);
    }
    KeyCodeUtils2.fromUserSettings = fromUserSettings;
    function toElectronAccelerator(keyCode) {
      if (keyCode >= 98 /* Numpad0 */ && keyCode <= 113 /* NumpadDivide */) {
        return null;
      }
      switch (keyCode) {
        case 16 /* UpArrow */:
          return "Up";
        case 18 /* DownArrow */:
          return "Down";
        case 15 /* LeftArrow */:
          return "Left";
        case 17 /* RightArrow */:
          return "Right";
      }
      return uiMap.keyCodeToStr(keyCode);
    }
    KeyCodeUtils2.toElectronAccelerator = toElectronAccelerator;
  })(KeyCodeUtils || (KeyCodeUtils = {}));

  // node_modules/@xterm/xterm/src/vs/base/common/keybindings.ts
  var KeyCodeChord = class _KeyCodeChord {
    constructor(ctrlKey, shiftKey, altKey, metaKey, keyCode) {
      this.ctrlKey = ctrlKey;
      this.shiftKey = shiftKey;
      this.altKey = altKey;
      this.metaKey = metaKey;
      this.keyCode = keyCode;
    }
    equals(other) {
      return other instanceof _KeyCodeChord && this.ctrlKey === other.ctrlKey && this.shiftKey === other.shiftKey && this.altKey === other.altKey && this.metaKey === other.metaKey && this.keyCode === other.keyCode;
    }
    getHashCode() {
      const ctrl = this.ctrlKey ? "1" : "0";
      const shift = this.shiftKey ? "1" : "0";
      const alt = this.altKey ? "1" : "0";
      const meta = this.metaKey ? "1" : "0";
      return `K${ctrl}${shift}${alt}${meta}${this.keyCode}`;
    }
    isModifierKey() {
      return this.keyCode === 0 /* Unknown */ || this.keyCode === 5 /* Ctrl */ || this.keyCode === 57 /* Meta */ || this.keyCode === 6 /* Alt */ || this.keyCode === 4 /* Shift */;
    }
    toKeybinding() {
      return new Keybinding([this]);
    }
    /**
     * Does this keybinding refer to the key code of a modifier and it also has the modifier flag?
     */
    isDuplicateModifierCase() {
      return this.ctrlKey && this.keyCode === 5 /* Ctrl */ || this.shiftKey && this.keyCode === 4 /* Shift */ || this.altKey && this.keyCode === 6 /* Alt */ || this.metaKey && this.keyCode === 57 /* Meta */;
    }
  };
  var Keybinding = class {
    constructor(chords) {
      if (chords.length === 0) {
        throw illegalArgument(`chords`);
      }
      this.chords = chords;
    }
    getHashCode() {
      let result = "";
      for (let i2 = 0, len = this.chords.length; i2 < len; i2++) {
        if (i2 !== 0) {
          result += ";";
        }
        result += this.chords[i2].getHashCode();
      }
      return result;
    }
    equals(other) {
      if (other === null) {
        return false;
      }
      if (this.chords.length !== other.chords.length) {
        return false;
      }
      for (let i2 = 0; i2 < this.chords.length; i2++) {
        if (!this.chords[i2].equals(other.chords[i2])) {
          return false;
        }
      }
      return true;
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/browser/keyboardEvent.ts
  function extractKeyCode(e) {
    if (e.charCode) {
      const char = String.fromCharCode(e.charCode).toUpperCase();
      return KeyCodeUtils.fromString(char);
    }
    const keyCode = e.keyCode;
    if (keyCode === 3) {
      return 7 /* PauseBreak */;
    } else if (isFirefox) {
      switch (keyCode) {
        case 59:
          return 85 /* Semicolon */;
        case 60:
          if (isLinux) {
            return 97 /* IntlBackslash */;
          }
          break;
        case 61:
          return 86 /* Equal */;
        // based on: https://developer.mozilla.org/en-US/docs/Web/API/KeyboardEvent/keyCode#numpad_keys
        case 107:
          return 109 /* NumpadAdd */;
        case 109:
          return 111 /* NumpadSubtract */;
        case 173:
          return 88 /* Minus */;
        case 224:
          if (isMacintosh) {
            return 57 /* Meta */;
          }
          break;
      }
    } else if (isWebKit) {
      if (isMacintosh && keyCode === 93) {
        return 57 /* Meta */;
      } else if (!isMacintosh && keyCode === 92) {
        return 57 /* Meta */;
      }
    }
    return EVENT_KEY_CODE_MAP[keyCode] || 0 /* Unknown */;
  }
  var ctrlKeyMod = isMacintosh ? 256 /* WinCtrl */ : 2048 /* CtrlCmd */;
  var altKeyMod = 512 /* Alt */;
  var shiftKeyMod = 1024 /* Shift */;
  var metaKeyMod = isMacintosh ? 2048 /* CtrlCmd */ : 256 /* WinCtrl */;
  var StandardKeyboardEvent = class {
    constructor(source) {
      this._standardKeyboardEventBrand = true;
      const e = source;
      this.browserEvent = e;
      this.target = e.target;
      this.ctrlKey = e.ctrlKey;
      this.shiftKey = e.shiftKey;
      this.altKey = e.altKey;
      this.metaKey = e.metaKey;
      this.altGraphKey = e.getModifierState?.("AltGraph");
      this.keyCode = extractKeyCode(e);
      this.code = e.code;
      this.ctrlKey = this.ctrlKey || this.keyCode === 5 /* Ctrl */;
      this.altKey = this.altKey || this.keyCode === 6 /* Alt */;
      this.shiftKey = this.shiftKey || this.keyCode === 4 /* Shift */;
      this.metaKey = this.metaKey || this.keyCode === 57 /* Meta */;
      this._asKeybinding = this._computeKeybinding();
      this._asKeyCodeChord = this._computeKeyCodeChord();
    }
    preventDefault() {
      if (this.browserEvent && this.browserEvent.preventDefault) {
        this.browserEvent.preventDefault();
      }
    }
    stopPropagation() {
      if (this.browserEvent && this.browserEvent.stopPropagation) {
        this.browserEvent.stopPropagation();
      }
    }
    toKeyCodeChord() {
      return this._asKeyCodeChord;
    }
    equals(other) {
      return this._asKeybinding === other;
    }
    _computeKeybinding() {
      let key = 0 /* Unknown */;
      if (this.keyCode !== 5 /* Ctrl */ && this.keyCode !== 4 /* Shift */ && this.keyCode !== 6 /* Alt */ && this.keyCode !== 57 /* Meta */) {
        key = this.keyCode;
      }
      let result = 0;
      if (this.ctrlKey) {
        result |= ctrlKeyMod;
      }
      if (this.altKey) {
        result |= altKeyMod;
      }
      if (this.shiftKey) {
        result |= shiftKeyMod;
      }
      if (this.metaKey) {
        result |= metaKeyMod;
      }
      result |= key;
      return result;
    }
    _computeKeyCodeChord() {
      let key = 0 /* Unknown */;
      if (this.keyCode !== 5 /* Ctrl */ && this.keyCode !== 4 /* Shift */ && this.keyCode !== 6 /* Alt */ && this.keyCode !== 57 /* Meta */) {
        key = this.keyCode;
      }
      return new KeyCodeChord(this.ctrlKey, this.shiftKey, this.altKey, this.metaKey, key);
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/browser/iframe.ts
  var sameOriginWindowChainCache = /* @__PURE__ */ new WeakMap();
  function getParentWindowIfSameOrigin(w) {
    if (!w.parent || w.parent === w) {
      return null;
    }
    try {
      const location = w.location;
      const parentLocation = w.parent.location;
      if (location.origin !== "null" && parentLocation.origin !== "null" && location.origin !== parentLocation.origin) {
        return null;
      }
    } catch (e) {
      return null;
    }
    return w.parent;
  }
  var IframeUtils = class {
    /**
     * Returns a chain of embedded windows with the same origin (which can be accessed programmatically).
     * Having a chain of length 1 might mean that the current execution environment is running outside of an iframe or inside an iframe embedded in a window with a different origin.
     */
    static getSameOriginWindowChain(targetWindow) {
      let windowChainCache = sameOriginWindowChainCache.get(targetWindow);
      if (!windowChainCache) {
        windowChainCache = [];
        sameOriginWindowChainCache.set(targetWindow, windowChainCache);
        let w = targetWindow;
        let parent;
        do {
          parent = getParentWindowIfSameOrigin(w);
          if (parent) {
            windowChainCache.push({
              window: new WeakRef(w),
              iframeElement: w.frameElement || null
            });
          } else {
            windowChainCache.push({
              window: new WeakRef(w),
              iframeElement: null
            });
          }
          w = parent;
        } while (w);
      }
      return windowChainCache.slice(0);
    }
    /**
     * Returns the position of `childWindow` relative to `ancestorWindow`
     */
    static getPositionOfChildWindowRelativeToAncestorWindow(childWindow, ancestorWindow) {
      if (!ancestorWindow || childWindow === ancestorWindow) {
        return {
          top: 0,
          left: 0
        };
      }
      let top = 0, left = 0;
      const windowChain = this.getSameOriginWindowChain(childWindow);
      for (const windowChainEl of windowChain) {
        const windowInChain = windowChainEl.window.deref();
        top += windowInChain?.scrollY ?? 0;
        left += windowInChain?.scrollX ?? 0;
        if (windowInChain === ancestorWindow) {
          break;
        }
        if (!windowChainEl.iframeElement) {
          break;
        }
        const boundingRect = windowChainEl.iframeElement.getBoundingClientRect();
        top += boundingRect.top;
        left += boundingRect.left;
      }
      return {
        top,
        left
      };
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/browser/mouseEvent.ts
  var StandardMouseEvent = class {
    constructor(targetWindow, e) {
      this.timestamp = Date.now();
      this.browserEvent = e;
      this.leftButton = e.button === 0;
      this.middleButton = e.button === 1;
      this.rightButton = e.button === 2;
      this.buttons = e.buttons;
      this.target = e.target;
      this.detail = e.detail || 1;
      if (e.type === "dblclick") {
        this.detail = 2;
      }
      this.ctrlKey = e.ctrlKey;
      this.shiftKey = e.shiftKey;
      this.altKey = e.altKey;
      this.metaKey = e.metaKey;
      if (typeof e.pageX === "number") {
        this.posx = e.pageX;
        this.posy = e.pageY;
      } else {
        this.posx = e.clientX + this.target.ownerDocument.body.scrollLeft + this.target.ownerDocument.documentElement.scrollLeft;
        this.posy = e.clientY + this.target.ownerDocument.body.scrollTop + this.target.ownerDocument.documentElement.scrollTop;
      }
      const iframeOffsets = IframeUtils.getPositionOfChildWindowRelativeToAncestorWindow(targetWindow, e.view);
      this.posx -= iframeOffsets.left;
      this.posy -= iframeOffsets.top;
    }
    preventDefault() {
      this.browserEvent.preventDefault();
    }
    stopPropagation() {
      this.browserEvent.stopPropagation();
    }
  };
  var StandardWheelEvent = class {
    constructor(e, deltaX = 0, deltaY = 0) {
      this.browserEvent = e || null;
      this.target = e ? e.target || e.targetNode || e.srcElement : null;
      this.deltaY = deltaY;
      this.deltaX = deltaX;
      let shouldFactorDPR = false;
      if (isChrome) {
        const chromeVersionMatch = navigator.userAgent.match(/Chrome\/(\d+)/);
        const chromeMajorVersion = chromeVersionMatch ? parseInt(chromeVersionMatch[1]) : 123;
        shouldFactorDPR = chromeMajorVersion <= 122;
      }
      if (e) {
        const e1 = e;
        const e2 = e;
        const devicePixelRatio = e.view?.devicePixelRatio || 1;
        if (typeof e1.wheelDeltaY !== "undefined") {
          if (shouldFactorDPR) {
            this.deltaY = e1.wheelDeltaY / (120 * devicePixelRatio);
          } else {
            this.deltaY = e1.wheelDeltaY / 120;
          }
        } else if (typeof e2.VERTICAL_AXIS !== "undefined" && e2.axis === e2.VERTICAL_AXIS) {
          this.deltaY = -e2.detail / 3;
        } else if (e.type === "wheel") {
          const ev = e;
          if (ev.deltaMode === ev.DOM_DELTA_LINE) {
            if (isFirefox && !isMacintosh) {
              this.deltaY = -e.deltaY / 3;
            } else {
              this.deltaY = -e.deltaY;
            }
          } else {
            this.deltaY = -e.deltaY / 40;
          }
        }
        if (typeof e1.wheelDeltaX !== "undefined") {
          if (isSafari && isWindows) {
            this.deltaX = -(e1.wheelDeltaX / 120);
          } else if (shouldFactorDPR) {
            this.deltaX = e1.wheelDeltaX / (120 * devicePixelRatio);
          } else {
            this.deltaX = e1.wheelDeltaX / 120;
          }
        } else if (typeof e2.HORIZONTAL_AXIS !== "undefined" && e2.axis === e2.HORIZONTAL_AXIS) {
          this.deltaX = -e.detail / 3;
        } else if (e.type === "wheel") {
          const ev = e;
          if (ev.deltaMode === ev.DOM_DELTA_LINE) {
            if (isFirefox && !isMacintosh) {
              this.deltaX = -e.deltaX / 3;
            } else {
              this.deltaX = -e.deltaX;
            }
          } else {
            this.deltaX = -e.deltaX / 40;
          }
        }
        if (this.deltaY === 0 && this.deltaX === 0 && e.wheelDelta) {
          if (shouldFactorDPR) {
            this.deltaY = e.wheelDelta / (120 * devicePixelRatio);
          } else {
            this.deltaY = e.wheelDelta / 120;
          }
        }
      }
    }
    preventDefault() {
      this.browserEvent?.preventDefault();
    }
    stopPropagation() {
      this.browserEvent?.stopPropagation();
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/common/cancellation.ts
  var shortcutEvent = Object.freeze(function(callback, context) {
    const handle = setTimeout(callback.bind(context), 0);
    return { dispose() {
      clearTimeout(handle);
    } };
  });
  var CancellationToken;
  ((CancellationToken3) => {
    function isCancellationToken(thing) {
      if (thing === CancellationToken3.None || thing === CancellationToken3.Cancelled) {
        return true;
      }
      if (thing instanceof MutableToken) {
        return true;
      }
      if (!thing || typeof thing !== "object") {
        return false;
      }
      return typeof thing.isCancellationRequested === "boolean" && typeof thing.onCancellationRequested === "function";
    }
    CancellationToken3.isCancellationToken = isCancellationToken;
    CancellationToken3.None = Object.freeze({
      isCancellationRequested: false,
      onCancellationRequested: Event.None
    });
    CancellationToken3.Cancelled = Object.freeze({
      isCancellationRequested: true,
      onCancellationRequested: shortcutEvent
    });
  })(CancellationToken || (CancellationToken = {}));
  var MutableToken = class {
    constructor() {
      this._isCancelled = false;
      this._emitter = null;
    }
    cancel() {
      if (!this._isCancelled) {
        this._isCancelled = true;
        if (this._emitter) {
          this._emitter.fire(void 0);
          this.dispose();
        }
      }
    }
    get isCancellationRequested() {
      return this._isCancelled;
    }
    get onCancellationRequested() {
      if (this._isCancelled) {
        return shortcutEvent;
      }
      if (!this._emitter) {
        this._emitter = new Emitter();
      }
      return this._emitter.event;
    }
    dispose() {
      if (this._emitter) {
        this._emitter.dispose();
        this._emitter = null;
      }
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/common/async.ts
  var TimeoutTimer = class {
    constructor(runner, timeout) {
      this._isDisposed = false;
      this._token = -1;
      if (typeof runner === "function" && typeof timeout === "number") {
        this.setIfNotSet(runner, timeout);
      }
    }
    dispose() {
      this.cancel();
      this._isDisposed = true;
    }
    cancel() {
      if (this._token !== -1) {
        clearTimeout(this._token);
        this._token = -1;
      }
    }
    cancelAndSet(runner, timeout) {
      if (this._isDisposed) {
        throw new BugIndicatingError(`Calling 'cancelAndSet' on a disposed TimeoutTimer`);
      }
      this.cancel();
      this._token = setTimeout(() => {
        this._token = -1;
        runner();
      }, timeout);
    }
    setIfNotSet(runner, timeout) {
      if (this._isDisposed) {
        throw new BugIndicatingError(`Calling 'setIfNotSet' on a disposed TimeoutTimer`);
      }
      if (this._token !== -1) {
        return;
      }
      this._token = setTimeout(() => {
        this._token = -1;
        runner();
      }, timeout);
    }
  };
  var IntervalTimer = class {
    constructor() {
      this.disposable = void 0;
      this.isDisposed = false;
    }
    cancel() {
      this.disposable?.dispose();
      this.disposable = void 0;
    }
    cancelAndSet(runner, interval, context = globalThis) {
      if (this.isDisposed) {
        throw new BugIndicatingError(`Calling 'cancelAndSet' on a disposed IntervalTimer`);
      }
      this.cancel();
      const handle = context.setInterval(() => {
        runner();
      }, interval);
      this.disposable = toDisposable(() => {
        context.clearInterval(handle);
        this.disposable = void 0;
      });
    }
    dispose() {
      this.cancel();
      this.isDisposed = true;
    }
  };
  var runWhenGlobalIdle;
  var _runWhenIdle;
  (function() {
    if (typeof globalThis.requestIdleCallback !== "function" || typeof globalThis.cancelIdleCallback !== "function") {
      _runWhenIdle = (_targetWindow, runner) => {
        setTimeout0(() => {
          if (disposed) {
            return;
          }
          const end = Date.now() + 15;
          const deadline = {
            didTimeout: true,
            timeRemaining() {
              return Math.max(0, end - Date.now());
            }
          };
          runner(Object.freeze(deadline));
        });
        let disposed = false;
        return {
          dispose() {
            if (disposed) {
              return;
            }
            disposed = true;
          }
        };
      };
    } else {
      _runWhenIdle = (targetWindow, runner, timeout) => {
        const handle = targetWindow.requestIdleCallback(runner, typeof timeout === "number" ? { timeout } : void 0);
        let disposed = false;
        return {
          dispose() {
            if (disposed) {
              return;
            }
            disposed = true;
            targetWindow.cancelIdleCallback(handle);
          }
        };
      };
    }
    runWhenGlobalIdle = (runner) => _runWhenIdle(globalThis, runner);
  })();
  var Promises;
  ((Promises2) => {
    async function settled(promises) {
      let firstError = void 0;
      const result = await Promise.all(promises.map((promise) => promise.then((value) => value, (error) => {
        if (!firstError) {
          firstError = error;
        }
        return void 0;
      })));
      if (typeof firstError !== "undefined") {
        throw firstError;
      }
      return result;
    }
    Promises2.settled = settled;
    function withAsyncBody(bodyFn) {
      return new Promise(async (resolve, reject) => {
        try {
          await bodyFn(resolve, reject);
        } catch (error) {
          reject(error);
        }
      });
    }
    Promises2.withAsyncBody = withAsyncBody;
  })(Promises || (Promises = {}));
  var _AsyncIterableObject = class _AsyncIterableObject {
    static fromArray(items) {
      return new _AsyncIterableObject((writer) => {
        writer.emitMany(items);
      });
    }
    static fromPromise(promise) {
      return new _AsyncIterableObject(async (emitter) => {
        emitter.emitMany(await promise);
      });
    }
    static fromPromises(promises) {
      return new _AsyncIterableObject(async (emitter) => {
        await Promise.all(promises.map(async (p) => emitter.emitOne(await p)));
      });
    }
    static merge(iterables) {
      return new _AsyncIterableObject(async (emitter) => {
        await Promise.all(iterables.map(async (iterable) => {
          for await (const item of iterable) {
            emitter.emitOne(item);
          }
        }));
      });
    }
    constructor(executor, onReturn) {
      this._state = 0 /* Initial */;
      this._results = [];
      this._error = null;
      this._onReturn = onReturn;
      this._onStateChanged = new Emitter();
      queueMicrotask(async () => {
        const writer = {
          emitOne: (item) => this.emitOne(item),
          emitMany: (items) => this.emitMany(items),
          reject: (error) => this.reject(error)
        };
        try {
          await Promise.resolve(executor(writer));
          this.resolve();
        } catch (err) {
          this.reject(err);
        } finally {
          writer.emitOne = void 0;
          writer.emitMany = void 0;
          writer.reject = void 0;
        }
      });
    }
    [Symbol.asyncIterator]() {
      let i2 = 0;
      return {
        next: async () => {
          do {
            if (this._state === 2 /* DoneError */) {
              throw this._error;
            }
            if (i2 < this._results.length) {
              return { done: false, value: this._results[i2++] };
            }
            if (this._state === 1 /* DoneOK */) {
              return { done: true, value: void 0 };
            }
            await Event.toPromise(this._onStateChanged.event);
          } while (true);
        },
        return: async () => {
          this._onReturn?.();
          return { done: true, value: void 0 };
        }
      };
    }
    static map(iterable, mapFn) {
      return new _AsyncIterableObject(async (emitter) => {
        for await (const item of iterable) {
          emitter.emitOne(mapFn(item));
        }
      });
    }
    map(mapFn) {
      return _AsyncIterableObject.map(this, mapFn);
    }
    static filter(iterable, filterFn) {
      return new _AsyncIterableObject(async (emitter) => {
        for await (const item of iterable) {
          if (filterFn(item)) {
            emitter.emitOne(item);
          }
        }
      });
    }
    filter(filterFn) {
      return _AsyncIterableObject.filter(this, filterFn);
    }
    static coalesce(iterable) {
      return _AsyncIterableObject.filter(iterable, (item) => !!item);
    }
    coalesce() {
      return _AsyncIterableObject.coalesce(this);
    }
    static async toPromise(iterable) {
      const result = [];
      for await (const item of iterable) {
        result.push(item);
      }
      return result;
    }
    toPromise() {
      return _AsyncIterableObject.toPromise(this);
    }
    /**
     * The value will be appended at the end.
     *
     * **NOTE** If `resolve()` or `reject()` have already been called, this method has no effect.
     */
    emitOne(value) {
      if (this._state !== 0 /* Initial */) {
        return;
      }
      this._results.push(value);
      this._onStateChanged.fire();
    }
    /**
     * The values will be appended at the end.
     *
     * **NOTE** If `resolve()` or `reject()` have already been called, this method has no effect.
     */
    emitMany(values) {
      if (this._state !== 0 /* Initial */) {
        return;
      }
      this._results = this._results.concat(values);
      this._onStateChanged.fire();
    }
    /**
     * Calling `resolve()` will mark the result array as complete.
     *
     * **NOTE** `resolve()` must be called, otherwise all consumers of this iterable will hang indefinitely, similar to a non-resolved promise.
     * **NOTE** If `resolve()` or `reject()` have already been called, this method has no effect.
     */
    resolve() {
      if (this._state !== 0 /* Initial */) {
        return;
      }
      this._state = 1 /* DoneOK */;
      this._onStateChanged.fire();
    }
    /**
     * Writing an error will permanently invalidate this iterable.
     * The current users will receive an error thrown, as will all future users.
     *
     * **NOTE** If `resolve()` or `reject()` have already been called, this method has no effect.
     */
    reject(error) {
      if (this._state !== 0 /* Initial */) {
        return;
      }
      this._state = 2 /* DoneError */;
      this._error = error;
      this._onStateChanged.fire();
    }
  };
  _AsyncIterableObject.EMPTY = _AsyncIterableObject.fromArray([]);
  var AsyncIterableObject = _AsyncIterableObject;

  // node_modules/@xterm/xterm/src/vs/base/common/strings.ts
  function isHighSurrogate(charCode) {
    return 55296 <= charCode && charCode <= 56319;
  }
  function isLowSurrogate(charCode) {
    return 56320 <= charCode && charCode <= 57343;
  }
  function computeCodePoint(highSurrogate, lowSurrogate) {
    return (highSurrogate - 55296 << 10) + (lowSurrogate - 56320) + 65536;
  }

  // node_modules/@xterm/xterm/src/vs/base/common/hash.ts
  function hash(obj) {
    return doHash(obj, 0);
  }
  function doHash(obj, hashVal) {
    switch (typeof obj) {
      case "object":
        if (obj === null) {
          return numberHash(349, hashVal);
        } else if (Array.isArray(obj)) {
          return arrayHash(obj, hashVal);
        }
        return objectHash(obj, hashVal);
      case "string":
        return stringHash(obj, hashVal);
      case "boolean":
        return booleanHash(obj, hashVal);
      case "number":
        return numberHash(obj, hashVal);
      case "undefined":
        return numberHash(937, hashVal);
      default:
        return numberHash(617, hashVal);
    }
  }
  function numberHash(val, initialHashVal) {
    return (initialHashVal << 5) - initialHashVal + val | 0;
  }
  function booleanHash(b, initialHashVal) {
    return numberHash(b ? 433 : 863, initialHashVal);
  }
  function stringHash(s, hashVal) {
    hashVal = numberHash(149417, hashVal);
    for (let i2 = 0, length = s.length; i2 < length; i2++) {
      hashVal = numberHash(s.charCodeAt(i2), hashVal);
    }
    return hashVal;
  }
  function arrayHash(arr, initialHashVal) {
    initialHashVal = numberHash(104579, initialHashVal);
    return arr.reduce((hashVal, item) => doHash(item, hashVal), initialHashVal);
  }
  function objectHash(obj, initialHashVal) {
    initialHashVal = numberHash(181387, initialHashVal);
    return Object.keys(obj).sort().reduce((hashVal, key) => {
      hashVal = stringHash(key, hashVal);
      return doHash(obj[key], hashVal);
    }, initialHashVal);
  }
  function leftRotate(value, bits, totalBits = 32) {
    const delta = totalBits - bits;
    const mask = ~((1 << delta) - 1);
    return (value << bits | (mask & value) >>> delta) >>> 0;
  }
  function fill(dest, index = 0, count = dest.byteLength, value = 0) {
    for (let i2 = 0; i2 < count; i2++) {
      dest[index + i2] = value;
    }
  }
  function leftPad(value, length, char = "0") {
    while (value.length < length) {
      value = char + value;
    }
    return value;
  }
  function toHexString(bufferOrValue, bitsize = 32) {
    if (bufferOrValue instanceof ArrayBuffer) {
      return Array.from(new Uint8Array(bufferOrValue)).map((b) => b.toString(16).padStart(2, "0")).join("");
    }
    return leftPad((bufferOrValue >>> 0).toString(16), bitsize / 4);
  }
  var _StringSHA1 = class _StringSHA1 {
    constructor() {
      // 80 * 4 = 320
      this._h0 = 1732584193;
      this._h1 = 4023233417;
      this._h2 = 2562383102;
      this._h3 = 271733878;
      this._h4 = 3285377520;
      this._buff = new Uint8Array(
        64 /* BLOCK_SIZE */ + 3
        /* to fit any utf-8 */
      );
      this._buffDV = new DataView(this._buff.buffer);
      this._buffLen = 0;
      this._totalLen = 0;
      this._leftoverHighSurrogate = 0;
      this._finished = false;
    }
    update(str) {
      const strLen = str.length;
      if (strLen === 0) {
        return;
      }
      const buff = this._buff;
      let buffLen = this._buffLen;
      let leftoverHighSurrogate = this._leftoverHighSurrogate;
      let charCode;
      let offset;
      if (leftoverHighSurrogate !== 0) {
        charCode = leftoverHighSurrogate;
        offset = -1;
        leftoverHighSurrogate = 0;
      } else {
        charCode = str.charCodeAt(0);
        offset = 0;
      }
      while (true) {
        let codePoint = charCode;
        if (isHighSurrogate(charCode)) {
          if (offset + 1 < strLen) {
            const nextCharCode = str.charCodeAt(offset + 1);
            if (isLowSurrogate(nextCharCode)) {
              offset++;
              codePoint = computeCodePoint(charCode, nextCharCode);
            } else {
              codePoint = 65533 /* UNICODE_REPLACEMENT */;
            }
          } else {
            leftoverHighSurrogate = charCode;
            break;
          }
        } else if (isLowSurrogate(charCode)) {
          codePoint = 65533 /* UNICODE_REPLACEMENT */;
        }
        buffLen = this._push(buff, buffLen, codePoint);
        offset++;
        if (offset < strLen) {
          charCode = str.charCodeAt(offset);
        } else {
          break;
        }
      }
      this._buffLen = buffLen;
      this._leftoverHighSurrogate = leftoverHighSurrogate;
    }
    _push(buff, buffLen, codePoint) {
      if (codePoint < 128) {
        buff[buffLen++] = codePoint;
      } else if (codePoint < 2048) {
        buff[buffLen++] = 192 | (codePoint & 1984) >>> 6;
        buff[buffLen++] = 128 | (codePoint & 63) >>> 0;
      } else if (codePoint < 65536) {
        buff[buffLen++] = 224 | (codePoint & 61440) >>> 12;
        buff[buffLen++] = 128 | (codePoint & 4032) >>> 6;
        buff[buffLen++] = 128 | (codePoint & 63) >>> 0;
      } else {
        buff[buffLen++] = 240 | (codePoint & 1835008) >>> 18;
        buff[buffLen++] = 128 | (codePoint & 258048) >>> 12;
        buff[buffLen++] = 128 | (codePoint & 4032) >>> 6;
        buff[buffLen++] = 128 | (codePoint & 63) >>> 0;
      }
      if (buffLen >= 64 /* BLOCK_SIZE */) {
        this._step();
        buffLen -= 64 /* BLOCK_SIZE */;
        this._totalLen += 64 /* BLOCK_SIZE */;
        buff[0] = buff[64 /* BLOCK_SIZE */ + 0];
        buff[1] = buff[64 /* BLOCK_SIZE */ + 1];
        buff[2] = buff[64 /* BLOCK_SIZE */ + 2];
      }
      return buffLen;
    }
    digest() {
      if (!this._finished) {
        this._finished = true;
        if (this._leftoverHighSurrogate) {
          this._leftoverHighSurrogate = 0;
          this._buffLen = this._push(this._buff, this._buffLen, 65533 /* UNICODE_REPLACEMENT */);
        }
        this._totalLen += this._buffLen;
        this._wrapUp();
      }
      return toHexString(this._h0) + toHexString(this._h1) + toHexString(this._h2) + toHexString(this._h3) + toHexString(this._h4);
    }
    _wrapUp() {
      this._buff[this._buffLen++] = 128;
      fill(this._buff, this._buffLen);
      if (this._buffLen > 56) {
        this._step();
        fill(this._buff);
      }
      const ml = 8 * this._totalLen;
      this._buffDV.setUint32(56, Math.floor(ml / 4294967296), false);
      this._buffDV.setUint32(60, ml % 4294967296, false);
      this._step();
    }
    _step() {
      const bigBlock32 = _StringSHA1._bigBlock32;
      const data = this._buffDV;
      for (let j = 0; j < 64; j += 4) {
        bigBlock32.setUint32(j, data.getUint32(j, false), false);
      }
      for (let j = 64; j < 320; j += 4) {
        bigBlock32.setUint32(j, leftRotate(bigBlock32.getUint32(j - 12, false) ^ bigBlock32.getUint32(j - 32, false) ^ bigBlock32.getUint32(j - 56, false) ^ bigBlock32.getUint32(j - 64, false), 1), false);
      }
      let a = this._h0;
      let b = this._h1;
      let c = this._h2;
      let d = this._h3;
      let e = this._h4;
      let f, k;
      let temp;
      for (let j = 0; j < 80; j++) {
        if (j < 20) {
          f = b & c | ~b & d;
          k = 1518500249;
        } else if (j < 40) {
          f = b ^ c ^ d;
          k = 1859775393;
        } else if (j < 60) {
          f = b & c | b & d | c & d;
          k = 2400959708;
        } else {
          f = b ^ c ^ d;
          k = 3395469782;
        }
        temp = leftRotate(a, 5) + f + e + k + bigBlock32.getUint32(j * 4, false) & 4294967295;
        e = d;
        d = c;
        c = leftRotate(b, 30);
        b = a;
        a = temp;
      }
      this._h0 = this._h0 + a & 4294967295;
      this._h1 = this._h1 + b & 4294967295;
      this._h2 = this._h2 + c & 4294967295;
      this._h3 = this._h3 + d & 4294967295;
      this._h4 = this._h4 + e & 4294967295;
    }
  };
  _StringSHA1._bigBlock32 = new DataView(new ArrayBuffer(320));
  var StringSHA1 = _StringSHA1;

  // node_modules/@xterm/xterm/src/vs/base/browser/dom.ts
  var {
    registerWindow,
    getWindow,
    getDocument,
    getWindows,
    getWindowsCount,
    getWindowId,
    getWindowById,
    hasWindow,
    onDidRegisterWindow,
    onWillUnregisterWindow,
    onDidUnregisterWindow
  } = (function() {
    const windows = /* @__PURE__ */ new Map();
    ensureCodeWindow(mainWindow, 1);
    const mainWindowRegistration = { window: mainWindow, disposables: new DisposableStore() };
    windows.set(mainWindow.vscodeWindowId, mainWindowRegistration);
    const onDidRegisterWindow2 = new Emitter();
    const onDidUnregisterWindow2 = new Emitter();
    const onWillUnregisterWindow2 = new Emitter();
    function getWindowById2(windowId, fallbackToMain) {
      const window2 = typeof windowId === "number" ? windows.get(windowId) : void 0;
      return window2 ?? (fallbackToMain ? mainWindowRegistration : void 0);
    }
    return {
      onDidRegisterWindow: onDidRegisterWindow2.event,
      onWillUnregisterWindow: onWillUnregisterWindow2.event,
      onDidUnregisterWindow: onDidUnregisterWindow2.event,
      registerWindow(window2) {
        if (windows.has(window2.vscodeWindowId)) {
          return Disposable.None;
        }
        const disposables = new DisposableStore();
        const registeredWindow = {
          window: window2,
          disposables: disposables.add(new DisposableStore())
        };
        windows.set(window2.vscodeWindowId, registeredWindow);
        disposables.add(toDisposable(() => {
          windows.delete(window2.vscodeWindowId);
          onDidUnregisterWindow2.fire(window2);
        }));
        disposables.add(addDisposableListener(window2, EventType.BEFORE_UNLOAD, () => {
          onWillUnregisterWindow2.fire(window2);
        }));
        onDidRegisterWindow2.fire(registeredWindow);
        return disposables;
      },
      getWindows() {
        return windows.values();
      },
      getWindowsCount() {
        return windows.size;
      },
      getWindowId(targetWindow) {
        return targetWindow.vscodeWindowId;
      },
      hasWindow(windowId) {
        return windows.has(windowId);
      },
      getWindowById: getWindowById2,
      getWindow(e) {
        const candidateNode = e;
        if (candidateNode?.ownerDocument?.defaultView) {
          return candidateNode.ownerDocument.defaultView.window;
        }
        const candidateEvent = e;
        if (candidateEvent?.view) {
          return candidateEvent.view.window;
        }
        return mainWindow;
      },
      getDocument(e) {
        const candidateNode = e;
        return getWindow(candidateNode).document;
      }
    };
  })();
  var DomListener = class {
    constructor(node, type, handler, options) {
      this._node = node;
      this._type = type;
      this._handler = handler;
      this._options = options || false;
      this._node.addEventListener(this._type, this._handler, this._options);
    }
    dispose() {
      if (!this._handler) {
        return;
      }
      this._node.removeEventListener(this._type, this._handler, this._options);
      this._node = null;
      this._handler = null;
    }
  };
  function addDisposableListener(node, type, handler, useCaptureOrOptions) {
    return new DomListener(node, type, handler, useCaptureOrOptions);
  }
  function _wrapAsStandardMouseEvent(targetWindow, handler) {
    return function(e) {
      return handler(new StandardMouseEvent(targetWindow, e));
    };
  }
  function _wrapAsStandardKeyboardEvent(handler) {
    return function(e) {
      return handler(new StandardKeyboardEvent(e));
    };
  }
  var addStandardDisposableListener = function addStandardDisposableListener2(node, type, handler, useCapture) {
    let wrapHandler = handler;
    if (type === "click" || type === "mousedown" || type === "contextmenu") {
      wrapHandler = _wrapAsStandardMouseEvent(getWindow(node), handler);
    } else if (type === "keydown" || type === "keypress" || type === "keyup") {
      wrapHandler = _wrapAsStandardKeyboardEvent(handler);
    }
    return addDisposableListener(node, type, wrapHandler, useCapture);
  };
  var runAtThisOrScheduleAtNextAnimationFrame;
  var scheduleAtNextAnimationFrame;
  var WindowIntervalTimer = class extends IntervalTimer {
    /**
     *
     * @param node The optional node from which the target window is determined
     */
    constructor(node) {
      super();
      this.defaultTarget = node && getWindow(node);
    }
    cancelAndSet(runner, interval, targetWindow) {
      return super.cancelAndSet(runner, interval, targetWindow ?? this.defaultTarget);
    }
  };
  var AnimationFrameQueueItem = class {
    constructor(runner, priority = 0) {
      this._runner = runner;
      this.priority = priority;
      this._canceled = false;
    }
    dispose() {
      this._canceled = true;
    }
    execute() {
      if (this._canceled) {
        return;
      }
      try {
        this._runner();
      } catch (e) {
        onUnexpectedError(e);
      }
    }
    // Sort by priority (largest to lowest)
    static sort(a, b) {
      return b.priority - a.priority;
    }
  };
  (function() {
    const NEXT_QUEUE = /* @__PURE__ */ new Map();
    const CURRENT_QUEUE = /* @__PURE__ */ new Map();
    const animFrameRequested = /* @__PURE__ */ new Map();
    const inAnimationFrameRunner = /* @__PURE__ */ new Map();
    const animationFrameRunner = (targetWindowId) => {
      animFrameRequested.set(targetWindowId, false);
      const currentQueue = NEXT_QUEUE.get(targetWindowId) ?? [];
      CURRENT_QUEUE.set(targetWindowId, currentQueue);
      NEXT_QUEUE.set(targetWindowId, []);
      inAnimationFrameRunner.set(targetWindowId, true);
      while (currentQueue.length > 0) {
        currentQueue.sort(AnimationFrameQueueItem.sort);
        const top = currentQueue.shift();
        top.execute();
      }
      inAnimationFrameRunner.set(targetWindowId, false);
    };
    scheduleAtNextAnimationFrame = (targetWindow, runner, priority = 0) => {
      const targetWindowId = getWindowId(targetWindow);
      const item = new AnimationFrameQueueItem(runner, priority);
      let nextQueue = NEXT_QUEUE.get(targetWindowId);
      if (!nextQueue) {
        nextQueue = [];
        NEXT_QUEUE.set(targetWindowId, nextQueue);
      }
      nextQueue.push(item);
      if (!animFrameRequested.get(targetWindowId)) {
        animFrameRequested.set(targetWindowId, true);
        targetWindow.requestAnimationFrame(() => animationFrameRunner(targetWindowId));
      }
      return item;
    };
    runAtThisOrScheduleAtNextAnimationFrame = (targetWindow, runner, priority) => {
      const targetWindowId = getWindowId(targetWindow);
      if (inAnimationFrameRunner.get(targetWindowId)) {
        const item = new AnimationFrameQueueItem(runner, priority);
        let currentQueue = CURRENT_QUEUE.get(targetWindowId);
        if (!currentQueue) {
          currentQueue = [];
          CURRENT_QUEUE.set(targetWindowId, currentQueue);
        }
        currentQueue.push(item);
        return item;
      } else {
        return scheduleAtNextAnimationFrame(targetWindow, runner, priority);
      }
    };
  })();
  var _Dimension = class _Dimension {
    constructor(width, height) {
      this.width = width;
      this.height = height;
    }
    with(width = this.width, height = this.height) {
      if (width !== this.width || height !== this.height) {
        return new _Dimension(width, height);
      } else {
        return this;
      }
    }
    static is(obj) {
      return typeof obj === "object" && typeof obj.height === "number" && typeof obj.width === "number";
    }
    static lift(obj) {
      if (obj instanceof _Dimension) {
        return obj;
      } else {
        return new _Dimension(obj.width, obj.height);
      }
    }
    static equals(a, b) {
      if (a === b) {
        return true;
      }
      if (!a || !b) {
        return false;
      }
      return a.width === b.width && a.height === b.height;
    }
  };
  _Dimension.None = new _Dimension(0, 0);
  var Dimension = _Dimension;
  function getDomNodePagePosition(domNode) {
    const bb = domNode.getBoundingClientRect();
    const window2 = getWindow(domNode);
    return {
      left: bb.left + window2.scrollX,
      top: bb.top + window2.scrollY,
      width: bb.width,
      height: bb.height
    };
  }
  var sharedMutationObserver = new class {
    constructor() {
      this.mutationObservers = /* @__PURE__ */ new Map();
    }
    observe(target, disposables, options) {
      let mutationObserversPerTarget = this.mutationObservers.get(target);
      if (!mutationObserversPerTarget) {
        mutationObserversPerTarget = /* @__PURE__ */ new Map();
        this.mutationObservers.set(target, mutationObserversPerTarget);
      }
      const optionsHash = hash(options);
      let mutationObserverPerOptions = mutationObserversPerTarget.get(optionsHash);
      if (!mutationObserverPerOptions) {
        const onDidMutate = new Emitter();
        const observer = new MutationObserver((mutations) => onDidMutate.fire(mutations));
        observer.observe(target, options);
        const resolvedMutationObserverPerOptions = mutationObserverPerOptions = {
          users: 1,
          observer,
          onDidMutate: onDidMutate.event
        };
        disposables.add(toDisposable(() => {
          resolvedMutationObserverPerOptions.users -= 1;
          if (resolvedMutationObserverPerOptions.users === 0) {
            onDidMutate.dispose();
            observer.disconnect();
            mutationObserversPerTarget?.delete(optionsHash);
            if (mutationObserversPerTarget?.size === 0) {
              this.mutationObservers.delete(target);
            }
          }
        }));
        mutationObserversPerTarget.set(optionsHash, mutationObserverPerOptions);
      } else {
        mutationObserverPerOptions.users += 1;
      }
      return mutationObserverPerOptions.onDidMutate;
    }
  }();
  var EventType = {
    // Mouse
    CLICK: "click",
    AUXCLICK: "auxclick",
    DBLCLICK: "dblclick",
    MOUSE_UP: "mouseup",
    MOUSE_DOWN: "mousedown",
    MOUSE_OVER: "mouseover",
    MOUSE_MOVE: "mousemove",
    MOUSE_OUT: "mouseout",
    MOUSE_ENTER: "mouseenter",
    MOUSE_LEAVE: "mouseleave",
    MOUSE_WHEEL: "wheel",
    POINTER_UP: "pointerup",
    POINTER_DOWN: "pointerdown",
    POINTER_MOVE: "pointermove",
    POINTER_LEAVE: "pointerleave",
    CONTEXT_MENU: "contextmenu",
    WHEEL: "wheel",
    // Keyboard
    KEY_DOWN: "keydown",
    KEY_PRESS: "keypress",
    KEY_UP: "keyup",
    // HTML Document
    LOAD: "load",
    BEFORE_UNLOAD: "beforeunload",
    UNLOAD: "unload",
    PAGE_SHOW: "pageshow",
    PAGE_HIDE: "pagehide",
    PASTE: "paste",
    ABORT: "abort",
    ERROR: "error",
    RESIZE: "resize",
    SCROLL: "scroll",
    FULLSCREEN_CHANGE: "fullscreenchange",
    WK_FULLSCREEN_CHANGE: "webkitfullscreenchange",
    // Form
    SELECT: "select",
    CHANGE: "change",
    SUBMIT: "submit",
    RESET: "reset",
    FOCUS: "focus",
    FOCUS_IN: "focusin",
    FOCUS_OUT: "focusout",
    BLUR: "blur",
    INPUT: "input",
    // Local Storage
    STORAGE: "storage",
    // Drag
    DRAG_START: "dragstart",
    DRAG: "drag",
    DRAG_ENTER: "dragenter",
    DRAG_LEAVE: "dragleave",
    DRAG_OVER: "dragover",
    DROP: "drop",
    DRAG_END: "dragend",
    // Animation
    ANIMATION_START: isWebKit ? "webkitAnimationStart" : "animationstart",
    ANIMATION_END: isWebKit ? "webkitAnimationEnd" : "animationend",
    ANIMATION_ITERATION: isWebKit ? "webkitAnimationIteration" : "animationiteration"
  };
  var SELECTOR_REGEX = /([\w\-]+)?(#([\w\-]+))?((\.([\w\-]+))*)/;
  function _$(namespace, description, attrs, ...children) {
    const match = SELECTOR_REGEX.exec(description);
    if (!match) {
      throw new Error("Bad use of emmet");
    }
    const tagName = match[1] || "div";
    let result;
    if (namespace !== "http://www.w3.org/1999/xhtml" /* HTML */) {
      result = document.createElementNS(namespace, tagName);
    } else {
      result = document.createElement(tagName);
    }
    if (match[3]) {
      result.id = match[3];
    }
    if (match[4]) {
      result.className = match[4].replace(/\./g, " ").trim();
    }
    if (attrs) {
      Object.entries(attrs).forEach(([name, value]) => {
        if (typeof value === "undefined") {
          return;
        }
        if (/^on\w+$/.test(name)) {
          result[name] = value;
        } else if (name === "selected") {
          if (value) {
            result.setAttribute(name, "true");
          }
        } else {
          result.setAttribute(name, value);
        }
      });
    }
    result.append(...children);
    return result;
  }
  function $(description, attrs, ...children) {
    return _$("http://www.w3.org/1999/xhtml" /* HTML */, description, attrs, ...children);
  }
  $.SVG = function(description, attrs, ...children) {
    return _$("http://www.w3.org/2000/svg" /* SVG */, description, attrs, ...children);
  };

  // node_modules/@xterm/xterm/src/vs/base/browser/fastDomNode.ts
  var FastDomNode = class {
    constructor(domNode) {
      this.domNode = domNode;
      this._maxWidth = "";
      this._width = "";
      this._height = "";
      this._top = "";
      this._left = "";
      this._bottom = "";
      this._right = "";
      this._paddingTop = "";
      this._paddingLeft = "";
      this._paddingBottom = "";
      this._paddingRight = "";
      this._fontFamily = "";
      this._fontWeight = "";
      this._fontSize = "";
      this._fontStyle = "";
      this._fontFeatureSettings = "";
      this._fontVariationSettings = "";
      this._textDecoration = "";
      this._lineHeight = "";
      this._letterSpacing = "";
      this._className = "";
      this._display = "";
      this._position = "";
      this._visibility = "";
      this._color = "";
      this._backgroundColor = "";
      this._layerHint = false;
      this._contain = "none";
      this._boxShadow = "";
    }
    setMaxWidth(_maxWidth) {
      const maxWidth = numberAsPixels(_maxWidth);
      if (this._maxWidth === maxWidth) {
        return;
      }
      this._maxWidth = maxWidth;
      this.domNode.style.maxWidth = this._maxWidth;
    }
    setWidth(_width) {
      const width = numberAsPixels(_width);
      if (this._width === width) {
        return;
      }
      this._width = width;
      this.domNode.style.width = this._width;
    }
    setHeight(_height) {
      const height = numberAsPixels(_height);
      if (this._height === height) {
        return;
      }
      this._height = height;
      this.domNode.style.height = this._height;
    }
    setTop(_top) {
      const top = numberAsPixels(_top);
      if (this._top === top) {
        return;
      }
      this._top = top;
      this.domNode.style.top = this._top;
    }
    setLeft(_left) {
      const left = numberAsPixels(_left);
      if (this._left === left) {
        return;
      }
      this._left = left;
      this.domNode.style.left = this._left;
    }
    setBottom(_bottom) {
      const bottom = numberAsPixels(_bottom);
      if (this._bottom === bottom) {
        return;
      }
      this._bottom = bottom;
      this.domNode.style.bottom = this._bottom;
    }
    setRight(_right) {
      const right = numberAsPixels(_right);
      if (this._right === right) {
        return;
      }
      this._right = right;
      this.domNode.style.right = this._right;
    }
    setPaddingTop(_paddingTop) {
      const paddingTop = numberAsPixels(_paddingTop);
      if (this._paddingTop === paddingTop) {
        return;
      }
      this._paddingTop = paddingTop;
      this.domNode.style.paddingTop = this._paddingTop;
    }
    setPaddingLeft(_paddingLeft) {
      const paddingLeft = numberAsPixels(_paddingLeft);
      if (this._paddingLeft === paddingLeft) {
        return;
      }
      this._paddingLeft = paddingLeft;
      this.domNode.style.paddingLeft = this._paddingLeft;
    }
    setPaddingBottom(_paddingBottom) {
      const paddingBottom = numberAsPixels(_paddingBottom);
      if (this._paddingBottom === paddingBottom) {
        return;
      }
      this._paddingBottom = paddingBottom;
      this.domNode.style.paddingBottom = this._paddingBottom;
    }
    setPaddingRight(_paddingRight) {
      const paddingRight = numberAsPixels(_paddingRight);
      if (this._paddingRight === paddingRight) {
        return;
      }
      this._paddingRight = paddingRight;
      this.domNode.style.paddingRight = this._paddingRight;
    }
    setFontFamily(fontFamily) {
      if (this._fontFamily === fontFamily) {
        return;
      }
      this._fontFamily = fontFamily;
      this.domNode.style.fontFamily = this._fontFamily;
    }
    setFontWeight(fontWeight) {
      if (this._fontWeight === fontWeight) {
        return;
      }
      this._fontWeight = fontWeight;
      this.domNode.style.fontWeight = this._fontWeight;
    }
    setFontSize(_fontSize) {
      const fontSize = numberAsPixels(_fontSize);
      if (this._fontSize === fontSize) {
        return;
      }
      this._fontSize = fontSize;
      this.domNode.style.fontSize = this._fontSize;
    }
    setFontStyle(fontStyle) {
      if (this._fontStyle === fontStyle) {
        return;
      }
      this._fontStyle = fontStyle;
      this.domNode.style.fontStyle = this._fontStyle;
    }
    setFontFeatureSettings(fontFeatureSettings) {
      if (this._fontFeatureSettings === fontFeatureSettings) {
        return;
      }
      this._fontFeatureSettings = fontFeatureSettings;
      this.domNode.style.fontFeatureSettings = this._fontFeatureSettings;
    }
    setFontVariationSettings(fontVariationSettings) {
      if (this._fontVariationSettings === fontVariationSettings) {
        return;
      }
      this._fontVariationSettings = fontVariationSettings;
      this.domNode.style.fontVariationSettings = this._fontVariationSettings;
    }
    setTextDecoration(textDecoration) {
      if (this._textDecoration === textDecoration) {
        return;
      }
      this._textDecoration = textDecoration;
      this.domNode.style.textDecoration = this._textDecoration;
    }
    setLineHeight(_lineHeight) {
      const lineHeight = numberAsPixels(_lineHeight);
      if (this._lineHeight === lineHeight) {
        return;
      }
      this._lineHeight = lineHeight;
      this.domNode.style.lineHeight = this._lineHeight;
    }
    setLetterSpacing(_letterSpacing) {
      const letterSpacing = numberAsPixels(_letterSpacing);
      if (this._letterSpacing === letterSpacing) {
        return;
      }
      this._letterSpacing = letterSpacing;
      this.domNode.style.letterSpacing = this._letterSpacing;
    }
    setClassName(className) {
      if (this._className === className) {
        return;
      }
      this._className = className;
      this.domNode.className = this._className;
    }
    toggleClassName(className, shouldHaveIt) {
      this.domNode.classList.toggle(className, shouldHaveIt);
      this._className = this.domNode.className;
    }
    setDisplay(display) {
      if (this._display === display) {
        return;
      }
      this._display = display;
      this.domNode.style.display = this._display;
    }
    setPosition(position) {
      if (this._position === position) {
        return;
      }
      this._position = position;
      this.domNode.style.position = this._position;
    }
    setVisibility(visibility) {
      if (this._visibility === visibility) {
        return;
      }
      this._visibility = visibility;
      this.domNode.style.visibility = this._visibility;
    }
    setColor(color2) {
      if (this._color === color2) {
        return;
      }
      this._color = color2;
      this.domNode.style.color = this._color;
    }
    setBackgroundColor(backgroundColor) {
      if (this._backgroundColor === backgroundColor) {
        return;
      }
      this._backgroundColor = backgroundColor;
      this.domNode.style.backgroundColor = this._backgroundColor;
    }
    setLayerHinting(layerHint) {
      if (this._layerHint === layerHint) {
        return;
      }
      this._layerHint = layerHint;
      this.domNode.style.transform = this._layerHint ? "translate3d(0px, 0px, 0px)" : "";
    }
    setBoxShadow(boxShadow) {
      if (this._boxShadow === boxShadow) {
        return;
      }
      this._boxShadow = boxShadow;
      this.domNode.style.boxShadow = boxShadow;
    }
    setContain(contain) {
      if (this._contain === contain) {
        return;
      }
      this._contain = contain;
      this.domNode.style.contain = this._contain;
    }
    setAttribute(name, value) {
      this.domNode.setAttribute(name, value);
    }
    removeAttribute(name) {
      this.domNode.removeAttribute(name);
    }
    appendChild(child) {
      this.domNode.appendChild(child.domNode);
    }
    removeChild(child) {
      this.domNode.removeChild(child.domNode);
    }
  };
  function numberAsPixels(value) {
    return typeof value === "number" ? `${value}px` : value;
  }
  function createFastDomNode(domNode) {
    return new FastDomNode(domNode);
  }

  // node_modules/@xterm/xterm/src/vs/base/browser/globalPointerMoveMonitor.ts
  var GlobalPointerMoveMonitor = class {
    constructor() {
      this._hooks = new DisposableStore();
      this._pointerMoveCallback = null;
      this._onStopCallback = null;
    }
    dispose() {
      this.stopMonitoring(false);
      this._hooks.dispose();
    }
    stopMonitoring(invokeStopCallback, browserEvent) {
      if (!this.isMonitoring()) {
        return;
      }
      this._hooks.clear();
      this._pointerMoveCallback = null;
      const onStopCallback = this._onStopCallback;
      this._onStopCallback = null;
      if (invokeStopCallback && onStopCallback) {
        onStopCallback(browserEvent);
      }
    }
    isMonitoring() {
      return !!this._pointerMoveCallback;
    }
    startMonitoring(initialElement, pointerId, initialButtons, pointerMoveCallback, onStopCallback) {
      if (this.isMonitoring()) {
        this.stopMonitoring(false);
      }
      this._pointerMoveCallback = pointerMoveCallback;
      this._onStopCallback = onStopCallback;
      let eventSource = initialElement;
      try {
        initialElement.setPointerCapture(pointerId);
        this._hooks.add(toDisposable(() => {
          try {
            initialElement.releasePointerCapture(pointerId);
          } catch (err) {
          }
        }));
      } catch (err) {
        eventSource = getWindow(initialElement);
      }
      this._hooks.add(addDisposableListener(
        eventSource,
        EventType.POINTER_MOVE,
        (e) => {
          if (e.buttons !== initialButtons) {
            this.stopMonitoring(true);
            return;
          }
          e.preventDefault();
          this._pointerMoveCallback(e);
        }
      ));
      this._hooks.add(addDisposableListener(
        eventSource,
        EventType.POINTER_UP,
        (e) => this.stopMonitoring(true)
      ));
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/common/decorators.ts
  function memoize(_target, key, descriptor) {
    let fnKey = null;
    let fn = null;
    if (typeof descriptor.value === "function") {
      fnKey = "value";
      fn = descriptor.value;
      if (fn.length !== 0) {
        console.warn("Memoize should only be used in functions with zero parameters");
      }
    } else if (typeof descriptor.get === "function") {
      fnKey = "get";
      fn = descriptor.get;
    }
    if (!fn) {
      throw new Error("not supported");
    }
    const memoizeKey = `$memoize$${key}`;
    descriptor[fnKey] = function(...args) {
      if (!this.hasOwnProperty(memoizeKey)) {
        Object.defineProperty(this, memoizeKey, {
          configurable: false,
          enumerable: false,
          writable: false,
          value: fn.apply(this, args)
        });
      }
      return this[memoizeKey];
    };
  }

  // node_modules/@xterm/xterm/src/vs/base/browser/touch.ts
  var EventType2;
  ((EventType3) => {
    EventType3.Tap = "-xterm-gesturetap";
    EventType3.Change = "-xterm-gesturechange";
    EventType3.Start = "-xterm-gesturestart";
    EventType3.End = "-xterm-gesturesend";
    EventType3.Contextmenu = "-xterm-gesturecontextmenu";
  })(EventType2 || (EventType2 = {}));
  var _Gesture = class _Gesture extends Disposable {
    // ms
    constructor() {
      super();
      this.dispatched = false;
      this.targets = new LinkedList();
      this.ignoreTargets = new LinkedList();
      this.activeTouches = {};
      this.handle = null;
      this._lastSetTapCountTime = 0;
      this._register(Event.runAndSubscribe(onDidRegisterWindow, ({ window: window2, disposables }) => {
        disposables.add(addDisposableListener(window2.document, "touchstart", (e) => this.onTouchStart(e), { passive: false }));
        disposables.add(addDisposableListener(window2.document, "touchend", (e) => this.onTouchEnd(window2, e)));
        disposables.add(addDisposableListener(window2.document, "touchmove", (e) => this.onTouchMove(e), { passive: false }));
      }, { window: mainWindow, disposables: this._store }));
    }
    static addTarget(element) {
      if (!_Gesture.isTouchDevice()) {
        return Disposable.None;
      }
      if (!_Gesture.INSTANCE) {
        _Gesture.INSTANCE = markAsSingleton(new _Gesture());
      }
      const remove = _Gesture.INSTANCE.targets.push(element);
      return toDisposable(remove);
    }
    static ignoreTarget(element) {
      if (!_Gesture.isTouchDevice()) {
        return Disposable.None;
      }
      if (!_Gesture.INSTANCE) {
        _Gesture.INSTANCE = markAsSingleton(new _Gesture());
      }
      const remove = _Gesture.INSTANCE.ignoreTargets.push(element);
      return toDisposable(remove);
    }
    static isTouchDevice() {
      return "ontouchstart" in mainWindow || navigator.maxTouchPoints > 0;
    }
    dispose() {
      if (this.handle) {
        this.handle.dispose();
        this.handle = null;
      }
      super.dispose();
    }
    onTouchStart(e) {
      const timestamp = Date.now();
      if (this.handle) {
        this.handle.dispose();
        this.handle = null;
      }
      for (let i2 = 0, len = e.targetTouches.length; i2 < len; i2++) {
        const touch = e.targetTouches.item(i2);
        this.activeTouches[touch.identifier] = {
          id: touch.identifier,
          initialTarget: touch.target,
          initialTimeStamp: timestamp,
          initialPageX: touch.pageX,
          initialPageY: touch.pageY,
          rollingTimestamps: [timestamp],
          rollingPageX: [touch.pageX],
          rollingPageY: [touch.pageY]
        };
        const evt = this.newGestureEvent(EventType2.Start, touch.target);
        evt.pageX = touch.pageX;
        evt.pageY = touch.pageY;
        this.dispatchEvent(evt);
      }
      if (this.dispatched) {
        e.preventDefault();
        e.stopPropagation();
        this.dispatched = false;
      }
    }
    onTouchEnd(targetWindow, e) {
      const timestamp = Date.now();
      const activeTouchCount = Object.keys(this.activeTouches).length;
      for (let i2 = 0, len = e.changedTouches.length; i2 < len; i2++) {
        const touch = e.changedTouches.item(i2);
        if (!this.activeTouches.hasOwnProperty(String(touch.identifier))) {
          console.warn("move of an UNKNOWN touch", touch);
          continue;
        }
        const data = this.activeTouches[touch.identifier], holdTime = Date.now() - data.initialTimeStamp;
        if (holdTime < _Gesture.HOLD_DELAY && Math.abs(data.initialPageX - tail(data.rollingPageX)) < 30 && Math.abs(data.initialPageY - tail(data.rollingPageY)) < 30) {
          const evt = this.newGestureEvent(EventType2.Tap, data.initialTarget);
          evt.pageX = tail(data.rollingPageX);
          evt.pageY = tail(data.rollingPageY);
          this.dispatchEvent(evt);
        } else if (holdTime >= _Gesture.HOLD_DELAY && Math.abs(data.initialPageX - tail(data.rollingPageX)) < 30 && Math.abs(data.initialPageY - tail(data.rollingPageY)) < 30) {
          const evt = this.newGestureEvent(EventType2.Contextmenu, data.initialTarget);
          evt.pageX = tail(data.rollingPageX);
          evt.pageY = tail(data.rollingPageY);
          this.dispatchEvent(evt);
        } else if (activeTouchCount === 1) {
          const finalX = tail(data.rollingPageX);
          const finalY = tail(data.rollingPageY);
          const deltaT = tail(data.rollingTimestamps) - data.rollingTimestamps[0];
          const deltaX = finalX - data.rollingPageX[0];
          const deltaY = finalY - data.rollingPageY[0];
          const dispatchTo = [...this.targets].filter((t) => data.initialTarget instanceof Node && t.contains(data.initialTarget));
          this.inertia(
            targetWindow,
            dispatchTo,
            timestamp,
            // time now
            Math.abs(deltaX) / deltaT,
            // speed
            deltaX > 0 ? 1 : -1,
            // x direction
            finalX,
            // x now
            Math.abs(deltaY) / deltaT,
            // y speed
            deltaY > 0 ? 1 : -1,
            // y direction
            finalY
            // y now
          );
        }
        this.dispatchEvent(this.newGestureEvent(EventType2.End, data.initialTarget));
        delete this.activeTouches[touch.identifier];
      }
      if (this.dispatched) {
        e.preventDefault();
        e.stopPropagation();
        this.dispatched = false;
      }
    }
    newGestureEvent(type, initialTarget) {
      const event = document.createEvent("CustomEvent");
      event.initEvent(type, false, true);
      event.initialTarget = initialTarget;
      event.tapCount = 0;
      return event;
    }
    dispatchEvent(event) {
      if (event.type === EventType2.Tap) {
        const currentTime = (/* @__PURE__ */ new Date()).getTime();
        let setTapCount = 0;
        if (currentTime - this._lastSetTapCountTime > _Gesture.CLEAR_TAP_COUNT_TIME) {
          setTapCount = 1;
        } else {
          setTapCount = 2;
        }
        this._lastSetTapCountTime = currentTime;
        event.tapCount = setTapCount;
      } else if (event.type === EventType2.Change || event.type === EventType2.Contextmenu) {
        this._lastSetTapCountTime = 0;
      }
      if (event.initialTarget instanceof Node) {
        for (const ignoreTarget of this.ignoreTargets) {
          if (ignoreTarget.contains(event.initialTarget)) {
            return;
          }
        }
        const targets = [];
        for (const target of this.targets) {
          if (target.contains(event.initialTarget)) {
            let depth = 0;
            let now = event.initialTarget;
            while (now && now !== target) {
              depth++;
              now = now.parentElement;
            }
            targets.push([depth, target]);
          }
        }
        targets.sort((a, b) => a[0] - b[0]);
        for (const [_, target] of targets) {
          target.dispatchEvent(event);
          this.dispatched = true;
        }
      }
    }
    inertia(targetWindow, dispatchTo, t1, vX, dirX, x, vY, dirY, y) {
      this.handle = scheduleAtNextAnimationFrame(targetWindow, () => {
        const now = Date.now();
        const deltaT = now - t1;
        let delta_pos_x = 0, delta_pos_y = 0;
        let stopped = true;
        vX += _Gesture.SCROLL_FRICTION * deltaT;
        vY += _Gesture.SCROLL_FRICTION * deltaT;
        if (vX > 0) {
          stopped = false;
          delta_pos_x = dirX * vX * deltaT;
        }
        if (vY > 0) {
          stopped = false;
          delta_pos_y = dirY * vY * deltaT;
        }
        const evt = this.newGestureEvent(EventType2.Change);
        evt.translationX = delta_pos_x;
        evt.translationY = delta_pos_y;
        dispatchTo.forEach((d) => d.dispatchEvent(evt));
        if (!stopped) {
          this.inertia(targetWindow, dispatchTo, now, vX, dirX, x + delta_pos_x, vY, dirY, y + delta_pos_y);
        }
      });
    }
    onTouchMove(e) {
      const timestamp = Date.now();
      for (let i2 = 0, len = e.changedTouches.length; i2 < len; i2++) {
        const touch = e.changedTouches.item(i2);
        if (!this.activeTouches.hasOwnProperty(String(touch.identifier))) {
          console.warn("end of an UNKNOWN touch", touch);
          continue;
        }
        const data = this.activeTouches[touch.identifier];
        const evt = this.newGestureEvent(EventType2.Change, data.initialTarget);
        evt.translationX = touch.pageX - tail(data.rollingPageX);
        evt.translationY = touch.pageY - tail(data.rollingPageY);
        evt.pageX = touch.pageX;
        evt.pageY = touch.pageY;
        this.dispatchEvent(evt);
        if (data.rollingPageX.length > 3) {
          data.rollingPageX.shift();
          data.rollingPageY.shift();
          data.rollingTimestamps.shift();
        }
        data.rollingPageX.push(touch.pageX);
        data.rollingPageY.push(touch.pageY);
        data.rollingTimestamps.push(timestamp);
      }
      if (this.dispatched) {
        e.preventDefault();
        e.stopPropagation();
        this.dispatched = false;
      }
    }
  };
  _Gesture.SCROLL_FRICTION = -5e-3;
  _Gesture.HOLD_DELAY = 700;
  _Gesture.CLEAR_TAP_COUNT_TIME = 400;
  __decorateClass([
    memoize
  ], _Gesture, "isTouchDevice", 1);
  var Gesture = _Gesture;

  // node_modules/@xterm/xterm/src/vs/base/browser/ui/widget.ts
  var Widget = class extends Disposable {
    onclick(domNode, listener) {
      this._register(addDisposableListener(domNode, EventType.CLICK, (e) => listener(new StandardMouseEvent(getWindow(domNode), e))));
    }
    onmousedown(domNode, listener) {
      this._register(addDisposableListener(domNode, EventType.MOUSE_DOWN, (e) => listener(new StandardMouseEvent(getWindow(domNode), e))));
    }
    onmouseover(domNode, listener) {
      this._register(addDisposableListener(domNode, EventType.MOUSE_OVER, (e) => listener(new StandardMouseEvent(getWindow(domNode), e))));
    }
    onmouseleave(domNode, listener) {
      this._register(addDisposableListener(domNode, EventType.MOUSE_LEAVE, (e) => listener(new StandardMouseEvent(getWindow(domNode), e))));
    }
    onkeydown(domNode, listener) {
      this._register(addDisposableListener(domNode, EventType.KEY_DOWN, (e) => listener(new StandardKeyboardEvent(e))));
    }
    onkeyup(domNode, listener) {
      this._register(addDisposableListener(domNode, EventType.KEY_UP, (e) => listener(new StandardKeyboardEvent(e))));
    }
    oninput(domNode, listener) {
      this._register(addDisposableListener(domNode, EventType.INPUT, listener));
    }
    onblur(domNode, listener) {
      this._register(addDisposableListener(domNode, EventType.BLUR, listener));
    }
    onfocus(domNode, listener) {
      this._register(addDisposableListener(domNode, EventType.FOCUS, listener));
    }
    onchange(domNode, listener) {
      this._register(addDisposableListener(domNode, EventType.CHANGE, listener));
    }
    ignoreGesture(domNode) {
      return Gesture.ignoreTarget(domNode);
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/browser/ui/scrollbar/scrollbarArrow.ts
  var ARROW_IMG_SIZE = 11;
  var ScrollbarArrow = class extends Widget {
    constructor(opts) {
      super();
      this._onActivate = opts.onActivate;
      this.bgDomNode = document.createElement("div");
      this.bgDomNode.className = "arrow-background";
      this.bgDomNode.style.position = "absolute";
      this.bgDomNode.style.width = opts.bgWidth + "px";
      this.bgDomNode.style.height = opts.bgHeight + "px";
      if (typeof opts.top !== "undefined") {
        this.bgDomNode.style.top = "0px";
      }
      if (typeof opts.left !== "undefined") {
        this.bgDomNode.style.left = "0px";
      }
      if (typeof opts.bottom !== "undefined") {
        this.bgDomNode.style.bottom = "0px";
      }
      if (typeof opts.right !== "undefined") {
        this.bgDomNode.style.right = "0px";
      }
      this.domNode = document.createElement("div");
      this.domNode.className = opts.className;
      this.domNode.style.position = "absolute";
      this.domNode.style.width = ARROW_IMG_SIZE + "px";
      this.domNode.style.height = ARROW_IMG_SIZE + "px";
      if (typeof opts.top !== "undefined") {
        this.domNode.style.top = opts.top + "px";
      }
      if (typeof opts.left !== "undefined") {
        this.domNode.style.left = opts.left + "px";
      }
      if (typeof opts.bottom !== "undefined") {
        this.domNode.style.bottom = opts.bottom + "px";
      }
      if (typeof opts.right !== "undefined") {
        this.domNode.style.right = opts.right + "px";
      }
      this._pointerMoveMonitor = this._register(new GlobalPointerMoveMonitor());
      this._register(addStandardDisposableListener(this.bgDomNode, EventType.POINTER_DOWN, (e) => this._arrowPointerDown(e)));
      this._register(addStandardDisposableListener(this.domNode, EventType.POINTER_DOWN, (e) => this._arrowPointerDown(e)));
      this._pointerdownRepeatTimer = this._register(new WindowIntervalTimer());
      this._pointerdownScheduleRepeatTimer = this._register(new TimeoutTimer());
    }
    _arrowPointerDown(e) {
      if (!e.target || !(e.target instanceof Element)) {
        return;
      }
      const scheduleRepeater = () => {
        this._pointerdownRepeatTimer.cancelAndSet(() => this._onActivate(), 1e3 / 24, getWindow(e));
      };
      this._onActivate();
      this._pointerdownRepeatTimer.cancel();
      this._pointerdownScheduleRepeatTimer.cancelAndSet(scheduleRepeater, 200);
      this._pointerMoveMonitor.startMonitoring(
        e.target,
        e.pointerId,
        e.buttons,
        (pointerMoveData) => {
        },
        () => {
          this._pointerdownRepeatTimer.cancel();
          this._pointerdownScheduleRepeatTimer.cancel();
        }
      );
      e.preventDefault();
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/common/scrollable.ts
  var ScrollState = class _ScrollState {
    constructor(_forceIntegerValues, width, scrollWidth, scrollLeft, height, scrollHeight, scrollTop) {
      this._forceIntegerValues = _forceIntegerValues;
      this._scrollStateBrand = void 0;
      if (this._forceIntegerValues) {
        width = width | 0;
        scrollWidth = scrollWidth | 0;
        scrollLeft = scrollLeft | 0;
        height = height | 0;
        scrollHeight = scrollHeight | 0;
        scrollTop = scrollTop | 0;
      }
      this.rawScrollLeft = scrollLeft;
      this.rawScrollTop = scrollTop;
      if (width < 0) {
        width = 0;
      }
      if (scrollLeft + width > scrollWidth) {
        scrollLeft = scrollWidth - width;
      }
      if (scrollLeft < 0) {
        scrollLeft = 0;
      }
      if (height < 0) {
        height = 0;
      }
      if (scrollTop + height > scrollHeight) {
        scrollTop = scrollHeight - height;
      }
      if (scrollTop < 0) {
        scrollTop = 0;
      }
      this.width = width;
      this.scrollWidth = scrollWidth;
      this.scrollLeft = scrollLeft;
      this.height = height;
      this.scrollHeight = scrollHeight;
      this.scrollTop = scrollTop;
    }
    equals(other) {
      return this.rawScrollLeft === other.rawScrollLeft && this.rawScrollTop === other.rawScrollTop && this.width === other.width && this.scrollWidth === other.scrollWidth && this.scrollLeft === other.scrollLeft && this.height === other.height && this.scrollHeight === other.scrollHeight && this.scrollTop === other.scrollTop;
    }
    withScrollDimensions(update, useRawScrollPositions) {
      return new _ScrollState(
        this._forceIntegerValues,
        typeof update.width !== "undefined" ? update.width : this.width,
        typeof update.scrollWidth !== "undefined" ? update.scrollWidth : this.scrollWidth,
        useRawScrollPositions ? this.rawScrollLeft : this.scrollLeft,
        typeof update.height !== "undefined" ? update.height : this.height,
        typeof update.scrollHeight !== "undefined" ? update.scrollHeight : this.scrollHeight,
        useRawScrollPositions ? this.rawScrollTop : this.scrollTop
      );
    }
    withScrollPosition(update) {
      return new _ScrollState(
        this._forceIntegerValues,
        this.width,
        this.scrollWidth,
        typeof update.scrollLeft !== "undefined" ? update.scrollLeft : this.rawScrollLeft,
        this.height,
        this.scrollHeight,
        typeof update.scrollTop !== "undefined" ? update.scrollTop : this.rawScrollTop
      );
    }
    createScrollEvent(previous, inSmoothScrolling) {
      const widthChanged = this.width !== previous.width;
      const scrollWidthChanged = this.scrollWidth !== previous.scrollWidth;
      const scrollLeftChanged = this.scrollLeft !== previous.scrollLeft;
      const heightChanged = this.height !== previous.height;
      const scrollHeightChanged = this.scrollHeight !== previous.scrollHeight;
      const scrollTopChanged = this.scrollTop !== previous.scrollTop;
      return {
        inSmoothScrolling,
        oldWidth: previous.width,
        oldScrollWidth: previous.scrollWidth,
        oldScrollLeft: previous.scrollLeft,
        width: this.width,
        scrollWidth: this.scrollWidth,
        scrollLeft: this.scrollLeft,
        oldHeight: previous.height,
        oldScrollHeight: previous.scrollHeight,
        oldScrollTop: previous.scrollTop,
        height: this.height,
        scrollHeight: this.scrollHeight,
        scrollTop: this.scrollTop,
        widthChanged,
        scrollWidthChanged,
        scrollLeftChanged,
        heightChanged,
        scrollHeightChanged,
        scrollTopChanged
      };
    }
  };
  var Scrollable = class extends Disposable {
    constructor(options) {
      super();
      this._scrollableBrand = void 0;
      this._onScroll = this._register(new Emitter());
      this.onScroll = this._onScroll.event;
      this._smoothScrollDuration = options.smoothScrollDuration;
      this._scheduleAtNextAnimationFrame = options.scheduleAtNextAnimationFrame;
      this._state = new ScrollState(options.forceIntegerValues, 0, 0, 0, 0, 0, 0);
      this._smoothScrolling = null;
    }
    dispose() {
      if (this._smoothScrolling) {
        this._smoothScrolling.dispose();
        this._smoothScrolling = null;
      }
      super.dispose();
    }
    setSmoothScrollDuration(smoothScrollDuration) {
      this._smoothScrollDuration = smoothScrollDuration;
    }
    validateScrollPosition(scrollPosition) {
      return this._state.withScrollPosition(scrollPosition);
    }
    getScrollDimensions() {
      return this._state;
    }
    setScrollDimensions(dimensions, useRawScrollPositions) {
      const newState = this._state.withScrollDimensions(dimensions, useRawScrollPositions);
      this._setState(newState, Boolean(this._smoothScrolling));
      this._smoothScrolling?.acceptScrollDimensions(this._state);
    }
    /**
     * Returns the final scroll position that the instance will have once the smooth scroll animation concludes.
     * If no scroll animation is occurring, it will return the current scroll position instead.
     */
    getFutureScrollPosition() {
      if (this._smoothScrolling) {
        return this._smoothScrolling.to;
      }
      return this._state;
    }
    /**
     * Returns the current scroll position.
     * Note: This result might be an intermediate scroll position, as there might be an ongoing smooth scroll animation.
     */
    getCurrentScrollPosition() {
      return this._state;
    }
    setScrollPositionNow(update) {
      const newState = this._state.withScrollPosition(update);
      if (this._smoothScrolling) {
        this._smoothScrolling.dispose();
        this._smoothScrolling = null;
      }
      this._setState(newState, false);
    }
    setScrollPositionSmooth(update, reuseAnimation) {
      if (this._smoothScrollDuration === 0) {
        return this.setScrollPositionNow(update);
      }
      if (this._smoothScrolling) {
        update = {
          scrollLeft: typeof update.scrollLeft === "undefined" ? this._smoothScrolling.to.scrollLeft : update.scrollLeft,
          scrollTop: typeof update.scrollTop === "undefined" ? this._smoothScrolling.to.scrollTop : update.scrollTop
        };
        const validTarget = this._state.withScrollPosition(update);
        if (this._smoothScrolling.to.scrollLeft === validTarget.scrollLeft && this._smoothScrolling.to.scrollTop === validTarget.scrollTop) {
          return;
        }
        let newSmoothScrolling;
        if (reuseAnimation) {
          newSmoothScrolling = new SmoothScrollingOperation(this._smoothScrolling.from, validTarget, this._smoothScrolling.startTime, this._smoothScrolling.duration);
        } else {
          newSmoothScrolling = this._smoothScrolling.combine(this._state, validTarget, this._smoothScrollDuration);
        }
        this._smoothScrolling.dispose();
        this._smoothScrolling = newSmoothScrolling;
      } else {
        const validTarget = this._state.withScrollPosition(update);
        this._smoothScrolling = SmoothScrollingOperation.start(this._state, validTarget, this._smoothScrollDuration);
      }
      this._smoothScrolling.animationFrameDisposable = this._scheduleAtNextAnimationFrame(() => {
        if (!this._smoothScrolling) {
          return;
        }
        this._smoothScrolling.animationFrameDisposable = null;
        this._performSmoothScrolling();
      });
    }
    hasPendingScrollAnimation() {
      return Boolean(this._smoothScrolling);
    }
    _performSmoothScrolling() {
      if (!this._smoothScrolling) {
        return;
      }
      const update = this._smoothScrolling.tick();
      const newState = this._state.withScrollPosition(update);
      this._setState(newState, true);
      if (!this._smoothScrolling) {
        return;
      }
      if (update.isDone) {
        this._smoothScrolling.dispose();
        this._smoothScrolling = null;
        return;
      }
      this._smoothScrolling.animationFrameDisposable = this._scheduleAtNextAnimationFrame(() => {
        if (!this._smoothScrolling) {
          return;
        }
        this._smoothScrolling.animationFrameDisposable = null;
        this._performSmoothScrolling();
      });
    }
    _setState(newState, inSmoothScrolling) {
      const oldState = this._state;
      if (oldState.equals(newState)) {
        return;
      }
      this._state = newState;
      this._onScroll.fire(this._state.createScrollEvent(oldState, inSmoothScrolling));
    }
  };
  var SmoothScrollingUpdate = class {
    constructor(scrollLeft, scrollTop, isDone) {
      this.scrollLeft = scrollLeft;
      this.scrollTop = scrollTop;
      this.isDone = isDone;
    }
  };
  function createEaseOutCubic(from, to) {
    const delta = to - from;
    return function(completion) {
      return from + delta * easeOutCubic(completion);
    };
  }
  function createComposed(a, b, cut) {
    return function(completion) {
      if (completion < cut) {
        return a(completion / cut);
      }
      return b((completion - cut) / (1 - cut));
    };
  }
  var SmoothScrollingOperation = class _SmoothScrollingOperation {
    constructor(from, to, startTime, duration) {
      this.from = from;
      this.to = to;
      this.duration = duration;
      this.startTime = startTime;
      this.animationFrameDisposable = null;
      this._initAnimations();
    }
    _initAnimations() {
      this.scrollLeft = this._initAnimation(this.from.scrollLeft, this.to.scrollLeft, this.to.width);
      this.scrollTop = this._initAnimation(this.from.scrollTop, this.to.scrollTop, this.to.height);
    }
    _initAnimation(from, to, viewportSize) {
      const delta = Math.abs(from - to);
      if (delta > 2.5 * viewportSize) {
        let stop1, stop2;
        if (from < to) {
          stop1 = from + 0.75 * viewportSize;
          stop2 = to - 0.75 * viewportSize;
        } else {
          stop1 = from - 0.75 * viewportSize;
          stop2 = to + 0.75 * viewportSize;
        }
        return createComposed(createEaseOutCubic(from, stop1), createEaseOutCubic(stop2, to), 0.33);
      }
      return createEaseOutCubic(from, to);
    }
    dispose() {
      if (this.animationFrameDisposable !== null) {
        this.animationFrameDisposable.dispose();
        this.animationFrameDisposable = null;
      }
    }
    acceptScrollDimensions(state) {
      this.to = state.withScrollPosition(this.to);
      this._initAnimations();
    }
    tick() {
      return this._tick(Date.now());
    }
    _tick(now) {
      const completion = (now - this.startTime) / this.duration;
      if (completion < 1) {
        const newScrollLeft = this.scrollLeft(completion);
        const newScrollTop = this.scrollTop(completion);
        return new SmoothScrollingUpdate(newScrollLeft, newScrollTop, false);
      }
      return new SmoothScrollingUpdate(this.to.scrollLeft, this.to.scrollTop, true);
    }
    combine(from, to, duration) {
      return _SmoothScrollingOperation.start(from, to, duration);
    }
    static start(from, to, duration) {
      duration = duration + 10;
      const startTime = Date.now() - 10;
      return new _SmoothScrollingOperation(from, to, startTime, duration);
    }
  };
  function easeInCubic(t) {
    return Math.pow(t, 3);
  }
  function easeOutCubic(t) {
    return 1 - easeInCubic(1 - t);
  }

  // node_modules/@xterm/xterm/src/vs/base/browser/ui/scrollbar/scrollbarVisibilityController.ts
  var ScrollbarVisibilityController = class extends Disposable {
    constructor(visibility, visibleClassName, invisibleClassName) {
      super();
      this._visibility = visibility;
      this._visibleClassName = visibleClassName;
      this._invisibleClassName = invisibleClassName;
      this._domNode = null;
      this._isVisible = false;
      this._isNeeded = false;
      this._rawShouldBeVisible = false;
      this._shouldBeVisible = false;
      this._revealTimer = this._register(new TimeoutTimer());
    }
    setVisibility(visibility) {
      if (this._visibility !== visibility) {
        this._visibility = visibility;
        this._updateShouldBeVisible();
      }
    }
    // ----------------- Hide / Reveal
    setShouldBeVisible(rawShouldBeVisible) {
      this._rawShouldBeVisible = rawShouldBeVisible;
      this._updateShouldBeVisible();
    }
    _applyVisibilitySetting() {
      if (this._visibility === 2 /* Hidden */) {
        return false;
      }
      if (this._visibility === 3 /* Visible */) {
        return true;
      }
      return this._rawShouldBeVisible;
    }
    _updateShouldBeVisible() {
      const shouldBeVisible = this._applyVisibilitySetting();
      if (this._shouldBeVisible !== shouldBeVisible) {
        this._shouldBeVisible = shouldBeVisible;
        this.ensureVisibility();
      }
    }
    setIsNeeded(isNeeded) {
      if (this._isNeeded !== isNeeded) {
        this._isNeeded = isNeeded;
        this.ensureVisibility();
      }
    }
    setDomNode(domNode) {
      this._domNode = domNode;
      this._domNode.setClassName(this._invisibleClassName);
      this.setShouldBeVisible(false);
    }
    ensureVisibility() {
      if (!this._isNeeded) {
        this._hide(false);
        return;
      }
      if (this._shouldBeVisible) {
        this._reveal();
      } else {
        this._hide(true);
      }
    }
    _reveal() {
      if (this._isVisible) {
        return;
      }
      this._isVisible = true;
      this._revealTimer.setIfNotSet(() => {
        this._domNode?.setClassName(this._visibleClassName);
      }, 0);
    }
    _hide(withFadeAway) {
      this._revealTimer.cancel();
      if (!this._isVisible) {
        return;
      }
      this._isVisible = false;
      this._domNode?.setClassName(this._invisibleClassName + (withFadeAway ? " fade" : ""));
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/browser/ui/scrollbar/abstractScrollbar.ts
  var POINTER_DRAG_RESET_DISTANCE = 140;
  var AbstractScrollbar = class extends Widget {
    constructor(opts) {
      super();
      this._lazyRender = opts.lazyRender;
      this._host = opts.host;
      this._scrollable = opts.scrollable;
      this._scrollByPage = opts.scrollByPage;
      this._scrollbarState = opts.scrollbarState;
      this._visibilityController = this._register(new ScrollbarVisibilityController(opts.visibility, "visible scrollbar " + opts.extraScrollbarClassName, "invisible scrollbar " + opts.extraScrollbarClassName));
      this._visibilityController.setIsNeeded(this._scrollbarState.isNeeded());
      this._pointerMoveMonitor = this._register(new GlobalPointerMoveMonitor());
      this._shouldRender = true;
      this.domNode = createFastDomNode(document.createElement("div"));
      this.domNode.setAttribute("role", "presentation");
      this.domNode.setAttribute("aria-hidden", "true");
      this._visibilityController.setDomNode(this.domNode);
      this.domNode.setPosition("absolute");
      this._register(addDisposableListener(this.domNode.domNode, EventType.POINTER_DOWN, (e) => this._domNodePointerDown(e)));
    }
    // ----------------- creation
    /**
     * Creates the dom node for an arrow & adds it to the container
     */
    _createArrow(opts) {
      const arrow = this._register(new ScrollbarArrow(opts));
      this.domNode.domNode.appendChild(arrow.bgDomNode);
      this.domNode.domNode.appendChild(arrow.domNode);
    }
    /**
     * Creates the slider dom node, adds it to the container & hooks up the events
     */
    _createSlider(top, left, width, height) {
      this.slider = createFastDomNode(document.createElement("div"));
      this.slider.setClassName("slider");
      this.slider.setPosition("absolute");
      this.slider.setTop(top);
      this.slider.setLeft(left);
      if (typeof width === "number") {
        this.slider.setWidth(width);
      }
      if (typeof height === "number") {
        this.slider.setHeight(height);
      }
      this.slider.setLayerHinting(true);
      this.slider.setContain("strict");
      this.domNode.domNode.appendChild(this.slider.domNode);
      this._register(addDisposableListener(
        this.slider.domNode,
        EventType.POINTER_DOWN,
        (e) => {
          if (e.button === 0) {
            e.preventDefault();
            this._sliderPointerDown(e);
          }
        }
      ));
      this.onclick(this.slider.domNode, (e) => {
        if (e.leftButton) {
          e.stopPropagation();
        }
      });
    }
    // ----------------- Update state
    _onElementSize(visibleSize) {
      if (this._scrollbarState.setVisibleSize(visibleSize)) {
        this._visibilityController.setIsNeeded(this._scrollbarState.isNeeded());
        this._shouldRender = true;
        if (!this._lazyRender) {
          this.render();
        }
      }
      return this._shouldRender;
    }
    _onElementScrollSize(elementScrollSize) {
      if (this._scrollbarState.setScrollSize(elementScrollSize)) {
        this._visibilityController.setIsNeeded(this._scrollbarState.isNeeded());
        this._shouldRender = true;
        if (!this._lazyRender) {
          this.render();
        }
      }
      return this._shouldRender;
    }
    _onElementScrollPosition(elementScrollPosition) {
      if (this._scrollbarState.setScrollPosition(elementScrollPosition)) {
        this._visibilityController.setIsNeeded(this._scrollbarState.isNeeded());
        this._shouldRender = true;
        if (!this._lazyRender) {
          this.render();
        }
      }
      return this._shouldRender;
    }
    // ----------------- rendering
    beginReveal() {
      this._visibilityController.setShouldBeVisible(true);
    }
    beginHide() {
      this._visibilityController.setShouldBeVisible(false);
    }
    render() {
      if (!this._shouldRender) {
        return;
      }
      this._shouldRender = false;
      this._renderDomNode(this._scrollbarState.getRectangleLargeSize(), this._scrollbarState.getRectangleSmallSize());
      this._updateSlider(this._scrollbarState.getSliderSize(), this._scrollbarState.getArrowSize() + this._scrollbarState.getSliderPosition());
    }
    // ----------------- DOM events
    _domNodePointerDown(e) {
      if (e.target !== this.domNode.domNode) {
        return;
      }
      this._onPointerDown(e);
    }
    delegatePointerDown(e) {
      const domTop = this.domNode.domNode.getClientRects()[0].top;
      const sliderStart = domTop + this._scrollbarState.getSliderPosition();
      const sliderStop = domTop + this._scrollbarState.getSliderPosition() + this._scrollbarState.getSliderSize();
      const pointerPos = this._sliderPointerPosition(e);
      if (sliderStart <= pointerPos && pointerPos <= sliderStop) {
        if (e.button === 0) {
          e.preventDefault();
          this._sliderPointerDown(e);
        }
      } else {
        this._onPointerDown(e);
      }
    }
    _onPointerDown(e) {
      let offsetX;
      let offsetY;
      if (e.target === this.domNode.domNode && typeof e.offsetX === "number" && typeof e.offsetY === "number") {
        offsetX = e.offsetX;
        offsetY = e.offsetY;
      } else {
        const domNodePosition = getDomNodePagePosition(this.domNode.domNode);
        offsetX = e.pageX - domNodePosition.left;
        offsetY = e.pageY - domNodePosition.top;
      }
      const offset = this._pointerDownRelativePosition(offsetX, offsetY);
      this._setDesiredScrollPositionNow(
        this._scrollByPage ? this._scrollbarState.getDesiredScrollPositionFromOffsetPaged(offset) : this._scrollbarState.getDesiredScrollPositionFromOffset(offset)
      );
      if (e.button === 0) {
        e.preventDefault();
        this._sliderPointerDown(e);
      }
    }
    _sliderPointerDown(e) {
      if (!e.target || !(e.target instanceof Element)) {
        return;
      }
      const initialPointerPosition = this._sliderPointerPosition(e);
      const initialPointerOrthogonalPosition = this._sliderOrthogonalPointerPosition(e);
      const initialScrollbarState = this._scrollbarState.clone();
      this.slider.toggleClassName("active", true);
      this._pointerMoveMonitor.startMonitoring(
        e.target,
        e.pointerId,
        e.buttons,
        (pointerMoveData) => {
          const pointerOrthogonalPosition = this._sliderOrthogonalPointerPosition(pointerMoveData);
          const pointerOrthogonalDelta = Math.abs(pointerOrthogonalPosition - initialPointerOrthogonalPosition);
          if (isWindows && pointerOrthogonalDelta > POINTER_DRAG_RESET_DISTANCE) {
            this._setDesiredScrollPositionNow(initialScrollbarState.getScrollPosition());
            return;
          }
          const pointerPosition = this._sliderPointerPosition(pointerMoveData);
          const pointerDelta = pointerPosition - initialPointerPosition;
          this._setDesiredScrollPositionNow(initialScrollbarState.getDesiredScrollPositionFromDelta(pointerDelta));
        },
        () => {
          this.slider.toggleClassName("active", false);
          this._host.onDragEnd();
        }
      );
      this._host.onDragStart();
    }
    _setDesiredScrollPositionNow(_desiredScrollPosition) {
      const desiredScrollPosition = {};
      this.writeScrollPosition(desiredScrollPosition, _desiredScrollPosition);
      this._scrollable.setScrollPositionNow(desiredScrollPosition);
    }
    updateScrollbarSize(scrollbarSize) {
      this._updateScrollbarSize(scrollbarSize);
      this._scrollbarState.setScrollbarSize(scrollbarSize);
      this._shouldRender = true;
      if (!this._lazyRender) {
        this.render();
      }
    }
    isNeeded() {
      return this._scrollbarState.isNeeded();
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/browser/ui/scrollbar/scrollbarState.ts
  var MINIMUM_SLIDER_SIZE = 20;
  var ScrollbarState = class _ScrollbarState {
    constructor(arrowSize, scrollbarSize, oppositeScrollbarSize, visibleSize, scrollSize, scrollPosition) {
      this._scrollbarSize = Math.round(scrollbarSize);
      this._oppositeScrollbarSize = Math.round(oppositeScrollbarSize);
      this._arrowSize = Math.round(arrowSize);
      this._visibleSize = visibleSize;
      this._scrollSize = scrollSize;
      this._scrollPosition = scrollPosition;
      this._computedAvailableSize = 0;
      this._computedIsNeeded = false;
      this._computedSliderSize = 0;
      this._computedSliderRatio = 0;
      this._computedSliderPosition = 0;
      this._refreshComputedValues();
    }
    clone() {
      return new _ScrollbarState(this._arrowSize, this._scrollbarSize, this._oppositeScrollbarSize, this._visibleSize, this._scrollSize, this._scrollPosition);
    }
    setVisibleSize(visibleSize) {
      const iVisibleSize = Math.round(visibleSize);
      if (this._visibleSize !== iVisibleSize) {
        this._visibleSize = iVisibleSize;
        this._refreshComputedValues();
        return true;
      }
      return false;
    }
    setScrollSize(scrollSize) {
      const iScrollSize = Math.round(scrollSize);
      if (this._scrollSize !== iScrollSize) {
        this._scrollSize = iScrollSize;
        this._refreshComputedValues();
        return true;
      }
      return false;
    }
    setScrollPosition(scrollPosition) {
      const iScrollPosition = Math.round(scrollPosition);
      if (this._scrollPosition !== iScrollPosition) {
        this._scrollPosition = iScrollPosition;
        this._refreshComputedValues();
        return true;
      }
      return false;
    }
    setScrollbarSize(scrollbarSize) {
      this._scrollbarSize = Math.round(scrollbarSize);
    }
    setOppositeScrollbarSize(oppositeScrollbarSize) {
      this._oppositeScrollbarSize = Math.round(oppositeScrollbarSize);
    }
    static _computeValues(oppositeScrollbarSize, arrowSize, visibleSize, scrollSize, scrollPosition) {
      const computedAvailableSize = Math.max(0, visibleSize - oppositeScrollbarSize);
      const computedRepresentableSize = Math.max(0, computedAvailableSize - 2 * arrowSize);
      const computedIsNeeded = scrollSize > 0 && scrollSize > visibleSize;
      if (!computedIsNeeded) {
        return {
          computedAvailableSize: Math.round(computedAvailableSize),
          computedIsNeeded,
          computedSliderSize: Math.round(computedRepresentableSize),
          computedSliderRatio: 0,
          computedSliderPosition: 0
        };
      }
      const computedSliderSize = Math.round(Math.max(MINIMUM_SLIDER_SIZE, Math.floor(visibleSize * computedRepresentableSize / scrollSize)));
      const computedSliderRatio = (computedRepresentableSize - computedSliderSize) / (scrollSize - visibleSize);
      const computedSliderPosition = scrollPosition * computedSliderRatio;
      return {
        computedAvailableSize: Math.round(computedAvailableSize),
        computedIsNeeded,
        computedSliderSize: Math.round(computedSliderSize),
        computedSliderRatio,
        computedSliderPosition: Math.round(computedSliderPosition)
      };
    }
    _refreshComputedValues() {
      const r = _ScrollbarState._computeValues(this._oppositeScrollbarSize, this._arrowSize, this._visibleSize, this._scrollSize, this._scrollPosition);
      this._computedAvailableSize = r.computedAvailableSize;
      this._computedIsNeeded = r.computedIsNeeded;
      this._computedSliderSize = r.computedSliderSize;
      this._computedSliderRatio = r.computedSliderRatio;
      this._computedSliderPosition = r.computedSliderPosition;
    }
    getArrowSize() {
      return this._arrowSize;
    }
    getScrollPosition() {
      return this._scrollPosition;
    }
    getRectangleLargeSize() {
      return this._computedAvailableSize;
    }
    getRectangleSmallSize() {
      return this._scrollbarSize;
    }
    isNeeded() {
      return this._computedIsNeeded;
    }
    getSliderSize() {
      return this._computedSliderSize;
    }
    getSliderPosition() {
      return this._computedSliderPosition;
    }
    /**
     * Compute a desired `scrollPosition` such that `offset` ends up in the center of the slider.
     * `offset` is based on the same coordinate system as the `sliderPosition`.
     */
    getDesiredScrollPositionFromOffset(offset) {
      if (!this._computedIsNeeded) {
        return 0;
      }
      const desiredSliderPosition = offset - this._arrowSize - this._computedSliderSize / 2;
      return Math.round(desiredSliderPosition / this._computedSliderRatio);
    }
    /**
     * Compute a desired `scrollPosition` from if offset is before or after the slider position.
     * If offset is before slider, treat as a page up (or left).  If after, page down (or right).
     * `offset` and `_computedSliderPosition` are based on the same coordinate system.
     * `_visibleSize` corresponds to a "page" of lines in the returned coordinate system.
     */
    getDesiredScrollPositionFromOffsetPaged(offset) {
      if (!this._computedIsNeeded) {
        return 0;
      }
      const correctedOffset = offset - this._arrowSize;
      let desiredScrollPosition = this._scrollPosition;
      if (correctedOffset < this._computedSliderPosition) {
        desiredScrollPosition -= this._visibleSize;
      } else {
        desiredScrollPosition += this._visibleSize;
      }
      return desiredScrollPosition;
    }
    /**
     * Compute a desired `scrollPosition` such that the slider moves by `delta`.
     */
    getDesiredScrollPositionFromDelta(delta) {
      if (!this._computedIsNeeded) {
        return 0;
      }
      const desiredSliderPosition = this._computedSliderPosition + delta;
      return Math.round(desiredSliderPosition / this._computedSliderRatio);
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/browser/ui/scrollbar/horizontalScrollbar.ts
  var HorizontalScrollbar = class extends AbstractScrollbar {
    constructor(scrollable, options, host) {
      const scrollDimensions = scrollable.getScrollDimensions();
      const scrollPosition = scrollable.getCurrentScrollPosition();
      super({
        lazyRender: options.lazyRender,
        host,
        scrollbarState: new ScrollbarState(
          options.horizontalHasArrows ? options.arrowSize : 0,
          options.horizontal === 2 /* Hidden */ ? 0 : options.horizontalScrollbarSize,
          options.vertical === 2 /* Hidden */ ? 0 : options.verticalScrollbarSize,
          scrollDimensions.width,
          scrollDimensions.scrollWidth,
          scrollPosition.scrollLeft
        ),
        visibility: options.horizontal,
        extraScrollbarClassName: "horizontal",
        scrollable,
        scrollByPage: options.scrollByPage
      });
      if (options.horizontalHasArrows) {
        throw new Error("horizontalHasArrows is not supported in xterm.js");
      }
      this._createSlider(Math.floor((options.horizontalScrollbarSize - options.horizontalSliderSize) / 2), 0, void 0, options.horizontalSliderSize);
    }
    _updateSlider(sliderSize, sliderPosition) {
      this.slider.setWidth(sliderSize);
      this.slider.setLeft(sliderPosition);
    }
    _renderDomNode(largeSize, smallSize) {
      this.domNode.setWidth(largeSize);
      this.domNode.setHeight(smallSize);
      this.domNode.setLeft(0);
      this.domNode.setBottom(0);
    }
    onDidScroll(e) {
      this._shouldRender = this._onElementScrollSize(e.scrollWidth) || this._shouldRender;
      this._shouldRender = this._onElementScrollPosition(e.scrollLeft) || this._shouldRender;
      this._shouldRender = this._onElementSize(e.width) || this._shouldRender;
      return this._shouldRender;
    }
    _pointerDownRelativePosition(offsetX, offsetY) {
      return offsetX;
    }
    _sliderPointerPosition(e) {
      return e.pageX;
    }
    _sliderOrthogonalPointerPosition(e) {
      return e.pageY;
    }
    _updateScrollbarSize(size) {
      this.slider.setHeight(size);
    }
    writeScrollPosition(target, scrollPosition) {
      target.scrollLeft = scrollPosition;
    }
    updateOptions(options) {
      this.updateScrollbarSize(options.horizontal === 2 /* Hidden */ ? 0 : options.horizontalScrollbarSize);
      this._scrollbarState.setOppositeScrollbarSize(options.vertical === 2 /* Hidden */ ? 0 : options.verticalScrollbarSize);
      this._visibilityController.setVisibility(options.horizontal);
      this._scrollByPage = options.scrollByPage;
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/browser/ui/scrollbar/verticalScrollbar.ts
  var VerticalScrollbar = class extends AbstractScrollbar {
    constructor(scrollable, options, host) {
      const scrollDimensions = scrollable.getScrollDimensions();
      const scrollPosition = scrollable.getCurrentScrollPosition();
      super({
        lazyRender: options.lazyRender,
        host,
        scrollbarState: new ScrollbarState(
          options.verticalHasArrows ? options.arrowSize : 0,
          options.vertical === 2 /* Hidden */ ? 0 : options.verticalScrollbarSize,
          // give priority to vertical scroll bar over horizontal and let it scroll all the way to the bottom
          0,
          scrollDimensions.height,
          scrollDimensions.scrollHeight,
          scrollPosition.scrollTop
        ),
        visibility: options.vertical,
        extraScrollbarClassName: "vertical",
        scrollable,
        scrollByPage: options.scrollByPage
      });
      if (options.verticalHasArrows) {
        throw new Error("horizontalHasArrows is not supported in xterm.js");
      }
      this._createSlider(0, Math.floor((options.verticalScrollbarSize - options.verticalSliderSize) / 2), options.verticalSliderSize, void 0);
    }
    _updateSlider(sliderSize, sliderPosition) {
      this.slider.setHeight(sliderSize);
      this.slider.setTop(sliderPosition);
    }
    _renderDomNode(largeSize, smallSize) {
      this.domNode.setWidth(smallSize);
      this.domNode.setHeight(largeSize);
      this.domNode.setRight(0);
      this.domNode.setTop(0);
    }
    onDidScroll(e) {
      this._shouldRender = this._onElementScrollSize(e.scrollHeight) || this._shouldRender;
      this._shouldRender = this._onElementScrollPosition(e.scrollTop) || this._shouldRender;
      this._shouldRender = this._onElementSize(e.height) || this._shouldRender;
      return this._shouldRender;
    }
    _pointerDownRelativePosition(offsetX, offsetY) {
      return offsetY;
    }
    _sliderPointerPosition(e) {
      return e.pageY;
    }
    _sliderOrthogonalPointerPosition(e) {
      return e.pageX;
    }
    _updateScrollbarSize(size) {
      this.slider.setWidth(size);
    }
    writeScrollPosition(target, scrollPosition) {
      target.scrollTop = scrollPosition;
    }
    updateOptions(options) {
      this.updateScrollbarSize(options.vertical === 2 /* Hidden */ ? 0 : options.verticalScrollbarSize);
      this._scrollbarState.setOppositeScrollbarSize(0);
      this._visibilityController.setVisibility(options.vertical);
      this._scrollByPage = options.scrollByPage;
    }
  };

  // node_modules/@xterm/xterm/src/vs/base/browser/ui/scrollbar/scrollableElement.ts
  var HIDE_TIMEOUT = 500;
  var SCROLL_WHEEL_SENSITIVITY = 50;
  var SCROLL_WHEEL_SMOOTH_SCROLL_ENABLED = true;
  var MouseWheelClassifierItem = class {
    constructor(timestamp, deltaX, deltaY) {
      this.timestamp = timestamp;
      this.deltaX = deltaX;
      this.deltaY = deltaY;
      this.score = 0;
    }
  };
  var _MouseWheelClassifier = class _MouseWheelClassifier {
    constructor() {
      this._capacity = 5;
      this._memory = [];
      this._front = -1;
      this._rear = -1;
    }
    isPhysicalMouseWheel() {
      if (this._front === -1 && this._rear === -1) {
        return false;
      }
      let remainingInfluence = 1;
      let score = 0;
      let iteration = 1;
      let index = this._rear;
      do {
        const influence = index === this._front ? remainingInfluence : Math.pow(2, -iteration);
        remainingInfluence -= influence;
        score += this._memory[index].score * influence;
        if (index === this._front) {
          break;
        }
        index = (this._capacity + index - 1) % this._capacity;
        iteration++;
      } while (true);
      return score <= 0.5;
    }
    acceptStandardWheelEvent(e) {
      if (isChrome) {
        const targetWindow = getWindow(e.browserEvent);
        const pageZoomFactor = getZoomFactor(targetWindow);
        this.accept(Date.now(), e.deltaX * pageZoomFactor, e.deltaY * pageZoomFactor);
      } else {
        this.accept(Date.now(), e.deltaX, e.deltaY);
      }
    }
    accept(timestamp, deltaX, deltaY) {
      let previousItem = null;
      const item = new MouseWheelClassifierItem(timestamp, deltaX, deltaY);
      if (this._front === -1 && this._rear === -1) {
        this._memory[0] = item;
        this._front = 0;
        this._rear = 0;
      } else {
        previousItem = this._memory[this._rear];
        this._rear = (this._rear + 1) % this._capacity;
        if (this._rear === this._front) {
          this._front = (this._front + 1) % this._capacity;
        }
        this._memory[this._rear] = item;
      }
      item.score = this._computeScore(item, previousItem);
    }
    /**
     * A score between 0 and 1 for `item`.
     *  - a score towards 0 indicates that the source appears to be a physical mouse wheel
     *  - a score towards 1 indicates that the source appears to be a touchpad or magic mouse, etc.
     */
    _computeScore(item, previousItem) {
      if (Math.abs(item.deltaX) > 0 && Math.abs(item.deltaY) > 0) {
        return 1;
      }
      let score = 0.5;
      if (!this._isAlmostInt(item.deltaX) || !this._isAlmostInt(item.deltaY)) {
        score += 0.25;
      }
      if (previousItem) {
        const absDeltaX = Math.abs(item.deltaX);
        const absDeltaY = Math.abs(item.deltaY);
        const absPreviousDeltaX = Math.abs(previousItem.deltaX);
        const absPreviousDeltaY = Math.abs(previousItem.deltaY);
        const minDeltaX = Math.max(Math.min(absDeltaX, absPreviousDeltaX), 1);
        const minDeltaY = Math.max(Math.min(absDeltaY, absPreviousDeltaY), 1);
        const maxDeltaX = Math.max(absDeltaX, absPreviousDeltaX);
        const maxDeltaY = Math.max(absDeltaY, absPreviousDeltaY);
        const isSameModulo = maxDeltaX % minDeltaX === 0 && maxDeltaY % minDeltaY === 0;
        if (isSameModulo) {
          score -= 0.5;
        }
      }
      return Math.min(Math.max(score, 0), 1);
    }
    _isAlmostInt(value) {
      const delta = Math.abs(Math.round(value) - value);
      return delta < 0.01;
    }
  };
  _MouseWheelClassifier.INSTANCE = new _MouseWheelClassifier();
  var MouseWheelClassifier = _MouseWheelClassifier;
  var AbstractScrollableElement = class extends Widget {
    constructor(element, options, scrollable) {
      super();
      this._onScroll = this._register(new Emitter());
      this.onScroll = this._onScroll.event;
      this._onWillScroll = this._register(new Emitter());
      this.onWillScroll = this._onWillScroll.event;
      this._options = resolveOptions(options);
      this._scrollable = scrollable;
      this._register(this._scrollable.onScroll((e) => {
        this._onWillScroll.fire(e);
        this._onDidScroll(e);
        this._onScroll.fire(e);
      }));
      const scrollbarHost = {
        onMouseWheel: (mouseWheelEvent) => this._onMouseWheel(mouseWheelEvent),
        onDragStart: () => this._onDragStart(),
        onDragEnd: () => this._onDragEnd()
      };
      this._verticalScrollbar = this._register(new VerticalScrollbar(this._scrollable, this._options, scrollbarHost));
      this._horizontalScrollbar = this._register(new HorizontalScrollbar(this._scrollable, this._options, scrollbarHost));
      this._domNode = document.createElement("div");
      this._domNode.className = "xterm-scrollable-element " + this._options.className;
      this._domNode.setAttribute("role", "presentation");
      this._domNode.style.position = "relative";
      this._domNode.appendChild(element);
      this._domNode.appendChild(this._horizontalScrollbar.domNode.domNode);
      this._domNode.appendChild(this._verticalScrollbar.domNode.domNode);
      if (this._options.useShadows) {
        this._leftShadowDomNode = createFastDomNode(document.createElement("div"));
        this._leftShadowDomNode.setClassName("shadow");
        this._domNode.appendChild(this._leftShadowDomNode.domNode);
        this._topShadowDomNode = createFastDomNode(document.createElement("div"));
        this._topShadowDomNode.setClassName("shadow");
        this._domNode.appendChild(this._topShadowDomNode.domNode);
        this._topLeftShadowDomNode = createFastDomNode(document.createElement("div"));
        this._topLeftShadowDomNode.setClassName("shadow");
        this._domNode.appendChild(this._topLeftShadowDomNode.domNode);
      } else {
        this._leftShadowDomNode = null;
        this._topShadowDomNode = null;
        this._topLeftShadowDomNode = null;
      }
      this._listenOnDomNode = this._options.listenOnDomNode || this._domNode;
      this._mouseWheelToDispose = [];
      this._setListeningToMouseWheel(this._options.handleMouseWheel);
      this.onmouseover(this._listenOnDomNode, (e) => this._onMouseOver(e));
      this.onmouseleave(this._listenOnDomNode, (e) => this._onMouseLeave(e));
      this._hideTimeout = this._register(new TimeoutTimer());
      this._isDragging = false;
      this._mouseIsOver = false;
      this._shouldRender = true;
      this._revealOnScroll = true;
    }
    get options() {
      return this._options;
    }
    dispose() {
      this._mouseWheelToDispose = dispose(this._mouseWheelToDispose);
      super.dispose();
    }
    /**
     * Get the generated 'scrollable' dom node
     */
    getDomNode() {
      return this._domNode;
    }
    getOverviewRulerLayoutInfo() {
      return {
        parent: this._domNode,
        insertBefore: this._verticalScrollbar.domNode.domNode
      };
    }
    /**
     * Delegate a pointer down event to the vertical scrollbar.
     * This is to help with clicking somewhere else and having the scrollbar react.
     */
    delegateVerticalScrollbarPointerDown(browserEvent) {
      this._verticalScrollbar.delegatePointerDown(browserEvent);
    }
    getScrollDimensions() {
      return this._scrollable.getScrollDimensions();
    }
    setScrollDimensions(dimensions) {
      this._scrollable.setScrollDimensions(dimensions, false);
    }
    /**
     * Update the class name of the scrollable element.
     */
    updateClassName(newClassName) {
      this._options.className = newClassName;
      if (isMacintosh) {
        this._options.className += " mac";
      }
      this._domNode.className = "xterm-scrollable-element " + this._options.className;
    }
    /**
     * Update configuration options for the scrollbar.
     */
    updateOptions(newOptions) {
      if (typeof newOptions.handleMouseWheel !== "undefined") {
        this._options.handleMouseWheel = newOptions.handleMouseWheel;
        this._setListeningToMouseWheel(this._options.handleMouseWheel);
      }
      if (typeof newOptions.mouseWheelScrollSensitivity !== "undefined") {
        this._options.mouseWheelScrollSensitivity = newOptions.mouseWheelScrollSensitivity;
      }
      if (typeof newOptions.fastScrollSensitivity !== "undefined") {
        this._options.fastScrollSensitivity = newOptions.fastScrollSensitivity;
      }
      if (typeof newOptions.scrollPredominantAxis !== "undefined") {
        this._options.scrollPredominantAxis = newOptions.scrollPredominantAxis;
      }
      if (typeof newOptions.horizontal !== "undefined") {
        this._options.horizontal = newOptions.horizontal;
      }
      if (typeof newOptions.vertical !== "undefined") {
        this._options.vertical = newOptions.vertical;
      }
      if (typeof newOptions.horizontalScrollbarSize !== "undefined") {
        this._options.horizontalScrollbarSize = newOptions.horizontalScrollbarSize;
      }
      if (typeof newOptions.verticalScrollbarSize !== "undefined") {
        this._options.verticalScrollbarSize = newOptions.verticalScrollbarSize;
      }
      if (typeof newOptions.scrollByPage !== "undefined") {
        this._options.scrollByPage = newOptions.scrollByPage;
      }
      this._horizontalScrollbar.updateOptions(this._options);
      this._verticalScrollbar.updateOptions(this._options);
      if (!this._options.lazyRender) {
        this._render();
      }
    }
    setRevealOnScroll(value) {
      this._revealOnScroll = value;
    }
    delegateScrollFromMouseWheelEvent(browserEvent) {
      this._onMouseWheel(new StandardWheelEvent(browserEvent));
    }
    // -------------------- mouse wheel scrolling --------------------
    _setListeningToMouseWheel(shouldListen) {
      const isListening = this._mouseWheelToDispose.length > 0;
      if (isListening === shouldListen) {
        return;
      }
      this._mouseWheelToDispose = dispose(this._mouseWheelToDispose);
      if (shouldListen) {
        const onMouseWheel = (browserEvent) => {
          this._onMouseWheel(new StandardWheelEvent(browserEvent));
        };
        this._mouseWheelToDispose.push(addDisposableListener(this._listenOnDomNode, EventType.MOUSE_WHEEL, onMouseWheel, { passive: false }));
      }
    }
    _onMouseWheel(e) {
      if (e.browserEvent?.defaultPrevented) {
        return;
      }
      const classifier = MouseWheelClassifier.INSTANCE;
      if (SCROLL_WHEEL_SMOOTH_SCROLL_ENABLED) {
        classifier.acceptStandardWheelEvent(e);
      }
      let didScroll = false;
      if (e.deltaY || e.deltaX) {
        let deltaY = e.deltaY * this._options.mouseWheelScrollSensitivity;
        let deltaX = e.deltaX * this._options.mouseWheelScrollSensitivity;
        if (this._options.scrollPredominantAxis) {
          if (this._options.scrollYToX && deltaX + deltaY === 0) {
            deltaX = deltaY = 0;
          } else if (Math.abs(deltaY) >= Math.abs(deltaX)) {
            deltaX = 0;
          } else {
            deltaY = 0;
          }
        }
        if (this._options.flipAxes) {
          [deltaY, deltaX] = [deltaX, deltaY];
        }
        const shiftConvert = !isMacintosh && e.browserEvent && e.browserEvent.shiftKey;
        if ((this._options.scrollYToX || shiftConvert) && !deltaX) {
          deltaX = deltaY;
          deltaY = 0;
        }
        if (e.browserEvent && e.browserEvent.altKey) {
          deltaX = deltaX * this._options.fastScrollSensitivity;
          deltaY = deltaY * this._options.fastScrollSensitivity;
        }
        const futureScrollPosition = this._scrollable.getFutureScrollPosition();
        let desiredScrollPosition = {};
        if (deltaY) {
          const deltaScrollTop = SCROLL_WHEEL_SENSITIVITY * deltaY;
          const desiredScrollTop = futureScrollPosition.scrollTop - (deltaScrollTop < 0 ? Math.floor(deltaScrollTop) : Math.ceil(deltaScrollTop));
          this._verticalScrollbar.writeScrollPosition(desiredScrollPosition, desiredScrollTop);
        }
        if (deltaX) {
          const deltaScrollLeft = SCROLL_WHEEL_SENSITIVITY * deltaX;
          const desiredScrollLeft = futureScrollPosition.scrollLeft - (deltaScrollLeft < 0 ? Math.floor(deltaScrollLeft) : Math.ceil(deltaScrollLeft));
          this._horizontalScrollbar.writeScrollPosition(desiredScrollPosition, desiredScrollLeft);
        }
        desiredScrollPosition = this._scrollable.validateScrollPosition(desiredScrollPosition);
        if (futureScrollPosition.scrollLeft !== desiredScrollPosition.scrollLeft || futureScrollPosition.scrollTop !== desiredScrollPosition.scrollTop) {
          const canPerformSmoothScroll = SCROLL_WHEEL_SMOOTH_SCROLL_ENABLED && this._options.mouseWheelSmoothScroll && classifier.isPhysicalMouseWheel();
          if (canPerformSmoothScroll) {
            this._scrollable.setScrollPositionSmooth(desiredScrollPosition);
          } else {
            this._scrollable.setScrollPositionNow(desiredScrollPosition);
          }
          didScroll = true;
        }
      }
      let consumeMouseWheel = didScroll;
      if (!consumeMouseWheel && this._options.alwaysConsumeMouseWheel) {
        consumeMouseWheel = true;
      }
      if (!consumeMouseWheel && this._options.consumeMouseWheelIfScrollbarIsNeeded && (this._verticalScrollbar.isNeeded() || this._horizontalScrollbar.isNeeded())) {
        consumeMouseWheel = true;
      }
      if (consumeMouseWheel) {
        e.preventDefault();
        e.stopPropagation();
      }
    }
    _onDidScroll(e) {
      this._shouldRender = this._horizontalScrollbar.onDidScroll(e) || this._shouldRender;
      this._shouldRender = this._verticalScrollbar.onDidScroll(e) || this._shouldRender;
      if (this._options.useShadows) {
        this._shouldRender = true;
      }
      if (this._revealOnScroll) {
        this._reveal();
      }
      if (!this._options.lazyRender) {
        this._render();
      }
    }
    /**
     * Render / mutate the DOM now.
     * Should be used together with the ctor option `lazyRender`.
     */
    renderNow() {
      if (!this._options.lazyRender) {
        throw new Error("Please use `lazyRender` together with `renderNow`!");
      }
      this._render();
    }
    _render() {
      if (!this._shouldRender) {
        return;
      }
      this._shouldRender = false;
      this._horizontalScrollbar.render();
      this._verticalScrollbar.render();
      if (this._options.useShadows) {
        const scrollState = this._scrollable.getCurrentScrollPosition();
        const enableTop = scrollState.scrollTop > 0;
        const enableLeft = scrollState.scrollLeft > 0;
        const leftClassName = enableLeft ? " left" : "";
        const topClassName = enableTop ? " top" : "";
        const topLeftClassName = enableLeft || enableTop ? " top-left-corner" : "";
        this._leftShadowDomNode.setClassName(`shadow${leftClassName}`);
        this._topShadowDomNode.setClassName(`shadow${topClassName}`);
        this._topLeftShadowDomNode.setClassName(`shadow${topLeftClassName}${topClassName}${leftClassName}`);
      }
    }
    // -------------------- fade in / fade out --------------------
    _onDragStart() {
      this._isDragging = true;
      this._reveal();
    }
    _onDragEnd() {
      this._isDragging = false;
      this._hide();
    }
    _onMouseLeave(e) {
      this._mouseIsOver = false;
      this._hide();
    }
    _onMouseOver(e) {
      this._mouseIsOver = true;
      this._reveal();
    }
    _reveal() {
      this._verticalScrollbar.beginReveal();
      this._horizontalScrollbar.beginReveal();
      this._scheduleHide();
    }
    _hide() {
      if (!this._mouseIsOver && !this._isDragging) {
        this._verticalScrollbar.beginHide();
        this._horizontalScrollbar.beginHide();
      }
    }
    _scheduleHide() {
      if (!this._mouseIsOver && !this._isDragging) {
        this._hideTimeout.cancelAndSet(() => this._hide(), HIDE_TIMEOUT);
      }
    }
  };
  var SmoothScrollableElement = class extends AbstractScrollableElement {
    constructor(element, options, scrollable) {
      super(element, options, scrollable);
    }
    setScrollPosition(update) {
      if (update.reuseAnimation) {
        this._scrollable.setScrollPositionSmooth(update, update.reuseAnimation);
      } else {
        this._scrollable.setScrollPositionNow(update);
      }
    }
    getScrollPosition() {
      return this._scrollable.getCurrentScrollPosition();
    }
  };
  function resolveOptions(opts) {
    const result = {
      lazyRender: typeof opts.lazyRender !== "undefined" ? opts.lazyRender : false,
      className: typeof opts.className !== "undefined" ? opts.className : "",
      useShadows: typeof opts.useShadows !== "undefined" ? opts.useShadows : true,
      handleMouseWheel: typeof opts.handleMouseWheel !== "undefined" ? opts.handleMouseWheel : true,
      flipAxes: typeof opts.flipAxes !== "undefined" ? opts.flipAxes : false,
      consumeMouseWheelIfScrollbarIsNeeded: typeof opts.consumeMouseWheelIfScrollbarIsNeeded !== "undefined" ? opts.consumeMouseWheelIfScrollbarIsNeeded : false,
      alwaysConsumeMouseWheel: typeof opts.alwaysConsumeMouseWheel !== "undefined" ? opts.alwaysConsumeMouseWheel : false,
      scrollYToX: typeof opts.scrollYToX !== "undefined" ? opts.scrollYToX : false,
      mouseWheelScrollSensitivity: typeof opts.mouseWheelScrollSensitivity !== "undefined" ? opts.mouseWheelScrollSensitivity : 1,
      fastScrollSensitivity: typeof opts.fastScrollSensitivity !== "undefined" ? opts.fastScrollSensitivity : 5,
      scrollPredominantAxis: typeof opts.scrollPredominantAxis !== "undefined" ? opts.scrollPredominantAxis : true,
      mouseWheelSmoothScroll: typeof opts.mouseWheelSmoothScroll !== "undefined" ? opts.mouseWheelSmoothScroll : true,
      arrowSize: typeof opts.arrowSize !== "undefined" ? opts.arrowSize : 11,
      listenOnDomNode: typeof opts.listenOnDomNode !== "undefined" ? opts.listenOnDomNode : null,
      horizontal: typeof opts.horizontal !== "undefined" ? opts.horizontal : 1 /* Auto */,
      horizontalScrollbarSize: typeof opts.horizontalScrollbarSize !== "undefined" ? opts.horizontalScrollbarSize : 10,
      horizontalSliderSize: typeof opts.horizontalSliderSize !== "undefined" ? opts.horizontalSliderSize : 0,
      horizontalHasArrows: typeof opts.horizontalHasArrows !== "undefined" ? opts.horizontalHasArrows : false,
      vertical: typeof opts.vertical !== "undefined" ? opts.vertical : 1 /* Auto */,
      verticalScrollbarSize: typeof opts.verticalScrollbarSize !== "undefined" ? opts.verticalScrollbarSize : 10,
      verticalHasArrows: typeof opts.verticalHasArrows !== "undefined" ? opts.verticalHasArrows : false,
      verticalSliderSize: typeof opts.verticalSliderSize !== "undefined" ? opts.verticalSliderSize : 0,
      scrollByPage: typeof opts.scrollByPage !== "undefined" ? opts.scrollByPage : false
    };
    result.horizontalSliderSize = typeof opts.horizontalSliderSize !== "undefined" ? opts.horizontalSliderSize : result.horizontalScrollbarSize;
    result.verticalSliderSize = typeof opts.verticalSliderSize !== "undefined" ? opts.verticalSliderSize : result.verticalScrollbarSize;
    if (isMacintosh) {
      result.className += " mac";
    }
    return result;
  }

  // node_modules/@xterm/xterm/src/browser/Viewport.ts
  var Viewport = class extends Disposable {
    constructor(element, screenElement, _bufferService, coreBrowserService, coreMouseService, themeService, _optionsService, _renderService) {
      super();
      this._bufferService = _bufferService;
      this._optionsService = _optionsService;
      this._renderService = _renderService;
      this._onRequestScrollLines = this._register(new Emitter());
      this.onRequestScrollLines = this._onRequestScrollLines.event;
      this._isSyncing = false;
      this._isHandlingScroll = false;
      this._suppressOnScrollHandler = false;
      const scrollable = this._register(new Scrollable({
        forceIntegerValues: false,
        smoothScrollDuration: this._optionsService.rawOptions.smoothScrollDuration,
        // This is used over `IRenderService.addRefreshCallback` since it can be canceled
        scheduleAtNextAnimationFrame: (cb) => scheduleAtNextAnimationFrame(coreBrowserService.window, cb)
      }));
      this._register(this._optionsService.onSpecificOptionChange("smoothScrollDuration", () => {
        scrollable.setSmoothScrollDuration(this._optionsService.rawOptions.smoothScrollDuration);
      }));
      this._scrollableElement = this._register(new SmoothScrollableElement(screenElement, {
        vertical: 1 /* Auto */,
        horizontal: 2 /* Hidden */,
        useShadows: false,
        mouseWheelSmoothScroll: true,
        ...this._getChangeOptions()
      }, scrollable));
      this._register(this._optionsService.onMultipleOptionChange([
        "scrollSensitivity",
        "fastScrollSensitivity",
        "overviewRuler"
      ], () => this._scrollableElement.updateOptions(this._getChangeOptions())));
      this._register(coreMouseService.onProtocolChange((type) => {
        this._scrollableElement.updateOptions({
          handleMouseWheel: !(type & 16 /* WHEEL */)
        });
      }));
      this._scrollableElement.setScrollDimensions({ height: 0, scrollHeight: 0 });
      this._register(Event.runAndSubscribe(themeService.onChangeColors, () => {
        this._scrollableElement.getDomNode().style.backgroundColor = themeService.colors.background.css;
      }));
      element.appendChild(this._scrollableElement.getDomNode());
      this._register(toDisposable(() => this._scrollableElement.getDomNode().remove()));
      this._styleElement = coreBrowserService.mainDocument.createElement("style");
      screenElement.appendChild(this._styleElement);
      this._register(toDisposable(() => this._styleElement.remove()));
      this._register(Event.runAndSubscribe(themeService.onChangeColors, () => {
        this._styleElement.textContent = [
          `.xterm .xterm-scrollable-element > .scrollbar > .slider {`,
          `  background: ${themeService.colors.scrollbarSliderBackground.css};`,
          `}`,
          `.xterm .xterm-scrollable-element > .scrollbar > .slider:hover {`,
          `  background: ${themeService.colors.scrollbarSliderHoverBackground.css};`,
          `}`,
          `.xterm .xterm-scrollable-element > .scrollbar > .slider.active {`,
          `  background: ${themeService.colors.scrollbarSliderActiveBackground.css};`,
          `}`
        ].join("\n");
      }));
      this._register(this._bufferService.onResize(() => this.queueSync()));
      this._register(this._bufferService.buffers.onBufferActivate(() => {
        this._latestYDisp = void 0;
        this.queueSync();
      }));
      this._register(this._bufferService.onScroll(() => this._sync()));
      this._register(this._scrollableElement.onScroll((e) => this._handleScroll(e)));
    }
    scrollLines(disp) {
      const pos = this._scrollableElement.getScrollPosition();
      this._scrollableElement.setScrollPosition({
        reuseAnimation: true,
        scrollTop: pos.scrollTop + disp * this._renderService.dimensions.css.cell.height
      });
    }
    scrollToLine(line, disableSmoothScroll) {
      if (disableSmoothScroll) {
        this._latestYDisp = line;
      }
      this._scrollableElement.setScrollPosition({
        reuseAnimation: !disableSmoothScroll,
        scrollTop: line * this._renderService.dimensions.css.cell.height
      });
    }
    _getChangeOptions() {
      return {
        mouseWheelScrollSensitivity: this._optionsService.rawOptions.scrollSensitivity,
        fastScrollSensitivity: this._optionsService.rawOptions.fastScrollSensitivity,
        verticalScrollbarSize: this._optionsService.rawOptions.overviewRuler?.width || 14 /* DEFAULT_SCROLL_BAR_WIDTH */
      };
    }
    queueSync(ydisp) {
      if (ydisp !== void 0) {
        this._latestYDisp = ydisp;
      }
      if (this._queuedAnimationFrame !== void 0) {
        return;
      }
      this._queuedAnimationFrame = this._renderService.addRefreshCallback(() => {
        this._queuedAnimationFrame = void 0;
        this._sync(this._latestYDisp);
      });
    }
    _sync(ydisp = this._bufferService.buffer.ydisp) {
      if (!this._renderService || this._isSyncing) {
        return;
      }
      this._isSyncing = true;
      this._suppressOnScrollHandler = true;
      this._scrollableElement.setScrollDimensions({
        height: this._renderService.dimensions.css.canvas.height,
        scrollHeight: this._renderService.dimensions.css.cell.height * this._bufferService.buffer.lines.length
      });
      this._suppressOnScrollHandler = false;
      if (ydisp !== this._latestYDisp) {
        this._scrollableElement.setScrollPosition({
          scrollTop: ydisp * this._renderService.dimensions.css.cell.height
        });
      }
      this._isSyncing = false;
    }
    _handleScroll(e) {
      if (!this._renderService) {
        return;
      }
      if (this._isHandlingScroll || this._suppressOnScrollHandler) {
        return;
      }
      this._isHandlingScroll = true;
      const newRow = Math.round(e.scrollTop / this._renderService.dimensions.css.cell.height);
      const diff = newRow - this._bufferService.buffer.ydisp;
      if (diff !== 0) {
        this._latestYDisp = newRow;
        this._onRequestScrollLines.fire(diff);
      }
      this._isHandlingScroll = false;
    }
  };
  Viewport = __decorateClass([
    __decorateParam(2, IBufferService),
    __decorateParam(3, ICoreBrowserService),
    __decorateParam(4, ICoreMouseService),
    __decorateParam(5, IThemeService),
    __decorateParam(6, IOptionsService),
    __decorateParam(7, IRenderService)
  ], Viewport);

  // node_modules/@xterm/xterm/src/browser/decorations/BufferDecorationRenderer.ts
  var BufferDecorationRenderer = class extends Disposable {
    constructor(_screenElement, _bufferService, _coreBrowserService, _decorationService, _renderService) {
      super();
      this._screenElement = _screenElement;
      this._bufferService = _bufferService;
      this._coreBrowserService = _coreBrowserService;
      this._decorationService = _decorationService;
      this._renderService = _renderService;
      this._decorationElements = /* @__PURE__ */ new Map();
      this._altBufferIsActive = false;
      this._dimensionsChanged = false;
      this._container = document.createElement("div");
      this._container.classList.add("xterm-decoration-container");
      this._screenElement.appendChild(this._container);
      this._register(this._renderService.onRenderedViewportChange(() => this._doRefreshDecorations()));
      this._register(this._renderService.onDimensionsChange(() => {
        this._dimensionsChanged = true;
        this._queueRefresh();
      }));
      this._register(this._coreBrowserService.onDprChange(() => this._queueRefresh()));
      this._register(this._bufferService.buffers.onBufferActivate(() => {
        this._altBufferIsActive = this._bufferService.buffer === this._bufferService.buffers.alt;
      }));
      this._register(this._decorationService.onDecorationRegistered(() => this._queueRefresh()));
      this._register(this._decorationService.onDecorationRemoved((decoration) => this._removeDecoration(decoration)));
      this._register(toDisposable(() => {
        this._container.remove();
        this._decorationElements.clear();
      }));
    }
    _queueRefresh() {
      if (this._animationFrame !== void 0) {
        return;
      }
      this._animationFrame = this._renderService.addRefreshCallback(() => {
        this._doRefreshDecorations();
        this._animationFrame = void 0;
      });
    }
    _doRefreshDecorations() {
      for (const decoration of this._decorationService.decorations) {
        this._renderDecoration(decoration);
      }
      this._dimensionsChanged = false;
    }
    _renderDecoration(decoration) {
      this._refreshStyle(decoration);
      if (this._dimensionsChanged) {
        this._refreshXPosition(decoration);
      }
    }
    _createElement(decoration) {
      const element = this._coreBrowserService.mainDocument.createElement("div");
      element.classList.add("xterm-decoration");
      element.classList.toggle("xterm-decoration-top-layer", decoration?.options?.layer === "top");
      element.style.width = `${Math.round((decoration.options.width || 1) * this._renderService.dimensions.css.cell.width)}px`;
      element.style.height = `${(decoration.options.height || 1) * this._renderService.dimensions.css.cell.height}px`;
      element.style.top = `${(decoration.marker.line - this._bufferService.buffers.active.ydisp) * this._renderService.dimensions.css.cell.height}px`;
      element.style.lineHeight = `${this._renderService.dimensions.css.cell.height}px`;
      const x = decoration.options.x ?? 0;
      if (x && x > this._bufferService.cols) {
        element.style.display = "none";
      }
      this._refreshXPosition(decoration, element);
      return element;
    }
    _refreshStyle(decoration) {
      const line = decoration.marker.line - this._bufferService.buffers.active.ydisp;
      if (line < 0 || line >= this._bufferService.rows) {
        if (decoration.element) {
          decoration.element.style.display = "none";
          decoration.onRenderEmitter.fire(decoration.element);
        }
      } else {
        let element = this._decorationElements.get(decoration);
        if (!element) {
          element = this._createElement(decoration);
          decoration.element = element;
          this._decorationElements.set(decoration, element);
          this._container.appendChild(element);
          decoration.onDispose(() => {
            this._decorationElements.delete(decoration);
            element.remove();
          });
        }
        element.style.display = this._altBufferIsActive ? "none" : "block";
        if (!this._altBufferIsActive) {
          element.style.width = `${Math.round((decoration.options.width || 1) * this._renderService.dimensions.css.cell.width)}px`;
          element.style.height = `${(decoration.options.height || 1) * this._renderService.dimensions.css.cell.height}px`;
          element.style.top = `${line * this._renderService.dimensions.css.cell.height}px`;
          element.style.lineHeight = `${this._renderService.dimensions.css.cell.height}px`;
        }
        decoration.onRenderEmitter.fire(element);
      }
    }
    _refreshXPosition(decoration, element = decoration.element) {
      if (!element) {
        return;
      }
      const x = decoration.options.x ?? 0;
      if ((decoration.options.anchor || "left") === "right") {
        element.style.right = x ? `${x * this._renderService.dimensions.css.cell.width}px` : "";
      } else {
        element.style.left = x ? `${x * this._renderService.dimensions.css.cell.width}px` : "";
      }
    }
    _removeDecoration(decoration) {
      this._decorationElements.get(decoration)?.remove();
      this._decorationElements.delete(decoration);
      decoration.dispose();
    }
  };
  BufferDecorationRenderer = __decorateClass([
    __decorateParam(1, IBufferService),
    __decorateParam(2, ICoreBrowserService),
    __decorateParam(3, IDecorationService),
    __decorateParam(4, IRenderService)
  ], BufferDecorationRenderer);

  // node_modules/@xterm/xterm/src/browser/decorations/ColorZoneStore.ts
  var ColorZoneStore = class {
    constructor() {
      this._zones = [];
      // The zone pool is used to keep zone objects from being freed between clearing the color zone
      // store and fetching the zones. This helps reduce GC pressure since the color zones are
      // accumulated on potentially every scroll event.
      this._zonePool = [];
      this._zonePoolIndex = 0;
      this._linePadding = {
        full: 0,
        left: 0,
        center: 0,
        right: 0
      };
    }
    get zones() {
      this._zonePool.length = Math.min(this._zonePool.length, this._zones.length);
      return this._zones;
    }
    clear() {
      this._zones.length = 0;
      this._zonePoolIndex = 0;
    }
    addDecoration(decoration) {
      if (!decoration.options.overviewRulerOptions) {
        return;
      }
      for (const z of this._zones) {
        if (z.color === decoration.options.overviewRulerOptions.color && z.position === decoration.options.overviewRulerOptions.position) {
          if (this._lineIntersectsZone(z, decoration.marker.line)) {
            return;
          }
          if (this._lineAdjacentToZone(z, decoration.marker.line, decoration.options.overviewRulerOptions.position)) {
            this._addLineToZone(z, decoration.marker.line);
            return;
          }
        }
      }
      if (this._zonePoolIndex < this._zonePool.length) {
        this._zonePool[this._zonePoolIndex].color = decoration.options.overviewRulerOptions.color;
        this._zonePool[this._zonePoolIndex].position = decoration.options.overviewRulerOptions.position;
        this._zonePool[this._zonePoolIndex].startBufferLine = decoration.marker.line;
        this._zonePool[this._zonePoolIndex].endBufferLine = decoration.marker.line;
        this._zones.push(this._zonePool[this._zonePoolIndex++]);
        return;
      }
      this._zones.push({
        color: decoration.options.overviewRulerOptions.color,
        position: decoration.options.overviewRulerOptions.position,
        startBufferLine: decoration.marker.line,
        endBufferLine: decoration.marker.line
      });
      this._zonePool.push(this._zones[this._zones.length - 1]);
      this._zonePoolIndex++;
    }
    setPadding(padding) {
      this._linePadding = padding;
    }
    _lineIntersectsZone(zone, line) {
      return line >= zone.startBufferLine && line <= zone.endBufferLine;
    }
    _lineAdjacentToZone(zone, line, position) {
      return line >= zone.startBufferLine - this._linePadding[position || "full"] && line <= zone.endBufferLine + this._linePadding[position || "full"];
    }
    _addLineToZone(zone, line) {
      zone.startBufferLine = Math.min(zone.startBufferLine, line);
      zone.endBufferLine = Math.max(zone.endBufferLine, line);
    }
  };

  // node_modules/@xterm/xterm/src/browser/decorations/OverviewRulerRenderer.ts
  var drawHeight = {
    full: 0,
    left: 0,
    center: 0,
    right: 0
  };
  var drawWidth = {
    full: 0,
    left: 0,
    center: 0,
    right: 0
  };
  var drawX = {
    full: 0,
    left: 0,
    center: 0,
    right: 0
  };
  var OverviewRulerRenderer = class extends Disposable {
    constructor(_viewportElement, _screenElement, _bufferService, _decorationService, _renderService, _optionsService, _themeService, _coreBrowserService) {
      super();
      this._viewportElement = _viewportElement;
      this._screenElement = _screenElement;
      this._bufferService = _bufferService;
      this._decorationService = _decorationService;
      this._renderService = _renderService;
      this._optionsService = _optionsService;
      this._themeService = _themeService;
      this._coreBrowserService = _coreBrowserService;
      this._colorZoneStore = new ColorZoneStore();
      this._shouldUpdateDimensions = true;
      this._shouldUpdateAnchor = true;
      this._lastKnownBufferLength = 0;
      this._canvas = this._coreBrowserService.mainDocument.createElement("canvas");
      this._canvas.classList.add("xterm-decoration-overview-ruler");
      this._refreshCanvasDimensions();
      this._viewportElement.parentElement?.insertBefore(this._canvas, this._viewportElement);
      this._register(toDisposable(() => this._canvas?.remove()));
      const ctx = this._canvas.getContext("2d");
      if (!ctx) {
        throw new Error("Ctx cannot be null");
      } else {
        this._ctx = ctx;
      }
      this._register(this._decorationService.onDecorationRegistered(() => this._queueRefresh(void 0, true)));
      this._register(this._decorationService.onDecorationRemoved(() => this._queueRefresh(void 0, true)));
      this._register(this._renderService.onRenderedViewportChange(() => this._queueRefresh()));
      this._register(this._bufferService.buffers.onBufferActivate(() => {
        this._canvas.style.display = this._bufferService.buffer === this._bufferService.buffers.alt ? "none" : "block";
      }));
      this._register(this._bufferService.onScroll(() => {
        if (this._lastKnownBufferLength !== this._bufferService.buffers.normal.lines.length) {
          this._refreshDrawHeightConstants();
          this._refreshColorZonePadding();
        }
      }));
      this._register(this._renderService.onRender(() => {
        if (!this._containerHeight || this._containerHeight !== this._screenElement.clientHeight) {
          this._queueRefresh(true);
          this._containerHeight = this._screenElement.clientHeight;
        }
      }));
      this._register(this._coreBrowserService.onDprChange(() => this._queueRefresh(true)));
      this._register(this._optionsService.onSpecificOptionChange("overviewRuler", () => this._queueRefresh(true)));
      this._register(this._themeService.onChangeColors(() => this._queueRefresh()));
      this._queueRefresh(true);
    }
    get _width() {
      return this._optionsService.options.overviewRuler?.width || 0;
    }
    _refreshDrawConstants() {
      const outerWidth = Math.floor((this._canvas.width - 1 /* OVERVIEW_RULER_BORDER_WIDTH */) / 3);
      const innerWidth = Math.ceil((this._canvas.width - 1 /* OVERVIEW_RULER_BORDER_WIDTH */) / 3);
      drawWidth.full = this._canvas.width;
      drawWidth.left = outerWidth;
      drawWidth.center = innerWidth;
      drawWidth.right = outerWidth;
      this._refreshDrawHeightConstants();
      drawX.full = 1 /* OVERVIEW_RULER_BORDER_WIDTH */;
      drawX.left = 1 /* OVERVIEW_RULER_BORDER_WIDTH */;
      drawX.center = 1 /* OVERVIEW_RULER_BORDER_WIDTH */ + drawWidth.left;
      drawX.right = 1 /* OVERVIEW_RULER_BORDER_WIDTH */ + drawWidth.left + drawWidth.center;
    }
    _refreshDrawHeightConstants() {
      drawHeight.full = Math.round(2 * this._coreBrowserService.dpr);
      const pixelsPerLine = this._canvas.height / this._bufferService.buffer.lines.length;
      const nonFullHeight = Math.round(Math.max(Math.min(pixelsPerLine, 12), 6) * this._coreBrowserService.dpr);
      drawHeight.left = nonFullHeight;
      drawHeight.center = nonFullHeight;
      drawHeight.right = nonFullHeight;
    }
    _refreshColorZonePadding() {
      this._colorZoneStore.setPadding({
        full: Math.floor(this._bufferService.buffers.active.lines.length / (this._canvas.height - 1) * drawHeight.full),
        left: Math.floor(this._bufferService.buffers.active.lines.length / (this._canvas.height - 1) * drawHeight.left),
        center: Math.floor(this._bufferService.buffers.active.lines.length / (this._canvas.height - 1) * drawHeight.center),
        right: Math.floor(this._bufferService.buffers.active.lines.length / (this._canvas.height - 1) * drawHeight.right)
      });
      this._lastKnownBufferLength = this._bufferService.buffers.normal.lines.length;
    }
    _refreshCanvasDimensions() {
      this._canvas.style.width = `${this._width}px`;
      this._canvas.width = Math.round(this._width * this._coreBrowserService.dpr);
      this._canvas.style.height = `${this._screenElement.clientHeight}px`;
      this._canvas.height = Math.round(this._screenElement.clientHeight * this._coreBrowserService.dpr);
      this._refreshDrawConstants();
      this._refreshColorZonePadding();
    }
    _refreshDecorations() {
      if (this._shouldUpdateDimensions) {
        this._refreshCanvasDimensions();
      }
      this._ctx.clearRect(0, 0, this._canvas.width, this._canvas.height);
      this._colorZoneStore.clear();
      for (const decoration of this._decorationService.decorations) {
        this._colorZoneStore.addDecoration(decoration);
      }
      this._ctx.lineWidth = 1;
      this._renderRulerOutline();
      const zones = this._colorZoneStore.zones;
      for (const zone of zones) {
        if (zone.position !== "full") {
          this._renderColorZone(zone);
        }
      }
      for (const zone of zones) {
        if (zone.position === "full") {
          this._renderColorZone(zone);
        }
      }
      this._shouldUpdateDimensions = false;
      this._shouldUpdateAnchor = false;
    }
    _renderRulerOutline() {
      this._ctx.fillStyle = this._themeService.colors.overviewRulerBorder.css;
      this._ctx.fillRect(0, 0, 1 /* OVERVIEW_RULER_BORDER_WIDTH */, this._canvas.height);
      if (this._optionsService.rawOptions.overviewRuler.showTopBorder) {
        this._ctx.fillRect(1 /* OVERVIEW_RULER_BORDER_WIDTH */, 0, this._canvas.width - 1 /* OVERVIEW_RULER_BORDER_WIDTH */, 1 /* OVERVIEW_RULER_BORDER_WIDTH */);
      }
      if (this._optionsService.rawOptions.overviewRuler.showBottomBorder) {
        this._ctx.fillRect(1 /* OVERVIEW_RULER_BORDER_WIDTH */, this._canvas.height - 1 /* OVERVIEW_RULER_BORDER_WIDTH */, this._canvas.width - 1 /* OVERVIEW_RULER_BORDER_WIDTH */, this._canvas.height);
      }
    }
    _renderColorZone(zone) {
      this._ctx.fillStyle = zone.color;
      this._ctx.fillRect(
        /* x */
        drawX[zone.position || "full"],
        /* y */
        Math.round(
          (this._canvas.height - 1) * // -1 to ensure at least 2px are allowed for decoration on last line
          (zone.startBufferLine / this._bufferService.buffers.active.lines.length) - drawHeight[zone.position || "full"] / 2
        ),
        /* w */
        drawWidth[zone.position || "full"],
        /* h */
        Math.round(
          (this._canvas.height - 1) * // -1 to ensure at least 2px are allowed for decoration on last line
          ((zone.endBufferLine - zone.startBufferLine) / this._bufferService.buffers.active.lines.length) + drawHeight[zone.position || "full"]
        )
      );
    }
    _queueRefresh(updateCanvasDimensions, updateAnchor) {
      this._shouldUpdateDimensions = updateCanvasDimensions || this._shouldUpdateDimensions;
      this._shouldUpdateAnchor = updateAnchor || this._shouldUpdateAnchor;
      if (this._animationFrame !== void 0) {
        return;
      }
      this._animationFrame = this._coreBrowserService.window.requestAnimationFrame(() => {
        this._refreshDecorations();
        this._animationFrame = void 0;
      });
    }
  };
  OverviewRulerRenderer = __decorateClass([
    __decorateParam(2, IBufferService),
    __decorateParam(3, IDecorationService),
    __decorateParam(4, IRenderService),
    __decorateParam(5, IOptionsService),
    __decorateParam(6, IThemeService),
    __decorateParam(7, ICoreBrowserService)
  ], OverviewRulerRenderer);

  // node_modules/@xterm/xterm/src/common/data/EscapeSequences.ts
  var C0;
  ((C02) => {
    C02.NUL = "\0";
    C02.SOH = "";
    C02.STX = "";
    C02.ETX = "";
    C02.EOT = "";
    C02.ENQ = "";
    C02.ACK = "";
    C02.BEL = "\x07";
    C02.BS = "\b";
    C02.HT = "	";
    C02.LF = "\n";
    C02.VT = "\v";
    C02.FF = "\f";
    C02.CR = "\r";
    C02.SO = "";
    C02.SI = "";
    C02.DLE = "";
    C02.DC1 = "";
    C02.DC2 = "";
    C02.DC3 = "";
    C02.DC4 = "";
    C02.NAK = "";
    C02.SYN = "";
    C02.ETB = "";
    C02.CAN = "";
    C02.EM = "";
    C02.SUB = "";
    C02.ESC = "\x1B";
    C02.FS = "";
    C02.GS = "";
    C02.RS = "";
    C02.US = "";
    C02.SP = " ";
    C02.DEL = "";
  })(C0 || (C0 = {}));
  var C1;
  ((C12) => {
    C12.PAD = "";
    C12.HOP = "";
    C12.BPH = "";
    C12.NBH = "";
    C12.IND = "";
    C12.NEL = "";
    C12.SSA = "";
    C12.ESA = "";
    C12.HTS = "";
    C12.HTJ = "";
    C12.VTS = "";
    C12.PLD = "";
    C12.PLU = "";
    C12.RI = "";
    C12.SS2 = "";
    C12.SS3 = "";
    C12.DCS = "";
    C12.PU1 = "";
    C12.PU2 = "";
    C12.STS = "";
    C12.CCH = "";
    C12.MW = "";
    C12.SPA = "";
    C12.EPA = "";
    C12.SOS = "";
    C12.SGCI = "";
    C12.SCI = "";
    C12.CSI = "";
    C12.ST = "";
    C12.OSC = "";
    C12.PM = "";
    C12.APC = "";
  })(C1 || (C1 = {}));
  var C1_ESCAPED;
  ((C1_ESCAPED2) => {
    C1_ESCAPED2.ST = `${C0.ESC}\\`;
  })(C1_ESCAPED || (C1_ESCAPED = {}));

  // node_modules/@xterm/xterm/src/browser/input/CompositionHelper.ts
  var CompositionHelper = class {
    constructor(_textarea, _compositionView, _bufferService, _optionsService, _coreService, _renderService) {
      this._textarea = _textarea;
      this._compositionView = _compositionView;
      this._bufferService = _bufferService;
      this._optionsService = _optionsService;
      this._coreService = _coreService;
      this._renderService = _renderService;
      this._isComposing = false;
      this._isSendingComposition = false;
      this._compositionPosition = { start: 0, end: 0 };
      this._dataAlreadySent = "";
    }
    get isComposing() {
      return this._isComposing;
    }
    /**
     * Handles the compositionstart event, activating the composition view.
     */
    compositionstart() {
      this._isComposing = true;
      this._compositionPosition.start = this._textarea.value.length;
      this._compositionView.textContent = "";
      this._dataAlreadySent = "";
      this._compositionView.classList.add("active");
    }
    /**
     * Handles the compositionupdate event, updating the composition view.
     * @param ev The event.
     */
    compositionupdate(ev) {
      this._compositionView.textContent = ev.data;
      this.updateCompositionElements();
      setTimeout(() => {
        this._compositionPosition.end = this._textarea.value.length;
      }, 0);
    }
    /**
     * Handles the compositionend event, hiding the composition view and sending the composition to
     * the handler.
     */
    compositionend() {
      this._finalizeComposition(true);
    }
    /**
     * Handles the keydown event, routing any necessary events to the CompositionHelper functions.
     * @param ev The keydown event.
     * @returns Whether the Terminal should continue processing the keydown event.
     */
    keydown(ev) {
      if (this._isComposing || this._isSendingComposition) {
        if (ev.keyCode === 20 || ev.keyCode === 229) {
          return false;
        }
        if (ev.keyCode === 16 || ev.keyCode === 17 || ev.keyCode === 18) {
          return false;
        }
        this._finalizeComposition(false);
      }
      if (ev.keyCode === 229) {
        this._handleAnyTextareaChanges();
        return false;
      }
      return true;
    }
    /**
     * Finalizes the composition, resuming regular input actions. This is called when a composition
     * is ending.
     * @param waitForPropagation Whether to wait for events to propagate before sending
     *   the input. This should be false if a non-composition keystroke is entered before the
     *   compositionend event is triggered, such as enter, so that the composition is sent before
     *   the command is executed.
     */
    _finalizeComposition(waitForPropagation) {
      this._compositionView.classList.remove("active");
      this._isComposing = false;
      if (!waitForPropagation) {
        this._isSendingComposition = false;
        const input = this._textarea.value.substring(this._compositionPosition.start, this._compositionPosition.end);
        this._coreService.triggerDataEvent(input, true);
      } else {
        const currentCompositionPosition = {
          start: this._compositionPosition.start,
          end: this._compositionPosition.end
        };
        this._isSendingComposition = true;
        setTimeout(() => {
          if (this._isSendingComposition) {
            this._isSendingComposition = false;
            let input;
            currentCompositionPosition.start += this._dataAlreadySent.length;
            if (this._isComposing) {
              input = this._textarea.value.substring(currentCompositionPosition.start, this._compositionPosition.start);
            } else {
              input = this._textarea.value.substring(currentCompositionPosition.start);
            }
            if (input.length > 0) {
              this._coreService.triggerDataEvent(input, true);
            }
          }
        }, 0);
      }
    }
    /**
     * Apply any changes made to the textarea after the current event chain is allowed to complete.
     * This should be called when not currently composing but a keydown event with the "composition
     * character" (229) is triggered, in order to allow non-composition text to be entered when an
     * IME is active.
     */
    _handleAnyTextareaChanges() {
      const oldValue = this._textarea.value;
      setTimeout(() => {
        if (!this._isComposing) {
          const newValue = this._textarea.value;
          const diff = newValue.replace(oldValue, "");
          this._dataAlreadySent = diff;
          if (newValue.length > oldValue.length) {
            this._coreService.triggerDataEvent(diff, true);
          } else if (newValue.length < oldValue.length) {
            this._coreService.triggerDataEvent(`${C0.DEL}`, true);
          } else if (newValue.length === oldValue.length && newValue !== oldValue) {
            this._coreService.triggerDataEvent(newValue, true);
          }
        }
      }, 0);
    }
    /**
     * Positions the composition view on top of the cursor and the textarea just below it (so the
     * IME helper dialog is positioned correctly).
     * @param dontRecurse Whether to use setTimeout to recursively trigger another update, this is
     *   necessary as the IME events across browsers are not consistently triggered.
     */
    updateCompositionElements(dontRecurse) {
      if (!this._isComposing) {
        return;
      }
      if (this._bufferService.buffer.isCursorInViewport) {
        const cursorX = Math.min(this._bufferService.buffer.x, this._bufferService.cols - 1);
        const cellHeight = this._renderService.dimensions.css.cell.height;
        const cursorTop = this._bufferService.buffer.y * this._renderService.dimensions.css.cell.height;
        const cursorLeft = cursorX * this._renderService.dimensions.css.cell.width;
        this._compositionView.style.left = cursorLeft + "px";
        this._compositionView.style.top = cursorTop + "px";
        this._compositionView.style.height = cellHeight + "px";
        this._compositionView.style.lineHeight = cellHeight + "px";
        this._compositionView.style.fontFamily = this._optionsService.rawOptions.fontFamily;
        this._compositionView.style.fontSize = this._optionsService.rawOptions.fontSize + "px";
        const compositionViewBounds = this._compositionView.getBoundingClientRect();
        this._textarea.style.left = cursorLeft + "px";
        this._textarea.style.top = cursorTop + "px";
        this._textarea.style.width = Math.max(compositionViewBounds.width, 1) + "px";
        this._textarea.style.height = Math.max(compositionViewBounds.height, 1) + "px";
        this._textarea.style.lineHeight = compositionViewBounds.height + "px";
      }
      if (!dontRecurse) {
        setTimeout(() => this.updateCompositionElements(true), 0);
      }
    }
  };
  CompositionHelper = __decorateClass([
    __decorateParam(2, IBufferService),
    __decorateParam(3, IOptionsService),
    __decorateParam(4, ICoreService),
    __decorateParam(5, IRenderService)
  ], CompositionHelper);

  // node_modules/@xterm/xterm/src/browser/renderer/shared/Constants.ts
  var INVERTED_DEFAULT_COLOR = 257;

  // node_modules/@xterm/xterm/src/common/Color.ts
  var $r = 0;
  var $g = 0;
  var $b = 0;
  var $a = 0;
  var NULL_COLOR = {
    css: "#00000000",
    rgba: 0
  };
  var channels;
  ((channels2) => {
    function toCss(r, g, b, a) {
      if (a !== void 0) {
        return `#${toPaddedHex(r)}${toPaddedHex(g)}${toPaddedHex(b)}${toPaddedHex(a)}`;
      }
      return `#${toPaddedHex(r)}${toPaddedHex(g)}${toPaddedHex(b)}`;
    }
    channels2.toCss = toCss;
    function toRgba(r, g, b, a = 255) {
      return (r << 24 | g << 16 | b << 8 | a) >>> 0;
    }
    channels2.toRgba = toRgba;
    function toColor(r, g, b, a) {
      return {
        css: channels2.toCss(r, g, b, a),
        rgba: channels2.toRgba(r, g, b, a)
      };
    }
    channels2.toColor = toColor;
  })(channels || (channels = {}));
  var color;
  ((color2) => {
    function blend(bg, fg) {
      $a = (fg.rgba & 255) / 255;
      if ($a === 1) {
        return {
          css: fg.css,
          rgba: fg.rgba
        };
      }
      const fgR = fg.rgba >> 24 & 255;
      const fgG = fg.rgba >> 16 & 255;
      const fgB = fg.rgba >> 8 & 255;
      const bgR = bg.rgba >> 24 & 255;
      const bgG = bg.rgba >> 16 & 255;
      const bgB = bg.rgba >> 8 & 255;
      $r = bgR + Math.round((fgR - bgR) * $a);
      $g = bgG + Math.round((fgG - bgG) * $a);
      $b = bgB + Math.round((fgB - bgB) * $a);
      const css2 = channels.toCss($r, $g, $b);
      const rgba2 = channels.toRgba($r, $g, $b);
      return { css: css2, rgba: rgba2 };
    }
    color2.blend = blend;
    function isOpaque(color3) {
      return (color3.rgba & 255) === 255;
    }
    color2.isOpaque = isOpaque;
    function ensureContrastRatio(bg, fg, ratio) {
      const result = rgba.ensureContrastRatio(bg.rgba, fg.rgba, ratio);
      if (!result) {
        return void 0;
      }
      return channels.toColor(
        result >> 24 & 255,
        result >> 16 & 255,
        result >> 8 & 255
      );
    }
    color2.ensureContrastRatio = ensureContrastRatio;
    function opaque(color3) {
      const rgbaColor = (color3.rgba | 255) >>> 0;
      [$r, $g, $b] = rgba.toChannels(rgbaColor);
      return {
        css: channels.toCss($r, $g, $b),
        rgba: rgbaColor
      };
    }
    color2.opaque = opaque;
    function opacity(color3, opacity2) {
      $a = Math.round(opacity2 * 255);
      [$r, $g, $b] = rgba.toChannels(color3.rgba);
      return {
        css: channels.toCss($r, $g, $b, $a),
        rgba: channels.toRgba($r, $g, $b, $a)
      };
    }
    color2.opacity = opacity;
    function multiplyOpacity(color3, factor) {
      $a = color3.rgba & 255;
      return opacity(color3, $a * factor / 255);
    }
    color2.multiplyOpacity = multiplyOpacity;
    function toColorRGB(color3) {
      return [color3.rgba >> 24 & 255, color3.rgba >> 16 & 255, color3.rgba >> 8 & 255];
    }
    color2.toColorRGB = toColorRGB;
  })(color || (color = {}));
  var css;
  ((css2) => {
    let $ctx;
    let $litmusColor;
    try {
      const canvas = document.createElement("canvas");
      canvas.width = 1;
      canvas.height = 1;
      const ctx = canvas.getContext("2d", {
        willReadFrequently: true
      });
      if (ctx) {
        $ctx = ctx;
        $ctx.globalCompositeOperation = "copy";
        $litmusColor = $ctx.createLinearGradient(0, 0, 1, 1);
      }
    } catch {
    }
    function toColor(css3) {
      if (css3.match(/#[\da-f]{3,8}/i)) {
        switch (css3.length) {
          case 4: {
            $r = parseInt(css3.slice(1, 2).repeat(2), 16);
            $g = parseInt(css3.slice(2, 3).repeat(2), 16);
            $b = parseInt(css3.slice(3, 4).repeat(2), 16);
            return channels.toColor($r, $g, $b);
          }
          case 5: {
            $r = parseInt(css3.slice(1, 2).repeat(2), 16);
            $g = parseInt(css3.slice(2, 3).repeat(2), 16);
            $b = parseInt(css3.slice(3, 4).repeat(2), 16);
            $a = parseInt(css3.slice(4, 5).repeat(2), 16);
            return channels.toColor($r, $g, $b, $a);
          }
          case 7:
            return {
              css: css3,
              rgba: (parseInt(css3.slice(1), 16) << 8 | 255) >>> 0
            };
          case 9:
            return {
              css: css3,
              rgba: parseInt(css3.slice(1), 16) >>> 0
            };
        }
      }
      const rgbaMatch = css3.match(/rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*(,\s*(0|1|\d?\.(\d+))\s*)?\)/);
      if (rgbaMatch) {
        $r = parseInt(rgbaMatch[1]);
        $g = parseInt(rgbaMatch[2]);
        $b = parseInt(rgbaMatch[3]);
        $a = Math.round((rgbaMatch[5] === void 0 ? 1 : parseFloat(rgbaMatch[5])) * 255);
        return channels.toColor($r, $g, $b, $a);
      }
      if (!$ctx || !$litmusColor) {
        throw new Error("css.toColor: Unsupported css format");
      }
      $ctx.fillStyle = $litmusColor;
      $ctx.fillStyle = css3;
      if (typeof $ctx.fillStyle !== "string") {
        throw new Error("css.toColor: Unsupported css format");
      }
      $ctx.fillRect(0, 0, 1, 1);
      [$r, $g, $b, $a] = $ctx.getImageData(0, 0, 1, 1).data;
      if ($a !== 255) {
        throw new Error("css.toColor: Unsupported css format");
      }
      return {
        rgba: channels.toRgba($r, $g, $b, $a),
        css: css3
      };
    }
    css2.toColor = toColor;
  })(css || (css = {}));
  var rgb;
  ((rgb2) => {
    function relativeLuminance(rgb3) {
      return relativeLuminance2(
        rgb3 >> 16 & 255,
        rgb3 >> 8 & 255,
        rgb3 & 255
      );
    }
    rgb2.relativeLuminance = relativeLuminance;
    function relativeLuminance2(r, g, b) {
      const rs = r / 255;
      const gs = g / 255;
      const bs = b / 255;
      const rr = rs <= 0.03928 ? rs / 12.92 : Math.pow((rs + 0.055) / 1.055, 2.4);
      const rg = gs <= 0.03928 ? gs / 12.92 : Math.pow((gs + 0.055) / 1.055, 2.4);
      const rb = bs <= 0.03928 ? bs / 12.92 : Math.pow((bs + 0.055) / 1.055, 2.4);
      return rr * 0.2126 + rg * 0.7152 + rb * 0.0722;
    }
    rgb2.relativeLuminance2 = relativeLuminance2;
  })(rgb || (rgb = {}));
  var rgba;
  ((rgba2) => {
    function blend(bg, fg) {
      $a = (fg & 255) / 255;
      if ($a === 1) {
        return fg;
      }
      const fgR = fg >> 24 & 255;
      const fgG = fg >> 16 & 255;
      const fgB = fg >> 8 & 255;
      const bgR = bg >> 24 & 255;
      const bgG = bg >> 16 & 255;
      const bgB = bg >> 8 & 255;
      $r = bgR + Math.round((fgR - bgR) * $a);
      $g = bgG + Math.round((fgG - bgG) * $a);
      $b = bgB + Math.round((fgB - bgB) * $a);
      return channels.toRgba($r, $g, $b);
    }
    rgba2.blend = blend;
    function ensureContrastRatio(bgRgba, fgRgba, ratio) {
      const bgL = rgb.relativeLuminance(bgRgba >> 8);
      const fgL = rgb.relativeLuminance(fgRgba >> 8);
      const cr = contrastRatio(bgL, fgL);
      if (cr < ratio) {
        if (fgL < bgL) {
          const resultA2 = reduceLuminance(bgRgba, fgRgba, ratio);
          const resultARatio2 = contrastRatio(bgL, rgb.relativeLuminance(resultA2 >> 8));
          if (resultARatio2 < ratio) {
            const resultB = increaseLuminance(bgRgba, fgRgba, ratio);
            const resultBRatio = contrastRatio(bgL, rgb.relativeLuminance(resultB >> 8));
            return resultARatio2 > resultBRatio ? resultA2 : resultB;
          }
          return resultA2;
        }
        const resultA = increaseLuminance(bgRgba, fgRgba, ratio);
        const resultARatio = contrastRatio(bgL, rgb.relativeLuminance(resultA >> 8));
        if (resultARatio < ratio) {
          const resultB = reduceLuminance(bgRgba, fgRgba, ratio);
          const resultBRatio = contrastRatio(bgL, rgb.relativeLuminance(resultB >> 8));
          return resultARatio > resultBRatio ? resultA : resultB;
        }
        return resultA;
      }
      return void 0;
    }
    rgba2.ensureContrastRatio = ensureContrastRatio;
    function reduceLuminance(bgRgba, fgRgba, ratio) {
      const bgR = bgRgba >> 24 & 255;
      const bgG = bgRgba >> 16 & 255;
      const bgB = bgRgba >> 8 & 255;
      let fgR = fgRgba >> 24 & 255;
      let fgG = fgRgba >> 16 & 255;
      let fgB = fgRgba >> 8 & 255;
      let cr = contrastRatio(rgb.relativeLuminance2(fgR, fgG, fgB), rgb.relativeLuminance2(bgR, bgG, bgB));
      while (cr < ratio && (fgR > 0 || fgG > 0 || fgB > 0)) {
        fgR -= Math.max(0, Math.ceil(fgR * 0.1));
        fgG -= Math.max(0, Math.ceil(fgG * 0.1));
        fgB -= Math.max(0, Math.ceil(fgB * 0.1));
        cr = contrastRatio(rgb.relativeLuminance2(fgR, fgG, fgB), rgb.relativeLuminance2(bgR, bgG, bgB));
      }
      return (fgR << 24 | fgG << 16 | fgB << 8 | 255) >>> 0;
    }
    rgba2.reduceLuminance = reduceLuminance;
    function increaseLuminance(bgRgba, fgRgba, ratio) {
      const bgR = bgRgba >> 24 & 255;
      const bgG = bgRgba >> 16 & 255;
      const bgB = bgRgba >> 8 & 255;
      let fgR = fgRgba >> 24 & 255;
      let fgG = fgRgba >> 16 & 255;
      let fgB = fgRgba >> 8 & 255;
      let cr = contrastRatio(rgb.relativeLuminance2(fgR, fgG, fgB), rgb.relativeLuminance2(bgR, bgG, bgB));
      while (cr < ratio && (fgR < 255 || fgG < 255 || fgB < 255)) {
        fgR = Math.min(255, fgR + Math.ceil((255 - fgR) * 0.1));
        fgG = Math.min(255, fgG + Math.ceil((255 - fgG) * 0.1));
        fgB = Math.min(255, fgB + Math.ceil((255 - fgB) * 0.1));
        cr = contrastRatio(rgb.relativeLuminance2(fgR, fgG, fgB), rgb.relativeLuminance2(bgR, bgG, bgB));
      }
      return (fgR << 24 | fgG << 16 | fgB << 8 | 255) >>> 0;
    }
    rgba2.increaseLuminance = increaseLuminance;
    function toChannels(value) {
      return [value >> 24 & 255, value >> 16 & 255, value >> 8 & 255, value & 255];
    }
    rgba2.toChannels = toChannels;
  })(rgba || (rgba = {}));
  function toPaddedHex(c) {
    const s = c.toString(16);
    return s.length < 2 ? "0" + s : s;
  }
  function contrastRatio(l1, l2) {
    if (l1 < l2) {
      return (l2 + 0.05) / (l1 + 0.05);
    }
    return (l1 + 0.05) / (l2 + 0.05);
  }

  // node_modules/@xterm/xterm/src/browser/services/CharacterJoinerService.ts
  var JoinedCellData = class extends AttributeData {
    constructor(firstCell, chars, width) {
      super();
      // .content carries no meaning for joined CellData, simply nullify it
      // thus we have to overload all other .content accessors
      this.content = 0;
      this.combinedData = "";
      this.fg = firstCell.fg;
      this.bg = firstCell.bg;
      this.combinedData = chars;
      this._width = width;
    }
    isCombined() {
      return 2097152 /* IS_COMBINED_MASK */;
    }
    getWidth() {
      return this._width;
    }
    getChars() {
      return this.combinedData;
    }
    getCode() {
      return 2097151;
    }
    setFromCharData(value) {
      throw new Error("not implemented");
    }
    getAsCharData() {
      return [this.fg, this.getChars(), this.getWidth(), this.getCode()];
    }
  };
  var CharacterJoinerService = class {
    constructor(_bufferService) {
      this._bufferService = _bufferService;
      this._characterJoiners = [];
      this._nextCharacterJoinerId = 0;
      this._workCell = new CellData();
    }
    register(handler) {
      const joiner = {
        id: this._nextCharacterJoinerId++,
        handler
      };
      this._characterJoiners.push(joiner);
      return joiner.id;
    }
    deregister(joinerId) {
      for (let i2 = 0; i2 < this._characterJoiners.length; i2++) {
        if (this._characterJoiners[i2].id === joinerId) {
          this._characterJoiners.splice(i2, 1);
          return true;
        }
      }
      return false;
    }
    getJoinedCharacters(row) {
      if (this._characterJoiners.length === 0) {
        return [];
      }
      const line = this._bufferService.buffer.lines.get(row);
      if (!line || line.length === 0) {
        return [];
      }
      const ranges = [];
      const lineStr = line.translateToString(true);
      let rangeStartColumn = 0;
      let currentStringIndex = 0;
      let rangeStartStringIndex = 0;
      let rangeAttrFG = line.getFg(0);
      let rangeAttrBG = line.getBg(0);
      for (let x = 0; x < line.getTrimmedLength(); x++) {
        line.loadCell(x, this._workCell);
        if (this._workCell.getWidth() === 0) {
          continue;
        }
        if (this._workCell.fg !== rangeAttrFG || this._workCell.bg !== rangeAttrBG) {
          if (x - rangeStartColumn > 1) {
            const joinedRanges = this._getJoinedRanges(
              lineStr,
              rangeStartStringIndex,
              currentStringIndex,
              line,
              rangeStartColumn
            );
            for (let i2 = 0; i2 < joinedRanges.length; i2++) {
              ranges.push(joinedRanges[i2]);
            }
          }
          rangeStartColumn = x;
          rangeStartStringIndex = currentStringIndex;
          rangeAttrFG = this._workCell.fg;
          rangeAttrBG = this._workCell.bg;
        }
        currentStringIndex += this._workCell.getChars().length || WHITESPACE_CELL_CHAR.length;
      }
      if (this._bufferService.cols - rangeStartColumn > 1) {
        const joinedRanges = this._getJoinedRanges(
          lineStr,
          rangeStartStringIndex,
          currentStringIndex,
          line,
          rangeStartColumn
        );
        for (let i2 = 0; i2 < joinedRanges.length; i2++) {
          ranges.push(joinedRanges[i2]);
        }
      }
      return ranges;
    }
    /**
     * Given a segment of a line of text, find all ranges of text that should be
     * joined in a single rendering unit. Ranges are internally converted to
     * column ranges, rather than string ranges.
     * @param line String representation of the full line of text
     * @param startIndex Start position of the range to search in the string (inclusive)
     * @param endIndex End position of the range to search in the string (exclusive)
     */
    _getJoinedRanges(line, startIndex, endIndex, lineData, startCol) {
      const text = line.substring(startIndex, endIndex);
      let allJoinedRanges = [];
      try {
        allJoinedRanges = this._characterJoiners[0].handler(text);
      } catch (error) {
        console.error(error);
      }
      for (let i2 = 1; i2 < this._characterJoiners.length; i2++) {
        try {
          const joinerRanges = this._characterJoiners[i2].handler(text);
          for (let j = 0; j < joinerRanges.length; j++) {
            CharacterJoinerService._mergeRanges(allJoinedRanges, joinerRanges[j]);
          }
        } catch (error) {
          console.error(error);
        }
      }
      this._stringRangesToCellRanges(allJoinedRanges, lineData, startCol);
      return allJoinedRanges;
    }
    /**
     * Modifies the provided ranges in-place to adjust for variations between
     * string length and cell width so that the range represents a cell range,
     * rather than the string range the joiner provides.
     * @param ranges String ranges containing start (inclusive) and end (exclusive) index
     * @param line Cell data for the relevant line in the terminal
     * @param startCol Offset within the line to start from
     */
    _stringRangesToCellRanges(ranges, line, startCol) {
      let currentRangeIndex = 0;
      let currentRangeStarted = false;
      let currentStringIndex = 0;
      let currentRange = ranges[currentRangeIndex];
      if (!currentRange) {
        return;
      }
      for (let x = startCol; x < this._bufferService.cols; x++) {
        const width = line.getWidth(x);
        const length = line.getString(x).length || WHITESPACE_CELL_CHAR.length;
        if (width === 0) {
          continue;
        }
        if (!currentRangeStarted && currentRange[0] <= currentStringIndex) {
          currentRange[0] = x;
          currentRangeStarted = true;
        }
        if (currentRange[1] <= currentStringIndex) {
          currentRange[1] = x;
          currentRange = ranges[++currentRangeIndex];
          if (!currentRange) {
            break;
          }
          if (currentRange[0] <= currentStringIndex) {
            currentRange[0] = x;
            currentRangeStarted = true;
          } else {
            currentRangeStarted = false;
          }
        }
        currentStringIndex += length;
      }
      if (currentRange) {
        currentRange[1] = this._bufferService.cols;
      }
    }
    /**
     * Merges the range defined by the provided start and end into the list of
     * existing ranges. The merge is done in place on the existing range for
     * performance and is also returned.
     * @param ranges Existing range list
     * @param newRange Tuple of two numbers representing the new range to merge in.
     * @returns The ranges input with the new range merged in place
     */
    static _mergeRanges(ranges, newRange) {
      let inRange = false;
      for (let i2 = 0; i2 < ranges.length; i2++) {
        const range = ranges[i2];
        if (!inRange) {
          if (newRange[1] <= range[0]) {
            ranges.splice(i2, 0, newRange);
            return ranges;
          }
          if (newRange[1] <= range[1]) {
            range[0] = Math.min(newRange[0], range[0]);
            return ranges;
          }
          if (newRange[0] < range[1]) {
            range[0] = Math.min(newRange[0], range[0]);
            inRange = true;
          }
          continue;
        } else {
          if (newRange[1] <= range[0]) {
            ranges[i2 - 1][1] = newRange[1];
            return ranges;
          }
          if (newRange[1] <= range[1]) {
            ranges[i2 - 1][1] = Math.max(newRange[1], range[1]);
            ranges.splice(i2, 1);
            return ranges;
          }
          ranges.splice(i2, 1);
          i2--;
        }
      }
      if (inRange) {
        ranges[ranges.length - 1][1] = newRange[1];
      } else {
        ranges.push(newRange);
      }
      return ranges;
    }
  };
  CharacterJoinerService = __decorateClass([
    __decorateParam(0, IBufferService)
  ], CharacterJoinerService);

  // node_modules/@xterm/xterm/src/browser/renderer/shared/RendererUtils.ts
  function isPowerlineGlyph(codepoint) {
    return 57508 <= codepoint && codepoint <= 57558;
  }
  function isBoxOrBlockGlyph(codepoint) {
    return 9472 <= codepoint && codepoint <= 9631;
  }
  function treatGlyphAsBackgroundColor(codepoint) {
    return isPowerlineGlyph(codepoint) || isBoxOrBlockGlyph(codepoint);
  }
  function createRenderDimensions() {
    return {
      css: {
        canvas: createDimension(),
        cell: createDimension()
      },
      device: {
        canvas: createDimension(),
        cell: createDimension(),
        char: {
          width: 0,
          height: 0,
          left: 0,
          top: 0
        }
      }
    };
  }
  function createDimension() {
    return {
      width: 0,
      height: 0
    };
  }

  // node_modules/@xterm/xterm/src/browser/renderer/dom/DomRendererRowFactory.ts
  var DomRendererRowFactory = class {
    constructor(_document, _characterJoinerService, _optionsService, _coreBrowserService, _coreService, _decorationService, _themeService) {
      this._document = _document;
      this._characterJoinerService = _characterJoinerService;
      this._optionsService = _optionsService;
      this._coreBrowserService = _coreBrowserService;
      this._coreService = _coreService;
      this._decorationService = _decorationService;
      this._themeService = _themeService;
      this._workCell = new CellData();
      this._columnSelectMode = false;
      this.defaultSpacing = 0;
    }
    handleSelectionChanged(start, end, columnSelectMode) {
      this._selectionStart = start;
      this._selectionEnd = end;
      this._columnSelectMode = columnSelectMode;
    }
    createRow(lineData, row, isCursorRow, cursorStyle, cursorInactiveStyle, cursorX, cursorBlink, cellWidth, widthCache, linkStart, linkEnd) {
      const elements = [];
      const joinedRanges = this._characterJoinerService.getJoinedCharacters(row);
      const colors = this._themeService.colors;
      let lineLength = lineData.getNoBgTrimmedLength();
      if (isCursorRow && lineLength < cursorX + 1) {
        lineLength = cursorX + 1;
      }
      let charElement;
      let cellAmount = 0;
      let text = "";
      let i2 = 0;
      let oldBg = 0;
      let oldFg = 0;
      let oldExt = 0;
      let oldLinkHover = false;
      let oldSpacing = 0;
      let oldIsInSelection = false;
      let spacing = 0;
      let skipJoinedCheckUntilX = 0;
      const classes = [];
      const hasHover = linkStart !== -1 && linkEnd !== -1;
      for (let x = 0; x < lineLength; x++) {
        lineData.loadCell(x, this._workCell);
        let width = this._workCell.getWidth();
        if (width === 0) {
          continue;
        }
        let isJoined = false;
        let isValidJoinRange = x >= skipJoinedCheckUntilX;
        let lastCharX = x;
        let cell = this._workCell;
        if (joinedRanges.length > 0 && x === joinedRanges[0][0] && isValidJoinRange) {
          const range = joinedRanges.shift();
          const firstSelectionState = this._isCellInSelection(range[0], row);
          for (i2 = range[0] + 1; i2 < range[1]; i2++) {
            isValidJoinRange && (isValidJoinRange = firstSelectionState === this._isCellInSelection(i2, row));
          }
          isValidJoinRange && (isValidJoinRange = !isCursorRow || cursorX < range[0] || cursorX >= range[1]);
          if (!isValidJoinRange) {
            skipJoinedCheckUntilX = range[1];
          } else {
            isJoined = true;
            cell = new JoinedCellData(
              this._workCell,
              lineData.translateToString(true, range[0], range[1]),
              range[1] - range[0]
            );
            lastCharX = range[1] - 1;
            width = cell.getWidth();
          }
        }
        const isInSelection = this._isCellInSelection(x, row);
        const isCursorCell = isCursorRow && x === cursorX;
        const isLinkHover = hasHover && x >= linkStart && x <= linkEnd;
        let isDecorated = false;
        this._decorationService.forEachDecorationAtCell(x, row, void 0, (d) => {
          isDecorated = true;
        });
        let chars = cell.getChars() || WHITESPACE_CELL_CHAR;
        if (chars === " " && (cell.isUnderline() || cell.isOverline())) {
          chars = " ";
        }
        spacing = width * cellWidth - widthCache.get(chars, cell.isBold(), cell.isItalic());
        if (!charElement) {
          charElement = this._document.createElement("span");
        } else {
          if (cellAmount && (isInSelection && oldIsInSelection || !isInSelection && !oldIsInSelection && cell.bg === oldBg) && (isInSelection && oldIsInSelection && colors.selectionForeground || cell.fg === oldFg) && cell.extended.ext === oldExt && isLinkHover === oldLinkHover && spacing === oldSpacing && !isCursorCell && !isJoined && !isDecorated && isValidJoinRange) {
            if (cell.isInvisible()) {
              text += WHITESPACE_CELL_CHAR;
            } else {
              text += chars;
            }
            cellAmount++;
            continue;
          } else {
            if (cellAmount) {
              charElement.textContent = text;
            }
            charElement = this._document.createElement("span");
            cellAmount = 0;
            text = "";
          }
        }
        oldBg = cell.bg;
        oldFg = cell.fg;
        oldExt = cell.extended.ext;
        oldLinkHover = isLinkHover;
        oldSpacing = spacing;
        oldIsInSelection = isInSelection;
        if (isJoined) {
          if (cursorX >= x && cursorX <= lastCharX) {
            cursorX = x;
          }
        }
        if (!this._coreService.isCursorHidden && isCursorCell && this._coreService.isCursorInitialized) {
          classes.push("xterm-cursor" /* CURSOR_CLASS */);
          if (this._coreBrowserService.isFocused) {
            if (cursorBlink) {
              classes.push("xterm-cursor-blink" /* CURSOR_BLINK_CLASS */);
            }
            classes.push(
              cursorStyle === "bar" ? "xterm-cursor-bar" /* CURSOR_STYLE_BAR_CLASS */ : cursorStyle === "underline" ? "xterm-cursor-underline" /* CURSOR_STYLE_UNDERLINE_CLASS */ : "xterm-cursor-block" /* CURSOR_STYLE_BLOCK_CLASS */
            );
          } else {
            if (cursorInactiveStyle) {
              switch (cursorInactiveStyle) {
                case "outline":
                  classes.push("xterm-cursor-outline" /* CURSOR_STYLE_OUTLINE_CLASS */);
                  break;
                case "block":
                  classes.push("xterm-cursor-block" /* CURSOR_STYLE_BLOCK_CLASS */);
                  break;
                case "bar":
                  classes.push("xterm-cursor-bar" /* CURSOR_STYLE_BAR_CLASS */);
                  break;
                case "underline":
                  classes.push("xterm-cursor-underline" /* CURSOR_STYLE_UNDERLINE_CLASS */);
                  break;
                default:
                  break;
              }
            }
          }
        }
        if (cell.isBold()) {
          classes.push("xterm-bold" /* BOLD_CLASS */);
        }
        if (cell.isItalic()) {
          classes.push("xterm-italic" /* ITALIC_CLASS */);
        }
        if (cell.isDim()) {
          classes.push("xterm-dim" /* DIM_CLASS */);
        }
        if (cell.isInvisible()) {
          text = WHITESPACE_CELL_CHAR;
        } else {
          text = cell.getChars() || WHITESPACE_CELL_CHAR;
        }
        if (cell.isUnderline()) {
          classes.push(`${"xterm-underline" /* UNDERLINE_CLASS */}-${cell.extended.underlineStyle}`);
          if (text === " ") {
            text = " ";
          }
          if (!cell.isUnderlineColorDefault()) {
            if (cell.isUnderlineColorRGB()) {
              charElement.style.textDecorationColor = `rgb(${AttributeData.toColorRGB(cell.getUnderlineColor()).join(",")})`;
            } else {
              let fg2 = cell.getUnderlineColor();
              if (this._optionsService.rawOptions.drawBoldTextInBrightColors && cell.isBold() && fg2 < 8) {
                fg2 += 8;
              }
              charElement.style.textDecorationColor = colors.ansi[fg2].css;
            }
          }
        }
        if (cell.isOverline()) {
          classes.push("xterm-overline" /* OVERLINE_CLASS */);
          if (text === " ") {
            text = " ";
          }
        }
        if (cell.isStrikethrough()) {
          classes.push("xterm-strikethrough" /* STRIKETHROUGH_CLASS */);
        }
        if (isLinkHover) {
          charElement.style.textDecoration = "underline";
        }
        let fg = cell.getFgColor();
        let fgColorMode = cell.getFgColorMode();
        let bg = cell.getBgColor();
        let bgColorMode = cell.getBgColorMode();
        const isInverse = !!cell.isInverse();
        if (isInverse) {
          const temp = fg;
          fg = bg;
          bg = temp;
          const temp2 = fgColorMode;
          fgColorMode = bgColorMode;
          bgColorMode = temp2;
        }
        let bgOverride;
        let fgOverride;
        let isTop = false;
        this._decorationService.forEachDecorationAtCell(x, row, void 0, (d) => {
          if (d.options.layer !== "top" && isTop) {
            return;
          }
          if (d.backgroundColorRGB) {
            bgColorMode = 50331648 /* CM_RGB */;
            bg = d.backgroundColorRGB.rgba >> 8 & 16777215;
            bgOverride = d.backgroundColorRGB;
          }
          if (d.foregroundColorRGB) {
            fgColorMode = 50331648 /* CM_RGB */;
            fg = d.foregroundColorRGB.rgba >> 8 & 16777215;
            fgOverride = d.foregroundColorRGB;
          }
          isTop = d.options.layer === "top";
        });
        if (!isTop && isInSelection) {
          bgOverride = this._coreBrowserService.isFocused ? colors.selectionBackgroundOpaque : colors.selectionInactiveBackgroundOpaque;
          bg = bgOverride.rgba >> 8 & 16777215;
          bgColorMode = 50331648 /* CM_RGB */;
          isTop = true;
          if (colors.selectionForeground) {
            fgColorMode = 50331648 /* CM_RGB */;
            fg = colors.selectionForeground.rgba >> 8 & 16777215;
            fgOverride = colors.selectionForeground;
          }
        }
        if (isTop) {
          classes.push("xterm-decoration-top");
        }
        let resolvedBg;
        switch (bgColorMode) {
          case 16777216 /* CM_P16 */:
          case 33554432 /* CM_P256 */:
            resolvedBg = colors.ansi[bg];
            classes.push(`xterm-bg-${bg}`);
            break;
          case 50331648 /* CM_RGB */:
            resolvedBg = channels.toColor(bg >> 16, bg >> 8 & 255, bg & 255);
            this._addStyle(charElement, `background-color:#${padStart((bg >>> 0).toString(16), "0", 6)}`);
            break;
          case 0 /* CM_DEFAULT */:
          default:
            if (isInverse) {
              resolvedBg = colors.foreground;
              classes.push(`xterm-bg-${INVERTED_DEFAULT_COLOR}`);
            } else {
              resolvedBg = colors.background;
            }
        }
        if (!bgOverride) {
          if (cell.isDim()) {
            bgOverride = color.multiplyOpacity(resolvedBg, 0.5);
          }
        }
        switch (fgColorMode) {
          case 16777216 /* CM_P16 */:
          case 33554432 /* CM_P256 */:
            if (cell.isBold() && fg < 8 && this._optionsService.rawOptions.drawBoldTextInBrightColors) {
              fg += 8;
            }
            if (!this._applyMinimumContrast(charElement, resolvedBg, colors.ansi[fg], cell, bgOverride, void 0)) {
              classes.push(`xterm-fg-${fg}`);
            }
            break;
          case 50331648 /* CM_RGB */:
            const color2 = channels.toColor(
              fg >> 16 & 255,
              fg >> 8 & 255,
              fg & 255
            );
            if (!this._applyMinimumContrast(charElement, resolvedBg, color2, cell, bgOverride, fgOverride)) {
              this._addStyle(charElement, `color:#${padStart(fg.toString(16), "0", 6)}`);
            }
            break;
          case 0 /* CM_DEFAULT */:
          default:
            if (!this._applyMinimumContrast(charElement, resolvedBg, colors.foreground, cell, bgOverride, fgOverride)) {
              if (isInverse) {
                classes.push(`xterm-fg-${INVERTED_DEFAULT_COLOR}`);
              }
            }
        }
        if (classes.length) {
          charElement.className = classes.join(" ");
          classes.length = 0;
        }
        if (!isCursorCell && !isJoined && !isDecorated && isValidJoinRange) {
          cellAmount++;
        } else {
          charElement.textContent = text;
        }
        if (spacing !== this.defaultSpacing) {
          charElement.style.letterSpacing = `${spacing}px`;
        }
        elements.push(charElement);
        x = lastCharX;
      }
      if (charElement && cellAmount) {
        charElement.textContent = text;
      }
      return elements;
    }
    _applyMinimumContrast(element, bg, fg, cell, bgOverride, fgOverride) {
      if (this._optionsService.rawOptions.minimumContrastRatio === 1 || treatGlyphAsBackgroundColor(cell.getCode())) {
        return false;
      }
      const cache = this._getContrastCache(cell);
      let adjustedColor = void 0;
      if (!bgOverride && !fgOverride) {
        adjustedColor = cache.getColor(bg.rgba, fg.rgba);
      }
      if (adjustedColor === void 0) {
        const ratio = this._optionsService.rawOptions.minimumContrastRatio / (cell.isDim() ? 2 : 1);
        adjustedColor = color.ensureContrastRatio(bgOverride || bg, fgOverride || fg, ratio);
        cache.setColor((bgOverride || bg).rgba, (fgOverride || fg).rgba, adjustedColor ?? null);
      }
      if (adjustedColor) {
        this._addStyle(element, `color:${adjustedColor.css}`);
        return true;
      }
      return false;
    }
    _getContrastCache(cell) {
      if (cell.isDim()) {
        return this._themeService.colors.halfContrastCache;
      }
      return this._themeService.colors.contrastCache;
    }
    _addStyle(element, style) {
      element.setAttribute("style", `${element.getAttribute("style") || ""}${style};`);
    }
    _isCellInSelection(x, y) {
      const start = this._selectionStart;
      const end = this._selectionEnd;
      if (!start || !end) {
        return false;
      }
      if (this._columnSelectMode) {
        if (start[0] <= end[0]) {
          return x >= start[0] && y >= start[1] && x < end[0] && y <= end[1];
        }
        return x < start[0] && y >= start[1] && x >= end[0] && y <= end[1];
      }
      return y > start[1] && y < end[1] || start[1] === end[1] && y === start[1] && x >= start[0] && x < end[0] || start[1] < end[1] && y === end[1] && x < end[0] || start[1] < end[1] && y === start[1] && x >= start[0];
    }
  };
  DomRendererRowFactory = __decorateClass([
    __decorateParam(1, ICharacterJoinerService),
    __decorateParam(2, IOptionsService),
    __decorateParam(3, ICoreBrowserService),
    __decorateParam(4, ICoreService),
    __decorateParam(5, IDecorationService),
    __decorateParam(6, IThemeService)
  ], DomRendererRowFactory);
  function padStart(text, padChar, length) {
    while (text.length < length) {
      text = padChar + text;
    }
    return text;
  }

  // node_modules/@xterm/xterm/src/browser/renderer/dom/WidthCache.ts
  var WidthCache = class {
    constructor(_document, _helperContainer) {
      // flat cache for regular variant up to CacheSettings.FLAT_SIZE
      // NOTE: ~4x faster access than holey (serving >>80% of terminal content)
      //       It has a small memory footprint (only 1MB for full BMP caching),
      //       still the sweet spot is not reached before touching 32k different codepoints,
      //       thus we store the remaining <<20% of terminal data in a holey structure.
      this._flat = new Float32Array(256 /* FLAT_SIZE */);
      this._font = "";
      this._fontSize = 0;
      this._weight = "normal";
      this._weightBold = "bold";
      this._measureElements = [];
      this._container = _document.createElement("div");
      this._container.classList.add("xterm-width-cache-measure-container");
      this._container.setAttribute("aria-hidden", "true");
      this._container.style.whiteSpace = "pre";
      this._container.style.fontKerning = "none";
      const regular = _document.createElement("span");
      regular.classList.add("xterm-char-measure-element");
      const bold = _document.createElement("span");
      bold.classList.add("xterm-char-measure-element");
      bold.style.fontWeight = "bold";
      const italic = _document.createElement("span");
      italic.classList.add("xterm-char-measure-element");
      italic.style.fontStyle = "italic";
      const boldItalic = _document.createElement("span");
      boldItalic.classList.add("xterm-char-measure-element");
      boldItalic.style.fontWeight = "bold";
      boldItalic.style.fontStyle = "italic";
      this._measureElements = [regular, bold, italic, boldItalic];
      this._container.appendChild(regular);
      this._container.appendChild(bold);
      this._container.appendChild(italic);
      this._container.appendChild(boldItalic);
      _helperContainer.appendChild(this._container);
      this.clear();
    }
    dispose() {
      this._container.remove();
      this._measureElements.length = 0;
      this._holey = void 0;
    }
    /**
     * Clear the width cache.
     */
    clear() {
      this._flat.fill(-9999 /* FLAT_UNSET */);
      this._holey = /* @__PURE__ */ new Map();
    }
    /**
     * Set the font for measuring.
     * Must be called for any changes on font settings.
     * Also clears the cache.
     */
    setFont(font, fontSize, weight, weightBold) {
      if (font === this._font && fontSize === this._fontSize && weight === this._weight && weightBold === this._weightBold) {
        return;
      }
      this._font = font;
      this._fontSize = fontSize;
      this._weight = weight;
      this._weightBold = weightBold;
      this._container.style.fontFamily = this._font;
      this._container.style.fontSize = `${this._fontSize}px`;
      this._measureElements[0 /* REGULAR */].style.fontWeight = `${weight}`;
      this._measureElements[1 /* BOLD */].style.fontWeight = `${weightBold}`;
      this._measureElements[2 /* ITALIC */].style.fontWeight = `${weight}`;
      this._measureElements[3 /* BOLD_ITALIC */].style.fontWeight = `${weightBold}`;
      this.clear();
    }
    /**
     * Get the render width for cell content `c` with current font settings.
     * `variant` denotes the font variant to be used.
     */
    get(c, bold, italic) {
      let cp = 0;
      if (!bold && !italic && c.length === 1 && (cp = c.charCodeAt(0)) < 256 /* FLAT_SIZE */) {
        if (this._flat[cp] !== -9999 /* FLAT_UNSET */) {
          return this._flat[cp];
        }
        const width2 = this._measure(c, 0);
        if (width2 > 0) {
          this._flat[cp] = width2;
        }
        return width2;
      }
      let key = c;
      if (bold) key += "B";
      if (italic) key += "I";
      let width = this._holey.get(key);
      if (width === void 0) {
        let variant = 0;
        if (bold) variant |= 1 /* BOLD */;
        if (italic) variant |= 2 /* ITALIC */;
        width = this._measure(c, variant);
        if (width > 0) {
          this._holey.set(key, width);
        }
      }
      return width;
    }
    _measure(c, variant) {
      const el = this._measureElements[variant];
      el.textContent = c.repeat(32 /* REPEAT */);
      return el.offsetWidth / 32 /* REPEAT */;
    }
  };

  // node_modules/@xterm/xterm/src/browser/renderer/shared/SelectionRenderModel.ts
  var SelectionRenderModel = class {
    constructor() {
      this.clear();
    }
    clear() {
      this.hasSelection = false;
      this.columnSelectMode = false;
      this.viewportStartRow = 0;
      this.viewportEndRow = 0;
      this.viewportCappedStartRow = 0;
      this.viewportCappedEndRow = 0;
      this.startCol = 0;
      this.endCol = 0;
      this.selectionStart = void 0;
      this.selectionEnd = void 0;
    }
    update(terminal, start, end, columnSelectMode = false) {
      this.selectionStart = start;
      this.selectionEnd = end;
      if (!start || !end || start[0] === end[0] && start[1] === end[1]) {
        this.clear();
        return;
      }
      const viewportY = terminal.buffers.active.ydisp;
      const viewportStartRow = start[1] - viewportY;
      const viewportEndRow = end[1] - viewportY;
      const viewportCappedStartRow = Math.max(viewportStartRow, 0);
      const viewportCappedEndRow = Math.min(viewportEndRow, terminal.rows - 1);
      if (viewportCappedStartRow >= terminal.rows || viewportCappedEndRow < 0) {
        this.clear();
        return;
      }
      this.hasSelection = true;
      this.columnSelectMode = columnSelectMode;
      this.viewportStartRow = viewportStartRow;
      this.viewportEndRow = viewportEndRow;
      this.viewportCappedStartRow = viewportCappedStartRow;
      this.viewportCappedEndRow = viewportCappedEndRow;
      this.startCol = start[0];
      this.endCol = end[0];
    }
    isCellSelected(terminal, x, y) {
      if (!this.hasSelection) {
        return false;
      }
      y -= terminal.buffer.active.viewportY;
      if (this.columnSelectMode) {
        if (this.startCol <= this.endCol) {
          return x >= this.startCol && y >= this.viewportCappedStartRow && x < this.endCol && y <= this.viewportCappedEndRow;
        }
        return x < this.startCol && y >= this.viewportCappedStartRow && x >= this.endCol && y <= this.viewportCappedEndRow;
      }
      return y > this.viewportStartRow && y < this.viewportEndRow || this.viewportStartRow === this.viewportEndRow && y === this.viewportStartRow && x >= this.startCol && x < this.endCol || this.viewportStartRow < this.viewportEndRow && y === this.viewportEndRow && x < this.endCol || this.viewportStartRow < this.viewportEndRow && y === this.viewportStartRow && x >= this.startCol;
    }
  };
  function createSelectionRenderModel() {
    return new SelectionRenderModel();
  }

  // node_modules/@xterm/xterm/src/browser/renderer/dom/DomRenderer.ts
  var TERMINAL_CLASS_PREFIX = "xterm-dom-renderer-owner-";
  var ROW_CONTAINER_CLASS = "xterm-rows";
  var FG_CLASS_PREFIX = "xterm-fg-";
  var BG_CLASS_PREFIX = "xterm-bg-";
  var FOCUS_CLASS = "xterm-focus";
  var SELECTION_CLASS = "xterm-selection";
  var nextTerminalId = 1;
  var DomRenderer = class extends Disposable {
    constructor(_terminal, _document, _element, _screenElement, _viewportElement, _helperContainer, _linkifier2, instantiationService, _charSizeService, _optionsService, _bufferService, _coreService, _coreBrowserService, _themeService) {
      super();
      this._terminal = _terminal;
      this._document = _document;
      this._element = _element;
      this._screenElement = _screenElement;
      this._viewportElement = _viewportElement;
      this._helperContainer = _helperContainer;
      this._linkifier2 = _linkifier2;
      this._charSizeService = _charSizeService;
      this._optionsService = _optionsService;
      this._bufferService = _bufferService;
      this._coreService = _coreService;
      this._coreBrowserService = _coreBrowserService;
      this._themeService = _themeService;
      this._terminalClass = nextTerminalId++;
      this._rowElements = [];
      this._selectionRenderModel = createSelectionRenderModel();
      this.onRequestRedraw = this._register(new Emitter()).event;
      this._rowContainer = this._document.createElement("div");
      this._rowContainer.classList.add(ROW_CONTAINER_CLASS);
      this._rowContainer.style.lineHeight = "normal";
      this._rowContainer.setAttribute("aria-hidden", "true");
      this._refreshRowElements(this._bufferService.cols, this._bufferService.rows);
      this._selectionContainer = this._document.createElement("div");
      this._selectionContainer.classList.add(SELECTION_CLASS);
      this._selectionContainer.setAttribute("aria-hidden", "true");
      this.dimensions = createRenderDimensions();
      this._updateDimensions();
      this._register(this._optionsService.onOptionChange(() => this._handleOptionsChanged()));
      this._register(this._themeService.onChangeColors((e) => this._injectCss(e)));
      this._injectCss(this._themeService.colors);
      this._rowFactory = instantiationService.createInstance(DomRendererRowFactory, document);
      this._element.classList.add(TERMINAL_CLASS_PREFIX + this._terminalClass);
      this._screenElement.appendChild(this._rowContainer);
      this._screenElement.appendChild(this._selectionContainer);
      this._register(this._linkifier2.onShowLinkUnderline((e) => this._handleLinkHover(e)));
      this._register(this._linkifier2.onHideLinkUnderline((e) => this._handleLinkLeave(e)));
      this._register(toDisposable(() => {
        this._element.classList.remove(TERMINAL_CLASS_PREFIX + this._terminalClass);
        this._rowContainer.remove();
        this._selectionContainer.remove();
        this._widthCache.dispose();
        this._themeStyleElement.remove();
        this._dimensionsStyleElement.remove();
      }));
      this._widthCache = new WidthCache(this._document, this._helperContainer);
      this._widthCache.setFont(
        this._optionsService.rawOptions.fontFamily,
        this._optionsService.rawOptions.fontSize,
        this._optionsService.rawOptions.fontWeight,
        this._optionsService.rawOptions.fontWeightBold
      );
      this._setDefaultSpacing();
    }
    _updateDimensions() {
      const dpr = this._coreBrowserService.dpr;
      this.dimensions.device.char.width = this._charSizeService.width * dpr;
      this.dimensions.device.char.height = Math.ceil(this._charSizeService.height * dpr);
      this.dimensions.device.cell.width = this.dimensions.device.char.width + Math.round(this._optionsService.rawOptions.letterSpacing);
      this.dimensions.device.cell.height = Math.floor(this.dimensions.device.char.height * this._optionsService.rawOptions.lineHeight);
      this.dimensions.device.char.left = 0;
      this.dimensions.device.char.top = 0;
      this.dimensions.device.canvas.width = this.dimensions.device.cell.width * this._bufferService.cols;
      this.dimensions.device.canvas.height = this.dimensions.device.cell.height * this._bufferService.rows;
      this.dimensions.css.canvas.width = Math.round(this.dimensions.device.canvas.width / dpr);
      this.dimensions.css.canvas.height = Math.round(this.dimensions.device.canvas.height / dpr);
      this.dimensions.css.cell.width = this.dimensions.css.canvas.width / this._bufferService.cols;
      this.dimensions.css.cell.height = this.dimensions.css.canvas.height / this._bufferService.rows;
      for (const element of this._rowElements) {
        element.style.width = `${this.dimensions.css.canvas.width}px`;
        element.style.height = `${this.dimensions.css.cell.height}px`;
        element.style.lineHeight = `${this.dimensions.css.cell.height}px`;
        element.style.overflow = "hidden";
      }
      if (!this._dimensionsStyleElement) {
        this._dimensionsStyleElement = this._document.createElement("style");
        this._screenElement.appendChild(this._dimensionsStyleElement);
      }
      const styles = `${this._terminalSelector} .${ROW_CONTAINER_CLASS} span { display: inline-block; height: 100%; vertical-align: top;}`;
      this._dimensionsStyleElement.textContent = styles;
      this._selectionContainer.style.height = this._viewportElement.style.height;
      this._screenElement.style.width = `${this.dimensions.css.canvas.width}px`;
      this._screenElement.style.height = `${this.dimensions.css.canvas.height}px`;
    }
    _injectCss(colors) {
      if (!this._themeStyleElement) {
        this._themeStyleElement = this._document.createElement("style");
        this._screenElement.appendChild(this._themeStyleElement);
      }
      let styles = `${this._terminalSelector} .${ROW_CONTAINER_CLASS} { pointer-events: none; color: ${colors.foreground.css}; font-family: ${this._optionsService.rawOptions.fontFamily}; font-size: ${this._optionsService.rawOptions.fontSize}px; font-kerning: none; white-space: pre}`;
      styles += `${this._terminalSelector} .${ROW_CONTAINER_CLASS} .xterm-dim { color: ${color.multiplyOpacity(colors.foreground, 0.5).css};}`;
      styles += `${this._terminalSelector} span:not(.${"xterm-bold" /* BOLD_CLASS */}) { font-weight: ${this._optionsService.rawOptions.fontWeight};}${this._terminalSelector} span.${"xterm-bold" /* BOLD_CLASS */} { font-weight: ${this._optionsService.rawOptions.fontWeightBold};}${this._terminalSelector} span.${"xterm-italic" /* ITALIC_CLASS */} { font-style: italic;}`;
      const blinkAnimationUnderlineId = `blink_underline_${this._terminalClass}`;
      const blinkAnimationBarId = `blink_bar_${this._terminalClass}`;
      const blinkAnimationBlockId = `blink_block_${this._terminalClass}`;
      styles += `@keyframes ${blinkAnimationUnderlineId} { 50% {  border-bottom-style: hidden; }}`;
      styles += `@keyframes ${blinkAnimationBarId} { 50% {  box-shadow: none; }}`;
      styles += `@keyframes ${blinkAnimationBlockId} { 0% {  background-color: ${colors.cursor.css};  color: ${colors.cursorAccent.css}; } 50% {  background-color: inherit;  color: ${colors.cursor.css}; }}`;
      styles += `${this._terminalSelector} .${ROW_CONTAINER_CLASS}.${FOCUS_CLASS} .${"xterm-cursor" /* CURSOR_CLASS */}.${"xterm-cursor-blink" /* CURSOR_BLINK_CLASS */}.${"xterm-cursor-underline" /* CURSOR_STYLE_UNDERLINE_CLASS */} { animation: ${blinkAnimationUnderlineId} 1s step-end infinite;}${this._terminalSelector} .${ROW_CONTAINER_CLASS}.${FOCUS_CLASS} .${"xterm-cursor" /* CURSOR_CLASS */}.${"xterm-cursor-blink" /* CURSOR_BLINK_CLASS */}.${"xterm-cursor-bar" /* CURSOR_STYLE_BAR_CLASS */} { animation: ${blinkAnimationBarId} 1s step-end infinite;}${this._terminalSelector} .${ROW_CONTAINER_CLASS}.${FOCUS_CLASS} .${"xterm-cursor" /* CURSOR_CLASS */}.${"xterm-cursor-blink" /* CURSOR_BLINK_CLASS */}.${"xterm-cursor-block" /* CURSOR_STYLE_BLOCK_CLASS */} { animation: ${blinkAnimationBlockId} 1s step-end infinite;}${this._terminalSelector} .${ROW_CONTAINER_CLASS} .${"xterm-cursor" /* CURSOR_CLASS */}.${"xterm-cursor-block" /* CURSOR_STYLE_BLOCK_CLASS */} { background-color: ${colors.cursor.css}; color: ${colors.cursorAccent.css};}${this._terminalSelector} .${ROW_CONTAINER_CLASS} .${"xterm-cursor" /* CURSOR_CLASS */}.${"xterm-cursor-block" /* CURSOR_STYLE_BLOCK_CLASS */}:not(.${"xterm-cursor-blink" /* CURSOR_BLINK_CLASS */}) { background-color: ${colors.cursor.css} !important; color: ${colors.cursorAccent.css} !important;}${this._terminalSelector} .${ROW_CONTAINER_CLASS} .${"xterm-cursor" /* CURSOR_CLASS */}.${"xterm-cursor-outline" /* CURSOR_STYLE_OUTLINE_CLASS */} { outline: 1px solid ${colors.cursor.css}; outline-offset: -1px;}${this._terminalSelector} .${ROW_CONTAINER_CLASS} .${"xterm-cursor" /* CURSOR_CLASS */}.${"xterm-cursor-bar" /* CURSOR_STYLE_BAR_CLASS */} { box-shadow: ${this._optionsService.rawOptions.cursorWidth}px 0 0 ${colors.cursor.css} inset;}${this._terminalSelector} .${ROW_CONTAINER_CLASS} .${"xterm-cursor" /* CURSOR_CLASS */}.${"xterm-cursor-underline" /* CURSOR_STYLE_UNDERLINE_CLASS */} { border-bottom: 1px ${colors.cursor.css}; border-bottom-style: solid; height: calc(100% - 1px);}`;
      styles += `${this._terminalSelector} .${SELECTION_CLASS} { position: absolute; top: 0; left: 0; z-index: 1; pointer-events: none;}${this._terminalSelector}.focus .${SELECTION_CLASS} div { position: absolute; background-color: ${colors.selectionBackgroundOpaque.css};}${this._terminalSelector} .${SELECTION_CLASS} div { position: absolute; background-color: ${colors.selectionInactiveBackgroundOpaque.css};}`;
      for (const [i2, c] of colors.ansi.entries()) {
        styles += `${this._terminalSelector} .${FG_CLASS_PREFIX}${i2} { color: ${c.css}; }${this._terminalSelector} .${FG_CLASS_PREFIX}${i2}.${"xterm-dim" /* DIM_CLASS */} { color: ${color.multiplyOpacity(c, 0.5).css}; }${this._terminalSelector} .${BG_CLASS_PREFIX}${i2} { background-color: ${c.css}; }`;
      }
      styles += `${this._terminalSelector} .${FG_CLASS_PREFIX}${INVERTED_DEFAULT_COLOR} { color: ${color.opaque(colors.background).css}; }${this._terminalSelector} .${FG_CLASS_PREFIX}${INVERTED_DEFAULT_COLOR}.${"xterm-dim" /* DIM_CLASS */} { color: ${color.multiplyOpacity(color.opaque(colors.background), 0.5).css}; }${this._terminalSelector} .${BG_CLASS_PREFIX}${INVERTED_DEFAULT_COLOR} { background-color: ${colors.foreground.css}; }`;
      this._themeStyleElement.textContent = styles;
    }
    /**
     * default letter spacing
     * Due to rounding issues in dimensions dpr calc glyph might render
     * slightly too wide or too narrow. The method corrects the stacking offsets
     * by applying a default letter-spacing for all chars.
     * The value gets passed to the row factory to avoid setting this value again
     * (render speedup is roughly 10%).
     */
    _setDefaultSpacing() {
      const spacing = this.dimensions.css.cell.width - this._widthCache.get("W", false, false);
      this._rowContainer.style.letterSpacing = `${spacing}px`;
      this._rowFactory.defaultSpacing = spacing;
    }
    handleDevicePixelRatioChange() {
      this._updateDimensions();
      this._widthCache.clear();
      this._setDefaultSpacing();
    }
    _refreshRowElements(cols, rows) {
      for (let i2 = this._rowElements.length; i2 <= rows; i2++) {
        const row = this._document.createElement("div");
        this._rowContainer.appendChild(row);
        this._rowElements.push(row);
      }
      while (this._rowElements.length > rows) {
        this._rowContainer.removeChild(this._rowElements.pop());
      }
    }
    handleResize(cols, rows) {
      this._refreshRowElements(cols, rows);
      this._updateDimensions();
      this.handleSelectionChanged(this._selectionRenderModel.selectionStart, this._selectionRenderModel.selectionEnd, this._selectionRenderModel.columnSelectMode);
    }
    handleCharSizeChanged() {
      this._updateDimensions();
      this._widthCache.clear();
      this._setDefaultSpacing();
    }
    handleBlur() {
      this._rowContainer.classList.remove(FOCUS_CLASS);
      this.renderRows(0, this._bufferService.rows - 1);
    }
    handleFocus() {
      this._rowContainer.classList.add(FOCUS_CLASS);
      this.renderRows(this._bufferService.buffer.y, this._bufferService.buffer.y);
    }
    handleSelectionChanged(start, end, columnSelectMode) {
      this._selectionContainer.replaceChildren();
      this._rowFactory.handleSelectionChanged(start, end, columnSelectMode);
      this.renderRows(0, this._bufferService.rows - 1);
      if (!start || !end) {
        return;
      }
      this._selectionRenderModel.update(this._terminal, start, end, columnSelectMode);
      if (!this._selectionRenderModel.hasSelection) {
        return;
      }
      const viewportStartRow = this._selectionRenderModel.viewportStartRow;
      const viewportEndRow = this._selectionRenderModel.viewportEndRow;
      const viewportCappedStartRow = this._selectionRenderModel.viewportCappedStartRow;
      const viewportCappedEndRow = this._selectionRenderModel.viewportCappedEndRow;
      const documentFragment = this._document.createDocumentFragment();
      if (columnSelectMode) {
        const isXFlipped = start[0] > end[0];
        documentFragment.appendChild(
          this._createSelectionElement(viewportCappedStartRow, isXFlipped ? end[0] : start[0], isXFlipped ? start[0] : end[0], viewportCappedEndRow - viewportCappedStartRow + 1)
        );
      } else {
        const startCol = viewportStartRow === viewportCappedStartRow ? start[0] : 0;
        const endCol = viewportCappedStartRow === viewportEndRow ? end[0] : this._bufferService.cols;
        documentFragment.appendChild(this._createSelectionElement(viewportCappedStartRow, startCol, endCol));
        const middleRowsCount = viewportCappedEndRow - viewportCappedStartRow - 1;
        documentFragment.appendChild(this._createSelectionElement(viewportCappedStartRow + 1, 0, this._bufferService.cols, middleRowsCount));
        if (viewportCappedStartRow !== viewportCappedEndRow) {
          const endCol2 = viewportEndRow === viewportCappedEndRow ? end[0] : this._bufferService.cols;
          documentFragment.appendChild(this._createSelectionElement(viewportCappedEndRow, 0, endCol2));
        }
      }
      this._selectionContainer.appendChild(documentFragment);
    }
    /**
     * Creates a selection element at the specified position.
     * @param row The row of the selection.
     * @param colStart The start column.
     * @param colEnd The end columns.
     */
    _createSelectionElement(row, colStart, colEnd, rowCount = 1) {
      const element = this._document.createElement("div");
      const left = colStart * this.dimensions.css.cell.width;
      let width = this.dimensions.css.cell.width * (colEnd - colStart);
      if (left + width > this.dimensions.css.canvas.width) {
        width = this.dimensions.css.canvas.width - left;
      }
      element.style.height = `${rowCount * this.dimensions.css.cell.height}px`;
      element.style.top = `${row * this.dimensions.css.cell.height}px`;
      element.style.left = `${left}px`;
      element.style.width = `${width}px`;
      return element;
    }
    handleCursorMove() {
    }
    _handleOptionsChanged() {
      this._updateDimensions();
      this._injectCss(this._themeService.colors);
      this._widthCache.setFont(
        this._optionsService.rawOptions.fontFamily,
        this._optionsService.rawOptions.fontSize,
        this._optionsService.rawOptions.fontWeight,
        this._optionsService.rawOptions.fontWeightBold
      );
      this._setDefaultSpacing();
    }
    clear() {
      for (const e of this._rowElements) {
        e.replaceChildren();
      }
    }
    renderRows(start, end) {
      const buffer = this._bufferService.buffer;
      const cursorAbsoluteY = buffer.ybase + buffer.y;
      const cursorX = Math.min(buffer.x, this._bufferService.cols - 1);
      const cursorBlink = this._coreService.decPrivateModes.cursorBlink ?? this._optionsService.rawOptions.cursorBlink;
      const cursorStyle = this._coreService.decPrivateModes.cursorStyle ?? this._optionsService.rawOptions.cursorStyle;
      const cursorInactiveStyle = this._optionsService.rawOptions.cursorInactiveStyle;
      for (let y = start; y <= end; y++) {
        const row = y + buffer.ydisp;
        const rowElement = this._rowElements[y];
        const lineData = buffer.lines.get(row);
        if (!rowElement || !lineData) {
          break;
        }
        rowElement.replaceChildren(
          ...this._rowFactory.createRow(
            lineData,
            row,
            row === cursorAbsoluteY,
            cursorStyle,
            cursorInactiveStyle,
            cursorX,
            cursorBlink,
            this.dimensions.css.cell.width,
            this._widthCache,
            -1,
            -1
          )
        );
      }
    }
    get _terminalSelector() {
      return `.${TERMINAL_CLASS_PREFIX}${this._terminalClass}`;
    }
    _handleLinkHover(e) {
      this._setCellUnderline(e.x1, e.x2, e.y1, e.y2, e.cols, true);
    }
    _handleLinkLeave(e) {
      this._setCellUnderline(e.x1, e.x2, e.y1, e.y2, e.cols, false);
    }
    _setCellUnderline(x, x2, y, y2, cols, enabled) {
      if (y < 0) x = 0;
      if (y2 < 0) x2 = 0;
      const maxY = this._bufferService.rows - 1;
      y = Math.max(Math.min(y, maxY), 0);
      y2 = Math.max(Math.min(y2, maxY), 0);
      cols = Math.min(cols, this._bufferService.cols);
      const buffer = this._bufferService.buffer;
      const cursorAbsoluteY = buffer.ybase + buffer.y;
      const cursorX = Math.min(buffer.x, cols - 1);
      const cursorBlink = this._optionsService.rawOptions.cursorBlink;
      const cursorStyle = this._optionsService.rawOptions.cursorStyle;
      const cursorInactiveStyle = this._optionsService.rawOptions.cursorInactiveStyle;
      for (let i2 = y; i2 <= y2; ++i2) {
        const row = i2 + buffer.ydisp;
        const rowElement = this._rowElements[i2];
        const bufferline = buffer.lines.get(row);
        if (!rowElement || !bufferline) {
          break;
        }
        rowElement.replaceChildren(
          ...this._rowFactory.createRow(
            bufferline,
            row,
            row === cursorAbsoluteY,
            cursorStyle,
            cursorInactiveStyle,
            cursorX,
            cursorBlink,
            this.dimensions.css.cell.width,
            this._widthCache,
            enabled ? i2 === y ? x : 0 : -1,
            enabled ? (i2 === y2 ? x2 : cols) - 1 : -1
          )
        );
      }
    }
  };
  DomRenderer = __decorateClass([
    __decorateParam(7, IInstantiationService),
    __decorateParam(8, ICharSizeService),
    __decorateParam(9, IOptionsService),
    __decorateParam(10, IBufferService),
    __decorateParam(11, ICoreService),
    __decorateParam(12, ICoreBrowserService),
    __decorateParam(13, IThemeService)
  ], DomRenderer);

  // node_modules/@xterm/xterm/src/browser/services/CharSizeService.ts
  var CharSizeService = class extends Disposable {
    constructor(document2, parentElement, _optionsService) {
      super();
      this._optionsService = _optionsService;
      this.width = 0;
      this.height = 0;
      this._onCharSizeChange = this._register(new Emitter());
      this.onCharSizeChange = this._onCharSizeChange.event;
      try {
        this._measureStrategy = this._register(new TextMetricsMeasureStrategy(this._optionsService));
      } catch {
        this._measureStrategy = this._register(new DomMeasureStrategy(document2, parentElement, this._optionsService));
      }
      this._register(this._optionsService.onMultipleOptionChange(["fontFamily", "fontSize"], () => this.measure()));
    }
    get hasValidSize() {
      return this.width > 0 && this.height > 0;
    }
    measure() {
      const result = this._measureStrategy.measure();
      if (result.width !== this.width || result.height !== this.height) {
        this.width = result.width;
        this.height = result.height;
        this._onCharSizeChange.fire();
      }
    }
  };
  CharSizeService = __decorateClass([
    __decorateParam(2, IOptionsService)
  ], CharSizeService);
  var BaseMeasureStategy = class extends Disposable {
    constructor() {
      super(...arguments);
      this._result = { width: 0, height: 0 };
    }
    _validateAndSet(width, height) {
      if (width !== void 0 && width > 0 && height !== void 0 && height > 0) {
        this._result.width = width;
        this._result.height = height;
      }
    }
  };
  var DomMeasureStrategy = class extends BaseMeasureStategy {
    constructor(_document, _parentElement, _optionsService) {
      super();
      this._document = _document;
      this._parentElement = _parentElement;
      this._optionsService = _optionsService;
      this._measureElement = this._document.createElement("span");
      this._measureElement.classList.add("xterm-char-measure-element");
      this._measureElement.textContent = "W".repeat(32 /* REPEAT */);
      this._measureElement.setAttribute("aria-hidden", "true");
      this._measureElement.style.whiteSpace = "pre";
      this._measureElement.style.fontKerning = "none";
      this._parentElement.appendChild(this._measureElement);
    }
    measure() {
      this._measureElement.style.fontFamily = this._optionsService.rawOptions.fontFamily;
      this._measureElement.style.fontSize = `${this._optionsService.rawOptions.fontSize}px`;
      this._validateAndSet(Number(this._measureElement.offsetWidth) / 32 /* REPEAT */, Number(this._measureElement.offsetHeight));
      return this._result;
    }
  };
  var TextMetricsMeasureStrategy = class extends BaseMeasureStategy {
    constructor(_optionsService) {
      super();
      this._optionsService = _optionsService;
      this._canvas = new OffscreenCanvas(100, 100);
      this._ctx = this._canvas.getContext("2d");
      const a = this._ctx.measureText("W");
      if (!("width" in a && "fontBoundingBoxAscent" in a && "fontBoundingBoxDescent" in a)) {
        throw new Error("Required font metrics not supported");
      }
    }
    measure() {
      this._ctx.font = `${this._optionsService.rawOptions.fontSize}px ${this._optionsService.rawOptions.fontFamily}`;
      const metrics = this._ctx.measureText("W");
      this._validateAndSet(metrics.width, metrics.fontBoundingBoxAscent + metrics.fontBoundingBoxDescent);
      return this._result;
    }
  };

  // node_modules/@xterm/xterm/src/browser/services/CoreBrowserService.ts
  var CoreBrowserService = class extends Disposable {
    constructor(_textarea, _window, mainDocument) {
      super();
      this._textarea = _textarea;
      this._window = _window;
      this.mainDocument = mainDocument;
      this._isFocused = false;
      this._cachedIsFocused = void 0;
      this._screenDprMonitor = this._register(new ScreenDprMonitor(this._window));
      this._onDprChange = this._register(new Emitter());
      this.onDprChange = this._onDprChange.event;
      this._onWindowChange = this._register(new Emitter());
      this.onWindowChange = this._onWindowChange.event;
      this._register(this.onWindowChange((w) => this._screenDprMonitor.setWindow(w)));
      this._register(Event.forward(this._screenDprMonitor.onDprChange, this._onDprChange));
      this._register(addDisposableListener(this._textarea, "focus", () => this._isFocused = true));
      this._register(addDisposableListener(this._textarea, "blur", () => this._isFocused = false));
    }
    get window() {
      return this._window;
    }
    set window(value) {
      if (this._window !== value) {
        this._window = value;
        this._onWindowChange.fire(this._window);
      }
    }
    get dpr() {
      return this.window.devicePixelRatio;
    }
    get isFocused() {
      if (this._cachedIsFocused === void 0) {
        this._cachedIsFocused = this._isFocused && this._textarea.ownerDocument.hasFocus();
        queueMicrotask(() => this._cachedIsFocused = void 0);
      }
      return this._cachedIsFocused;
    }
  };
  var ScreenDprMonitor = class extends Disposable {
    constructor(_parentWindow) {
      super();
      this._parentWindow = _parentWindow;
      this._windowResizeListener = this._register(new MutableDisposable());
      this._onDprChange = this._register(new Emitter());
      this.onDprChange = this._onDprChange.event;
      this._outerListener = () => this._setDprAndFireIfDiffers();
      this._currentDevicePixelRatio = this._parentWindow.devicePixelRatio;
      this._updateDpr();
      this._setWindowResizeListener();
      this._register(toDisposable(() => this.clearListener()));
    }
    setWindow(parentWindow) {
      this._parentWindow = parentWindow;
      this._setWindowResizeListener();
      this._setDprAndFireIfDiffers();
    }
    _setWindowResizeListener() {
      this._windowResizeListener.value = addDisposableListener(this._parentWindow, "resize", () => this._setDprAndFireIfDiffers());
    }
    _setDprAndFireIfDiffers() {
      if (this._parentWindow.devicePixelRatio !== this._currentDevicePixelRatio) {
        this._onDprChange.fire(this._parentWindow.devicePixelRatio);
      }
      this._updateDpr();
    }
    _updateDpr() {
      if (!this._outerListener) {
        return;
      }
      this._resolutionMediaMatchList?.removeListener(this._outerListener);
      this._currentDevicePixelRatio = this._parentWindow.devicePixelRatio;
      this._resolutionMediaMatchList = this._parentWindow.matchMedia(`screen and (resolution: ${this._parentWindow.devicePixelRatio}dppx)`);
      this._resolutionMediaMatchList.addListener(this._outerListener);
    }
    clearListener() {
      if (!this._resolutionMediaMatchList || !this._outerListener) {
        return;
      }
      this._resolutionMediaMatchList.removeListener(this._outerListener);
      this._resolutionMediaMatchList = void 0;
      this._outerListener = void 0;
    }
  };

  // node_modules/@xterm/xterm/src/browser/services/LinkProviderService.ts
  var LinkProviderService = class extends Disposable {
    constructor() {
      super();
      this.linkProviders = [];
      this._register(toDisposable(() => this.linkProviders.length = 0));
    }
    registerLinkProvider(linkProvider) {
      this.linkProviders.push(linkProvider);
      return {
        dispose: () => {
          const providerIndex = this.linkProviders.indexOf(linkProvider);
          if (providerIndex !== -1) {
            this.linkProviders.splice(providerIndex, 1);
          }
        }
      };
    }
  };

  // node_modules/@xterm/xterm/src/browser/input/Mouse.ts
  function getCoordsRelativeToElement(window2, event, element) {
    const rect = element.getBoundingClientRect();
    const elementStyle = window2.getComputedStyle(element);
    const leftPadding = parseInt(elementStyle.getPropertyValue("padding-left"));
    const topPadding = parseInt(elementStyle.getPropertyValue("padding-top"));
    return [
      event.clientX - rect.left - leftPadding,
      event.clientY - rect.top - topPadding
    ];
  }
  function getCoords(window2, event, element, colCount, rowCount, hasValidCharSize, cssCellWidth, cssCellHeight, isSelection) {
    if (!hasValidCharSize) {
      return void 0;
    }
    const coords = getCoordsRelativeToElement(window2, event, element);
    if (!coords) {
      return void 0;
    }
    coords[0] = Math.ceil((coords[0] + (isSelection ? cssCellWidth / 2 : 0)) / cssCellWidth);
    coords[1] = Math.ceil(coords[1] / cssCellHeight);
    coords[0] = Math.min(Math.max(coords[0], 1), colCount + (isSelection ? 1 : 0));
    coords[1] = Math.min(Math.max(coords[1], 1), rowCount);
    return coords;
  }

  // node_modules/@xterm/xterm/src/browser/services/MouseService.ts
  var MouseService = class {
    constructor(_renderService, _charSizeService) {
      this._renderService = _renderService;
      this._charSizeService = _charSizeService;
    }
    getCoords(event, element, colCount, rowCount, isSelection) {
      return getCoords(
        window,
        event,
        element,
        colCount,
        rowCount,
        this._charSizeService.hasValidSize,
        this._renderService.dimensions.css.cell.width,
        this._renderService.dimensions.css.cell.height,
        isSelection
      );
    }
    getMouseReportCoords(event, element) {
      const coords = getCoordsRelativeToElement(window, event, element);
      if (!this._charSizeService.hasValidSize) {
        return void 0;
      }
      coords[0] = Math.min(Math.max(coords[0], 0), this._renderService.dimensions.css.canvas.width - 1);
      coords[1] = Math.min(Math.max(coords[1], 0), this._renderService.dimensions.css.canvas.height - 1);
      return {
        col: Math.floor(coords[0] / this._renderService.dimensions.css.cell.width),
        row: Math.floor(coords[1] / this._renderService.dimensions.css.cell.height),
        x: Math.floor(coords[0]),
        y: Math.floor(coords[1])
      };
    }
  };
  MouseService = __decorateClass([
    __decorateParam(0, IRenderService),
    __decorateParam(1, ICharSizeService)
  ], MouseService);

  // node_modules/@xterm/xterm/src/browser/RenderDebouncer.ts
  var RenderDebouncer = class {
    constructor(_renderCallback, _coreBrowserService) {
      this._renderCallback = _renderCallback;
      this._coreBrowserService = _coreBrowserService;
      this._refreshCallbacks = [];
    }
    dispose() {
      if (this._animationFrame) {
        this._coreBrowserService.window.cancelAnimationFrame(this._animationFrame);
        this._animationFrame = void 0;
      }
    }
    addRefreshCallback(callback) {
      this._refreshCallbacks.push(callback);
      if (!this._animationFrame) {
        this._animationFrame = this._coreBrowserService.window.requestAnimationFrame(() => this._innerRefresh());
      }
      return this._animationFrame;
    }
    refresh(rowStart, rowEnd, rowCount) {
      this._rowCount = rowCount;
      rowStart = rowStart !== void 0 ? rowStart : 0;
      rowEnd = rowEnd !== void 0 ? rowEnd : this._rowCount - 1;
      this._rowStart = this._rowStart !== void 0 ? Math.min(this._rowStart, rowStart) : rowStart;
      this._rowEnd = this._rowEnd !== void 0 ? Math.max(this._rowEnd, rowEnd) : rowEnd;
      if (this._animationFrame) {
        return;
      }
      this._animationFrame = this._coreBrowserService.window.requestAnimationFrame(() => this._innerRefresh());
    }
    _innerRefresh() {
      this._animationFrame = void 0;
      if (this._rowStart === void 0 || this._rowEnd === void 0 || this._rowCount === void 0) {
        this._runRefreshCallbacks();
        return;
      }
      const start = Math.max(this._rowStart, 0);
      const end = Math.min(this._rowEnd, this._rowCount - 1);
      this._rowStart = void 0;
      this._rowEnd = void 0;
      this._renderCallback(start, end);
      this._runRefreshCallbacks();
    }
    _runRefreshCallbacks() {
      for (const callback of this._refreshCallbacks) {
        callback(0);
      }
      this._refreshCallbacks = [];
    }
  };

  // node_modules/@xterm/xterm/src/common/Platform.ts
  var Platform_exports = {};
  __export(Platform_exports, {
    getSafariVersion: () => getSafariVersion,
    isChromeOS: () => isChromeOS,
    isFirefox: () => isFirefox3,
    isIpad: () => isIpad,
    isIphone: () => isIphone,
    isLegacyEdge: () => isLegacyEdge,
    isLinux: () => isLinux2,
    isMac: () => isMac,
    isNode: () => isNode,
    isSafari: () => isSafari3,
    isWindows: () => isWindows2
  });
  var isNode = typeof process !== "undefined" && "title" in process ? true : false;
  var userAgent3 = isNode ? "node" : navigator.userAgent;
  var platform = isNode ? "node" : navigator.platform;
  var isFirefox3 = userAgent3.includes("Firefox");
  var isLegacyEdge = userAgent3.includes("Edge");
  var isSafari3 = /^((?!chrome|android).)*safari/i.test(userAgent3);
  function getSafariVersion() {
    if (!isSafari3) {
      return 0;
    }
    const majorVersion = userAgent3.match(/Version\/(\d+)/);
    if (majorVersion === null || majorVersion.length < 2) {
      return 0;
    }
    return parseInt(majorVersion[1]);
  }
  var isMac = ["Macintosh", "MacIntel", "MacPPC", "Mac68K"].includes(platform);
  var isIpad = platform === "iPad";
  var isIphone = platform === "iPhone";
  var isWindows2 = ["Windows", "Win16", "Win32", "WinCE"].includes(platform);
  var isLinux2 = platform.indexOf("Linux") >= 0;
  var isChromeOS = /\bCrOS\b/.test(userAgent3);

  // node_modules/@xterm/xterm/src/common/TaskQueue.ts
  var TaskQueue = class {
    constructor() {
      this._tasks = [];
      this._i = 0;
    }
    enqueue(task) {
      this._tasks.push(task);
      this._start();
    }
    flush() {
      while (this._i < this._tasks.length) {
        if (!this._tasks[this._i]()) {
          this._i++;
        }
      }
      this.clear();
    }
    clear() {
      if (this._idleCallback) {
        this._cancelCallback(this._idleCallback);
        this._idleCallback = void 0;
      }
      this._i = 0;
      this._tasks.length = 0;
    }
    _start() {
      if (!this._idleCallback) {
        this._idleCallback = this._requestCallback(this._process.bind(this));
      }
    }
    _process(deadline) {
      this._idleCallback = void 0;
      let taskDuration = 0;
      let longestTask = 0;
      let lastDeadlineRemaining = deadline.timeRemaining();
      let deadlineRemaining = 0;
      while (this._i < this._tasks.length) {
        taskDuration = performance.now();
        if (!this._tasks[this._i]()) {
          this._i++;
        }
        taskDuration = Math.max(1, performance.now() - taskDuration);
        longestTask = Math.max(taskDuration, longestTask);
        deadlineRemaining = deadline.timeRemaining();
        if (longestTask * 1.5 > deadlineRemaining) {
          if (lastDeadlineRemaining - taskDuration < -20) {
            console.warn(`task queue exceeded allotted deadline by ${Math.abs(Math.round(lastDeadlineRemaining - taskDuration))}ms`);
          }
          this._start();
          return;
        }
        lastDeadlineRemaining = deadlineRemaining;
      }
      this.clear();
    }
  };
  var PriorityTaskQueue = class extends TaskQueue {
    _requestCallback(callback) {
      return setTimeout(() => callback(this._createDeadline(16)));
    }
    _cancelCallback(identifier) {
      clearTimeout(identifier);
    }
    _createDeadline(duration) {
      const end = performance.now() + duration;
      return {
        timeRemaining: () => Math.max(0, end - performance.now())
      };
    }
  };
  var IdleTaskQueueInternal = class extends TaskQueue {
    _requestCallback(callback) {
      return requestIdleCallback(callback);
    }
    _cancelCallback(identifier) {
      cancelIdleCallback(identifier);
    }
  };
  var IdleTaskQueue = !isNode && "requestIdleCallback" in window ? IdleTaskQueueInternal : PriorityTaskQueue;
  var DebouncedIdleTask = class {
    constructor() {
      this._queue = new IdleTaskQueue();
    }
    set(task) {
      this._queue.clear();
      this._queue.enqueue(task);
    }
    flush() {
      this._queue.flush();
    }
  };

  // node_modules/@xterm/xterm/src/browser/services/RenderService.ts
  var RenderService = class extends Disposable {
    constructor(_rowCount, screenElement, _optionsService, _charSizeService, _coreService, decorationService, bufferService, _coreBrowserService, themeService) {
      super();
      this._rowCount = _rowCount;
      this._optionsService = _optionsService;
      this._charSizeService = _charSizeService;
      this._coreService = _coreService;
      this._coreBrowserService = _coreBrowserService;
      this._renderer = this._register(new MutableDisposable());
      this._pausedResizeTask = new DebouncedIdleTask();
      this._observerDisposable = this._register(new MutableDisposable());
      this._isPaused = false;
      this._needsFullRefresh = false;
      this._isNextRenderRedrawOnly = true;
      this._needsSelectionRefresh = false;
      this._canvasWidth = 0;
      this._canvasHeight = 0;
      this._selectionState = {
        start: void 0,
        end: void 0,
        columnSelectMode: false
      };
      this._onDimensionsChange = this._register(new Emitter());
      this.onDimensionsChange = this._onDimensionsChange.event;
      this._onRenderedViewportChange = this._register(new Emitter());
      this.onRenderedViewportChange = this._onRenderedViewportChange.event;
      this._onRender = this._register(new Emitter());
      this.onRender = this._onRender.event;
      this._onRefreshRequest = this._register(new Emitter());
      this.onRefreshRequest = this._onRefreshRequest.event;
      this._renderDebouncer = new RenderDebouncer((start, end) => this._renderRows(start, end), this._coreBrowserService);
      this._register(this._renderDebouncer);
      this._syncOutputHandler = new SynchronizedOutputHandler(
        this._coreBrowserService,
        this._coreService,
        () => this._fullRefresh()
      );
      this._register(toDisposable(() => this._syncOutputHandler.dispose()));
      this._register(this._coreBrowserService.onDprChange(() => this.handleDevicePixelRatioChange()));
      this._register(bufferService.onResize(() => this._fullRefresh()));
      this._register(bufferService.buffers.onBufferActivate(() => this._renderer.value?.clear()));
      this._register(this._optionsService.onOptionChange(() => this._handleOptionsChanged()));
      this._register(this._charSizeService.onCharSizeChange(() => this.handleCharSizeChanged()));
      this._register(decorationService.onDecorationRegistered(() => this._fullRefresh()));
      this._register(decorationService.onDecorationRemoved(() => this._fullRefresh()));
      this._register(this._optionsService.onMultipleOptionChange([
        "customGlyphs",
        "drawBoldTextInBrightColors",
        "letterSpacing",
        "lineHeight",
        "fontFamily",
        "fontSize",
        "fontWeight",
        "fontWeightBold",
        "minimumContrastRatio",
        "rescaleOverlappingGlyphs"
      ], () => {
        this.clear();
        this.handleResize(bufferService.cols, bufferService.rows);
        this._fullRefresh();
      }));
      this._register(this._optionsService.onMultipleOptionChange([
        "cursorBlink",
        "cursorStyle"
      ], () => this.refreshRows(bufferService.buffer.y, bufferService.buffer.y, true)));
      this._register(themeService.onChangeColors(() => this._fullRefresh()));
      this._registerIntersectionObserver(this._coreBrowserService.window, screenElement);
      this._register(this._coreBrowserService.onWindowChange((w) => this._registerIntersectionObserver(w, screenElement)));
    }
    get dimensions() {
      return this._renderer.value.dimensions;
    }
    _registerIntersectionObserver(w, screenElement) {
      if ("IntersectionObserver" in w) {
        const observer = new w.IntersectionObserver((e) => this._handleIntersectionChange(e[e.length - 1]), { threshold: 0 });
        observer.observe(screenElement);
        this._observerDisposable.value = toDisposable(() => observer.disconnect());
      }
    }
    _handleIntersectionChange(entry) {
      this._isPaused = entry.isIntersecting === void 0 ? entry.intersectionRatio === 0 : !entry.isIntersecting;
      if (!this._isPaused && !this._charSizeService.hasValidSize) {
        this._charSizeService.measure();
      }
      if (!this._isPaused && this._needsFullRefresh) {
        this._pausedResizeTask.flush();
        this.refreshRows(0, this._rowCount - 1);
        this._needsFullRefresh = false;
      }
    }
    refreshRows(start, end, isRedrawOnly = false) {
      if (this._isPaused) {
        this._needsFullRefresh = true;
        return;
      }
      if (this._coreService.decPrivateModes.synchronizedOutput) {
        this._syncOutputHandler.bufferRows(start, end);
        return;
      }
      const buffered = this._syncOutputHandler.flush();
      if (buffered) {
        start = Math.min(start, buffered.start);
        end = Math.max(end, buffered.end);
      }
      if (!isRedrawOnly) {
        this._isNextRenderRedrawOnly = false;
      }
      this._renderDebouncer.refresh(start, end, this._rowCount);
    }
    _renderRows(start, end) {
      if (!this._renderer.value) {
        return;
      }
      if (this._coreService.decPrivateModes.synchronizedOutput) {
        this._syncOutputHandler.bufferRows(start, end);
        return;
      }
      start = Math.min(start, this._rowCount - 1);
      end = Math.min(end, this._rowCount - 1);
      this._renderer.value.renderRows(start, end);
      if (this._needsSelectionRefresh) {
        this._renderer.value.handleSelectionChanged(this._selectionState.start, this._selectionState.end, this._selectionState.columnSelectMode);
        this._needsSelectionRefresh = false;
      }
      if (!this._isNextRenderRedrawOnly) {
        this._onRenderedViewportChange.fire({ start, end });
      }
      this._onRender.fire({ start, end });
      this._isNextRenderRedrawOnly = true;
    }
    resize(cols, rows) {
      this._rowCount = rows;
      this._fireOnCanvasResize();
    }
    _handleOptionsChanged() {
      if (!this._renderer.value) {
        return;
      }
      this.refreshRows(0, this._rowCount - 1);
      this._fireOnCanvasResize();
    }
    _fireOnCanvasResize() {
      if (!this._renderer.value) {
        return;
      }
      if (this._renderer.value.dimensions.css.canvas.width === this._canvasWidth && this._renderer.value.dimensions.css.canvas.height === this._canvasHeight) {
        return;
      }
      this._onDimensionsChange.fire(this._renderer.value.dimensions);
    }
    hasRenderer() {
      return !!this._renderer.value;
    }
    setRenderer(renderer) {
      this._renderer.value = renderer;
      if (this._renderer.value) {
        this._renderer.value.onRequestRedraw((e) => this.refreshRows(e.start, e.end, true));
        this._needsSelectionRefresh = true;
        this._fullRefresh();
      }
    }
    addRefreshCallback(callback) {
      return this._renderDebouncer.addRefreshCallback(callback);
    }
    _fullRefresh() {
      if (this._isPaused) {
        this._needsFullRefresh = true;
      } else {
        this.refreshRows(0, this._rowCount - 1);
      }
    }
    clearTextureAtlas() {
      if (!this._renderer.value) {
        return;
      }
      this._renderer.value.clearTextureAtlas?.();
      this._fullRefresh();
    }
    handleDevicePixelRatioChange() {
      this._charSizeService.measure();
      if (!this._renderer.value) {
        return;
      }
      this._renderer.value.handleDevicePixelRatioChange();
      this.refreshRows(0, this._rowCount - 1);
    }
    handleResize(cols, rows) {
      if (!this._renderer.value) {
        return;
      }
      if (this._isPaused) {
        this._pausedResizeTask.set(() => this._renderer.value?.handleResize(cols, rows));
      } else {
        this._renderer.value.handleResize(cols, rows);
      }
      this._fullRefresh();
    }
    // TODO: Is this useful when we have onResize?
    handleCharSizeChanged() {
      this._renderer.value?.handleCharSizeChanged();
    }
    handleBlur() {
      this._renderer.value?.handleBlur();
    }
    handleFocus() {
      this._renderer.value?.handleFocus();
    }
    handleSelectionChanged(start, end, columnSelectMode) {
      this._selectionState.start = start;
      this._selectionState.end = end;
      this._selectionState.columnSelectMode = columnSelectMode;
      this._renderer.value?.handleSelectionChanged(start, end, columnSelectMode);
    }
    handleCursorMove() {
      this._renderer.value?.handleCursorMove();
    }
    clear() {
      this._renderer.value?.clear();
    }
  };
  RenderService = __decorateClass([
    __decorateParam(2, IOptionsService),
    __decorateParam(3, ICharSizeService),
    __decorateParam(4, ICoreService),
    __decorateParam(5, IDecorationService),
    __decorateParam(6, IBufferService),
    __decorateParam(7, ICoreBrowserService),
    __decorateParam(8, IThemeService)
  ], RenderService);
  var SynchronizedOutputHandler = class {
    constructor(_coreBrowserService, _coreService, _onTimeout) {
      this._coreBrowserService = _coreBrowserService;
      this._coreService = _coreService;
      this._onTimeout = _onTimeout;
      this._start = 0;
      this._end = 0;
      this._isBuffering = false;
    }
    bufferRows(start, end) {
      if (!this._isBuffering) {
        this._start = start;
        this._end = end;
        this._isBuffering = true;
      } else {
        this._start = Math.min(this._start, start);
        this._end = Math.max(this._end, end);
      }
      if (this._timeout === void 0) {
        this._timeout = this._coreBrowserService.window.setTimeout(() => {
          this._timeout = void 0;
          this._coreService.decPrivateModes.synchronizedOutput = false;
          this._onTimeout();
        }, 1e3 /* SYNCHRONIZED_OUTPUT_TIMEOUT_MS */);
      }
    }
    flush() {
      if (this._timeout !== void 0) {
        this._coreBrowserService.window.clearTimeout(this._timeout);
        this._timeout = void 0;
      }
      if (!this._isBuffering) {
        return void 0;
      }
      const result = { start: this._start, end: this._end };
      this._isBuffering = false;
      return result;
    }
    dispose() {
      if (this._timeout !== void 0) {
        this._coreBrowserService.window.clearTimeout(this._timeout);
        this._timeout = void 0;
      }
    }
  };

  // node_modules/@xterm/xterm/src/browser/input/MoveToCell.ts
  function moveToCellSequence(targetX, targetY, bufferService, applicationCursor) {
    const startX = bufferService.buffer.x;
    const startY = bufferService.buffer.y;
    if (!bufferService.buffer.hasScrollback) {
      return resetStartingRow(startX, startY, targetX, targetY, bufferService, applicationCursor) + moveToRequestedRow(startY, targetY, bufferService, applicationCursor) + moveToRequestedCol(startX, startY, targetX, targetY, bufferService, applicationCursor);
    }
    let direction;
    if (startY === targetY) {
      direction = startX > targetX ? "D" /* LEFT */ : "C" /* RIGHT */;
      return repeat(Math.abs(startX - targetX), sequence(direction, applicationCursor));
    }
    direction = startY > targetY ? "D" /* LEFT */ : "C" /* RIGHT */;
    const rowDifference = Math.abs(startY - targetY);
    const cellsToMove = colsFromRowEnd(startY > targetY ? targetX : startX, bufferService) + (rowDifference - 1) * bufferService.cols + 1 + colsFromRowBeginning(startY > targetY ? startX : targetX, bufferService);
    return repeat(cellsToMove, sequence(direction, applicationCursor));
  }
  function colsFromRowBeginning(currX, bufferService) {
    return currX - 1;
  }
  function colsFromRowEnd(currX, bufferService) {
    return bufferService.cols - currX;
  }
  function resetStartingRow(startX, startY, targetX, targetY, bufferService, applicationCursor) {
    if (moveToRequestedRow(startY, targetY, bufferService, applicationCursor).length === 0) {
      return "";
    }
    return repeat(bufferLine(
      startX,
      startY,
      startX,
      startY - wrappedRowsForRow(startY, bufferService),
      false,
      bufferService
    ).length, sequence("D" /* LEFT */, applicationCursor));
  }
  function moveToRequestedRow(startY, targetY, bufferService, applicationCursor) {
    const startRow = startY - wrappedRowsForRow(startY, bufferService);
    const endRow = targetY - wrappedRowsForRow(targetY, bufferService);
    const rowsToMove = Math.abs(startRow - endRow) - wrappedRowsCount(startY, targetY, bufferService);
    return repeat(rowsToMove, sequence(verticalDirection(startY, targetY), applicationCursor));
  }
  function moveToRequestedCol(startX, startY, targetX, targetY, bufferService, applicationCursor) {
    let startRow;
    if (moveToRequestedRow(startY, targetY, bufferService, applicationCursor).length > 0) {
      startRow = targetY - wrappedRowsForRow(targetY, bufferService);
    } else {
      startRow = startY;
    }
    const endRow = targetY;
    const direction = horizontalDirection(startX, startY, targetX, targetY, bufferService, applicationCursor);
    return repeat(bufferLine(
      startX,
      startRow,
      targetX,
      endRow,
      direction === "C" /* RIGHT */,
      bufferService
    ).length, sequence(direction, applicationCursor));
  }
  function wrappedRowsCount(startY, targetY, bufferService) {
    let wrappedRows = 0;
    const startRow = startY - wrappedRowsForRow(startY, bufferService);
    const endRow = targetY - wrappedRowsForRow(targetY, bufferService);
    for (let i2 = 0; i2 < Math.abs(startRow - endRow); i2++) {
      const direction = verticalDirection(startY, targetY) === "A" /* UP */ ? -1 : 1;
      const line = bufferService.buffer.lines.get(startRow + direction * i2);
      if (line?.isWrapped) {
        wrappedRows++;
      }
    }
    return wrappedRows;
  }
  function wrappedRowsForRow(currentRow, bufferService) {
    let rowCount = 0;
    let line = bufferService.buffer.lines.get(currentRow);
    let lineWraps = line?.isWrapped;
    while (lineWraps && currentRow >= 0 && currentRow < bufferService.rows) {
      rowCount++;
      line = bufferService.buffer.lines.get(--currentRow);
      lineWraps = line?.isWrapped;
    }
    return rowCount;
  }
  function horizontalDirection(startX, startY, targetX, targetY, bufferService, applicationCursor) {
    let startRow;
    if (moveToRequestedRow(targetX, targetY, bufferService, applicationCursor).length > 0) {
      startRow = targetY - wrappedRowsForRow(targetY, bufferService);
    } else {
      startRow = startY;
    }
    if (startX < targetX && startRow <= targetY || // down/right or same y/right
    startX >= targetX && startRow < targetY) {
      return "C" /* RIGHT */;
    }
    return "D" /* LEFT */;
  }
  function verticalDirection(startY, targetY) {
    return startY > targetY ? "A" /* UP */ : "B" /* DOWN */;
  }
  function bufferLine(startCol, startRow, endCol, endRow, forward, bufferService) {
    let currentCol = startCol;
    let currentRow = startRow;
    let bufferStr = "";
    while ((currentCol !== endCol || currentRow !== endRow) && currentRow >= 0 && currentRow < bufferService.buffer.lines.length) {
      currentCol += forward ? 1 : -1;
      if (forward && currentCol > bufferService.cols - 1) {
        bufferStr += bufferService.buffer.translateBufferLineToString(
          currentRow,
          false,
          startCol,
          currentCol
        );
        currentCol = 0;
        startCol = 0;
        currentRow++;
      } else if (!forward && currentCol < 0) {
        bufferStr += bufferService.buffer.translateBufferLineToString(
          currentRow,
          false,
          0,
          startCol + 1
        );
        currentCol = bufferService.cols - 1;
        startCol = currentCol;
        currentRow--;
      }
    }
    return bufferStr + bufferService.buffer.translateBufferLineToString(
      currentRow,
      false,
      startCol,
      currentCol
    );
  }
  function sequence(direction, applicationCursor) {
    const mod = applicationCursor ? "O" : "[";
    return C0.ESC + mod + direction;
  }
  function repeat(count, str) {
    count = Math.floor(count);
    let rpt = "";
    for (let i2 = 0; i2 < count; i2++) {
      rpt += str;
    }
    return rpt;
  }

  // node_modules/@xterm/xterm/src/browser/selection/SelectionModel.ts
  var SelectionModel = class {
    constructor(_bufferService) {
      this._bufferService = _bufferService;
      /**
       * Whether select all is currently active.
       */
      this.isSelectAllActive = false;
      /**
       * The minimal length of the selection from the start position. When double
       * clicking on a word, the word will be selected which makes the selection
       * start at the start of the word and makes this variable the length.
       */
      this.selectionStartLength = 0;
    }
    /**
     * Clears the current selection.
     */
    clearSelection() {
      this.selectionStart = void 0;
      this.selectionEnd = void 0;
      this.isSelectAllActive = false;
      this.selectionStartLength = 0;
    }
    /**
     * The final selection start, taking into consideration select all.
     */
    get finalSelectionStart() {
      if (this.isSelectAllActive) {
        return [0, 0];
      }
      if (!this.selectionEnd || !this.selectionStart) {
        return this.selectionStart;
      }
      return this.areSelectionValuesReversed() ? this.selectionEnd : this.selectionStart;
    }
    /**
     * The final selection end, taking into consideration select all, double click
     * word selection and triple click line selection.
     */
    get finalSelectionEnd() {
      if (this.isSelectAllActive) {
        return [this._bufferService.cols, this._bufferService.buffer.ybase + this._bufferService.rows - 1];
      }
      if (!this.selectionStart) {
        return void 0;
      }
      if (!this.selectionEnd || this.areSelectionValuesReversed()) {
        const startPlusLength = this.selectionStart[0] + this.selectionStartLength;
        if (startPlusLength > this._bufferService.cols) {
          if (startPlusLength % this._bufferService.cols === 0) {
            return [this._bufferService.cols, this.selectionStart[1] + Math.floor(startPlusLength / this._bufferService.cols) - 1];
          }
          return [startPlusLength % this._bufferService.cols, this.selectionStart[1] + Math.floor(startPlusLength / this._bufferService.cols)];
        }
        return [startPlusLength, this.selectionStart[1]];
      }
      if (this.selectionStartLength) {
        if (this.selectionEnd[1] === this.selectionStart[1]) {
          const startPlusLength = this.selectionStart[0] + this.selectionStartLength;
          if (startPlusLength > this._bufferService.cols) {
            return [startPlusLength % this._bufferService.cols, this.selectionStart[1] + Math.floor(startPlusLength / this._bufferService.cols)];
          }
          return [Math.max(startPlusLength, this.selectionEnd[0]), this.selectionEnd[1]];
        }
      }
      return this.selectionEnd;
    }
    /**
     * Returns whether the selection start and end are reversed.
     */
    areSelectionValuesReversed() {
      const start = this.selectionStart;
      const end = this.selectionEnd;
      if (!start || !end) {
        return false;
      }
      return start[1] > end[1] || start[1] === end[1] && start[0] > end[0];
    }
    /**
     * Handle the buffer being trimmed, adjust the selection position.
     * @param amount The amount the buffer is being trimmed.
     * @returns Whether a refresh is necessary.
     */
    handleTrim(amount) {
      if (this.selectionStart) {
        this.selectionStart[1] -= amount;
      }
      if (this.selectionEnd) {
        this.selectionEnd[1] -= amount;
      }
      if (this.selectionEnd && this.selectionEnd[1] < 0) {
        this.clearSelection();
        return true;
      }
      if (this.selectionStart && this.selectionStart[1] < 0) {
        this.selectionStart[1] = 0;
      }
      return false;
    }
  };

  // node_modules/@xterm/xterm/src/common/buffer/BufferRange.ts
  function getRangeLength(range, bufferCols) {
    if (range.start.y > range.end.y) {
      throw new Error(`Buffer range end (${range.end.x}, ${range.end.y}) cannot be before start (${range.start.x}, ${range.start.y})`);
    }
    return bufferCols * (range.end.y - range.start.y) + (range.end.x - range.start.x + 1);
  }

  // node_modules/@xterm/xterm/src/browser/services/SelectionService.ts
  var DRAG_SCROLL_MAX_THRESHOLD = 50;
  var DRAG_SCROLL_MAX_SPEED = 15;
  var DRAG_SCROLL_INTERVAL = 50;
  var ALT_CLICK_MOVE_CURSOR_TIME = 500;
  var NON_BREAKING_SPACE_CHAR = String.fromCharCode(160);
  var ALL_NON_BREAKING_SPACE_REGEX = new RegExp(NON_BREAKING_SPACE_CHAR, "g");
  var SelectionService = class extends Disposable {
    constructor(_element, _screenElement, _linkifier, _bufferService, _coreService, _mouseService, _optionsService, _renderService, _coreBrowserService) {
      super();
      this._element = _element;
      this._screenElement = _screenElement;
      this._linkifier = _linkifier;
      this._bufferService = _bufferService;
      this._coreService = _coreService;
      this._mouseService = _mouseService;
      this._optionsService = _optionsService;
      this._renderService = _renderService;
      this._coreBrowserService = _coreBrowserService;
      /**
       * The amount to scroll every drag scroll update (depends on how far the mouse
       * drag is above or below the terminal).
       */
      this._dragScrollAmount = 0;
      /**
       * Whether selection is enabled.
       */
      this._enabled = true;
      this._workCell = new CellData();
      this._mouseDownTimeStamp = 0;
      this._oldHasSelection = false;
      this._oldSelectionStart = void 0;
      this._oldSelectionEnd = void 0;
      this._onLinuxMouseSelection = this._register(new Emitter());
      this.onLinuxMouseSelection = this._onLinuxMouseSelection.event;
      this._onRedrawRequest = this._register(new Emitter());
      this.onRequestRedraw = this._onRedrawRequest.event;
      this._onSelectionChange = this._register(new Emitter());
      this.onSelectionChange = this._onSelectionChange.event;
      this._onRequestScrollLines = this._register(new Emitter());
      this.onRequestScrollLines = this._onRequestScrollLines.event;
      this._mouseMoveListener = (event) => this._handleMouseMove(event);
      this._mouseUpListener = (event) => this._handleMouseUp(event);
      this._coreService.onUserInput(() => {
        if (this.hasSelection) {
          this.clearSelection();
        }
      });
      this._trimListener = this._bufferService.buffer.lines.onTrim((amount) => this._handleTrim(amount));
      this._register(this._bufferService.buffers.onBufferActivate((e) => this._handleBufferActivate(e)));
      this.enable();
      this._model = new SelectionModel(this._bufferService);
      this._activeSelectionMode = 0 /* NORMAL */;
      this._register(toDisposable(() => {
        this._removeMouseDownListeners();
      }));
      this._register(this._bufferService.onResize((e) => {
        if (e.rowsChanged) {
          this.clearSelection();
        }
      }));
    }
    reset() {
      this.clearSelection();
    }
    /**
     * Disables the selection manager. This is useful for when terminal mouse
     * are enabled.
     */
    disable() {
      this.clearSelection();
      this._enabled = false;
    }
    /**
     * Enable the selection manager.
     */
    enable() {
      this._enabled = true;
    }
    get selectionStart() {
      return this._model.finalSelectionStart;
    }
    get selectionEnd() {
      return this._model.finalSelectionEnd;
    }
    /**
     * Gets whether there is an active text selection.
     */
    get hasSelection() {
      const start = this._model.finalSelectionStart;
      const end = this._model.finalSelectionEnd;
      if (!start || !end) {
        return false;
      }
      return start[0] !== end[0] || start[1] !== end[1];
    }
    /**
     * Gets the text currently selected.
     */
    get selectionText() {
      const start = this._model.finalSelectionStart;
      const end = this._model.finalSelectionEnd;
      if (!start || !end) {
        return "";
      }
      const buffer = this._bufferService.buffer;
      const result = [];
      if (this._activeSelectionMode === 3 /* COLUMN */) {
        if (start[0] === end[0]) {
          return "";
        }
        const startCol = start[0] < end[0] ? start[0] : end[0];
        const endCol = start[0] < end[0] ? end[0] : start[0];
        for (let i2 = start[1]; i2 <= end[1]; i2++) {
          const lineText = buffer.translateBufferLineToString(i2, true, startCol, endCol);
          result.push(lineText);
        }
      } else {
        const startRowEndCol = start[1] === end[1] ? end[0] : void 0;
        result.push(buffer.translateBufferLineToString(start[1], true, start[0], startRowEndCol));
        for (let i2 = start[1] + 1; i2 <= end[1] - 1; i2++) {
          const bufferLine2 = buffer.lines.get(i2);
          const lineText = buffer.translateBufferLineToString(i2, true);
          if (bufferLine2?.isWrapped) {
            result[result.length - 1] += lineText;
          } else {
            result.push(lineText);
          }
        }
        if (start[1] !== end[1]) {
          const bufferLine2 = buffer.lines.get(end[1]);
          const lineText = buffer.translateBufferLineToString(end[1], true, 0, end[0]);
          if (bufferLine2 && bufferLine2.isWrapped) {
            result[result.length - 1] += lineText;
          } else {
            result.push(lineText);
          }
        }
      }
      const formattedResult = result.map((line) => {
        return line.replace(ALL_NON_BREAKING_SPACE_REGEX, " ");
      }).join(isWindows2 ? "\r\n" : "\n");
      return formattedResult;
    }
    /**
     * Clears the current terminal selection.
     */
    clearSelection() {
      this._model.clearSelection();
      this._removeMouseDownListeners();
      this.refresh();
      this._onSelectionChange.fire();
    }
    /**
     * Queues a refresh, redrawing the selection on the next opportunity.
     * @param isLinuxMouseSelection Whether the selection should be registered as a new
     * selection on Linux.
     */
    refresh(isLinuxMouseSelection) {
      if (!this._refreshAnimationFrame) {
        this._refreshAnimationFrame = this._coreBrowserService.window.requestAnimationFrame(() => this._refresh());
      }
      if (isLinux2 && isLinuxMouseSelection) {
        const selectionText = this.selectionText;
        if (selectionText.length) {
          this._onLinuxMouseSelection.fire(this.selectionText);
        }
      }
    }
    /**
     * Fires the refresh event, causing consumers to pick it up and redraw the
     * selection state.
     */
    _refresh() {
      this._refreshAnimationFrame = void 0;
      this._onRedrawRequest.fire({
        start: this._model.finalSelectionStart,
        end: this._model.finalSelectionEnd,
        columnSelectMode: this._activeSelectionMode === 3 /* COLUMN */
      });
    }
    /**
     * Checks if the current click was inside the current selection
     * @param event The mouse event
     */
    _isClickInSelection(event) {
      const coords = this._getMouseBufferCoords(event);
      const start = this._model.finalSelectionStart;
      const end = this._model.finalSelectionEnd;
      if (!start || !end || !coords) {
        return false;
      }
      return this._areCoordsInSelection(coords, start, end);
    }
    isCellInSelection(x, y) {
      const start = this._model.finalSelectionStart;
      const end = this._model.finalSelectionEnd;
      if (!start || !end) {
        return false;
      }
      return this._areCoordsInSelection([x, y], start, end);
    }
    _areCoordsInSelection(coords, start, end) {
      return coords[1] > start[1] && coords[1] < end[1] || start[1] === end[1] && coords[1] === start[1] && coords[0] >= start[0] && coords[0] < end[0] || start[1] < end[1] && coords[1] === end[1] && coords[0] < end[0] || start[1] < end[1] && coords[1] === start[1] && coords[0] >= start[0];
    }
    /**
     * Selects word at the current mouse event coordinates.
     * @param event The mouse event.
     */
    _selectWordAtCursor(event, allowWhitespaceOnlySelection) {
      const range = this._linkifier.currentLink?.link?.range;
      if (range) {
        this._model.selectionStart = [range.start.x - 1, range.start.y - 1];
        this._model.selectionStartLength = getRangeLength(range, this._bufferService.cols);
        this._model.selectionEnd = void 0;
        return true;
      }
      const coords = this._getMouseBufferCoords(event);
      if (coords) {
        this._selectWordAt(coords, allowWhitespaceOnlySelection);
        this._model.selectionEnd = void 0;
        return true;
      }
      return false;
    }
    /**
     * Selects all text within the terminal.
     */
    selectAll() {
      this._model.isSelectAllActive = true;
      this.refresh();
      this._onSelectionChange.fire();
    }
    selectLines(start, end) {
      this._model.clearSelection();
      start = Math.max(start, 0);
      end = Math.min(end, this._bufferService.buffer.lines.length - 1);
      this._model.selectionStart = [0, start];
      this._model.selectionEnd = [this._bufferService.cols, end];
      this.refresh();
      this._onSelectionChange.fire();
    }
    /**
     * Handle the buffer being trimmed, adjust the selection position.
     * @param amount The amount the buffer is being trimmed.
     */
    _handleTrim(amount) {
      const needsRefresh = this._model.handleTrim(amount);
      if (needsRefresh) {
        this.refresh();
      }
    }
    /**
     * Gets the 0-based [x, y] buffer coordinates of the current mouse event.
     * @param event The mouse event.
     */
    _getMouseBufferCoords(event) {
      const coords = this._mouseService.getCoords(event, this._screenElement, this._bufferService.cols, this._bufferService.rows, true);
      if (!coords) {
        return void 0;
      }
      coords[0]--;
      coords[1]--;
      coords[1] += this._bufferService.buffer.ydisp;
      return coords;
    }
    /**
     * Gets the amount the viewport should be scrolled based on how far out of the
     * terminal the mouse is.
     * @param event The mouse event.
     */
    _getMouseEventScrollAmount(event) {
      let offset = getCoordsRelativeToElement(this._coreBrowserService.window, event, this._screenElement)[1];
      const terminalHeight = this._renderService.dimensions.css.canvas.height;
      if (offset >= 0 && offset <= terminalHeight) {
        return 0;
      }
      if (offset > terminalHeight) {
        offset -= terminalHeight;
      }
      offset = Math.min(Math.max(offset, -DRAG_SCROLL_MAX_THRESHOLD), DRAG_SCROLL_MAX_THRESHOLD);
      offset /= DRAG_SCROLL_MAX_THRESHOLD;
      return offset / Math.abs(offset) + Math.round(offset * (DRAG_SCROLL_MAX_SPEED - 1));
    }
    /**
     * Returns whether the selection manager should force selection, regardless of
     * whether the terminal is in mouse events mode.
     * @param event The mouse event.
     */
    shouldForceSelection(event) {
      if (isMac) {
        return event.altKey && this._optionsService.rawOptions.macOptionClickForcesSelection;
      }
      return event.shiftKey;
    }
    /**
     * Handles te mousedown event, setting up for a new selection.
     * @param event The mousedown event.
     */
    handleMouseDown(event) {
      this._mouseDownTimeStamp = event.timeStamp;
      if (event.button === 2 && this.hasSelection) {
        return;
      }
      if (event.button !== 0) {
        return;
      }
      if (!this._enabled) {
        if (!this.shouldForceSelection(event)) {
          return;
        }
        event.stopPropagation();
      }
      event.preventDefault();
      this._dragScrollAmount = 0;
      if (this._enabled && event.shiftKey) {
        this._handleIncrementalClick(event);
      } else {
        if (event.detail === 1) {
          this._handleSingleClick(event);
        } else if (event.detail === 2) {
          this._handleDoubleClick(event);
        } else if (event.detail === 3) {
          this._handleTripleClick(event);
        }
      }
      this._addMouseDownListeners();
      this.refresh(true);
    }
    /**
     * Adds listeners when mousedown is triggered.
     */
    _addMouseDownListeners() {
      if (this._screenElement.ownerDocument) {
        this._screenElement.ownerDocument.addEventListener("mousemove", this._mouseMoveListener);
        this._screenElement.ownerDocument.addEventListener("mouseup", this._mouseUpListener);
      }
      this._dragScrollIntervalTimer = this._coreBrowserService.window.setInterval(() => this._dragScroll(), DRAG_SCROLL_INTERVAL);
    }
    /**
     * Removes the listeners that are registered when mousedown is triggered.
     */
    _removeMouseDownListeners() {
      if (this._screenElement.ownerDocument) {
        this._screenElement.ownerDocument.removeEventListener("mousemove", this._mouseMoveListener);
        this._screenElement.ownerDocument.removeEventListener("mouseup", this._mouseUpListener);
      }
      this._coreBrowserService.window.clearInterval(this._dragScrollIntervalTimer);
      this._dragScrollIntervalTimer = void 0;
    }
    /**
     * Performs an incremental click, setting the selection end position to the mouse
     * position.
     * @param event The mouse event.
     */
    _handleIncrementalClick(event) {
      if (this._model.selectionStart) {
        this._model.selectionEnd = this._getMouseBufferCoords(event);
      }
    }
    /**
     * Performs a single click, resetting relevant state and setting the selection
     * start position.
     * @param event The mouse event.
     */
    _handleSingleClick(event) {
      this._model.selectionStartLength = 0;
      this._model.isSelectAllActive = false;
      this._activeSelectionMode = this.shouldColumnSelect(event) ? 3 /* COLUMN */ : 0 /* NORMAL */;
      this._model.selectionStart = this._getMouseBufferCoords(event);
      if (!this._model.selectionStart) {
        return;
      }
      this._model.selectionEnd = void 0;
      const line = this._bufferService.buffer.lines.get(this._model.selectionStart[1]);
      if (!line) {
        return;
      }
      if (line.length === this._model.selectionStart[0]) {
        return;
      }
      if (line.hasWidth(this._model.selectionStart[0]) === 0) {
        this._model.selectionStart[0]++;
      }
    }
    /**
     * Performs a double click, selecting the current word.
     * @param event The mouse event.
     */
    _handleDoubleClick(event) {
      if (this._selectWordAtCursor(event, true)) {
        this._activeSelectionMode = 1 /* WORD */;
      }
    }
    /**
     * Performs a triple click, selecting the current line and activating line
     * select mode.
     * @param event The mouse event.
     */
    _handleTripleClick(event) {
      const coords = this._getMouseBufferCoords(event);
      if (coords) {
        this._activeSelectionMode = 2 /* LINE */;
        this._selectLineAt(coords[1]);
      }
    }
    /**
     * Returns whether the selection manager should operate in column select mode
     * @param event the mouse or keyboard event
     */
    shouldColumnSelect(event) {
      return event.altKey && !(isMac && this._optionsService.rawOptions.macOptionClickForcesSelection);
    }
    /**
     * Handles the mousemove event when the mouse button is down, recording the
     * end of the selection and refreshing the selection.
     * @param event The mousemove event.
     */
    _handleMouseMove(event) {
      event.stopImmediatePropagation();
      if (!this._model.selectionStart) {
        return;
      }
      const previousSelectionEnd = this._model.selectionEnd ? [this._model.selectionEnd[0], this._model.selectionEnd[1]] : null;
      this._model.selectionEnd = this._getMouseBufferCoords(event);
      if (!this._model.selectionEnd) {
        this.refresh(true);
        return;
      }
      if (this._activeSelectionMode === 2 /* LINE */) {
        if (this._model.selectionEnd[1] < this._model.selectionStart[1]) {
          this._model.selectionEnd[0] = 0;
        } else {
          this._model.selectionEnd[0] = this._bufferService.cols;
        }
      } else if (this._activeSelectionMode === 1 /* WORD */) {
        this._selectToWordAt(this._model.selectionEnd);
      }
      this._dragScrollAmount = this._getMouseEventScrollAmount(event);
      if (this._activeSelectionMode !== 3 /* COLUMN */) {
        if (this._dragScrollAmount > 0) {
          this._model.selectionEnd[0] = this._bufferService.cols;
        } else if (this._dragScrollAmount < 0) {
          this._model.selectionEnd[0] = 0;
        }
      }
      const buffer = this._bufferService.buffer;
      if (this._model.selectionEnd[1] < buffer.lines.length) {
        const line = buffer.lines.get(this._model.selectionEnd[1]);
        if (line && line.hasWidth(this._model.selectionEnd[0]) === 0) {
          if (this._model.selectionEnd[0] < this._bufferService.cols) {
            this._model.selectionEnd[0]++;
          }
        }
      }
      if (!previousSelectionEnd || previousSelectionEnd[0] !== this._model.selectionEnd[0] || previousSelectionEnd[1] !== this._model.selectionEnd[1]) {
        this.refresh(true);
      }
    }
    /**
     * The callback that occurs every DRAG_SCROLL_INTERVAL ms that does the
     * scrolling of the viewport.
     */
    _dragScroll() {
      if (!this._model.selectionEnd || !this._model.selectionStart) {
        return;
      }
      if (this._dragScrollAmount) {
        this._onRequestScrollLines.fire({ amount: this._dragScrollAmount, suppressScrollEvent: false });
        const buffer = this._bufferService.buffer;
        if (this._dragScrollAmount > 0) {
          if (this._activeSelectionMode !== 3 /* COLUMN */) {
            this._model.selectionEnd[0] = this._bufferService.cols;
          }
          this._model.selectionEnd[1] = Math.min(buffer.ydisp + this._bufferService.rows, buffer.lines.length - 1);
        } else {
          if (this._activeSelectionMode !== 3 /* COLUMN */) {
            this._model.selectionEnd[0] = 0;
          }
          this._model.selectionEnd[1] = buffer.ydisp;
        }
        this.refresh();
      }
    }
    /**
     * Handles the mouseup event, removing the mousedown listeners.
     * @param event The mouseup event.
     */
    _handleMouseUp(event) {
      const timeElapsed = event.timeStamp - this._mouseDownTimeStamp;
      this._removeMouseDownListeners();
      if (this.selectionText.length <= 1 && timeElapsed < ALT_CLICK_MOVE_CURSOR_TIME && event.altKey && this._optionsService.rawOptions.altClickMovesCursor) {
        if (this._bufferService.buffer.ybase === this._bufferService.buffer.ydisp) {
          const coordinates = this._mouseService.getCoords(
            event,
            this._element,
            this._bufferService.cols,
            this._bufferService.rows,
            false
          );
          if (coordinates && coordinates[0] !== void 0 && coordinates[1] !== void 0) {
            const sequence2 = moveToCellSequence(coordinates[0] - 1, coordinates[1] - 1, this._bufferService, this._coreService.decPrivateModes.applicationCursorKeys);
            this._coreService.triggerDataEvent(sequence2, true);
          }
        }
      } else {
        this._fireEventIfSelectionChanged();
      }
    }
    _fireEventIfSelectionChanged() {
      const start = this._model.finalSelectionStart;
      const end = this._model.finalSelectionEnd;
      const hasSelection = !!start && !!end && (start[0] !== end[0] || start[1] !== end[1]);
      if (!hasSelection) {
        if (this._oldHasSelection) {
          this._fireOnSelectionChange(start, end, hasSelection);
        }
        return;
      }
      if (!start || !end) {
        return;
      }
      if (!this._oldSelectionStart || !this._oldSelectionEnd || (start[0] !== this._oldSelectionStart[0] || start[1] !== this._oldSelectionStart[1] || end[0] !== this._oldSelectionEnd[0] || end[1] !== this._oldSelectionEnd[1])) {
        this._fireOnSelectionChange(start, end, hasSelection);
      }
    }
    _fireOnSelectionChange(start, end, hasSelection) {
      this._oldSelectionStart = start;
      this._oldSelectionEnd = end;
      this._oldHasSelection = hasSelection;
      this._onSelectionChange.fire();
    }
    _handleBufferActivate(e) {
      this.clearSelection();
      this._trimListener.dispose();
      this._trimListener = e.activeBuffer.lines.onTrim((amount) => this._handleTrim(amount));
    }
    /**
     * Converts a viewport column (0 to cols - 1) to the character index on the
     * buffer line, the latter takes into account wide and null characters.
     * @param bufferLine The buffer line to use.
     * @param x The x index in the buffer line to convert.
     */
    _convertViewportColToCharacterIndex(bufferLine2, x) {
      let charIndex = x;
      for (let i2 = 0; x >= i2; i2++) {
        const length = bufferLine2.loadCell(i2, this._workCell).getChars().length;
        if (this._workCell.getWidth() === 0) {
          charIndex--;
        } else if (length > 1 && x !== i2) {
          charIndex += length - 1;
        }
      }
      return charIndex;
    }
    setSelection(col, row, length) {
      this._model.clearSelection();
      this._removeMouseDownListeners();
      this._model.selectionStart = [col, row];
      this._model.selectionStartLength = length;
      this.refresh();
      this._fireEventIfSelectionChanged();
    }
    rightClickSelect(ev) {
      if (!this._isClickInSelection(ev)) {
        if (this._selectWordAtCursor(ev, false)) {
          this.refresh(true);
        }
        this._fireEventIfSelectionChanged();
      }
    }
    /**
     * Gets positional information for the word at the coordinated specified.
     * @param coords The coordinates to get the word at.
     */
    _getWordAt(coords, allowWhitespaceOnlySelection, followWrappedLinesAbove = true, followWrappedLinesBelow = true) {
      if (coords[0] >= this._bufferService.cols) {
        return void 0;
      }
      const buffer = this._bufferService.buffer;
      const bufferLine2 = buffer.lines.get(coords[1]);
      if (!bufferLine2) {
        return void 0;
      }
      const line = buffer.translateBufferLineToString(coords[1], false);
      let startIndex = this._convertViewportColToCharacterIndex(bufferLine2, coords[0]);
      let endIndex = startIndex;
      const charOffset = coords[0] - startIndex;
      let leftWideCharCount = 0;
      let rightWideCharCount = 0;
      let leftLongCharOffset = 0;
      let rightLongCharOffset = 0;
      if (line.charAt(startIndex) === " ") {
        while (startIndex > 0 && line.charAt(startIndex - 1) === " ") {
          startIndex--;
        }
        while (endIndex < line.length && line.charAt(endIndex + 1) === " ") {
          endIndex++;
        }
      } else {
        let startCol = coords[0];
        let endCol = coords[0];
        if (bufferLine2.getWidth(startCol) === 0) {
          leftWideCharCount++;
          startCol--;
        }
        if (bufferLine2.getWidth(endCol) === 2) {
          rightWideCharCount++;
          endCol++;
        }
        const length2 = bufferLine2.getString(endCol).length;
        if (length2 > 1) {
          rightLongCharOffset += length2 - 1;
          endIndex += length2 - 1;
        }
        while (startCol > 0 && startIndex > 0 && !this._isCharWordSeparator(bufferLine2.loadCell(startCol - 1, this._workCell))) {
          bufferLine2.loadCell(startCol - 1, this._workCell);
          const length3 = this._workCell.getChars().length;
          if (this._workCell.getWidth() === 0) {
            leftWideCharCount++;
            startCol--;
          } else if (length3 > 1) {
            leftLongCharOffset += length3 - 1;
            startIndex -= length3 - 1;
          }
          startIndex--;
          startCol--;
        }
        while (endCol < bufferLine2.length && endIndex + 1 < line.length && !this._isCharWordSeparator(bufferLine2.loadCell(endCol + 1, this._workCell))) {
          bufferLine2.loadCell(endCol + 1, this._workCell);
          const length3 = this._workCell.getChars().length;
          if (this._workCell.getWidth() === 2) {
            rightWideCharCount++;
            endCol++;
          } else if (length3 > 1) {
            rightLongCharOffset += length3 - 1;
            endIndex += length3 - 1;
          }
          endIndex++;
          endCol++;
        }
      }
      endIndex++;
      let start = startIndex + charOffset - leftWideCharCount + leftLongCharOffset;
      let length = Math.min(
        this._bufferService.cols,
        // Disallow lengths larger than the terminal cols
        endIndex - startIndex + leftWideCharCount + rightWideCharCount - leftLongCharOffset - rightLongCharOffset
      );
      if (!allowWhitespaceOnlySelection && line.slice(startIndex, endIndex).trim() === "") {
        return void 0;
      }
      if (followWrappedLinesAbove) {
        if (start === 0 && bufferLine2.getCodePoint(0) !== 32) {
          const previousBufferLine = buffer.lines.get(coords[1] - 1);
          if (previousBufferLine && bufferLine2.isWrapped && previousBufferLine.getCodePoint(this._bufferService.cols - 1) !== 32) {
            const previousLineWordPosition = this._getWordAt([this._bufferService.cols - 1, coords[1] - 1], false, true, false);
            if (previousLineWordPosition) {
              const offset = this._bufferService.cols - previousLineWordPosition.start;
              start -= offset;
              length += offset;
            }
          }
        }
      }
      if (followWrappedLinesBelow) {
        if (start + length === this._bufferService.cols && bufferLine2.getCodePoint(this._bufferService.cols - 1) !== 32) {
          const nextBufferLine = buffer.lines.get(coords[1] + 1);
          if (nextBufferLine?.isWrapped && nextBufferLine.getCodePoint(0) !== 32) {
            const nextLineWordPosition = this._getWordAt([0, coords[1] + 1], false, false, true);
            if (nextLineWordPosition) {
              length += nextLineWordPosition.length;
            }
          }
        }
      }
      return { start, length };
    }
    /**
     * Selects the word at the coordinates specified.
     * @param coords The coordinates to get the word at.
     * @param allowWhitespaceOnlySelection If whitespace should be selected
     */
    _selectWordAt(coords, allowWhitespaceOnlySelection) {
      const wordPosition = this._getWordAt(coords, allowWhitespaceOnlySelection);
      if (wordPosition) {
        while (wordPosition.start < 0) {
          wordPosition.start += this._bufferService.cols;
          coords[1]--;
        }
        this._model.selectionStart = [wordPosition.start, coords[1]];
        this._model.selectionStartLength = wordPosition.length;
      }
    }
    /**
     * Sets the selection end to the word at the coordinated specified.
     * @param coords The coordinates to get the word at.
     */
    _selectToWordAt(coords) {
      const wordPosition = this._getWordAt(coords, true);
      if (wordPosition) {
        let endRow = coords[1];
        while (wordPosition.start < 0) {
          wordPosition.start += this._bufferService.cols;
          endRow--;
        }
        if (!this._model.areSelectionValuesReversed()) {
          while (wordPosition.start + wordPosition.length > this._bufferService.cols) {
            wordPosition.length -= this._bufferService.cols;
            endRow++;
          }
        }
        this._model.selectionEnd = [this._model.areSelectionValuesReversed() ? wordPosition.start : wordPosition.start + wordPosition.length, endRow];
      }
    }
    /**
     * Gets whether the character is considered a word separator by the select
     * word logic.
     * @param cell The cell to check.
     */
    _isCharWordSeparator(cell) {
      if (cell.getWidth() === 0) {
        return false;
      }
      return this._optionsService.rawOptions.wordSeparator.indexOf(cell.getChars()) >= 0;
    }
    /**
     * Selects the line specified.
     * @param line The line index.
     */
    _selectLineAt(line) {
      const wrappedRange = this._bufferService.buffer.getWrappedRangeForLine(line);
      const range = {
        start: { x: 0, y: wrappedRange.first },
        end: { x: this._bufferService.cols - 1, y: wrappedRange.last }
      };
      this._model.selectionStart = [0, wrappedRange.first];
      this._model.selectionEnd = void 0;
      this._model.selectionStartLength = getRangeLength(range, this._bufferService.cols);
    }
  };
  SelectionService = __decorateClass([
    __decorateParam(3, IBufferService),
    __decorateParam(4, ICoreService),
    __decorateParam(5, IMouseService),
    __decorateParam(6, IOptionsService),
    __decorateParam(7, IRenderService),
    __decorateParam(8, ICoreBrowserService)
  ], SelectionService);

  // node_modules/@xterm/xterm/src/common/MultiKeyMap.ts
  var TwoKeyMap = class {
    constructor() {
      this._data = {};
    }
    set(first, second, value) {
      if (!this._data[first]) {
        this._data[first] = {};
      }
      this._data[first][second] = value;
    }
    get(first, second) {
      return this._data[first] ? this._data[first][second] : void 0;
    }
    clear() {
      this._data = {};
    }
  };

  // node_modules/@xterm/xterm/src/browser/ColorContrastCache.ts
  var ColorContrastCache = class {
    constructor() {
      this._color = new TwoKeyMap();
      this._css = new TwoKeyMap();
    }
    setCss(bg, fg, value) {
      this._css.set(bg, fg, value);
    }
    getCss(bg, fg) {
      return this._css.get(bg, fg);
    }
    setColor(bg, fg, value) {
      this._color.set(bg, fg, value);
    }
    getColor(bg, fg) {
      return this._color.get(bg, fg);
    }
    clear() {
      this._color.clear();
      this._css.clear();
    }
  };

  // node_modules/@xterm/xterm/src/browser/Types.ts
  var DEFAULT_ANSI_COLORS = Object.freeze((() => {
    const colors = [
      // dark:
      css.toColor("#2e3436"),
      css.toColor("#cc0000"),
      css.toColor("#4e9a06"),
      css.toColor("#c4a000"),
      css.toColor("#3465a4"),
      css.toColor("#75507b"),
      css.toColor("#06989a"),
      css.toColor("#d3d7cf"),
      // bright:
      css.toColor("#555753"),
      css.toColor("#ef2929"),
      css.toColor("#8ae234"),
      css.toColor("#fce94f"),
      css.toColor("#729fcf"),
      css.toColor("#ad7fa8"),
      css.toColor("#34e2e2"),
      css.toColor("#eeeeec")
    ];
    const v = [0, 95, 135, 175, 215, 255];
    for (let i2 = 0; i2 < 216; i2++) {
      const r = v[i2 / 36 % 6 | 0];
      const g = v[i2 / 6 % 6 | 0];
      const b = v[i2 % 6];
      colors.push({
        css: channels.toCss(r, g, b),
        rgba: channels.toRgba(r, g, b)
      });
    }
    for (let i2 = 0; i2 < 24; i2++) {
      const c = 8 + i2 * 10;
      colors.push({
        css: channels.toCss(c, c, c),
        rgba: channels.toRgba(c, c, c)
      });
    }
    return colors;
  })());

  // node_modules/@xterm/xterm/src/browser/services/ThemeService.ts
  var DEFAULT_FOREGROUND = css.toColor("#ffffff");
  var DEFAULT_BACKGROUND = css.toColor("#000000");
  var DEFAULT_CURSOR = css.toColor("#ffffff");
  var DEFAULT_CURSOR_ACCENT = DEFAULT_BACKGROUND;
  var DEFAULT_SELECTION = {
    css: "rgba(255, 255, 255, 0.3)",
    rgba: 4294967117
  };
  var DEFAULT_OVERVIEW_RULER_BORDER = DEFAULT_FOREGROUND;
  var ThemeService = class extends Disposable {
    constructor(_optionsService) {
      super();
      this._optionsService = _optionsService;
      this._contrastCache = new ColorContrastCache();
      this._halfContrastCache = new ColorContrastCache();
      this._onChangeColors = this._register(new Emitter());
      this.onChangeColors = this._onChangeColors.event;
      this._colors = {
        foreground: DEFAULT_FOREGROUND,
        background: DEFAULT_BACKGROUND,
        cursor: DEFAULT_CURSOR,
        cursorAccent: DEFAULT_CURSOR_ACCENT,
        selectionForeground: void 0,
        selectionBackgroundTransparent: DEFAULT_SELECTION,
        selectionBackgroundOpaque: color.blend(DEFAULT_BACKGROUND, DEFAULT_SELECTION),
        selectionInactiveBackgroundTransparent: DEFAULT_SELECTION,
        selectionInactiveBackgroundOpaque: color.blend(DEFAULT_BACKGROUND, DEFAULT_SELECTION),
        scrollbarSliderBackground: color.opacity(DEFAULT_FOREGROUND, 0.2),
        scrollbarSliderHoverBackground: color.opacity(DEFAULT_FOREGROUND, 0.4),
        scrollbarSliderActiveBackground: color.opacity(DEFAULT_FOREGROUND, 0.5),
        overviewRulerBorder: DEFAULT_FOREGROUND,
        ansi: DEFAULT_ANSI_COLORS.slice(),
        contrastCache: this._contrastCache,
        halfContrastCache: this._halfContrastCache
      };
      this._updateRestoreColors();
      this._setTheme(this._optionsService.rawOptions.theme);
      this._register(this._optionsService.onSpecificOptionChange("minimumContrastRatio", () => this._contrastCache.clear()));
      this._register(this._optionsService.onSpecificOptionChange("theme", () => this._setTheme(this._optionsService.rawOptions.theme)));
    }
    get colors() {
      return this._colors;
    }
    /**
     * Sets the terminal's theme.
     * @param theme The  theme to use. If a partial theme is provided then default
     * colors will be used where colors are not defined.
     */
    _setTheme(theme = {}) {
      const colors = this._colors;
      colors.foreground = parseColor(theme.foreground, DEFAULT_FOREGROUND);
      colors.background = parseColor(theme.background, DEFAULT_BACKGROUND);
      colors.cursor = color.blend(colors.background, parseColor(theme.cursor, DEFAULT_CURSOR));
      colors.cursorAccent = color.blend(colors.background, parseColor(theme.cursorAccent, DEFAULT_CURSOR_ACCENT));
      colors.selectionBackgroundTransparent = parseColor(theme.selectionBackground, DEFAULT_SELECTION);
      colors.selectionBackgroundOpaque = color.blend(colors.background, colors.selectionBackgroundTransparent);
      colors.selectionInactiveBackgroundTransparent = parseColor(theme.selectionInactiveBackground, colors.selectionBackgroundTransparent);
      colors.selectionInactiveBackgroundOpaque = color.blend(colors.background, colors.selectionInactiveBackgroundTransparent);
      colors.selectionForeground = theme.selectionForeground ? parseColor(theme.selectionForeground, NULL_COLOR) : void 0;
      if (colors.selectionForeground === NULL_COLOR) {
        colors.selectionForeground = void 0;
      }
      if (color.isOpaque(colors.selectionBackgroundTransparent)) {
        const opacity = 0.3;
        colors.selectionBackgroundTransparent = color.opacity(colors.selectionBackgroundTransparent, opacity);
      }
      if (color.isOpaque(colors.selectionInactiveBackgroundTransparent)) {
        const opacity = 0.3;
        colors.selectionInactiveBackgroundTransparent = color.opacity(colors.selectionInactiveBackgroundTransparent, opacity);
      }
      colors.scrollbarSliderBackground = parseColor(theme.scrollbarSliderBackground, color.opacity(colors.foreground, 0.2));
      colors.scrollbarSliderHoverBackground = parseColor(theme.scrollbarSliderHoverBackground, color.opacity(colors.foreground, 0.4));
      colors.scrollbarSliderActiveBackground = parseColor(theme.scrollbarSliderActiveBackground, color.opacity(colors.foreground, 0.5));
      colors.overviewRulerBorder = parseColor(theme.overviewRulerBorder, DEFAULT_OVERVIEW_RULER_BORDER);
      colors.ansi = DEFAULT_ANSI_COLORS.slice();
      colors.ansi[0] = parseColor(theme.black, DEFAULT_ANSI_COLORS[0]);
      colors.ansi[1] = parseColor(theme.red, DEFAULT_ANSI_COLORS[1]);
      colors.ansi[2] = parseColor(theme.green, DEFAULT_ANSI_COLORS[2]);
      colors.ansi[3] = parseColor(theme.yellow, DEFAULT_ANSI_COLORS[3]);
      colors.ansi[4] = parseColor(theme.blue, DEFAULT_ANSI_COLORS[4]);
      colors.ansi[5] = parseColor(theme.magenta, DEFAULT_ANSI_COLORS[5]);
      colors.ansi[6] = parseColor(theme.cyan, DEFAULT_ANSI_COLORS[6]);
      colors.ansi[7] = parseColor(theme.white, DEFAULT_ANSI_COLORS[7]);
      colors.ansi[8] = parseColor(theme.brightBlack, DEFAULT_ANSI_COLORS[8]);
      colors.ansi[9] = parseColor(theme.brightRed, DEFAULT_ANSI_COLORS[9]);
      colors.ansi[10] = parseColor(theme.brightGreen, DEFAULT_ANSI_COLORS[10]);
      colors.ansi[11] = parseColor(theme.brightYellow, DEFAULT_ANSI_COLORS[11]);
      colors.ansi[12] = parseColor(theme.brightBlue, DEFAULT_ANSI_COLORS[12]);
      colors.ansi[13] = parseColor(theme.brightMagenta, DEFAULT_ANSI_COLORS[13]);
      colors.ansi[14] = parseColor(theme.brightCyan, DEFAULT_ANSI_COLORS[14]);
      colors.ansi[15] = parseColor(theme.brightWhite, DEFAULT_ANSI_COLORS[15]);
      if (theme.extendedAnsi) {
        const colorCount = Math.min(colors.ansi.length - 16, theme.extendedAnsi.length);
        for (let i2 = 0; i2 < colorCount; i2++) {
          colors.ansi[i2 + 16] = parseColor(theme.extendedAnsi[i2], DEFAULT_ANSI_COLORS[i2 + 16]);
        }
      }
      this._contrastCache.clear();
      this._halfContrastCache.clear();
      this._updateRestoreColors();
      this._onChangeColors.fire(this.colors);
    }
    restoreColor(slot) {
      this._restoreColor(slot);
      this._onChangeColors.fire(this.colors);
    }
    _restoreColor(slot) {
      if (slot === void 0) {
        for (let i2 = 0; i2 < this._restoreColors.ansi.length; ++i2) {
          this._colors.ansi[i2] = this._restoreColors.ansi[i2];
        }
        return;
      }
      switch (slot) {
        case 256 /* FOREGROUND */:
          this._colors.foreground = this._restoreColors.foreground;
          break;
        case 257 /* BACKGROUND */:
          this._colors.background = this._restoreColors.background;
          break;
        case 258 /* CURSOR */:
          this._colors.cursor = this._restoreColors.cursor;
          break;
        default:
          this._colors.ansi[slot] = this._restoreColors.ansi[slot];
      }
    }
    modifyColors(callback) {
      callback(this._colors);
      this._onChangeColors.fire(this.colors);
    }
    _updateRestoreColors() {
      this._restoreColors = {
        foreground: this._colors.foreground,
        background: this._colors.background,
        cursor: this._colors.cursor,
        ansi: this._colors.ansi.slice()
      };
    }
  };
  ThemeService = __decorateClass([
    __decorateParam(0, IOptionsService)
  ], ThemeService);
  function parseColor(cssString, fallback) {
    if (cssString !== void 0) {
      try {
        return css.toColor(cssString);
      } catch {
      }
    }
    return fallback;
  }

  // node_modules/@xterm/xterm/src/common/services/InstantiationService.ts
  var ServiceCollection = class {
    constructor(...entries) {
      this._entries = /* @__PURE__ */ new Map();
      for (const [id2, service] of entries) {
        this.set(id2, service);
      }
    }
    set(id2, instance) {
      const result = this._entries.get(id2);
      this._entries.set(id2, instance);
      return result;
    }
    forEach(callback) {
      for (const [key, value] of this._entries.entries()) {
        callback(key, value);
      }
    }
    has(id2) {
      return this._entries.has(id2);
    }
    get(id2) {
      return this._entries.get(id2);
    }
  };
  var InstantiationService = class {
    constructor() {
      this._services = new ServiceCollection();
      this._services.set(IInstantiationService, this);
    }
    setService(id2, instance) {
      this._services.set(id2, instance);
    }
    getService(id2) {
      return this._services.get(id2);
    }
    createInstance(ctor, ...args) {
      const serviceDependencies = getServiceDependencies(ctor).sort((a, b) => a.index - b.index);
      const serviceArgs = [];
      for (const dependency of serviceDependencies) {
        const service = this._services.get(dependency.id);
        if (!service) {
          throw new Error(`[createInstance] ${ctor.name} depends on UNKNOWN service ${dependency.id._id}.`);
        }
        serviceArgs.push(service);
      }
      const firstServiceArgPos = serviceDependencies.length > 0 ? serviceDependencies[0].index : args.length;
      if (args.length !== firstServiceArgPos) {
        throw new Error(`[createInstance] First service dependency of ${ctor.name} at position ${firstServiceArgPos + 1} conflicts with ${args.length} static arguments`);
      }
      return new ctor(...[...args, ...serviceArgs]);
    }
  };

  // node_modules/@xterm/xterm/src/common/services/LogService.ts
  var optionsKeyToLogLevel = {
    trace: 0 /* TRACE */,
    debug: 1 /* DEBUG */,
    info: 2 /* INFO */,
    warn: 3 /* WARN */,
    error: 4 /* ERROR */,
    off: 5 /* OFF */
  };
  var LOG_PREFIX = "xterm.js: ";
  var LogService = class extends Disposable {
    constructor(_optionsService) {
      super();
      this._optionsService = _optionsService;
      this._logLevel = 5 /* OFF */;
      this._updateLogLevel();
      this._register(this._optionsService.onSpecificOptionChange("logLevel", () => this._updateLogLevel()));
      traceLogger = this;
    }
    get logLevel() {
      return this._logLevel;
    }
    _updateLogLevel() {
      this._logLevel = optionsKeyToLogLevel[this._optionsService.rawOptions.logLevel];
    }
    _evalLazyOptionalParams(optionalParams) {
      for (let i2 = 0; i2 < optionalParams.length; i2++) {
        if (typeof optionalParams[i2] === "function") {
          optionalParams[i2] = optionalParams[i2]();
        }
      }
    }
    _log(type, message, optionalParams) {
      this._evalLazyOptionalParams(optionalParams);
      type.call(console, (this._optionsService.options.logger ? "" : LOG_PREFIX) + message, ...optionalParams);
    }
    trace(message, ...optionalParams) {
      if (this._logLevel <= 0 /* TRACE */) {
        this._log(this._optionsService.options.logger?.trace.bind(this._optionsService.options.logger) ?? console.log, message, optionalParams);
      }
    }
    debug(message, ...optionalParams) {
      if (this._logLevel <= 1 /* DEBUG */) {
        this._log(this._optionsService.options.logger?.debug.bind(this._optionsService.options.logger) ?? console.log, message, optionalParams);
      }
    }
    info(message, ...optionalParams) {
      if (this._logLevel <= 2 /* INFO */) {
        this._log(this._optionsService.options.logger?.info.bind(this._optionsService.options.logger) ?? console.info, message, optionalParams);
      }
    }
    warn(message, ...optionalParams) {
      if (this._logLevel <= 3 /* WARN */) {
        this._log(this._optionsService.options.logger?.warn.bind(this._optionsService.options.logger) ?? console.warn, message, optionalParams);
      }
    }
    error(message, ...optionalParams) {
      if (this._logLevel <= 4 /* ERROR */) {
        this._log(this._optionsService.options.logger?.error.bind(this._optionsService.options.logger) ?? console.error, message, optionalParams);
      }
    }
  };
  LogService = __decorateClass([
    __decorateParam(0, IOptionsService)
  ], LogService);
  var traceLogger;

  // node_modules/@xterm/xterm/src/common/CircularList.ts
  var CircularList = class extends Disposable {
    constructor(_maxLength) {
      super();
      this._maxLength = _maxLength;
      this.onDeleteEmitter = this._register(new Emitter());
      this.onDelete = this.onDeleteEmitter.event;
      this.onInsertEmitter = this._register(new Emitter());
      this.onInsert = this.onInsertEmitter.event;
      this.onTrimEmitter = this._register(new Emitter());
      this.onTrim = this.onTrimEmitter.event;
      this._array = new Array(this._maxLength);
      this._startIndex = 0;
      this._length = 0;
    }
    get maxLength() {
      return this._maxLength;
    }
    set maxLength(newMaxLength) {
      if (this._maxLength === newMaxLength) {
        return;
      }
      const newArray = new Array(newMaxLength);
      for (let i2 = 0; i2 < Math.min(newMaxLength, this.length); i2++) {
        newArray[i2] = this._array[this._getCyclicIndex(i2)];
      }
      this._array = newArray;
      this._maxLength = newMaxLength;
      this._startIndex = 0;
    }
    get length() {
      return this._length;
    }
    set length(newLength) {
      if (newLength > this._length) {
        for (let i2 = this._length; i2 < newLength; i2++) {
          this._array[i2] = void 0;
        }
      }
      this._length = newLength;
    }
    /**
     * Gets the value at an index.
     *
     * Note that for performance reasons there is no bounds checking here, the index reference is
     * circular so this should always return a value and never throw.
     * @param index The index of the value to get.
     * @returns The value corresponding to the index.
     */
    get(index) {
      return this._array[this._getCyclicIndex(index)];
    }
    /**
     * Sets the value at an index.
     *
     * Note that for performance reasons there is no bounds checking here, the index reference is
     * circular so this should always return a value and never throw.
     * @param index The index to set.
     * @param value The value to set.
     */
    set(index, value) {
      this._array[this._getCyclicIndex(index)] = value;
    }
    /**
     * Pushes a new value onto the list, wrapping around to the start of the array, overriding index 0
     * if the maximum length is reached.
     * @param value The value to push onto the list.
     */
    push(value) {
      this._array[this._getCyclicIndex(this._length)] = value;
      if (this._length === this._maxLength) {
        this._startIndex = ++this._startIndex % this._maxLength;
        this.onTrimEmitter.fire(1);
      } else {
        this._length++;
      }
    }
    /**
     * Advance ringbuffer index and return current element for recycling.
     * Note: The buffer must be full for this method to work.
     * @throws When the buffer is not full.
     */
    recycle() {
      if (this._length !== this._maxLength) {
        throw new Error("Can only recycle when the buffer is full");
      }
      this._startIndex = ++this._startIndex % this._maxLength;
      this.onTrimEmitter.fire(1);
      return this._array[this._getCyclicIndex(this._length - 1)];
    }
    /**
     * Ringbuffer is at max length.
     */
    get isFull() {
      return this._length === this._maxLength;
    }
    /**
     * Removes and returns the last value on the list.
     * @returns The popped value.
     */
    pop() {
      return this._array[this._getCyclicIndex(this._length-- - 1)];
    }
    /**
     * Deletes and/or inserts items at a particular index (in that order). Unlike
     * Array.prototype.splice, this operation does not return the deleted items as a new array in
     * order to save creating a new array. Note that this operation may shift all values in the list
     * in the worst case.
     * @param start The index to delete and/or insert.
     * @param deleteCount The number of elements to delete.
     * @param items The items to insert.
     */
    splice(start, deleteCount, ...items) {
      if (deleteCount) {
        for (let i2 = start; i2 < this._length - deleteCount; i2++) {
          this._array[this._getCyclicIndex(i2)] = this._array[this._getCyclicIndex(i2 + deleteCount)];
        }
        this._length -= deleteCount;
        this.onDeleteEmitter.fire({ index: start, amount: deleteCount });
      }
      for (let i2 = this._length - 1; i2 >= start; i2--) {
        this._array[this._getCyclicIndex(i2 + items.length)] = this._array[this._getCyclicIndex(i2)];
      }
      for (let i2 = 0; i2 < items.length; i2++) {
        this._array[this._getCyclicIndex(start + i2)] = items[i2];
      }
      if (items.length) {
        this.onInsertEmitter.fire({ index: start, amount: items.length });
      }
      if (this._length + items.length > this._maxLength) {
        const countToTrim = this._length + items.length - this._maxLength;
        this._startIndex += countToTrim;
        this._length = this._maxLength;
        this.onTrimEmitter.fire(countToTrim);
      } else {
        this._length += items.length;
      }
    }
    /**
     * Trims a number of items from the start of the list.
     * @param count The number of items to remove.
     */
    trimStart(count) {
      if (count > this._length) {
        count = this._length;
      }
      this._startIndex += count;
      this._length -= count;
      this.onTrimEmitter.fire(count);
    }
    shiftElements(start, count, offset) {
      if (count <= 0) {
        return;
      }
      if (start < 0 || start >= this._length) {
        throw new Error("start argument out of range");
      }
      if (start + offset < 0) {
        throw new Error("Cannot shift elements in list beyond index 0");
      }
      if (offset > 0) {
        for (let i2 = count - 1; i2 >= 0; i2--) {
          this.set(start + i2 + offset, this.get(start + i2));
        }
        const expandListBy = start + count + offset - this._length;
        if (expandListBy > 0) {
          this._length += expandListBy;
          while (this._length > this._maxLength) {
            this._length--;
            this._startIndex++;
            this.onTrimEmitter.fire(1);
          }
        }
      } else {
        for (let i2 = 0; i2 < count; i2++) {
          this.set(start + i2 + offset, this.get(start + i2));
        }
      }
    }
    /**
     * Gets the cyclic index for the specified regular index. The cyclic index can then be used on the
     * backing array to get the element associated with the regular index.
     * @param index The regular index.
     * @returns The cyclic index.
     */
    _getCyclicIndex(index) {
      return (this._startIndex + index) % this._maxLength;
    }
  };

  // node_modules/@xterm/xterm/src/common/buffer/BufferLine.ts
  var CELL_SIZE = 3;
  var DEFAULT_ATTR_DATA = Object.freeze(new AttributeData());
  var $startIndex = 0;
  var CLEANUP_THRESHOLD = 2;
  var BufferLine = class _BufferLine {
    constructor(cols, fillCellData, isWrapped = false) {
      this.isWrapped = isWrapped;
      this._combined = {};
      this._extendedAttrs = {};
      this._data = new Uint32Array(cols * CELL_SIZE);
      const cell = fillCellData || CellData.fromCharData([0, NULL_CELL_CHAR, NULL_CELL_WIDTH, NULL_CELL_CODE]);
      for (let i2 = 0; i2 < cols; ++i2) {
        this.setCell(i2, cell);
      }
      this.length = cols;
    }
    /**
     * Get cell data CharData.
     * @deprecated
     */
    get(index) {
      const content = this._data[index * CELL_SIZE + 0 /* CONTENT */];
      const cp = content & 2097151 /* CODEPOINT_MASK */;
      return [
        this._data[index * CELL_SIZE + 1 /* FG */],
        content & 2097152 /* IS_COMBINED_MASK */ ? this._combined[index] : cp ? stringFromCodePoint(cp) : "",
        content >> 22 /* WIDTH_SHIFT */,
        content & 2097152 /* IS_COMBINED_MASK */ ? this._combined[index].charCodeAt(this._combined[index].length - 1) : cp
      ];
    }
    /**
     * Set cell data from CharData.
     * @deprecated
     */
    set(index, value) {
      this._data[index * CELL_SIZE + 1 /* FG */] = value[CHAR_DATA_ATTR_INDEX];
      if (value[CHAR_DATA_CHAR_INDEX].length > 1) {
        this._combined[index] = value[1];
        this._data[index * CELL_SIZE + 0 /* CONTENT */] = index | 2097152 /* IS_COMBINED_MASK */ | value[CHAR_DATA_WIDTH_INDEX] << 22 /* WIDTH_SHIFT */;
      } else {
        this._data[index * CELL_SIZE + 0 /* CONTENT */] = value[CHAR_DATA_CHAR_INDEX].charCodeAt(0) | value[CHAR_DATA_WIDTH_INDEX] << 22 /* WIDTH_SHIFT */;
      }
    }
    /**
     * primitive getters
     * use these when only one value is needed, otherwise use `loadCell`
     */
    getWidth(index) {
      return this._data[index * CELL_SIZE + 0 /* CONTENT */] >> 22 /* WIDTH_SHIFT */;
    }
    /** Test whether content has width. */
    hasWidth(index) {
      return this._data[index * CELL_SIZE + 0 /* CONTENT */] & 12582912 /* WIDTH_MASK */;
    }
    /** Get FG cell component. */
    getFg(index) {
      return this._data[index * CELL_SIZE + 1 /* FG */];
    }
    /** Get BG cell component. */
    getBg(index) {
      return this._data[index * CELL_SIZE + 2 /* BG */];
    }
    /**
     * Test whether contains any chars.
     * Basically an empty has no content, but other cells might differ in FG/BG
     * from real empty cells.
     */
    hasContent(index) {
      return this._data[index * CELL_SIZE + 0 /* CONTENT */] & 4194303 /* HAS_CONTENT_MASK */;
    }
    /**
     * Get codepoint of the cell.
     * To be in line with `code` in CharData this either returns
     * a single UTF32 codepoint or the last codepoint of a combined string.
     */
    getCodePoint(index) {
      const content = this._data[index * CELL_SIZE + 0 /* CONTENT */];
      if (content & 2097152 /* IS_COMBINED_MASK */) {
        return this._combined[index].charCodeAt(this._combined[index].length - 1);
      }
      return content & 2097151 /* CODEPOINT_MASK */;
    }
    /** Test whether the cell contains a combined string. */
    isCombined(index) {
      return this._data[index * CELL_SIZE + 0 /* CONTENT */] & 2097152 /* IS_COMBINED_MASK */;
    }
    /** Returns the string content of the cell. */
    getString(index) {
      const content = this._data[index * CELL_SIZE + 0 /* CONTENT */];
      if (content & 2097152 /* IS_COMBINED_MASK */) {
        return this._combined[index];
      }
      if (content & 2097151 /* CODEPOINT_MASK */) {
        return stringFromCodePoint(content & 2097151 /* CODEPOINT_MASK */);
      }
      return "";
    }
    /** Get state of protected flag. */
    isProtected(index) {
      return this._data[index * CELL_SIZE + 2 /* BG */] & 536870912 /* PROTECTED */;
    }
    /**
     * Load data at `index` into `cell`. This is used to access cells in a way that's more friendly
     * to GC as it significantly reduced the amount of new objects/references needed.
     */
    loadCell(index, cell) {
      $startIndex = index * CELL_SIZE;
      cell.content = this._data[$startIndex + 0 /* CONTENT */];
      cell.fg = this._data[$startIndex + 1 /* FG */];
      cell.bg = this._data[$startIndex + 2 /* BG */];
      if (cell.content & 2097152 /* IS_COMBINED_MASK */) {
        cell.combinedData = this._combined[index];
      }
      if (cell.bg & 268435456 /* HAS_EXTENDED */) {
        cell.extended = this._extendedAttrs[index];
      }
      return cell;
    }
    /**
     * Set data at `index` to `cell`.
     */
    setCell(index, cell) {
      if (cell.content & 2097152 /* IS_COMBINED_MASK */) {
        this._combined[index] = cell.combinedData;
      }
      if (cell.bg & 268435456 /* HAS_EXTENDED */) {
        this._extendedAttrs[index] = cell.extended;
      }
      this._data[index * CELL_SIZE + 0 /* CONTENT */] = cell.content;
      this._data[index * CELL_SIZE + 1 /* FG */] = cell.fg;
      this._data[index * CELL_SIZE + 2 /* BG */] = cell.bg;
    }
    /**
     * Set cell data from input handler.
     * Since the input handler see the incoming chars as UTF32 codepoints,
     * it gets an optimized access method.
     */
    setCellFromCodepoint(index, codePoint, width, attrs) {
      if (attrs.bg & 268435456 /* HAS_EXTENDED */) {
        this._extendedAttrs[index] = attrs.extended;
      }
      this._data[index * CELL_SIZE + 0 /* CONTENT */] = codePoint | width << 22 /* WIDTH_SHIFT */;
      this._data[index * CELL_SIZE + 1 /* FG */] = attrs.fg;
      this._data[index * CELL_SIZE + 2 /* BG */] = attrs.bg;
    }
    /**
     * Add a codepoint to a cell from input handler.
     * During input stage combining chars with a width of 0 follow and stack
     * onto a leading char. Since we already set the attrs
     * by the previous `setDataFromCodePoint` call, we can omit it here.
     */
    addCodepointToCell(index, codePoint, width) {
      let content = this._data[index * CELL_SIZE + 0 /* CONTENT */];
      if (content & 2097152 /* IS_COMBINED_MASK */) {
        this._combined[index] += stringFromCodePoint(codePoint);
      } else {
        if (content & 2097151 /* CODEPOINT_MASK */) {
          this._combined[index] = stringFromCodePoint(content & 2097151 /* CODEPOINT_MASK */) + stringFromCodePoint(codePoint);
          content &= ~2097151 /* CODEPOINT_MASK */;
          content |= 2097152 /* IS_COMBINED_MASK */;
        } else {
          content = codePoint | 1 << 22 /* WIDTH_SHIFT */;
        }
      }
      if (width) {
        content &= ~12582912 /* WIDTH_MASK */;
        content |= width << 22 /* WIDTH_SHIFT */;
      }
      this._data[index * CELL_SIZE + 0 /* CONTENT */] = content;
    }
    insertCells(pos, n, fillCellData) {
      pos %= this.length;
      if (pos && this.getWidth(pos - 1) === 2) {
        this.setCellFromCodepoint(pos - 1, 0, 1, fillCellData);
      }
      if (n < this.length - pos) {
        const cell = new CellData();
        for (let i2 = this.length - pos - n - 1; i2 >= 0; --i2) {
          this.setCell(pos + n + i2, this.loadCell(pos + i2, cell));
        }
        for (let i2 = 0; i2 < n; ++i2) {
          this.setCell(pos + i2, fillCellData);
        }
      } else {
        for (let i2 = pos; i2 < this.length; ++i2) {
          this.setCell(i2, fillCellData);
        }
      }
      if (this.getWidth(this.length - 1) === 2) {
        this.setCellFromCodepoint(this.length - 1, 0, 1, fillCellData);
      }
    }
    deleteCells(pos, n, fillCellData) {
      pos %= this.length;
      if (n < this.length - pos) {
        const cell = new CellData();
        for (let i2 = 0; i2 < this.length - pos - n; ++i2) {
          this.setCell(pos + i2, this.loadCell(pos + n + i2, cell));
        }
        for (let i2 = this.length - n; i2 < this.length; ++i2) {
          this.setCell(i2, fillCellData);
        }
      } else {
        for (let i2 = pos; i2 < this.length; ++i2) {
          this.setCell(i2, fillCellData);
        }
      }
      if (pos && this.getWidth(pos - 1) === 2) {
        this.setCellFromCodepoint(pos - 1, 0, 1, fillCellData);
      }
      if (this.getWidth(pos) === 0 && !this.hasContent(pos)) {
        this.setCellFromCodepoint(pos, 0, 1, fillCellData);
      }
    }
    replaceCells(start, end, fillCellData, respectProtect = false) {
      if (respectProtect) {
        if (start && this.getWidth(start - 1) === 2 && !this.isProtected(start - 1)) {
          this.setCellFromCodepoint(start - 1, 0, 1, fillCellData);
        }
        if (end < this.length && this.getWidth(end - 1) === 2 && !this.isProtected(end)) {
          this.setCellFromCodepoint(end, 0, 1, fillCellData);
        }
        while (start < end && start < this.length) {
          if (!this.isProtected(start)) {
            this.setCell(start, fillCellData);
          }
          start++;
        }
        return;
      }
      if (start && this.getWidth(start - 1) === 2) {
        this.setCellFromCodepoint(start - 1, 0, 1, fillCellData);
      }
      if (end < this.length && this.getWidth(end - 1) === 2) {
        this.setCellFromCodepoint(end, 0, 1, fillCellData);
      }
      while (start < end && start < this.length) {
        this.setCell(start++, fillCellData);
      }
    }
    /**
     * Resize BufferLine to `cols` filling excess cells with `fillCellData`.
     * The underlying array buffer will not change if there is still enough space
     * to hold the new buffer line data.
     * Returns a boolean indicating, whether a `cleanupMemory` call would free
     * excess memory (true after shrinking > CLEANUP_THRESHOLD).
     */
    resize(cols, fillCellData) {
      if (cols === this.length) {
        return this._data.length * 4 * CLEANUP_THRESHOLD < this._data.buffer.byteLength;
      }
      const uint32Cells = cols * CELL_SIZE;
      if (cols > this.length) {
        if (this._data.buffer.byteLength >= uint32Cells * 4) {
          this._data = new Uint32Array(this._data.buffer, 0, uint32Cells);
        } else {
          const data = new Uint32Array(uint32Cells);
          data.set(this._data);
          this._data = data;
        }
        for (let i2 = this.length; i2 < cols; ++i2) {
          this.setCell(i2, fillCellData);
        }
      } else {
        this._data = this._data.subarray(0, uint32Cells);
        const keys = Object.keys(this._combined);
        for (let i2 = 0; i2 < keys.length; i2++) {
          const key = parseInt(keys[i2], 10);
          if (key >= cols) {
            delete this._combined[key];
          }
        }
        const extKeys = Object.keys(this._extendedAttrs);
        for (let i2 = 0; i2 < extKeys.length; i2++) {
          const key = parseInt(extKeys[i2], 10);
          if (key >= cols) {
            delete this._extendedAttrs[key];
          }
        }
      }
      this.length = cols;
      return uint32Cells * 4 * CLEANUP_THRESHOLD < this._data.buffer.byteLength;
    }
    /**
     * Cleanup underlying array buffer.
     * A cleanup will be triggered if the array buffer exceeds the actual used
     * memory by a factor of CLEANUP_THRESHOLD.
     * Returns 0 or 1 indicating whether a cleanup happened.
     */
    cleanupMemory() {
      if (this._data.length * 4 * CLEANUP_THRESHOLD < this._data.buffer.byteLength) {
        const data = new Uint32Array(this._data.length);
        data.set(this._data);
        this._data = data;
        return 1;
      }
      return 0;
    }
    /** fill a line with fillCharData */
    fill(fillCellData, respectProtect = false) {
      if (respectProtect) {
        for (let i2 = 0; i2 < this.length; ++i2) {
          if (!this.isProtected(i2)) {
            this.setCell(i2, fillCellData);
          }
        }
        return;
      }
      this._combined = {};
      this._extendedAttrs = {};
      for (let i2 = 0; i2 < this.length; ++i2) {
        this.setCell(i2, fillCellData);
      }
    }
    /** alter to a full copy of line  */
    copyFrom(line) {
      if (this.length !== line.length) {
        this._data = new Uint32Array(line._data);
      } else {
        this._data.set(line._data);
      }
      this.length = line.length;
      this._combined = {};
      for (const el in line._combined) {
        this._combined[el] = line._combined[el];
      }
      this._extendedAttrs = {};
      for (const el in line._extendedAttrs) {
        this._extendedAttrs[el] = line._extendedAttrs[el];
      }
      this.isWrapped = line.isWrapped;
    }
    /** create a new clone */
    clone() {
      const newLine = new _BufferLine(0);
      newLine._data = new Uint32Array(this._data);
      newLine.length = this.length;
      for (const el in this._combined) {
        newLine._combined[el] = this._combined[el];
      }
      for (const el in this._extendedAttrs) {
        newLine._extendedAttrs[el] = this._extendedAttrs[el];
      }
      newLine.isWrapped = this.isWrapped;
      return newLine;
    }
    getTrimmedLength() {
      for (let i2 = this.length - 1; i2 >= 0; --i2) {
        if (this._data[i2 * CELL_SIZE + 0 /* CONTENT */] & 4194303 /* HAS_CONTENT_MASK */) {
          return i2 + (this._data[i2 * CELL_SIZE + 0 /* CONTENT */] >> 22 /* WIDTH_SHIFT */);
        }
      }
      return 0;
    }
    getNoBgTrimmedLength() {
      for (let i2 = this.length - 1; i2 >= 0; --i2) {
        if (this._data[i2 * CELL_SIZE + 0 /* CONTENT */] & 4194303 /* HAS_CONTENT_MASK */ || this._data[i2 * CELL_SIZE + 2 /* BG */] & 50331648 /* CM_MASK */) {
          return i2 + (this._data[i2 * CELL_SIZE + 0 /* CONTENT */] >> 22 /* WIDTH_SHIFT */);
        }
      }
      return 0;
    }
    copyCellsFrom(src, srcCol, destCol, length, applyInReverse) {
      const srcData = src._data;
      if (applyInReverse) {
        for (let cell = length - 1; cell >= 0; cell--) {
          for (let i2 = 0; i2 < CELL_SIZE; i2++) {
            this._data[(destCol + cell) * CELL_SIZE + i2] = srcData[(srcCol + cell) * CELL_SIZE + i2];
          }
          if (srcData[(srcCol + cell) * CELL_SIZE + 2 /* BG */] & 268435456 /* HAS_EXTENDED */) {
            this._extendedAttrs[destCol + cell] = src._extendedAttrs[srcCol + cell];
          }
        }
      } else {
        for (let cell = 0; cell < length; cell++) {
          for (let i2 = 0; i2 < CELL_SIZE; i2++) {
            this._data[(destCol + cell) * CELL_SIZE + i2] = srcData[(srcCol + cell) * CELL_SIZE + i2];
          }
          if (srcData[(srcCol + cell) * CELL_SIZE + 2 /* BG */] & 268435456 /* HAS_EXTENDED */) {
            this._extendedAttrs[destCol + cell] = src._extendedAttrs[srcCol + cell];
          }
        }
      }
      const srcCombinedKeys = Object.keys(src._combined);
      for (let i2 = 0; i2 < srcCombinedKeys.length; i2++) {
        const key = parseInt(srcCombinedKeys[i2], 10);
        if (key >= srcCol) {
          this._combined[key - srcCol + destCol] = src._combined[key];
        }
      }
    }
    /**
     * Translates the buffer line to a string.
     *
     * @param trimRight Whether to trim any empty cells on the right.
     * @param startCol The column to start the string (0-based inclusive).
     * @param endCol The column to end the string (0-based exclusive).
     * @param outColumns if specified, this array will be filled with column numbers such that
     * `returnedString[i]` is displayed at `outColumns[i]` column. `outColumns[returnedString.length]`
     * is where the character following `returnedString` will be displayed.
     *
     * When a single cell is translated to multiple UTF-16 code units (e.g. surrogate pair) in the
     * returned string, the corresponding entries in `outColumns` will have the same column number.
     */
    translateToString(trimRight, startCol, endCol, outColumns) {
      startCol = startCol ?? 0;
      endCol = endCol ?? this.length;
      if (trimRight) {
        endCol = Math.min(endCol, this.getTrimmedLength());
      }
      if (outColumns) {
        outColumns.length = 0;
      }
      let result = "";
      while (startCol < endCol) {
        const content = this._data[startCol * CELL_SIZE + 0 /* CONTENT */];
        const cp = content & 2097151 /* CODEPOINT_MASK */;
        const chars = content & 2097152 /* IS_COMBINED_MASK */ ? this._combined[startCol] : cp ? stringFromCodePoint(cp) : WHITESPACE_CELL_CHAR;
        result += chars;
        if (outColumns) {
          for (let i2 = 0; i2 < chars.length; ++i2) {
            outColumns.push(startCol);
          }
        }
        startCol += content >> 22 /* WIDTH_SHIFT */ || 1;
      }
      if (outColumns) {
        outColumns.push(startCol);
      }
      return result;
    }
  };

  // node_modules/@xterm/xterm/src/common/buffer/BufferReflow.ts
  function reflowLargerGetLinesToRemove(lines, oldCols, newCols, bufferAbsoluteY, nullCell, reflowCursorLine) {
    const toRemove = [];
    for (let y = 0; y < lines.length - 1; y++) {
      let i2 = y;
      let nextLine = lines.get(++i2);
      if (!nextLine.isWrapped) {
        continue;
      }
      const wrappedLines = [lines.get(y)];
      while (i2 < lines.length && nextLine.isWrapped) {
        wrappedLines.push(nextLine);
        nextLine = lines.get(++i2);
      }
      if (!reflowCursorLine) {
        if (bufferAbsoluteY >= y && bufferAbsoluteY < i2) {
          y += wrappedLines.length - 1;
          continue;
        }
      }
      let destLineIndex = 0;
      let destCol = getWrappedLineTrimmedLength(wrappedLines, destLineIndex, oldCols);
      let srcLineIndex = 1;
      let srcCol = 0;
      while (srcLineIndex < wrappedLines.length) {
        const srcTrimmedTineLength = getWrappedLineTrimmedLength(wrappedLines, srcLineIndex, oldCols);
        const srcRemainingCells = srcTrimmedTineLength - srcCol;
        const destRemainingCells = newCols - destCol;
        const cellsToCopy = Math.min(srcRemainingCells, destRemainingCells);
        wrappedLines[destLineIndex].copyCellsFrom(wrappedLines[srcLineIndex], srcCol, destCol, cellsToCopy, false);
        destCol += cellsToCopy;
        if (destCol === newCols) {
          destLineIndex++;
          destCol = 0;
        }
        srcCol += cellsToCopy;
        if (srcCol === srcTrimmedTineLength) {
          srcLineIndex++;
          srcCol = 0;
        }
        if (destCol === 0 && destLineIndex !== 0) {
          if (wrappedLines[destLineIndex - 1].getWidth(newCols - 1) === 2) {
            wrappedLines[destLineIndex].copyCellsFrom(wrappedLines[destLineIndex - 1], newCols - 1, destCol++, 1, false);
            wrappedLines[destLineIndex - 1].setCell(newCols - 1, nullCell);
          }
        }
      }
      wrappedLines[destLineIndex].replaceCells(destCol, newCols, nullCell);
      let countToRemove = 0;
      for (let i3 = wrappedLines.length - 1; i3 > 0; i3--) {
        if (i3 > destLineIndex || wrappedLines[i3].getTrimmedLength() === 0) {
          countToRemove++;
        } else {
          break;
        }
      }
      if (countToRemove > 0) {
        toRemove.push(y + wrappedLines.length - countToRemove);
        toRemove.push(countToRemove);
      }
      y += wrappedLines.length - 1;
    }
    return toRemove;
  }
  function reflowLargerCreateNewLayout(lines, toRemove) {
    const layout = [];
    let nextToRemoveIndex = 0;
    let nextToRemoveStart = toRemove[nextToRemoveIndex];
    let countRemovedSoFar = 0;
    for (let i2 = 0; i2 < lines.length; i2++) {
      if (nextToRemoveStart === i2) {
        const countToRemove = toRemove[++nextToRemoveIndex];
        lines.onDeleteEmitter.fire({
          index: i2 - countRemovedSoFar,
          amount: countToRemove
        });
        i2 += countToRemove - 1;
        countRemovedSoFar += countToRemove;
        nextToRemoveStart = toRemove[++nextToRemoveIndex];
      } else {
        layout.push(i2);
      }
    }
    return {
      layout,
      countRemoved: countRemovedSoFar
    };
  }
  function reflowLargerApplyNewLayout(lines, newLayout) {
    const newLayoutLines = [];
    for (let i2 = 0; i2 < newLayout.length; i2++) {
      newLayoutLines.push(lines.get(newLayout[i2]));
    }
    for (let i2 = 0; i2 < newLayoutLines.length; i2++) {
      lines.set(i2, newLayoutLines[i2]);
    }
    lines.length = newLayout.length;
  }
  function reflowSmallerGetNewLineLengths(wrappedLines, oldCols, newCols) {
    const newLineLengths = [];
    const cellsNeeded = wrappedLines.map((l, i2) => getWrappedLineTrimmedLength(wrappedLines, i2, oldCols)).reduce((p, c) => p + c);
    let srcCol = 0;
    let srcLine = 0;
    let cellsAvailable = 0;
    while (cellsAvailable < cellsNeeded) {
      if (cellsNeeded - cellsAvailable < newCols) {
        newLineLengths.push(cellsNeeded - cellsAvailable);
        break;
      }
      srcCol += newCols;
      const oldTrimmedLength = getWrappedLineTrimmedLength(wrappedLines, srcLine, oldCols);
      if (srcCol > oldTrimmedLength) {
        srcCol -= oldTrimmedLength;
        srcLine++;
      }
      const endsWithWide = wrappedLines[srcLine].getWidth(srcCol - 1) === 2;
      if (endsWithWide) {
        srcCol--;
      }
      const lineLength = endsWithWide ? newCols - 1 : newCols;
      newLineLengths.push(lineLength);
      cellsAvailable += lineLength;
    }
    return newLineLengths;
  }
  function getWrappedLineTrimmedLength(lines, i2, cols) {
    if (i2 === lines.length - 1) {
      return lines[i2].getTrimmedLength();
    }
    const endsInNull = !lines[i2].hasContent(cols - 1) && lines[i2].getWidth(cols - 1) === 1;
    const followingLineStartsWithWide = lines[i2 + 1].getWidth(0) === 2;
    if (endsInNull && followingLineStartsWithWide) {
      return cols - 1;
    }
    return cols;
  }

  // node_modules/@xterm/xterm/src/common/buffer/Marker.ts
  var _Marker = class _Marker {
    constructor(line) {
      this.line = line;
      this.isDisposed = false;
      this._disposables = [];
      this._id = _Marker._nextId++;
      this._onDispose = this.register(new Emitter());
      this.onDispose = this._onDispose.event;
    }
    get id() {
      return this._id;
    }
    dispose() {
      if (this.isDisposed) {
        return;
      }
      this.isDisposed = true;
      this.line = -1;
      this._onDispose.fire();
      dispose(this._disposables);
      this._disposables.length = 0;
    }
    register(disposable) {
      this._disposables.push(disposable);
      return disposable;
    }
  };
  _Marker._nextId = 1;
  var Marker = _Marker;

  // node_modules/@xterm/xterm/src/common/data/Charsets.ts
  var CHARSETS = {};
  var DEFAULT_CHARSET = CHARSETS["B"];
  CHARSETS["0"] = {
    "`": "◆",
    // '◆'
    "a": "▒",
    // '▒'
    "b": "␉",
    // '␉' (HT)
    "c": "␌",
    // '␌' (FF)
    "d": "␍",
    // '␍' (CR)
    "e": "␊",
    // '␊' (LF)
    "f": "°",
    // '°'
    "g": "±",
    // '±'
    "h": "␤",
    // '␤' (NL)
    "i": "␋",
    // '␋' (VT)
    "j": "┘",
    // '┘'
    "k": "┐",
    // '┐'
    "l": "┌",
    // '┌'
    "m": "└",
    // '└'
    "n": "┼",
    // '┼'
    "o": "⎺",
    // '⎺'
    "p": "⎻",
    // '⎻'
    "q": "─",
    // '─'
    "r": "⎼",
    // '⎼'
    "s": "⎽",
    // '⎽'
    "t": "├",
    // '├'
    "u": "┤",
    // '┤'
    "v": "┴",
    // '┴'
    "w": "┬",
    // '┬'
    "x": "│",
    // '│'
    "y": "≤",
    // '≤'
    "z": "≥",
    // '≥'
    "{": "π",
    // 'π'
    "|": "≠",
    // '≠'
    "}": "£",
    // '£'
    "~": "·"
    // '·'
  };
  CHARSETS["A"] = {
    "#": "£"
  };
  CHARSETS["B"] = void 0;
  CHARSETS["4"] = {
    "#": "£",
    "@": "¾",
    "[": "ij",
    "\\": "½",
    "]": "|",
    "{": "¨",
    "|": "f",
    "}": "¼",
    "~": "´"
  };
  CHARSETS["C"] = CHARSETS["5"] = {
    "[": "Ä",
    "\\": "Ö",
    "]": "Å",
    "^": "Ü",
    "`": "é",
    "{": "ä",
    "|": "ö",
    "}": "å",
    "~": "ü"
  };
  CHARSETS["R"] = {
    "#": "£",
    "@": "à",
    "[": "°",
    "\\": "ç",
    "]": "§",
    "{": "é",
    "|": "ù",
    "}": "è",
    "~": "¨"
  };
  CHARSETS["Q"] = {
    "@": "à",
    "[": "â",
    "\\": "ç",
    "]": "ê",
    "^": "î",
    "`": "ô",
    "{": "é",
    "|": "ù",
    "}": "è",
    "~": "û"
  };
  CHARSETS["K"] = {
    "@": "§",
    "[": "Ä",
    "\\": "Ö",
    "]": "Ü",
    "{": "ä",
    "|": "ö",
    "}": "ü",
    "~": "ß"
  };
  CHARSETS["Y"] = {
    "#": "£",
    "@": "§",
    "[": "°",
    "\\": "ç",
    "]": "é",
    "`": "ù",
    "{": "à",
    "|": "ò",
    "}": "è",
    "~": "ì"
  };
  CHARSETS["E"] = CHARSETS["6"] = {
    "@": "Ä",
    "[": "Æ",
    "\\": "Ø",
    "]": "Å",
    "^": "Ü",
    "`": "ä",
    "{": "æ",
    "|": "ø",
    "}": "å",
    "~": "ü"
  };
  CHARSETS["Z"] = {
    "#": "£",
    "@": "§",
    "[": "¡",
    "\\": "Ñ",
    "]": "¿",
    "{": "°",
    "|": "ñ",
    "}": "ç"
  };
  CHARSETS["H"] = CHARSETS["7"] = {
    "@": "É",
    "[": "Ä",
    "\\": "Ö",
    "]": "Å",
    "^": "Ü",
    "`": "é",
    "{": "ä",
    "|": "ö",
    "}": "å",
    "~": "ü"
  };
  CHARSETS["="] = {
    "#": "ù",
    "@": "à",
    "[": "é",
    "\\": "ç",
    "]": "ê",
    "^": "î",
    // eslint-disable-next-line @typescript-eslint/naming-convention
    "_": "è",
    "`": "ô",
    "{": "ä",
    "|": "ö",
    "}": "ü",
    "~": "û"
  };

  // node_modules/@xterm/xterm/src/common/buffer/Buffer.ts
  var MAX_BUFFER_SIZE = 4294967295;
  var Buffer2 = class {
    constructor(_hasScrollback, _optionsService, _bufferService) {
      this._hasScrollback = _hasScrollback;
      this._optionsService = _optionsService;
      this._bufferService = _bufferService;
      this.ydisp = 0;
      this.ybase = 0;
      this.y = 0;
      this.x = 0;
      this.tabs = {};
      this.savedY = 0;
      this.savedX = 0;
      this.savedCurAttrData = DEFAULT_ATTR_DATA.clone();
      this.savedCharset = DEFAULT_CHARSET;
      this.markers = [];
      this._nullCell = CellData.fromCharData([0, NULL_CELL_CHAR, NULL_CELL_WIDTH, NULL_CELL_CODE]);
      this._whitespaceCell = CellData.fromCharData([0, WHITESPACE_CELL_CHAR, WHITESPACE_CELL_WIDTH, WHITESPACE_CELL_CODE]);
      this._isClearing = false;
      this._memoryCleanupQueue = new IdleTaskQueue();
      this._memoryCleanupPosition = 0;
      this._cols = this._bufferService.cols;
      this._rows = this._bufferService.rows;
      this.lines = new CircularList(this._getCorrectBufferLength(this._rows));
      this.scrollTop = 0;
      this.scrollBottom = this._rows - 1;
      this.setupTabStops();
    }
    getNullCell(attr) {
      if (attr) {
        this._nullCell.fg = attr.fg;
        this._nullCell.bg = attr.bg;
        this._nullCell.extended = attr.extended;
      } else {
        this._nullCell.fg = 0;
        this._nullCell.bg = 0;
        this._nullCell.extended = new ExtendedAttrs();
      }
      return this._nullCell;
    }
    getWhitespaceCell(attr) {
      if (attr) {
        this._whitespaceCell.fg = attr.fg;
        this._whitespaceCell.bg = attr.bg;
        this._whitespaceCell.extended = attr.extended;
      } else {
        this._whitespaceCell.fg = 0;
        this._whitespaceCell.bg = 0;
        this._whitespaceCell.extended = new ExtendedAttrs();
      }
      return this._whitespaceCell;
    }
    getBlankLine(attr, isWrapped) {
      return new BufferLine(this._bufferService.cols, this.getNullCell(attr), isWrapped);
    }
    get hasScrollback() {
      return this._hasScrollback && this.lines.maxLength > this._rows;
    }
    get isCursorInViewport() {
      const absoluteY = this.ybase + this.y;
      const relativeY = absoluteY - this.ydisp;
      return relativeY >= 0 && relativeY < this._rows;
    }
    /**
     * Gets the correct buffer length based on the rows provided, the terminal's
     * scrollback and whether this buffer is flagged to have scrollback or not.
     * @param rows The terminal rows to use in the calculation.
     */
    _getCorrectBufferLength(rows) {
      if (!this._hasScrollback) {
        return rows;
      }
      const correctBufferLength = rows + this._optionsService.rawOptions.scrollback;
      return correctBufferLength > MAX_BUFFER_SIZE ? MAX_BUFFER_SIZE : correctBufferLength;
    }
    /**
     * Fills the buffer's viewport with blank lines.
     */
    fillViewportRows(fillAttr) {
      if (this.lines.length === 0) {
        if (fillAttr === void 0) {
          fillAttr = DEFAULT_ATTR_DATA;
        }
        let i2 = this._rows;
        while (i2--) {
          this.lines.push(this.getBlankLine(fillAttr));
        }
      }
    }
    /**
     * Clears the buffer to it's initial state, discarding all previous data.
     */
    clear() {
      this.ydisp = 0;
      this.ybase = 0;
      this.y = 0;
      this.x = 0;
      this.lines = new CircularList(this._getCorrectBufferLength(this._rows));
      this.scrollTop = 0;
      this.scrollBottom = this._rows - 1;
      this.setupTabStops();
    }
    /**
     * Resizes the buffer, adjusting its data accordingly.
     * @param newCols The new number of columns.
     * @param newRows The new number of rows.
     */
    resize(newCols, newRows) {
      const nullCell = this.getNullCell(DEFAULT_ATTR_DATA);
      let dirtyMemoryLines = 0;
      const newMaxLength = this._getCorrectBufferLength(newRows);
      if (newMaxLength > this.lines.maxLength) {
        this.lines.maxLength = newMaxLength;
      }
      if (this.lines.length > 0) {
        if (this._cols < newCols) {
          for (let i2 = 0; i2 < this.lines.length; i2++) {
            dirtyMemoryLines += +this.lines.get(i2).resize(newCols, nullCell);
          }
        }
        let addToY = 0;
        if (this._rows < newRows) {
          for (let y = this._rows; y < newRows; y++) {
            if (this.lines.length < newRows + this.ybase) {
              if (this._optionsService.rawOptions.windowsMode || this._optionsService.rawOptions.windowsPty.backend !== void 0 || this._optionsService.rawOptions.windowsPty.buildNumber !== void 0) {
                this.lines.push(new BufferLine(newCols, nullCell));
              } else {
                if (this.ybase > 0 && this.lines.length <= this.ybase + this.y + addToY + 1) {
                  this.ybase--;
                  addToY++;
                  if (this.ydisp > 0) {
                    this.ydisp--;
                  }
                } else {
                  this.lines.push(new BufferLine(newCols, nullCell));
                }
              }
            }
          }
        } else {
          for (let y = this._rows; y > newRows; y--) {
            if (this.lines.length > newRows + this.ybase) {
              if (this.lines.length > this.ybase + this.y + 1) {
                this.lines.pop();
              } else {
                this.ybase++;
                this.ydisp++;
              }
            }
          }
        }
        if (newMaxLength < this.lines.maxLength) {
          const amountToTrim = this.lines.length - newMaxLength;
          if (amountToTrim > 0) {
            this.lines.trimStart(amountToTrim);
            this.ybase = Math.max(this.ybase - amountToTrim, 0);
            this.ydisp = Math.max(this.ydisp - amountToTrim, 0);
            this.savedY = Math.max(this.savedY - amountToTrim, 0);
          }
          this.lines.maxLength = newMaxLength;
        }
        this.x = Math.min(this.x, newCols - 1);
        this.y = Math.min(this.y, newRows - 1);
        if (addToY) {
          this.y += addToY;
        }
        this.savedX = Math.min(this.savedX, newCols - 1);
        this.scrollTop = 0;
      }
      this.scrollBottom = newRows - 1;
      if (this._isReflowEnabled) {
        this._reflow(newCols, newRows);
        if (this._cols > newCols) {
          for (let i2 = 0; i2 < this.lines.length; i2++) {
            dirtyMemoryLines += +this.lines.get(i2).resize(newCols, nullCell);
          }
        }
      }
      this._cols = newCols;
      this._rows = newRows;
      this._memoryCleanupQueue.clear();
      if (dirtyMemoryLines > 0.1 * this.lines.length) {
        this._memoryCleanupPosition = 0;
        this._memoryCleanupQueue.enqueue(() => this._batchedMemoryCleanup());
      }
    }
    _batchedMemoryCleanup() {
      let normalRun = true;
      if (this._memoryCleanupPosition >= this.lines.length) {
        this._memoryCleanupPosition = 0;
        normalRun = false;
      }
      let counted = 0;
      while (this._memoryCleanupPosition < this.lines.length) {
        counted += this.lines.get(this._memoryCleanupPosition++).cleanupMemory();
        if (counted > 100) {
          return true;
        }
      }
      return normalRun;
    }
    get _isReflowEnabled() {
      const windowsPty = this._optionsService.rawOptions.windowsPty;
      if (windowsPty && windowsPty.buildNumber) {
        return this._hasScrollback && windowsPty.backend === "conpty" && windowsPty.buildNumber >= 21376;
      }
      return this._hasScrollback && !this._optionsService.rawOptions.windowsMode;
    }
    _reflow(newCols, newRows) {
      if (this._cols === newCols) {
        return;
      }
      if (newCols > this._cols) {
        this._reflowLarger(newCols, newRows);
      } else {
        this._reflowSmaller(newCols, newRows);
      }
    }
    _reflowLarger(newCols, newRows) {
      const reflowCursorLine = this._optionsService.rawOptions.reflowCursorLine;
      const toRemove = reflowLargerGetLinesToRemove(this.lines, this._cols, newCols, this.ybase + this.y, this.getNullCell(DEFAULT_ATTR_DATA), reflowCursorLine);
      if (toRemove.length > 0) {
        const newLayoutResult = reflowLargerCreateNewLayout(this.lines, toRemove);
        reflowLargerApplyNewLayout(this.lines, newLayoutResult.layout);
        this._reflowLargerAdjustViewport(newCols, newRows, newLayoutResult.countRemoved);
      }
    }
    _reflowLargerAdjustViewport(newCols, newRows, countRemoved) {
      const nullCell = this.getNullCell(DEFAULT_ATTR_DATA);
      let viewportAdjustments = countRemoved;
      while (viewportAdjustments-- > 0) {
        if (this.ybase === 0) {
          if (this.y > 0) {
            this.y--;
          }
          if (this.lines.length < newRows) {
            this.lines.push(new BufferLine(newCols, nullCell));
          }
        } else {
          if (this.ydisp === this.ybase) {
            this.ydisp--;
          }
          this.ybase--;
        }
      }
      this.savedY = Math.max(this.savedY - countRemoved, 0);
    }
    _reflowSmaller(newCols, newRows) {
      const reflowCursorLine = this._optionsService.rawOptions.reflowCursorLine;
      const nullCell = this.getNullCell(DEFAULT_ATTR_DATA);
      const toInsert = [];
      let countToInsert = 0;
      for (let y = this.lines.length - 1; y >= 0; y--) {
        let nextLine = this.lines.get(y);
        if (!nextLine || !nextLine.isWrapped && nextLine.getTrimmedLength() <= newCols) {
          continue;
        }
        const wrappedLines = [nextLine];
        while (nextLine.isWrapped && y > 0) {
          nextLine = this.lines.get(--y);
          wrappedLines.unshift(nextLine);
        }
        if (!reflowCursorLine) {
          const absoluteY = this.ybase + this.y;
          if (absoluteY >= y && absoluteY < y + wrappedLines.length) {
            continue;
          }
        }
        const lastLineLength = wrappedLines[wrappedLines.length - 1].getTrimmedLength();
        const destLineLengths = reflowSmallerGetNewLineLengths(wrappedLines, this._cols, newCols);
        const linesToAdd = destLineLengths.length - wrappedLines.length;
        let trimmedLines;
        if (this.ybase === 0 && this.y !== this.lines.length - 1) {
          trimmedLines = Math.max(0, this.y - this.lines.maxLength + linesToAdd);
        } else {
          trimmedLines = Math.max(0, this.lines.length - this.lines.maxLength + linesToAdd);
        }
        const newLines = [];
        for (let i2 = 0; i2 < linesToAdd; i2++) {
          const newLine = this.getBlankLine(DEFAULT_ATTR_DATA, true);
          newLines.push(newLine);
        }
        if (newLines.length > 0) {
          toInsert.push({
            // countToInsert here gets the actual index, taking into account other inserted items.
            // using this we can iterate through the list forwards
            start: y + wrappedLines.length + countToInsert,
            newLines
          });
          countToInsert += newLines.length;
        }
        wrappedLines.push(...newLines);
        let destLineIndex = destLineLengths.length - 1;
        let destCol = destLineLengths[destLineIndex];
        if (destCol === 0) {
          destLineIndex--;
          destCol = destLineLengths[destLineIndex];
        }
        let srcLineIndex = wrappedLines.length - linesToAdd - 1;
        let srcCol = lastLineLength;
        while (srcLineIndex >= 0) {
          const cellsToCopy = Math.min(srcCol, destCol);
          if (wrappedLines[destLineIndex] === void 0) {
            break;
          }
          wrappedLines[destLineIndex].copyCellsFrom(wrappedLines[srcLineIndex], srcCol - cellsToCopy, destCol - cellsToCopy, cellsToCopy, true);
          destCol -= cellsToCopy;
          if (destCol === 0) {
            destLineIndex--;
            destCol = destLineLengths[destLineIndex];
          }
          srcCol -= cellsToCopy;
          if (srcCol === 0) {
            srcLineIndex--;
            const wrappedLinesIndex = Math.max(srcLineIndex, 0);
            srcCol = getWrappedLineTrimmedLength(wrappedLines, wrappedLinesIndex, this._cols);
          }
        }
        for (let i2 = 0; i2 < wrappedLines.length; i2++) {
          if (destLineLengths[i2] < newCols) {
            wrappedLines[i2].setCell(destLineLengths[i2], nullCell);
          }
        }
        let viewportAdjustments = linesToAdd - trimmedLines;
        while (viewportAdjustments-- > 0) {
          if (this.ybase === 0) {
            if (this.y < newRows - 1) {
              this.y++;
              this.lines.pop();
            } else {
              this.ybase++;
              this.ydisp++;
            }
          } else {
            if (this.ybase < Math.min(this.lines.maxLength, this.lines.length + countToInsert) - newRows) {
              if (this.ybase === this.ydisp) {
                this.ydisp++;
              }
              this.ybase++;
            }
          }
        }
        this.savedY = Math.min(this.savedY + linesToAdd, this.ybase + newRows - 1);
      }
      if (toInsert.length > 0) {
        const insertEvents = [];
        const originalLines = [];
        for (let i2 = 0; i2 < this.lines.length; i2++) {
          originalLines.push(this.lines.get(i2));
        }
        const originalLinesLength = this.lines.length;
        let originalLineIndex = originalLinesLength - 1;
        let nextToInsertIndex = 0;
        let nextToInsert = toInsert[nextToInsertIndex];
        this.lines.length = Math.min(this.lines.maxLength, this.lines.length + countToInsert);
        let countInsertedSoFar = 0;
        for (let i2 = Math.min(this.lines.maxLength - 1, originalLinesLength + countToInsert - 1); i2 >= 0; i2--) {
          if (nextToInsert && nextToInsert.start > originalLineIndex + countInsertedSoFar) {
            for (let nextI = nextToInsert.newLines.length - 1; nextI >= 0; nextI--) {
              this.lines.set(i2--, nextToInsert.newLines[nextI]);
            }
            i2++;
            insertEvents.push({
              index: originalLineIndex + 1,
              amount: nextToInsert.newLines.length
            });
            countInsertedSoFar += nextToInsert.newLines.length;
            nextToInsert = toInsert[++nextToInsertIndex];
          } else {
            this.lines.set(i2, originalLines[originalLineIndex--]);
          }
        }
        let insertCountEmitted = 0;
        for (let i2 = insertEvents.length - 1; i2 >= 0; i2--) {
          insertEvents[i2].index += insertCountEmitted;
          this.lines.onInsertEmitter.fire(insertEvents[i2]);
          insertCountEmitted += insertEvents[i2].amount;
        }
        const amountToTrim = Math.max(0, originalLinesLength + countToInsert - this.lines.maxLength);
        if (amountToTrim > 0) {
          this.lines.onTrimEmitter.fire(amountToTrim);
        }
      }
    }
    /**
     * Translates a buffer line to a string, with optional start and end columns.
     * Wide characters will count as two columns in the resulting string. This
     * function is useful for getting the actual text underneath the raw selection
     * position.
     * @param lineIndex The absolute index of the line being translated.
     * @param trimRight Whether to trim whitespace to the right.
     * @param startCol The column to start at.
     * @param endCol The column to end at.
     */
    translateBufferLineToString(lineIndex, trimRight, startCol = 0, endCol) {
      const line = this.lines.get(lineIndex);
      if (!line) {
        return "";
      }
      return line.translateToString(trimRight, startCol, endCol);
    }
    getWrappedRangeForLine(y) {
      let first = y;
      let last = y;
      while (first > 0 && this.lines.get(first).isWrapped) {
        first--;
      }
      while (last + 1 < this.lines.length && this.lines.get(last + 1).isWrapped) {
        last++;
      }
      return { first, last };
    }
    /**
     * Setup the tab stops.
     * @param i The index to start setting up tab stops from.
     */
    setupTabStops(i2) {
      if (i2 !== null && i2 !== void 0) {
        if (!this.tabs[i2]) {
          i2 = this.prevStop(i2);
        }
      } else {
        this.tabs = {};
        i2 = 0;
      }
      for (; i2 < this._cols; i2 += this._optionsService.rawOptions.tabStopWidth) {
        this.tabs[i2] = true;
      }
    }
    /**
     * Move the cursor to the previous tab stop from the given position (default is current).
     * @param x The position to move the cursor to the previous tab stop.
     */
    prevStop(x) {
      if (x === null || x === void 0) {
        x = this.x;
      }
      while (!this.tabs[--x] && x > 0) ;
      return x >= this._cols ? this._cols - 1 : x < 0 ? 0 : x;
    }
    /**
     * Move the cursor one tab stop forward from the given position (default is current).
     * @param x The position to move the cursor one tab stop forward.
     */
    nextStop(x) {
      if (x === null || x === void 0) {
        x = this.x;
      }
      while (!this.tabs[++x] && x < this._cols) ;
      return x >= this._cols ? this._cols - 1 : x < 0 ? 0 : x;
    }
    /**
     * Clears markers on single line.
     * @param y The line to clear.
     */
    clearMarkers(y) {
      this._isClearing = true;
      for (let i2 = 0; i2 < this.markers.length; i2++) {
        if (this.markers[i2].line === y) {
          this.markers[i2].dispose();
          this.markers.splice(i2--, 1);
        }
      }
      this._isClearing = false;
    }
    /**
     * Clears markers on all lines
     */
    clearAllMarkers() {
      this._isClearing = true;
      for (let i2 = 0; i2 < this.markers.length; i2++) {
        this.markers[i2].dispose();
      }
      this.markers.length = 0;
      this._isClearing = false;
    }
    addMarker(y) {
      const marker = new Marker(y);
      this.markers.push(marker);
      marker.register(this.lines.onTrim((amount) => {
        marker.line -= amount;
        if (marker.line < 0) {
          marker.dispose();
        }
      }));
      marker.register(this.lines.onInsert((event) => {
        if (marker.line >= event.index) {
          marker.line += event.amount;
        }
      }));
      marker.register(this.lines.onDelete((event) => {
        if (marker.line >= event.index && marker.line < event.index + event.amount) {
          marker.dispose();
        }
        if (marker.line > event.index) {
          marker.line -= event.amount;
        }
      }));
      marker.register(marker.onDispose(() => this._removeMarker(marker)));
      return marker;
    }
    _removeMarker(marker) {
      if (!this._isClearing) {
        this.markers.splice(this.markers.indexOf(marker), 1);
      }
    }
  };

  // node_modules/@xterm/xterm/src/common/buffer/BufferSet.ts
  var BufferSet = class extends Disposable {
    /**
     * Create a new BufferSet for the given terminal.
     */
    constructor(_optionsService, _bufferService) {
      super();
      this._optionsService = _optionsService;
      this._bufferService = _bufferService;
      this._onBufferActivate = this._register(new Emitter());
      this.onBufferActivate = this._onBufferActivate.event;
      this.reset();
      this._register(this._optionsService.onSpecificOptionChange("scrollback", () => this.resize(this._bufferService.cols, this._bufferService.rows)));
      this._register(this._optionsService.onSpecificOptionChange("tabStopWidth", () => this.setupTabStops()));
    }
    reset() {
      this._normal = new Buffer2(true, this._optionsService, this._bufferService);
      this._normal.fillViewportRows();
      this._alt = new Buffer2(false, this._optionsService, this._bufferService);
      this._activeBuffer = this._normal;
      this._onBufferActivate.fire({
        activeBuffer: this._normal,
        inactiveBuffer: this._alt
      });
      this.setupTabStops();
    }
    /**
     * Returns the alt Buffer of the BufferSet
     */
    get alt() {
      return this._alt;
    }
    /**
     * Returns the currently active Buffer of the BufferSet
     */
    get active() {
      return this._activeBuffer;
    }
    /**
     * Returns the normal Buffer of the BufferSet
     */
    get normal() {
      return this._normal;
    }
    /**
     * Sets the normal Buffer of the BufferSet as its currently active Buffer
     */
    activateNormalBuffer() {
      if (this._activeBuffer === this._normal) {
        return;
      }
      this._normal.x = this._alt.x;
      this._normal.y = this._alt.y;
      this._alt.clearAllMarkers();
      this._alt.clear();
      this._activeBuffer = this._normal;
      this._onBufferActivate.fire({
        activeBuffer: this._normal,
        inactiveBuffer: this._alt
      });
    }
    /**
     * Sets the alt Buffer of the BufferSet as its currently active Buffer
     */
    activateAltBuffer(fillAttr) {
      if (this._activeBuffer === this._alt) {
        return;
      }
      this._alt.fillViewportRows(fillAttr);
      this._alt.x = this._normal.x;
      this._alt.y = this._normal.y;
      this._activeBuffer = this._alt;
      this._onBufferActivate.fire({
        activeBuffer: this._alt,
        inactiveBuffer: this._normal
      });
    }
    /**
     * Resizes both normal and alt buffers, adjusting their data accordingly.
     * @param newCols The new number of columns.
     * @param newRows The new number of rows.
     */
    resize(newCols, newRows) {
      this._normal.resize(newCols, newRows);
      this._alt.resize(newCols, newRows);
      this.setupTabStops(newCols);
    }
    /**
     * Setup the tab stops.
     * @param i The index to start setting up tab stops from.
     */
    setupTabStops(i2) {
      this._normal.setupTabStops(i2);
      this._alt.setupTabStops(i2);
    }
  };

  // node_modules/@xterm/xterm/src/common/services/BufferService.ts
  var MINIMUM_COLS = 2;
  var MINIMUM_ROWS = 1;
  var BufferService = class extends Disposable {
    constructor(optionsService) {
      super();
      /** Whether the user is scrolling (locks the scroll position) */
      this.isUserScrolling = false;
      this._onResize = this._register(new Emitter());
      this.onResize = this._onResize.event;
      this._onScroll = this._register(new Emitter());
      this.onScroll = this._onScroll.event;
      this.cols = Math.max(optionsService.rawOptions.cols || 0, MINIMUM_COLS);
      this.rows = Math.max(optionsService.rawOptions.rows || 0, MINIMUM_ROWS);
      this.buffers = this._register(new BufferSet(optionsService, this));
      this._register(this.buffers.onBufferActivate((e) => {
        this._onScroll.fire(e.activeBuffer.ydisp);
      }));
    }
    get buffer() {
      return this.buffers.active;
    }
    resize(cols, rows) {
      const colsChanged = this.cols !== cols;
      const rowsChanged = this.rows !== rows;
      this.cols = cols;
      this.rows = rows;
      this.buffers.resize(cols, rows);
      this._onResize.fire({ cols, rows, colsChanged, rowsChanged });
    }
    reset() {
      this.buffers.reset();
      this.isUserScrolling = false;
    }
    /**
     * Scroll the terminal down 1 row, creating a blank line.
     * @param eraseAttr The attribute data to use the for blank line.
     * @param isWrapped Whether the new line is wrapped from the previous line.
     */
    scroll(eraseAttr, isWrapped = false) {
      const buffer = this.buffer;
      let newLine;
      newLine = this._cachedBlankLine;
      if (!newLine || newLine.length !== this.cols || newLine.getFg(0) !== eraseAttr.fg || newLine.getBg(0) !== eraseAttr.bg) {
        newLine = buffer.getBlankLine(eraseAttr, isWrapped);
        this._cachedBlankLine = newLine;
      }
      newLine.isWrapped = isWrapped;
      const topRow = buffer.ybase + buffer.scrollTop;
      const bottomRow = buffer.ybase + buffer.scrollBottom;
      if (buffer.scrollTop === 0) {
        const willBufferBeTrimmed = buffer.lines.isFull;
        if (bottomRow === buffer.lines.length - 1) {
          if (willBufferBeTrimmed) {
            buffer.lines.recycle().copyFrom(newLine);
          } else {
            buffer.lines.push(newLine.clone());
          }
        } else {
          buffer.lines.splice(bottomRow + 1, 0, newLine.clone());
        }
        if (!willBufferBeTrimmed) {
          buffer.ybase++;
          if (!this.isUserScrolling) {
            buffer.ydisp++;
          }
        } else {
          if (this.isUserScrolling) {
            buffer.ydisp = Math.max(buffer.ydisp - 1, 0);
          }
        }
      } else {
        const scrollRegionHeight = bottomRow - topRow + 1;
        buffer.lines.shiftElements(topRow + 1, scrollRegionHeight - 1, -1);
        buffer.lines.set(bottomRow, newLine.clone());
      }
      if (!this.isUserScrolling) {
        buffer.ydisp = buffer.ybase;
      }
      this._onScroll.fire(buffer.ydisp);
    }
    /**
     * Scroll the display of the terminal
     * @param disp The number of lines to scroll down (negative scroll up).
     * @param suppressScrollEvent Don't emit the scroll event as scrollLines. This is used
     * to avoid unwanted events being handled by the viewport when the event was triggered from the
     * viewport originally.
     */
    scrollLines(disp, suppressScrollEvent) {
      const buffer = this.buffer;
      if (disp < 0) {
        if (buffer.ydisp === 0) {
          return;
        }
        this.isUserScrolling = true;
      } else if (disp + buffer.ydisp >= buffer.ybase) {
        this.isUserScrolling = false;
      }
      const oldYdisp = buffer.ydisp;
      buffer.ydisp = Math.max(Math.min(buffer.ydisp + disp, buffer.ybase), 0);
      if (oldYdisp === buffer.ydisp) {
        return;
      }
      if (!suppressScrollEvent) {
        this._onScroll.fire(buffer.ydisp);
      }
    }
  };
  BufferService = __decorateClass([
    __decorateParam(0, IOptionsService)
  ], BufferService);

  // node_modules/@xterm/xterm/src/common/services/OptionsService.ts
  var DEFAULT_OPTIONS = {
    cols: 80,
    rows: 24,
    cursorBlink: false,
    cursorStyle: "block",
    cursorWidth: 1,
    cursorInactiveStyle: "outline",
    customGlyphs: true,
    drawBoldTextInBrightColors: true,
    documentOverride: null,
    fastScrollModifier: "alt",
    fastScrollSensitivity: 5,
    fontFamily: "monospace",
    fontSize: 15,
    fontWeight: "normal",
    fontWeightBold: "bold",
    ignoreBracketedPasteMode: false,
    lineHeight: 1,
    letterSpacing: 0,
    linkHandler: null,
    logLevel: "info",
    logger: null,
    scrollback: 1e3,
    scrollOnEraseInDisplay: false,
    scrollOnUserInput: true,
    scrollSensitivity: 1,
    screenReaderMode: false,
    smoothScrollDuration: 0,
    macOptionIsMeta: false,
    macOptionClickForcesSelection: false,
    minimumContrastRatio: 1,
    disableStdin: false,
    allowProposedApi: false,
    allowTransparency: false,
    tabStopWidth: 8,
    theme: {},
    reflowCursorLine: false,
    rescaleOverlappingGlyphs: false,
    rightClickSelectsWord: isMac,
    windowOptions: {},
    windowsMode: false,
    windowsPty: {},
    wordSeparator: " ()[]{}',\"`",
    altClickMovesCursor: true,
    convertEol: false,
    termName: "xterm",
    cancelEvents: false,
    overviewRuler: {}
  };
  var FONT_WEIGHT_OPTIONS = ["normal", "bold", "100", "200", "300", "400", "500", "600", "700", "800", "900"];
  var OptionsService = class extends Disposable {
    constructor(options) {
      super();
      this._onOptionChange = this._register(new Emitter());
      this.onOptionChange = this._onOptionChange.event;
      const defaultOptions = { ...DEFAULT_OPTIONS };
      for (const key in options) {
        if (key in defaultOptions) {
          try {
            const newValue = options[key];
            defaultOptions[key] = this._sanitizeAndValidateOption(key, newValue);
          } catch (e) {
            console.error(e);
          }
        }
      }
      this.rawOptions = defaultOptions;
      this.options = { ...defaultOptions };
      this._setupOptions();
      this._register(toDisposable(() => {
        this.rawOptions.linkHandler = null;
        this.rawOptions.documentOverride = null;
      }));
    }
    // eslint-disable-next-line @typescript-eslint/naming-convention
    onSpecificOptionChange(key, listener) {
      return this.onOptionChange((eventKey) => {
        if (eventKey === key) {
          listener(this.rawOptions[key]);
        }
      });
    }
    // eslint-disable-next-line @typescript-eslint/naming-convention
    onMultipleOptionChange(keys, listener) {
      return this.onOptionChange((eventKey) => {
        if (keys.indexOf(eventKey) !== -1) {
          listener();
        }
      });
    }
    _setupOptions() {
      const getter = (propName) => {
        if (!(propName in DEFAULT_OPTIONS)) {
          throw new Error(`No option with key "${propName}"`);
        }
        return this.rawOptions[propName];
      };
      const setter = (propName, value) => {
        if (!(propName in DEFAULT_OPTIONS)) {
          throw new Error(`No option with key "${propName}"`);
        }
        value = this._sanitizeAndValidateOption(propName, value);
        if (this.rawOptions[propName] !== value) {
          this.rawOptions[propName] = value;
          this._onOptionChange.fire(propName);
        }
      };
      for (const propName in this.rawOptions) {
        const desc = {
          get: getter.bind(this, propName),
          set: setter.bind(this, propName)
        };
        Object.defineProperty(this.options, propName, desc);
      }
    }
    _sanitizeAndValidateOption(key, value) {
      switch (key) {
        case "cursorStyle":
          if (!value) {
            value = DEFAULT_OPTIONS[key];
          }
          if (!isCursorStyle(value)) {
            throw new Error(`"${value}" is not a valid value for ${key}`);
          }
          break;
        case "wordSeparator":
          if (!value) {
            value = DEFAULT_OPTIONS[key];
          }
          break;
        case "fontWeight":
        case "fontWeightBold":
          if (typeof value === "number" && 1 <= value && value <= 1e3) {
            break;
          }
          value = FONT_WEIGHT_OPTIONS.includes(value) ? value : DEFAULT_OPTIONS[key];
          break;
        case "cursorWidth":
          value = Math.floor(value);
        // Fall through for bounds check
        case "lineHeight":
        case "tabStopWidth":
          if (value < 1) {
            throw new Error(`${key} cannot be less than 1, value: ${value}`);
          }
          break;
        case "minimumContrastRatio":
          value = Math.max(1, Math.min(21, Math.round(value * 10) / 10));
          break;
        case "scrollback":
          value = Math.min(value, 4294967295);
          if (value < 0) {
            throw new Error(`${key} cannot be less than 0, value: ${value}`);
          }
          break;
        case "fastScrollSensitivity":
        case "scrollSensitivity":
          if (value <= 0) {
            throw new Error(`${key} cannot be less than or equal to 0, value: ${value}`);
          }
          break;
        case "rows":
        case "cols":
          if (!value && value !== 0) {
            throw new Error(`${key} must be numeric, value: ${value}`);
          }
          break;
        case "windowsPty":
          value = value ?? {};
          break;
      }
      return value;
    }
  };
  function isCursorStyle(value) {
    return value === "block" || value === "underline" || value === "bar";
  }

  // node_modules/@xterm/xterm/src/common/Clone.ts
  function clone(val, depth = 5) {
    if (typeof val !== "object") {
      return val;
    }
    const clonedObject = Array.isArray(val) ? [] : {};
    for (const key in val) {
      clonedObject[key] = depth <= 1 ? val[key] : val[key] && clone(val[key], depth - 1);
    }
    return clonedObject;
  }

  // node_modules/@xterm/xterm/src/common/services/CoreService.ts
  var DEFAULT_MODES = Object.freeze({
    insertMode: false
  });
  var DEFAULT_DEC_PRIVATE_MODES = Object.freeze({
    applicationCursorKeys: false,
    applicationKeypad: false,
    bracketedPasteMode: false,
    cursorBlink: void 0,
    cursorStyle: void 0,
    origin: false,
    reverseWraparound: false,
    sendFocus: false,
    synchronizedOutput: false,
    wraparound: true
    // defaults: xterm - true, vt100 - false
  });
  var CoreService = class extends Disposable {
    constructor(_bufferService, _logService, _optionsService) {
      super();
      this._bufferService = _bufferService;
      this._logService = _logService;
      this._optionsService = _optionsService;
      this.isCursorInitialized = false;
      this.isCursorHidden = false;
      this._onData = this._register(new Emitter());
      this.onData = this._onData.event;
      this._onUserInput = this._register(new Emitter());
      this.onUserInput = this._onUserInput.event;
      this._onBinary = this._register(new Emitter());
      this.onBinary = this._onBinary.event;
      this._onRequestScrollToBottom = this._register(new Emitter());
      this.onRequestScrollToBottom = this._onRequestScrollToBottom.event;
      this.modes = clone(DEFAULT_MODES);
      this.decPrivateModes = clone(DEFAULT_DEC_PRIVATE_MODES);
    }
    reset() {
      this.modes = clone(DEFAULT_MODES);
      this.decPrivateModes = clone(DEFAULT_DEC_PRIVATE_MODES);
    }
    triggerDataEvent(data, wasUserInput = false) {
      if (this._optionsService.rawOptions.disableStdin) {
        return;
      }
      const buffer = this._bufferService.buffer;
      if (wasUserInput && this._optionsService.rawOptions.scrollOnUserInput && buffer.ybase !== buffer.ydisp) {
        this._onRequestScrollToBottom.fire();
      }
      if (wasUserInput) {
        this._onUserInput.fire();
      }
      this._logService.debug(`sending data "${data}"`);
      this._logService.trace(`sending data (codes)`, () => data.split("").map((e) => e.charCodeAt(0)));
      this._onData.fire(data);
    }
    triggerBinaryEvent(data) {
      if (this._optionsService.rawOptions.disableStdin) {
        return;
      }
      this._logService.debug(`sending binary "${data}"`);
      this._logService.trace(`sending binary (codes)`, () => data.split("").map((e) => e.charCodeAt(0)));
      this._onBinary.fire(data);
    }
  };
  CoreService = __decorateClass([
    __decorateParam(0, IBufferService),
    __decorateParam(1, ILogService),
    __decorateParam(2, IOptionsService)
  ], CoreService);

  // node_modules/@xterm/xterm/src/common/services/CoreMouseService.ts
  var DEFAULT_PROTOCOLS = {
    /**
     * NONE
     * Events: none
     * Modifiers: none
     */
    NONE: {
      events: 0 /* NONE */,
      restrict: () => false
    },
    /**
     * X10
     * Events: mousedown
     * Modifiers: none
     */
    X10: {
      events: 1 /* DOWN */,
      restrict: (e) => {
        if (e.button === 4 /* WHEEL */ || e.action !== 1 /* DOWN */) {
          return false;
        }
        e.ctrl = false;
        e.alt = false;
        e.shift = false;
        return true;
      }
    },
    /**
     * VT200
     * Events: mousedown / mouseup / wheel
     * Modifiers: all
     */
    VT200: {
      events: 1 /* DOWN */ | 2 /* UP */ | 16 /* WHEEL */,
      restrict: (e) => {
        if (e.action === 32 /* MOVE */) {
          return false;
        }
        return true;
      }
    },
    /**
     * DRAG
     * Events: mousedown / mouseup / wheel / mousedrag
     * Modifiers: all
     */
    DRAG: {
      events: 1 /* DOWN */ | 2 /* UP */ | 16 /* WHEEL */ | 4 /* DRAG */,
      restrict: (e) => {
        if (e.action === 32 /* MOVE */ && e.button === 3 /* NONE */) {
          return false;
        }
        return true;
      }
    },
    /**
     * ANY
     * Events: all mouse related events
     * Modifiers: all
     */
    ANY: {
      events: 1 /* DOWN */ | 2 /* UP */ | 16 /* WHEEL */ | 4 /* DRAG */ | 8 /* MOVE */,
      restrict: (e) => true
    }
  };
  function eventCode(e, isSGR) {
    let code = (e.ctrl ? 16 /* CTRL */ : 0) | (e.shift ? 4 /* SHIFT */ : 0) | (e.alt ? 8 /* ALT */ : 0);
    if (e.button === 4 /* WHEEL */) {
      code |= 64;
      code |= e.action;
    } else {
      code |= e.button & 3;
      if (e.button & 4) {
        code |= 64;
      }
      if (e.button & 8) {
        code |= 128;
      }
      if (e.action === 32 /* MOVE */) {
        code |= 32 /* MOVE */;
      } else if (e.action === 0 /* UP */ && !isSGR) {
        code |= 3 /* NONE */;
      }
    }
    return code;
  }
  var S = String.fromCharCode;
  var DEFAULT_ENCODINGS = {
    /**
     * DEFAULT - CSI M Pb Px Py
     * Single byte encoding for coords and event code.
     * Can encode values up to 223 (1-based).
     */
    DEFAULT: (e) => {
      const params = [eventCode(e, false) + 32, e.col + 32, e.row + 32];
      if (params[0] > 255 || params[1] > 255 || params[2] > 255) {
        return "";
      }
      return `\x1B[M${S(params[0])}${S(params[1])}${S(params[2])}`;
    },
    /**
     * SGR - CSI < Pb ; Px ; Py M|m
     * No encoding limitation.
     * Can report button on release and works with a well formed sequence.
     */
    SGR: (e) => {
      const final = e.action === 0 /* UP */ && e.button !== 4 /* WHEEL */ ? "m" : "M";
      return `\x1B[<${eventCode(e, true)};${e.col};${e.row}${final}`;
    },
    SGR_PIXELS: (e) => {
      const final = e.action === 0 /* UP */ && e.button !== 4 /* WHEEL */ ? "m" : "M";
      return `\x1B[<${eventCode(e, true)};${e.x};${e.y}${final}`;
    }
  };
  var CoreMouseService = class extends Disposable {
    constructor(_bufferService, _coreService, _optionsService) {
      super();
      this._bufferService = _bufferService;
      this._coreService = _coreService;
      this._optionsService = _optionsService;
      this._protocols = {};
      this._encodings = {};
      this._activeProtocol = "";
      this._activeEncoding = "";
      this._lastEvent = null;
      this._wheelPartialScroll = 0;
      this._onProtocolChange = this._register(new Emitter());
      this.onProtocolChange = this._onProtocolChange.event;
      for (const name of Object.keys(DEFAULT_PROTOCOLS)) this.addProtocol(name, DEFAULT_PROTOCOLS[name]);
      for (const name of Object.keys(DEFAULT_ENCODINGS)) this.addEncoding(name, DEFAULT_ENCODINGS[name]);
      this.reset();
    }
    addProtocol(name, protocol) {
      this._protocols[name] = protocol;
    }
    addEncoding(name, encoding) {
      this._encodings[name] = encoding;
    }
    get activeProtocol() {
      return this._activeProtocol;
    }
    get areMouseEventsActive() {
      return this._protocols[this._activeProtocol].events !== 0;
    }
    set activeProtocol(name) {
      if (!this._protocols[name]) {
        throw new Error(`unknown protocol "${name}"`);
      }
      this._activeProtocol = name;
      this._onProtocolChange.fire(this._protocols[name].events);
    }
    get activeEncoding() {
      return this._activeEncoding;
    }
    set activeEncoding(name) {
      if (!this._encodings[name]) {
        throw new Error(`unknown encoding "${name}"`);
      }
      this._activeEncoding = name;
    }
    reset() {
      this.activeProtocol = "NONE";
      this.activeEncoding = "DEFAULT";
      this._lastEvent = null;
      this._wheelPartialScroll = 0;
    }
    /**
     * Processes a wheel event, accounting for partial scrolls for trackpad, mouse scrolls.
     * This prevents hyper-sensitive scrolling in alt buffer.
     */
    consumeWheelEvent(ev, cellHeight, dpr) {
      if (ev.deltaY === 0 || ev.shiftKey) {
        return 0;
      }
      if (cellHeight === void 0 || dpr === void 0) {
        return 0;
      }
      const targetWheelEventPixels = cellHeight / dpr;
      let amount = this._applyScrollModifier(ev.deltaY, ev);
      if (ev.deltaMode === WheelEvent.DOM_DELTA_PIXEL) {
        amount /= targetWheelEventPixels + 0;
        const isLikelyTrackpad = Math.abs(ev.deltaY) < 50;
        if (isLikelyTrackpad) {
          amount *= 0.3;
        }
        this._wheelPartialScroll += amount;
        amount = Math.floor(Math.abs(this._wheelPartialScroll)) * (this._wheelPartialScroll > 0 ? 1 : -1);
        this._wheelPartialScroll %= 1;
      } else if (ev.deltaMode === WheelEvent.DOM_DELTA_PAGE) {
        amount *= this._bufferService.rows;
      }
      return amount;
    }
    _applyScrollModifier(amount, ev) {
      if (ev.altKey || ev.ctrlKey || ev.shiftKey) {
        return amount * this._optionsService.rawOptions.fastScrollSensitivity * this._optionsService.rawOptions.scrollSensitivity;
      }
      return amount * this._optionsService.rawOptions.scrollSensitivity;
    }
    /**
     * Triggers a mouse event to be sent.
     *
     * Returns true if the event passed all protocol restrictions and a report
     * was sent, otherwise false. The return value may be used to decide whether
     * the default event action in the bowser component should be omitted.
     *
     * Note: The method will change values of the given event object
     * to fullfill protocol and encoding restrictions.
     */
    triggerMouseEvent(e) {
      if (e.col < 0 || e.col >= this._bufferService.cols || e.row < 0 || e.row >= this._bufferService.rows) {
        return false;
      }
      if (e.button === 4 /* WHEEL */ && e.action === 32 /* MOVE */) {
        return false;
      }
      if (e.button === 3 /* NONE */ && e.action !== 32 /* MOVE */) {
        return false;
      }
      if (e.button !== 4 /* WHEEL */ && (e.action === 2 /* LEFT */ || e.action === 3 /* RIGHT */)) {
        return false;
      }
      e.col++;
      e.row++;
      if (e.action === 32 /* MOVE */ && this._lastEvent && this._equalEvents(this._lastEvent, e, this._activeEncoding === "SGR_PIXELS")) {
        return false;
      }
      if (!this._protocols[this._activeProtocol].restrict(e)) {
        return false;
      }
      const report = this._encodings[this._activeEncoding](e);
      if (report) {
        if (this._activeEncoding === "DEFAULT") {
          this._coreService.triggerBinaryEvent(report);
        } else {
          this._coreService.triggerDataEvent(report, true);
        }
      }
      this._lastEvent = e;
      return true;
    }
    explainEvents(events) {
      return {
        down: !!(events & 1 /* DOWN */),
        up: !!(events & 2 /* UP */),
        drag: !!(events & 4 /* DRAG */),
        move: !!(events & 8 /* MOVE */),
        wheel: !!(events & 16 /* WHEEL */)
      };
    }
    _equalEvents(e1, e2, pixels) {
      if (pixels) {
        if (e1.x !== e2.x) return false;
        if (e1.y !== e2.y) return false;
      } else {
        if (e1.col !== e2.col) return false;
        if (e1.row !== e2.row) return false;
      }
      if (e1.button !== e2.button) return false;
      if (e1.action !== e2.action) return false;
      if (e1.ctrl !== e2.ctrl) return false;
      if (e1.alt !== e2.alt) return false;
      if (e1.shift !== e2.shift) return false;
      return true;
    }
  };
  CoreMouseService = __decorateClass([
    __decorateParam(0, IBufferService),
    __decorateParam(1, ICoreService),
    __decorateParam(2, IOptionsService)
  ], CoreMouseService);

  // node_modules/@xterm/xterm/src/common/input/UnicodeV6.ts
  var BMP_COMBINING = [
    [768, 879],
    [1155, 1158],
    [1160, 1161],
    [1425, 1469],
    [1471, 1471],
    [1473, 1474],
    [1476, 1477],
    [1479, 1479],
    [1536, 1539],
    [1552, 1557],
    [1611, 1630],
    [1648, 1648],
    [1750, 1764],
    [1767, 1768],
    [1770, 1773],
    [1807, 1807],
    [1809, 1809],
    [1840, 1866],
    [1958, 1968],
    [2027, 2035],
    [2305, 2306],
    [2364, 2364],
    [2369, 2376],
    [2381, 2381],
    [2385, 2388],
    [2402, 2403],
    [2433, 2433],
    [2492, 2492],
    [2497, 2500],
    [2509, 2509],
    [2530, 2531],
    [2561, 2562],
    [2620, 2620],
    [2625, 2626],
    [2631, 2632],
    [2635, 2637],
    [2672, 2673],
    [2689, 2690],
    [2748, 2748],
    [2753, 2757],
    [2759, 2760],
    [2765, 2765],
    [2786, 2787],
    [2817, 2817],
    [2876, 2876],
    [2879, 2879],
    [2881, 2883],
    [2893, 2893],
    [2902, 2902],
    [2946, 2946],
    [3008, 3008],
    [3021, 3021],
    [3134, 3136],
    [3142, 3144],
    [3146, 3149],
    [3157, 3158],
    [3260, 3260],
    [3263, 3263],
    [3270, 3270],
    [3276, 3277],
    [3298, 3299],
    [3393, 3395],
    [3405, 3405],
    [3530, 3530],
    [3538, 3540],
    [3542, 3542],
    [3633, 3633],
    [3636, 3642],
    [3655, 3662],
    [3761, 3761],
    [3764, 3769],
    [3771, 3772],
    [3784, 3789],
    [3864, 3865],
    [3893, 3893],
    [3895, 3895],
    [3897, 3897],
    [3953, 3966],
    [3968, 3972],
    [3974, 3975],
    [3984, 3991],
    [3993, 4028],
    [4038, 4038],
    [4141, 4144],
    [4146, 4146],
    [4150, 4151],
    [4153, 4153],
    [4184, 4185],
    [4448, 4607],
    [4959, 4959],
    [5906, 5908],
    [5938, 5940],
    [5970, 5971],
    [6002, 6003],
    [6068, 6069],
    [6071, 6077],
    [6086, 6086],
    [6089, 6099],
    [6109, 6109],
    [6155, 6157],
    [6313, 6313],
    [6432, 6434],
    [6439, 6440],
    [6450, 6450],
    [6457, 6459],
    [6679, 6680],
    [6912, 6915],
    [6964, 6964],
    [6966, 6970],
    [6972, 6972],
    [6978, 6978],
    [7019, 7027],
    [7616, 7626],
    [7678, 7679],
    [8203, 8207],
    [8234, 8238],
    [8288, 8291],
    [8298, 8303],
    [8400, 8431],
    [12330, 12335],
    [12441, 12442],
    [43014, 43014],
    [43019, 43019],
    [43045, 43046],
    [64286, 64286],
    [65024, 65039],
    [65056, 65059],
    [65279, 65279],
    [65529, 65531]
  ];
  var HIGH_COMBINING = [
    [68097, 68099],
    [68101, 68102],
    [68108, 68111],
    [68152, 68154],
    [68159, 68159],
    [119143, 119145],
    [119155, 119170],
    [119173, 119179],
    [119210, 119213],
    [119362, 119364],
    [917505, 917505],
    [917536, 917631],
    [917760, 917999]
  ];
  var table;
  function bisearch(ucs, data) {
    let min = 0;
    let max = data.length - 1;
    let mid;
    if (ucs < data[0][0] || ucs > data[max][1]) {
      return false;
    }
    while (max >= min) {
      mid = min + max >> 1;
      if (ucs > data[mid][1]) {
        min = mid + 1;
      } else if (ucs < data[mid][0]) {
        max = mid - 1;
      } else {
        return true;
      }
    }
    return false;
  }
  var UnicodeV6 = class {
    constructor() {
      this.version = "6";
      if (!table) {
        table = new Uint8Array(65536);
        table.fill(1);
        table[0] = 0;
        table.fill(0, 1, 32);
        table.fill(0, 127, 160);
        table.fill(2, 4352, 4448);
        table[9001] = 2;
        table[9002] = 2;
        table.fill(2, 11904, 42192);
        table[12351] = 1;
        table.fill(2, 44032, 55204);
        table.fill(2, 63744, 64256);
        table.fill(2, 65040, 65050);
        table.fill(2, 65072, 65136);
        table.fill(2, 65280, 65377);
        table.fill(2, 65504, 65511);
        for (let r = 0; r < BMP_COMBINING.length; ++r) {
          table.fill(0, BMP_COMBINING[r][0], BMP_COMBINING[r][1] + 1);
        }
      }
    }
    wcwidth(num) {
      if (num < 32) return 0;
      if (num < 127) return 1;
      if (num < 65536) return table[num];
      if (bisearch(num, HIGH_COMBINING)) return 0;
      if (num >= 131072 && num <= 196605 || num >= 196608 && num <= 262141) return 2;
      return 1;
    }
    charProperties(codepoint, preceding) {
      let width = this.wcwidth(codepoint);
      let shouldJoin = width === 0 && preceding !== 0;
      if (shouldJoin) {
        const oldWidth = UnicodeService.extractWidth(preceding);
        if (oldWidth === 0) {
          shouldJoin = false;
        } else if (oldWidth > width) {
          width = oldWidth;
        }
      }
      return UnicodeService.createPropertyValue(0, width, shouldJoin);
    }
  };

  // node_modules/@xterm/xterm/src/common/services/UnicodeService.ts
  var UnicodeService = class _UnicodeService {
    constructor() {
      this._providers = /* @__PURE__ */ Object.create(null);
      this._active = "";
      this._onChange = new Emitter();
      this.onChange = this._onChange.event;
      const defaultProvider = new UnicodeV6();
      this.register(defaultProvider);
      this._active = defaultProvider.version;
      this._activeProvider = defaultProvider;
    }
    static extractShouldJoin(value) {
      return (value & 1) !== 0;
    }
    static extractWidth(value) {
      return value >> 1 & 3;
    }
    static extractCharKind(value) {
      return value >> 3;
    }
    static createPropertyValue(state, width, shouldJoin = false) {
      return (state & 16777215) << 3 | (width & 3) << 1 | (shouldJoin ? 1 : 0);
    }
    dispose() {
      this._onChange.dispose();
    }
    get versions() {
      return Object.keys(this._providers);
    }
    get activeVersion() {
      return this._active;
    }
    set activeVersion(version) {
      if (!this._providers[version]) {
        throw new Error(`unknown Unicode version "${version}"`);
      }
      this._active = version;
      this._activeProvider = this._providers[version];
      this._onChange.fire(version);
    }
    register(provider) {
      this._providers[provider.version] = provider;
    }
    /**
     * Unicode version dependent interface.
     */
    wcwidth(num) {
      return this._activeProvider.wcwidth(num);
    }
    getStringCellWidth(s) {
      let result = 0;
      let precedingInfo = 0;
      const length = s.length;
      for (let i2 = 0; i2 < length; ++i2) {
        let code = s.charCodeAt(i2);
        if (55296 <= code && code <= 56319) {
          if (++i2 >= length) {
            return result + this.wcwidth(code);
          }
          const second = s.charCodeAt(i2);
          if (56320 <= second && second <= 57343) {
            code = (code - 55296) * 1024 + second - 56320 + 65536;
          } else {
            result += this.wcwidth(second);
          }
        }
        const currentInfo = this.charProperties(code, precedingInfo);
        let chWidth = _UnicodeService.extractWidth(currentInfo);
        if (_UnicodeService.extractShouldJoin(currentInfo)) {
          chWidth -= _UnicodeService.extractWidth(precedingInfo);
        }
        result += chWidth;
        precedingInfo = currentInfo;
      }
      return result;
    }
    charProperties(codepoint, preceding) {
      return this._activeProvider.charProperties(codepoint, preceding);
    }
  };

  // node_modules/@xterm/xterm/src/common/services/CharsetService.ts
  var CharsetService = class {
    constructor() {
      this.glevel = 0;
      this._charsets = [];
    }
    reset() {
      this.charset = void 0;
      this._charsets = [];
      this.glevel = 0;
    }
    setgLevel(g) {
      this.glevel = g;
      this.charset = this._charsets[g];
    }
    setgCharset(g, charset) {
      this._charsets[g] = charset;
      if (this.glevel === g) {
        this.charset = charset;
      }
    }
  };

  // node_modules/@xterm/xterm/src/common/WindowsMode.ts
  function updateWindowsModeWrappedState(bufferService) {
    const line = bufferService.buffer.lines.get(bufferService.buffer.ybase + bufferService.buffer.y - 1);
    const lastChar = line?.get(bufferService.cols - 1);
    const nextLine = bufferService.buffer.lines.get(bufferService.buffer.ybase + bufferService.buffer.y);
    if (nextLine && lastChar) {
      nextLine.isWrapped = lastChar[CHAR_DATA_CODE_INDEX] !== NULL_CELL_CODE && lastChar[CHAR_DATA_CODE_INDEX] !== WHITESPACE_CELL_CODE;
    }
  }

  // node_modules/@xterm/xterm/src/common/parser/Constants.ts
  var PAYLOAD_LIMIT = 1e7;

  // node_modules/@xterm/xterm/src/common/parser/Params.ts
  var MAX_VALUE = 2147483647;
  var MAX_SUBPARAMS = 256;
  var Params = class _Params {
    /**
     * @param maxLength max length of storable parameters
     * @param maxSubParamsLength max length of storable sub parameters
     */
    constructor(maxLength = 32, maxSubParamsLength = 32) {
      this.maxLength = maxLength;
      this.maxSubParamsLength = maxSubParamsLength;
      if (maxSubParamsLength > MAX_SUBPARAMS) {
        throw new Error("maxSubParamsLength must not be greater than 256");
      }
      this.params = new Int32Array(maxLength);
      this.length = 0;
      this._subParams = new Int32Array(maxSubParamsLength);
      this._subParamsLength = 0;
      this._subParamsIdx = new Uint16Array(maxLength);
      this._rejectDigits = false;
      this._rejectSubDigits = false;
      this._digitIsSub = false;
    }
    /**
     * Create a `Params` type from JS array representation.
     */
    static fromArray(values) {
      const params = new _Params();
      if (!values.length) {
        return params;
      }
      for (let i2 = Array.isArray(values[0]) ? 1 : 0; i2 < values.length; ++i2) {
        const value = values[i2];
        if (Array.isArray(value)) {
          for (let k = 0; k < value.length; ++k) {
            params.addSubParam(value[k]);
          }
        } else {
          params.addParam(value);
        }
      }
      return params;
    }
    /**
     * Clone object.
     */
    clone() {
      const newParams = new _Params(this.maxLength, this.maxSubParamsLength);
      newParams.params.set(this.params);
      newParams.length = this.length;
      newParams._subParams.set(this._subParams);
      newParams._subParamsLength = this._subParamsLength;
      newParams._subParamsIdx.set(this._subParamsIdx);
      newParams._rejectDigits = this._rejectDigits;
      newParams._rejectSubDigits = this._rejectSubDigits;
      newParams._digitIsSub = this._digitIsSub;
      return newParams;
    }
    /**
     * Get a JS array representation of the current parameters and sub parameters.
     * The array is structured as follows:
     *    sequence: "1;2:3:4;5::6"
     *    array   : [1, 2, [3, 4], 5, [-1, 6]]
     */
    toArray() {
      const res = [];
      for (let i2 = 0; i2 < this.length; ++i2) {
        res.push(this.params[i2]);
        const start = this._subParamsIdx[i2] >> 8;
        const end = this._subParamsIdx[i2] & 255;
        if (end - start > 0) {
          res.push(Array.prototype.slice.call(this._subParams, start, end));
        }
      }
      return res;
    }
    /**
     * Reset to initial empty state.
     */
    reset() {
      this.length = 0;
      this._subParamsLength = 0;
      this._rejectDigits = false;
      this._rejectSubDigits = false;
      this._digitIsSub = false;
    }
    /**
     * Add a parameter value.
     * `Params` only stores up to `maxLength` parameters, any later
     * parameter will be ignored.
     * Note: VT devices only stored up to 16 values, xterm seems to
     * store up to 30.
     */
    addParam(value) {
      this._digitIsSub = false;
      if (this.length >= this.maxLength) {
        this._rejectDigits = true;
        return;
      }
      if (value < -1) {
        throw new Error("values lesser than -1 are not allowed");
      }
      this._subParamsIdx[this.length] = this._subParamsLength << 8 | this._subParamsLength;
      this.params[this.length++] = value > MAX_VALUE ? MAX_VALUE : value;
    }
    /**
     * Add a sub parameter value.
     * The sub parameter is automatically associated with the last parameter value.
     * Thus it is not possible to add a subparameter without any parameter added yet.
     * `Params` only stores up to `subParamsLength` sub parameters, any later
     * sub parameter will be ignored.
     */
    addSubParam(value) {
      this._digitIsSub = true;
      if (!this.length) {
        return;
      }
      if (this._rejectDigits || this._subParamsLength >= this.maxSubParamsLength) {
        this._rejectSubDigits = true;
        return;
      }
      if (value < -1) {
        throw new Error("values lesser than -1 are not allowed");
      }
      this._subParams[this._subParamsLength++] = value > MAX_VALUE ? MAX_VALUE : value;
      this._subParamsIdx[this.length - 1]++;
    }
    /**
     * Whether parameter at index `idx` has sub parameters.
     */
    hasSubParams(idx) {
      return (this._subParamsIdx[idx] & 255) - (this._subParamsIdx[idx] >> 8) > 0;
    }
    /**
     * Return sub parameters for parameter at index `idx`.
     * Note: The values are borrowed, thus you need to copy
     * the values if you need to hold them in nonlocal scope.
     */
    getSubParams(idx) {
      const start = this._subParamsIdx[idx] >> 8;
      const end = this._subParamsIdx[idx] & 255;
      if (end - start > 0) {
        return this._subParams.subarray(start, end);
      }
      return null;
    }
    /**
     * Return all sub parameters as {idx: subparams} mapping.
     * Note: The values are not borrowed.
     */
    getSubParamsAll() {
      const result = {};
      for (let i2 = 0; i2 < this.length; ++i2) {
        const start = this._subParamsIdx[i2] >> 8;
        const end = this._subParamsIdx[i2] & 255;
        if (end - start > 0) {
          result[i2] = this._subParams.slice(start, end);
        }
      }
      return result;
    }
    /**
     * Add a single digit value to current parameter.
     * This is used by the parser to account digits on a char by char basis.
     */
    addDigit(value) {
      let length;
      if (this._rejectDigits || !(length = this._digitIsSub ? this._subParamsLength : this.length) || this._digitIsSub && this._rejectSubDigits) {
        return;
      }
      const store = this._digitIsSub ? this._subParams : this.params;
      const cur = store[length - 1];
      store[length - 1] = ~cur ? Math.min(cur * 10 + value, MAX_VALUE) : value;
    }
  };

  // node_modules/@xterm/xterm/src/common/parser/OscParser.ts
  var EMPTY_HANDLERS = [];
  var OscParser = class {
    constructor() {
      this._state = 0 /* START */;
      this._active = EMPTY_HANDLERS;
      this._id = -1;
      this._handlers = /* @__PURE__ */ Object.create(null);
      this._handlerFb = () => {
      };
      this._stack = {
        paused: false,
        loopPosition: 0,
        fallThrough: false
      };
    }
    registerHandler(ident, handler) {
      if (this._handlers[ident] === void 0) {
        this._handlers[ident] = [];
      }
      const handlerList = this._handlers[ident];
      handlerList.push(handler);
      return {
        dispose: () => {
          const handlerIndex = handlerList.indexOf(handler);
          if (handlerIndex !== -1) {
            handlerList.splice(handlerIndex, 1);
          }
        }
      };
    }
    clearHandler(ident) {
      if (this._handlers[ident]) delete this._handlers[ident];
    }
    setHandlerFallback(handler) {
      this._handlerFb = handler;
    }
    dispose() {
      this._handlers = /* @__PURE__ */ Object.create(null);
      this._handlerFb = () => {
      };
      this._active = EMPTY_HANDLERS;
    }
    reset() {
      if (this._state === 2 /* PAYLOAD */) {
        for (let j = this._stack.paused ? this._stack.loopPosition - 1 : this._active.length - 1; j >= 0; --j) {
          this._active[j].end(false);
        }
      }
      this._stack.paused = false;
      this._active = EMPTY_HANDLERS;
      this._id = -1;
      this._state = 0 /* START */;
    }
    _start() {
      this._active = this._handlers[this._id] || EMPTY_HANDLERS;
      if (!this._active.length) {
        this._handlerFb(this._id, "START");
      } else {
        for (let j = this._active.length - 1; j >= 0; j--) {
          this._active[j].start();
        }
      }
    }
    _put(data, start, end) {
      if (!this._active.length) {
        this._handlerFb(this._id, "PUT", utf32ToString(data, start, end));
      } else {
        for (let j = this._active.length - 1; j >= 0; j--) {
          this._active[j].put(data, start, end);
        }
      }
    }
    start() {
      this.reset();
      this._state = 1 /* ID */;
    }
    /**
     * Put data to current OSC command.
     * Expects the identifier of the OSC command in the form
     * OSC id ; payload ST/BEL
     * Payload chunks are not further processed and get
     * directly passed to the handlers.
     */
    put(data, start, end) {
      if (this._state === 3 /* ABORT */) {
        return;
      }
      if (this._state === 1 /* ID */) {
        while (start < end) {
          const code = data[start++];
          if (code === 59) {
            this._state = 2 /* PAYLOAD */;
            this._start();
            break;
          }
          if (code < 48 || 57 < code) {
            this._state = 3 /* ABORT */;
            return;
          }
          if (this._id === -1) {
            this._id = 0;
          }
          this._id = this._id * 10 + code - 48;
        }
      }
      if (this._state === 2 /* PAYLOAD */ && end - start > 0) {
        this._put(data, start, end);
      }
    }
    /**
     * Indicates end of an OSC command.
     * Whether the OSC got aborted or finished normally
     * is indicated by `success`.
     */
    end(success, promiseResult = true) {
      if (this._state === 0 /* START */) {
        return;
      }
      if (this._state !== 3 /* ABORT */) {
        if (this._state === 1 /* ID */) {
          this._start();
        }
        if (!this._active.length) {
          this._handlerFb(this._id, "END", success);
        } else {
          let handlerResult = false;
          let j = this._active.length - 1;
          let fallThrough = false;
          if (this._stack.paused) {
            j = this._stack.loopPosition - 1;
            handlerResult = promiseResult;
            fallThrough = this._stack.fallThrough;
            this._stack.paused = false;
          }
          if (!fallThrough && handlerResult === false) {
            for (; j >= 0; j--) {
              handlerResult = this._active[j].end(success);
              if (handlerResult === true) {
                break;
              } else if (handlerResult instanceof Promise) {
                this._stack.paused = true;
                this._stack.loopPosition = j;
                this._stack.fallThrough = false;
                return handlerResult;
              }
            }
            j--;
          }
          for (; j >= 0; j--) {
            handlerResult = this._active[j].end(false);
            if (handlerResult instanceof Promise) {
              this._stack.paused = true;
              this._stack.loopPosition = j;
              this._stack.fallThrough = true;
              return handlerResult;
            }
          }
        }
      }
      this._active = EMPTY_HANDLERS;
      this._id = -1;
      this._state = 0 /* START */;
    }
  };
  var OscHandler = class {
    constructor(_handler) {
      this._handler = _handler;
      this._data = "";
      this._hitLimit = false;
    }
    start() {
      this._data = "";
      this._hitLimit = false;
    }
    put(data, start, end) {
      if (this._hitLimit) {
        return;
      }
      this._data += utf32ToString(data, start, end);
      if (this._data.length > PAYLOAD_LIMIT) {
        this._data = "";
        this._hitLimit = true;
      }
    }
    end(success) {
      let ret = false;
      if (this._hitLimit) {
        ret = false;
      } else if (success) {
        ret = this._handler(this._data);
        if (ret instanceof Promise) {
          return ret.then((res) => {
            this._data = "";
            this._hitLimit = false;
            return res;
          });
        }
      }
      this._data = "";
      this._hitLimit = false;
      return ret;
    }
  };

  // node_modules/@xterm/xterm/src/common/parser/DcsParser.ts
  var EMPTY_HANDLERS2 = [];
  var DcsParser = class {
    constructor() {
      this._handlers = /* @__PURE__ */ Object.create(null);
      this._active = EMPTY_HANDLERS2;
      this._ident = 0;
      this._handlerFb = () => {
      };
      this._stack = {
        paused: false,
        loopPosition: 0,
        fallThrough: false
      };
    }
    dispose() {
      this._handlers = /* @__PURE__ */ Object.create(null);
      this._handlerFb = () => {
      };
      this._active = EMPTY_HANDLERS2;
    }
    registerHandler(ident, handler) {
      if (this._handlers[ident] === void 0) {
        this._handlers[ident] = [];
      }
      const handlerList = this._handlers[ident];
      handlerList.push(handler);
      return {
        dispose: () => {
          const handlerIndex = handlerList.indexOf(handler);
          if (handlerIndex !== -1) {
            handlerList.splice(handlerIndex, 1);
          }
        }
      };
    }
    clearHandler(ident) {
      if (this._handlers[ident]) delete this._handlers[ident];
    }
    setHandlerFallback(handler) {
      this._handlerFb = handler;
    }
    reset() {
      if (this._active.length) {
        for (let j = this._stack.paused ? this._stack.loopPosition - 1 : this._active.length - 1; j >= 0; --j) {
          this._active[j].unhook(false);
        }
      }
      this._stack.paused = false;
      this._active = EMPTY_HANDLERS2;
      this._ident = 0;
    }
    hook(ident, params) {
      this.reset();
      this._ident = ident;
      this._active = this._handlers[ident] || EMPTY_HANDLERS2;
      if (!this._active.length) {
        this._handlerFb(this._ident, "HOOK", params);
      } else {
        for (let j = this._active.length - 1; j >= 0; j--) {
          this._active[j].hook(params);
        }
      }
    }
    put(data, start, end) {
      if (!this._active.length) {
        this._handlerFb(this._ident, "PUT", utf32ToString(data, start, end));
      } else {
        for (let j = this._active.length - 1; j >= 0; j--) {
          this._active[j].put(data, start, end);
        }
      }
    }
    unhook(success, promiseResult = true) {
      if (!this._active.length) {
        this._handlerFb(this._ident, "UNHOOK", success);
      } else {
        let handlerResult = false;
        let j = this._active.length - 1;
        let fallThrough = false;
        if (this._stack.paused) {
          j = this._stack.loopPosition - 1;
          handlerResult = promiseResult;
          fallThrough = this._stack.fallThrough;
          this._stack.paused = false;
        }
        if (!fallThrough && handlerResult === false) {
          for (; j >= 0; j--) {
            handlerResult = this._active[j].unhook(success);
            if (handlerResult === true) {
              break;
            } else if (handlerResult instanceof Promise) {
              this._stack.paused = true;
              this._stack.loopPosition = j;
              this._stack.fallThrough = false;
              return handlerResult;
            }
          }
          j--;
        }
        for (; j >= 0; j--) {
          handlerResult = this._active[j].unhook(false);
          if (handlerResult instanceof Promise) {
            this._stack.paused = true;
            this._stack.loopPosition = j;
            this._stack.fallThrough = true;
            return handlerResult;
          }
        }
      }
      this._active = EMPTY_HANDLERS2;
      this._ident = 0;
    }
  };
  var EMPTY_PARAMS = new Params();
  EMPTY_PARAMS.addParam(0);
  var DcsHandler = class {
    constructor(_handler) {
      this._handler = _handler;
      this._data = "";
      this._params = EMPTY_PARAMS;
      this._hitLimit = false;
    }
    hook(params) {
      this._params = params.length > 1 || params.params[0] ? params.clone() : EMPTY_PARAMS;
      this._data = "";
      this._hitLimit = false;
    }
    put(data, start, end) {
      if (this._hitLimit) {
        return;
      }
      this._data += utf32ToString(data, start, end);
      if (this._data.length > PAYLOAD_LIMIT) {
        this._data = "";
        this._hitLimit = true;
      }
    }
    unhook(success) {
      let ret = false;
      if (this._hitLimit) {
        ret = false;
      } else if (success) {
        ret = this._handler(this._data, this._params);
        if (ret instanceof Promise) {
          return ret.then((res) => {
            this._params = EMPTY_PARAMS;
            this._data = "";
            this._hitLimit = false;
            return res;
          });
        }
      }
      this._params = EMPTY_PARAMS;
      this._data = "";
      this._hitLimit = false;
      return ret;
    }
  };

  // node_modules/@xterm/xterm/src/common/parser/EscapeSequenceParser.ts
  var TransitionTable = class {
    constructor(length) {
      this.table = new Uint8Array(length);
    }
    /**
     * Set default transition.
     * @param action default action
     * @param next default next state
     */
    setDefault(action, next) {
      this.table.fill(action << 4 /* TRANSITION_ACTION_SHIFT */ | next);
    }
    /**
     * Add a transition to the transition table.
     * @param code input character code
     * @param state current parser state
     * @param action parser action to be done
     * @param next next parser state
     */
    add(code, state, action, next) {
      this.table[state << 8 /* INDEX_STATE_SHIFT */ | code] = action << 4 /* TRANSITION_ACTION_SHIFT */ | next;
    }
    /**
     * Add transitions for multiple input character codes.
     * @param codes input character code array
     * @param state current parser state
     * @param action parser action to be done
     * @param next next parser state
     */
    addMany(codes, state, action, next) {
      for (let i2 = 0; i2 < codes.length; i2++) {
        this.table[state << 8 /* INDEX_STATE_SHIFT */ | codes[i2]] = action << 4 /* TRANSITION_ACTION_SHIFT */ | next;
      }
    }
  };
  var NON_ASCII_PRINTABLE = 160;
  var VT500_TRANSITION_TABLE = (function() {
    const table2 = new TransitionTable(4095);
    const BYTE_VALUES = 256;
    const blueprint = Array.apply(null, Array(BYTE_VALUES)).map((unused, i2) => i2);
    const r = (start, end) => blueprint.slice(start, end);
    const PRINTABLES = r(32, 127);
    const EXECUTABLES = r(0, 24);
    EXECUTABLES.push(25);
    EXECUTABLES.push.apply(EXECUTABLES, r(28, 32));
    const states = r(0 /* GROUND */, 13 /* DCS_PASSTHROUGH */ + 1);
    let state;
    table2.setDefault(1 /* ERROR */, 0 /* GROUND */);
    table2.addMany(PRINTABLES, 0 /* GROUND */, 2 /* PRINT */, 0 /* GROUND */);
    for (state in states) {
      table2.addMany([24, 26, 153, 154], state, 3 /* EXECUTE */, 0 /* GROUND */);
      table2.addMany(r(128, 144), state, 3 /* EXECUTE */, 0 /* GROUND */);
      table2.addMany(r(144, 152), state, 3 /* EXECUTE */, 0 /* GROUND */);
      table2.add(156, state, 0 /* IGNORE */, 0 /* GROUND */);
      table2.add(27, state, 11 /* CLEAR */, 1 /* ESCAPE */);
      table2.add(157, state, 4 /* OSC_START */, 8 /* OSC_STRING */);
      table2.addMany([152, 158, 159], state, 0 /* IGNORE */, 7 /* SOS_PM_APC_STRING */);
      table2.add(155, state, 11 /* CLEAR */, 3 /* CSI_ENTRY */);
      table2.add(144, state, 11 /* CLEAR */, 9 /* DCS_ENTRY */);
    }
    table2.addMany(EXECUTABLES, 0 /* GROUND */, 3 /* EXECUTE */, 0 /* GROUND */);
    table2.addMany(EXECUTABLES, 1 /* ESCAPE */, 3 /* EXECUTE */, 1 /* ESCAPE */);
    table2.add(127, 1 /* ESCAPE */, 0 /* IGNORE */, 1 /* ESCAPE */);
    table2.addMany(EXECUTABLES, 8 /* OSC_STRING */, 0 /* IGNORE */, 8 /* OSC_STRING */);
    table2.addMany(EXECUTABLES, 3 /* CSI_ENTRY */, 3 /* EXECUTE */, 3 /* CSI_ENTRY */);
    table2.add(127, 3 /* CSI_ENTRY */, 0 /* IGNORE */, 3 /* CSI_ENTRY */);
    table2.addMany(EXECUTABLES, 4 /* CSI_PARAM */, 3 /* EXECUTE */, 4 /* CSI_PARAM */);
    table2.add(127, 4 /* CSI_PARAM */, 0 /* IGNORE */, 4 /* CSI_PARAM */);
    table2.addMany(EXECUTABLES, 6 /* CSI_IGNORE */, 3 /* EXECUTE */, 6 /* CSI_IGNORE */);
    table2.addMany(EXECUTABLES, 5 /* CSI_INTERMEDIATE */, 3 /* EXECUTE */, 5 /* CSI_INTERMEDIATE */);
    table2.add(127, 5 /* CSI_INTERMEDIATE */, 0 /* IGNORE */, 5 /* CSI_INTERMEDIATE */);
    table2.addMany(EXECUTABLES, 2 /* ESCAPE_INTERMEDIATE */, 3 /* EXECUTE */, 2 /* ESCAPE_INTERMEDIATE */);
    table2.add(127, 2 /* ESCAPE_INTERMEDIATE */, 0 /* IGNORE */, 2 /* ESCAPE_INTERMEDIATE */);
    table2.add(93, 1 /* ESCAPE */, 4 /* OSC_START */, 8 /* OSC_STRING */);
    table2.addMany(PRINTABLES, 8 /* OSC_STRING */, 5 /* OSC_PUT */, 8 /* OSC_STRING */);
    table2.add(127, 8 /* OSC_STRING */, 5 /* OSC_PUT */, 8 /* OSC_STRING */);
    table2.addMany([156, 27, 24, 26, 7], 8 /* OSC_STRING */, 6 /* OSC_END */, 0 /* GROUND */);
    table2.addMany(r(28, 32), 8 /* OSC_STRING */, 0 /* IGNORE */, 8 /* OSC_STRING */);
    table2.addMany([88, 94, 95], 1 /* ESCAPE */, 0 /* IGNORE */, 7 /* SOS_PM_APC_STRING */);
    table2.addMany(PRINTABLES, 7 /* SOS_PM_APC_STRING */, 0 /* IGNORE */, 7 /* SOS_PM_APC_STRING */);
    table2.addMany(EXECUTABLES, 7 /* SOS_PM_APC_STRING */, 0 /* IGNORE */, 7 /* SOS_PM_APC_STRING */);
    table2.add(156, 7 /* SOS_PM_APC_STRING */, 0 /* IGNORE */, 0 /* GROUND */);
    table2.add(127, 7 /* SOS_PM_APC_STRING */, 0 /* IGNORE */, 7 /* SOS_PM_APC_STRING */);
    table2.add(91, 1 /* ESCAPE */, 11 /* CLEAR */, 3 /* CSI_ENTRY */);
    table2.addMany(r(64, 127), 3 /* CSI_ENTRY */, 7 /* CSI_DISPATCH */, 0 /* GROUND */);
    table2.addMany(r(48, 60), 3 /* CSI_ENTRY */, 8 /* PARAM */, 4 /* CSI_PARAM */);
    table2.addMany([60, 61, 62, 63], 3 /* CSI_ENTRY */, 9 /* COLLECT */, 4 /* CSI_PARAM */);
    table2.addMany(r(48, 60), 4 /* CSI_PARAM */, 8 /* PARAM */, 4 /* CSI_PARAM */);
    table2.addMany(r(64, 127), 4 /* CSI_PARAM */, 7 /* CSI_DISPATCH */, 0 /* GROUND */);
    table2.addMany([60, 61, 62, 63], 4 /* CSI_PARAM */, 0 /* IGNORE */, 6 /* CSI_IGNORE */);
    table2.addMany(r(32, 64), 6 /* CSI_IGNORE */, 0 /* IGNORE */, 6 /* CSI_IGNORE */);
    table2.add(127, 6 /* CSI_IGNORE */, 0 /* IGNORE */, 6 /* CSI_IGNORE */);
    table2.addMany(r(64, 127), 6 /* CSI_IGNORE */, 0 /* IGNORE */, 0 /* GROUND */);
    table2.addMany(r(32, 48), 3 /* CSI_ENTRY */, 9 /* COLLECT */, 5 /* CSI_INTERMEDIATE */);
    table2.addMany(r(32, 48), 5 /* CSI_INTERMEDIATE */, 9 /* COLLECT */, 5 /* CSI_INTERMEDIATE */);
    table2.addMany(r(48, 64), 5 /* CSI_INTERMEDIATE */, 0 /* IGNORE */, 6 /* CSI_IGNORE */);
    table2.addMany(r(64, 127), 5 /* CSI_INTERMEDIATE */, 7 /* CSI_DISPATCH */, 0 /* GROUND */);
    table2.addMany(r(32, 48), 4 /* CSI_PARAM */, 9 /* COLLECT */, 5 /* CSI_INTERMEDIATE */);
    table2.addMany(r(32, 48), 1 /* ESCAPE */, 9 /* COLLECT */, 2 /* ESCAPE_INTERMEDIATE */);
    table2.addMany(r(32, 48), 2 /* ESCAPE_INTERMEDIATE */, 9 /* COLLECT */, 2 /* ESCAPE_INTERMEDIATE */);
    table2.addMany(r(48, 127), 2 /* ESCAPE_INTERMEDIATE */, 10 /* ESC_DISPATCH */, 0 /* GROUND */);
    table2.addMany(r(48, 80), 1 /* ESCAPE */, 10 /* ESC_DISPATCH */, 0 /* GROUND */);
    table2.addMany(r(81, 88), 1 /* ESCAPE */, 10 /* ESC_DISPATCH */, 0 /* GROUND */);
    table2.addMany([89, 90, 92], 1 /* ESCAPE */, 10 /* ESC_DISPATCH */, 0 /* GROUND */);
    table2.addMany(r(96, 127), 1 /* ESCAPE */, 10 /* ESC_DISPATCH */, 0 /* GROUND */);
    table2.add(80, 1 /* ESCAPE */, 11 /* CLEAR */, 9 /* DCS_ENTRY */);
    table2.addMany(EXECUTABLES, 9 /* DCS_ENTRY */, 0 /* IGNORE */, 9 /* DCS_ENTRY */);
    table2.add(127, 9 /* DCS_ENTRY */, 0 /* IGNORE */, 9 /* DCS_ENTRY */);
    table2.addMany(r(28, 32), 9 /* DCS_ENTRY */, 0 /* IGNORE */, 9 /* DCS_ENTRY */);
    table2.addMany(r(32, 48), 9 /* DCS_ENTRY */, 9 /* COLLECT */, 12 /* DCS_INTERMEDIATE */);
    table2.addMany(r(48, 60), 9 /* DCS_ENTRY */, 8 /* PARAM */, 10 /* DCS_PARAM */);
    table2.addMany([60, 61, 62, 63], 9 /* DCS_ENTRY */, 9 /* COLLECT */, 10 /* DCS_PARAM */);
    table2.addMany(EXECUTABLES, 11 /* DCS_IGNORE */, 0 /* IGNORE */, 11 /* DCS_IGNORE */);
    table2.addMany(r(32, 128), 11 /* DCS_IGNORE */, 0 /* IGNORE */, 11 /* DCS_IGNORE */);
    table2.addMany(r(28, 32), 11 /* DCS_IGNORE */, 0 /* IGNORE */, 11 /* DCS_IGNORE */);
    table2.addMany(EXECUTABLES, 10 /* DCS_PARAM */, 0 /* IGNORE */, 10 /* DCS_PARAM */);
    table2.add(127, 10 /* DCS_PARAM */, 0 /* IGNORE */, 10 /* DCS_PARAM */);
    table2.addMany(r(28, 32), 10 /* DCS_PARAM */, 0 /* IGNORE */, 10 /* DCS_PARAM */);
    table2.addMany(r(48, 60), 10 /* DCS_PARAM */, 8 /* PARAM */, 10 /* DCS_PARAM */);
    table2.addMany([60, 61, 62, 63], 10 /* DCS_PARAM */, 0 /* IGNORE */, 11 /* DCS_IGNORE */);
    table2.addMany(r(32, 48), 10 /* DCS_PARAM */, 9 /* COLLECT */, 12 /* DCS_INTERMEDIATE */);
    table2.addMany(EXECUTABLES, 12 /* DCS_INTERMEDIATE */, 0 /* IGNORE */, 12 /* DCS_INTERMEDIATE */);
    table2.add(127, 12 /* DCS_INTERMEDIATE */, 0 /* IGNORE */, 12 /* DCS_INTERMEDIATE */);
    table2.addMany(r(28, 32), 12 /* DCS_INTERMEDIATE */, 0 /* IGNORE */, 12 /* DCS_INTERMEDIATE */);
    table2.addMany(r(32, 48), 12 /* DCS_INTERMEDIATE */, 9 /* COLLECT */, 12 /* DCS_INTERMEDIATE */);
    table2.addMany(r(48, 64), 12 /* DCS_INTERMEDIATE */, 0 /* IGNORE */, 11 /* DCS_IGNORE */);
    table2.addMany(r(64, 127), 12 /* DCS_INTERMEDIATE */, 12 /* DCS_HOOK */, 13 /* DCS_PASSTHROUGH */);
    table2.addMany(r(64, 127), 10 /* DCS_PARAM */, 12 /* DCS_HOOK */, 13 /* DCS_PASSTHROUGH */);
    table2.addMany(r(64, 127), 9 /* DCS_ENTRY */, 12 /* DCS_HOOK */, 13 /* DCS_PASSTHROUGH */);
    table2.addMany(EXECUTABLES, 13 /* DCS_PASSTHROUGH */, 13 /* DCS_PUT */, 13 /* DCS_PASSTHROUGH */);
    table2.addMany(PRINTABLES, 13 /* DCS_PASSTHROUGH */, 13 /* DCS_PUT */, 13 /* DCS_PASSTHROUGH */);
    table2.add(127, 13 /* DCS_PASSTHROUGH */, 0 /* IGNORE */, 13 /* DCS_PASSTHROUGH */);
    table2.addMany([27, 156, 24, 26], 13 /* DCS_PASSTHROUGH */, 14 /* DCS_UNHOOK */, 0 /* GROUND */);
    table2.add(NON_ASCII_PRINTABLE, 0 /* GROUND */, 2 /* PRINT */, 0 /* GROUND */);
    table2.add(NON_ASCII_PRINTABLE, 8 /* OSC_STRING */, 5 /* OSC_PUT */, 8 /* OSC_STRING */);
    table2.add(NON_ASCII_PRINTABLE, 6 /* CSI_IGNORE */, 0 /* IGNORE */, 6 /* CSI_IGNORE */);
    table2.add(NON_ASCII_PRINTABLE, 11 /* DCS_IGNORE */, 0 /* IGNORE */, 11 /* DCS_IGNORE */);
    table2.add(NON_ASCII_PRINTABLE, 13 /* DCS_PASSTHROUGH */, 13 /* DCS_PUT */, 13 /* DCS_PASSTHROUGH */);
    return table2;
  })();
  var EscapeSequenceParser = class extends Disposable {
    constructor(_transitions = VT500_TRANSITION_TABLE) {
      super();
      this._transitions = _transitions;
      // parser stack save for async handler support
      this._parseStack = {
        state: 0 /* NONE */,
        handlers: [],
        handlerPos: 0,
        transition: 0,
        chunkPos: 0
      };
      this.initialState = 0 /* GROUND */;
      this.currentState = this.initialState;
      this._params = new Params();
      this._params.addParam(0);
      this._collect = 0;
      this.precedingJoinState = 0;
      this._printHandlerFb = (data, start, end) => {
      };
      this._executeHandlerFb = (code) => {
      };
      this._csiHandlerFb = (ident, params) => {
      };
      this._escHandlerFb = (ident) => {
      };
      this._errorHandlerFb = (state) => state;
      this._printHandler = this._printHandlerFb;
      this._executeHandlers = /* @__PURE__ */ Object.create(null);
      this._csiHandlers = /* @__PURE__ */ Object.create(null);
      this._escHandlers = /* @__PURE__ */ Object.create(null);
      this._register(toDisposable(() => {
        this._csiHandlers = /* @__PURE__ */ Object.create(null);
        this._executeHandlers = /* @__PURE__ */ Object.create(null);
        this._escHandlers = /* @__PURE__ */ Object.create(null);
      }));
      this._oscParser = this._register(new OscParser());
      this._dcsParser = this._register(new DcsParser());
      this._errorHandler = this._errorHandlerFb;
      this.registerEscHandler({ final: "\\" }, () => true);
    }
    _identifier(id2, finalRange = [64, 126]) {
      let res = 0;
      if (id2.prefix) {
        if (id2.prefix.length > 1) {
          throw new Error("only one byte as prefix supported");
        }
        res = id2.prefix.charCodeAt(0);
        if (res && 60 > res || res > 63) {
          throw new Error("prefix must be in range 0x3c .. 0x3f");
        }
      }
      if (id2.intermediates) {
        if (id2.intermediates.length > 2) {
          throw new Error("only two bytes as intermediates are supported");
        }
        for (let i2 = 0; i2 < id2.intermediates.length; ++i2) {
          const intermediate = id2.intermediates.charCodeAt(i2);
          if (32 > intermediate || intermediate > 47) {
            throw new Error("intermediate must be in range 0x20 .. 0x2f");
          }
          res <<= 8;
          res |= intermediate;
        }
      }
      if (id2.final.length !== 1) {
        throw new Error("final must be a single byte");
      }
      const finalCode = id2.final.charCodeAt(0);
      if (finalRange[0] > finalCode || finalCode > finalRange[1]) {
        throw new Error(`final must be in range ${finalRange[0]} .. ${finalRange[1]}`);
      }
      res <<= 8;
      res |= finalCode;
      return res;
    }
    identToString(ident) {
      const res = [];
      while (ident) {
        res.push(String.fromCharCode(ident & 255));
        ident >>= 8;
      }
      return res.reverse().join("");
    }
    setPrintHandler(handler) {
      this._printHandler = handler;
    }
    clearPrintHandler() {
      this._printHandler = this._printHandlerFb;
    }
    registerEscHandler(id2, handler) {
      const ident = this._identifier(id2, [48, 126]);
      if (this._escHandlers[ident] === void 0) {
        this._escHandlers[ident] = [];
      }
      const handlerList = this._escHandlers[ident];
      handlerList.push(handler);
      return {
        dispose: () => {
          const handlerIndex = handlerList.indexOf(handler);
          if (handlerIndex !== -1) {
            handlerList.splice(handlerIndex, 1);
          }
        }
      };
    }
    clearEscHandler(id2) {
      if (this._escHandlers[this._identifier(id2, [48, 126])]) delete this._escHandlers[this._identifier(id2, [48, 126])];
    }
    setEscHandlerFallback(handler) {
      this._escHandlerFb = handler;
    }
    setExecuteHandler(flag, handler) {
      this._executeHandlers[flag.charCodeAt(0)] = handler;
    }
    clearExecuteHandler(flag) {
      if (this._executeHandlers[flag.charCodeAt(0)]) delete this._executeHandlers[flag.charCodeAt(0)];
    }
    setExecuteHandlerFallback(handler) {
      this._executeHandlerFb = handler;
    }
    registerCsiHandler(id2, handler) {
      const ident = this._identifier(id2);
      if (this._csiHandlers[ident] === void 0) {
        this._csiHandlers[ident] = [];
      }
      const handlerList = this._csiHandlers[ident];
      handlerList.push(handler);
      return {
        dispose: () => {
          const handlerIndex = handlerList.indexOf(handler);
          if (handlerIndex !== -1) {
            handlerList.splice(handlerIndex, 1);
          }
        }
      };
    }
    clearCsiHandler(id2) {
      if (this._csiHandlers[this._identifier(id2)]) delete this._csiHandlers[this._identifier(id2)];
    }
    setCsiHandlerFallback(callback) {
      this._csiHandlerFb = callback;
    }
    registerDcsHandler(id2, handler) {
      return this._dcsParser.registerHandler(this._identifier(id2), handler);
    }
    clearDcsHandler(id2) {
      this._dcsParser.clearHandler(this._identifier(id2));
    }
    setDcsHandlerFallback(handler) {
      this._dcsParser.setHandlerFallback(handler);
    }
    registerOscHandler(ident, handler) {
      return this._oscParser.registerHandler(ident, handler);
    }
    clearOscHandler(ident) {
      this._oscParser.clearHandler(ident);
    }
    setOscHandlerFallback(handler) {
      this._oscParser.setHandlerFallback(handler);
    }
    setErrorHandler(callback) {
      this._errorHandler = callback;
    }
    clearErrorHandler() {
      this._errorHandler = this._errorHandlerFb;
    }
    /**
     * Reset parser to initial values.
     *
     * This can also be used to lift the improper continuation error condition
     * when dealing with async handlers. Use this only as a last resort to silence
     * that error when the terminal has no pending data to be processed. Note that
     * the interrupted async handler might continue its work in the future messing
     * up the terminal state even further.
     */
    reset() {
      this.currentState = this.initialState;
      this._oscParser.reset();
      this._dcsParser.reset();
      this._params.reset();
      this._params.addParam(0);
      this._collect = 0;
      this.precedingJoinState = 0;
      if (this._parseStack.state !== 0 /* NONE */) {
        this._parseStack.state = 2 /* RESET */;
        this._parseStack.handlers = [];
      }
    }
    /**
     * Async parse support.
     */
    _preserveStack(state, handlers, handlerPos, transition, chunkPos) {
      this._parseStack.state = state;
      this._parseStack.handlers = handlers;
      this._parseStack.handlerPos = handlerPos;
      this._parseStack.transition = transition;
      this._parseStack.chunkPos = chunkPos;
    }
    /**
     * Parse UTF32 codepoints in `data` up to `length`.
     *
     * Note: For several actions with high data load the parsing is optimized
     * by using local read ahead loops with hardcoded conditions to
     * avoid costly table lookups. Make sure that any change of table values
     * will be reflected in the loop conditions as well and vice versa.
     * Affected states/actions:
     * - GROUND:PRINT
     * - CSI_PARAM:PARAM
     * - DCS_PARAM:PARAM
     * - OSC_STRING:OSC_PUT
     * - DCS_PASSTHROUGH:DCS_PUT
     *
     * Note on asynchronous handler support:
     * Any handler returning a promise will be treated as asynchronous.
     * To keep the in-band blocking working for async handlers, `parse` pauses execution,
     * creates a stack save and returns the promise to the caller.
     * For proper continuation of the paused state it is important
     * to await the promise resolving. On resolve the parse must be repeated
     * with the same chunk of data and the resolved value in `promiseResult`
     * until no promise is returned.
     *
     * Important: With only sync handlers defined, parsing is completely synchronous as well.
     * As soon as an async handler is involved, synchronous parsing is not possible anymore.
     *
     * Boilerplate for proper parsing of multiple chunks with async handlers:
     *
     * ```typescript
     * async function parseMultipleChunks(chunks: Uint32Array[]): Promise<void> {
     *   for (const chunk of chunks) {
     *     let result: void | Promise<boolean>;
     *     let prev: boolean | undefined;
     *     while (result = parser.parse(chunk, chunk.length, prev)) {
     *       prev = await result;
     *     }
     *   }
     *   // finished parsing all chunks...
     * }
     * ```
     */
    parse(data, length, promiseResult) {
      let code = 0;
      let transition = 0;
      let start = 0;
      let handlerResult;
      if (this._parseStack.state) {
        if (this._parseStack.state === 2 /* RESET */) {
          this._parseStack.state = 0 /* NONE */;
          start = this._parseStack.chunkPos + 1;
        } else {
          if (promiseResult === void 0 || this._parseStack.state === 1 /* FAIL */) {
            this._parseStack.state = 1 /* FAIL */;
            throw new Error("improper continuation due to previous async handler, giving up parsing");
          }
          const handlers = this._parseStack.handlers;
          let handlerPos = this._parseStack.handlerPos - 1;
          switch (this._parseStack.state) {
            case 3 /* CSI */:
              if (promiseResult === false && handlerPos > -1) {
                for (; handlerPos >= 0; handlerPos--) {
                  handlerResult = handlers[handlerPos](this._params);
                  if (handlerResult === true) {
                    break;
                  } else if (handlerResult instanceof Promise) {
                    this._parseStack.handlerPos = handlerPos;
                    return handlerResult;
                  }
                }
              }
              this._parseStack.handlers = [];
              break;
            case 4 /* ESC */:
              if (promiseResult === false && handlerPos > -1) {
                for (; handlerPos >= 0; handlerPos--) {
                  handlerResult = handlers[handlerPos]();
                  if (handlerResult === true) {
                    break;
                  } else if (handlerResult instanceof Promise) {
                    this._parseStack.handlerPos = handlerPos;
                    return handlerResult;
                  }
                }
              }
              this._parseStack.handlers = [];
              break;
            case 6 /* DCS */:
              code = data[this._parseStack.chunkPos];
              handlerResult = this._dcsParser.unhook(code !== 24 && code !== 26, promiseResult);
              if (handlerResult) {
                return handlerResult;
              }
              if (code === 27) this._parseStack.transition |= 1 /* ESCAPE */;
              this._params.reset();
              this._params.addParam(0);
              this._collect = 0;
              break;
            case 5 /* OSC */:
              code = data[this._parseStack.chunkPos];
              handlerResult = this._oscParser.end(code !== 24 && code !== 26, promiseResult);
              if (handlerResult) {
                return handlerResult;
              }
              if (code === 27) this._parseStack.transition |= 1 /* ESCAPE */;
              this._params.reset();
              this._params.addParam(0);
              this._collect = 0;
              break;
          }
          this._parseStack.state = 0 /* NONE */;
          start = this._parseStack.chunkPos + 1;
          this.precedingJoinState = 0;
          this.currentState = this._parseStack.transition & 15 /* TRANSITION_STATE_MASK */;
        }
      }
      for (let i2 = start; i2 < length; ++i2) {
        code = data[i2];
        transition = this._transitions.table[this.currentState << 8 /* INDEX_STATE_SHIFT */ | (code < 160 ? code : NON_ASCII_PRINTABLE)];
        switch (transition >> 4 /* TRANSITION_ACTION_SHIFT */) {
          case 2 /* PRINT */:
            for (let j2 = i2 + 1; ; ++j2) {
              if (j2 >= length || (code = data[j2]) < 32 || code > 126 && code < NON_ASCII_PRINTABLE) {
                this._printHandler(data, i2, j2);
                i2 = j2 - 1;
                break;
              }
              if (++j2 >= length || (code = data[j2]) < 32 || code > 126 && code < NON_ASCII_PRINTABLE) {
                this._printHandler(data, i2, j2);
                i2 = j2 - 1;
                break;
              }
              if (++j2 >= length || (code = data[j2]) < 32 || code > 126 && code < NON_ASCII_PRINTABLE) {
                this._printHandler(data, i2, j2);
                i2 = j2 - 1;
                break;
              }
              if (++j2 >= length || (code = data[j2]) < 32 || code > 126 && code < NON_ASCII_PRINTABLE) {
                this._printHandler(data, i2, j2);
                i2 = j2 - 1;
                break;
              }
            }
            break;
          case 3 /* EXECUTE */:
            if (this._executeHandlers[code]) this._executeHandlers[code]();
            else this._executeHandlerFb(code);
            this.precedingJoinState = 0;
            break;
          case 0 /* IGNORE */:
            break;
          case 1 /* ERROR */:
            const inject = this._errorHandler(
              {
                position: i2,
                code,
                currentState: this.currentState,
                collect: this._collect,
                params: this._params,
                abort: false
              }
            );
            if (inject.abort) return;
            break;
          case 7 /* CSI_DISPATCH */:
            const handlers = this._csiHandlers[this._collect << 8 | code];
            let j = handlers ? handlers.length - 1 : -1;
            for (; j >= 0; j--) {
              handlerResult = handlers[j](this._params);
              if (handlerResult === true) {
                break;
              } else if (handlerResult instanceof Promise) {
                this._preserveStack(3 /* CSI */, handlers, j, transition, i2);
                return handlerResult;
              }
            }
            if (j < 0) {
              this._csiHandlerFb(this._collect << 8 | code, this._params);
            }
            this.precedingJoinState = 0;
            break;
          case 8 /* PARAM */:
            do {
              switch (code) {
                case 59:
                  this._params.addParam(0);
                  break;
                case 58:
                  this._params.addSubParam(-1);
                  break;
                default:
                  this._params.addDigit(code - 48);
              }
            } while (++i2 < length && (code = data[i2]) > 47 && code < 60);
            i2--;
            break;
          case 9 /* COLLECT */:
            this._collect <<= 8;
            this._collect |= code;
            break;
          case 10 /* ESC_DISPATCH */:
            const handlersEsc = this._escHandlers[this._collect << 8 | code];
            let jj = handlersEsc ? handlersEsc.length - 1 : -1;
            for (; jj >= 0; jj--) {
              handlerResult = handlersEsc[jj]();
              if (handlerResult === true) {
                break;
              } else if (handlerResult instanceof Promise) {
                this._preserveStack(4 /* ESC */, handlersEsc, jj, transition, i2);
                return handlerResult;
              }
            }
            if (jj < 0) {
              this._escHandlerFb(this._collect << 8 | code);
            }
            this.precedingJoinState = 0;
            break;
          case 11 /* CLEAR */:
            this._params.reset();
            this._params.addParam(0);
            this._collect = 0;
            break;
          case 12 /* DCS_HOOK */:
            this._dcsParser.hook(this._collect << 8 | code, this._params);
            break;
          case 13 /* DCS_PUT */:
            for (let j2 = i2 + 1; ; ++j2) {
              if (j2 >= length || (code = data[j2]) === 24 || code === 26 || code === 27 || code > 127 && code < NON_ASCII_PRINTABLE) {
                this._dcsParser.put(data, i2, j2);
                i2 = j2 - 1;
                break;
              }
            }
            break;
          case 14 /* DCS_UNHOOK */:
            handlerResult = this._dcsParser.unhook(code !== 24 && code !== 26);
            if (handlerResult) {
              this._preserveStack(6 /* DCS */, [], 0, transition, i2);
              return handlerResult;
            }
            if (code === 27) transition |= 1 /* ESCAPE */;
            this._params.reset();
            this._params.addParam(0);
            this._collect = 0;
            this.precedingJoinState = 0;
            break;
          case 4 /* OSC_START */:
            this._oscParser.start();
            break;
          case 5 /* OSC_PUT */:
            for (let j2 = i2 + 1; ; j2++) {
              if (j2 >= length || (code = data[j2]) < 32 || code > 127 && code < NON_ASCII_PRINTABLE) {
                this._oscParser.put(data, i2, j2);
                i2 = j2 - 1;
                break;
              }
            }
            break;
          case 6 /* OSC_END */:
            handlerResult = this._oscParser.end(code !== 24 && code !== 26);
            if (handlerResult) {
              this._preserveStack(5 /* OSC */, [], 0, transition, i2);
              return handlerResult;
            }
            if (code === 27) transition |= 1 /* ESCAPE */;
            this._params.reset();
            this._params.addParam(0);
            this._collect = 0;
            this.precedingJoinState = 0;
            break;
        }
        this.currentState = transition & 15 /* TRANSITION_STATE_MASK */;
      }
    }
  };

  // node_modules/@xterm/xterm/src/common/input/XParseColor.ts
  var RGB_REX = /^([\da-f])\/([\da-f])\/([\da-f])$|^([\da-f]{2})\/([\da-f]{2})\/([\da-f]{2})$|^([\da-f]{3})\/([\da-f]{3})\/([\da-f]{3})$|^([\da-f]{4})\/([\da-f]{4})\/([\da-f]{4})$/;
  var HASH_REX = /^[\da-f]+$/;
  function parseColor2(data) {
    if (!data) return;
    let low = data.toLowerCase();
    if (low.indexOf("rgb:") === 0) {
      low = low.slice(4);
      const m = RGB_REX.exec(low);
      if (m) {
        const base = m[1] ? 15 : m[4] ? 255 : m[7] ? 4095 : 65535;
        return [
          Math.round(parseInt(m[1] || m[4] || m[7] || m[10], 16) / base * 255),
          Math.round(parseInt(m[2] || m[5] || m[8] || m[11], 16) / base * 255),
          Math.round(parseInt(m[3] || m[6] || m[9] || m[12], 16) / base * 255)
        ];
      }
    } else if (low.indexOf("#") === 0) {
      low = low.slice(1);
      if (HASH_REX.exec(low) && [3, 6, 9, 12].includes(low.length)) {
        const adv = low.length / 3;
        const result = [0, 0, 0];
        for (let i2 = 0; i2 < 3; ++i2) {
          const c = parseInt(low.slice(adv * i2, adv * i2 + adv), 16);
          result[i2] = adv === 1 ? c << 4 : adv === 2 ? c : adv === 3 ? c >> 4 : c >> 8;
        }
        return result;
      }
    }
  }
  function pad(n, bits) {
    const s = n.toString(16);
    const s2 = s.length < 2 ? "0" + s : s;
    switch (bits) {
      case 4:
        return s[0];
      case 8:
        return s2;
      case 12:
        return (s2 + s2).slice(0, 3);
      default:
        return s2 + s2;
    }
  }
  function toRgbString(color2, bits = 16) {
    const [r, g, b] = color2;
    return `rgb:${pad(r, bits)}/${pad(g, bits)}/${pad(b, bits)}`;
  }

  // node_modules/@xterm/xterm/src/common/InputHandler.ts
  var GLEVEL = { "(": 0, ")": 1, "*": 2, "+": 3, "-": 1, ".": 2 };
  var MAX_PARSEBUFFER_LENGTH = 131072;
  var STACK_LIMIT = 10;
  function paramToWindowOption(n, opts) {
    if (n > 24) {
      return opts.setWinLines || false;
    }
    switch (n) {
      case 1:
        return !!opts.restoreWin;
      case 2:
        return !!opts.minimizeWin;
      case 3:
        return !!opts.setWinPosition;
      case 4:
        return !!opts.setWinSizePixels;
      case 5:
        return !!opts.raiseWin;
      case 6:
        return !!opts.lowerWin;
      case 7:
        return !!opts.refreshWin;
      case 8:
        return !!opts.setWinSizeChars;
      case 9:
        return !!opts.maximizeWin;
      case 10:
        return !!opts.fullscreenWin;
      case 11:
        return !!opts.getWinState;
      case 13:
        return !!opts.getWinPosition;
      case 14:
        return !!opts.getWinSizePixels;
      case 15:
        return !!opts.getScreenSizePixels;
      case 16:
        return !!opts.getCellSizePixels;
      case 18:
        return !!opts.getWinSizeChars;
      case 19:
        return !!opts.getScreenSizeChars;
      case 20:
        return !!opts.getIconTitle;
      case 21:
        return !!opts.getWinTitle;
      case 22:
        return !!opts.pushTitle;
      case 23:
        return !!opts.popTitle;
      case 24:
        return !!opts.setWinLines;
    }
    return false;
  }
  var SLOW_ASYNC_LIMIT = 5e3;
  var $temp = 0;
  var InputHandler = class extends Disposable {
    constructor(_bufferService, _charsetService, _coreService, _logService, _optionsService, _oscLinkService, _coreMouseService, _unicodeService, _parser = new EscapeSequenceParser()) {
      super();
      this._bufferService = _bufferService;
      this._charsetService = _charsetService;
      this._coreService = _coreService;
      this._logService = _logService;
      this._optionsService = _optionsService;
      this._oscLinkService = _oscLinkService;
      this._coreMouseService = _coreMouseService;
      this._unicodeService = _unicodeService;
      this._parser = _parser;
      this._parseBuffer = new Uint32Array(4096);
      this._stringDecoder = new StringToUtf32();
      this._utf8Decoder = new Utf8ToUtf32();
      this._windowTitle = "";
      this._iconName = "";
      this._windowTitleStack = [];
      this._iconNameStack = [];
      this._curAttrData = DEFAULT_ATTR_DATA.clone();
      this._eraseAttrDataInternal = DEFAULT_ATTR_DATA.clone();
      this._onRequestBell = this._register(new Emitter());
      this.onRequestBell = this._onRequestBell.event;
      this._onRequestRefreshRows = this._register(new Emitter());
      this.onRequestRefreshRows = this._onRequestRefreshRows.event;
      this._onRequestReset = this._register(new Emitter());
      this.onRequestReset = this._onRequestReset.event;
      this._onRequestSendFocus = this._register(new Emitter());
      this.onRequestSendFocus = this._onRequestSendFocus.event;
      this._onRequestSyncScrollBar = this._register(new Emitter());
      this.onRequestSyncScrollBar = this._onRequestSyncScrollBar.event;
      this._onRequestWindowsOptionsReport = this._register(new Emitter());
      this.onRequestWindowsOptionsReport = this._onRequestWindowsOptionsReport.event;
      this._onA11yChar = this._register(new Emitter());
      this.onA11yChar = this._onA11yChar.event;
      this._onA11yTab = this._register(new Emitter());
      this.onA11yTab = this._onA11yTab.event;
      this._onCursorMove = this._register(new Emitter());
      this.onCursorMove = this._onCursorMove.event;
      this._onLineFeed = this._register(new Emitter());
      this.onLineFeed = this._onLineFeed.event;
      this._onScroll = this._register(new Emitter());
      this.onScroll = this._onScroll.event;
      this._onTitleChange = this._register(new Emitter());
      this.onTitleChange = this._onTitleChange.event;
      this._onColor = this._register(new Emitter());
      this.onColor = this._onColor.event;
      this._parseStack = {
        paused: false,
        cursorStartX: 0,
        cursorStartY: 0,
        decodedLength: 0,
        position: 0
      };
      // special colors - OSC 10 | 11 | 12
      this._specialColors = [256 /* FOREGROUND */, 257 /* BACKGROUND */, 258 /* CURSOR */];
      this._register(this._parser);
      this._dirtyRowTracker = new DirtyRowTracker(this._bufferService);
      this._activeBuffer = this._bufferService.buffer;
      this._register(this._bufferService.buffers.onBufferActivate((e) => this._activeBuffer = e.activeBuffer));
      this._parser.setCsiHandlerFallback((ident, params) => {
        this._logService.debug("Unknown CSI code: ", { identifier: this._parser.identToString(ident), params: params.toArray() });
      });
      this._parser.setEscHandlerFallback((ident) => {
        this._logService.debug("Unknown ESC code: ", { identifier: this._parser.identToString(ident) });
      });
      this._parser.setExecuteHandlerFallback((code) => {
        this._logService.debug("Unknown EXECUTE code: ", { code });
      });
      this._parser.setOscHandlerFallback((identifier, action, data) => {
        this._logService.debug("Unknown OSC code: ", { identifier, action, data });
      });
      this._parser.setDcsHandlerFallback((ident, action, payload) => {
        if (action === "HOOK") {
          payload = payload.toArray();
        }
        this._logService.debug("Unknown DCS code: ", { identifier: this._parser.identToString(ident), action, payload });
      });
      this._parser.setPrintHandler((data, start, end) => this.print(data, start, end));
      this._parser.registerCsiHandler({ final: "@" }, (params) => this.insertChars(params));
      this._parser.registerCsiHandler({ intermediates: " ", final: "@" }, (params) => this.scrollLeft(params));
      this._parser.registerCsiHandler({ final: "A" }, (params) => this.cursorUp(params));
      this._parser.registerCsiHandler({ intermediates: " ", final: "A" }, (params) => this.scrollRight(params));
      this._parser.registerCsiHandler({ final: "B" }, (params) => this.cursorDown(params));
      this._parser.registerCsiHandler({ final: "C" }, (params) => this.cursorForward(params));
      this._parser.registerCsiHandler({ final: "D" }, (params) => this.cursorBackward(params));
      this._parser.registerCsiHandler({ final: "E" }, (params) => this.cursorNextLine(params));
      this._parser.registerCsiHandler({ final: "F" }, (params) => this.cursorPrecedingLine(params));
      this._parser.registerCsiHandler({ final: "G" }, (params) => this.cursorCharAbsolute(params));
      this._parser.registerCsiHandler({ final: "H" }, (params) => this.cursorPosition(params));
      this._parser.registerCsiHandler({ final: "I" }, (params) => this.cursorForwardTab(params));
      this._parser.registerCsiHandler({ final: "J" }, (params) => this.eraseInDisplay(params, false));
      this._parser.registerCsiHandler({ prefix: "?", final: "J" }, (params) => this.eraseInDisplay(params, true));
      this._parser.registerCsiHandler({ final: "K" }, (params) => this.eraseInLine(params, false));
      this._parser.registerCsiHandler({ prefix: "?", final: "K" }, (params) => this.eraseInLine(params, true));
      this._parser.registerCsiHandler({ final: "L" }, (params) => this.insertLines(params));
      this._parser.registerCsiHandler({ final: "M" }, (params) => this.deleteLines(params));
      this._parser.registerCsiHandler({ final: "P" }, (params) => this.deleteChars(params));
      this._parser.registerCsiHandler({ final: "S" }, (params) => this.scrollUp(params));
      this._parser.registerCsiHandler({ final: "T" }, (params) => this.scrollDown(params));
      this._parser.registerCsiHandler({ final: "X" }, (params) => this.eraseChars(params));
      this._parser.registerCsiHandler({ final: "Z" }, (params) => this.cursorBackwardTab(params));
      this._parser.registerCsiHandler({ final: "`" }, (params) => this.charPosAbsolute(params));
      this._parser.registerCsiHandler({ final: "a" }, (params) => this.hPositionRelative(params));
      this._parser.registerCsiHandler({ final: "b" }, (params) => this.repeatPrecedingCharacter(params));
      this._parser.registerCsiHandler({ final: "c" }, (params) => this.sendDeviceAttributesPrimary(params));
      this._parser.registerCsiHandler({ prefix: ">", final: "c" }, (params) => this.sendDeviceAttributesSecondary(params));
      this._parser.registerCsiHandler({ final: "d" }, (params) => this.linePosAbsolute(params));
      this._parser.registerCsiHandler({ final: "e" }, (params) => this.vPositionRelative(params));
      this._parser.registerCsiHandler({ final: "f" }, (params) => this.hVPosition(params));
      this._parser.registerCsiHandler({ final: "g" }, (params) => this.tabClear(params));
      this._parser.registerCsiHandler({ final: "h" }, (params) => this.setMode(params));
      this._parser.registerCsiHandler({ prefix: "?", final: "h" }, (params) => this.setModePrivate(params));
      this._parser.registerCsiHandler({ final: "l" }, (params) => this.resetMode(params));
      this._parser.registerCsiHandler({ prefix: "?", final: "l" }, (params) => this.resetModePrivate(params));
      this._parser.registerCsiHandler({ final: "m" }, (params) => this.charAttributes(params));
      this._parser.registerCsiHandler({ final: "n" }, (params) => this.deviceStatus(params));
      this._parser.registerCsiHandler({ prefix: "?", final: "n" }, (params) => this.deviceStatusPrivate(params));
      this._parser.registerCsiHandler({ intermediates: "!", final: "p" }, (params) => this.softReset(params));
      this._parser.registerCsiHandler({ intermediates: " ", final: "q" }, (params) => this.setCursorStyle(params));
      this._parser.registerCsiHandler({ final: "r" }, (params) => this.setScrollRegion(params));
      this._parser.registerCsiHandler({ final: "s" }, (params) => this.saveCursor(params));
      this._parser.registerCsiHandler({ final: "t" }, (params) => this.windowOptions(params));
      this._parser.registerCsiHandler({ final: "u" }, (params) => this.restoreCursor(params));
      this._parser.registerCsiHandler({ intermediates: "'", final: "}" }, (params) => this.insertColumns(params));
      this._parser.registerCsiHandler({ intermediates: "'", final: "~" }, (params) => this.deleteColumns(params));
      this._parser.registerCsiHandler({ intermediates: '"', final: "q" }, (params) => this.selectProtected(params));
      this._parser.registerCsiHandler({ intermediates: "$", final: "p" }, (params) => this.requestMode(params, true));
      this._parser.registerCsiHandler({ prefix: "?", intermediates: "$", final: "p" }, (params) => this.requestMode(params, false));
      this._parser.setExecuteHandler(C0.BEL, () => this.bell());
      this._parser.setExecuteHandler(C0.LF, () => this.lineFeed());
      this._parser.setExecuteHandler(C0.VT, () => this.lineFeed());
      this._parser.setExecuteHandler(C0.FF, () => this.lineFeed());
      this._parser.setExecuteHandler(C0.CR, () => this.carriageReturn());
      this._parser.setExecuteHandler(C0.BS, () => this.backspace());
      this._parser.setExecuteHandler(C0.HT, () => this.tab());
      this._parser.setExecuteHandler(C0.SO, () => this.shiftOut());
      this._parser.setExecuteHandler(C0.SI, () => this.shiftIn());
      this._parser.setExecuteHandler(C1.IND, () => this.index());
      this._parser.setExecuteHandler(C1.NEL, () => this.nextLine());
      this._parser.setExecuteHandler(C1.HTS, () => this.tabSet());
      this._parser.registerOscHandler(0, new OscHandler((data) => {
        this.setTitle(data);
        this.setIconName(data);
        return true;
      }));
      this._parser.registerOscHandler(1, new OscHandler((data) => this.setIconName(data)));
      this._parser.registerOscHandler(2, new OscHandler((data) => this.setTitle(data)));
      this._parser.registerOscHandler(4, new OscHandler((data) => this.setOrReportIndexedColor(data)));
      this._parser.registerOscHandler(8, new OscHandler((data) => this.setHyperlink(data)));
      this._parser.registerOscHandler(10, new OscHandler((data) => this.setOrReportFgColor(data)));
      this._parser.registerOscHandler(11, new OscHandler((data) => this.setOrReportBgColor(data)));
      this._parser.registerOscHandler(12, new OscHandler((data) => this.setOrReportCursorColor(data)));
      this._parser.registerOscHandler(104, new OscHandler((data) => this.restoreIndexedColor(data)));
      this._parser.registerOscHandler(110, new OscHandler((data) => this.restoreFgColor(data)));
      this._parser.registerOscHandler(111, new OscHandler((data) => this.restoreBgColor(data)));
      this._parser.registerOscHandler(112, new OscHandler((data) => this.restoreCursorColor(data)));
      this._parser.registerEscHandler({ final: "7" }, () => this.saveCursor());
      this._parser.registerEscHandler({ final: "8" }, () => this.restoreCursor());
      this._parser.registerEscHandler({ final: "D" }, () => this.index());
      this._parser.registerEscHandler({ final: "E" }, () => this.nextLine());
      this._parser.registerEscHandler({ final: "H" }, () => this.tabSet());
      this._parser.registerEscHandler({ final: "M" }, () => this.reverseIndex());
      this._parser.registerEscHandler({ final: "=" }, () => this.keypadApplicationMode());
      this._parser.registerEscHandler({ final: ">" }, () => this.keypadNumericMode());
      this._parser.registerEscHandler({ final: "c" }, () => this.fullReset());
      this._parser.registerEscHandler({ final: "n" }, () => this.setgLevel(2));
      this._parser.registerEscHandler({ final: "o" }, () => this.setgLevel(3));
      this._parser.registerEscHandler({ final: "|" }, () => this.setgLevel(3));
      this._parser.registerEscHandler({ final: "}" }, () => this.setgLevel(2));
      this._parser.registerEscHandler({ final: "~" }, () => this.setgLevel(1));
      this._parser.registerEscHandler({ intermediates: "%", final: "@" }, () => this.selectDefaultCharset());
      this._parser.registerEscHandler({ intermediates: "%", final: "G" }, () => this.selectDefaultCharset());
      for (const flag in CHARSETS) {
        this._parser.registerEscHandler({ intermediates: "(", final: flag }, () => this.selectCharset("(" + flag));
        this._parser.registerEscHandler({ intermediates: ")", final: flag }, () => this.selectCharset(")" + flag));
        this._parser.registerEscHandler({ intermediates: "*", final: flag }, () => this.selectCharset("*" + flag));
        this._parser.registerEscHandler({ intermediates: "+", final: flag }, () => this.selectCharset("+" + flag));
        this._parser.registerEscHandler({ intermediates: "-", final: flag }, () => this.selectCharset("-" + flag));
        this._parser.registerEscHandler({ intermediates: ".", final: flag }, () => this.selectCharset("." + flag));
        this._parser.registerEscHandler({ intermediates: "/", final: flag }, () => this.selectCharset("/" + flag));
      }
      this._parser.registerEscHandler({ intermediates: "#", final: "8" }, () => this.screenAlignmentPattern());
      this._parser.setErrorHandler((state) => {
        this._logService.error("Parsing error: ", state);
        return state;
      });
      this._parser.registerDcsHandler({ intermediates: "$", final: "q" }, new DcsHandler((data, params) => this.requestStatusString(data, params)));
    }
    getAttrData() {
      return this._curAttrData;
    }
    /**
     * Async parse support.
     */
    _preserveStack(cursorStartX, cursorStartY, decodedLength, position) {
      this._parseStack.paused = true;
      this._parseStack.cursorStartX = cursorStartX;
      this._parseStack.cursorStartY = cursorStartY;
      this._parseStack.decodedLength = decodedLength;
      this._parseStack.position = position;
    }
    _logSlowResolvingAsync(p) {
      if (this._logService.logLevel <= 3 /* WARN */) {
        Promise.race([p, new Promise((res, rej) => setTimeout(() => rej("#SLOW_TIMEOUT"), SLOW_ASYNC_LIMIT))]).catch((err) => {
          if (err !== "#SLOW_TIMEOUT") {
            throw err;
          }
          console.warn(`async parser handler taking longer than ${SLOW_ASYNC_LIMIT} ms`);
        });
      }
    }
    _getCurrentLinkId() {
      return this._curAttrData.extended.urlId;
    }
    /**
     * Parse call with async handler support.
     *
     * Whether the stack state got preserved for the next call, is indicated by the return value:
     * - undefined (void):
     *   all handlers were sync, no stack save, continue normally with next chunk
     * - Promise\<boolean\>:
     *   execution stopped at async handler, stack saved, continue with same chunk and the promise
     *   resolve value as `promiseResult` until the method returns `undefined`
     *
     * Note: This method should only be called by `Terminal.write` to ensure correct execution order
     * and proper continuation of async parser handlers.
     */
    parse(data, promiseResult) {
      let result;
      let cursorStartX = this._activeBuffer.x;
      let cursorStartY = this._activeBuffer.y;
      let start = 0;
      const wasPaused = this._parseStack.paused;
      if (wasPaused) {
        if (result = this._parser.parse(this._parseBuffer, this._parseStack.decodedLength, promiseResult)) {
          this._logSlowResolvingAsync(result);
          return result;
        }
        cursorStartX = this._parseStack.cursorStartX;
        cursorStartY = this._parseStack.cursorStartY;
        this._parseStack.paused = false;
        if (data.length > MAX_PARSEBUFFER_LENGTH) {
          start = this._parseStack.position + MAX_PARSEBUFFER_LENGTH;
        }
      }
      if (this._logService.logLevel <= 1 /* DEBUG */) {
        this._logService.debug(`parsing data ${typeof data === "string" ? ` "${data}"` : ` "${Array.prototype.map.call(data, (e) => String.fromCharCode(e)).join("")}"`}`);
      }
      if (this._logService.logLevel === 0 /* TRACE */) {
        this._logService.trace(
          `parsing data (codes)`,
          typeof data === "string" ? data.split("").map((e) => e.charCodeAt(0)) : data
        );
      }
      if (this._parseBuffer.length < data.length) {
        if (this._parseBuffer.length < MAX_PARSEBUFFER_LENGTH) {
          this._parseBuffer = new Uint32Array(Math.min(data.length, MAX_PARSEBUFFER_LENGTH));
        }
      }
      if (!wasPaused) {
        this._dirtyRowTracker.clearRange();
      }
      if (data.length > MAX_PARSEBUFFER_LENGTH) {
        for (let i2 = start; i2 < data.length; i2 += MAX_PARSEBUFFER_LENGTH) {
          const end = i2 + MAX_PARSEBUFFER_LENGTH < data.length ? i2 + MAX_PARSEBUFFER_LENGTH : data.length;
          const len = typeof data === "string" ? this._stringDecoder.decode(data.substring(i2, end), this._parseBuffer) : this._utf8Decoder.decode(data.subarray(i2, end), this._parseBuffer);
          if (result = this._parser.parse(this._parseBuffer, len)) {
            this._preserveStack(cursorStartX, cursorStartY, len, i2);
            this._logSlowResolvingAsync(result);
            return result;
          }
        }
      } else {
        if (!wasPaused) {
          const len = typeof data === "string" ? this._stringDecoder.decode(data, this._parseBuffer) : this._utf8Decoder.decode(data, this._parseBuffer);
          if (result = this._parser.parse(this._parseBuffer, len)) {
            this._preserveStack(cursorStartX, cursorStartY, len, 0);
            this._logSlowResolvingAsync(result);
            return result;
          }
        }
      }
      if (this._activeBuffer.x !== cursorStartX || this._activeBuffer.y !== cursorStartY) {
        this._onCursorMove.fire();
      }
      const viewportEnd = this._dirtyRowTracker.end + (this._bufferService.buffer.ybase - this._bufferService.buffer.ydisp);
      const viewportStart = this._dirtyRowTracker.start + (this._bufferService.buffer.ybase - this._bufferService.buffer.ydisp);
      if (viewportStart < this._bufferService.rows) {
        this._onRequestRefreshRows.fire({
          start: Math.min(viewportStart, this._bufferService.rows - 1),
          end: Math.min(viewportEnd, this._bufferService.rows - 1)
        });
      }
    }
    print(data, start, end) {
      let code;
      let chWidth;
      const charset = this._charsetService.charset;
      const screenReaderMode = this._optionsService.rawOptions.screenReaderMode;
      const cols = this._bufferService.cols;
      const wraparoundMode = this._coreService.decPrivateModes.wraparound;
      const insertMode = this._coreService.modes.insertMode;
      const curAttr = this._curAttrData;
      let bufferRow = this._activeBuffer.lines.get(this._activeBuffer.ybase + this._activeBuffer.y);
      this._dirtyRowTracker.markDirty(this._activeBuffer.y);
      if (this._activeBuffer.x && end - start > 0 && bufferRow.getWidth(this._activeBuffer.x - 1) === 2) {
        bufferRow.setCellFromCodepoint(this._activeBuffer.x - 1, 0, 1, curAttr);
      }
      let precedingJoinState = this._parser.precedingJoinState;
      for (let pos = start; pos < end; ++pos) {
        code = data[pos];
        if (code < 127 && charset) {
          const ch = charset[String.fromCharCode(code)];
          if (ch) {
            code = ch.charCodeAt(0);
          }
        }
        const currentInfo = this._unicodeService.charProperties(code, precedingJoinState);
        chWidth = UnicodeService.extractWidth(currentInfo);
        const shouldJoin = UnicodeService.extractShouldJoin(currentInfo);
        const oldWidth = shouldJoin ? UnicodeService.extractWidth(precedingJoinState) : 0;
        precedingJoinState = currentInfo;
        if (screenReaderMode) {
          this._onA11yChar.fire(stringFromCodePoint(code));
        }
        if (this._getCurrentLinkId()) {
          this._oscLinkService.addLineToLink(this._getCurrentLinkId(), this._activeBuffer.ybase + this._activeBuffer.y);
        }
        if (this._activeBuffer.x + chWidth - oldWidth > cols) {
          if (wraparoundMode) {
            const oldRow = bufferRow;
            let oldCol = this._activeBuffer.x - oldWidth;
            this._activeBuffer.x = oldWidth;
            this._activeBuffer.y++;
            if (this._activeBuffer.y === this._activeBuffer.scrollBottom + 1) {
              this._activeBuffer.y--;
              this._bufferService.scroll(this._eraseAttrData(), true);
            } else {
              if (this._activeBuffer.y >= this._bufferService.rows) {
                this._activeBuffer.y = this._bufferService.rows - 1;
              }
              this._activeBuffer.lines.get(this._activeBuffer.ybase + this._activeBuffer.y).isWrapped = true;
            }
            bufferRow = this._activeBuffer.lines.get(this._activeBuffer.ybase + this._activeBuffer.y);
            if (oldWidth > 0 && bufferRow instanceof BufferLine) {
              bufferRow.copyCellsFrom(
                oldRow,
                oldCol,
                0,
                oldWidth,
                false
              );
            }
            while (oldCol < cols) {
              oldRow.setCellFromCodepoint(oldCol++, 0, 1, curAttr);
            }
          } else {
            this._activeBuffer.x = cols - 1;
            if (chWidth === 2) {
              continue;
            }
          }
        }
        if (shouldJoin && this._activeBuffer.x) {
          const offset = bufferRow.getWidth(this._activeBuffer.x - 1) ? 1 : 2;
          bufferRow.addCodepointToCell(
            this._activeBuffer.x - offset,
            code,
            chWidth
          );
          for (let delta = chWidth - oldWidth; --delta >= 0; ) {
            bufferRow.setCellFromCodepoint(this._activeBuffer.x++, 0, 0, curAttr);
          }
          continue;
        }
        if (insertMode) {
          bufferRow.insertCells(this._activeBuffer.x, chWidth - oldWidth, this._activeBuffer.getNullCell(curAttr));
          if (bufferRow.getWidth(cols - 1) === 2) {
            bufferRow.setCellFromCodepoint(cols - 1, NULL_CELL_CODE, NULL_CELL_WIDTH, curAttr);
          }
        }
        bufferRow.setCellFromCodepoint(this._activeBuffer.x++, code, chWidth, curAttr);
        if (chWidth > 0) {
          while (--chWidth) {
            bufferRow.setCellFromCodepoint(this._activeBuffer.x++, 0, 0, curAttr);
          }
        }
      }
      this._parser.precedingJoinState = precedingJoinState;
      if (this._activeBuffer.x < cols && end - start > 0 && bufferRow.getWidth(this._activeBuffer.x) === 0 && !bufferRow.hasContent(this._activeBuffer.x)) {
        bufferRow.setCellFromCodepoint(this._activeBuffer.x, 0, 1, curAttr);
      }
      this._dirtyRowTracker.markDirty(this._activeBuffer.y);
    }
    /**
     * Forward registerCsiHandler from parser.
     */
    registerCsiHandler(id2, callback) {
      if (id2.final === "t" && !id2.prefix && !id2.intermediates) {
        return this._parser.registerCsiHandler(id2, (params) => {
          if (!paramToWindowOption(params.params[0], this._optionsService.rawOptions.windowOptions)) {
            return true;
          }
          return callback(params);
        });
      }
      return this._parser.registerCsiHandler(id2, callback);
    }
    /**
     * Forward registerDcsHandler from parser.
     */
    registerDcsHandler(id2, callback) {
      return this._parser.registerDcsHandler(id2, new DcsHandler(callback));
    }
    /**
     * Forward registerEscHandler from parser.
     */
    registerEscHandler(id2, callback) {
      return this._parser.registerEscHandler(id2, callback);
    }
    /**
     * Forward registerOscHandler from parser.
     */
    registerOscHandler(ident, callback) {
      return this._parser.registerOscHandler(ident, new OscHandler(callback));
    }
    /**
     * BEL
     * Bell (Ctrl-G).
     *
     * @vt: #Y   C0    BEL   "Bell"  "\a, \x07"  "Ring the bell."
     * The behavior of the bell is further customizable with `ITerminalOptions.bellStyle`
     * and `ITerminalOptions.bellSound`.
     */
    bell() {
      this._onRequestBell.fire();
      return true;
    }
    /**
     * LF
     * Line Feed or New Line (NL).  (LF  is Ctrl-J).
     *
     * @vt: #Y   C0    LF   "Line Feed"            "\n, \x0A"  "Move the cursor one row down, scrolling if needed."
     * Scrolling is restricted to scroll margins and will only happen on the bottom line.
     *
     * @vt: #Y   C0    VT   "Vertical Tabulation"  "\v, \x0B"  "Treated as LF."
     * @vt: #Y   C0    FF   "Form Feed"            "\f, \x0C"  "Treated as LF."
     */
    lineFeed() {
      this._dirtyRowTracker.markDirty(this._activeBuffer.y);
      if (this._optionsService.rawOptions.convertEol) {
        this._activeBuffer.x = 0;
      }
      this._activeBuffer.y++;
      if (this._activeBuffer.y === this._activeBuffer.scrollBottom + 1) {
        this._activeBuffer.y--;
        this._bufferService.scroll(this._eraseAttrData());
      } else if (this._activeBuffer.y >= this._bufferService.rows) {
        this._activeBuffer.y = this._bufferService.rows - 1;
      } else {
        this._activeBuffer.lines.get(this._activeBuffer.ybase + this._activeBuffer.y).isWrapped = false;
      }
      if (this._activeBuffer.x >= this._bufferService.cols) {
        this._activeBuffer.x--;
      }
      this._dirtyRowTracker.markDirty(this._activeBuffer.y);
      this._onLineFeed.fire();
      return true;
    }
    /**
     * CR
     * Carriage Return (Ctrl-M).
     *
     * @vt: #Y   C0    CR   "Carriage Return"  "\r, \x0D"  "Move the cursor to the beginning of the row."
     */
    carriageReturn() {
      this._activeBuffer.x = 0;
      return true;
    }
    /**
     * BS
     * Backspace (Ctrl-H).
     *
     * @vt: #Y   C0    BS   "Backspace"  "\b, \x08"  "Move the cursor one position to the left."
     * By default it is not possible to move the cursor past the leftmost position.
     * If `reverse wrap-around` (`CSI ? 45 h`) is set, a previous soft line wrap (DECAWM)
     * can be undone with BS within the scroll margins. In that case the cursor will wrap back
     * to the end of the previous row. Note that it is not possible to peek back into the scrollbuffer
     * with the cursor, thus at the home position (top-leftmost cell) this has no effect.
     */
    backspace() {
      if (!this._coreService.decPrivateModes.reverseWraparound) {
        this._restrictCursor();
        if (this._activeBuffer.x > 0) {
          this._activeBuffer.x--;
        }
        return true;
      }
      this._restrictCursor(this._bufferService.cols);
      if (this._activeBuffer.x > 0) {
        this._activeBuffer.x--;
      } else {
        if (this._activeBuffer.x === 0 && this._activeBuffer.y > this._activeBuffer.scrollTop && this._activeBuffer.y <= this._activeBuffer.scrollBottom && this._activeBuffer.lines.get(this._activeBuffer.ybase + this._activeBuffer.y)?.isWrapped) {
          this._activeBuffer.lines.get(this._activeBuffer.ybase + this._activeBuffer.y).isWrapped = false;
          this._activeBuffer.y--;
          this._activeBuffer.x = this._bufferService.cols - 1;
          const line = this._activeBuffer.lines.get(this._activeBuffer.ybase + this._activeBuffer.y);
          if (line.hasWidth(this._activeBuffer.x) && !line.hasContent(this._activeBuffer.x)) {
            this._activeBuffer.x--;
          }
        }
      }
      this._restrictCursor();
      return true;
    }
    /**
     * TAB
     * Horizontal Tab (HT) (Ctrl-I).
     *
     * @vt: #Y   C0    HT   "Horizontal Tabulation"  "\t, \x09"  "Move the cursor to the next character tab stop."
     */
    tab() {
      if (this._activeBuffer.x >= this._bufferService.cols) {
        return true;
      }
      const originalX = this._activeBuffer.x;
      this._activeBuffer.x = this._activeBuffer.nextStop();
      if (this._optionsService.rawOptions.screenReaderMode) {
        this._onA11yTab.fire(this._activeBuffer.x - originalX);
      }
      return true;
    }
    /**
     * SO
     * Shift Out (Ctrl-N) -> Switch to Alternate Character Set.  This invokes the
     * G1 character set.
     *
     * @vt: #P[Only limited ISO-2022 charset support.]  C0    SO   "Shift Out"  "\x0E"  "Switch to an alternative character set."
     */
    shiftOut() {
      this._charsetService.setgLevel(1);
      return true;
    }
    /**
     * SI
     * Shift In (Ctrl-O) -> Switch to Standard Character Set.  This invokes the G0
     * character set (the default).
     *
     * @vt: #Y   C0    SI   "Shift In"   "\x0F"  "Return to regular character set after Shift Out."
     */
    shiftIn() {
      this._charsetService.setgLevel(0);
      return true;
    }
    /**
     * Restrict cursor to viewport size / scroll margin (origin mode).
     */
    _restrictCursor(maxCol = this._bufferService.cols - 1) {
      this._activeBuffer.x = Math.min(maxCol, Math.max(0, this._activeBuffer.x));
      this._activeBuffer.y = this._coreService.decPrivateModes.origin ? Math.min(this._activeBuffer.scrollBottom, Math.max(this._activeBuffer.scrollTop, this._activeBuffer.y)) : Math.min(this._bufferService.rows - 1, Math.max(0, this._activeBuffer.y));
      this._dirtyRowTracker.markDirty(this._activeBuffer.y);
    }
    /**
     * Set absolute cursor position.
     */
    _setCursor(x, y) {
      this._dirtyRowTracker.markDirty(this._activeBuffer.y);
      if (this._coreService.decPrivateModes.origin) {
        this._activeBuffer.x = x;
        this._activeBuffer.y = this._activeBuffer.scrollTop + y;
      } else {
        this._activeBuffer.x = x;
        this._activeBuffer.y = y;
      }
      this._restrictCursor();
      this._dirtyRowTracker.markDirty(this._activeBuffer.y);
    }
    /**
     * Set relative cursor position.
     */
    _moveCursor(x, y) {
      this._restrictCursor();
      this._setCursor(this._activeBuffer.x + x, this._activeBuffer.y + y);
    }
    /**
     * CSI Ps A
     * Cursor Up Ps Times (default = 1) (CUU).
     *
     * @vt: #Y CSI CUU   "Cursor Up"   "CSI Ps A"  "Move cursor `Ps` times up (default=1)."
     * If the cursor would pass the top scroll margin, it will stop there.
     */
    cursorUp(params) {
      const diffToTop = this._activeBuffer.y - this._activeBuffer.scrollTop;
      if (diffToTop >= 0) {
        this._moveCursor(0, -Math.min(diffToTop, params.params[0] || 1));
      } else {
        this._moveCursor(0, -(params.params[0] || 1));
      }
      return true;
    }
    /**
     * CSI Ps B
     * Cursor Down Ps Times (default = 1) (CUD).
     *
     * @vt: #Y CSI CUD   "Cursor Down"   "CSI Ps B"  "Move cursor `Ps` times down (default=1)."
     * If the cursor would pass the bottom scroll margin, it will stop there.
     */
    cursorDown(params) {
      const diffToBottom = this._activeBuffer.scrollBottom - this._activeBuffer.y;
      if (diffToBottom >= 0) {
        this._moveCursor(0, Math.min(diffToBottom, params.params[0] || 1));
      } else {
        this._moveCursor(0, params.params[0] || 1);
      }
      return true;
    }
    /**
     * CSI Ps C
     * Cursor Forward Ps Times (default = 1) (CUF).
     *
     * @vt: #Y CSI CUF   "Cursor Forward"    "CSI Ps C"  "Move cursor `Ps` times forward (default=1)."
     */
    cursorForward(params) {
      this._moveCursor(params.params[0] || 1, 0);
      return true;
    }
    /**
     * CSI Ps D
     * Cursor Backward Ps Times (default = 1) (CUB).
     *
     * @vt: #Y CSI CUB   "Cursor Backward"   "CSI Ps D"  "Move cursor `Ps` times backward (default=1)."
     */
    cursorBackward(params) {
      this._moveCursor(-(params.params[0] || 1), 0);
      return true;
    }
    /**
     * CSI Ps E
     * Cursor Next Line Ps Times (default = 1) (CNL).
     * Other than cursorDown (CUD) also set the cursor to first column.
     *
     * @vt: #Y CSI CNL   "Cursor Next Line"  "CSI Ps E"  "Move cursor `Ps` times down (default=1) and to the first column."
     * Same as CUD, additionally places the cursor at the first column.
     */
    cursorNextLine(params) {
      this.cursorDown(params);
      this._activeBuffer.x = 0;
      return true;
    }
    /**
     * CSI Ps F
     * Cursor Previous Line Ps Times (default = 1) (CPL).
     * Other than cursorUp (CUU) also set the cursor to first column.
     *
     * @vt: #Y CSI CPL   "Cursor Backward"   "CSI Ps F"  "Move cursor `Ps` times up (default=1) and to the first column."
     * Same as CUU, additionally places the cursor at the first column.
     */
    cursorPrecedingLine(params) {
      this.cursorUp(params);
      this._activeBuffer.x = 0;
      return true;
    }
    /**
     * CSI Ps G
     * Cursor Character Absolute  [column] (default = [row,1]) (CHA).
     *
     * @vt: #Y CSI CHA   "Cursor Horizontal Absolute" "CSI Ps G" "Move cursor to `Ps`-th column of the active row (default=1)."
     */
    cursorCharAbsolute(params) {
      this._setCursor((params.params[0] || 1) - 1, this._activeBuffer.y);
      return true;
    }
    /**
     * CSI Ps ; Ps H
     * Cursor Position [row;column] (default = [1,1]) (CUP).
     *
     * @vt: #Y CSI CUP   "Cursor Position"   "CSI Ps ; Ps H"  "Set cursor to position [`Ps`, `Ps`] (default = [1, 1])."
     * If ORIGIN mode is set, places the cursor to the absolute position within the scroll margins.
     * If ORIGIN mode is not set, places the cursor to the absolute position within the viewport.
     * Note that the coordinates are 1-based, thus the top left position starts at `1 ; 1`.
     */
    cursorPosition(params) {
      this._setCursor(
        // col
        params.length >= 2 ? (params.params[1] || 1) - 1 : 0,
        // row
        (params.params[0] || 1) - 1
      );
      return true;
    }
    /**
     * CSI Pm `  Character Position Absolute
     *   [column] (default = [row,1]) (HPA).
     * Currently same functionality as CHA.
     *
     * @vt: #Y CSI HPA   "Horizontal Position Absolute"  "CSI Ps ` " "Same as CHA."
     */
    charPosAbsolute(params) {
      this._setCursor((params.params[0] || 1) - 1, this._activeBuffer.y);
      return true;
    }
    /**
     * CSI Pm a  Character Position Relative
     *   [columns] (default = [row,col+1]) (HPR)
     *
     * @vt: #Y CSI HPR   "Horizontal Position Relative"  "CSI Ps a"  "Same as CUF."
     */
    hPositionRelative(params) {
      this._moveCursor(params.params[0] || 1, 0);
      return true;
    }
    /**
     * CSI Pm d  Vertical Position Absolute (VPA)
     *   [row] (default = [1,column])
     *
     * @vt: #Y CSI VPA   "Vertical Position Absolute"    "CSI Ps d"  "Move cursor to `Ps`-th row (default=1)."
     */
    linePosAbsolute(params) {
      this._setCursor(this._activeBuffer.x, (params.params[0] || 1) - 1);
      return true;
    }
    /**
     * CSI Pm e  Vertical Position Relative (VPR)
     *   [rows] (default = [row+1,column])
     * reuse CSI Ps B ?
     *
     * @vt: #Y CSI VPR   "Vertical Position Relative"    "CSI Ps e"  "Move cursor `Ps` times down (default=1)."
     */
    vPositionRelative(params) {
      this._moveCursor(0, params.params[0] || 1);
      return true;
    }
    /**
     * CSI Ps ; Ps f
     *   Horizontal and Vertical Position [row;column] (default =
     *   [1,1]) (HVP).
     *   Same as CUP.
     *
     * @vt: #Y CSI HVP   "Horizontal and Vertical Position" "CSI Ps ; Ps f"  "Same as CUP."
     */
    hVPosition(params) {
      this.cursorPosition(params);
      return true;
    }
    /**
     * CSI Ps g  Tab Clear (TBC).
     *     Ps = 0  -> Clear Current Column (default).
     *     Ps = 3  -> Clear All.
     * Potentially:
     *   Ps = 2  -> Clear Stops on Line.
     *   http://vt100.net/annarbor/aaa-ug/section6.html
     *
     * @vt: #Y CSI TBC   "Tab Clear" "CSI Ps g"  "Clear tab stops at current position (0) or all (3) (default=0)."
     * Clearing tabstops off the active row (Ps = 2, VT100) is currently not supported.
     */
    tabClear(params) {
      const param = params.params[0];
      if (param === 0) {
        delete this._activeBuffer.tabs[this._activeBuffer.x];
      } else if (param === 3) {
        this._activeBuffer.tabs = {};
      }
      return true;
    }
    /**
     * CSI Ps I
     *   Cursor Forward Tabulation Ps tab stops (default = 1) (CHT).
     *
     * @vt: #Y CSI CHT   "Cursor Horizontal Tabulation" "CSI Ps I" "Move cursor `Ps` times tabs forward (default=1)."
     */
    cursorForwardTab(params) {
      if (this._activeBuffer.x >= this._bufferService.cols) {
        return true;
      }
      let param = params.params[0] || 1;
      while (param--) {
        this._activeBuffer.x = this._activeBuffer.nextStop();
      }
      return true;
    }
    /**
     * CSI Ps Z  Cursor Backward Tabulation Ps tab stops (default = 1) (CBT).
     *
     * @vt: #Y CSI CBT   "Cursor Backward Tabulation"  "CSI Ps Z"  "Move cursor `Ps` tabs backward (default=1)."
     */
    cursorBackwardTab(params) {
      if (this._activeBuffer.x >= this._bufferService.cols) {
        return true;
      }
      let param = params.params[0] || 1;
      while (param--) {
        this._activeBuffer.x = this._activeBuffer.prevStop();
      }
      return true;
    }
    /**
     * CSI Ps " q  Select Character Protection Attribute (DECSCA).
     *
     * @vt: #Y CSI DECSCA   "Select Character Protection Attribute"  "CSI Ps " q"  "Whether DECSED and DECSEL can erase (0=default, 2) or not (1)."
     */
    selectProtected(params) {
      const p = params.params[0];
      if (p === 1) this._curAttrData.bg |= 536870912 /* PROTECTED */;
      if (p === 2 || p === 0) this._curAttrData.bg &= ~536870912 /* PROTECTED */;
      return true;
    }
    /**
     * Helper method to erase cells in a terminal row.
     * The cell gets replaced with the eraseChar of the terminal.
     * @param y The row index relative to the viewport.
     * @param start The start x index of the range to be erased.
     * @param end The end x index of the range to be erased (exclusive).
     * @param clearWrap clear the isWrapped flag
     * @param respectProtect Whether to respect the protection attribute (DECSCA).
     */
    _eraseInBufferLine(y, start, end, clearWrap = false, respectProtect = false) {
      const line = this._activeBuffer.lines.get(this._activeBuffer.ybase + y);
      line.replaceCells(
        start,
        end,
        this._activeBuffer.getNullCell(this._eraseAttrData()),
        respectProtect
      );
      if (clearWrap) {
        line.isWrapped = false;
      }
    }
    /**
     * Helper method to reset cells in a terminal row. The cell gets replaced with the eraseChar of
     * the terminal and the isWrapped property is set to false.
     * @param y row index
     */
    _resetBufferLine(y, respectProtect = false) {
      const line = this._activeBuffer.lines.get(this._activeBuffer.ybase + y);
      if (line) {
        line.fill(this._activeBuffer.getNullCell(this._eraseAttrData()), respectProtect);
        this._bufferService.buffer.clearMarkers(this._activeBuffer.ybase + y);
        line.isWrapped = false;
      }
    }
    /**
     * CSI Ps J  Erase in Display (ED).
     *     Ps = 0  -> Erase Below (default).
     *     Ps = 1  -> Erase Above.
     *     Ps = 2  -> Erase All.
     *     Ps = 3  -> Erase Saved Lines (xterm).
     * CSI ? Ps J
     *   Erase in Display (DECSED).
     *     Ps = 0  -> Selective Erase Below (default).
     *     Ps = 1  -> Selective Erase Above.
     *     Ps = 2  -> Selective Erase All.
     *
     * @vt: #Y CSI ED  "Erase In Display"  "CSI Ps J"  "Erase various parts of the viewport."
     * Supported param values:
     *
     * | Ps | Effect                                                       |
     * | -- | ------------------------------------------------------------ |
     * | 0  | Erase from the cursor through the end of the viewport.       |
     * | 1  | Erase from the beginning of the viewport through the cursor. |
     * | 2  | Erase complete viewport.                                     |
     * | 3  | Erase scrollback.                                            |
     *
     * @vt: #Y CSI DECSED   "Selective Erase In Display"  "CSI ? Ps J"  "Same as ED with respecting protection flag."
     */
    eraseInDisplay(params, respectProtect = false) {
      this._restrictCursor(this._bufferService.cols);
      let j;
      switch (params.params[0]) {
        case 0:
          j = this._activeBuffer.y;
          this._dirtyRowTracker.markDirty(j);
          this._eraseInBufferLine(j++, this._activeBuffer.x, this._bufferService.cols, this._activeBuffer.x === 0, respectProtect);
          for (; j < this._bufferService.rows; j++) {
            this._resetBufferLine(j, respectProtect);
          }
          this._dirtyRowTracker.markDirty(j);
          break;
        case 1:
          j = this._activeBuffer.y;
          this._dirtyRowTracker.markDirty(j);
          this._eraseInBufferLine(j, 0, this._activeBuffer.x + 1, true, respectProtect);
          if (this._activeBuffer.x + 1 >= this._bufferService.cols) {
            this._activeBuffer.lines.get(j + 1).isWrapped = false;
          }
          while (j--) {
            this._resetBufferLine(j, respectProtect);
          }
          this._dirtyRowTracker.markDirty(0);
          break;
        case 2:
          if (this._optionsService.rawOptions.scrollOnEraseInDisplay) {
            j = this._bufferService.rows;
            this._dirtyRowTracker.markRangeDirty(0, j - 1);
            while (j--) {
              const currentLine = this._activeBuffer.lines.get(this._activeBuffer.ybase + j);
              if (currentLine?.getTrimmedLength()) {
                break;
              }
            }
            for (; j >= 0; j--) {
              this._bufferService.scroll(this._eraseAttrData());
            }
          } else {
            j = this._bufferService.rows;
            this._dirtyRowTracker.markDirty(j - 1);
            while (j--) {
              this._resetBufferLine(j, respectProtect);
            }
            this._dirtyRowTracker.markDirty(0);
          }
          break;
        case 3:
          const scrollBackSize = this._activeBuffer.lines.length - this._bufferService.rows;
          if (scrollBackSize > 0) {
            this._activeBuffer.lines.trimStart(scrollBackSize);
            this._activeBuffer.ybase = Math.max(this._activeBuffer.ybase - scrollBackSize, 0);
            this._activeBuffer.ydisp = Math.max(this._activeBuffer.ydisp - scrollBackSize, 0);
            this._onScroll.fire(0);
          }
          break;
      }
      return true;
    }
    /**
     * CSI Ps K  Erase in Line (EL).
     *     Ps = 0  -> Erase to Right (default).
     *     Ps = 1  -> Erase to Left.
     *     Ps = 2  -> Erase All.
     * CSI ? Ps K
     *   Erase in Line (DECSEL).
     *     Ps = 0  -> Selective Erase to Right (default).
     *     Ps = 1  -> Selective Erase to Left.
     *     Ps = 2  -> Selective Erase All.
     *
     * @vt: #Y CSI EL    "Erase In Line"  "CSI Ps K"  "Erase various parts of the active row."
     * Supported param values:
     *
     * | Ps | Effect                                                   |
     * | -- | -------------------------------------------------------- |
     * | 0  | Erase from the cursor through the end of the row.        |
     * | 1  | Erase from the beginning of the line through the cursor. |
     * | 2  | Erase complete line.                                     |
     *
     * @vt: #Y CSI DECSEL   "Selective Erase In Line"  "CSI ? Ps K"  "Same as EL with respecting protecting flag."
     */
    eraseInLine(params, respectProtect = false) {
      this._restrictCursor(this._bufferService.cols);
      switch (params.params[0]) {
        case 0:
          this._eraseInBufferLine(this._activeBuffer.y, this._activeBuffer.x, this._bufferService.cols, this._activeBuffer.x === 0, respectProtect);
          break;
        case 1:
          this._eraseInBufferLine(this._activeBuffer.y, 0, this._activeBuffer.x + 1, false, respectProtect);
          break;
        case 2:
          this._eraseInBufferLine(this._activeBuffer.y, 0, this._bufferService.cols, true, respectProtect);
          break;
      }
      this._dirtyRowTracker.markDirty(this._activeBuffer.y);
      return true;
    }
    /**
     * CSI Ps L
     * Insert Ps Line(s) (default = 1) (IL).
     *
     * @vt: #Y CSI IL  "Insert Line"   "CSI Ps L"  "Insert `Ps` blank lines at active row (default=1)."
     * For every inserted line at the scroll top one line at the scroll bottom gets removed.
     * The cursor is set to the first column.
     * IL has no effect if the cursor is outside the scroll margins.
     */
    insertLines(params) {
      this._restrictCursor();
      let param = params.params[0] || 1;
      if (this._activeBuffer.y > this._activeBuffer.scrollBottom || this._activeBuffer.y < this._activeBuffer.scrollTop) {
        return true;
      }
      const row = this._activeBuffer.ybase + this._activeBuffer.y;
      const scrollBottomRowsOffset = this._bufferService.rows - 1 - this._activeBuffer.scrollBottom;
      const scrollBottomAbsolute = this._bufferService.rows - 1 + this._activeBuffer.ybase - scrollBottomRowsOffset + 1;
      while (param--) {
        this._activeBuffer.lines.splice(scrollBottomAbsolute - 1, 1);
        this._activeBuffer.lines.splice(row, 0, this._activeBuffer.getBlankLine(this._eraseAttrData()));
      }
      this._dirtyRowTracker.markRangeDirty(this._activeBuffer.y, this._activeBuffer.scrollBottom);
      this._activeBuffer.x = 0;
      return true;
    }
    /**
     * CSI Ps M
     * Delete Ps Line(s) (default = 1) (DL).
     *
     * @vt: #Y CSI DL  "Delete Line"   "CSI Ps M"  "Delete `Ps` lines at active row (default=1)."
     * For every deleted line at the scroll top one blank line at the scroll bottom gets appended.
     * The cursor is set to the first column.
     * DL has no effect if the cursor is outside the scroll margins.
     */
    deleteLines(params) {
      this._restrictCursor();
      let param = params.params[0] || 1;
      if (this._activeBuffer.y > this._activeBuffer.scrollBottom || this._activeBuffer.y < this._activeBuffer.scrollTop) {
        return true;
      }
      const row = this._activeBuffer.ybase + this._activeBuffer.y;
      let j;
      j = this._bufferService.rows - 1 - this._activeBuffer.scrollBottom;
      j = this._bufferService.rows - 1 + this._activeBuffer.ybase - j;
      while (param--) {
        this._activeBuffer.lines.splice(row, 1);
        this._activeBuffer.lines.splice(j, 0, this._activeBuffer.getBlankLine(this._eraseAttrData()));
      }
      this._dirtyRowTracker.markRangeDirty(this._activeBuffer.y, this._activeBuffer.scrollBottom);
      this._activeBuffer.x = 0;
      return true;
    }
    /**
     * CSI Ps @
     * Insert Ps (Blank) Character(s) (default = 1) (ICH).
     *
     * @vt: #Y CSI ICH  "Insert Characters"   "CSI Ps @"  "Insert `Ps` (blank) characters (default = 1)."
     * The ICH sequence inserts `Ps` blank characters. The cursor remains at the beginning of the
     * blank characters. Text between the cursor and right margin moves to the right. Characters moved
     * past the right margin are lost.
     *
     *
     * FIXME: check against xterm - should not work outside of scroll margins (see VT520 manual)
     */
    insertChars(params) {
      this._restrictCursor();
      const line = this._activeBuffer.lines.get(this._activeBuffer.ybase + this._activeBuffer.y);
      if (line) {
        line.insertCells(
          this._activeBuffer.x,
          params.params[0] || 1,
          this._activeBuffer.getNullCell(this._eraseAttrData())
        );
        this._dirtyRowTracker.markDirty(this._activeBuffer.y);
      }
      return true;
    }
    /**
     * CSI Ps P
     * Delete Ps Character(s) (default = 1) (DCH).
     *
     * @vt: #Y CSI DCH   "Delete Character"  "CSI Ps P"  "Delete `Ps` characters (default=1)."
     * As characters are deleted, the remaining characters between the cursor and right margin move to
     * the left. Character attributes move with the characters. The terminal adds blank characters at
     * the right margin.
     *
     *
     * FIXME: check against xterm - should not work outside of scroll margins (see VT520 manual)
     */
    deleteChars(params) {
      this._restrictCursor();
      const line = this._activeBuffer.lines.get(this._activeBuffer.ybase + this._activeBuffer.y);
      if (line) {
        line.deleteCells(
          this._activeBuffer.x,
          params.params[0] || 1,
          this._activeBuffer.getNullCell(this._eraseAttrData())
        );
        this._dirtyRowTracker.markDirty(this._activeBuffer.y);
      }
      return true;
    }
    /**
     * CSI Ps S  Scroll up Ps lines (default = 1) (SU).
     *
     * @vt: #Y CSI SU  "Scroll Up"   "CSI Ps S"  "Scroll `Ps` lines up (default=1)."
     *
     *
     * FIXME: scrolled out lines at top = 1 should add to scrollback (xterm)
     */
    scrollUp(params) {
      let param = params.params[0] || 1;
      while (param--) {
        this._activeBuffer.lines.splice(this._activeBuffer.ybase + this._activeBuffer.scrollTop, 1);
        this._activeBuffer.lines.splice(this._activeBuffer.ybase + this._activeBuffer.scrollBottom, 0, this._activeBuffer.getBlankLine(this._eraseAttrData()));
      }
      this._dirtyRowTracker.markRangeDirty(this._activeBuffer.scrollTop, this._activeBuffer.scrollBottom);
      return true;
    }
    /**
     * CSI Ps T  Scroll down Ps lines (default = 1) (SD).
     *
     * @vt: #Y CSI SD  "Scroll Down"   "CSI Ps T"  "Scroll `Ps` lines down (default=1)."
     */
    scrollDown(params) {
      let param = params.params[0] || 1;
      while (param--) {
        this._activeBuffer.lines.splice(this._activeBuffer.ybase + this._activeBuffer.scrollBottom, 1);
        this._activeBuffer.lines.splice(this._activeBuffer.ybase + this._activeBuffer.scrollTop, 0, this._activeBuffer.getBlankLine(DEFAULT_ATTR_DATA));
      }
      this._dirtyRowTracker.markRangeDirty(this._activeBuffer.scrollTop, this._activeBuffer.scrollBottom);
      return true;
    }
    /**
     * CSI Ps SP @  Scroll left Ps columns (default = 1) (SL) ECMA-48
     *
     * Notation: (Pn)
     * Representation: CSI Pn 02/00 04/00
     * Parameter default value: Pn = 1
     * SL causes the data in the presentation component to be moved by n character positions
     * if the line orientation is horizontal, or by n line positions if the line orientation
     * is vertical, such that the data appear to move to the left; where n equals the value of Pn.
     * The active presentation position is not affected by this control function.
     *
     * Supported:
     *   - always left shift (no line orientation setting respected)
     *
     * @vt: #Y CSI SL  "Scroll Left" "CSI Ps SP @" "Scroll viewport `Ps` times to the left."
     * SL moves the content of all lines within the scroll margins `Ps` times to the left.
     * SL has no effect outside of the scroll margins.
     */
    scrollLeft(params) {
      if (this._activeBuffer.y > this._activeBuffer.scrollBottom || this._activeBuffer.y < this._activeBuffer.scrollTop) {
        return true;
      }
      const param = params.params[0] || 1;
      for (let y = this._activeBuffer.scrollTop; y <= this._activeBuffer.scrollBottom; ++y) {
        const line = this._activeBuffer.lines.get(this._activeBuffer.ybase + y);
        line.deleteCells(0, param, this._activeBuffer.getNullCell(this._eraseAttrData()));
        line.isWrapped = false;
      }
      this._dirtyRowTracker.markRangeDirty(this._activeBuffer.scrollTop, this._activeBuffer.scrollBottom);
      return true;
    }
    /**
     * CSI Ps SP A  Scroll right Ps columns (default = 1) (SR) ECMA-48
     *
     * Notation: (Pn)
     * Representation: CSI Pn 02/00 04/01
     * Parameter default value: Pn = 1
     * SR causes the data in the presentation component to be moved by n character positions
     * if the line orientation is horizontal, or by n line positions if the line orientation
     * is vertical, such that the data appear to move to the right; where n equals the value of Pn.
     * The active presentation position is not affected by this control function.
     *
     * Supported:
     *   - always right shift (no line orientation setting respected)
     *
     * @vt: #Y CSI SR  "Scroll Right"  "CSI Ps SP A"   "Scroll viewport `Ps` times to the right."
     * SL moves the content of all lines within the scroll margins `Ps` times to the right.
     * Content at the right margin is lost.
     * SL has no effect outside of the scroll margins.
     */
    scrollRight(params) {
      if (this._activeBuffer.y > this._activeBuffer.scrollBottom || this._activeBuffer.y < this._activeBuffer.scrollTop) {
        return true;
      }
      const param = params.params[0] || 1;
      for (let y = this._activeBuffer.scrollTop; y <= this._activeBuffer.scrollBottom; ++y) {
        const line = this._activeBuffer.lines.get(this._activeBuffer.ybase + y);
        line.insertCells(0, param, this._activeBuffer.getNullCell(this._eraseAttrData()));
        line.isWrapped = false;
      }
      this._dirtyRowTracker.markRangeDirty(this._activeBuffer.scrollTop, this._activeBuffer.scrollBottom);
      return true;
    }
    /**
     * CSI Pm ' }
     * Insert Ps Column(s) (default = 1) (DECIC), VT420 and up.
     *
     * @vt: #Y CSI DECIC "Insert Columns"  "CSI Ps ' }"  "Insert `Ps` columns at cursor position."
     * DECIC inserts `Ps` times blank columns at the cursor position for all lines with the scroll
     * margins, moving content to the right. Content at the right margin is lost. DECIC has no effect
     * outside the scrolling margins.
     */
    insertColumns(params) {
      if (this._activeBuffer.y > this._activeBuffer.scrollBottom || this._activeBuffer.y < this._activeBuffer.scrollTop) {
        return true;
      }
      const param = params.params[0] || 1;
      for (let y = this._activeBuffer.scrollTop; y <= this._activeBuffer.scrollBottom; ++y) {
        const line = this._activeBuffer.lines.get(this._activeBuffer.ybase + y);
        line.insertCells(this._activeBuffer.x, param, this._activeBuffer.getNullCell(this._eraseAttrData()));
        line.isWrapped = false;
      }
      this._dirtyRowTracker.markRangeDirty(this._activeBuffer.scrollTop, this._activeBuffer.scrollBottom);
      return true;
    }
    /**
     * CSI Pm ' ~
     * Delete Ps Column(s) (default = 1) (DECDC), VT420 and up.
     *
     * @vt: #Y CSI DECDC "Delete Columns"  "CSI Ps ' ~"  "Delete `Ps` columns at cursor position."
     * DECDC deletes `Ps` times columns at the cursor position for all lines with the scroll margins,
     * moving content to the left. Blank columns are added at the right margin.
     * DECDC has no effect outside the scrolling margins.
     */
    deleteColumns(params) {
      if (this._activeBuffer.y > this._activeBuffer.scrollBottom || this._activeBuffer.y < this._activeBuffer.scrollTop) {
        return true;
      }
      const param = params.params[0] || 1;
      for (let y = this._activeBuffer.scrollTop; y <= this._activeBuffer.scrollBottom; ++y) {
        const line = this._activeBuffer.lines.get(this._activeBuffer.ybase + y);
        line.deleteCells(this._activeBuffer.x, param, this._activeBuffer.getNullCell(this._eraseAttrData()));
        line.isWrapped = false;
      }
      this._dirtyRowTracker.markRangeDirty(this._activeBuffer.scrollTop, this._activeBuffer.scrollBottom);
      return true;
    }
    /**
     * CSI Ps X
     * Erase Ps Character(s) (default = 1) (ECH).
     *
     * @vt: #Y CSI ECH   "Erase Character"   "CSI Ps X"  "Erase `Ps` characters from current cursor position to the right (default=1)."
     * ED erases `Ps` characters from current cursor position to the right.
     * ED works inside or outside the scrolling margins.
     */
    eraseChars(params) {
      this._restrictCursor();
      const line = this._activeBuffer.lines.get(this._activeBuffer.ybase + this._activeBuffer.y);
      if (line) {
        line.replaceCells(
          this._activeBuffer.x,
          this._activeBuffer.x + (params.params[0] || 1),
          this._activeBuffer.getNullCell(this._eraseAttrData())
        );
        this._dirtyRowTracker.markDirty(this._activeBuffer.y);
      }
      return true;
    }
    /**
     * CSI Ps b  Repeat the preceding graphic character Ps times (REP).
     * From ECMA 48 (@see http://www.ecma-international.org/publications/files/ECMA-ST/Ecma-048.pdf)
     *    Notation: (Pn)
     *    Representation: CSI Pn 06/02
     *    Parameter default value: Pn = 1
     *    REP is used to indicate that the preceding character in the data stream,
     *    if it is a graphic character (represented by one or more bit combinations) including SPACE,
     *    is to be repeated n times, where n equals the value of Pn.
     *    If the character preceding REP is a control function or part of a control function,
     *    the effect of REP is not defined by this Standard.
     *
     * We extend xterm's behavior to allow repeating entire grapheme clusters.
     * This isn't 100% xterm-compatible, but it seems saner and more useful.
     *    - text attrs are applied normally
     *    - wrap around is respected
     *    - any valid sequence resets the carried forward char
     *
     * Note: To get reset on a valid sequence working correctly without much runtime penalty, the
     * preceding codepoint is stored on the parser in `this.print` and reset during `parser.parse`.
     *
     * @vt: #Y CSI REP   "Repeat Preceding Character"    "CSI Ps b"  "Repeat preceding character `Ps` times (default=1)."
     * REP repeats the previous character `Ps` times advancing the cursor, also wrapping if DECAWM is
     * set. REP has no effect if the sequence does not follow a printable ASCII character
     * (NOOP for any other sequence in between or NON ASCII characters).
     */
    repeatPrecedingCharacter(params) {
      const joinState = this._parser.precedingJoinState;
      if (!joinState) {
        return true;
      }
      const length = params.params[0] || 1;
      const chWidth = UnicodeService.extractWidth(joinState);
      const x = this._activeBuffer.x - chWidth;
      const bufferRow = this._activeBuffer.lines.get(this._activeBuffer.ybase + this._activeBuffer.y);
      const text = bufferRow.getString(x);
      const data = new Uint32Array(text.length * length);
      let idata = 0;
      for (let itext = 0; itext < text.length; ) {
        const ch = text.codePointAt(itext) || 0;
        data[idata++] = ch;
        itext += ch > 65535 ? 2 : 1;
      }
      let tlength = idata;
      for (let i2 = 1; i2 < length; ++i2) {
        data.copyWithin(tlength, 0, idata);
        tlength += idata;
      }
      this.print(data, 0, tlength);
      return true;
    }
    /**
     * CSI Ps c  Send Device Attributes (Primary DA).
     *     Ps = 0  or omitted -> request attributes from terminal.  The
     *     response depends on the decTerminalID resource setting.
     *     -> CSI ? 1 ; 2 c  (``VT100 with Advanced Video Option'')
     *     -> CSI ? 1 ; 0 c  (``VT101 with No Options'')
     *     -> CSI ? 6 c  (``VT102'')
     *     -> CSI ? 6 0 ; 1 ; 2 ; 6 ; 8 ; 9 ; 1 5 ; c  (``VT220'')
     *   The VT100-style response parameters do not mean anything by
     *   themselves.  VT220 parameters do, telling the host what fea-
     *   tures the terminal supports:
     *     Ps = 1  -> 132-columns.
     *     Ps = 2  -> Printer.
     *     Ps = 6  -> Selective erase.
     *     Ps = 8  -> User-defined keys.
     *     Ps = 9  -> National replacement character sets.
     *     Ps = 1 5  -> Technical characters.
     *     Ps = 2 2  -> ANSI color, e.g., VT525.
     *     Ps = 2 9  -> ANSI text locator (i.e., DEC Locator mode).
     *
     * @vt: #Y CSI DA1   "Primary Device Attributes"     "CSI c"  "Send primary device attributes."
     *
     *
     * TODO: fix and cleanup response
     */
    sendDeviceAttributesPrimary(params) {
      if (params.params[0] > 0) {
        return true;
      }
      if (this._is("xterm") || this._is("rxvt-unicode") || this._is("screen")) {
        this._coreService.triggerDataEvent(C0.ESC + "[?1;2c");
      } else if (this._is("linux")) {
        this._coreService.triggerDataEvent(C0.ESC + "[?6c");
      }
      return true;
    }
    /**
     * CSI > Ps c
     *   Send Device Attributes (Secondary DA).
     *     Ps = 0  or omitted -> request the terminal's identification
     *     code.  The response depends on the decTerminalID resource set-
     *     ting.  It should apply only to VT220 and up, but xterm extends
     *     this to VT100.
     *     -> CSI  > Pp ; Pv ; Pc c
     *   where Pp denotes the terminal type
     *     Pp = 0  -> ``VT100''.
     *     Pp = 1  -> ``VT220''.
     *   and Pv is the firmware version (for xterm, this was originally
     *   the XFree86 patch number, starting with 95).  In a DEC termi-
     *   nal, Pc indicates the ROM cartridge registration number and is
     *   always zero.
     * More information:
     *   xterm/charproc.c - line 2012, for more information.
     *   vim responds with ^[[?0c or ^[[?1c after the terminal's response (?)
     *
     * @vt: #Y CSI DA2   "Secondary Device Attributes"   "CSI > c" "Send primary device attributes."
     *
     *
     * TODO: fix and cleanup response
     */
    sendDeviceAttributesSecondary(params) {
      if (params.params[0] > 0) {
        return true;
      }
      if (this._is("xterm")) {
        this._coreService.triggerDataEvent(C0.ESC + "[>0;276;0c");
      } else if (this._is("rxvt-unicode")) {
        this._coreService.triggerDataEvent(C0.ESC + "[>85;95;0c");
      } else if (this._is("linux")) {
        this._coreService.triggerDataEvent(params.params[0] + "c");
      } else if (this._is("screen")) {
        this._coreService.triggerDataEvent(C0.ESC + "[>83;40003;0c");
      }
      return true;
    }
    /**
     * Evaluate if the current terminal is the given argument.
     * @param term The terminal name to evaluate
     */
    _is(term) {
      return (this._optionsService.rawOptions.termName + "").indexOf(term) === 0;
    }
    /**
     * CSI Pm h  Set Mode (SM).
     *     Ps = 2  -> Keyboard Action Mode (AM).
     *     Ps = 4  -> Insert Mode (IRM).
     *     Ps = 1 2  -> Send/receive (SRM).
     *     Ps = 2 0  -> Automatic Newline (LNM).
     *
     * @vt: #P[Only IRM is supported.]    CSI SM    "Set Mode"  "CSI Pm h"  "Set various terminal modes."
     * Supported param values by SM:
     *
     * | Param | Action                                 | Support |
     * | ----- | -------------------------------------- | ------- |
     * | 2     | Keyboard Action Mode (KAM). Always on. | #N      |
     * | 4     | Insert Mode (IRM).                     | #Y      |
     * | 12    | Send/receive (SRM). Always off.        | #N      |
     * | 20    | Automatic Newline (LNM).               | #Y      |
     */
    setMode(params) {
      for (let i2 = 0; i2 < params.length; i2++) {
        switch (params.params[i2]) {
          case 4:
            this._coreService.modes.insertMode = true;
            break;
          case 20:
            this._optionsService.options.convertEol = true;
            break;
        }
      }
      return true;
    }
    /**
     * CSI ? Pm h
     *   DEC Private Mode Set (DECSET).
     *     Ps = 1  -> Application Cursor Keys (DECCKM).
     *     Ps = 2  -> Designate USASCII for character sets G0-G3
     *     (DECANM), and set VT100 mode.
     *     Ps = 3  -> 132 Column Mode (DECCOLM).
     *     Ps = 4  -> Smooth (Slow) Scroll (DECSCLM).
     *     Ps = 5  -> Reverse Video (DECSCNM).
     *     Ps = 6  -> Origin Mode (DECOM).
     *     Ps = 7  -> Wraparound Mode (DECAWM).
     *     Ps = 8  -> Auto-repeat Keys (DECARM).
     *     Ps = 9  -> Send Mouse X & Y on button press.  See the sec-
     *     tion Mouse Tracking.
     *     Ps = 1 0  -> Show toolbar (rxvt).
     *     Ps = 1 2  -> Start Blinking Cursor (att610).
     *     Ps = 1 8  -> Print form feed (DECPFF).
     *     Ps = 1 9  -> Set print extent to full screen (DECPEX).
     *     Ps = 2 5  -> Show Cursor (DECTCEM).
     *     Ps = 3 0  -> Show scrollbar (rxvt).
     *     Ps = 3 5  -> Enable font-shifting functions (rxvt).
     *     Ps = 3 8  -> Enter Tektronix Mode (DECTEK).
     *     Ps = 4 0  -> Allow 80 -> 132 Mode.
     *     Ps = 4 1  -> more(1) fix (see curses resource).
     *     Ps = 4 2  -> Enable Nation Replacement Character sets (DECN-
     *     RCM).
     *     Ps = 4 4  -> Turn On Margin Bell.
     *     Ps = 4 5  -> Reverse-wraparound Mode.
     *     Ps = 4 6  -> Start Logging.  This is normally disabled by a
     *     compile-time option.
     *     Ps = 4 7  -> Use Alternate Screen Buffer.  (This may be dis-
     *     abled by the titeInhibit resource).
     *     Ps = 6 6  -> Application keypad (DECNKM).
     *     Ps = 6 7  -> Backarrow key sends backspace (DECBKM).
     *     Ps = 1 0 0 0  -> Send Mouse X & Y on button press and
     *     release.  See the section Mouse Tracking.
     *     Ps = 1 0 0 1  -> Use Hilite Mouse Tracking.
     *     Ps = 1 0 0 2  -> Use Cell Motion Mouse Tracking.
     *     Ps = 1 0 0 3  -> Use All Motion Mouse Tracking.
     *     Ps = 1 0 0 4  -> Send FocusIn/FocusOut events.
     *     Ps = 1 0 0 5  -> Enable Extended Mouse Mode.
     *     Ps = 1 0 1 0  -> Scroll to bottom on tty output (rxvt).
     *     Ps = 1 0 1 1  -> Scroll to bottom on key press (rxvt).
     *     Ps = 1 0 3 4  -> Interpret "meta" key, sets eighth bit.
     *     (enables the eightBitInput resource).
     *     Ps = 1 0 3 5  -> Enable special modifiers for Alt and Num-
     *     Lock keys.  (This enables the numLock resource).
     *     Ps = 1 0 3 6  -> Send ESC   when Meta modifies a key.  (This
     *     enables the metaSendsEscape resource).
     *     Ps = 1 0 3 7  -> Send DEL from the editing-keypad Delete
     *     key.
     *     Ps = 1 0 3 9  -> Send ESC  when Alt modifies a key.  (This
     *     enables the altSendsEscape resource).
     *     Ps = 1 0 4 0  -> Keep selection even if not highlighted.
     *     (This enables the keepSelection resource).
     *     Ps = 1 0 4 1  -> Use the CLIPBOARD selection.  (This enables
     *     the selectToClipboard resource).
     *     Ps = 1 0 4 2  -> Enable Urgency window manager hint when
     *     Control-G is received.  (This enables the bellIsUrgent
     *     resource).
     *     Ps = 1 0 4 3  -> Enable raising of the window when Control-G
     *     is received.  (enables the popOnBell resource).
     *     Ps = 1 0 4 7  -> Use Alternate Screen Buffer.  (This may be
     *     disabled by the titeInhibit resource).
     *     Ps = 1 0 4 8  -> Save cursor as in DECSC.  (This may be dis-
     *     abled by the titeInhibit resource).
     *     Ps = 1 0 4 9  -> Save cursor as in DECSC and use Alternate
     *     Screen Buffer, clearing it first.  (This may be disabled by
     *     the titeInhibit resource).  This combines the effects of the 1
     *     0 4 7  and 1 0 4 8  modes.  Use this with terminfo-based
     *     applications rather than the 4 7  mode.
     *     Ps = 1 0 5 0  -> Set terminfo/termcap function-key mode.
     *     Ps = 1 0 5 1  -> Set Sun function-key mode.
     *     Ps = 1 0 5 2  -> Set HP function-key mode.
     *     Ps = 1 0 5 3  -> Set SCO function-key mode.
     *     Ps = 1 0 6 0  -> Set legacy keyboard emulation (X11R6).
     *     Ps = 1 0 6 1  -> Set VT220 keyboard emulation.
     *     Ps = 2 0 0 4  -> Set bracketed paste mode.
     * Modes:
     *   http: *vt100.net/docs/vt220-rm/chapter4.html
     *
     * @vt: #P[See below for supported modes.]    CSI DECSET  "DEC Private Set Mode" "CSI ? Pm h"  "Set various terminal attributes."
     * Supported param values by DECSET:
     *
     * | param | Action                                                  | Support |
     * | ----- | ------------------------------------------------------- | --------|
     * | 1     | Application Cursor Keys (DECCKM).                       | #Y      |
     * | 2     | Designate US-ASCII for character sets G0-G3 (DECANM).   | #Y      |
     * | 3     | 132 Column Mode (DECCOLM).                              | #Y      |
     * | 6     | Origin Mode (DECOM).                                    | #Y      |
     * | 7     | Auto-wrap Mode (DECAWM).                                | #Y      |
     * | 8     | Auto-repeat Keys (DECARM). Always on.                   | #N      |
     * | 9     | X10 xterm mouse protocol.                               | #Y      |
     * | 12    | Start Blinking Cursor.                                  | #Y      |
     * | 25    | Show Cursor (DECTCEM).                                  | #Y      |
     * | 45    | Reverse wrap-around.                                    | #Y      |
     * | 47    | Use Alternate Screen Buffer.                            | #Y      |
     * | 66    | Application keypad (DECNKM).                            | #Y      |
     * | 1000  | X11 xterm mouse protocol.                               | #Y      |
     * | 1002  | Use Cell Motion Mouse Tracking.                         | #Y      |
     * | 1003  | Use All Motion Mouse Tracking.                          | #Y      |
     * | 1004  | Send FocusIn/FocusOut events                            | #Y      |
     * | 1005  | Enable UTF-8 Mouse Mode.                                | #N      |
     * | 1006  | Enable SGR Mouse Mode.                                  | #Y      |
     * | 1015  | Enable urxvt Mouse Mode.                                | #N      |
     * | 1016  | Enable SGR-Pixels Mouse Mode.                           | #Y      |
     * | 1047  | Use Alternate Screen Buffer.                            | #Y      |
     * | 1048  | Save cursor as in DECSC.                                | #Y      |
     * | 1049  | Save cursor and switch to alternate buffer clearing it. | #P[Does not clear the alternate buffer.] |
     * | 2004  | Set bracketed paste mode.                               | #Y      |
     *
     *
     * FIXME: implement DECSCNM, 1049 should clear altbuffer
     */
    setModePrivate(params) {
      for (let i2 = 0; i2 < params.length; i2++) {
        switch (params.params[i2]) {
          case 1:
            this._coreService.decPrivateModes.applicationCursorKeys = true;
            break;
          case 2:
            this._charsetService.setgCharset(0, DEFAULT_CHARSET);
            this._charsetService.setgCharset(1, DEFAULT_CHARSET);
            this._charsetService.setgCharset(2, DEFAULT_CHARSET);
            this._charsetService.setgCharset(3, DEFAULT_CHARSET);
            break;
          case 3:
            if (this._optionsService.rawOptions.windowOptions.setWinLines) {
              this._bufferService.resize(132, this._bufferService.rows);
              this._onRequestReset.fire();
            }
            break;
          case 6:
            this._coreService.decPrivateModes.origin = true;
            this._setCursor(0, 0);
            break;
          case 7:
            this._coreService.decPrivateModes.wraparound = true;
            break;
          case 12:
            this._optionsService.options.cursorBlink = true;
            break;
          case 45:
            this._coreService.decPrivateModes.reverseWraparound = true;
            break;
          case 66:
            this._logService.debug("Serial port requested application keypad.");
            this._coreService.decPrivateModes.applicationKeypad = true;
            this._onRequestSyncScrollBar.fire();
            break;
          case 9:
            this._coreMouseService.activeProtocol = "X10";
            break;
          case 1e3:
            this._coreMouseService.activeProtocol = "VT200";
            break;
          case 1002:
            this._coreMouseService.activeProtocol = "DRAG";
            break;
          case 1003:
            this._coreMouseService.activeProtocol = "ANY";
            break;
          case 1004:
            this._coreService.decPrivateModes.sendFocus = true;
            this._onRequestSendFocus.fire();
            break;
          case 1005:
            this._logService.debug("DECSET 1005 not supported (see #2507)");
            break;
          case 1006:
            this._coreMouseService.activeEncoding = "SGR";
            break;
          case 1015:
            this._logService.debug("DECSET 1015 not supported (see #2507)");
            break;
          case 1016:
            this._coreMouseService.activeEncoding = "SGR_PIXELS";
            break;
          case 25:
            this._coreService.isCursorHidden = false;
            break;
          case 1048:
            this.saveCursor();
            break;
          case 1049:
            this.saveCursor();
          // FALL-THROUGH
          case 47:
          // alt screen buffer
          case 1047:
            this._bufferService.buffers.activateAltBuffer(this._eraseAttrData());
            this._coreService.isCursorInitialized = true;
            this._onRequestRefreshRows.fire(void 0);
            this._onRequestSyncScrollBar.fire();
            break;
          case 2004:
            this._coreService.decPrivateModes.bracketedPasteMode = true;
            break;
          case 2026:
            this._coreService.decPrivateModes.synchronizedOutput = true;
            break;
        }
      }
      return true;
    }
    /**
     * CSI Pm l  Reset Mode (RM).
     *     Ps = 2  -> Keyboard Action Mode (AM).
     *     Ps = 4  -> Replace Mode (IRM).
     *     Ps = 1 2  -> Send/receive (SRM).
     *     Ps = 2 0  -> Normal Linefeed (LNM).
     *
     * @vt: #P[Only IRM is supported.]    CSI RM    "Reset Mode"  "CSI Pm l"  "Set various terminal attributes."
     * Supported param values by RM:
     *
     * | Param | Action                                 | Support |
     * | ----- | -------------------------------------- | ------- |
     * | 2     | Keyboard Action Mode (KAM). Always on. | #N      |
     * | 4     | Replace Mode (IRM). (default)          | #Y      |
     * | 12    | Send/receive (SRM). Always off.        | #N      |
     * | 20    | Normal Linefeed (LNM).                 | #Y      |
     *
     *
     * FIXME: why is LNM commented out?
     */
    resetMode(params) {
      for (let i2 = 0; i2 < params.length; i2++) {
        switch (params.params[i2]) {
          case 4:
            this._coreService.modes.insertMode = false;
            break;
          case 20:
            this._optionsService.options.convertEol = false;
            break;
        }
      }
      return true;
    }
    /**
     * CSI ? Pm l
     *   DEC Private Mode Reset (DECRST).
     *     Ps = 1  -> Normal Cursor Keys (DECCKM).
     *     Ps = 2  -> Designate VT52 mode (DECANM).
     *     Ps = 3  -> 80 Column Mode (DECCOLM).
     *     Ps = 4  -> Jump (Fast) Scroll (DECSCLM).
     *     Ps = 5  -> Normal Video (DECSCNM).
     *     Ps = 6  -> Normal Cursor Mode (DECOM).
     *     Ps = 7  -> No Wraparound Mode (DECAWM).
     *     Ps = 8  -> No Auto-repeat Keys (DECARM).
     *     Ps = 9  -> Don't send Mouse X & Y on button press.
     *     Ps = 1 0  -> Hide toolbar (rxvt).
     *     Ps = 1 2  -> Stop Blinking Cursor (att610).
     *     Ps = 1 8  -> Don't print form feed (DECPFF).
     *     Ps = 1 9  -> Limit print to scrolling region (DECPEX).
     *     Ps = 2 5  -> Hide Cursor (DECTCEM).
     *     Ps = 3 0  -> Don't show scrollbar (rxvt).
     *     Ps = 3 5  -> Disable font-shifting functions (rxvt).
     *     Ps = 4 0  -> Disallow 80 -> 132 Mode.
     *     Ps = 4 1  -> No more(1) fix (see curses resource).
     *     Ps = 4 2  -> Disable Nation Replacement Character sets (DEC-
     *     NRCM).
     *     Ps = 4 4  -> Turn Off Margin Bell.
     *     Ps = 4 5  -> No Reverse-wraparound Mode.
     *     Ps = 4 6  -> Stop Logging.  (This is normally disabled by a
     *     compile-time option).
     *     Ps = 4 7  -> Use Normal Screen Buffer.
     *     Ps = 6 6  -> Numeric keypad (DECNKM).
     *     Ps = 6 7  -> Backarrow key sends delete (DECBKM).
     *     Ps = 1 0 0 0  -> Don't send Mouse X & Y on button press and
     *     release.  See the section Mouse Tracking.
     *     Ps = 1 0 0 1  -> Don't use Hilite Mouse Tracking.
     *     Ps = 1 0 0 2  -> Don't use Cell Motion Mouse Tracking.
     *     Ps = 1 0 0 3  -> Don't use All Motion Mouse Tracking.
     *     Ps = 1 0 0 4  -> Don't send FocusIn/FocusOut events.
     *     Ps = 1 0 0 5  -> Disable Extended Mouse Mode.
     *     Ps = 1 0 1 0  -> Don't scroll to bottom on tty output
     *     (rxvt).
     *     Ps = 1 0 1 1  -> Don't scroll to bottom on key press (rxvt).
     *     Ps = 1 0 3 4  -> Don't interpret "meta" key.  (This disables
     *     the eightBitInput resource).
     *     Ps = 1 0 3 5  -> Disable special modifiers for Alt and Num-
     *     Lock keys.  (This disables the numLock resource).
     *     Ps = 1 0 3 6  -> Don't send ESC  when Meta modifies a key.
     *     (This disables the metaSendsEscape resource).
     *     Ps = 1 0 3 7  -> Send VT220 Remove from the editing-keypad
     *     Delete key.
     *     Ps = 1 0 3 9  -> Don't send ESC  when Alt modifies a key.
     *     (This disables the altSendsEscape resource).
     *     Ps = 1 0 4 0  -> Do not keep selection when not highlighted.
     *     (This disables the keepSelection resource).
     *     Ps = 1 0 4 1  -> Use the PRIMARY selection.  (This disables
     *     the selectToClipboard resource).
     *     Ps = 1 0 4 2  -> Disable Urgency window manager hint when
     *     Control-G is received.  (This disables the bellIsUrgent
     *     resource).
     *     Ps = 1 0 4 3  -> Disable raising of the window when Control-
     *     G is received.  (This disables the popOnBell resource).
     *     Ps = 1 0 4 7  -> Use Normal Screen Buffer, clearing screen
     *     first if in the Alternate Screen.  (This may be disabled by
     *     the titeInhibit resource).
     *     Ps = 1 0 4 8  -> Restore cursor as in DECRC.  (This may be
     *     disabled by the titeInhibit resource).
     *     Ps = 1 0 4 9  -> Use Normal Screen Buffer and restore cursor
     *     as in DECRC.  (This may be disabled by the titeInhibit
     *     resource).  This combines the effects of the 1 0 4 7  and 1 0
     *     4 8  modes.  Use this with terminfo-based applications rather
     *     than the 4 7  mode.
     *     Ps = 1 0 5 0  -> Reset terminfo/termcap function-key mode.
     *     Ps = 1 0 5 1  -> Reset Sun function-key mode.
     *     Ps = 1 0 5 2  -> Reset HP function-key mode.
     *     Ps = 1 0 5 3  -> Reset SCO function-key mode.
     *     Ps = 1 0 6 0  -> Reset legacy keyboard emulation (X11R6).
     *     Ps = 1 0 6 1  -> Reset keyboard emulation to Sun/PC style.
     *     Ps = 2 0 0 4  -> Reset bracketed paste mode.
     *
     * @vt: #P[See below for supported modes.]    CSI DECRST  "DEC Private Reset Mode" "CSI ? Pm l"  "Reset various terminal attributes."
     * Supported param values by DECRST:
     *
     * | param | Action                                                  | Support |
     * | ----- | ------------------------------------------------------- | ------- |
     * | 1     | Normal Cursor Keys (DECCKM).                            | #Y      |
     * | 2     | Designate VT52 mode (DECANM).                           | #N      |
     * | 3     | 80 Column Mode (DECCOLM).                               | #B[Switches to old column width instead of 80.] |
     * | 6     | Normal Cursor Mode (DECOM).                             | #Y      |
     * | 7     | No Wraparound Mode (DECAWM).                            | #Y      |
     * | 8     | No Auto-repeat Keys (DECARM).                           | #N      |
     * | 9     | Don't send Mouse X & Y on button press.                 | #Y      |
     * | 12    | Stop Blinking Cursor.                                   | #Y      |
     * | 25    | Hide Cursor (DECTCEM).                                  | #Y      |
     * | 45    | No reverse wrap-around.                                 | #Y      |
     * | 47    | Use Normal Screen Buffer.                               | #Y      |
     * | 66    | Numeric keypad (DECNKM).                                | #Y      |
     * | 1000  | Don't send Mouse reports.                               | #Y      |
     * | 1002  | Don't use Cell Motion Mouse Tracking.                   | #Y      |
     * | 1003  | Don't use All Motion Mouse Tracking.                    | #Y      |
     * | 1004  | Don't send FocusIn/FocusOut events.                     | #Y      |
     * | 1005  | Disable UTF-8 Mouse Mode.                               | #N      |
     * | 1006  | Disable SGR Mouse Mode.                                 | #Y      |
     * | 1015  | Disable urxvt Mouse Mode.                               | #N      |
     * | 1016  | Disable SGR-Pixels Mouse Mode.                          | #Y      |
     * | 1047  | Use Normal Screen Buffer (clearing screen if in alt).   | #Y      |
     * | 1048  | Restore cursor as in DECRC.                             | #Y      |
     * | 1049  | Use Normal Screen Buffer and restore cursor.            | #Y      |
     * | 2004  | Reset bracketed paste mode.                             | #Y      |
     *
     *
     * FIXME: DECCOLM is currently broken (already fixed in window options PR)
     */
    resetModePrivate(params) {
      for (let i2 = 0; i2 < params.length; i2++) {
        switch (params.params[i2]) {
          case 1:
            this._coreService.decPrivateModes.applicationCursorKeys = false;
            break;
          case 3:
            if (this._optionsService.rawOptions.windowOptions.setWinLines) {
              this._bufferService.resize(80, this._bufferService.rows);
              this._onRequestReset.fire();
            }
            break;
          case 6:
            this._coreService.decPrivateModes.origin = false;
            this._setCursor(0, 0);
            break;
          case 7:
            this._coreService.decPrivateModes.wraparound = false;
            break;
          case 12:
            this._optionsService.options.cursorBlink = false;
            break;
          case 45:
            this._coreService.decPrivateModes.reverseWraparound = false;
            break;
          case 66:
            this._logService.debug("Switching back to normal keypad.");
            this._coreService.decPrivateModes.applicationKeypad = false;
            this._onRequestSyncScrollBar.fire();
            break;
          case 9:
          // X10 Mouse
          case 1e3:
          // vt200 mouse
          case 1002:
          // button event mouse
          case 1003:
            this._coreMouseService.activeProtocol = "NONE";
            break;
          case 1004:
            this._coreService.decPrivateModes.sendFocus = false;
            break;
          case 1005:
            this._logService.debug("DECRST 1005 not supported (see #2507)");
            break;
          case 1006:
            this._coreMouseService.activeEncoding = "DEFAULT";
            break;
          case 1015:
            this._logService.debug("DECRST 1015 not supported (see #2507)");
            break;
          case 1016:
            this._coreMouseService.activeEncoding = "DEFAULT";
            break;
          case 25:
            this._coreService.isCursorHidden = true;
            break;
          case 1048:
            this.restoreCursor();
            break;
          case 1049:
          // alt screen buffer cursor
          // FALL-THROUGH
          case 47:
          // normal screen buffer
          case 1047:
            this._bufferService.buffers.activateNormalBuffer();
            if (params.params[i2] === 1049) {
              this.restoreCursor();
            }
            this._coreService.isCursorInitialized = true;
            this._onRequestRefreshRows.fire(void 0);
            this._onRequestSyncScrollBar.fire();
            break;
          case 2004:
            this._coreService.decPrivateModes.bracketedPasteMode = false;
            break;
          case 2026:
            this._coreService.decPrivateModes.synchronizedOutput = false;
            this._onRequestRefreshRows.fire(void 0);
            break;
        }
      }
      return true;
    }
    /**
     * CSI Ps $ p Request ANSI Mode (DECRQM).
     *
     * Reports CSI Ps; Pm $ y (DECRPM), where Ps is the mode number as in SM/RM,
     * and Pm is the mode value:
     *    0 - not recognized
     *    1 - set
     *    2 - reset
     *    3 - permanently set
     *    4 - permanently reset
     *
     * @vt: #Y  CSI   DECRQM  "Request Mode"  "CSI Ps $p"  "Request mode state."
     * Returns a report as `CSI Ps; Pm $ y` (DECRPM), where `Ps` is the mode number as in SM/RM
     * or DECSET/DECRST, and `Pm` is the mode value:
     * - 0: not recognized
     * - 1: set
     * - 2: reset
     * - 3: permanently set
     * - 4: permanently reset
     *
     * For modes not understood xterm.js always returns `notRecognized`. In general this means,
     * that a certain operation mode is not implemented and cannot be used.
     *
     * Modes changing the active terminal buffer (47, 1047, 1049) are not subqueried
     * and only report, whether the alternate buffer is set.
     *
     * Mouse encodings and mouse protocols are handled mutual exclusive,
     * thus only one of each of those can be set at a given time.
     *
     * There is a chance, that some mode reports are not fully in line with xterm.js' behavior,
     * e.g. if the default implementation already exposes a certain behavior. If you find
     * discrepancies in the mode reports, please file a bug.
     */
    requestMode(params, ansi) {
      let V;
      ((V2) => {
        V2[V2["NOT_RECOGNIZED"] = 0] = "NOT_RECOGNIZED";
        V2[V2["SET"] = 1] = "SET";
        V2[V2["RESET"] = 2] = "RESET";
        V2[V2["PERMANENTLY_SET"] = 3] = "PERMANENTLY_SET";
        V2[V2["PERMANENTLY_RESET"] = 4] = "PERMANENTLY_RESET";
      })(V || (V = {}));
      const dm = this._coreService.decPrivateModes;
      const { activeProtocol: mouseProtocol, activeEncoding: mouseEncoding } = this._coreMouseService;
      const cs = this._coreService;
      const { buffers, cols } = this._bufferService;
      const { active, alt } = buffers;
      const opts = this._optionsService.rawOptions;
      const f = (m, v) => {
        cs.triggerDataEvent(`${C0.ESC}[${ansi ? "" : "?"}${m};${v}$y`);
        return true;
      };
      const b2v = (value) => value ? 1 /* SET */ : 2 /* RESET */;
      const p = params.params[0];
      if (ansi) {
        if (p === 2) return f(p, 4 /* PERMANENTLY_RESET */);
        if (p === 4) return f(p, b2v(cs.modes.insertMode));
        if (p === 12) return f(p, 3 /* PERMANENTLY_SET */);
        if (p === 20) return f(p, b2v(opts.convertEol));
        return f(p, 0 /* NOT_RECOGNIZED */);
      }
      if (p === 1) return f(p, b2v(dm.applicationCursorKeys));
      if (p === 3) return f(p, opts.windowOptions.setWinLines ? cols === 80 ? 2 /* RESET */ : cols === 132 ? 1 /* SET */ : 0 /* NOT_RECOGNIZED */ : 0 /* NOT_RECOGNIZED */);
      if (p === 6) return f(p, b2v(dm.origin));
      if (p === 7) return f(p, b2v(dm.wraparound));
      if (p === 8) return f(p, 3 /* PERMANENTLY_SET */);
      if (p === 9) return f(p, b2v(mouseProtocol === "X10"));
      if (p === 12) return f(p, b2v(opts.cursorBlink));
      if (p === 25) return f(p, b2v(!cs.isCursorHidden));
      if (p === 45) return f(p, b2v(dm.reverseWraparound));
      if (p === 66) return f(p, b2v(dm.applicationKeypad));
      if (p === 67) return f(p, 4 /* PERMANENTLY_RESET */);
      if (p === 1e3) return f(p, b2v(mouseProtocol === "VT200"));
      if (p === 1002) return f(p, b2v(mouseProtocol === "DRAG"));
      if (p === 1003) return f(p, b2v(mouseProtocol === "ANY"));
      if (p === 1004) return f(p, b2v(dm.sendFocus));
      if (p === 1005) return f(p, 4 /* PERMANENTLY_RESET */);
      if (p === 1006) return f(p, b2v(mouseEncoding === "SGR"));
      if (p === 1015) return f(p, 4 /* PERMANENTLY_RESET */);
      if (p === 1016) return f(p, b2v(mouseEncoding === "SGR_PIXELS"));
      if (p === 1048) return f(p, 1 /* SET */);
      if (p === 47 || p === 1047 || p === 1049) return f(p, b2v(active === alt));
      if (p === 2004) return f(p, b2v(dm.bracketedPasteMode));
      if (p === 2026) return f(p, b2v(dm.synchronizedOutput));
      return f(p, 0 /* NOT_RECOGNIZED */);
    }
    /**
     * Helper to write color information packed with color mode.
     */
    _updateAttrColor(color2, mode, c1, c2, c3) {
      if (mode === 2) {
        color2 |= 50331648 /* CM_RGB */;
        color2 &= ~16777215 /* RGB_MASK */;
        color2 |= AttributeData.fromColorRGB([c1, c2, c3]);
      } else if (mode === 5) {
        color2 &= ~(50331648 /* CM_MASK */ | 255 /* PCOLOR_MASK */);
        color2 |= 33554432 /* CM_P256 */ | c1 & 255;
      }
      return color2;
    }
    /**
     * Helper to extract and apply color params/subparams.
     * Returns advance for params index.
     */
    _extractColor(params, pos, attr) {
      const accu = [0, 0, -1, 0, 0, 0];
      let cSpace = 0;
      let advance = 0;
      do {
        accu[advance + cSpace] = params.params[pos + advance];
        if (params.hasSubParams(pos + advance)) {
          const subparams = params.getSubParams(pos + advance);
          let i2 = 0;
          do {
            if (accu[1] === 5) {
              cSpace = 1;
            }
            accu[advance + i2 + 1 + cSpace] = subparams[i2];
          } while (++i2 < subparams.length && i2 + advance + 1 + cSpace < accu.length);
          break;
        }
        if (accu[1] === 5 && advance + cSpace >= 2 || accu[1] === 2 && advance + cSpace >= 5) {
          break;
        }
        if (accu[1]) {
          cSpace = 1;
        }
      } while (++advance + pos < params.length && advance + cSpace < accu.length);
      for (let i2 = 2; i2 < accu.length; ++i2) {
        if (accu[i2] === -1) {
          accu[i2] = 0;
        }
      }
      switch (accu[0]) {
        case 38:
          attr.fg = this._updateAttrColor(attr.fg, accu[1], accu[3], accu[4], accu[5]);
          break;
        case 48:
          attr.bg = this._updateAttrColor(attr.bg, accu[1], accu[3], accu[4], accu[5]);
          break;
        case 58:
          attr.extended = attr.extended.clone();
          attr.extended.underlineColor = this._updateAttrColor(attr.extended.underlineColor, accu[1], accu[3], accu[4], accu[5]);
      }
      return advance;
    }
    /**
     * SGR 4 subparams:
     *    4:0   -   equal to SGR 24 (turn off all underline)
     *    4:1   -   equal to SGR 4 (single underline)
     *    4:2   -   equal to SGR 21 (double underline)
     *    4:3   -   curly underline
     *    4:4   -   dotted underline
     *    4:5   -   dashed underline
     */
    _processUnderline(style, attr) {
      attr.extended = attr.extended.clone();
      if (!~style || style > 5) {
        style = 1;
      }
      attr.extended.underlineStyle = style;
      attr.fg |= 268435456 /* UNDERLINE */;
      if (style === 0) {
        attr.fg &= ~268435456 /* UNDERLINE */;
      }
      attr.updateExtended();
    }
    _processSGR0(attr) {
      attr.fg = DEFAULT_ATTR_DATA.fg;
      attr.bg = DEFAULT_ATTR_DATA.bg;
      attr.extended = attr.extended.clone();
      attr.extended.underlineStyle = 0 /* NONE */;
      attr.extended.underlineColor &= ~(50331648 /* CM_MASK */ | 16777215 /* RGB_MASK */);
      attr.updateExtended();
    }
    /**
     * CSI Pm m  Character Attributes (SGR).
     *
     * @vt: #P[See below for supported attributes.]    CSI SGR   "Select Graphic Rendition"  "CSI Pm m"  "Set/Reset various text attributes."
     * SGR selects one or more character attributes at the same time. Multiple params (up to 32)
     * are applied in order from left to right. The changed attributes are applied to all new
     * characters received. If you move characters in the viewport by scrolling or any other means,
     * then the attributes move with the characters.
     *
     * Supported param values by SGR:
     *
     * | Param     | Meaning                                                  | Support |
     * | --------- | -------------------------------------------------------- | ------- |
     * | 0         | Normal (default). Resets any other preceding SGR.        | #Y      |
     * | 1         | Bold. (also see `options.drawBoldTextInBrightColors`)    | #Y      |
     * | 2         | Faint, decreased intensity.                              | #Y      |
     * | 3         | Italic.                                                  | #Y      |
     * | 4         | Underlined (see below for style support).                | #Y      |
     * | 5         | Slowly blinking.                                         | #N      |
     * | 6         | Rapidly blinking.                                        | #N      |
     * | 7         | Inverse. Flips foreground and background color.          | #Y      |
     * | 8         | Invisible (hidden).                                      | #Y      |
     * | 9         | Crossed-out characters (strikethrough).                  | #Y      |
     * | 21        | Doubly underlined.                                       | #Y      |
     * | 22        | Normal (neither bold nor faint).                         | #Y      |
     * | 23        | No italic.                                               | #Y      |
     * | 24        | Not underlined.                                          | #Y      |
     * | 25        | Steady (not blinking).                                   | #Y      |
     * | 27        | Positive (not inverse).                                  | #Y      |
     * | 28        | Visible (not hidden).                                    | #Y      |
     * | 29        | Not Crossed-out (strikethrough).                         | #Y      |
     * | 30        | Foreground color: Black.                                 | #Y      |
     * | 31        | Foreground color: Red.                                   | #Y      |
     * | 32        | Foreground color: Green.                                 | #Y      |
     * | 33        | Foreground color: Yellow.                                | #Y      |
     * | 34        | Foreground color: Blue.                                  | #Y      |
     * | 35        | Foreground color: Magenta.                               | #Y      |
     * | 36        | Foreground color: Cyan.                                  | #Y      |
     * | 37        | Foreground color: White.                                 | #Y      |
     * | 38        | Foreground color: Extended color.                        | #P[Support for RGB and indexed colors, see below.] |
     * | 39        | Foreground color: Default (original).                    | #Y      |
     * | 40        | Background color: Black.                                 | #Y      |
     * | 41        | Background color: Red.                                   | #Y      |
     * | 42        | Background color: Green.                                 | #Y      |
     * | 43        | Background color: Yellow.                                | #Y      |
     * | 44        | Background color: Blue.                                  | #Y      |
     * | 45        | Background color: Magenta.                               | #Y      |
     * | 46        | Background color: Cyan.                                  | #Y      |
     * | 47        | Background color: White.                                 | #Y      |
     * | 48        | Background color: Extended color.                        | #P[Support for RGB and indexed colors, see below.] |
     * | 49        | Background color: Default (original).                    | #Y      |
     * | 53        | Overlined.                                               | #Y      |
     * | 55        | Not Overlined.                                           | #Y      |
     * | 58        | Underline color: Extended color.                         | #P[Support for RGB and indexed colors, see below.] |
     * | 90 - 97   | Bright foreground color (analogous to 30 - 37).          | #Y      |
     * | 100 - 107 | Bright background color (analogous to 40 - 47).          | #Y      |
     *
     * Underline supports subparams to denote the style in the form `4 : x`:
     *
     * | x      | Meaning                                                       | Support |
     * | ------ | ------------------------------------------------------------- | ------- |
     * | 0      | No underline. Same as `SGR 24 m`.                             | #Y      |
     * | 1      | Single underline. Same as `SGR 4 m`.                          | #Y      |
     * | 2      | Double underline.                                             | #Y      |
     * | 3      | Curly underline.                                              | #Y      |
     * | 4      | Dotted underline.                                             | #Y      |
     * | 5      | Dashed underline.                                             | #Y      |
     * | other  | Single underline. Same as `SGR 4 m`.                          | #Y      |
     *
     * Extended colors are supported for foreground (Ps=38), background (Ps=48) and underline (Ps=58)
     * as follows:
     *
     * | Ps + 1 | Meaning                                                       | Support |
     * | ------ | ------------------------------------------------------------- | ------- |
     * | 0      | Implementation defined.                                       | #N      |
     * | 1      | Transparent.                                                  | #N      |
     * | 2      | RGB color as `Ps ; 2 ; R ; G ; B` or `Ps : 2 : : R : G : B`.  | #Y      |
     * | 3      | CMY color.                                                    | #N      |
     * | 4      | CMYK color.                                                   | #N      |
     * | 5      | Indexed (256 colors) as `Ps ; 5 ; INDEX` or `Ps : 5 : INDEX`. | #Y      |
     *
     *
     * FIXME: blinking is implemented in attrs, but not working in renderers?
     * FIXME: remove dead branch for p=100
     */
    charAttributes(params) {
      if (params.length === 1 && params.params[0] === 0) {
        this._processSGR0(this._curAttrData);
        return true;
      }
      const l = params.length;
      let p;
      const attr = this._curAttrData;
      for (let i2 = 0; i2 < l; i2++) {
        p = params.params[i2];
        if (p >= 30 && p <= 37) {
          attr.fg &= ~(50331648 /* CM_MASK */ | 255 /* PCOLOR_MASK */);
          attr.fg |= 16777216 /* CM_P16 */ | p - 30;
        } else if (p >= 40 && p <= 47) {
          attr.bg &= ~(50331648 /* CM_MASK */ | 255 /* PCOLOR_MASK */);
          attr.bg |= 16777216 /* CM_P16 */ | p - 40;
        } else if (p >= 90 && p <= 97) {
          attr.fg &= ~(50331648 /* CM_MASK */ | 255 /* PCOLOR_MASK */);
          attr.fg |= 16777216 /* CM_P16 */ | p - 90 | 8;
        } else if (p >= 100 && p <= 107) {
          attr.bg &= ~(50331648 /* CM_MASK */ | 255 /* PCOLOR_MASK */);
          attr.bg |= 16777216 /* CM_P16 */ | p - 100 | 8;
        } else if (p === 0) {
          this._processSGR0(attr);
        } else if (p === 1) {
          attr.fg |= 134217728 /* BOLD */;
        } else if (p === 3) {
          attr.bg |= 67108864 /* ITALIC */;
        } else if (p === 4) {
          attr.fg |= 268435456 /* UNDERLINE */;
          this._processUnderline(params.hasSubParams(i2) ? params.getSubParams(i2)[0] : 1 /* SINGLE */, attr);
        } else if (p === 5) {
          attr.fg |= 536870912 /* BLINK */;
        } else if (p === 7) {
          attr.fg |= 67108864 /* INVERSE */;
        } else if (p === 8) {
          attr.fg |= 1073741824 /* INVISIBLE */;
        } else if (p === 9) {
          attr.fg |= 2147483648 /* STRIKETHROUGH */;
        } else if (p === 2) {
          attr.bg |= 134217728 /* DIM */;
        } else if (p === 21) {
          this._processUnderline(2 /* DOUBLE */, attr);
        } else if (p === 22) {
          attr.fg &= ~134217728 /* BOLD */;
          attr.bg &= ~134217728 /* DIM */;
        } else if (p === 23) {
          attr.bg &= ~67108864 /* ITALIC */;
        } else if (p === 24) {
          attr.fg &= ~268435456 /* UNDERLINE */;
          this._processUnderline(0 /* NONE */, attr);
        } else if (p === 25) {
          attr.fg &= ~536870912 /* BLINK */;
        } else if (p === 27) {
          attr.fg &= ~67108864 /* INVERSE */;
        } else if (p === 28) {
          attr.fg &= ~1073741824 /* INVISIBLE */;
        } else if (p === 29) {
          attr.fg &= ~2147483648 /* STRIKETHROUGH */;
        } else if (p === 39) {
          attr.fg &= ~(50331648 /* CM_MASK */ | 16777215 /* RGB_MASK */);
          attr.fg |= DEFAULT_ATTR_DATA.fg & (255 /* PCOLOR_MASK */ | 16777215 /* RGB_MASK */);
        } else if (p === 49) {
          attr.bg &= ~(50331648 /* CM_MASK */ | 16777215 /* RGB_MASK */);
          attr.bg |= DEFAULT_ATTR_DATA.bg & (255 /* PCOLOR_MASK */ | 16777215 /* RGB_MASK */);
        } else if (p === 38 || p === 48 || p === 58) {
          i2 += this._extractColor(params, i2, attr);
        } else if (p === 53) {
          attr.bg |= 1073741824 /* OVERLINE */;
        } else if (p === 55) {
          attr.bg &= ~1073741824 /* OVERLINE */;
        } else if (p === 59) {
          attr.extended = attr.extended.clone();
          attr.extended.underlineColor = -1;
          attr.updateExtended();
        } else if (p === 100) {
          attr.fg &= ~(50331648 /* CM_MASK */ | 16777215 /* RGB_MASK */);
          attr.fg |= DEFAULT_ATTR_DATA.fg & (255 /* PCOLOR_MASK */ | 16777215 /* RGB_MASK */);
          attr.bg &= ~(50331648 /* CM_MASK */ | 16777215 /* RGB_MASK */);
          attr.bg |= DEFAULT_ATTR_DATA.bg & (255 /* PCOLOR_MASK */ | 16777215 /* RGB_MASK */);
        } else {
          this._logService.debug("Unknown SGR attribute: %d.", p);
        }
      }
      return true;
    }
    /**
     * CSI Ps n  Device Status Report (DSR).
     *     Ps = 5  -> Status Report.  Result (``OK'') is
     *   CSI 0 n
     *     Ps = 6  -> Report Cursor Position (CPR) [row;column].
     *   Result is
     *   CSI r ; c R
     * CSI ? Ps n
     *   Device Status Report (DSR, DEC-specific).
     *     Ps = 6  -> Report Cursor Position (CPR) [row;column] as CSI
     *     ? r ; c R (assumes page is zero).
     *     Ps = 1 5  -> Report Printer status as CSI ? 1 0  n  (ready).
     *     or CSI ? 1 1  n  (not ready).
     *     Ps = 2 5  -> Report UDK status as CSI ? 2 0  n  (unlocked)
     *     or CSI ? 2 1  n  (locked).
     *     Ps = 2 6  -> Report Keyboard status as
     *   CSI ? 2 7  ;  1  ;  0  ;  0  n  (North American).
     *   The last two parameters apply to VT400 & up, and denote key-
     *   board ready and LK01 respectively.
     *     Ps = 5 3  -> Report Locator status as
     *   CSI ? 5 3  n  Locator available, if compiled-in, or
     *   CSI ? 5 0  n  No Locator, if not.
     *
     * @vt: #Y CSI DSR   "Device Status Report"  "CSI Ps n"  "Request cursor position (CPR) with `Ps` = 6."
     */
    deviceStatus(params) {
      switch (params.params[0]) {
        case 5:
          this._coreService.triggerDataEvent(`${C0.ESC}[0n`);
          break;
        case 6:
          const y = this._activeBuffer.y + 1;
          const x = this._activeBuffer.x + 1;
          this._coreService.triggerDataEvent(`${C0.ESC}[${y};${x}R`);
          break;
      }
      return true;
    }
    // @vt: #P[Only CPR is supported.]  CSI DECDSR  "DEC Device Status Report"  "CSI ? Ps n"  "Only CPR is supported (same as DSR)."
    deviceStatusPrivate(params) {
      switch (params.params[0]) {
        case 6:
          const y = this._activeBuffer.y + 1;
          const x = this._activeBuffer.x + 1;
          this._coreService.triggerDataEvent(`${C0.ESC}[?${y};${x}R`);
          break;
        case 15:
          break;
        case 25:
          break;
        case 26:
          break;
        case 53:
          break;
      }
      return true;
    }
    /**
     * CSI ! p   Soft terminal reset (DECSTR).
     * http://vt100.net/docs/vt220-rm/table4-10.html
     *
     * @vt: #Y CSI DECSTR  "Soft Terminal Reset"   "CSI ! p"   "Reset several terminal attributes to initial state."
     * There are two terminal reset sequences - RIS and DECSTR. While RIS performs almost a full
     * terminal bootstrap, DECSTR only resets certain attributes. For most needs DECSTR should be
     * sufficient.
     *
     * The following terminal attributes are reset to default values:
     * - IRM is reset (dafault = false)
     * - scroll margins are reset (default = viewport size)
     * - erase attributes are reset to default
     * - charsets are reset
     * - DECSC data is reset to initial values
     * - DECOM is reset to absolute mode
     *
     *
     * FIXME: there are several more attributes missing (see VT520 manual)
     */
    softReset(params) {
      this._coreService.isCursorHidden = false;
      this._onRequestSyncScrollBar.fire();
      this._activeBuffer.scrollTop = 0;
      this._activeBuffer.scrollBottom = this._bufferService.rows - 1;
      this._curAttrData = DEFAULT_ATTR_DATA.clone();
      this._coreService.reset();
      this._charsetService.reset();
      this._activeBuffer.savedX = 0;
      this._activeBuffer.savedY = this._activeBuffer.ybase;
      this._activeBuffer.savedCurAttrData.fg = this._curAttrData.fg;
      this._activeBuffer.savedCurAttrData.bg = this._curAttrData.bg;
      this._activeBuffer.savedCharset = this._charsetService.charset;
      this._coreService.decPrivateModes.origin = false;
      return true;
    }
    /**
     * CSI Ps SP q  Set cursor style (DECSCUSR, VT520).
     *   Ps = 0  -> reset to option.
     *   Ps = 1  -> blinking block (default).
     *   Ps = 2  -> steady block.
     *   Ps = 3  -> blinking underline.
     *   Ps = 4  -> steady underline.
     *   Ps = 5  -> blinking bar (xterm).
     *   Ps = 6  -> steady bar (xterm).
     *
     * @vt: #Y CSI DECSCUSR  "Set Cursor Style"  "CSI Ps SP q"   "Set cursor style."
     * Supported cursor styles:
     *  - 0: reset to option
     *  - empty, 1: blinking block
     *  - 2: steady block
     *  - 3: blinking underline
     *  - 4: steady underline
     *  - 5: blinking bar
     *  - 6: steady bar
     */
    setCursorStyle(params) {
      const param = params.length === 0 ? 1 : params.params[0];
      if (param === 0) {
        this._coreService.decPrivateModes.cursorStyle = void 0;
        this._coreService.decPrivateModes.cursorBlink = void 0;
      } else {
        switch (param) {
          case 1:
          case 2:
            this._coreService.decPrivateModes.cursorStyle = "block";
            break;
          case 3:
          case 4:
            this._coreService.decPrivateModes.cursorStyle = "underline";
            break;
          case 5:
          case 6:
            this._coreService.decPrivateModes.cursorStyle = "bar";
            break;
        }
        const isBlinking = param % 2 === 1;
        this._coreService.decPrivateModes.cursorBlink = isBlinking;
      }
      return true;
    }
    /**
     * CSI Ps ; Ps r
     *   Set Scrolling Region [top;bottom] (default = full size of win-
     *   dow) (DECSTBM).
     *
     * @vt: #Y CSI DECSTBM "Set Top and Bottom Margin" "CSI Ps ; Ps r" "Set top and bottom margins of the viewport [top;bottom] (default = viewport size)."
     */
    setScrollRegion(params) {
      const top = params.params[0] || 1;
      let bottom;
      if (params.length < 2 || (bottom = params.params[1]) > this._bufferService.rows || bottom === 0) {
        bottom = this._bufferService.rows;
      }
      if (bottom > top) {
        this._activeBuffer.scrollTop = top - 1;
        this._activeBuffer.scrollBottom = bottom - 1;
        this._setCursor(0, 0);
      }
      return true;
    }
    /**
     * CSI Ps ; Ps ; Ps t - Various window manipulations and reports (xterm)
     *
     * Note: Only those listed below are supported. All others are left to integrators and
     * need special treatment based on the embedding environment.
     *
     *    Ps = 1 4                                                          supported
     *      Report xterm text area size in pixels.
     *      Result is CSI 4 ; height ; width t
     *    Ps = 14 ; 2                                                       not implemented
     *    Ps = 16                                                           supported
     *      Report xterm character cell size in pixels.
     *      Result is CSI 6 ; height ; width t
     *    Ps = 18                                                           supported
     *      Report the size of the text area in characters.
     *      Result is CSI 8 ; height ; width t
     *    Ps = 20                                                           supported
     *      Report xterm window's icon label.
     *      Result is OSC L label ST
     *    Ps = 21                                                           supported
     *      Report xterm window's title.
     *      Result is OSC l label ST
     *    Ps = 22 ; 0  -> Save xterm icon and window title on stack.        supported
     *    Ps = 22 ; 1  -> Save xterm icon title on stack.                   supported
     *    Ps = 22 ; 2  -> Save xterm window title on stack.                 supported
     *    Ps = 23 ; 0  -> Restore xterm icon and window title from stack.   supported
     *    Ps = 23 ; 1  -> Restore xterm icon title from stack.              supported
     *    Ps = 23 ; 2  -> Restore xterm window title from stack.            supported
     *    Ps >= 24                                                          not implemented
     */
    windowOptions(params) {
      if (!paramToWindowOption(params.params[0], this._optionsService.rawOptions.windowOptions)) {
        return true;
      }
      const second = params.length > 1 ? params.params[1] : 0;
      switch (params.params[0]) {
        case 14:
          if (second !== 2) {
            this._onRequestWindowsOptionsReport.fire(0 /* GET_WIN_SIZE_PIXELS */);
          }
          break;
        case 16:
          this._onRequestWindowsOptionsReport.fire(1 /* GET_CELL_SIZE_PIXELS */);
          break;
        case 18:
          if (this._bufferService) {
            this._coreService.triggerDataEvent(`${C0.ESC}[8;${this._bufferService.rows};${this._bufferService.cols}t`);
          }
          break;
        case 22:
          if (second === 0 || second === 2) {
            this._windowTitleStack.push(this._windowTitle);
            if (this._windowTitleStack.length > STACK_LIMIT) {
              this._windowTitleStack.shift();
            }
          }
          if (second === 0 || second === 1) {
            this._iconNameStack.push(this._iconName);
            if (this._iconNameStack.length > STACK_LIMIT) {
              this._iconNameStack.shift();
            }
          }
          break;
        case 23:
          if (second === 0 || second === 2) {
            if (this._windowTitleStack.length) {
              this.setTitle(this._windowTitleStack.pop());
            }
          }
          if (second === 0 || second === 1) {
            if (this._iconNameStack.length) {
              this.setIconName(this._iconNameStack.pop());
            }
          }
          break;
      }
      return true;
    }
    /**
     * CSI s
     * ESC 7
     *   Save cursor (ANSI.SYS).
     *
     * @vt: #P[TODO...]  CSI SCOSC   "Save Cursor"   "CSI s"   "Save cursor position, charmap and text attributes."
     * @vt: #Y ESC  SC   "Save Cursor"   "ESC 7"   "Save cursor position, charmap and text attributes."
     */
    saveCursor(params) {
      this._activeBuffer.savedX = this._activeBuffer.x;
      this._activeBuffer.savedY = this._activeBuffer.ybase + this._activeBuffer.y;
      this._activeBuffer.savedCurAttrData.fg = this._curAttrData.fg;
      this._activeBuffer.savedCurAttrData.bg = this._curAttrData.bg;
      this._activeBuffer.savedCharset = this._charsetService.charset;
      return true;
    }
    /**
     * CSI u
     * ESC 8
     *   Restore cursor (ANSI.SYS).
     *
     * @vt: #P[TODO...]  CSI SCORC "Restore Cursor"  "CSI u"   "Restore cursor position, charmap and text attributes."
     * @vt: #Y ESC  RC "Restore Cursor"  "ESC 8"   "Restore cursor position, charmap and text attributes."
     */
    restoreCursor(params) {
      this._activeBuffer.x = this._activeBuffer.savedX || 0;
      this._activeBuffer.y = Math.max(this._activeBuffer.savedY - this._activeBuffer.ybase, 0);
      this._curAttrData.fg = this._activeBuffer.savedCurAttrData.fg;
      this._curAttrData.bg = this._activeBuffer.savedCurAttrData.bg;
      this._charsetService.charset = this._savedCharset;
      if (this._activeBuffer.savedCharset) {
        this._charsetService.charset = this._activeBuffer.savedCharset;
      }
      this._restrictCursor();
      return true;
    }
    /**
     * OSC 2; <data> ST (set window title)
     *   Proxy to set window title.
     *
     * @vt: #P[Icon name is not exposed.]   OSC    0   "Set Windows Title and Icon Name"  "OSC 0 ; Pt BEL"  "Set window title and icon name."
     * Icon name is not supported. For Window Title see below.
     *
     * @vt: #Y     OSC    2   "Set Windows Title"  "OSC 2 ; Pt BEL"  "Set window title."
     * xterm.js does not manipulate the title directly, instead exposes changes via the event
     * `Terminal.onTitleChange`.
     */
    setTitle(data) {
      this._windowTitle = data;
      this._onTitleChange.fire(data);
      return true;
    }
    /**
     * OSC 1; <data> ST
     * Note: Icon name is not exposed.
     */
    setIconName(data) {
      this._iconName = data;
      return true;
    }
    /**
     * OSC 4; <num> ; <text> ST (set ANSI color <num> to <text>)
     *
     * @vt: #Y    OSC    4    "Set ANSI color"   "OSC 4 ; c ; spec BEL" "Change color number `c` to the color specified by `spec`."
     * `c` is the color index between 0 and 255. The color format of `spec` is derived from
     * `XParseColor` (see OSC 10 for supported formats). There may be multipe `c ; spec` pairs present
     * in the same instruction. If `spec` contains `?` the terminal returns a sequence with the
     * currently set color.
     */
    setOrReportIndexedColor(data) {
      const event = [];
      const slots = data.split(";");
      while (slots.length > 1) {
        const idx = slots.shift();
        const spec = slots.shift();
        if (/^\d+$/.exec(idx)) {
          const index = parseInt(idx);
          if (isValidColorIndex(index)) {
            if (spec === "?") {
              event.push({ type: 0 /* REPORT */, index });
            } else {
              const color2 = parseColor2(spec);
              if (color2) {
                event.push({ type: 1 /* SET */, index, color: color2 });
              }
            }
          }
        }
      }
      if (event.length) {
        this._onColor.fire(event);
      }
      return true;
    }
    /**
     * OSC 8 ; <params> ; <uri> ST - create hyperlink
     * OSC 8 ; ; ST - finish hyperlink
     *
     * Test case:
     *
     * ```sh
     * printf '\e]8;;http://example.com\e\\This is a link\e]8;;\e\\\n'
     * ```
     *
     * @vt: #Y    OSC    8    "Create hyperlink"   "OSC 8 ; params ; uri BEL" "Create a hyperlink to `uri` using `params`."
     * `uri` is a hyperlink starting with `http://`, `https://`, `ftp://`, `file://` or `mailto://`. `params` is an
     * optional list of key=value assignments, separated by the : character.
     * Example: `id=xyz123:foo=bar:baz=quux`.
     * Currently only the id key is defined. Cells that share the same ID and URI share hover
     * feedback. Use `OSC 8 ; ; BEL` to finish the current hyperlink.
     */
    setHyperlink(data) {
      const idx = data.indexOf(";");
      if (idx === -1) {
        return true;
      }
      const id2 = data.slice(0, idx).trim();
      const uri = data.slice(idx + 1);
      if (uri) {
        return this._createHyperlink(id2, uri);
      }
      if (id2.trim()) {
        return false;
      }
      return this._finishHyperlink();
    }
    _createHyperlink(params, uri) {
      if (this._getCurrentLinkId()) {
        this._finishHyperlink();
      }
      const parsedParams = params.split(":");
      let id2;
      const idParamIndex = parsedParams.findIndex((e) => e.startsWith("id="));
      if (idParamIndex !== -1) {
        id2 = parsedParams[idParamIndex].slice(3) || void 0;
      }
      this._curAttrData.extended = this._curAttrData.extended.clone();
      this._curAttrData.extended.urlId = this._oscLinkService.registerLink({ id: id2, uri });
      this._curAttrData.updateExtended();
      return true;
    }
    _finishHyperlink() {
      this._curAttrData.extended = this._curAttrData.extended.clone();
      this._curAttrData.extended.urlId = 0;
      this._curAttrData.updateExtended();
      return true;
    }
    /**
     * Apply colors requests for special colors in OSC 10 | 11 | 12.
     * Since these commands are stacking from multiple parameters,
     * we handle them in a loop with an entry offset to `_specialColors`.
     */
    _setOrReportSpecialColor(data, offset) {
      const slots = data.split(";");
      for (let i2 = 0; i2 < slots.length; ++i2, ++offset) {
        if (offset >= this._specialColors.length) break;
        if (slots[i2] === "?") {
          this._onColor.fire([{ type: 0 /* REPORT */, index: this._specialColors[offset] }]);
        } else {
          const color2 = parseColor2(slots[i2]);
          if (color2) {
            this._onColor.fire([{ type: 1 /* SET */, index: this._specialColors[offset], color: color2 }]);
          }
        }
      }
      return true;
    }
    /**
     * OSC 10 ; <xcolor name>|<?> ST - set or query default foreground color
     *
     * @vt: #Y  OSC   10    "Set or query default foreground color"   "OSC 10 ; Pt BEL"  "Set or query default foreground color."
     * To set the color, the following color specification formats are supported:
     * - `rgb:<red>/<green>/<blue>` for  `<red>, <green>, <blue>` in `h | hh | hhh | hhhh`, where
     *   `h` is a single hexadecimal digit (case insignificant). The different widths scale
     *   from 4 bit (`h`) to 16 bit (`hhhh`) and get converted to 8 bit (`hh`).
     * - `#RGB` - 4 bits per channel, expanded to `#R0G0B0`
     * - `#RRGGBB` - 8 bits per channel
     * - `#RRRGGGBBB` - 12 bits per channel, truncated to `#RRGGBB`
     * - `#RRRRGGGGBBBB` - 16 bits per channel, truncated to `#RRGGBB`
     *
     * **Note:** X11 named colors are currently unsupported.
     *
     * If `Pt` contains `?` instead of a color specification, the terminal
     * returns a sequence with the current default foreground color
     * (use that sequence to restore the color after changes).
     *
     * **Note:** Other than xterm, xterm.js does not support OSC 12 - 19.
     * Therefore stacking multiple `Pt` separated by `;` only works for the first two entries.
     */
    setOrReportFgColor(data) {
      return this._setOrReportSpecialColor(data, 0);
    }
    /**
     * OSC 11 ; <xcolor name>|<?> ST - set or query default background color
     *
     * @vt: #Y  OSC   11    "Set or query default background color"   "OSC 11 ; Pt BEL"  "Same as OSC 10, but for default background."
     */
    setOrReportBgColor(data) {
      return this._setOrReportSpecialColor(data, 1);
    }
    /**
     * OSC 12 ; <xcolor name>|<?> ST - set or query default cursor color
     *
     * @vt: #Y  OSC   12    "Set or query default cursor color"   "OSC 12 ; Pt BEL"  "Same as OSC 10, but for default cursor color."
     */
    setOrReportCursorColor(data) {
      return this._setOrReportSpecialColor(data, 2);
    }
    /**
     * OSC 104 ; <num> ST - restore ANSI color <num>
     *
     * @vt: #Y  OSC   104    "Reset ANSI color"   "OSC 104 ; c BEL" "Reset color number `c` to themed color."
     * `c` is the color index between 0 and 255. This function restores the default color for `c` as
     * specified by the loaded theme. Any number of `c` parameters may be given.
     * If no parameters are given, the entire indexed color table will be reset.
     */
    restoreIndexedColor(data) {
      if (!data) {
        this._onColor.fire([{ type: 2 /* RESTORE */ }]);
        return true;
      }
      const event = [];
      const slots = data.split(";");
      for (let i2 = 0; i2 < slots.length; ++i2) {
        if (/^\d+$/.exec(slots[i2])) {
          const index = parseInt(slots[i2]);
          if (isValidColorIndex(index)) {
            event.push({ type: 2 /* RESTORE */, index });
          }
        }
      }
      if (event.length) {
        this._onColor.fire(event);
      }
      return true;
    }
    /**
     * OSC 110 ST - restore default foreground color
     *
     * @vt: #Y  OSC   110    "Restore default foreground color"   "OSC 110 BEL"  "Restore default foreground to themed color."
     */
    restoreFgColor(data) {
      this._onColor.fire([{ type: 2 /* RESTORE */, index: 256 /* FOREGROUND */ }]);
      return true;
    }
    /**
     * OSC 111 ST - restore default background color
     *
     * @vt: #Y  OSC   111    "Restore default background color"   "OSC 111 BEL"  "Restore default background to themed color."
     */
    restoreBgColor(data) {
      this._onColor.fire([{ type: 2 /* RESTORE */, index: 257 /* BACKGROUND */ }]);
      return true;
    }
    /**
     * OSC 112 ST - restore default cursor color
     *
     * @vt: #Y  OSC   112    "Restore default cursor color"   "OSC 112 BEL"  "Restore default cursor to themed color."
     */
    restoreCursorColor(data) {
      this._onColor.fire([{ type: 2 /* RESTORE */, index: 258 /* CURSOR */ }]);
      return true;
    }
    /**
     * ESC E
     * C1.NEL
     *   DEC mnemonic: NEL (https://vt100.net/docs/vt510-rm/NEL)
     *   Moves cursor to first position on next line.
     *
     * @vt: #Y   C1    NEL   "Next Line"   "\x85"    "Move the cursor to the beginning of the next row."
     * @vt: #Y   ESC   NEL   "Next Line"   "ESC E"   "Move the cursor to the beginning of the next row."
     */
    nextLine() {
      this._activeBuffer.x = 0;
      this.index();
      return true;
    }
    /**
     * ESC =
     *   DEC mnemonic: DECKPAM (https://vt100.net/docs/vt510-rm/DECKPAM.html)
     *   Enables the numeric keypad to send application sequences to the host.
     */
    keypadApplicationMode() {
      this._logService.debug("Serial port requested application keypad.");
      this._coreService.decPrivateModes.applicationKeypad = true;
      this._onRequestSyncScrollBar.fire();
      return true;
    }
    /**
     * ESC >
     *   DEC mnemonic: DECKPNM (https://vt100.net/docs/vt510-rm/DECKPNM.html)
     *   Enables the keypad to send numeric characters to the host.
     */
    keypadNumericMode() {
      this._logService.debug("Switching back to normal keypad.");
      this._coreService.decPrivateModes.applicationKeypad = false;
      this._onRequestSyncScrollBar.fire();
      return true;
    }
    /**
     * ESC % @
     * ESC % G
     *   Select default character set. UTF-8 is not supported (string are unicode anyways)
     *   therefore ESC % G does the same.
     */
    selectDefaultCharset() {
      this._charsetService.setgLevel(0);
      this._charsetService.setgCharset(0, DEFAULT_CHARSET);
      return true;
    }
    /**
     * ESC ( C
     *   Designate G0 Character Set, VT100, ISO 2022.
     * ESC ) C
     *   Designate G1 Character Set (ISO 2022, VT100).
     * ESC * C
     *   Designate G2 Character Set (ISO 2022, VT220).
     * ESC + C
     *   Designate G3 Character Set (ISO 2022, VT220).
     * ESC - C
     *   Designate G1 Character Set (VT300).
     * ESC . C
     *   Designate G2 Character Set (VT300).
     * ESC / C
     *   Designate G3 Character Set (VT300). C = A  -> ISO Latin-1 Supplemental. - Supported?
     */
    selectCharset(collectAndFlag) {
      if (collectAndFlag.length !== 2) {
        this.selectDefaultCharset();
        return true;
      }
      if (collectAndFlag[0] === "/") {
        return true;
      }
      this._charsetService.setgCharset(GLEVEL[collectAndFlag[0]], CHARSETS[collectAndFlag[1]] || DEFAULT_CHARSET);
      return true;
    }
    /**
     * ESC D
     * C1.IND
     *   DEC mnemonic: IND (https://vt100.net/docs/vt510-rm/IND.html)
     *   Moves the cursor down one line in the same column.
     *
     * @vt: #Y   C1    IND   "Index"   "\x84"    "Move the cursor one line down scrolling if needed."
     * @vt: #Y   ESC   IND   "Index"   "ESC D"   "Move the cursor one line down scrolling if needed."
     */
    index() {
      this._restrictCursor();
      this._activeBuffer.y++;
      if (this._activeBuffer.y === this._activeBuffer.scrollBottom + 1) {
        this._activeBuffer.y--;
        this._bufferService.scroll(this._eraseAttrData());
      } else if (this._activeBuffer.y >= this._bufferService.rows) {
        this._activeBuffer.y = this._bufferService.rows - 1;
      }
      this._restrictCursor();
      return true;
    }
    /**
     * ESC H
     * C1.HTS
     *   DEC mnemonic: HTS (https://vt100.net/docs/vt510-rm/HTS.html)
     *   Sets a horizontal tab stop at the column position indicated by
     *   the value of the active column when the terminal receives an HTS.
     *
     * @vt: #Y   C1    HTS   "Horizontal Tabulation Set" "\x88"    "Places a tab stop at the current cursor position."
     * @vt: #Y   ESC   HTS   "Horizontal Tabulation Set" "ESC H"   "Places a tab stop at the current cursor position."
     */
    tabSet() {
      this._activeBuffer.tabs[this._activeBuffer.x] = true;
      return true;
    }
    /**
     * ESC M
     * C1.RI
     *   DEC mnemonic: HTS
     *   Moves the cursor up one line in the same column. If the cursor is at the top margin,
     *   the page scrolls down.
     *
     * @vt: #Y ESC  IR "Reverse Index" "ESC M"  "Move the cursor one line up scrolling if needed."
     */
    reverseIndex() {
      this._restrictCursor();
      if (this._activeBuffer.y === this._activeBuffer.scrollTop) {
        const scrollRegionHeight = this._activeBuffer.scrollBottom - this._activeBuffer.scrollTop;
        this._activeBuffer.lines.shiftElements(this._activeBuffer.ybase + this._activeBuffer.y, scrollRegionHeight, 1);
        this._activeBuffer.lines.set(this._activeBuffer.ybase + this._activeBuffer.y, this._activeBuffer.getBlankLine(this._eraseAttrData()));
        this._dirtyRowTracker.markRangeDirty(this._activeBuffer.scrollTop, this._activeBuffer.scrollBottom);
      } else {
        this._activeBuffer.y--;
        this._restrictCursor();
      }
      return true;
    }
    /**
     * ESC c
     *   DEC mnemonic: RIS (https://vt100.net/docs/vt510-rm/RIS.html)
     *   Reset to initial state.
     */
    fullReset() {
      this._parser.reset();
      this._onRequestReset.fire();
      return true;
    }
    reset() {
      this._curAttrData = DEFAULT_ATTR_DATA.clone();
      this._eraseAttrDataInternal = DEFAULT_ATTR_DATA.clone();
    }
    /**
     * back_color_erase feature for xterm.
     */
    _eraseAttrData() {
      this._eraseAttrDataInternal.bg &= ~(50331648 /* CM_MASK */ | 16777215);
      this._eraseAttrDataInternal.bg |= this._curAttrData.bg & ~4227858432;
      return this._eraseAttrDataInternal;
    }
    /**
     * ESC n
     * ESC o
     * ESC |
     * ESC }
     * ESC ~
     *   DEC mnemonic: LS (https://vt100.net/docs/vt510-rm/LS.html)
     *   When you use a locking shift, the character set remains in GL or GR until
     *   you use another locking shift. (partly supported)
     */
    setgLevel(level) {
      this._charsetService.setgLevel(level);
      return true;
    }
    /**
     * ESC # 8
     *   DEC mnemonic: DECALN (https://vt100.net/docs/vt510-rm/DECALN.html)
     *   This control function fills the complete screen area with
     *   a test pattern (E) used for adjusting screen alignment.
     *
     * @vt: #Y   ESC   DECALN   "Screen Alignment Pattern"  "ESC # 8"  "Fill viewport with a test pattern (E)."
     */
    screenAlignmentPattern() {
      const cell = new CellData();
      cell.content = 1 << 22 /* WIDTH_SHIFT */ | "E".charCodeAt(0);
      cell.fg = this._curAttrData.fg;
      cell.bg = this._curAttrData.bg;
      this._setCursor(0, 0);
      for (let yOffset = 0; yOffset < this._bufferService.rows; ++yOffset) {
        const row = this._activeBuffer.ybase + this._activeBuffer.y + yOffset;
        const line = this._activeBuffer.lines.get(row);
        if (line) {
          line.fill(cell);
          line.isWrapped = false;
        }
      }
      this._dirtyRowTracker.markAllDirty();
      this._setCursor(0, 0);
      return true;
    }
    /**
     * DCS $ q Pt ST
     *   DECRQSS (https://vt100.net/docs/vt510-rm/DECRQSS.html)
     *   Request Status String (DECRQSS), VT420 and up.
     *   Response: DECRPSS (https://vt100.net/docs/vt510-rm/DECRPSS.html)
     *
     * @vt: #P[Limited support, see below.]  DCS   DECRQSS   "Request Selection or Setting"  "DCS $ q Pt ST"   "Request several terminal settings."
     * Response is in the form `ESC P 1 $ r Pt ST` for valid requests, where `Pt` contains the
     * corresponding CSI string, `ESC P 0 ST` for invalid requests.
     *
     * Supported requests and responses:
     *
     * | Type                             | Request           | Response (`Pt`)                                       |
     * | -------------------------------- | ----------------- | ----------------------------------------------------- |
     * | Graphic Rendition (SGR)          | `DCS $ q m ST`    | always reporting `0m` (currently broken)              |
     * | Top and Bottom Margins (DECSTBM) | `DCS $ q r ST`    | `Ps ; Ps r`                                           |
     * | Cursor Style (DECSCUSR)          | `DCS $ q SP q ST` | `Ps SP q`                                             |
     * | Protection Attribute (DECSCA)    | `DCS $ q " q ST`  | `Ps " q` (DECSCA 2 is reported as Ps = 0)             |
     * | Conformance Level (DECSCL)       | `DCS $ q " p ST`  | always reporting `61 ; 1 " p` (DECSCL is unsupported) |
     *
     *
     * TODO:
     * - fix SGR report
     * - either check which conformance is better suited or remove the report completely
     *   --> we are currently a mixture of all up to VT400 but dont follow anyone strictly
     */
    requestStatusString(data, params) {
      const f = (s) => {
        this._coreService.triggerDataEvent(`${C0.ESC}${s}${C0.ESC}\\`);
        return true;
      };
      const b = this._bufferService.buffer;
      const opts = this._optionsService.rawOptions;
      const STYLES = { "block": 2, "underline": 4, "bar": 6 };
      if (data === '"q') return f(`P1$r${this._curAttrData.isProtected() ? 1 : 0}"q`);
      if (data === '"p') return f(`P1$r61;1"p`);
      if (data === "r") return f(`P1$r${b.scrollTop + 1};${b.scrollBottom + 1}r`);
      if (data === "m") return f(`P1$r0m`);
      if (data === " q") return f(`P1$r${STYLES[opts.cursorStyle] - (opts.cursorBlink ? 1 : 0)} q`);
      return f(`P0$r`);
    }
    markRangeDirty(y1, y2) {
      this._dirtyRowTracker.markRangeDirty(y1, y2);
    }
  };
  var DirtyRowTracker = class {
    constructor(_bufferService) {
      this._bufferService = _bufferService;
      this.clearRange();
    }
    clearRange() {
      this.start = this._bufferService.buffer.y;
      this.end = this._bufferService.buffer.y;
    }
    markDirty(y) {
      if (y < this.start) {
        this.start = y;
      } else if (y > this.end) {
        this.end = y;
      }
    }
    markRangeDirty(y1, y2) {
      if (y1 > y2) {
        $temp = y1;
        y1 = y2;
        y2 = $temp;
      }
      if (y1 < this.start) {
        this.start = y1;
      }
      if (y2 > this.end) {
        this.end = y2;
      }
    }
    markAllDirty() {
      this.markRangeDirty(0, this._bufferService.rows - 1);
    }
  };
  DirtyRowTracker = __decorateClass([
    __decorateParam(0, IBufferService)
  ], DirtyRowTracker);
  function isValidColorIndex(value) {
    return 0 <= value && value < 256;
  }

  // node_modules/@xterm/xterm/src/common/input/WriteBuffer.ts
  var DISCARD_WATERMARK = 5e7;
  var WRITE_TIMEOUT_MS = 12;
  var WRITE_BUFFER_LENGTH_THRESHOLD = 50;
  var WriteBuffer = class extends Disposable {
    constructor(_action) {
      super();
      this._action = _action;
      this._writeBuffer = [];
      this._callbacks = [];
      this._pendingData = 0;
      this._bufferOffset = 0;
      this._isSyncWriting = false;
      this._syncCalls = 0;
      this._didUserInput = false;
      this._onWriteParsed = this._register(new Emitter());
      this.onWriteParsed = this._onWriteParsed.event;
    }
    handleUserInput() {
      this._didUserInput = true;
    }
    /**
     * @deprecated Unreliable, to be removed soon.
     */
    writeSync(data, maxSubsequentCalls) {
      if (maxSubsequentCalls !== void 0 && this._syncCalls > maxSubsequentCalls) {
        this._syncCalls = 0;
        return;
      }
      this._pendingData += data.length;
      this._writeBuffer.push(data);
      this._callbacks.push(void 0);
      this._syncCalls++;
      if (this._isSyncWriting) {
        return;
      }
      this._isSyncWriting = true;
      let chunk;
      while (chunk = this._writeBuffer.shift()) {
        this._action(chunk);
        const cb = this._callbacks.shift();
        if (cb) cb();
      }
      this._pendingData = 0;
      this._bufferOffset = 2147483647;
      this._isSyncWriting = false;
      this._syncCalls = 0;
    }
    write(data, callback) {
      if (this._pendingData > DISCARD_WATERMARK) {
        throw new Error("write data discarded, use flow control to avoid losing data");
      }
      if (!this._writeBuffer.length) {
        this._bufferOffset = 0;
        if (this._didUserInput) {
          this._didUserInput = false;
          this._pendingData += data.length;
          this._writeBuffer.push(data);
          this._callbacks.push(callback);
          this._innerWrite();
          return;
        }
        setTimeout(() => this._innerWrite());
      }
      this._pendingData += data.length;
      this._writeBuffer.push(data);
      this._callbacks.push(callback);
    }
    /**
     * Inner write call, that enters the sliced chunk processing by timing.
     *
     * `lastTime` indicates, when the last _innerWrite call had started.
     * It is used to aggregate async handler execution under a timeout constraint
     * effectively lowering the redrawing needs, schematically:
     *
     *   macroTask _innerWrite:
     *     if (performance.now() - (lastTime | 0) < WRITE_TIMEOUT_MS):
     *        schedule microTask _innerWrite(lastTime)
     *     else:
     *        schedule macroTask _innerWrite(0)
     *
     *   overall execution order on task queues:
     *
     *   macrotasks:  [...]  -->  _innerWrite(0)  -->  [...]  -->  screenUpdate  -->  [...]
     *         m  t:                    |
     *         i  a:                  [...]
     *         c  s:                    |
     *         r  k:              while < timeout:
     *         o  s:                _innerWrite(timeout)
     *
     * `promiseResult` depicts the promise resolve value of an async handler.
     * This value gets carried forward through all saved stack states of the
     * paused parser for proper continuation.
     *
     * Note, for pure sync code `lastTime` and `promiseResult` have no meaning.
     */
    _innerWrite(lastTime = 0, promiseResult = true) {
      const startTime = lastTime || performance.now();
      while (this._writeBuffer.length > this._bufferOffset) {
        const data = this._writeBuffer[this._bufferOffset];
        const result = this._action(data, promiseResult);
        if (result) {
          const continuation = (r) => performance.now() - startTime >= WRITE_TIMEOUT_MS ? setTimeout(() => this._innerWrite(0, r)) : this._innerWrite(startTime, r);
          result.catch((err) => {
            queueMicrotask(() => {
              throw err;
            });
            return Promise.resolve(false);
          }).then(continuation);
          return;
        }
        const cb = this._callbacks[this._bufferOffset];
        if (cb) cb();
        this._bufferOffset++;
        this._pendingData -= data.length;
        if (performance.now() - startTime >= WRITE_TIMEOUT_MS) {
          break;
        }
      }
      if (this._writeBuffer.length > this._bufferOffset) {
        if (this._bufferOffset > WRITE_BUFFER_LENGTH_THRESHOLD) {
          this._writeBuffer = this._writeBuffer.slice(this._bufferOffset);
          this._callbacks = this._callbacks.slice(this._bufferOffset);
          this._bufferOffset = 0;
        }
        setTimeout(() => this._innerWrite());
      } else {
        this._writeBuffer.length = 0;
        this._callbacks.length = 0;
        this._pendingData = 0;
        this._bufferOffset = 0;
      }
      this._onWriteParsed.fire();
    }
  };

  // node_modules/@xterm/xterm/src/common/services/OscLinkService.ts
  var OscLinkService = class {
    constructor(_bufferService) {
      this._bufferService = _bufferService;
      this._nextId = 1;
      /**
       * A map of the link key to link entry. This is used to add additional lines to links with ids.
       */
      this._entriesWithId = /* @__PURE__ */ new Map();
      /**
       * A map of the link id to the link entry. The "link id" (number) which is the numberic
       * representation of a unique link should not be confused with "id" (string) which comes in with
       * `id=` in the OSC link's properties.
       */
      this._dataByLinkId = /* @__PURE__ */ new Map();
    }
    registerLink(data) {
      const buffer = this._bufferService.buffer;
      if (data.id === void 0) {
        const marker2 = buffer.addMarker(buffer.ybase + buffer.y);
        const entry2 = {
          data,
          id: this._nextId++,
          lines: [marker2]
        };
        marker2.onDispose(() => this._removeMarkerFromLink(entry2, marker2));
        this._dataByLinkId.set(entry2.id, entry2);
        return entry2.id;
      }
      const castData = data;
      const key = this._getEntryIdKey(castData);
      const match = this._entriesWithId.get(key);
      if (match) {
        this.addLineToLink(match.id, buffer.ybase + buffer.y);
        return match.id;
      }
      const marker = buffer.addMarker(buffer.ybase + buffer.y);
      const entry = {
        id: this._nextId++,
        key: this._getEntryIdKey(castData),
        data: castData,
        lines: [marker]
      };
      marker.onDispose(() => this._removeMarkerFromLink(entry, marker));
      this._entriesWithId.set(entry.key, entry);
      this._dataByLinkId.set(entry.id, entry);
      return entry.id;
    }
    addLineToLink(linkId, y) {
      const entry = this._dataByLinkId.get(linkId);
      if (!entry) {
        return;
      }
      if (entry.lines.every((e) => e.line !== y)) {
        const marker = this._bufferService.buffer.addMarker(y);
        entry.lines.push(marker);
        marker.onDispose(() => this._removeMarkerFromLink(entry, marker));
      }
    }
    getLinkData(linkId) {
      return this._dataByLinkId.get(linkId)?.data;
    }
    _getEntryIdKey(linkData) {
      return `${linkData.id};;${linkData.uri}`;
    }
    _removeMarkerFromLink(entry, marker) {
      const index = entry.lines.indexOf(marker);
      if (index === -1) {
        return;
      }
      entry.lines.splice(index, 1);
      if (entry.lines.length === 0) {
        if (entry.data.id !== void 0) {
          this._entriesWithId.delete(entry.key);
        }
        this._dataByLinkId.delete(entry.id);
      }
    }
  };
  OscLinkService = __decorateClass([
    __decorateParam(0, IBufferService)
  ], OscLinkService);

  // node_modules/@xterm/xterm/src/common/CoreTerminal.ts
  var hasWriteSyncWarnHappened = false;
  var CoreTerminal = class extends Disposable {
    constructor(options) {
      super();
      this._windowsWrappingHeuristics = this._register(new MutableDisposable());
      this._onBinary = this._register(new Emitter());
      this.onBinary = this._onBinary.event;
      this._onData = this._register(new Emitter());
      this.onData = this._onData.event;
      this._onLineFeed = this._register(new Emitter());
      this.onLineFeed = this._onLineFeed.event;
      this._onResize = this._register(new Emitter());
      this.onResize = this._onResize.event;
      this._onWriteParsed = this._register(new Emitter());
      this.onWriteParsed = this._onWriteParsed.event;
      this._onScroll = this._register(new Emitter());
      this._instantiationService = new InstantiationService();
      this.optionsService = this._register(new OptionsService(options));
      this._instantiationService.setService(IOptionsService, this.optionsService);
      this._bufferService = this._register(this._instantiationService.createInstance(BufferService));
      this._instantiationService.setService(IBufferService, this._bufferService);
      this._logService = this._register(this._instantiationService.createInstance(LogService));
      this._instantiationService.setService(ILogService, this._logService);
      this.coreService = this._register(this._instantiationService.createInstance(CoreService));
      this._instantiationService.setService(ICoreService, this.coreService);
      this.coreMouseService = this._register(this._instantiationService.createInstance(CoreMouseService));
      this._instantiationService.setService(ICoreMouseService, this.coreMouseService);
      this.unicodeService = this._register(this._instantiationService.createInstance(UnicodeService));
      this._instantiationService.setService(IUnicodeService, this.unicodeService);
      this._charsetService = this._instantiationService.createInstance(CharsetService);
      this._instantiationService.setService(ICharsetService, this._charsetService);
      this._oscLinkService = this._instantiationService.createInstance(OscLinkService);
      this._instantiationService.setService(IOscLinkService, this._oscLinkService);
      this._inputHandler = this._register(new InputHandler(this._bufferService, this._charsetService, this.coreService, this._logService, this.optionsService, this._oscLinkService, this.coreMouseService, this.unicodeService));
      this._register(Event.forward(this._inputHandler.onLineFeed, this._onLineFeed));
      this._register(this._inputHandler);
      this._register(Event.forward(this._bufferService.onResize, this._onResize));
      this._register(Event.forward(this.coreService.onData, this._onData));
      this._register(Event.forward(this.coreService.onBinary, this._onBinary));
      this._register(this.coreService.onRequestScrollToBottom(() => this.scrollToBottom(true)));
      this._register(this.coreService.onUserInput(() => this._writeBuffer.handleUserInput()));
      this._register(this.optionsService.onMultipleOptionChange(["windowsMode", "windowsPty"], () => this._handleWindowsPtyOptionChange()));
      this._register(this._bufferService.onScroll(() => {
        this._onScroll.fire({ position: this._bufferService.buffer.ydisp });
        this._inputHandler.markRangeDirty(this._bufferService.buffer.scrollTop, this._bufferService.buffer.scrollBottom);
      }));
      this._writeBuffer = this._register(new WriteBuffer((data, promiseResult) => this._inputHandler.parse(data, promiseResult)));
      this._register(Event.forward(this._writeBuffer.onWriteParsed, this._onWriteParsed));
    }
    get onScroll() {
      if (!this._onScrollApi) {
        this._onScrollApi = this._register(new Emitter());
        this._onScroll.event((ev) => {
          this._onScrollApi?.fire(ev.position);
        });
      }
      return this._onScrollApi.event;
    }
    get cols() {
      return this._bufferService.cols;
    }
    get rows() {
      return this._bufferService.rows;
    }
    get buffers() {
      return this._bufferService.buffers;
    }
    get options() {
      return this.optionsService.options;
    }
    set options(options) {
      for (const key in options) {
        this.optionsService.options[key] = options[key];
      }
    }
    write(data, callback) {
      this._writeBuffer.write(data, callback);
    }
    /**
     * Write data to terminal synchonously.
     *
     * This method is unreliable with async parser handlers, thus should not
     * be used anymore. If you need blocking semantics on data input consider
     * `write` with a callback instead.
     *
     * @deprecated Unreliable, will be removed soon.
     */
    writeSync(data, maxSubsequentCalls) {
      if (this._logService.logLevel <= 3 /* WARN */ && !hasWriteSyncWarnHappened) {
        this._logService.warn("writeSync is unreliable and will be removed soon.");
        hasWriteSyncWarnHappened = true;
      }
      this._writeBuffer.writeSync(data, maxSubsequentCalls);
    }
    input(data, wasUserInput = true) {
      this.coreService.triggerDataEvent(data, wasUserInput);
    }
    resize(x, y) {
      if (isNaN(x) || isNaN(y)) {
        return;
      }
      x = Math.max(x, MINIMUM_COLS);
      y = Math.max(y, MINIMUM_ROWS);
      this._bufferService.resize(x, y);
    }
    /**
     * Scroll the terminal down 1 row, creating a blank line.
     * @param eraseAttr The attribute data to use the for blank line.
     * @param isWrapped Whether the new line is wrapped from the previous line.
     */
    scroll(eraseAttr, isWrapped = false) {
      this._bufferService.scroll(eraseAttr, isWrapped);
    }
    /**
     * Scroll the display of the terminal
     * @param disp The number of lines to scroll down (negative scroll up).
     * @param suppressScrollEvent Don't emit the scroll event as scrollLines. This is used to avoid
     * unwanted events being handled by the viewport when the event was triggered from the viewport
     * originally.
     */
    scrollLines(disp, suppressScrollEvent) {
      this._bufferService.scrollLines(disp, suppressScrollEvent);
    }
    scrollPages(pageCount) {
      this.scrollLines(pageCount * (this.rows - 1));
    }
    scrollToTop() {
      this.scrollLines(-this._bufferService.buffer.ydisp);
    }
    scrollToBottom(disableSmoothScroll) {
      this.scrollLines(this._bufferService.buffer.ybase - this._bufferService.buffer.ydisp);
    }
    scrollToLine(line) {
      const scrollAmount = line - this._bufferService.buffer.ydisp;
      if (scrollAmount !== 0) {
        this.scrollLines(scrollAmount);
      }
    }
    /** Add handler for ESC escape sequence. See xterm.d.ts for details. */
    registerEscHandler(id2, callback) {
      return this._inputHandler.registerEscHandler(id2, callback);
    }
    /** Add handler for DCS escape sequence. See xterm.d.ts for details. */
    registerDcsHandler(id2, callback) {
      return this._inputHandler.registerDcsHandler(id2, callback);
    }
    /** Add handler for CSI escape sequence. See xterm.d.ts for details. */
    registerCsiHandler(id2, callback) {
      return this._inputHandler.registerCsiHandler(id2, callback);
    }
    /** Add handler for OSC escape sequence. See xterm.d.ts for details. */
    registerOscHandler(ident, callback) {
      return this._inputHandler.registerOscHandler(ident, callback);
    }
    _setup() {
      this._handleWindowsPtyOptionChange();
    }
    reset() {
      this._inputHandler.reset();
      this._bufferService.reset();
      this._charsetService.reset();
      this.coreService.reset();
      this.coreMouseService.reset();
    }
    _handleWindowsPtyOptionChange() {
      let value = false;
      const windowsPty = this.optionsService.rawOptions.windowsPty;
      if (windowsPty && windowsPty.buildNumber !== void 0 && windowsPty.buildNumber !== void 0) {
        value = !!(windowsPty.backend === "conpty" && windowsPty.buildNumber < 21376);
      } else if (this.optionsService.rawOptions.windowsMode) {
        value = true;
      }
      if (value) {
        this._enableWindowsWrappingHeuristics();
      } else {
        this._windowsWrappingHeuristics.clear();
      }
    }
    _enableWindowsWrappingHeuristics() {
      if (!this._windowsWrappingHeuristics.value) {
        const disposables = [];
        disposables.push(this.onLineFeed(updateWindowsModeWrappedState.bind(null, this._bufferService)));
        disposables.push(this.registerCsiHandler({ final: "H" }, () => {
          updateWindowsModeWrappedState(this._bufferService);
          return false;
        }));
        this._windowsWrappingHeuristics.value = toDisposable(() => {
          for (const d of disposables) {
            d.dispose();
          }
        });
      }
    }
  };

  // node_modules/@xterm/xterm/src/common/input/Keyboard.ts
  var KEYCODE_KEY_MAPPINGS = {
    // digits 0-9
    48: ["0", ")"],
    49: ["1", "!"],
    50: ["2", "@"],
    51: ["3", "#"],
    52: ["4", "$"],
    53: ["5", "%"],
    54: ["6", "^"],
    55: ["7", "&"],
    56: ["8", "*"],
    57: ["9", "("],
    // special chars
    186: [";", ":"],
    187: ["=", "+"],
    188: [",", "<"],
    189: ["-", "_"],
    190: [".", ">"],
    191: ["/", "?"],
    192: ["`", "~"],
    219: ["[", "{"],
    220: ["\\", "|"],
    221: ["]", "}"],
    222: ["'", '"']
  };
  function evaluateKeyboardEvent(ev, applicationCursorMode, isMac2, macOptionIsMeta) {
    const result = {
      type: 0 /* SEND_KEY */,
      // Whether to cancel event propagation (NOTE: this may not be needed since the event is
      // canceled at the end of keyDown
      cancel: false,
      // The new key even to emit
      key: void 0
    };
    const modifiers = (ev.shiftKey ? 1 : 0) | (ev.altKey ? 2 : 0) | (ev.ctrlKey ? 4 : 0) | (ev.metaKey ? 8 : 0);
    switch (ev.keyCode) {
      case 0:
        if (ev.key === "UIKeyInputUpArrow") {
          if (applicationCursorMode) {
            result.key = C0.ESC + "OA";
          } else {
            result.key = C0.ESC + "[A";
          }
        } else if (ev.key === "UIKeyInputLeftArrow") {
          if (applicationCursorMode) {
            result.key = C0.ESC + "OD";
          } else {
            result.key = C0.ESC + "[D";
          }
        } else if (ev.key === "UIKeyInputRightArrow") {
          if (applicationCursorMode) {
            result.key = C0.ESC + "OC";
          } else {
            result.key = C0.ESC + "[C";
          }
        } else if (ev.key === "UIKeyInputDownArrow") {
          if (applicationCursorMode) {
            result.key = C0.ESC + "OB";
          } else {
            result.key = C0.ESC + "[B";
          }
        }
        break;
      case 8:
        result.key = ev.ctrlKey ? "\b" : C0.DEL;
        if (ev.altKey) {
          result.key = C0.ESC + result.key;
        }
        break;
      case 9:
        if (ev.shiftKey) {
          result.key = C0.ESC + "[Z";
          break;
        }
        result.key = C0.HT;
        result.cancel = true;
        break;
      case 13:
        result.key = ev.altKey ? C0.ESC + C0.CR : C0.CR;
        result.cancel = true;
        break;
      case 27:
        result.key = C0.ESC;
        if (ev.altKey) {
          result.key = C0.ESC + C0.ESC;
        }
        result.cancel = true;
        break;
      case 37:
        if (ev.metaKey) {
          break;
        }
        if (modifiers) {
          result.key = C0.ESC + "[1;" + (modifiers + 1) + "D";
        } else if (applicationCursorMode) {
          result.key = C0.ESC + "OD";
        } else {
          result.key = C0.ESC + "[D";
        }
        break;
      case 39:
        if (ev.metaKey) {
          break;
        }
        if (modifiers) {
          result.key = C0.ESC + "[1;" + (modifiers + 1) + "C";
        } else if (applicationCursorMode) {
          result.key = C0.ESC + "OC";
        } else {
          result.key = C0.ESC + "[C";
        }
        break;
      case 38:
        if (ev.metaKey) {
          break;
        }
        if (modifiers) {
          result.key = C0.ESC + "[1;" + (modifiers + 1) + "A";
        } else if (applicationCursorMode) {
          result.key = C0.ESC + "OA";
        } else {
          result.key = C0.ESC + "[A";
        }
        break;
      case 40:
        if (ev.metaKey) {
          break;
        }
        if (modifiers) {
          result.key = C0.ESC + "[1;" + (modifiers + 1) + "B";
        } else if (applicationCursorMode) {
          result.key = C0.ESC + "OB";
        } else {
          result.key = C0.ESC + "[B";
        }
        break;
      case 45:
        if (!ev.shiftKey && !ev.ctrlKey) {
          result.key = C0.ESC + "[2~";
        }
        break;
      case 46:
        if (modifiers) {
          result.key = C0.ESC + "[3;" + (modifiers + 1) + "~";
        } else {
          result.key = C0.ESC + "[3~";
        }
        break;
      case 36:
        if (modifiers) {
          result.key = C0.ESC + "[1;" + (modifiers + 1) + "H";
        } else if (applicationCursorMode) {
          result.key = C0.ESC + "OH";
        } else {
          result.key = C0.ESC + "[H";
        }
        break;
      case 35:
        if (modifiers) {
          result.key = C0.ESC + "[1;" + (modifiers + 1) + "F";
        } else if (applicationCursorMode) {
          result.key = C0.ESC + "OF";
        } else {
          result.key = C0.ESC + "[F";
        }
        break;
      case 33:
        if (ev.shiftKey) {
          result.type = 2 /* PAGE_UP */;
        } else if (ev.ctrlKey) {
          result.key = C0.ESC + "[5;" + (modifiers + 1) + "~";
        } else {
          result.key = C0.ESC + "[5~";
        }
        break;
      case 34:
        if (ev.shiftKey) {
          result.type = 3 /* PAGE_DOWN */;
        } else if (ev.ctrlKey) {
          result.key = C0.ESC + "[6;" + (modifiers + 1) + "~";
        } else {
          result.key = C0.ESC + "[6~";
        }
        break;
      case 112:
        if (modifiers) {
          result.key = C0.ESC + "[1;" + (modifiers + 1) + "P";
        } else {
          result.key = C0.ESC + "OP";
        }
        break;
      case 113:
        if (modifiers) {
          result.key = C0.ESC + "[1;" + (modifiers + 1) + "Q";
        } else {
          result.key = C0.ESC + "OQ";
        }
        break;
      case 114:
        if (modifiers) {
          result.key = C0.ESC + "[1;" + (modifiers + 1) + "R";
        } else {
          result.key = C0.ESC + "OR";
        }
        break;
      case 115:
        if (modifiers) {
          result.key = C0.ESC + "[1;" + (modifiers + 1) + "S";
        } else {
          result.key = C0.ESC + "OS";
        }
        break;
      case 116:
        if (modifiers) {
          result.key = C0.ESC + "[15;" + (modifiers + 1) + "~";
        } else {
          result.key = C0.ESC + "[15~";
        }
        break;
      case 117:
        if (modifiers) {
          result.key = C0.ESC + "[17;" + (modifiers + 1) + "~";
        } else {
          result.key = C0.ESC + "[17~";
        }
        break;
      case 118:
        if (modifiers) {
          result.key = C0.ESC + "[18;" + (modifiers + 1) + "~";
        } else {
          result.key = C0.ESC + "[18~";
        }
        break;
      case 119:
        if (modifiers) {
          result.key = C0.ESC + "[19;" + (modifiers + 1) + "~";
        } else {
          result.key = C0.ESC + "[19~";
        }
        break;
      case 120:
        if (modifiers) {
          result.key = C0.ESC + "[20;" + (modifiers + 1) + "~";
        } else {
          result.key = C0.ESC + "[20~";
        }
        break;
      case 121:
        if (modifiers) {
          result.key = C0.ESC + "[21;" + (modifiers + 1) + "~";
        } else {
          result.key = C0.ESC + "[21~";
        }
        break;
      case 122:
        if (modifiers) {
          result.key = C0.ESC + "[23;" + (modifiers + 1) + "~";
        } else {
          result.key = C0.ESC + "[23~";
        }
        break;
      case 123:
        if (modifiers) {
          result.key = C0.ESC + "[24;" + (modifiers + 1) + "~";
        } else {
          result.key = C0.ESC + "[24~";
        }
        break;
      default:
        if (ev.ctrlKey && !ev.shiftKey && !ev.altKey && !ev.metaKey) {
          if (ev.keyCode >= 65 && ev.keyCode <= 90) {
            result.key = String.fromCharCode(ev.keyCode - 64);
          } else if (ev.keyCode === 32) {
            result.key = C0.NUL;
          } else if (ev.keyCode >= 51 && ev.keyCode <= 55) {
            result.key = String.fromCharCode(ev.keyCode - 51 + 27);
          } else if (ev.keyCode === 56) {
            result.key = C0.DEL;
          } else if (ev.keyCode === 219) {
            result.key = C0.ESC;
          } else if (ev.keyCode === 220) {
            result.key = C0.FS;
          } else if (ev.keyCode === 221) {
            result.key = C0.GS;
          }
        } else if ((!isMac2 || macOptionIsMeta) && ev.altKey && !ev.metaKey) {
          const keyMapping = KEYCODE_KEY_MAPPINGS[ev.keyCode];
          const key = keyMapping?.[!ev.shiftKey ? 0 : 1];
          if (key) {
            result.key = C0.ESC + key;
          } else if (ev.keyCode >= 65 && ev.keyCode <= 90) {
            const keyCode = ev.ctrlKey ? ev.keyCode - 64 : ev.keyCode + 32;
            let keyString = String.fromCharCode(keyCode);
            if (ev.shiftKey) {
              keyString = keyString.toUpperCase();
            }
            result.key = C0.ESC + keyString;
          } else if (ev.keyCode === 32) {
            result.key = C0.ESC + (ev.ctrlKey ? C0.NUL : " ");
          } else if (ev.key === "Dead" && ev.code.startsWith("Key")) {
            let keyString = ev.code.slice(3, 4);
            if (!ev.shiftKey) {
              keyString = keyString.toLowerCase();
            }
            result.key = C0.ESC + keyString;
            result.cancel = true;
          }
        } else if (isMac2 && !ev.altKey && !ev.ctrlKey && !ev.shiftKey && ev.metaKey) {
          if (ev.keyCode === 65) {
            result.type = 1 /* SELECT_ALL */;
          }
        } else if (ev.key && !ev.ctrlKey && !ev.altKey && !ev.metaKey && ev.keyCode >= 48 && ev.key.length === 1) {
          result.key = ev.key;
        } else if (ev.key && ev.ctrlKey) {
          if (ev.key === "_") {
            result.key = C0.US;
          }
          if (ev.key === "@") {
            result.key = C0.NUL;
          }
        }
        break;
    }
    return result;
  }

  // node_modules/@xterm/xterm/src/common/SortedList.ts
  var i = 0;
  var SortedList = class {
    constructor(_getKey) {
      this._getKey = _getKey;
      this._array = [];
      this._insertedValues = [];
      this._flushInsertedTask = new IdleTaskQueue();
      this._isFlushingInserted = false;
      this._deletedIndices = [];
      this._flushDeletedTask = new IdleTaskQueue();
      this._isFlushingDeleted = false;
    }
    clear() {
      this._array.length = 0;
      this._insertedValues.length = 0;
      this._flushInsertedTask.clear();
      this._isFlushingInserted = false;
      this._deletedIndices.length = 0;
      this._flushDeletedTask.clear();
      this._isFlushingDeleted = false;
    }
    insert(value) {
      this._flushCleanupDeleted();
      if (this._insertedValues.length === 0) {
        this._flushInsertedTask.enqueue(() => this._flushInserted());
      }
      this._insertedValues.push(value);
    }
    _flushInserted() {
      const sortedAddedValues = this._insertedValues.sort((a, b) => this._getKey(a) - this._getKey(b));
      let sortedAddedValuesIndex = 0;
      let arrayIndex = 0;
      const newArray = new Array(this._array.length + this._insertedValues.length);
      for (let newArrayIndex = 0; newArrayIndex < newArray.length; newArrayIndex++) {
        if (arrayIndex >= this._array.length || this._getKey(sortedAddedValues[sortedAddedValuesIndex]) <= this._getKey(this._array[arrayIndex])) {
          newArray[newArrayIndex] = sortedAddedValues[sortedAddedValuesIndex];
          sortedAddedValuesIndex++;
        } else {
          newArray[newArrayIndex] = this._array[arrayIndex++];
        }
      }
      this._array = newArray;
      this._insertedValues.length = 0;
    }
    _flushCleanupInserted() {
      if (!this._isFlushingInserted && this._insertedValues.length > 0) {
        this._flushInsertedTask.flush();
      }
    }
    delete(value) {
      this._flushCleanupInserted();
      if (this._array.length === 0) {
        return false;
      }
      const key = this._getKey(value);
      if (key === void 0) {
        return false;
      }
      i = this._search(key);
      if (i === -1) {
        return false;
      }
      if (this._getKey(this._array[i]) !== key) {
        return false;
      }
      do {
        if (this._array[i] === value) {
          if (this._deletedIndices.length === 0) {
            this._flushDeletedTask.enqueue(() => this._flushDeleted());
          }
          this._deletedIndices.push(i);
          return true;
        }
      } while (++i < this._array.length && this._getKey(this._array[i]) === key);
      return false;
    }
    _flushDeleted() {
      this._isFlushingDeleted = true;
      const sortedDeletedIndices = this._deletedIndices.sort((a, b) => a - b);
      let sortedDeletedIndicesIndex = 0;
      const newArray = new Array(this._array.length - sortedDeletedIndices.length);
      let newArrayIndex = 0;
      for (let i2 = 0; i2 < this._array.length; i2++) {
        if (sortedDeletedIndices[sortedDeletedIndicesIndex] === i2) {
          sortedDeletedIndicesIndex++;
        } else {
          newArray[newArrayIndex++] = this._array[i2];
        }
      }
      this._array = newArray;
      this._deletedIndices.length = 0;
      this._isFlushingDeleted = false;
    }
    _flushCleanupDeleted() {
      if (!this._isFlushingDeleted && this._deletedIndices.length > 0) {
        this._flushDeletedTask.flush();
      }
    }
    *getKeyIterator(key) {
      this._flushCleanupInserted();
      this._flushCleanupDeleted();
      if (this._array.length === 0) {
        return;
      }
      i = this._search(key);
      if (i < 0 || i >= this._array.length) {
        return;
      }
      if (this._getKey(this._array[i]) !== key) {
        return;
      }
      do {
        yield this._array[i];
      } while (++i < this._array.length && this._getKey(this._array[i]) === key);
    }
    forEachByKey(key, callback) {
      this._flushCleanupInserted();
      this._flushCleanupDeleted();
      if (this._array.length === 0) {
        return;
      }
      i = this._search(key);
      if (i < 0 || i >= this._array.length) {
        return;
      }
      if (this._getKey(this._array[i]) !== key) {
        return;
      }
      do {
        callback(this._array[i]);
      } while (++i < this._array.length && this._getKey(this._array[i]) === key);
    }
    values() {
      this._flushCleanupInserted();
      this._flushCleanupDeleted();
      return [...this._array].values();
    }
    _search(key) {
      let min = 0;
      let max = this._array.length - 1;
      while (max >= min) {
        let mid = min + max >> 1;
        const midKey = this._getKey(this._array[mid]);
        if (midKey > key) {
          max = mid - 1;
        } else if (midKey < key) {
          min = mid + 1;
        } else {
          while (mid > 0 && this._getKey(this._array[mid - 1]) === key) {
            mid--;
          }
          return mid;
        }
      }
      return min;
    }
  };

  // node_modules/@xterm/xterm/src/common/services/DecorationService.ts
  var $xmin = 0;
  var $xmax = 0;
  var DecorationService = class extends Disposable {
    constructor() {
      super();
      /**
       * A list of all decorations, sorted by the marker's line value. This relies on the fact that
       * while marker line values do change, they should all change by the same amount so this should
       * never become out of order.
       */
      this._decorations = new SortedList((e) => e?.marker.line);
      this._onDecorationRegistered = this._register(new Emitter());
      this.onDecorationRegistered = this._onDecorationRegistered.event;
      this._onDecorationRemoved = this._register(new Emitter());
      this.onDecorationRemoved = this._onDecorationRemoved.event;
      this._register(toDisposable(() => this.reset()));
    }
    get decorations() {
      return this._decorations.values();
    }
    registerDecoration(options) {
      if (options.marker.isDisposed) {
        return void 0;
      }
      const decoration = new Decoration(options);
      if (decoration) {
        const markerDispose = decoration.marker.onDispose(() => decoration.dispose());
        const listener = decoration.onDispose(() => {
          listener.dispose();
          if (decoration) {
            if (this._decorations.delete(decoration)) {
              this._onDecorationRemoved.fire(decoration);
            }
            markerDispose.dispose();
          }
        });
        this._decorations.insert(decoration);
        this._onDecorationRegistered.fire(decoration);
      }
      return decoration;
    }
    reset() {
      for (const d of this._decorations.values()) {
        d.dispose();
      }
      this._decorations.clear();
    }
    *getDecorationsAtCell(x, line, layer) {
      let xmin = 0;
      let xmax = 0;
      for (const d of this._decorations.getKeyIterator(line)) {
        xmin = d.options.x ?? 0;
        xmax = xmin + (d.options.width ?? 1);
        if (x >= xmin && x < xmax && (!layer || (d.options.layer ?? "bottom") === layer)) {
          yield d;
        }
      }
    }
    forEachDecorationAtCell(x, line, layer, callback) {
      this._decorations.forEachByKey(line, (d) => {
        $xmin = d.options.x ?? 0;
        $xmax = $xmin + (d.options.width ?? 1);
        if (x >= $xmin && x < $xmax && (!layer || (d.options.layer ?? "bottom") === layer)) {
          callback(d);
        }
      });
    }
  };
  var Decoration = class extends DisposableStore {
    constructor(options) {
      super();
      this.options = options;
      this.onRenderEmitter = this.add(new Emitter());
      this.onRender = this.onRenderEmitter.event;
      this._onDispose = this.add(new Emitter());
      this.onDispose = this._onDispose.event;
      this._cachedBg = null;
      this._cachedFg = null;
      this.marker = options.marker;
      if (this.options.overviewRulerOptions && !this.options.overviewRulerOptions.position) {
        this.options.overviewRulerOptions.position = "full";
      }
    }
    get backgroundColorRGB() {
      if (this._cachedBg === null) {
        if (this.options.backgroundColor) {
          this._cachedBg = css.toColor(this.options.backgroundColor);
        } else {
          this._cachedBg = void 0;
        }
      }
      return this._cachedBg;
    }
    get foregroundColorRGB() {
      if (this._cachedFg === null) {
        if (this.options.foregroundColor) {
          this._cachedFg = css.toColor(this.options.foregroundColor);
        } else {
          this._cachedFg = void 0;
        }
      }
      return this._cachedFg;
    }
    dispose() {
      this._onDispose.fire();
      super.dispose();
    }
  };

  // node_modules/@xterm/xterm/src/browser/TimeBasedDebouncer.ts
  var RENDER_DEBOUNCE_THRESHOLD_MS = 1e3;
  var TimeBasedDebouncer = class {
    constructor(_renderCallback, _debounceThresholdMS = RENDER_DEBOUNCE_THRESHOLD_MS) {
      this._renderCallback = _renderCallback;
      this._debounceThresholdMS = _debounceThresholdMS;
      // The last moment that the Terminal was refreshed at
      this._lastRefreshMs = 0;
      // Whether a trailing refresh should be triggered due to a refresh request that was throttled
      this._additionalRefreshRequested = false;
    }
    dispose() {
      if (this._refreshTimeoutID) {
        clearTimeout(this._refreshTimeoutID);
      }
    }
    refresh(rowStart, rowEnd, rowCount) {
      this._rowCount = rowCount;
      rowStart = rowStart !== void 0 ? rowStart : 0;
      rowEnd = rowEnd !== void 0 ? rowEnd : this._rowCount - 1;
      this._rowStart = this._rowStart !== void 0 ? Math.min(this._rowStart, rowStart) : rowStart;
      this._rowEnd = this._rowEnd !== void 0 ? Math.max(this._rowEnd, rowEnd) : rowEnd;
      const refreshRequestTime = performance.now();
      if (refreshRequestTime - this._lastRefreshMs >= this._debounceThresholdMS) {
        this._lastRefreshMs = refreshRequestTime;
        this._innerRefresh();
      } else if (!this._additionalRefreshRequested) {
        const elapsed = refreshRequestTime - this._lastRefreshMs;
        const waitPeriodBeforeTrailingRefresh = this._debounceThresholdMS - elapsed;
        this._additionalRefreshRequested = true;
        this._refreshTimeoutID = window.setTimeout(() => {
          this._lastRefreshMs = performance.now();
          this._innerRefresh();
          this._additionalRefreshRequested = false;
          this._refreshTimeoutID = void 0;
        }, waitPeriodBeforeTrailingRefresh);
      }
    }
    _innerRefresh() {
      if (this._rowStart === void 0 || this._rowEnd === void 0 || this._rowCount === void 0) {
        return;
      }
      const start = Math.max(this._rowStart, 0);
      const end = Math.min(this._rowEnd, this._rowCount - 1);
      this._rowStart = void 0;
      this._rowEnd = void 0;
      this._renderCallback(start, end);
    }
  };

  // node_modules/@xterm/xterm/src/browser/AccessibilityManager.ts
  var MAX_ROWS_TO_READ = 20;
  var DEBUG = false;
  var AccessibilityManager = class extends Disposable {
    constructor(_terminal, instantiationService, _coreBrowserService, _renderService) {
      super();
      this._terminal = _terminal;
      this._coreBrowserService = _coreBrowserService;
      this._renderService = _renderService;
      this._rowColumns = /* @__PURE__ */ new WeakMap();
      this._liveRegionLineCount = 0;
      /**
       * This queue has a character pushed to it for keys that are pressed, if the
       * next character added to the terminal is equal to the key char then it is
       * not announced (added to live region) because it has already been announced
       * by the textarea event (which cannot be canceled). There are some race
       * condition cases if there is typing while data is streaming, but this covers
       * the main case of typing into the prompt and inputting the answer to a
       * question (Y/N, etc.).
       */
      this._charsToConsume = [];
      this._charsToAnnounce = "";
      const doc = this._coreBrowserService.mainDocument;
      this._accessibilityContainer = doc.createElement("div");
      this._accessibilityContainer.classList.add("xterm-accessibility");
      this._rowContainer = doc.createElement("div");
      this._rowContainer.setAttribute("role", "list");
      this._rowContainer.classList.add("xterm-accessibility-tree");
      this._rowElements = [];
      for (let i2 = 0; i2 < this._terminal.rows; i2++) {
        this._rowElements[i2] = this._createAccessibilityTreeNode();
        this._rowContainer.appendChild(this._rowElements[i2]);
      }
      this._topBoundaryFocusListener = (e) => this._handleBoundaryFocus(e, 0 /* TOP */);
      this._bottomBoundaryFocusListener = (e) => this._handleBoundaryFocus(e, 1 /* BOTTOM */);
      this._rowElements[0].addEventListener("focus", this._topBoundaryFocusListener);
      this._rowElements[this._rowElements.length - 1].addEventListener("focus", this._bottomBoundaryFocusListener);
      this._accessibilityContainer.appendChild(this._rowContainer);
      this._liveRegion = doc.createElement("div");
      this._liveRegion.classList.add("live-region");
      this._liveRegion.setAttribute("aria-live", "assertive");
      this._accessibilityContainer.appendChild(this._liveRegion);
      this._liveRegionDebouncer = this._register(new TimeBasedDebouncer(this._renderRows.bind(this)));
      if (!this._terminal.element) {
        throw new Error("Cannot enable accessibility before Terminal.open");
      }
      if (DEBUG) {
        this._accessibilityContainer.classList.add("debug");
        this._rowContainer.classList.add("debug");
        this._debugRootContainer = doc.createElement("div");
        this._debugRootContainer.classList.add("xterm");
        this._debugRootContainer.appendChild(doc.createTextNode("------start a11y------"));
        this._debugRootContainer.appendChild(this._accessibilityContainer);
        this._debugRootContainer.appendChild(doc.createTextNode("------end a11y------"));
        this._terminal.element.insertAdjacentElement("afterend", this._debugRootContainer);
      } else {
        this._terminal.element.insertAdjacentElement("afterbegin", this._accessibilityContainer);
      }
      this._register(this._terminal.onResize((e) => this._handleResize(e.rows)));
      this._register(this._terminal.onRender((e) => this._refreshRows(e.start, e.end)));
      this._register(this._terminal.onScroll(() => this._refreshRows()));
      this._register(this._terminal.onA11yChar((char) => this._handleChar(char)));
      this._register(this._terminal.onLineFeed(() => this._handleChar("\n")));
      this._register(this._terminal.onA11yTab((spaceCount) => this._handleTab(spaceCount)));
      this._register(this._terminal.onKey((e) => this._handleKey(e.key)));
      this._register(this._terminal.onBlur(() => this._clearLiveRegion()));
      this._register(this._renderService.onDimensionsChange(() => this._refreshRowsDimensions()));
      this._register(addDisposableListener(doc, "selectionchange", () => this._handleSelectionChange()));
      this._register(this._coreBrowserService.onDprChange(() => this._refreshRowsDimensions()));
      this._refreshRowsDimensions();
      this._refreshRows();
      this._register(toDisposable(() => {
        if (DEBUG) {
          this._debugRootContainer.remove();
        } else {
          this._accessibilityContainer.remove();
        }
        this._rowElements.length = 0;
      }));
    }
    _handleTab(spaceCount) {
      for (let i2 = 0; i2 < spaceCount; i2++) {
        this._handleChar(" ");
      }
    }
    _handleChar(char) {
      if (this._liveRegionLineCount < MAX_ROWS_TO_READ + 1) {
        if (this._charsToConsume.length > 0) {
          const shiftedChar = this._charsToConsume.shift();
          if (shiftedChar !== char) {
            this._charsToAnnounce += char;
          }
        } else {
          this._charsToAnnounce += char;
        }
        if (char === "\n") {
          this._liveRegionLineCount++;
          if (this._liveRegionLineCount === MAX_ROWS_TO_READ + 1) {
            this._liveRegion.textContent += tooMuchOutput.get();
          }
        }
      }
    }
    _clearLiveRegion() {
      this._liveRegion.textContent = "";
      this._liveRegionLineCount = 0;
    }
    _handleKey(keyChar) {
      this._clearLiveRegion();
      if (!/\p{Control}/u.test(keyChar)) {
        this._charsToConsume.push(keyChar);
      }
    }
    _refreshRows(start, end) {
      this._liveRegionDebouncer.refresh(start, end, this._terminal.rows);
    }
    _renderRows(start, end) {
      const buffer = this._terminal.buffer;
      const setSize = buffer.lines.length.toString();
      for (let i2 = start; i2 <= end; i2++) {
        const line = buffer.lines.get(buffer.ydisp + i2);
        const columns = [];
        const lineData = line?.translateToString(true, void 0, void 0, columns) || "";
        const posInSet = (buffer.ydisp + i2 + 1).toString();
        const element = this._rowElements[i2];
        if (element) {
          if (lineData.length === 0) {
            element.textContent = " ";
            this._rowColumns.set(element, [0, 1]);
          } else {
            element.textContent = lineData;
            this._rowColumns.set(element, columns);
          }
          element.setAttribute("aria-posinset", posInSet);
          element.setAttribute("aria-setsize", setSize);
          this._alignRowWidth(element);
        }
      }
      this._announceCharacters();
    }
    _announceCharacters() {
      if (this._charsToAnnounce.length === 0) {
        return;
      }
      this._liveRegion.textContent += this._charsToAnnounce;
      this._charsToAnnounce = "";
    }
    _handleBoundaryFocus(e, position) {
      const boundaryElement = e.target;
      const beforeBoundaryElement = this._rowElements[position === 0 /* TOP */ ? 1 : this._rowElements.length - 2];
      const posInSet = boundaryElement.getAttribute("aria-posinset");
      const lastRowPos = position === 0 /* TOP */ ? "1" : `${this._terminal.buffer.lines.length}`;
      if (posInSet === lastRowPos) {
        return;
      }
      if (e.relatedTarget !== beforeBoundaryElement) {
        return;
      }
      let topBoundaryElement;
      let bottomBoundaryElement;
      if (position === 0 /* TOP */) {
        topBoundaryElement = boundaryElement;
        bottomBoundaryElement = this._rowElements.pop();
        this._rowContainer.removeChild(bottomBoundaryElement);
      } else {
        topBoundaryElement = this._rowElements.shift();
        bottomBoundaryElement = boundaryElement;
        this._rowContainer.removeChild(topBoundaryElement);
      }
      topBoundaryElement.removeEventListener("focus", this._topBoundaryFocusListener);
      bottomBoundaryElement.removeEventListener("focus", this._bottomBoundaryFocusListener);
      if (position === 0 /* TOP */) {
        const newElement = this._createAccessibilityTreeNode();
        this._rowElements.unshift(newElement);
        this._rowContainer.insertAdjacentElement("afterbegin", newElement);
      } else {
        const newElement = this._createAccessibilityTreeNode();
        this._rowElements.push(newElement);
        this._rowContainer.appendChild(newElement);
      }
      this._rowElements[0].addEventListener("focus", this._topBoundaryFocusListener);
      this._rowElements[this._rowElements.length - 1].addEventListener("focus", this._bottomBoundaryFocusListener);
      this._terminal.scrollLines(position === 0 /* TOP */ ? -1 : 1);
      this._rowElements[position === 0 /* TOP */ ? 1 : this._rowElements.length - 2].focus();
      e.preventDefault();
      e.stopImmediatePropagation();
    }
    _handleSelectionChange() {
      if (this._rowElements.length === 0) {
        return;
      }
      const selection = this._coreBrowserService.mainDocument.getSelection();
      if (!selection) {
        return;
      }
      if (selection.isCollapsed) {
        if (this._rowContainer.contains(selection.anchorNode)) {
          this._terminal.clearSelection();
        }
        return;
      }
      if (!selection.anchorNode || !selection.focusNode) {
        console.error("anchorNode and/or focusNode are null");
        return;
      }
      let begin = { node: selection.anchorNode, offset: selection.anchorOffset };
      let end = { node: selection.focusNode, offset: selection.focusOffset };
      if (begin.node.compareDocumentPosition(end.node) & Node.DOCUMENT_POSITION_PRECEDING || begin.node === end.node && begin.offset > end.offset) {
        [begin, end] = [end, begin];
      }
      if (begin.node.compareDocumentPosition(this._rowElements[0]) & (Node.DOCUMENT_POSITION_CONTAINED_BY | Node.DOCUMENT_POSITION_FOLLOWING)) {
        begin = { node: this._rowElements[0].childNodes[0], offset: 0 };
      }
      if (!this._rowContainer.contains(begin.node)) {
        return;
      }
      const lastRowElement = this._rowElements.slice(-1)[0];
      if (end.node.compareDocumentPosition(lastRowElement) & (Node.DOCUMENT_POSITION_CONTAINED_BY | Node.DOCUMENT_POSITION_PRECEDING)) {
        end = {
          node: lastRowElement,
          offset: lastRowElement.textContent?.length ?? 0
        };
      }
      if (!this._rowContainer.contains(end.node)) {
        return;
      }
      const toRowColumn = ({ node, offset }) => {
        const rowElement = node instanceof Text ? node.parentNode : node;
        let row = parseInt(rowElement?.getAttribute("aria-posinset"), 10) - 1;
        if (isNaN(row)) {
          console.warn("row is invalid. Race condition?");
          return null;
        }
        const columns = this._rowColumns.get(rowElement);
        if (!columns) {
          console.warn("columns is null. Race condition?");
          return null;
        }
        let column = offset < columns.length ? columns[offset] : columns.slice(-1)[0] + 1;
        if (column >= this._terminal.cols) {
          ++row;
          column = 0;
        }
        return {
          row,
          column
        };
      };
      const beginRowColumn = toRowColumn(begin);
      const endRowColumn = toRowColumn(end);
      if (!beginRowColumn || !endRowColumn) {
        return;
      }
      if (beginRowColumn.row > endRowColumn.row || beginRowColumn.row === endRowColumn.row && beginRowColumn.column >= endRowColumn.column) {
        throw new Error("invalid range");
      }
      this._terminal.select(
        beginRowColumn.column,
        beginRowColumn.row,
        (endRowColumn.row - beginRowColumn.row) * this._terminal.cols - beginRowColumn.column + endRowColumn.column
      );
    }
    _handleResize(rows) {
      this._rowElements[this._rowElements.length - 1].removeEventListener("focus", this._bottomBoundaryFocusListener);
      for (let i2 = this._rowContainer.children.length; i2 < this._terminal.rows; i2++) {
        this._rowElements[i2] = this._createAccessibilityTreeNode();
        this._rowContainer.appendChild(this._rowElements[i2]);
      }
      while (this._rowElements.length > rows) {
        this._rowContainer.removeChild(this._rowElements.pop());
      }
      this._rowElements[this._rowElements.length - 1].addEventListener("focus", this._bottomBoundaryFocusListener);
      this._refreshRowsDimensions();
    }
    _createAccessibilityTreeNode() {
      const element = this._coreBrowserService.mainDocument.createElement("div");
      element.setAttribute("role", "listitem");
      element.tabIndex = -1;
      this._refreshRowDimensions(element);
      return element;
    }
    _refreshRowsDimensions() {
      if (!this._renderService.dimensions.css.cell.height) {
        return;
      }
      Object.assign(this._accessibilityContainer.style, {
        width: `${this._renderService.dimensions.css.canvas.width}px`,
        fontSize: `${this._terminal.options.fontSize}px`
      });
      if (this._rowElements.length !== this._terminal.rows) {
        this._handleResize(this._terminal.rows);
      }
      for (let i2 = 0; i2 < this._terminal.rows; i2++) {
        this._refreshRowDimensions(this._rowElements[i2]);
        this._alignRowWidth(this._rowElements[i2]);
      }
    }
    _refreshRowDimensions(element) {
      element.style.height = `${this._renderService.dimensions.css.cell.height}px`;
    }
    /**
     * Scale the width of a row so that each of the character is (mostly) aligned
     * with the actual rendering. This will allow the screen reader to draw
     * selection outline at the correct position.
     *
     * On top of using the "monospace" font and correct font size, the scaling
     * here is necessary to handle characters that are not covered by the font
     * (e.g. CJK).
     */
    _alignRowWidth(element) {
      element.style.transform = "";
      const width = element.getBoundingClientRect().width;
      const lastColumn = this._rowColumns.get(element)?.slice(-1)?.[0];
      if (!lastColumn) {
        return;
      }
      const targetWidth = lastColumn * this._renderService.dimensions.css.cell.width;
      element.style.transform = `scaleX(${targetWidth / width})`;
    }
  };
  AccessibilityManager = __decorateClass([
    __decorateParam(1, IInstantiationService),
    __decorateParam(2, ICoreBrowserService),
    __decorateParam(3, IRenderService)
  ], AccessibilityManager);

  // node_modules/@xterm/xterm/src/browser/Linkifier.ts
  var Linkifier = class extends Disposable {
    constructor(_element, _mouseService, _renderService, _bufferService, _linkProviderService) {
      super();
      this._element = _element;
      this._mouseService = _mouseService;
      this._renderService = _renderService;
      this._bufferService = _bufferService;
      this._linkProviderService = _linkProviderService;
      this._linkCacheDisposables = [];
      this._isMouseOut = true;
      this._wasResized = false;
      this._activeLine = -1;
      this._onShowLinkUnderline = this._register(new Emitter());
      this.onShowLinkUnderline = this._onShowLinkUnderline.event;
      this._onHideLinkUnderline = this._register(new Emitter());
      this.onHideLinkUnderline = this._onHideLinkUnderline.event;
      this._register(toDisposable(() => {
        dispose(this._linkCacheDisposables);
        this._linkCacheDisposables.length = 0;
        this._lastMouseEvent = void 0;
        this._activeProviderReplies?.clear();
      }));
      this._register(this._bufferService.onResize(() => {
        this._clearCurrentLink();
        this._wasResized = true;
      }));
      this._register(addDisposableListener(this._element, "mouseleave", () => {
        this._isMouseOut = true;
        this._clearCurrentLink();
      }));
      this._register(addDisposableListener(this._element, "mousemove", this._handleMouseMove.bind(this)));
      this._register(addDisposableListener(this._element, "mousedown", this._handleMouseDown.bind(this)));
      this._register(addDisposableListener(this._element, "mouseup", this._handleMouseUp.bind(this)));
    }
    get currentLink() {
      return this._currentLink;
    }
    _handleMouseMove(event) {
      this._lastMouseEvent = event;
      const position = this._positionFromMouseEvent(event, this._element, this._mouseService);
      if (!position) {
        return;
      }
      this._isMouseOut = false;
      const composedPath = event.composedPath();
      for (let i2 = 0; i2 < composedPath.length; i2++) {
        const target = composedPath[i2];
        if (target.classList.contains("xterm")) {
          break;
        }
        if (target.classList.contains("xterm-hover")) {
          return;
        }
      }
      if (!this._lastBufferCell || (position.x !== this._lastBufferCell.x || position.y !== this._lastBufferCell.y)) {
        this._handleHover(position);
        this._lastBufferCell = position;
      }
    }
    _handleHover(position) {
      if (this._activeLine !== position.y || this._wasResized) {
        this._clearCurrentLink();
        this._askForLink(position, false);
        this._wasResized = false;
        return;
      }
      const isCurrentLinkInPosition = this._currentLink && this._linkAtPosition(this._currentLink.link, position);
      if (!isCurrentLinkInPosition) {
        this._clearCurrentLink();
        this._askForLink(position, true);
      }
    }
    _askForLink(position, useLineCache) {
      if (!this._activeProviderReplies || !useLineCache) {
        this._activeProviderReplies?.forEach((reply) => {
          reply?.forEach((linkWithState) => {
            if (linkWithState.link.dispose) {
              linkWithState.link.dispose();
            }
          });
        });
        this._activeProviderReplies = /* @__PURE__ */ new Map();
        this._activeLine = position.y;
      }
      let linkProvided = false;
      for (const [i2, linkProvider] of this._linkProviderService.linkProviders.entries()) {
        if (useLineCache) {
          const existingReply = this._activeProviderReplies?.get(i2);
          if (existingReply) {
            linkProvided = this._checkLinkProviderResult(i2, position, linkProvided);
          }
        } else {
          linkProvider.provideLinks(position.y, (links) => {
            if (this._isMouseOut) {
              return;
            }
            const linksWithState = links?.map((link) => ({ link }));
            this._activeProviderReplies?.set(i2, linksWithState);
            linkProvided = this._checkLinkProviderResult(i2, position, linkProvided);
            if (this._activeProviderReplies?.size === this._linkProviderService.linkProviders.length) {
              this._removeIntersectingLinks(position.y, this._activeProviderReplies);
            }
          });
        }
      }
    }
    _removeIntersectingLinks(y, replies) {
      const occupiedCells = /* @__PURE__ */ new Set();
      for (let i2 = 0; i2 < replies.size; i2++) {
        const providerReply = replies.get(i2);
        if (!providerReply) {
          continue;
        }
        for (let i3 = 0; i3 < providerReply.length; i3++) {
          const linkWithState = providerReply[i3];
          const startX = linkWithState.link.range.start.y < y ? 0 : linkWithState.link.range.start.x;
          const endX = linkWithState.link.range.end.y > y ? this._bufferService.cols : linkWithState.link.range.end.x;
          for (let x = startX; x <= endX; x++) {
            if (occupiedCells.has(x)) {
              providerReply.splice(i3--, 1);
              break;
            }
            occupiedCells.add(x);
          }
        }
      }
    }
    _checkLinkProviderResult(index, position, linkProvided) {
      if (!this._activeProviderReplies) {
        return linkProvided;
      }
      const links = this._activeProviderReplies.get(index);
      let hasLinkBefore = false;
      for (let j = 0; j < index; j++) {
        if (!this._activeProviderReplies.has(j) || this._activeProviderReplies.get(j)) {
          hasLinkBefore = true;
        }
      }
      if (!hasLinkBefore && links) {
        const linkAtPosition = links.find((link) => this._linkAtPosition(link.link, position));
        if (linkAtPosition) {
          linkProvided = true;
          this._handleNewLink(linkAtPosition);
        }
      }
      if (this._activeProviderReplies.size === this._linkProviderService.linkProviders.length && !linkProvided) {
        for (let j = 0; j < this._activeProviderReplies.size; j++) {
          const currentLink = this._activeProviderReplies.get(j)?.find((link) => this._linkAtPosition(link.link, position));
          if (currentLink) {
            linkProvided = true;
            this._handleNewLink(currentLink);
            break;
          }
        }
      }
      return linkProvided;
    }
    _handleMouseDown() {
      this._mouseDownLink = this._currentLink;
    }
    _handleMouseUp(event) {
      if (!this._currentLink) {
        return;
      }
      const position = this._positionFromMouseEvent(event, this._element, this._mouseService);
      if (!position) {
        return;
      }
      if (this._mouseDownLink && linkEquals(this._mouseDownLink.link, this._currentLink.link) && this._linkAtPosition(this._currentLink.link, position)) {
        this._currentLink.link.activate(event, this._currentLink.link.text);
      }
    }
    _clearCurrentLink(startRow, endRow) {
      if (!this._currentLink || !this._lastMouseEvent) {
        return;
      }
      if (!startRow || !endRow || this._currentLink.link.range.start.y >= startRow && this._currentLink.link.range.end.y <= endRow) {
        this._linkLeave(this._element, this._currentLink.link, this._lastMouseEvent);
        this._currentLink = void 0;
        dispose(this._linkCacheDisposables);
        this._linkCacheDisposables.length = 0;
      }
    }
    _handleNewLink(linkWithState) {
      if (!this._lastMouseEvent) {
        return;
      }
      const position = this._positionFromMouseEvent(this._lastMouseEvent, this._element, this._mouseService);
      if (!position) {
        return;
      }
      if (this._linkAtPosition(linkWithState.link, position)) {
        this._currentLink = linkWithState;
        this._currentLink.state = {
          decorations: {
            underline: linkWithState.link.decorations === void 0 ? true : linkWithState.link.decorations.underline,
            pointerCursor: linkWithState.link.decorations === void 0 ? true : linkWithState.link.decorations.pointerCursor
          },
          isHovered: true
        };
        this._linkHover(this._element, linkWithState.link, this._lastMouseEvent);
        linkWithState.link.decorations = {};
        Object.defineProperties(linkWithState.link.decorations, {
          pointerCursor: {
            get: () => this._currentLink?.state?.decorations.pointerCursor,
            set: (v) => {
              if (this._currentLink?.state && this._currentLink.state.decorations.pointerCursor !== v) {
                this._currentLink.state.decorations.pointerCursor = v;
                if (this._currentLink.state.isHovered) {
                  this._element.classList.toggle("xterm-cursor-pointer", v);
                }
              }
            }
          },
          underline: {
            get: () => this._currentLink?.state?.decorations.underline,
            set: (v) => {
              if (this._currentLink?.state && this._currentLink?.state?.decorations.underline !== v) {
                this._currentLink.state.decorations.underline = v;
                if (this._currentLink.state.isHovered) {
                  this._fireUnderlineEvent(linkWithState.link, v);
                }
              }
            }
          }
        });
        this._linkCacheDisposables.push(this._renderService.onRenderedViewportChange((e) => {
          if (!this._currentLink) {
            return;
          }
          const start = e.start === 0 ? 0 : e.start + 1 + this._bufferService.buffer.ydisp;
          const end = this._bufferService.buffer.ydisp + 1 + e.end;
          if (this._currentLink.link.range.start.y >= start && this._currentLink.link.range.end.y <= end) {
            this._clearCurrentLink(start, end);
            if (this._lastMouseEvent) {
              const position2 = this._positionFromMouseEvent(this._lastMouseEvent, this._element, this._mouseService);
              if (position2) {
                this._askForLink(position2, false);
              }
            }
          }
        }));
      }
    }
    _linkHover(element, link, event) {
      if (this._currentLink?.state) {
        this._currentLink.state.isHovered = true;
        if (this._currentLink.state.decorations.underline) {
          this._fireUnderlineEvent(link, true);
        }
        if (this._currentLink.state.decorations.pointerCursor) {
          element.classList.add("xterm-cursor-pointer");
        }
      }
      if (link.hover) {
        link.hover(event, link.text);
      }
    }
    _fireUnderlineEvent(link, showEvent) {
      const range = link.range;
      const scrollOffset = this._bufferService.buffer.ydisp;
      const event = this._createLinkUnderlineEvent(range.start.x - 1, range.start.y - scrollOffset - 1, range.end.x, range.end.y - scrollOffset - 1, void 0);
      const emitter = showEvent ? this._onShowLinkUnderline : this._onHideLinkUnderline;
      emitter.fire(event);
    }
    _linkLeave(element, link, event) {
      if (this._currentLink?.state) {
        this._currentLink.state.isHovered = false;
        if (this._currentLink.state.decorations.underline) {
          this._fireUnderlineEvent(link, false);
        }
        if (this._currentLink.state.decorations.pointerCursor) {
          element.classList.remove("xterm-cursor-pointer");
        }
      }
      if (link.leave) {
        link.leave(event, link.text);
      }
    }
    /**
     * Check if the buffer position is within the link
     * @param link
     * @param position
     */
    _linkAtPosition(link, position) {
      const lower = link.range.start.y * this._bufferService.cols + link.range.start.x;
      const upper = link.range.end.y * this._bufferService.cols + link.range.end.x;
      const current = position.y * this._bufferService.cols + position.x;
      return lower <= current && current <= upper;
    }
    /**
     * Get the buffer position from a mouse event
     * @param event
     */
    _positionFromMouseEvent(event, element, mouseService) {
      const coords = mouseService.getCoords(event, element, this._bufferService.cols, this._bufferService.rows);
      if (!coords) {
        return;
      }
      return { x: coords[0], y: coords[1] + this._bufferService.buffer.ydisp };
    }
    _createLinkUnderlineEvent(x1, y1, x2, y2, fg) {
      return { x1, y1, x2, y2, cols: this._bufferService.cols, fg };
    }
  };
  Linkifier = __decorateClass([
    __decorateParam(1, IMouseService),
    __decorateParam(2, IRenderService),
    __decorateParam(3, IBufferService),
    __decorateParam(4, ILinkProviderService)
  ], Linkifier);
  function linkEquals(a, b) {
    return a.text === b.text && a.range.start.x === b.range.start.x && a.range.start.y === b.range.start.y && a.range.end.x === b.range.end.x && a.range.end.y === b.range.end.y;
  }

  // node_modules/@xterm/xterm/src/browser/CoreBrowserTerminal.ts
  var CoreBrowserTerminal = class extends CoreTerminal {
    constructor(options = {}) {
      super(options);
      this._linkifier = this._register(new MutableDisposable());
      this.browser = Platform_exports;
      /**
       * Records whether the keydown event has already been handled and triggered a data event, if so
       * the keypress event should not trigger a data event but should still print to the textarea so
       * screen readers will announce it.
       */
      this._keyDownHandled = false;
      /**
       * Records whether a keydown event has occured since the last keyup event, i.e. whether a key
       * is currently "pressed".
       */
      this._keyDownSeen = false;
      /**
       * Records whether the keypress event has already been handled and triggered a data event, if so
       * the input event should not trigger a data event but should still print to the textarea so
       * screen readers will announce it.
       */
      this._keyPressHandled = false;
      /**
       * Records whether there has been a keydown event for a dead key without a corresponding keydown
       * event for the composed/alternative character. If we cancel the keydown event for the dead key,
       * no events will be emitted for the final character.
       */
      this._unprocessedDeadKey = false;
      this._accessibilityManager = this._register(new MutableDisposable());
      this._onCursorMove = this._register(new Emitter());
      this.onCursorMove = this._onCursorMove.event;
      this._onKey = this._register(new Emitter());
      this.onKey = this._onKey.event;
      this._onRender = this._register(new Emitter());
      this.onRender = this._onRender.event;
      this._onSelectionChange = this._register(new Emitter());
      this.onSelectionChange = this._onSelectionChange.event;
      this._onTitleChange = this._register(new Emitter());
      this.onTitleChange = this._onTitleChange.event;
      this._onBell = this._register(new Emitter());
      this.onBell = this._onBell.event;
      this._onFocus = this._register(new Emitter());
      this._onBlur = this._register(new Emitter());
      this._onA11yCharEmitter = this._register(new Emitter());
      this._onA11yTabEmitter = this._register(new Emitter());
      this._onWillOpen = this._register(new Emitter());
      this._setup();
      this._decorationService = this._instantiationService.createInstance(DecorationService);
      this._instantiationService.setService(IDecorationService, this._decorationService);
      this._linkProviderService = this._instantiationService.createInstance(LinkProviderService);
      this._instantiationService.setService(ILinkProviderService, this._linkProviderService);
      this._linkProviderService.registerLinkProvider(this._instantiationService.createInstance(OscLinkProvider));
      this._register(this._inputHandler.onRequestBell(() => this._onBell.fire()));
      this._register(this._inputHandler.onRequestRefreshRows((e) => this.refresh(e?.start ?? 0, e?.end ?? this.rows - 1)));
      this._register(this._inputHandler.onRequestSendFocus(() => this._reportFocus()));
      this._register(this._inputHandler.onRequestReset(() => this.reset()));
      this._register(this._inputHandler.onRequestWindowsOptionsReport((type) => this._reportWindowsOptions(type)));
      this._register(this._inputHandler.onColor((event) => this._handleColorEvent(event)));
      this._register(Event.forward(this._inputHandler.onCursorMove, this._onCursorMove));
      this._register(Event.forward(this._inputHandler.onTitleChange, this._onTitleChange));
      this._register(Event.forward(this._inputHandler.onA11yChar, this._onA11yCharEmitter));
      this._register(Event.forward(this._inputHandler.onA11yTab, this._onA11yTabEmitter));
      this._register(this._bufferService.onResize((e) => this._afterResize(e.cols, e.rows)));
      this._register(toDisposable(() => {
        this._customKeyEventHandler = void 0;
        this.element?.parentNode?.removeChild(this.element);
      }));
    }
    get linkifier() {
      return this._linkifier.value;
    }
    get onFocus() {
      return this._onFocus.event;
    }
    get onBlur() {
      return this._onBlur.event;
    }
    get onA11yChar() {
      return this._onA11yCharEmitter.event;
    }
    get onA11yTab() {
      return this._onA11yTabEmitter.event;
    }
    get onWillOpen() {
      return this._onWillOpen.event;
    }
    /**
     * Handle color event from inputhandler for OSC 4|104 | 10|110 | 11|111 | 12|112.
     * An event from OSC 4|104 may contain multiple set or report requests, and multiple
     * or none restore requests (resetting all),
     * while an event from OSC 10|110 | 11|111 | 12|112 always contains a single request.
     */
    _handleColorEvent(event) {
      if (!this._themeService) return;
      for (const req of event) {
        let acc;
        let ident = "";
        switch (req.index) {
          case 256 /* FOREGROUND */:
            acc = "foreground";
            ident = "10";
            break;
          case 257 /* BACKGROUND */:
            acc = "background";
            ident = "11";
            break;
          case 258 /* CURSOR */:
            acc = "cursor";
            ident = "12";
            break;
          default:
            acc = "ansi";
            ident = "4;" + req.index;
        }
        switch (req.type) {
          case 0 /* REPORT */:
            const colorRgb = color.toColorRGB(acc === "ansi" ? this._themeService.colors.ansi[req.index] : this._themeService.colors[acc]);
            this.coreService.triggerDataEvent(`${C0.ESC}]${ident};${toRgbString(colorRgb)}${C1_ESCAPED.ST}`);
            break;
          case 1 /* SET */:
            if (acc === "ansi") {
              this._themeService.modifyColors((colors) => colors.ansi[req.index] = channels.toColor(...req.color));
            } else {
              const narrowedAcc = acc;
              this._themeService.modifyColors((colors) => colors[narrowedAcc] = channels.toColor(...req.color));
            }
            break;
          case 2 /* RESTORE */:
            this._themeService.restoreColor(req.index);
            break;
        }
      }
    }
    _setup() {
      super._setup();
      this._customKeyEventHandler = void 0;
    }
    /**
     * Convenience property to active buffer.
     */
    get buffer() {
      return this.buffers.active;
    }
    /**
     * Focus the terminal. Delegates focus handling to the terminal's DOM element.
     */
    focus() {
      if (this.textarea) {
        this.textarea.focus({ preventScroll: true });
      }
    }
    _handleScreenReaderModeOptionChange(value) {
      if (value) {
        if (!this._accessibilityManager.value && this._renderService) {
          this._accessibilityManager.value = this._instantiationService.createInstance(AccessibilityManager, this);
        }
      } else {
        this._accessibilityManager.clear();
      }
    }
    /**
     * Binds the desired focus behavior on a given terminal object.
     */
    _handleTextAreaFocus(ev) {
      if (this.coreService.decPrivateModes.sendFocus) {
        this.coreService.triggerDataEvent(C0.ESC + "[I");
      }
      this.element.classList.add("focus");
      this._showCursor();
      this._onFocus.fire();
    }
    /**
     * Blur the terminal, calling the blur function on the terminal's underlying
     * textarea.
     */
    blur() {
      return this.textarea?.blur();
    }
    /**
     * Binds the desired blur behavior on a given terminal object.
     */
    _handleTextAreaBlur() {
      this.textarea.value = "";
      this.refresh(this.buffer.y, this.buffer.y);
      if (this.coreService.decPrivateModes.sendFocus) {
        this.coreService.triggerDataEvent(C0.ESC + "[O");
      }
      this.element.classList.remove("focus");
      this._onBlur.fire();
    }
    _syncTextArea() {
      if (!this.textarea || !this.buffer.isCursorInViewport || this._compositionHelper.isComposing || !this._renderService) {
        return;
      }
      const cursorY = this.buffer.ybase + this.buffer.y;
      const bufferLine2 = this.buffer.lines.get(cursorY);
      if (!bufferLine2) {
        return;
      }
      const cursorX = Math.min(this.buffer.x, this.cols - 1);
      const cellHeight = this._renderService.dimensions.css.cell.height;
      const width = bufferLine2.getWidth(cursorX);
      const cellWidth = this._renderService.dimensions.css.cell.width * width;
      const cursorTop = this.buffer.y * this._renderService.dimensions.css.cell.height;
      const cursorLeft = cursorX * this._renderService.dimensions.css.cell.width;
      this.textarea.style.left = cursorLeft + "px";
      this.textarea.style.top = cursorTop + "px";
      this.textarea.style.width = cellWidth + "px";
      this.textarea.style.height = cellHeight + "px";
      this.textarea.style.lineHeight = cellHeight + "px";
      this.textarea.style.zIndex = "-5";
    }
    /**
     * Initialize default behavior
     */
    _initGlobal() {
      this._bindKeys();
      this._register(addDisposableListener(this.element, "copy", (event) => {
        if (!this.hasSelection()) {
          return;
        }
        copyHandler(event, this._selectionService);
      }));
      const pasteHandlerWrapper = (event) => handlePasteEvent(event, this.textarea, this.coreService, this.optionsService);
      this._register(addDisposableListener(this.textarea, "paste", pasteHandlerWrapper));
      this._register(addDisposableListener(this.element, "paste", pasteHandlerWrapper));
      if (isFirefox3) {
        this._register(addDisposableListener(this.element, "mousedown", (event) => {
          if (event.button === 2) {
            rightClickHandler(event, this.textarea, this.screenElement, this._selectionService, this.options.rightClickSelectsWord);
          }
        }));
      } else {
        this._register(addDisposableListener(this.element, "contextmenu", (event) => {
          rightClickHandler(event, this.textarea, this.screenElement, this._selectionService, this.options.rightClickSelectsWord);
        }));
      }
      if (isLinux2) {
        this._register(addDisposableListener(this.element, "auxclick", (event) => {
          if (event.button === 1) {
            moveTextAreaUnderMouseCursor(event, this.textarea, this.screenElement);
          }
        }));
      }
    }
    /**
     * Apply key handling to the terminal
     */
    _bindKeys() {
      this._register(addDisposableListener(this.textarea, "keyup", (ev) => this._keyUp(ev), true));
      this._register(addDisposableListener(this.textarea, "keydown", (ev) => this._keyDown(ev), true));
      this._register(addDisposableListener(this.textarea, "keypress", (ev) => this._keyPress(ev), true));
      this._register(addDisposableListener(this.textarea, "compositionstart", () => this._compositionHelper.compositionstart()));
      this._register(addDisposableListener(this.textarea, "compositionupdate", (e) => this._compositionHelper.compositionupdate(e)));
      this._register(addDisposableListener(this.textarea, "compositionend", () => this._compositionHelper.compositionend()));
      this._register(addDisposableListener(this.textarea, "input", (ev) => this._inputEvent(ev), true));
      this._register(this.onRender(() => this._compositionHelper.updateCompositionElements()));
    }
    /**
     * Opens the terminal within an element.
     *
     * @param parent The element to create the terminal within.
     */
    open(parent) {
      if (!parent) {
        throw new Error("Terminal requires a parent element.");
      }
      if (!parent.isConnected) {
        this._logService.debug("Terminal.open was called on an element that was not attached to the DOM");
      }
      if (this.element?.ownerDocument.defaultView && this._coreBrowserService) {
        if (this.element.ownerDocument.defaultView !== this._coreBrowserService.window) {
          this._coreBrowserService.window = this.element.ownerDocument.defaultView;
        }
        return;
      }
      this._document = parent.ownerDocument;
      if (this.options.documentOverride && this.options.documentOverride instanceof Document) {
        this._document = this.optionsService.rawOptions.documentOverride;
      }
      this.element = this._document.createElement("div");
      this.element.dir = "ltr";
      this.element.classList.add("terminal");
      this.element.classList.add("xterm");
      parent.appendChild(this.element);
      const fragment = this._document.createDocumentFragment();
      this._viewportElement = this._document.createElement("div");
      this._viewportElement.classList.add("xterm-viewport");
      fragment.appendChild(this._viewportElement);
      this.screenElement = this._document.createElement("div");
      this.screenElement.classList.add("xterm-screen");
      this._register(addDisposableListener(this.screenElement, "mousemove", (ev) => this.updateCursorStyle(ev)));
      this._helperContainer = this._document.createElement("div");
      this._helperContainer.classList.add("xterm-helpers");
      this.screenElement.appendChild(this._helperContainer);
      fragment.appendChild(this.screenElement);
      const textarea = this.textarea = this._document.createElement("textarea");
      this.textarea.classList.add("xterm-helper-textarea");
      this.textarea.setAttribute("aria-label", promptLabel.get());
      if (!isChromeOS) {
        this.textarea.setAttribute("aria-multiline", "false");
      }
      this.textarea.setAttribute("autocorrect", "off");
      this.textarea.setAttribute("autocapitalize", "off");
      this.textarea.setAttribute("spellcheck", "false");
      this.textarea.tabIndex = 0;
      this._register(this.optionsService.onSpecificOptionChange("disableStdin", () => textarea.readOnly = this.optionsService.rawOptions.disableStdin));
      this.textarea.readOnly = this.optionsService.rawOptions.disableStdin;
      this._coreBrowserService = this._register(this._instantiationService.createInstance(
        CoreBrowserService,
        this.textarea,
        parent.ownerDocument.defaultView ?? window,
        // Force unsafe null in node.js environment for tests
        this._document ?? typeof window !== "undefined" ? window.document : null
      ));
      this._instantiationService.setService(ICoreBrowserService, this._coreBrowserService);
      this._register(addDisposableListener(this.textarea, "focus", (ev) => this._handleTextAreaFocus(ev)));
      this._register(addDisposableListener(this.textarea, "blur", () => this._handleTextAreaBlur()));
      this._helperContainer.appendChild(this.textarea);
      this._charSizeService = this._instantiationService.createInstance(CharSizeService, this._document, this._helperContainer);
      this._instantiationService.setService(ICharSizeService, this._charSizeService);
      this._themeService = this._instantiationService.createInstance(ThemeService);
      this._instantiationService.setService(IThemeService, this._themeService);
      this._characterJoinerService = this._instantiationService.createInstance(CharacterJoinerService);
      this._instantiationService.setService(ICharacterJoinerService, this._characterJoinerService);
      this._renderService = this._register(this._instantiationService.createInstance(RenderService, this.rows, this.screenElement));
      this._instantiationService.setService(IRenderService, this._renderService);
      this._register(this._renderService.onRenderedViewportChange((e) => this._onRender.fire(e)));
      this.onResize((e) => this._renderService.resize(e.cols, e.rows));
      this._compositionView = this._document.createElement("div");
      this._compositionView.classList.add("composition-view");
      this._compositionHelper = this._instantiationService.createInstance(CompositionHelper, this.textarea, this._compositionView);
      this._helperContainer.appendChild(this._compositionView);
      this._mouseService = this._instantiationService.createInstance(MouseService);
      this._instantiationService.setService(IMouseService, this._mouseService);
      const linkifier = this._linkifier.value = this._register(this._instantiationService.createInstance(Linkifier, this.screenElement));
      this.element.appendChild(fragment);
      try {
        this._onWillOpen.fire(this.element);
      } catch {
      }
      if (!this._renderService.hasRenderer()) {
        this._renderService.setRenderer(this._createRenderer());
      }
      this._register(this.onCursorMove(() => {
        this._renderService.handleCursorMove();
        this._syncTextArea();
      }));
      this._register(this.onResize(() => this._renderService.handleResize(this.cols, this.rows)));
      this._register(this.onBlur(() => this._renderService.handleBlur()));
      this._register(this.onFocus(() => this._renderService.handleFocus()));
      this._viewport = this._register(this._instantiationService.createInstance(Viewport, this.element, this.screenElement));
      this._register(this._viewport.onRequestScrollLines((e) => {
        super.scrollLines(e, false);
        this.refresh(0, this.rows - 1);
      }));
      this._selectionService = this._register(this._instantiationService.createInstance(
        SelectionService,
        this.element,
        this.screenElement,
        linkifier
      ));
      this._instantiationService.setService(ISelectionService, this._selectionService);
      this._register(this._selectionService.onRequestScrollLines((e) => this.scrollLines(e.amount, e.suppressScrollEvent)));
      this._register(this._selectionService.onSelectionChange(() => this._onSelectionChange.fire()));
      this._register(this._selectionService.onRequestRedraw((e) => this._renderService.handleSelectionChanged(e.start, e.end, e.columnSelectMode)));
      this._register(this._selectionService.onLinuxMouseSelection((text) => {
        this.textarea.value = text;
        this.textarea.focus();
        this.textarea.select();
      }));
      this._register(Event.any(
        this._onScroll.event,
        this._inputHandler.onScroll
      )(() => {
        this._selectionService.refresh();
        this._viewport?.queueSync();
      }));
      this._register(this._instantiationService.createInstance(BufferDecorationRenderer, this.screenElement));
      this._register(addDisposableListener(this.element, "mousedown", (e) => this._selectionService.handleMouseDown(e)));
      if (this.coreMouseService.areMouseEventsActive) {
        this._selectionService.disable();
        this.element.classList.add("enable-mouse-events");
      } else {
        this._selectionService.enable();
      }
      if (this.options.screenReaderMode) {
        this._accessibilityManager.value = this._instantiationService.createInstance(AccessibilityManager, this);
      }
      this._register(this.optionsService.onSpecificOptionChange("screenReaderMode", (e) => this._handleScreenReaderModeOptionChange(e)));
      if (this.options.overviewRuler.width) {
        this._overviewRulerRenderer = this._register(this._instantiationService.createInstance(OverviewRulerRenderer, this._viewportElement, this.screenElement));
      }
      this.optionsService.onSpecificOptionChange("overviewRuler", (value) => {
        if (!this._overviewRulerRenderer && value && this._viewportElement && this.screenElement) {
          this._overviewRulerRenderer = this._register(this._instantiationService.createInstance(OverviewRulerRenderer, this._viewportElement, this.screenElement));
        }
      });
      this._charSizeService.measure();
      this.refresh(0, this.rows - 1);
      this._initGlobal();
      this.bindMouse();
    }
    _createRenderer() {
      return this._instantiationService.createInstance(DomRenderer, this, this._document, this.element, this.screenElement, this._viewportElement, this._helperContainer, this.linkifier);
    }
    /**
     * Bind certain mouse events to the terminal.
     * By default only 3 button + wheel up/down is ativated. For higher buttons
     * no mouse report will be created. Typically the standard actions will be active.
     *
     * There are several reasons not to enable support for higher buttons/wheel:
     * - Button 4 and 5 are typically used for history back and forward navigation,
     *   there is no straight forward way to supress/intercept those standard actions.
     * - Support for higher buttons does not work in some platform/browser combinations.
     * - Left/right wheel was not tested.
     * - Emulators vary in mouse button support, typically only 3 buttons and
     *   wheel up/down work reliable.
     *
     * TODO: Move mouse event code into its own file.
     */
    bindMouse() {
      const self = this;
      const el = this.element;
      function sendEvent(ev) {
        const pos = self._mouseService.getMouseReportCoords(ev, self.screenElement);
        if (!pos) {
          return false;
        }
        let but;
        let action;
        switch (ev.overrideType || ev.type) {
          case "mousemove":
            action = 32 /* MOVE */;
            if (ev.buttons === void 0) {
              but = 3 /* NONE */;
              if (ev.button !== void 0) {
                but = ev.button < 3 ? ev.button : 3 /* NONE */;
              }
            } else {
              but = ev.buttons & 1 ? 0 /* LEFT */ : ev.buttons & 4 ? 1 /* MIDDLE */ : ev.buttons & 2 ? 2 /* RIGHT */ : 3 /* NONE */;
            }
            break;
          case "mouseup":
            action = 0 /* UP */;
            but = ev.button < 3 ? ev.button : 3 /* NONE */;
            break;
          case "mousedown":
            action = 1 /* DOWN */;
            but = ev.button < 3 ? ev.button : 3 /* NONE */;
            break;
          case "wheel":
            if (self._customWheelEventHandler && self._customWheelEventHandler(ev) === false) {
              return false;
            }
            const deltaY = ev.deltaY;
            if (deltaY === 0) {
              return false;
            }
            const lines = self.coreMouseService.consumeWheelEvent(
              ev,
              self._renderService?.dimensions?.device?.cell?.height,
              self._coreBrowserService?.dpr
            );
            if (lines === 0) {
              return false;
            }
            action = deltaY < 0 ? 0 /* UP */ : 1 /* DOWN */;
            but = 4 /* WHEEL */;
            break;
          default:
            return false;
        }
        if (action === void 0 || but === void 0 || but > 4 /* WHEEL */) {
          return false;
        }
        return self.coreMouseService.triggerMouseEvent({
          col: pos.col,
          row: pos.row,
          x: pos.x,
          y: pos.y,
          button: but,
          action,
          ctrl: ev.ctrlKey,
          alt: ev.altKey,
          shift: ev.shiftKey
        });
      }
      const requestedEvents = {
        mouseup: null,
        wheel: null,
        mousedrag: null,
        mousemove: null
      };
      const eventListeners = {
        mouseup: (ev) => {
          sendEvent(ev);
          if (!ev.buttons) {
            this._document.removeEventListener("mouseup", requestedEvents.mouseup);
            if (requestedEvents.mousedrag) {
              this._document.removeEventListener("mousemove", requestedEvents.mousedrag);
            }
          }
          return this.cancel(ev);
        },
        wheel: (ev) => {
          sendEvent(ev);
          return this.cancel(ev, true);
        },
        mousedrag: (ev) => {
          if (ev.buttons) {
            sendEvent(ev);
          }
        },
        mousemove: (ev) => {
          if (!ev.buttons) {
            sendEvent(ev);
          }
        }
      };
      this._register(this.coreMouseService.onProtocolChange((events) => {
        if (events) {
          if (this.optionsService.rawOptions.logLevel === "debug") {
            this._logService.debug("Binding to mouse events:", this.coreMouseService.explainEvents(events));
          }
          this.element.classList.add("enable-mouse-events");
          this._selectionService.disable();
        } else {
          this._logService.debug("Unbinding from mouse events.");
          this.element.classList.remove("enable-mouse-events");
          this._selectionService.enable();
        }
        if (!(events & 8 /* MOVE */)) {
          el.removeEventListener("mousemove", requestedEvents.mousemove);
          requestedEvents.mousemove = null;
        } else if (!requestedEvents.mousemove) {
          el.addEventListener("mousemove", eventListeners.mousemove);
          requestedEvents.mousemove = eventListeners.mousemove;
        }
        if (!(events & 16 /* WHEEL */)) {
          el.removeEventListener("wheel", requestedEvents.wheel);
          requestedEvents.wheel = null;
        } else if (!requestedEvents.wheel) {
          el.addEventListener("wheel", eventListeners.wheel, { passive: false });
          requestedEvents.wheel = eventListeners.wheel;
        }
        if (!(events & 2 /* UP */)) {
          this._document.removeEventListener("mouseup", requestedEvents.mouseup);
          requestedEvents.mouseup = null;
        } else if (!requestedEvents.mouseup) {
          requestedEvents.mouseup = eventListeners.mouseup;
        }
        if (!(events & 4 /* DRAG */)) {
          this._document.removeEventListener("mousemove", requestedEvents.mousedrag);
          requestedEvents.mousedrag = null;
        } else if (!requestedEvents.mousedrag) {
          requestedEvents.mousedrag = eventListeners.mousedrag;
        }
      }));
      this.coreMouseService.activeProtocol = this.coreMouseService.activeProtocol;
      this._register(addDisposableListener(el, "mousedown", (ev) => {
        ev.preventDefault();
        this.focus();
        if (!this.coreMouseService.areMouseEventsActive || this._selectionService.shouldForceSelection(ev)) {
          return;
        }
        sendEvent(ev);
        if (requestedEvents.mouseup) {
          this._document.addEventListener("mouseup", requestedEvents.mouseup);
        }
        if (requestedEvents.mousedrag) {
          this._document.addEventListener("mousemove", requestedEvents.mousedrag);
        }
        return this.cancel(ev);
      }));
      this._register(addDisposableListener(el, "wheel", (ev) => {
        if (requestedEvents.wheel) return;
        if (this._customWheelEventHandler && this._customWheelEventHandler(ev) === false) {
          return false;
        }
        if (!this.buffer.hasScrollback) {
          const deltaY = ev.deltaY;
          if (deltaY === 0) {
            return false;
          }
          const lines = self.coreMouseService.consumeWheelEvent(
            ev,
            self._renderService?.dimensions?.device?.cell?.height,
            self._coreBrowserService?.dpr
          );
          if (lines === 0) {
            return this.cancel(ev, true);
          }
          const sequence2 = C0.ESC + (this.coreService.decPrivateModes.applicationCursorKeys ? "O" : "[") + (ev.deltaY < 0 ? "A" : "B");
          this.coreService.triggerDataEvent(sequence2, true);
          return this.cancel(ev, true);
        }
      }, { passive: false }));
    }
    /**
     * Tells the renderer to refresh terminal content between two rows (inclusive) at the next
     * opportunity.
     * @param start The row to start from (between 0 and this.rows - 1).
     * @param end The row to end at (between start and this.rows - 1).
     */
    refresh(start, end) {
      this._renderService?.refreshRows(start, end);
    }
    /**
     * Change the cursor style for different selection modes
     */
    updateCursorStyle(ev) {
      if (this._selectionService?.shouldColumnSelect(ev)) {
        this.element.classList.add("column-select");
      } else {
        this.element.classList.remove("column-select");
      }
    }
    /**
     * Display the cursor element
     */
    _showCursor() {
      if (!this.coreService.isCursorInitialized) {
        this.coreService.isCursorInitialized = true;
        this.refresh(this.buffer.y, this.buffer.y);
      }
    }
    scrollLines(disp, suppressScrollEvent) {
      if (this._viewport) {
        this._viewport.scrollLines(disp);
      } else {
        super.scrollLines(disp, suppressScrollEvent);
      }
      this.refresh(0, this.rows - 1);
    }
    scrollPages(pageCount) {
      this.scrollLines(pageCount * (this.rows - 1));
    }
    scrollToTop() {
      this.scrollLines(-this._bufferService.buffer.ydisp);
    }
    scrollToBottom(disableSmoothScroll) {
      if (disableSmoothScroll && this._viewport) {
        this._viewport.scrollToLine(this.buffer.ybase, true);
      } else {
        this.scrollLines(this._bufferService.buffer.ybase - this._bufferService.buffer.ydisp);
      }
    }
    scrollToLine(line) {
      const scrollAmount = line - this._bufferService.buffer.ydisp;
      if (scrollAmount !== 0) {
        this.scrollLines(scrollAmount);
      }
    }
    paste(data) {
      paste(data, this.textarea, this.coreService, this.optionsService);
    }
    attachCustomKeyEventHandler(customKeyEventHandler) {
      this._customKeyEventHandler = customKeyEventHandler;
    }
    attachCustomWheelEventHandler(customWheelEventHandler) {
      this._customWheelEventHandler = customWheelEventHandler;
    }
    registerLinkProvider(linkProvider) {
      return this._linkProviderService.registerLinkProvider(linkProvider);
    }
    registerCharacterJoiner(handler) {
      if (!this._characterJoinerService) {
        throw new Error("Terminal must be opened first");
      }
      const joinerId = this._characterJoinerService.register(handler);
      this.refresh(0, this.rows - 1);
      return joinerId;
    }
    deregisterCharacterJoiner(joinerId) {
      if (!this._characterJoinerService) {
        throw new Error("Terminal must be opened first");
      }
      if (this._characterJoinerService.deregister(joinerId)) {
        this.refresh(0, this.rows - 1);
      }
    }
    get markers() {
      return this.buffer.markers;
    }
    registerMarker(cursorYOffset) {
      return this.buffer.addMarker(this.buffer.ybase + this.buffer.y + cursorYOffset);
    }
    registerDecoration(decorationOptions) {
      return this._decorationService.registerDecoration(decorationOptions);
    }
    /**
     * Gets whether the terminal has an active selection.
     */
    hasSelection() {
      return this._selectionService ? this._selectionService.hasSelection : false;
    }
    /**
     * Selects text within the terminal.
     * @param column The column the selection starts at..
     * @param row The row the selection starts at.
     * @param length The length of the selection.
     */
    select(column, row, length) {
      this._selectionService.setSelection(column, row, length);
    }
    /**
     * Gets the terminal's current selection, this is useful for implementing copy
     * behavior outside of xterm.js.
     */
    getSelection() {
      return this._selectionService ? this._selectionService.selectionText : "";
    }
    getSelectionPosition() {
      if (!this._selectionService || !this._selectionService.hasSelection) {
        return void 0;
      }
      return {
        start: {
          x: this._selectionService.selectionStart[0],
          y: this._selectionService.selectionStart[1]
        },
        end: {
          x: this._selectionService.selectionEnd[0],
          y: this._selectionService.selectionEnd[1]
        }
      };
    }
    /**
     * Clears the current terminal selection.
     */
    clearSelection() {
      this._selectionService?.clearSelection();
    }
    /**
     * Selects all text within the terminal.
     */
    selectAll() {
      this._selectionService?.selectAll();
    }
    selectLines(start, end) {
      this._selectionService?.selectLines(start, end);
    }
    /**
     * Handle a keydown [KeyboardEvent].
     *
     * [KeyboardEvent]: https://developer.mozilla.org/en-US/docs/DOM/KeyboardEvent
     */
    _keyDown(event) {
      this._keyDownHandled = false;
      this._keyDownSeen = true;
      if (this._customKeyEventHandler && this._customKeyEventHandler(event) === false) {
        return false;
      }
      const shouldIgnoreComposition = this.browser.isMac && this.options.macOptionIsMeta && event.altKey;
      if (!shouldIgnoreComposition && !this._compositionHelper.keydown(event)) {
        if (this.options.scrollOnUserInput && this.buffer.ybase !== this.buffer.ydisp) {
          this.scrollToBottom(true);
        }
        return false;
      }
      if (!shouldIgnoreComposition && (event.key === "Dead" || event.key === "AltGraph")) {
        this._unprocessedDeadKey = true;
      }
      const result = evaluateKeyboardEvent(event, this.coreService.decPrivateModes.applicationCursorKeys, this.browser.isMac, this.options.macOptionIsMeta);
      this.updateCursorStyle(event);
      if (result.type === 3 /* PAGE_DOWN */ || result.type === 2 /* PAGE_UP */) {
        const scrollCount = this.rows - 1;
        this.scrollLines(result.type === 2 /* PAGE_UP */ ? -scrollCount : scrollCount);
        return this.cancel(event, true);
      }
      if (result.type === 1 /* SELECT_ALL */) {
        this.selectAll();
      }
      if (this._isThirdLevelShift(this.browser, event)) {
        return true;
      }
      if (result.cancel) {
        this.cancel(event, true);
      }
      if (!result.key) {
        return true;
      }
      if (event.key && !event.ctrlKey && !event.altKey && !event.metaKey && event.key.length === 1) {
        if (event.key.charCodeAt(0) >= 65 && event.key.charCodeAt(0) <= 90) {
          return true;
        }
      }
      if (this._unprocessedDeadKey) {
        this._unprocessedDeadKey = false;
        return true;
      }
      if (result.key === C0.ETX || result.key === C0.CR) {
        this.textarea.value = "";
      }
      this._onKey.fire({ key: result.key, domEvent: event });
      this._showCursor();
      this.coreService.triggerDataEvent(result.key, true);
      if (!this.optionsService.rawOptions.screenReaderMode || event.altKey || event.ctrlKey) {
        return this.cancel(event, true);
      }
      this._keyDownHandled = true;
    }
    _isThirdLevelShift(browser, ev) {
      const thirdLevelKey = browser.isMac && !this.options.macOptionIsMeta && ev.altKey && !ev.ctrlKey && !ev.metaKey || browser.isWindows && ev.altKey && ev.ctrlKey && !ev.metaKey || browser.isWindows && ev.getModifierState("AltGraph");
      if (ev.type === "keypress") {
        return thirdLevelKey;
      }
      return thirdLevelKey && (!ev.keyCode || ev.keyCode > 47);
    }
    _keyUp(ev) {
      this._keyDownSeen = false;
      if (this._customKeyEventHandler && this._customKeyEventHandler(ev) === false) {
        return;
      }
      if (!wasModifierKeyOnlyEvent(ev)) {
        this.focus();
      }
      this.updateCursorStyle(ev);
      this._keyPressHandled = false;
    }
    /**
     * Handle a keypress event.
     * Key Resources:
     *   - https://developer.mozilla.org/en-US/docs/DOM/KeyboardEvent
     * @param ev The keypress event to be handled.
     */
    _keyPress(ev) {
      let key;
      this._keyPressHandled = false;
      if (this._keyDownHandled) {
        return false;
      }
      if (this._customKeyEventHandler && this._customKeyEventHandler(ev) === false) {
        return false;
      }
      this.cancel(ev);
      if (ev.charCode) {
        key = ev.charCode;
      } else if (ev.which === null || ev.which === void 0) {
        key = ev.keyCode;
      } else if (ev.which !== 0 && ev.charCode !== 0) {
        key = ev.which;
      } else {
        return false;
      }
      if (!key || (ev.altKey || ev.ctrlKey || ev.metaKey) && !this._isThirdLevelShift(this.browser, ev)) {
        return false;
      }
      key = String.fromCharCode(key);
      this._onKey.fire({ key, domEvent: ev });
      this._showCursor();
      this.coreService.triggerDataEvent(key, true);
      this._keyPressHandled = true;
      this._unprocessedDeadKey = false;
      return true;
    }
    /**
     * Handle an input event.
     * Key Resources:
     *   - https://developer.mozilla.org/en-US/docs/Web/API/InputEvent
     * @param ev The input event to be handled.
     */
    _inputEvent(ev) {
      if (ev.data && ev.inputType === "insertText" && (!ev.composed || !this._keyDownSeen) && !this.optionsService.rawOptions.screenReaderMode) {
        if (this._keyPressHandled) {
          return false;
        }
        this._unprocessedDeadKey = false;
        const text = ev.data;
        this.coreService.triggerDataEvent(text, true);
        this.cancel(ev);
        return true;
      }
      return false;
    }
    /**
     * Resizes the terminal.
     *
     * @param x The number of columns to resize to.
     * @param y The number of rows to resize to.
     */
    resize(x, y) {
      if (x === this.cols && y === this.rows) {
        if (this._charSizeService && !this._charSizeService.hasValidSize) {
          this._charSizeService.measure();
        }
        return;
      }
      super.resize(x, y);
    }
    _afterResize(x, y) {
      this._charSizeService?.measure();
    }
    /**
     * Clear the entire buffer, making the prompt line the new first line.
     */
    clear() {
      if (this.buffer.ybase === 0 && this.buffer.y === 0) {
        return;
      }
      this.buffer.clearAllMarkers();
      this.buffer.lines.set(0, this.buffer.lines.get(this.buffer.ybase + this.buffer.y));
      this.buffer.lines.length = 1;
      this.buffer.ydisp = 0;
      this.buffer.ybase = 0;
      this.buffer.y = 0;
      for (let i2 = 1; i2 < this.rows; i2++) {
        this.buffer.lines.push(this.buffer.getBlankLine(DEFAULT_ATTR_DATA));
      }
      this._onScroll.fire({ position: this.buffer.ydisp });
      this.refresh(0, this.rows - 1);
    }
    /**
     * Reset terminal.
     * Note: Calling this directly from JS is synchronous but does not clear
     * input buffers and does not reset the parser, thus the terminal will
     * continue to apply pending input data.
     * If you need in band reset (synchronous with input data) consider
     * using DECSTR (soft reset, CSI ! p) or RIS instead (hard reset, ESC c).
     */
    reset() {
      this.options.rows = this.rows;
      this.options.cols = this.cols;
      const customKeyEventHandler = this._customKeyEventHandler;
      this._setup();
      super.reset();
      this._selectionService?.reset();
      this._decorationService.reset();
      this._customKeyEventHandler = customKeyEventHandler;
      this.refresh(0, this.rows - 1);
    }
    clearTextureAtlas() {
      this._renderService?.clearTextureAtlas();
    }
    _reportFocus() {
      if (this.element?.classList.contains("focus")) {
        this.coreService.triggerDataEvent(C0.ESC + "[I");
      } else {
        this.coreService.triggerDataEvent(C0.ESC + "[O");
      }
    }
    _reportWindowsOptions(type) {
      if (!this._renderService) {
        return;
      }
      switch (type) {
        case 0 /* GET_WIN_SIZE_PIXELS */:
          const canvasWidth = this._renderService.dimensions.css.canvas.width.toFixed(0);
          const canvasHeight = this._renderService.dimensions.css.canvas.height.toFixed(0);
          this.coreService.triggerDataEvent(`${C0.ESC}[4;${canvasHeight};${canvasWidth}t`);
          break;
        case 1 /* GET_CELL_SIZE_PIXELS */:
          const cellWidth = this._renderService.dimensions.css.cell.width.toFixed(0);
          const cellHeight = this._renderService.dimensions.css.cell.height.toFixed(0);
          this.coreService.triggerDataEvent(`${C0.ESC}[6;${cellHeight};${cellWidth}t`);
          break;
      }
    }
    // TODO: Remove cancel function and cancelEvents option
    cancel(ev, force) {
      if (!this.options.cancelEvents && !force) {
        return;
      }
      ev.preventDefault();
      ev.stopPropagation();
      return false;
    }
  };
  function wasModifierKeyOnlyEvent(ev) {
    return ev.keyCode === 16 || // Shift
    ev.keyCode === 17 || // Ctrl
    ev.keyCode === 18;
  }

  // node_modules/@xterm/xterm/src/common/public/AddonManager.ts
  var AddonManager = class {
    constructor() {
      this._addons = [];
    }
    dispose() {
      for (let i2 = this._addons.length - 1; i2 >= 0; i2--) {
        this._addons[i2].instance.dispose();
      }
    }
    loadAddon(terminal, instance) {
      const loadedAddon = {
        instance,
        dispose: instance.dispose,
        isDisposed: false
      };
      this._addons.push(loadedAddon);
      instance.dispose = () => this._wrappedAddonDispose(loadedAddon);
      instance.activate(terminal);
    }
    _wrappedAddonDispose(loadedAddon) {
      if (loadedAddon.isDisposed) {
        return;
      }
      let index = -1;
      for (let i2 = 0; i2 < this._addons.length; i2++) {
        if (this._addons[i2] === loadedAddon) {
          index = i2;
          break;
        }
      }
      if (index === -1) {
        throw new Error("Could not dispose an addon that has not been loaded");
      }
      loadedAddon.isDisposed = true;
      loadedAddon.dispose.apply(loadedAddon.instance);
      this._addons.splice(index, 1);
    }
  };

  // node_modules/@xterm/xterm/src/common/public/BufferLineApiView.ts
  var BufferLineApiView = class {
    constructor(_line) {
      this._line = _line;
    }
    get isWrapped() {
      return this._line.isWrapped;
    }
    get length() {
      return this._line.length;
    }
    getCell(x, cell) {
      if (x < 0 || x >= this._line.length) {
        return void 0;
      }
      if (cell) {
        this._line.loadCell(x, cell);
        return cell;
      }
      return this._line.loadCell(x, new CellData());
    }
    translateToString(trimRight, startColumn, endColumn) {
      return this._line.translateToString(trimRight, startColumn, endColumn);
    }
  };

  // node_modules/@xterm/xterm/src/common/public/BufferApiView.ts
  var BufferApiView = class {
    constructor(_buffer, type) {
      this._buffer = _buffer;
      this.type = type;
    }
    init(buffer) {
      this._buffer = buffer;
      return this;
    }
    get cursorY() {
      return this._buffer.y;
    }
    get cursorX() {
      return this._buffer.x;
    }
    get viewportY() {
      return this._buffer.ydisp;
    }
    get baseY() {
      return this._buffer.ybase;
    }
    get length() {
      return this._buffer.lines.length;
    }
    getLine(y) {
      const line = this._buffer.lines.get(y);
      if (!line) {
        return void 0;
      }
      return new BufferLineApiView(line);
    }
    getNullCell() {
      return new CellData();
    }
  };

  // node_modules/@xterm/xterm/src/common/public/BufferNamespaceApi.ts
  var BufferNamespaceApi = class extends Disposable {
    constructor(_core) {
      super();
      this._core = _core;
      this._onBufferChange = this._register(new Emitter());
      this.onBufferChange = this._onBufferChange.event;
      this._normal = new BufferApiView(this._core.buffers.normal, "normal");
      this._alternate = new BufferApiView(this._core.buffers.alt, "alternate");
      this._core.buffers.onBufferActivate(() => this._onBufferChange.fire(this.active));
    }
    get active() {
      if (this._core.buffers.active === this._core.buffers.normal) {
        return this.normal;
      }
      if (this._core.buffers.active === this._core.buffers.alt) {
        return this.alternate;
      }
      throw new Error("Active buffer is neither normal nor alternate");
    }
    get normal() {
      return this._normal.init(this._core.buffers.normal);
    }
    get alternate() {
      return this._alternate.init(this._core.buffers.alt);
    }
  };

  // node_modules/@xterm/xterm/src/common/public/ParserApi.ts
  var ParserApi = class {
    constructor(_core) {
      this._core = _core;
    }
    registerCsiHandler(id2, callback) {
      return this._core.registerCsiHandler(id2, (params) => callback(params.toArray()));
    }
    addCsiHandler(id2, callback) {
      return this.registerCsiHandler(id2, callback);
    }
    registerDcsHandler(id2, callback) {
      return this._core.registerDcsHandler(id2, (data, params) => callback(data, params.toArray()));
    }
    addDcsHandler(id2, callback) {
      return this.registerDcsHandler(id2, callback);
    }
    registerEscHandler(id2, handler) {
      return this._core.registerEscHandler(id2, handler);
    }
    addEscHandler(id2, handler) {
      return this.registerEscHandler(id2, handler);
    }
    registerOscHandler(ident, callback) {
      return this._core.registerOscHandler(ident, callback);
    }
    addOscHandler(ident, callback) {
      return this.registerOscHandler(ident, callback);
    }
  };

  // node_modules/@xterm/xterm/src/common/public/UnicodeApi.ts
  var UnicodeApi = class {
    constructor(_core) {
      this._core = _core;
    }
    register(provider) {
      this._core.unicodeService.register(provider);
    }
    get versions() {
      return this._core.unicodeService.versions;
    }
    get activeVersion() {
      return this._core.unicodeService.activeVersion;
    }
    set activeVersion(version) {
      this._core.unicodeService.activeVersion = version;
    }
  };

  // node_modules/@xterm/xterm/src/browser/public/Terminal.ts
  var CONSTRUCTOR_ONLY_OPTIONS = ["cols", "rows"];
  var $value = 0;
  var Terminal = class extends Disposable {
    constructor(options) {
      super();
      this._core = this._register(new CoreBrowserTerminal(options));
      this._addonManager = this._register(new AddonManager());
      this._publicOptions = { ...this._core.options };
      const getter = (propName) => {
        return this._core.options[propName];
      };
      const setter = (propName, value) => {
        this._checkReadonlyOptions(propName);
        this._core.options[propName] = value;
      };
      for (const propName in this._core.options) {
        const desc = {
          get: getter.bind(this, propName),
          set: setter.bind(this, propName)
        };
        Object.defineProperty(this._publicOptions, propName, desc);
      }
    }
    _checkReadonlyOptions(propName) {
      if (CONSTRUCTOR_ONLY_OPTIONS.includes(propName)) {
        throw new Error(`Option "${propName}" can only be set in the constructor`);
      }
    }
    _checkProposedApi() {
      if (!this._core.optionsService.rawOptions.allowProposedApi) {
        throw new Error("You must set the allowProposedApi option to true to use proposed API");
      }
    }
    get onBell() {
      return this._core.onBell;
    }
    get onBinary() {
      return this._core.onBinary;
    }
    get onCursorMove() {
      return this._core.onCursorMove;
    }
    get onData() {
      return this._core.onData;
    }
    get onKey() {
      return this._core.onKey;
    }
    get onLineFeed() {
      return this._core.onLineFeed;
    }
    get onRender() {
      return this._core.onRender;
    }
    get onResize() {
      return this._core.onResize;
    }
    get onScroll() {
      return this._core.onScroll;
    }
    get onSelectionChange() {
      return this._core.onSelectionChange;
    }
    get onTitleChange() {
      return this._core.onTitleChange;
    }
    get onWriteParsed() {
      return this._core.onWriteParsed;
    }
    get element() {
      return this._core.element;
    }
    get parser() {
      if (!this._parser) {
        this._parser = new ParserApi(this._core);
      }
      return this._parser;
    }
    get unicode() {
      this._checkProposedApi();
      return new UnicodeApi(this._core);
    }
    get textarea() {
      return this._core.textarea;
    }
    get rows() {
      return this._core.rows;
    }
    get cols() {
      return this._core.cols;
    }
    get buffer() {
      if (!this._buffer) {
        this._buffer = this._register(new BufferNamespaceApi(this._core));
      }
      return this._buffer;
    }
    get markers() {
      this._checkProposedApi();
      return this._core.markers;
    }
    get modes() {
      const m = this._core.coreService.decPrivateModes;
      let mouseTrackingMode = "none";
      switch (this._core.coreMouseService.activeProtocol) {
        case "X10":
          mouseTrackingMode = "x10";
          break;
        case "VT200":
          mouseTrackingMode = "vt200";
          break;
        case "DRAG":
          mouseTrackingMode = "drag";
          break;
        case "ANY":
          mouseTrackingMode = "any";
          break;
      }
      return {
        applicationCursorKeysMode: m.applicationCursorKeys,
        applicationKeypadMode: m.applicationKeypad,
        bracketedPasteMode: m.bracketedPasteMode,
        insertMode: this._core.coreService.modes.insertMode,
        mouseTrackingMode,
        originMode: m.origin,
        reverseWraparoundMode: m.reverseWraparound,
        sendFocusMode: m.sendFocus,
        synchronizedOutputMode: m.synchronizedOutput,
        wraparoundMode: m.wraparound
      };
    }
    get options() {
      return this._publicOptions;
    }
    set options(options) {
      for (const propName in options) {
        this._publicOptions[propName] = options[propName];
      }
    }
    blur() {
      this._core.blur();
    }
    focus() {
      this._core.focus();
    }
    input(data, wasUserInput = true) {
      this._core.input(data, wasUserInput);
    }
    resize(columns, rows) {
      this._verifyIntegers(columns, rows);
      this._core.resize(columns, rows);
    }
    open(parent) {
      this._core.open(parent);
    }
    attachCustomKeyEventHandler(customKeyEventHandler) {
      this._core.attachCustomKeyEventHandler(customKeyEventHandler);
    }
    attachCustomWheelEventHandler(customWheelEventHandler) {
      this._core.attachCustomWheelEventHandler(customWheelEventHandler);
    }
    registerLinkProvider(linkProvider) {
      return this._core.registerLinkProvider(linkProvider);
    }
    registerCharacterJoiner(handler) {
      this._checkProposedApi();
      return this._core.registerCharacterJoiner(handler);
    }
    deregisterCharacterJoiner(joinerId) {
      this._checkProposedApi();
      this._core.deregisterCharacterJoiner(joinerId);
    }
    registerMarker(cursorYOffset = 0) {
      this._verifyIntegers(cursorYOffset);
      return this._core.registerMarker(cursorYOffset);
    }
    registerDecoration(decorationOptions) {
      this._checkProposedApi();
      this._verifyPositiveIntegers(decorationOptions.x ?? 0, decorationOptions.width ?? 0, decorationOptions.height ?? 0);
      return this._core.registerDecoration(decorationOptions);
    }
    hasSelection() {
      return this._core.hasSelection();
    }
    select(column, row, length) {
      this._verifyIntegers(column, row, length);
      this._core.select(column, row, length);
    }
    getSelection() {
      return this._core.getSelection();
    }
    getSelectionPosition() {
      return this._core.getSelectionPosition();
    }
    clearSelection() {
      this._core.clearSelection();
    }
    selectAll() {
      this._core.selectAll();
    }
    selectLines(start, end) {
      this._verifyIntegers(start, end);
      this._core.selectLines(start, end);
    }
    dispose() {
      super.dispose();
    }
    scrollLines(amount) {
      this._verifyIntegers(amount);
      this._core.scrollLines(amount);
    }
    scrollPages(pageCount) {
      this._verifyIntegers(pageCount);
      this._core.scrollPages(pageCount);
    }
    scrollToTop() {
      this._core.scrollToTop();
    }
    scrollToBottom() {
      this._core.scrollToBottom();
    }
    scrollToLine(line) {
      this._verifyIntegers(line);
      this._core.scrollToLine(line);
    }
    clear() {
      this._core.clear();
    }
    write(data, callback) {
      this._core.write(data, callback);
    }
    writeln(data, callback) {
      this._core.write(data);
      this._core.write("\r\n", callback);
    }
    paste(data) {
      this._core.paste(data);
    }
    refresh(start, end) {
      this._verifyIntegers(start, end);
      this._core.refresh(start, end);
    }
    reset() {
      this._core.reset();
    }
    clearTextureAtlas() {
      this._core.clearTextureAtlas();
    }
    loadAddon(addon) {
      this._addonManager.loadAddon(this, addon);
    }
    static get strings() {
      return {
        get promptLabel() {
          return promptLabel.get();
        },
        set promptLabel(value) {
          promptLabel.set(value);
        },
        get tooMuchOutput() {
          return tooMuchOutput.get();
        },
        set tooMuchOutput(value) {
          tooMuchOutput.set(value);
        }
      };
    }
    _verifyIntegers(...values) {
      for ($value of values) {
        if ($value === Infinity || isNaN($value) || $value % 1 !== 0) {
          throw new Error("This API only accepts integers");
        }
      }
    }
    _verifyPositiveIntegers(...values) {
      for ($value of values) {
        if ($value && ($value === Infinity || isNaN($value) || $value % 1 !== 0 || $value < 0)) {
          throw new Error("This API only accepts positive integers");
        }
      }
    }
  };

  // node_modules/@xterm/addon-fit/src/FitAddon.ts
  var MINIMUM_COLS2 = 2;
  var MINIMUM_ROWS2 = 1;
  var FitAddon = class {
    activate(terminal) {
      this._terminal = terminal;
    }
    dispose() {
    }
    fit() {
      const dims = this.proposeDimensions();
      if (!dims || !this._terminal || isNaN(dims.cols) || isNaN(dims.rows)) {
        return;
      }
      const core = this._terminal._core;
      if (this._terminal.rows !== dims.rows || this._terminal.cols !== dims.cols) {
        core._renderService.clear();
        this._terminal.resize(dims.cols, dims.rows);
      }
    }
    proposeDimensions() {
      if (!this._terminal) {
        return void 0;
      }
      if (!this._terminal.element || !this._terminal.element.parentElement) {
        return void 0;
      }
      const core = this._terminal._core;
      const dims = core._renderService.dimensions;
      if (dims.css.cell.width === 0 || dims.css.cell.height === 0) {
        return void 0;
      }
      const scrollbarWidth = this._terminal.options.scrollback === 0 ? 0 : this._terminal.options.overviewRuler?.width || 14 /* DEFAULT_SCROLL_BAR_WIDTH */;
      const parentElementStyle = window.getComputedStyle(this._terminal.element.parentElement);
      const parentElementHeight = parseInt(parentElementStyle.getPropertyValue("height"));
      const parentElementWidth = Math.max(0, parseInt(parentElementStyle.getPropertyValue("width")));
      const elementStyle = window.getComputedStyle(this._terminal.element);
      const elementPadding = {
        top: parseInt(elementStyle.getPropertyValue("padding-top")),
        bottom: parseInt(elementStyle.getPropertyValue("padding-bottom")),
        right: parseInt(elementStyle.getPropertyValue("padding-right")),
        left: parseInt(elementStyle.getPropertyValue("padding-left"))
      };
      const elementPaddingVer = elementPadding.top + elementPadding.bottom;
      const elementPaddingHor = elementPadding.right + elementPadding.left;
      const availableHeight = parentElementHeight - elementPaddingVer;
      const availableWidth = parentElementWidth - elementPaddingHor - scrollbarWidth;
      const geometry = {
        cols: Math.max(MINIMUM_COLS2, Math.floor(availableWidth / dims.css.cell.width)),
        rows: Math.max(MINIMUM_ROWS2, Math.floor(availableHeight / dims.css.cell.height))
      };
      return geometry;
    }
  };

  // node_modules/bidi-js/src/index.js
  var src_exports = {};
  __export(src_exports, {
    closingToOpeningBracket: () => closingToOpeningBracket,
    getBidiCharType: () => getBidiCharType,
    getBidiCharTypeName: () => getBidiCharTypeName,
    getCanonicalBracket: () => getCanonicalBracket,
    getEmbeddingLevels: () => getEmbeddingLevels,
    getMirroredCharacter: () => getMirroredCharacter,
    getMirroredCharactersMap: () => getMirroredCharactersMap,
    getReorderSegments: () => getReorderSegments,
    getReorderedIndices: () => getReorderedIndices,
    getReorderedString: () => getReorderedString,
    openingToClosingBracket: () => openingToClosingBracket
  });

  // node_modules/bidi-js/src/data/bidiCharTypes.data.js
  var bidiCharTypes_data_default = {
    "R": "13k,1a,2,3,3,2+1j,ch+16,a+1,5+2,2+n,5,a,4,6+16,4+3,h+1b,4mo,179q,2+9,2+11,2i9+7y,2+68,4,3+4,5+13,4+3,2+4k,3+29,8+cf,1t+7z,w+17,3+3m,1t+3z,16o1+5r,8+30,8+mc,29+1r,29+4v,75+73",
    "EN": "1c+9,3d+1,6,187+9,513,4+5,7+9,sf+j,175h+9,qw+q,161f+1d,4xt+a,25i+9",
    "ES": "17,2,6dp+1,f+1,av,16vr,mx+1,4o,2",
    "ET": "z+2,3h+3,b+1,ym,3e+1,2o,p4+1,8,6u,7c,g6,1wc,1n9+4,30+1b,2n,6d,qhx+1,h0m,a+1,49+2,63+1,4+1,6bb+3,12jj",
    "AN": "16o+5,2j+9,2+1,35,ed,1ff2+9,87+u",
    "CS": "18,2+1,b,2u,12k,55v,l,17v0,2,3,53,2+1,b",
    "B": "a,3,f+2,2v,690",
    "S": "9,2,k",
    "WS": "c,k,4f4,1vk+a,u,1j,335",
    "ON": "x+1,4+4,h+5,r+5,r+3,z,5+3,2+1,2+1,5,2+2,3+4,o,w,ci+1,8+d,3+d,6+8,2+g,39+1,9,6+1,2,33,b8,3+1,3c+1,7+1,5r,b,7h+3,sa+5,2,3i+6,jg+3,ur+9,2v,ij+1,9g+9,7+a,8m,4+1,49+x,14u,2+2,c+2,e+2,e+2,e+1,i+n,e+e,2+p,u+2,e+2,36+1,2+3,2+1,b,2+2,6+5,2,2,2,h+1,5+4,6+3,3+f,16+2,5+3l,3+81,1y+p,2+40,q+a,m+13,2r+ch,2+9e,75+hf,3+v,2+2w,6e+5,f+6,75+2a,1a+p,2+2g,d+5x,r+b,6+3,4+o,g,6+1,6+2,2k+1,4,2j,5h+z,1m+1,1e+f,t+2,1f+e,d+3,4o+3,2s+1,w,535+1r,h3l+1i,93+2,2s,b+1,3l+x,2v,4g+3,21+3,kz+1,g5v+1,5a,j+9,n+v,2,3,2+8,2+1,3+2,2,3,46+1,4+4,h+5,r+5,r+a,3h+2,4+6,b+4,78,1r+24,4+c,4,1hb,ey+6,103+j,16j+c,1ux+7,5+g,fsh,jdq+1t,4,57+2e,p1,1m,1m,1m,1m,4kt+1,7j+17,5+2r,d+e,3+e,2+e,2+10,m+4,w,1n+5,1q,4z+5,4b+rb,9+c,4+c,4+37,d+2g,8+b,l+b,5+1j,9+9,7+13,9+t,3+1,27+3c,2+29,2+3q,d+d,3+4,4+2,6+6,a+o,8+6,a+2,e+6,16+42,2+1i",
    "BN": "0+8,6+d,2s+5,2+p,e,4m9,1kt+2,2b+5,5+5,17q9+v,7k,6p+8,6+1,119d+3,440+7,96s+1,1ekf+1,1ekf+1,1ekf+1,1ekf+1,1ekf+1,1ekf+1,1ekf+1,1ekf+1,1ekf+1,1ekf+1,1ekf+1,1ekf+75,6p+2rz,1ben+1,1ekf+1,1ekf+1",
    "NSM": "lc+33,7o+6,7c+18,2,2+1,2+1,2,21+a,1d+k,h,2u+6,3+5,3+1,2+3,10,v+q,2k+a,1n+8,a,p+3,2+8,2+2,2+4,18+2,3c+e,2+v,1k,2,5+7,5,4+6,b+1,u,1n,5+3,9,l+1,r,3+1,1m,5+1,5+1,3+2,4,v+1,4,c+1,1m,5+4,2+1,5,l+1,n+5,2,1n,3,2+3,9,8+1,c+1,v,1q,d,1f,4,1m+2,6+2,2+3,8+1,c+1,u,1n,g+1,l+1,t+1,1m+1,5+3,9,l+1,u,21,8+2,2,2j,3+6,d+7,2r,3+8,c+5,23+1,s,2,2,1k+d,2+4,2+1,6+a,2+z,a,2v+3,2+5,2+1,3+1,q+1,5+2,h+3,e,3+1,7,g,jk+2,qb+2,u+2,u+1,v+1,1t+1,2+6,9,3+a,a,1a+2,3c+1,z,3b+2,5+1,a,7+2,64+1,3,1n,2+6,2,2,3+7,7+9,3,1d+g,1s+3,1d,2+4,2,6,15+8,d+1,x+3,3+1,2+2,1l,2+1,4,2+2,1n+7,3+1,49+2,2+c,2+6,5,7,4+1,5j+1l,2+4,k1+w,2db+2,3y,2p+v,ff+3,30+1,n9x+3,2+9,x+1,29+1,7l,4,5,q+1,6,48+1,r+h,e,13+7,q+a,1b+2,1d,3+3,3+1,14,1w+5,3+1,3+1,d,9,1c,1g,2+2,3+1,6+1,2,17+1,9,6n,3,5,fn5,ki+f,h+f,r2,6b,46+4,1af+2,2+1,6+3,15+2,5,4m+1,fy+3,as+1,4a+a,4x,1j+e,1l+2,1e+3,3+1,1y+2,11+4,2+7,1r,d+1,1h+8,b+3,3,2o+2,3,2+1,7,4h,4+7,m+1,1m+1,4,12+6,4+4,5g+7,3+2,2,o,2d+5,2,5+1,2+1,6n+3,7+1,2+1,s+1,2e+7,3,2+1,2z,2,3+5,2,2u+2,3+3,2+4,78+8,2+1,75+1,2,5,41+3,3+1,5,x+5,3+1,15+5,3+3,9,a+5,3+2,1b+c,2+1,bb+6,2+5,2d+l,3+6,2+1,2+1,3f+5,4,2+1,2+6,2,21+1,4,2,9o+1,f0c+4,1o+6,t5,1s+3,2a,f5l+1,43t+2,i+7,3+6,v+3,45+2,1j0+1i,5+1d,9,f,n+4,2+e,11t+6,2+g,3+6,2+1,2+4,7a+6,c6+3,15t+6,32+6,gzhy+6n",
    "AL": "16w,3,2,e+1b,z+2,2+2s,g+1,8+1,b+m,2+t,s+2i,c+e,4h+f,1d+1e,1bwe+dp,3+3z,x+c,2+1,35+3y,2rm+z,5+7,b+5,dt+l,c+u,17nl+27,1t+27,4x+6n,3+d",
    "LRO": "6ct",
    "RLO": "6cu",
    "LRE": "6cq",
    "RLE": "6cr",
    "PDF": "6cs",
    "LRI": "6ee",
    "RLI": "6ef",
    "FSI": "6eg",
    "PDI": "6eh"
  };

  // node_modules/bidi-js/src/charTypes.js
  var TYPES = {};
  var TYPES_TO_NAMES = {};
  TYPES.L = 1;
  TYPES_TO_NAMES[1] = "L";
  Object.keys(bidiCharTypes_data_default).forEach((type, i2) => {
    TYPES[type] = 1 << i2 + 1;
    TYPES_TO_NAMES[TYPES[type]] = type;
  });
  Object.freeze(TYPES);
  var ISOLATE_INIT_TYPES = TYPES.LRI | TYPES.RLI | TYPES.FSI;
  var STRONG_TYPES = TYPES.L | TYPES.R | TYPES.AL;
  var NEUTRAL_ISOLATE_TYPES = TYPES.B | TYPES.S | TYPES.WS | TYPES.ON | TYPES.FSI | TYPES.LRI | TYPES.RLI | TYPES.PDI;
  var BN_LIKE_TYPES = TYPES.BN | TYPES.RLE | TYPES.LRE | TYPES.RLO | TYPES.LRO | TYPES.PDF;
  var TRAILING_TYPES = TYPES.S | TYPES.WS | TYPES.B | ISOLATE_INIT_TYPES | TYPES.PDI | BN_LIKE_TYPES;
  var map = null;
  function parseData() {
    if (!map) {
      map = /* @__PURE__ */ new Map();
      for (let type in bidiCharTypes_data_default) {
        if (bidiCharTypes_data_default.hasOwnProperty(type)) {
          let lastCode = 0;
          bidiCharTypes_data_default[type].split(",").forEach((range) => {
            let [skip, step] = range.split("+");
            skip = parseInt(skip, 36);
            step = step ? parseInt(step, 36) : 0;
            map.set(lastCode += skip, TYPES[type]);
            for (let i2 = 0; i2 < step; i2++) {
              map.set(++lastCode, TYPES[type]);
            }
          });
        }
      }
    }
  }
  function getBidiCharType(char) {
    parseData();
    return map.get(char.codePointAt(0)) || TYPES.L;
  }
  function getBidiCharTypeName(char) {
    return TYPES_TO_NAMES[getBidiCharType(char)];
  }

  // node_modules/bidi-js/src/data/bidiBrackets.data.js
  var bidiBrackets_data_default = {
    "pairs": "14>1,1e>2,u>2,2wt>1,1>1,1ge>1,1wp>1,1j>1,f>1,hm>1,1>1,u>1,u6>1,1>1,+5,28>1,w>1,1>1,+3,b8>1,1>1,+3,1>3,-1>-1,3>1,1>1,+2,1s>1,1>1,x>1,th>1,1>1,+2,db>1,1>1,+3,3>1,1>1,+2,14qm>1,1>1,+1,4q>1,1e>2,u>2,2>1,+1",
    "canonical": "6f1>-6dx,6dy>-6dx,6ec>-6ed,6ee>-6ed,6ww>2jj,-2ji>2jj,14r4>-1e7l,1e7m>-1e7l,1e7m>-1e5c,1e5d>-1e5b,1e5c>-14qx,14qy>-14qx,14vn>-1ecg,1ech>-1ecg,1edu>-1ecg,1eci>-1ecg,1eda>-1ecg,1eci>-1ecg,1eci>-168q,168r>-168q,168s>-14ye,14yf>-14ye"
  };

  // node_modules/bidi-js/src/util/parseCharacterMap.js
  function parseCharacterMap(encodedString, includeReverse) {
    const radix = 36;
    let lastCode = 0;
    const map2 = /* @__PURE__ */ new Map();
    const reverseMap = includeReverse && /* @__PURE__ */ new Map();
    let prevPair;
    encodedString.split(",").forEach(function visit(entry) {
      if (entry.indexOf("+") !== -1) {
        for (let i2 = +entry; i2--; ) {
          visit(prevPair);
        }
      } else {
        prevPair = entry;
        let [a, b] = entry.split(">");
        a = String.fromCodePoint(lastCode += parseInt(a, radix));
        b = String.fromCodePoint(lastCode += parseInt(b, radix));
        map2.set(a, b);
        includeReverse && reverseMap.set(b, a);
      }
    });
    return { map: map2, reverseMap };
  }

  // node_modules/bidi-js/src/brackets.js
  var openToClose;
  var closeToOpen;
  var canonical;
  function parse() {
    if (!openToClose) {
      let { map: map2, reverseMap } = parseCharacterMap(bidiBrackets_data_default.pairs, true);
      openToClose = map2;
      closeToOpen = reverseMap;
      canonical = parseCharacterMap(bidiBrackets_data_default.canonical, false).map;
    }
  }
  function openingToClosingBracket(char) {
    parse();
    return openToClose.get(char) || null;
  }
  function closingToOpeningBracket(char) {
    parse();
    return closeToOpen.get(char) || null;
  }
  function getCanonicalBracket(char) {
    parse();
    return canonical.get(char) || null;
  }

  // node_modules/bidi-js/src/embeddingLevels.js
  var {
    L: TYPE_L,
    R: TYPE_R,
    EN: TYPE_EN,
    ES: TYPE_ES,
    ET: TYPE_ET,
    AN: TYPE_AN,
    CS: TYPE_CS,
    B: TYPE_B,
    S: TYPE_S,
    ON: TYPE_ON,
    BN: TYPE_BN,
    NSM: TYPE_NSM,
    AL: TYPE_AL,
    LRO: TYPE_LRO,
    RLO: TYPE_RLO,
    LRE: TYPE_LRE,
    RLE: TYPE_RLE,
    PDF: TYPE_PDF,
    LRI: TYPE_LRI,
    RLI: TYPE_RLI,
    FSI: TYPE_FSI,
    PDI: TYPE_PDI
  } = TYPES;
  function getEmbeddingLevels(string, baseDirection) {
    const MAX_DEPTH = 125;
    const charTypes = new Uint32Array(string.length);
    for (let i2 = 0; i2 < string.length; i2++) {
      charTypes[i2] = getBidiCharType(string[i2]);
    }
    const charTypeCounts = /* @__PURE__ */ new Map();
    function changeCharType(i2, type) {
      const oldType = charTypes[i2];
      charTypes[i2] = type;
      charTypeCounts.set(oldType, charTypeCounts.get(oldType) - 1);
      if (oldType & NEUTRAL_ISOLATE_TYPES) {
        charTypeCounts.set(NEUTRAL_ISOLATE_TYPES, charTypeCounts.get(NEUTRAL_ISOLATE_TYPES) - 1);
      }
      charTypeCounts.set(type, (charTypeCounts.get(type) || 0) + 1);
      if (type & NEUTRAL_ISOLATE_TYPES) {
        charTypeCounts.set(NEUTRAL_ISOLATE_TYPES, (charTypeCounts.get(NEUTRAL_ISOLATE_TYPES) || 0) + 1);
      }
    }
    const embedLevels = new Uint8Array(string.length);
    const isolationPairs = /* @__PURE__ */ new Map();
    const paragraphs = [];
    let paragraph = null;
    for (let i2 = 0; i2 < string.length; i2++) {
      if (!paragraph) {
        paragraphs.push(paragraph = {
          start: i2,
          end: string.length - 1,
          // 3.3.1 P2-P3: Determine the paragraph level
          level: baseDirection === "rtl" ? 1 : baseDirection === "ltr" ? 0 : determineAutoEmbedLevel(i2, false)
        });
      }
      if (charTypes[i2] & TYPE_B) {
        paragraph.end = i2;
        paragraph = null;
      }
    }
    const FORMATTING_TYPES = TYPE_RLE | TYPE_LRE | TYPE_RLO | TYPE_LRO | ISOLATE_INIT_TYPES | TYPE_PDI | TYPE_PDF | TYPE_B;
    const nextEven = (n) => n + (n & 1 ? 1 : 2);
    const nextOdd = (n) => n + (n & 1 ? 2 : 1);
    for (let paraIdx = 0; paraIdx < paragraphs.length; paraIdx++) {
      paragraph = paragraphs[paraIdx];
      const statusStack = [{
        _level: paragraph.level,
        _override: 0,
        //0=neutral, 1=L, 2=R
        _isolate: 0
        //bool
      }];
      let stackTop;
      let overflowIsolateCount = 0;
      let overflowEmbeddingCount = 0;
      let validIsolateCount = 0;
      charTypeCounts.clear();
      for (let i2 = paragraph.start; i2 <= paragraph.end; i2++) {
        let charType = charTypes[i2];
        stackTop = statusStack[statusStack.length - 1];
        charTypeCounts.set(charType, (charTypeCounts.get(charType) || 0) + 1);
        if (charType & NEUTRAL_ISOLATE_TYPES) {
          charTypeCounts.set(NEUTRAL_ISOLATE_TYPES, (charTypeCounts.get(NEUTRAL_ISOLATE_TYPES) || 0) + 1);
        }
        if (charType & FORMATTING_TYPES) {
          if (charType & (TYPE_RLE | TYPE_LRE)) {
            embedLevels[i2] = stackTop._level;
            const level = (charType === TYPE_RLE ? nextOdd : nextEven)(stackTop._level);
            if (level <= MAX_DEPTH && !overflowIsolateCount && !overflowEmbeddingCount) {
              statusStack.push({
                _level: level,
                _override: 0,
                _isolate: 0
              });
            } else if (!overflowIsolateCount) {
              overflowEmbeddingCount++;
            }
          } else if (charType & (TYPE_RLO | TYPE_LRO)) {
            embedLevels[i2] = stackTop._level;
            const level = (charType === TYPE_RLO ? nextOdd : nextEven)(stackTop._level);
            if (level <= MAX_DEPTH && !overflowIsolateCount && !overflowEmbeddingCount) {
              statusStack.push({
                _level: level,
                _override: charType & TYPE_RLO ? TYPE_R : TYPE_L,
                _isolate: 0
              });
            } else if (!overflowIsolateCount) {
              overflowEmbeddingCount++;
            }
          } else if (charType & ISOLATE_INIT_TYPES) {
            if (charType & TYPE_FSI) {
              charType = determineAutoEmbedLevel(i2 + 1, true) === 1 ? TYPE_RLI : TYPE_LRI;
            }
            embedLevels[i2] = stackTop._level;
            if (stackTop._override) {
              changeCharType(i2, stackTop._override);
            }
            const level = (charType === TYPE_RLI ? nextOdd : nextEven)(stackTop._level);
            if (level <= MAX_DEPTH && overflowIsolateCount === 0 && overflowEmbeddingCount === 0) {
              validIsolateCount++;
              statusStack.push({
                _level: level,
                _override: 0,
                _isolate: 1,
                _isolInitIndex: i2
              });
            } else {
              overflowIsolateCount++;
            }
          } else if (charType & TYPE_PDI) {
            if (overflowIsolateCount > 0) {
              overflowIsolateCount--;
            } else if (validIsolateCount > 0) {
              overflowEmbeddingCount = 0;
              while (!statusStack[statusStack.length - 1]._isolate) {
                statusStack.pop();
              }
              const isolInitIndex = statusStack[statusStack.length - 1]._isolInitIndex;
              if (isolInitIndex != null) {
                isolationPairs.set(isolInitIndex, i2);
                isolationPairs.set(i2, isolInitIndex);
              }
              statusStack.pop();
              validIsolateCount--;
            }
            stackTop = statusStack[statusStack.length - 1];
            embedLevels[i2] = stackTop._level;
            if (stackTop._override) {
              changeCharType(i2, stackTop._override);
            }
          } else if (charType & TYPE_PDF) {
            if (overflowIsolateCount === 0) {
              if (overflowEmbeddingCount > 0) {
                overflowEmbeddingCount--;
              } else if (!stackTop._isolate && statusStack.length > 1) {
                statusStack.pop();
                stackTop = statusStack[statusStack.length - 1];
              }
            }
            embedLevels[i2] = stackTop._level;
          } else if (charType & TYPE_B) {
            embedLevels[i2] = paragraph.level;
          }
        } else {
          embedLevels[i2] = stackTop._level;
          if (stackTop._override && charType !== TYPE_BN) {
            changeCharType(i2, stackTop._override);
          }
        }
      }
      const levelRuns = [];
      let currentRun = null;
      let isolationLevel = 0;
      for (let i2 = paragraph.start; i2 <= paragraph.end; i2++) {
        const charType = charTypes[i2];
        if (!(charType & BN_LIKE_TYPES)) {
          const lvl = embedLevels[i2];
          const isIsolInit = charType & ISOLATE_INIT_TYPES;
          const isPDI = charType === TYPE_PDI;
          if (isIsolInit) {
            isolationLevel++;
          }
          if (currentRun && lvl === currentRun._level) {
            currentRun._end = i2;
            currentRun._endsWithIsolInit = isIsolInit;
          } else {
            levelRuns.push(currentRun = {
              _start: i2,
              _end: i2,
              _level: lvl,
              _startsWithPDI: isPDI,
              _endsWithIsolInit: isIsolInit
            });
          }
          if (isPDI) {
            isolationLevel--;
          }
        }
      }
      const isolatingRunSeqs = [];
      for (let runIdx = 0; runIdx < levelRuns.length; runIdx++) {
        const run = levelRuns[runIdx];
        if (!run._startsWithPDI || run._startsWithPDI && !isolationPairs.has(run._start)) {
          const seqRuns = [currentRun = run];
          for (let pdiIndex; currentRun && currentRun._endsWithIsolInit && (pdiIndex = isolationPairs.get(currentRun._end)) != null; ) {
            for (let i2 = runIdx + 1; i2 < levelRuns.length; i2++) {
              if (levelRuns[i2]._start === pdiIndex) {
                seqRuns.push(currentRun = levelRuns[i2]);
                break;
              }
            }
          }
          const seqIndices = [];
          for (let i2 = 0; i2 < seqRuns.length; i2++) {
            const run2 = seqRuns[i2];
            for (let j = run2._start; j <= run2._end; j++) {
              seqIndices.push(j);
            }
          }
          let firstLevel = embedLevels[seqIndices[0]];
          let prevLevel = paragraph.level;
          for (let i2 = seqIndices[0] - 1; i2 >= 0; i2--) {
            if (!(charTypes[i2] & BN_LIKE_TYPES)) {
              prevLevel = embedLevels[i2];
              break;
            }
          }
          const lastIndex = seqIndices[seqIndices.length - 1];
          let lastLevel = embedLevels[lastIndex];
          let nextLevel = paragraph.level;
          if (!(charTypes[lastIndex] & ISOLATE_INIT_TYPES)) {
            for (let i2 = lastIndex + 1; i2 <= paragraph.end; i2++) {
              if (!(charTypes[i2] & BN_LIKE_TYPES)) {
                nextLevel = embedLevels[i2];
                break;
              }
            }
          }
          isolatingRunSeqs.push({
            _seqIndices: seqIndices,
            _sosType: Math.max(prevLevel, firstLevel) % 2 ? TYPE_R : TYPE_L,
            _eosType: Math.max(nextLevel, lastLevel) % 2 ? TYPE_R : TYPE_L
          });
        }
      }
      for (let seqIdx = 0; seqIdx < isolatingRunSeqs.length; seqIdx++) {
        const { _seqIndices: seqIndices, _sosType: sosType, _eosType: eosType } = isolatingRunSeqs[seqIdx];
        const embedDirection = embedLevels[seqIndices[0]] & 1 ? TYPE_R : TYPE_L;
        if (charTypeCounts.get(TYPE_NSM)) {
          for (let si = 0; si < seqIndices.length; si++) {
            const i2 = seqIndices[si];
            if (charTypes[i2] & TYPE_NSM) {
              let prevType = sosType;
              for (let sj = si - 1; sj >= 0; sj--) {
                if (!(charTypes[seqIndices[sj]] & BN_LIKE_TYPES)) {
                  prevType = charTypes[seqIndices[sj]];
                  break;
                }
              }
              changeCharType(i2, prevType & (ISOLATE_INIT_TYPES | TYPE_PDI) ? TYPE_ON : prevType);
            }
          }
        }
        if (charTypeCounts.get(TYPE_EN)) {
          for (let si = 0; si < seqIndices.length; si++) {
            const i2 = seqIndices[si];
            if (charTypes[i2] & TYPE_EN) {
              for (let sj = si - 1; sj >= -1; sj--) {
                const prevCharType = sj === -1 ? sosType : charTypes[seqIndices[sj]];
                if (prevCharType & STRONG_TYPES) {
                  if (prevCharType === TYPE_AL) {
                    changeCharType(i2, TYPE_AN);
                  }
                  break;
                }
              }
            }
          }
        }
        if (charTypeCounts.get(TYPE_AL)) {
          for (let si = 0; si < seqIndices.length; si++) {
            const i2 = seqIndices[si];
            if (charTypes[i2] & TYPE_AL) {
              changeCharType(i2, TYPE_R);
            }
          }
        }
        if (charTypeCounts.get(TYPE_ES) || charTypeCounts.get(TYPE_CS)) {
          for (let si = 1; si < seqIndices.length - 1; si++) {
            const i2 = seqIndices[si];
            if (charTypes[i2] & (TYPE_ES | TYPE_CS)) {
              let prevType = 0, nextType = 0;
              for (let sj = si - 1; sj >= 0; sj--) {
                prevType = charTypes[seqIndices[sj]];
                if (!(prevType & BN_LIKE_TYPES)) {
                  break;
                }
              }
              for (let sj = si + 1; sj < seqIndices.length; sj++) {
                nextType = charTypes[seqIndices[sj]];
                if (!(nextType & BN_LIKE_TYPES)) {
                  break;
                }
              }
              if (prevType === nextType && (charTypes[i2] === TYPE_ES ? prevType === TYPE_EN : prevType & (TYPE_EN | TYPE_AN))) {
                changeCharType(i2, prevType);
              }
            }
          }
        }
        if (charTypeCounts.get(TYPE_EN)) {
          for (let si = 0; si < seqIndices.length; si++) {
            const i2 = seqIndices[si];
            if (charTypes[i2] & TYPE_EN) {
              for (let sj = si - 1; sj >= 0 && charTypes[seqIndices[sj]] & (TYPE_ET | BN_LIKE_TYPES); sj--) {
                changeCharType(seqIndices[sj], TYPE_EN);
              }
              for (si++; si < seqIndices.length && charTypes[seqIndices[si]] & (TYPE_ET | BN_LIKE_TYPES | TYPE_EN); si++) {
                if (charTypes[seqIndices[si]] !== TYPE_EN) {
                  changeCharType(seqIndices[si], TYPE_EN);
                }
              }
            }
          }
        }
        if (charTypeCounts.get(TYPE_ET) || charTypeCounts.get(TYPE_ES) || charTypeCounts.get(TYPE_CS)) {
          for (let si = 0; si < seqIndices.length; si++) {
            const i2 = seqIndices[si];
            if (charTypes[i2] & (TYPE_ET | TYPE_ES | TYPE_CS)) {
              changeCharType(i2, TYPE_ON);
              for (let sj = si - 1; sj >= 0 && charTypes[seqIndices[sj]] & BN_LIKE_TYPES; sj--) {
                changeCharType(seqIndices[sj], TYPE_ON);
              }
              for (let sj = si + 1; sj < seqIndices.length && charTypes[seqIndices[sj]] & BN_LIKE_TYPES; sj++) {
                changeCharType(seqIndices[sj], TYPE_ON);
              }
            }
          }
        }
        if (charTypeCounts.get(TYPE_EN)) {
          for (let si = 0, prevStrongType = sosType; si < seqIndices.length; si++) {
            const i2 = seqIndices[si];
            const type = charTypes[i2];
            if (type & TYPE_EN) {
              if (prevStrongType === TYPE_L) {
                changeCharType(i2, TYPE_L);
              }
            } else if (type & STRONG_TYPES) {
              prevStrongType = type;
            }
          }
        }
        if (charTypeCounts.get(NEUTRAL_ISOLATE_TYPES)) {
          const R_TYPES_FOR_N_STEPS = TYPE_R | TYPE_EN | TYPE_AN;
          const STRONG_TYPES_FOR_N_STEPS = R_TYPES_FOR_N_STEPS | TYPE_L;
          const bracketPairs = [];
          {
            const openerStack = [];
            for (let si = 0; si < seqIndices.length; si++) {
              if (charTypes[seqIndices[si]] & NEUTRAL_ISOLATE_TYPES) {
                const char = string[seqIndices[si]];
                let oppositeBracket;
                if (openingToClosingBracket(char) !== null) {
                  if (openerStack.length < 63) {
                    openerStack.push({ char, seqIndex: si });
                  } else {
                    break;
                  }
                } else if ((oppositeBracket = closingToOpeningBracket(char)) !== null) {
                  for (let stackIdx = openerStack.length - 1; stackIdx >= 0; stackIdx--) {
                    const stackChar = openerStack[stackIdx].char;
                    if (stackChar === oppositeBracket || stackChar === closingToOpeningBracket(getCanonicalBracket(char)) || openingToClosingBracket(getCanonicalBracket(stackChar)) === char) {
                      bracketPairs.push([openerStack[stackIdx].seqIndex, si]);
                      openerStack.length = stackIdx;
                      break;
                    }
                  }
                }
              }
            }
            bracketPairs.sort((a, b) => a[0] - b[0]);
          }
          for (let pairIdx = 0; pairIdx < bracketPairs.length; pairIdx++) {
            const [openSeqIdx, closeSeqIdx] = bracketPairs[pairIdx];
            let foundStrongType = false;
            let useStrongType = 0;
            for (let si = openSeqIdx + 1; si < closeSeqIdx; si++) {
              const i2 = seqIndices[si];
              if (charTypes[i2] & STRONG_TYPES_FOR_N_STEPS) {
                foundStrongType = true;
                const lr = charTypes[i2] & R_TYPES_FOR_N_STEPS ? TYPE_R : TYPE_L;
                if (lr === embedDirection) {
                  useStrongType = lr;
                  break;
                }
              }
            }
            if (foundStrongType && !useStrongType) {
              useStrongType = sosType;
              for (let si = openSeqIdx - 1; si >= 0; si--) {
                const i2 = seqIndices[si];
                if (charTypes[i2] & STRONG_TYPES_FOR_N_STEPS) {
                  const lr = charTypes[i2] & R_TYPES_FOR_N_STEPS ? TYPE_R : TYPE_L;
                  if (lr !== embedDirection) {
                    useStrongType = lr;
                  } else {
                    useStrongType = embedDirection;
                  }
                  break;
                }
              }
            }
            if (useStrongType) {
              charTypes[seqIndices[openSeqIdx]] = charTypes[seqIndices[closeSeqIdx]] = useStrongType;
              if (useStrongType !== embedDirection) {
                for (let si = openSeqIdx + 1; si < seqIndices.length; si++) {
                  if (!(charTypes[seqIndices[si]] & BN_LIKE_TYPES)) {
                    if (getBidiCharType(string[seqIndices[si]]) & TYPE_NSM) {
                      charTypes[seqIndices[si]] = useStrongType;
                    }
                    break;
                  }
                }
              }
              if (useStrongType !== embedDirection) {
                for (let si = closeSeqIdx + 1; si < seqIndices.length; si++) {
                  if (!(charTypes[seqIndices[si]] & BN_LIKE_TYPES)) {
                    if (getBidiCharType(string[seqIndices[si]]) & TYPE_NSM) {
                      charTypes[seqIndices[si]] = useStrongType;
                    }
                    break;
                  }
                }
              }
            }
          }
          for (let si = 0; si < seqIndices.length; si++) {
            if (charTypes[seqIndices[si]] & NEUTRAL_ISOLATE_TYPES) {
              let niRunStart = si, niRunEnd = si;
              let prevType = sosType;
              for (let si2 = si - 1; si2 >= 0; si2--) {
                if (charTypes[seqIndices[si2]] & BN_LIKE_TYPES) {
                  niRunStart = si2;
                } else {
                  prevType = charTypes[seqIndices[si2]] & R_TYPES_FOR_N_STEPS ? TYPE_R : TYPE_L;
                  break;
                }
              }
              let nextType = eosType;
              for (let si2 = si + 1; si2 < seqIndices.length; si2++) {
                if (charTypes[seqIndices[si2]] & (NEUTRAL_ISOLATE_TYPES | BN_LIKE_TYPES)) {
                  niRunEnd = si2;
                } else {
                  nextType = charTypes[seqIndices[si2]] & R_TYPES_FOR_N_STEPS ? TYPE_R : TYPE_L;
                  break;
                }
              }
              for (let sj = niRunStart; sj <= niRunEnd; sj++) {
                charTypes[seqIndices[sj]] = prevType === nextType ? prevType : embedDirection;
              }
              si = niRunEnd;
            }
          }
        }
      }
      for (let i2 = paragraph.start; i2 <= paragraph.end; i2++) {
        const level = embedLevels[i2];
        const type = charTypes[i2];
        if (level & 1) {
          if (type & (TYPE_L | TYPE_EN | TYPE_AN)) {
            embedLevels[i2]++;
          }
        } else {
          if (type & TYPE_R) {
            embedLevels[i2]++;
          } else if (type & (TYPE_AN | TYPE_EN)) {
            embedLevels[i2] += 2;
          }
        }
        if (type & BN_LIKE_TYPES) {
          embedLevels[i2] = i2 === 0 ? paragraph.level : embedLevels[i2 - 1];
        }
        if (i2 === paragraph.end || getBidiCharType(string[i2]) & (TYPE_S | TYPE_B)) {
          for (let j = i2; j >= 0 && getBidiCharType(string[j]) & TRAILING_TYPES; j--) {
            embedLevels[j] = paragraph.level;
          }
        }
      }
    }
    return {
      levels: embedLevels,
      paragraphs
    };
    function determineAutoEmbedLevel(start, isFSI) {
      for (let i2 = start; i2 < string.length; i2++) {
        const charType = charTypes[i2];
        if (charType & (TYPE_R | TYPE_AL)) {
          return 1;
        }
        if (charType & (TYPE_B | TYPE_L) || isFSI && charType === TYPE_PDI) {
          return 0;
        }
        if (charType & ISOLATE_INIT_TYPES) {
          const pdi = indexOfMatchingPDI(i2);
          i2 = pdi === -1 ? string.length : pdi;
        }
      }
      return 0;
    }
    function indexOfMatchingPDI(isolateStart) {
      let isolationLevel = 1;
      for (let i2 = isolateStart + 1; i2 < string.length; i2++) {
        const charType = charTypes[i2];
        if (charType & TYPE_B) {
          break;
        }
        if (charType & TYPE_PDI) {
          if (--isolationLevel === 0) {
            return i2;
          }
        } else if (charType & ISOLATE_INIT_TYPES) {
          isolationLevel++;
        }
      }
      return -1;
    }
  }

  // node_modules/bidi-js/src/data/bidiMirroring.data.js
  var bidiMirroring_data_default = "14>1,j>2,t>2,u>2,1a>g,2v3>1,1>1,1ge>1,1wd>1,b>1,1j>1,f>1,ai>3,-2>3,+1,8>1k0,-1jq>1y7,-1y6>1hf,-1he>1h6,-1h5>1ha,-1h8>1qi,-1pu>1,6>3u,-3s>7,6>1,1>1,f>1,1>1,+2,3>1,1>1,+13,4>1,1>1,6>1eo,-1ee>1,3>1mg,-1me>1mk,-1mj>1mi,-1mg>1mi,-1md>1,1>1,+2,1>10k,-103>1,1>1,4>1,5>1,1>1,+10,3>1,1>8,-7>8,+1,-6>7,+1,a>1,1>1,u>1,u6>1,1>1,+5,26>1,1>1,2>1,2>2,8>1,7>1,4>1,1>1,+5,b8>1,1>1,+3,1>3,-2>1,2>1,1>1,+2,c>1,3>1,1>1,+2,h>1,3>1,a>1,1>1,2>1,3>1,1>1,d>1,f>1,3>1,1a>1,1>1,6>1,7>1,13>1,k>1,1>1,+19,4>1,1>1,+2,2>1,1>1,+18,m>1,a>1,1>1,lk>1,1>1,4>1,2>1,f>1,3>1,1>1,+3,db>1,1>1,+3,3>1,1>1,+2,14qm>1,1>1,+1,6>1,4j>1,j>2,t>2,u>2,2>1,+1";

  // node_modules/bidi-js/src/mirroring.js
  var mirrorMap;
  function parse2() {
    if (!mirrorMap) {
      const { map: map2, reverseMap } = parseCharacterMap(bidiMirroring_data_default, true);
      reverseMap.forEach((value, key) => {
        map2.set(key, value);
      });
      mirrorMap = map2;
    }
  }
  function getMirroredCharacter(char) {
    parse2();
    return mirrorMap.get(char) || null;
  }
  function getMirroredCharactersMap(string, embeddingLevels, start, end) {
    let strLen = string.length;
    start = Math.max(0, start == null ? 0 : +start);
    end = Math.min(strLen - 1, end == null ? strLen - 1 : +end);
    const map2 = /* @__PURE__ */ new Map();
    for (let i2 = start; i2 <= end; i2++) {
      if (embeddingLevels[i2] & 1) {
        const mirror = getMirroredCharacter(string[i2]);
        if (mirror !== null) {
          map2.set(i2, mirror);
        }
      }
    }
    return map2;
  }

  // node_modules/bidi-js/src/reordering.js
  function getReorderSegments(string, embeddingLevelsResult, start, end) {
    let strLen = string.length;
    start = Math.max(0, start == null ? 0 : +start);
    end = Math.min(strLen - 1, end == null ? strLen - 1 : +end);
    const segments = [];
    embeddingLevelsResult.paragraphs.forEach((paragraph) => {
      const lineStart = Math.max(start, paragraph.start);
      const lineEnd = Math.min(end, paragraph.end);
      if (lineStart < lineEnd) {
        const lineLevels = embeddingLevelsResult.levels.slice(lineStart, lineEnd + 1);
        for (let i2 = lineEnd; i2 >= lineStart && getBidiCharType(string[i2]) & TRAILING_TYPES; i2--) {
          lineLevels[i2] = paragraph.level;
        }
        let maxLevel = paragraph.level;
        let minOddLevel = Infinity;
        for (let i2 = 0; i2 < lineLevels.length; i2++) {
          const level = lineLevels[i2];
          if (level > maxLevel) maxLevel = level;
          if (level < minOddLevel) minOddLevel = level | 1;
        }
        for (let lvl = maxLevel; lvl >= minOddLevel; lvl--) {
          for (let i2 = 0; i2 < lineLevels.length; i2++) {
            if (lineLevels[i2] >= lvl) {
              const segStart = i2;
              while (i2 + 1 < lineLevels.length && lineLevels[i2 + 1] >= lvl) {
                i2++;
              }
              if (i2 > segStart) {
                segments.push([segStart + lineStart, i2 + lineStart]);
              }
            }
          }
        }
      }
    });
    return segments;
  }
  function getReorderedString(string, embedLevelsResult, start, end) {
    const indices = getReorderedIndices(string, embedLevelsResult, start, end);
    const chars = [...string];
    indices.forEach((charIndex, i2) => {
      chars[i2] = (embedLevelsResult.levels[charIndex] & 1 ? getMirroredCharacter(string[charIndex]) : null) || string[charIndex];
    });
    return chars.join("");
  }
  function getReorderedIndices(string, embedLevelsResult, start, end) {
    const segments = getReorderSegments(string, embedLevelsResult, start, end);
    const indices = [];
    for (let i2 = 0; i2 < string.length; i2++) {
      indices[i2] = i2;
    }
    segments.forEach(([start2, end2]) => {
      const slice = indices.slice(start2, end2 + 1);
      for (let i2 = slice.length; i2--; ) {
        indices[end2 - i2] = slice[i2];
      }
    });
    return indices;
  }
  return __toCommonJS(entry_exports);
})();
