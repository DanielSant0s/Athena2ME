public class AthenaLZ4 {

    static final int MAXD = 1 << 16;

    static int hash(byte[] buf, int i) {
        int v = (buf[i] & 0xFF) | ((buf[i + 1] & 0xFF) << 8) | ((buf[i + 2] & 0xFF) << 16) | ((buf[i + 3] & 0xFF) << 24);
        return (v * -1640531527) >>> 20; // hash multiplier (2654435761 = -1640531527)
    }

    public static byte[] compressRaw(byte[] src, int srcOff, int srcLen) {
        if (srcLen < 5) {
            byte[] dest = new byte[srcLen + 1];
            dest[0] = (byte) (srcLen << 4);
            System.arraycopy(src, srcOff, dest, 1, srcLen);
            return dest;
        }

        // Output size estimation: worst case is slightly larger than input
        byte[] dest = new byte[srcLen + (srcLen / 255) + 16];
        int s = srcOff;
        int d = 0;
        int end = srcOff + srcLen;
        int mflimit = end - 12; // Minimum match distance from end

        int[] hashTable = new int[4096]; // 4K entries
        for (int i = 0; i < 4096; i++) hashTable[i] = -1;

        int anchor = s;

        while (s < mflimit) {
            int step = 1;
            int searchMatchNb = 1 << 12; // limit search
            int matchIdx = -1;
            
            // Search loop
            int h = hash(src, s);
            while (true) {
                int ref = hashTable[h];
                hashTable[h] = s;
                
                if (ref != -1 && s - ref < MAXD && ref >= srcOff) {
                    if (src[ref] == src[s] && src[ref + 1] == src[s + 1] && src[ref + 2] == src[s + 2] && src[ref + 3] == src[s + 3]) {
                        matchIdx = ref;
                        break;
                    }
                }
                
                s += step;
                if (s >= mflimit) break;
                h = hash(src, s);
                step = 1 + (searchMatchNb++ >>> 8);
            }

            if (s >= mflimit) break;

            int litLen = s - anchor;
            int token = d++;

            if (litLen >= 15) {
                dest[token] = (byte) 0xF0;
                int l = litLen - 15;
                while (l >= 255) {
                    dest[d++] = (byte) 255;
                    l -= 255;
                }
                dest[d++] = (byte) l;
            } else {
                dest[token] = (byte) (litLen << 4);
            }

            System.arraycopy(src, anchor, dest, d, litLen);
            d += litLen;

            // Match offset
            int offset = s - matchIdx;
            dest[d++] = (byte) (offset & 0xFF);
            dest[d++] = (byte) ((offset >>> 8) & 0xFF);
            
            s += 4;
            matchIdx += 4;
            
            // Match length
            int matchLen = 0;
            while (s < end - 5 && src[s] == src[matchIdx]) {
                s++;
                matchIdx++;
                matchLen++;
            }

            if (matchLen >= 15) {
                dest[token] |= 0x0F;
                int l = matchLen - 15;
                while (l >= 255) {
                    dest[d++] = (byte) 255;
                    l -= 255;
                }
                dest[d++] = (byte) l;
            } else {
                dest[token] |= (byte) matchLen;
            }

            anchor = s;
        }

        // Last literals
        int litLen = end - anchor;
        int token = d++;
        if (litLen >= 15) {
            dest[token] = (byte) 0xF0;
            int l = litLen - 15;
            while (l >= 255) {
                dest[d++] = (byte) 255;
                l -= 255;
            }
            dest[d++] = (byte) l;
        } else {
            dest[token] = (byte) (litLen << 4);
        }
        System.arraycopy(src, anchor, dest, d, litLen);
        d += litLen;

        // Trim result
        byte[] res = new byte[d];
        System.arraycopy(dest, 0, res, 0, d);
        return res;
    }

    public static void decompressRaw(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff, int destLen) {
        int s = srcOff;
        int d = destOff;
        int srcEnd = srcOff + srcLen;
        int destEnd = destOff + destLen;

        while (s < srcEnd && d < destEnd) {
            int token = src[s++] & 0xFF;
            
            int litLen = token >>> 4;
            if (litLen == 15) {
                int l;
                do {
                    l = src[s++] & 0xFF;
                    litLen += l;
                } while (l == 255 && s < srcEnd);
            }
            
            if (litLen > 0) {
                System.arraycopy(src, s, dest, d, litLen);
                s += litLen;
                d += litLen;
            }

            if (s >= srcEnd || d >= destEnd) break;

            int offset = (src[s++] & 0xFF) | ((src[s++] & 0xFF) << 8);
            
            int matchLen = token & 0x0F;
            if (matchLen == 15) {
                int l;
                do {
                    l = src[s++] & 0xFF;
                    matchLen += l;
                } while (l == 255 && s < srcEnd);
            }
            matchLen += 4;
            
            int ref = d - offset;
            for (int i = 0; i < matchLen && d < destEnd; i++) {
                dest[d++] = dest[ref++];
            }
        }
    }
}
