var x = 10
var y = 10

var dir_x = 1
var dir_y = 0

var cur_time = 0

var cross_img = Screen.loadImage("/cross.png");
var circle_img = Screen.loadImage("/circle.png");
var square_img = Screen.loadImage("/square.png");
var triangle_img = Screen.loadImage("/triangle.png");

while(true) {
    if (Date.now() > cur_time) {
        cur_time = Date.now() + 250

        Screen.clear(0x8000FF)
        Screen.drawText("Hello from Athena2ME!", 15, 15, 0, 0xFFFFFF)

        Screen.drawImage(cross_img, 5, 5)
        Screen.drawImage(circle_img, 42, 5)
        Screen.drawImage(square_img, 79, 5)
        Screen.drawImage(triangle_img, 116, 5)

        Screen.drawRect(x, y, 15, 15, 0xFFFFFF)

        if (dir_x == 1) {
            x += 15
        } else if (dir_x == 2) {  
            x -= 15
        } 

        if (dir_y == 1) {
            y += 15
        } else if (dir_y == 2) {  
            y -= 15
        } 

        if (x >= (Screen.width-45) && dir_x == 1 && dir_y == 0) {
            dir_x = 0
            dir_y = 1
        } else if (y >= (Screen.height-45) && dir_x == 0 && dir_y == 1) {
            dir_x = 2
            dir_y = 0
        } else if (x <= 15 && dir_x == 2 && dir_y == 0) {
            dir_x = 0
            dir_y = 2
        } else if (y <= 15 && dir_x == 0 && dir_y == 2) {
            dir_x = 1
            dir_y = 0
        }

        Screen.update()
    }
}