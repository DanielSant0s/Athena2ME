var name = "roroks";
var a = { name: "rocks", echo: function() { alert(this.name); } };
var b = a.echo;
a.echo();
b();
