package net.cnjm.j2me.tinybro;

import java.util.Hashtable;
import java.util.Vector;

import net.cnjm.j2me.util.Pack;

/**
 * Builds a flat draw-list and focus order from a laid-out {@link Page} for the
 * JS renderer ({@code Browser.*} bindings).
 */
public final class BrowserSession {

    /** Per-op payload: see {@link #addOp}. */
    private final Vector ops = new Vector();

    private final Vector focusNodes = new Vector();

    private final Vector focusNids = new Vector();

    /** Maps walked {@link Node} to its draw-list id (for inline text under {@code <a>}). */
    private final Hashtable nodeToId = new Hashtable();

    private Page page;

    private String lastHtml = "";

    private int scrollY;

    private int lastViewportH;

    private int focusIndex;

    private int contentBottom;

    private final Vector history = new Vector();

    private int historyPos = -1;

    public BrowserSession() {
    }

    public final Page getPage() {
        return page;
    }

    public final void moveFocus(int delta) {
        int n = focusNodes.size();
        if (n <= 0) {
            return;
        }
        focusIndex += delta;
        while (focusIndex < 0) {
            focusIndex += n;
        }
        while (focusIndex >= n) {
            focusIndex -= n;
        }
    }

    public final int getScrollY() {
        return scrollY;
    }

    public final void setScrollY(int y) {
        scrollY = y;
        if (scrollY < 0) {
            scrollY = 0;
        }
        clampScrollY();
    }

    public final void scrollBy(int delta, int viewportH) {
        lastViewportH = viewportH > 0 ? viewportH : lastViewportH;
        scrollY += delta;
        if (scrollY < 0) {
            scrollY = 0;
        }
        clampScrollY();
    }

    private void clampScrollY() {
        int viewH = lastViewportH > 0 ? lastViewportH : 1;
        int max = contentBottom > viewH ? contentBottom - viewH : 0;
        if (scrollY > max) {
            scrollY = max;
        }
    }

    public final int getContentBottom() {
        return contentBottom;
    }

    public final int getFocusIndex() {
        return focusIndex;
    }

    public final void setFocusIndex(int i) {
        if (i < 0) {
            i = 0;
        }
        if (i >= focusNodes.size()) {
            i = focusNodes.size() > 0 ? focusNodes.size() - 1 : 0;
        }
        focusIndex = i;
    }

    public final int getFocusCount() {
        return focusNodes.size();
    }

    public final Node getFocusNode(int i) {
        if (i < 0 || i >= focusNodes.size()) {
            return null;
        }
        return (Node) focusNodes.elementAt(i);
    }

    public final int getOpCount() {
        return ops.size();
    }

    public final Object[] getOpArray(int i) {
        if (i < 0 || i >= ops.size()) {
            return null;
        }
        return (Object[]) ops.elementAt(i);
    }

    public final void loadHtml(Object classLoaderLock, Class midletClass, String html, int w, int h) {
        lastHtml = html != null ? html : "";
        AthenaPageHost host = new AthenaPageHost(classLoaderLock, midletClass);
        page = Page.createPage(host, lastHtml, w, h);
        scrollY = 0;
        focusIndex = 0;
        lastViewportH = h > 38 ? h - 38 : h;
        rebuildOpsAndFocus();
    }

    public final void reload(Object classLoaderLock, Class midletClass, int w, int h) {
        if (lastHtml == null || lastHtml.length() == 0) {
            return;
        }
        loadHtml(classLoaderLock, midletClass, lastHtml, w, h);
    }

    public final void clear() {
        page = null;
        ops.removeAllElements();
        focusNodes.removeAllElements();
        focusNids.removeAllElements();
        nodeToId.clear();
        scrollY = 0;
        focusIndex = 0;
        contentBottom = 0;
    }

    public final int hitTest(int docX, int docY) {
        for (int i = ops.size() - 1; i >= 0; i--) {
            Object[] row = (Object[]) ops.elementAt(i);
            int ox = ((Integer) row[1]).intValue();
            int oy = ((Integer) row[2]).intValue();
            int w = ((Integer) row[3]).intValue();
            int h = ((Integer) row[4]).intValue();
            if (w <= 0 || h <= 0) {
                continue;
            }
            if (docX >= ox && docY >= oy && docX < ox + w && docY < oy + h) {
                return ((Integer) row[11]).intValue();
            }
        }
        return -1;
    }

