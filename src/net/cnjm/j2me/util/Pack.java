package net.cnjm.j2me.util;

public class Pack {

    public int[] iArray;
    
    public Object[] oArray;
    
    public int iSize;
    
    public int oSize;

    public Pack(int iInitVol, int oInitVol) {
        reset(iInitVol, oInitVol);
    }

    public Pack reset(int iInitVol, int oInitVol) {
        this.iSize = 0;
        this.oSize = 0;
        iArray = iInitVol < 0 ? null : new int[iInitVol];
        oArray = oInitVol < 0 ? null : new Object[oInitVol];
        return this;
    }
    
    /**
     * -1 means no change
     * @param iNewSize
     * @param oNewSize
     * @return
     */
    public Pack setSize(int iNewSize, int oNewSize) {
        if (iNewSize >= 0) {
            int oldsize = iSize;
            iSize = iNewSize;
            if (iNewSize > oldsize) {
                checkEnlarge(false);
            } else if (iNewSize < oldsize) {
                int[] arr = this.iArray;
                for (int i = oldsize; --i >= iNewSize; arr[i] = 0); 
            }
        }
        if (oNewSize >= 0) {
            int oldsize = oSize;
            oSize = oNewSize;
            if (oNewSize > oldsize) {
                checkEnlarge(true);
            } else if (oNewSize < oldsize) {
                Object[] arr = this.oArray;
                for (int i = oldsize; --i >= oNewSize; arr[i] = null); 
            }
        }
        return this;
    }

    public final int getInt(int index) {
        if (index < 0) index = iSize + index;
        if (index >= iSize) throw new ArrayIndexOutOfBoundsException(index);
        return iArray[index];
    }
    
    public final Object getObject(int index) {
        if (index < 0) index = oSize + index;
        if (index >= oSize) throw new ArrayIndexOutOfBoundsException(index);
        return oArray[index];
    }
    
    public final Pack add(int ival) {
        return add(iSize, ival);
    }
    
    public final Pack add(Object oval) {
        return add(oSize, oval);
    }

    public final Pack add(int index, int ival) {
        if (index < 0) index = iSize + index;
        if (index > iSize) throw new ArrayIndexOutOfBoundsException(index);
        int[] arr;
        int size = iSize++;
        if ((arr = iArray) == null || size + 1 >= arr.length) {
            checkEnlarge(false);
            arr = iArray;
        }
        if (index < size) {
            System.arraycopy(arr, index, arr, index + 1, size - index);
        }
        arr[index] = ival;
        return this;
    }
    
    public final Pack add(int index, Object oval) {
        if (index < 0) index = oSize + index;
        if (index > oSize) throw new ArrayIndexOutOfBoundsException(index);
        Object[] arr;
        int size = oSize++;
        if ((arr = oArray) == null || size + 1 >= arr.length) {
            checkEnlarge(true);
            arr = oArray;
        }
        if (index < size) {
            System.arraycopy(arr, index, arr, index + 1, size - index);
        }
        arr[index] = oval;
        return this;
    }
    
    public final Pack set(int index, int ival) {
        if (index < 0) index = iSize + index;
        if (index >= iSize) throw new ArrayIndexOutOfBoundsException(index);
        iArray[index] = ival;
        return this;
    }
    
    public final Pack set(int index, Object oval) {
        if (index < 0) index = oSize + index;
        if (index >= oSize) throw new ArrayIndexOutOfBoundsException(index);
        oArray[index] = oval;
        return this;
    }
    
    public final int indexOf(int ival) {
        int[] arr;
        if ((arr = iArray) != null) {
            for (int i = 0, n = iSize; i < n; i++) {
                if (ival == arr[i]) {
                    return i;
                }
            }
        }
        return -1;
    }

    public final int indexOf(Object oval) {
        Object[] arr;
        if ((arr = oArray) != null) {
            for (int i = 0, n = oSize; i < n; i++) {
                Object o;
                if (oval == (o = arr[i]) || oval != null && oval.equals(o)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public final int removeInt(int index) {
        if (index < 0) index = iSize + index;
        if (index >= iSize) throw new ArrayIndexOutOfBoundsException(index);
        int[] arr = iArray;
        int len, size;
        if ((len = (size = --iSize) - index) > 0) {
            System.arraycopy(arr, index + 1, arr, index, len);
        }
        int ret = arr[size];
        return ret;
    }
    
    public final Object removeObject(int index) {
        if (index < 0) index = oSize + index;
        if (index >= oSize) throw new ArrayIndexOutOfBoundsException(index);
        Object[] arr = oArray;
        int len, size;
        if ((len = (size = --oSize) - index) > 0) {
            System.arraycopy(arr, index + 1, arr, index, len);
        }
        Object ret = arr[size];
        arr[size] = null;
        return ret;
    }

    public final Pack trimToSize() {
        Object[] oarr, onewarr;
        int[] iarr, inewarr;
        int size;
        if ((oarr = oArray) != null && (size = oSize) < oarr.length) {
            oArray =  onewarr = new Object[size];
            System.arraycopy(oarr, 0, onewarr, 0, size);
        }
        if ((iarr = iArray) != null && (size = iSize) < iarr.length) {
            iArray = inewarr = new int[size];
            System.arraycopy(iarr, 0, inewarr, 0, size);
        }
        return this;
    }
    
    public final Object clone() {
        int[] iarr;
        Object[] oarr;
        Pack ret = new Pack((iarr = this.iArray) != null ? iarr.length : -1,
                (oarr = this.oArray) != null ? oarr.length : -1);
        if (iarr != null) {
            System.arraycopy(iarr, 0, ret.iArray, 0, iarr.length);
        }
        if (oarr != null) {
            System.arraycopy(oarr, 0, ret.oArray, 0, oarr.length);
        }
        ret.iSize = this.iSize;
        ret.oSize = this.oSize;
        return ret;
    }

    public final String toString() {
        StringBuffer buf = new StringBuffer("pack(");
        buf.append(iSize).append(',').append(oSize).append(") {");
        int[] iarr;
        if ((iarr = iArray) != null) {
            buf.append("{");
            for (int i = 0; i < iSize; i++) {
                if (i > 0) buf.append(',');
                buf.append(iarr[i]);
            }
            buf.append("},");
        } else {
            buf.append((Object) null).append(", ");
        }
        Object[] oarr;
        if ((oarr = oArray) != null) {
            buf.append("{");
            for (int i = 0; i < oSize; i++) {
                if (i > 0) buf.append(',');
                buf.append('\"').append(oarr[i]).append('\"');
            }
            buf.append("}");
        } else {
            buf.append((Object) null);
        }
        buf.append("}");
        return buf.toString();
    }

    private final void checkEnlarge(boolean obj) {
        int size;
        if (obj) {
            Object[] oarr, newarray;
            int len = (oarr = oArray) == null ? -1 : oarr.length;
            if ((size = oSize) > len || len < 0) {
                int newsize = size != len ? size : size * 2;
                if (newsize < 4) newsize = 4;
                oArray = newarray = new Object[newsize];
                if (len > 0) {
                    System.arraycopy(oarr, 0, newarray, 0, len);
                }
            }
        } else {
            int[] iarr, newarray;
            int len = (iarr = iArray) == null ? -1 : iarr.length;
            if ((size = iSize) > len || len < 0) {
                int newsize = size != len ? size : size * 2;
                if (newsize < 4) newsize = 4;
                iArray = newarray = new int[newsize];
                if (len > 0) {
                    System.arraycopy(iarr, 0, newarray, 0, len);
                }
            }
        }
    }

}
