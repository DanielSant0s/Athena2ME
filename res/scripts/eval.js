var str = """
var a = 3, b = 4, foo = function(x, y) { return x ** 2 + y ** 2 };
console.log("result=" + foo(a, b));
"""
function test() {
    eval(str);
}
test();