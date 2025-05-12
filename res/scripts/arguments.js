function foo() {
    console.log("arguments.length=$arguments.length");
    for (var i in arguments)
        console.log("arguments[$i]=$arguments[i]");

    var arr = arguments.concat("hello", "world", [ "22", , , "55" ])
    for (var i in arr) {
        console.log("arr[$i]=$arr[i]");
    }

}

foo(11, 22, 33, "test", undefined, null);