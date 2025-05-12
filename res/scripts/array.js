var arr = [100, '2nd', 102, "mid", , 'last']
console.log("original: arr.length = $arr.length, content: ${arr.join(',')}")
arr.length = 4
console.log("trim to 4: arr.length = $arr.length, content: ${arr.join(',')}")
arr = arr.concat.apply(arr, [8, [22,"haha",44], 10]);
console.log("concat: ${arr.join(',')}")
arr = arr.slice(1, 5);
console.log("slice(1, 5): ${arr.join(',')}")
arr.push('CC', [10, 'pu'], 'DD')
console.log("push: ${arr.join(',')}")
arr.reverse();
console.log("reversed: ${arr.join(',')}")
arr.unshift('1st', 2);
console.log("unshift('1st', 2): ${arr.join(',')}")
var pop = arr.pop()
console.log("pop(): $pop");
var shift = arr.shift()
console.log("shift(): $shift");
console.log("after pop&shift: ${arr.join(',')}")
arr.sort()
console.log("after sort: ${arr.join(',')}")
arr = [ 44, , 2222, 5, 11111, , undefined, 333 ]
console.log("before sort: ${arr.join(',')}")
arr.sort()
console.log("after sort: ${arr.join(',')}")
arr = [ 44, , 2222, 5, 11111, , undefined, 333 ]
arr.sort(function(a, b) { return a - b})
console.log("numbered sort: ${arr.join(',')}")
