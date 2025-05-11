// This function adds property accessor methods for a property with
// the specified name to the object o.  The methods are named get<name>
// and set<name>.  If a predicate function is supplied, the setter
// method uses it to test its argument for validity before storing it.
// If the predicate returns false, the setter method throws an exception.
//
// The unusual thing about this function is that the property value
// that is manipulated by the getter and setter methods is not stored in
// the object o.  Instead, the value is stored only in a local variable
// in this function.  The getter and setter methods are also defined
// locally to this function and therefore have access to this local variable.
// Note that the value is private to the two accessor methods, and it cannot
// be set or modified except through the setter.
function makeProperty(o, name, predicate) {
    var value;  // This is the property value

    // The setter method simply returns the value.
    o["get" + name] = function() { return value; };

    // The getter method stores the value or throws an exception if
    // the predicate rejects the value.
    o["set" + name] = function(v) {
        if (predicate && !predicate(v))
            throw "set" + name + ": invalid value " + v;
        else
            value = v;
    };
}

// The following code demonstrates the makeProperty() method.
var o = {};  // Here is an empty object

// Add property accessor methods getName and setName()
// Ensure that only string values are allowed
makeProperty(o, "Name", function(x) { return typeof x == "string"; });
makeProperty(o, "Sex");


o.setName("Frank");  // Set the property value
println(o.getName());  // Get the property value
//o.setName(0);        // Try to set a value of the wrong type
o.setSex("male");
println(o.getSex());
println("${o.getName()} is ${o.getSex()}");

