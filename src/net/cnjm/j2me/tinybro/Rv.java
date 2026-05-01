package net.cnjm.j2me.tinybro;

import net.cnjm.j2me.util.Pack;

public class Rv {
    
    static final int UNDEFINED =        RC.TOK_UNKNOWN; // undefined
    static final int NUMBER =           RC.TOK_NUMBER;
    static final int STRING =           RC.TOK_STRING;
    static final int SYMBOL =           RC.TOK_SYMBOL; // unresolved symbol
    static final int LVALUE =           9;    // left_value
    
    public static final int OBJECT =           0x10;
    static final int NUMBER_OBJECT =    OBJECT + Rv.NUMBER;
    static final int STRING_OBJECT =    OBJECT + Rv.STRING;
    static final int ARRAY =            OBJECT + 0x0A;
    static final int ARGUMENTS =        OBJECT + 0x0B;
    static final int ERROR =            OBJECT + 0x0C;
    /** Uint8Array: uses {@link #opaque} {@link Uint8View}; {@code num} is element count. */
    public static final int UINT8_ARRAY = OBJECT + 0x0E;
    /** Int32Array: uses {@link #opaque} {@link Int32View}; {@code num} is element count (not bytes). */
    public static final int INT32_ARRAY = OBJECT + 0x0F;
    /** Float32Array: uses {@link #opaque} {@link Float32View}; element size 4 bytes; {@code num} = element count. */
    public static final int FLOAT32_ARRAY = OBJECT + 0x10;
    
    static final int FUNCTION =         0x2C;
    static final int NATIVE =           0x2D; // native function
    static final int CTOR_MASK =        0x40;
    
    public int type;

    /** Pre-computed String.hashCode() for SYMBOL/STRING values used as map keys.
     *  0 means "not cached yet". Saves work on every Rhash lookup in the hot path. */
    public int hash;

    /** Structural generation counter. Bumped every time a property is added or
     *  replaced via {@link #putl}. Used by the polymorphic inline cache in
     *  LVALUE nodes to validate whether a previously memoised lookup result is
     *  still fresh. */
    public int gen;

    /** Monomorphic inline cache for {@link #getByKey(Rv)} (scope chain symbol lookup). */
    private Rv symPicKey;
    private Rv symPicVal;
    private int symPicGen;
    /** Scope object that owns the binding; {@link #gen} is validated with {@link #symPicHostGen}. */
    private Rv symPicHost;
    private int symPicHostGen;

    /**
     * Polymorphic (PIC) LVALUE read cache, allocated on first use of {@link #get()}.
     * See {@link LvalueInlineCache}. Null keeps bytecode pool {@link Rv} nodes small
     * until a member-read site is actually warm.
     */
    LvalueInlineCache icPic;

    /**
     * 2–4 slot polymorphic inline cache for a single LVALUE member-read site.
     *  Each entry mirrors the monomorphic case: holder, resolved value, key,
     *  the holder’s backing {@link Rhash}, and that map’s {@code gen} at
     *  resolution. Miss → full {@link Rv#get(String)}; install uses round-robin
     *  to evict (see {@link #write}).
     *
     *  <p>Identity of {@code rhash} plus {@code stamp} is required: array holders
     *  may replace {@code prop} (e.g. {@code unshift/sort/reverse}) and a new
     *  map can carry the same {@code gen} as an unrelated one.
     *
     *  <p>A future shape-keyed entry (e.g. cache by the map that supplied the
     *  value, not the receiver) could help many distinct instances sharing one
     *  prototype; this PIC is keyed by {@code holder} identity only.
     */
    static final class LvalueInlineCache {
        /** 6 entries: more slots reduce PIC thrash on warm polymorphic read sites. */
        static final int SLOTS = 6;
        final Rv[] holder;
        final Rv[] value;
        final String[] key;
        final Rhash[] rhash;
        final int[] stamp;
        final int[] layout;
        /** Next {@link #SLOTS} index to write on a cache miss. */
        int write;

        LvalueInlineCache() {
            holder = new Rv[SLOTS];
            value = new Rv[SLOTS];
            key = new String[SLOTS];
            rhash = new Rhash[SLOTS];
            stamp = new int[SLOTS];
            layout = new int[SLOTS];
        }
    }
    
    // ------- NUM STR SYM LVA OBJ NOB SOB ARR ARG FUN NAT cob CTR
    // num      o           x       o       o   o   o   o         
    // str          o   o   x           o               o       x 
    // node                                         o           x 
    // prop                     o   o   o   o   o   o   o   o   o 
    // co                   o                       o   o       o 
    // ctPr                     o   o   o   o   o   o   o       x 
    // prev                                                 o     
      
    /** Some helper to hold Opaque data (such as native objects) */
    public Object opaque;
    /** For function & native it's number of formal arguments */
    public int num;
    /** When {@code true}, numeric value is in {@link #d} (IEEE-754); else integer fast path in {@link #num}. */
    public boolean f;
    /** IEEE-754 value when {@link #f} is {@code true}. */
    public double d;
    /** For native it's the name of native function */
    public String str;
    /** For non-native function only */
    public Object obj;
    /** For all object type */
    public Rhash prop;
    /** For functions it's callObject, for lvalue it's the referenced object */
    public Rv co;
    /** prototype for constructor, constructor for other object types */
    public Rv ctorOrProt;
//    /** prototype, only used by constructors */
//    public Rv prot;
    /** only used by CallObject, this may NOT be null for an plain object (With statement) */
    public Rv prev;

    public Rv() {
    }

    /** Fixed slab for {@link Rhash} entry nodes. These Rv cells are never JS values. */
    private static final int RHASH_ENTRY_SLAB_CAP = 4096;
    private static final Rv[] RHASH_ENTRY_SLAB = new Rv[RHASH_ENTRY_SLAB_CAP];
    private static int rhashEntryTop;

    static final Rv acquireRhashEntry() {
        if (rhashEntryTop > 0) {
            return RHASH_ENTRY_SLAB[--rhashEntryTop];
        }
        return new Rv();
    }

    static final void releaseRhashEntry(Rv entry) {
        if (entry == null) {
            return;
        }
        entry.clearRhashEntry();
        if (rhashEntryTop < RHASH_ENTRY_SLAB_CAP) {
            RHASH_ENTRY_SLAB[rhashEntryTop++] = entry;
        }
    }

    /** Recycled {@link Rhash} entry nodes sitting in the slab freelist (diagnostic). */
    public static int rhashEntryRecycleDepth() {
        return rhashEntryTop;
    }

    public static int rhashEntrySlabCapacity() {
        return RHASH_ENTRY_SLAB_CAP;
    }

    private final void clearRhashEntry() {
        type = Rv.UNDEFINED;
        hash = 0;
        gen = 0;
        icPic = null;
        opaque = null;
        num = 0;
        f = false;
        d = 0.0;
        str = null;
        obj = null;
        prop = null;
        co = null;
        ctorOrProt = null;
        prev = null;
    }

    final Rv resetTempLvalue(String s, Rv referenced) {
        clearRhashEntry();
        type = Rv.LVALUE;
        str = s;
        co = referenced;
        return this;
    }

    final void clearEvalTemp() {
        clearRhashEntry();
    }
    
    /**
     * To create a number
     * @param n
     */
    public Rv(int n) {
        this.type = Rv.NUMBER;
        this.num = n;
        this.f = false;
    }

    /** Primitive number from IEEE-754. */
    public Rv(double x) {
        this.type = Rv.NUMBER;
        setPrimitiveFromDouble(x);
    }

    /** Prefer int storage when the value is an integer in int32 range; else {@link #f}/{@link #d}. */
    public final void setPrimitiveFromDouble(double x) {
        if (Double.isNaN(x)) {
            this.f = true;
            this.d = Double.NaN;
            this.num = 0;
            return;
        }
        if (Double.isInfinite(x)) {
            this.f = true;
            this.d = x;
            this.num = 0;
            return;
        }
        if (x >= (double) Integer.MIN_VALUE && x <= (double) Integer.MAX_VALUE) {
            int ix = (int) x;
            if (x == (double) ix) {
                this.f = false;
                this.num = ix;
                return;
            }
        }
        this.f = true;
        this.d = x;
        this.num = 0;
    }

