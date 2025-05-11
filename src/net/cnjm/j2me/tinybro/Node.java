package net.cnjm.j2me.tinybro;

import java.util.Hashtable;

import javax.microedition.lcdui.*;

import net.cnjm.j2me.util.Pack;

public class Node {
    
    public static final int REMOVE = 1;
    public static final int REPLACE = 2;
    public static final int INSERT_BEFORE = 3;
    public static final int APPEND_CHILD = 4;
    
    public Object owner;
    
    public Node parent;
    
    public Pack children;
    
    /**
     * properties = Pack(n, n) {
     *   { prop-name0, prop-name1... },
     *   { prop-valu0, prop-valu1... },
     * }
     * As script AST node: tokens
     */
    public Pack properties; 
    
    /**
     * including unrecognized properties
     * can be use to store user data
     */
    public Hashtable moreProperties;
    
    /**
     * drawinfo = Object[1 or 4] {
     *   fixed,
     *   changeable0, 
     *   changeable1, 
     *   changeable2, 
     * }
     * fixed = block | t
     * 
     * block = Pack(20 + n + 1, -1) {
     *   rowIdx, offset-x, offset-y, width, height, 
     *   margin <top, right, bottom, left>,
     *   border-width <top, right, bottom, left>
     *   padding <top, right, bottom, left>, 
     *   text-align, 
     *   vertical-align, 
     *   line-height, 
     *   row-offset-y0, row-offset-y1, ..., all-row-height 
     * }
     * t = Pack(7 * n, -1) {
     *   rowIdx0, offset-x0, offset-y0, width0, height0, srcIdx0, srcLen0, ...
     * }
     * changeable = Pack(28, 3) {
     *   {
     *   border-color <top, right, bottom, left>
     *   border-style <top, right, bottom, left>
     *   background-color, 
     *   repeat, positionX, postionY
     *   crop <x, y, width, height>, transform,
     *   font-face(system), font-style(italic|bold|underline), font-size(small|default) 
     *   'x'-height, 'x'-width, 'X'-width, 'жа'-width, 
     *   font-color(black|transparent), shadow-color(transparent), length-x, length-y
     *   }
     *   { image_url, image_object, font-object }
     * }
     */
    protected Pack[] drawinfo;
    
    // As script AST node: function name
    public String id;
    
    /** 
     * As script AST node: 
     *  - switch: branches
     *  - function: className.oArray[0]: callObj
     */ 
    public Pack className;
    
    public int tagType; // enumeration type
    
    // As script AST node: startpos
    public int display; // inline | block | inline-block | none, visible | hidden
    
    // As script AST node: len, if (len >= 0) then the node is raw 
    public int state = C.DI_NORMAL; // normal, hover (focus), active
    
    public Node(Object owner, int tagType) {
        this.owner = owner;
        this.tagType = tagType;
    }
    
    public Node(Object owner, String tagName) {
        this(owner, Parser.stringToEnum(tagName));
        int ttype = tagType;
        int disp = C.DISP_BLOCK;
        Pack[] di = null;
        if (ttype == C.E_BR){
            disp = C.DISP_NONE;
        } else {
            di = new Pack[C.DI_SIZE];
        }
        switch (ttype) {
        case C.E_SPAN:
        case C.E_STRONG:
        case C.E_EM:
        case C.E_B:
        case C.E_I:
        case C.E_A:
        case C.E_T:
            disp = C.DISP_INLINE;
            break;
        case C.E_IMG:
        case C.E_SELECT:
        case C.E_TEXTAREA:
        case C.E_INPUT:
            disp = C.DISP_INBLOCK;
            break;
        }
        this.display = disp;
        this.drawinfo = di;
        if (ttype == C.E_T) {
            di[0] = new Pack(7, -1).setSize(7, -1);
        } else if (ttype != C.E_BR) {
            di[0] = new Pack(22, -1).setSize(20, -1);
        }

        switch (ttype) {
        case C.E_A:
            di[C.DI_NORMAL] = (Pack) DEF_DIC_A.clone();
            di[C.DI_FOCUS] = (Pack) DEF_DIC_AHOVER.clone();
            di[C.DI_ACTIVE] = (Pack) DEF_DIC_AACTIVE.clone();
            break;
        case C.E_TEXTAREA:
        case C.E_SELECT:
        case C.E_INPUT:
            di[C.DI_NORMAL] = (Pack) DEF_DIC_INPUT.clone();
            break;
        } 
    }
    
