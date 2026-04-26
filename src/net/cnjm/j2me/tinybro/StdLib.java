package net.cnjm.j2me.tinybro;

import net.cnjm.j2me.util.Pack;

/**
 * ES6+ standard library extensions for the embedded RockScript interpreter.
 *
 * <p>Everything here is registered on top of the minimal ES5 surface that the
 * upstream RockScript / javascript4me ships with. All bindings are implemented
 * as {@link NativeFunctionFast} instances so they skip the per-call
 * {@code arguments} allocation and string-keyed argument access, which matters
 * a lot on S40-class hardware.</p>
 *
 * <p>Organisation:</p>
 * <ul>
 *   <li>{@link #install(RocksInterpreter, Rv)} registers every entry.</li>
 *   <li>All entries are kept in {@link #ENTRIES} as a single flat array to keep
 *       the function-name hash stable.</li>
 * </ul>
 */
final class StdLib {

    private StdLib() {}

    // ------------------------------------------------------------------
    //  Shared helpers
    // ------------------------------------------------------------------

    static final Rv arg(Pack args, int start, int num, int i) {
        return Rv.argAt(args, start, num, i);
    }

    static final int toInt(Rv v, int def) {
        if (v == null || v == Rv._undefined) return def;
        Rv n = v.toNum();
        if (n == Rv._NaN) return def;
        double d = Rv.numValue(n);
        if (Double.isNaN(d) || Double.isInfinite(d)) return def;
        return (int) d;
    }

    static final double toDouble(Rv v, double def) {
        if (v == null || v == Rv._undefined) return def;
        Rv n = v.toNum();
        if (n == Rv._NaN) return def;
        double d = Rv.numValue(n);
        return Double.isNaN(d) ? def : d;
    }

    static final String toStr(Rv v) {
        return v == null ? "" : v.toStr().str;
    }

    static final boolean isArray(Rv v) {
        return v != null && v.type == Rv.ARRAY;
    }

    // ------------------------------------------------------------------
    //  Transcendental math: see {@link CldcMath} (CLDC Math omits several
    //  double overloads used by ES Math / ** operator).
    // ------------------------------------------------------------------

    static final Rv newArray() {
        return new Rv(Rv.ARRAY, Rv._Array);
    }

    static final Rv newArray(int len) {
        Rv a = new Rv(Rv.ARRAY, Rv._Array);
        a.num = len;
        return a;
    }

    static final Rv newObject() {
        return new Rv(Rv.OBJECT, Rv._Object);
    }

    static final Rv getIdx(Rv arr, int i) {
        Rv v = arr.get(Rv.intStr(i));
        return v != null ? v : Rv._undefined;
    }

    static final void pushItem(Rv arr, Rv v) {
        arr.putl(arr.num, v);
    }

    /** Caps single ArrayBuffer allocation size for J2ME heap safety (1 MiB). */
    static final int MAX_ARRAY_BUFFER_BYTES = 1 * 1024 * 1024;

    static final int clampArrayBufferLength(double d) {
        if (Double.isNaN(d) || d <= 0) return 0;
        if (d > MAX_ARRAY_BUFFER_BYTES) return MAX_ARRAY_BUFFER_BYTES;
        return (int) d;
    }

    static final int sliceIndex(double x, int len) {
        int k = Rv.toInt32(x);
        if (k < 0) {
            k = len + k;
            if (k < 0) k = 0;
        }
        if (k > len) k = len;
        return k;
    }

    static final int sliceEndIndex(double x, int len, int start) {
        int k = Rv.toInt32(x);
        if (k < 0) {
            k = len + k;
            if (k < 0) k = 0;
        }
        if (k > len) k = len;
        if (k < start) k = start;
        return k;
    }

    static final Rv newArrayBufferRv(int rawLen) {
        int n = clampArrayBufferLength((double) rawLen);
        Rv bufRv = new Rv(Rv.OBJECT, Rv._ArrayBuffer);
        bufRv.type = Rv.OBJECT;
        bufRv.ctorOrProt = Rv._ArrayBuffer;
        bufRv.opaque = new Rv.ArrayBufferBacking(n > 0 ? new byte[n] : new byte[0]);
        bufRv.putl("byteLength", new Rv(n));
        return bufRv;
    }

    static final boolean littleEndianArg(Pack args, int start, int num, int i) {
        Rv v = arg(args, start, num, i);
        return v != null && v != Rv._undefined && v.asBool();
    }

    static final int readUint16(byte[] data, int pos, boolean le) {
        if (le) {
            return (data[pos] & 0xff) | ((data[pos + 1] & 0xff) << 8);
        }
        return ((data[pos] & 0xff) << 8) | (data[pos + 1] & 0xff);
    }

    static final void writeUint16(byte[] data, int pos, int v, boolean le) {
        v &= 0xffff;
        if (le) {
            data[pos] = (byte) (v & 0xff);
            data[pos + 1] = (byte) ((v >> 8) & 0xff);
        } else {
            data[pos] = (byte) ((v >> 8) & 0xff);
            data[pos + 1] = (byte) (v & 0xff);
        }
    }

