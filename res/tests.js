// -----------------------------------------------------------------------------
//  Athena2ME / forked RockScript — ES6+ smoke tests
//
//  These tests exercise the features added in the modernization plan. They are
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

    // -- Fase A --------------------------------------------------------------

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
        eq(Number.isNaN(NaN), true, "Number.isNaN(NaN)");
        eq(Number.parseInt("42"), 42, "Number.parseInt");
        eq(Math.abs(-7), 7, "Math.abs");
        eq(Math.sqrt(16), 4, "Math.sqrt(16)");
        eq(Math.pow(2, 10), 1024, "Math.pow(2,10)");
        eq(Math.sign(-5), -1, "Math.sign(-5)");
    }

    // -- Fase B --------------------------------------------------------------

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

    // -- Fase C --------------------------------------------------------------

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

    // -- Fase D --------------------------------------------------------------

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

        console.log("----------");
        console.log("Tests run: " + total + ", failed: " + failed);
        return failed === 0;
    }

    return { runAll: runAll };
}());

tests.runAll();
