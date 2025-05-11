package net.cnjm.j2me.tinybro;

import java.util.Hashtable;

class C {

    static final int DI_FIXED = 0;
    static final int DI_NORMAL = 1;
    static final int DI_FOCUS = 2; // or hover
    static final int DI_ACTIVE = 3;
    static final int DI_SIZE = 5;
    static final int DI_TMP = DI_SIZE - 1;
    
    static final int TMP_CONT_LEFT = 0;
    static final int TMP_CONT_TOP = 1;
    static final int TMP_OFFSETX = 2;
    static final int TMP_OFFSETY = 3;
    static final int TMP_ROWH = 4;
    static final int TMP_ROWIDX = 5;
    static final int TMP_SIZE = 6;

    static final int M = 0x00FFFFFF;
    static final int M_COLOR = 0x01000000;
    static final int M_PX = 0x02000000;
    static final int M_PERC = 0x04000000;
    
    static final int M_DISPLAY = 0x30; // 0x00: none, 0x10: inline, 0x20: block: 0x30: inline-block
    static final int M_VISIBILITY = 0x01; // 0: hidden, 1: visible
    static final int DISP_NONE = 0;
    static final int DISP_INLINE = 0x11; // 0x80 + 0x00 + 0x01
    static final int DISP_BLOCK = 0x21; // 0x80 + 0x10 + 0x01
    static final int DISP_INBLOCK = 0x31; // 0x80 + 0x20 + 0x01
    
    static final int I_BLOCK = 0;
    static final int I_T = 0;
    
    static final int F_ROWIDX = 0;
    static final int F_OFFSETX = 1;
    static final int F_OFFSETY = 2;
    static final int F_WIDTH = 3;
    static final int F_HEIGHT = 4;
    static final int F_MT = 5;
    static final int F_MR = 6;
    static final int F_MB = 7;
    static final int F_ML = 8;
    static final int F_BT = 9;
    static final int F_BR = 10;
    static final int F_BB = 11;
    static final int F_BL = 12;
    static final int F_PT = 13;
    static final int F_PR = 14;
    static final int F_PB = 15;
    static final int F_PL = 16;
    static final int F_TEXTALIGN = 17;
    static final int F_VERTALIGN = 18;
    static final int F_LINEH = 19;
    static final int F_ROWOFFSET = 20;
    
    static final int T_SRCIDX = 5;
    static final int T_SRCLEN = 6;
    
    static final int C_START = 30;
    static final int C_BCT = 0;
    static final int C_BCR = 1;
    static final int C_BCB = 2;
    static final int C_BCL = 3;
    static final int C_BST = 4;
    static final int C_BSR = 5;
    static final int C_BSB = 6;
    static final int C_BSL = 7;
    static final int C_BGCOLOR = 8;
    static final int C_REPEAT = 9;
    static final int C_POSX = 10;
    static final int C_POSY = 11;
    static final int C_CROPX = 12;
    static final int C_CROPY = 13;
    static final int C_CROPW = 14;
    static final int C_CROPH = 15;
    static final int C_TRANS = 16;
    static final int C_FONTFACE = 17;
    static final int C_FONTSTYLE = 18;
    static final int C_FONTSIZE = 19;
    static final int C_FONTH = 20;
    static final int C_FONTWX1 = 21;
    static final int C_FONTWX2 = 22;
    static final int C_FONTWHZ = 23;
    static final int C_COLOR = 24;
    static final int C_SHADOW = 25;
    static final int C_LENX = 26;
    static final int C_LENY = 27;
    
    static final int C_IMGURL = 0;
    static final int C_IMGOBJ = 1;
    static final int C_FONTOBJ = 2;
    
    static final int STYLE_PLAIN = 0;
    static final int STYLE_BOLD = 1;
    static final int STYLE_ITALIC = 2;
    static final int STYLE_UNDERLINED = 4;
    static final int SIZE_SMALL = 8;
    static final int SIZE_MEDIUM = 0;
    static final int SIZE_LARGE = 16;
    static final int FACE_SYSTEM = 0;
    static final int FACE_MONOSPACE = 32;
    static final int FACE_PROPORTIONAL = 64;
    
