function Rect(w, h) {
    this.width = w;
    this.height = h;
    this.area = function() { return this.width * this.height }
    this.echo = function() { console.log("width=$this.width, height=$this.height, area=${this.area()}") }
    this.volumn = function(length) { console.log("for cube ($this.width,$this.height,$length), volumn=${this.area() * length}") }
    return
}

var r = new Rect(20, 10)
r.echo();
r.echo.call({ width: 15, height: 25, area: function() {return "Not implemented!"} });
new Rect(4, 5).volumn(6);
r.volumn.apply(new Rect(30, 10), [15])