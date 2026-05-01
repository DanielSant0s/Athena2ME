package net.cnjm.j2me.tinybro;

import java.util.*;

import net.cnjm.j2me.util.*;

import net.cnjm.j2me.tinybro.NativeFunction;

public class RocksInterpreter {
    
    public boolean DEBUG = false;

    /**
     * Toggles the final ES6 literal-fold preprocessor pass. Exposed for hosts
     * outside the {@code tinybro} package.
     */
    public static void setPreprocLiteralFold(boolean on) {
        Es6Preproc.ENABLE_LITERAL_FOLD = on;
    }

    /** whether to evaluate in-string expressions in language level */
    public boolean evalString = false;
    
    public String src;
    // array of tokens. a token = (type, { [pos, len], value} )
    public Pack tt;
    public int pos = 0;
    public int endpos = 0;

    public StringBuffer out = new StringBuffer();

    /** Empty varargs pack for {@code invokeJS} with {@code num == 0} (reuse; single-thread JS). */
    public static final Pack EMPTY_ARGS_PACK = new Pack(-1, 0);

    private final int[] errTokPosLen = new int[2];
    private final Object[] errTokPairBuf = new Object[2];
    private final Pack invokeOneArgPack = new Pack(-1, 4);
    private final Pack invokeThreeArgPack = new Pack(-1, 8);

    /** Package-visible: reused 2-argument pack ({@link PromiseRuntime} thenable path). */
    final Pack scratchTwoArgs = new Pack(-1, 4);

    /** Register a {@link NativeFunctionFast} without adding to the global {@link NativeFunctionList}. */
    public final Rv newInlineCapability(NativeFunctionFast f, int argc) {
        Rv ret = new Rv(true, "", argc);
        ret.obj = f;
        return ret;
    }

    /**
     * When true, the next full-range {@link #reset(String, Pack, int, int)} will skip
     * {@link Es6Preproc#process(String)} (caller already loaded preprocessed text from e.g. RMS).
     */
    public boolean skipEs6PreprocessForNextReset;

    /**
     * When false, {@link Es6Preproc#process(String)} is never run (faster; scripts must be ES5/legacy
     * syntax). Hosts set this from boot configuration; default is true.
     */
    public boolean es6PreprocessEnabled = true;

    public RocksInterpreter() {
    }

    public RocksInterpreter(String src, Pack tokens, int pos, int len) {
        reset(src, tokens, pos, len);
    }
    
    public RocksInterpreter reset(String src, Pack tokens, int pos, int len) {
        if (tokens == null && src != null && pos == 0 && len == src.length()) {
            // ES6 desugaring runs only on top-level script sources, not on cached
            // sub-token ranges (where pos/len already reference a preprocessed buffer).
            if (!skipEs6PreprocessForNextReset && es6PreprocessEnabled) {
                String pp = Es6Preproc.process(src);
                if (pp != src) {
                    src = pp;
                    len = pp.length();
                }
            } else {
                skipEs6PreprocessForNextReset = false;
            }
            if (DEBUG) {
                System.out.println("=== preprocessed source (" + src.length() + " chars) ===");
                System.out.println(src);
                System.out.println("=== end preprocessed source ===");
            }
        }
        this.src = src;
        if (tokens == null) {
            tt = tokenize(src, pos, len);
            this.pos = 0;
            this.endpos = tt.oSize;
        } else {
            tt = tokens;
            this.pos = pos;
            this.endpos = pos + len;
        }
        return this;
    }

    /**
     * Parse and run a standalone script fragment in {@code globalObj}'s scope, using the same
     * synthetic top-level function shape as the MIDlet bootstrap. Saves and restores
     * {@link #src}/{@link #tt}/{@link #pos}/{@link #endpos} so the host program keeps a consistent
     * lexer state (unlike {@code eval}, which replaces {@link #src} without restoring).
     */
    public Rv runInGlobalScope(String fragment, Rv globalObj) {
        if (fragment == null) {
            fragment = "";
        }
        String savedSrc = this.src;
        Pack savedTt = this.tt;
        int savedPos = this.pos;
        int savedEndpos = this.endpos;
        try {
            reset(fragment, null, 0, fragment.length());
            Node func = astNode(null, RC.TOK_LBR, 0, 0);
            astNode(func, RC.TOK_LBR, 0, endpos);
            func.referencesArguments = stmtBlockReferencesArguments(func);
            Rv rv = new Rv(false, func, 0);
            rv.co = globalObj;
            return call(false, rv, globalObj, null, null, 0, 0);
        } finally {
            this.src = savedSrc;
            this.tt = savedTt;
            this.pos = savedPos;
            this.endpos = savedEndpos;
        }
    }
    
////////////////////////////// Lexer Method ///////////////////////////

    /**
     * TODO support of native regular expression "/<pattern>/<args>"
     * @param src
     * @param pos
     * @param len
     * @return
     */
    public final Pack tokenize(String src, int pos, int len) {
        char[] cc = src.toCharArray();
        Pack tt = new Pack(150, 50);
        // lexer states:
        // * 0: normal, 
        // * '/': single-line comments, '*': multi-line comments
        // * '\'': single-quote string, '"': double-quote string
        // * '3': triple quote string
        int state = 0; // 
        StringBuffer buf = new StringBuffer();
        int startPos = 0, endpos = pos + len;
        boolean continueline = false;

        boolean afterDot = false;
        
        while (pos < endpos) {
mainswitch:
            switch (state) {
            case 0: 
                char c = cc[pos];
                // skip white spaces { ' ', '\t', '\r' }
                for (; c == ' ' || c == '\t' || c == '\r';) {
                    ++pos;
                    if (pos < endpos) {
                        c = cc[pos];
                    } else {
                        break mainswitch;
                    }
                }
                if (continueline && c == RC.TOK_EOL) {
                    ++pos;
                    continueline = false;
                } else if (c >= '0' && c <= '9'
                        || c == '.' && pos + 1 < endpos && cc[pos + 1] >= '0' && cc[pos + 1] <= '9') { // number
                    int next, p = pos;
                    if (c == '0' && pos + 1 < endpos && ((next = cc[pos + 1]) == 'x' || next == 'X')) {
                        int d;
                        for (d = 8, pos += 2, c = cc[pos]; --d >= 0 
                                && (c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F');
                                c = cc[++pos])
                            ;
                        int ival = (int) Long.parseLong(new String(cc, p + 2, pos - p - 2), 16);
                        addToken(tt, RC.TOK_NUMBER, p, pos - p, new Rv(ival));
                    } else {
                        if (c == '.') {
                            ++pos;
                            c = pos < endpos ? cc[pos] : 0;
                            while (c >= '0' && c <= '9') {
                                ++pos;
                                c = pos < endpos ? cc[pos] : 0;
                            }
                        } else {
                            while (c >= '0' && c <= '9') {
                                ++pos;
                                c = pos < endpos ? cc[pos] : 0;
                            }
                            if (c == '.') {
                                ++pos;
                                c = pos < endpos ? cc[pos] : 0;
                                while (c >= '0' && c <= '9') {
                                    ++pos;
                                    c = pos < endpos ? cc[pos] : 0;
                                }
                            }
                        }
                        if (c == 'e' || c == 'E') {
                            ++pos;
                            c = pos < endpos ? cc[pos] : 0;
                            if (c == '+' || c == '-') {
                                ++pos;
                                c = pos < endpos ? cc[pos] : 0;
                            }
                            while (c >= '0' && c <= '9') {
                                ++pos;
                                c = pos < endpos ? cc[pos] : 0;
                            }
                        }
                        String sub = new String(cc, p, pos - p);
                        addToken(tt, RC.TOK_NUMBER, p, pos - p, new Rv(Double.parseDouble(sub)));
                    }
                    afterDot = false; 
                } else if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'
                    || c == '_' || c == '$') { // symbol or keyword
                    int p = pos;
                    while (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'
                            || c >= '0' && c <= '9'
                            || c == '_' || c == '$') {
                        ++pos;
                        c = pos < endpos ? cc[pos] : 0;
                    }
                    String symb = new String(cc, p, pos - p);
                    int iKey = keywordIndex(symb);
                    // ES6 aliases: "let" and "const" behave as "var" at the token level.
                    // Real block-scoping for let/const is layered on top in the parser.
                    if (iKey < 0 && ("let".equals(symb) || "const".equals(symb))) {
                        iKey = RC.TOK_VAR;
                    }

                    if (afterDot) {
                        addToken(tt, RC.TOK_SYMBOL, p, pos - p, symb);
                    } else {
                        addToken(tt, iKey < 0 ? RC.TOK_SYMBOL : iKey, p, pos - p, symb);
                    }
                    
                    afterDot = false; 
                } else {
                    switch (c) {
                    case RC.TOK_SEM: // ;

                        if (!shouldIgnoreSemicolon(tt)) {
                            addToken(tt, c, pos, 1, null);
                        }
                        pos++;
                        afterDot = false;
                        break;
                    case RC.TOK_DOT: // .
                        addToken(tt, c, pos++, 1, null);

                        afterDot = true;
                        break;
                    case RC.TOK_EOL: // \n
                    case RC.TOK_LBK: // [
                    case RC.TOK_RBK: // ]
                    case RC.TOK_LBR: // {
                    case RC.TOK_RBR: // }
                    case RC.TOK_LPR: // (
                    case RC.TOK_RPR: // )
                    case RC.TOK_COM: // ,
                    case RC.TOK_COL: // :
                    case RC.TOK_QMK: // ?
                    case RC.TOK_BNO: // ~
                        addToken(tt, c, pos++, 1, null);
                        afterDot = false;
                        break;
                    case RC.BACKSLASH: // \
                        ++pos;
                        continueline = true;
                        afterDot = false;
                        break;
                    case RC.DQUOT: // ", """
                    case RC.SQUOT: // '
                        if (c == '"' && pos + 2 < endpos && cc[pos + 1] == '"' && cc[pos + 2] == '"') {
                            state = 3;
                            pos += 3;
                            startPos = pos;
                        } else {
                            state = c; // '\'' or '"'
                            startPos = ++pos;
                        }
                        afterDot = false;
                        break;
                    case RC.TOK_MOD: // %, %=
                    case RC.TOK_BXO: // ^, ^=
                    case RC.TOK_ADD: // +, +=, ++
                    case RC.TOK_MIN: // -, -=, --
                    case RC.TOK_MUL: // *, *=, **
                    case RC.TOK_BOR: // |, |=, ||
                    case RC.TOK_BAN: // &, &=, &&
                    case RC.TOK_ASS: // =, ==, ===
                    case RC.TOK_NOT: // !, !=, !==
                    case RC.TOK_DIV: // /, /=, //, /*
                    case RC.TOK_GRT: // >, >=, >>, >>=, >>>, >>>=
                    case RC.TOK_LES: // <, <=, <<, <<=
                        int nextp, nextc = (nextp = pos + 1) < endpos ? cc[nextp] : 0;
                        int tokinc = 0, posinc = 1;
                        if (c == '/' && (nextc == '/' || nextc == '*')) {
                            state = nextc; // '/' or '*'
                            pos += 2;
                            startPos = pos;
                            break;
                        } else if (nextc == '=') {
                            if ((c == '=' || c == '!') && pos + 2 < endpos && cc[pos + 2] == '=') {
                                tokinc = RC.TRI_START;
                                posinc = 3;
                            } else {
                                tokinc = RC.ASS_START;
                                ++posinc;
                            }
                        } else if (nextc == c) {
                            int nnext = pos + 2 < endpos ? cc[pos + 2] : 0;
                            if (c == '>' || c == '<') {
                                if (nnext == '=') { // <<=, >>=
                                    tokinc = RC.TRI_START;
                                    posinc = 3;
                                } else if (c == '>' && nnext == '>') { // >>>, >>>=
                                    if (pos + 3 < endpos && cc[pos + 3] == '=') { // >>>=
                                        addToken(tt, RC.TOK_RZA, pos, 4, null);
                                        pos += 4;
                                    } else { // >>>
                                        addToken(tt, RC.TOK_RSZ, pos, 3, null);
                                        pos += 3;
                                    }
                                    break;
                                } else { // <<, >>
                                    tokinc = RC.DBL_START;
                                    ++posinc;
                                }
                            } else if (c != '%' && c != '^') {
                                tokinc = RC.DBL_START;
                                ++posinc;
                            }
                        }
                        addToken(tt, c + tokinc, pos, posinc, null);
                        pos += posinc;
                        afterDot = false;
                        break;
                    default:
                        throw ex(c, pos, null);
                    }
                }
                break;
            case '\'':
            case '"':
            case 3: // """: triple quote string
                c = cc[pos];
                if (state == 3 && c == '"' && pos + 2 < endpos && cc[pos + 1] == '"' && cc[pos + 2] == '"') {
                    addToken(tt, RC.TOK_STRING, startPos, pos - startPos, buf.toString());
                    buf.setLength(0);
                    pos += 3;
                    state = 0;
                    afterDot = false;
                } else if (state != 3 && c == state) {
                    addToken(tt, RC.TOK_STRING, startPos, pos - startPos, buf.toString());
                    buf.setLength(0);
                    ++pos;
                    state = 0;
                    afterDot = false;
                } else if (state != 3 && c == RC.TOK_EOL) {
                    throw ex(c, pos, String.valueOf((char) state));
                } else if (c == '\\' && pos + 1 < endpos){
                    char nc = cc[pos + 1];
                    int inc = 2;
                    switch (nc) {
                    case 'n':
                        buf.append('\n');
                        break;
                    case 'r':
                        buf.append('\r');
                        break;
                    case 't':
                        buf.append('\t');
                        break;
                    case 'u':
                        if (pos + 5 < endpos) {
                            char uc = (char) Integer.parseInt(new String(cc, pos + 2, 4), 16);
                            buf.append(uc);
                            inc = 6;
                        }
                        break;
                    case '\\':
                    case '\'':
                    case '"':
                        buf.append(nc);
                        break;
                    default:
                        inc = 1;
                        buf.append(c);
                        break;
                    }
                    pos += inc;
                } else {
                    buf.append(c);
                    ++pos;
                }
                break;
            case '/': // single-line comment
                c = cc[pos++];
                if (c == RC.TOK_EOL) {
                    state = 0;
                }
                break;
            case '*': // multi-line comment
                c = cc[pos++];
                if (c == '*' && pos < endpos && cc[pos] == '/') {
                    ++pos;
                    state = 0;
                }
                break;
            }
        }
        addToken(tt, RC.TOK_EOL, pos, 0, null);
        
        return tt;
    }

