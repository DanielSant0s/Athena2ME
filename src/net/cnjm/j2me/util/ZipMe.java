package net.cnjm.j2me.util;

import java.util.*;

public class ZipMe {

    private static final int BTYPE_NONE = 0;
    private static final int BTYPE_DYNAMIC = 2;
    private static final int MAX_BITS = 16;
    private static final int MAX_CODE_LITERALS = 287;
    private static final int MAX_CODE_DISTANCES = 31;
    private static final int MAX_CODE_LENGTHS = 18;
    private static final int EOB_CODE = 256;

    private static final int LENGTH_EXTRA_BITS[] = { 0, 0, 0, 0, 0, 0, 0, 0, 1,
            1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0, 99, 99 };
    private static final int LENGTH_VALUES[] = { 3, 4, 5, 6, 7, 8, 9, 10, 11,
            13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131,
            163, 195, 227, 258, 0, 0 };
    private static final int DISTANCE_EXTRA_BITS[] = { 0, 0, 0, 0, 1, 1, 2, 2,
            3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12,
            13, 13 };
    private static final int DISTANCE_VALUES[] = { 1, 2, 3, 4, 5, 7, 9, 13, 17,
            25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049,
            3073, 4097, 6145, 8193, 12289, 16385, 24577 };
    private static final int DYNAMIC_LENGTH_ORDER[] = { 16, 17, 18, 0, 8, 7, 9,
            6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15 };

    private int curIndex, curByte, curBit;

    public final  byte[] inflate(byte data[], int startIdx, int size) {
        curIndex = startIdx;
        byte ret[] = new byte[size];
        int idx, bfinal, btype;
        idx = bfinal = btype = curByte = curBit = 0;
        do {
            bfinal = readBits(data, 1);
            btype = readBits(data, 2);
            if (btype == BTYPE_NONE) {
                curBit = 0;
                // LEN.
                int len = readBits(data, 16);
                // NLEN.
                readBits(data, 16);
                System.arraycopy(data, curIndex, ret, idx, len);
                curIndex += len;
                idx += len;
            } else {
                int literalTree[], distanceTree[];
                if (btype == BTYPE_DYNAMIC) {
                    int hlit = readBits(data, 5) + 257;
                    int hdist = readBits(data, 5) + 1;
                    int hclen = readBits(data, 4) + 4;
                    byte lengthBits[] = new byte[MAX_CODE_LENGTHS + 1];
                    for (int i = 0; i < hclen; i++)
                        lengthBits[DYNAMIC_LENGTH_ORDER[i]] = (byte) readBits(data, 3);
                    int lengthTree[] = huffmanTree(lengthBits, MAX_CODE_LENGTHS);
                    literalTree = huffmanTree(codeLengths(data,
                            lengthTree, hlit), hlit - 1);
                    distanceTree = huffmanTree(codeLengths(data,
                            lengthTree, hdist), hdist - 1);
                } else {
                    byte literalBits[] = new byte[MAX_CODE_LITERALS + 1];
                    for (int i = 144; --i >= 0; literalBits[i] = 8);
                    for (int i = 256; --i >= 144; literalBits[i] = 9);
                    for (int i = 280; --i >= 256; literalBits[i] = 7);
                    for (int i = 288; --i >= 280; literalBits[i] = 8);
                    literalTree = huffmanTree(literalBits, MAX_CODE_LITERALS);

                    byte distanceBits[] = new byte[MAX_CODE_DISTANCES + 1];
                    for (int i = distanceBits.length; --i >= 0; distanceBits[i] = 5);
                    distanceTree = huffmanTree(distanceBits, MAX_CODE_DISTANCES);
                }
                int code = 0, leb = 0, deb = 0;
                while ((code = readCode(data, literalTree)) != EOB_CODE) {
                    if (code > EOB_CODE) {
                        code -= 257;
                        int length = LENGTH_VALUES[code];
                        if ((leb = LENGTH_EXTRA_BITS[code]) > 0)
                            length += readBits(data, leb);
                        code = readCode(data, distanceTree);
                        int distance = DISTANCE_VALUES[code];
                        if ((deb = DISTANCE_EXTRA_BITS[code]) > 0)
                            distance += readBits(data, deb);
                        int offset = idx - distance;
                        while (distance < length) {
                            System.arraycopy(ret, offset, ret, idx, distance);
                            idx += distance;
                            length -= distance;
                            distance <<= 1;
                        }
                        System.arraycopy(ret, offset, ret, idx, length);
                        idx += length;
                    } else {
                        ret[idx++] = (byte) code;
                    }
                }
            }
        } while (bfinal == 0);
        return ret;
    }

    private final int readBits(byte bb[], int n) {
        int data = (curBit == 0 ? (curByte = (bb[curIndex++] & 0xFF))
                : (curByte >> curBit));
        for (int i = (8 - curBit); i < n; i += 8) {
            curByte = (bb[curIndex++] & 0xFF);
            data |= (curByte << i);
        }
        curBit = (curBit + n) & 7;
        return (data & ((1 << n) - 1));
    }

