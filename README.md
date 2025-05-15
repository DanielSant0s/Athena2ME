<br />
<p align="center">
  <a href="https://github.com/DanielSant0s/Athena2ME/">
    <img src="https://github.com/DanielSant0s/AthenaEnv/assets/47725160/f507ad9b-f9a1-4000-a454-ff824bc9d70b" alt="Logo" width="100%" height="auto">
  </a>

  <p align="center">
    Enhanced JavaScript environment for J2ME Devices
    <br />
  </p>
</p>  


<details open="open">
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#about-athenaenv">About Athena2ME</a>
      <ul>
        <li><a href="#function-types">Function types</a></li>
        <li><a href="#built-with">Built With</a></li>
      </ul>
    </li>
    <li>
      <a href="#coding">Coding</a>
      <ul>
        <li><a href="#prerequisites">Prerequisites</a></li>
        <li><a href="#features">Features</a></li>
        <li><a href="#functions-classes-and-consts">Functions and classes</a></li>
      </ul>
    </li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#contact">Contact</a></li>
    <li><a href="#thanks">Thanks</a></li>
  </ol>
</details>

## About Athena2ME

Athena2ME is a project that seeks to facilitate and at the same time brings a complete kit for users to create homebrew software for Java ME mobile divices using the JavaScript language. It has dozens of built-in functions, both for creating games and apps. The main advantage over using Athena2ME project instead of Sun Wireless Toolkit or Nokia S40 SDK is above all the practicality, you will use one of the simplest possible languages to create what you have in mind, besides not having to compile, just script and test, fast and simple.

### Modules
* os: OS dependant functions in general.
* Image: Image drawing.
* Draw: Shape drawing, rectangles, triangles etc.
* Screen: The entire screen of your project, enable or disable parameters.
* Font: Functions that control the texts that appear on the screen, loading texts, drawing and unloading from memory.
* Pad: Above being able to draw and everything else, A human interface is important.
* Keyboard: Basic keypad support.
* Timer: Control the time precisely in your code, it contains several timing functions.

New types are always being added and this list can grow a lot over time, so stay tuned.

### Progress

- [x] Image basic functions
- [x] Screen basic functions
- [x] OS Font functions
- [x] Physical pad functions
- [x] Keypad functions
- [x] Timer functions
- [ ] OS external file functions
- [ ] OS platform functions
- [ ] std functions
- [ ] Thread functions
- [ ] 3D Render functions
- [ ] Network (requests, sockets, websockets)
- [ ] Archive (zip, 7zip, tar, rar) system

### Built With

