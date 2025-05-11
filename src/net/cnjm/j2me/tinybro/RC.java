package net.cnjm.j2me.tinybro;

import net.cnjm.j2me.util.Pack;

class RC {
    
    // All tokens
    static final int TOK_UNKNOWN = 0;
    static final int TOK_NUMBER = 1;
    static final int TOK_STRING = 2;
    static final int TOK_SYMBOL = 3;
    
    static final int TOK_EOL = 10; // \n

    static final int TOK_DOT = '.';
    static final int TOK_LBK = '[';
    static final int TOK_RBK = ']';
    static final int TOK_LBR = '{';
    static final int TOK_RBR = '}';
    static final int TOK_LPR = '(';
    static final int TOK_RPR = ')';
    static final int TOK_COM = ',';
    static final int TOK_COL = ':';
    static final int TOK_SEM = ';';

    static final int DQUOT = '"';
    static final int SQUOT = '\'';
    static final int BACKSLASH = '\\'; // line continue
    
    static final int TOK_ASS = '=';
    static final int TOK_NOT = '!';
    static final int TOK_GRT = '>';
    static final int TOK_LES = '<';
    static final int TOK_ADD = '+';
    static final int TOK_MIN = '-';
    static final int TOK_MUL = '*';
    static final int TOK_DIV = '/';
    static final int TOK_MOD = '%';
    static final int TOK_QMK = '?';
    static final int TOK_BNO = '~';
    static final int TOK_BAN = '&';
    static final int TOK_BXO = '^';
    static final int TOK_BOR = '|';

    static final int ASS_START = 200;
    static final int TOK_EQ = TOK_ASS + ASS_START; // ==
    static final int TOK_NE = TOK_NOT + ASS_START; // !=
    static final int TOK_GE = TOK_GRT + ASS_START; // >=
    static final int TOK_LE = TOK_LES + ASS_START; // <=
    static final int TOK_ADA = TOK_ADD + ASS_START; // +=
    static final int TOK_MIA = TOK_MIN + ASS_START; // -=
    static final int TOK_MUA = TOK_MUL + ASS_START; // *=
    static final int TOK_DIA = TOK_DIV + ASS_START; // /=
    static final int TOK_MOA = TOK_MOD + ASS_START; // %=
    static final int TOK_BAA = TOK_BAN + ASS_START; // &=
    static final int TOK_BXA = TOK_BXO + ASS_START; // ^=
    static final int TOK_BOA = TOK_BOR + ASS_START; // |=

    static final int DBL_START = 400;
    static final int TOK_POW = TOK_MUL + DBL_START; // **
    static final int TOK_INC = TOK_ADD + DBL_START; // ++
    static final int TOK_DEC = TOK_MIN + DBL_START; // --
    static final int TOK_OR  = TOK_BOR + DBL_START; // ||
    static final int TOK_AND = TOK_BAN + DBL_START; // &&
    static final int TOK_LSH = TOK_LES + DBL_START; // <<
    static final int TOK_RSH = TOK_GRT + DBL_START; // >>

    static final int TRI_START = 600;
    static final int TOK_IDN = TOK_ASS + TRI_START; // ===
    static final int TOK_NID = TOK_NOT + TRI_START; // !==
    static final int TOK_LSA = TOK_LES + TRI_START; // <<=
    static final int TOK_RSA = TOK_GRT + TRI_START; // >>=
    static final int TOK_RZA = TOK_RBK + TRI_START; // >>>=
    static final int TOK_RSZ = TOK_RPR + TRI_START; // >>> 

    static final int TOK_IF =           130;
    static final int TOK_ELSE =         131;
    static final int TOK_FOR =          132;
    static final int TOK_WHILE =        133;
    static final int TOK_DO =           134;
    static final int TOK_BREAK =        135;
    static final int TOK_CONTINUE =     136;
    static final int TOK_VAR =          137;
    static final int TOK_FUNCTION =     138;
    static final int TOK_RETURN =       139;
    static final int TOK_WITH =         140;
    static final int TOK_NEW =          141;
    static final int TOK_IN =           142;
    static final int TOK_SWITCH =       143;
    static final int TOK_CASE =         144;
    static final int TOK_DEFAULT =      145;
    static final int TOK_TYPEOF =       146;
    static final int TOK_DELETE =       147;
    static final int TOK_INSTANCEOF =   148;
    static final int TOK_THROW =        149;
    static final int TOK_TRY =          150;
    static final int TOK_CATCH =        151;
    static final int TOK_FINALLY =      152;

