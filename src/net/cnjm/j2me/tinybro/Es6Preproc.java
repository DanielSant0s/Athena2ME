package net.cnjm.j2me.tinybro;

import java.util.Hashtable;
import java.util.Vector;

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
 *   <li>{@code async function} with a <strong>linear</strong> body (semicolon-separated
 *       statements, no {@code if}/{@code for}/{@code while}/{@code switch}/{@code try})
 *       → desugaring with {@code __awaitStep} and {@code Promise.resolve} (see README).</li>
 * </ul>
 *
 * <p>This preprocessor runs once per script-source, so it is O(n) and does not
 * touch the hot eval loop.</p>
 */
final class Es6Preproc {

    private Es6Preproc() {}

    /**
     * When false, the conservative literal-constant pass ({@link #preprocessConstantFold})
     * is skipped. Set to true for smaller token streams and less RPN at runtime.
     */
    public static boolean ENABLE_LITERAL_FOLD = true;

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
        src = preprocessAsyncAwait(src);
        if (ENABLE_LITERAL_FOLD) {
            src = preprocessConstantFold(src);
        }
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
        // Fast-skip: avoid matching "class" as substring of identifiers (e.g. "subclass")
        if (src.indexOf("class ") < 0 && src.indexOf("class\n") < 0
                && src.indexOf("class\t") < 0 && src.indexOf("class\r") < 0) {
            return src;
        }
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

    // ==================================================================
    //  async / await (linear body only)
    // ==================================================================

