package net.cnjm.j2me.tinybro;

import net.cnjm.j2me.util.Pack;

/**
 * Fast-path alternative to {@link NativeFunction}.
 *
 * <p>The standard {@code NativeFunction.func(isNew, thiz, args)} signature forces the
 * interpreter to allocate a fresh {@code arguments} Rv, a call-object Rhash, and to
 * copy each argument into the Rhash under its stringified index before dispatching.
 * On feature-phone JVMs this is the single largest per-frame allocator for games that
 * call {@code Draw.rect / Image.draw / Font.print / Pad.update} repeatedly.
 *
 * <p>Implementations of this class receive the raw operand stack and the range of
 * pushed arguments. They MUST NOT mutate the stack or indices outside the given range.
 */
public abstract class NativeFunctionFast extends NativeFunction {

    /**
     * Fast dispatch entry-point. Arguments live contiguously at
     * {@code args.getObject(start) ... args.getObject(start + num - 1)}.
     *
     * @param isNew  {@code true} if invoked with {@code new}.
     * @param thiz   {@code this} binding (may be {@code null}).
     * @param args   operand stack holding the actual argument Rvs.
     * @param start  index of the first argument inside {@code args}.
     * @param num    number of arguments.
     * @param ri     current interpreter (for callers that need {@code ri.call} back).
     */
    public abstract Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri);

    /**
     * Default bridge so that code paths that still call the slow contract keep
     * working. We unwrap the arguments Rv into a throw-away Pack and delegate.
     */
    public final Rv func(boolean isNew, Rv _this, Rv argsObj) {
        Pack p = new Pack(-1, 4);
        int n = argsObj != null ? argsObj.num : 0;
        for (int i = 0; i < n; i++) {
            Rv a = argsObj.get(Rv.intStr(i));
            p.add(a != null ? a : Rv._undefined);
        }
        return callFast(isNew, _this, p, 0, n, null);
    }
}
