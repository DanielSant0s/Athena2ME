var arr = [100, '2nd', 102, "mid", , 'last']
println("original: arr.length = $arr.length, content: ${arr.join(',')}")
arr.length = 4
println("trim to 4: arr.length = $arr.length, content: ${arr.join(',')}")
arr = arr.concat.apply(arr, [8, [22,"haha",44], 10]);
println("concat: ${arr.join(',')}")
arr = arr.slice(1, 5);
println("slice(1, 5): ${arr.join(',')}")
arr.push('CC', [10, 'pu'], 'DD')
println("push: ${arr.join(',')}")
arr.reverse();
println("reversed: ${arr.join(',')}")
arr.unshift('1st', 2);
println("unshift('1st', 2): ${arr.join(',')}")
var pop = arr.pop()
println("pop(): $pop");
var shift = arr.shift()
println("shift(): $shift");
println("after pop&shift: ${arr.join(',')}")
arr.sort()
println("after sort: ${arr.join(',')}")
arr = [ 44, , 2222, 5, 11111, , undefined, 333 ]
println("before sort: ${arr.join(',')}")
arr.sort()
println("after sort: ${arr.join(',')}")
arr = [ 44, , 2222, 5, 11111, , undefined, 333 ]
arr.sort(function(a, b) { return a - b})
println("numbered sort: ${arr.join(',')}")
