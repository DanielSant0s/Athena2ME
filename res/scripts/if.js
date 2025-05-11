var a = 0;
var b = 0;
var result = "passed";

if ( a = b ) {
  result = "failed:  a = b should return 0";
}
println(result);