    static final int TOK_EOF =          999;
    
    static final int RPN_START =        1000;
    static final int TOK_POS =          TOK_ADD + RPN_START;
    static final int TOK_NEG =          TOK_MIN + RPN_START;
    static final int TOK_INVOKE =       TOK_LPR + RPN_START;
    static final int TOK_INIT =         TOK_NEW + RPN_START;
    static final int TOK_JSONARR =      TOK_LBK + RPN_START;
    static final int TOK_POSTINC =      TOK_INC + RPN_START;
    static final int TOK_POSTDEC =      TOK_DEC + RPN_START;
    static final int TOK_SEP =          TOK_COM + RPN_START;
    static final int TOK_JSONCOL =      TOK_COL + RPN_START;
    static final int TOK_EMPTY =        TOK_RPR;
    
    static final String tokensText(Pack tt, int pos, int len) {
        StringBuffer buf = new StringBuffer();
        for (int i = pos, n = pos + len; i < n; i++) {
            if (i > pos) buf.append(',');
            int ttype = tt.getInt(i);
            buf.append(tokenName(ttype, tt.getObject(i)));
        }
        return buf.toString();
    }
    
    static final String tokenName(int type, Object value) {
        int offset = type >> 16;
        type &= 0xffff;
        if (type == RC.TOK_EOF) {
            return "EOF";
        } else if (type >= RC.RPN_START) {
            switch (type) {
            case RC.TOK_POS:
                return "'positive'";
            case RC.TOK_NEG:
                return "'negative'";
            case RC.TOK_INVOKE:
                return "'invoke'";
            case RC.TOK_INIT:
                return "'init'";
            case RC.TOK_JSONARR:
                return "'json_array'";
            case RC.TOK_POSTINC:
                return "++(p)";
            case RC.TOK_POSTDEC:
                return "'--(p)'";
            case RC.TOK_SEP:
                return "'separator'";
            case RC.TOK_JSONCOL:
                return "'json_colon'";
            }
        } else if (type >= RC.TRI_START) {
            switch (type) {
            case TOK_IDN:
                return "'==='";
            case TOK_NID:
                return "'!=='";
            case TOK_RSZ:
                return "'>>>'";
            case TOK_LSA:
                return "'<<='";
            case TOK_RSA:
                return "'>>='";
            case TOK_RZA:
                return "'>>>='";
            }
        } else if (type >= RC.DBL_START) {
            String soff = offset > 0 ? "+" + offset : "";
            char c;
            return "'" + (c = (char) (type - RC.DBL_START)) + c + soff + "'";
        } else if (type >= RC.ASS_START) {
            return "'" + (char) (type - RC.ASS_START) + "='";
        } else if (type >= RC.TOK_IF) {
            return keywordName(type);
        } else {
            Object val = null;
            if (value != null) {
                Object[] oo;
                val = value instanceof Rv ? value : (oo = (Object[]) value).length > 1 ? oo[1] : null;
            }
            switch (type) {
            case RC.TOK_NUMBER:
                return "[" + val + "]";
            case RC.TOK_STRING:
//                val = ((String) val).replaceAll("[\\r\\n]+", "\\\\n");
                return "\"" + val + "\"";
            case RC.TOK_SYMBOL:
                return "<" + val + ">";
            case RC.TOK_EOL:
                return "EOL";
            default:
                return "'" + (char) type + "'";
            }
        }
        return "[UNKNOWN_TYPE(" + type + ")=" + value + "]";
    }
    
    static final Rhash htKeywordNames;
    
    static {
        Rhash ht = htKeywordNames = new Rhash(41);
        Pack pk = RocksInterpreter.split(RocksInterpreter.KEYWORDS, ",");
        Object[] pkar = pk.oArray;
        for (int i = pk.oSize; --i >= 0;) {
            Rv entry = new Rv();
            entry.obj = pkar[i];
            ht.putEntry(130 + i, null, entry);
        }
    }
    
    private static final String keywordName(int index) {
        return (String) htKeywordNames.getEntry(index, null).obj;
    }
}