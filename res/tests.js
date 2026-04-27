// -----------------------------------------------------------------------------
//  Athena2ME / forked RockScript — ES6+ smoke tests
//
//  These tests exercise the ES6+ feature set added in this fork. They are
//  intentionally shallow and do not require any host bindings beyond
//  console.log. To run them, either:
//
//    * rename this file to main.js (replacing the app's entry point); or
//    * paste its contents at the top of main.js and call tests.runAll().
//
//  Each assertion prints a short "PASS/FAIL" line; a final summary is printed
//  at the end. The last expression of the file kicks tests.runAll() off so
//  that the hosting MIDlet does not need any additional wiring.
// -----------------------------------------------------------------------------

var tests = (function () {
    var total = 0;
    var failed = 0;

    function eq(actual, expected, label) {
        total++;
        var ok = actual === expected
            || (typeof actual === "object" && JSON.stringify(actual) === JSON.stringify(expected));
        if (ok) {
            console.log("PASS  " + label);
        } else {
            failed++;
            console.log("FAIL  " + label + "  expected=" + JSON.stringify(expected) + " actual=" + JSON.stringify(actual));
        }
    }

    function truthy(v, label) { eq(!!v, true, label); }

    // -- Built-ins: array, object, string, JSON, number -----------------------

    function testArray() {
        var a = [1, 2, 3, 4];
        eq(a.map(function (x) { return x * 2; }), [2, 4, 6, 8], "Array.map");
        eq(a.filter(function (x) { return x % 2; }), [1, 3], "Array.filter");
        eq(a.reduce(function (acc, x) { return acc + x; }, 0), 10, "Array.reduce");
        eq(a.reduceRight(function (acc, x) { return acc + x * 10; }, 0), 1234, "Array.reduceRight");
        eq(a.find(function (x) { return x > 2; }), 3, "Array.find");
        eq(a.findIndex(function (x) { return x > 2; }), 2, "Array.findIndex");
        eq(a.some(function (x) { return x > 3; }), true, "Array.some");
        eq(a.every(function (x) { return x > 0; }), true, "Array.every");
        eq(a.includes(2), true, "Array.includes");
        eq(a.indexOf(3), 2, "Array.indexOf");
        eq(a.lastIndexOf(2), 1, "Array.lastIndexOf");
        eq([1, 2, 3].fill(0), [0, 0, 0], "Array.fill");
        eq([[1], [2, 3], [4]].flat(), [1, 2, 3, 4], "Array.flat");
        eq(Array.isArray(a), true, "Array.isArray");
        eq(Array.isArray("x"), false, "Array.isArray on non-array");
        eq(Array.of(9, 8, 7), [9, 8, 7], "Array.of");
        eq(Array.from("abc"), ["a", "b", "c"], "Array.from string");
    }

    function testObject() {
        var o = { a: 1, b: 2, c: 3 };
        eq(Object.keys(o), ["a", "b", "c"], "Object.keys");
        eq(Object.values(o), [1, 2, 3], "Object.values");
        eq(Object.entries(o), [["a", 1], ["b", 2], ["c", 3]], "Object.entries");
        var t = Object.assign({}, o, { b: 9 });
        eq(t, { a: 1, b: 9, c: 3 }, "Object.assign");
    }

    function testString() {
        eq("  hi  ".trim(), "hi", "String.trim");
        eq("abcdef".includes("cd"), true, "String.includes");
        eq("abcdef".startsWith("abc"), true, "String.startsWith");
        eq("abcdef".endsWith("ef"), true, "String.endsWith");
        eq("ab".repeat(3), "ababab", "String.repeat");
        eq("7".padStart(3, "0"), "007", "String.padStart");
        eq("abc".padEnd(5, "-"), "abc--", "String.padEnd");
        eq("hello".replace("l", "L"), "heLlo", "String.replace");
        eq("hello".replaceAll("l", "L"), "heLLo", "String.replaceAll");
        eq("ABC".toLowerCase(), "abc", "String.toLowerCase");
        eq("abc".toUpperCase(), "ABC", "String.toUpperCase");
        eq("abcdef".slice(1, 4), "bcd", "String.slice");
    }

    function testJson() {
        var obj = { a: 1, b: [2, 3], c: "x" };
        var s = JSON.stringify(obj);
        truthy(s.length > 0, "JSON.stringify produces output");
        eq(JSON.parse(s), obj, "JSON round-trip");
        eq(JSON.parse("[1,2,3]"), [1, 2, 3], "JSON array");
        eq(JSON.parse("null"), null, "JSON null");
        eq(JSON.parse("true"), true, "JSON true");
    }

    function testNumber() {
        eq(Number.isInteger(3), true, "Number.isInteger(3)");
        eq(Number.isNaN(0 / 0), true, "Number.isNaN(0/0)");
        eq(Number.parseInt("42"), 42, "Number.parseInt");
        eq(Math.abs(-7), 7, "Math.abs");
        eq(Math.sqrt(16), 4, "Math.sqrt(16)");
        eq(Math.pow(2, 10), 1024, "Math.pow(2,10)");
        eq(Math.sign(-5), -1, "Math.sign(-5)");
        eq(5 / 2, 2.5, "division float");
        eq(1.5 + 1.5, 3, "float add");
        truthy(Math.abs(Math.sin(Math.PI / 2) - 1) < 0.02, "Math.sin PI/2");
        eq(Math.floor(9.9), 9, "Math.floor float");
        eq(Number.parseFloat("3.14"), 3.14, "parseFloat");
        eq(1.9 | 0, 1, "bitwise | ToInt32");
    }

    // -- let/const, arrow, template, shorthand, defaults ----------------------

    function testLetConst() {
        let x = 1;
        const y = 2;
        eq(x + y, 3, "let + const basic arithmetic");
    }

    function testArrow() {
        var add = (a, b) => a + b;
        eq(add(2, 3), 5, "arrow (a,b) => a+b");
        var sq = n => n * n;
        eq(sq(4), 16, "arrow single param");
        var noop = () => 42;
        eq(noop(), 42, "arrow ()");
    }

    function testTemplate() {
        var x = 42;
        eq(`x is ${x}`, "x is 42", "template basic");
        eq(`${x + 1}`, "43", "template expression");
        eq(`multi
line`, "multi\nline", "template newline");
    }

    function testShorthand() {
        var a = 1, b = 2;
        var o = { a, b };
        eq(o.a, 1, "shorthand a");
        eq(o.b, 2, "shorthand b");
    }

    function testDefaults() {
        function greet(name = "world") { return "hi " + name; }
        eq(greet(), "hi world", "default param used");
        eq(greet("Dan"), "hi Dan", "default param overridden");
    }

    // -- spread, rest, destructuring, for...of --------------------------------

    function testSpread() {
        function sum(a, b, c) { return a + b + c; }
        var args = [1, 2, 3];
        eq(sum(...args), 6, "spread call");
    }

    function testRest() {
        function coll(first, ...rest) { return rest; }
        eq(coll(1, 2, 3, 4), [2, 3, 4], "rest param");
    }

    function testDestructuring() {
        var [a, b] = [10, 20];
        eq(a, 10, "array destructuring a");
        eq(b, 20, "array destructuring b");
        var { x, y } = { x: 1, y: 2 };
        eq(x, 1, "object destructuring x");
        eq(y, 2, "object destructuring y");
    }

    function testForOf() {
        var arr = [1, 2, 3];
        var sum = 0;
        for (var v of arr) sum += v;
        eq(sum, 6, "for...of array");
        var joined = "";
        for (var ch of "abc") joined += ch;
        eq(joined, "abc", "for...of string");
    }

    // -- class, Map, Set, Symbol ---------------------------------------------

    function testClass() {
        class Animal {
            constructor(name) { this.name = name; }
            speak() { return this.name + " speaks"; }
            static create(n) { return new Animal(n); }
        }
        class Dog extends Animal {
            constructor(name) { super(name); this.kind = "dog"; }
            speak() { return super.speak() + " (bark)"; }
        }
        var d = new Dog("Rex");
        eq(d.name, "Rex", "class ctor/inheritance");
        eq(d.kind, "dog", "class subclass state");
        eq(d.speak(), "Rex speaks (bark)", "class super.method()");
        eq(Animal.create("Kit").name, "Kit", "class static method");
    }

    function testMapSet() {
        var m = new Map();
        m.set("a", 1); m.set("b", 2);
        eq(m.get("a"), 1, "Map.get");
        eq(m.has("b"), true, "Map.has");
        eq(m.has("c"), false, "Map.has false");
        eq(m.keys(), ["a", "b"], "Map.keys");
        m.delete("a");
        eq(m.get("a"), undefined, "Map.delete");

        var s = new Set();
        s.add(1); s.add(2); s.add(1);
        eq(s.has(1), true, "Set.has");
        eq(s.values().length, 2, "Set dedup");
    }

    function testSymbol() {
        var a = Symbol("x");
        var b = Symbol("x");
        eq(a === b, false, "Symbol uniqueness");
        eq(a === a, true, "Symbol identity");
    }

    function testArrayBuffer() {
        var ab = new ArrayBuffer(8);
        eq(ab.byteLength, 8, "ArrayBuffer.byteLength");
        eq(ab instanceof ArrayBuffer, true, "instanceof ArrayBuffer");

        var u = new Uint8Array(ab);
        eq(u.length, 8, "Uint8Array.length");
        eq(u instanceof Uint8Array, true, "instanceof Uint8Array");
        u[0] = 200;
        u[7] = 99;
        eq(u[0], 200, "Uint8Array[] read");
        eq(u[7], 99, "Uint8Array[] end");

        var u2 = new Uint8Array(ab);
        eq(u2[0], 200, "Uint8Array shares ArrayBuffer");

        var sub = ab.slice(2, 6);
        eq(sub.byteLength, 4, "ArrayBuffer.slice length");
        var us = new Uint8Array(sub);
        eq(us[0], 0, "slice is zero-filled copy");

        var v = u.subarray(2, 6);
        v[0] = 55;
        eq(u[2], 55, "subarray shares memory");

        var empty = new Uint8Array();
        eq(empty.length, 0, "Uint8Array no-args");

        var d = new DataView(ab);
        d.setUint16(0, 0x3412, true);
        eq(d.getUint16(0, true), 0x3412, "DataView uint16 LE");
        d.setUint32(0, -1, false);
        eq(d.getInt32(0, false), -1, "DataView int32 BE");

        var sum = 0;
        for (var i = 0; i < u.length; i++) sum = sum + u[i];
        truthy(sum >= 200, "indexed sum over Uint8Array");
    }

    function testInt32Array() {
        var a = new Int32Array(4);
        eq(a.length, 4, "Int32Array.length");
        eq(a.byteLength, 16, "Int32Array.byteLength");
        eq(Int32Array.BYTES_PER_ELEMENT, 4, "BYTES_PER_ELEMENT");
        eq(a instanceof Int32Array, true, "instanceof Int32Array");
        a[0] = -1;
        a[3] = 0x7eedbeef;
        eq(a[0], -1, "Int32Array negative");
        eq(a[3], 0x7eedbeef, "Int32Array large positive");

        var ab = new ArrayBuffer(16);
        var b = new Int32Array(ab, 4, 2);
        eq(b.length, 2, "Int32Array buffer view length");
        b[0] = 42;
        var dv = new DataView(ab);
        eq(dv.getInt32(4, true), 42, "Int32Array little-endian matches DataView");

        var c = a.subarray(1, 3);
        a[1] = 100;
        eq(c[0], 100, "Int32Array.subarray shares memory");
    }

    // ------------------------------------------------------------------------

    function testConstantFolding() {
        eq((255 * 4), 1020, "const fold literal mul");
        const A = 255 * 4;
        eq(A, 1020, "const const propagation");
        const B = (1 << 8) | 0xF;
        eq(B, 271, "const fold bitwise");
        const C = "v" + "1" + "." + "0";
        eq(C, "v1.0", "const fold string concat");
        const D = Math.PI;
        truthy(D > 3.14, "const fold Math.PI");
        const E = Number.MAX_VALUE;
        truthy(E > 1e300, "const fold Number.MAX_VALUE");
        eq(0 && 1, 0, "const fold && (both foldable)");
        var X = 1;
        X = 2;
        eq(X, 2, "taint: reassigned not folded as const");
        const T = 1;
        function fShadow(x) {
            return T + 0;
        }
        eq(fShadow(0), 1, "outer const visible in function (no shadow name clash)");
    }

    function testOsPool() {
        function Box(v) {
            this.v = v;
        }
        var pool = os.pool(Box, 2);
        eq(pool.capacity(), 2, "os.pool capacity");
        eq(pool.free(), 2, "os.pool free initial");
        eq(pool.inUse(), 0, "os.pool inUse initial");
        var a = pool.acquire(10);
        truthy(a instanceof Box, "pooled instanceof constructor");
        eq(a.v, 10, "os.pool acquire passes args");
        eq(pool.free(), 1, "os.pool free after acquire");
        eq(pool.inUse(), 1, "os.pool inUse after acquire");
        var b = pool.acquire(20);
        eq(b.v, 20, "os.pool second acquire");
        eq(pool.acquire(99), null, "os.pool exhausted returns null");
        pool.release(a);
        eq(pool.free(), 1, "os.pool free after release");
        var a2 = pool.acquire(30);
        eq(a2 === a, true, "os.pool reuses same object");
        eq(a2.v, 30, "os.pool reinit after reuse");
        pool.release(a2);
        pool.release(b);
        eq(pool.free(), 2, "os.pool all slots free");
        eq(pool.inUse(), 0, "os.pool none in use");
    }

    function testScreenBatchAndLayer() {
        var L = Screen.createLayer(8, 8);
        truthy(L !== null && typeof L.width === "number", "Screen.createLayer returns object");
        eq(L.width, 8, "layer.width");
        eq(L.height, 8, "layer.height");
        Screen.setLayer(L);
        Draw.rect(0, 0, 4, 4, 0xff0000);
        Screen.setLayer(null);
        Screen.clear(0x000000);
        Screen.drawLayer(L, 0, 0);
        Screen.beginBatch();
        Screen.flushBatch();
        Screen.endBatch();
        Screen.clearLayer(L, 0x00ff00);
        Screen.freeLayer(L);
        truthy(true, "Screen batch/layer smoke");
    }

    /** Soft-path timing + getCapabilities (plan: profile Render3D). */
    function testRender3DBench() {
        if (typeof Render3D === "undefined") {
            console.log("SKIP Render3D (module missing)");
            return;
        }
        var cap0 = Render3D.getCapabilities();
        truthy(cap0 != null && typeof cap0.backend === "string", "Render3D.getCapabilities");
        truthy(cap0.m3gPresent === 0 || cap0.m3gPresent === 1, "getCapabilities.m3gPresent");
        var err = Render3D.setBackend("soft");
        if (err) {
            console.log("SKIP Render3D soft: " + err);
            return;
        }
        Render3D.init();
        var cap = Render3D.getCapabilities();
        eq(cap.backend, "soft", "getCapabilities.backend after setBackend soft");
        eq(cap.depthBufferOption, 1, "getCapabilities.depthBufferOption soft");
        truthy(cap.maxTriangles >= 32 && cap.maxTriangles <= 4096, "getCapabilities.maxTriangles range");
        var tA = os.uptimeMillis ? os.uptimeMillis() : 0;
        var strip = new Int32Array([3]);
        var pos = new Float32Array([-1, 0, 0, 1, 0, 0, 0, 1, 0]);
        Render3D.setTriangleStripMesh(pos, strip, null);
        var f;
        for (f = 0; f < 40; f++) {
            Render3D.begin();
            Render3D.render();
            Render3D.end();
        }
        var tB = os.uptimeMillis ? os.uptimeMillis() : 0;
        console.log("Render3D soft profile: 40 frames solid tris ~" + (tB - tA) + "ms (uptimeMillis)");
        err = Render3D.setTextureFilter("linear");
        if (err) {
            failed++;
            console.log("FAIL setTextureFilter: " + err);
        }
        err = Render3D.setTextureWrap("repeat");
        if (err) {
            failed++;
            console.log("FAIL setTextureWrap: " + err);
        }
        Render3D.setBackend("auto");
        truthy(true, "Render3D profile + texture options");
    }

    // ------------------------------------------------------------------------

    function runAll() {
        total = 0; failed = 0;
        try { testArray(); }         catch (e) { failed++; console.log("FAIL array: " + e.message); }
        try { testObject(); }        catch (e) { failed++; console.log("FAIL object: " + e.message); }
        try { testString(); }        catch (e) { failed++; console.log("FAIL string: " + e.message); }
        try { testJson(); }          catch (e) { failed++; console.log("FAIL json: " + e.message); }
        try { testNumber(); }        catch (e) { failed++; console.log("FAIL number: " + e.message); }
        try { testLetConst(); }      catch (e) { failed++; console.log("FAIL letconst: " + e.message); }
        try { testArrow(); }         catch (e) { failed++; console.log("FAIL arrow: " + e.message); }
        try { testTemplate(); }      catch (e) { failed++; console.log("FAIL template: " + e.message); }
        try { testShorthand(); }     catch (e) { failed++; console.log("FAIL shorthand: " + e.message); }
        try { testDefaults(); }      catch (e) { failed++; console.log("FAIL defaults: " + e.message); }
        try { testSpread(); }        catch (e) { failed++; console.log("FAIL spread: " + e.message); }
        try { testRest(); }          catch (e) { failed++; console.log("FAIL rest: " + e.message); }
        try { testDestructuring(); } catch (e) { failed++; console.log("FAIL destr: " + e.message); }
        try { testForOf(); }         catch (e) { failed++; console.log("FAIL forof: " + e.message); }
        try { testClass(); }         catch (e) { failed++; console.log("FAIL class: " + e.message); }
        try { testMapSet(); }        catch (e) { failed++; console.log("FAIL mapset: " + e.message); }
        try { testSymbol(); }        catch (e) { failed++; console.log("FAIL symbol: " + e.message); }
        try { testArrayBuffer(); }   catch (e) { failed++; console.log("FAIL arraybuffer: " + e.message); }
        try { testInt32Array(); }    catch (e) { failed++; console.log("FAIL int32array: " + e.message); }
        try { testConstantFolding(); } catch (e) { failed++; console.log("FAIL constfold: " + e.message); }
        try { testOsPool(); }         catch (e) { failed++; console.log("FAIL ospool: " + e.message); }
        try { testScreenBatchAndLayer(); } catch (e) { failed++; console.log("FAIL screen render: " + e.message); }
        try { testRender3DBench(); } catch (e) { failed++; console.log("FAIL render3d: " + e.message); }

        console.log("----------");
        console.log("Tests run: " + total + ", failed: " + failed);
        return failed === 0;
    }

    return { runAll: runAll };
}());

tests.runAll();