    /**
     * ASI-like heuristic: drop a redundant ';' when the previous token already
     * terminates a statement (block close '}' or a newline). We intentionally do
     * NOT drop ';' after ')' or ']' — those close sub-expressions and a
     * following ';' is a mandatory separator (classic example: the init/cond
     * separators inside a {@code for(init; cond; update)} header, which in turn
     * routinely end with ')' when the init contains a parenthesised expression).
     *
     * <p>Upstream RockScript dropped ';' after ')' and ']' unconditionally, which
     * quietly broke every {@code for (...0); cond; ...)} header where the init
     * expression finished with a closing paren — the tokenizer swallowed the
     * first separator, collapsing init+cond into a single clause and later
     * tripping eatUntil() with an unmatched ')'.</p>
     */
    private boolean shouldIgnoreSemicolon(Pack tokens) {
        if (tokens.oSize <= 0) {
            return false;
        }

        int lastTok = tokens.oSize - 1;
        int lastToken = tokens.getInt(lastTok * RC.LEX_STRIDE);

        return lastToken == RC.TOK_RBR || // }
               lastToken == RC.TOK_EOL;   // \n
    }

    /** Error context for lexer token at index {@code tokIdx} (reuses internal buffers). */
    final Object[] makeErrTokPair(int tokIdx) {
        int b = tokIdx * RC.LEX_STRIDE;
        errTokPosLen[0] = tt.iArray[b + 1];
        errTokPosLen[1] = tt.iArray[b + 2];
        errTokPairBuf[0] = errTokPosLen;
        errTokPairBuf[1] = tt.oArray[tokIdx];
        return errTokPairBuf;
    }

    private static String tokenSymbolName(Pack tokens, int tokIdx) {
        Object val = tokenValue(tokens, tokIdx);
        return val instanceof Rv ? ((Rv) val).str : (String) val;
    }

    private static Object tokenValue(Pack tokens, int tokIdx) {
        Object val = tokens.getObject(tokIdx);
        if (val instanceof Rv) {
            return val;
        }
        if (val instanceof Object[]) {
            Object[] pair = (Object[]) val;
            return pair.length > 1 ? pair[1] : null;
        }
        return val;
    }

    private static Rv rpnOperandValue(int token, Object val) {
        if (token == RC.TOK_STRING) {
            return val instanceof Rv ? (Rv) val : Rv.stringLiteral((String) val);
        }
        if (token == RC.TOK_SYMBOL) {
            return val instanceof Rv ? (Rv) val : Rv.symbol((String) val);
        }
        return (Rv) val;
    }
    
////////////////////////////// Parser Methods ///////////////////////////
    
    final void statements(Rv callObj, Node node, int loop) {
        int[] tti = tt.iArray;
        int endpos = this.endpos;
        while (pos < endpos && loop-- != 0) {
            int t;
            if ((t = tti[pos * RC.LEX_STRIDE]) == RC.TOK_EOL) {
                t = eat(RC.TOK_EOL);
            }
            if (pos >= endpos) break;
            // `async function` — preprocessor normally removes `async`; accept it here too.
            if (t == RC.TOK_ASYNC && pos + 1 < endpos && tti[(pos + 1) * RC.LEX_STRIDE] == RC.TOK_FUNCTION) {
                pos++;
                t = RC.TOK_FUNCTION;
            }
            int posmk = pos++;
            switch (t) {
//            case RC.TOK_SEM: // blank statement
//                break;
            case RC.TOK_IF:
                Node n = astNode(node, t, posmk, 0);
                eat(RC.TOK_LPR);
                astNode(n, RC.TOK_MUL, pos, eatUntil(RC.TOK_RPR, 0)); // * = exp
                eat(RC.TOK_RPR);
                statements(callObj, n, 1);
                if (pos < endpos && tti[pos * RC.LEX_STRIDE] == RC.TOK_ELSE) {
                    ++pos;
                    n.tagType = RC.TOK_ELSE;
                    statements(callObj, n, 1);
                }
                break;
            case RC.TOK_WHILE:
            case RC.TOK_WITH:
                n = astNode(node, t, posmk, 0);
                eat(RC.TOK_LPR);
                astNode(n, RC.TOK_MUL, pos, eatUntil(RC.TOK_RPR, 0)); // * = exp
                eat(RC.TOK_RPR);
                statements(callObj, n, 1);
                break;
            case RC.TOK_DO:
                n = astNode(node, t, posmk, 0);
                statements(callObj, n, 1);
                eat(RC.TOK_WHILE);
                eat(RC.TOK_LPR);
                astNode(n, RC.TOK_MUL, pos, eatUntil(RC.TOK_RPR, 0)); // * = exp
                eat(RC.TOK_RPR);
                break;
            case RC.TOK_FOR:
                n = astNode(node, t, posmk, 0);
                eat(RC.TOK_LPR);
                int p = pos;
                eatUntil(RC.TOK_SEM, RC.TOK_RPR);
                if ((t = tti[pos * RC.LEX_STRIDE]) == RC.TOK_RPR) { // ';' not found, this is a "for ... in"
                    pos = p; // go back
                    n.tagType = RC.TOK_IN;
                    astNode(n, RC.TOK_MUL, pos, eatUntil(RC.TOK_IN, 0));
                    eat(RC.TOK_IN);
                } else { // found ';'
                    astNode(n, RC.TOK_MUL, p, pos - p);
                    eat(t); // skip ';'
                    astNode(n, RC.TOK_MUL, pos, eatUntil(RC.TOK_SEM, 0)); // * = exp
                    eat(RC.TOK_SEM);
                }
                astNode(n, RC.TOK_MUL, pos, eatUntil(RC.TOK_RPR, 0));
                eat(RC.TOK_RPR);
                statements(callObj, n, 1);
                break;
            case RC.TOK_SWITCH:
                n = astNode(node, t, posmk, 0);
                eat(RC.TOK_LPR);
                astNode(n, RC.TOK_MUL, pos, eatUntil(RC.TOK_RPR, 0));
                eat(RC.TOK_RPR);
                eat(RC.TOK_LBR);
                astNode(n, RC.TOK_LBR, pos, eatUntil(RC.TOK_RBR, 0));
                eat(RC.TOK_RBR);
                break;
            case RC.TOK_CASE:
                n = astNode(node, t, posmk, 0);
                astNode(n, RC.TOK_MUL, pos, eatUntil(RC.TOK_COL, 0));
                eat(RC.TOK_COL);
                break;
            case RC.TOK_DEFAULT:
                n = astNode(node, t, posmk, 1);
                eat(RC.TOK_COL);
                break;
            case RC.TOK_FUNCTION:
                n = astNode(null, t, posmk, 0);
                int pp = pos;
                eatUntil(RC.TOK_LPR, 0);
                boolean findname = false;
                Rv func = null;
                String funcId = null;
                for (int ii = pos; --ii >= pp;) {
                    if (tti[ii * RC.LEX_STRIDE] == RC.TOK_SYMBOL) {
                        funcId = n.id = tokenSymbolName(tt, ii);
                        func = new Rv(false, n, 0);
                        func.co.prev = callObj;
                        callObj.putl(funcId, func);
                        findname = true;
                        break;
                    }
                }
                if (!findname) throw ex(tti[pos * RC.LEX_STRIDE], makeErrTokPair(pos), "function name");
                funcBody(n);
                func.num = n.children.oSize - 1;
                break;
            case RC.TOK_TRY:
                n = astNode(node, t, posmk, 0);
                eat(RC.TOK_LBR);
                Node fn = astNode(n, RC.TOK_FUNCTION, pos, 0);
                astNode(fn, RC.TOK_LBR, pos, eatUntil(RC.TOK_RBR, 0)); 
                eat(RC.TOK_RBR);
                computeFunctionReferencesArguments(fn);
                boolean hascatch = false, hasfinally = false;
                if (tti[pos * RC.LEX_STRIDE] == RC.TOK_CATCH) {
                    eat(RC.TOK_CATCH);
                    eat(RC.TOK_LPR);
                    fn = astNode(n, RC.TOK_FUNCTION, pos, 0);
                    astNode(fn, RC.TOK_MUL, pos, eatUntil(RC.TOK_RPR, 0));
                    eat(RC.TOK_RPR);
                    eat(RC.TOK_LBR);
                    astNode(fn, RC.TOK_LBR, pos, eatUntil(RC.TOK_RBR, 0)); 
                    eat(RC.TOK_RBR);
                    computeFunctionReferencesArguments(fn);
                    hascatch = true;
                }
                if (pos < endpos && tti[pos * RC.LEX_STRIDE] == RC.TOK_FINALLY) {
                    if (!hascatch) {
                        fn = astNode(n, RC.TOK_FUNCTION, pos, 0);
                    }
                    eat(RC.TOK_FINALLY);
                    eat(RC.TOK_LBR);
                    fn = astNode(n, RC.TOK_FUNCTION, pos, 0);
                    astNode(fn, RC.TOK_LBR, pos, eatUntil(RC.TOK_RBR, 0)); 
                    eat(RC.TOK_RBR);
                    computeFunctionReferencesArguments(fn);
                    hasfinally = true;
                }
                if (!hasfinally && !hascatch) { // try only
                    throw ex(tti[pos * RC.LEX_STRIDE], makeErrTokPair(pos), "catch/finally");
                }
                if (!hasfinally) {
                    fn = astNode(n, RC.TOK_FUNCTION, pos, 0);
                }
                break;
            case RC.TOK_RETURN:
            case RC.TOK_THROW:
                n = astNode(node, t, posmk, 0);
                astNode(n, RC.TOK_MUL, pos, eatUntil(RC.TOK_EOL, RC.TOK_SEM));
                if (pos < endpos) eat(tti[pos * RC.LEX_STRIDE]); // skip eol or ';'
                break;
            case RC.TOK_BREAK:
            case RC.TOK_CONTINUE:
                astNode(node, t, posmk, 1);
                break;
            case RC.TOK_LBR:
                n = astNode(node, t, pos, eatUntil(RC.TOK_RBR, 0)); // '{' = block
                eat(RC.TOK_RBR);
                break;
            default: // expression
                pos = posmk; // pos was increased by default
                astNode(node, RC.TOK_MUL, posmk, eatUntil(RC.TOK_EOL, RC.TOK_SEM));
                if (pos < endpos) eat(tti[pos * RC.LEX_STRIDE]); // skip eol or ';'
                break;
            }
        }
    }

    private void exprRpnPush(int op, Object c) {
        if (exprRpnOps == null) {
            exprRpnOps = new int[8];
            exprRpnConsts = new Object[8];
        } else if (exprRpnSize >= exprRpnOps.length) {
            int n = exprRpnOps.length * 2;
            int[] no = new int[n];
            Object[] nc = new Object[n];
            System.arraycopy(exprRpnOps, 0, no, 0, exprRpnSize);
            System.arraycopy(exprRpnConsts, 0, nc, 0, exprRpnSize);
            exprRpnOps = no;
            exprRpnConsts = nc;
        }
        exprRpnOps[exprRpnSize] = op;
        exprRpnConsts[exprRpnSize] = c;
        ++exprRpnSize;
    }
    