    /** Numeric value as IEEE-754 (primitive or boxed number). */
    public static final double numValue(Rv r) {
        if (r == null || r == Rv._undefined) return Double.NaN;
        if (r == Rv._NaN) return Double.NaN;
        int t = r.type;
        if (t != Rv.NUMBER && t != Rv.NUMBER_OBJECT) return Double.NaN;
        return r.f ? r.d : (double) r.num;
    }

    public static final int toInt32(double x) {
        if (Double.isNaN(x) || Double.isInfinite(x) || x == 0.0) {
            return 0;
        }
        double mod = x % 4294967296.0;
        if (mod >= 2147483648.0) {
            mod -= 4294967296.0;
        } else if (mod < -2147483648.0) {
            mod += 4294967296.0;
        }
        return (int) mod;
    }

    public static final long toUint32(double x) {
        return (long) (toInt32(x) & 0xffffffffL);
    }

    static final double jsRemainder(double n, double d) {
        if (Double.isNaN(n) || Double.isNaN(d)) {
            return Double.NaN;
        }
        if (Double.isInfinite(n)) {
            return Double.NaN;
        }
        if (d == 0.0) {
            return Double.NaN;
        }
        if (Double.isInfinite(d)) {
            return n;
        }
        return n % d;
    }

    public static final boolean sameNumberStrict(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return false;
        }
        return a == b;
    }
    
    /**
     * To create a string or symbol
     * @param s
     */
    public Rv(String s) {
        this.type = Rv.STRING;
        this.str = s;
    }
    
    /**
     * Canonical Java {@link String} instances for compile-time identifiers and
     * string literals. Distinct dynamic strings must never be inserted here.
     */
    private static final java.util.Hashtable _atomPool = new java.util.Hashtable();

    public static final String atom(String s) {
        String c = (String) _atomPool.get(s);
        if (c == null) {
            _atomPool.put(s, s);
            return s;
        }
        return c;
    }

    /**
     * Canonical SYMBOL pool. Ensures that a given identifier string always maps
     * to the same Rv, so comparisons can short-circuit to pointer equality and
     * the hashCode is computed once per symbol name.
     */
    private static final java.util.Hashtable _symbolPool = new java.util.Hashtable();

    /**
     * Literals longer than this skip {@link #_atomPool} so huge or one-off
     * embedded strings do not grow the intern table unbounded (J2ME heap).
     * {@link Rhash} still resolves keys with {@code equals} when references differ.
     */
    static final int ATOM_INTERN_MAX_STRLEN = 512;

    public static final Rv symbol(String s) {
        s = atom(s);
        Rv ret = (Rv) _symbolPool.get(s);
        if (ret == null) {
            ret = new Rv(s);
            ret.type = Rv.SYMBOL;
            ret.hash = s.hashCode();
            _symbolPool.put(s, ret);
        }
        return ret;
    }

    /**
     * String primitive from a JS source literal; short literals use {@link #atom}
     * (shared with {@link #symbol}) so Rhash can often match with reference
     * equality. Long literals skip the atom pool to limit heap on constrained VMs.
     */
    public static final Rv stringLiteral(String s) {
        if (s != null && s.length() > ATOM_INTERN_MAX_STRLEN) {
            Rv ret = new Rv(s);
            ret.hash = s.hashCode();
            return ret;
        }
        s = atom(s);
        Rv ret = new Rv(s);
        ret.hash = s.hashCode();
        return ret;
    }
    
    public static final Rv error(String msg) {
        return new Rv(Rv.ERROR, Rv._Error).putl("message", new Rv(msg));
    }
    
    /**
     * To create a lvalue
     * @param n
     * @param s
     * @param referenced
     */
    public Rv(String s, Rv referenced) {
        this.type = Rv.LVALUE;
        this.str = s;
        this.co = referenced;
    }
    
    /** 
     * To create an object
     * Type can not be function or native
     * @param type
     * @param ctor must not be null
     */
    public Rv(int type, Rv ctor) {
        if (type < Rv.OBJECT || type >= Rv.FUNCTION || ctor.type < Rv.CTOR_MASK) {
            throw new RuntimeException("not a valid ctor(object)");
        }
        this.type = type;
        this.ctorOrProt = ctor;
        this.prop = new Rhash(11);
    }
    
    /**
     * To create a function or native function
     * @param isNative
     * @param func
     * @param prototype can be null
     */
    public Rv(boolean isNative, Object func, int num) {
        if (isNative) {
            this.type = Rv.NATIVE;
            this.str = (String) func;
        } else {
            this.type = Rv.FUNCTION;
            this.obj = func;
        }
        this.num = num;
        Rv co = this.co = new Rv();
        co.type = Rv.OBJECT;
        co.prop = new Rhash(11);
        this.ctorOrProt = Rv._Function;
        this.prop = new Rhash(11);
    }
    
    /**
     * TODO change to reset
     * @param str
     * @param prevCallObj
     * @return
     */
    public final Rv nativeCtor(String str, Rv prevCallObj) {
        this.type = Rv.NATIVE | Rv.CTOR_MASK;
        this.str = str;
        this.num = 1; // length of all native ctors are 1 
        this.ctorOrProt = new Rv(Rv.OBJECT, Rv._Object);
        this.prop = new Rhash(11);
        (this.co = new Rv(Rv.OBJECT, Rv._Object)).prev = prevCallObj;
        return this;
    }
    
    public final Pack keyArray() {
        if (type == ARRAY || type == ARGUMENTS) {
            Pack p;
            int len = this.num;
            if ((p = (Pack) this.obj) == null || p.oSize != len) {
                this.obj = p = new Pack(-1, len);
                for (int i = 0; i < len; p.add(intStr(i++)));
            }
            return p;
        }
        return this.prop.keys();
    }
    
    /**
     * This only apply to lvalue
     * @return
     */
    public final Rv get() {
        int t;
        if ((t = co.type) <= Rv.STRING) { // NUMBER or STRING
            Rv newco = new Rv(t + Rv.OBJECT, t == Rv.STRING ? Rv._String : Rv._Number);
            newco.str = co.str;
            newco.num = co.num;
            newco.f = co.f;
            newco.d = co.d;
            this.co = newco;
        }
        // ---- polymorphic inline cache (PIC) for LVALUE reads ----
        // Per-slot: same receiver identity + same backing Rhash + unchanged gen
        // + same key => return cached value. Rhash identity (not just gen) is
        // required because Array.unshift/sort/reverse can replace the holder’s map.
        Rv holder = this.co;
        String key = this.str;
        int hty = holder.type;
        // Typed-array elements live in byte[] views; indexed writes bump
        // holder.gen but not holder.prop.gen, so a PIC keyed only on Rhash.gen
        // would stay hot forever and return stale values (see put() branches).
        boolean skipLvaluePic =
                hty == Rv.UINT8_ARRAY || hty == Rv.INT32_ARRAY || hty == Rv.FLOAT32_ARRAY;
        Rhash hp;
        LvalueInlineCache pic = this.icPic;
        if (!skipLvaluePic && pic != null) {
            for (int i = 0, n = LvalueInlineCache.SLOTS; i < n; i++) {
                Rv h0 = pic.holder[i];
                if (h0 == null) {
                    continue;
                }
                if (h0 == holder
                        && (hp = holder.prop) != null
                        && pic.rhash[i] == hp
                        && pic.stamp[i] == hp.gen
                        && pic.layout[i] == hp.layoutFp
                        && (pic.key[i] == key
                                || (pic.key[i] != null && pic.key[i].equals(key)))) {
                    return pic.value[i];
                }
            }
        }
        Rv v = holder.get(key);
        if (skipLvaluePic) {
            return v;
        }
        if (pic == null) {
            this.icPic = pic = new LvalueInlineCache();
        }
        int w = pic.write;
        pic.holder[w] = holder;
        pic.rhash[w] = (hp = holder.prop);
        pic.stamp[w] = hp != null ? hp.gen : 0;
        pic.layout[w] = hp != null ? hp.layoutFp : 0;
        pic.key[w] = key;
        pic.value[w] = v;
        pic.write = (w + 1) % LvalueInlineCache.SLOTS;
        return v;
    }
    
    public final boolean has(String p) {
        int type;
        if ((type = this.type) < Rv.OBJECT) {
            return false;
        }
        if (type >= Rv.CTOR_MASK && "prototype".equals(p)) { // this is a constructor
            return this.ctorOrProt != null;
        } else if (type >= Rv.ARRAY && "length".equals(p)) { // array/arguments/function/native
            return true;
        } else if (type == Rv.UINT8_ARRAY || type == Rv.INT32_ARRAY || type == Rv.FLOAT32_ARRAY) {
            int idx = arrayIndexKey(p);
            if (idx >= 0) {
                return idx < this.num;
            }
        }
        int ph = p.hashCode();
        Rv ctor, prot, obj, ret;
        for (obj = this; (ret = obj.prop.getH(ph, p)) == null
                && (ctor = obj.ctorOrProt) != null
                && (prot = ctor.ctorOrProt) != null; obj = prot)
            ;
        return ret != null;
    }
    
    public final Rv get(String p) {
        int type;
        // ES5 string indexing / length. Works for both the primitive STRING (e.g.
        // directly holding a string literal) and for STRING_OBJECT (produced when
        // `Rv.get()` promotes a primitive string holder into a boxed object so
        // properties can be looked up via the member operators `.`/`[]`).
        // Required so that generic container code (for..of desugaring, iterator
        // helpers, …) can treat strings as read-only arrays of one-character
        // strings without a per-call typeof branch.
        if ((type = this.type) == Rv.STRING || type == Rv.STRING_OBJECT) {
            if ("length".equals(p)) {
                return Rv.smallInt(this.str.length());
            }
            int pl = p.length();
            if (pl > 0) {
                char c0 = p.charAt(0);
                if (c0 >= '0' && c0 <= '9') {
                    int idx = arrayIndexKey(p);
                    if (idx >= 0 && idx < this.str.length()) {
                        return Rv.asciiCharRv(this.str.charAt(idx));
                    }
                }
            }
            if (type == Rv.STRING) {
                return Rv._undefined;
            }
            // STRING_OBJECT: fall through to prototype lookup below for things
            // like "abc".charAt, "abc".indexOf, etc.
        } else if (type < Rv.OBJECT) {
            return Rv._undefined;
        }
        // `.prototype` on a plain FUNCTION/NATIVE that hasn't been used as a
        // constructor yet. In stock ES every function is born with its own
        // `.prototype` object; upstream RockScript only materialised one lazily
        // on the first `new`, so code like `Entity.prototype.update = fn`
        // silently wrote into `undefined` (desugared classes install their
        // methods *before* ever calling `new`). Materialise the prototype on
        // first read and flip CTOR_MASK so `call(isInit, ...)` won't overwrite
        // it on the first `new`. Note: at this point `ctorOrProt` is set to
        // `Rv._Function` (the global Function proto, installed by the Rv
        // constructor) — it is *not* null, which is why the existing check
        // below doesn't cover this case.
        if ("prototype".equals(p) && (type == Rv.FUNCTION || type == Rv.NATIVE)) {
            // If user already wrote `fn.prototype = X` before this first read,
            // the value landed in `this.prop["prototype"]` because the function
            // didn't yet carry CTOR_MASK (see `put()`), and the constructor
            // path below wasn't taken. Promote it now instead of shadowing it
            // with a freshly materialised empty proto: otherwise the explicit
            // assignment is silently lost (class desugar does exactly this:
            // `Player.prototype = Object.create(Entity.prototype)` followed
            // by method installs on `Player.prototype.*`).
            Rv proto = this.prop != null ? this.prop.get(p) : null;
            if (proto != null) {
                this.prop.removeAndRelease(p.hashCode(), p);
            } else {
                proto = new Rv(Rv.OBJECT, Rv._Object);
            }
            this.ctorOrProt = proto;
            this.type = type | Rv.CTOR_MASK;
            return proto;
        }
        if (type >= Rv.CTOR_MASK && "prototype".equals(p) // this is a constructor
                || type >= Rv.OBJECT && type < Rv.CTOR_MASK && "constructor".equals(p)) { 
            return this.ctorOrProt != null ? this.ctorOrProt : Rv._undefined;
        } else if ("length".equals(p)) { // array/arguments/function/native
            int num = type >= Rv.ARRAY ? this.num : -1;
            if (num >= 0) return Rv.smallInt(num);
        } else if (type == Rv.UINT8_ARRAY || type == Rv.INT32_ARRAY || type == Rv.FLOAT32_ARRAY) {
            int idx = arrayIndexKey(p);
            if (idx >= 0) {
                if (type == Rv.UINT8_ARRAY) {
                    Uint8View uv = (Uint8View) this.opaque;
                    if (idx < uv.byteLength) {
                        return Rv.smallInt(uv.data[uv.offset + idx] & 0xff);
                    }
                } else if (type == Rv.INT32_ARRAY) {
                    Int32View iv = (Int32View) this.opaque;
                    int nel = iv.byteLength >> 2;
                    if (idx < nel) {
                        return new Rv(int32LoadLE(iv.data, iv.offset + (idx << 2)));
                    }
                } else {
                    Float32View fv = (Float32View) this.opaque;
                    int nel = fv.byteLength >> 2;
                    if (idx < nel) {
                        return new Rv((double) Float.intBitsToFloat(int32LoadLE(fv.data, fv.offset + (idx << 2))));
                    }
                }
            }
        }
        int ph = p.hashCode();
        Rv ctor, prot, prev, obj, ret;
        for (obj = this; (ret = obj.prop.getH(ph, p)) == null
                && (ctor = obj.ctorOrProt) != null
                && (prot = ctor.ctorOrProt) != null; obj = prot)
            ;
        // Upstream initialises every plain function with `ctorOrProt =
        // Rv._Function`, which is how lookups like `fn.call`/`fn.apply` reach
        // `Function.prototype.*`. The moment the function is used as a
        // constructor (or `fn.prototype = X` is assigned), that slot is
        // replaced by the instance-proto, severing the Function.prototype
        // chain. Class desugar relies on both semantics simultaneously (an
        // `Entity.prototype.m = ...` install before `new`, plus a
        // `Entity.call(this, ...)` super invocation), so fall back to
        // `Function.prototype` once the normal walk has run dry.
        if (ret == null && (type == Rv.FUNCTION || type == Rv.NATIVE
                || type == (Rv.FUNCTION | Rv.CTOR_MASK)
                || type == (Rv.NATIVE | Rv.CTOR_MASK))) {
            Rv fp = Rv._Function != null ? Rv._Function.ctorOrProt : null;
            if (fp != null && fp.prop != null) {
                ret = fp.prop.getH(ph, p);
            }
        }
        if (ret == null && (prev = this.prev) != null) { // this is a call object
            for (obj = prev; (ret = obj.prop.getH(ph, p)) == null
                    && (prev = obj.prev) != null; obj = prev)
                ;
        }
        return ret != null ? ret : Rv._undefined;
    }
    
    /**
     * for lvalue only
     * @param val
     * @return
     */
    public final Rv put(Rv val) {
        val = val.pv();
        String p = this.str;
        Rv o = this.co;
        int type;
        if ((type = o.type) < Rv.OBJECT) {
            // do nothing
        } else if ("prototype".equals(p)
                && (type == (Rv.FUNCTION | Rv.CTOR_MASK)
                        || type == (Rv.NATIVE | Rv.CTOR_MASK)
                        || type == Rv.FUNCTION
                        || type == Rv.NATIVE)) {
            // Writing `.prototype` on any function installs the value as the
            // constructor's prototype. Upstream only honoured this when the
            // function was already flagged as a constructor; class desugar
            // assigns `Player.prototype = Object.create(Entity.prototype)`
            // on a brand new `Player` function that still has no CTOR_MASK,
            // so the assignment used to fall through to the plain property
            // branch below and was later discarded on the first `new`.
            int valty;
            if ((valty = val.type) >= Rv.OBJECT && valty < Rv.CTOR_MASK) {
                o.ctorOrProt = val;
                if ((type & Rv.CTOR_MASK) == 0) o.type = type | Rv.CTOR_MASK;
            }
        } else if (type >= Rv.ARRAY && "length".equals(p)) {
            if (type == Rv.ARRAY && (val = val.toNum()) != Rv._NaN) {
                int newNum;
                double nv = numValue(val);
                if (Double.isNaN(nv)) {
                    newNum = 0;
                } else {
                    newNum = (int) nv;
                }
                if (newNum < o.num) { // trim array
                    Rhash prop = o.prop;
                    for (int i = o.num; --i >= newNum; prop.removeAndRelease(0, intStr(i)));
                    ++o.gen;
                }
                o.num = newNum;
            } // else do nothing (for Arguments, Function, Native)
        } else {
            Rv obj = o, prev;
            if (obj.type == Rv.ARRAY) {
                try {
                    int idx = Integer.parseInt(p);
                    obj.putl(idx, val);
                    return this;
                } catch (Exception ex) { }
            } else if (obj.type == Rv.UINT8_ARRAY) {
                int idx = arrayIndexKey(p);
                if (idx >= 0) {
                    Uint8View uv = (Uint8View) obj.opaque;
                    if (idx < uv.byteLength) {
                        Rv n = val.toNum();
                        int b = toInt32(numValue(n));
                        uv.data[uv.offset + idx] = (byte) (b & 0xff);
                        // Bump gen so the per-instance read PIC (symPic*) does not
                        // return a stale value next time the same key Rv is used.
                        ++obj.gen;
                    }
                }
                return this;
            } else if (obj.type == Rv.INT32_ARRAY) {
                int idx = arrayIndexKey(p);
                if (idx >= 0) {
                    Int32View iv = (Int32View) obj.opaque;
                    int nel = iv.byteLength >> 2;
                    if (idx < nel) {
                        Rv n = val.toNum();
                        int32StoreLE(iv.data, iv.offset + (idx << 2), toInt32(numValue(n)));
                        ++obj.gen;
                    }
                }
                return this;
            } else if (obj.type == Rv.FLOAT32_ARRAY) {
                int idx = arrayIndexKey(p);
                if (idx >= 0) {
                    Float32View fv = (Float32View) obj.opaque;
                    int nel = fv.byteLength >> 2;
                    if (idx < nel) {
                        Rv n = val.toNum();
                        if (n != Rv._NaN) {
                            float f = (float) numValue(n);
                            int32StoreLE(fv.data, fv.offset + (idx << 2), Float.floatToIntBits(f));
                        }
                        ++obj.gen;
                    }
                }
                return this;
            }
            Rv ret;
            for (; (ret = obj.prop.get(p)) == null && (prev = obj.prev) != null; obj = prev);
            Rv target = ret == null ? o : obj;
            target.prop.put(p, val);
            ++target.gen;
            Object op;
            if ((op = target.opaque) instanceof OpaquePropertySink) {
                ((OpaquePropertySink) op).onPropertyPut(p, val);
            }
        }
        return this;
    }
    
    /**
     * local put, i.e. always put to this object, no checking for previous CallObject 
     * @param p property name, must be a String
     * @param val
     * @return
     */
    final Rv putl(String p, Rv val) {
        this.prop.put(p, val);
        ++this.gen;
        Object op;
        if ((op = this.opaque) instanceof OpaquePropertySink) {
            ((OpaquePropertySink) op).onPropertyPut(p, val);
        }
        return this;
    }

    final Rv putl(int i, Rv val) {
        this.prop.put(intStr(i), val);
        if (i >= this.num) this.num = i + 1;
        ++this.gen;
        return this;
    }
    
    /** This must be an array object 
     */
    final Rv shift(int idx) {
        Rhash prop = this.prop;
        Rv ret = prop.get(intStr(idx));
        for (int i = idx, n = --this.num; i < n; i++) {
            Rv val = prop.get(intStr(i + 1));
            prop.put(intStr(i), val != null ? val : Rv._undefined);
        }
        // Always bump gen: pop() (idx == num-1) walks zero iterations of the
        // loop above and would otherwise leave the Rhash generation unchanged.
        // The LVALUE PIC on `array.length` validates via Rhash gen, so a
        // no-bump pop stale-caches length -> tight `while (arr.length > n)
        // arr.pop()` loops spin forever.
        ++prop.gen;
        ++this.gen;
        return ret != null ? ret : Rv._undefined;
    }

    public final boolean isNum() {
        return this.type == Rv.NUMBER || this.type == Rv.NUMBER_OBJECT;
    }
    
    public final Rv toNum() {
        if (this == _null) return Rv._false;
        switch (this.type) {
        case Rv.NUMBER:
            return this;
        case Rv.NUMBER_OBJECT: {
            Rv u = new Rv(0);
            u.type = Rv.NUMBER;
            u.f = this.f;
            u.num = this.num;
            u.d = this.d;
            return u;
        }
        case Rv.STRING:
        case Rv.STRING_OBJECT:
            try {
                return new Rv(Double.parseDouble(this.str.trim()));
            } catch (Exception ex) { }
        } 
        return Rv._NaN;
    }

    public final boolean isStr() {
        return this.type == Rv.STRING || this.type == Rv.STRING_OBJECT;
    }
    
    public final Rv toStr() {
        String ret;
        int t;
        switch (t = this.type) {
        case Rv.STRING:
            return this;
        case Rv.STRING_OBJECT:
            ret = this.str;
            break;
        case Rv.NUMBER:
        case Rv.NUMBER_OBJECT:
            if (this == Rv._NaN) {
                ret = "NaN";
            } else if (this.f) {
                double v = this.d;
                if (Double.isInfinite(v)) {
                    ret = v > 0 ? "Infinity" : "-Infinity";
                } else {
                    ret = Double.toString(v);
                }
            } else {
                ret = Integer.toString(this.num);
            }
            break;
        default:
            ret = t >= Rv.OBJECT ? "object"
                    : this == Rv._undefined ? "undefined"
                    : this == Rv._null ? "null" 
                    : "??";
        }
        return new Rv(ret);
    }
    
    public final Rv toPrim() {
        int t;
        if ((t = this.type) == Rv.NUMBER || t == Rv.NUMBER_OBJECT) {
            return this.toNum();
        } else if (t == Rv.STRING || t == Rv.STRING_OBJECT) {
            return this.toStr();
        }
        return Rv._undefined;
    }

    /**
     * @return
     */
    public final boolean asBool() {
        int t;
        switch (t = this.type) {
        case Rv.NUMBER:
        case Rv.NUMBER_OBJECT:
            if (this == Rv._NaN) return false;
            if (this.f) {
                double v = this.d;
                return v != 0.0 && !Double.isNaN(v);
            }
            return this.num != 0;
        case Rv.STRING:
        case Rv.STRING_OBJECT:
            return this.str.length() > 0;
        default:
            return t >= Rv.OBJECT || t != Rv.UNDEFINED;
        }
    }
    
    public final Rv evalVal(Rv callObj) {
        int t;
        if ((t = this.type) == Rv.LVALUE) {
            return this.get();
        } else if (t == Rv.SYMBOL) {
            return callObj.getByKey(this);
        }
        return this;
    }

    /**
     * Like {@link #get(String)} but uses the pre-computed hash stored on the key Rv
     * (typically a canonical SYMBOL), avoiding a String.hashCode() per access.
     */
    public final Rv getByKey(Rv key) {
        int type;
        String p = key.str;
        if (symPicKey == key && symPicGen == this.gen
                && symPicHost != null && symPicHost.gen == symPicHostGen) {
            return symPicVal;
        }
        if ((type = this.type) < Rv.OBJECT) {
            return Rv._undefined;
        }
        if (type >= Rv.CTOR_MASK && "prototype".equals(p)
                || type >= Rv.OBJECT && type < Rv.CTOR_MASK && "constructor".equals(p)) {
            return this.ctorOrProt != null ? this.ctorOrProt : Rv._undefined;
        } else if ("length".equals(p)) {
            int num = type >= Rv.ARRAY ? this.num
                    : type == Rv.STRING || type == Rv.STRING_OBJECT ? this.str.length()
                    : -1;
            if (num >= 0) return Rv.smallInt(num);
        } else if (type == Rv.UINT8_ARRAY || type == Rv.INT32_ARRAY || type == Rv.FLOAT32_ARRAY) {
            int idx = arrayIndexFromRvKey(key);
            if (idx >= 0) {
                Rv tarr;
                if (type == Rv.UINT8_ARRAY) {
                    Uint8View uv = (Uint8View) this.opaque;
                    if (idx < uv.byteLength) {
                        tarr = Rv.smallInt(uv.data[uv.offset + idx] & 0xff);
                        symPicKey = key;
                        symPicVal = tarr;
                        symPicGen = this.gen;
                        symPicHost = this;
                        symPicHostGen = this.gen;
                        return tarr;
                    }
                } else if (type == Rv.INT32_ARRAY) {
                    Int32View iv = (Int32View) this.opaque;
                    int nel = iv.byteLength >> 2;
                    if (idx < nel) {
                        tarr = new Rv(int32LoadLE(iv.data, iv.offset + (idx << 2)));
                        symPicKey = key;
                        symPicVal = tarr;
                        symPicGen = this.gen;
                        symPicHost = this;
                        symPicHostGen = this.gen;
                        return tarr;
                    }
                } else {
                    Float32View fv = (Float32View) this.opaque;
                    int nel = fv.byteLength >> 2;
                    if (idx < nel) {
                        tarr = new Rv((double) Float.intBitsToFloat(int32LoadLE(fv.data, fv.offset + (idx << 2))));
                        symPicKey = key;
                        symPicVal = tarr;
                        symPicGen = this.gen;
                        symPicHost = this;
                        symPicHostGen = this.gen;
                        return tarr;
                    }
                }
            }
        }
        if (p == null) {
            return Rv._undefined;
        }
        int ph = key.hash;
        if (ph == 0) ph = key.hash = p.hashCode();
        Rv ctor, prot, prev, obj, ret;
        for (obj = this; (ret = obj.prop.getH(ph, p)) == null
                && (ctor = obj.ctorOrProt) != null
                && (prot = ctor.ctorOrProt) != null; obj = prot)
            ;
        if (ret == null && (prev = this.prev) != null) {
            for (obj = prev; (ret = obj.prop.getH(ph, p)) == null
                    && (prev = obj.prev) != null; obj = prev)
                ;
        }
        Rv out = ret != null ? ret : Rv._undefined;
        symPicKey = key;
        symPicVal = out;
        symPicGen = this.gen;
        // Host where lookup concluded (owning object or last env record searched for missing binding).
        symPicHost = obj;
        symPicHostGen = obj.gen;
        return out;
    }
    
    public final Rv evalRef(Rv callObj) {
        return evalRef(callObj, null);
    }

    public final Rv evalRef(Rv callObj, Rv scratch) {
        Rv ret = this;
        int t;
        if ((t = ret.type) == Rv.SYMBOL) {
            ret = scratch != null ? scratch.resetTempLvalue(ret.str, callObj) : new Rv(ret.str, callObj);
        } else if (t != Rv.LVALUE) { // error
            ret = Rv._undefined;
        } // else if (t == Rv.LVALUE) do nothing
        return ret;
    }
    
    public final Rv pv() {
        int t;
        if ((t = this.type) == Rv.UNDEFINED || t >= Rv.OBJECT || this == Rv._NaN) {
            return this;
        }
        if (t == Rv.NUMBER) {
            if (this.f) {
                Rv c = new Rv(0);
                c.type = Rv.NUMBER;
                c.f = true;
                c.d = this.d;
                return c;
            }
            return new Rv(this.num);
        }
        return new Rv(this.str);
    }
    
    /**
     * apply to:
     *   '**', '+', '-', '*', '/', '%', 
     *   '<', '<=', '>', '>=', '==', '!=', '===', '!==', 
     *   '&&', '||',
     *   'in', 'instanceof'
     * @param op
     * @param rv
     * @return
     */
    public final Rv binary(int op, Rv r1, Rv r2) {
        int t1 = r1.type, t2 = r2.type;
        switch (op) {
        case RC.TOK_ADD:
            if ((t1 == Rv.NUMBER || t1 == Rv.NUMBER_OBJECT) &&
                    (t2 == Rv.NUMBER || t2 == Rv.NUMBER_OBJECT)) {
                if (!r1.f && !r2.f && r1 != Rv._NaN && r2 != Rv._NaN) {
                    long sum = (long) r1.num + (long) r2.num;
                    if (sum >= (long) Integer.MIN_VALUE && sum <= (long) Integer.MAX_VALUE) {
                        this.type = Rv.NUMBER;
                        this.f = false;
                        this.num = (int) sum;
                        return this;
                    }
                }
                double a = numValue(r1), b = numValue(r2);
                if (Double.isNaN(a) || Double.isNaN(b)) {
                    this.type = Rv.NUMBER;
                    this.setPrimitiveFromDouble(Double.NaN);
                    return this;
                }
                this.type = Rv.NUMBER;
                this.setPrimitiveFromDouble(a + b);
                return this;
            } else {
                this.type = Rv.STRING;
                this.str = r1.toStr().str + r2.toStr().str;
            }
            return this;
        case RC.TOK_IN:
            return r2.has(r1.toStr().str) ? Rv._true : Rv._false;
        case RC.TOK_INSTANCEOF:
            return r1.instanceOf(r2) ? Rv._true : Rv._false;
        case RC.TOK_IDN: // ===
            return isIden(r1, r2) ? Rv._true : Rv._false;
        case RC.TOK_EQ: // ==
            return isEq(r1, r2) ? Rv._true : Rv._false;
        case RC.TOK_NID: // !==
            return isIden(r1, r2) ? Rv._false : Rv._true;
        case RC.TOK_NE: // !=
            return isEq(r1, r2) ? Rv._false : Rv._true;
        }
        if (op >= 0 && op < RocksInterpreter.OPTR_TABLE_SIZE
                && RocksInterpreter.optrIndex[op] == 5) { // >, >=, <, <=
            if (r1 == Rv._undefined || r2 == Rv._undefined) {
                return Rv._false;
            }
            if ((t1 == Rv.NUMBER || t1 == Rv.NUMBER_OBJECT) &&
                    (t2 == Rv.NUMBER || t2 == Rv.NUMBER_OBJECT)) {
                if (!r1.f && !r2.f && r1 != Rv._NaN && r2 != Rv._NaN) {
                    int ai = r1.num, bi = r2.num;
                    switch (op) {
                    case RC.TOK_GRT:
                        return ai > bi ? Rv._true : Rv._false;
                    case RC.TOK_GE:
                        return ai >= bi ? Rv._true : Rv._false;
                    case RC.TOK_LES:
                        return ai < bi ? Rv._true : Rv._false;
                    case RC.TOK_LE:
                        return ai <= bi ? Rv._true : Rv._false;
                    }
                }
                double a = numValue(r1), b = numValue(r2);
                if (Double.isNaN(a) || Double.isNaN(b)) {
                    return Rv._false;
                }
                switch (op) {
                case RC.TOK_GRT:
                    return a > b ? Rv._true : Rv._false;
                case RC.TOK_GE:
                    return a >= b ? Rv._true : Rv._false;
                case RC.TOK_LES:
                    return a < b ? Rv._true : Rv._false;
                case RC.TOK_LE:
                    return a <= b ? Rv._true : Rv._false;
                }
            }
            String s1 = r1.toStr().str, s2 = r2.toStr().str;
            switch (op) {
            case RC.TOK_GRT:
                return s1.compareTo(s2) > 0 ? Rv._true : Rv._false;
            case RC.TOK_GE:
                return s1.compareTo(s2) >= 0 ? Rv._true : Rv._false;
            case RC.TOK_LES:
                return s1.compareTo(s2) < 0 ? Rv._true : Rv._false;
            case RC.TOK_LE:
                return s1.compareTo(s2) <= 0 ? Rv._true : Rv._false;
            }
        } else {
            // **, *, /, %, -, <<, >>, >>>, &, ^, |
            if ((r1 = r1.toNum()) == Rv._NaN || (r2 = r2.toNum()) == Rv._NaN) {
                return Rv._NaN;
            }
            double n1d = numValue(r1), n2d = numValue(r2);
            if (Double.isNaN(n1d) || Double.isNaN(n2d)) {
                this.type = Rv.NUMBER;
                this.setPrimitiveFromDouble(Double.NaN);
                return this;
            }
            switch (op) {
            case RC.TOK_MIN: {
                if (!r1.f && !r2.f && r1 != Rv._NaN && r2 != Rv._NaN) {
                    long diff = (long) r1.num - (long) r2.num;
                    if (diff >= (long) Integer.MIN_VALUE && diff <= (long) Integer.MAX_VALUE) {
                        this.type = Rv.NUMBER;
                        this.f = false;
                        this.num = (int) diff;
                        return this;
                    }
                }
                this.type = Rv.NUMBER;
                this.setPrimitiveFromDouble(n1d - n2d);
                return this;
            }
            case RC.TOK_MUL: {
                if (!r1.f && !r2.f && r1 != Rv._NaN && r2 != Rv._NaN) {
                    long prod = (long) r1.num * (long) r2.num;
                    if (prod >= (long) Integer.MIN_VALUE && prod <= (long) Integer.MAX_VALUE) {
                        this.type = Rv.NUMBER;
                        this.f = false;
                        this.num = (int) prod;
                        return this;
                    }
                }
                this.type = Rv.NUMBER;
                this.setPrimitiveFromDouble(n1d * n2d);
                return this;
            }
            case RC.TOK_DIV: {
                this.type = Rv.NUMBER;
                this.setPrimitiveFromDouble(n1d / n2d);
                return this;
            }
            case RC.TOK_MOD: {
                this.type = Rv.NUMBER;
                this.setPrimitiveFromDouble(jsRemainder(n1d, n2d));
                return this;
            }
            case RC.TOK_POW: {
                this.type = Rv.NUMBER;
                this.setPrimitiveFromDouble(CldcMath.pow(n1d, n2d));
                return this;
            }
            case RC.TOK_LSH: {
                int a32 = toInt32(n1d), b32 = toInt32(n2d) & 31;
                this.type = Rv.NUMBER;
                this.f = false;
                this.num = a32 << b32;
                return this;
            }
            case RC.TOK_RSH: {
                int a32 = toInt32(n1d), b32 = toInt32(n2d) & 31;
                this.type = Rv.NUMBER;
                this.f = false;
                this.num = a32 >> b32;
                return this;
            }
            case RC.TOK_RSZ: {
                int a32 = toInt32(n1d), b32 = toInt32(n2d) & 31;
                this.type = Rv.NUMBER;
                this.f = false;
                this.num = a32 >>> b32;
                return this;
            }
            case RC.TOK_BAN: {
                int a32 = toInt32(n1d), b32 = toInt32(n2d);
                this.type = Rv.NUMBER;
                this.f = false;
                this.num = a32 & b32;
                return this;
            }
            case RC.TOK_BXO: {
                int a32 = toInt32(n1d), b32 = toInt32(n2d);
                this.type = Rv.NUMBER;
                this.f = false;
                this.num = a32 ^ b32;
                return this;
            }
            case RC.TOK_BOR: {
                int a32 = toInt32(n1d), b32 = toInt32(n2d);
                this.type = Rv.NUMBER;
                this.f = false;
                this.num = a32 | b32;
                return this;
            }
            }
        }
        return Rv._undefined; // never happens
    }
    
    /**
     * apply to '++', '--', '++(p)', '--(p)', NEG, POS, typeof, delete
     * @param op
     * @return
     */
    public final Rv unary(Rv callObj, int op, Rv rv) {
        return unary(callObj, op, rv, null);
    }

    public final Rv unary(Rv callObj, int op, Rv rv, Rv refScratch) {
        switch (op) {
        case RC.TOK_NEG:
        case RC.TOK_POS:
            if ((rv = rv.evalVal(callObj).toNum()) == Rv._NaN) return Rv._NaN;
            this.type = Rv.NUMBER;
            if (rv.f) {
                this.f = true;
                this.d = op == RC.TOK_NEG ? -rv.d : rv.d;
            } else {
                this.f = false;
                this.num = op == RC.TOK_NEG ? -rv.num : rv.num;
            }
            break;
        case RC.TOK_INC:
        case RC.TOK_DEC:
        case RC.TOK_POSTINC:
        case RC.TOK_POSTDEC:
            rv = rv.evalRef(callObj, refScratch);
            if (rv == Rv._undefined) return Rv._NaN;
            Rv prop;
            if ((prop = rv.get()) == Rv._undefined || (prop = prop.toNum()) == Rv._NaN) return Rv._NaN;
            int delta = (op == RC.TOK_INC || op == RC.TOK_POSTINC) ? 1 : -1;
            if (!prop.f) {
                int oldi = prop.num;
                long newL = (long) oldi + (long) delta;
                if (newL >= (long) Integer.MIN_VALUE && newL <= (long) Integer.MAX_VALUE) {
                    prop.type = Rv.NUMBER;
                    prop.f = false;
                    prop.num = (int) newL;
                    rv.put(prop);
                    if (op == RC.TOK_INC || op == RC.TOK_DEC) {
                        return prop;
                    }
                    this.type = Rv.NUMBER;
                    this.f = false;
                    this.num = oldi;
                    break;
                }
            }
            double oldv = numValue(prop);
            if (Double.isNaN(oldv)) return Rv._NaN;
            double newv = oldv + (double) delta;
            prop.type = Rv.NUMBER;
            prop.setPrimitiveFromDouble(newv);
            rv.put(prop); // rv is a lvalue
            if (op == RC.TOK_INC || op == RC.TOK_DEC) {
                return prop;
            }
            this.type = Rv.NUMBER;
            this.setPrimitiveFromDouble(oldv);
            break; // postinc or postdec
        case RC.TOK_NOT:
            return rv.evalVal(callObj).asBool() ? Rv._false : Rv._true;
        case RC.TOK_BNO:
            if ((rv = rv.evalVal(callObj).toNum()) == Rv._NaN) return Rv._NaN;
            this.type = Rv.NUMBER;
            this.f = false;
            this.num = ~toInt32(numValue(rv));
            break;
        case RC.TOK_TYPEOF:
            this.type = Rv.STRING;
            this.str = rv.evalVal(callObj).typeOf();
            break;
        case RC.TOK_AWAIT:
            return Rv.error("await: use async function with a simple sequential body (preprocessor), or use .then()");
        case RC.TOK_DELETE:
            rv = rv.evalRef(callObj, refScratch);
            Rv arr = rv.co;
            if (rv != Rv._undefined) {
                arr.prop.removeAndRelease(0, rv.str);
                ++arr.gen;
            }
            if (arr.type == Rv.ARRAY) {
                try {
                    int idx = Integer.parseInt(rv.str);
                    if (idx < rv.co.num) arr.shift(idx);
                } catch (Exception ex) { }
            }
            return Rv._true;
        }
        return this;
    }
    
    /**
     * apply to ':', invoke, init, jsonarr, jsonobj
     * @param op
     * @param opnd
     * @param num
     * @return
     */
    public static final Rv polynary(Rv callObj, int op, Pack opnd, int num) {
        int idx = opnd.oSize - num;
        switch (op) {
        case RC.TOK_COL: // ... ? ... : ...
            boolean cond = ((Rv) opnd.getObject(idx)).evalVal(callObj).asBool();
            return ((Rv) opnd.getObject(idx + (cond ? 1 : 2))).evalVal(callObj);
        case RC.TOK_JSONARR:
            Rv arr = new Rv(Rv.ARRAY, Rv._Array);
            for (int i = idx, j = 0, n = opnd.oSize - 1; i < n; i++, j++) {
                arr.putl(j, ((Rv) opnd.getObject(i)).evalVal(callObj));
            }
            return arr;
        case RC.TOK_LBR: // json_object
            Rv obj = new Rv(Rv.OBJECT, Rv._Object);
            for (int i = idx, n = opnd.oSize - 1; i < n; i += 2) {
                Rv k = (Rv) opnd.getObject(i);
                String ks;
                if (k.type == Rv.NUMBER) {
                    ks = k.f ? Double.toString(k.d) : intStr(k.num);
                } else {
                    ks = k.str;
                }
                Rv v = ((Rv) opnd.getObject(i + 1)).evalVal(callObj);
                obj.putl(ks, v);
            }
            return obj;
        }
        return Rv._undefined; // never happens
    }
    
    /**
     * apply to '=', '+=', '-=', '*=', '/=', '%='
     * @param op
     * @param rv
     * @return
     */
    public final Rv assign(Rv callObj, int op, Rv r1, Rv r2) {
        if (r1.type != Rv.LVALUE) return Rv._undefined;
        Rv val;
        boolean isass;
        if (isass = op == RC.TOK_ASS) {
            val = r2;
        } else {
            switch (op) {
            case RC.TOK_LSA:
                op = RC.TOK_LSH;
                break;
            case RC.TOK_RSA:
                op = RC.TOK_RSH;
                break;
            case RC.TOK_RZA:
                op = RC.TOK_RSZ;
                break;
            default:
                op -= RC.ASS_START;
                break;
            }
            val = this.binary(op, r1.evalVal(callObj), r2);
        }
        r1.put(val); // r1 is a lvalue
        return isass ? r1 : val;
    }
    
    public String typeOf() {
        int t;
        switch (t = this.type) {
        case Rv.NUMBER:
            return "number";
        case Rv.STRING:
            return "string";
        default:
            return t >= Rv.FUNCTION ? "function" 
                    : t >= Rv.OBJECT ? "object" : "undefined";
        }
    }
    
    public final boolean instanceOf(Rv rv) {
        int t;
        boolean found = false;
        if (rv.type > Rv.CTOR_MASK && (t = this.type) >= Rv.OBJECT && t < Rv.CTOR_MASK) {
            Rv ctor, prot, left, right;
            for (left = this.ctorOrProt, right = rv; !(found = left == right)
                    && (prot = left.ctorOrProt) != null
                    && (ctor = prot.ctorOrProt) != null; left = ctor)
                ;
        }
        return found;
    }
    
    public final boolean equals(Object o) {
        if (this == o) return true;
        Rv rv;
        if (this.type != (rv = (Rv) o).type) return false;
        boolean ret = false;
        switch (rv.type) {
        case Rv.NUMBER:
            if (this == Rv._NaN || rv == Rv._NaN) return false;
            if (!this.f && !rv.f) return this.num == rv.num;
            return sameNumberStrict(numValue(this), numValue(rv));
        case Rv.STRING:
        case Rv.SYMBOL:
            return this.str.equals(rv.str);
        case Rv.LVALUE:
            return this.str.equals(rv.str) && this.co == rv.co;
        default: // Object etc.
            ret = this.ctorOrProt == rv.ctorOrProt && this.prop.equals(rv.prop);
        }
        if (!ret) return false;
        switch (rv.type) {
        case Rv.ARRAY:
        case Rv.ARGUMENTS:
        case Rv.NUMBER_OBJECT:
            return this.f == rv.f && (this.f ? this.d == rv.d : this.num == rv.num);
        case Rv.NATIVE:
        case Rv.NATIVE | Rv.CTOR_MASK:
        case Rv.STRING_OBJECT:
            return this.str.equals(rv.str);
        case Rv.FUNCTION:
        case Rv.FUNCTION | Rv.CTOR_MASK:
            return this.obj == rv.obj;
        }
        return false;
    }
    
    public String toString() {
        String s = "";
        StringBuffer buf = new StringBuffer();
        switch (this.type) {
        case Rv.UNDEFINED:
            return this == Rv._undefined ? "undefined" : this == Rv._null ? "null" : "error";
        case Rv.NUMBER_OBJECT:
            s = "_object";
        case Rv.NUMBER:
            return "number" + s + "(" + (this.f ? Double.toString(this.d) : Integer.toString(this.num)) + ")";
        case Rv.STRING_OBJECT:
            s = "_object";
        case Rv.STRING:
            return "string" + s + "(" + this.str + ")";
        case Rv.SYMBOL:
            return "symbol(" + this.str + ")";
        case Rv.LVALUE:
            return "lvalue(" + this.co + "." + this.str + ")";
        case Rv.OBJECT:
            buf.append("object{");
            Rhash ht = this.prop;
            Pack keys = ht.keys();
            for (int i = 0, n = keys.oSize; i < n; i++) {
                if (i > 0) buf.append(", ");
                String k = (String) keys.oArray[i]; // String
                Rv v = ht.get(k);       // Rv
                buf.append(k).append(": ").append(v.toStr().str);
            }
            return buf.append('}').toString();
        case Rv.ARGUMENTS:
            s = "arguments";
        case Rv.ARRAY:
            if (s.length() == 0) s = "array";
            buf.append(s);
            ht = this.prop;
            int len = this.num;
            buf.append('(').append(len).append(")[");
            for (int i = 0; i < len; i++) {
                if (i > 0) buf.append(", ");
                Object elem = ht.get(intStr(i));
                buf.append(elem);
            }
            return buf.append(']').toString();
        case Rv.FUNCTION:
        case Rv.FUNCTION | Rv.CTOR_MASK:
            return "function(" + this.num + ")$" + this.obj + "$";
        case Rv.NATIVE:
        case Rv.NATIVE | Rv.CTOR_MASK:
            return "native(" + this.num + ")$" + this.str + "$"; 
        case Rv.ERROR:
            return "error('" + this.get("message") + "')";
        default:
            return "unknown(" + this.type + ")";
        }
    }
    
    static int int32LoadLE(byte[] b, int pos) {
        int b0 = b[pos] & 0xff;
        int b1 = b[pos + 1] & 0xff;
        int b2 = b[pos + 2] & 0xff;
        int b3 = b[pos + 3] & 0xff;
        return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    static void int32StoreLE(byte[] b, int pos, int v) {
        b[pos] = (byte) (v & 0xff);
        b[pos + 1] = (byte) ((v >> 8) & 0xff);
        b[pos + 2] = (byte) ((v >> 16) & 0xff);
        b[pos + 3] = (byte) ((v >> 24) & 0xff);
    }

    /**
     * Cached Integer.toString for small indices used as array/arguments keys.
     * Avoids allocating a new String on every array push/shift/get in hot loops.
     */
    static final int INT_STR_CACHE_SIZE = 512;
    public static final String[] INT_STR = new String[INT_STR_CACHE_SIZE];
    static {
        for (int i = 0; i < INT_STR_CACHE_SIZE; i++) {
            INT_STR[i] = Integer.toString(i);
        }
    }

    public static final String intStr(int i) {
        return (i >= 0 && i < INT_STR_CACHE_SIZE) ? INT_STR[i] : Integer.toString(i);
    }

    /**
     * Fast non-negative int parse for array/typed-array index keys (avoids
     * {@code Integer.parseInt} exception path on hot {@code arr[i]} sites).
     */
    static int arrayIndexKey(String p) {
        if (p == null) {
            return -1;
        }
        int pl = p.length();
        if (pl == 0) {
            return -1;
        }
        int v = 0;
        for (int i = 0; i < pl; i++) {
            char c = p.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
            v = v * 10 + (c - '0');
            if (v < 0) {
                return -1;
            }
        }
        return v;
    }

    /**
     * Typed-array / string index from a key {@link Rv}: numeric keys avoid string
     * parsing; string keys reuse {@link #arrayIndexKey(String)}.
     */
    static int arrayIndexFromRvKey(Rv key) {
        if (key == null) {
            return -1;
        }
        if (key.type == Rv.NUMBER) {
            if (key.f) {
                double d = key.d;
                if (d < 0.0 || Double.isNaN(d)) {
                    return -1;
                }
                int vi = (int) d;
                if ((double) vi != d) {
                    return -1;
                }
                return vi;
            }
            return key.num >= 0 ? key.num : -1;
        }
        return arrayIndexKey(key.str);
    }

    private static final Rv[] ASCII_CHAR_RV = new Rv[128];

    /** One-character string values for ASCII (cached). */
    public static Rv asciiCharRv(char c) {
        if (c < 128) {
            Rv r = ASCII_CHAR_RV[c];
            if (r == null) {
                r = new Rv(String.valueOf(c));
                ASCII_CHAR_RV[c] = r;
            }
            return r;
        }
        return new Rv(String.valueOf(c));
    }

    /** Small non-negative integers & common negatives: use {@link #_SmallInt} when in range. */
    public static final Rv smallInt(int v) {
        if (v >= 0 && v < 256) {
            return _SmallInt[v];
        }
        return new Rv(v);
    }

    public final boolean isCallable() {
        int t = this.type & ~Rv.CTOR_MASK;
        return t == Rv.FUNCTION || t == Rv.NATIVE;
    }

    /**
     * Positional argument accessor for {@link NativeFunctionFast} bindings.
     * Returns {@link #_undefined} when the caller passed fewer arguments than expected.
     */
    public static final Rv argAt(net.cnjm.j2me.util.Pack args, int start, int num, int i) {
        if (args == null || i >= num) return _undefined;
        Rv a = (Rv) args.getObject(start + i);
        return a != null ? a : _undefined;
    }

    public static final Rv _null;
    public static final Rv _undefined;
    public static final Rv _NaN;
    public static final Rv _true;
    public static final Rv _false;
    /** Canonical {@code Rv(NUMBER)} for 0..255 (inclusive); reduces per-frame allocation in hot native returns. */
    public static final Rv[] _SmallInt;
    public static final Rv _empty;
    public static final Rv _Object;
    public static final Rv _Function;
    public static final Rv _Array;
    public static final Rv _Arguments;
    public static final Rv _Number;
    public static final Rv _String;
    public static final Rv _Date;
    public static final Rv _Error;
    /** ES6 Map constructor slot (initialized lazily by StdLib). */
    public static Rv _Map;
    /** ES6 Set constructor slot (initialized lazily by StdLib). */
    public static Rv _Set;
    /** ES6 Symbol constructor slot (initialized lazily by StdLib). */
    public static Rv _Symbol;
    /** ArrayBuffer constructor (initialized lazily by StdLib). */
    public static Rv _ArrayBuffer;
    /** Uint8Array constructor (initialized lazily by StdLib). */
    public static Rv _Uint8Array;
    /** Int32Array constructor (initialized lazily by StdLib). */
    public static Rv _Int32Array;
    public static Rv _Float32Array;
    /** DataView constructor (initialized lazily by StdLib). */
    public static Rv _DataView;
    /** Promise constructor (initialized lazily by StdLib). */
    public static Rv _Promise;

    /** Backing store for {@code ArrayBuffer} instances ({@code opaque}). */
    public static final class ArrayBufferBacking {
        public final byte[] data;
        public ArrayBufferBacking(byte[] data) {
            this.data = data;
        }
    }

    /** Shared byte view for {@code Uint8Array} ({@code opaque} when {@link #type} is {@link #UINT8_ARRAY}). */
    public static final class Uint8View {
        public final byte[] data;
        public final int offset;
        public final int byteLength;
        public final Rv bufferRv;
        public Uint8View(byte[] data, int offset, int byteLength, Rv bufferRv) {
            this.data = data;
            this.offset = offset;
            this.byteLength = byteLength;
            this.bufferRv = bufferRv;
        }
    }

    /**
     * View for {@code Int32Array} ({@code opaque} when {@link #type} is {@link #INT32_ARRAY}).
     * {@code byteLength} is always a multiple of 4; elements are little-endian.
     */
    public static final class Int32View {
        public final byte[] data;
        public final int offset;
        public final int byteLength;
        public final Rv bufferRv;
        public Int32View(byte[] data, int offset, int byteLength, Rv bufferRv) {
            this.data = data;
            this.offset = offset;
            this.byteLength = byteLength;
            this.bufferRv = bufferRv;
        }
    }

    /**
     * View for {@code Float32Array} ({@link #type} is {@link #FLOAT32_ARRAY}).
     * Layout matches {@link Int32View} (IEEE-754 little-endian per element).
     */
    public static final class Float32View {
        public final byte[] data;
        public final int offset;
        public final int byteLength;
        public final Rv bufferRv;
        public Float32View(byte[] data, int offset, int byteLength, Rv bufferRv) {
            this.data = data;
            this.offset = offset;
            this.byteLength = byteLength;
            this.bufferRv = bufferRv;
        }
    }

    /** Byte range for {@code DataView} instances ({@code opaque}). */
    public static final class DataViewState {
        public final byte[] data;
        public final int offset;
        public final int byteLength;
        public final Rv bufferRv;
        public DataViewState(byte[] data, int offset, int byteLength, Rv bufferRv) {
            this.data = data;
            this.offset = offset;
            this.byteLength = byteLength;
            this.bufferRv = bufferRv;
        }
    }

    /**
     * Plain JS array of three numbers (e.g. translation {@code [x,y,z]}) for native bindings
     * outside this package; {@link #ARRAY} and {@link #putl(int, Rv)} are not public.
     */
    public static Rv newJsArray3(double x, double y, double z) {
        Rv a = new Rv(Rv.ARRAY, Rv._Array);
        a.putl(0, new Rv(x));
        a.putl(1, new Rv(y));
        a.putl(2, new Rv(z));
        a.num = 3;
        return a;
    }

    static {
        for (int i = 0; i < RHASH_ENTRY_SLAB_CAP; i++) {
            RHASH_ENTRY_SLAB[i] = new Rv();
        }
        rhashEntryTop = RHASH_ENTRY_SLAB_CAP;
        (_null = new Rv(0)).type = Rv.UNDEFINED;
        (_undefined = new Rv(0)).type = Rv.UNDEFINED;
        _NaN = new Rv(0);
        _true = new Rv(1);
        _false = new Rv(0);
        _SmallInt = new Rv[256];
        for (int i = 0; i < 256; i++) {
            _SmallInt[i] = new Rv(i);
        }
        _empty = new Rv("");
        _Object = new Rv();
        _Function = new Rv();
        _Array = new Rv();
        _Arguments = new Rv();
        _Number = new Rv();
        _String = new Rv();
        _Date = new Rv();
        _Error = new Rv();
     }
    
    private static final boolean isEq(Rv r1, Rv r2) {
        int t1, t2;
        if ((t1 = r1.type) == (t2 = r2.type)) {
            switch (t1) {
            case Rv.UNDEFINED: return true;
            case Rv.NUMBER: {
                if (r1 == Rv._NaN || r2 == Rv._NaN) return false;
                if (!r1.f && !r2.f) return r1.num == r2.num;
                double a = numValue(r1), b = numValue(r2);
                if (Double.isNaN(a) || Double.isNaN(b)) return false;
                return a == b;
            }
            case Rv.STRING: return r1.str.equals(r2.str);
            default: return r1.equals(r2);
            }
        }
        if (t1 == Rv.UNDEFINED || t2 == Rv.UNDEFINED) return false;
        boolean first;
        if ((first = t1 == Rv.NUMBER) && t2 == Rv.STRING || t1 == Rv.STRING && t2 == Rv.NUMBER) {
            Rv ns = first ? r2.toNum() : r1.toNum();
            if (ns == Rv._NaN) return false;
            Rv nn = first ? r1 : r2;
            double a = numValue(ns), b = numValue(nn);
            if (Double.isNaN(a) || Double.isNaN(b)) return false;
            return a == b;
        }
        if ((first = t1 <= Rv.STRING) && t2 >= Rv.OBJECT || t1 >= Rv.OBJECT && t2 <= Rv.STRING) {
            Rv po = first ? r2.toPrim() : r1.toPrim();
            if (po == Rv._undefined) return false;
            Rv pp = first ? r1 : r2;
            return pp.equals(po);
        }
        return false;
    }
    
    private static final boolean isIden(Rv r1, Rv r2) {
        // Strict equality (===): same type AND same value. The upstream RockScript
        // had the condition inverted (returned false when types matched), which
        // silently broke every `a === literal` check in user code.
        int t1;
        if ((t1 = r1.type) != r2.type) return false;
        switch (t1) {
        case Rv.UNDEFINED: return true;
        case Rv.NUMBER:
            if (r1 == Rv._NaN || r2 == Rv._NaN) return false;
            if (!r1.f && !r2.f) return r1.num == r2.num;
            return sameNumberStrict(numValue(r1), numValue(r2));
        case Rv.STRING:    return r1.str.equals(r2.str);
        default:           return r1 == r2;
        }
    }
    
}
