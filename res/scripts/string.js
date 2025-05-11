function StringBuffer() {
  this._strings = new Array();
}
new StringBuffer();
StringBuffer.prototype.append = function (str) {
  this._strings.push(str);
};
StringBuffer.prototype.toString = function () {
 return this._strings.join(",");
};
function aa() {
    var str = "";
    var sbf = new StringBuffer();
    for(var i = 0; i < 20; i++){
     sbf.append("$i");
    }
  str = sbf.toString();
  println(str);
}
aa();