    static final int readInt32(byte[] data, int pos, boolean le) {
        int b0 = data[pos] & 0xff;
        int b1 = data[pos + 1] & 0xff;
        int b2 = data[pos + 2] & 0xff;
        int b3 = data[pos + 3] & 0xff;
        if (le) {
            return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
        }
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    static final void writeInt32(byte[] data, int pos, int v, boolean le) {
        if (le) {
            data[pos] = (byte) (v & 0xff);
            data[pos + 1] = (byte) ((v >> 8) & 0xff);
            data[pos + 2] = (byte) ((v >> 16) & 0xff);
            data[pos + 3] = (byte) ((v >> 24) & 0xff);
        } else {
            data[pos] = (byte) ((v >> 24) & 0xff);
            data[pos + 1] = (byte) ((v >> 16) & 0xff);
            data[pos + 2] = (byte) ((v >> 8) & 0xff);
            data[pos + 3] = (byte) (v & 0xff);
        }
    }

    static final Rv.ArrayBufferBacking arrayBufferOf(Rv r) {
        return r != null && r.opaque instanceof Rv.ArrayBufferBacking
                ? (Rv.ArrayBufferBacking) r.opaque : null;
    }

    static final Rv.Uint8View uint8ViewOf(Rv r) {
        return r != null && r.type == Rv.UINT8_ARRAY && r.opaque instanceof Rv.Uint8View
                ? (Rv.Uint8View) r.opaque : null;
    }

    static final Rv.Int32View int32ViewOf(Rv r) {
        return r != null && r.type == Rv.INT32_ARRAY && r.opaque instanceof Rv.Int32View
                ? (Rv.Int32View) r.opaque : null;
    }

    /** Element count for {@code new Int32Array(n)}; caps total bytes via {@link #clampArrayBufferLength}. */
    static final int clampInt32ArrayElementCount(double d) {
        if (Double.isNaN(d) || d <= 0) {
            return 0;
        }
        int byteCap = clampArrayBufferLength(d * 4.0);
        return byteCap >> 2;
    }

    static final Rv.DataViewState dataViewOf(Rv r) {
        return r != null && r.opaque instanceof Rv.DataViewState
                ? (Rv.DataViewState) r.opaque : null;
    }

    // ------------------------------------------------------------------
    //  Installation
    // ------------------------------------------------------------------

    static final void install(RocksInterpreter ri, Rv go) {
        ri.addNativeFunctionList(ENTRIES);

        // ---- Array ----
        Rv arrProto = Rv._Array.ctorOrProt;
        arrProto.putl("map",         ri.addNativeFunction(entryOf("Array.map")));
        arrProto.putl("filter",      ri.addNativeFunction(entryOf("Array.filter")));
        arrProto.putl("forEach",     ri.addNativeFunction(entryOf("Array.forEach")));
        arrProto.putl("reduce",      ri.addNativeFunction(entryOf("Array.reduce")));
        arrProto.putl("reduceRight", ri.addNativeFunction(entryOf("Array.reduceRight")));
        arrProto.putl("find",        ri.addNativeFunction(entryOf("Array.find")));
        arrProto.putl("findIndex",   ri.addNativeFunction(entryOf("Array.findIndex")));
        arrProto.putl("some",        ri.addNativeFunction(entryOf("Array.some")));
        arrProto.putl("every",       ri.addNativeFunction(entryOf("Array.every")));
        arrProto.putl("includes",    ri.addNativeFunction(entryOf("Array.includes")));
        arrProto.putl("indexOf",     ri.addNativeFunction(entryOf("Array.indexOf")));
        arrProto.putl("lastIndexOf", ri.addNativeFunction(entryOf("Array.lastIndexOf")));
        arrProto.putl("fill",        ri.addNativeFunction(entryOf("Array.fill")));
        arrProto.putl("flat",        ri.addNativeFunction(entryOf("Array.flat")));
        arrProto.putl("copyWithin",  ri.addNativeFunction(entryOf("Array.copyWithin")));
        Rv._Array.putl("isArray",    ri.addNativeFunction(entryOf("Array.isArray")));
        Rv._Array.putl("of",         ri.addNativeFunction(entryOf("Array.of")));
        Rv._Array.putl("from",       ri.addNativeFunction(entryOf("Array.from")));

        // ---- Object ----
        Rv._Object.putl("keys",            ri.addNativeFunction(entryOf("Object.keys")));
        Rv._Object.putl("values",          ri.addNativeFunction(entryOf("Object.values")));
        Rv._Object.putl("entries",         ri.addNativeFunction(entryOf("Object.entries")));
        Rv._Object.putl("assign",          ri.addNativeFunction(entryOf("Object.assign")));
        Rv._Object.putl("freeze",          ri.addNativeFunction(entryOf("Object.freeze")));
        Rv._Object.putl("isFrozen",        ri.addNativeFunction(entryOf("Object.isFrozen")));
        Rv._Object.putl("getPrototypeOf",  ri.addNativeFunction(entryOf("Object.getPrototypeOf")));
        Rv._Object.putl("create",          ri.addNativeFunction(entryOf("Object.create")));

        // ---- String ----
        Rv strProto = Rv._String.ctorOrProt;
        strProto.putl("trim",         ri.addNativeFunction(entryOf("String.trim")));
        strProto.putl("trimStart",    ri.addNativeFunction(entryOf("String.trimStart")));
        strProto.putl("trimEnd",      ri.addNativeFunction(entryOf("String.trimEnd")));
        strProto.putl("includes",     ri.addNativeFunction(entryOf("String.includes")));
        strProto.putl("startsWith",   ri.addNativeFunction(entryOf("String.startsWith")));
        strProto.putl("endsWith",     ri.addNativeFunction(entryOf("String.endsWith")));
        strProto.putl("repeat",       ri.addNativeFunction(entryOf("String.repeat")));
        strProto.putl("padStart",     ri.addNativeFunction(entryOf("String.padStart")));
        strProto.putl("padEnd",       ri.addNativeFunction(entryOf("String.padEnd")));
        strProto.putl("replace",      ri.addNativeFunction(entryOf("String.replace")));
        strProto.putl("replaceAll",   ri.addNativeFunction(entryOf("String.replaceAll")));
        strProto.putl("toLowerCase",  ri.addNativeFunction(entryOf("String.toLowerCase")));
        strProto.putl("toUpperCase",  ri.addNativeFunction(entryOf("String.toUpperCase")));
        strProto.putl("concat",       ri.addNativeFunction(entryOf("String.concat")));
        strProto.putl("slice",        ri.addNativeFunction(entryOf("String.slice")));

        // ---- JSON ----
        Rv json = StdLib.newObject();
        json.putl("parse",     ri.addNativeFunction(entryOf("JSON.parse")));
        json.putl("stringify", ri.addNativeFunction(entryOf("JSON.stringify")));
        go.putl("JSON", json);

        // ---- Number extras ----
        Rv._Number.putl("isInteger",        ri.addNativeFunction(entryOf("Number.isInteger")));
        Rv._Number.putl("isFinite",         ri.addNativeFunction(entryOf("Number.isFinite")));
        Rv._Number.putl("isNaN",            ri.addNativeFunction(entryOf("Number.isNaN")));
        Rv._Number.putl("parseInt",         ri.addNativeFunction(entryOf("Number.parseInt")));
        Rv._Number.putl("parseFloat",       ri.addNativeFunction(entryOf("Number.parseFloat")));
        Rv._Number.putl("EPSILON",          new Rv(2.220446049250313e-16));
        Rv._Number.putl("MAX_SAFE_INTEGER", new Rv(9007199254740991.0));
        Rv._Number.putl("MIN_SAFE_INTEGER", new Rv(-9007199254740991.0));
        go.putl("parseFloat", ri.addNativeFunction(entryOf("parseFloat")));

        // ---- Math extras ----
        Rv math = (Rv) go.get("Math");
        if (math != null) {
            math.putl("random", ri.addNativeFunction(entryOf("Math.random")));
            math.putl("abs",    ri.addNativeFunction(entryOf("Math.abs")));
            math.putl("floor",  ri.addNativeFunction(entryOf("Math.floor")));
            math.putl("ceil",   ri.addNativeFunction(entryOf("Math.ceil")));
            math.putl("round",  ri.addNativeFunction(entryOf("Math.round")));
            math.putl("sqrt",   ri.addNativeFunction(entryOf("Math.sqrt")));
            math.putl("pow",    ri.addNativeFunction(entryOf("Math.pow")));
            math.putl("sin",    ri.addNativeFunction(entryOf("Math.sin")));
            math.putl("cos",    ri.addNativeFunction(entryOf("Math.cos")));
            math.putl("tan",    ri.addNativeFunction(entryOf("Math.tan")));
            math.putl("atan",   ri.addNativeFunction(entryOf("Math.atan")));
            math.putl("atan2",  ri.addNativeFunction(entryOf("Math.atan2")));
            math.putl("exp",    ri.addNativeFunction(entryOf("Math.exp")));
            math.putl("log",    ri.addNativeFunction(entryOf("Math.log")));
            math.putl("sign",   ri.addNativeFunction(entryOf("Math.sign")));
            math.putl("trunc",  ri.addNativeFunction(entryOf("Math.trunc")));
            math.putl("PI",     new Rv(Math.PI));
            math.putl("E",      new Rv(Math.E));
        }

        // ---- ArrayBuffer / Uint8Array / Int32Array / DataView ----
        Rv._ArrayBuffer = new Rv();
        Rv._ArrayBuffer.nativeCtor("ArrayBuffer", go).ctorOrProt
                .putl("slice", ri.addNativeFunction(entryOf("ArrayBuffer.slice")));
        go.putl("ArrayBuffer", Rv._ArrayBuffer);

        Rv._Uint8Array = new Rv();
        Rv._Uint8Array.nativeCtor("Uint8Array", go).ctorOrProt
                .putl("subarray", ri.addNativeFunction(entryOf("Uint8Array.subarray")));
        go.putl("Uint8Array", Rv._Uint8Array);

        Rv._Int32Array = new Rv();
        Rv._Int32Array.nativeCtor("Int32Array", go).ctorOrProt
                .putl("subarray", ri.addNativeFunction(entryOf("Int32Array.subarray")));
        go.putl("Int32Array", Rv._Int32Array);
        Rv._Int32Array.putl("BYTES_PER_ELEMENT", new Rv(4));

        Rv._DataView = new Rv();
        Rv._DataView.nativeCtor("DataView", go).ctorOrProt
                .putl("getUint8", ri.addNativeFunction(entryOf("DataView.getUint8")))
                .putl("setUint8", ri.addNativeFunction(entryOf("DataView.setUint8")))
                .putl("getUint16", ri.addNativeFunction(entryOf("DataView.getUint16")))
                .putl("setUint16", ri.addNativeFunction(entryOf("DataView.setUint16")))
                .putl("getInt32", ri.addNativeFunction(entryOf("DataView.getInt32")))
                .putl("setInt32", ri.addNativeFunction(entryOf("DataView.setInt32")));
        go.putl("DataView", Rv._DataView);

        // ---- Map / Set / Symbol ----
        Rv._Map = new Rv();
        Rv._Map.nativeCtor("Map", go).ctorOrProt
                .putl("get",      ri.addNativeFunction(entryOf("Map.get")))
                .putl("set",      ri.addNativeFunction(entryOf("Map.set")))
                .putl("has",      ri.addNativeFunction(entryOf("Map.has")))
                .putl("delete",   ri.addNativeFunction(entryOf("Map.delete")))
                .putl("clear",    ri.addNativeFunction(entryOf("Map.clear")))
                .putl("forEach",  ri.addNativeFunction(entryOf("Map.forEach")))
                .putl("keys",     ri.addNativeFunction(entryOf("Map.keys")))
                .putl("values",   ri.addNativeFunction(entryOf("Map.values")))
                .putl("entries",  ri.addNativeFunction(entryOf("Map.entries")));
        go.putl("Map", Rv._Map);

        Rv._Set = new Rv();
        Rv._Set.nativeCtor("Set", go).ctorOrProt
                .putl("add",      ri.addNativeFunction(entryOf("Set.add")))
                .putl("has",      ri.addNativeFunction(entryOf("Set.has")))
                .putl("delete",   ri.addNativeFunction(entryOf("Set.delete")))
                .putl("clear",    ri.addNativeFunction(entryOf("Set.clear")))
                .putl("forEach",  ri.addNativeFunction(entryOf("Set.forEach")))
                .putl("values",   ri.addNativeFunction(entryOf("Set.values")));
        go.putl("Set", Rv._Set);

        Rv._Symbol = ri.addNativeFunction(entryOf("Symbol"));
        go.putl("Symbol", Rv._Symbol);

        // ---- Promise (then/catch, resolve/reject, executor ctor, thenable assimilation) ----
        Rv._Promise = new Rv();
        Rv promProto = Rv._Promise.nativeCtor("Promise", go).ctorOrProt;
        promProto.putl("then", ri.addNativeFunction(entryOf("Promise.then")));
        promProto.putl("catch", ri.addNativeFunction(entryOf("Promise.catch")));
        Rv._Promise.putl("resolve", ri.addNativeFunction(entryOf("Promise.resolve")));
        Rv._Promise.putl("reject", ri.addNativeFunction(entryOf("Promise.reject")));
        go.putl("Promise", Rv._Promise);
        go.putl("__awaitStep", ri.addNativeFunction(entryOf("__awaitStep")));
    }

    static NativeFunctionListEntry entryOf(String name) {
        for (int i = 0; i < ENTRIES.length; i++) {
            if (ENTRIES[i].name.equals(name)) return ENTRIES[i];
        }
        throw new RuntimeException("StdLib entry not found: " + name);
    }

    // ------------------------------------------------------------------
    //  Entries
    // ------------------------------------------------------------------

    static final NativeFunctionListEntry[] ENTRIES = new NativeFunctionListEntry[] {

        // ============================================================
        //                          ARRAY
        // ============================================================

        new NativeFunctionListEntry("Array.map", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv fn = arg(args, start, num, 0);
                Rv thisArg = arg(args, start, num, 1);
                int len = thiz.num;
                Rv out = newArray(len);
                for (int i = 0; i < len; i++) {
                    Rv v = getIdx(thiz, i);
                    Rv r = ri.invokeJS3(fn, thisArg, v, new Rv(i), thiz);
                    out.putl(i, r);
                }
                return out;
            }
        }),

