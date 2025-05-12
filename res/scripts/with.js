var rocks = { name: "Rocks", age: 32 };
with (rocks)
console.log("$name is $age years old. the combination is ${({a:1, b:2}.b + 5) * [3,5][1] }. ");
