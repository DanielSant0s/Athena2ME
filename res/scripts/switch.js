var myobj = { a: "dd", b: "bbb", inner: [1, 2, 3] }
var c = 100;
var i = 0;
var sum = 0;
while (i++ < c)  sum += i;
println("sum=" + sum)
with (myobj)
switch (a) {
case "b":
    println("b");
    break;
default:
    println("default");
    break;
case "aaa":
    println("aaa");
    break;
case "bbb":
    println("bbb");
    break;
}
println("end!!");