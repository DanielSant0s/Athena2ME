var s = "1,2,3,,5,6,"
var idx1 = s.indexOf('5,');
var idx2 = s.indexOf('6,', 5);
var idx3 = s.indexOf('9,,');
println("idx1=$idx1, idx2=$idx2, idx3=$idx3");
var idx1 = s.lastIndexOf('5,');
var idx2 = s.lastIndexOf('2,', 5);
var idx3 = s.lastIndexOf('9,,');
println("idx1=$idx1, idx2=$idx2, idx3=$idx3");

println("charAt(5)=${s.charAt(4)}, charCode=${s.charCodeAt(4)}")

