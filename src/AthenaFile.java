import java.io.*;
import java.util.*;

import javax.microedition.io.*;
import javax.microedition.io.file.*;

public class AthenaFile {
    private FileConnection fc = null;
    private InputStream is = null;
    private OutputStream os = null;
    private int pos = 0;
    private int size = 0;
    private int flags = 0;
    private boolean isEOF = false;

    private static final int NUM_DESCRIPTORS = 16;

    private static AthenaFile descriptors[] = new AthenaFile[NUM_DESCRIPTORS];

    public static final int O_RDONLY = 0x0;
    public static final int O_WRONLY = 0x1;
    public static final int O_RDWR = 0x2;
    public static final int O_NDELAY = 0x4;
    public static final int O_APPEND = 0x10;
    public static final int O_CREAT = 0x400;
    public static final int O_TRUNC = 0x1000;
    public static final int O_EXCL = 0x2000;

    public static final int SEEK_SET = 0;
    public static final int SEEK_CUR = 1;
    public static final int SEEK_END = 2;

    public static final String MODE_READ = "r"; 
    public static final String MODE_WRITE = "w";      
    public static final String MODE_APPEND = "a";     
    public static final String MODE_READ_PLUS = "r+"; 
    public static final String MODE_WRITE_PLUS = "w+";
    public static final String MODE_APPEND_PLUS = "a+";