    /**
     * set display and visibility values, possible values:
     * none, inline, block, inline-block, visible, hidden
     * @param newdisp
     * @return
     */
    public Node setDisplay(int newdisp) {
        int disp = this.display;
        if (newdisp >= C.E_HIDDEN && newdisp <= C.E_VISIBLE) {
            disp = (disp & 0xF0) + (newdisp & 0x0F);
        } else if (newdisp >= C.E_NONE && newdisp <= C.E_INLINE_BLOCK) {
            disp = (disp & 0x0F) + ((newdisp & 0x0F) << 4);
        }
        this.display = disp;
        return this;
    }
    
    public Node appendChild(Node child) {
        if (children == null) {
            children = new Pack(-1, 2);
        }
        child.parent = this;
        children.add(child);
        return this;
    }
    
    public Node getChild(int index) {
        if (children != null && index < children.oSize) {
            return (Node) children.oArray[index];
        }
        return null;
    }
    
    public Node addProperty(String name, String value) {
        int type = Parser.stringToEnum(name);
        if (type == C.E_UNDEFINED) {
            if (moreProperties == null) moreProperties = new Hashtable();
            moreProperties.put(name, value);
        } else {
            if (properties == null) properties = new Pack(2, 2);
            properties.add(type).add(value);
        }
        if (this.tagType == C.E_INPUT && type == C.E_TYPE && 
                C.E_HIDDEN == Parser.stringToEnum(value)) {
            setDisplay(C.DISP_NONE);
        }
        return this;
    }
    
    public String getProperty(String name) {
        Object ret = null;
        if (moreProperties != null) {
            ret = moreProperties.get(name);
        }
        if (ret == null && properties != null) {
            int type = Parser.stringToEnum(name);
            int idx = properties.indexOf(type);
            if (idx >= 0) ret = properties.oArray[idx];
        }
        return (String) ret;
    }
    
    /**
     * return pack(n, n) {
     *   { node_state0, node_state1,... } // i.e. normal, focus, active
     *   { node0, node1,... }
     * }
     * @param selector
     * @return
     */
    public final Pack select(String selector) {
        Pack ret = new Pack(4, 4);
        ret.add(this).add(0);
        Pack sel = new Parser(selector).cssSelector();
        for (int i = 0, n = sel.oSize; i < n; i++) {
            if (ret.oSize > 0) {
                Pack grp = (Pack) sel.oArray[i];
                Pack tmp = new Pack(4, 4);
                for (int ii = ret.oSize; --ii >= 0;) {
                    Node nd = (Node) ret.oArray[ii];
                    for (int j = grp.oSize; --j >= 0;) {
                        int st = 0;
                        String s = (String) grp.oArray[j];
                        int idx = s.lastIndexOf(':');
                        if (idx > 0) {
                            st = Parser.stringToEnum(s.substring(idx + 1));
                            switch (st) {
                            case 1010: // hover
                            case 1011: // focus
                                st = 1;
                                break;
                            case 1012: // active
                                st = 2;
                                break;
                            default:
                                st = 0;
                            }
                            s = s.substring(0, idx);
                        }
                        int type = s.charAt(0);
                        if (type != '#') {
                            idx = s.indexOf('.');
                            String tag = s;
                            if (idx == 0) {
                                tag = null;
                                type = '.';
                                s = s.substring(idx + 1);
                            } else if (idx > 0) {
                                tag = s.substring(0, idx);
                                s = s.substring(idx + 1);
                            } else { // idx < 0
                                s = null;
                            }
                            if (tag != null) type = Parser.stringToEnum(tag);
                        } else { // type == '#'
                            s = s.substring(1);
                        }
                        nd.select(tmp, type, s, st); // TODO ??
                    }
                }
                ret = tmp;
            }
        }
        return ret;
    }
    
