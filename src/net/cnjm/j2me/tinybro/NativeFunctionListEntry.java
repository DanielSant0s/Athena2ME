package net.cnjm.j2me.tinybro;

public class NativeFunctionListEntry {
    public String name;
    public NativeFunction func;

    public NativeFunctionListEntry(String name, NativeFunction func) {
        this.name = name;
        this.func = func;
    }
}