    public final void historyPush(String url) {
        while (history.size() > historyPos + 1) {
            history.removeElementAt(history.size() - 1);
        }
        history.addElement(url);
        historyPos = history.size() - 1;
    }

    public final String historyBack() {
        if (historyPos <= 0) {
            return null;
        }
        historyPos--;
        return (String) history.elementAt(historyPos);
    }

    public final String historyForward() {
        if (historyPos >= history.size() - 1) {
            return null;
        }
        historyPos++;
        return (String) history.elementAt(historyPos);
    }

    private void addOp(String kind, int x, int y, int w, int h, int color, int fontFace, int fontStyle,
            int fontSize, int flags, int nodeTag, int nodeId, String text, String href, String imgSrc) {
        Object[] row = new Object[15];
        row[0] = kind;
        row[1] = new Integer(x);
        row[2] = new Integer(y);
        row[3] = new Integer(w);
        row[4] = new Integer(h);
        row[5] = new Integer(color);
        row[6] = new Integer(fontFace);
        row[7] = new Integer(fontStyle);
        row[8] = new Integer(fontSize);
        row[9] = new Integer(flags);
        row[10] = new Integer(nodeTag);
        row[11] = new Integer(nodeId);
        row[12] = text;
        row[13] = href;
        row[14] = imgSrc;
        ops.addElement(row);
    }

    /** Full-page background behind all content (root div {@code background-color}). */
    private void prependRootBackground() {
        if (page == null || page.dom == null || contentBottom <= 0) {
            return;
        }
        Pack dic = Node.inheritedStyle(page.dom);
        int bg = dic.iArray[C.C_BGCOLOR];
        if (bg == C.E_TRANSPARENT) {
            return;
        }
        int w = page.width > 0 ? page.width : 320;
        int h = contentBottom;
        if (h < 16) {
            h = 16;
        }
        Object[] row = new Object[15];
        row[0] = "rect";
        row[1] = new Integer(0);
        row[2] = new Integer(0);
        row[3] = new Integer(w);
        row[4] = new Integer(h);
        row[5] = new Integer(bg & C.M);
        row[6] = new Integer(0);
        row[7] = new Integer(0);
        row[8] = new Integer(0);
        row[9] = new Integer(0);
        row[10] = new Integer(page.dom.tagType);
        row[11] = new Integer(0);
        row[12] = null;
        row[13] = null;
        row[14] = null;
        ops.insertElementAt(row, 0);
    }

    public final int getFocusNodeId() {
        if (focusIndex < 0 || focusIndex >= focusNids.size()) {
            return -1;
        }
        return ((Integer) focusNids.elementAt(focusIndex)).intValue();
    }

    private int registerFocus(Node n, int nodeId) {
        focusNodes.addElement(n);
        focusNids.addElement(new Integer(nodeId));
        return focusNodes.size() - 1;
    }

    private int nextNodeId = 1;

    private int allocNodeId() {
        return nextNodeId++;
    }

    /** Row baseline Y within parent content box (stored in {@code F_VERTALIGN} by layout). */
    private static int boxRowY(Pack block, Pack contblock) {
        if (block == null) {
            return 0;
        }
        int[] ba = block.iArray;
        if (ba == null) {
            return 0;
        }
        int y = C.px(ba[C.F_VERTALIGN]);
        if (y == 0 && contblock != null) {
            int ri = ba[C.F_ROWIDX];
            if (ri > 0) {
                y = rowY(contblock, ri);
            }
        }
        return y;
    }

