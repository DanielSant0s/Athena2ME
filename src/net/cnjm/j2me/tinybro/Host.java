package net.cnjm.j2me.tinybro;

public interface Host {
    
    /**
     * "http://www.domain.com/a.png": download from web
     * "a.png": load a local image
     * "style.css": load a local css style
     * "rgbImage@20,20,aarrggbb": create a semi-transparent image
     * @param name
     * @return css: String, image: Image object
     */
    public Object getResource(String name);
    
    /**
     * returned value is used by "onsubmit" event
     * @param src
     * @param eventId
     * @return 
     */
    public boolean handleEvent(Node src, int eventId);

}