    private final void select(Pack out, int type, String txt, int state) {
        int idx = out.indexOf(this);
        if (idx < 0 || out.iArray[idx] != state) {
            boolean add = false;
            switch (type) {
            case '#':
                if (txt.equals(this.id)) {
                    add = true;
                }
                break;
            case '.':
                if (className != null && className.indexOf(txt) >= 0) {
                    add = true;
                }
                break;
            default:
                if (this.tagType == type && 
                        (txt == null || className != null && className.indexOf(txt) >= 0)) {
                    add = true;
                }
                break;
            }
            if (add) out.add(this).add(state);
        }
        if (type != '#' && children != null && children.oSize > 0) {
            Object[] tmp = children.oArray;
            for (int i = children.oSize; --i >= 0;) {
                ((Node) tmp[i]).select(out, type, txt, state);
            }
        }
    }
    
    /**
     * @param option
     * @param src
     */
    public final void update(int option, String src) {
        Pack siblings = option == APPEND_CHILD ? null : parent.children;
        Node n;
        switch (option) {
        case REMOVE:
            siblings.removeObject(siblings.indexOf(this));
            this.owner = null;
            this.parent = null;
            break;
        case REPLACE:
            n = new Parser(src).html((Page) this.owner);
            n.owner = this.owner;
            n.parent = this.parent;
            this.owner = null;
            this.parent = null;
            siblings.set(siblings.indexOf(this), n);
            break;
        case INSERT_BEFORE:
            n = new Parser(src).html((Page) this.owner);
            n.owner = this.owner;
            n.parent = this.parent;
            siblings.add(siblings.indexOf(this), n);
            break;
        case APPEND_CHILD:
            n = new Parser(src).html((Page) this.owner);
            appendChild(n);
            break;
        }
    }
    