    /** Ensure {@code img} draw ops have non-zero dimensions. */
    private static void resolveImgSize(Node node, Pack dic, int[] ba) {
        int bw = C.px(ba[C.F_WIDTH]);
        int bh = C.px(ba[C.F_HEIGHT]);
        if (bw <= 0 || bh <= 0) {
            Object imo = dic.oArray[C.C_IMGOBJ];
            if (imo instanceof javax.microedition.lcdui.Image) {
                javax.microedition.lcdui.Image im = (javax.microedition.lcdui.Image) imo;
                if (bw <= 0) {
                    bw = im.getWidth();
                }
                if (bh <= 0) {
                    bh = im.getHeight();
                }
            }
        }
        if (bw <= 0) {
            bw = 48;
        }
        if (bh <= 0) {
            bh = 48;
        }
        ba[C.F_WIDTH] = bw;
        ba[C.F_HEIGHT] = bh;
    }

    /** Row baseline Y within {@code contblock}'s row-offset table (text runs). */
    private static int rowY(Pack contblock, int rowIdx) {
        if (contblock == null) {
            return 0;
        }
        int off = C.F_ROWOFFSET + rowIdx;
        if (off < C.F_ROWOFFSET || off >= contblock.iSize) {
            return 0;
        }
        return C.px(contblock.iArray[off]);
    }

    private void emitTextOps(Node node, int nid, int tag, Pack contblock, int contleft, int conttop) {
        Pack dif = node.drawinfo[0];
        Pack dicInh = Node.inheritedStyle(node);
        String s = node.getProperty("t");
        if (s == null || s.length() == 0) {
            return;
        }
        int color = dicInh.iArray[C.C_COLOR] & C.M;
        if (color == 0) {
            color = 0xe8f0ff;
        }
        int ff = dicInh.iArray[C.C_FONTFACE];
        int fst = dicInh.iArray[C.C_FONTSTYLE];
        int fsz = dicInh.iArray[C.C_FONTSIZE];
        Node.ensureFontMetrics(dicInh);
        int fontH = dicInh.iArray[C.C_FONTH];

        if (dif != null && dif.iSize > 0) {
            int[] t = dif.iArray;
            for (int i = 0, tlen = dif.iSize; i < tlen; i += 7) {
                int rowIdx = t[i];
                int yoff = conttop + rowY(contblock, rowIdx);
                int tx = contleft + C.px(t[i + C.F_OFFSETX]);
                int ty = yoff + C.px(t[i + C.F_OFFSETY]);
                int tw = t[i + 3];
                int th = t[i + 4];
                if (th <= 0) {
                    th = fontH;
                }
                int srcidx = t[i + C.T_SRCIDX];
                int srclen = t[i + C.T_SRCLEN];
                String chunk = s.substring(srcidx, srcidx + srclen);
                addOp("text", tx, ty, tw, th, color, ff, fst, fsz, 0, tag, nid, chunk, null, null);
                bumpContent(tx, ty, tw, th);
            }
            return;
        }

        int tw = Node.measureTextWidth(dicInh, s);
        int th = Node.textHeight(dicInh);
        addOp("text", contleft, conttop, tw, th, color, ff, fst, fsz, 0, tag, nid, s, null, null);
        bumpContent(contleft, conttop, tw, th);
    }

    private void emitWidgetOps(Node node, int nid, int tag, int bx, int by, int bw, int bh, Pack dic) {
        if (bw <= 0) {
            bw = 48;
        }
        if (bh <= 0) {
            bh = Node.textHeight(dic);
        }
        int[] dica = dic.iArray;
        int bg = dica[C.C_BGCOLOR];
        if (bg == C.E_TRANSPARENT) {
            bg = 0x203040;
        }
        addOp("rect", bx, by, bw, bh, bg & C.M, 0, 0, 0, 0, tag, nid, null, null, null);
        bumpContent(bx, by, bw, bh);
        addOp("border", bx, by, bw, bh, 0x6080a0, 0, 0, 0, 0, tag, nid, null, null, null);
        bumpContent(bx, by, bw, bh);

        String label = node.getProperty("value");
        if (label == null || label.length() == 0) {
            label = node.getProperty("name");
        }
        if (label == null) {
            label = "";
        }
        if (label.length() > 0) {
            int color = dica[C.C_COLOR] & C.M;
            if (color == 0) {
                color = 0xe8f0ff;
            }
            int ff = dica[C.C_FONTFACE];
            int fst = dica[C.C_FONTSTYLE];
            int fsz = dica[C.C_FONTSIZE];
            int tw = Node.measureTextWidth(dic, label);
            int th = Node.textHeight(dic);
            addOp("text", bx + 2, by + 1, tw, th, color, ff, fst, fsz, 0, tag, nid, label, null, null);
            bumpContent(bx + 2, by + 1, tw, th);
        }
    }