    private final int readCode(byte bb[], int tree[]) {
        int node = tree[0];
        while (node >= 0) {
            if (curBit == 0) curByte = (bb[curIndex++] & 0xFF);
            node = (((curByte & (1 << curBit)) == 0) ? tree[node >> 16]
                    : tree[node & 0xFFFF]);
            curBit = (curBit + 1) & 7;
        }
        return (node & 0xFFFF);
    }

    private final byte[] codeLengths(byte bb[], int lentree[], int count) {
        byte bits[] = new byte[count];
        for (int i = 0, code = 0, last = 0; i < count;) {
            code = readCode(bb, lentree);
            if (code >= 16) {
                int repeat = 0;
                if (code == 16) {
                    repeat = 3 + readBits(bb, 2);
                    code = last;
                } else {
                    if (code == 17)
                        repeat = 3 + readBits(bb, 3);
                    else
                        repeat = 11 + readBits(bb, 7);
                    code = 0;
                }
                while (repeat-- > 0)
                    bits[i++] = (byte) code;
            } else {
                bits[i++] = (byte) code;
            }
            last = code;
        }
        return bits;
    }

    private final static int[] huffmanTree(byte bits[], int maxCode) {
        int bl_count[] = new int[MAX_BITS + 1];
        for (int i = 0, n = bits.length; i < n; i++)
            bl_count[bits[i]]++;
        int code = 0;
        bl_count[0] = 0;
        int next_code[] = new int[MAX_BITS + 1];
        for (int i = 1; i <= MAX_BITS; i++)
            next_code[i] = code = (code + bl_count[i - 1]) << 1;
        int tree[] = new int[(maxCode << 1) + MAX_BITS];
        int treeInsert = 1;
        for (int i = 0; i <= maxCode; i++) {
            int len = bits[i];
            if (len != 0) {
                code = next_code[len]++;
                int node = 0;
                for (int bit = len - 1; bit >= 0; bit--) {
                    int value = code & (1 << bit);
                    if (value == 0) {
                        int left = tree[node] >> 16;
                        if (left == 0) {
                            tree[node] |= (treeInsert << 16);
                            node = treeInsert++;
                        } else
                            node = left;
                    } else {
                        int right = tree[node] & 0xFFFF;
                        if (right == 0) {
                            tree[node] |= treeInsert;
                            node = treeInsert++;
                        } else
                            node = right;
                    }
                }
                tree[node] = 0x80000000 | i;
            }
        }
        return tree;
    }
    
    private byte[] data;
    
    private Hashtable htToc;
    
    public ZipMe(byte[] data) {
        reset(data);
    }
    
    public final ZipMe reset(byte[] data) {
        this.data = data;
        htToc = null;
        return this;
    }
    
    public final Pack list() {
        Hashtable toc;
        if ((toc = htToc) == null) {
            toc = htToc = new Hashtable();
            int offset, i;
            offset = i = 0;
            for (;;) {
                int n = read(data, offset, 4);
                if (n != 0x04034B50) break; // end of data section
                offset += 8; // header,pkware_ver,global_flag
                int method = read(data, offset, 2);
                offset += 10; // method,date,time,crc
                int size = read(data, offset, 4);
                offset += 4; // compressed_size
                int orisize = read(data, offset, 4);
                offset += 4; // original_size
                int filenamelen = read(data, offset, 2);
                offset += 2;
                int extlen = read(data, offset, 2);
                offset += 2;
                String filename = new String(data, offset, filenamelen);
                offset += filenamelen + extlen;
                toc.put(filename, new int[] { offset, size, orisize, method, i++ } );
                offset += size;
            }
        }
        int size;
        Pack ret = new Pack(-1, size = toc.size()).setSize(-1, size);
        for (Enumeration e = toc.keys(); e.hasMoreElements();) {
            String filename = (String) e.nextElement();
            int[] info = (int[]) toc.get(filename);
            ret.set(info[4], filename);
        }
        return ret;
    }
    
    public final byte[] get(String name) {
        if (htToc == null) list();
        int[] info = (int[]) htToc.get(name);
        if (info == null) return null;
        if (info[1] == 0) return new byte[0];
        if (info[3] == 0) { // store
            byte[] ret = new byte[info[2]];
            System.arraycopy(data, info[0], ret, 0, info[2]);
            return ret;
        } else if (info[3] == 8) { // deflate
            byte[] ret = inflate(data, info[0], info[2]);
            return ret;
        }
        return null;
    }
    
    private static final int read(byte[] data, int offset, int len) {
        int ret = 0;
        for (int i = offset, j = 0, n = offset + len; i < n; i++, j += 8) {
            ret |= ((data[i] & 0xff) << j);
        }
        return ret;
    }
    
}