    public AthenaFile(FileConnection conn, int flag) {
        fc = conn;
        flags = flag;
        
        try {
            if (fc.exists()) {
                size = (int)fc.fileSize();
            }

            if ((flags & O_TRUNC) != 0 && fc.exists()) {
                fc.truncate(0);
                size = 0;
            }

            initializeStreams();
            
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public static AthenaFile fopen(String filename, String mode) {
        int flags = 0;
        
        if (mode.equals(MODE_READ)) {
            flags = O_RDONLY;
        } else if (mode.equals(MODE_WRITE)) {
            flags = O_WRONLY | O_CREAT | O_TRUNC;
        } else if (mode.equals(MODE_APPEND)) {
            flags = O_WRONLY | O_CREAT | O_APPEND;
        } else if (mode.equals(MODE_READ_PLUS)) {
            flags = O_RDWR;
        } else if (mode.equals(MODE_WRITE_PLUS)) {
            flags = O_RDWR | O_CREAT | O_TRUNC;
        } else if (mode.equals(MODE_APPEND_PLUS)) {
            flags = O_RDWR | O_CREAT | O_APPEND;
        } else {
            return null; 
        }
        
        try {
            FileConnection conn = (FileConnection)Connector.open(filename);
            
            if (!conn.exists() && (flags & O_CREAT) == 0) {
                conn.close();
                return null;
            }
            
            if (!conn.exists() && (flags & O_CREAT) != 0) {
                conn.create();
            }
            
            if (conn.exists() && (flags & O_TRUNC) != 0) {
                conn.truncate(0);
            }
            
            return new AthenaFile(conn, flags);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return null;
        }
    }

    public int fread(byte[] buffer, int size, int count) {
        if ((flags & O_RDONLY) == 0 && (flags & O_RDWR) == 0) {
            return -1; 
        }
        
        if (isEOF) {
            return 0; 
        }
        
        try {
            if (is == null) {
                is = fc.openInputStream();
            }
            
            is.close();
            is = fc.openInputStream();
            
            long skipped = 0;
            while (skipped < pos) {
                long s = is.skip(pos - skipped);
                if (s <= 0) {
                    break;
                }
                skipped += s;
            }
            
            int bytesToRead = size * count;
            int bytesRead = is.read(buffer, 0, bytesToRead);
            
            if (bytesRead > 0) {
                pos += bytesRead;
            }
            
            if (bytesRead < bytesToRead) {
                isEOF = true;
            }
            
            return bytesRead / size; 
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return -1;
        }
    }

    public int fwrite(byte[] buffer, int size, int count) {
        if ((flags & O_WRONLY) == 0 && (flags & O_RDWR) == 0) {
            return -1; 
        }
        
        try {
            if (os == null) {
                if ((flags & O_APPEND) != 0) {
                    os = fc.openOutputStream(this.size);
                    pos = this.size;
                } else {
                    os = fc.openOutputStream();
                }
            }
            
            if ((flags & O_APPEND) != 0) {
                os.close();
                os = fc.openOutputStream(this.size);
                pos = this.size;
            } 
            else if (pos != this.size) {
                if (os != null) {
                    os.close();
                }
                if (is != null) {
                    is.close();
                }
                
                InputStream tempIn = fc.openInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] tempBuffer = new byte[1024];
                int len;
                
                while ((len = tempIn.read(tempBuffer)) > 0) {
                    baos.write(tempBuffer, 0, len);
                }
                
                tempIn.close();
                byte[] fileContent = baos.toByteArray();
                
                fc.truncate(0);
                os = fc.openOutputStream();
                
                if (pos > 0) {
                    os.write(fileContent, 0, Math.min(pos, fileContent.length));
                }
            }
            
            int bytesToWrite = size * count;
            os.write(buffer, 0, bytesToWrite);
            os.flush();
            
            pos += bytesToWrite;
            if (pos > this.size) {
                this.size = pos;
            }
            
            return count; 
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return -1;
        }
    }

    public int fseek(int offset, int whence) {
        int newPos;
        
        switch (whence) {
            case SEEK_SET:
                newPos = offset;
                break;
            case SEEK_CUR:
                newPos = pos + offset;
                break;
            case SEEK_END:
                newPos = size + offset;
                break;
            default:
                return -1;
        }
        
        if (newPos < 0) {
            return -1;
        }

        isEOF = false;
        pos = newPos;
        
        try {
            if (is != null) {
                is.close();
                is = null;
            }
            if (os != null) {
                os.close();
                os = null;
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        
        return 0; 
    }

    public int ftell() {
        return pos;
    }

    public int fclose() {
        try {
            closeStreams();
            fc.close();
            return 0;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return -1;
        }
    }

    public int fileno() {
        for (int i = 0; i < NUM_DESCRIPTORS; i++) {
            if (descriptors[i] == this) {
                return i;
            }
        }

        for (int i = 0; i < NUM_DESCRIPTORS; i++) {
            if (descriptors[i] == null) {
                descriptors[i] = this;
                return i;
            }
        }
        
        return -1;
    }

    public int fprintf(String format, Object[] args) {
        if ((flags & O_WRONLY) == 0 && (flags & O_RDWR) == 0) {
            return -1; 
        }
        
        try {
            String text;
            
            StringBuffer sb = new StringBuffer();
            int argIndex = 0;
            
            for (int i = 0; i < format.length(); i++) {
                char c = format.charAt(i);
                if (c == '%' && i < format.length() - 1) {
                    char next = format.charAt(++i);
                    if (next == 'd' && argIndex < args.length) {
                        sb.append(args[argIndex++].toString());
                    } else if (next == 's' && argIndex < args.length) {
                        sb.append(args[argIndex++].toString());
                    } else if (next == 'f' && argIndex < args.length) {
                        sb.append(args[argIndex++].toString());
                    } else if (next == '%') {
                        sb.append('%');
                    } else {
                        sb.append('%').append(next);
                    }
                } else {
                    sb.append(c);
                }
            }

            text = sb.toString();
            
            byte[] buffer = text.getBytes();
            return fwrite(buffer, 1, buffer.length);
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public int getline(byte[] lineBuffer, int maxLength) {
        if ((flags & O_RDONLY) == 0 && (flags & O_RDWR) == 0) {
            return -1; 
        }
        
        if (isEOF) {
            return -1; 
        }
        
        try {
            if (is == null) {
                is = fc.openInputStream();
            }
            
            is.close();
            is = fc.openInputStream();
            
            long skipped = 0;
            while (skipped < pos) {
                long s = is.skip(pos - skipped);
                if (s <= 0) {
                    break;
                }
                skipped += s;
            }
            
            int bytesRead = 0;
            boolean foundEOL = false;
            
            for (int i = 0; i < maxLength - 1; i++) {
                int b = is.read();
                if (b == -1) {
                    isEOF = true;
                    break;
                }
                
                bytesRead++;
                lineBuffer[i] = (byte)b;
                
                if (b == '\n') {
                    foundEOL = true;
                    break;
                }
            }
            
            pos += bytesRead;
            return bytesRead;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return -1;
        }
    }
    
    private void initializeStreams() throws IOException {
        closeStreams();

        if ((flags & O_RDONLY) != 0 || (flags & O_RDWR) != 0) {
            is = fc.openInputStream();
        }
        
        if ((flags & O_WRONLY) != 0 || (flags & O_RDWR) != 0) {
            if ((flags & O_APPEND) != 0) {
                os = fc.openOutputStream(size); 
                pos = size;
            } else {
                os = fc.openOutputStream();
            }
        }
    }
    
    private void closeStreams() throws IOException {
        if (is != null) {
            is.close();
            is = null;
        }
        if (os != null) {
            os.close();
            os = null;
        }
    }

    static public int open(String name, int flags) {
        FileConnection conn = null;
        try {
            conn = (FileConnection)Connector.open(name);
            if (!conn.exists() && (flags & O_CREAT) == 0) {
                conn.close();
                return -1;
            }

            if (!conn.exists() && (flags & O_CREAT) != 0) {
                conn.create();
            }

            if (conn.exists() && (flags & O_CREAT) != 0 && (flags & O_EXCL) != 0) {
                conn.close();
                return -1;
            }
            
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return -1;
        }

        for (int i = 0; i < NUM_DESCRIPTORS; i++) {
            if (descriptors[i] == null) {
                descriptors[i] = new AthenaFile(conn, flags);
                return i;
            }
        }

        try {
            conn.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return -1;
    }

    static public void close(int fd) {
        if (fd < 0 || fd >= NUM_DESCRIPTORS || descriptors[fd] == null) {
            return;
        }
        
        try {
            descriptors[fd].closeStreams();
            descriptors[fd].fc.close();
        } catch (IOException ioe) {
            System.out.println("Error in close: " + ioe.getMessage());
        } finally {
            descriptors[fd] = null;
        }
    }

    static public int read(int fd, byte[] buffer, int count) {
        if (fd < 0 || fd >= NUM_DESCRIPTORS || descriptors[fd] == null) {
            return -1;
        }
        
        AthenaFile file = descriptors[fd];

        if ((file.flags & O_RDONLY) == 0 && (file.flags & O_RDWR) == 0) {
            return -1;
        }

        try {
            if (file.is == null) {
                file.is = file.fc.openInputStream();
            }

            file.is.close();
            file.is = file.fc.openInputStream();

            long skipped = 0;
            while (skipped < file.pos) {
                long s = file.is.skip(file.pos - skipped);
                if (s <= 0) {
                    break; 
                }
                skipped += s;
            }

            int bytesRead = file.is.read(buffer, 0, count);

            if (bytesRead > 0) {
                file.pos += bytesRead;
            }
            
            return bytesRead;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return -1;
        }
    }

    static public int write(int fd, byte[] buffer, int count) {
        if (fd < 0 || fd >= NUM_DESCRIPTORS || descriptors[fd] == null) {
            return -1;
        }
        
        AthenaFile file = descriptors[fd];

        if ((file.flags & O_WRONLY) == 0 && (file.flags & O_RDWR) == 0) {
            return -1;
        }
        
        try {
            if (file.os == null) {
                if ((file.flags & O_APPEND) != 0) {
                    file.os = file.fc.openOutputStream(file.size);
                    file.pos = file.size;
                } else {
                    file.os = file.fc.openOutputStream();
                }
            }

            if ((file.flags & O_APPEND) != 0) {
                file.os.close();
                file.os = file.fc.openOutputStream(file.size);
                file.pos = file.size;
            } 

            else if (file.pos != file.size) {
                if (file.os != null) {
                    file.os.close();
                }
                if (file.is != null) {
                    file.is.close();
                }

                InputStream tempIn = file.fc.openInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] tempBuffer = new byte[1024];
                int len;
                
                while ((len = tempIn.read(tempBuffer)) > 0) {
                    baos.write(tempBuffer, 0, len);
                }
                
                tempIn.close();
                byte[] fileContent = baos.toByteArray();

                file.fc.truncate(0);
                file.os = file.fc.openOutputStream();

                if (file.pos > 0) {
                    file.os.write(fileContent, 0, Math.min(file.pos, fileContent.length));
                }
            }
            
            file.os.write(buffer, 0, count);
            file.os.flush();

            file.pos += count;
            if (file.pos > file.size) {
                file.size = file.pos;
            }

            if ((file.flags & O_RDWR) != 0 && file.is == null) {
                file.is = file.fc.openInputStream();
            }
            
            return count;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return -1;
        }
    }

    static public int seek(int fd, int offset, int whence) {
        if (fd < 0 || fd >= NUM_DESCRIPTORS || descriptors[fd] == null) {
            return -1;
        }
        
        AthenaFile file = descriptors[fd];
        int newPos;

        switch (whence) {
            case SEEK_SET: 
                newPos = offset;
                break;
            case SEEK_CUR: 
                newPos = file.pos + offset;
                break;
            case SEEK_END: 
                newPos = file.size + offset;
                break;
            default:
                return -1;
        }

        if (newPos < 0) {
            return -1;
        }

        file.pos = newPos;

        try {
            if (file.is != null) {
                file.is.close();
                file.is = null;
            }
            if (file.os != null) {
                file.os.close();
                file.os = null;
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        
        return file.pos;
    }

    static public long[] stat(int fd) {
        if (fd < 0 || fd >= NUM_DESCRIPTORS || descriptors[fd] == null) {
            return null;
        }
        
        AthenaFile file = descriptors[fd];
        
        try {
            long[] stats = new long[3];
            stats[0] = file.fc.fileSize();
            stats[1] = file.fc.isDirectory() ? 1 : 0;
            stats[2] = file.fc.lastModified();
            return stats;
        } catch (IOException ioe) {
           ioe.printStackTrace();
            return null;
        }
    }
}