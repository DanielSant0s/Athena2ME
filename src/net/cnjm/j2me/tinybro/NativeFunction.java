package net.cnjm.j2me.tinybro;

import java.util.*;

import net.cnjm.j2me.util.*;

interface CallableFunction {
    Rv execute(Rv args);
    Rv execute(Rv _this, Rv args);
}

public class NativeFunction {
    private String name;
    private int length;
    private final CallableFunction func;

    public NativeFunction(String name, CallableFunction func, int length) {
        this.name = name;
        this.func = func;
        this.length = length;
    }

    public Rv call(Rv args) {
        return func.execute(args);
    }

    public Rv call(Rv _this, Rv args) {
        return func.execute(_this, args);
    }
}