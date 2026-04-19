package net.cnjm.j2me.tinybro;

/**
 * Math helpers missing from CLDC 1.1 {@link Math} (no {@code pow(double,double)},
 * {@code atan}, {@code atan2}, {@code exp}, {@code log} on some profiles).
 * Uses small series approximations; adequate for games and scripting.
 */
final class CldcMath {

    private CldcMath() {}

    static final double E_CONST = 2.718281828459045;
    static final double LN2 = 0.6931471805599453;

    static double atan(double x) {
        boolean neg = x < 0;
        if (neg) x = -x;
        boolean inv = x > 1;
        if (inv) x = 1.0 / x;
        double x2 = x * x;
        double r = x * (1.0 - x2 * (1.0 / 3.0 - x2 * (1.0 / 5.0 - x2 * (1.0 / 7.0 - x2 * (1.0 / 9.0)))));
        if (inv) r = Math.PI / 2.0 - r;
        return neg ? -r : r;
    }

    static double atan2(double y, double x) {
        if (x > 0) return atan(y / x);
        if (x < 0 && y >= 0) return atan(y / x) + Math.PI;
        if (x < 0 && y < 0) return atan(y / x) - Math.PI;
        if (x == 0 && y > 0) return Math.PI / 2.0;
        if (x == 0 && y < 0) return -Math.PI / 2.0;
        return 0;
    }

    static double exp(double x) {
        int n = (int) (x < 0 ? x - 0.5 : x + 0.5);
        double r = x - n;
        double term = 1, sum = 1;
        for (int i = 1; i < 12; i++) {
            term *= r / i;
            sum += term;
        }
        double eN = 1;
        if (n >= 0) {
            for (int i = 0; i < n; i++) eN *= E_CONST;
        } else {
            for (int i = 0; i < -n; i++) eN /= E_CONST;
        }
        return eN * sum;
    }

    static double log(double x) {
        int k = 0;
        while (x >= 2.0) {
            x /= 2.0;
            k++;
        }
        while (x < 1.0) {
            x *= 2.0;
            k--;
        }
        double u = x - 1.0;
        double term = u, sum = 0;
        for (int i = 1; i < 25; i++) {
            sum += (i & 1) == 1 ? term / i : -term / i;
            term *= u;
        }
        return k * LN2 + sum;
    }

    /**
     * {@code Math.pow} semantics for typical script/game use (CLDC has no
     * {@code Math.pow(double,double)}).
     */
    static double pow(double b, double e) {
        if (e == 0) return 1.0;
        if (b != b) return Double.NaN;
        if (e != e) return Double.NaN;

        if (b == 0.0) {
            if (e < 0) return Double.POSITIVE_INFINITY;
            if (e > 0) {
                long bits = Double.doubleToLongBits(b);
                if (bits < 0) {
                    double ef = Math.floor(e);
                    if (ef == e && (((long) ef) & 1L) != 0) return -0.0;
                }
            }
            return 0.0;
        }

        if (b == Double.POSITIVE_INFINITY) return e > 0 ? Double.POSITIVE_INFINITY : 0.0;
        if (b == Double.NEGATIVE_INFINITY) {
            double ef = Math.floor(e);
            if (ef != e) return Double.NaN;
            long ei = (long) ef;
            if (ei != ef) return Double.NaN;
            if (e > 0) return ((ei & 1L) != 0) ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            return 0.0;
        }

        if (e == Double.POSITIVE_INFINITY) {
            if (Math.abs(b) > 1) return Double.POSITIVE_INFINITY;
            if (Math.abs(b) < 1) return 0.0;
            return 1.0;
        }
        if (e == Double.NEGATIVE_INFINITY) {
            if (Math.abs(b) > 1) return 0.0;
            if (Math.abs(b) < 1) return Double.POSITIVE_INFINITY;
            return 1.0;
        }

        if (b < 0) {
            double ef = Math.floor(e);
            if (ef != e) return Double.NaN;
            long ei = (long) ef;
            double p = pow(-b, e);
            return ((ei & 1L) != 0) ? -p : p;
        }

        return exp(e * log(b));
    }
}