    private int resolveEmitNodeId(Node node, int defaultNid) {
        for (Node p = node.parent; p != null; p = p.parent) {
            if (p.tagType == C.E_A) {
                String href = p.getProperty("href");
                if (href != null && href.length() > 0) {
                    Integer pid = (Integer) nodeToId.get(p);
                    if (pid != null) {
                        return pid.intValue();
                    }
                }
                break;
            }
            if ((p.display & C.M_DISPLAY) > C.DISP_INLINE) {
                break;
            }
        }
        return defaultNid;
    }

    private void rebuildOpsAndFocus() {
        ops.removeAllElements();
        focusNodes.removeAllElements();
        focusNids.removeAllElements();
        nodeToId.clear();
        nextNodeId = 1;
        contentBottom = 0;
        if (page == null || page.dom == null) {
            return;
        }

        Node parent = new Node(page, C.E_UNDEFINED);
        parent.children = new Pack(-1, 1).add(page.dom);
        Pack contblock = new Pack(22, -1);
        int contleft = 0;
        int conttop = 0;

        Pack stack = new Pack(20, 20);
        Pack contstack = new Pack(20, 10);
        int idx = 0;
        int size = parent.children.oSize;
        Object[] siblings = parent.children.oArray;

        for (;; idx++) {
            if (idx >= size) {
                if (stack.iSize == 0) {
                    break;
                }
                idx = stack.removeInt(stack.iSize - 1);
                parent = (Node) stack.removeObject(stack.oSize - 1);
                size = parent.children.oSize;
                siblings = parent.children.oArray;
                if ((parent.display & C.M_DISPLAY) > C.DISP_INLINE && contstack.oSize > 0) {
                    contblock = (Pack) contstack.removeObject(contstack.oSize - 1);
                    conttop = contstack.removeInt(contstack.iSize - 1);
                    contleft = contstack.removeInt(contstack.iSize - 1);
                }
                continue;
            }
            Node node = (Node) siblings[idx];
            if ((node.display & C.M_DISPLAY) == 0) {
                continue;
            }

            int nid = allocNodeId();
            nodeToId.put(node, new Integer(nid));
            int tag = node.tagType;

            Pack block = null;
            int[] ba = null;

            if (node.tagType == C.E_T) {
                emitTextOps(node, resolveEmitNodeId(node, nid), tag, contblock, contleft, conttop);
            } else if ((node.display & C.M_DISPLAY) > 0x10) {
                block = node.drawinfo[0];
                ba = block.iArray;
                Pack dic = node.drawinfo[node.state];
                if (dic == null) {
                    dic = Node.DEF_DIC;
                }
                Pack dicStyle = Node.inheritedStyle(node);
                int[] dica = dicStyle.iArray;

                if (tag == C.E_IMG) {
                    resolveImgSize(node, dic, ba);
                }
                int yoff = conttop + boxRowY(block, contblock);
                int bx = contleft + C.px(ba[C.F_OFFSETX]) + C.px(ba[C.F_ML]) + C.px(ba[C.F_BL]);
                int by = yoff + C.px(ba[C.F_OFFSETY]) + C.px(ba[C.F_MT]) + C.px(ba[C.F_BT]);
                int bw = C.px(ba[C.F_WIDTH]);
                int bh = C.px(ba[C.F_HEIGHT]);
                if (bh <= 0) {
                    bh = Node.blockContentHeight(block);
                }

                if (dica[C.C_BGCOLOR] != C.E_TRANSPARENT && (dica[C.C_BGCOLOR] & C.M) != 0) {
                    addOp("rect", bx, by, bw, bh, dica[C.C_BGCOLOR] & C.M, 0, 0, 0, 0, tag, nid, null, null, null);
                    bumpContent(bx, by, bw, bh);
                }
                if (C.px(ba[C.F_BT]) > 0) {
                    int bc = dica[C.C_BCT] & C.M;
                    int bx0 = contleft + C.px(ba[C.F_OFFSETX]) + C.px(ba[C.F_ML]);
                    int by0 = yoff + C.px(ba[C.F_OFFSETY]) + C.px(ba[C.F_MT]);
                    int bw0 = bw + C.px(ba[C.F_BL]) + C.px(ba[C.F_BR]);
                    int bh0 = bh + C.px(ba[C.F_BT]) + C.px(ba[C.F_BB]);
                    addOp("border", bx0, by0, bw0, bh0, bc, 0, 0, 0, 0, tag, nid, null, null, null);
                    bumpContent(bx0, by0, bw0, bh0);
                }

                maybeRegisterFocusable(node, nid);

                if (tag == C.E_IMG) {
                    String src = node.getProperty("src");
                    addOp("img", bx, by, bw, bh, 0, 0, 0, 0, 0, tag, nid, null, null, src != null ? src : "");
                    bumpContent(bx, by, bw, bh);
                } else if (tag == C.E_INPUT) {
                    emitWidgetOps(node, nid, tag, bx, by, bw, bh, dic);
                }
            } else {
                maybeRegisterFocusable(node, nid);
            }

            Pack children;
            int childrensize;
            if ((children = node.children) != null && (childrensize = children.oSize) > 0) {
                stack.add(parent).add(idx);
                parent = node;
                idx = -1;
                size = childrensize;
                siblings = children.oArray;
                if ((node.display & C.M_DISPLAY) > 0x10) {
                    contstack.add(contblock).add(contleft).add(conttop);
                    int yoffD = conttop + boxRowY(block, contblock);
                    contleft = contleft + C.px(ba[C.F_OFFSETX]) + C.px(ba[C.F_ML]) + C.px(ba[C.F_BL]) + C.px(ba[C.F_PL]);
                    conttop = yoffD + C.px(ba[C.F_OFFSETY]) + C.px(ba[C.F_MT]) + C.px(ba[C.F_BT]) + C.px(ba[C.F_PT]);
                    contblock = block;
                }
            }
        }
        prependRootBackground();
    }

