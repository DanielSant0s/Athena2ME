package net.cnjm.j2me.tinybro;

import java.util.*;

import javax.microedition.lcdui.*;

import net.cnjm.j2me.util.Pack;

public class Page {
    
    public Host host;
    
    public Node dom; // root of DOM
    
    public String title;
    
    public Hashtable nameNodes; // nodeId -> Node
    
    public int width, height; // size of view port
    
    public int scrollPos;
    
    /**
     * rect = { int[] { x, y, width, height }, node }
     */
    Pack rects; // 
    
    Pack focusRects; // indices to rects
    
//    static Frame dummyFrame = new Frame("");
    
    public void keyPressed(int keyCode, int gameAction) {
        
    }
    
    public void keyReleased(int keyCode, int gameAction) {
        
    }
    
    /**
     * x, y must be the value relative to browser windows' top-left corner, not the canvas top-left corner.
     * @param x
     * @param y
     */
    public void pointerPressed(int x, int y) {
        
    }
    
    public void pointerReleased(int x, int y) {
        
    }
    
    public void pointerDragged(int x, int y) {
        
    }
    
    public static Page createPage(Host host, String src, int width, int height) {
        Page ret = new Page();
        ret.host = host;
        ret.dom = new Parser(src).html(ret);
        ret.width = width;
        ret.height = height;
        ret.calcStyle();
        return ret;
    }

    public final void calcStyle() {
        Node parent = new Node(this, "div");
        parent.children = new Pack(-1, 1).add(dom);
        Node container = parent;
        container.drawinfo[C.DI_FIXED] = new Pack(22, -1).setSize(22, -1).set(C.F_WIDTH, width);
        container.drawinfo[C.DI_TMP] = new Pack(C.TMP_SIZE, -1).setSize(C.TMP_SIZE, -1);
        int[] contblock = container.drawinfo[C.DI_FIXED].iArray;
        int[] conttmp = container.drawinfo[C.DI_TMP].iArray;
        Pack rowelem = new Pack(20, 10);
        Pack stack = new Pack(20, 20);
        Pack contstack = new Pack(-1, 10);
        int idx = 0, size = parent.children.oSize;
        int indent = 0; // TODO delete this
        Object[] siblings = parent.children.oArray;
        for (;; idx++) {
            if (idx >= size) {
                if (stack.iSize == 0) break;
                if ((container.display & C.M_DISPLAY) > C.DISP_INLINE) { // block, inline-block
                    int[] cba = contblock;
                    container = (Node) contstack.removeObject(contstack.oSize - 1);
                    contblock = container.drawinfo[C.DI_FIXED].iArray;
                    conttmp = container.drawinfo[C.DI_TMP].iArray;
                    conttmp[C.TMP_OFFSETX] += cba[C.F_WIDTH] + cba[C.F_ML] + cba[C.F_MR];
                }
                idx = stack.removeInt(stack.iSize - 1);
                parent = (Node) stack.removeObject(stack.oSize - 1);
                size = parent.children.oSize;
                siblings = parent.children.oArray;
                indent -= 2; // TODO delete this
                continue;
            }
            Node node = (Node) siblings[idx];
            int disp = node.display & C.M_DISPLAY;
            for (int i = 4; --i > 0;) { // loop in 1..3
                Pack dic;
                if ((dic = node.drawinfo[i]) == null) continue;
                int[] ia = dic.iArray;
                Object[] oa = dic.oArray;
                if (oa[C.C_IMGURL] != null) {
                    Image im = (Image) host.getResource((String) oa[C.C_IMGURL]);
                    oa[C.C_IMGOBJ] = im;
                }
//                Font fo = new Font("SimSun", ia[C.C_FONTSTYLE], ia[C.C_FONTSIZE]);
                Font fo = Font.getFont(Font.FACE_SYSTEM, ia[C.C_FONTSTYLE], ia[C.C_FONTSIZE]);
                oa[C.C_FONTOBJ] = fo;
                ia[C.C_FONTH] = fo.getHeight();
            }

//            ParserTest.dumpNode(node, indent); // TODO delete this

            // process current node
            if (disp == 0) { // display = none
                if (node.tagType == C.E_BR) {
                    // TODO calc row elements
                    rowEnds(container, rowelem);
                }
                continue;
            }
            int[] block = null;
            if (node.tagType == C.E_T) {
                Pack dif = node.drawinfo[C.DI_FIXED];
                Node par = node.parent;
                Pack dicInh;
                dicInh = par.drawinfo[par.state]; // direct parent
                for (; dicInh == null; par = par.parent) {
                    dicInh = par.drawinfo[par.state];
                }
                int[] dica = dicInh.iArray;
                if (dicInh.oArray[C.C_FONTOBJ] == null) {
//                    Font f = new Font("SimSun", dica[C.C_FONTSTYLE], dica[C.C_FONTSIZE]);
                    Font f = Font.getFont(Font.FACE_SYSTEM, dica[C.C_FONTSTYLE], dica[C.C_FONTSIZE]);
                    dicInh.oArray[C.C_FONTOBJ] = f;
//                    FontMetrics fm = dummyFrame.getFontMetrics(f);
                    dica[C.C_FONTH] = f.getHeight();
                    dica[C.C_FONTWX1] = f.charWidth('x');
                    dica[C.C_FONTWX2] = f.charWidth('X');
                    //dica[C.C_FONTWHZ] = f.charWidth('жа');
                }
                String s = node.getProperty("t");
                Font fo = (Font) dicInh.oArray[C.C_FONTOBJ];
                char[] cc = s.toCharArray();
                int contw = contblock[C.F_WIDTH];
                int strw = fo.charsWidth(cc, 0, cc.length);
                dif.set(0, conttmp[C.TMP_ROWIDX]).set(1, conttmp[C.TMP_OFFSETX]).set(3, strw).set(4, fo.getHeight())
                        .set(C.T_SRCIDX, 0).set(C.T_SRCLEN, cc.length);
                rowelem.add(dif).add(0).add(strw); // TODO ?
                conttmp[C.TMP_OFFSETX] += strw;
//                int estn = 8;
//                for (int off = curoffx, i1 = 0, i2 = cc.length < estn ? cc.length : estn;;) {
//                    int maxw = contw - off;
//                    int strw = fo.charsWidth(cc, i1, i2 - i1);
//                    if (strw < maxw)
//                }
            } else if (disp > 0x10) { // block | inline-block
                if (disp == 0x20) { // block
                    rowEnds(container, rowelem);
                }
                block = node.drawinfo[C.DI_FIXED].iArray;
                Pack dic = node.drawinfo[node.state];
                if (dic == null) dic = Node.DEF_DIC;
                int[] dica = dic.iArray;
                int w = block[C.F_WIDTH], contw = contblock[C.F_WIDTH];
                int mask = w & ~C.M;
                w = w & C.M;
                if (mask == C.M_PERC) {
                    w = contw * w / 100;
                } else if (mask == 0) { // enumeration, "undefined" or "auto"
                    w = autoWidth(node, contw);
                }
                if (w > contw) w = contw;
                block[C.F_WIDTH] = w;
                if (conttmp[C.TMP_OFFSETX] + w > contw || disp == 0x20) {
                    rowEnds(container, rowelem);
                }
                block[C.F_ROWIDX] = conttmp[C.TMP_ROWIDX];
            } else { // inline
                // do nothing?
            }

            Pack children;
            int childrensize;
            if ((children = node.children) != null && 
                    (childrensize = children.oSize) > 0) {
                stack.add(parent).add(idx);
                parent = node;
                idx = -1;
                size = childrensize;
                siblings = children.oArray;
                if (disp > 0x10) { // block, inline-block
                    conttmp[C.TMP_CONT_LEFT] += block[C.F_OFFSETX] + block[C.F_ML] + block[C.F_BL] + block[C.F_PL];
                    conttmp[C.TMP_CONT_TOP] += block[C.F_OFFSETY] + block[C.F_MT] + block[C.F_BT] + block[C.F_PT];
                    contstack.add(container);
                    container = node;
                    contblock = block;
                    container.drawinfo[C.DI_FIXED].add(0); // rowoffset0
                    container.drawinfo[C.DI_TMP] = new Pack(C.TMP_SIZE, -1);
                    conttmp = node.drawinfo[C.DI_TMP].iArray;
                }
                indent += 2; // TODO delete this
            }
        }
    }
    
