package net.cnjm.j2me.tinybro;

/**
 * Source-level desugaring for ES6 syntax that the tokenizer / parser can't
 * handle directly on a CLDC 1.1 budget.
 *
 * <p>We avoid adding new parser states for these constructs by rewriting them
 * to the subset RockScript already understands before the lexer ever sees the
 * code:</p>
 *
 * <ul>
 *   <li>Template literals {@code `hello ${name}!`} →
 *       {@code ("hello " + (name) + "!")}.</li>
 *   <li>Arrow functions {@code (a, b) => a + b} →
 *       {@code function(a, b){return a + b;}}. Expression-bodied arrows get
 *       wrapped with {@code {return ...;}}; block-bodied arrows are left as-is
 *       after dropping the {@code =>} token.</li>
 *   <li>{@code class Foo extends Bar { ... }} → {@code function}-based shim,
 *       done in {@link #preprocessClasses(String)}. Supports constructor,
 *       methods, static methods, getters/setters are NOT supported.</li>
 *   <li>{@code for (let x of arr) { ... }} → classic index-based for over
 *       arrays / strings (and Map/Set via {@code .entries()}/{@code .values()}).</li>
 * </ul>
 *
 * <p>This preprocessor runs once per script-source, so it is O(n) and does not
 * touch the hot eval loop.</p>
 */
final class Es6Preproc {

    private Es6Preproc() {}

    static String process(String src) {
        if (src == null || src.length() == 0) return src;
        // Order matters: classes may contain arrows, arrows may contain templates.
        src = preprocessClasses(src);
        src = preprocessForOf(src);
        src = preprocessTemplates(src);
        src = preprocessArrows(src);
        src = preprocessDefaultParams(src);
        src = preprocessDestructuring(src);
        src = preprocessShorthandProps(src);
        src = preprocessSpread(src);
        return src;
    }

    // ==================================================================
    //  Common helpers
    // ==================================================================

    static boolean isWs(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

    static boolean isIdentStart(char c) {
        return c == '_' || c == '$' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    static boolean isIdentPart(char c) {
        return isIdentStart(c) || (c >= '0' && c <= '9');
    }

    // CLDC 1.1's StringBuffer lacks substring(int,int); provide a tiny helper.
    static String sbSubstr(StringBuffer sb, int start, int end) {
        char[] buf = new char[end - start];
        for (int i = 0; i < buf.length; i++) buf[i] = sb.charAt(start + i);
        return new String(buf);
    }

    /**
     * Copy a string/template/comment literal starting at {@code i} from {@code src}
     * into {@code out}, returning the new position past the closing delimiter.
     * Does nothing and returns -1 when the char at {@code i} is not a literal start.
     */
    static int copyLiteral(String src, int i, StringBuffer out) {
        int n = src.length();
        char c = src.charAt(i);
        if (c == '"' || c == '\'') {
            out.append(c);
            i++;
            while (i < n) {
                char cc = src.charAt(i);
                out.append(cc);
                i++;
                if (cc == '\\' && i < n) { out.append(src.charAt(i)); i++; continue; }
                if (cc == c) break;
            }
            return i;
        }
        if (c == '`') {
            // copy untouched; templates are rewritten in their own pass
            out.append(c);
            i++;
            while (i < n) {
                char cc = src.charAt(i);
                out.append(cc);
                i++;
                if (cc == '\\' && i < n) { out.append(src.charAt(i)); i++; continue; }
                if (cc == '`') break;
            }
            return i;
        }
        if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
            while (i < n && src.charAt(i) != '\n') { out.append(src.charAt(i)); i++; }
            return i;
        }
        if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
            out.append("/*"); i += 2;
            while (i < n) {
                char cc = src.charAt(i);
                if (cc == '*' && i + 1 < n && src.charAt(i + 1) == '/') {
                    out.append("*/"); i += 2; break;
                }
                out.append(cc); i++;
            }
            return i;
        }
        return -1;
    }

    // ==================================================================
    //  Template literals
    // ==================================================================