    private void bumpContent(int x, int y, int w, int h) {
        int b = y + h;
        if (b > contentBottom) {
            contentBottom = b;
        }
    }

    private void maybeRegisterFocusable(Node n, int nodeId) {
        int t = n.tagType;
        if (t == C.E_A) {
            String href = n.getProperty("href");
            if (href != null && href.length() > 0) {
                registerFocus(n, nodeId);
            }
        } else if (t == C.E_INPUT || t == C.E_BUTTON || t == C.E_SUBMIT || t == C.E_RESET
                || t == C.E_TEXTAREA || t == C.E_SELECT) {
            registerFocus(n, nodeId);
        } else if (t == C.E_IMG) {
            String src = n.getProperty("src");
            if (src != null && src.length() > 0) {
                registerFocus(n, nodeId);
            }
        }
    }

    /**
     * @return href to navigate, or {@code null}; {@code outMethod} 0=GET, 1=POST; {@code outBody} may hold urlencoded body
     */
    public final String activateFocused(StringBuffer outMethod, StringBuffer outBody) {
        outMethod.setLength(0);
        outBody.setLength(0);
        Node n = getFocusNode(focusIndex);
        if (n == null) {
            return null;
        }
        int t = n.tagType;
        if (t == C.E_A) {
            return n.getProperty("href");
        }
        if (t == C.E_INPUT) {
            String ty = n.getProperty("type");
            int ity = ty != null ? Parser.stringToEnum(ty) : C.E_TEXT;
            if (ity == C.E_SUBMIT || ity == C.E_BUTTON) {
                return submitEnclosingForm(n, outMethod, outBody);
            }
        }
        if (t == C.E_SUBMIT || t == C.E_RESET || t == C.E_BUTTON) {
            return submitEnclosingForm(n, outMethod, outBody);
        }
        return null;
    }

    private static Node findFormAncestor(Node n) {
        for (Node p = n.parent; p != null; p = p.parent) {
            if (p.tagType == C.E_FORM) {
                return p;
            }
        }
        return null;
    }

