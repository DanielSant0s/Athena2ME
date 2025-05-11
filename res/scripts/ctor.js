var c = 5;
var a=25+c,foo=function(a,b) { return a + b**5;},b=2;
var c = foo(a, b);
alert(c);
alert(this);
var myobj = new MyClass(100);
alert(myobj.val);
alert("myobj instanceof MyClass = ${myobj instanceof MyClass}");
alert("myobj instanceof Object = ${myobj instanceof Object}");
alert("myobj instanceof Function = ${myobj instanceof Function}");

function MyClass(value) {
    alert("arguments instanceof Array = ${arguments instanceof Array}")
    alert("arguments instanceof Object = ${arguments instanceof Object}")
    this.val = value;
}

