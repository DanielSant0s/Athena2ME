package net.cnjm.j2me.tinybro;

import net.cnjm.j2me.util.Pack;

/**
 * Minimal synchronous generator support: {@code function*}, {@code yield},
 * iterator objects with {@code next()} returning {@code { value, done }}.
 */
public final class GeneratorRuntime {

    private GeneratorRuntime() {}

    /** Returned from {@link RocksInterpreter#call} when execution suspends on {@code yield}. */
    public static final Rv SUSPEND = Rv.symbol("@@generatorSuspend@@");

    public static final class State {
        public final RocksInterpreter ri;
        public final Rv genFunc;
        /** Root activation object (arguments, this, locals) — pinned until done. */
        public Rv rootFunCo;
        /** Innermost scope chain head at last suspend (same as {@link RocksInterpreter} {@code funCo}). */
        public Rv funCoLeaf;
        public boolean started;
        public boolean done;
        public Rv lastYield;
        public Rv finalValue;
        public Node resumeNode;
        public int resumeIdx;
        public Pack savedStack;
        public boolean released;

        State(RocksInterpreter ri, Rv genFunc, Rv rootFunCo) {
            this.ri = ri;
            this.genFunc = genFunc;
            this.rootFunCo = rootFunCo;
            this.funCoLeaf = rootFunCo;
        }
    }

    static Pack copyPack(Pack src) {
        if (src == null) {
            return new Pack(8, 8);
        }
        Pack d = new Pack(Math.max(8, src.iSize), Math.max(8, src.oSize));
        if (src.iSize > 0) {
            System.arraycopy(src.iArray, 0, d.iArray, 0, src.iSize);
        }
        if (src.oSize > 0) {
            System.arraycopy(src.oArray, 0, d.oArray, 0, src.oSize);
        }
        d.iSize = src.iSize;
        d.oSize = src.oSize;
        return d;
    }

    static void restorePack(Pack dst, Pack src) {
        dst.iSize = 0;
        dst.oSize = 0;
        if (src == null) {
            return;
        }
        if (dst.iArray == null || dst.iArray.length < src.iSize) {
            dst.iArray = new int[Math.max(20, src.iSize)];
        }
        if (dst.oArray == null || dst.oArray.length < src.oSize) {
            dst.oArray = new Object[Math.max(20, src.oSize)];
        }
        if (src.iSize > 0) {
            System.arraycopy(src.iArray, 0, dst.iArray, 0, src.iSize);
        }
        if (src.oSize > 0) {
            System.arraycopy(src.oArray, 0, dst.oArray, 0, src.oSize);
        }
        dst.iSize = src.iSize;
        dst.oSize = src.oSize;
    }

    public static Rv createIterator(RocksInterpreter ri, Rv genFunc, Rv rootFunCo) {
        ri.captureScopeChain(rootFunCo);
        State st = new State(ri, genFunc, rootFunCo);
        Rv ctor = new Rv();
        ctor.type = Rv.FUNCTION | Rv.CTOR_MASK;
        ctor.ctorOrProt = Rv._GeneratorIterProto;
        ctor.prop = new Rhash(3);
        Rv it = new Rv(Rv.OBJECT, ctor);
        it.opaque = st;
        return it;
    }

    static Rv iteratorResult(Rv value, boolean done) {
        Rv o = new Rv(Rv.OBJECT, Rv._Object);
        o.putl("value", value != null ? value : Rv._undefined);
        o.putl("done", done ? Rv._true : Rv._false);
        return o;
    }

    static void releaseChain(RocksInterpreter ri, State st) {
        if (st == null || st.released) {
            return;
        }
        st.released = true;
        Rv leaf = st.funCoLeaf != null ? st.funCoLeaf : st.rootFunCo;
        Rv root = st.rootFunCo;
        for (Rv e = leaf; e != null && e != root; ) {
            Rv p = e.prev;
            if (e.opaque == RocksInterpreter.CAPTURED_CALL_OBJECT) {
                e.opaque = RocksInterpreter.ACTIVE_CALL_OBJECT;
            }
            ri.recycleCallObject(e);
            e = p;
        }
        if (root != null) {
            if (root.opaque == RocksInterpreter.CAPTURED_CALL_OBJECT) {
                root.opaque = RocksInterpreter.ACTIVE_CALL_OBJECT;
            }
            ri.recycleCallObject(root);
        }
        st.rootFunCo = null;
        st.funCoLeaf = null;
        st.savedStack = null;
    }

    public static Rv nativeNext(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
        if (thiz == null || !(thiz.opaque instanceof State)) {
            return Rv.error("Generator iterator expected");
        }
        State st = (State) thiz.opaque;
        if (st.done) {
            return iteratorResult(Rv._undefined, true);
        }
        ri.generatorResumeContext = st;
        try {
            Rv r = ri.call(false, st.genFunc, st.funCoLeaf, null, args, start, num);
            if (r == SUSPEND) {
                return iteratorResult(st.lastYield != null ? st.lastYield : Rv._undefined, false);
            }
            if (r != null && r.type == Rv.ERROR) {
                GeneratorRuntime.releaseChain(ri, st);
                return r;
            }
            if (st.done) {
                GeneratorRuntime.releaseChain(ri, st);
                return iteratorResult(st.finalValue != null ? st.finalValue : Rv._undefined, true);
            }
            st.done = true;
            st.finalValue = r != null ? r : Rv._undefined;
            GeneratorRuntime.releaseChain(ri, st);
            return iteratorResult(st.finalValue, true);
        } finally {
            ri.generatorResumeContext = null;
        }
    }

    static Rv suspendFromYield(RocksInterpreter ri, State st, Rv yieldVal,
            Node resumeNode, int resumeIdx, Pack stackSnapshot, Rv funCoLeaf) {
        st.lastYield = yieldVal != null ? yieldVal : Rv._undefined;
        st.resumeNode = resumeNode;
        st.resumeIdx = resumeIdx;
        st.savedStack = stackSnapshot;
        st.funCoLeaf = funCoLeaf;
        ri.captureScopeChain(funCoLeaf);
        ri.generatorSuspendSkipRecycle = true;
        return SUSPEND;
    }
}
