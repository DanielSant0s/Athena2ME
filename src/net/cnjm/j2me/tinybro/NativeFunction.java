package net.cnjm.j2me.tinybro;

import java.util.*;

import net.cnjm.j2me.util.*;

public abstract class NativeFunction {
    public int length = 0;
    public abstract Rv func(boolean isNew, Rv _this, Rv args);

}