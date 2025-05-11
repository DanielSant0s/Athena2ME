function foo() {
    println("arguments.length=$arguments.length");
    for (var i in arguments)
        println("arguments[$i]=$arguments[i]");

    var arr = arguments.concat("hello", "world", [ "22", , , "55" ])
    for (var i in arr) {
        println("arr[$i]=$arr[i]");
    }

}

foo(11, 22, 33, "test", undefined, null);