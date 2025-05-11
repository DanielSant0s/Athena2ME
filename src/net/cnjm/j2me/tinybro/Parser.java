package net.cnjm.j2me.tinybro;

import java.util.Hashtable;

import net.cnjm.j2me.util.Pack;

/**
 * values: 
 * width/height: length, auto
 * line-height: length, normal // 行高=字体高+行距
 * border-width: length
 * border-style: none, solid, dotted
 * text-align: left(default), center, right, justify
 * vertical-align: top (default for block), middle, bottom (default for inline)
 * font: normal italic bold small medium large system monospace proportional
 * background-color: transparent
 * background-image: none image_url(index)
 * visibility: inherit visible hidden 
 * display: normal, none // non-standard
 * text-decoration: none underline 
 * 
 * css value types: 
 * length (signed): px (mask = 0x02), em (mask = 0x03), % (mask = 0x04)
 * color: #rrbbbb red green blue black white gray (mask = 0x01)
 * enumeration (e.g. none, auto, bold...) (mask = 0x00)
 * url (e.g. "bg.png?crop=0,0,20,20&transform=mirror", "/submit.jsp?parm1=1"...)
 *   path(string), arg0(string), value0(string), arg1, value1... 
 */
public class Parser {
    
    static final String ENUM_STR =  
        // special
        "undefined,0," + 
        "invalid,1," + 
        "html,10," + 
        "head," + 
        "link," + // = 12
        "style," + // = 13
        // block
        "body,100," + 
        "div," + 
        "h1," + 
        "h2," + 
        "h3," + 
        "h4," + 
        "h5," + 
        "h6," + 
        "p," + 
        "hr," + 
        "br," + 
        "form," + 
        "table," + 
        "tr," + 
        "td," + 
        "ol," + 
        "ul," + 
        "li," + 
        "postfield," + 
        "legend," + 
        "marquee," + 
        "object," + 
        "option," + 
        // inline
        "t," + // for plain text
        "span," + 
        "b," + 
        "i," + 
        "strong," + 
        "em," + 
        "a," + 
        "img," +
        "select," + 
        "textarea," + 
        "input," + 
        "text," + 
        "button," + 
        "submit," + 
        "password," + 
        "reset," + 
        "checkbox," + 
        "radio," + 
        // #hidden
        // properties
        "id,300," + // = 300
        "class," + // = 301
        "type," + // = 302; input, object
        // #style
        "name," + 
        "value," + 
        "accesskey," + 
        "src," + // img
        "href," + // a, link
        "maxlength," + 
        "constraints," + // text, not standard
        "method," + 
        "action," + 
        "colspan," + 
        "rowspan," + 
        "checked," + 
        "selected," + 
        "disabled," + 
        // event properties
        "onclick,400," + // :active
        "onfocus," + // :focus, :hover
        "onload," +   // body, img
        "onsubmit," + // form
        "onsubmitted," + // TODO change to submit? 
        // html property values
        "true,500," + // checked, selected, events
        "false," + 
        // pseudo-class
        "hover,1000," + 
        "focus," + // same as hover
        "active," + 
        // css properties
        "width,1100," + 
        "height," + 
        "font," + 
        "color," + 
        "text-decoration," + 
        "text-shadow," + 
        "line-height," + 
        "background," + //1107
        "border," + 
        "border-width," + 
        "border-color," + 
        "border-style," + 
        "border-top," + 
        "border-right," + 
        "border-bottom," + 
        "border-left," + 
        "margin," + 
        "padding," + 
        "text-align," + 
        "vertical-align," + 
        "display," + 
        "visibility," + 
        // css value," + 
        "auto,1200," + 
        "normal," + 
        "system," + 
        "monospace," + 
        "proportional," + 
        "default," + 
        "bold," + 
        "italic," + 
        "underline," + // text-decoration
        "small," + 
        "medium," + 
        "large," + 
        "transparent," + 
        "invert," + 
        "no-repeat," + 
        "repeat," + 
        "repeat-x," + 
        "repeat-y," + 
        "solid," + 
        "dotted," + 
        // visibility & display
        "hidden,1312," + // 0x520 
        "visible," +
        "none,1328," + // 0x530
        "inline," + 
        "block," + 
        "inline-block," + 
        // text-align
        "left,1344," + 
        "center," + 
        "right," + 
        "justify," + 
        // vertical-align and text-align
        "top,1360," + 
        "middle," + 
        "bottom," + 
        // color," + 
        "red,1500," + 
        "green," + 
        "blue," + 
        "black," + 
        "white," + 
        "gray," + 
        // keys
        "up,1600," + 
        // #right
        "down," + 
        // #left
        "fire," + 
        // #a
        // #b
        "c," + 
        "d," + 
        "num0," + 
        "num1," + 
        "num2," + 
        "num3," + 
        "num4," + 
        "num5," + 
        "num6," + 
        "num7," + 
        "num8," + 
        "num9," + 
        "pound," + 
        "star," + 
        "soft1," + 
        "soft2," + 
        ""; // end
    private static final Hashtable STR_TO_ENUM;
    