        new NativeFunctionListEntry("Array.filter", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv fn = arg(args, start, num, 0);
                Rv thisArg = arg(args, start, num, 1);
                int len = thiz.num;
                Rv out = newArray();
                for (int i = 0; i < len; i++) {
                    Rv v = getIdx(thiz, i);
                    if (ri.invokeJS3(fn, thisArg, v, new Rv(i), thiz).asBool()) pushItem(out, v);
                }
                return out;
            }
        }),

        new NativeFunctionListEntry("Array.forEach", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv fn = arg(args, start, num, 0);
                Rv thisArg = arg(args, start, num, 1);
                int len = thiz.num;
                for (int i = 0; i < len; i++) {
                    ri.invokeJS3(fn, thisArg, getIdx(thiz, i), new Rv(i), thiz);
                }
                return Rv._undefined;
            }
        }),

        new NativeFunctionListEntry("Array.reduce", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv fn = arg(args, start, num, 0);
                int len = thiz.num;
                int i = 0;
                Rv acc;
                if (num >= 2) {
                    acc = arg(args, start, num, 1);
                } else {
                    if (len == 0) return Rv.error("Reduce of empty array with no initial value");
                    acc = getIdx(thiz, 0);
                    i = 1;
                }
                Pack p = new Pack(-1, 4);
                for (; i < len; i++) {
                    p.iSize = 0; p.oSize = 0;
                    p.add(acc); p.add(getIdx(thiz, i)); p.add(new Rv(i)); p.add(thiz);
                    acc = ri.invokeJS(fn, Rv._undefined, p, 0, 4);
                }
                return acc;
            }
        }),

        new NativeFunctionListEntry("Array.reduceRight", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv fn = arg(args, start, num, 0);
                int len = thiz.num;
                int i = len - 1;
                Rv acc;
                if (num >= 2) {
                    acc = arg(args, start, num, 1);
                } else {
                    if (len == 0) return Rv.error("Reduce of empty array with no initial value");
                    acc = getIdx(thiz, i);
                    i--;
                }
                Pack p = new Pack(-1, 4);
                for (; i >= 0; i--) {
                    p.iSize = 0; p.oSize = 0;
                    p.add(acc); p.add(getIdx(thiz, i)); p.add(new Rv(i)); p.add(thiz);
                    acc = ri.invokeJS(fn, Rv._undefined, p, 0, 4);
                }
                return acc;
            }
        }),

        new NativeFunctionListEntry("Array.find", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv fn = arg(args, start, num, 0);
                Rv thisArg = arg(args, start, num, 1);
                int len = thiz.num;
                for (int i = 0; i < len; i++) {
                    Rv v = getIdx(thiz, i);
                    if (ri.invokeJS3(fn, thisArg, v, new Rv(i), thiz).asBool()) return v;
                }
                return Rv._undefined;
            }
        }),

        new NativeFunctionListEntry("Array.findIndex", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv fn = arg(args, start, num, 0);
                Rv thisArg = arg(args, start, num, 1);
                int len = thiz.num;
                for (int i = 0; i < len; i++) {
                    if (ri.invokeJS3(fn, thisArg, getIdx(thiz, i), new Rv(i), thiz).asBool()) return new Rv(i);
                }
                return new Rv(-1);
            }
        }),

        new NativeFunctionListEntry("Array.some", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv fn = arg(args, start, num, 0);
                Rv thisArg = arg(args, start, num, 1);
                int len = thiz.num;
                for (int i = 0; i < len; i++) {
                    if (ri.invokeJS3(fn, thisArg, getIdx(thiz, i), new Rv(i), thiz).asBool()) return Rv._true;
                }
                return Rv._false;
            }
        }),

        new NativeFunctionListEntry("Array.every", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv fn = arg(args, start, num, 0);
                Rv thisArg = arg(args, start, num, 1);
                int len = thiz.num;
                for (int i = 0; i < len; i++) {
                    if (!ri.invokeJS3(fn, thisArg, getIdx(thiz, i), new Rv(i), thiz).asBool()) return Rv._false;
                }
                return Rv._true;
            }
        }),

        new NativeFunctionListEntry("Array.includes", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv needle = arg(args, start, num, 0);
                int from = toInt(arg(args, start, num, 1), 0);
                int len = thiz.num;
                if (from < 0) from = Math.max(0, len + from);
                for (int i = from; i < len; i++) {
                    Rv v = getIdx(thiz, i);
                    if (strictEq(v, needle)) return Rv._true;
                }
                return Rv._false;
            }
        }),

        new NativeFunctionListEntry("Array.indexOf", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv needle = arg(args, start, num, 0);
                int from = toInt(arg(args, start, num, 1), 0);
                int len = thiz.num;
                if (from < 0) from = Math.max(0, len + from);
                for (int i = from; i < len; i++) {
                    if (strictEq(getIdx(thiz, i), needle)) return new Rv(i);
                }
                return new Rv(-1);
            }
        }),

        new NativeFunctionListEntry("Array.lastIndexOf", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv needle = arg(args, start, num, 0);
                int len = thiz.num;
                int from = num >= 2 ? toInt(arg(args, start, num, 1), len - 1) : len - 1;
                if (from < 0) from += len;
                if (from >= len) from = len - 1;
                for (int i = from; i >= 0; i--) {
                    if (strictEq(getIdx(thiz, i), needle)) return new Rv(i);
                }
                return new Rv(-1);
            }
        }),

        new NativeFunctionListEntry("Array.fill", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv v = arg(args, start, num, 0);
                int len = thiz.num;
                int s = toInt(arg(args, start, num, 1), 0);
                int e = num >= 3 ? toInt(arg(args, start, num, 2), len) : len;
                if (s < 0) s = Math.max(0, len + s);
                if (e < 0) e = Math.max(0, len + e);
                if (e > len) e = len;
                for (int i = s; i < e; i++) thiz.putl(i, v);
                return thiz;
            }
        }),

        new NativeFunctionListEntry("Array.flat", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                int depth = num >= 1 ? toInt(arg(args, start, num, 0), 1) : 1;
                Rv out = newArray();
                flatInto(thiz, out, depth);
                return out;
            }
        }),

        new NativeFunctionListEntry("Array.copyWithin", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                int len = thiz.num;
                int target = toInt(arg(args, start, num, 0), 0);
                int s = toInt(arg(args, start, num, 1), 0);
                int e = num >= 3 ? toInt(arg(args, start, num, 2), len) : len;
                if (target < 0) target = Math.max(0, len + target);
                if (s < 0) s = Math.max(0, len + s);
                if (e < 0) e = Math.max(0, len + e);
                if (e > len) e = len;
                int count = Math.min(e - s, len - target);
                // copy to a buffer first (handles overlap)
                Rv[] buf = new Rv[count];
                for (int i = 0; i < count; i++) buf[i] = getIdx(thiz, s + i);
                for (int i = 0; i < count; i++) thiz.putl(target + i, buf[i]);
                return thiz;
            }
        }),

        new NativeFunctionListEntry("Array.isArray", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv v = arg(args, start, num, 0);
                return isArray(v) ? Rv._true : Rv._false;
            }
        }),

        new NativeFunctionListEntry("Array.of", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv out = newArray();
                for (int i = 0; i < num; i++) out.putl(i, arg(args, start, num, i));
                return out;
            }
        }),

        new NativeFunctionListEntry("Array.from", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv src = arg(args, start, num, 0);
                Rv mapfn = arg(args, start, num, 1);
                boolean hasMap = mapfn != null && mapfn.isCallable();
                Rv out = newArray();
                if (src == null || src == Rv._undefined) return out;
                if (src.type == Rv.STRING || src.type == Rv.STRING_OBJECT) {
                    String s = src.toStr().str;
                    for (int i = 0, n = s.length(); i < n; i++) {
                        Rv v = new Rv(String.valueOf(s.charAt(i)));
                        if (hasMap) v = ri.invokeJS3(mapfn, Rv._undefined, v, new Rv(i), src);
                        out.putl(i, v);
                    }
                    return out;
                }
                int len = src.num;
                for (int i = 0; i < len; i++) {
                    Rv v = getIdx(src, i);
                    if (hasMap) v = ri.invokeJS3(mapfn, Rv._undefined, v, new Rv(i), src);
                    out.putl(i, v);
                }
                return out;
            }
        }),

        // ============================================================
        //                          OBJECT
        // ============================================================

        new NativeFunctionListEntry("Object.keys", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv src = arg(args, start, num, 0);
                Rv out = newArray();
                if (src == null || src.prop == null) return out;
                Pack keys = src.prop.keys();
                for (int i = 0, n = keys.oSize; i < n; i++) {
                    out.putl(i, new Rv((String) keys.oArray[i]));
                }
                return out;
            }
        }),

        new NativeFunctionListEntry("Object.values", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv src = arg(args, start, num, 0);
                Rv out = newArray();
                if (src == null || src.prop == null) return out;
                Pack keys = src.prop.keys();
                for (int i = 0, n = keys.oSize; i < n; i++) {
                    out.putl(i, src.prop.get((String) keys.oArray[i]));
                }
                return out;
            }
        }),

        new NativeFunctionListEntry("Object.entries", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv src = arg(args, start, num, 0);
                Rv out = newArray();
                if (src == null || src.prop == null) return out;
                Pack keys = src.prop.keys();
                for (int i = 0, n = keys.oSize; i < n; i++) {
                    String k = (String) keys.oArray[i];
                    Rv pair = newArray();
                    pair.putl(0, new Rv(k));
                    pair.putl(1, src.prop.get(k));
                    out.putl(i, pair);
                }
                return out;
            }
        }),

        new NativeFunctionListEntry("Object.assign", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv target = arg(args, start, num, 0);
                if (target == null || target == Rv._undefined) return Rv.error("Cannot convert undefined to object");
                for (int a = 1; a < num; a++) {
                    Rv src = arg(args, start, num, a);
                    if (src == null || src.prop == null) continue;
                    Pack keys = src.prop.keys();
                    for (int i = 0, n = keys.oSize; i < n; i++) {
                        String k = (String) keys.oArray[i];
                        target.putl(k, src.prop.get(k));
                    }
                }
                return target;
            }
        }),

        new NativeFunctionListEntry("Object.freeze", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv v = arg(args, start, num, 0);
                if (v != null) v.opaque = FROZEN;
                return v;
            }
        }),

        new NativeFunctionListEntry("Object.isFrozen", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv v = arg(args, start, num, 0);
                return v != null && v.opaque == FROZEN ? Rv._true : Rv._false;
            }
        }),

        new NativeFunctionListEntry("Object.getPrototypeOf", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv v = arg(args, start, num, 0);
                if (v == null || v.ctorOrProt == null) return Rv._null;
                // ctorOrProt for instances points to the ctor; prototype is ctor.ctorOrProt
                Rv ctor = v.ctorOrProt;
                return ctor.ctorOrProt != null ? ctor.ctorOrProt : Rv._null;
            }
        }),

        new NativeFunctionListEntry("Object.create", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv proto = arg(args, start, num, 0);
                Rv props = arg(args, start, num, 1);
                // In this interpreter, `Rv.ctorOrProt` of an *instance* points
                // to the constructor function and the property lookup walks
                // `ctor.ctorOrProt` to reach the actual proto object. If the
                // caller passed an arbitrary object as `proto` (e.g. the common
                // `Object.create(Base.prototype)` pattern from class desugaring),
                // the standard `new Rv(OBJECT, proto)` constructor rejects it
                // because that proto isn't flagged as a constructor. Wrap it in
                // a synthetic ctor so the chain walk still reaches the given
                // proto object correctly.
                Rv out;
                if (proto == null || proto == Rv._null || proto == Rv._undefined) {
                    out = new Rv(Rv.OBJECT, Rv._Object);
                } else if (proto.type >= Rv.CTOR_MASK) {
                    out = new Rv(Rv.OBJECT, proto);
                } else {
                    Rv synthCtor = new Rv();
                    synthCtor.type = Rv.FUNCTION | Rv.CTOR_MASK;
                    synthCtor.ctorOrProt = proto;
                    synthCtor.prop = new Rhash(3);
                    out = new Rv();
                    out.type = Rv.OBJECT;
                    out.ctorOrProt = synthCtor;
                    out.prop = new Rhash(11);
                }
                if (props != null && props.prop != null) {
                    Pack keys = props.prop.keys();
                    for (int i = 0, n = keys.oSize; i < n; i++) {
                        String k = (String) keys.oArray[i];
                        Rv desc = props.prop.get(k);
                        Rv val = desc != null ? desc.get("value") : null;
                        if (val != null) out.putl(k, val);
                    }
                }
                return out;
            }
        }),

        // ============================================================
        //                          STRING
        // ============================================================

        new NativeFunctionListEntry("String.trim", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                return new Rv(thiz.toStr().str.trim());
            }
        }),

        new NativeFunctionListEntry("String.trimStart", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                String s = thiz.toStr().str;
                int i = 0, n = s.length();
                while (i < n && s.charAt(i) <= ' ') i++;
                return new Rv(i == 0 ? s : s.substring(i));
            }
        }),

        new NativeFunctionListEntry("String.trimEnd", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                String s = thiz.toStr().str;
                int e = s.length();
                while (e > 0 && s.charAt(e - 1) <= ' ') e--;
                return new Rv(e == s.length() ? s : s.substring(0, e));
            }
        }),

        new NativeFunctionListEntry("String.includes", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                String s = thiz.toStr().str;
                String needle = toStr(arg(args, start, num, 0));
                int from = toInt(arg(args, start, num, 1), 0);
                return s.indexOf(needle, from) >= 0 ? Rv._true : Rv._false;
            }
        }),

        new NativeFunctionListEntry("String.startsWith", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                String s = thiz.toStr().str;
                String needle = toStr(arg(args, start, num, 0));
                int from = toInt(arg(args, start, num, 1), 0);
                return s.startsWith(needle, from) ? Rv._true : Rv._false;
            }
        }),

        new NativeFunctionListEntry("String.endsWith", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                String s = thiz.toStr().str;
                String needle = toStr(arg(args, start, num, 0));
                int endIndex = num >= 2 ? toInt(arg(args, start, num, 1), s.length()) : s.length();
                if (endIndex > s.length()) endIndex = s.length();
                int startIdx = endIndex - needle.length();
                if (startIdx < 0) return Rv._false;
                return s.regionMatches(false, startIdx, needle, 0, needle.length()) ? Rv._true : Rv._false;
            }
        }),

        new NativeFunctionListEntry("String.repeat", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                String s = thiz.toStr().str;
                int n2 = toInt(arg(args, start, num, 0), 0);
                if (n2 < 0) return Rv.error("Invalid count value");
                if (n2 == 0 || s.length() == 0) return new Rv("");
                StringBuffer buf = new StringBuffer(s.length() * n2);
                for (int i = 0; i < n2; i++) buf.append(s);
                return new Rv(buf.toString());
            }
        }),

        new NativeFunctionListEntry("String.padStart", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                return new Rv(pad(thiz.toStr().str,
                        toInt(arg(args, start, num, 0), 0),
                        num >= 2 ? toStr(arg(args, start, num, 1)) : " ",
                        true));
            }
        }),

        new NativeFunctionListEntry("String.padEnd", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                return new Rv(pad(thiz.toStr().str,
                        toInt(arg(args, start, num, 0), 0),
                        num >= 2 ? toStr(arg(args, start, num, 1)) : " ",
                        false));
            }
        }),

        new NativeFunctionListEntry("String.replace", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                String s = thiz.toStr().str;
                String from = toStr(arg(args, start, num, 0));
                Rv rep = arg(args, start, num, 1);
                int idx = s.indexOf(from);
                if (idx < 0 || from.length() == 0) return new Rv(s);
                String repStr = rep != null && rep.isCallable()
                        ? ri.invokeJS3(rep, Rv._undefined, new Rv(from), new Rv(idx), new Rv(s)).toStr().str
                        : toStr(rep);
                return new Rv(s.substring(0, idx) + repStr + s.substring(idx + from.length()));
            }
        }),

        new NativeFunctionListEntry("String.replaceAll", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                String s = thiz.toStr().str;
                String from = toStr(arg(args, start, num, 0));
                Rv rep = arg(args, start, num, 1);
                if (from.length() == 0) return new Rv(s);
                boolean isFn = rep != null && rep.isCallable();
                String repStr = isFn ? null : toStr(rep);
                StringBuffer out = new StringBuffer(s.length());
                int i = 0;
                while (i < s.length()) {
                    int idx = s.indexOf(from, i);
                    if (idx < 0) { out.append(s.substring(i)); break; }
                    out.append(s.substring(i, idx));
                    if (isFn) {
                        out.append(ri.invokeJS3(rep, Rv._undefined, new Rv(from), new Rv(idx), new Rv(s)).toStr().str);
                    } else {
                        out.append(repStr);
                    }
                    i = idx + from.length();
                }
                return new Rv(out.toString());
            }
        }),

        new NativeFunctionListEntry("String.toLowerCase", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                return new Rv(thiz.toStr().str.toLowerCase());
            }
        }),

        new NativeFunctionListEntry("String.toUpperCase", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                return new Rv(thiz.toStr().str.toUpperCase());
            }
        }),

        new NativeFunctionListEntry("String.concat", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                StringBuffer buf = new StringBuffer();
                buf.append(thiz.toStr().str);
                for (int i = 0; i < num; i++) buf.append(toStr(arg(args, start, num, i)));
                return new Rv(buf.toString());
            }
        }),

        new NativeFunctionListEntry("String.slice", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                String s = thiz.toStr().str;
                int len = s.length();
                int a = toInt(arg(args, start, num, 0), 0);
                int b = num >= 2 ? toInt(arg(args, start, num, 1), len) : len;
                if (a < 0) a = Math.max(0, len + a);
                if (b < 0) b = Math.max(0, len + b);
                if (a > len) a = len;
                if (b > len) b = len;
                if (a >= b) return new Rv("");
                return new Rv(s.substring(a, b));
            }
        }),

        // ============================================================
        //                          JSON
        // ============================================================

        new NativeFunctionListEntry("JSON.parse", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                String src = toStr(arg(args, start, num, 0));
                int[] pos = new int[] { 0 };
                try {
                    Rv r = jsonParse(src, pos);
                    skipWs(src, pos);
                    if (pos[0] != src.length()) return Rv.error("JSON.parse: trailing input");
                    return r;
                } catch (RuntimeException e) {
                    return Rv.error("JSON.parse: " + e.getMessage());
                }
            }
        }),

        new NativeFunctionListEntry("JSON.stringify", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv v = arg(args, start, num, 0);
                Rv indentArg = arg(args, start, num, 2);
                String indent = null;
                if (indentArg != null && indentArg != Rv._undefined) {
                    if (indentArg.type == Rv.NUMBER || indentArg.type == Rv.NUMBER_OBJECT) {
                        int n2 = Math.min(10, Math.max(0, toInt(indentArg, 0)));
                        StringBuffer b = new StringBuffer();
                        for (int i = 0; i < n2; i++) b.append(' ');
                        indent = b.toString();
                    } else if (indentArg.type == Rv.STRING || indentArg.type == Rv.STRING_OBJECT) {
                        String s = indentArg.str;
                        indent = s.length() > 10 ? s.substring(0, 10) : s;
                    }
                }
                StringBuffer buf = new StringBuffer();
                boolean ok = jsonStringify(v, buf, indent, 0);
                return ok ? new Rv(buf.toString()) : Rv._undefined;
            }
        }),

        // ============================================================
        //                          NUMBER
        // ============================================================

        new NativeFunctionListEntry("Number.isInteger", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv v = arg(args, start, num, 0);
                if (v == null || (v.type != Rv.NUMBER && v.type != Rv.NUMBER_OBJECT) || v == Rv._NaN) {
                    return Rv._false;
                }
                double x = Rv.numValue(v);
                if (Double.isNaN(x) || Double.isInfinite(x)) return Rv._false;
                return Math.floor(x) == x ? Rv._true : Rv._false;
            }
        }),

        new NativeFunctionListEntry("Number.isFinite", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv v = arg(args, start, num, 0);
                if (v == null || (v.type != Rv.NUMBER && v.type != Rv.NUMBER_OBJECT)) return Rv._false;
                double x = Rv.numValue(v);
                return !Double.isNaN(x) && !Double.isInfinite(x) ? Rv._true : Rv._false;
            }
        }),

        new NativeFunctionListEntry("Number.isNaN", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv v = arg(args, start, num, 0);
                if (v == null) return Rv._false;
                return Double.isNaN(Rv.numValue(v.toNum())) ? Rv._true : Rv._false;
            }
        }),

        new NativeFunctionListEntry("Number.parseInt", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                return parseIntImpl(arg(args, start, num, 0), arg(args, start, num, 1));
            }
        }),

        new NativeFunctionListEntry("Number.parseFloat", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                return parseFloatImpl(arg(args, start, num, 0));
            }
        }),

        new NativeFunctionListEntry("parseFloat", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                return parseFloatImpl(arg(args, start, num, 0));
            }
        }),

        // ============================================================
        //                          MATH
        // ============================================================

        new NativeFunctionListEntry("Math.random", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                if (num < 1) {
                    return new Rv(RocksInterpreter.random.nextDouble());
                }
                Rv a0 = arg(args, start, num, 0);
                if (a0 == null || a0 == Rv._undefined) {
                    return new Rv(RocksInterpreter.random.nextDouble());
                }
                if (a0.toNum() == Rv._NaN) {
                    return Rv._undefined;
                }
                int low = (int) Rv.numValue(a0.toNum());
                Rv a1 = arg(args, start, num, 1);
                int high = a1 != null && a1 != Rv._undefined && a1.toNum() != Rv._NaN
                        ? (int) Rv.numValue(a1.toNum()) : low - 1;
                if (high <= low) {
                    high = low;
                    low = 0;
                }
                int span = high - low;
                if (span <= 0) {
                    return Rv.smallInt(low);
                }
                int rand = (RocksInterpreter.random.nextInt() & 0x7fffffff) % span;
                return Rv.smallInt(low + rand);
            }
        }),

        new NativeFunctionListEntry("Math.abs", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                double v = toDouble(arg(args, start, num, 0), 0);
                return new Rv(Math.abs(v));
            }
        }),

        new NativeFunctionListEntry("Math.floor", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                double v = toDouble(arg(args, start, num, 0), 0);
                return new Rv(Math.floor(v));
            }
        }),

        new NativeFunctionListEntry("Math.ceil", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                double v = toDouble(arg(args, start, num, 0), 0);
                return new Rv(Math.ceil(v));
            }
        }),

        new NativeFunctionListEntry("Math.round", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                double v = toDouble(arg(args, start, num, 0), 0);
                return new Rv(Math.floor(v + 0.5));
            }
        }),

        new NativeFunctionListEntry("Math.sqrt", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                double v = toDouble(arg(args, start, num, 0), 0);
                return new Rv(Math.sqrt(v));
            }
        }),

        new NativeFunctionListEntry("Math.pow", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                double b = toDouble(arg(args, start, num, 0), 0);
                double e = toDouble(arg(args, start, num, 1), 0);
                return new Rv(CldcMath.pow(b, e));
            }
        }),

        new NativeFunctionListEntry("Math.sin", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                double r = toDouble(arg(args, start, num, 0), 0);
                return new Rv(Math.sin(r));
            }
        }),

        new NativeFunctionListEntry("Math.cos", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                double r = toDouble(arg(args, start, num, 0), 0);
                return new Rv(Math.cos(r));
            }
        }),

        new NativeFunctionListEntry("Math.tan", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                double r = toDouble(arg(args, start, num, 0), 0);
                return new Rv(Math.tan(r));
            }
        }),

        new NativeFunctionListEntry("Math.atan", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                double r = toDouble(arg(args, start, num, 0), 0);
                return new Rv(CldcMath.atan(r));
            }
        }),

        new NativeFunctionListEntry("Math.atan2", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                double y = toDouble(arg(args, start, num, 0), 0);
                double x = toDouble(arg(args, start, num, 1), 0);
                return new Rv(CldcMath.atan2(y, x));
            }
        }),

        new NativeFunctionListEntry("Math.exp", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                double v = toDouble(arg(args, start, num, 0), 0);
                return new Rv(CldcMath.exp(v));
            }
        }),

        new NativeFunctionListEntry("Math.log", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                double v = toDouble(arg(args, start, num, 0), 0);
                if (v <= 0) return Rv._NaN;
                return new Rv(CldcMath.log(v));
            }
        }),

        new NativeFunctionListEntry("Math.sign", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                double v = toDouble(arg(args, start, num, 0), 0);
                if (Double.isNaN(v)) return Rv._NaN;
                return v == 0.0 ? new Rv(0) : new Rv(v > 0 ? 1 : -1);
            }
        }),

        new NativeFunctionListEntry("Math.trunc", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                double v = toDouble(arg(args, start, num, 0), 0);
                return new Rv(v < 0 ? Math.ceil(v) : Math.floor(v));
            }
        }),

        // ============================================================
        //           ArrayBuffer / Uint8Array / Int32Array / DataView
        // ============================================================

        new NativeFunctionListEntry("ArrayBuffer", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv inst = isNew ? thiz : new Rv(Rv.OBJECT, Rv._ArrayBuffer);
                inst.type = Rv.OBJECT;
                inst.ctorOrProt = Rv._ArrayBuffer;
                int byteLen = clampArrayBufferLength(toDouble(arg(args, start, num, 0), 0));
                byte[] data = byteLen > 0 ? new byte[byteLen] : new byte[0];
                inst.opaque = new Rv.ArrayBufferBacking(data);
                inst.putl("byteLength", new Rv(byteLen));
                return inst;
            }
        }),

        new NativeFunctionListEntry("ArrayBuffer.slice", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv.ArrayBufferBacking bb = arrayBufferOf(thiz);
                if (bb == null) return Rv._undefined;
                int len = bb.data.length;
                int b = sliceIndex(toDouble(arg(args, start, num, 0), 0), len);
                int e = num > 1
                        ? sliceEndIndex(toDouble(arg(args, start, num, 1), len), len, b)
                        : len;
                int n = e - b;
                byte[] copy = n > 0 ? new byte[n] : new byte[0];
                if (n > 0) System.arraycopy(bb.data, b, copy, 0, n);
                Rv out = new Rv(Rv.OBJECT, Rv._ArrayBuffer);
                out.type = Rv.OBJECT;
                out.ctorOrProt = Rv._ArrayBuffer;
                out.opaque = new Rv.ArrayBufferBacking(copy);
                out.putl("byteLength", new Rv(n));
                return out;
            }
        }),

        new NativeFunctionListEntry("Uint8Array", new NativeFunctionFast() {
            public final int length = 3;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv inst = isNew ? thiz : new Rv(Rv.UINT8_ARRAY, Rv._Uint8Array);
                inst.type = Rv.UINT8_ARRAY;
                inst.ctorOrProt = Rv._Uint8Array;
                Rv a0 = arg(args, start, num, 0);
                if (a0 == null || a0 == Rv._undefined) {
                    Rv bufRv = newArrayBufferRv(0);
                    Rv.ArrayBufferBacking bb = arrayBufferOf(bufRv);
                    inst.opaque = new Rv.Uint8View(bb.data, 0, 0, bufRv);
                    inst.num = 0;
                    inst.putl("buffer", bufRv);
                    inst.putl("byteOffset", new Rv(0));
                    inst.putl("byteLength", new Rv(0));
                    return inst;
                }
                Rv.ArrayBufferBacking bb0 = arrayBufferOf(a0);
                if (bb0 != null) {
                    int bufLen = bb0.data.length;
                    int bo = toInt(arg(args, start, num, 1), 0);
                    if (bo < 0) bo = 0;
                    if (bo > bufLen) bo = bufLen;
                    int elen;
                    if (num >= 3 && arg(args, start, num, 2) != Rv._undefined) {
                        elen = toInt(arg(args, start, num, 2), bufLen - bo);
                    } else {
                        elen = bufLen - bo;
                    }
                    if (elen < 0) elen = 0;
                    if (bo + elen > bufLen) elen = bufLen - bo;
                    inst.opaque = new Rv.Uint8View(bb0.data, bo, elen, a0);
                    inst.num = elen;
                    inst.putl("buffer", a0);
                    inst.putl("byteOffset", new Rv(bo));
                    inst.putl("byteLength", new Rv(elen));
                    return inst;
                }
                int n = clampArrayBufferLength(toDouble(a0, 0));
                Rv bufRv = newArrayBufferRv(n);
                Rv.ArrayBufferBacking bb = arrayBufferOf(bufRv);
                inst.opaque = new Rv.Uint8View(bb.data, 0, n, bufRv);
                inst.num = n;
                inst.putl("buffer", bufRv);
                inst.putl("byteOffset", new Rv(0));
                inst.putl("byteLength", new Rv(n));
                return inst;
            }
        }),

        new NativeFunctionListEntry("Uint8Array.subarray", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv.Uint8View uv = uint8ViewOf(thiz);
                if (uv == null) return Rv._undefined;
                int len = uv.byteLength;
                int b = sliceIndex(toDouble(arg(args, start, num, 0), 0), len);
                int e = num > 1
                        ? sliceEndIndex(toDouble(arg(args, start, num, 1), len), len, b)
                        : len;
                int n = e - b;
                Rv out = new Rv(Rv.UINT8_ARRAY, Rv._Uint8Array);
                out.type = Rv.UINT8_ARRAY;
                out.ctorOrProt = Rv._Uint8Array;
                out.opaque = new Rv.Uint8View(uv.data, uv.offset + b, n, uv.bufferRv);
                out.num = n;
                out.putl("buffer", uv.bufferRv);
                out.putl("byteOffset", new Rv(uv.offset + b));
                out.putl("byteLength", new Rv(n));
                return out;
            }
        }),

        new NativeFunctionListEntry("Int32Array", new NativeFunctionFast() {
            public final int length = 3;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv inst = isNew ? thiz : new Rv(Rv.INT32_ARRAY, Rv._Int32Array);
                inst.type = Rv.INT32_ARRAY;
                inst.ctorOrProt = Rv._Int32Array;
                Rv a0 = arg(args, start, num, 0);
                if (a0 == null || a0 == Rv._undefined) {
                    Rv bufRv = newArrayBufferRv(0);
                    Rv.ArrayBufferBacking bb = arrayBufferOf(bufRv);
                    inst.opaque = new Rv.Int32View(bb.data, 0, 0, bufRv);
                    inst.num = 0;
                    inst.putl("buffer", bufRv);
                    inst.putl("byteOffset", new Rv(0));
                    inst.putl("byteLength", new Rv(0));
                    return inst;
                }
                Rv.ArrayBufferBacking bb0 = arrayBufferOf(a0);
                if (bb0 != null) {
                    int bufLen = bb0.data.length;
                    int bo = toInt(arg(args, start, num, 1), 0);
                    if (bo < 0) {
                        bo = 0;
                    }
                    bo = bo - (bo & 3);
                    if (bo > bufLen) {
                        bo = bufLen;
                    }
                    int maxB = bufLen - bo;
                    maxB = maxB - (maxB & 3);
                    int elen;
                    if (num >= 3 && arg(args, start, num, 2) != Rv._undefined) {
                        elen = toInt(arg(args, start, num, 2), maxB >> 2);
                    } else {
                        elen = maxB >> 2;
                    }
                    if (elen < 0) {
                        elen = 0;
                    }
                    int maxEl = maxB >> 2;
                    if (elen > maxEl) {
                        elen = maxEl;
                    }
                    int byteLen = elen << 2;
                    inst.opaque = new Rv.Int32View(bb0.data, bo, byteLen, a0);
                    inst.num = elen;
                    inst.putl("buffer", a0);
                    inst.putl("byteOffset", new Rv(bo));
                    inst.putl("byteLength", new Rv(byteLen));
                    return inst;
                }
                int el = clampInt32ArrayElementCount(toDouble(a0, 0));
                int bytes = el << 2;
                Rv bufRv = newArrayBufferRv(bytes);
                Rv.ArrayBufferBacking bb = arrayBufferOf(bufRv);
                inst.opaque = new Rv.Int32View(bb.data, 0, bytes, bufRv);
                inst.num = el;
                inst.putl("buffer", bufRv);
                inst.putl("byteOffset", new Rv(0));
                inst.putl("byteLength", new Rv(bytes));
                return inst;
            }
        }),

        new NativeFunctionListEntry("Int32Array.subarray", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv.Int32View iv = int32ViewOf(thiz);
                if (iv == null) {
                    return Rv._undefined;
                }
                int elemLen = iv.byteLength >> 2;
                int b = sliceIndex(toDouble(arg(args, start, num, 0), 0), elemLen);
                int e = num > 1
                        ? sliceEndIndex(toDouble(arg(args, start, num, 1), elemLen), elemLen, b)
                        : elemLen;
                int n = e - b;
                int byteOff = iv.offset + (b << 2);
                int byteLen = n << 2;
                Rv out = new Rv(Rv.INT32_ARRAY, Rv._Int32Array);
                out.type = Rv.INT32_ARRAY;
                out.ctorOrProt = Rv._Int32Array;
                out.opaque = new Rv.Int32View(iv.data, byteOff, byteLen, iv.bufferRv);
                out.num = n;
                out.putl("buffer", iv.bufferRv);
                out.putl("byteOffset", new Rv(byteOff));
                out.putl("byteLength", new Rv(byteLen));
                return out;
            }
        }),

        new NativeFunctionListEntry("DataView", new NativeFunctionFast() {
            public final int length = 3;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv inst = isNew ? thiz : new Rv(Rv.OBJECT, Rv._DataView);
                inst.type = Rv.OBJECT;
                inst.ctorOrProt = Rv._DataView;
                Rv buf = arg(args, start, num, 0);
                Rv.ArrayBufferBacking bb = arrayBufferOf(buf);
                if (bb == null) {
                    inst.opaque = new Rv.DataViewState(new byte[0], 0, 0, buf);
                    return inst;
                }
                int bufLen = bb.data.length;
                int bo = toInt(arg(args, start, num, 1), 0);
                if (bo < 0) bo = 0;
                if (bo > bufLen) bo = bufLen;
                int vlen;
                if (num >= 3 && arg(args, start, num, 2) != Rv._undefined) {
                    vlen = toInt(arg(args, start, num, 2), bufLen - bo);
                } else {
                    vlen = bufLen - bo;
                }
                if (vlen < 0) vlen = 0;
                if (bo + vlen > bufLen) vlen = bufLen - bo;
                inst.opaque = new Rv.DataViewState(bb.data, bo, vlen, buf);
                return inst;
            }
        }),

        new NativeFunctionListEntry("DataView.getUint8", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv.DataViewState dv = dataViewOf(thiz);
                if (dv == null) return Rv._undefined;
                int rel = toInt(arg(args, start, num, 0), 0);
                if (rel < 0 || rel >= dv.byteLength) return Rv._undefined;
                return new Rv(dv.data[dv.offset + rel] & 0xff);
            }
        }),

        new NativeFunctionListEntry("DataView.setUint8", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv.DataViewState dv = dataViewOf(thiz);
                if (dv == null) return Rv._undefined;
                int rel = toInt(arg(args, start, num, 0), 0);
                if (rel < 0 || rel >= dv.byteLength) return Rv._undefined;
                Rv a1 = arg(args, start, num, 1);
                if (a1 == null || a1 == Rv._undefined) return Rv._undefined;
                int b = Rv.toInt32(Rv.numValue(a1.toNum())) & 0xff;
                dv.data[dv.offset + rel] = (byte) b;
                return Rv._undefined;
            }
        }),

        new NativeFunctionListEntry("DataView.getUint16", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv.DataViewState dv = dataViewOf(thiz);
                if (dv == null) return Rv._undefined;
                int rel = toInt(arg(args, start, num, 0), 0);
                boolean le = littleEndianArg(args, start, num, 1);
                if (rel < 0 || rel + 2 > dv.byteLength) return Rv._undefined;
                return new Rv(readUint16(dv.data, dv.offset + rel, le));
            }
        }),

        new NativeFunctionListEntry("DataView.setUint16", new NativeFunctionFast() {
            public final int length = 3;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv.DataViewState dv = dataViewOf(thiz);
                if (dv == null) return Rv._undefined;
                int rel = toInt(arg(args, start, num, 0), 0);
                boolean le = littleEndianArg(args, start, num, 2);
                if (rel < 0 || rel + 2 > dv.byteLength) return Rv._undefined;
                Rv av = arg(args, start, num, 1);
                if (av == null || av == Rv._undefined) return Rv._undefined;
                int v = Rv.toInt32(Rv.numValue(av.toNum())) & 0xffff;
                writeUint16(dv.data, dv.offset + rel, v, le);
                return Rv._undefined;
            }
        }),

        new NativeFunctionListEntry("DataView.getInt32", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv.DataViewState dv = dataViewOf(thiz);
                if (dv == null) return Rv._undefined;
                int rel = toInt(arg(args, start, num, 0), 0);
                boolean le = littleEndianArg(args, start, num, 1);
                if (rel < 0 || rel + 4 > dv.byteLength) return Rv._undefined;
                return new Rv(readInt32(dv.data, dv.offset + rel, le));
            }
        }),

        new NativeFunctionListEntry("DataView.setInt32", new NativeFunctionFast() {
            public final int length = 3;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv.DataViewState dv = dataViewOf(thiz);
                if (dv == null) return Rv._undefined;
                int rel = toInt(arg(args, start, num, 0), 0);
                boolean le = littleEndianArg(args, start, num, 2);
                if (rel < 0 || rel + 4 > dv.byteLength) return Rv._undefined;
                Rv av = arg(args, start, num, 1);
                if (av == null || av == Rv._undefined) return Rv._undefined;
                int v = Rv.toInt32(Rv.numValue(av.toNum()));
                writeInt32(dv.data, dv.offset + rel, v, le);
                return Rv._undefined;
            }
        }),

        // ============================================================
        //                  MAP / SET / SYMBOL
        // ============================================================

        new NativeFunctionListEntry("Map", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv m = isNew ? thiz : new Rv(Rv.OBJECT, Rv._Map);
                m.type = Rv.OBJECT;
                m.ctorOrProt = Rv._Map;
                m.opaque = new MapBacking();
                Rv src = arg(args, start, num, 0);
                if (src != null && src.type == Rv.ARRAY) {
                    int len = src.num;
                    for (int i = 0; i < len; i++) {
                        Rv pair = getIdx(src, i);
                        if (pair != null && pair.type == Rv.ARRAY && pair.num >= 2) {
                            mapSet(m, getIdx(pair, 0), getIdx(pair, 1));
                        }
                    }
                }
                return m;
            }
        }),

        new NativeFunctionListEntry("Map.get", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                MapBacking mb = mapOf(thiz);
                if (mb == null) return Rv._undefined;
                Rv k = arg(args, start, num, 0);
                int idx = mb.indexOf(k);
                return idx < 0 ? Rv._undefined : (Rv) mb.values.getObject(idx);
            }
        }),

        new NativeFunctionListEntry("Map.set", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                mapSet(thiz, arg(args, start, num, 0), arg(args, start, num, 1));
                return thiz;
            }
        }),

        new NativeFunctionListEntry("Map.has", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                MapBacking mb = mapOf(thiz);
                return mb != null && mb.indexOf(arg(args, start, num, 0)) >= 0 ? Rv._true : Rv._false;
            }
        }),

        new NativeFunctionListEntry("Map.delete", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                MapBacking mb = mapOf(thiz);
                if (mb == null) return Rv._false;
                int idx = mb.indexOf(arg(args, start, num, 0));
                if (idx < 0) return Rv._false;
                mb.removeAt(idx);
                return Rv._true;
            }
        }),

        new NativeFunctionListEntry("Map.clear", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                MapBacking mb = mapOf(thiz);
                if (mb != null) mb.clear();
                return Rv._undefined;
            }
        }),

        new NativeFunctionListEntry("Map.forEach", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                MapBacking mb = mapOf(thiz);
                if (mb == null) return Rv._undefined;
                Rv fn = arg(args, start, num, 0);
                Rv thisArg = arg(args, start, num, 1);
                for (int i = 0, n = mb.keys.oSize; i < n; i++) {
                    ri.invokeJS3(fn, thisArg, (Rv) mb.values.getObject(i), (Rv) mb.keys.getObject(i), thiz);
                }
                return Rv._undefined;
            }
        }),

        new NativeFunctionListEntry("Map.keys", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                MapBacking mb = mapOf(thiz);
                Rv out = newArray();
                if (mb != null) {
                    for (int i = 0, n = mb.keys.oSize; i < n; i++) out.putl(i, (Rv) mb.keys.getObject(i));
                }
                return out;
            }
        }),

        new NativeFunctionListEntry("Map.values", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                MapBacking mb = mapOf(thiz);
                Rv out = newArray();
                if (mb != null) {
                    for (int i = 0, n = mb.values.oSize; i < n; i++) out.putl(i, (Rv) mb.values.getObject(i));
                }
                return out;
            }
        }),

        new NativeFunctionListEntry("Map.entries", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                MapBacking mb = mapOf(thiz);
                Rv out = newArray();
                if (mb != null) {
                    for (int i = 0, n = mb.keys.oSize; i < n; i++) {
                        Rv pair = newArray();
                        pair.putl(0, (Rv) mb.keys.getObject(i));
                        pair.putl(1, (Rv) mb.values.getObject(i));
                        out.putl(i, pair);
                    }
                }
                return out;
            }
        }),

        new NativeFunctionListEntry("Set", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv s = isNew ? thiz : new Rv(Rv.OBJECT, Rv._Set);
                s.type = Rv.OBJECT;
                s.ctorOrProt = Rv._Set;
                s.opaque = new SetBacking();
                Rv src = arg(args, start, num, 0);
                if (src != null && src.type == Rv.ARRAY) {
                    int len = src.num;
                    for (int i = 0; i < len; i++) setAdd(s, getIdx(src, i));
                }
                return s;
            }
        }),

        new NativeFunctionListEntry("Set.add", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                setAdd(thiz, arg(args, start, num, 0));
                return thiz;
            }
        }),

        new NativeFunctionListEntry("Set.has", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                SetBacking sb = setOf(thiz);
                return sb != null && sb.indexOf(arg(args, start, num, 0)) >= 0 ? Rv._true : Rv._false;
            }
        }),

        new NativeFunctionListEntry("Set.delete", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                SetBacking sb = setOf(thiz);
                if (sb == null) return Rv._false;
                int idx = sb.indexOf(arg(args, start, num, 0));
                if (idx < 0) return Rv._false;
                sb.removeAt(idx);
                return Rv._true;
            }
        }),

        new NativeFunctionListEntry("Set.clear", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                SetBacking sb = setOf(thiz);
                if (sb != null) sb.items.oSize = 0;
                return Rv._undefined;
            }
        }),

        new NativeFunctionListEntry("Set.forEach", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                SetBacking sb = setOf(thiz);
                if (sb == null) return Rv._undefined;
                Rv fn = arg(args, start, num, 0);
                Rv thisArg = arg(args, start, num, 1);
                for (int i = 0, n = sb.items.oSize; i < n; i++) {
                    Rv v = (Rv) sb.items.getObject(i);
                    ri.invokeJS3(fn, thisArg, v, v, thiz);
                }
                return Rv._undefined;
            }
        }),

        new NativeFunctionListEntry("Set.values", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                SetBacking sb = setOf(thiz);
                Rv out = newArray();
                if (sb != null) {
                    for (int i = 0, n = sb.items.oSize; i < n; i++) out.putl(i, (Rv) sb.items.getObject(i));
                }
                return out;
            }
        }),

        new NativeFunctionListEntry("Symbol", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                String desc = num > 0 ? toStr(arg(args, start, num, 0)) : "";
                Rv s = new Rv(Rv.OBJECT, Rv._Object);
                s.str = desc;
                s.opaque = UNIQUE_SYMBOL;
                return s;
            }
        }),

        // ============================================================
        //                        PROMISE
        // ============================================================

        new NativeFunctionListEntry("Promise", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                return PromiseRuntime.nativeCtor(isNew, thiz, args, start, num, ri);
            }
        }),

        new NativeFunctionListEntry("__awaitStep", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv p = StdLib.arg(args, start, num, 0);
                Rv cont = StdLib.arg(args, start, num, 1);
                Rv pres = PromiseRuntime.promiseResolve(ri, p);
                return PromiseRuntime.then(ri, pres, cont, Rv._undefined);
            }
        }),

        new NativeFunctionListEntry("Promise.then", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                return PromiseRuntime.nativeThen(isNew, thiz, args, start, num, ri);
            }
        }),

        new NativeFunctionListEntry("Promise.catch", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                return PromiseRuntime.nativeCatch(isNew, thiz, args, start, num, ri);
            }
        }),

        new NativeFunctionListEntry("Promise.resolve", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                return PromiseRuntime.nativeResolve(isNew, thiz, args, start, num, ri);
            }
        }),

        new NativeFunctionListEntry("Promise.reject", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                return PromiseRuntime.nativeReject(isNew, thiz, args, start, num, ri);
            }
        }),

    };

    // ------------------------------------------------------------------
    //  Helpers used by the entries above
    // ------------------------------------------------------------------

    private static final Object FROZEN = new Object();
    private static final Object UNIQUE_SYMBOL = new Object();

    static final boolean strictEq(Rv a, Rv b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.type != b.type) return false;
        switch (a.type) {
            case Rv.UNDEFINED: return true;
            case Rv.NUMBER:    return Rv.sameNumberStrict(Rv.numValue(a), Rv.numValue(b));
            case Rv.STRING:    return a.str.equals(b.str);
            default:           return a == b;
        }
    }

    static final void flatInto(Rv src, Rv dst, int depth) {
        int len = src.num;
        for (int i = 0; i < len; i++) {
            Rv v = getIdx(src, i);
            if (depth > 0 && isArray(v)) {
                flatInto(v, dst, depth - 1);
            } else {
                pushItem(dst, v);
            }
        }
    }

    static final String pad(String s, int targetLen, String filler, boolean atStart) {
        int need = targetLen - s.length();
        if (need <= 0 || filler.length() == 0) return s;
        StringBuffer buf = new StringBuffer(targetLen);
        int fl = filler.length();
        StringBuffer fillBuf = new StringBuffer(need);
        for (int i = 0; i < need; i++) fillBuf.append(filler.charAt(i % fl));
        if (atStart) {
            buf.append(fillBuf.toString()).append(s);
        } else {
            buf.append(s).append(fillBuf.toString());
        }
        return buf.toString();
    }

    static final Rv parseFloatImpl(Rv src) {
        if (src == null) return Rv._NaN;
        String s = src.toStr().str.trim();
        if (s.length() == 0) return Rv._NaN;
        try {
            return new Rv(Double.parseDouble(s));
        } catch (Exception ex) {
            return Rv._NaN;
        }
    }

    static final Rv parseIntImpl(Rv src, Rv radixRv) {
        if (src == null) return Rv._NaN;
        String s = src.toStr().str.trim();
        if (s.length() == 0) return Rv._NaN;
        int radix = 10;
        if (radixRv != null && radixRv != Rv._undefined) {
            radix = toInt(radixRv, 10);
            if (radix == 0) radix = 10;
        }
        int sign = 1, i = 0;
        if (s.charAt(0) == '+') i = 1;
        else if (s.charAt(0) == '-') { i = 1; sign = -1; }
        if (i < s.length() - 1 && s.charAt(i) == '0' && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X')) {
            radix = 16;
            i += 2;
        }
        if (i >= s.length()) return Rv._NaN;
        int v = 0;
        boolean any = false;
        for (; i < s.length(); i++) {
            int d = digitOf(s.charAt(i), radix);
            if (d < 0) break;
            v = v * radix + d;
            any = true;
        }
        return any ? new Rv(sign * v) : Rv._NaN;
    }

    static final int digitOf(char c, int radix) {
        int d = -1;
        if (c >= '0' && c <= '9') d = c - '0';
        else if (c >= 'a' && c <= 'z') d = c - 'a' + 10;
        else if (c >= 'A' && c <= 'Z') d = c - 'A' + 10;
        return d < radix ? d : -1;
    }

    // ---- JSON parse ----

    static final void skipWs(String s, int[] pos) {
        int i = pos[0], n = s.length();
        while (i < n && s.charAt(i) <= ' ') i++;
        pos[0] = i;
    }

    static final Rv jsonParse(String s, int[] pos) {
        skipWs(s, pos);
        if (pos[0] >= s.length()) throw new RuntimeException("unexpected end");
        char c = s.charAt(pos[0]);
        if (c == '{') return jsonParseObject(s, pos);
        if (c == '[') return jsonParseArray(s, pos);
        if (c == '"') return new Rv(jsonParseString(s, pos));
        if (c == 't' || c == 'f') return jsonParseBool(s, pos);
        if (c == 'n') { expect(s, pos, "null"); return Rv._null; }
        if (c == '-' || (c >= '0' && c <= '9')) return jsonParseNumber(s, pos);
        throw new RuntimeException("unexpected '" + c + "' at " + pos[0]);
    }

    static final Rv jsonParseObject(String s, int[] pos) {
        pos[0]++; // consume {
        Rv obj = newObject();
        skipWs(s, pos);
        if (pos[0] < s.length() && s.charAt(pos[0]) == '}') { pos[0]++; return obj; }
        while (true) {
            skipWs(s, pos);
            if (pos[0] >= s.length() || s.charAt(pos[0]) != '"') throw new RuntimeException("expected key");
            String key = jsonParseString(s, pos);
            skipWs(s, pos);
            if (pos[0] >= s.length() || s.charAt(pos[0]) != ':') throw new RuntimeException("expected ':'");
            pos[0]++;
            obj.putl(key, jsonParse(s, pos));
            skipWs(s, pos);
            if (pos[0] >= s.length()) throw new RuntimeException("unterminated object");
            char c = s.charAt(pos[0]);
            if (c == ',') { pos[0]++; continue; }
            if (c == '}') { pos[0]++; return obj; }
            throw new RuntimeException("expected ',' or '}'");
        }
    }

    static final Rv jsonParseArray(String s, int[] pos) {
        pos[0]++; // consume [
        Rv arr = newArray();
        skipWs(s, pos);
        if (pos[0] < s.length() && s.charAt(pos[0]) == ']') { pos[0]++; return arr; }
        int i = 0;
        while (true) {
            arr.putl(i++, jsonParse(s, pos));
            skipWs(s, pos);
            if (pos[0] >= s.length()) throw new RuntimeException("unterminated array");
            char c = s.charAt(pos[0]);
            if (c == ',') { pos[0]++; continue; }
            if (c == ']') { pos[0]++; return arr; }
            throw new RuntimeException("expected ',' or ']'");
        }
    }

    static final String jsonParseString(String s, int[] pos) {
        pos[0]++; // consume "
        StringBuffer buf = new StringBuffer();
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]++);
            if (c == '"') return buf.toString();
            if (c == '\\') {
                if (pos[0] >= s.length()) throw new RuntimeException("bad escape");
                char e = s.charAt(pos[0]++);
                switch (e) {
                    case '"':  buf.append('"'); break;
                    case '\\': buf.append('\\'); break;
                    case '/':  buf.append('/'); break;
                    case 'n':  buf.append('\n'); break;
                    case 't':  buf.append('\t'); break;
                    case 'r':  buf.append('\r'); break;
                    case 'b':  buf.append('\b'); break;
                    case 'f':  buf.append('\f'); break;
                    case 'u':
                        if (pos[0] + 4 > s.length()) throw new RuntimeException("bad \\u escape");
                        buf.append((char) Integer.parseInt(s.substring(pos[0], pos[0] + 4), 16));
                        pos[0] += 4;
                        break;
                    default: throw new RuntimeException("bad escape \\" + e);
                }
            } else {
                buf.append(c);
            }
        }
        throw new RuntimeException("unterminated string");
    }

    static final Rv jsonParseBool(String s, int[] pos) {
        if (s.charAt(pos[0]) == 't') { expect(s, pos, "true"); return Rv._true; }
        expect(s, pos, "false"); return Rv._false;
    }

    static final void expect(String s, int[] pos, String lit) {
        int n = lit.length();
        if (pos[0] + n > s.length() || !s.substring(pos[0], pos[0] + n).equals(lit)) {
            throw new RuntimeException("expected " + lit);
        }
        pos[0] += n;
    }

    static final Rv jsonParseNumber(String s, int[] pos) {
        int st = pos[0];
        if (s.charAt(pos[0]) == '-') pos[0]++;
        while (pos[0] < s.length() && s.charAt(pos[0]) >= '0' && s.charAt(pos[0]) <= '9') pos[0]++;
        // skip fractional / exponent (truncated to int)
        if (pos[0] < s.length() && s.charAt(pos[0]) == '.') {
            pos[0]++;
            while (pos[0] < s.length() && s.charAt(pos[0]) >= '0' && s.charAt(pos[0]) <= '9') pos[0]++;
        }
        if (pos[0] < s.length() && (s.charAt(pos[0]) == 'e' || s.charAt(pos[0]) == 'E')) {
            pos[0]++;
            if (pos[0] < s.length() && (s.charAt(pos[0]) == '+' || s.charAt(pos[0]) == '-')) pos[0]++;
            while (pos[0] < s.length() && s.charAt(pos[0]) >= '0' && s.charAt(pos[0]) <= '9') pos[0]++;
        }
        String lit = s.substring(st, pos[0]);
        try {
            return new Rv(Double.parseDouble(lit));
        } catch (NumberFormatException ex) {
            return Rv._NaN;
        }
    }

    // ---- JSON stringify ----

    static final boolean jsonStringify(Rv v, StringBuffer buf, String indent, int depth) {
        if (v == null || v == Rv._undefined || v.isCallable()) return false;
        if (v == Rv._null) { buf.append("null"); return true; }
        if (v == Rv._true) { buf.append("true"); return true; }
        if (v == Rv._false) { buf.append("false"); return true; }
        int t = v.type;
        if (t == Rv.NUMBER || t == Rv.NUMBER_OBJECT) {
            if (v == Rv._NaN) { buf.append("null"); return true; }
            buf.append(v.toStr().str);
            return true;
        }
        if (t == Rv.STRING || t == Rv.STRING_OBJECT) {
            jsonEncodeString(v.str, buf);
            return true;
        }
        if (t == Rv.ARRAY) {
            int len = v.num;
            buf.append('[');
            boolean first = true;
            for (int i = 0; i < len; i++) {
                if (!first) buf.append(',');
                if (indent != null) { buf.append('\n'); appendIndent(buf, indent, depth + 1); }
                Rv item = getIdx(v, i);
                StringBuffer tmp = new StringBuffer();
                if (!jsonStringify(item, tmp, indent, depth + 1)) tmp.append("null");
                buf.append(tmp.toString());
                first = false;
            }
            if (!first && indent != null) { buf.append('\n'); appendIndent(buf, indent, depth); }
            buf.append(']');
            return true;
        }
        // generic object
        if (v.prop == null) { buf.append("{}"); return true; }
        Pack keys = v.prop.keys();
        buf.append('{');
        boolean first = true;
        for (int i = 0, n = keys.oSize; i < n; i++) {
            String k = (String) keys.oArray[i];
            Rv val = v.prop.get(k);
            StringBuffer tmp = new StringBuffer();
            if (!jsonStringify(val, tmp, indent, depth + 1)) continue;
            if (!first) buf.append(',');
            if (indent != null) { buf.append('\n'); appendIndent(buf, indent, depth + 1); }
            jsonEncodeString(k, buf);
            buf.append(':');
            if (indent != null) buf.append(' ');
            buf.append(tmp.toString());
            first = false;
        }
        if (!first && indent != null) { buf.append('\n'); appendIndent(buf, indent, depth); }
        buf.append('}');
        return true;
    }

    static final void appendIndent(StringBuffer buf, String indent, int depth) {
        for (int i = 0; i < depth; i++) buf.append(indent);
    }

    static final void jsonEncodeString(String s, StringBuffer buf) {
        buf.append('"');
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  buf.append("\\\""); break;
                case '\\': buf.append("\\\\"); break;
                case '\n': buf.append("\\n"); break;
                case '\t': buf.append("\\t"); break;
                case '\r': buf.append("\\r"); break;
                case '\b': buf.append("\\b"); break;
                case '\f': buf.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        buf.append("\\u");
                        String hex = Integer.toHexString(c);
                        for (int p = hex.length(); p < 4; p++) buf.append('0');
                        buf.append(hex);
                    } else {
                        buf.append(c);
                    }
            }
        }
        buf.append('"');
    }

    // ---- Map / Set ----

    static final class MapBacking {
        final Pack keys = new Pack(-1, 8);
        final Pack values = new Pack(-1, 8);

        int indexOf(Rv k) {
            for (int i = 0, n = keys.oSize; i < n; i++) {
                if (strictEq(k, (Rv) keys.getObject(i))) return i;
            }
            return -1;
        }

        void set(Rv k, Rv v) {
            int i = indexOf(k);
            if (i >= 0) {
                values.oArray[i] = v;
            } else {
                keys.add(k);
                values.add(v);
            }
        }

        void removeAt(int i) {
            int n = keys.oSize;
            for (int j = i + 1; j < n; j++) {
                keys.oArray[j - 1] = keys.oArray[j];
                values.oArray[j - 1] = values.oArray[j];
            }
            keys.oSize = n - 1;
            values.oSize = n - 1;
        }

        void clear() {
            keys.oSize = 0;
            values.oSize = 0;
        }
    }

    static final class SetBacking {
        final Pack items = new Pack(-1, 8);

        int indexOf(Rv v) {
            for (int i = 0, n = items.oSize; i < n; i++) {
                if (strictEq(v, (Rv) items.getObject(i))) return i;
            }
            return -1;
        }

        void removeAt(int i) {
            int n = items.oSize;
            for (int j = i + 1; j < n; j++) items.oArray[j - 1] = items.oArray[j];
            items.oSize = n - 1;
        }
    }

    static final MapBacking mapOf(Rv r) {
        return r != null && r.opaque instanceof MapBacking ? (MapBacking) r.opaque : null;
    }

    static final SetBacking setOf(Rv r) {
        return r != null && r.opaque instanceof SetBacking ? (SetBacking) r.opaque : null;
    }

    static final void mapSet(Rv m, Rv k, Rv v) {
        MapBacking mb = mapOf(m);
        if (mb == null) { mb = new MapBacking(); m.opaque = mb; }
        mb.set(k, v);
    }

    static final void setAdd(Rv s, Rv v) {
        SetBacking sb = setOf(s);
        if (sb == null) { sb = new SetBacking(); s.opaque = sb; }
        if (sb.indexOf(v) < 0) sb.items.add(v);
    }
}