    static final Hashtable ENUM_TO_STR;

    static final String enumToString(int n) {
        return (String) ENUM_TO_STR.get(new Integer(n));
    }
    
    static final Hashtable init(boolean output) {
        Hashtable h = new Hashtable();
        Hashtable tmp = new Hashtable();
        int val = 0, i1 = 0, i2 = 0;
        String key = null;
        while (true) {
            i2 = Parser.ENUM_STR.indexOf(',', i1);
            if (i2 < 0) break; // reaches end
            String s = Parser.ENUM_STR.substring(i1, i2).trim().toLowerCase();
            char c = s.charAt(0);
            if (c <= '9' && c >= '0') { // it's a number
                val = Integer.parseInt(s);
            } else {
                if (key != null) {
                    if (tmp.containsKey(key)) {
                        throw new RuntimeException("Duplicated Enumeration " + key);
                    } else {
                        if (output) {
                            System.out.println("    static final int E_" + key.toUpperCase().replace('-', '_') + " = " + val + ";");
                        }
                    }
                    h.put(new Integer(val++), key);
                    tmp.put(key, "");
                }
                key = s;
            }
            i1 = i2 + 1;
        }
        if (output) { 
            System.out.println("    static final int E_" + key.toUpperCase().replace('-', '_') + " = " + val + ";");
        }
        h.put(new Integer(val), key);
        return h;
    }
    
    static {
        ENUM_TO_STR = init(false);
    }

