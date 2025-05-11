var outter = [];
function clouseTest () {
    var array = ["one", "two", "three", "four"];
    for(var i = 0; i < array.length;i++){
       var x = {};
       x.no = i;
       x.text = array[i];
       x.invoke = function(){
           println(i);
       }
       outter.push(x);
    }
}

clouseTest();

println(outter[0].invoke());
println(outter[1].invoke());
println(outter[2].invoke());
println(outter[3].invoke());