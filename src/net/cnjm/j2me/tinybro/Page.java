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

    /** Binary search longest substring starting at {@code from} that fits {@code maxWidth}. */
    private static final int breakTextLine(String s, int from, Font fo, int maxWidth) {
        int n = s.length();
        if (from >= n) {
            return n;
        }
        if (maxWidth < 2) {
            return from + 1;
        }
        char[] cc = s.toCharArray();
        int lo = from + 1;
        int hi = n;
        while (lo < hi) {
            int mid = (lo + hi + 1) >> 1;
            if (fo.charsWidth(cc, from, mid - from) <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        if (lo <= from) {
            return from + 1;
        }
        return lo;
    }

    public final void calcStyle() {
        Node parent = new Node(this, "div");
        parent.children = new Pack(-1, 1).add(dom);
        Node container = parent;
        container.drawinfo[C.DI_FIXED] = new Pack(22, -1).setSize(22, -1).set(C.F_WIDTH, width);
        container.drawinfo[C.DI_TMP] = new Pack(C.TMP_SIZE, -1).setSize(C.TMP_SIZE, -1);
        Pack contblock = container.drawinfo[C.DI_FIXED];
        int[] conttmp = container.drawinfo[C.DI_TMP].iArray;
        Pack rowelem = new Pack(20, 10);
        Pack stack = new Pack(20, 20);
        Pack contstack = new Pack(-1, 10);
        int idx = 0, size = parent.children.oSize;
        Object[] siblings = parent.children.oArray;
        for (;; idx++) {
            if (idx >= size) {
                if (stack.iSize == 0) {
                    rowEnds(container, rowelem);
                    break;
                }
                if ((container.display & C.M_DISPLAY) > C.DISP_INLINE && contstack.oSize > 0) { // block, inline-block
                    rowEnds(container, rowelem);
                    rowelem.setSize(0, 0);
                    int closedDisp = container.display & C.M_DISPLAY;
                    Pack closedBlock = container.drawinfo[C.DI_FIXED];
                    int[] closedBa = closedBlock.iArray;
                    container = (Node) contstack.removeObject(contstack.oSize - 1);
                    contblock = container.drawinfo[C.DI_FIXED];
                    conttmp = container.drawinfo[C.DI_TMP].iArray;
                    if (closedDisp == 0x20) { // block: stack next row in parent
                        int closedH = Node.blockContentHeight(closedBlock);
                        if (closedH <= 1) {
                            closedH = 16;
                        }
                        if (closedH > 0) {
                            closedBlock.set(C.F_HEIGHT, closedH);
                        }
                        if (closedH > conttmp[C.TMP_ROWH]) {
                            conttmp[C.TMP_ROWH] = closedH;
                        }
                        rowEnds(container, rowelem);
                        rowelem.setSize(0, 0);
                    } else if (closedDisp == 0x30) { // inline-block: row height + horizontal cursor
                        int closedH = Node.blockContentHeight(closedBlock);
                        if (closedH > conttmp[C.TMP_ROWH]) {
                            conttmp[C.TMP_ROWH] = closedH;
                        }
                        conttmp[C.TMP_OFFSETX] += C.px(closedBa[C.F_WIDTH]) + C.px(closedBa[C.F_ML])
                                + C.px(closedBa[C.F_MR]);
                    }
                }
                idx = stack.removeInt(stack.iSize - 1);
                parent = (Node) stack.removeObject(stack.oSize - 1);
                size = parent.children.oSize;
                siblings = parent.children.oArray;
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
                Font fo = Font.getFont(Font.FACE_SYSTEM, ia[C.C_FONTSTYLE], ia[C.C_FONTSIZE]);
                if (ia[C.C_FONTFACE] == C.FACE_MONOSPACE) {
                    fo = Font.getFont(Font.FACE_MONOSPACE, ia[C.C_FONTSTYLE], ia[C.C_FONTSIZE]);
                } else if (ia[C.C_FONTFACE] == C.FACE_PROPORTIONAL) {
                    fo = Font.getFont(Font.FACE_PROPORTIONAL, ia[C.C_FONTSTYLE], ia[C.C_FONTSIZE]);
                }
                oa[C.C_FONTOBJ] = fo;
                ia[C.C_FONTH] = fo.getHeight();
            }

            // process current node
            if (disp == 0) { // display = none
                if (node.tagType == C.E_BR) {
                    rowEnds(container, rowelem);
                }
                continue;
            }
            int[] block = null;
            if (node.tagType == C.E_T) {
                Pack dif = node.drawinfo[C.DI_FIXED];
                Pack dicInh = Node.inheritedStyle(node);
                int[] dica = dicInh.iArray;
                if (dicInh.oArray[C.C_FONTOBJ] == null) {
                    int face = dica[C.C_FONTFACE];
                    Font foInit;
                    if (face == C.FACE_MONOSPACE) {
                        foInit = Font.getFont(Font.FACE_MONOSPACE, dica[C.C_FONTSTYLE], dica[C.C_FONTSIZE]);
                    } else if (face == C.FACE_PROPORTIONAL) {
                        foInit = Font.getFont(Font.FACE_PROPORTIONAL, dica[C.C_FONTSTYLE], dica[C.C_FONTSIZE]);
                    } else {
                        foInit = Font.getFont(Font.FACE_SYSTEM, dica[C.C_FONTSTYLE], dica[C.C_FONTSIZE]);
                    }
                    dicInh.oArray[C.C_FONTOBJ] = foInit;
                    dica[C.C_FONTH] = foInit.getHeight();
                    dica[C.C_FONTWX1] = foInit.charWidth('x');
                    dica[C.C_FONTWX2] = foInit.charWidth('X');
                }
                String s = node.getProperty("t");
                if (s == null || s.length() == 0) {
                    continue;
                }
                Font fo = (Font) dicInh.oArray[C.C_FONTOBJ];
                char[] cc = s.toCharArray();
                int contw = contblock.iArray[C.F_WIDTH];
                int pos = 0;
                dif.setSize(0, -1);
                while (pos < cc.length) {
                    int room = contw - conttmp[C.TMP_OFFSETX];
                    if (room < 2) {
                        rowEnds(container, rowelem);
                        room = contw - conttmp[C.TMP_OFFSETX];
                    }
                    int end = breakTextLine(s, pos, fo, room);
                    if (end <= pos) {
                        end = pos + 1;
                    }
                    int strw = fo.charsWidth(cc, pos, end - pos);
                    int oldLen = dif.iSize;
                    dif.setSize(oldLen + 7, -1);
                    dif.set(oldLen + 0, conttmp[C.TMP_ROWIDX]);
                    dif.set(oldLen + 1, conttmp[C.TMP_OFFSETX]);
                    dif.set(oldLen + 2, 0);
                    dif.set(oldLen + 3, strw);
                    dif.set(oldLen + 4, fo.getHeight());
                    dif.set(oldLen + C.T_SRCIDX, pos);
                    dif.set(oldLen + C.T_SRCLEN, end - pos);
                    rowelem.add(dif).add(0).add(strw);
                    conttmp[C.TMP_OFFSETX] += strw;
                    pos = end;
                    if (pos < cc.length) {
                        rowEnds(container, rowelem);
                    }
                }
            } else if (disp > 0x10) { // block | inline-block
                if (disp == 0x20) {
                    rowEnds(container, rowelem);
                }
                block = node.drawinfo[C.DI_FIXED].iArray;
                Pack dic = node.drawinfo[node.state];
                if (dic == null) dic = Node.DEF_DIC;
                int[] dica = dic.iArray;
                int w = block[C.F_WIDTH], contw = contblock.iArray[C.F_WIDTH];
                int mask = w & ~C.M;
                w = w & C.M;
                if (mask == C.M_PERC) {
                    w = contw * w / 100;
                } else if (mask == 0) { // enumeration, "undefined" or "auto"
                    w = autoWidth(node, contw);
                }
                if (w > contw) w = contw;
                block[C.F_WIDTH] = w;
                if (disp != 0x20 && conttmp[C.TMP_OFFSETX] + w > contw) {
                    rowEnds(container, rowelem);
                }
                block[C.F_ROWIDX] = conttmp[C.TMP_ROWIDX];
                {
                    int rowYInParent = 0;
                    int rowOffIdx = C.F_ROWOFFSET + block[C.F_ROWIDX];
                    if (rowOffIdx >= 0 && rowOffIdx < contblock.iSize) {
                        rowYInParent = C.px(contblock.iArray[rowOffIdx]);
                    }
                    block[C.F_VERTALIGN] = rowYInParent;
                }
                if (disp != 0x20) { // inline-block: record horizontal slot + row height
                    block[C.F_OFFSETX] = conttmp[C.TMP_OFFSETX];
                    int bh = C.px(block[C.F_HEIGHT]);
                    if (bh <= 0 && node.tagType == C.E_IMG) {
                        Object imo = dic.oArray[C.C_IMGOBJ];
                        if (imo instanceof Image) {
                            bh = ((Image) imo).getHeight();
                        }
                    }
                    if (bh <= 0) {
                        bh = C.px(block[C.F_WIDTH]);
                    }
                    if (bh > conttmp[C.TMP_ROWH]) {
                        conttmp[C.TMP_ROWH] = bh;
                    }
                    conttmp[C.TMP_OFFSETX] += w;
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
                if (disp > 0x10) { // block, inline-block
                    conttmp[C.TMP_CONT_LEFT] += C.px(block[C.F_OFFSETX]) + C.px(block[C.F_ML]) + C.px(block[C.F_BL]) + C.px(block[C.F_PL]);
                    conttmp[C.TMP_CONT_TOP] += C.px(block[C.F_OFFSETY]) + C.px(block[C.F_MT]) + C.px(block[C.F_BT]) + C.px(block[C.F_PT]);
                    contstack.add(container);
                    container = node;
                    container.drawinfo[C.DI_FIXED].add(0); // rowoffset0
                    contblock = container.drawinfo[C.DI_FIXED];
                    container.drawinfo[C.DI_TMP] = new Pack(C.TMP_SIZE, -1).setSize(C.TMP_SIZE, -1);
                    conttmp = container.drawinfo[C.DI_TMP].iArray;
                }
            }
        }
    }
    
    private static final void rowEnds(Node container, Pack rowelem) {
        int[] conttmp = container.drawinfo[C.DI_TMP].iArray;
        Pack fixed = container.drawinfo[C.DI_FIXED];
        int[] fa = fixed.iArray;
        int rowIdx = conttmp[C.TMP_ROWIDX];
        if (rowelem == null || rowelem.oSize == 0) {
            int dh = conttmp[C.TMP_ROWH] > 0 ? conttmp[C.TMP_ROWH] : 1;
            int next = C.F_ROWOFFSET + rowIdx + 1;
            while (fixed.iSize <= next) {
                fixed.add(0);
            }
            int prevY = fa[C.F_ROWOFFSET + rowIdx];
            fixed.set(next, prevY + dh);
            conttmp[C.TMP_ROWIDX] = rowIdx + 1;
            conttmp[C.TMP_OFFSETX] = 0;
            conttmp[C.TMP_ROWH] = 0;
            return;
        }
        int maxH = 1;
        for (int k = 0; k < rowelem.oSize; k++) {
            Pack dif = (Pack) rowelem.oArray[k];
            if (dif != null && dif.iSize > 4) {
                int hh = dif.iArray[4];
                if (hh > maxH) {
                    maxH = hh;
                }
            }
        }
        Pack cdic = container.drawinfo[container.state];
        if (cdic == null) {
            cdic = Node.DEF_DIC;
        }
        int hAlign = (cdic.iArray[C.F_TEXTALIGN] >> 4) & 0x0F;
        int slack = fa[C.F_WIDTH] - conttmp[C.TMP_OFFSETX];
        if (slack < 0) {
            slack = 0;
        }
        int shift = 0;
        if (hAlign == (C.E_CENTER & 0x0F)) {
            shift = slack >> 1;
        } else if (hAlign == (C.E_RIGHT & 0x0F)) {
            shift = slack;
        }
        for (int k = 0; k < rowelem.oSize; k++) {
            Pack dif = (Pack) rowelem.oArray[k];
            if (dif != null) {
                for (int seg = 0, n = dif.iSize; seg < n; seg += 7) {
                    if (dif.iArray[seg + C.F_ROWIDX] != rowIdx) {
                        continue;
                    }
                    int ox = dif.iArray[seg + C.F_OFFSETX];
                    dif.set(seg + C.F_OFFSETX, ox + shift);
                }
            }
        }
        int next = C.F_ROWOFFSET + rowIdx + 1;
        while (fixed.iSize <= next) {
            fixed.add(0);
        }
        int prevY = fa[C.F_ROWOFFSET + rowIdx];
        fixed.set(next, prevY + maxH);
        conttmp[C.TMP_ROWIDX] = rowIdx + 1;
        conttmp[C.TMP_OFFSETX] = 0;
        conttmp[C.TMP_ROWH] = 0;
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
              if ((parent.display & C.M_DISPLAY) > C.DISP_INLINE && contstack.oSize > 0) {
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
                          contleft + C.px(t[i + C.F_OFFSETX]), yoff + C.px(t[i + C.F_OFFSETY]) + t[C.C_FONTH], Graphics.BASELINE);
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
                  g.fillRect(contleft + C.px(ba[C.F_OFFSETX]) + C.px(ba[C.F_ML]) + C.px(ba[C.F_BL]), 
                          yoff + C.px(ba[C.F_OFFSETY]) + C.px(ba[C.F_MT]) + C.px(ba[C.F_BT]), 
                          C.px(ba[C.F_WIDTH]), C.px(ba[C.F_HEIGHT]));
              }
              // TODO draw border
              if (C.px(ba[C.F_BT]) > 0) {
                  g.setColor(dica[C.C_BCT] & C.M);
                  g.drawRect(contleft + C.px(ba[C.F_OFFSETX]) + C.px(ba[C.F_ML]), 
                          yoff + C.px(ba[C.F_OFFSETY]) + C.px(ba[C.F_MT]), 
                          C.px(ba[C.F_WIDTH]) + C.px(ba[C.F_BL]) + C.px(ba[C.F_BR]), 
                          C.px(ba[C.F_HEIGHT]) + C.px(ba[C.F_BT]) + C.px(ba[C.F_BB]));
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
                  contleft += C.px(ba[C.F_OFFSETX]) + C.px(ba[C.F_ML]) + C.px(ba[C.F_BL]) + C.px(ba[C.F_PL]);
                  conttop += C.px(ba[C.F_OFFSETY]) + C.px(ba[C.F_MT]) + C.px(ba[C.F_BT]) + C.px(ba[C.F_PT]);
                  contstack.add(contblock).add(contleft).add(conttop);
                  contblock = block;
              }
              indent += 2; // TODO delete this
          }
      }
  }
  
}
