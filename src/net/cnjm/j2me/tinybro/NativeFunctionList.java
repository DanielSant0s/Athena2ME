package net.cnjm.j2me.tinybro;

/** Sorted name table for native lookups ({@code compareTo} + binary search). */
public class NativeFunctionList {

    private String[] names;
    private NativeFunction[] funcs;
    private int n;

    public NativeFunctionList(NativeFunctionListEntry entries[]) {
        n = entries != null ? entries.length : 0;
        names = new String[n == 0 ? 8 : n];
        funcs = new NativeFunction[n == 0 ? 8 : n];
        for (int i = 0; i < n; i++) {
            names[i] = entries[i].name;
            funcs[i] = entries[i].func;
        }
        sortByName(0, n);
    }

    private void sortByName(int start, int len) {
        for (int i = start + 1; i < start + len; i++) {
            String kn = names[i];
            NativeFunction fn = funcs[i];
            int j = i - 1;
            while (j >= start && names[j].compareTo(kn) > 0) {
                names[j + 1] = names[j];
                funcs[j + 1] = funcs[j];
                j--;
            }
            names[j + 1] = kn;
            funcs[j + 1] = fn;
        }
    }

    private void ensureCap(int need) {
        if (names == null || names.length < need) {
            int cap = names == null ? 16 : names.length * 2;
            while (cap < need) {
                cap *= 2;
            }
            String[] nn = new String[cap];
            NativeFunction[] nf = new NativeFunction[cap];
            if (n > 0) {
                System.arraycopy(names, 0, nn, 0, n);
                System.arraycopy(funcs, 0, nf, 0, n);
            }
            names = nn;
            funcs = nf;
        }
    }

    private int findIndex(String name) {
        int lo = 0, hi = n - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = name.compareTo(names[mid]);
            if (c == 0) {
                return mid;
            }
            if (c < 0) {
                hi = mid - 1;
            } else {
                lo = mid + 1;
            }
        }
        return -lo - 1;
    }

    public void trimToSize() {
        if (n <= 0) {
            return;
        }
        if (names != null && names.length > n) {
            String[] nn = new String[n];
            NativeFunction[] nf = new NativeFunction[n];
            System.arraycopy(names, 0, nn, 0, n);
            System.arraycopy(funcs, 0, nf, 0, n);
            names = nn;
            funcs = nf;
        }
    }

    public NativeFunction get(String name) {
        int idx = findIndex(name);
        return idx >= 0 ? funcs[idx] : null;
    }

    public int size() {
        return n;
    }

    public void concat(NativeFunctionListEntry entries[]) {
        if (entries == null) {
            return;
        }
        ensureCap(n + entries.length);
        for (int i = 0; i < entries.length; i++) {
            put(entries[i]);
        }
    }

    public void put(NativeFunctionListEntry entry) {
        if (entry == null) {
            return;
        }
        int idx = findIndex(entry.name);
        if (idx >= 0) {
            funcs[idx] = entry.func;
            return;
        }
        int ip = -idx - 1;
        ensureCap(n + 1);
        System.arraycopy(names, ip, names, ip + 1, n - ip);
        System.arraycopy(funcs, ip, funcs, ip + 1, n - ip);
        names[ip] = entry.name;
        funcs[ip] = entry.func;
        n++;
    }
}
