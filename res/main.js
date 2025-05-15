var x = 10;
var y = 10;

var dir_x = 1;
var dir_y = 0;

var cur_time = 0;

var cross_img = new Image("/cross.png");
var circle_img = new Image("/circle.png");
var square_img = new Image("/square.png");
var triangle_img = new Image("/triangle.png");

var PURPLE = Color.new(80, 0, 160);
var WHITE = Color.new(255, 255, 255);

var font = new Font("default");

var running = true;

os.setExitHandler(function () {
    running = false;
});

while(running) {
    if (Date.now() > cur_time) {
        cur_time = Date.now() + 16;
        Pad.update();

        Screen.clear(PURPLE);

        font.print("Hello from Athena2ME!", 15, 15);

        cross_img.draw(5, 5);
        circle_img.draw(42, 5);
        square_img.draw(79, 5);
        triangle_img.draw(116, 5);

        Draw.rect(x, y, 15, 15, WHITE);

        if (dir_x == 1) {
            x += 2;
        } else if (dir_x == 2) {  
            x -= 2;
        } 

        if (dir_y == 1) {
            y += 2;
        } else if (dir_y == 2) {  
            y -= 2;
        } 

        if (Pad.justPressed(Pad.UP) && !(dir_x == 0 && dir_y == 2)) {
            dir_x = 0;
            dir_y = 2;
        } else if (Pad.justPressed(Pad.DOWN) && !(dir_x == 0 && dir_y == 1)) {
            dir_x = 0;
            dir_y = 1;
        } else if (Pad.justPressed(Pad.LEFT) && !(dir_x == 1 && dir_y == 0)) {
            dir_x = 2;
            dir_y = 0;
        } else if (Pad.justPressed(Pad.RIGHT) && !(dir_x == 2 && dir_y == 0)) {
            dir_x = 1;
            dir_y = 0;
        }

        Screen.update();
    }
}