    private static final char[] SPACES = "\r\n\t ".toCharArray();
    private static final char[] SPACES_GT_SLASH = ">/\r\n\t ".toCharArray();
    private static final char[] SPACES_COMMA = ",\r\n\t ".toCharArray();
    private static final char[] SPACES_SEMICOLON = ";\r\n\t ".toCharArray();
    private static final char[] LT = "<".toCharArray();
    private static final char[] GT = ">".toCharArray();
    private static final char[] EQ = "=".toCharArray();
    private static final char[] COLON = ":".toCharArray();
    
    private static final int TAG_CONTENT = 1;
    private static final int TAG_START = 2;
    private static final int TAG_END = 3;
    private static final int PROP = 4;
    private static final int TEXTAREA_CONTENT = 8;
    private static final int CSS_SEL_LIST = 11;
    private static final int CSS_SELECTOR = 12;
    private static final int CSS_PROP_LIST = 13;
    private static final int CSS_VAL_LIST = 14;
    
    String src;
    char[] cc;
    int pos;
    int len;
    
    public Parser(String src) {
        reset(src);
    }
    
    public Parser reset(String src) {
        this.src = src;
        cc = src.toCharArray();
        len = cc.length;
        pos = 0;
        return this;
    }
    
    public final Node html(Page owner) {
        int state = TAG_CONTENT;
        Node root = new Node(owner, "");
        Node n = root;
        
        Pack styles = new Pack(-1, 20);
        Pack stack = new Pack(-1, 20);
        String s;
main:
        while (pos < len) {
            switch (state) {
            case TAG_CONTENT:
                if (C.E_TEXTAREA == n.tagType) {
                    state = TEXTAREA_CONTENT;
                    break;
                }
                try {
                    s = eatUnless(LT);
                    ++pos; // skip LT
                } catch (RuntimeException ex) {
                    System.out.println("dirty html");
                    break main;
                }
                if (s.trim().length() > 0) {
                    if (C.E_STYLE == n.tagType) {
                        styles.add(root).add(s);
                    } else {
                        n.appendChild(new Node(owner, "t").addProperty("t", htmlText(s)));
                    }
                }
                if (cc[pos] == '/') { // tag ends
                    ++pos;
                    state = TAG_END;
                } else { // another child node
                    state = TAG_START;
                }
                break;
            case TAG_START:
                s = eatUnless(SPACES_GT_SLASH);
                eatIf(SPACES);
                Node newchild = new Node(owner, s);
                n.appendChild(newchild);
                if (cc[pos] == '/') {
                    if (cc[++pos] != '>') throw ex(">");
                    ++pos;
                    state = TAG_CONTENT;
                } else { // cc[pos] == '>'
                    stack.add(n);
                    n = newchild;
                    state = PROP;
                }
                break;
            case TAG_END:
                s = eatUnless(GT).trim();
                ++pos;
                if (n.tagType != Parser.stringToEnum(s)) throw ex("TAG_END " + n.tagType);
                n = (Node) stack.removeObject(stack.oSize - 1);
                state = TAG_CONTENT;
                break;
            case PROP:
                char c = cc[pos];
                if (c == '>') {
                    ++pos;
                    int tt = n.tagType;
                    // <link ...>, <br>, <hr>, <img ...>, <input ...> 
                    if (tt == C.E_LINK || tt == C.E_BR || tt == C.E_HR || 
                            tt == C.E_IMG || tt == C.E_INPUT || tt == C.E_OBJECT) { 
                        n = (Node) stack.removeObject(stack.oSize - 1);
                    }
                    state = TAG_CONTENT;
                } else if (c == '/') {
                    if (cc[++pos] != '>') throw ex(">");
                    ++pos;
                    n = (Node) stack.removeObject(stack.oSize - 1);
                    state = TAG_CONTENT;
                } else {
                    s = eatUnless(EQ).trim();
                    ++pos;
                    eatIf(SPACES);
                    String v;
                    c = cc[pos];
                    if (c == '\'' || c == '"') {
                        ++pos;
                        v = eatUnless(new char[] { c });
                        ++pos;
                    } else {
                        v = eatUnless(SPACES_GT_SLASH);
                    }
                    eatIf(SPACES);
                    int type = Parser.stringToEnum(s);
                    if (type == C.E_ID) {
                        n.id = v;
                    } else if (type == C.E_CLASS) {
                        n.className = split(v, " ");
                    } else if (type == C.E_STYLE) {
                        styles.add(n).add(v);
                    } else {
                        n.addProperty(s, v);
                    }
                }
                break;
            case TEXTAREA_CONTENT:
                StringBuffer buf = new StringBuffer();
                while (state == TEXTAREA_CONTENT) { 
                    s = eatUnless(LT);
                    buf.append(s);
                    ++pos;
                    if (pos + 10 <= len && cc[pos] == '/' && 
                            "textarea".equals(new String(cc, pos + 1, 8).toLowerCase())) {
                        ++pos;
                        n.addProperty("t", buf.toString());
                        state = TAG_END;
                    } else {
                        buf.append('<');
                    }
                }
                break;
            }
        }
        
        Parser tmp = new Parser("");
        for (int i = 0, size = styles.oSize; i < size; i += 2) {
            n = (Node) styles.oArray[i];
            String css = ((String) styles.oArray[i + 1]).trim();
            if (n == root) { // TODO
//                Pack sp = tmp.reset(css).style();
//                Pack np = new Pack(-1, -1);
//                for (int ii = 0, nn = sp.oSize; ii < nn; ii += 2) {
//                    n.select(np.reset(-1, 4), (String) sp.oArray[ii]);
//                    Pack prop = tmp.reset((String) sp.oArray[ii + 1]).cssProperties();
//                    for (int jj = 0, jn = np.oSize; jj < jn; jj++) {
//                        ((Node) np.oArray[jj]).applyStyle(prop);
//                    }
//                }
            } else {
                n.setStyle(0, tmp.reset(css).cssProperties());
            }
        }
        return root.getChild(0);
    }
    
