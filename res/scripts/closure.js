// This function returns a function each time it is called
// The scope in which the function is defined differs for each call
function makefunc(x) {
//    return function() { return x; }
//  return new Function($direct_func_xxx$);
    return new Function("return x;")
}

// Call makefunc() several times, and save the results in an array:
var a = [makefunc(0), makefunc(1), makefunc(2)];

// Now call these functions and display their values.
// Although the body of each function is the same, the scope is
// different, and each call returns a different value:
alert(a[0]());  // Displays 0
alert(a[1]());  // Displays 1
alert(a[2]());  // Displays 2

