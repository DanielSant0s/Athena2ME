package net.cnjm.j2me.tinybro;

import java.util.Vector;

import net.cnjm.j2me.util.Pack;

/**
 * Minimal Promise runtime: thread-safe microtask queue drained on the JS thread
 * via {@link #drain(RocksInterpreter)}, plus {@code then}/{@code catch} settlement.
 */
public final class PromiseRuntime {

    private PromiseRuntime() {}

    private static int capSeq;

    private static synchronized int nextCapSeq() {
        return ++capSeq;
    }

    private static final Object QUEUE_LOCK = new Object();
    private static final Vector TASKS = new Vector();

    /** Max microtasks processed per {@link #drain} call (avoids infinite starvation). */
    private static final int DRAIN_CAP = 8192;

    public interface Microtask {
        void run(RocksInterpreter ri);
    }

    static final class PromiseState {
        static final int PENDING = 0;
        static final int FULFILLED = 1;
        static final int REJECTED = 2;

        /** The {@link Rv} promise object that owns this state (for cycle checks). */
        Rv selfRef;
        int state = PENDING;
        Rv value;
        final Vector reactions = new Vector();
    }

    static final class Reaction {
        final Rv onFulfilled;
        final Rv onRejected;
        final Rv child;

        Reaction(Rv onFulfilled, Rv onRejected, Rv child) {
            this.onFulfilled = onFulfilled;
            this.onRejected = onRejected;
            this.child = child;
        }
    }

    public static void enqueue(Microtask job) {
        if (job == null) {
            return;
        }
        synchronized (QUEUE_LOCK) {
            TASKS.addElement(job);
        }
    }

    public static boolean hasPending() {
        synchronized (QUEUE_LOCK) {
            return TASKS.size() > 0;
        }
    }