    private static final void rowEnds(Node container, Pack rowelem) {
        // TODO
        
        rowelem.setSize(0, 0);
    }
    
    private static final int autoWidth(Node n, int contwidth) {
        int ttype = n.tagType;
        if (ttype == C.E_INPUT) {
            String s = n.getProperty("type");
            ttype = s != null ? Parser.stringToEnum(s) : C.E_TEXT;
        }
        int ret = contwidth;
        switch (ttype) {
        case C.E_TEXT:
        case C.E_PASSWORD:
        case C.E_SELECT:
            ret = contwidth / 2;
            break;
        case C.E_BUTTON:
        case C.E_SUBMIT:
        case C.E_RESET:
            String v = n.getProperty("value");
            if (v == null) v = "  ";
            Font fo = (Font) n.drawinfo[C.DI_NORMAL].oArray[C.C_FONTOBJ];
            ret = fo.stringWidth(v) + 4;
            break;
        case C.E_CHECKBOX:
        case C.E_RADIO:
            ret = 10;
            break;
        case C.E_TEXTAREA:
            ret = contwidth - 4;
            break;
        case C.E_IMG:
            Image im = (Image) ((Page) n.owner).host.getResource(n.getProperty("src"));
//            ret = im.getWidth(dummyFrame);
            ret = im.getWidth();
            break;
        }
        return ret | C.M_PX;
    }
    
