var a = 0xffffffff;
var b = ~a;
var c = 0x77 ^ 0x33;
var d = c & ~0xf;
var e = d;
e |= 0x80000000
var f = e >>> 16
var g = e << 8
var h = g >> 8

println("a=$a, b=$b, c=$c, d=$d, e=$e, f=$f, g=$g, h=$h");