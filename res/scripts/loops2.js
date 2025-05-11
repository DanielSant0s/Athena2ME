var a = [31, 14, 55, ,  , 22, 92, , 121,]

println("raw array: \"${a.join(',')}\"");
for (var i = 0; i < a.length; i++) {
    if (a[i] == undefined) {
        println("The first undefined element found at index $i");
        break;
    }
    println("$a[i]")
}

for (var i = 0; i < a.length; i++) {
    if (a[i] == undefined || a[i] == null) {
        delete a[i--]
        println("delete empty element from index $i");
        continue
    }
    println("a[$i]=$a[i] not empty");
}
println("tidied: \"${a.join(',')}\"");
