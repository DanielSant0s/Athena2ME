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
    
    static final int FUNCTION =         0x2C;
    static final int NATIVE =           0x2D; // native function
    static final int CTOR_MASK =        0x40;
    
    public int type;

    /** Pre-computed String.hashCode() for SYMBOL/STRING values used as map keys.
     *  0 means "not cached yet". Saves work on every Rhash lookup in the hot path. */
    public int hash;

    /** Structural generation counter. Bumped every time a property is added or
     *  replaced via {@link #putl}. Used by the monomorphic inline cache in
     *  LVALUE nodes to validate whether a previously memoised lookup result is
     *  still fresh. */
    public int gen;

    /** Inline cache slots, only meaningful when {@code type == LVALUE}. Store the
     *  last resolved value, the holder it was fetched from, the property key we
     *  looked up, the Rhash we found it in, and the Rhash's {@link Rhash#gen} at
     *  resolution time. Treated as a best-effort hint: a miss just falls through
     *  to the normal chain-walking lookup.
     *
     *  <p>Both {@code icRhash} identity and {@code icStamp} are required: holders
     *  like arrays may wholesale-replace their backing {@link Rhash} (e.g.
     *  {@code Array.unshift}/{@code sort}/{@code reverse}) and the replacement
     *  Rhash starts its own {@code gen} counter from 0 — so the stamp check
     *  alone can spuriously match on a structurally unrelated map. */
    public Rv icHolder;
    public Rv icValue;
    public String icKey;
    public Rhash icRhash;
    public int icStamp;
    
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
    
    /**
     * To create a number
     * @param n
     */
    public Rv(int n) {
        this.type = Rv.NUMBER;
        this.num = n;
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
     * Canonical SYMBOL pool. Ensures that a given identifier string always maps
     * to the same Rv, so comparisons can short-circuit to pointer equality and
     * the hashCode is computed once per symbol name.
     */
    private static final java.util.Hashtable _symbolPool = new java.util.Hashtable();

    public static final Rv symbol(String s) {
        Rv ret = (Rv) _symbolPool.get(s);
        if (ret == null) {
            ret = new Rv(s);
            ret.type = Rv.SYMBOL;
            ret.hash = s.hashCode();
            _symbolPool.put(s, ret);
        }
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
            this.co = newco;
        }
        // ---- monomorphic inline cache ----
        // Identical holder + same backing Rhash + unchanged gen + same key
        // => return cached value. We compare Rhash identity (not just the gen
        // stamp) because mutating natives like Array.unshift/sort/reverse
        // replace the holder's Rhash entirely, and the replacement can happen
        // to carry the same `gen` value as the map that was cached against.
        Rv holder = this.co;
        String key = this.str;
        Rhash hp;
        if (this.icHolder == holder
                && this.icValue != null
                && (hp = holder.prop) != null
                && this.icRhash == hp
                && this.icStamp == hp.gen
                && (this.icKey == key || (this.icKey != null && this.icKey.equals(key)))) {
            return this.icValue;
        }
        Rv v = holder.get(key);
        this.icHolder = holder;
        this.icRhash = (hp = holder.prop);
        this.icStamp = hp != null ? hp.gen : 0;
        this.icKey = key;
        this.icValue = v;
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
                return new Rv(this.str.length());
            }
            int pl = p.length();
            if (pl > 0) {
                char c0 = p.charAt(0);
                if (c0 >= '0' && c0 <= '9') {
                    try {
                        int idx = Integer.parseInt(p);
                        if (idx >= 0 && idx < this.str.length()) {
                            return new Rv(String.valueOf(this.str.charAt(idx)));
                        }
                    } catch (NumberFormatException ex) { /* non-numeric, fall through */ }
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
                this.prop.remove(p.hashCode(), p);
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
            if (num >= 0) return new Rv(num);
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
                if ((newNum = val.num) < o.num) { // trim array
                    Rhash prop = o.prop;
                    for (int i = o.num; --i >= newNum; prop.remove(0, intStr(i)));
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
            }
            Rv ret;
            for (; (ret = obj.prop.get(p)) == null && (prev = obj.prev) != null; obj = prev);
            Rv target = ret == null ? o : obj;
            target.prop.put(p, val);
            ++target.gen;
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
        // The monomorphic IC on `array.length` validates via Rhash gen, so a
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
        case Rv.NUMBER_OBJECT:
            return new Rv(this.num);
        case Rv.STRING:
        case Rv.STRING_OBJECT:
            try {
                return new Rv(Integer.parseInt(this.str));
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
            ret = this == Rv._NaN ? "NaN" : Integer.toString(this.num);
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
            return this.num != 0; // for NaN.num == 0, +/-Infinity.num != 0
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
            if (num >= 0) return new Rv(num);
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
        return ret != null ? ret : Rv._undefined;
    }
    
    public final Rv evalRef(Rv callObj) {
        Rv ret = this;
        int t;
        if ((t = ret.type) == Rv.SYMBOL) {
            ret = new Rv(ret.str, callObj);
        } else if (t != Rv.LVALUE) { // error
            ret = Rv._undefined;
        } // else if (t == Rv.LVALUE) do nothing
        return ret;
    }
    
    public final Rv pv() {
        int t;
        return (t = this.type) == Rv.UNDEFINED || t >= Rv.OBJECT || this == Rv._NaN
                ? this // object type, pass by reference
                : (t == Rv.NUMBER ? new Rv(this.num) : new Rv(this.str)); // primary type, pass by value
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
                this.type = Rv.NUMBER;
                this.num = r1.num + r2.num;
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
            if (r1 == Rv._undefined || r2 == Rv._undefined 
                    || r1 == Rv._NaN || r2 == Rv._NaN) return Rv._NaN;
            if ((t1 == Rv.NUMBER || t1 == Rv.NUMBER_OBJECT) &&
                    (t2 == Rv.NUMBER || t2 == Rv.NUMBER_OBJECT)) {
                switch (op) {
                case RC.TOK_GRT:
                    return r1.num > r2.num ? Rv._true : Rv._false;
                case RC.TOK_GE:
                    return r1.num >= r2.num ? Rv._true : Rv._false;
                case RC.TOK_LES:
                    return r1.num < r2.num ? Rv._true : Rv._false;
                case RC.TOK_LE:
                    return r1.num <= r2.num ? Rv._true : Rv._false;
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
            if ((r1 = r1.toNum()) == Rv._NaN || (r2 = r2.toNum()) == Rv._NaN) return Rv._NaN;
            int n1 = r1.num, n2 = r2.num;
            switch (op) {
            case RC.TOK_MIN:
                n1 -= n2;
                break;
            case RC.TOK_MUL:
                n1 *= n2;
                break;
            case RC.TOK_DIV:
                if (n2 == 0) return Rv._NaN;
                n1 /= n2;
                break;
            case RC.TOK_MOD:
                if (n2 == 0) return Rv._NaN;
                n1 %= n2;
                break;
            case RC.TOK_POW:
                int n, i;
                for (n = n1, n1 = 1, i = n2; --i >= 0; n1 *= n);
                break;
            case RC.TOK_LSH:
                n1 <<= n2;
                break;
            case RC.TOK_RSH:
                n1 >>= n2;
                break;
            case RC.TOK_RSZ:
                n1 >>>= n2;
                break;
            case RC.TOK_BAN:
                n1 &= n2;
                break;
            case RC.TOK_BXO:
                n1 ^= n2;
                break;
            case RC.TOK_BOR:
                n1 |= n2;
                break;
            }
            this.num = n1;
            return this;
        }
        return Rv._undefined; // never happens
    }
    
    /**
     * apply to '++', '--', '++(p)', '--(p)', NEG, POS, typeof, delete
     * @param op
     * @return
     */
    public final Rv unary(Rv callObj, int op, Rv rv) {
        switch (op) {
        case RC.TOK_NEG:
        case RC.TOK_POS:
            if ((rv = rv.evalVal(callObj).toNum()) == Rv._NaN) return Rv._NaN;
            this.num = op == RC.TOK_NEG ? -rv.num : rv.num;
            break;
        case RC.TOK_INC:
        case RC.TOK_DEC:
        case RC.TOK_POSTINC:
        case RC.TOK_POSTDEC:
            rv = rv.evalRef(callObj);
            if (rv == Rv._undefined) return Rv._NaN;
            Rv prop;
            if ((prop = rv.get()) == Rv._undefined || (prop = prop.toNum()) == Rv._NaN) return Rv._NaN;
            int n = prop.num;
            if (op == RC.TOK_INC || op == RC.TOK_POSTINC) {
                prop.num++;
            } else {
                prop.num--;
            }
            rv.put(prop); // rv is a lvalue
            if (op == RC.TOK_INC || op == RC.TOK_DEC) {
                return prop;
            }
            this.num = n;
            break; // postinc or postdec
        case RC.TOK_NOT:
            return rv.evalVal(callObj).asBool() ? Rv._false : Rv._true;
        case RC.TOK_BNO:
            if ((rv = rv.evalVal(callObj).toNum()) == Rv._NaN) return Rv._NaN;
            this.num = ~rv.num;
            break;
        case RC.TOK_TYPEOF:
            this.type = Rv.STRING;
            this.str = rv.evalVal(callObj).typeOf();
            break;
        case RC.TOK_DELETE:
            rv = rv.evalRef(callObj);
            Rv arr = rv.co;
            if (rv != Rv._undefined) {
                arr.prop.remove(0, rv.str);
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
                String ks = k.type == Rv.NUMBER ? intStr(k.num) : k.str;
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
            return this.num == rv.num;
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
            return this.num == rv.num;
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
            return "number" + s + "(" + this.num + ")";
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
    
    static {
        (_null = new Rv(0)).type = Rv.UNDEFINED;
        (_undefined = new Rv(0)).type = Rv.UNDEFINED;
        _NaN = new Rv(0);
        _true = new Rv(1);
        _false = new Rv(0);
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
            case Rv.NUMBER: return r1 != Rv._NaN && r2 != Rv._NaN && r1.num == r2.num;
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
            return ns.num == nn.num;
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
        case Rv.NUMBER:    return r1 != Rv._NaN && r2 != Rv._NaN && r1.num == r2.num;
        case Rv.STRING:    return r1.str.equals(r2.str);
        default:           return r1 == r2;
        }
    }
    
}
