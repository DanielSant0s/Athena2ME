function MyError(s) {
    this.message = "MyError.msg=" + s;
}
new MyError("")
MyError.prototype = new Error()


try {
    var a = 5 + 6;
    throw new MyError(a)
} catch (e) {
    println("catch: Error.message=" + e.message);
    //throw e
} finally {
    println("finally");
}
println("after try")

