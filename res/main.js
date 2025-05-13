var x = 10;
var y = 10;

var dir_x = 1;
var dir_y = 0;

var cur_time = 0;

var cross_img = Screen.loadImage("/cross.png");
var circle_img = Screen.loadImage("/circle.png");
var square_img = Screen.loadImage("/square.png");
var triangle_img = Screen.loadImage("/triangle.png");

var PURPLE = Color.new(80, 0, 160);
var WHITE = Color.new(255, 255, 255);

while(true) {
    if (Date.now() > cur_time) {
        cur_time = Date.now() + 16;
        Pad.update();

        Screen.clear(PURPLE);
        Screen.drawText("Hello from Athena2ME!", 15, 15, 0, WHITE);

        Screen.drawImage(cross_img, 5, 5);
        Screen.drawImage(circle_img, 42, 5);
        Screen.drawImage(square_img, 79, 5);
        Screen.drawImage(triangle_img, 116, 5);

        Screen.drawRect(x, y, 15, 15, WHITE);

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