    final void expression(Node node, Rv callObj) {
        int[] tti = tt.iArray;
        int endpos = this.endpos, prev, cocnt, hint = endpos - pos;
        int state; // 1: normal, 2: invoke, 3: json array, 4: json object
        prev = cocnt = state = 0;
        if (hint < 8) {
            hint = 8;
        }
        if (exprRpnOps == null || exprRpnOps.length < hint) {
            exprRpnOps = new int[hint];
            exprRpnConsts = new Object[hint];
        }
        exprRpnSize = 0;
        if (exprOpPack == null) {
            exprOpPack = new Pack(20, 20);
        } else {
            exprOpPack.iSize = 0;
            exprOpPack.oSize = 0;
        }
        Pack op = exprOpPack;
        if (exprStPack == null) {
            exprStPack = new Pack(10, -1);
        } else {
            exprStPack.iSize = 0;
        }
        Pack st = exprStPack;
        int[] opidx = optrIndex;
        int[][] ptab = prioTable;
        boolean isNew = false;
mainloop:
        while (pos <= endpos) {
            boolean noeof;
            int t = (noeof = pos < endpos) ? tti[pos * RC.LEX_STRIDE] : RC.TOK_EOF;
            Object tokObj = noeof ? tokenValue(tt, pos) : null;
            switch (t) {
            case RC.TOK_NEW: // fall throuth
                // TODO handle new Array;
                isNew = true;
            case RC.TOK_EOL:
                ++pos;
                continue mainloop;
            case RC.TOK_COM:
                if (state > 1) {
                    t = RC.TOK_SEP;
                    if (state == 3 && (prev == RC.TOK_SEP || prev == RC.TOK_JSONARR)) { // handle arr = [1,,,2]
                        exprRpnPush(RC.TOK_NUMBER, Rv._undefined);
                    }
                }
                ++cocnt;
                break;
            case RC.TOK_COL:
                if (state == 4) t = RC.TOK_JSONCOL;
                break;
            case RC.TOK_LPR:
            case RC.TOK_LBK:
            case RC.TOK_INC:
            case RC.TOK_DEC:
            case RC.TOK_MIN:
            case RC.TOK_ADD:
                boolean prevSym = prev > 0 && prev <= RC.TOK_SYMBOL // NUMBER, STRING or SYMBOL
                        || prev == RC.TOK_RPR || prev == RC.TOK_RBK || prev == RC.TOK_RBR;
                boolean isBkOrMin = t == RC.TOK_LBK || t == RC.TOK_MIN || t == RC.TOK_ADD;
                if (prevSym && !isBkOrMin // foo(), a++  
                        || !prevSym && isBkOrMin) { // a = [1, 2], 12 + -5
                    t += RC.RPN_START;
                    if (t == RC.TOK_INVOKE && isNew) {
                        t = RC.TOK_INIT;
                        isNew = false;
                    }
                }
                break;
            case RC.TOK_FUNCTION:
                Node n = astNode(null, t, pos, 0);
                eat(RC.TOK_FUNCTION); // skip "function"
                String id = null;
                if (tti[pos * RC.LEX_STRIDE] == RC.TOK_SYMBOL) { // named function
                    id = tokenSymbolName(tt, pos++);
                } else {
                    id = "$direct_func$" + pos;
                }
                n.id = id;
                funcBody(n);

                Rv func = new Rv(false, n, 0);
                Rv go;
                for (go = callObj; go.prev != null; go = go.prev);
                func.co.prev = go;
                go.putl(id, func);

                func.num = n.children.oSize - 1;
                exprRpnPush(RC.TOK_SYMBOL, Rv.symbol("Function"));
                exprRpnPush(RC.TOK_SYMBOL, Rv.symbol(id));
                exprRpnPush(RC.TOK_NUMBER, Rv._true); // numArgs = 1
                exprRpnPush(RC.TOK_INIT, new Rv());
                prev = RC.TOK_INIT;
                continue mainloop;
            }
            while (t != 0) {
                int top = op.iSize > 0 ? op.iArray[op.iSize - 1] : RC.TOK_EMPTY;
                int offset = top >>> 16;
                top &= 0xffff;
                int row = t < OPTR_TABLE_SIZE ? opidx[t] : -1;
                int col = top < OPTR_TABLE_SIZE ? opidx[top] : -1;
                if (row == -1 || col == -1) {
                    throw ex(t, makeErrTokPair(pos), "stack top: " + RC.tokenName(top, null));
                }
                int act = ptab[row][col];
                int newstt = (act >> 1) & 0x7, newact = (act >> 4) & 0x7, consume = act & 0x1;

                switch (newact) {
                case 2: // p
                    op.removeInt(-1);
                    op.removeObject(-1);
                    break;
                case 3: // q
                    op.removeInt(-1); // pop '?'
                    op.removeObject(-1);
                    op.add(t).add(new Integer(pos));
                    break;
                case 4: // n
                    Object o = tokObj;
                    Rv val = rpnOperandValue(t, o);
                    exprRpnPush(t, val); // this must be an operand
                    break;
                case 5: // <
                    if (top == RC.TOK_INVOKE || top == RC.TOK_INIT 
                            || top == RC.TOK_JSONARR || top == RC.TOK_LBR) {
                        int inc = prev == RC.TOK_SEP || prev == top ? 0 : 1;
                        exprRpnPush(RC.TOK_NUMBER, new Rv(cocnt + inc));
                    }
                    if (top == RC.TOK_AND || top == RC.TOK_OR) {
                        exprRpnOps[offset] += exprRpnSize << 16;
                    }
                    op.removeInt(-1);
                    op.removeObject(-1);
                    exprRpnPush(top,
                            (top == RC.TOK_INVOKE || top == RC.TOK_INIT) ? new InvokeOpRv() : new Rv(0)); // operator cell
                    break;
                case 6: // >
                    int newt = t;
                    if (t == RC.TOK_AND || t == RC.TOK_OR) {
                        newt = t + (exprRpnSize << 16);
                        exprRpnPush(t, new Rv(0)); // this is an operator
                    }
                    op.add(newt).add(new Integer(pos));
                    break;
                case 7: // x
                    throw ex(t, makeErrTokPair(pos), "stack top: " + RC.tokenName(top, null));
                }
                if (consume > 0) {
                    prev = t;
                    t = 0;
                    if (newstt == 7) {
                        cocnt = st.removeInt(-1);
                        state = st.removeInt(-1);
                    } else if (newstt > 0) {
                        st.add(state).add(cocnt);
                        state = newstt;
                        cocnt = 0;
                    }
                }
            }
            ++pos;
        }
        --pos; // let pos = endpos
        int n = exprRpnSize;
        if (n == 0) {
            node.rpnOps = null;
            node.rpnConsts = null;
            node.rpnLen = 0;
        } else {
            int[] ops = new int[n];
            Object[] consts = new Object[n];
            System.arraycopy(exprRpnOps, 0, ops, 0, n);
            System.arraycopy(exprRpnConsts, 0, consts, 0, n);
            node.rpnOps = ops;
            node.rpnConsts = consts;
            node.rpnLen = n;
        }
        node.children = null;
    }

////////////////////////////// Interpreter Methods ///////////////////////////
    
    /**
     * Evaluate an expression node
     * @param callObj
     * @param node
     * @return
     */
    public final Rv eval(Rv callObj, Object node) {
        Node nd;
        if ((nd = (Node) node).state >= 0) { // not resolved
            this.reset(src, nd.properties, nd.display, nd.state);
            expression(nd, callObj);
            nd.state |= 0x80000000;
        }
        if (DEBUG) {
            System.out.println("EVAL_EXP: " + node);
            System.out.println("CALL_OBJ: " + callObj);
        }
        // node must be a expression node
        int n = nd.rpnLen;
        if (n == 0) {
            return Rv._undefined;
        }
        int[] tt = nd.rpnOps;
        Object[] to = nd.rpnConsts;
        // ---- acquire an operand stack from the pool ----
        Pack[] pool = opndPool;
        int depth = evalDepth;
        if (depth >= pool.length) {
            Pack[] grown = new Pack[pool.length * 2];
            System.arraycopy(pool, 0, grown, 0, pool.length);
            opndPool = pool = grown;
            Rv[][] tempGrown = new Rv[grown.length][];
            System.arraycopy(evalTempPool, 0, tempGrown, 0, evalTempPool.length);
            evalTempPool = tempGrown;
            int[] usedGrown = new int[grown.length];
            System.arraycopy(evalTempUsed, 0, usedGrown, 0, evalTempUsed.length);
            evalTempUsed = usedGrown;
        }
        Pack opnd = pool[depth];
        if (opnd == null) {
            opnd = new Pack(-1, 16);
            pool[depth] = opnd;
        } else {
            opnd.iSize = 0;
            opnd.oSize = 0;
        }
        evalTempUsed[depth] = 0;
        evalDepth = depth + 1;
        try {
        boolean isLocal = false;
        int[] _optrType = optrType;
        for (int i = 0; i < n; i++) {
            int t = tt[i];
            int offset = t >> 16;
            t &= 0xffff;
            int cat = t < OPTR_TABLE_SIZE ? _optrType[t] : 0;
            switch (cat) {
            case 1: // unary op
                Rv o = (Rv) opnd.oArray[opnd.oSize - 1];
                opnd.oArray[opnd.oSize - 1] = ((Rv) to[i]).unary(callObj, t, o, acquireEvalTemp(depth));
                break;
            case 2: // binary op
                Rv o2 = ((Rv) opnd.oArray[--opnd.oSize]).evalVal(callObj);
                Rv o1 = ((Rv) opnd.oArray[opnd.oSize - 1]).evalVal(callObj);
                opnd.oArray[opnd.oSize - 1] = ((Rv) to[i]).binary(t, o1, o2);
                break;
            case 3: // assign
                int next = i + 1 < n ? tt[i + 1] : RC.TOK_EOF;
                if (!isLocal && next == RC.TOK_VAR) {
                    isLocal = true;
                    next = RC.TOK_COM;
                }
                o2 = ((Rv) opnd.oArray[--opnd.oSize]).evalVal(callObj);
                o1 = ((Rv) opnd.oArray[opnd.oSize - 1]).evalRef(callObj, acquireEvalTemp(depth));
                String symname = o1.str;
                if (isLocal && next == RC.TOK_COM) {
                    callObj.putl(symname, Rv._undefined);
                }
                opnd.oArray[opnd.oSize - 1] = ((Rv) to[i]).assign(callObj, t, o1, o2);
                break;
            default: // misc op
                int num = 0;
                switch (t) {
                case RC.TOK_AND:
                case RC.TOK_OR:
                    if (offset > 0) {
                        o = ((Rv) opnd.oArray[opnd.oSize - 1]).evalVal(callObj);
                        boolean b, or = t == RC.TOK_OR;
                        if ((b = o.asBool()) && or || !b && !or) {
                            i = offset; // skip next condition check
                        } else {
                            --opnd.oSize;
                        }
                    } // else keep first condition on opnd stack
                    break;
                case RC.TOK_NUMBER:
                    opnd.add(opnd.oSize, to[i]);
                    break;
                case RC.TOK_SYMBOL:
                    next = i + 1 < n ? tt[i + 1] : RC.TOK_EOF;
                    if (!isLocal && next == RC.TOK_VAR) {
                        isLocal = true;
                        next = RC.TOK_COM;
                    }
                    if (isLocal && next == RC.TOK_COM) {
                        callObj.putl(((Rv) to[i]).str, Rv._undefined);
                    }
                    opnd.add(opnd.oSize, to[i]);
                    break;
                case RC.TOK_STRING:
                    Rv s = (Rv) to[i], rv = null;
                    if (evalString && (rv = evalString(s.str, callObj)).type == Rv.ERROR) return rv;
                    opnd.add(opnd.oSize, evalString ? rv : s);
                    break;
                case RC.TOK_VAR:
                    // skip
                    break;
                case RC.TOK_COM:
                    o2 = ((Rv) opnd.oArray[--opnd.oSize]).evalVal(callObj);
                    o1 = ((Rv) opnd.oArray[opnd.oSize - 1]).evalVal(callObj);
                    opnd.oArray[opnd.oSize - 1] = o2;
                    break;
                case RC.TOK_DOT:
                case RC.TOK_LBK:
                    o2 = (Rv) opnd.oArray[--opnd.oSize];
                    if (t == RC.TOK_DOT && o2.type != Rv.SYMBOL) {
                        return Rv.error("syntax error");
                    }
                    String pname;
                    if (t == RC.TOK_LBK) {
                        Rv ix = o2.evalVal(callObj);
                        if (ix.type == Rv.NUMBER && !ix.f) {
                            pname = Rv.intStr(ix.num);
                        } else {
                            pname = ix.toStr().str;
                        }
                    } else {
                        pname = o2.str;
                    }
                    o1 = ((Rv) opnd.oArray[opnd.oSize - 1]).evalVal(callObj);
                    Rv ref;
                    opnd.oArray[opnd.oSize - 1] = (ref = (Rv) to[i]);
                    ref.type = Rv.LVALUE;
                    ref.co = o1;
                    ref.str = pname;
                    break;
                case RC.TOK_INIT:
                case RC.TOK_INVOKE:
                    num = ((Rv) opnd.oArray[opnd.oSize - 1]).num;
                    int idx = opnd.oSize - num - 2;
                    Rv fun = (Rv) opnd.oArray[idx];
                    Rv funRef;
                    Rv funObj;
                    boolean callSiteHit = t == RC.TOK_INVOKE && to[i] instanceof InvokeOpRv
                            && tryCallSiteCache((InvokeOpRv) to[i], fun, callObj, depth, callSiteResolve);
                    if (callSiteHit) {
                        funRef = callSiteResolve[0];
                        funObj = callSiteResolve[1];
                    } else {
                        if (fun.type == Rv.FUNCTION) {
                            funRef = acquireEvalTemp(depth).resetTempLvalue("inline", callObj);
                            funObj = fun;
                        } else {
                            funRef = fun.evalRef(callObj, acquireEvalTemp(depth));
                            funObj = funRef.get();
                        }
                    }
                    int type;
                    boolean isInit;
                    if ((type = funObj.type) < Rv.FUNCTION) {
                        return Rv.error("undefined function: " + funRef.str);
                    }
                    if (t == RC.TOK_INVOKE && !callSiteHit && to[i] instanceof InvokeOpRv) {
                        installInvokeCallSite((InvokeOpRv) to[i], fun, funObj);
                    }
                    if ((isInit = (t == RC.TOK_INIT)) && (type & Rv.CTOR_MASK) == 0) { // call as a constructor for the first time
                        funObj.type |= Rv.CTOR_MASK;
                        // Preserve an explicitly-assigned prototype if one is
                        // sitting in `prop["prototype"]` (class desugar path).
                        // Only fall back to a fresh empty object when nothing
                        // has been set. ctorOrProt is initialised to _Function
                        // by the Rv ctor, so comparing to _Function (and null)
                        // distinguishes "untouched" from "user-installed".
                        Rv explicit = funObj.prop != null ? funObj.prop.get("prototype") : null;
                        if (explicit != null) {
                            funObj.ctorOrProt = explicit;
                            funObj.prop.removeAndRelease("prototype".hashCode(), "prototype");
                        } else if (funObj.ctorOrProt == null || funObj.ctorOrProt == Rv._Function) {
                            funObj.ctorOrProt = new Rv(Rv.OBJECT, Rv._Object);
                        }
                    }
                    for (int ii = idx + 1, nn = ii + num; ii < nn; ii++) {
                        opnd.oArray[ii] = ((Rv) opnd.oArray[ii]).evalVal(callObj).pv();
                    }
                    Rv cobakFun = funObj.co;
                    Rv funCo = borrowCallObject();
                    try {
                        funCo.prev = funObj == Rv._Function ? callObj : funObj.co.prev;
                        Rv thiz = isInit ? new Rv(Rv.OBJECT, funObj) : funRef.co;
                        Rv ret = call(isInit, funObj, funObj.co = funCo, thiz, opnd, idx + 1, num);
                        opnd.oSize = idx + 1;
                        opnd.oArray[opnd.oSize - 1] = isInit && ret == Rv._undefined ? thiz : ret;
                    } finally {
                        funObj.co = cobakFun;
                        recycleCallObject(funCo);
                    }
                    break;
                case RC.TOK_COL:
                    num = 3; // fall through
                case RC.TOK_JSONARR:
                    if (num == 0) num = ((Rv) opnd.oArray[opnd.oSize - 1]).num + 1; // fall through
                case RC.TOK_LBR: // json object
                    if (num == 0) num = ((Rv) opnd.oArray[opnd.oSize - 1]).num * 2 + 1;
                    rv = Rv.polynary(callObj, t, opnd, num);
                    opnd.oSize = opnd.oSize - num + 1;
                    opnd.oArray[opnd.oSize - 1] = rv;
                    break;
                }
                break;
            }
        }
        if (opnd.oSize > 1) {
            return Rv.error("invalid expression");
        }
        if (DEBUG) {
            System.out.println("EVAL_RETURN: " + opnd.oArray[0]);
        }
        Rv out = (Rv) opnd.oArray[0];
        return isEvalTemp(depth, out) ? out.evalVal(callObj) : out;
        } finally {
            clearEvalTemps(depth);
            evalDepth = depth;
        }
    }

