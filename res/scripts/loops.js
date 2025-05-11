var a = [31, 14, 55, ,  , 22, 92, , 121,]

for (var i = 0; i < a.length; i++) {
    println("$a[i]")
}

var b = { Name: "Rocks", "Family Name": "Wang", Sex: "Male", Age: 32 }

for (var i in b) {
    println("$i = $b[i]")
}

var i = 100, sum = 0;
do {
    sum = sum + i;
    --i;
} while (i > 0);

println("do-while: sum=$sum");

sum = 0, i = 100;
while (i > 0) sum += i--;
println("while: sum=$sum");