* [WTK](https://www.oracle.com/java/technologies/sun-java-wireless-toolkit.html)
* [RockScript](https://code.google.com/archive/p/javascript4me/)

## Coding

In this section you will have some information about how to code using Athena2ME, from prerequisites to useful functions and information about the language.

### Prerequisites

Using Athena2ME you only need one way to code and one way to test your code, that is, if you want, you can even create your code on yout J2ME device, but I'll leave some recommendations below.

* PC: [Visual Studio Code](https://code.visualstudio.com)(with JavaScript extension) and some J2ME emulator or a real device.

* Android: [QuickEdit](https://play.google.com/store/apps/details?id=com.rhmsoft.edit&hl=pt_BR&gl=US) and some J2ME emulator or a real device.

Oh, and I also have to mention that an essential prerequisite for using Athena2ME is knowing how to code in JavaScript.

## Quick start with Athena

Hello World:  
```js
var font = new Font("default");

var running = true;

System.setExitHandler(function () {
    running = false;
});

while (running) { 
  Screen.clear(); // Clear screen for the next frame.
  font.print("Hello from Athena2ME!", 15, 15);
  Screen.update(); // Updates the screen.
}
```

## Features

Athena2ME uses a heavily modified version of the RockScript interpreter for JavaScript language, which means that it brings some basic ES5 JavaScript features so far.

### JavaScript standard functions
  
* Object  
  • toString  
  • hasOwnProperty  
  
* Function  
  • call  
  • apply   
  
* Number  
  • MAX_VALUE  
  • MIN_VALUE  
  • NaN  
  • valueOf  
  
* String  
  • fromCharCode  
  • valueOf  
  • charAt  
  • charCodeAt  
  • indexOf  
  • lastIndexOf  
  • substring  
  • split  
  
* Array  
  • concat  
  • join  
  • push  
  • pop  
  • shift  
  • unshift  
  • slice  
  • sort  
  • reverse  
  
* Date  
  • now  
  • getTime  
  • setTime  
  
* Error  
  • name  
  • message  
  • toString  
  
* Math  
  • random  
  • min  
  • max  
  
* Misc  
  • console.log  
  • isNaN  
  • parseInt  
  • eval  
  • es - evalString  
  
**How to run it**

Athena is basically a JavaScript loader, so it loads .js files inside .jar file (which is a zip file). It runs "main.js" by default.

## Functions, classes and consts

Below is the list of usable functions of Athena2ME project currently, this list is constantly being updated.

P.S.: *Italic* parameters refer to optional parameters
    
### os module
* os.setExitHandler(func) - Set *func* to be called when the device run any action to exit Athena2ME.
* os.platform - Return a string representing the platform: "j2me".

### Color module
* var col = Color.new(r, g, b, *a*) - Returns a color object from the specified RGB(A) parameters.

### Image Module  

Construction:  

* var image = new Image(path);  
  path - Path to the file, E.g.: "/test.png".  

```js
var test = new Image("/owl.png"); 
``` 

Properties:

* width, height - Image drawing size, default value is the original image size.
* startx, starty - Beginning of the area that will be drawn from the image, the default value is 0.0.
* endx, endy - End of the area that will be drawn from the image, the default value is the original image size.

Methods:

* draw(x, y) - Draw loaded image onscreen(call it every frame). Example: image.draw(15.0, 100.0);
* free() - Free content immediately. 
  
### Draw module
* Draw.rect(x, y, width, height, color) - Draws a rectangle on the specified color, position and size on the screen.
* Draw.line(x, y, x2, y2, color) - Draws a line on the specified colors and position on the screen.
* Draw.triangle(x, y, x2, y2, x3, y3, color) - Draws a triangle on the specified points positions and colors on the screen.
  
### Screen module
* Screen.clear(*color*) - Clears screen with the specified color. If you don't specify any argument, it will use black as default.  
* Screen.update() - Run the render queue and jump to the next frame, i.e.: Updates your screen.  

### Font module

**Constants:**

*Faces:*  
* Font.FACE_MONOSPACE
* Font.FACE_PROPORTIONAL
* Font.FACE_SYSTEM  
  
*Styles (P.S.: Styles can be combined, excepting STYLE_PLAIN):*  
* Font.STYLE_PLAIN
* Font.STYLE_BOLD
* Font.STYLE_ITALIC
* Font.STYLE_UNDERLINED  
  
*Sizes:*  
* Font.SIZE_SMALL
* Font.SIZE_MEDIUM
* Font.SIZE_LARGE  
  
Construction:  

```js
var osdfnt = new Font("default");  //Load default font
var font = new Font(Font.FACE_MONOSPACE, Font.STYLE_ITALIC, Font.SIZE_MEDIUM); //Load a custom variant font. Arguments: face, style, size (style and size are optional)
``` 

Properties:
* color - Define font tinting, default value is Color.new(255, 255, 255).
* align - Font alignment, default value is FontAlign.NONE. Avaliable options below:  
  • FontAlign.NONE  
  • FontAlign.TOP  
  • FontAlign.BOTTOM  
  • FontAlign.LEFT  
  • FontAlign.RIGHT  
  • FontAlign.VCENTER  
  • FontAlign.HCENTER  
  • FontAlign.CENTER  
  
Methods:
* print(x, y, text) - Draw text on screen(call it every frame). Example: font.print(10.0, 10.0, "Hello world!);
* getTextSize(text) - Returns text absolute size in pixels (width, height). Example: const size = font.getTextSize("Hello world!");
* free() - Free asset content immediately. 

### Pad module

* Buttons list:  
  • Pad.UP  
  • Pad.DOWN  
  • Pad.LEFT  
  • Pad.RIGHT  
  • Pad.FIRE  
  • Pad.GAME_A  
  • Pad.GAME_B  
  • Pad.GAME_C  
  • Pad.GAME_D  

* Pad.update() - Updates all pads pressed. 
* var fire_pressed = Pad.pressed(button) - Check if a button is being pressed (continuously). 
* var fire_pressed = Pad.justPressed(button) - Checks if a button was pressed only once.  
  
### Keyboard module
* var c = Keyboard.get() - Get keyboard current char.

### Timer module

* var timer = new Timer()  
  • get()  
  • set(value)  
  • free()  
  • pause()  
  • resume()  
  • reset()  
  • playing()  
  
## Contributing

Contributions are what make the open source community such an amazing place to be learn, inspire, and create. Any contributions you make are **greatly appreciated**.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AwesomeFeature`)
3. Commit your Changes (`git commit -m 'Add some AwesomeFeature'`)
4. Push to the Branch (`git push origin feature/AwesomeFeature`)
5. Open a Pull Request

## License

Distributed under MIT. See [LICENSE](LICENSE) for more information.

<!-- CONTACT -->
## Contact

Daniel Santos - [@danadsees](https://twitter.com/danadsees) - danielsantos346@gmail.com

Project Link: [https://github.com/DanielSant0s/Athena2ME](https://github.com/DanielSant0s/Athena2ME)