    public final Pack style() {
        int state = 1;
        Pack ret = new Pack(-1, 8);
        String s;
        while (pos < len) {
            switch (state) {
            case 1:
                eatIf(SPACES);
                try {
                    s = eatUnless("{".toCharArray()).trim();
                } catch (Exception ex) {
                    pos = len;
                    break;
                }
                ++pos;
                ret.add(s);
                state = 2;
                break;
            case 2:
                eatIf(SPACES);
                s = eatUnless("}".toCharArray()).trim();
                ++pos;
                ret.add(s);
                state = 1;
                break;
            }
        }
        return ret;
    }
    
    /**
     * styles = pack(-1, n) { block0, block1... }
     * block = pack[2] { selectors, properties }
     * selectors = pack(-1, n) { selector0, selector1... }
     * properties = pack(n, n) { { p-name0, p-name1... }, { property0, property1... } }
     * selector = [ "*" | "#id" | ".clz" | ".tbl td" | "div a:focus" ]
     * property = pack(n, 1/-1) { { p-val0, p-val1, p-val2... }, [ { image_url } ] }
     */
    public final Pack cssSelector() {
        int state = CSS_SEL_LIST;
        Pack ret = new Pack(-1, 2);
        Pack group = new Pack(-1, 2);
        String s;
        while (pos < len) {
            switch (state) {
            case CSS_SEL_LIST:
                eatIf(SPACES);
                if (pos >= len) break;
                char c = cc[pos];
                if ((c >= 'a' && c <= 'z') ||
                        (c >= 'A' && c <= 'Z') ||
                        c == '#' || c == '.' || c == '*') {
                    state = CSS_SELECTOR;
                } else {
                    throw ex("a-zA-Z*.# or </");
                }
                break;
            case CSS_SELECTOR:
                try {
                    s = eatUnless(SPACES_COMMA);
                } catch (Exception ex) {
                    s = new String(cc, pos, len - pos);
                    pos = len;
                }
                eatIf(SPACES);
                group.add(s);
                if (pos < len && cc[pos] == ',') {
                    ++pos;
                    eatIf(SPACES);
//                case '{': 
//                    selectors.add(group);
//                    ++pos;
//                    s = eatUnless("}".toCharArray());
//                    ++pos;
//                    properties = propParser.reset(s).cssProperties();
//                    ret.add(selectors).add(properties);
//                    break;
                } else { // spaces, another group
                    ret.add(group);
                    group = new Pack(-1, 2);
                }
                break;
            }
        }
        return ret;
    }
    