    public final void setStyle(int state, Pack style) {
        Pack dest = new Pack(12, -1);
        String bgimg = null;
        for (int i = style.iSize; --i >= 0;) {
            Pack src;
            int srcsize;
            if ((src = (Pack) style.oArray[i]) == null || (srcsize = src.iSize) == 0) continue;
            int type = style.iArray[i];
            int[] val = src.iArray;
            int v0 = val[0];
            switch (type) {
            case C.E_DISPLAY:
            case C.E_VISIBILITY:
                setDisplay(v0);
                break;
            case C.E_COLOR:
                dest.add(C.C_START + C.C_COLOR).add(v0);
                break;
            case C.E_TEXT_SHADOW:
                int idx = 0;
                for (int j = 0; j < srcsize; j++) {
                    int v = val[j];
                    if ((v & ~C.M) == C.M_COLOR) {
                        dest.add(C.C_START + C.C_SHADOW).add(v0);
                    } else if (idx < 2 && (v & ~C.M) == C.M_PX) {
                        dest.add(C.C_START + C.C_LENX + idx++).add(v);
                    }
                }
                break;
            case C.E_FONT:
                for (int j = 0; j < srcsize; j++) {
                    int v = val[j];
                    switch (v) {
                    case C.E_BOLD:
                        dest.add(C.C_START + C.C_FONTSTYLE).add(C.STYLE_BOLD);
                        break;
                    case C.E_ITALIC:
                        dest.add(C.C_START + C.C_FONTSTYLE).add(C.STYLE_ITALIC);
                        break;
                    case C.E_SMALL:
                        dest.add(C.C_START + C.C_FONTSIZE).add(C.SIZE_SMALL);
                        break;
                    case C.E_LARGE:
                        dest.add(C.C_START + C.C_FONTSIZE).add(C.SIZE_LARGE);
                        break;
                    case C.E_MEDIUM:
                        dest.add(C.C_START + C.C_FONTSIZE).add(C.SIZE_MEDIUM);
                        break;
                    case C.E_DEFAULT:
                        dest.add(C.C_START + C.C_FONTSIZE).add(C.E_DEFAULT);
                        break;
                    case C.E_SYSTEM:
                        dest.add(C.C_START + C.C_FONTFACE).add(C.FACE_SYSTEM);
                        break;
                    case C.E_MONOSPACE:
                        dest.add(C.C_START + C.C_FONTFACE).add(C.FACE_MONOSPACE);
                        break;
                    case C.E_PROPORTIONAL:
                        dest.add(C.C_START + C.C_FONTFACE).add(C.FACE_PROPORTIONAL);
                        break;
                    default:
                        if ((v & ~C.M) == C.M_PX) { // line-height
                            dest.add(C.F_LINEH).add(v);
                        }
                    }
                }
                break;
            case C.E_TEXT_DECORATION:
                dest.add(C.C_START + C.C_FONTSTYLE)
                    .add(v0 == C.E_UNDERLINE ? C.STYLE_UNDERLINED : 0);
                break;
            case C.E_LINE_HEIGHT:
                dest.add(C.F_LINEH).add(v0);
                break;
            case C.E_BACKGROUND:
                idx = 0;
                for (int j = 0; j < srcsize; j++) {
                    int v = val[j];
                    if (v == C.E_TRANSPARENT || (v & ~C.M) == C.M_COLOR) {
                        dest.add(C.C_START + C.C_BGCOLOR).add(v);
                    } else if (v >= C.E_REPEAT && v <= C.E_REPEAT_Y) {
                        dest.add(C.C_START + C.C_REPEAT).add(v);
                    } else if (idx < 2 && (v & ~C.M) != 0) {
                        dest.add(C.C_START + C.C_POSX + idx++).add(v);
                    }
                }
                if (src.oSize > 0) {
                    bgimg = (String) src.oArray[0];
                }
                break;
            case C.E_WIDTH:
                dest.add(C.F_WIDTH).add(v0);
                break;
            case C.E_HEIGHT:
                dest.add(C.F_HEIGHT).add(v0);
                break;
            case C.E_TEXT_ALIGN:
                v0 = 0;
                for (int j = 0; j < srcsize; j++) {
                    int v = val[j];
                    if (v >= C.E_LEFT && v <= C.E_JUSTIFY) {
                        v0 += (v & 0x0F) << 4;
                    } else if (v >= C.E_TOP && v <= C.E_BOTTOM) {
                        v0 += v & 0x0F;
                    }
                }
                dest.add(C.F_TEXTALIGN).add(v0);
                break;
            case C.E_VERTICAL_ALIGN:
                if (v0 >= C.E_TOP && v0 <= C.E_BOTTOM) {
                    v0 &= 0x0F;
                    dest.add(C.F_VERTALIGN).add(v0);
                }
                break;
            case C.E_BORDER:
                for (int j = 0; j < srcsize; j++) {
                    int v = val[j];
                    if (v == C.E_SOLID || v == C.E_DOTTED || v == C.E_NONE) {
                        for (int bi = C.C_BST; bi <= C.C_BSL; dest.add(bi++).add(v));
                    } else if ((v & ~C.M) == C.M_PX) {
                        for (int bi = C.F_BT; bi <= C.F_BL; dest.add(bi++).add(v));
                    } else if ((v & ~C.M) == C.M_COLOR) {
                        for (int bi = C.C_BCT; bi <= C.C_BCL; dest.add(bi++).add(v));
                    }
                }
                break;
            case C.E_BORDER_TOP:
            case C.E_BORDER_RIGHT:
            case C.E_BORDER_BOTTOM:
            case C.E_BORDER_LEFT:
                idx = type - C.E_BORDER_TOP;
                for (int j = 0; j < srcsize; j++) {
                    int v = val[j];
                    if (v == C.E_SOLID || v == C.E_DOTTED || v == C.E_NONE) {
                        dest.add(C.C_START + C.C_BST + idx).add(v);
                    } else if ((v & ~C.M) == C.M_PX) {
                        dest.add(C.F_BT + idx).add(v);
                    } else if ((v & ~C.M) == C.M_COLOR) {
                        dest.add(C.C_START + C.C_BCT + idx).add(v);
                    }
                }
                break;
            case C.E_BORDER_WIDTH:
                if (srcsize > 4) srcsize = 4;
                for (int j = 0; j < srcsize; dest.add(C.F_BT + j).add(val[j++]));
                break;
            case C.E_BORDER_COLOR:
                if (srcsize > 4) srcsize = 4;
                for (int j = 0; j < srcsize; dest.add(C.C_START + C.C_BCT + j).add(val[j++]));
                break;
            case C.E_BORDER_STYLE:
                if (srcsize > 4) srcsize = 4;
                for (int j = 0; j < srcsize; dest.add(C.C_START + C.C_BST + j).add(val[j++]));
                break;
            case C.E_MARGIN:
                if (srcsize > 4) srcsize = 4;
                for (int j = 0; j < srcsize; dest.add(C.F_MT + j).add(val[j++]));
                break;
            case C.E_PADDING:
                if (srcsize > 4) srcsize = 4;
                for (int j = 0; j < srcsize; dest.add(C.F_PT + j).add(val[j++]));
                break;
            }
        }
        int[] arr = dest.iArray;
        Pack p;
        for (int i = 0, n = dest.iSize; i < n; i += 2) {
            int idx = arr[i];
            int val = arr[i + 1];
            if (idx >= C.C_START) {
                if ((p = drawinfo[state]) == null) {
                    p = (Pack) DEF_DIC.clone();
                    drawinfo[state] = p;
                }
                idx -= C.C_START;
            } else {
                p = drawinfo[0];
            }
            p.iArray[idx] = val;
        }
        if (bgimg != null) {
            if ((p = drawinfo[state]) == null) {
                p = (Pack) DEF_DIC.clone();
                drawinfo[state] = p;
            }
            p.oArray[C.C_IMGURL] = bgimg;
        }
    }
    
