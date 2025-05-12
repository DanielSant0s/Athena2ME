package net.cnjm.j2me.tinybro;

import java.util.Hashtable;

public class NativeFunctionList {
    public Hashtable functions = new Hashtable();

    public NativeFunctionList(NativeFunctionListEntry entries[]) {
        for (int i = 0; i < entries.length; i++) {
            functions.put((Object)entries[i].name, (Object)entries[i].func);
        }
    }

    public NativeFunction get(String name) {
        return (NativeFunction)functions.get(name);
    }

    public void concat(NativeFunctionListEntry entries[]) {
        for (int i = 0; i < entries.length; i++) {
            functions.put((Object)entries[i].name, (Object)entries[i].func);
        }
    }

    public void put(NativeFunctionListEntry entry) {
        functions.put((Object)entry.name, (Object)entry.func);
    }

}