    public final Pack cssProperties() {
        int state = CSS_PROP_LIST;
        Pack ret = new Pack(4, 4);

        Pack prop = null;
        String s;
        while (pos < len) {
            switch (state) {
            case CSS_PROP_LIST:
                eatIf(SPACES);
                if (pos >= len) break;
                switch (cc[pos]) {
                case ';':
                    ++pos;
                    prop = null;
                    break;
                default:
                    s = eatUnless(COLON).trim();
                    ++pos;
                    int iname = Parser.stringToEnum(s);
                    if (iname == 0) { // undefined
                        System.out.println("unsupported css property: " + s);
                    }
                    prop = new Pack(5, -1);
                    ret.add(iname).add(prop);
                    state = CSS_VAL_LIST;
                }
                break;
            case CSS_VAL_LIST:
                eatIf(SPACES);
                char c = cc[pos];
                if (c == ';') {
                    state = CSS_PROP_LIST;
                } else {
                    try {
                        s = eatUnless(SPACES_SEMICOLON);
                    } catch (Exception ex) {
                        s = new String(cc, pos, len - pos);
                        pos = len;
                    }
                    eatIf(SPACES);
                    c = s.charAt(0);
                    int slen = s.length();
                    if (c == '#' && slen == 7) { // color #rrggbb
                        prop.add(Integer.parseInt(s.substring(1), 16) & 0x00FFFFFF | C.M_COLOR);
                    } else if (c >= '0' && c <= '9' || c == '-') { // number 0, 25px, -2em, 33%
                        s = s.toLowerCase();
                        if (s.endsWith("%")) {
                            prop.add(Integer.parseInt(s.substring(0, slen - 1)) & 0x00FFFFFF | C.M_PERC);
                        } else { // px or nothing
                            if (s.endsWith("px")) {
                                s = s.substring(0, slen - 2);
                            }
                            prop.add(Integer.parseInt(s) & 0x00FFFFFF | C.M_PX);
                        }
                    } else if (s.toLowerCase().startsWith("url(") && s.endsWith(")")) { // url('')
                        String url = unquote(s.substring(4, s.length() - 1));
                        prop.add(url).add(Parser.stringToEnum("src"));
                    } else { // enum
                        int e = Parser.stringToEnum(s);
                        if (e == C.E_UNDEFINED) {
                            System.out.println("unsupported css value: " + s);
                        }
                        prop.add(e);
                    }
                }
                break;
                
            }
        }
        return ret;
    }
    