    public static void drain(RocksInterpreter ri) {
        if (ri == null) {
            return;
        }
        int n = 0;
        while (n++ < DRAIN_CAP) {
            Microtask job;
            synchronized (QUEUE_LOCK) {
                if (TASKS.size() == 0) {
                    break;
                }
                job = (Microtask) TASKS.firstElement();
                TASKS.removeElementAt(0);
            }
            try {
                job.run(ri);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public static boolean isPromise(Rv v) {
        return v != null && v.opaque instanceof PromiseState;
    }

    public static Rv createPending(RocksInterpreter ri) {
        Rv p = new Rv(Rv.OBJECT, Rv._Promise);
        PromiseState st = new PromiseState();
        p.opaque = st;
        st.selfRef = p;
        return p;
    }

    public static Rv resolved(RocksInterpreter ri, Rv value) {
        Rv p = createPending(ri);
        PromiseState st = (PromiseState) p.opaque;
        synchronized (st) {
            st.state = PromiseState.FULFILLED;
            st.value = value != null ? value : Rv._undefined;
        }
        return p;
    }

    public static Rv rejected(RocksInterpreter ri, Rv reason) {
        Rv p = createPending(ri);
        PromiseState st = (PromiseState) p.opaque;
        synchronized (st) {
            st.state = PromiseState.REJECTED;
            st.value = reason != null ? reason : Rv._undefined;
        }
        return p;
    }

    private static PromiseState stateOf(Rv promise) {
        if (!isPromise(promise)) {
            throw new RuntimeException("Promise expected");
        }
        return (PromiseState) promise.opaque;
    }

    public static void fulfill(RocksInterpreter ri, Rv promise, Rv value) {
        PromiseState st = stateOf(promise);
        Vector pending;
        Rv settledVal;
        synchronized (st) {
            if (st.state != PromiseState.PENDING) {
                return;
            }
            st.state = PromiseState.FULFILLED;
            st.value = value != null ? value : Rv._undefined;
            settledVal = st.value;
            pending = copyReactions(st);
            st.reactions.removeAllElements();
        }
        scheduleReactions(ri, pending, true, settledVal);
    }

    public static void reject(RocksInterpreter ri, Rv promise, Rv reason) {
        PromiseState st = stateOf(promise);
        Vector pending;
        Rv settledVal;
        synchronized (st) {
            if (st.state != PromiseState.PENDING) {
                return;
            }
            st.state = PromiseState.REJECTED;
            st.value = reason != null ? reason : Rv._undefined;
            settledVal = st.value;
            pending = copyReactions(st);
            st.reactions.removeAllElements();
        }
        scheduleReactions(ri, pending, false, settledVal);
    }

    static boolean objectLikeThenable(Rv x) {
        if (x == null || x == Rv._undefined) {
            return false;
        }
        int t = x.type;
        return t == Rv.OBJECT || t == Rv.ARRAY || t == Rv.UINT8_ARRAY || t == Rv.ERROR
                || t == Rv.FUNCTION || t == Rv.NUMBER_OBJECT || t == Rv.STRING_OBJECT;
    }

    static Rv getThenMethod(Rv x) {
        if (!objectLikeThenable(x)) {
            return null;
        }
        Rv t;
        try {
            t = x.get("then");
        } catch (Throwable ex) {
            return null;
        }
        if (t == null || t == Rv._undefined || !t.isCallable()) {
            return null;
        }
        return t;
    }

    /**
     * Promise Resolution Procedure: settle {@code promise} with {@code x}, assimilating
     * native promises and thenables (ECMA-262 subset).
     */
    public static void resolveViaCapability(RocksInterpreter ri, Rv promise, Rv x) {
        if (ri == null || promise == null) {
            return;
        }
        PromiseState st = stateOf(promise);
        synchronized (st) {
            if (st.state != PromiseState.PENDING) {
                return;
            }
        }
        if (x == promise) {
            reject(ri, promise, Rv.error("TypeError: cannot resolve promise with itself"));
            return;
        }
        if (isPromise(x)) {
            final RocksInterpreter riCap = ri;
            final Rv promiseCap = promise;
            final Rv xCap = x;
            enqueue(new Microtask() {
                public void run(RocksInterpreter ri2) {
                    RocksInterpreter use = ri2 != null ? ri2 : riCap;
                    Rv res = newCapResolve(use, promiseCap);
                    Rv rej = newCapReject(use, promiseCap);
                    then(use, xCap, res, rej);
                }
            });
            return;
        }
        Rv thenM = getThenMethod(x);
        if (thenM != null) {
            enqueue(new ThenableInvokeJob(ri, promise, x, thenM));
            return;
        }
        fulfill(ri, promise, x);
    }

    static Rv newCapResolve(final RocksInterpreter ri, final Rv promise) {
        return ri.addNativeFunction(new NativeFunctionListEntry("Promise.__cr" + nextCapSeq(), new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack a, int s, int n, RocksInterpreter ri2) {
                RocksInterpreter use = ri2 != null ? ri2 : ri;
                Rv v = n > 0 ? StdLib.arg(a, s, n, 0) : Rv._undefined;
                resolveViaCapability(use, promise, v);
                return Rv._undefined;
            }
        }));
    }

    static Rv newCapReject(final RocksInterpreter ri, final Rv promise) {
        return ri.addNativeFunction(new NativeFunctionListEntry("Promise.__cj" + nextCapSeq(), new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack a, int s, int n, RocksInterpreter ri2) {
                RocksInterpreter use = ri2 != null ? ri2 : ri;
                Rv e = n > 0 ? StdLib.arg(a, s, n, 0) : Rv._undefined;
                reject(use, promise, e);
                return Rv._undefined;
            }
        }));
    }

    static final class ThenableInvokeJob implements Microtask {
        final RocksInterpreter riRef;
        final Rv targetPromise;
        final Rv thenable;
        final Rv thenFn;

        ThenableInvokeJob(RocksInterpreter riRef, Rv targetPromise, Rv thenable, Rv thenFn) {
            this.riRef = riRef;
            this.targetPromise = targetPromise;
            this.thenable = thenable;
            this.thenFn = thenFn;
        }

        public void run(RocksInterpreter ri) {
            RocksInterpreter use = ri != null ? ri : riRef;
            Rv res = newCapResolve(use, targetPromise);
            Rv rej = newCapReject(use, targetPromise);
            try {
                Pack ap = new Pack(-1, 2);
                ap.add(res);
                ap.add(rej);
                use.invokeJS(thenFn, thenable, ap, 0, 2);
            } catch (Throwable t) {
                reject(use, targetPromise, Rv.error(t.getMessage() != null ? t.getMessage() : "thenable threw"));
            }
        }
    }

    /** Same observable behaviour as {@code Promise.resolve(x)} (assimilates thenables). */
    public static Rv promiseResolve(RocksInterpreter ri, Rv x) {
        if (isPromise(x)) {
            return x;
        }
        Rv p = createPending(ri);
        resolveViaCapability(ri, p, x);
        return p;
    }

    private static Vector copyReactions(PromiseState st) {
        Vector out = new Vector();
        for (int i = 0, n = st.reactions.size(); i < n; i++) {
            out.addElement(st.reactions.elementAt(i));
        }
        return out;
    }

    private static void scheduleReactions(RocksInterpreter ri, Vector pending, boolean fulfilledPath, Rv val) {
        for (int i = 0, n = pending.size(); i < n; i++) {
            Reaction rx = (Reaction) pending.elementAt(i);
            enqueue(new ReactionJob(ri, rx, fulfilledPath, val));
        }
    }

    public static Rv then(RocksInterpreter ri, Rv promise, Rv onFulfilled, Rv onRejected) {
        if (!isPromise(promise)) {
            Rv base = resolved(ri, promise);
            return then(ri, base, onFulfilled, onRejected);
        }
        PromiseState st = (PromiseState) promise.opaque;
        Rv child = createPending(ri);
        Reaction rx = new Reaction(onFulfilled, onRejected, child);
        synchronized (st) {
            if (st.state == PromiseState.PENDING) {
                st.reactions.addElement(rx);
                return child;
            }
            boolean fulfilledPath = st.state == PromiseState.FULFILLED;
            Rv val = st.value;
            enqueue(new ReactionJob(ri, rx, fulfilledPath, val));
            return child;
        }
    }

    static final class ReactionJob implements Microtask {
        final RocksInterpreter riRef;
        final Reaction rx;
        final boolean fulfilledPath;
        final Rv val;

        ReactionJob(RocksInterpreter riRef, Reaction rx, boolean fulfilledPath, Rv val) {
            this.riRef = riRef;
            this.rx = rx;
            this.fulfilledPath = fulfilledPath;
            this.val = val;
        }

        public void run(RocksInterpreter ri) {
            RocksInterpreter use = ri != null ? ri : riRef;
            if (use == null) {
                return;
            }
            try {
                if (fulfilledPath) {
                    if (rx.onFulfilled != null && rx.onFulfilled != Rv._undefined && rx.onFulfilled.isCallable()) {
                        Rv out = use.invokeJS1(rx.onFulfilled, Rv._undefined, val);
                        resolveViaCapability(use, rx.child, out != null ? out : Rv._undefined);
                    } else {
                        resolveViaCapability(use, rx.child, val);
                    }
                } else {
                    if (rx.onRejected != null && rx.onRejected != Rv._undefined && rx.onRejected.isCallable()) {
                        Rv out = use.invokeJS1(rx.onRejected, Rv._undefined, val);
                        resolveViaCapability(use, rx.child, out != null ? out : Rv._undefined);
                    } else {
                        reject(use, rx.child, val);
                    }
                }
            } catch (Throwable t) {
                reject(use, rx.child, Rv.error(t.getMessage() != null ? t.getMessage() : "Promise reaction failed"));
            }
        }
    }

    /**
     * Builds a {@code Uint8Array} view backed by a fresh {@code ArrayBuffer}
     * (same pattern as {@code Athena2ME} / {@code StdLib}).
     */
    public static Rv newUint8Array(RocksInterpreter ri, byte[] data) {
        if (data == null) {
            data = new byte[0];
        }
        Rv bufRv = new Rv(Rv.OBJECT, Rv._ArrayBuffer);
        bufRv.type = Rv.OBJECT;
        bufRv.ctorOrProt = Rv._ArrayBuffer;
        bufRv.opaque = new Rv.ArrayBufferBacking(data);
        ri.addToObject(bufRv, "byteLength", new Rv(data.length));
        Rv inst = new Rv(Rv.UINT8_ARRAY, Rv._Uint8Array);
        inst.type = Rv.UINT8_ARRAY;
        inst.ctorOrProt = Rv._Uint8Array;
        inst.opaque = new Rv.Uint8View(data, 0, data.length, bufRv);
        inst.num = data.length;
        ri.addToObject(inst, "buffer", bufRv);
        ri.addToObject(inst, "byteOffset", new Rv(0));
        ri.addToObject(inst, "byteLength", new Rv(data.length));
        return inst;
    }

    // ---- StdLib native entry points ----

    public static Rv nativeThen(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
        Rv onF = num > 0 ? StdLib.arg(args, start, num, 0) : Rv._undefined;
        Rv onR = num > 1 ? StdLib.arg(args, start, num, 1) : Rv._undefined;
        return then(ri, thiz, onF, onR);
    }

    public static Rv nativeCatch(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
        Rv onR = num > 0 ? StdLib.arg(args, start, num, 0) : Rv._undefined;
        return then(ri, thiz, Rv._undefined, onR);
    }

    public static Rv nativeResolve(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
        Rv x = num > 0 ? StdLib.arg(args, start, num, 0) : Rv._undefined;
        return promiseResolve(ri, x);
    }

    public static Rv nativeReject(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
        Rv x = num > 0 ? StdLib.arg(args, start, num, 0) : Rv._undefined;
        return rejected(ri, x);
    }

    /** {@code new Promise(executor)} with optional executor {@code (resolve, reject) => { ... }}. */
    public static Rv nativeCtor(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
        Rv p = createPending(ri);
        if (num <= 0 || ri == null) {
            return p;
        }
        Rv executor = StdLib.arg(args, start, num, 0);
        if (executor == null || executor == Rv._undefined || !executor.isCallable()) {
            return p;
        }
        Rv resFn = newCapResolve(ri, p);
        Rv rejFn = newCapReject(ri, p);
        try {
            Pack ap = new Pack(-1, 2);
            ap.add(resFn);
            ap.add(rejFn);
            ri.invokeJS(executor, Rv._undefined, ap, 0, 2);
        } catch (Throwable t) {
            PromiseState st = (PromiseState) p.opaque;
            synchronized (st) {
                if (st.state == PromiseState.PENDING) {
                    reject(ri, p, Rv.error(t.getMessage() != null ? t.getMessage() : "executor failed"));
                }
            }
        }
        return p;
    }
}
