var a = [31, 14, 55, ,  , 22, 92, , 121,]

console.log("raw array: \"${a.join(',')}\"");
for (var i = 0; i < a.length; i++) {
    if (a[i] == undefined) {
        console.log("The first undefined element found at index $i");
        break;
    }
    console.log("$a[i]")
}

for (var i = 0; i < a.length; i++) {
    if (a[i] == undefined || a[i] == null) {
        delete a[i--]
        console.log("delete empty element from index $i");
        continue
    }
    console.log("a[$i]=$a[i] not empty");
}
console.log("tidied: \"${a.join(',')}\"");