    protected void paint(Graphics g, int x, int y) {
        int type = tagType;
        if (type == C.E_INPUT) {
            int idx = properties.indexOf(C.E_TYPE);
            type = idx < 0 ? C.E_TEXT :
                Parser.stringToEnum((String) properties.oArray[idx]);
        }
        // TODO complete real painting code
        switch (type) {
        case C.E_IMG:
            break;
        case C.E_TEXTAREA:
            break;
        case C.E_SELECT:
            break;
        case C.E_TEXT:
        case C.E_PASSWORD:
            break;
        case C.E_BUTTON:
        case C.E_RESET:
        case C.E_SUBMIT:
            break;
        case C.E_CHECKBOX:
            break;
        case C.E_RADIO:
            break;
        }
    }
    
    static final Pack DEF_DIC;
    static final Pack DEF_DIC_A;
    static final Pack DEF_DIC_AHOVER;
    static final Pack DEF_DIC_AACTIVE;
    static final Pack DEF_DIC_INPUT;
    static final Font DEF_FONT = Font.getDefaultFont();
//    static final Font DEF_FONT = new Font("SimSun", Font.PLAIN, 12);
    
    static {
        int face = C.FACE_SYSTEM; // DEF_FONT.getFace();
        int style = DEF_FONT.getStyle();
        int size = DEF_FONT.getSize();
        
        Pack dic = new Pack(28, 3).setSize(28, 3);
        dic.iArray[C.C_FONTFACE] = face;
        dic.iArray[C.C_FONTSTYLE] = style;
        dic.iArray[C.C_FONTSIZE] = size;
//        dic.oArray[C.C_FONTOBJ] = DEF_FONT;
        DEF_DIC = dic;
        
        dic = (Pack) dic.clone();
        dic.iArray[C.C_FONTSTYLE] = C.STYLE_UNDERLINED;
        dic.iArray[C.C_COLOR] = C.M_COLOR + 0x0000FF; // blue
        DEF_DIC_A = dic;
        
        dic = (Pack) dic.clone();
        dic.iArray[C.C_BGCOLOR] = C.M_COLOR + 0x0000FF; // blue
        dic.iArray[C.C_COLOR] = C.M_COLOR + 0xFFFFFF; // white
        DEF_DIC_AHOVER = dic;
        
        dic = (Pack) dic.clone();
        for (int i = 4; --i >= 0;) {
            dic.iArray[C.C_BST + i] = C.E_DOTTED;
        }
        DEF_DIC_AACTIVE = dic;
        
        dic = (Pack) DEF_DIC.clone();
        for (int i = 4; --i >= 0;) {
            dic.iArray[C.C_BCT + i] = C.M_COLOR + 0x0000FF;
            dic.iArray[C.C_BST + i] = C.E_SOLID;
        }
        DEF_DIC_INPUT = dic;
        
    }
    