    static String preprocessAsyncAwait(String src) {
        if (src == null || src.indexOf("async") < 0) {
            return src;
        }
        int n = src.length();
        StringBuffer out = new StringBuffer(n + 64);
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'' || c == '`' || (c == '/' && i + 1 < n && (src.charAt(i + 1) == '/' || src.charAt(i + 1) == '*'))) {
                int j = copyLiteral(src, i, out);
                if (j > i) {
                    i = j;
                    continue;
                }
            }
            if (matchKw(src, i, "async")) {
                int j = i + 5;
                while (j < n && isWs(src.charAt(j))) {
                    j++;
                }
                if (j + 8 <= n && src.regionMatches(false, j, "function", 0, 8)
                        && (j + 8 >= n || !isIdentPart(src.charAt(j + 8)))) {
                    int afterFn = j + 8;
                    int[] nameEnd = new int[1];
                    int argOpen = skipFunctionNameAndFindParen(src, afterFn, nameEnd);
                    if (argOpen > 0) {
                        int argClose = findMatchingParen(src, argOpen);
                        if (argClose > argOpen) {
                            int bodyOpen = argClose + 1;
                            while (bodyOpen < n && isWs(src.charAt(bodyOpen))) {
                                bodyOpen++;
                            }
                            if (bodyOpen < n && src.charAt(bodyOpen) == '{') {
                                int bodyClose = findMatchingBrace(src, bodyOpen);
                                if (bodyClose > bodyOpen) {
                                    String inner = src.substring(bodyOpen + 1, bodyClose);
                                    String rewritten = rewriteLinearAsyncBody(inner);
                                    if (rewritten != null) {
                                        out.append(src.substring(j, bodyOpen + 1));
                                        out.append(' ');
                                        out.append(rewritten);
                                        out.append(' ');
                                        out.append('}');
                                        i = bodyClose + 1;
                                        continue;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    static int skipFunctionNameAndFindParen(String src, int start, int[] nameEndOut) {
        int n = src.length();
        int p = start;
        while (p < n && isWs(src.charAt(p))) {
            p++;
        }
        if (p < n && isIdentStart(src.charAt(p))) {
            String id = readIdent(src, p);
            if (id != null) {
                p += id.length();
            }
        }
        while (p < n && isWs(src.charAt(p))) {
            p++;
        }
        nameEndOut[0] = p;
        if (p < n && src.charAt(p) == '(') {
            return p;
        }
        return -1;
    }

    static int findMatchingParen(String src, int openIdx) {
        int n = src.length();
        int depth = 0;
        for (int i = openIdx; i < n; i++) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'' || c == '`') {
                StringBuffer tmp = new StringBuffer();
                i = copyLiteral(src, i, tmp) - 1;
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    i++;
                }
                i++;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    static int findMatchingBrace(String src, int openIdx) {
        int n = src.length();
        int depth = 0;
        for (int i = openIdx; i < n; i++) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'' || c == '`') {
                StringBuffer tmp = new StringBuffer();
                i = copyLiteral(src, i, tmp) - 1;
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    i++;
                }
                i++;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    static Vector splitLinearStatements(String body) {
        Vector v = new Vector();
        int n = body.length();
        int start = 0;
        int dParen = 0, dBrack = 0, dBrace = 0;
        int i = 0;
        while (i < n) {
            char c = body.charAt(i);
            if (c == '"' || c == '\'' || c == '`') {
                StringBuffer tmp = new StringBuffer();
                i = copyLiteral(body, i, tmp);
                continue;
            }
            if (c == '/' && i + 1 < n && body.charAt(i + 1) == '/') {
                while (i < n && body.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && body.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(body.charAt(i) == '*' && body.charAt(i + 1) == '/')) {
                    i++;
                }
                i += 2;
                continue;
            }
            if (c == '(') {
                dParen++;
            } else if (c == ')') {
                dParen--;
            } else if (c == '[') {
                dBrack++;
            } else if (c == ']') {
                dBrack--;
            } else if (c == '{') {
                dBrace++;
            } else if (c == '}') {
                dBrace--;
            } else if (c == ';' && dParen == 0 && dBrack == 0 && dBrace == 0) {
                String stmt = body.substring(start, i).trim();
                if (stmt.length() > 0) {
                    v.addElement(stmt);
                }
                start = i + 1;
            }
            i++;
        }
        String tail = body.substring(start).trim();
        if (tail.length() > 0) {
            v.addElement(tail);
        }
        return v;
    }

    static boolean isUnsupportedAsyncStmt(String stmt) {
        String t = stmt.trim();
        if (t.length() == 0) {
            return false;
        }
        int k = 0;
        while (k < t.length() && isWs(t.charAt(k))) {
            k++;
        }
        return matchKw(t, k, "if") || matchKw(t, k, "for") || matchKw(t, k, "while")
                || matchKw(t, k, "switch") || matchKw(t, k, "try") || matchKw(t, k, "do");
    }

    static boolean stmtContainsAwait(String stmt) {
        int n = stmt.length();
        int i = 0;
        while (i < n) {
            char c = stmt.charAt(i);
            if (c == '"' || c == '\'' || c == '`') {
                StringBuffer tmp = new StringBuffer();
                i = copyLiteral(stmt, i, tmp);
                continue;
            }
            if (c == '/' && i + 1 < n && stmt.charAt(i + 1) == '/') {
                while (i < n && stmt.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (matchKw(stmt, i, "await")) {
                return true;
            }
            i++;
        }
        return false;
    }

    static String rewriteLinearAsyncBody(String inner) {
        Vector stmts = splitLinearStatements(inner);
        for (int s = 0; s < stmts.size(); s++) {
            String st = (String) stmts.elementAt(s);
            if (isUnsupportedAsyncStmt(st)) {
                return null;
            }
            if (stmtContainsAwait(st) && st.indexOf('{') >= 0) {
                return null;
            }
        }
        return emitAsyncChain(stmts, 0);
    }

    static String emitAsyncChain(Vector stmts, int idx) {
        if (idx >= stmts.size()) {
            return "Promise.resolve(undefined)";
        }
        String s = ((String) stmts.elementAt(idx)).trim();
        if (s.length() == 0) {
            return emitAsyncChain(stmts, idx + 1);
        }
        String tret = tryEmitAsyncReturn(s);
        if (tret != null) {
            return tret;
        }
        String vawait = tryEmitVarAwaitChain(s, stmts, idx);
        if (vawait != null) {
            return vawait;
        }
        String t = s.trim();
        if (matchKw(t, 0, "await")) {
            int p = 5;
            while (p < t.length() && isWs(t.charAt(p))) {
                p++;
            }
            String expr = t.substring(p).trim();
            if (expr.endsWith(";")) {
                expr = expr.substring(0, expr.length() - 1).trim();
            }
            return "__awaitStep((" + expr + "),function(__aw){return " + emitAsyncChain(stmts, idx + 1) + ";})";
        }
        return "Promise.resolve(undefined).then(function(){" + s + ";return " + emitAsyncChain(stmts, idx + 1) + ";})";
    }

    static String tryEmitAsyncReturn(String t) {
        String s = t.trim();
        if (!matchKw(s, 0, "return")) {
            return null;
        }
        int p = 6;
        while (p < s.length() && isWs(s.charAt(p))) {
            p++;
        }
        if (p >= s.length()) {
            return "Promise.resolve(undefined)";
        }
        String rest = s.substring(p).trim();
        if (matchKw(rest, 0, "await")) {
            int q = 5;
            while (q < rest.length() && isWs(rest.charAt(q))) {
                q++;
            }
            String expr = rest.substring(q).trim();
            if (expr.endsWith(";")) {
                expr = expr.substring(0, expr.length() - 1).trim();
            }
            return "__awaitStep((" + expr + "),function(__r){return Promise.resolve(__r);})";
        }
        if (rest.endsWith(";")) {
            rest = rest.substring(0, rest.length() - 1).trim();
        }
        return "Promise.resolve((" + rest + "))";
    }

    static String tryEmitVarAwaitChain(String s, Vector stmts, int idx) {
        String t = s.trim();
        int declLen = 0;
        if (matchKw(t, 0, "var")) {
            declLen = 3;
        } else if (matchKw(t, 0, "let")) {
            declLen = 3;
        } else if (matchKw(t, 0, "const")) {
            declLen = 5;
        } else {
            return null;
        }
        int p = declLen;
        while (p < t.length() && isWs(t.charAt(p))) {
            p++;
        }
        String name = readIdent(t, p);
        if (name == null) {
            return null;
        }
        p += name.length();
        while (p < t.length() && isWs(t.charAt(p))) {
            p++;
        }
        if (p >= t.length() || t.charAt(p) != '=') {
            return null;
        }
        p++;
        while (p < t.length() && isWs(t.charAt(p))) {
            p++;
        }
        if (!matchKw(t, p, "await")) {
            return null;
        }
        p += 5;
        while (p < t.length() && isWs(t.charAt(p))) {
            p++;
        }
        String expr = t.substring(p).trim();
        if (expr.endsWith(";")) {
            expr = expr.substring(0, expr.length() - 1).trim();
        }
        return "__awaitStep((" + expr + "),function(" + name + "){return " + emitAsyncChain(stmts, idx + 1) + ";})";
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

    // ------------------------------------------------------------------
    //  Constant folding (static analysis on source text, before tokenize)
    // ------------------------------------------------------------------

    private static final Object SCOPE_SHADOW = new Object();

    private static void taint(Hashtable tainted, String name) {
        if (name != null && name.length() > 0) {
            tainted.put(name, SCOPE_SHADOW);
        }
    }

    private static int skipWsIn(String s, int i, int n) {
        while (i < n && isWs(s.charAt(i))) i++;
        return i;
    }

    private static int readHexInt(String s, int i, int n, int[] outVal) {
        int p = i;
        long v = 0;
        while (i < n) {
            char c = s.charAt(i);
            int d;
            if (c >= '0' && c <= '9') d = c - '0';
            else if (c >= 'a' && c <= 'f') d = 10 + (c - 'a');
            else if (c >= 'A' && c <= 'F') d = 10 + (c - 'A');
            else break;
            v = (v * 16L) + d;
            i++;
        }
        if (i == p) return -1;
        outVal[0] = (int) (v & 0xffffffffL);
        return i;
    }

    private static int readNumberLen(String s, int i, int n, double[] outVal) {
        if (i >= n) return -1;
        int p = i;
        char c0 = s.charAt(i);
        if (c0 == '0' && i + 1 < n) {
            char c1 = s.charAt(i + 1);
            if (c1 == 'x' || c1 == 'X') {
                int[] iv = new int[1];
                int j = readHexInt(s, i + 2, n, iv);
                if (j < 0) return -1;
                outVal[0] = (double) (iv[0] & 0xffffffffL);
                return j - p;
            }
        }
        if (c0 == '.') {
            if (i + 1 >= n || s.charAt(i + 1) < '0' || s.charAt(i + 1) > '9') {
                return -1;
            }
        } else if (c0 < '0' || c0 > '9') {
            if (c0 != '.') return -1;
        }
        int j = i;
        if (s.charAt(j) == '.') {
            j++;
            while (j < n && s.charAt(j) >= '0' && s.charAt(j) <= '9') j++;
        } else {
            while (j < n && s.charAt(j) >= '0' && s.charAt(j) <= '9') j++;
            if (j < n && s.charAt(j) == '.') {
                j++;
                while (j < n && s.charAt(j) >= '0' && s.charAt(j) <= '9') j++;
            }
        }
        if (j < n) {
            char e = s.charAt(j);
            if (e == 'e' || e == 'E') {
                j++;
                if (j < n && (s.charAt(j) == '+' || s.charAt(j) == '-')) j++;
                while (j < n && s.charAt(j) >= '0' && s.charAt(j) <= '9') j++;
            }
        }
        try {
            outVal[0] = Double.parseDouble(s.substring(p, j));
        } catch (Throwable t) {
            return -1;
        }
        return j - p;
    }

    private static int toInt32(double n) {
        if (n != n) return 0;
        if (n == 0.0) return 0;
        if (n == Double.POSITIVE_INFINITY) return 0;
        if (n == Double.NEGATIVE_INFINITY) return 0;
        double a = n % 4294967296.0;
        if (a < 0) a += 4294967296.0;
        if (a >= 2147483648.0) a -= 4294967296.0;
        return (int) a;
    }

    private static double toUint32(double n) {
        return (double) (toInt32(n) & 0xffffffffL);
    }

    private static double uintRsh(double l, int r) {
        long x = toInt32(l) & 0xffffffffL;
        r &= 31;
        return (double) (x >>> r);
    }

    private static int jsRhsShift(double n) {
        int t = toInt32(n) & 31;
        return t;
    }

    private static void scanAssignmentLhsTaint(String s, int assignPos, Hashtable tainted) {
        if (assignPos < 0) return;
        int j = assignPos - 1;
        while (j >= 0 && isWs(s.charAt(j))) j--;
        if (j < 0) return;
        if (!isIdentPart(s.charAt(j))) return;
        int e = j;
        while (j >= 0 && isIdentPart(s.charAt(j))) j--;
        int st = j + 1;
        char before = (j < 0) ? 0 : s.charAt(j);
        if (before == '.') return;
        String id = s.substring(st, e + 1);
        taint(tainted, id);
    }

    private static int scanParensTaintParams(String s, int openParen, Hashtable tainted) {
        int n = s.length();
        int p = openParen;
        if (p >= n || s.charAt(p) != '(') return openParen;
        int depth = 0;
        int p0 = p;
        while (p < n) {
            char c = s.charAt(p);
            if (c == '"' || c == '\'' || c == '`' || (c == '/' && p + 1 < n && (s.charAt(p + 1) == '/' || s.charAt(p + 1) == '*'))) {
                StringBuffer t = new StringBuffer();
                int j = copyLiteral(s, p, t);
                if (j > p) { p = j; continue; }
            }
            if (c == '(') {
                if (depth == 0) p0 = p;
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    String inner = s.substring(openParen + 1, p);
                    taintParamList(inner, tainted);
                    return p;
                }
            }
            p++;
        }
        return p0;
    }

    private static void taintParamList(String inner, Hashtable tainted) {
        if (inner == null) return;
        int n = inner.length();
        int i = 0, brk = 0;
        while (i < n) {
            char c = inner.charAt(i);
            if (c == '(' || c == '[' || c == '{') brk++;
            else if (c == ')' || c == ']' || c == '}') brk--;
            if (c == ',' && brk == 0) { i++; continue; }
            if (c == '.' && i + 1 < n && inner.charAt(i + 1) == '.' && i + 2 < n && inner.charAt(i + 2) == '.') {
                i += 3;
                i = skipWsIn(inner, i, n);
                String r = readIdent(inner, i);
                if (r != null) taint(tainted, r);
                while (i < n && (isIdentPart(inner.charAt(i)) || isWs(inner.charAt(i)) || inner.charAt(i) == ',')) {
                    if (inner.charAt(i) == ',') break;
                    i++;
                }
                continue;
            }
            if (brk == 0) {
                String id = readIdent(inner, i);
                if (id != null) {
                    taint(tainted, id);
                }
            }
            i++;
        }
    }

    private static int scanTaintVarLetBody(String s, int start, Hashtable tainted) {
        int n = s.length();
        int p = start;
        int dparen = 0, dbr = 0, dbrace = 0;
        int lastEq = -1;
        while (p < n) {
            char c = s.charAt(p);
            if (c == '"' || c == '\'' || c == '`' || (c == '/' && p + 1 < n && (s.charAt(p + 1) == '/' || s.charAt(p + 1) == '*'))) {
                StringBuffer t = new StringBuffer();
                int j = copyLiteral(s, p, t);
                if (j > p) { p = j; continue; }
            }
            if (c == '(') dparen++;
            else if (c == ')') dparen--;
            else if (c == '{') dbrace++;
            else if (c == '}') dbrace--;
            if (c == '=' && dparen == 0 && dbr == 0) {
                if (p + 1 < n) {
                    char c2 = s.charAt(p + 1);
                    if (c2 != '=' && c2 != '>') lastEq = p;
                } else {
                    lastEq = p;
                }
            }
            if (c == ',' && dparen == 0 && dbr == 0 && dbrace == 0) {
                p++;
                if (dbrace < 0) return p;
                int segStart = p;
                while (p < n) {
                    char cc = s.charAt(p);
                    if (cc == '"' || cc == '\'' || cc == '`' || (cc == '/' && p + 1 < n)) {
                        StringBuffer t = new StringBuffer();
                        p = copyLiteral(s, p, t);
                        continue;
                    }
                    if (cc == '{' || cc == '(' || cc == '[') dparen = dbr = 0; // reset inner
                    if (cc == ';' && dparen == 0) break;
                    if (cc == ')' && dparen == 0) break;
                    p++;
                }
                continue;
            }
            if (c == ';' && dparen == 0 && dbr == 0 && dbrace == 0) {
                if (lastEq > 0) {
                    int i = lastEq - 1;
                    while (i >= 0 && isWs(s.charAt(i))) i--;
                    if (i >= 0 && isIdentPart(s.charAt(i))) {
                        int e = i, st;
                        while (e >= 0 && isIdentPart(s.charAt(e))) e--;
                        st = e + 1;
                        if (st <= i) {
                            char b = (e < 0) ? 0 : s.charAt(e);
                            if (b != '.') taint(tainted, s.substring(st, i + 1));
                        }
                    }
                }
                return p;
            }
            p++;
        }
        return p;
    }

    static Hashtable scanMutatedNames(String src) {
        Hashtable tainted = new Hashtable();
        if (src == null || src.length() == 0) return tainted;
        int n = src.length();
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'' || c == '`' || (c == '/' && i + 1 < n && (src.charAt(i + 1) == '/' || src.charAt(i + 1) == '*'))) {
                StringBuffer t = new StringBuffer();
                int j = copyLiteral(src, i, t);
                if (j > i) { i = j; continue; }
            }
            if (c == 'f' && i + 8 <= n && matchKw(src, i, "function") && (i + 8 >= n || !isIdentPart(src.charAt(i + 8)))) {
                int j = i + 8;
                j = skipWsIn(src, j, n);
                if (j < n) {
                    char cc = src.charAt(j);
                    if (isIdentStart(cc) && (j + 1 >= n || src.charAt(j + 1) != '(')) {
                        j = j + 1;
                        while (j < n && isIdentPart(src.charAt(j))) j++;
                    }
                }
                j = skipWsIn(src, j, n);
                if (j < n && src.charAt(j) == '(') {
                    j = scanParensTaintParams(src, j, tainted);
                }
                i = j + 1;
                continue;
            }
            if (c == 'c' && i + 4 < n && matchKw(src, i, "catch") && (i + 4 >= n || !isIdentPart(src.charAt(i + 4)))) {
                int j = i + 5;
                j = skipWsIn(src, j, n);
                if (j < n && src.charAt(j) == '(') {
                    j++;
                    j = skipWsIn(src, j, n);
                    String e = readIdent(src, j);
                    if (e != null) taint(tainted, e);
                }
                i = j;
                continue;
            }
            if (i + 2 < n) {
                if (src.charAt(i) == '+' && src.charAt(i + 1) == '+') {
                    taintPpOrMm(src, i, i + 2, tainted, true);
                    i += 2;
                    continue;
                }
                if (src.charAt(i) == '-' && src.charAt(i + 1) == '-') {
                    taintPpOrMm(src, i, i + 2, tainted, false);
                    i += 2;
                    continue;
                }
            }
            if (i + 1 < n) {
                char c0 = c;
                char c1 = src.charAt(i + 1);
                if (c0 == '<' && c1 == '<' && i + 2 < n && src.charAt(i + 2) == '=') {
                    scanAssignmentLhsTaint(src, i, tainted);
                    i += 3;
                    continue;
                }
                if (c0 == '>' && c1 == '>' && i + 2 < n) {
                    if (i + 3 < n && src.charAt(i + 2) == '>' && src.charAt(i + 3) == '=') {
                        scanAssignmentLhsTaint(src, i, tainted);
                        i += 4;
                        continue;
                    }
                    if (i + 2 < n && src.charAt(i + 2) == '=') {
                        scanAssignmentLhsTaint(src, i, tainted);
                        i += 3;
                        continue;
                    }
                }
                if ((c0 == '&' && c1 == '&') || (c0 == '|' && c1 == '|') || c1 == '>' && (c0 == '>' && i + 2 < n)) {
                } else if (c0 == '!' || c0 == '?' || c0 == '>' && c1 == '=') {
                } else if (c1 == '=') {
                    if (c0 == '=') {
                    } else if (c0 == '!' && c1 == '=') {
                    } else {
                        if (c0 != '=') scanAssignmentLhsTaint(src, i, tainted);
                        i += 2;
                        continue;
                    }
                } else {
                }
            }
            if (c == '=' && i + 1 < n) {
                char c1 = src.charAt(i + 1);
                if (c1 != '=' && c1 != '>') {
                    if (i == 0 || src.charAt(i - 1) != '=') {
                        scanAssignmentLhsTaint(src, i, tainted);
                    }
                }
            }
            if (c == 'v' && i + 2 < n && matchKw(src, i, "var") && (i + 2 >= n || !isIdentPart(src.charAt(i + 2)))) {
                int j = i + 3;
                j = skipWsIn(src, j, n);
                if (j < n && (src.charAt(j) == '{' || src.charAt(j) == '[')) { i = j; continue; }
                i = scanTaintVarLetBody(src, j, tainted);
                continue;
            }
            if (c == 'l' && i + 2 < n && matchKw(src, i, "let") && (i + 2 >= n || !isIdentPart(src.charAt(i + 2)))) {
                int j = i + 3;
                j = skipWsIn(src, j, n);
                if (j < n && (src.charAt(j) == '{' || src.charAt(j) == '[')) { i = j; continue; }
                i = scanTaintVarLetBody(src, j, tainted);
                continue;
            }
            i++;
        }
        return tainted;
    }

    private static void taintPpOrMm(String s, int opPos, int afterOp, Hashtable tainted, boolean isPp) {
        int n = s.length();
        if (isPp) {
            int p = afterOp;
            p = skipWsIn(s, p, n);
            String id = readIdent(s, p);
            if (id != null) taint(tainted, id);
        } else {
            int p = opPos - 1;
            while (p >= 0 && isWs(s.charAt(p))) p--;
            if (p < 0) return;
            if (!isIdentPart(s.charAt(p))) return;
            int e = p, st;
            while (p >= 0 && isIdentPart(s.charAt(p))) p--;
            st = p + 1;
            if (e >= st) {
                char b = p < 0 ? 0 : s.charAt(p);
                if (b != '.') taint(tainted, s.substring(st, e + 1));
            }
        }
    }

    static final class FoldVal {
        static final int T_NUM = 1;
        static final int T_STR = 2;
        static final int T_BOOL = 3;
        static final int T_NULL = 4;
        static final int T_UNDEF = 5;
        int type;
        double num;
        String str;
        boolean b;

        static FoldVal numV(double d) {
            FoldVal v = new FoldVal();
            v.type = T_NUM;
            v.num = d;
            return v;
        }

        static FoldVal strV(String s) {
            FoldVal v = new FoldVal();
            v.type = T_STR;
            v.str = s;
            return v;
        }

        static FoldVal boolV(boolean x) {
            FoldVal v = new FoldVal();
            v.type = T_BOOL;
            v.b = x;
            return v;
        }

        String emit() {
            switch (type) {
            case T_NUM:
                if (num != num) return "(0/0)";
                if (num == Double.POSITIVE_INFINITY) return "(1/0)";
                if (num == Double.NEGATIVE_INFINITY) return "(-1/0)";
                if (num == 0) {
                    if (1.0 / num < 0) return "(-0)";
                }
                if (num == (double) (long) num && num >= -2147483648.0 && num <= 2147483647.0) {
                    return String.valueOf((long) num);
                }
                return String.valueOf(num);
            case T_STR:
                return stringLiteral(str);
            case T_BOOL:
                return b ? "true" : "false";
            case T_NULL:
                return "null";
            case T_UNDEF:
            default:
                return "undefined";
            }
        }

        static String stringLiteral(String s) {
            if (s == null) s = "";
            StringBuffer b = new StringBuffer(s.length() + 2);
            b.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '"') b.append("\\\"");
                else if (c == '\\') b.append("\\\\");
                else if (c == '\n') b.append("\\n");
                else if (c == '\r') b.append("\\r");
                else if (c == '\t') b.append("\\t");
                else b.append(c);
            }
            b.append('"');
            return b.toString();
        }
    }

    private static void scopePutName(Vector scopes, String name) {
        Hashtable t = (Hashtable) scopes.lastElement();
        t.put(name, SCOPE_SHADOW);
    }

    private static void scopePutVal(Vector scopes, String name, FoldVal v) {
        Hashtable t = (Hashtable) scopes.lastElement();
        t.put(name, v);
    }

    private static FoldVal scopeLookupName(Vector scopes, String name, Hashtable tainted) {
        if (tainted != null && tainted.get(name) != null) {
            return null;
        }
        for (int s = scopes.size() - 1; s >= 0; s--) {
            Hashtable h = (Hashtable) scopes.elementAt(s);
            if (!h.containsKey(name)) {
                continue;
            }
            Object o = h.get(name);
            if (o == SCOPE_SHADOW) {
                return null;
            }
            if (o instanceof FoldVal) {
                return (FoldVal) o;
            }
        }
        return null;
    }

    private static int readStringLiteral(String s, int i, int n, StringBuffer out) {
        if (i >= n) return -1;
        char q = s.charAt(i);
        if (q != '"' && q != '\'') {
            return -1;
        }
        int p = i + 1;
        StringBuffer b = new StringBuffer();
        while (p < n) {
            char c = s.charAt(p);
            if (c == q) {
                if (out != null) {
                    out.append(b.toString());
                }
                return p + 1;
            }
            if (c == '\\' && p + 1 < n) {
                char d = s.charAt(p + 1);
                if (d == 'n') b.append('\n');
                else if (d == 'r') b.append('\r');
                else if (d == 't') b.append('\t');
                else if (d == 'u' && p + 5 < n) {
                } else b.append(d);
                p += 2;
            } else {
                b.append(c);
                p++;
            }
        }
        return -1;
    }

    private static int findRhsEndInDecl(String s, int from, int n) {
        int p = from;
        int dparen = 0, dbr = 0, dbrace = 0;
        while (p < n) {
            char c = s.charAt(p);
            if (c == '"' || c == '\'' || c == '`' || (c == '/' && p + 1 < n && (s.charAt(p + 1) == '/' || s.charAt(p + 1) == '*'))) {
                StringBuffer t = new StringBuffer();
                int j = copyLiteral(s, p, t);
                if (j > p) { p = j; continue; }
            }
            if (c == '(') dparen++;
            else if (c == ')') dparen--;
            else if (c == '[') dbr++;
            else if (c == ']') dbr--;
            else if (c == '{') dbrace++;
            else if (c == '}') dbrace--;
            if (c == ',' && dparen == 0 && dbr == 0 && dbrace == 0) {
                return p;
            }
            if (c == ';' && dparen == 0 && dbr == 0 && dbrace == 0) {
                return p;
            }
            if (c == '\n' && dparen == 0 && dbr == 0 && dbrace == 0) {
                return p;
            }
            p++;
        }
        return p;
    }

    private static int tryParseFoldExprAt(String s, int start, int n, Hashtable tainted, Vector scopes, FoldVal[] outVal) {
        CFParser p = new CFParser();
        p.init(s, start, n, tainted, scopes);
        int save = start;
        FoldVal v = p.parseTernary();
        p.skipW();
        if (p.fail || v == null || p.pos == save) {
            return -1;
        }
        if (!p.isValidExprEnd()) {
            return -1;
        }
        if (outVal != null) {
            outVal[0] = v;
        }
        return p.pos;
    }

    static final class CFParser {
        String src;
        int n;
        int pos;
        Hashtable tainted;
        Vector scopes;
        boolean fail;

        void init(String s, int p0, int limit, Hashtable t, Vector sc) {
            src = s;
            n = limit;
            pos = p0;
            tainted = t;
            scopes = sc;
            fail = false;
        }

        int skipW() {
            while (pos < n && isWs(src.charAt(pos))) {
                pos++;
            }
            return pos;
        }

        boolean isValidExprEnd() {
            if (pos >= n) {
                return true;
            }
            int p = pos;
            while (p < n && isWs(src.charAt(p))) {
                p++;
            }
            if (p >= n) {
                return true;
            }
            char c = src.charAt(p);
            return c == ')' || c == ']' || c == '}' || c == ',' || c == ';' || c == '\n' || c == ':';
        }

        FoldVal parseTernary() {
            FoldVal c = parseLor();
            if (fail) {
                return null;
            }
            skipW();
            if (pos < n && src.charAt(pos) == '?') {
                pos++;
                FoldVal t = parseLor();
                skipW();
                if (pos >= n || src.charAt(pos) != ':') {
                    fail = true;
                    return null;
                }
                pos++;
                FoldVal f = parseTernary();
                if (c == null || t == null || f == null) {
                    return null;
                }
                return boolish(c) ? t : f;
            }
            return c;
        }

        private boolean boolish(FoldVal v) {
            return cfpToBoolean(v);
        }

        FoldVal parseLor() {
            FoldVal l = parseLand();
            if (fail) {
                return null;
            }
            for (;;) {
                skipW();
                if (pos + 1 < n && src.charAt(pos) == '|' && src.charAt(pos + 1) == '|') {
                    pos += 2;
                    FoldVal r = parseLand();
                    if (l == null || r == null) {
                        return null;
                    }
                    l = cfpToBoolean(l) ? l : r;
                } else {
                    return l;
                }
            }
        }

        FoldVal parseLand() {
            FoldVal l = parseBor();
            if (fail) {
                return null;
            }
            for (;;) {
                skipW();
                if (pos + 1 < n && src.charAt(pos) == '&' && src.charAt(pos + 1) == '&') {
                    pos += 2;
                    FoldVal r = parseBor();
                    if (l == null || r == null) {
                        return null;
                    }
                    l = cfpToBoolean(l) ? r : l;
                } else {
                    return l;
                }
            }
        }

        FoldVal parseBor() {
            FoldVal l = parseBxor();
            if (fail) {
                return null;
            }
            for (;;) {
                skipW();
                if (pos < n && src.charAt(pos) == '|' && (pos + 1 >= n || src.charAt(pos + 1) != '|')) {
                    pos++;
                    FoldVal r = parseBxor();
                    if (l == null || r == null) {
                        return null;
                    }
                    if (l.type != FoldVal.T_NUM || r.type != FoldVal.T_NUM) {
                        return null;
                    }
                    l = FoldVal.numV((double) (toInt32(l.num) | toInt32(r.num)));
                } else {
                    return l;
                }
            }
        }

        FoldVal parseBxor() {
            FoldVal l = parseBand();
            if (fail) {
                return null;
            }
            for (;;) {
                skipW();
                if (pos < n && src.charAt(pos) == '^') {
                    pos++;
                    FoldVal r = parseBand();
                    if (l == null || r == null) {
                        return null;
                    }
                    if (l.type != FoldVal.T_NUM || r.type != FoldVal.T_NUM) {
                        return null;
                    }
                    l = FoldVal.numV((double) (toInt32(l.num) ^ toInt32(r.num)));
                } else {
                    return l;
                }
            }
        }

        FoldVal parseBand() {
            FoldVal l = parseEq();
            if (fail) {
                return null;
            }
            for (;;) {
                skipW();
                if (pos < n && src.charAt(pos) == '&' && (pos + 1 >= n || src.charAt(pos + 1) != '&')) {
                    pos++;
                    FoldVal r = parseEq();
                    if (l == null || r == null) {
                        return null;
                    }
                    if (l.type != FoldVal.T_NUM || r.type != FoldVal.T_NUM) {
                        return null;
                    }
                    l = FoldVal.numV((double) (toInt32(l.num) & toInt32(r.num)));
                } else {
                    return l;
                }
            }
        }

        FoldVal parseEq() {
            FoldVal l = parseRel();
            if (fail) {
                return null;
            }
            for (;;) {
                skipW();
                boolean notEq = false;
                boolean strict = false;
                if (pos + 2 < n && src.charAt(pos) == '=' && src.charAt(pos + 1) == '=' && src.charAt(pos + 2) == '=') {
                    pos += 3;
                    strict = true;
                } else if (pos + 1 < n && src.charAt(pos) == '=' && src.charAt(pos + 1) == '=') {
                    pos += 2;
                    strict = false;
                } else if (pos + 2 < n && src.charAt(pos) == '!' && src.charAt(pos + 1) == '=' && src.charAt(pos + 2) == '=') {
                    notEq = true;
                    pos += 3;
                    strict = true;
                } else if (pos + 1 < n && src.charAt(pos) == '!' && src.charAt(pos + 1) == '=') {
                    notEq = true;
                    pos += 2;
                    strict = false;
                } else {
                    return l;
                }
                FoldVal r = parseRel();
                if (l == null || r == null) {
                    return null;
                }
                boolean b = strict ? cfpStrictEqual(l, r) : cfpLooseEqual(l, r);
                if (notEq) {
                    b = !b;
                }
                l = FoldVal.boolV(b);
            }
        }

        FoldVal parseRel() {
            FoldVal l = parseShift();
            if (fail) {
                return null;
            }
            for (;;) {
                skipW();
                if (pos + 1 < n && src.charAt(pos) == '<' && src.charAt(pos + 1) == '=') {
                    pos += 2;
                    FoldVal r = parseShift();
                    if (l == null || r == null) {
                        return null;
                    }
                    if (l.type != FoldVal.T_NUM || r.type != FoldVal.T_NUM) {
                        return null;
                    }
                    l = FoldVal.boolV(l.num <= r.num);
                } else if (pos + 1 < n && src.charAt(pos) == '>' && src.charAt(pos + 1) == '=') {
                    pos += 2;
                    FoldVal r = parseShift();
                    if (l == null || r == null) {
                        return null;
                    }
                    if (l.type != FoldVal.T_NUM || r.type != FoldVal.T_NUM) {
                        return null;
                    }
                    l = FoldVal.boolV(l.num >= r.num);
                } else if (pos < n && src.charAt(pos) == '<') {
                    pos++;
                    FoldVal r = parseShift();
                    if (l == null || r == null) {
                        return null;
                    }
                    if (l.type != FoldVal.T_NUM || r.type != FoldVal.T_NUM) {
                        return null;
                    }
                    l = FoldVal.boolV(l.num < r.num);
                } else if (pos < n && src.charAt(pos) == '>') {
                    pos++;
                    FoldVal r = parseShift();
                    if (l == null || r == null) {
                        return null;
                    }
                    if (l.type != FoldVal.T_NUM || r.type != FoldVal.T_NUM) {
                        return null;
                    }
                    l = FoldVal.boolV(l.num > r.num);
                } else {
                    return l;
                }
            }
        }

        FoldVal parseShift() {
            FoldVal l = parseAdd();
            if (fail) {
                return null;
            }
            for (;;) {
                skipW();
                if (pos + 1 < n && src.charAt(pos) == '<' && src.charAt(pos + 1) == '<') {
                    pos += 2;
                    FoldVal r = parseAdd();
                    if (l == null || r == null) {
                        return null;
                    }
                    if (l.type != FoldVal.T_NUM || r.type != FoldVal.T_NUM) {
                        return null;
                    }
                    l = FoldVal.numV((double) (toInt32(l.num) << jsRhsShift(r.num)));
                } else if (pos + 2 < n && src.charAt(pos) == '>' && src.charAt(pos + 1) == '>' && src.charAt(pos + 2) == '>') {
                    pos += 3;
                    FoldVal r = parseAdd();
                    if (l == null || r == null) {
                        return null;
                    }
                    if (l.type != FoldVal.T_NUM || r.type != FoldVal.T_NUM) {
                        return null;
                    }
                    l = FoldVal.numV(uintRsh(l.num, (int) (toInt32(r.num) & 31)));
                } else if (pos + 1 < n && src.charAt(pos) == '>' && src.charAt(pos + 1) == '>') {
                    pos += 2;
                    FoldVal r = parseAdd();
                    if (l == null || r == null) {
                        return null;
                    }
                    if (l.type != FoldVal.T_NUM || r.type != FoldVal.T_NUM) {
                        return null;
                    }
                    l = FoldVal.numV((double) (toInt32(l.num) >> jsRhsShift(r.num)));
                } else {
                    return l;
                }
            }
        }

        FoldVal parseAdd() {
            FoldVal l = parseMul();
            if (fail) {
                return null;
            }
            for (;;) {
                skipW();
                if (pos < n && src.charAt(pos) == '+') {
                    pos++;
                    FoldVal r = parseMul();
                    if (l == null || r == null) {
                        return null;
                    }
                    l = cfpAdd(l, r);
                } else if (pos < n && src.charAt(pos) == '-') {
                    pos++;
                    FoldVal r = parseMul();
                    if (l == null || r == null) {
                        return null;
                    }
                    if (l.type != FoldVal.T_NUM || r.type != FoldVal.T_NUM) {
                        return null;
                    }
                    l = FoldVal.numV(l.num - r.num);
                } else {
                    return l;
                }
            }
        }

        FoldVal parseMul() {
            FoldVal l = parsePow();
            if (fail) {
                return null;
            }
            for (;;) {
                skipW();
                if (pos < n && src.charAt(pos) == '*') {
                    if (pos + 1 < n && src.charAt(pos + 1) == '*') {
                        return l;
                    }
                    pos++;
                    FoldVal r = parsePow();
                    if (l == null || r == null) {
                        return null;
                    }
                    if (l.type != FoldVal.T_NUM || r.type != FoldVal.T_NUM) {
                        return null;
                    }
                    l = FoldVal.numV(l.num * r.num);
                } else if (pos < n && src.charAt(pos) == '/') {
                    pos++;
                    FoldVal r = parsePow();
                    if (l == null || r == null) {
                        return null;
                    }
                    if (l.type != FoldVal.T_NUM || r.type != FoldVal.T_NUM) {
                        return null;
                    }
                    l = FoldVal.numV(l.num / r.num);
                } else if (pos < n && src.charAt(pos) == '%') {
                    pos++;
                    FoldVal r = parsePow();
                    if (l == null || r == null) {
                        return null;
                    }
                    if (l.type != FoldVal.T_NUM || r.type != FoldVal.T_NUM) {
                        return null;
                    }
                    l = FoldVal.numV(l.num % r.num);
                } else {
                    return l;
                }
            }
        }

        FoldVal parsePow() {
            FoldVal l = parseUnary();
            if (fail) {
                return null;
            }
            skipW();
            if (pos + 1 < n && src.charAt(pos) == '*' && src.charAt(pos + 1) == '*') {
                pos += 2;
                FoldVal r = parsePow();
                if (l == null || r == null) {
                    return null;
                }
                if (l.type != FoldVal.T_NUM || r.type != FoldVal.T_NUM) {
                    return null;
                }
                return FoldVal.numV(CldcMath.pow(l.num, r.num));
            }
            return l;
        }

        FoldVal parseUnary() {
            skipW();
            if (pos < n && src.charAt(pos) == '+') {
                pos++;
                return parseUnary();
            }
            if (pos < n && src.charAt(pos) == '-') {
                pos++;
                FoldVal u = parseUnary();
                if (u == null) {
                    return null;
                }
                if (u.type != FoldVal.T_NUM) {
                    return null;
                }
                return FoldVal.numV(-u.num);
            }
            if (pos < n && src.charAt(pos) == '~') {
                pos++;
                FoldVal u = parseUnary();
                if (u == null) {
                    return null;
                }
                if (u.type != FoldVal.T_NUM) {
                    return null;
                }
                return FoldVal.numV((double) (toInt32(u.num) ^ 0xffffffff));
            }
            if (pos < n && src.charAt(pos) == '!') {
                pos++;
                FoldVal u = parseUnary();
                if (u == null) {
                    return null;
                }
                return FoldVal.boolV(!cfpToBoolean(u));
            }
            if (n - pos >= 6 && (src.regionMatches(false, pos, "typeof", 0, 6)) && (pos + 6 >= n || !isIdentPart(src.charAt(pos + 6)))) {
                pos += 6;
                FoldVal u = parseUnary();
                if (u == null) {
                    return null;
                }
                return FoldVal.strV(cfpTypeofVal(u));
            }
            return parsePrimary();
        }

        String cfpTypeofVal(FoldVal u) {
            if (u == null) {
                return "object";
            }
            if (u.type == FoldVal.T_UNDEF) {
                return "undefined";
            }
            if (u.type == FoldVal.T_NULL) {
                return "object";
            }
            if (u.type == FoldVal.T_NUM) {
                return "number";
            }
            if (u.type == FoldVal.T_STR) {
                return "string";
            }
            if (u.type == FoldVal.T_BOOL) {
                return "boolean";
            }
            return "object";
        }

        FoldVal parsePrimary() {
            skipW();
            if (pos >= n) {
                return null;
            }
            if (pos < n && (src.charAt(pos) == '"' || src.charAt(pos) == '\'')) {
                StringBuffer sb = new StringBuffer();
                int e = readStringLiteral(src, pos, n, sb);
                if (e < 0) {
                    return null;
                }
                pos = e;
                return FoldVal.strV(sb.toString());
            }
            if (pos < n && (src.charAt(pos) == '(')) {
                pos++;
                FoldVal v = parseTernary();
                skipW();
                if (pos >= n || src.charAt(pos) != ')') {
                    return null;
                }
                pos++;
                return v;
            }
            double[] nv = new double[1];
            int nl = readNumberLen(src, pos, n, nv);
            if (nl > 0) {
                pos += nl;
                return FoldVal.numV(nv[0]);
            }
            if (n - pos >= 4 && (src.regionMatches(false, pos, "true", 0, 4)) && (pos + 4 >= n || !isIdentPart(src.charAt(pos + 4)))) {
                pos += 4;
                return FoldVal.boolV(true);
            }
            if (n - pos >= 5 && (src.regionMatches(false, pos, "false", 0, 5)) && (pos + 5 >= n || !isIdentPart(src.charAt(pos + 5)))) {
                pos += 5;
                return FoldVal.boolV(false);
            }
            if (n - pos >= 4 && (src.regionMatches(false, pos, "null", 0, 4)) && (pos + 4 >= n || !isIdentPart(src.charAt(pos + 4)))) {
                pos += 4;
                FoldVal v = new FoldVal();
                v.type = FoldVal.T_NULL;
                return v;
            }
            if (n - pos >= 9 && (src.regionMatches(false, pos, "undefined", 0, 9)) && (pos + 9 >= n || !isIdentPart(src.charAt(pos + 9)))) {
                pos += 9;
                FoldVal v = new FoldVal();
                v.type = FoldVal.T_UNDEF;
                return v;
            }
            if (n - pos >= 3 && (src.regionMatches(false, pos, "NaN", 0, 3)) && (pos + 3 >= n || !isIdentPart(src.charAt(pos + 3)))) {
                pos += 3;
                return FoldVal.numV(Double.NaN);
            }
            if (n - pos >= 8 && (src.regionMatches(false, pos, "Infinity", 0, 8)) && (pos + 8 >= n || !isIdentPart(src.charAt(pos + 8)))) {
                pos += 8;
                return FoldVal.numV(Double.POSITIVE_INFINITY);
            }
            if (n - pos > 0) {
                char z = src.charAt(pos);
                if (z == '/' && pos + 1 < n) {
                    char z2 = src.charAt(pos + 1);
                    if (z2 == '/' || z2 == '*') {
                        return null;
                    }
                }
            }
            String id = readIdent(src, pos);
            if (id == null) {
                return null;
            }
            int afterId = pos + id.length();
            if ("Math".equals(id) && afterId < n && src.charAt(afterId) == '.') {
                int p = afterId + 1;
                String mem = readIdent(src, p);
                if (mem == null) {
                    return null;
                }
                p = p + mem.length();
                if (p < n && src.charAt(p) == '(') {
                    pos = p;
                    return parseMathCall(mem);
                }
                FoldVal prop = mathStaticProperty(mem);
                if (prop != null) {
                    pos = p;
                    return prop;
                }
                return null;
            }
            if ("Number".equals(id) && afterId < n && src.charAt(afterId) == '.') {
                int p = afterId + 1;
                String mem = readIdent(src, p);
                if (mem == null) {
                    return null;
                }
                p = p + mem.length();
                if (p < n && src.charAt(p) == '(') {
                    return null;
                }
                FoldVal st = numberStatic(mem);
                if (st == null) {
                    return null;
                }
                pos = p;
                return st;
            }
            pos = afterId;
            return scopeLookupName(scopes, id, tainted);
        }

        private FoldVal mathStaticProperty(String mem) {
            if ("PI".equals(mem)) {
                return FoldVal.numV(Math.PI);
            }
            if ("E".equals(mem)) {
                return FoldVal.numV(Math.E);
            }
            if ("LN2".equals(mem)) {
                return FoldVal.numV(CldcMath.LN2);
            }
            if ("LN10".equals(mem)) {
                return FoldVal.numV(2.302585092994046);
            }
            if ("LOG2E".equals(mem)) {
                return FoldVal.numV(1.4426950408889634);
            }
            if ("LOG10E".equals(mem)) {
                return FoldVal.numV(0.4342944819032518);
            }
            if ("SQRT2".equals(mem)) {
                return FoldVal.numV(1.4142135623730951);
            }
            if ("SQRT1_2".equals(mem)) {
                return FoldVal.numV(0.7071067811865476);
            }
            return null;
        }

        private FoldVal numberStatic(String mem) {
            if ("MAX_VALUE".equals(mem)) {
                return FoldVal.numV(Double.MAX_VALUE);
            }
            if ("MIN_VALUE".equals(mem)) {
                return FoldVal.numV(Double.MIN_VALUE);
            }
            if ("POSITIVE_INFINITY".equals(mem)) {
                return FoldVal.numV(Double.POSITIVE_INFINITY);
            }
            if ("NEGATIVE_INFINITY".equals(mem)) {
                return FoldVal.numV(Double.NEGATIVE_INFINITY);
            }
            if ("NaN".equals(mem)) {
                return FoldVal.numV(Double.NaN);
            }
            if ("EPSILON".equals(mem)) {
                return FoldVal.numV(2.220446049250313e-16);
            }
            return null;
        }

        private FoldVal parseMathCall(String mem) {
            if (pos >= n || src.charAt(pos) != '(') {
                return null;
            }
            pos++;
            Vector args = new Vector();
            skipW();
            if (pos < n && src.charAt(pos) == ')') {
                pos++;
                if ("random".equals(mem)) {
                    return null;
                }
                if ("max".equals(mem)) {
                    return FoldVal.numV(Double.NEGATIVE_INFINITY);
                }
                if ("min".equals(mem)) {
                    return FoldVal.numV(Double.POSITIVE_INFINITY);
                }
                return null;
            }
            for (;;) {
                FoldVal a = parseTernary();
                if (a == null) {
                    return null;
                }
                args.addElement(a);
                skipW();
                if (pos < n && src.charAt(pos) == ',') {
                    pos++;
                    continue;
                }
                break;
            }
            if (pos >= n || src.charAt(pos) != ')') {
                return null;
            }
            pos++;
            if ("floor".equals(mem) && args.size() == 1) {
                FoldVal a = (FoldVal) args.elementAt(0);
                if (a.type != FoldVal.T_NUM) {
                    return null;
                }
                return FoldVal.numV(Math.floor(a.num));
            }
            if ("ceil".equals(mem) && args.size() == 1) {
                FoldVal a = (FoldVal) args.elementAt(0);
                if (a.type != FoldVal.T_NUM) {
                    return null;
                }
                return FoldVal.numV(Math.ceil(a.num));
            }
            if ("round".equals(mem) && args.size() == 1) {
                FoldVal a = (FoldVal) args.elementAt(0);
                if (a.type != FoldVal.T_NUM) {
                    return null;
                }
                return FoldVal.numV((double) cfpMathRound(a.num));
            }
            if ("abs".equals(mem) && args.size() == 1) {
                FoldVal a = (FoldVal) args.elementAt(0);
                if (a.type != FoldVal.T_NUM) {
                    return null;
                }
                return FoldVal.numV(Math.abs(a.num));
            }
            if ("sqrt".equals(mem) && args.size() == 1) {
                FoldVal a = (FoldVal) args.elementAt(0);
                if (a.type != FoldVal.T_NUM) {
                    return null;
                }
                return FoldVal.numV(Math.sqrt(a.num));
            }
            if ("log".equals(mem) && args.size() == 1) {
                FoldVal a = (FoldVal) args.elementAt(0);
                if (a.type != FoldVal.T_NUM) {
                    return null;
                }
                return FoldVal.numV(CldcMath.log(a.num));
            }
            if ("exp".equals(mem) && args.size() == 1) {
                FoldVal a = (FoldVal) args.elementAt(0);
                if (a.type != FoldVal.T_NUM) {
                    return null;
                }
                return FoldVal.numV(CldcMath.exp(a.num));
            }
            if ("sin".equals(mem) && args.size() == 1) {
                FoldVal a = (FoldVal) args.elementAt(0);
                if (a.type != FoldVal.T_NUM) {
                    return null;
                }
                return FoldVal.numV(Math.sin(a.num));
            }
            if ("cos".equals(mem) && args.size() == 1) {
                FoldVal a = (FoldVal) args.elementAt(0);
                if (a.type != FoldVal.T_NUM) {
                    return null;
                }
                return FoldVal.numV(Math.cos(a.num));
            }
            if ("tan".equals(mem) && args.size() == 1) {
                FoldVal a = (FoldVal) args.elementAt(0);
                if (a.type != FoldVal.T_NUM) {
                    return null;
                }
                return FoldVal.numV(Math.tan(a.num));
            }
            if ("pow".equals(mem) && args.size() == 2) {
                FoldVal a0 = (FoldVal) args.elementAt(0);
                FoldVal a1 = (FoldVal) args.elementAt(1);
                if (a0.type != FoldVal.T_NUM || a1.type != FoldVal.T_NUM) {
                    return null;
                }
                return FoldVal.numV(CldcMath.pow(a0.num, a1.num));
            }
            if ("min".equals(mem)) {
                if (args.size() == 0) {
                    return null;
                }
                double m = toNumberT((FoldVal) args.elementAt(0));
                for (int k = 1; k < args.size(); k++) {
                    m = Math.min(m, toNumberT((FoldVal) args.elementAt(k)));
                }
                return FoldVal.numV(m);
            }
            if ("max".equals(mem)) {
                if (args.size() == 0) {
                    return null;
                }
                double m = toNumberT((FoldVal) args.elementAt(0));
                for (int k = 1; k < args.size(); k++) {
                    m = Math.max(m, toNumberT((FoldVal) args.elementAt(k)));
                }
                return FoldVal.numV(m);
            }
            return null;
        }

        double toNumberT(FoldVal v) {
            return cfpToNumberVal(v);
        }
    }

    private static int cfpMathRound(double x) {
        if (x != x) {
            return 0;
        }
        if (x < 0) {
            return -((int) Math.floor(-x + 0.5));
        }
        return (int) Math.floor(x + 0.5);
    }

    private static double cfpToNumberVal(FoldVal v) {
        if (v == null) {
            return Double.NaN;
        }
        if (v.type == FoldVal.T_NUM) {
            return v.num;
        }
        if (v.type == FoldVal.T_STR) {
            if (v.str == null) {
                return 0.0;
            }
            if (v.str.length() == 0) {
                return 0.0;
            }
            try {
                return Double.parseDouble(v.str);
            } catch (Throwable t) {
                return Double.NaN;
            }
        }
        if (v.type == FoldVal.T_BOOL) {
            return v.b ? 1.0 : 0.0;
        }
        if (v.type == FoldVal.T_NULL) {
            return 0.0;
        }
        if (v.type == FoldVal.T_UNDEF) {
            return Double.NaN;
        }
        return Double.NaN;
    }

    private static FoldVal cfpAdd(FoldVal l, FoldVal r) {
        if (l.type == FoldVal.T_STR || r.type == FoldVal.T_STR) {
            return FoldVal.strV(cfpToStringHelp(l) + cfpToStringHelp(r));
        }
        if (l.type == FoldVal.T_NUM && r.type == FoldVal.T_NUM) {
            return FoldVal.numV(l.num + r.num);
        }
        return null;
    }

    private static String cfpToStringHelp(FoldVal v) {
        if (v == null) {
            return "null";
        }
        if (v.type == FoldVal.T_STR) {
            return v.str == null ? "null" : v.str;
        }
        if (v.type == FoldVal.T_NUM) {
            if (v.num != v.num) {
                return "NaN";
            }
            if (v.num == 0) {
                return "0";
            }
            if (v.num == (double) (long) v.num && v.num >= -2147483648.0 && v.num <= 2147483647.0) {
                return String.valueOf((long) v.num);
            }
            return String.valueOf(v.num);
        }
        if (v.type == FoldVal.T_BOOL) {
            return v.b ? "true" : "false";
        }
        if (v.type == FoldVal.T_NULL) {
            return "null";
        }
        if (v.type == FoldVal.T_UNDEF) {
            return "undefined";
        }
        return "";
    }

    private static boolean cfpToBoolean(FoldVal v) {
        if (v == null) {
            return false;
        }
        if (v.type == FoldVal.T_NUM) {
            if (v.num != v.num) {
                return false;
            }
            if (v.num == 0.0) {
                return 1.0 / v.num > 0;
            } else {
                return true;
            }
        }
        if (v.type == FoldVal.T_STR) {
            return v.str != null && v.str.length() > 0;
        }
        if (v.type == FoldVal.T_BOOL) {
            return v.b;
        }
        if (v.type == FoldVal.T_NULL) {
            return false;
        }
        if (v.type == FoldVal.T_UNDEF) {
            return false;
        }
        return false;
    }

    private static boolean cfpStrictEqual(FoldVal a, FoldVal b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.type != b.type) {
            if ((a.type == FoldVal.T_NULL && b.type == FoldVal.T_UNDEF) || (a.type == FoldVal.T_UNDEF && b.type == FoldVal.T_NULL)) {
                return false;
            }
            return false;
        }
        if (a.type == FoldVal.T_NUM) {
            if (a.num != a.num && b.num != b.num) {
                return true;
            }
            return a.num == b.num;
        }
        if (a.type == FoldVal.T_STR) {
            if (a.str == b.str) {
                return true;
            }
            if (a.str == null || b.str == null) {
                return false;
            }
            return a.str.equals(b.str);
        }
        if (a.type == FoldVal.T_BOOL) {
            return a.b == b.b;
        }
        if (a.type == FoldVal.T_NULL) {
            return true;
        }
        if (a.type == FoldVal.T_UNDEF) {
            return true;
        }
        return false;
    }

    private static boolean cfpLooseEqual(FoldVal a, FoldVal b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.type == b.type) {
            return cfpStrictEqual(a, b);
        }
        if (a.type == FoldVal.T_NULL && b.type == FoldVal.T_UNDEF) {
            return true;
        }
        if (a.type == FoldVal.T_UNDEF && b.type == FoldVal.T_NULL) {
            return true;
        }
        if (a.type == FoldVal.T_NUM) {
            if (b.type == FoldVal.T_STR) {
                return a.num == cfpToNumberVal(b);
            }
        }
        if (a.type == FoldVal.T_STR) {
            if (b.type == FoldVal.T_NUM) {
                return cfpToNumberVal(a) == b.num;
            }
        }
        if (a.type == FoldVal.T_BOOL) {
            return cfpLooseEqual(FoldVal.numV(a.b ? 1.0 : 0.0), b);
        }
        if (b.type == FoldVal.T_BOOL) {
            return cfpLooseEqual(a, FoldVal.numV(b.b ? 1.0 : 0.0));
        }
        return cfpToNumberVal(a) == cfpToNumberVal(b);
    }

    private static int copyFunctionPreambleToCloseParen(String s, int i, int n, StringBuffer out, StringBuffer paramInner) {
        if (i + 8 > n || !matchKw(s, i, "function") || (i + 8 < n && isIdentPart(s.charAt(i + 8)))) {
            return -1;
        }
        int j = i;
        for (int k = 0; k < 8; k++) {
            out.append(s.charAt(j++));
        }
        while (j < n && isWs(s.charAt(j))) {
            out.append(s.charAt(j++));
        }
        if (j < n) {
            char c = s.charAt(j);
            if (isIdentStart(c)) {
                if (j + 1 < n) {
                    int lp2 = s.indexOf('(', j);
                    if (lp2 > j) {
                        while (j < n && isIdentPart(s.charAt(j))) {
                            out.append(s.charAt(j));
                            j++;
                        }
                    }
                }
            }
        }
        while (j < n && isWs(s.charAt(j))) {
            out.append(s.charAt(j++));
        }
        if (j >= n || s.charAt(j) != '(') {
            return -1;
        }
        out.append('(');
        int p0 = j + 1;
        int depth = 1;
        j++;
        StringBuffer tbuf = new StringBuffer();
        int j2;
        while (j < n && depth > 0) {
            char c = s.charAt(j);
            if (c == '"' || c == '\'' || c == '`' || (c == '/' && j + 1 < n && (s.charAt(j + 1) == '/' || s.charAt(j + 1) == '*'))) {
                tbuf.setLength(0);
                j2 = copyLiteral(s, j, tbuf);
                if (j2 > j) {
                    out.append(tbuf.toString());
                    j = j2;
                    continue;
                }
            }
            if (c == '(') {
                depth++;
                out.append(c);
                j++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    if (paramInner != null) {
                        paramInner.setLength(0);
                        paramInner.append(s.substring(p0, j));
                    }
                    out.append(')');
                    j++;
                    return j;
                }
                out.append(c);
                j++;
            } else {
                out.append(c);
                j++;
            }
        }
        return -1;
    }

    private static int skipDeclKeyword(String s, int i, int n) {
        if (i + 5 <= n && matchKw(s, i, "const")) {
            return i + 5;
        }
        if (i + 3 <= n && matchKw(s, i, "var")) {
            return i + 3;
        }
        if (i + 3 <= n && matchKw(s, i, "let")) {
            return i + 3;
        }
        return -1;
    }

    private static char lastNonWsIn(StringBuffer out) {
        int b = out.length() - 1;
        while (b >= 0 && isWs(out.charAt(b))) {
            b--;
        }
        if (b < 0) {
            return 0;
        }
        return out.charAt(b);
    }

    private static boolean isExprStartCharAt(StringBuffer out) {
        int b = out.length() - 1;
        while (b >= 0 && isWs(out.charAt(b))) {
            b--;
        }
        if (b < 0) {
            return true;
        }
        char c = out.charAt(b);
        if (c == '=' || c == '(' || c == '[' || c == ','
                || c == ';' || c == ':' || c == '?' || c == '!'
                || c == '+' || c == '-' || c == '*' || c == '%' || c == '&' || c == '|' || c == '^' || c == '<' || c == '>' || c == '~' || c == '\n') {
            return true;
        }
        if (b >= 5) {
            if (b - 5 >= 0) {
                String tail = sbSubstr(out, b - 5, b + 1);
                if (tail != null && "return".equals(tail)
                        && (b < 5 || b - 6 < 0 || !isIdentPart(out.charAt(b - 6)))) {
                    return true;
                }
            }
        }
        if (b >= 4) {
            if (b - 4 >= 0) {
                String t2 = sbSubstr(out, b - 4, b + 1);
                if (t2 != null && "case".equals(t2) && (b - 4 == 0 || b - 5 < 0 || !isIdentPart(out.charAt(b - 5)))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void putParamNamesAsShadows(String paramInner, Hashtable h) {
        if (paramInner == null || h == null) {
            return;
        }
        String p2 = paramInner.trim();
        if (p2.length() == 0) {
            return;
        }
        int a = 0, m = p2.length();
        int brk = 0;
        int segStart = 0;
        for (a = 0; a < m; a++) {
            char c = p2.charAt(a);
            if (c == '(' || c == '[' || c == '{') {
                brk++;
            } else if (c == ')' || c == ']' || c == '}') {
                brk--;
            } else if (c == ',' && brk == 0) {
                String seg = p2.substring(segStart, a).trim();
                putOneParamShadowIn(seg, h);
                segStart = a + 1;
            }
        }
        putOneParamShadowIn(p2.substring(segStart, m).trim(), h);
    }

    private static void putOneParamShadowIn(String seg, Hashtable h) {
        if (seg == null || seg.length() == 0) {
            return;
        }
        if (seg.charAt(0) == '{' || seg.charAt(0) == '[') {
            return;
        }
        int t = 0;
        if (seg.length() > 3 && seg.charAt(0) == '.' && seg.charAt(1) == '.' && seg.charAt(2) == '.') {
            t = 3;
        }
        while (t < seg.length() && isWs(seg.charAt(t))) {
            t++;
        }
        String name = readIdent(seg, t);
        if (name != null) {
            h.put(name, SCOPE_SHADOW);
        }
    }

    private static int tryHandleSimpleDecl(String s, int i, int n, StringBuffer out, Vector scopes, Hashtable tainted) {
        int klen = skipDeclKeyword(s, i, n);
        if (klen < 0) {
            return -1;
        }
        boolean isConst = (klen - i) == 5;
        int j = klen;
        j = skipWsIn(s, j, n);
        if (j < n) {
            char c = s.charAt(j);
            if (c == '{' || c == '[') {
                return -1;
            }
        }
        String name = readIdent(s, j);
        if (name == null) {
            return -1;
        }
        j += name.length();
        j = skipWsIn(s, j, n);
        if (j >= n || s.charAt(j) != '=') {
            return -1;
        }
        j++;
        int rhs0 = j;
        int q = findRhsEndInDecl(s, rhs0, n);
        if (q < n && s.charAt(q) == ',') {
            return -1;
        }
        FoldVal[] outFv = new FoldVal[1];
        int nend = tryParseFoldExprAt(s, rhs0, q, tainted, scopes, outFv);
        out.append("var").append(" ").append(name).append("=");
        if (nend > 0 && outFv[0] != null) {
            out.append(outFv[0].emit());
        } else {
            for (int z = rhs0; z < q; z++) {
                out.append(s.charAt(z));
            }
        }
        if (nend > 0 && isConst && tainted.get(name) == null) {
            scopePutVal(scopes, name, outFv[0]);
        }
        if (!isConst) {
            scopePutName(scopes, name);
        }
        if (q < n) {
            out.append(s.charAt(q));
        }
        return q + 1;
    }

    private static int tryProcessCatchPreamble(String s, int i, int n, StringBuffer out, String[] nameOut) {
        if (i + 4 > n || !matchKw(s, i, "catch") || (i + 4 < n && isIdentPart(s.charAt(i + 4)))) {
            return -1;
        }
        for (int c = 0; c < 5; c++) {
            out.append(s.charAt(i + c));
        }
        int j = i + 5;
        j = skipWsIn(s, j, n);
        if (j >= n || s.charAt(j) != '(') {
            return -1;
        }
        out.append('(');
        j++;
        j = skipWsIn(s, j, n);
        String e = readIdent(s, j);
        if (e == null) {
            return -1;
        }
        for (int k = 0; k < e.length(); k++) {
            out.append(s.charAt(j + k));
        }
        if (nameOut != null && nameOut.length > 0) {
            nameOut[0] = e;
        }
        j += e.length();
        j = skipWsIn(s, j, n);
        if (j >= n || s.charAt(j) != ')') {
            return -1;
        }
        out.append(')');
        return j + 1;
    }

    static String preprocessConstantFold(String src) {
        if (src == null || src.length() == 0) {
            return src;
        }
        Hashtable tainted = scanMutatedNames(src);
        int n = src.length();
        StringBuffer out = new StringBuffer(n + 32);
        Vector scopes = new Vector();
        scopes.addElement(new Hashtable());
        int i = 0;
        String innerParams = null;
        String catchName = null;
        while (i < n) {
            int lj = copyLiteral(src, i, out);
            if (lj > i) {
                i = lj;
                continue;
            }
            char c = src.charAt(i);
            if (c == '{') {
                Hashtable h2 = new Hashtable();
                if (innerParams != null) {
                    putParamNamesAsShadows(innerParams, h2);
                    innerParams = null;
                }
                if (catchName != null) {
                    h2.put(catchName, SCOPE_SHADOW);
                    catchName = null;
                }
                scopes.addElement(h2);
                out.append(c);
                i++;
                continue;
            }
            if (c == '}') {
                if (scopes.size() > 1) {
                    scopes.removeElementAt(scopes.size() - 1);
                }
                out.append(c);
                i++;
                continue;
            }
            if (i + 8 <= n && matchKw(src, i, "function") && (i + 8 >= n || !isIdentPart(src.charAt(i + 8)))) {
                StringBuffer pbuf = new StringBuffer();
                int fj = copyFunctionPreambleToCloseParen(src, i, n, out, pbuf);
                if (fj > i) {
                    innerParams = pbuf.toString();
                    i = fj;
                    continue;
                }
            }
            if (i + 5 <= n && matchKw(src, i, "catch") && (i + 5 >= n || !isIdentPart(src.charAt(i + 5)))) {
                String[] cname = new String[1];
                int cj = tryProcessCatchPreamble(src, i, n, out, cname);
                if (cj > 0) {
                    catchName = cname[0];
                    i = cj;
                    continue;
                }
            }
            int dkw2 = tryHandleSimpleDecl(src, i, n, out, scopes, tainted);
            if (dkw2 > i) {
                i = dkw2;
                continue;
            }
            if (lastNonWsIn(out) != '.'
                    && isExprStartCharAt(out)
                    && skipDeclKeyword(src, i, n) < 0
                    && !(i + 8 <= n && matchKw(src, i, "function") && (i + 8 >= n || !isIdentPart(src.charAt(i + 8))))) {
                FoldVal[] fv = new FoldVal[1];
                int en = tryParseFoldExprAt(src, i, n, tainted, scopes, fv);
                if (en > i) {
                    int wsEnd = skipWsIn(src, i, n);
                    for (int z = i; z < wsEnd; z++) {
                        out.append(src.charAt(z));
                    }
                    out.append(fv[0].emit());
                    i = en;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