    /**
     * function
     *   - type: FUNCTION
     *   - node: 
     *     - Arg1
     *     - Arg2
     *     - ...
     *     - block
     *   - co: callObj
     *     - this
     *     - arguments
     *     - arg1
     *     - arg2
     *     - ...
     *     - function1
     *     - function2
     *     - ...
     * @return
     */
    public final Rv call(boolean isInit, Rv function, Rv funCo, Rv thiz, Pack argSrc, int start, int num) {
        if (function.type < Rv.FUNCTION) {
            return Rv.error("invalid function");
        }
        boolean isNative = (function.type & ~Rv.CTOR_MASK) == Rv.NATIVE;
        // ============ fast path for NativeFunctionFast ============
        // Skip the expensive arguments / funCo / stringified-index setup that the
        // classic contract requires. All the interpreter needs is (isNew, thiz, raw args).
        if (isNative && function.obj instanceof NativeFunctionFast) {
            NativeFunctionFast fast = (NativeFunctionFast) function.obj;
            return fast.callFast(isInit, thiz, argSrc, start, num, this);
        }
        Pack children = isNative ? null : ((Node) function.obj).children;
        if (thiz != null) {
            // Native callables use function.obj = NativeFunction (or Fast); only JS
            // functions have a {@link Node} in obj — never cast function.obj to Node
            // when isNative, or ClassCastException (e.g. setInterval callback to native).
            boolean needArgs = isNative
                    || ((Node) function.obj).referencesArguments;
            Rv args = needArgs ? new Rv(Rv.ARGUMENTS, Rv._Arguments) : null;
            if (args != null) {
                args.num = num;
                args.putl("callee", function);
            }
            int numFormalArgs = isNative ? 0 : children.oSize - 1; // minus Block node
            for (int i = 0; i < num; i++) {
                Rv realArg = (Rv) argSrc.getObject(i + start);
                if (args != null) {
                    args.putl(i, realArg);
                }
                if (i >= numFormalArgs) continue;
                Node argnode = (Node) children.getObject(i);
                funCo.putl(tokenSymbolName(argnode.properties, argnode.display), realArg);
            }
            funCo.putl("this", thiz);
            if (args != null) {
                funCo.putl("arguments", args);
            }
        }        
        if (isNative) {
            return callNative(isInit, function, funCo);
        }
        
        Node node = (Node) children.getObject(-1); // the block ('{') node
        
        int _cd0 = callDepth;
        if (_cd0 >= callStackPool.length) {
            Pack[] np = new Pack[callStackPool.length * 2];
            System.arraycopy(callStackPool, 0, np, 0, callStackPool.length);
            callStackPool = np;
        }
        Pack stack = callStackPool[_cd0];
        if (stack == null) {
            callStackPool[_cd0] = stack = new Pack(20, 20);
        } else {
            stack.iSize = 0;
            stack.oSize = 0;
        }
        callDepth = _cd0 + 1;
        int idx = 0;
        try {
        Rv evr = null;
        for (;;) {
            Object next = null;
            int t;
            if ((t = node.tagType) == RC.TOK_LBR && node.state >= 0) { // not resolved
                this.reset(src, node.properties, node.display, node.state);
                statements(funCo, node, -1);
                node.state |= 0x80000000;
            }
            boolean isbrk;
            if ((isbrk = t == RC.TOK_BREAK) || t == RC.TOK_CONTINUE) {
                for (;;) {
                    if (stack.iSize == 0) {
                        throw new RuntimeException("syntax error: " + (isbrk ? "break" : "continue"));
                    }
                    idx = stack.removeInt(-1) + 1;
                    node = (Node) stack.removeObject(-1);
                    int ty = node.tagType;
                    if (ty == RC.TOK_WHILE || ty == RC.TOK_FOR || ty == RC.TOK_IN || ty == RC.TOK_DO
                            || isbrk && ty == RC.TOK_SWITCH) {
                        break;
                    }
                }
                if (isbrk) { // one more pop
                    idx = stack.removeInt(-1) + 1;
                    node = (Node) stack.removeObject(-1);
                }
                continue;
            }
            if ((children = node.children) == null) children = EMPTY_BLOCK;
            Object[] cc = children.oArray;
            int startIdx = 0;
            switch (t) {
            case RC.TOK_LBR: // block
                if (idx < children.oSize) {
                    next = cc[idx];
                } // else pop
                break;
            case RC.TOK_IF:
            case RC.TOK_ELSE:
                if (idx == 0) {
                    if ((evr = eval(funCo, cc[0])).type == Rv.ERROR) return evr;
                    if (evr.evalVal(funCo).asBool()) {
                        next = cc[1];
                    } else if (t == RC.TOK_ELSE) { // has else
                        next = cc[2];
                    }
                }
                break;
            case RC.TOK_WHILE:
                if ((evr = eval(funCo, cc[0])).type == Rv.ERROR) return evr;
                if (evr.evalVal(funCo).asBool()) {
                    next = cc[1];
                }
                break;
            case RC.TOK_DO:
                if (idx > 0 && (evr = eval(funCo, cc[1])).type == Rv.ERROR) return evr;
                if (idx == 0 || evr.evalVal(funCo).asBool()) next = cc[0];
                break;
            case RC.TOK_FOR:
                if (idx == 0 && (evr = eval(funCo, cc[idx++])).type == Rv.ERROR) return evr;
                if (((idx - 1) & 0x1) == 0) {
                    if ((evr = eval(funCo, cc[1])).type == Rv.ERROR) return evr;
                    Rv cond = evr.evalVal(funCo);
                    if (((Node) cc[1]).rpnLen == 0 // empty condition
                            || cond.asBool()) {
                        next = cc[3];
                    } // else pop
                } else {
                    next = cc[2];
                }
                break;
            case RC.TOK_THROW:
                evr = eval(funCo, cc[0]).evalVal(funCo);
                if (evr.type >= Rv.OBJECT) {
                    evr.type = Rv.ERROR;
                } else {
                    evr = Rv.error(evr.toStr().str);
                }
                return evr;
            case RC.TOK_RETURN:
                return eval(funCo, cc[0]).evalVal(funCo);
            case RC.TOK_TRY:
                Rv tmpfun = new Rv(false, cc[0], 0); // try node
                Rv tmpret = call(false, tmpfun, funCo, null, null, 0, 0);
                if (tmpret.type == Rv.ERROR) {
                    Node catnode = (Node) cc[1];
                    if (catnode.children != null) { // valid catch
                        Node argnode = (Node) catnode.children.oArray[0];
                        funCo.putl(tokenSymbolName(argnode.properties, argnode.display), tmpret);
                        tmpfun = new Rv(false, catnode, 0);
                        tmpret = call(false, tmpfun, funCo, null, null, 0, 0);
                    }
                }
                Node finode = (Node) cc[2];
                Rv tmpret2 = Rv._undefined;
                if (finode.children != null) { // valid finally
                    tmpfun = new Rv(false, finode, 0);
                    tmpret2 = call(false, tmpfun, funCo, null, null, 0, 0);
                }
                boolean ret2;
                if ((ret2 = tmpret2 != Rv._undefined) || tmpret != Rv._undefined ) {
                    return ret2 ? tmpret2 : tmpret;
                }
                break;
            case RC.TOK_IN:
                if ((evr = eval(funCo, cc[1])).type == Rv.ERROR) return evr;
                Pack arr = evr.evalVal(funCo).keyArray();
                if (idx < arr.oSize) {
                	Rv ref;
                    if ((ref = eval(funCo, cc[0])).type == Rv.ERROR) return ref;
                    ref.evalRef(funCo).put(new Rv((String) arr.oArray[idx]));
                    next = cc[2];
                } // else pop
                break;
            case RC.TOK_WITH:
                if (idx == 0) {
                    if ((evr = eval(funCo, cc[0])).type == Rv.ERROR) return evr;
                    Rv tmpCo = evr.evalVal(funCo);
                    tmpCo.prev = funCo;
                    funCo = tmpCo;
                    next = cc[1];
                } else {
                    Rv tmpCo = funCo;
                    funCo = funCo.prev;
                    tmpCo.prev = null;
                    // and pop
                }
                break;
            case RC.TOK_SWITCH:
                Node block;
                if ((block = (Node) cc[1]).children == null) {
                    this.reset(src, block.properties, block.display, block.state);
                    statements(funCo, block, -1);
                    block.state |= 0x80000000;
                }
                Object[] blkoo = (block = (Node) cc[1]).children.oArray;
                if (idx == 0) {
                    if ((evr = eval(funCo, cc[0])).type == Rv.ERROR) return evr;
                    Rv rv = evr.evalVal(funCo);
                    if (node.className == null) { // first call
                        Pack brch = node.className = new Pack(8, 8);
                        int defIdx = -1;
                        for (int i = 0, n = block.children.oSize; i < n; i++) {
                            Node stmt;
                            int stty = (stmt = (Node) blkoo[i]).tagType;
                            if (stty == RC.TOK_CASE) {
                                brch.add(i).add(stmt.children.oArray[0]); // index => exp
                            } else if (stty == RC.TOK_DEFAULT) {
                                defIdx = i;
                            } else if (i == 0) {
                                throw new RuntimeException("syntax error: switch");
                            }
                        }
                        if (defIdx >= 0) brch.add(defIdx).add(null); // default branch
                    }
                    Pack brch = node.className;
                    for (int i = 0, n = brch.iSize; i < n; i++) {
                        Object caseexp;
                        if ((caseexp = brch.getObject(i)) != null) {
                            if ((evr = eval(funCo, caseexp)).type == Rv.ERROR) return evr;
                            if (!rv.equals(evr.evalVal(funCo))) continue;
                        }
                        startIdx = brch.getInt(i) + 1;
                        next = block;
                        break;
                    }
                    // no matching branch, pop
                } // else pop
                break;
            }
            int nextty;
            if (next == null) {
                if (stack.iSize == 0) break;
                idx = stack.iArray[--stack.iSize] + 1;
                node = (Node) stack.oArray[--stack.oSize];
            } else if ((nextty = ((Node) next).tagType) == '*') {
                if ((evr = eval(funCo, next)).type == Rv.ERROR) return evr;
                ++idx;
            } else if (nextty == RC.TOK_CASE || nextty == RC.TOK_DEFAULT) { // go to next node
                ++idx;
            } else {
                stack.add(node).add(idx);
                node = (Node) next;
                idx = startIdx;
            }
    
        }
        return Rv._undefined;
        } finally {
            callDepth = _cd0;
        }
    }

    public Rv js_call_apply(boolean isNew, Rv _this, Rv args, int magic) {
        Rv jsFunc = args.get("0");
        Rv jsArgs = args.get("1");

        boolean isCall = (magic == 0);
        if (jsFunc != null && jsFunc.type >= Rv.OBJECT 
                && (isCall || jsArgs != null && jsArgs.type == Rv.ARRAY)) {
            Rv funCo = borrowCallObject();
            try {
                funCo.prev = _this.co.prev;
                int argNum, argStart;
                Rv argsArr;
                if (isCall) {
                    argNum = args.num - 1;
                    argStart = 1;
                    argsArr = args;
                } else {
                    argNum = jsArgs.num;
                    argStart = 0;
                    argsArr = jsArgs;
                }
                Pack argSrc = new Pack(-1, argNum);

                for (int ii = argStart, nn = argStart + argNum; ii < nn; argSrc.add(argsArr.get(Rv.intStr(ii++))));
                Rv cobak = _this.co;
                Rv ret = call(false, _this, _this.co = funCo, jsFunc, argSrc, 0, argNum);
                _this.co = cobak;
                return ret;
            } finally {
                recycleCallObject(funCo);
            }
        }

        return Rv._undefined;
    }

    /**
     * Generic callback invoker used by stdlib bindings (Array.map, filter, reduce,
     * Array.sort, for...of helpers, etc.). Handles the funCo bookkeeping that
     * upstream RockScript requires for JS function callbacks; native callbacks
     * go through the usual {@code call()} fast paths.
     *
     * @param fn      the function Rv (FUNCTION or NATIVE).
     * @param thiz    value for {@code this} inside the callback.
     * @param args    argument stack ({@code Pack} with args at [start, start+num)).
     * @param start   first arg index.
     * @param num     number of args.
     * @return        the callback's return value (or {@code undefined} if fn is not callable).
     */
    public final Rv invokeJS(Rv fn, Rv thiz, Pack args, int start, int num) {
        if (fn == null || !fn.isCallable()) return Rv._undefined;
        int t = fn.type & ~Rv.CTOR_MASK;
        if (t == Rv.NATIVE) {
            Rv funCo = borrowCallObject();
            try {
                return call(false, fn, funCo, thiz, args, start, num);
            } finally {
                recycleCallObject(funCo);
            }
        }
        // JS function: respect lexicalThis for arrow-like bindings
        Rv effThis = fn.opaque instanceof Rv ? (Rv) fn.opaque : thiz;
        Rv funCo = borrowCallObject();
        funCo.prev = fn.co.prev;
        Rv cobak = fn.co;
        try {
            return call(false, fn, fn.co = funCo, effThis, args, start, num);
        } finally {
            fn.co = cobak;
            recycleCallObject(funCo);
        }
    }