    public void paint(Graphics g, int x, int y) {
//      g.setClip(x, y, width, height);
      Node parent = new Node(this, C.E_UNDEFINED);
      parent.children = new Pack(-1, 1).add(dom);
      Pack contblock = new Pack(22, -1);
      int contleft, conttop;
      contleft = conttop = 0;
      
      Pack stack = new Pack(20, 20);
      Pack contstack = new Pack(20, 10);
      int idx = 0, size = parent.children.oSize;
      int indent = 0; // TODO delete this
      Object[] siblings = parent.children.oArray;
      for (;; idx++) {
          if (idx >= size) {
              if (stack.iSize == 0) break;
              idx = stack.removeInt(stack.iSize - 1);
              parent = (Node) stack.removeObject(stack.oSize - 1);
              size = parent.children.oSize;
              siblings = parent.children.oArray;
              if ((parent.display & C.M_DISPLAY) > C.DISP_INLINE) {
                  contblock = (Pack) contstack.removeObject(contstack.oSize - 1);
                  conttop = contstack.removeInt(contstack.iSize - 1);
                  contleft = contstack.removeInt(contstack.iSize - 1);
              }
              indent -= 2; // TODO delete this
              continue;
          }
          Node node = (Node) siblings[idx];

//          ParserTest.dumpNode(node, indent); // TODO delete this

          // process current node
          if ((node.display & C.M_DISPLAY) == 0) { // display = none
              continue;
          }
          Pack block = null;
          int[] ba = null;
          if (node.tagType == C.E_T) {
              Pack dif = node.drawinfo[0];
              int[] t = dif.iArray;
              Node par = node.parent;
              Pack dicInh, dicDir;
              dicInh = dicDir = par.drawinfo[par.state]; // direct parent
              if (dicDir == null) dicDir = Node.DEF_DIC;
              for (; dicInh == null; par = par.parent) {
                  dicInh = par.drawinfo[par.state];
              }
              String s = node.getProperty("t");
              for (int i = 0, tlen = dif.iSize; i < tlen; i += 7) {
                  int rowIdx = t[i];
                  int yoff = conttop + contblock.iArray[C.F_ROWOFFSET + rowIdx];
                  // TODO draw border, background with dicDir
                  
//                  g.setColor(new Color(dicInh.iArray[C.C_COLOR & C.M]));
                  g.setColor(dicInh.iArray[C.C_COLOR & C.M]);
                  int srcidx;
                  // TODO draw text shadow
                  g.drawString(s.substring(srcidx = t[i + C.T_SRCIDX], srcidx + t[i + C.T_SRCLEN]), 
                          contleft + t[i + C.F_OFFSETX], yoff + t[i + C.F_OFFSETY] + t[C.C_FONTH], Graphics.BASELINE);
              }
          } else if ((node.display & C.M_DISPLAY) > 0x10) { // block | inline-block
              block = node.drawinfo[0];
              ba = block.iArray;
              Pack dic = node.drawinfo[node.state];
              if (dic == null) dic = Node.DEF_DIC;
              int[] cba = contblock.iArray;
              int[] dica = dic.iArray;

              // TODO draw background image
              int yoff = conttop + cba[C.F_ROWOFFSET + ba[C.F_ROWIDX]];
              if (dica[C.C_BGCOLOR] != C.E_TRANSPARENT) {
                  g.setColor(dica[C.C_BGCOLOR] & C.M);
                  g.fillRect(contleft + ba[C.F_OFFSETX] + ba[C.F_ML] + ba[C.F_BL], 
                          yoff + ba[C.F_OFFSETY] + ba[C.F_MT] + ba[C.F_BT], 
                          ba[C.F_WIDTH], ba[C.F_HEIGHT]);
              }
              // TODO draw border
              if (ba[C.F_BT] > 0) {
                  g.setColor(dica[C.C_BCT] & C.M);
                  g.drawRect(contleft + ba[C.F_OFFSETX] + ba[C.F_ML], 
                          yoff + ba[C.F_OFFSETY] + ba[C.F_MT], 
                          ba[C.F_WIDTH] + ba[C.F_BL] + ba[C.F_BR], 
                          ba[C.F_HEIGHT] + ba[C.F_BT] + ba[C.F_BB]);
              }
              
          } else { // inline
              // do nothing?
          }

          Pack children;
          int childrensize;
          if ((children = node.children) != null && 
                  (childrensize = children.oSize) > 0) {
              stack.add(parent).add(idx);
              parent = node;
              idx = -1;
              size = childrensize;
              siblings = children.oArray;
              if ((node.display & C.M_DISPLAY) > 0x10) { // block, inline-block
                  contleft += ba[C.F_OFFSETX] + ba[C.F_ML] + ba[C.F_BL] + ba[C.F_PL];
                  conttop += ba[C.F_OFFSETY] + ba[C.F_MT] + ba[C.F_BT] + ba[C.F_PT];
                  contstack.add(contblock).add(contleft).add(conttop);
                  contblock = block;
              }
              indent += 2; // TODO delete this
          }
      }
  }
  
}
