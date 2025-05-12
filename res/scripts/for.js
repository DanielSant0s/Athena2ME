var a = {key : "key", value: [3,4,5,6,] };
var b = a.key + "=" + (a.value[1] + a.value[3]);
var sum = 0;
for (var i = 0; i < a.value.length; ++i) {
    sum += a.value[i];
}
console.log("b = $b, sum = $sum");
