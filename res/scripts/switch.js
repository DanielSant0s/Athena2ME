var myobj = { a: "dd", b: "bbb", inner: [1, 2, 3] }
var c = 100;
var i = 0;
var sum = 0;
while (i++ < c)  sum += i;
console.log("sum=" + sum)
with (myobj)
switch (a) {
case "b":
    console.log("b");
    break;
default:
    console.log("default");
    break;
case "aaa":
    console.log("aaa");
    break;
case "bbb":
    console.log("bbb");
    break;
}
console.log("end!!");