    static String preprocessTemplates(String src) {
        int n = src.length();
        if (src.indexOf('`') < 0) return src;
        StringBuffer out = new StringBuffer(n + 16);
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'' || (c == '/' && i + 1 < n && (src.charAt(i + 1) == '/' || src.charAt(i + 1) == '*'))) {
                int j = copyLiteral(src, i, out);
                if (j > i) { i = j; continue; }
            }
            if (c == '`') {
                i = rewriteTemplate(src, i, out);
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** Rewrite a single {@code `...`} literal into a parenthesised concatenation. */
    static int rewriteTemplate(String src, int start, StringBuffer out) {
        int n = src.length();
        int i = start + 1;
        out.append("(\"");
        boolean anyPart = false;
        StringBuffer piece = new StringBuffer();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '`') { i++; break; }
            if (c == '\\' && i + 1 < n) {
                char nc = src.charAt(i + 1);
                switch (nc) {
                    case '`':  piece.append('`'); break;
                    case '$':  piece.append('$'); break;
                    case '\\': piece.append("\\\\"); break;
                    case '\'': piece.append("\\'"); break;
                    case '"':  piece.append("\\\""); break;
                    case 'n':  piece.append("\\n"); break;
                    case 't':  piece.append("\\t"); break;
                    case 'r':  piece.append("\\r"); break;
                    default:   piece.append('\\').append(nc); break;
                }
                i += 2;
                continue;
            }
            if (c == '$' && i + 1 < n && src.charAt(i + 1) == '{') {
                out.append(piece.toString()).append("\" + (");
                piece.setLength(0);
                int depth = 1;
                i += 2;
                StringBuffer expr = new StringBuffer();
                while (i < n && depth > 0) {
                    char cc = src.charAt(i);
                    if (cc == '{') { depth++; expr.append(cc); i++; continue; }
                    if (cc == '}') { depth--; if (depth == 0) { i++; break; } expr.append(cc); i++; continue; }
                    if (cc == '`') {
                        StringBuffer inner = new StringBuffer();
                        i = rewriteTemplate(src, i, inner);
                        expr.append(inner.toString());
                        continue;
                    }
                    if (cc == '"' || cc == '\'') {
                        int j = copyLiteral(src, i, expr);
                        if (j > i) { i = j; continue; }
                    }
                    expr.append(cc);
                    i++;
                }
                // inner templates were already desugared recursively
                out.append(Es6Preproc.preprocessTemplates(expr.toString())).append(") + \"");
                anyPart = true;
                continue;
            }
            if (c == '"') { piece.append("\\\""); i++; continue; }
            if (c == '\\') { piece.append("\\\\"); i++; continue; }
            if (c == '\n') { piece.append("\\n"); i++; continue; }
            if (c == '\r') { i++; continue; }
            piece.append(c);
            i++;
        }
        out.append(piece.toString()).append("\")");
        // anyPart suppression: if we never added an expr, keeping the parens is fine.
        // Compiler silence — 'anyPart' is retained for future diagnostics.
        if (anyPart) { /* no-op */ }
        return i;
    }

    // ==================================================================
    //  Arrow functions
    // ==================================================================

    static String preprocessArrows(String src) {
        int n = src.length();
        if (src.indexOf("=>") < 0) return src;
        StringBuffer out = new StringBuffer(n + 32);
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            // Skip over string literals and comments untouched
            if (c == '"' || c == '\'' || c == '`' || (c == '/' && i + 1 < n && (src.charAt(i + 1) == '/' || src.charAt(i + 1) == '*'))) {
                int j = copyLiteral(src, i, out);
                if (j > i) { i = j; continue; }
            }
            if (c == '=' && i + 1 < n && src.charAt(i + 1) == '>') {
                handleArrow(out, src, i + 2);
                i = arrowConsumedUpTo;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static int arrowConsumedUpTo;

    /** Handle a {@code =>} at source position {@code afterArrow} (i.e. char after '>'). */
    static void handleArrow(StringBuffer out, String src, int afterArrow) {
        int n = src.length();
        // 1) Backtrack in out to find the params (either ")" group or a single identifier).
        int b = out.length() - 1;
        while (b >= 0 && isWs(out.charAt(b))) b--;
        int paramsStart;
        if (b >= 0 && out.charAt(b) == ')') {
            int depth = 1, pp = b;
            while (--pp >= 0) {
                char cc = out.charAt(pp);
                if (cc == '"' || cc == '\'') {
                    // skip backwards over string — naive but ok for well-formed code
                    char q = cc;
                    while (--pp >= 0 && out.charAt(pp) != q) ;
                    continue;
                }
                if (cc == ')') depth++;
                else if (cc == '(') { if (--depth == 0) break; }
            }
            paramsStart = pp;
            out.insert(paramsStart, "function");
        } else {
            int pp = b;
            while (pp >= 0 && isIdentPart(out.charAt(pp))) pp--;
            paramsStart = pp + 1;
            String id = sbSubstr(out, paramsStart, b + 1);
            out.setLength(paramsStart);
            out.append("function(").append(id).append(")");
        }
        // 2) Skip ws after =>
        int i = afterArrow;
        while (i < n && isWs(src.charAt(i))) i++;
        // 3) If body is a block, just drop =>; otherwise wrap expr in {return ...;}
        if (i < n && src.charAt(i) == '{') {
            arrowConsumedUpTo = i;
            return;
        }
        out.append("{return ");
        int depth = 0;
        while (i < n) {
            char cc = src.charAt(i);
            if (cc == '"' || cc == '\'' || cc == '`') {
                int j = copyLiteral(src, i, out);
                if (j > i) { i = j; continue; }
            }
            if (cc == '(' || cc == '[' || cc == '{') { depth++; out.append(cc); i++; continue; }
            if (cc == ')' || cc == ']' || cc == '}') {
                if (depth == 0) break;
                depth--; out.append(cc); i++; continue;
            }
            if (depth == 0 && (cc == ',' || cc == ';' || cc == '\n')) break;
            out.append(cc);
            i++;
        }
        out.append(";}");
        arrowConsumedUpTo = i;
    }

    // ==================================================================
    //  Spread in call arguments:  foo(...arr)  →  foo.apply(null, arr)
    // ==================================================================

    /**
     * Minimal spread / rest support:
     *
     * <ul>
     *   <li>{@code f(...arr)} — single spread argument — rewritten to {@code f.apply(null, arr)}.</li>
     *   <li>{@code [...a, ...b, x]} array literals — rewritten to {@code a.concat(b).concat([x])}.</li>
     *   <li>{@code function f(...rest)} — rewritten to {@code function f()} plus a leading
     *       {@code var rest = Array.prototype.slice.call(arguments, N);} in the body.</li>
     * </ul>
     */
    static String preprocessSpread(String src) {
        if (src.indexOf("...") < 0) return src;
        src = rewriteCallSpread(src);
        src = rewriteRestParams(src);
        return src;
    }

    static String rewriteCallSpread(String src) {
        int n = src.length();
        StringBuffer out = new StringBuffer(n + 16);
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'' || c == '`' || (c == '/' && i + 1 < n && (src.charAt(i + 1) == '/' || src.charAt(i + 1) == '*'))) {
                int j = copyLiteral(src, i, out);
                if (j > i) { i = j; continue; }
            }
            if (c == '(' && out.length() > 0) {
                // Is this a call (prev char is identifier/closing bracket)?
                int b = out.length() - 1;
                while (b >= 0 && isWs(out.charAt(b))) b--;
                boolean isCall = b >= 0 && (isIdentPart(out.charAt(b)) || out.charAt(b) == ')' || out.charAt(b) == ']');
                if (isCall && isIdentPart(out.charAt(b))) {
                    // Scan back the identifier and reject reserved words that
                    // syntactically take a '(' but aren't callsites. In
                    // particular `function(...x)` is a rest parameter declaration,
                    // not a call with spread; if we rewrote it to
                    // `function.apply(null, x)` the parser would later fail on
                    // `function.` (keyword treated as value).
                    int idEnd = b + 1, idStart = b;
                    while (idStart > 0 && isIdentPart(out.charAt(idStart - 1))) idStart--;
                    String prevId = sbSubstr(out, idStart, idEnd);
                    if ("function".equals(prevId) || "if".equals(prevId)
                            || "while".equals(prevId) || "for".equals(prevId)
                            || "switch".equals(prevId) || "catch".equals(prevId)
                            || "return".equals(prevId) || "typeof".equals(prevId)
                            || "delete".equals(prevId) || "void".equals(prevId)
                            || "new".equals(prevId) || "throw".equals(prevId)
                            || "in".equals(prevId)   || "of".equals(prevId)
                            || "do".equals(prevId)) {
                        isCall = false;
                    }
                }
                if (isCall) {
                    // Check if the argument list starts with spread
                    int k = i + 1;
                    while (k < n && isWs(src.charAt(k))) k++;
                    if (k + 2 < n && src.charAt(k) == '.' && src.charAt(k + 1) == '.' && src.charAt(k + 2) == '.') {
                        // Find matching ')'
                        int depth = 1, p = i + 1;
                        while (p < n && depth > 0) {
                            char cc = src.charAt(p);
                            if (cc == '"' || cc == '\'' || cc == '`') {
                                StringBuffer tmp = new StringBuffer();
                                int j = copyLiteral(src, p, tmp);
                                if (j > p) { p = j; continue; }
                            }
                            if (cc == '(') depth++;
                            else if (cc == ')') { depth--; if (depth == 0) break; }
                            p++;
                        }
                        String spreadExpr = src.substring(k + 3, p).trim();
                        // Rewrite call: fn(...) → fn.apply(null, spreadExpr)
                        // We have already emitted 'fn' into out. Append .apply(null, ...)
                        // Strip the trailing ws in out
                        int tail = out.length();
                        while (tail > 0 && isWs(out.charAt(tail - 1))) tail--;
                        out.setLength(tail);
                        out.append(".apply(null,").append(spreadExpr).append(')');
                        i = p + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    static String rewriteRestParams(String src) {
        int n = src.length();
        StringBuffer out = new StringBuffer(n + 16);
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'' || c == '`' || (c == '/' && i + 1 < n && (src.charAt(i + 1) == '/' || src.charAt(i + 1) == '*'))) {
                int j = copyLiteral(src, i, out);
                if (j > i) { i = j; continue; }
            }
            if (c == 'f' && i + 8 <= n && src.regionMatches(false, i, "function", 0, 8)
                    && (i == 0 || !isIdentPart(src.charAt(i - 1)))
                    && (i + 8 >= n || !isIdentPart(src.charAt(i + 8)))) {
                int lpr = src.indexOf('(', i + 8);
                if (lpr < 0) { out.append(c); i++; continue; }
                int pStart = lpr + 1, depth = 1, p = pStart;
                while (p < n && depth > 0) {
                    char cc = src.charAt(p);
                    if (cc == '(') depth++;
                    else if (cc == ')') { depth--; if (depth == 0) break; }
                    p++;
                }
                String params = src.substring(pStart, p);
                int restIdx = params.indexOf("...");
                if (restIdx < 0) { out.append(c); i++; continue; }
                // Split params: names before rest, name after ...
                String before = params.substring(0, restIdx).trim();
                if (before.endsWith(",")) before = before.substring(0, before.length() - 1).trim();
                String restName = params.substring(restIdx + 3).trim();
                // Count fixed params
                int fixedCount = 0;
                if (before.length() > 0) {
                    int depthP = 0;
                    fixedCount = 1;
                    for (int q = 0; q < before.length(); q++) {
                        char cc = before.charAt(q);
                        if (cc == '(' || cc == '[' || cc == '{') depthP++;
                        else if (cc == ')' || cc == ']' || cc == '}') depthP--;
                        else if (cc == ',' && depthP == 0) fixedCount++;
                    }
                }
                out.append(src.substring(i, lpr + 1));
                out.append(before);
                out.append(')');
                i = p + 1;
                while (i < n && isWs(src.charAt(i))) out.append(src.charAt(i++));
                if (i < n && src.charAt(i) == '{') {
                    out.append('{');
                    out.append("var ").append(restName)
                       .append("=Array.prototype.slice.call(arguments,").append(fixedCount).append(");");
                    i++;
                }
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    // ==================================================================
    //  for...of
    // ==================================================================

    static String preprocessForOf(String src) {
        if (src.indexOf(" of ") < 0 && src.indexOf("\tof ") < 0) return src;
        int n = src.length();
        StringBuffer out = new StringBuffer(n + 32);
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'' || c == '`' || (c == '/' && i + 1 < n && (src.charAt(i + 1) == '/' || src.charAt(i + 1) == '*'))) {
                int j = copyLiteral(src, i, out);
                if (j > i) { i = j; continue; }
            }
            // Match "for (" optionally followed by "var|let|const".
            if (c == 'f' && i + 4 < n
                    && src.charAt(i + 1) == 'o'
                    && src.charAt(i + 2) == 'r'
                    && (i == 0 || !isIdentPart(src.charAt(i - 1)))
                    && isWsOrOpen(src, i + 3)) {
                int k = i + 3;
                while (k < n && isWs(src.charAt(k))) k++;
                if (k < n && src.charAt(k) == '(') {
                    int afterLpr = k + 1;
                    // Parse simple "for (VAR ID of EXPR)" heuristically
                    int scan = afterLpr;
                    while (scan < n && isWs(src.charAt(scan))) scan++;
                    int declEnd = scan;
                    // optional var/let/const keyword
                    String kw = readIdent(src, scan);
                    if ("var".equals(kw) || "let".equals(kw) || "const".equals(kw)) {
                        declEnd = scan + kw.length();
                        while (declEnd < n && isWs(src.charAt(declEnd))) declEnd++;
                    }
                    // loopVar may be a plain identifier OR a destructuring pattern
                    // ( {a,b}  or  [a,b] ). We capture the raw text and later
                    // reuse it as the declaration target.
                    String loopVar = readIdent(src, declEnd);
                    int afterVar;
                    String loopPattern = null; // non-null if destructuring
                    if (loopVar != null) {
                        afterVar = declEnd + loopVar.length();
                    } else if (declEnd < n && (src.charAt(declEnd) == '{' || src.charAt(declEnd) == '[')) {
                        char open = src.charAt(declEnd);
                        char close = open == '{' ? '}' : ']';
                        int d = 1, pp = declEnd + 1;
                        while (pp < n && d > 0) {
                            char cc = src.charAt(pp);
                            if (cc == open) d++;
                            else if (cc == close) { d--; if (d == 0) break; }
                            pp++;
                        }
                        if (d == 0) {
                            loopPattern = src.substring(declEnd, pp + 1);
                            afterVar = pp + 1;
                        } else {
                            afterVar = declEnd;
                        }
                    } else {
                        afterVar = declEnd;
                    }
                    int ws = afterVar;
                    while (ws < n && isWs(src.charAt(ws))) ws++;
                    if ((loopVar != null || loopPattern != null)
                            && ws + 1 < n && src.charAt(ws) == 'o' && src.charAt(ws + 1) == 'f'
                            && (ws + 2 >= n || isWs(src.charAt(ws + 2)))) {
                        // Found: for (LOOPVAR of ...). Parse expression until matching ')'
                        int exprStart = ws + 3;
                        while (exprStart < n && isWs(src.charAt(exprStart))) exprStart++;
                        int depth = 1, p = exprStart;
                        while (p < n && depth > 0) {
                            char cc = src.charAt(p);
                            if (cc == '"' || cc == '\'' || cc == '`') {
                                StringBuffer tmp = new StringBuffer();
                                int j = copyLiteral(src, p, tmp);
                                if (j > p) { p = j; continue; }
                            }
                            if (cc == '(') depth++;
                            else if (cc == ')') { depth--; if (depth == 0) break; }
                            p++;
                        }
                        String collExpr = src.substring(exprStart, p);
                        // Rewrite:
                        // for (var __of0 = 0, __of0s = EXPR; __of0 < __of0s.length; __of0++) { var VAR = __of0s[__of0]; ...body unchanged... }
                        // Pick unique-ish names using current out length as seed.
                        String suf = "_" + out.length();
                        out.append("for(var __it").append(suf).append("=0,__src").append(suf)
                           .append("=(").append(collExpr).append(")")
                           .append(",__len").append(suf).append("=(__src").append(suf)
                           .append(".length!==undefined?__src").append(suf).append(".length:0);__it")
                           .append(suf).append("<__len").append(suf).append(";__it").append(suf)
                           .append("++)");
                        // Advance past ')'
                        i = p + 1;
                        // Expect '{' next; inject declaration at start of body.
                        while (i < n && isWs(src.charAt(i))) out.append(src.charAt(i++));
                        // Element expression: plain numeric indexing works for both arrays
                        // and primitive strings (see Rv.get() — string[i] returns the
                        // one-character substring, matching ES5 semantics). Avoid the
                        // typeof ternary because RockScript's RPN evaluator is eager:
                        // even the non-taken branch would fully evaluate, and e.g.
                        // arr.charAt(i) blows up as "undefined function: charAt".
                        String elemExpr = "__src" + suf + "[__it" + suf + "]";
                        // Declaration: plain var or destructuring desugared inline.
                        String elHolder = "__el" + suf;
                        String decl;
                        if (loopPattern != null) {
                            StringBuffer db = new StringBuffer();
                            db.append("var ").append(elHolder).append("=").append(elemExpr).append(";");
                            emitDestructDecl(db, loopPattern, elHolder);
                            decl = db.toString();
                        } else {
                            decl = "var " + loopVar + "=" + elemExpr + ";";
                        }
                        if (i < n && src.charAt(i) == '{') {
                            out.append('{').append(decl).append(' ');
                            i++;
                        } else {
                            // single-statement body — wrap in block
                            out.append('{').append(decl).append(' ');
                            // read a single statement until ';' or newline at depth 0
                            int ddepth = 0;
                            while (i < n) {
                                char cc = src.charAt(i);
                                if (cc == '{' || cc == '(' || cc == '[') ddepth++;
                                else if (cc == '}' || cc == ')' || cc == ']') {
                                    if (ddepth == 0) break;
                                    ddepth--;
                                }
                                out.append(cc);
                                i++;
                                if (ddepth == 0 && (cc == ';' || cc == '\n')) break;
                            }
                            out.append('}');
                        }
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    static boolean isWsOrOpen(String src, int i) {
        if (i >= src.length()) return false;
        char c = src.charAt(i);
        return isWs(c) || c == '(';
    }

    static String readIdent(String src, int i) {
        int n = src.length();
        if (i >= n || !isIdentStart(src.charAt(i))) return null;
        int st = i;
        while (i < n && isIdentPart(src.charAt(i))) i++;
        return src.substring(st, i);
    }

    // ==================================================================
    //  class ... extends ...
    // ==================================================================

    static String preprocessClasses(String src) {
        int idx = src.indexOf("class");
        if (idx < 0) return src;
        int n = src.length();
        StringBuffer out = new StringBuffer(n + 32);
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'' || c == '`' || (c == '/' && i + 1 < n && (src.charAt(i + 1) == '/' || src.charAt(i + 1) == '*'))) {
                int j = copyLiteral(src, i, out);
                if (j > i) { i = j; continue; }
            }
            if (c == 'c' && i + 4 < n
                    && src.regionMatches(false, i, "class", 0, 5)
                    && (i == 0 || !isIdentPart(src.charAt(i - 1)))
                    && i + 5 < n && isWs(src.charAt(i + 5))) {
                int k = i + 5;
                while (k < n && isWs(src.charAt(k))) k++;
                String name = readIdent(src, k);
                if (name != null) {
                    int afterName = k + name.length();
                    while (afterName < n && isWs(src.charAt(afterName))) afterName++;
                    String parent = null;
                    if (afterName + 7 <= n && src.regionMatches(false, afterName, "extends", 0, 7)
                            && isWs(src.charAt(afterName + 7))) {
                        int pStart = afterName + 8;
                        while (pStart < n && isWs(src.charAt(pStart))) pStart++;
                        int pEnd = pStart;
                        while (pEnd < n && (isIdentPart(src.charAt(pEnd)) || src.charAt(pEnd) == '.')) pEnd++;
                        parent = src.substring(pStart, pEnd);
                        afterName = pEnd;
                        while (afterName < n && isWs(src.charAt(afterName))) afterName++;
                    }
                    if (afterName < n && src.charAt(afterName) == '{') {
                        int bodyStart = afterName + 1;
                        int depth = 1, p = bodyStart;
                        while (p < n && depth > 0) {
                            char cc = src.charAt(p);
                            if (cc == '"' || cc == '\'' || cc == '`') {
                                StringBuffer tmp = new StringBuffer();
                                int j = copyLiteral(src, p, tmp);
                                if (j > p) { p = j; continue; }
                            }
                            if (cc == '{') depth++;
                            else if (cc == '}') { depth--; if (depth == 0) break; }
                            p++;
                        }
                        String body = src.substring(bodyStart, p);
                        // Rewrite class
                        appendClassDesugar(out, name, parent, body);
                        i = p + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** Desugar "class Name extends Parent { ... }" body into {@code function}+prototype. */
    static void appendClassDesugar(StringBuffer out, String name, String parent, String body) {
        // Parse body: find "constructor(", "methodName(" and "static methodName(".
        int i = 0, n = body.length();
        StringBuffer methods = new StringBuffer();
        StringBuffer staticMethods = new StringBuffer();
        String ctorParams = null;
        String ctorBody = null;
        while (i < n) {
            char c = body.charAt(i);
            if (isWs(c) || c == ';') { i++; continue; }
            if (c == '/' && i + 1 < n && (body.charAt(i + 1) == '/' || body.charAt(i + 1) == '*')) {
                StringBuffer dump = new StringBuffer();
                int j = copyLiteral(body, i, dump);
                if (j > i) { i = j; continue; }
            }
            boolean isStatic = false;
            if (body.regionMatches(false, i, "static", 0, 6) && i + 6 < n && isWs(body.charAt(i + 6))) {
                isStatic = true;
                i += 6;
                while (i < n && isWs(body.charAt(i))) i++;
            }
            String mname = readIdent(body, i);
            if (mname == null) { i++; continue; }
            int afterName = i + mname.length();
            while (afterName < n && isWs(body.charAt(afterName))) afterName++;
            if (afterName >= n || body.charAt(afterName) != '(') { i = afterName; continue; }
            // Params
            int pStart = afterName + 1, depth = 1, p = pStart;
            while (p < n && depth > 0) {
                char cc = body.charAt(p);
                if (cc == '(') depth++;
                else if (cc == ')') { depth--; if (depth == 0) break; }
                p++;
            }
            String params = body.substring(pStart, p);
            p++;
            while (p < n && isWs(body.charAt(p))) p++;
            if (p >= n || body.charAt(p) != '{') { i = p; continue; }
            int bStart = p + 1;
            depth = 1;
            p = bStart;
            while (p < n && depth > 0) {
                char cc = body.charAt(p);
                if (cc == '"' || cc == '\'' || cc == '`') {
                    StringBuffer tmp = new StringBuffer();
                    int j = copyLiteral(body, p, tmp);
                    if (j > p) { p = j; continue; }
                }
                if (cc == '{') depth++;
                else if (cc == '}') { depth--; if (depth == 0) break; }
                p++;
            }
            String mbody = body.substring(bStart, p);
            i = p + 1;
            // `super.method(...)` inside any non-constructor method must be
            // desugared to `Parent.prototype.method.call(this, ...)`. Static
            // methods never get a parent pointer, so we skip them. The
            // constructor is handled further below with its own super-call
            // rewriting (which also covers bare `super(...)`).
            if (parent != null && !isStatic && !"constructor".equals(mname)) {
                mbody = rewriteSuperCalls(mbody, parent);
            }
            if (!isStatic && "constructor".equals(mname)) {
                ctorParams = params;
                ctorBody = mbody;
            } else if (isStatic) {
                // Trailing '\n' — RockScript's ASI heuristic drops the ';' that
                // follows a '}', so without an explicit EOL between two
                // `A.x = function(){};` statements the parser would collapse
                // them into a chained `A.x = A.y = ...` expression and hang.
                staticMethods.append(name).append(".").append(mname)
                        .append("=function(").append(params).append("){")
                        .append(mbody).append("};\n");
            } else {
                methods.append(name).append(".prototype.").append(mname)
                        .append("=function(").append(params).append("){")
                        .append(mbody).append("};\n");
            }
        }
        if (ctorParams == null) { ctorParams = ""; ctorBody = ""; }
        if (parent != null) {
            // "constructor" calls super(...) as parent.call(this, ...)
            // We rewrite textual "super(" → "__parent.call(this,"
            ctorBody = rewriteSuperCalls(ctorBody, parent);
        }
        // Emit one statement per line. The '\n' separators are REQUIRED because
        // RockScript's ASI heuristic drops ';' after '}' (see
        // `RocksInterpreter.shouldIgnoreSemicolon`), so without an explicit EOL
        // between two `A.x = function(){};` statements the tokenizer would
        // swallow the separator and the parser would collapse everything into a
        // single chained `A.x = A.y = ...` expression and loop/hang.
        out.append("function ").append(name).append("(").append(ctorParams).append("){")
           .append(ctorBody).append("}\n");
        if (parent != null) {
            out.append(name).append(".prototype=Object.create(").append(parent).append(".prototype);\n")
               .append(name).append(".prototype.constructor=").append(name).append(";\n");
        }
        out.append(methods.toString());
        out.append(staticMethods.toString());
    }

    // ==================================================================
    //  Default parameters:  function f(a = 1, b)  →  function f(a, b){if(a===undefined)a=1; ...}
    // ==================================================================

    static String preprocessDefaultParams(String src) {
        int idx = src.indexOf("function");
        if (idx < 0) return src;
        int n = src.length();
        StringBuffer out = new StringBuffer(n + 16);
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'' || c == '`' || (c == '/' && i + 1 < n && (src.charAt(i + 1) == '/' || src.charAt(i + 1) == '*'))) {
                int j = copyLiteral(src, i, out);
                if (j > i) { i = j; continue; }
            }
            if (c == 'f' && i + 8 <= n && src.regionMatches(false, i, "function", 0, 8)
                    && (i == 0 || !isIdentPart(src.charAt(i - 1)))
                    && (i + 8 >= n || !isIdentPart(src.charAt(i + 8)))) {
                int lpr = src.indexOf('(', i + 8);
                if (lpr < 0) { out.append(c); i++; continue; }
                out.append(src.substring(i, lpr + 1));
                int pStart = lpr + 1, depth = 1, p = pStart;
                while (p < n && depth > 0) {
                    char cc = src.charAt(p);
                    if (cc == '(') depth++;
                    else if (cc == ')') { depth--; if (depth == 0) break; }
                    p++;
                }
                String params = src.substring(pStart, p);
                // Split params at top-level commas. Supports three param shapes:
                //   1. plain identifier, optional default  (handled inline)
                //   2. destructuring pattern ([...] / {...}), optional default
                //      Pattern is replaced by a synthetic name; the unpacking is
                //      emitted at the top of the body as a regular `var` decl so
                //      the later `preprocessDestructuring` pass can expand it.
                //   3. rest (`...name`) — left alone so `rewriteRestParams`
                //      can pick it up in the spread pass.
                StringBuffer cleanParams = new StringBuffer();
                StringBuffer defaults = new StringBuffer();
                int pd = 0, pi = 0, pn = params.length();
                while (pi < pn) {
                    int start = pi;
                    int brk = 0;
                    while (pi < pn) {
                        char cc = params.charAt(pi);
                        if (cc == '(' || cc == '[' || cc == '{') brk++;
                        else if (cc == ')' || cc == ']' || cc == '}') brk--;
                        else if (cc == ',' && brk == 0) break;
                        pi++;
                    }
                    String one = params.substring(start, pi).trim();
                    if (one.length() > 0) {
                        int eq = findTopLevelEq(one);
                        String head = eq > 0 ? one.substring(0, eq).trim() : one;
                        String def  = eq > 0 ? one.substring(eq + 1).trim() : null;
                        boolean destruct = head.length() > 0
                                && (head.charAt(0) == '[' || head.charAt(0) == '{');
                        if (destruct) {
                            String synth = "$dp" + (out.length() + pd);
                            if (cleanParams.length() > 0) cleanParams.append(',');
                            cleanParams.append(synth);
                            if (def != null) {
                                // Wrap the default in parens: RockScript's parser
                                // treats a bare `{}` / `{a:b}` after `=` as a block,
                                // silently dropping the following statement. Parens
                                // force expression context.
                                defaults.append("if(").append(synth).append("===undefined)")
                                        .append(synth).append("=(").append(def).append(");");
                            }
                            defaults.append("var ").append(head).append("=").append(synth).append(";");
                        } else if (def != null) {
                            if (cleanParams.length() > 0) cleanParams.append(',');
                            cleanParams.append(head);
                            defaults.append("if(").append(head).append("===undefined)")
                                    .append(head).append("=(").append(def).append(");");
                        } else {
                            if (cleanParams.length() > 0) cleanParams.append(',');
                            cleanParams.append(one);
                        }
                    }
                    if (pi < pn && params.charAt(pi) == ',') pi++;
                    pd++;
                }
                out.append(cleanParams.toString()).append(')');
                i = p + 1;
                while (i < n && isWs(src.charAt(i))) out.append(src.charAt(i++));
                if (i < n && src.charAt(i) == '{' && defaults.length() > 0) {
                    out.append('{').append(defaults.toString());
                    i++;
                }
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** Find the index of a top-level '=' in a single parameter (not inside nested brackets). */
    static int findTopLevelEq(String s) {
        int d = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[' || c == '{') d++;
            else if (c == ')' || c == ']' || c == '}') d--;
            else if (c == '=' && d == 0) {
                // not '==' or '=>'
                if (i + 1 < s.length() && (s.charAt(i + 1) == '=' || s.charAt(i + 1) == '>')) continue;
                if (i > 0 && (s.charAt(i - 1) == '=' || s.charAt(i - 1) == '!' || s.charAt(i - 1) == '<' || s.charAt(i - 1) == '>')) continue;
                return i;
            }
        }
        return -1;
    }

    // ==================================================================
    //  Destructuring:  var {a, b} = o  and  var [a, b] = arr
    // ==================================================================

    static String preprocessDestructuring(String src) {
        int n = src.length();
        if (src.indexOf("var ") < 0 && src.indexOf("let ") < 0 && src.indexOf("const ") < 0) return src;
        StringBuffer out = new StringBuffer(n + 32);
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'' || c == '`' || (c == '/' && i + 1 < n && (src.charAt(i + 1) == '/' || src.charAt(i + 1) == '*'))) {
                int j = copyLiteral(src, i, out);
                if (j > i) { i = j; continue; }
            }
            int kwLen = 0;
            if (matchKw(src, i, "var"))   kwLen = 3;
            else if (matchKw(src, i, "let"))   kwLen = 3;
            else if (matchKw(src, i, "const")) kwLen = 5;
            if (kwLen > 0) {
                int k = i + kwLen;
                while (k < n && isWs(src.charAt(k))) k++;
                if (k < n && (src.charAt(k) == '{' || src.charAt(k) == '[')) {
                    char open = src.charAt(k);
                    char close = open == '{' ? '}' : ']';
                    int depth = 1, p = k + 1;
                    while (p < n && depth > 0) {
                        char cc = src.charAt(p);
                        if (cc == open) depth++;
                        else if (cc == close) { depth--; if (depth == 0) break; }
                        p++;
                    }
                    String pattern = src.substring(k + 1, p);
                    int afterPattern = p + 1;
                    while (afterPattern < n && isWs(src.charAt(afterPattern))) afterPattern++;
                    if (afterPattern < n && src.charAt(afterPattern) == '=') {
                        int exprStart = afterPattern + 1;
                        int depth2 = 0, q = exprStart;
                        while (q < n) {
                            char cc = src.charAt(q);
                            if (cc == '"' || cc == '\'' || cc == '`') {
                                StringBuffer tmp = new StringBuffer();
                                int j = copyLiteral(src, q, tmp);
                                if (j > q) { q = j; continue; }
                            }
                            if (cc == '(' || cc == '[' || cc == '{') depth2++;
                            else if (cc == ')' || cc == ']' || cc == '}') {
                                if (depth2 == 0) break;
                                depth2--;
                            } else if (depth2 == 0 && (cc == ',' && isAtTopLevel(src, q) || cc == ';' || cc == '\n')) break;
                            q++;
                        }
                        String expr = src.substring(exprStart, q).trim();
                        String tmp = "__d" + out.length();
                        out.append("var ").append(tmp).append("=").append(expr).append(";");
                        expandPattern(out, pattern, open == '[', tmp);
                        i = q;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    static boolean matchKw(String src, int i, String kw) {
        int n = src.length(), kl = kw.length();
        if (i + kl > n) return false;
        if (!src.regionMatches(false, i, kw, 0, kl)) return false;
        if (i > 0 && isIdentPart(src.charAt(i - 1))) return false;
        if (i + kl < n && isIdentPart(src.charAt(i + kl))) return false;
        return true;
    }

    static boolean isAtTopLevel(String src, int q) { return true; }

    /**
     * Expand a destructuring pattern ({a,b} or [a,b], surrounding brackets
     * included) into {@code var NAME = holder.NAME;} / {@code var NAME = holder[i];}
     * declarations, appending them to {@code out}. Used by the for-of pass so it
     * can reuse the same desugaring logic as the regular declaration pass.
     */
    static void emitDestructDecl(StringBuffer out, String wrapped, String holder) {
        if (wrapped == null || wrapped.length() < 2) return;
        char open = wrapped.charAt(0);
        String inner = wrapped.substring(1, wrapped.length() - 1);
        expandPattern(out, inner, open == '[', holder);
    }

    /** For each comma-separated name in {@code pattern}, emit {@code var NAME = tmp.NAME;} or {@code var NAME = tmp[IDX];}. */
    static void expandPattern(StringBuffer out, String pattern, boolean isArray, String tmp) {
        int i = 0, n = pattern.length(), idx = 0;
        boolean first = true;
        while (i < n) {
            // Skip ws/commas. For array patterns we must NOT double-count the
            // comma as an extra index bump here, because we already bump `idx`
            // at the bottom of the loop after emitting the current item. We
            // only count EXTRA commas (holes like `[a,,b]`) beyond the single
            // separator expected between two elements.
            boolean sawComma = false;
            while (i < n && (isWs(pattern.charAt(i)) || pattern.charAt(i) == ',')) {
                if (pattern.charAt(i) == ',') {
                    if (sawComma && !first) idx++; // hole: extra comma after the separator
                    sawComma = true;
                }
                i++;
            }
            first = false;
            if (i >= n) break;
            // rest element: ... name
            if (i + 2 < n && pattern.charAt(i) == '.' && pattern.charAt(i + 1) == '.' && pattern.charAt(i + 2) == '.') {
                i += 3;
                String name = readIdent(pattern, i);
                if (name != null) {
                    i += name.length();
                    if (isArray) {
                        out.append("var ").append(name).append("=").append(tmp).append(".slice(").append(idx).append(");");
                    }
                }
                continue;
            }
            String name = readIdent(pattern, i);
            if (name == null) { i++; continue; }
            i += name.length();
            while (i < n && isWs(pattern.charAt(i))) i++;
            String renamed = name;
            // "a: b" form for objects
            if (!isArray && i < n && pattern.charAt(i) == ':') {
                i++;
                while (i < n && isWs(pattern.charAt(i))) i++;
                String bname = readIdent(pattern, i);
                if (bname != null) { renamed = bname; i += bname.length(); while (i < n && isWs(pattern.charAt(i))) i++; }
            }
            String defExpr = null;
            if (i < n && pattern.charAt(i) == '=') {
                i++;
                StringBuffer dbuf = new StringBuffer();
                int d = 0;
                while (i < n) {
                    char cc = pattern.charAt(i);
                    if (cc == '(' || cc == '[' || cc == '{') d++;
                    else if (cc == ')' || cc == ']' || cc == '}') { if (d == 0) break; d--; }
                    else if (cc == ',' && d == 0) break;
                    dbuf.append(cc);
                    i++;
                }
                defExpr = dbuf.toString().trim();
            }
            if (isArray) {
                out.append("var ").append(renamed).append("=").append(tmp).append("[").append(idx).append("]");
            } else {
                out.append("var ").append(renamed).append("=").append(tmp).append(".").append(name);
            }
            if (defExpr != null) {
                // Paren-wrap to avoid `= {}` / `= {x:1}` being parsed as a block.
                out.append("; if(").append(renamed).append("===undefined)").append(renamed)
                        .append("=(").append(defExpr).append(")");
            }
            out.append(";");
            idx++;
        }
    }

    // ==================================================================
    //  Object shorthand: {x, y}  →  {x: x, y: y}
    //  (heuristic: only rewrites inside braces that are object literal context;
    //   we detect context by the char before '{', accepting = , ( [ : return ? ;)
    // ==================================================================

    static String preprocessShorthandProps(String src) {
        int n = src.length();
        StringBuffer out = new StringBuffer(n + 8);
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'' || c == '`' || (c == '/' && i + 1 < n && (src.charAt(i + 1) == '/' || src.charAt(i + 1) == '*'))) {
                int j = copyLiteral(src, i, out);
                if (j > i) { i = j; continue; }
            }
            if (c == '{') {
                // Determine context via last non-ws char in out.
                int b = out.length() - 1;
                while (b >= 0 && isWs(out.charAt(b))) b--;
                boolean objCtx = false;
                if (b < 0) objCtx = false;
                else {
                    char pc = out.charAt(b);
                    if (pc == '=' || pc == '(' || pc == '[' || pc == ',' || pc == ':' || pc == '?' || pc == '!' || pc == '+' || pc == '-') objCtx = true;
                    else if (pc == 'n' && b >= 5 && sbSubstr(out, b - 5, b + 1).endsWith("return")) objCtx = true;
                }
                if (objCtx) {
                    // Rewrite object literal shorthand. Find matching '}'.
                    int depth = 1, p = i + 1;
                    boolean balanced = true;
                    while (p < n && depth > 0) {
                        char cc = src.charAt(p);
                        if (cc == '"' || cc == '\'' || cc == '`') {
                            StringBuffer tmp = new StringBuffer();
                            int j = copyLiteral(src, p, tmp);
                            if (j > p) { p = j; continue; }
                        }
                        if (cc == '{') depth++;
                        else if (cc == '}') { depth--; if (depth == 0) break; }
                        p++;
                    }
                    if (p >= n) balanced = false;
                    if (balanced) {
                        String body = src.substring(i + 1, p);
                        out.append('{');
                        out.append(rewriteShorthand(body));
                        out.append('}');
                        i = p + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    static String rewriteShorthand(String body) {
        StringBuffer out = new StringBuffer(body.length() + 8);
        int n = body.length(), i = 0;
        while (i < n) {
            // skip ws
            while (i < n && isWs(body.charAt(i))) { out.append(body.charAt(i)); i++; }
            if (i >= n) break;
            // one entry: KEY or KEY: VALUE or [expr]: VALUE or METHOD(){...}
            int start = i;
            int d = 0;
            boolean sawColon = false;
            boolean sawParen = false;
            while (i < n) {
                char cc = body.charAt(i);
                if (cc == '"' || cc == '\'' || cc == '`') {
                    StringBuffer tmp = new StringBuffer();
                    int j = copyLiteral(body, i, tmp);
                    if (j > i) { i = j; continue; }
                }
                if (cc == '(' || cc == '[' || cc == '{') { d++; if (cc == '(' && d == 1) sawParen = true; }
                else if (cc == ')' || cc == ']' || cc == '}') d--;
                else if (d == 0) {
                    if (cc == ':') sawColon = true;
                    if (cc == ',') break;
                }
                i++;
            }
            String entry = body.substring(start, i).trim();
            if (entry.length() > 0) {
                if (!sawColon && !sawParen) {
                    // shorthand: "x" → "x: x"
                    String name = entry;
                    if (isIdent(name)) {
                        out.append(name).append(": ").append(name);
                    } else {
                        out.append(entry);
                    }
                } else {
                    out.append(entry);
                }
            }
            if (i < n) { out.append(body.charAt(i)); i++; }
        }
        return out.toString();
    }

    static boolean isIdent(String s) {
        int n = s.length();
        if (n == 0) return false;
        if (!isIdentStart(s.charAt(0))) return false;
        for (int i = 1; i < n; i++) if (!isIdentPart(s.charAt(i))) return false;
        return true;
    }

    static String rewriteSuperCalls(String body, String parent) {
        int n = body.length();
        int idx = body.indexOf("super");
        if (idx < 0) return body;
        StringBuffer out = new StringBuffer(n + 16);
        int i = 0;
        while (i < n) {
            char c = body.charAt(i);
            if (c == '"' || c == '\'' || c == '`' || (c == '/' && i + 1 < n && (body.charAt(i + 1) == '/' || body.charAt(i + 1) == '*'))) {
                int j = copyLiteral(body, i, out);
                if (j > i) { i = j; continue; }
            }
            if (c == 's' && i + 4 < n && body.regionMatches(false, i, "super", 0, 5)
                    && (i == 0 || !isIdentPart(body.charAt(i - 1)))
                    && (i + 5 >= n || !isIdentPart(body.charAt(i + 5)))) {
                int k = i + 5;
                while (k < n && isWs(body.charAt(k))) k++;
                if (k < n && body.charAt(k) == '(') {
                    out.append(parent).append(".call(this");
                    // check if there are any args
                    int look = k + 1;
                    while (look < n && isWs(body.charAt(look))) look++;
                    if (look < n && body.charAt(look) != ')') out.append(',');
                    i = k + 1;
                    continue;
                }
                if (k < n && body.charAt(k) == '.') {
                    // super.method(...) → parent.prototype.method.call(this, ...)
                    int dot = k + 1;
                    String m = readIdent(body, dot);
                    if (m != null) {
                        int afterM = dot + m.length();
                        while (afterM < n && isWs(body.charAt(afterM))) afterM++;
                        if (afterM < n && body.charAt(afterM) == '(') {
                            out.append(parent).append(".prototype.").append(m).append(".call(this");
                            int look = afterM + 1;
                            while (look < n && isWs(body.charAt(look))) look++;
                            if (look < n && body.charAt(look) != ')') out.append(',');
                            i = afterM + 1;
                            continue;
                        }
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
