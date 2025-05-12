var outter = [];
function clouseTest () {
    var array = ["one", "two", "three", "four"];
    for(var i = 0; i < array.length;i++){
       var x = {};
       x.no = i;
       x.text = array[i];
       x.invoke = function(){
           console.log(i);
       }
       outter.push(x);
    }
}

clouseTest();

console.log(outter[0].invoke());
console.log(outter[1].invoke());
console.log(outter[2].invoke());
console.log(outter[3].invoke());