    private String submitEnclosingForm(Node n, StringBuffer outMethod, StringBuffer outBody) {
        Node form = findFormAncestor(n);
        if (form == null) {
            return null;
        }
        String action = form.getProperty("action");
        if (action == null) {
            action = "";
        }
        String method = form.getProperty("method");
        boolean post = method != null && method.toLowerCase().equals("post");
        outMethod.append(post ? "POST" : "GET");
        if (post) {
            outBody.append(buildFormBody(form));
            return action;
        }
        String q = buildFormQuery(form);
        if (q.length() == 0) {
            return action;
        }
        if (action.indexOf('?') >= 0) {
            return action + "&" + q;
        }
        return action + "?" + q;
    }

    private static String buildFormQuery(Node form) {
        StringBuffer sb = new StringBuffer();
        appendFormFields(form, sb, '&', true);
        return sb.toString();
    }

    private static String buildFormBody(Node form) {
        StringBuffer sb = new StringBuffer();
        appendFormFields(form, sb, '&', false);
        return sb.toString();
    }

    private static void appendFormFields(Node form, StringBuffer sb, char sep, boolean forQuery) {
        appendFormFieldsInner(form, sb, sep, forQuery);
    }

    private static void appendFormFieldsInner(Node form, StringBuffer sb, char sep, boolean forQuery) {
        if (form.children == null) {
            return;
        }
        Object[] ch = form.children.oArray;
        for (int i = 0; i < form.children.oSize; i++) {
            Node c = (Node) ch[i];
            if (c.children != null) {
                appendFormFieldsInner(c, sb, sep, forQuery);
            }
            int t = c.tagType;
            if (t == C.E_INPUT) {
                String name = c.getProperty("name");
                String val = valueForInput(c);
                appendPair(sb, name, val, sep);
            } else if (t == C.E_TEXTAREA) {
                String name = c.getProperty("name");
                String val = c.getProperty("t");
                if (val == null) {
                    val = "";
                }
                appendPair(sb, name, val, sep);
            } else if (t == C.E_SELECT) {
                String name = c.getProperty("name");
                String val = selectedOptionValue(c);
                appendPair(sb, name, val, sep);
            }
        }
    }

    private static void appendPair(StringBuffer sb, String name, String val, char sep) {
        if (name == null || name.length() == 0) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(sep);
        }
        sb.append(urlEncode(name)).append('=').append(urlEncode(val != null ? val : ""));
    }

    private static String urlEncode(String s) {
        if (s == null) {
            return "";
        }
        StringBuffer o = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                o.append(c);
            } else if (c == ' ') {
                o.append('+');
            } else {
                o.append('%');
                int hi = (c >> 4) & 0xF;
                int lo = c & 0xF;
                o.append(hex(hi)).append(hex(lo));
            }
        }
        return o.toString();
    }

    private static char hex(int n) {
        if (n < 10) {
            return (char) ('0' + n);
        }
        return (char) ('A' + (n - 10));
    }

    private static String valueForInput(Node c) {
        String ty = c.getProperty("type");
        int ity = ty != null ? Parser.stringToEnum(ty) : C.E_TEXT;
        if (ity == C.E_CHECKBOX || ity == C.E_RADIO) {
            String ch = c.getProperty("checked");
            if (ch != null && Parser.stringToEnum(ch) == C.E_TRUE) {
                return c.getProperty("value") != null ? c.getProperty("value") : "on";
            }
            return "";
        }
        String v = c.getProperty("value");
        return v != null ? v : "";
    }

    private static String selectedOptionValue(Node select) {
        if (select.children == null) {
            return "";
        }
        Object[] ch = select.children.oArray;
        for (int i = 0; i < select.children.oSize; i++) {
            Node opt = (Node) ch[i];
            if (opt.tagType != C.E_OPTION) {
                continue;
            }
            String sel = opt.getProperty("selected");
            if (sel != null && Parser.stringToEnum(sel) == C.E_TRUE) {
                String v = opt.getProperty("value");
                return v != null ? v : "";
            }
        }
        return "";
    }
}