    static final int E_UNDEFINED = 0;
    static final int E_INVALID = 1;
    static final int E_HTML = 10;
    static final int E_HEAD = 11;
    static final int E_LINK = 12;
    static final int E_STYLE = 13;
    static final int E_BODY = 100;
    static final int E_DIV = 101;
    static final int E_H1 = 102;
    static final int E_H2 = 103;
    static final int E_H3 = 104;
    static final int E_H4 = 105;
    static final int E_H5 = 106;
    static final int E_H6 = 107;
    static final int E_P = 108;
    static final int E_HR = 109;
    static final int E_BR = 110;
    static final int E_FORM = 111;
    static final int E_TABLE = 112;
    static final int E_TR = 113;
    static final int E_TD = 114;
    static final int E_OL = 115;
    static final int E_UL = 116;
    static final int E_LI = 117;
    static final int E_POSTFIELD = 118;
    static final int E_LEGEND = 119;
    static final int E_MARQUEE = 120;
    static final int E_OBJECT = 121;
    static final int E_OPTION = 122;
    static final int E_T = 123;
    static final int E_SPAN = 124;
    static final int E_B = 125;
    static final int E_I = 126;
    static final int E_STRONG = 127;
    static final int E_EM = 128;
    static final int E_A = 129;
    static final int E_IMG = 130;
    static final int E_SELECT = 131;
    static final int E_TEXTAREA = 132;
    static final int E_INPUT = 133;
    static final int E_TEXT = 134;
    static final int E_BUTTON = 135;
    static final int E_SUBMIT = 136;
    static final int E_PASSWORD = 137;
    static final int E_RESET = 138;
    static final int E_CHECKBOX = 139;
    static final int E_RADIO = 140;
    static final int E_ID = 300;
    static final int E_CLASS = 301;
    static final int E_TYPE = 302;
    static final int E_NAME = 303;
    static final int E_VALUE = 304;
    static final int E_ACCESSKEY = 305;
    static final int E_SRC = 306;
    static final int E_HREF = 307;
    static final int E_MAXLENGTH = 308;
    static final int E_CONSTRAINTS = 309;
    static final int E_METHOD = 310;
    static final int E_ACTION = 311;
    static final int E_COLSPAN = 312;
    static final int E_ROWSPAN = 313;
    static final int E_CHECKED = 314;
    static final int E_SELECTED = 315;
    static final int E_DISABLED = 316;
    static final int E_ONCLICK = 400;
    static final int E_ONFOCUS = 401;
    static final int E_ONLOAD = 402;
    static final int E_ONSUBMIT = 403;
    static final int E_ONSUBMITTED = 404;
    static final int E_TRUE = 500;
    static final int E_FALSE = 501;
    static final int E_HOVER = 1000;
    static final int E_FOCUS = 1001;
    static final int E_ACTIVE = 1002;
    static final int E_WIDTH = 1100;
    static final int E_HEIGHT = 1101;
    static final int E_FONT = 1102;
    static final int E_COLOR = 1103;
    static final int E_TEXT_DECORATION = 1104;
    static final int E_TEXT_SHADOW = 1105;
    static final int E_LINE_HEIGHT = 1106;
    static final int E_BACKGROUND = 1107;
    static final int E_BORDER = 1108;
    static final int E_BORDER_WIDTH = 1109;
    static final int E_BORDER_COLOR = 1110;
    static final int E_BORDER_STYLE = 1111;
    static final int E_BORDER_TOP = 1112;
    static final int E_BORDER_RIGHT = 1113;
    static final int E_BORDER_BOTTOM = 1114;
    static final int E_BORDER_LEFT = 1115;
    static final int E_MARGIN = 1116;
    static final int E_PADDING = 1117;
    static final int E_TEXT_ALIGN = 1118;
    static final int E_VERTICAL_ALIGN = 1119;
    static final int E_DISPLAY = 1120;
    static final int E_VISIBILITY = 1121;
    static final int E_AUTO = 1200;
    static final int E_NORMAL = 1201;
    static final int E_SYSTEM = 1202;
    static final int E_MONOSPACE = 1203;
    static final int E_PROPORTIONAL = 1204;
    static final int E_DEFAULT = 1205;
    static final int E_BOLD = 1206;
    static final int E_ITALIC = 1207;
    static final int E_UNDERLINE = 1208;
    static final int E_SMALL = 1209;
    static final int E_MEDIUM = 1210;
    static final int E_LARGE = 1211;
    static final int E_TRANSPARENT = 1212;
    static final int E_INVERT = 1213;
    static final int E_NO_REPEAT = 1214;
    static final int E_REPEAT = 1215;
    static final int E_REPEAT_X = 1216;
    static final int E_REPEAT_Y = 1217;
    static final int E_SOLID = 1218;
    static final int E_DOTTED = 1219;
    static final int E_HIDDEN = 1312;
    static final int E_VISIBLE = 1313;
    static final int E_NONE = 1328;
    static final int E_INLINE = 1329;
    static final int E_BLOCK = 1330;
    static final int E_INLINE_BLOCK = 1331;
    static final int E_LEFT = 1344;
    static final int E_CENTER = 1345;
    static final int E_RIGHT = 1346;
    static final int E_JUSTIFY = 1347;
    static final int E_TOP = 1360;
    static final int E_MIDDLE = 1361;
    static final int E_BOTTOM = 1362;
    static final int E_RED = 1500;
    static final int E_GREEN = 1501;
    static final int E_BLUE = 1502;
    static final int E_BLACK = 1503;
    static final int E_WHITE = 1504;
    static final int E_GRAY = 1505;
    static final int E_UP = 1600;
    static final int E_DOWN = 1601;
    static final int E_FIRE = 1602;
    static final int E_C = 1603;
    static final int E_D = 1604;
    static final int E_NUM0 = 1605;
    static final int E_NUM1 = 1606;
    static final int E_NUM2 = 1607;
    static final int E_NUM3 = 1608;
    static final int E_NUM4 = 1609;
    static final int E_NUM5 = 1610;
    static final int E_NUM6 = 1611;
    static final int E_NUM7 = 1612;
    static final int E_NUM8 = 1613;
    static final int E_NUM9 = 1614;
    static final int E_POUND = 1615;
    static final int E_STAR = 1616;
    static final int E_SOFT1 = 1617;
    static final int E_SOFT2 = 1618;

}
