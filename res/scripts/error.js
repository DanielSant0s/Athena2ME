function MyError(s) {
    this.message = "MyError.msg=" + s;
}
new MyError("")
MyError.prototype = new Error()


try {
    var a = 5 + 6;
    throw new MyError(a)
} catch (e) {
    console.log("catch: Error.message=" + e.message);
    //throw e
} finally {
    console.log("finally");
}
console.log("after try")