    /**
     * for debug only
     */
    public final String toString() {
        StringBuffer buf = new StringBuffer();
        Pack prop = this.properties, cc = this.children;
        if (owner instanceof Page) {
            buf.append("node(").append(C.enumToString(tagType))
                    .append(',').append(cc != null ? cc.oSize : 0)
                    .append(',').append(prop != null ? prop.oSize : 0)
                    .append(") {id='").append(id).append("',class='").append(className).append('\'');
            for (int i = 0, n = prop != null ? prop.oSize : 0; i < n; i++) {
                if (i == 0) buf.append(',');
                buf.append(C.enumToString(prop.iArray[i])).append('=')
                        .append('\'').append(chop((String) prop.oArray[i])).append('\'');
            }
            buf.append('}');
        } else { // RocksInterpreter
            Object[] tinfo = (Object[]) prop.oArray[display];
            Pack l = ((RocksInterpreter) owner).loc(((int[]) tinfo[0])[0]);
            int cclen = cc != null ? cc.oSize : 0;
            buf.append("astNode(").append(RC.tokenName(tagType, null))
                    .append(')').append(cclen > 0 ? "[" + cclen + "]" : "")
                    .append(" at (").append(l.iSize).append(',').append(l.oSize).append(')');
            if (tagType == RC.TOK_FUNCTION) {
                buf.append(" <ID=").append(id).append('>');
            } else if (tagType == RC.TOK_MUL && cc != null) {
                buf.append(" <RPN> { ").append(RC.tokensText(cc, 0, cc.iSize)).append(" }");
            }

            int pos = display, len = state & 0x7FFFFFFF;
            buf.append(" <RAW=").append(pos).append(",").append(len).append("> { ");
            if (len <= 10) {
                buf.append(RC.tokensText(prop, pos, len));
            } else {
                buf.append(RC.tokensText(prop, pos, 5)).append(" ... ")
                        .append(RC.tokensText(prop, pos + len - 5, 5));
            }
            buf.append(" }");
        }
        return buf.toString();
    }
    
    /**
     * for debug only
     * @param s
     * @return
     */
    static final String chop(String s) {
        StringBuffer buf = new StringBuffer();
        char[] cc = s.toCharArray();
        for (int i = 0, n = cc.length > 40 ? 40 : cc.length; i < n; i++) {
            char c = cc[i];
            switch (c) {
            case '\r':
                buf.append("\\r");
                break;
            case '\n':
                buf.append("\\n");
                break;
            case '\t':
                buf.append("\\t");
                break;
            default:
                buf.append(c);
            }
        }
        if (cc.length > 40) {
            buf.append("...");
        }
        return buf.toString();
    }
    
}