    /** Convenience wrapper: invoke {@code fn} with a single arg. */
    public final Rv invokeJS1(Rv fn, Rv thiz, Rv a0) {
        Pack p = invokeOneArgPack;
        p.iSize = 0;
        p.oSize = 0;
        p.add(a0);
        return invokeJS(fn, thiz, p, 0, 1);
    }

    /** Convenience wrapper: invoke {@code fn} with three args (Array callback shape). */
    public final Rv invokeJS3(Rv fn, Rv thiz, Rv a0, Rv a1, Rv a2) {
        Pack p = invokeThreeArgPack;
        p.iSize = 0;
        p.oSize = 0;
        p.add(a0);
        p.add(a1);
        p.add(a2);
        return invokeJS(fn, thiz, p, 0, 3);
    }

    /** Bootstrap hook: register all ES6+ stdlib bindings onto the global object. */
    public final void installStdLib(Rv go) {
        StdLib.install(this, go);
    }

    private NativeFunctionList function_list = new NativeFunctionList(
        new NativeFunctionListEntry[] {
            new NativeFunctionListEntry(
                "Object", 
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv _this, Rv args) {
                        Rv arg = args.get("0");
                        Rv ret = isNew ? _this : new Rv(Rv.OBJECT, Rv._Object);
                        if (arg != null) {
                            int type;
                            if ((type = arg.type) == Rv.NUMBER || type == Rv.NUMBER_OBJECT) {
                                ret.type = Rv.NUMBER_OBJECT;
                                ret.f = arg.f;
                                ret.num = arg.num;
                                ret.d = arg.d;
                            } else if (type == Rv.STRING || type == Rv.STRING_OBJECT) {
                                ret.type = Rv.STRING_OBJECT;
                                ret.str = arg.str;
                            } else { // object
                                ret = arg;
                            }
                        }

                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Number", 
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv _this, Rv args) {
                        Rv arg = args.get("0");
                        Rv ret = _this;
                        if (isNew) {
                            ret = _this;
                            ret.type = Rv.NUMBER_OBJECT;
                            ret.ctorOrProt = Rv._Number;
                        } else {
                            ret = new Rv(0);
                        }

                        if (arg != null && (arg = arg.toNum()) != Rv._NaN) {
                            ret.f = arg.f;
                            ret.num = arg.num;
                            ret.d = arg.d;
                        } else {
                            ret.f = false;
                            ret.num = 0;
                        }

                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "String", 
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv _this, Rv args) {
                        Rv arg = args.get("0");
                        Rv ret = _this;
                        if (isNew) {
                            ret.type = Rv.STRING_OBJECT;
                            ret.ctorOrProt = Rv._String;
                        } else {
                            ret = new Rv("");
                        }
                        ret.str = arg != null? arg.toStr().str : "";

                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Array", 
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv _this, Rv args) {
                        Rv ret = isNew ? _this : new Rv(Rv.ARRAY, Rv._Array);

                        ret.type = Rv.ARRAY;
                        ret.ctorOrProt = Rv._Array;
                        Rv len;

                        if (args.num == 1 && (len = _this.toNum()) != Rv._NaN) {
                            ret.num = (int) Rv.numValue(len);
                        } else { // 0 or more
                            ret.num = args.num;
                            for (int i = 0; i < args.num; i++) {
                                ret.putl(i, args.get(Rv.intStr(i)));
                            }
                        }

                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Date", 
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv _this, Rv args) {
                        Rv arg = args.get("0");
                        Rv ret = isNew ? _this : new Rv(Rv.OBJECT, Rv._Date);
                        ret.type = Rv.NUMBER_OBJECT;
                        ret.ctorOrProt = Rv._Date;
                        _this.f = false;
                        _this.num = arg != null && (arg = arg.toNum()) != Rv._NaN
                                ? (int) Rv.numValue(arg)
                                : (int) (System.currentTimeMillis() - bootTime);

                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Error", 
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv _this, Rv args) {
                        Rv arg = args.get("0");
                        Rv ret = isNew ? _this : new Rv(Rv.ERROR, Rv._Error);
                        ret.type = Rv.ERROR;
                        ret.ctorOrProt = Rv._Error;
                        if (arg != null) ret.putl("message", arg.toStr());
                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Object.toString", 
                new NativeFunction() {
                    public final int length = 0;
                    public Rv func(boolean isNew, Rv _this, Rv args) {
                        return _this.toStr();
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Object.hasOwnProperty", 
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv _this, Rv args) {
                        Rv arg = args.get("0");
                        return arg != null && _this.has(arg.toStr().str) ? Rv._true : Rv._false;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Function.call", 
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv _this, Rv args) {
                        return js_call_apply(isNew, _this, args, 0);
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Function.apply", 
                new NativeFunction() {
                    public final int length = 2;
                    public Rv func(boolean isNew, Rv _this, Rv args) {
                        return js_call_apply(isNew, _this, args, 1);
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Number.valueOf", 
                new NativeFunction() {
                    public final int length = 0;
                    public Rv func(boolean isNew, Rv _this, Rv args) {
                        if (_this.type != Rv.NUMBER_OBJECT) return Rv._undefined;
                        Rv r = new Rv(0);
                        r.type = Rv.NUMBER;
                        r.f = _this.f;
                        r.num = _this.num;
                        r.d = _this.d;
                        return r;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "String.valueOf", 
                new NativeFunction() {
                    public final int length = 0;
                    public Rv func(boolean isNew, Rv _this, Rv args) {
                        return _this.type == Rv.STRING_OBJECT ? _this : Rv._undefined;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "String.charAt", 
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv _this, Rv args) {
                        Rv arg = args.get("0");
                        int pos = (arg = arg.toNum()) != Rv._NaN ? (int) Rv.numValue(arg) : -1;
                        return pos < 0 || pos >= _this.str.length() ? Rv._empty
                                : new Rv(String.valueOf(_this.str.charAt(pos)));
                    }
                }
            ),
            new NativeFunctionListEntry(
                "String.indexOf", 
                new NativeFunction() {
                    public final int length = 2;
                    public Rv func(boolean isNew, Rv _this, Rv args) {
                        Rv key = args.get("0");
                        Rv start = args.get("1");
                        Rv ret = new Rv(-1);
                        if (key != null) {
                            String s = key.toStr().str;
                            int idx = start != null && (start = start.toNum()) != Rv._NaN ? (int) Rv.numValue(start) : 0;
                            ret = new Rv(_this.str.indexOf(s, idx));
                        }

                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "String.lastIndexOf",
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        Rv arg0 = args.get("0");
                        Rv arg1 = args.get("1");
                        Rv ret = new Rv(-1);
                        
                        if (arg0 != null) {
                            String s = arg0.toStr().str;
                            String src = thiz.toStr().str;
                            int l = s.length(), srcl = src.length();
                            int idx = arg1 != null && (arg1 = arg1.toNum()) != Rv._NaN ? (int) Rv.numValue(arg1) : srcl;
                            if (idx >= 0) {
                                if (idx >= srcl - l) idx = srcl - l;
                                for (int i = idx + 1; --i >= 0;) {
                                    if (src.regionMatches(false, i, s, 0, l)) {
                                        ret = new Rv(i);
                                        break;
                                    }
                                }
                            }
                        }
                        
                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "String.substring",
                new NativeFunction() {
                    public final int length = 2;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        Rv arg0 = args.get("0");
                        Rv arg1 = args.get("1");
                        Rv ret = Rv._undefined;
                        
                        if (arg0 != null) {
                            thiz = thiz.toStr();
                            int i1 = (arg0 = arg0.toNum()) != Rv._NaN ? (int) Rv.numValue(arg0) : 0;
                            int i2 = arg1 != null && (arg1 = arg1.toNum()) != Rv._NaN ? (int) Rv.numValue(arg1) : Integer.MAX_VALUE;
                            int strlen;
                            if (i2 > (strlen = thiz.str.length())) i2 = strlen;
                            ret = new Rv(thiz.str.substring(i1, i2));
                        }
                        
                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "String.split",
                new NativeFunction() {
                    public final int length = 2;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        Rv arg0 = args.get("0");
                        Rv arg1 = args.get("1");
                        Rv ret = Rv._undefined;
                        
                        if (arg0 != null) {
                            thiz = thiz.toStr();
                            int limit = arg1 != null && (arg1 = arg1.toNum()) != Rv._NaN ? (int) Rv.numValue(arg1) : -1;
                            String delim;
                            Pack p = split(thiz.str, delim = arg0.toStr().str);
                            if (limit >= 1) {
                                StringBuffer buf = new StringBuffer();
                                for (int i = limit - 1, n = p.oSize; i < n; i++) {
                                    if (i > limit - 1) buf.append(delim);
                                    buf.append(p.oArray[i]);
                                }
                                p.setSize(-1, limit).set(-1, buf.toString());
                            }
                            ret = new Rv(Rv.ARRAY, Rv._Array);
                            for (int i = 0, n = p.oSize; i < n; i++) {
                                ret.putl(i, new Rv((String) p.oArray[i])); 
                            }
                        }
                        
                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "String.charCodeAt",
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        Rv arg0 = args.get("0");
                        int pos = (arg0 = arg0.toNum()) != Rv._NaN ? (int) Rv.numValue(arg0) : -1;
                        Rv ret = pos < 0 || pos >= thiz.str.length() ? Rv._NaN
                                : new Rv(thiz.str.charAt(pos));
                        
                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "String.fromCharCode",
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        StringBuffer buf = new StringBuffer();
                        int argLen = args.num;
                        
                        for (int i = 0; i < argLen; i++) {
                            Rv charcode = args.get(Rv.intStr(i)).toNum();
                            if (charcode != Rv._NaN) buf.append((char) (int) Rv.numValue(charcode));
                        }
                        Rv ret = new Rv(buf.toString());
                        
                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Array.concat",
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        Rv ret = new Rv(Rv.ARRAY, Rv._Array);
                        ret.num = thiz.num;
                        Rhash dest = ret.prop;
                        Rhash prop = thiz.prop;
                        String key;
                        Pack keys = prop.keys();
                        int argLen = args.num;
                        
                        for (int i = 0, n = keys.oSize; i < n; i++) {
                            dest.put(key = (String) keys.oArray[i], prop.get(key));
                        }
                        for (int i = 0; i < argLen; i++) {
                            Rv obj = args.get(Rv.intStr(i));
                            if (obj.type == Rv.ARRAY) {
                                for (int j = 0, n = obj.num, b = ret.num; j < n; j++) {
                                    ret.putl(b + j, obj.get(Rv.intStr(j)));
                                }
                            } else {
                                ret.putl(ret.num, obj);
                            }
                        }
                        
                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Array.join",
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        Rv arg0 = args.get("0");
                        String sep = arg0 != null ? arg0.toStr().str : ",";
                        StringBuffer buf = new StringBuffer();
                        Rhash prop = thiz.prop;
                        
                        for (int i = 0, n = thiz.num; i < n; i++) {
                            if (i > 0) buf.append(sep);
                            buf.append(prop.get(Rv.intStr(i)).toStr().str);
                        }
                        Rv ret = new Rv(buf.toString());
                        
                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Array.push",
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        int argLen = args.num;
                        
                        for (int i = 0, b = thiz.num; i < argLen; i++) {
                            thiz.putl(b + i, args.get(Rv.intStr(i)));
                        }
                        Rv ret = new Rv(thiz.num);
                        
                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Array.pop",
                new NativeFunction() {
                    public final int length = 0;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        Rv ret = thiz.shift(thiz.num - 1);
                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Array.shift",
                new NativeFunction() {
                    public final int length = 0;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        Rv ret = thiz.shift(0);
                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Array.unshift",
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        Rhash ht = new Rhash(11);
                        Rhash prop = thiz.prop;
                        int argLen = args.num;
                        
                        for (int i = 0; i < argLen; i++) {
                            String idx = Rv.intStr(i);
                            Rv val = args.prop.get(idx); 
                            if (val != null) ht.put(idx, val);
                        }
                        for (int i = 0, n = thiz.num; i < n; i++) {
                            Rv val = prop.get(Rv.intStr(i)); 
                            if (val != null) ht.put(Rv.intStr(i + argLen), val);
                        }
                        thiz.num += argLen;
                        thiz.prop = ht;
                        
                        return new Rv(thiz.num);
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Array.slice",
                new NativeFunction() {
                    public final int length = 2;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        Rv arg0 = args.get("0");
                        Rv arg1 = args.get("1");
                        Rv ret = Rv._undefined;
                        Rhash prop = thiz.prop;
                        
                        if (arg0 != null) {
                            int i1 = (arg0 = arg0.toNum()) != Rv._NaN ? (int) Rv.numValue(arg0) : 0;
                            int i2 = arg1 != null && (arg1 = arg1.toNum()) != Rv._NaN ? (int) Rv.numValue(arg1) : thiz.num;
                            ret = new Rv(Rv.ARRAY, Rv._Array);
                            Rhash ht = ret.prop;
                            int i = 0, n = ret.num = i2 - i1;
                            for (; i < n; i++) {
                                Rv val = prop.get(Rv.intStr(i + i1)); 
                                if (val != null) ht.put(Rv.intStr(i), val);
                            }
                        }
                        
                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Array.sort",
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        Rv arg0 = args.get("0");
                        Rhash prop = thiz.prop;
                        Rv comp = arg0 != null && arg0.type >= Rv.FUNCTION ? arg0 : null;
                        int num;
                        Pack tmp = new Pack(-1, num = thiz.num);
                        
                        for (int i = 0; i < num; tmp.add(prop.get(Rv.intStr(i++))));
                        Object[] arr = tmp.oArray;
                        for (int i = 0, n = num - 1; i < n; i++) {
                            Rv r1 = (Rv) arr[i];
                            for (int j = i + 1; j < num; j++) {
                                Rv r2 = (Rv) arr[j];
                                boolean grtr = false;
                                if (r1 == null || r2 == null || r1 == Rv._undefined || r2 == Rv._undefined) {
                                    grtr = r1 == null && r2 != null 
                                            || r1 == Rv._undefined && r2 != null && r2 != Rv._undefined;
                                } else {
                                    if (comp == null) {
                                        grtr = r1.toStr().str.compareTo(r2.toStr().str) > 0;
                                    } else {
                                        Pack argSrc = new Pack(-1, 2).add(r1).add(r2);
                                        Rv funCo = new Rv(Rv.OBJECT, Rv._Object);
                                        funCo.prev = comp.co.prev;
                                        Rv cobak = comp.co;
                                        grtr = Rv.numValue(call(false, comp, comp.co = funCo, thiz, argSrc, 0, 2).toNum()) > 0;
                                        comp.co = cobak;
                                    }
                                }
                                if (grtr) {
                                    arr[j] = r1;
                                    arr[i] = r1 = r2;
                                }
                            }
                        }
                        Rhash ht = new Rhash(11);
                        for (int i = num; --i >= 0;) {
                            Rv val; 
                            if ((val = (Rv) arr[i]) != null) ht.put(Rv.intStr(i), val);
                        }
                        thiz.prop = ht;
                        
                        return thiz;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Array.reverse",
                new NativeFunction() {
                    public final int length = 0;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        Rhash prop = thiz.prop;
                        Rhash ht = new Rhash(11);

                        for (int i = 0, j = thiz.num; --j >= 0; i++) {
                            Rv val = prop.get(Rv.intStr(j)); 
                            if (val != null) ht.put(Rv.intStr(i), val);
                        }
                        thiz.prop = ht;

                        return thiz;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Date.getTime",
                new NativeFunction() {
                    public final int length = 0;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        Rv ret = new Rv(thiz.num);
                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Date.setTime",
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        Rv arg0 = args.get("0");

                        if (arg0 != null && (arg0 = arg0.toNum()) != Rv._NaN) {
                            thiz.f = arg0.f;
                            thiz.num = arg0.num;
                            thiz.d = arg0.d;
                        }

                        return thiz;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Date.now",
                new NativeFunction() {
                    public final int length = 0;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        return new Rv((int) (System.currentTimeMillis()-bootTime));
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Error.toString",
                new NativeFunction() {
                    public final int length = 0;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        Rv ret = thiz.get("message");
                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Math.min",
                new NativeFunction() {
                    public final int length = 2;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        int argLen = args.num;
                        Rv ret = Rv._undefined;

                        if (argLen > 0) {
                            double iret = Double.POSITIVE_INFINITY;
                            for (int i = 0; i < argLen; i++) {
                                Rv val = args.get(Rv.intStr(i)).toNum();
                                if (val == Rv._NaN) {
                                    return val;
                                }
                                double v = Rv.numValue(val);
                                if (Double.isNaN(v)) {
                                    return Rv._NaN;
                                }
                                if (v < iret) iret = v;
                            }
                            ret = new Rv(iret);
                        }

                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "Math.max",
                new NativeFunction() {
                    public final int length = 2;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        int argLen = args.num;
                        Rv ret = Rv._undefined;

                        if (argLen > 0) {
                            double iret = Double.NEGATIVE_INFINITY;
                            for (int i = 0; i < argLen; i++) {
                                Rv val = args.get(Rv.intStr(i)).toNum();
                                if (val == Rv._NaN) {
                                    return val;
                                }
                                double v = Rv.numValue(val);
                                if (Double.isNaN(v)) {
                                    return Rv._NaN;
                                }
                                if (v > iret) iret = v;
                            }
                            ret = new Rv(iret);
                        }

                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "isNaN",
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        Rv arg0 = args.get("0");
                        if (arg0 == null) return Rv._true;
                        Rv n = arg0.toNum();
                        return Double.isNaN(Rv.numValue(n)) ? Rv._true : Rv._false;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "parseInt",
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        Rv arg0 = args.get("0");
                        Rv arg1 = args.get("1");
                        Rv ret = Rv._undefined;

                        int radix = arg1 != null && arg1.toNum() != Rv._NaN ? (int) Rv.numValue(arg1.toNum()) : 10;
                        String sNum = arg0 != null ? arg0.toStr().str : null;
                        try {
                            ret = new Rv(Integer.parseInt(sNum, radix));
                        } catch (Exception ex) { } // do nothing, ret = undefined

                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "eval",
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        Rv arg0 = args.get("0");
                        Rv ret = Rv._undefined;

                        if (arg0 != null) {
                            String s;
                            reset(s = arg0.toStr().str, null, 0, s.length());
                            Node node = astNode(null, RC.TOK_FUNCTION, 0, 0);
                            astNode(node, RC.TOK_LBR, 0, endpos); // '{' = block
                            RocksInterpreter.computeFunctionReferencesArguments(node);
                            Rv func = new Rv(false, node, 0);
                            ret = call(false, func, thiz, null, null, 0, 0);
                        }

                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "es",
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv thiz, Rv args) {
                        Rv arg0 = args.get("0");
                        Rv ret = Rv._undefined;

                        if (arg0 != null) {
                            ret = evalString(arg0.toStr().str, thiz);
                        }

                        return ret;
                    }
                }
            ),
            new NativeFunctionListEntry(
                "console.log", 
                new NativeFunction() {
                    public final int length = 1;
                    public Rv func(boolean isNew, Rv _this, Rv args) {
                        Rv arg = args.get("0");
                        String msg = args.num > 0 ? arg.toStr().str : "";

                        System.out.println(msg);

                        return Rv._undefined;
                    }
                }
            ),
        }
    );

    public final Rv newNativeFunction(String name) {
        NativeFunction func = function_list.get(name);
        return new Rv(true, name, func.length);
    }

    public final void addNativeFunctionList(NativeFunctionListEntry entries[]) {
        function_list.concat(entries);
    }

    public final Rv addNativeFunction(NativeFunctionListEntry entry) {
        function_list.put(entry);
        Rv ret = new Rv(true, entry.name, entry.func.length);
        // Keep a direct reference to the NativeFunction instance so callNative
        // doesn't have to do a Hashtable<String,NativeFunction> lookup per call.
        ret.obj = entry.func;
        return ret;
    }

    private static final Object ACTIVE_CALL_OBJECT = new Object();
    private static final Object CAPTURED_CALL_OBJECT = new Object();

    private Rv captureScopeChain(Rv env) {
        return captureScopeChain(env, 0);
    }

    private Rv captureScopeChain(Rv env, int depth) {
        if (env == null || env.prev == null) {
            return env;
        }
        // Defensive cap: a valid lexical chain is shallow; a cycle must not be preserved.
        if (depth >= 64) {
            return null;
        }
        if (env.opaque == ACTIVE_CALL_OBJECT || env.opaque == CAPTURED_CALL_OBJECT) {
            env.opaque = CAPTURED_CALL_OBJECT;
        }
        captureScopeChain(env.prev, depth + 1);
        return env;
    }

    /**
     * call a native function
     * @param isNew
     * @param thiz
     * @param args
     * @return
     */
    protected Rv callNative(boolean isNew, Rv function, Rv callObj) {
        Rv args = callObj.get("arguments");
        Rv thiz = callObj.get("this");

        // Direct-ref path: avoid the per-call Hashtable<String,NativeFunction>
        // lookup when we already resolved the function at registration time.
        Object direct = function.obj;
        NativeFunction native_func = direct instanceof NativeFunction
                ? (NativeFunction) direct
                : function_list.get(function.str);

        if (native_func != null) {
            return native_func.func(isNew, thiz, args);
        }

        Rv idEnt;
        if ((idEnt = htNativeIndex.getEntry(0, function.str)) == null) return Rv._undefined;

        Rhash prop = thiz.prop;
        int argLen = args.num;
        Rv arg0, arg1, ret;
        arg0 = argLen > 0 ? (Rv) args.get("0") : null;
        arg1 = argLen > 1 ? (Rv) args.get("1") : null;
        ret = Rv._undefined;

        
        
        int funcId;
        switch (funcId = idEnt.num) {
        case 102: // Function(1)
            if (argLen > 0) {
                ret = isNew ? thiz : new Rv(Rv.OBJECT, Rv._Object);
                ret.type = Rv.FUNCTION;
                Node n;
                if (argLen == 1 && arg0.type == Rv.FUNCTION) {
                    n = (Node) arg0.obj;
                } else {
                    n = astNode(null, RC.TOK_FUNCTION, 0, 0);
                    int numArgs = argLen - 1;
                    for (int i = 0; i < numArgs; i++) {
                        String arg;
                        this.reset(arg = args.get(Rv.intStr(i)).toStr().str, null, 0, arg.length());
                        astNode(n, RC.TOK_MUL, 0, this.endpos);
                    }
                    String src;
                    this.reset(src = args.get(Rv.intStr(numArgs)).toStr().str, null, 0, src.length());
                    astNode(n, RC.TOK_LBR, 0, this.endpos); // '{' = block
                }
                computeFunctionReferencesArguments(n);
                ret.obj = n;
                ret.ctorOrProt = Rv._Function;
                ret.co = new Rv(Rv.OBJECT, Rv._Object);
                ret.co.prev = argLen == 1 && arg0.type == Rv.FUNCTION
                        ? captureScopeChain(callObj.prev)
                        : callObj.prev;
            }
            break;
        }
//        StringBuffer buf = new StringBuffer();
//        buf.append("this=" + thiz.toStr());
//        for (int i = 0; i < argLen; i++) {
//            buf.append(", ").append(args.get(Integer.toString(i)));
//        }
//        System.out.println(">> " + (isNew ? " NEW" : "CALL") + ": " + function.str + "(" + buf + "), Result=" + ret);
        return ret;
    }
    
    public Rv initGlobalObject() {
        Rv go = new Rv();
        go.type = Rv.OBJECT;
        go.prop = new Rhash(43);
        
        if (Rv._Object.type == Rv.UNDEFINED) { // Rv not initialized
            Rv._Object.nativeCtor("Object", go)
                    .ctorOrProt
                    .putl("toString", newNativeFunction("Object.toString"))
                    .putl("hasOwnProperty", newNativeFunction("Object.hasOwnProperty"))
                    .ctorOrProt = null;
            ;
            Rv._Function.nativeCtor("Function", go)
                    .ctorOrProt
                    .putl("call", newNativeFunction("Function.call"))      // call(thisObj, [arg1, [arg2...]])
                    .putl("apply", newNativeFunction("Function.apply"))    // apply(thisObj, arrayArgs)
            ;
            Rv._Number.nativeCtor("Number", go)
                    .putl("MAX_VALUE", new Rv(Integer.MAX_VALUE))
                    .putl("MIN_VALUE", new Rv(Integer.MIN_VALUE))
                    .putl("NaN", Rv._NaN)
                    .ctorOrProt
                    .putl("valueOf", newNativeFunction("Number.valueOf"))
            ;
            Rv._String.nativeCtor("String", go)
                    .putl("fromCharCode", newNativeFunction("String.fromCharCode"))
                    .ctorOrProt
                    .putl("valueOf", newNativeFunction("String.valueOf"))
                    .putl("charAt", newNativeFunction("String.charAt"))
                    .putl("charCodeAt", newNativeFunction("String.charCodeAt"))
                    .putl("indexOf", newNativeFunction("String.indexOf"))
                    .putl("lastIndexOf", newNativeFunction("String.lastIndexOf"))
                    .putl("substring", newNativeFunction("String.substring"))
                    .putl("split", newNativeFunction("String.split"))
            ;
            Rv._Array.nativeCtor("Array", go)
                    .ctorOrProt
                    .putl("concat", newNativeFunction("Array.concat"))    // concat(arg0[, arg1...])
                    .putl("join", newNativeFunction("Array.join"))        // join(separator)
                    .putl("push", newNativeFunction("Array.push"))        // push(arg0[, arg1...])
                    .putl("pop", newNativeFunction("Array.pop"))          // pop()
                    .putl("shift", newNativeFunction("Array.shift"))      // shift()
                    .putl("unshift", newNativeFunction("Array.unshift"))  // unshift(arg0[, arg1...])
                    .putl("slice", newNativeFunction("Array.slice"))      // slice(start, end)
                    .putl("sort", newNativeFunction("Array.sort"))        // sort(comparefn)
                    .putl("reverse", newNativeFunction("Array.reverse"))  // reverse()
            ;
            Rv._Date.nativeCtor("Date", go)
                    .putl("now", newNativeFunction("Date.now"))
                    .ctorOrProt
                    .putl("getTime", newNativeFunction("Date.getTime"))   // getTime()
                    .putl("setTime", newNativeFunction("Date.setTime"))   // setTime(arg0)
            ;
            Rv._Error.nativeCtor("Error", go)
                    .putl("name", new Rv("Error"))
                    .putl("message", new Rv("Error"))
                    .ctorOrProt
                    .putl("toString", newNativeFunction("Error.toString"))    // toString()
            ;
            Rv._Arguments.nativeCtor("Arguments", go)
                    .ctorOrProt
                    .ctorOrProt = Rv._Array
            ;
        }
        Rv _console = new Rv(Rv.OBJECT, Rv._Object)
                .putl("log", newNativeFunction("console.log"))   // console.log()
        ;
        Rv _Math = new Rv(Rv.OBJECT, Rv._Object)
                .putl("min", newNativeFunction("Math.min"))
                .putl("max", newNativeFunction("Math.max"))
        ;
        // fill global Object
        Rv console_log;
        go.putl("true", Rv._true)
                .putl("false", Rv._false)
                .putl("null", Rv._null)
                .putl("undefined", Rv._undefined)
                .putl("NaN", Rv._NaN)
                .putl("Object", Rv._Object)
                .putl("Function", Rv._Function)
                .putl("Number", Rv._Number)
                .putl("String", Rv._String)
                .putl("Array", Rv._Array)
                .putl("Date", Rv._Date)
                .putl("console", _console)
                .putl("Error", Rv._Error)
                .putl("Math", _Math)
                .putl("isNaN", newNativeFunction("isNaN"))
                .putl("parseInt", newNativeFunction("parseInt"))
                .putl("eval", newNativeFunction("eval"))
                .putl("es", newNativeFunction("es"))
                //.putl("alert", console_log)
                ;
        
        go.putl("this", go);
        installStdLib(go);
        return go;
    }

    public void addToObject(Rv obj, String key, Rv prop) {
        obj.putl(key, prop);
    }

    public Rv newModule() {
        return new Rv(Rv.OBJECT, Rv._Object);
    }

    /** Empty dense array (length 0), for native bindings outside this package. */
    public Rv newEmptyArray() {
        Rv a = new Rv(Rv.ARRAY, Rv._Array);
        a.type = Rv.ARRAY;
        a.ctorOrProt = Rv._Array;
        a.num = 0;
        return a;
    }
    
////////////////////////////// Auxiliary Routines ///////////////////////////
    
    final int eat(int tokenType) {
        int[] tti = tt.iArray;
        int tpos = pos, endpos = this.endpos;
        int token = 0;
        boolean found = false;
        for (;;) {
            if (tpos >= endpos) {
                if (found) break;
                throw ex(RC.TOK_EOF, new Object[] { new int[] { src.length(), 0 } }, RC.tokenName(tokenType, null));
            }
            token = tti[tpos * RC.LEX_STRIDE];
            if (!found && token == tokenType) {
                found = true;
                ++tpos;
            } else if (token == RC.TOK_EOL) {
                ++tpos;
            } else if (found) {
                break;
            } else {
                throw ex(token, makeErrTokPair(tpos), RC.tokenName(tokenType, null));
            }
        }
        pos = tpos;
        return token;
    }
    
    /**
     * TODO in expression, consider operators (+, *, &&, !, ?, :, function() etc.)
     * @param ttype
     * @param ttype2
     * @return run length
     */
    final int eatUntil(int ttype, int ttype2) {
        int[] tti = tt.iArray;
        int tpos = pos, endpos = this.endpos;
        Pack stack = new Pack(20, -1);
        for (;;) {
            if (tpos >= endpos) {
                if (ttype != RC.TOK_EOL) {
                    String expected = RC.tokenName(ttype, null);
                    if (ttype2 > 0) expected += "," + RC.tokenName(ttype2, null);
                    throw ex(RC.TOK_EOF, new Object[] { new int[] { src.length(), 0 } }, expected);
                }
                break;
            }
            int token = tti[tpos * RC.LEX_STRIDE];
            if ((token == ttype || token == ttype2) && stack.iSize == 0) {
                break;
            }
            int pr = 0;
            switch (token) {
            case RC.TOK_QMK:
                pr = RC.TOK_COL;
            case RC.TOK_FUNCTION:
                if (pr == 0) pr = RC.TOK_LBR;
            case RC.TOK_LBK:
                if (pr == 0) pr = RC.TOK_RBK;
            case RC.TOK_LPR:
                if (pr == 0) pr = RC.TOK_RPR;
                stack.add(pr);
                break;
            case RC.TOK_LBR:
                if (stack.iSize > 0 && token == stack.getInt(-1)) {
                    stack.iSize--;
                }
                stack.add(RC.TOK_RBR);
                break;
            case RC.TOK_COL:
                if (stack.iSize > 0 && token == stack.getInt(-1)) {
                    stack.iSize--;
                }
                break;
            case RC.TOK_RPR:
            case RC.TOK_RBR:
            case RC.TOK_RBK:
                if (stack.iSize == 0) {
                    // Unmatched closer: surface as a parse error instead of an
                    // anonymous ArrayIndexOutOfBoundsException. This typically
                    // means the source is malformed OR the preprocessor/tokenizer
                    // emitted an unbalanced token stream for this sub-expression.
                    throw ex(token, makeErrTokPair(tpos), "balanced delim (stack empty)");
                }
                int top = stack.getInt(-1);
                if (top == token) {
                    stack.iSize--;
                } else {
                    String expected = RC.tokenName(top, null);
                    throw ex(token, makeErrTokPair(tpos), expected);
                }
                break;
            }
            ++tpos;
        }

        int ret = tpos - pos;
        pos = tpos;
        return ret;
    }

    private static final String ARGUMENTS_NAME = "arguments";

    /**
     * True if the RPN stream references the special identifier {@code arguments}.
     */
    static boolean rpnReferencesArguments(int[] ops, Object[] consts, int len) {
        if (ops == null || len <= 0) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            if ((ops[i] & 0xffff) == RC.TOK_SYMBOL) {
                Rv sym = (Rv) consts[i];
                if (sym != null && sym.type == Rv.SYMBOL && ARGUMENTS_NAME.equals(sym.str)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the list of {@link Node} children (statement list or block) references
     * {@code arguments}. Descends into child blocks, {@link RC#TOK_MUL} expression
     * nodes, etc.; does not look inside nested {@code function} bodies (they have
     * their own {@code arguments}).
     */
    public static boolean stmtBlockReferencesArguments(Node n) {
        if (n == null || n.children == null) {
            return false;
        }
        Pack p = n.children;
        for (int i = 0, lim = p.oSize; i < lim; i++) {
            Node c = (Node) p.getObject(i);
            if (c.tagType == RC.TOK_FUNCTION) {
                continue;
            }
            if (c.tagType == RC.TOK_MUL) {
                if (rpnReferencesArguments(c.rpnOps, c.rpnConsts, c.rpnLen)) {
                    return true;
                }
            } else if (stmtBlockReferencesArguments(c)) {
                return true;
            }
        }
        return false;
    }

    static void computeFunctionReferencesArguments(Node function) {
        if (function == null || function.children == null || function.children.oSize < 1) {
            return;
        }
        function.referencesArguments = stmtBlockReferencesArguments(
                (Node) function.children.getObject(-1));
    }

    /**
     * @param function
     */
    final private void funcBody(Node function) {
        int[] tti = tt.iArray;
        eat(RC.TOK_LPR);
        for (;;) {
            int pp = pos;
            for (int ii = pp + eatUntil(RC.TOK_COM, RC.TOK_RPR); --ii >= pp;) {
                if (tti[ii * RC.LEX_STRIDE] != RC.TOK_EOL) {
                    astNode(function, RC.TOK_MUL, pp, pos - pp);
                    break;
                }
            }
            int delim = tti[pos * RC.LEX_STRIDE];
            eat(delim);
            if (delim == RC.TOK_RPR) break;
        }
        eat(RC.TOK_LBR);
        astNode(function, RC.TOK_LBR, pos, eatUntil(RC.TOK_RBR, 0)); // '{' = block
        eat(RC.TOK_RBR);
        computeFunctionReferencesArguments(function);
    }
    
    // 
    public final Node astNode(Node parent, int type, int pos, int len) {
        Node n = new Node(this, type);
        if (parent != null) {
            parent.appendChild(n);
        }
        n.properties = tt;
        n.display = pos;
        n.state = len;
        return n;
    }
    
    final Rv evalString(String str, Rv callObj) {
        char[] cc = str.toCharArray();
        StringBuffer buf = new StringBuffer();
        for (int ii = 0, nn = cc.length; ii < nn; ++ii) {
            char c;
            switch (c = cc[ii]) {
            case '\\':
                if (ii + 1 < nn && cc[ii + 1] == '$') {
                    buf.append('$');
                    ++ii;
                }
                break;
            case '$':
                int iistart = ii;
                try {
                    String exp = null;
                    if ((c = cc[++ii]) == '{') {
                        int ccnt = 1;
                        while ((c = cc[++ii]) != '}' || --ccnt > 0) {
                            if (c == '{') ++ccnt;
                        }
                        exp = str.substring(iistart + 2, ii);
                    } else if (c >= 'A' && c <= 'Z' 
                            || c >= 'a' && c <= 'z' 
                            || c == '_' || c == '$') { // identifier start
                        int ccnt = 0;
                        while (++ii < nn 
                                && ((c = cc[ii]) >= 'A' && c <= 'Z' 
                                || c >= 'a' && c <= 'z'
                                || c >= '0' && c <= '9'
                                || c == '_' || c == '$'
                                || c == '.' || c == '[' 
                                || (c == ']' && ccnt >= 1))) {
                            if (c == '[' || c == ']') ccnt += '\\' - c; // [: 0x5B, \: 0x5C, ]: 0x5D
                        }
                        exp = str.substring(iistart + 1, ii--);
                    } else {
                        buf.append('$').append(c);
                    }
                    if (exp != null) {
                        this.reset(exp, null, 0, exp.length());
                        Node expnode = astNode(null, RC.TOK_MUL, 0, this.endpos);
                        Rv ret;
                        if ((ret = eval(callObj, expnode)).type == Rv.ERROR) return ret;
                        buf.append(ret.evalVal(callObj).toStr().str);
                    }
                } catch (Exception ex) {
                    buf.append('$');
                    ii = iistart; // recover
                }
                break;
            default:
                buf.append(c);
                break;
            }
        }
        return new Rv(buf.toString());
    }
    
    final RuntimeException ex(char encountered, int pos, String expected) {
        return ex("LEXER", pos, "'" + encountered + "'", expected);
    }
    
    final RuntimeException ex(int ttype, Object tinfo, String expected) {
        Object[] oo = (Object[]) tinfo;
        int pos = ((int[]) oo[0])[0];
        return ex("PARSER", pos, RC.tokenName(ttype, null), expected);
    }
    
    final RuntimeException ex(String type, int pos, String encountered, String expected) {
        StringBuffer buf = new StringBuffer(type).append(" ERROR: at position ");
        Pack l = loc(pos);
        buf.append("line " + l.iSize + " column " + l.oSize);
        buf.append(", encountered ").append(encountered);
        if (expected != null) {
            buf.append(", expects ").append(expected);
        }
        buf.append('.');
        return new RuntimeException(buf.toString());
    }
    
    final Pack loc(int pos) {
        int i1, i2, row, col;
        i1 = i2 = row = col = 0;
        for (;;) {
            i2 = src.indexOf('\n', i1);
            if (pos >= i1 && pos <= i2 || i2 < 0) {
                col = pos - i1;
                break;
            }
            i1 = i2 + 1;
            ++row;
        }
        Pack ret = new Pack(-1, -1);
        ret.iSize = row + 1;
        ret.oSize = col + 1;
        return ret;
    }
    
    /**
     * Special symbols are members of global/call object
     * "this", "null", "undefined", "NaN", "true", "false", "arguments"
     */
    static final String KEYWORDS = 
        "if," +  
        "else," + 
        "for," + 
        "while," + 
        "do," + 
        "break," + 
        "continue," + 
        "var," + 
        "function," + 
        "return," + 
        "with," +
        "new," + 
        "in," + 
        "switch," + 
        "case," + 
        "default," + 
        "typeof," + 
        "delete," + 
        "instanceof," + 
        "throw," + 
        "try," + 
        "catch," + 
        "finally," + 
        "async," + 
        "await," + 
        "";
    
    static final Rhash htKeywords;

    static final String OPTRINDEX = 
        ",46," +                        // .
        ",442," +                       // **
        ",42,47,37," +                  // *, /, %
        ",43,45," +                     // +, -
        ",460,462,641," +               // <<, >>, >>>
        ",60,62,260,262,142,148," +     // <, >, <=, >=, in, instanceof
        ",261,233,661,633," +           // ==, !=, ===, !==
        ",38," +                        // &
        ",94," +                        // ^
        ",124," +                       // |
        ",438," +                       // &&
        ",524," +                       // ||
        ",61,243,245,242,247,237,238,324,294,660,662,693," + // =, +=, -=, *=, /=, %=, &=, |=, ^=, <<=, >>=, >>>= 
        ",44," +                        // ,
        ",1443,1445," +                 // POSTINC, POSTDEC
        ",443,445,147," +               // INC, DEC, delete
        ",1043,1045,146,154,33,126," +      // POS, NEG, typeof, await, !, ~
        ",63," +                        // ?
        ",58," +                        // COLON
        ",1058," +                      // jsoncol
        ",137," +                       // var
        ",40," +                        // (
        ",1040,1141," +                 // invoke, init
        ",91," +                        // [
        ",1091," +                      // jsonarr
        ",123," +                       // { (jsonobj)
        ",41," +                        // )
        ",93," +                        // ]
        ",125," +                       // }
        ",1044," +                      // SEPTOR
        ",1," +                         // NUMBER
        ",2," +                         // STRING
        ",3," +                         // SYMBOL
        ",999," +                       // EOF
        "";
    
    static final Rhash htOptrIndex;
    static final Rhash htOptrType;

    static final int OPTR_TABLE_SIZE = 2048;
    public static final int[] optrIndex = new int[OPTR_TABLE_SIZE];
    public static final int[] optrType = new int[OPTR_TABLE_SIZE];

    // Per-interpreter pool of operand stacks used by eval(). Grows on demand
    // as eval() recurses (eval -> call -> eval ...). Reusing these Packs turns
    // "one allocation per expression" into zero on the steady state.
    private Pack[] opndPool = new Pack[16];
    /** Reused {@link #expression} operator / state stacks (not the RPN output, which is retained on AST). */
    private Pack exprOpPack;
    private Pack exprStPack;
    private int[] exprRpnOps;
    private Object[] exprRpnConsts;
    private int exprRpnSize;
    // Frame-local temporary Rv cells used for non-escaping eval intermediates
    // such as SYMBOL -> LVALUE refs. Cleared when the eval frame exits.
    private Rv[][] evalTempPool = new Rv[16][];
    private int[] evalTempUsed = new int[16];
    private int evalDepth = 0;
    /** Reused per JS call() control-flow stack; avoids new Pack(20,20) per function invocation. */
    private int callDepth = 0;
    private Pack[] callStackPool = new Pack[16];

    /** Pooled empty scope objects for {@code call()} ({@code funCo} / invoke bind records). */
    private Rv[] callObjectFree = new Rv[48];
    private int callObjectFreeSize;
    private final Rv[] callSiteResolve = new Rv[2];

    /**
     * Polymorphic inline cache for a single RPN call site ({@code TOK_INVOKE}).
     * {@code KIND_DIRECT} — bare {@code f()} with {@code f} a FUNCTION;
     * {@code KIND_MEMBER} — {@code o.m} with {@code o} a stable receiver map.
     */
    static final class CallSiteCache {
        static final int SLOTS = 4;
        static final int KIND_NONE = 0;
        static final int KIND_DIRECT = 1;
        static final int KIND_MEMBER = 2;
        int[] kind = new int[SLOTS];
        Rv[] directFun = new Rv[SLOTS];
        Rv[] mHolder = new Rv[SLOTS];
        String[] mKey = new String[SLOTS];
        Rhash[] mMap = new Rhash[SLOTS];
        int[] mStamp = new int[SLOTS];
        int[] mLayout = new int[SLOTS];
        Rv[] mFunObj = new Rv[SLOTS];
        int write;
    }

    /** RPN placeholder for {@code TOK_INVOKE} / {@code TOK_INIT} — holds optional {@link #csc}. */
    static final class InvokeOpRv extends Rv {
        CallSiteCache csc;

        InvokeOpRv() {
            super(0);
        }
    }

    Rv borrowCallObject() {
        Rv c;
        if (callObjectFreeSize > 0) {
            c = callObjectFree[--callObjectFreeSize];
        } else {
            c = new Rv(Rv.OBJECT, Rv._Object);
        }
        c.type = Rv.OBJECT;
        c.ctorOrProt = Rv._Object;
        c.prev = null;
        c.gen = 0;
        c.opaque = ACTIVE_CALL_OBJECT;
        c.num = 0;
        c.str = null;
        c.obj = null;
        c.prop.clearPreserveCapacity();
        return c;
    }

    void recycleCallObject(Rv c) {
        if (c == null) {
            return;
        }
        if (c.opaque == CAPTURED_CALL_OBJECT) {
            return;
        }
        c.prev = null;
        c.opaque = null;
        c.num = 0;
        c.str = null;
        c.obj = null;
        c.gen = 0;
        c.ctorOrProt = Rv._Object;
        c.prop.clearPreserveCapacity();
        if (callObjectFreeSize < callObjectFree.length) {
            callObjectFree[callObjectFreeSize++] = c;
        }
    }

    void installInvokeCallSite(InvokeOpRv isite, Rv fun, Rv funObj) {
        if (isite == null || funObj == null || funObj.type < Rv.FUNCTION) {
            return;
        }
        CallSiteCache csc = isite.csc;
        if (csc == null) {
            isite.csc = csc = new CallSiteCache();
        }
        int w = csc.write;
        if (fun.type == Rv.FUNCTION) {
            csc.kind[w] = CallSiteCache.KIND_DIRECT;
            csc.directFun[w] = fun;
        } else if (fun.type == Rv.LVALUE) {
            Rv h0 = fun.co;
            Rhash hp0 = h0 != null ? h0.prop : null;
            if (hp0 != null) {
                csc.kind[w] = CallSiteCache.KIND_MEMBER;
                csc.mHolder[w] = h0;
                csc.mKey[w] = fun.str;
                csc.mMap[w] = hp0;
                csc.mStamp[w] = hp0.gen;
                csc.mLayout[w] = hp0.layoutFp;
                csc.mFunObj[w] = funObj;
            }
        }
        csc.write = (w + 1) % CallSiteCache.SLOTS;
    }

    /**
     * Tries the polymorphic call-site cache for {@code TOK_INVOKE}. On success,
     * {@code out[0] = funRef} and {@code out[1] = funObj}.
     */
    boolean tryCallSiteCache(InvokeOpRv isite, Rv fun, Rv callObj, int depth, Rv[] out) {
        CallSiteCache csc;
        if (isite == null || (csc = isite.csc) == null) {
            return false;
        }
        for (int si = 0; si < CallSiteCache.SLOTS; si++) {
            if (csc.kind[si] == CallSiteCache.KIND_DIRECT) {
                if (fun.type == Rv.FUNCTION && fun == csc.directFun[si]) {
                    out[0] = acquireEvalTemp(depth).resetTempLvalue("inline", callObj);
                    out[1] = fun;
                    return true;
                }
            } else if (csc.kind[si] == CallSiteCache.KIND_MEMBER) {
                if (fun.type == Rv.LVALUE) {
                    Rv h0 = fun.co;
                    Rhash hp0 = h0 != null ? h0.prop : null;
                    String k0 = fun.str;
                    if (h0 == csc.mHolder[si] && hp0 == csc.mMap[si] && hp0 != null
                            && csc.mStamp[si] == hp0.gen && csc.mLayout[si] == hp0.layoutFp
                            && (k0 == csc.mKey[si]
                            || (k0 != null && csc.mKey[si] != null && csc.mKey[si].equals(k0)))) {
                        out[0] = fun.evalRef(callObj, acquireEvalTemp(depth));
                        out[1] = csc.mFunObj[si];
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Rv acquireEvalTemp(int depth) {
        Rv[] pool = evalTempPool[depth];
        if (pool == null) {
            pool = new Rv[8];
            evalTempPool[depth] = pool;
        }
        int used = evalTempUsed[depth];
        if (used >= pool.length) {
            Rv[] grown = new Rv[pool.length * 2];
            System.arraycopy(pool, 0, grown, 0, pool.length);
            evalTempPool[depth] = pool = grown;
        }
        Rv r = pool[used];
        if (r == null) {
            r = new Rv();
            pool[used] = r;
        }
        evalTempUsed[depth] = used + 1;
        return r;
    }

    private boolean isEvalTemp(int depth, Rv r) {
        Rv[] pool = evalTempPool[depth];
        int used = evalTempUsed[depth];
        for (int i = 0; i < used; i++) {
            if (pool[i] == r) {
                return true;
            }
        }
        return false;
    }

    private void clearEvalTemps(int depth) {
        Rv[] pool = evalTempPool[depth];
        int used = evalTempUsed[depth];
        if (pool != null) {
            for (int i = 0; i < used; i++) {
                if (pool[i] != null) {
                    pool[i].clearEvalTemp();
                }
            }
        }
        evalTempUsed[depth] = 0;
    }
    
    static final String PRECEDENCE = 
        "Paaaaaaaaaaaaapaaaaapaaaapa" + // DOT
        "PPaaaaaaaaaaaaPPPaaapaaaapa" + // POW
        "PPPaaaaaaaaaaaPPPaaapaaaapa" + // MUL
        "PPPPaaaaaaaaaaPPPaaapaaaapa" + // ADD
        "PPPPPaaaaaaaaaPPPaaapaaaapa" + // LSH
        "PPPPPPaaaaaaaaPPPaaapaaaapa" + // LES
        "PPPPPPPaaaaaaaPPPaaapaaaapa" + // EQ
        "PPPPPPPPaaaaaaPPPaaapaaaapa" + // BAN
        "PPPPPPPPPaaaaaPPPaaapaaaapa" + // BXO
        "PPPPPPPPPPaaaaPPPaaapaaaapa" + // BOR
        "PPPPPPPPPPPaaaPPPaaapaaaapa" + // AND
        "PPPPPPPPPPPPaaPPPaaapaaaapa" + // OR
        "PPPPPPPPPPPPaaPPPpppaaaaapa" + // ASS
        "PPPPPPPPPPPPPPPPPpPpPapappa" + // COMMA
        "paaaaaaaaaaaaapppaaapaaaapa" + // POSTINC
        "paaaaaaaaaaaaapppaaapaaaapa" + // INC
        "paaaaaaaaaaaaapppaaapaaaapa" + // POS
        "RRRRRRRRRRRRccRRRcccpccccpc" + // QMK
        "PPPPPPPPPPPPppPPP?Ppppppppp" + // COLON
        "pppppppppppppppppppppppppap" + // jsoncol
        "ppppppppppppppppppppppppppa" + // var
        "ccccccccccccccpcccccccccccc" + // (
        "Teeeeeeeeeeeeepeeeeeeeeeepe" + // invoke
        "Rcccccccccccccpccccccccccpc" + // [
        "pgggggggggggggpggggggggggpg" + // jsonarr
        "piiiiiiiiiiiiipiiiiiiiiiipi" + // {
        "PPPPPPPPPPPPPPPPPpPpp/_pppp" + // )
        "PPPPPPPPPPPPPPPPPpPpppp__pp" + // ]
        "PPPPPPPPPPPPPPPPPpPPppppp_p" + // }
        "PPPPPPPPPPPPPpPPPpPQpp\u0001p\u0001\u0001p" + // SEPTOR
        "AAAAAAAAAAAAAAppAAAApAAAAAA" + // NUMBER
        "AAAAAAAAAAAAAAppAAAApAAAAAA" + // STRING
        "AAAAAAAAAAAAAApAAAAAAAAAAAA" + // SYMBOL
        "PPPPPPPPPPPPPPPPPpPpPppppp\u0001" + // EOF
        "";
    
    static final int PT_COL = 27;
    static final int PT_ROW = 34;
    static final int[][] prioTable;

    private static final String NATIVE_FUNC =
        // id, name, numArguments
        "102,Function,1," +
        "";
    
    static final Rhash htNativeIndex;
    static final Rhash htNativeLength;
    public static final long bootTime = System.currentTimeMillis();
    static final Random random = new Random(bootTime);
    static final Pack EMPTY_BLOCK = new Pack(-1, 0);
    
    static {
        Rhash ht = htKeywords = new Rhash(41);
        Pack pk = split(KEYWORDS, ",");
        Object[] pkar = pk.oArray;
        for (int i = pk.oSize; --i >= 0; ht.put((String) pkar[i], 130 + i));
        
        Rhash ih = htOptrIndex = new Rhash(53);
        Rhash ot = htOptrType = new Rhash(53);
        for (int i = optrIndex.length; --i >= 0;) optrIndex[i] = -1;
        pk = split(OPTRINDEX, ",");
        pkar = pk.oArray;
        for (int i = 0, idx = -1, n = pk.oSize; i < n; i++) {
            String s = (String) pkar[i];
            if (s.length() == 0) {
                ++idx;
            } else {
                int optr;
                ih.put(optr = Integer.parseInt(s), idx);
                int type = idx >= 14 && idx <= 16 ? 1   // unary op
                        : idx >= 1 && idx <= 9 ? 2      // binary op
                        : idx == 12 ? 3                 // assign op 
                        : 0;                            // misc op
                ot.put(optr, type);
                if (optr >= 0 && optr < OPTR_TABLE_SIZE) {
                    optrIndex[optr] = idx;
                    optrType[optr] = type;
                }
            }
        }
        
        int[][] pt = prioTable = new int[PT_ROW][PT_COL];
        char[] cc = PRECEDENCE.toCharArray();
        for (int i = 0, ii = 0; i < PT_ROW; i++) {
            for (int j = 0; j < PT_COL; j++) {
                pt[i][j] = cc[ii++];
            }
        }

        ht = htNativeIndex = new Rhash(61);
        Rhash ht2 = htNativeLength = new Rhash(61);
        pk = split(NATIVE_FUNC, ",");
        pkar = pk.oArray;
        for (int i = 0, n = pk.oSize; i < n; i += 3) {
            String name = (String) pkar[i + 1];
            int id = Integer.parseInt((String) pkar[i]); 
            int len = Integer.parseInt((String) pkar[i + 2]);
            ht.put(name, id);
            ht2.put(name, len);
        }
    }
    
    static final int keywordIndex(String s) {
        Rv entry;
        return (entry = htKeywords.getEntry(0, s)) == null ? -1 : entry.num;
    }
    
    static final Pack split(String src, String delim) {
        Pack ret = new Pack(-1, 20);
        if (delim.length() == 0) {
            char[] cc = src.toCharArray();
            for (int i = 0, n = cc.length; i < n; ret.add("" + cc[i++]));
            return ret;
        }
        int i1, i2;
        i1 = i2 = 0;
        for (;;) {
            i2 = src.indexOf(delim, i1);
            if (i2 < 0) break; // reaches end
            ret.add(src.substring(i1, i2));
            i1 = i2 + 1;
        }
        if (i1 < src.length()) {
            ret.add(src.substring(i1));
        }
        return ret;
    }
    
    final static void addToken(Pack tokens, int type, int pos, int len, Object val) {
        tokens.add(type).add(pos).add(len);
        tokens.add(val);
    }

    public final int getNativeFunctionCount() {
        return function_list.size();
    }

    /** Shrinks backing arrays for {@link Pack} pools and the native table (post-spike / idle). */
    public final void trimInternalPools() {
        function_list.trimToSize();
        for (int i = 0; i < opndPool.length; i++) {
            Pack p = opndPool[i];
            if (p != null) {
                p.trimToSize();
            }
        }
        for (int i = 0; i < callStackPool.length; i++) {
            Pack p = callStackPool[i];
            if (p != null) {
                p.trimToSize();
            }
        }
    }
}
