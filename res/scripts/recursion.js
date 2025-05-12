var a = function(x) { if (x <= 1) return 1; return x * arguments.callee(x - 1); }
console.log("result=${a(5)}");