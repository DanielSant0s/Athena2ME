var n = 100;//sum(100);
alert(sum(n));
function sum(max) {
    var n = 0;
    for (var i = 1; i <= max; i++) {
        n = n + i;
    }
    return n;
}
