public class AthenaColor {
    public static final int color(int r, int g, int b, int a) {
        return (b | (g << 8) | (r << 16) | (a << 24));
    }
}
