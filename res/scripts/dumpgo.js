var a = { n: 15, ob: { key: "aa", value: "bb" } }
console.log(">>>> Dumping content of global object...");
var test = new Test(20);
dumpObject(0, this);
function dumpObject(indent, obj) {
    for (var pname in obj) {
        if (indent >= 2) {
            for (var i = indent - 2; --i >= 0;) print(' ');
            print("- ");
        }
        var pvalue = obj[pname];
        switch (var type = typeof pvalue) {
        case "function":
            console.log("${pvalue.prototype ? 'Constructor' : 'Function'}: $pname($pvalue.length)");
            if (pvalue.prototype) {
                dumpObject(indent + 2, pvalue.prototype);
            }
            break;
        case "object":
            console.log("Object: $pname");
            if (pname != "this") {
                dumpObject(indent + 2, pvalue);
            } else {
                console.log("- not extended");
            }
            break;
        default:
            console.log("$pname=$type($pvalue)");
            break;
        }
    }
}

function Test(v) {
    this.value = v;
    this.array = [22, { a: 5, b: "hi!" }, , "blabla"]
}