    final String eatUnless(char[] chars) {
        StringBuffer buf = new StringBuffer();
        int i, charsLen = chars.length;
        for (i = pos; i < len; i++) {
            char c = cc[i];
            int idx;
            for (idx = charsLen; --idx >= 0;) {
                if (chars[idx] == c) break;
            }
            if (idx >= 0) { // found
                break;
            } else {
                buf.append(c);
            }
        }
        if (i >= len) {
            throw ex(new String(chars));
        }
        pos = i;
        return buf.toString();
    }
    
    final String eatIf(char[] chars) {
        StringBuffer buf = new StringBuffer();
        int i, charsLen = chars.length;
        for (i = pos; i < len; i++) {
            char c = cc[i];
            int idx;
            for (idx = charsLen; --idx >= 0;) {
                if (chars[idx] == c) break;
            }
            if (idx < 0) { // not found
                break;
            } else {
                buf.append(c);
            }
        }
        pos = i;
        return buf.toString();
    }
    
    private static final String unquote(String s) {
        char[] cc = s.toCharArray();
        int len = cc.length;
        char c1 = cc[0], c2 = cc[len - 1];
        if (c1 == '\'' && c2 == '\'' || c1 == '"' && c2 == '"') {
            s = new String(cc, 1, len - 2);
        }
        return s;
    }
    
    public static final Pack split(String s, String delim) {
        Pack ret = new Pack(-1, 4);
        int i1, i2, dlen = delim.length();
        i1 = i2 = 0;
        while (true) {
            i2 = s.indexOf(delim, i1);
            if (i2 < 0) break;
            ret.add(s.substring(i1, i2));
            i1 = i2 + dlen;
        }
        ret.add(s.substring(i1));
        return ret;
    }
    
    public static final int stringToEnum(String s) {
        Integer idx = (Integer) STR_TO_ENUM.get(s.toLowerCase());
        return idx != null ? idx.intValue() : C.E_UNDEFINED;
    }
    
    public static final String htmlText(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        StringBuffer buf = new StringBuffer(s.length());
        char[] cc = s.toCharArray();
        for (int i = 0, prev = 0, n = cc.length; i < n; i++) {
            char c = cc[i];
            if (c != ' ' || prev != ' ') {
                prev = c;
                if (c == '&') {
                    int idx = s.indexOf(';', i);
                    if (idx > 0) {
                        String ent = s.substring(i + 1, idx).toLowerCase();
                        if ("nbsp".equals(ent)) {
                            c = ' ';
                        } else if ("lt".equals(ent)) {
                            c = '<';
                        } else if ("gt".equals(ent)) {
                            c = '>';
                        }
                        i = idx;
                    }
                }
                buf.append(c);
            }
        }
        return buf.toString();
    }

    static {
        Hashtable h = new Hashtable();
        int val = 0, i1 = 0, i2 = 0;
        String key = null;
        while (true) {
            i2 = ENUM_STR.indexOf(',', i1);
            if (i2 < 0) break; // reaches end
            String s = ENUM_STR.substring(i1, i2).trim().toLowerCase();
            char c = s.charAt(0);
            if (c <= '9' && c >= '0') { // it's a number
                val = Integer.parseInt(s);
            } else {
                if (key != null) h.put(key, new Integer(val++));
                key = s;
            }
            i1 = i2 + 1;
        }
        h.put(key, new Integer(val));
        STR_TO_ENUM = h;
    }

    private final RuntimeException ex(String expected) {
        StringBuffer buf = new StringBuffer("ERROR: at position ");
        buf.append(loc(pos));
        buf.append(", expects (").append(expected).append(')');
        return new RuntimeException(buf.toString());
    }
    
    private final String loc(int pos) {
        int i1, i2, row, col;
        i1 = i2 = row = col = 0;
        while (true) {
            i2 = src.indexOf('\n', i1);
            if (pos >= i1 && pos < i2 || i2 < 0) {
                col = pos - i1;
                break;
            }
            i1 = i2 + 1;
            ++row;
        }
        return "row " + (row + 1) + " column " + (col + 1) + " ";
    }
    
}
