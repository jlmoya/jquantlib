package org.jquantlib.math.transcendental;

/**
 * Pure-Java port of CORE-MATH's correctly-rounded {@code cr_sin} and
 * {@code cr_cos}.
 *
 * <p>Source: CORE-MATH {@code src/binary64/sin/sin.c} and
 * {@code src/binary64/cos/cos.c} (Sibidanov / Zimmermann / Hubrecht et al.,
 * Inria; MIT-licensed; canonical Inria source). Transcribed faithfully so
 * every intermediate produces the same bit-exact value as the C reference.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>IEEE-754 dispatch: NaN / ±inf / |x| < 2^-26 (sin) or 2^-27 (cos)
 *       fast-return paths.</li>
 *   <li>Fast path: Payne-Hanek argument reduction (double-double precision)
 *       + polynomial approximation in the reduced argument + S/C/SC table
 *       lookup.</li>
 *   <li>Accurate path (cold): full Payne-Hanek via {@link Dint64} 128-bit
 *       arithmetic + accurate polynomial in 128-bit precision; falls back
 *       to a small per-primitive exception table for hard-rounding cases.</li>
 * </ol>
 *
 * <p>Both {@link #cos(double)} and {@link #sin(double)} share Payne-Hanek
 * reduction, the {@link #reduceFast} primitive, the {@code SC} / {@code S}
 * / {@code C} lookup tables, and the dint-precision polynomial pipeline.
 * The two functions differ only in the quadrant→primitive mapping.
 */
final class SinCosKernel {

    private SinCosKernel() {}

    // ---------------------------------------------------------------
    //  Public entry points
    // ---------------------------------------------------------------

    static double sin(double x) {
        final long u = Double.doubleToRawLongBits(x);
        final int e = (int) ((u >>> 52) & 0x7ff);

        if (e == 0x7ff) {
            // NaN, +Inf, -Inf
            if ((u << 1) == (0x7ffL << 53)) {
                return x - x; // ±inf → NaN, raises invalid
            }
            return x + x; // NaN
        }

        // For |x| <= 0x1.7137449123ef6p-26, sin(x) rounds to x.
        final long ux = u & 0x7fffffffffffffffL;
        if (Long.compareUnsigned(ux, 0x3e57137449123ef6L) <= 0) {
            if (x == 0.0) return x;
            // Taylor: sin(x) ~ x - x^3/6 ; for |x| < 2^-26, fma(x,-2^-54,x)
            // gives the correctly-rounded result.
            return Math.fma(x, -0x1p-54, x);
        }

        final double[] hl = new double[2];
        final double err = sinFast(hl, x);
        final double h = hl[0], l = hl[1];
        final double left = h + (l - err);
        final double right = h + (l + err);
        if (left == right) return left;

        return sinAccurate(x);
    }

    static double cos(double x) {
        final long u = Double.doubleToRawLongBits(x);
        final int e = (int) ((u >>> 52) & 0x7ff);

        if (e == 0x7ff) {
            if ((u << 1) == (0x7ffL << 53)) {
                return 0.0 / 0.0; // ±inf → NaN, raises invalid
            }
            return x + x; // NaN
        }

        // For |x| <= 0x1.6a09e667f3bccp-27, cos(x) rounds to fma(|x|, -2^-28, 1).
        final long ux = u & 0x7fffffffffffffffL;
        if (Long.compareUnsigned(ux, 0x3e46a09e667f3bccL) <= 0) {
            return Math.fma(Double.longBitsToDouble(ux), -0x1p-28, 1.0);
        }

        final double absx = Double.longBitsToDouble(ux);
        final double[] hl = new double[2];
        final double err = cosFast(hl, absx);
        final double h = hl[0], l = hl[1];
        final double left = h + (l - err);
        final double right = h + (l + err);
        if (left == right) return left;

        return cosAccurate(absx);
    }

    // ---------------------------------------------------------------
    //  Double-double primitives (CORE-MATH a_mul/s_mul/d_mul/fast_two_sum)
    // ---------------------------------------------------------------

    /** Exact double*double → (hi, lo) via a_mul. Stores hi at out[0], lo at out[1]. */
    private static void aMul(double[] out, double a, double b) {
        final double hi = a * b;
        out[0] = hi;
        out[1] = Math.fma(a, b, -hi);
    }

    /** s_mul: scalar a * (bh+bl) → (hi, lo) with error bounded by ulp(lo). */
    private static void sMul(double[] out, double a, double bh, double bl) {
        aMul(out, a, bh);
        out[1] = Math.fma(a, bl, out[1]);
    }

    /** d_mul: (ah+al)*(bh+bl) - al*bl → (hi, lo). */
    private static void dMul(double[] out, double ah, double al, double bh, double bl) {
        final double hi = ah * bh;
        final double s = Math.fma(ah, bh, -hi);
        final double t = Math.fma(al, bh, s);
        out[0] = hi;
        out[1] = Math.fma(ah, bl, t);
    }

    /** fast_two_sum: (hi, lo) such that hi+lo == a+b exactly when |a| >= |b|. */
    private static void fastTwoSum(double[] out, double a, double b) {
        final double hi = a + b;
        final double e = hi - a;
        out[0] = hi;
        out[1] = b - e;
    }

    // ---------------------------------------------------------------
    //  Fast-path polynomial evaluators
    // ---------------------------------------------------------------

    /**
     * evalPSfast: approximate sin2pi(xh+xl) → (h, l).
     * Domain: 2^-24 <= xh+xl < 2^-11+2^-24, |xl| < 2^-52.36.
     * Assumes uh+ul approximates (xh+xl)^2.
     */
    private static void evalPSfast(double[] out, double xh, double xl, double uh, double ul) {
        double h = PSfast[4]; // degree 7
        h = Math.fma(h, uh, PSfast[3]); // degree 5
        h = Math.fma(h, uh, PSfast[2]); // degree 3
        // s_mul(h, l, h, uh, ul)
        final double[] tmp = {0, 0};
        sMul(tmp, h, uh, ul);
        h = tmp[0];
        double l = tmp[1];
        // fast_two_sum(h, t, PSfast[0], h)
        fastTwoSum(tmp, PSfast[0], h);
        h = tmp[0];
        final double t = tmp[1];
        l += PSfast[1] + t;
        // multiply by xh+xl
        dMul(tmp, h, l, xh, xl);
        out[0] = tmp[0];
        out[1] = tmp[1];
    }

    /**
     * evalPCfast: approximate cos2pi(xh+xl) → (h, l).
     * Domain: 2^-24 <= xh+xl < 2^-11+2^-24.
     * Even function: only uh+ul ~ (xh+xl)^2 needed.
     */
    private static void evalPCfast(double[] out, double uh, double ul) {
        double h = PCfast[4]; // degree 6
        h = Math.fma(h, uh, PCfast[3]); // degree 4
        h = Math.fma(h, uh, PCfast[2]); // degree 2
        final double[] tmp = {0, 0};
        sMul(tmp, h, uh, ul);
        h = tmp[0];
        double l = tmp[1];
        fastTwoSum(tmp, PCfast[0], h);
        h = tmp[0];
        final double t = tmp[1];
        l += PCfast[1] + t;
        out[0] = h;
        out[1] = l;
    }

    // ---------------------------------------------------------------
    //  Fast-path Payne-Hanek argument reduction (set_dd helper, reduceFast)
    // ---------------------------------------------------------------

    private static final double CH = 0x1.45f306dc9c883p-3;
    private static final double CL = -0x1.6b01ec5417056p-57;

    /** set_dd: convert two-uint64 fixed-point fraction to (h, l) double-double. */
    private static void setDd(double[] out, long c1, long c0) {
        long h = 0L, l = 0L;
        if (c1 != 0L) {
            int e = Long.numberOfLeadingZeros(c1);
            if (e != 0) {
                c1 = (c1 << e) | (c0 >>> (64 - e));
                c0 = c0 << e;
            }
            long f = 0x3feL - e;
            h = (f << 52) | ((c1 << 1) >>> 12);
            c0 = (c1 << 53) | (c0 >>> 11);
            if (c0 != 0L) {
                int g = Long.numberOfLeadingZeros(c0);
                if (g != 0) c0 = c0 << g;
                l = ((f - 53 - g) << 52) | ((c0 << 1) >>> 12);
            }
        } else if (c0 != 0L) {
            int e = Long.numberOfLeadingZeros(c0);
            long f = 0x3feL - 64 - e;
            c0 = c0 << (e + 1);
            h = (f << 52) | (c0 >>> 12);
            c0 = c0 << 52;
            if (c0 != 0L) {
                int g = Long.numberOfLeadingZeros(c0);
                c0 = c0 << (g + 1);
                l = ((f - 64 - g) << 52) | (c0 >>> 12);
            }
        }
        out[0] = Double.longBitsToDouble(h);
        out[1] = Double.longBitsToDouble(l);
    }

    /**
     * Returns i in [0, 2048) and writes h, l, err1 into hlErr.
     * Assuming |x| > 0x1.7137449123ef6p-26 (sin) or > 0x1.6a09e667f3bccp-27 (cos).
     * h, l, err1 stored at hlErr[0], hlErr[1], hlErr[2]; |h| < 2^-11, |l| < 2^-52.36.
     */
    private static int reduceFast(double[] hlErr, double x) {
        double h, l, err1;
        if (x <= 0x1.921fb54442d17p+2) {
            // x < 2*pi
            final double[] tmp = {0, 0};
            aMul(tmp, CH, x);  // exact
            h = tmp[0];
            l = Math.fma(CL, x, tmp[1]);
            err1 = 0x1.d9p-105 * h;
        } else {
            // x > 0x1.921fb54442d17p+2 — Payne-Hanek
            final long u = Double.doubleToRawLongBits(x);
            int e = (int) ((u >>> 52) & 0x7ff); // 1025 <= e <= 2046
            final long m = (1L << 52) | (u & 0xfffffffffffffL);
            final long[] c = new long[3];
            // u128 mul m * T[i*]
            if (e <= 1074) { // 2^2 <= x < 2^52
                long lo, hi;
                // m * T[1]
                hi = u128MulHi(m, T[1]);
                lo = m * T[1];
                c[0] = lo;
                c[1] = hi;
                // m * T[0]
                hi = u128MulHi(m, T[0]);
                lo = m * T[0];
                final long sum = c[1] + lo;
                final long carry = (Long.compareUnsigned(sum, c[1]) < 0) ? 1L : 0L;
                c[1] = sum;
                c[2] = hi + carry;
                e = 1075 - e; // 1 <= e <= 50
            } else {
                int i = (e - 1138 + 63) >>> 6; // (e-1138)/64 ceil; 0 <= i <= 15
                long lo, hi;
                hi = u128MulHi(m, T[i + 2]);
                lo = m * T[i + 2];
                c[0] = lo;
                c[1] = hi;
                hi = u128MulHi(m, T[i + 1]);
                lo = m * T[i + 1];
                long sum = c[1] + lo;
                long carry = (Long.compareUnsigned(sum, c[1]) < 0) ? 1L : 0L;
                c[1] = sum;
                c[2] = hi + carry;
                hi = u128MulHi(m, T[i]);
                lo = m * T[i];
                sum = c[2] + lo;
                c[2] = sum;
                // The C only adds the low half here; the high carry contributes
                // to bits beyond what we need.
                e = 1139 + (i << 6) - e; // 1 <= e <= 64
            }
            if (e == 64) {
                c[0] = c[1];
                c[1] = c[2];
            } else {
                c[0] = (c[1] << (64 - e)) | (c[0] >>> e);
                c[1] = (c[2] << (64 - e)) | (c[1] >>> e);
            }
            final double[] tmp = {0, 0};
            setDd(tmp, c[1], c[0]);
            h = tmp[0];
            l = tmp[1];
            err1 = 0x1.01p-76;
        }
        // i = floor(h * 2^11); h -= i * 2^-11
        final double i = Math.floor(h * 0x1p11);
        h = Math.fma(i, -0x1p-11, h);
        hlErr[0] = h;
        hlErr[1] = l;
        hlErr[2] = err1;
        return (int) i;
    }

    // ---------------------------------------------------------------
    //  Fast paths
    // ---------------------------------------------------------------

    /** sin_fast: writes (h, l) into hl[0..1]; returns max absolute error. */
    private static double sinFast(double[] hl, double x) {
        final boolean negIn = x < 0.0;
        final double absx = negIn ? -x : x;

        final double[] hlErr = new double[3];
        int i = reduceFast(hlErr, absx);
        double h = hlErr[0];
        double l = hlErr[1];
        final double err1 = hlErr[2];

        boolean neg = negIn;
        boolean isSin = true;

        // i >> 10: pi <= x: sin(pi+x) = -sin(x)
        neg = neg ^ ((i >> 10) != 0);
        i = i & 0x3ff;
        // i >> 9: pi/2 <= x: sin(pi/2+x) = cos(x)
        isSin = isSin ^ ((i >> 9) != 0);
        i = i & 0x1ff;
        // i & 0x100: pi/4 <= x_red <= pi/2
        if ((i & 0x100) != 0) {
            isSin = !isSin;
            i = 0x1ff - i;
            // 2^-11 - h is exact.
            h = 0x1p-11 - h;
            l = -l;
        }

        // SC[i*3 + {0,1,2}]: SC[i][0]=delta, SC[i][1]=sin2pi(xi), SC[i][2]=cos2pi(xi)
        h -= SC[i * 3];

        final double[] tmp = {0, 0};
        aMul(tmp, h, h);
        double uh = tmp[0];
        double ul = Math.fma(h + h, l, tmp[1]);

        final double[] sin = {0, 0};
        evalPSfast(sin, h, l, uh, ul);
        double sh = sin[0], sl = sin[1];
        final double[] cos = {0, 0};
        evalPCfast(cos, uh, ul);
        double ch = cos[0], cl = cos[1];

        final double sgn = neg ? -1.0 : 1.0;
        double err;
        if (isSin) {
            sMul(tmp, sgn * SC[i * 3 + 2], sh, sl);
            sh = tmp[0]; sl = tmp[1];
            sMul(tmp, sgn * SC[i * 3 + 1], ch, cl);
            ch = tmp[0]; cl = tmp[1];
            fastTwoSum(tmp, ch, sh);
            hl[0] = tmp[0];
            hl[1] = tmp[1] + sl + cl;
            err = 0x1.55p-69;
        } else {
            sMul(tmp, sgn * SC[i * 3 + 2], ch, cl);
            ch = tmp[0]; cl = tmp[1];
            sMul(tmp, sgn * SC[i * 3 + 1], sh, sl);
            sh = tmp[0]; sl = tmp[1];
            fastTwoSum(tmp, ch, -sh);
            hl[0] = tmp[0];
            hl[1] = tmp[1] + cl - sl;
            err = 0x1.81p-69;
        }
        return err + err1;
    }

    /** cos_fast: writes (h, l) into hl[0..1]; returns max absolute error.
     *  Caller must have ensured x >= 0 (cos is even, so cos_fast operates on |x|). */
    private static double cosFast(double[] hl, double x) {
        boolean neg = false;
        boolean isCos = true;

        final double[] hlErr = new double[3];
        int i = reduceFast(hlErr, x);
        double h = hlErr[0];
        double l = hlErr[1];
        final double err1 = hlErr[2];

        // pi <= x: cos(pi+x) = -cos(x)
        neg = neg ^ ((i >> 10) != 0);
        i = i & 0x3ff;
        // pi/2 <= x: cos(pi/2+x) = -sin(x)
        if ((i >> 9) != 0) {
            isCos = !isCos;
            neg = !neg;
        }
        i = i & 0x1ff;
        // pi/4 <= x_red <= pi/2
        if ((i & 0x100) != 0) {
            isCos = !isCos;
            i = 0x1ff - i;
            h = 0x1p-11 - h;
            l = -l;
        }

        h -= SC[i * 3];

        final double[] tmp = {0, 0};
        aMul(tmp, h, h);
        double uh = tmp[0];
        double ul = Math.fma(h + h, l, tmp[1]);

        final double[] sin = {0, 0};
        evalPSfast(sin, h, l, uh, ul);
        double sh = sin[0], sl = sin[1];
        final double[] cos = {0, 0};
        evalPCfast(cos, uh, ul);
        double ch = cos[0], cl = cos[1];

        double err;
        if (!isCos) {
            sMul(tmp, SC[i * 3 + 2], sh, sl);
            sh = tmp[0]; sl = tmp[1];
            sMul(tmp, SC[i * 3 + 1], ch, cl);
            ch = tmp[0]; cl = tmp[1];
            fastTwoSum(tmp, ch, sh);
            hl[0] = tmp[0];
            hl[1] = tmp[1] + sl + cl;
            err = 0x1.55p-69;
        } else {
            sMul(tmp, SC[i * 3 + 2], ch, cl);
            ch = tmp[0]; cl = tmp[1];
            sMul(tmp, SC[i * 3 + 1], sh, sl);
            sh = tmp[0]; sl = tmp[1];
            fastTwoSum(tmp, ch, -sh);
            hl[0] = tmp[0];
            hl[1] = tmp[1] + cl - sl;
            err = 0x1.81p-69;
        }
        if (neg) {
            hl[0] = -hl[0];
            hl[1] = -hl[1];
        }
        return err + err1;
    }

    // ---------------------------------------------------------------
    //  Accurate-path argument reduction (operates on Dint64)
    // ---------------------------------------------------------------

    /** normalize: shift X.hi-X.lo so that X.hi MSB is set (when X != 0). */
    private static void normalize(Dint64 X) {
        if (X.hi != 0L) {
            int cnt = Long.numberOfLeadingZeros(X.hi);
            if (cnt != 0) {
                X.hi = (X.hi << cnt) | (X.lo >>> (64 - cnt));
                X.lo = X.lo << cnt;
            }
            X.ex -= cnt;
        } else if (X.lo != 0L) {
            int cnt = Long.numberOfLeadingZeros(X.lo);
            X.hi = X.lo << cnt;
            X.lo = 0L;
            X.ex -= 64 + cnt;
        }
    }

    /** reduce: X /= 2*pi (mod 1). Assumes X is normalized; output is normalized. */
    private static void reduce(Dint64 X) {
        long e = X.ex;
        if (e <= 1L) { // |X| < 2
            // X.lo = (X.hi * T[1]) >> 64; tiny = X.hi * T[1] (low)
            final long m1Lo = X.hi * T[1];
            final long m1Hi = u128MulHi(X.hi, T[1]);
            final long tiny = m1Lo;
            X.lo = m1Hi;
            // X.hi * T[0]
            final long m0Lo = X.hi * T[0];
            final long m0Hi = u128MulHi(X.hi, T[0]);
            // X.lo += m0Lo
            final long sumLo = X.lo + m0Lo;
            final long carry = (Long.compareUnsigned(sumLo, X.lo) < 0) ? 1L : 0L;
            X.lo = sumLo;
            X.hi = m0Hi + carry;
            // normalize
            long e0 = X.ex;
            normalize(X);
            int eShift = (int) (e0 - X.ex);
            if (eShift != 0) {
                X.lo |= tiny >>> (64 - eShift);
            }
            return;
        }

        // 2 <= e <= 1024
        int i = (e < 127L) ? 0 : (int) ((e - 127L + 64L - 1L) / 64L);
        // 0 <= i <= 15
        final long[] c = new long[5];
        // m * T[i+3]
        long lo = X.hi * T[i + 3];
        long hi = u128MulHi(X.hi, T[i + 3]);
        c[0] = lo;
        c[1] = hi;
        // m * T[i+2]
        lo = X.hi * T[i + 2];
        hi = u128MulHi(X.hi, T[i + 2]);
        long sum = c[1] + lo;
        long cy = (Long.compareUnsigned(sum, c[1]) < 0) ? 1L : 0L;
        c[1] = sum;
        c[2] = hi + cy;
        // m * T[i+1]
        lo = X.hi * T[i + 1];
        hi = u128MulHi(X.hi, T[i + 1]);
        sum = c[2] + lo;
        cy = (Long.compareUnsigned(sum, c[2]) < 0) ? 1L : 0L;
        c[2] = sum;
        c[3] = hi + cy;
        // m * T[i]
        lo = X.hi * T[i];
        hi = u128MulHi(X.hi, T[i]);
        sum = c[3] + lo;
        cy = (Long.compareUnsigned(sum, c[3]) < 0) ? 1L : 0L;
        c[3] = sum;
        c[4] = hi + cy;

        int f = (int) (e - 64L * i);
        long tiny;
        if (f < 64) {
            X.hi = (c[4] << f) | (c[3] >>> (64 - f));
            X.lo = (c[3] << f) | (c[2] >>> (64 - f));
            tiny = (c[2] << f) | (c[1] >>> (64 - f));
        } else if (f == 64) {
            X.hi = c[3];
            X.lo = c[2];
            tiny = c[1];
        } else {
            // 65 <= f <= 127
            int g = f - 64;
            // extra term: u = (m * T[i+4]) >> 64, then c[0] += u
            final long uHi = u128MulHi(X.hi, T[i + 4]);
            final long c0New = c[0] + uHi;
            final boolean overflow0 = Long.compareUnsigned(c0New, c[0]) < 0;
            c[0] = c0New;
            if (overflow0) {
                final long c1New = c[1] + 1L;
                final boolean overflow1 = c1New == 0L;
                c[1] = c1New;
                if (overflow1) {
                    final long c2New = c[2] + 1L;
                    final boolean overflow2 = c2New == 0L;
                    c[2] = c2New;
                    if (overflow2) {
                        final long c3New = c[3] + 1L;
                        final boolean overflow3 = c3New == 0L;
                        c[3] = c3New;
                        if (overflow3) c[4]++;
                    }
                }
            }
            X.hi = (c[3] << g) | (c[2] >>> (64 - g));
            X.lo = (c[2] << g) | (c[1] >>> (64 - g));
            tiny = (c[1] << g) | (c[0] >>> (64 - g));
        }
        X.ex = 0L;
        normalize(X);
        if (X.ex < 0L) {
            // put upper -ex bits of tiny into low bits of lo
            X.lo |= tiny >>> (64 + (int) X.ex);
        }
    }

    /** reduce2: write X = i/2^11 + r exact. Returns i and modifies X to r. */
    private static int reduce2(Dint64 X) {
        if (X.ex <= -11L) {
            return 0;
        }
        int sh = 64 - 11 - (int) X.ex;
        int i = (int) (X.hi >>> sh);
        X.hi = X.hi & ((1L << sh) - 1L);
        normalize(X);
        return i;
    }

    // ---------------------------------------------------------------
    //  Accurate-path polynomial evaluators (operate on Dint64)
    // ---------------------------------------------------------------

    /** Build a Dint64 from indexed PS/PC/S/C entries (lo, hi, ex, sgn). */
    private static Dint64 dintFromTable(long[] tbl, int idx) {
        final int b = idx * 4;
        return new Dint64(tbl[b], tbl[b + 1], tbl[b + 2], tbl[b + 3]);
    }

    /** evalPS: Y ~ sin2pi(X), X^2 ~ X2. Polynomial degree 11 odd. */
    private static void evalPS(Dint64 Y, Dint64 X, Dint64 X2) {
        // Y = X2 * PS[5] (mul_dint_21 since PS[5].lo=0... wait PS has nonzero lo,
        //   the C uses mul_dint_21 because X2 was built via mul_dint).
        // Actually mul_dint_21 requires b.lo == 0; here PS+5 is the b argument
        //   in C (mul_dint_21 (Y, X2, PS+5)) — but PS[5].lo is nonzero!
        // Re-reading: mul_dint_21 doesn't strictly require b.lo==0; it's a fast
        //   variant that ignores b.lo. It's used here because the algorithm tolerates
        //   the truncation. In our Java port we have mul21Assign with same semantics.
        final Dint64 ps5 = dintFromTable(PS, 5);
        final Dint64 ps4 = dintFromTable(PS, 4);
        final Dint64 ps3 = dintFromTable(PS, 3);
        final Dint64 ps2 = dintFromTable(PS, 2);
        final Dint64 ps1 = dintFromTable(PS, 1);
        final Dint64 ps0 = dintFromTable(PS, 0);
        final Dint64 t = new Dint64();

        t.mul21Assign(X2, ps5); Y.copyFrom(t);
        t.addAssign(Y, ps4);    Y.copyFrom(t);
        t.mulAssign(Y, X2);     Y.copyFrom(t);
        t.addAssign(Y, ps3);    Y.copyFrom(t);
        t.mulAssign(Y, X2);     Y.copyFrom(t);
        t.addAssign(Y, ps2);    Y.copyFrom(t);
        t.mulAssign(Y, X2);     Y.copyFrom(t);
        t.addAssign(Y, ps1);    Y.copyFrom(t);
        t.mulAssign(Y, X2);     Y.copyFrom(t);
        t.addAssign(Y, ps0);    Y.copyFrom(t);
        t.mulAssign(Y, X);      Y.copyFrom(t);
    }

    /** evalPC: Y ~ cos2pi(X), X^2 ~ X2. Polynomial degree 10 even. */
    private static void evalPC(Dint64 Y, Dint64 X2) {
        final Dint64 pc5 = dintFromTable(PC, 5);
        final Dint64 pc4 = dintFromTable(PC, 4);
        final Dint64 pc3 = dintFromTable(PC, 3);
        final Dint64 pc2 = dintFromTable(PC, 2);
        final Dint64 pc1 = dintFromTable(PC, 1);
        final Dint64 pc0 = dintFromTable(PC, 0);
        final Dint64 t = new Dint64();

        t.mul21Assign(X2, pc5); Y.copyFrom(t);
        t.addAssign(Y, pc4);    Y.copyFrom(t);
        t.mulAssign(Y, X2);     Y.copyFrom(t);
        t.addAssign(Y, pc3);    Y.copyFrom(t);
        t.mulAssign(Y, X2);     Y.copyFrom(t);
        t.addAssign(Y, pc2);    Y.copyFrom(t);
        t.mulAssign(Y, X2);     Y.copyFrom(t);
        t.addAssign(Y, pc1);    Y.copyFrom(t);
        t.mulAssign(Y, X2);     Y.copyFrom(t);
        t.addAssign(Y, pc0);    Y.copyFrom(t);
    }

    // ---------------------------------------------------------------
    //  Accurate paths
    // ---------------------------------------------------------------

    /** MAGIC = 1/2^11 in dint form. */
    private static final long MAGIC_LO = 0x0L;
    private static final long MAGIC_HI = 0x8000000000000000L;
    private static final long MAGIC_EX = -10L;
    private static final long MAGIC_SGN = 0x0L;

    private static double sinAccurate(double x) {
        final double absx = (x > 0.0) ? x : -x;
        final Dint64 X = new Dint64();
        X.fromDouble(absx);
        reduce(X);

        boolean neg = x < 0.0;
        boolean isSin = true;

        int i = reduce2(X);
        if ((i & 0x400) != 0) {
            neg = !neg;
            i = i & 0x3ff;
        }
        if ((i & 0x200) != 0) {
            isSin = false;
            i = i & 0x1ff;
        }
        if ((i & 0x100) != 0) {
            isSin = !isSin;
            X.sgn = 1L;
            // X = MAGIC + (-X)  →  X = 2^-11 - X
            final Dint64 magic = new Dint64(MAGIC_LO, MAGIC_HI, MAGIC_EX, MAGIC_SGN);
            final Dint64 t = new Dint64();
            t.addAssign(magic, X);
            X.copyFrom(t);
            i = 0x1ff - i;
        }

        final Dint64 X2 = new Dint64();
        X2.mulAssign(X, X);

        final Dint64 U = new Dint64();
        evalPC(U, X2);
        final Dint64 V = new Dint64();
        evalPS(V, X, X2);

        final Dint64 t = new Dint64();
        if (isSin) {
            // sin: U = S[i]*U + C[i]*V
            final Dint64 si = dintFromTable(S, i);
            final Dint64 ci = dintFromTable(C, i);
            t.mulAssign(si, U); U.copyFrom(t);
            t.mulAssign(ci, V); V.copyFrom(t);
        } else {
            // cos: U = C[i]*U - S[i]*V
            final Dint64 si = dintFromTable(S, i);
            final Dint64 ci = dintFromTable(C, i);
            t.mulAssign(ci, U); U.copyFrom(t);
            t.mulAssign(si, V); V.copyFrom(t);
            V.sgn = 1L - V.sgn;
        }
        t.addAssign(U, V); U.copyFrom(t);

        // Hard-rounding band check + exception lookup
        final long err = 41L;
        final long lo0 = U.lo - err;
        final long hi0 = U.hi - ((Long.compareUnsigned(lo0, U.lo) > 0) ? 1L : 0L);
        final long lo1 = U.lo + err;
        final long hi1 = U.hi + ((Long.compareUnsigned(lo1, U.lo) < 0) ? 1L : 0L);
        if ((hi0 >>> 10) != (hi1 >>> 10)) {
            final double absxOrig = Math.abs(x);
            // sin exception 0
            if (absxOrig == 0x1.e0000000001c2p-20) {
                return (x > 0.0)
                    ? 0x1.dfffffffff02ep-20 + 0x1.dcba692492527p-146
                    : -0x1.dfffffffff02ep-20 - 0x1.dcba692492527p-146;
            }
            // sin exception 1
            if (absxOrig == 0x1.6ac5b262ca1ffp+849) {
                return (x > 0.0)
                    ? 0x1p+0 + (-0x1.2b089ea1e692bp-123)
                    : -0x1p+0 - (-0x1.2b089ea1e692bp-123);
            }
        }

        if (neg) U.sgn = 1L - U.sgn;
        return U.toDouble();
    }

    private static double cosAccurate(double x) {
        // x is already non-negative (caller passed |x|)
        final Dint64 X = new Dint64();
        X.fromDouble(x);
        reduce(X);

        boolean neg = false;
        boolean isCos = true;

        int i = reduce2(X);
        if ((i & 0x400) != 0) {
            neg = true;
            i = i & 0x3ff;
        }
        if ((i & 0x200) != 0) {
            neg = !neg;
            isCos = false;
            i = i & 0x1ff;
        }
        if ((i & 0x100) != 0) {
            isCos = !isCos;
            X.sgn = 1L;
            final Dint64 magic = new Dint64(MAGIC_LO, MAGIC_HI, MAGIC_EX, MAGIC_SGN);
            final Dint64 t = new Dint64();
            t.addAssign(magic, X);
            X.copyFrom(t);
            i = 0x1ff - i;
        }

        final Dint64 X2 = new Dint64();
        X2.mulAssign(X, X);

        final Dint64 U = new Dint64();
        evalPC(U, X2);
        final Dint64 V = new Dint64();
        evalPS(V, X, X2);

        final Dint64 t = new Dint64();
        if (!isCos) {
            // sin form: U = S[i]*U + C[i]*V
            final Dint64 si = dintFromTable(S, i);
            final Dint64 ci = dintFromTable(C, i);
            t.mulAssign(si, U); U.copyFrom(t);
            t.mulAssign(ci, V); V.copyFrom(t);
        } else {
            // cos form: U = C[i]*U - S[i]*V
            final Dint64 si = dintFromTable(S, i);
            final Dint64 ci = dintFromTable(C, i);
            t.mulAssign(ci, U); U.copyFrom(t);
            t.mulAssign(si, V); V.copyFrom(t);
            V.sgn = 1L - V.sgn;
        }
        t.addAssign(U, V); U.copyFrom(t);

        final long err = 41L;
        final long lo0 = U.lo - err;
        final long hi0 = U.hi - ((Long.compareUnsigned(lo0, U.lo) > 0) ? 1L : 0L);
        final long lo1 = U.lo + err;
        final long hi1 = U.hi + ((Long.compareUnsigned(lo1, U.lo) < 0) ? 1L : 0L);
        if ((hi0 >>> 10) != (hi1 >>> 10)) {
            final double absxOrig = x;  // already non-negative
            if (absxOrig == 0x1.8000000000009p-23) return 0x1.fffffffffff7p-1 + 0x1.b56666666666cp-143;
            if (absxOrig == 0x1.8000000000024p-22) return 0x1.ffffffffffdcp-1 + 0x1.b56666666667ep-137;
            if (absxOrig == 0x1.800000000009p-21)  return 0x1.ffffffffff7p-1  + 0x1.b5666666666c4p-131;
            if (absxOrig == 0x1.20000000000f3p-20) return 0x1.fffffffffebcp-1 + 0x1.37642666666fdp-127;
            if (absxOrig == 0x1.800000000024p-20)  return 0x1.fffffffffdcp-1  + 0x1.b5666666667ddp-125;
        }

        if (neg) U.sgn = 1L - U.sgn;
        return U.toDouble();
    }

    // ---------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------

    /** Unsigned 64×64 → high 64 bits of 128-bit product. */
    private static long u128MulHi(long a, long b) {
        final long aLo = a & 0xffffffffL;
        final long aHi = a >>> 32;
        final long bLo = b & 0xffffffffL;
        final long bHi = b >>> 32;
        final long ll = aLo * bLo;
        final long lh = aLo * bHi;
        final long hl = aHi * bLo;
        final long hh = aHi * bHi;
        final long mid = (ll >>> 32) + (lh & 0xffffffffL) + (hl & 0xffffffffL);
        return hh + (lh >>> 32) + (hl >>> 32) + (mid >>> 32);
    }

    // ---------------------------------------------------------------
    //  CORE-MATH static tables (extracted bit-exact from sin.c)
    // ---------------------------------------------------------------

// === Generated table initialisers (extract_tables.py from CORE-MATH sin.c) ===

    private static final long[] T = {
        0x28be60db9391054aL, // i=0
        0x7f09d5f47d4d3770L, // i=1
        0x36d8a5664f10e410L, // i=2
        0x7f9458eaf7aef158L, // i=3
        0x6dc91b8e909374b8L, // i=4
        0x01924bba82746487L, // i=5
        0x3f877ac72c4a69cfL, // i=6
        0xba208d7d4baed121L, // i=7
        0x3a671c09ad17df90L, // i=8
        0x4e64758e60d4ce7dL, // i=9
        0x272117e2ef7e4a0eL, // i=10
        0xc7fe25fff7816603L, // i=11
        0xfbcbc462d6829b47L, // i=12
        0xdb4d9fb3c9f2c26dL, // i=13
        0xd3d18fd9a797fa8bL, // i=14
        0x5d49eeb1faf97c5eL, // i=15
        0xcf41ce7de294a4baL, // i=16
        0x9afed7ec47e35742L, // i=17
        0x1580cc11bf1edaeaL, // i=18
        0xfc33ef0826bd0d87L, // i=19
    };

    /** S: 256 entries; index 4×i = lo, +1 = hi, +2 = ex, +3 = sgn. */
    private static final long[] S = new long[1024];
    static {
        S[0] = 0x0000000000000000L; S[1] = 0x0000000000000000L; S[2] = 128L; S[3] = 0x0L;
        S[4] = 0x480f7956b6470765L; S[5] = 0xc90fc5f66525d257L; S[6] = -8L; S[7] = 0x0L;
        S[8] = 0xcb3ff35bd4d81baaL; S[9] = 0xc90f87f3380388d5L; S[10] = -7L; S[11] = 0x0L;
        S[12] = 0xb767005691b9d9d1L; S[13] = 0x96cb587284b81770L; S[14] = -6L; S[15] = 0x0L;
        S[16] = 0xf1d7d06db39ea9fcL; S[17] = 0xc90e8fe6f63c2330L; S[18] = -6L; S[19] = 0x0L;
        S[20] = 0xd784e031f9af76d6L; S[21] = 0xfb514b55ccbe541aL; S[22] = -6L; S[23] = 0x0L;
        S[24] = 0xf91ee371d6467dcaL; S[25] = 0x96c9b5df1877e9b5L; S[26] = -5L; S[27] = 0x0L;
        S[28] = 0xf56e3c87ae3c56dfL; S[29] = 0xafea690fd5912ef3L; S[30] = -5L; S[31] = 0x0L;
        S[32] = 0xc539edcbfda0cf2cL; S[33] = 0xc90aafbd1b33efc9L; S[34] = -5L; S[35] = 0x0L;
        S[36] = 0x850021e392744a4fL; S[37] = 0xe22a7a6729d8e453L; S[38] = -5L; S[39] = 0x0L;
        S[40] = 0x00b21ccebc9caac3L; S[41] = 0xfb49b98e8e7807f6L; S[42] = -5L; S[43] = 0x0L;
        S[44] = 0xde5b1068d174be9cL; S[45] = 0x8a342eda160bf5aeL; S[46] = -4L; S[47] = 0x0L;
        S[48] = 0x37b2dd49d5fca3c0L; S[49] = 0x96c32baca2ae68b4L; S[50] = -4L; S[51] = 0x0L;
        S[52] = 0xb56007d16d4ad5a3L; S[53] = 0xa351cb7fc30bc889L; S[54] = -4L; S[55] = 0x0L;
        S[56] = 0xcd34d2751c2e1da7L; S[57] = 0xafe00694866a1b44L; S[58] = -4L; S[59] = 0x0L;
        S[60] = 0xf10bfca3d6464012L; S[61] = 0xbc6dd52c3a342eb5L; S[62] = -4L; S[63] = 0x0L;
        S[64] = 0x6a17954b2b7c5171L; S[65] = 0xc8fb2f886ec09f37L; S[66] = -4L; S[67] = 0x0L;
        S[68] = 0x73d1472472f4a390L; S[69] = 0xd5880deafc18b534L; S[70] = -4L; S[71] = 0x0L;
        S[72] = 0x438b4a73aecd2541L; S[73] = 0xe214689606bf1676L; S[74] = -4L; S[75] = 0x0L;
        S[76] = 0xc4e92d01a2f42935L; S[77] = 0xeea037cc04764844L; S[78] = -4L; S[79] = 0x0L;
        S[80] = 0xf0a0e36a000c7350L; S[81] = 0xfb2b73cfc106ff68L; S[82] = -4L; S[83] = 0x0L;
        S[84] = 0x60e782313f6161afL; S[85] = 0x83db0a7231831d8fL; S[86] = -3L; S[87] = 0x0L;
        S[88] = 0x77724a2b2a669bc4L; S[89] = 0x8a2009a6b84d9402L; S[90] = -3L; S[91] = 0x0L;
        S[92] = 0x56e0a8b0d177b55dL; S[93] = 0x9064b3a76a22640cL; S[94] = -3L; S[95] = 0x0L;
        S[96] = 0xf77574094d3c35c4L; S[97] = 0x96a9049670cfae65L; S[98] = -3L; S[99] = 0x0L;
        S[100] = 0x50ffe4f5caa7f1faL; S[101] = 0x9cecf8962d14c822L; S[102] = -3L; S[103] = 0x0L;
        S[104] = 0xdec1b7f2768bdafaL; S[105] = 0xa3308bc93904ad69L; S[106] = -3L; S[107] = 0x0L;
        S[108] = 0x76f8c63986598c79L; S[109] = 0xa973ba526a6850d9L; S[110] = -3L; S[111] = 0x0L;
        S[112] = 0xfdd2fc0936594c2dL; S[113] = 0xafb68054d520c60bL; S[114] = -3L; S[115] = 0x0L;
        S[116] = 0x924bef13600f9852L; S[117] = 0xb5f8d9f3cd8945d6L; S[118] = -3L; S[119] = 0x0L;
        S[120] = 0xeb13e106732687f1L; S[121] = 0xbc3ac352ead90abeL; S[122] = -3L; S[123] = 0x0L;
        S[124] = 0xb228a03916371f6fL; S[125] = 0xc27c389609850433L; S[126] = -3L; S[127] = 0x0L;
        S[128] = 0xc7396c894bbf7389L; S[129] = 0xc8bd35e14da15f0eL; S[130] = -3L; S[131] = 0x0L;
        S[132] = 0x6b47b8c44e5b037eL; S[133] = 0xcefdb7592542e1e9L; S[134] = -3L; S[135] = 0x0L;
        S[136] = 0x7337412cf70716cbL; S[137] = 0xd53db9224ae01bcaL; S[138] = -3L; S[139] = 0x0L;
        S[140] = 0xbb286d23e11c8337L; S[141] = 0xdb7d3761c7b263b6L; S[142] = -3L; S[143] = 0x0L;
        S[144] = 0x31883b30137c6e62L; S[145] = 0xe1bc2e3cf616a7acL; S[146] = -3L; S[147] = 0x0L;
        S[148] = 0xeeb8f9c33340a2f2L; S[149] = 0xe7fa99d983ee098fL; S[150] = -3L; S[151] = 0x0L;
        S[152] = 0xed16b994af6c18aeL; S[153] = 0xee38765d74fe4897L; S[154] = -3L; S[155] = 0x0L;
        S[156] = 0x14e1a5488eaeab96L; S[157] = 0xf475bfef2551f5b9L; S[158] = -3L; S[159] = 0x0L;
        S[160] = 0x704729ae56d78a37L; S[161] = 0xfab272b54b9871a2L; S[162] = -3L; S[163] = 0x0L;
        S[164] = 0x3eac8308f1113e5eL; S[165] = 0x8077456b7dc2d967L; S[166] = -2L; S[167] = 0x0L;
        S[168] = 0xdb1f70118c9c2198L; S[169] = 0x8395023dd418e919L; S[170] = -2L; S[171] = 0x0L;
        S[172] = 0xc5a9decdfaad4db5L; S[173] = 0x86b26de5933c2e8eL; S[174] = -2L; S[175] = 0x0L;
        S[176] = 0x97965c9860c34e44L; S[177] = 0x89cf8676d7abb55bL; S[178] = -2L; S[179] = 0x0L;
        S[180] = 0xdcdca90cc73b116aL; S[181] = 0x8cec4a05f12739e8L; S[182] = -2L; S[183] = 0x0L;
        S[184] = 0xa6e3df5975cca9daL; S[185] = 0x9008b6a763de75b7L; S[186] = -2L; S[187] = 0x0L;
        S[188] = 0x899c4de737feec22L; S[189] = 0x9324ca6fe9a04b4eL; S[190] = -2L; S[191] = 0x0L;
        S[192] = 0x000a89a11e07c1feL; S[193] = 0x964083747309d113L; S[194] = -2L; S[195] = 0x0L;
        S[196] = 0x49c4863de522b217L; S[197] = 0x995bdfca28b53a54L; S[198] = -2L; S[199] = 0x0L;
        S[200] = 0xe7bc08111d0bfca4L; S[201] = 0x9c76dd866c689dccL; S[202] = -2L; S[203] = 0x0L;
        S[204] = 0xf3ff913a4aadb85eL; S[205] = 0x9f917abeda4498dfL; S[206] = -2L; S[207] = 0x0L;
        S[208] = 0xa5dbee6084ee1260L; S[209] = 0xa2abb58949f2ced7L; S[210] = -2L; S[211] = 0x0L;
        S[212] = 0x69fcb11e19f58619L; S[213] = 0xa5c58bfbcfd4436aL; S[214] = -2L; S[215] = 0x0L;
        S[216] = 0x0cd12a1f6ab6b095L; S[217] = 0xa8defc2cbe2f8fccL; S[218] = -2L; S[219] = 0x0L;
        S[220] = 0x8c95c4c91179176bL; S[221] = 0xabf80432a65ef190L; S[222] = -2L; S[223] = 0x0L;
        S[224] = 0x3feef3bb58b1f10dL; S[225] = 0xaf10a22459fe32a6L; S[226] = -2L; S[227] = 0x0L;
        S[228] = 0x16031a34d4fc855dL; S[229] = 0xb228d418ec1869adL; S[230] = -2L; S[231] = 0x0L;
        S[232] = 0xcd73fb5d8d45d302L; S[233] = 0xb5409827b25591f0L; S[234] = -2L; S[235] = 0x0L;
        S[236] = 0x187e26d290714d70L; S[237] = 0xb857ec684627fa4cL; S[238] = -2L; S[239] = 0x0L;
        S[240] = 0xbddd8a0365d6b1d3L; S[241] = 0xbb6ecef285f98a3aL; S[242] = -2L; S[243] = 0x0L;
        S[244] = 0xdfe1b074e22fc666L; S[245] = 0xbe853dde9658dc60L; S[246] = -2L; S[247] = 0x0L;
        S[248] = 0xad5a41de48f6b26fL; S[249] = 0xc19b3744e3262dcdL; S[250] = -2L; S[251] = 0x0L;
        S[252] = 0xdab4e426409b23a0L; S[253] = 0xc4b0b93e20c0213fL; S[254] = -2L; S[255] = 0x0L;
        S[256] = 0x5cc8c00e4fccd850L; S[257] = 0xc7c5c1e34d3055b2L; S[258] = -2L; S[259] = 0x0L;
        S[260] = 0xfa6171200ab2efc3L; S[261] = 0xcada4f4db157cf77L; S[262] = -2L; S[263] = 0x0L;
        S[264] = 0x65a3132adfb7dfd5L; S[265] = 0xcdee5f96e21b332cL; S[266] = -2L; S[267] = 0x0L;
        S[268] = 0xaadb580a1eba209fL; S[269] = 0xd101f0d8c18ed1c1L; S[270] = -2L; S[271] = 0x0L;
        S[272] = 0xdf4005ef6a64aa02L; S[273] = 0xd415012d802284f0L; S[274] = -2L; S[275] = 0x0L;
        S[276] = 0x1779df36d1cc8912L; S[277] = 0xd7278eaf9dcd5b55L; S[278] = -2L; S[279] = 0x0L;
        S[280] = 0xcbabaeb97af8e8aaL; S[281] = 0xda399779eb391377L; S[282] = -2L; S[283] = 0x0L;
        S[284] = 0xece7f445cecf1e28L; S[285] = 0xdd4b19a78aed6515L; S[286] = -2L; S[287] = 0x0L;
        S[288] = 0x0ebc61ade6ca83cdL; S[289] = 0xe05c1353f27b17e5L; S[290] = -2L; S[291] = 0x0L;
        S[292] = 0x26a0eecdb4f16266L; S[293] = 0xe36c829aeba6e720L; S[294] = -2L; S[295] = 0x0L;
        S[296] = 0x82b0aecadf808123L; S[297] = 0xe67c659895943123L; S[298] = -2L; S[299] = 0x0L;
        S[300] = 0xb91caf23416e7e80L; S[301] = 0xe98bba6965ef725fL; S[302] = -2L; S[303] = 0x0L;
        S[304] = 0x7244ee20f591983bL; S[305] = 0xec9a7f2a2a188aebL; S[306] = -2L; S[307] = 0x0L;
        S[308] = 0x1050cdf22f34182fL; S[309] = 0xefa8b1f8084ccdfcL; S[310] = -2L; S[311] = 0x0L;
        S[312] = 0x587f3fa044e2d27dL; S[313] = 0xf2b650f080d0da8dL; S[314] = -2L; S[315] = 0x0L;
        S[316] = 0x643720de93ba81bdL; S[317] = 0xf5c35a316f1a3c80L; S[318] = -2L; S[319] = 0x0L;
        S[320] = 0x4221dc4ba772598dL; S[321] = 0xf8cfcbd90af8d57aL; S[322] = -2L; S[323] = 0x0L;
        S[324] = 0xd24d3023da491920L; S[325] = 0xfbdba405e9c00ccaL; S[326] = -2L; S[327] = 0x0L;
        S[328] = 0x8b74fe2508ab8fc2L; S[329] = 0xfee6e0d6ff6fc5a4L; S[330] = -2L; S[331] = 0x0L;
        S[332] = 0xfd958d68e8b49e6bL; S[333] = 0x80f8c035cfee8d76L; S[334] = -1L; S[335] = 0x0L;
        S[336] = 0xfb4c92369f0cf008L; S[337] = 0x827dc071bfed6ffaL; S[338] = -1L; S[339] = 0x0L;
        S[340] = 0xcb07b25a7b0372a7L; S[341] = 0x8402702f5b30f2a9L; S[342] = -1L; S[343] = 0x0L;
        S[344] = 0x9d3dc689006896f4L; S[345] = 0x8586ce7ededc809dL; S[346] = -1L; S[347] = 0x0L;
        S[348] = 0x009d52755ece3f70L; S[349] = 0x870ada70ba4e6d49L; S[350] = -1L; S[351] = 0x0L;
        S[352] = 0x984156f553344306L; S[353] = 0x888e93158fb3bb04L; S[354] = -1L; S[355] = 0x0L;
        S[356] = 0xa66d1d936c38c329L; S[357] = 0x8a11f77e349bc245L; S[358] = -1L; S[359] = 0x0L;
        S[360] = 0x575f33366be0afefL; S[361] = 0x8b9506bbb28bb922L; S[362] = -1L; S[363] = 0x0L;
        S[364] = 0xcb590d74f64e77c9L; S[365] = 0x8d17bfdf47921ac8L; S[366] = -1L; S[367] = 0x0L;
        S[368] = 0xf2be3ecae62789d4L; S[369] = 0x8e9a21fa66d9ee8dL; S[370] = -1L; S[371] = 0x0L;
        S[372] = 0x632b9cff5cfee724L; S[373] = 0x901c2c1eb93dee39L; S[374] = -1L; S[375] = 0x0L;
        S[376] = 0x609c464b3dd676ecL; S[377] = 0x919ddd5e1ddb8b33L; S[378] = -1L; S[379] = 0x0L;
        S[380] = 0x6a1ff8bfe6396e28L; S[381] = 0x931f34caaaa5d23aL; S[382] = -1L; S[383] = 0x0L;
        S[384] = 0xae4ba773da6bf754L; S[385] = 0x94a03176acf82d45L; S[386] = -1L; S[387] = 0x0L;
        S[388] = 0xe06a955a5b8e301dL; S[389] = 0x9620d274aa290339L; S[390] = -1L; S[391] = 0x0L;
        S[392] = 0xfc8b7184b21f2d50L; S[393] = 0x97a116d7601c3515L; S[394] = -1L; S[395] = 0x0L;
        S[396] = 0x9dd1eedf18a2e4dfL; S[397] = 0x9920fdb1c5d5783dL; S[398] = -1L; S[399] = 0x0L;
        S[400] = 0x9ffa0d23f3c26c62L; S[401] = 0x9aa086170c0a8d86L; S[402] = -1L; S[403] = 0x0L;
        S[404] = 0xdab6b478577e7be5L; S[405] = 0x9c1faf1a9db554afL; S[406] = -1L; S[407] = 0x0L;
        S[408] = 0xdb895384528d0d60L; S[409] = 0x9d9e77d020a5bbe6L; S[410] = -1L; S[411] = 0x0L;
        S[412] = 0x98dbd3555ebcdefeL; S[413] = 0x9f1cdf4b76138b02L; S[414] = -1L; S[415] = 0x0L;
        S[416] = 0x2f895f44a303cc0bL; S[417] = 0xa09ae4a0bb300a19L; S[418] = -1L; S[419] = 0x0L;
        S[420] = 0xd29d23a624acd00cL; S[421] = 0xa21886e449b78316L; S[422] = -1L; S[423] = 0x0L;
        S[424] = 0x2be036401ba87cc2L; S[425] = 0xa395c52ab8829dfcL; S[426] = -1L; S[427] = 0x0L;
        S[428] = 0x82d9495ead5be348L; S[429] = 0xa5129e88dc17976aL; S[430] = -1L; S[431] = 0x0L;
        S[432] = 0x17218792857f4c5aL; S[433] = 0xa68f1213c73b5124L; S[434] = -1L; S[435] = 0x0L;
        S[436] = 0x3269f4702b88324aL; S[437] = 0xa80b1ee0cb823c27L; S[438] = -1L; S[439] = 0x0L;
        S[440] = 0x8e3bdf8085321556L; S[441] = 0xa986c40579e11c0aL; S[442] = -1L; S[443] = 0x0L;
        S[444] = 0xc1654b64a0081b46L; S[445] = 0xab020097a33da341L; S[446] = -1L; S[447] = 0x0L;
        S[448] = 0x811f953984eff83eL; S[449] = 0xac7cd3ad58fee7f0L; S[450] = -1L; S[451] = 0x0L;
        S[452] = 0x9a5318ac6fe94e4dL; S[453] = 0xadf73c5ced9db0f3L; S[454] = -1L; S[455] = 0x0L;
        S[456] = 0x9fe5f4ea48965e2cL; S[457] = 0xaf7139bcf5349ac6L; S[458] = -1L; S[459] = 0x0L;
        S[460] = 0x63c66682bae74898L; S[461] = 0xb0eacae4461013edL; S[462] = -1L; S[463] = 0x0L;
        S[464] = 0x695a5332090bb09bL; S[465] = 0xb263eee9f93e3088L; S[466] = -1L; S[467] = 0x0L;
        S[468] = 0x992d96e5021e3c37L; S[469] = 0xb3dca4e56b1e54bbL; S[470] = -1L; S[471] = 0x0L;
        S[472] = 0x971f4da709ad4378L; S[473] = 0xb554ebee3bf0b58eL; S[474] = -1L; S[475] = 0x0L;
        S[476] = 0x35ebacd79f209137L; S[477] = 0xb6ccc31c5065afeeL; S[478] = -1L; S[479] = 0x0L;
        S[480] = 0x9cc3ef36746de3b8L; S[481] = 0xb8442987d22cf576L; S[482] = -1L; S[483] = 0x0L;
        S[484] = 0xcdb0531c4e58484bL; S[485] = 0xb9bb1e4930848eadL; S[486] = -1L; S[487] = 0x0L;
        S[488] = 0x55b92083658bb897L; S[489] = 0xbb31a07920c7b256L; S[490] = -1L; S[491] = 0x0L;
        S[492] = 0x0a4b0d21fc5036a5L; S[493] = 0xbca7af309efd7182L; S[494] = -1L; S[495] = 0x0L;
        S[496] = 0xd1f90f79f46c7e01L; S[497] = 0xbe1d4988ee67380cL; S[498] = -1L; S[499] = 0x0L;
        S[500] = 0x91a1b5eb79658c67L; S[501] = 0xbf926e9b9a0f2127L; S[502] = -1L; S[503] = 0x0L;
        S[504] = 0x721853f8e528a934L; S[505] = 0xc1071d8275561f9bL; S[506] = -1L; S[507] = 0x0L;
        S[508] = 0xcdc2bd470675104dL; S[509] = 0xc27b55579c81f96dL; S[510] = -1L; S[511] = 0x0L;
        S[512] = 0x3122c2a59efddc37L; S[513] = 0xc3ef1535754b168dL; S[514] = -1L; S[515] = 0x0L;
        S[516] = 0xf4ff2895ab6ebe89L; S[517] = 0xc5625c36af6a222fL; S[518] = -1L; S[519] = 0x0L;
        S[520] = 0x14d24739de27e2e9L; S[521] = 0xc6d5297645257e8dL; S[522] = -1L; S[523] = 0x0L;
        S[524] = 0x004ce0246ad4fa74L; S[525] = 0xc8477c0f7bde8a98L; S[526] = -1L; S[527] = 0x0L;
        S[528] = 0x4319e5ad5b0dcb84L; S[529] = 0xc9b9531de49eb968L; S[530] = -1L; S[531] = 0x0L;
        S[532] = 0xfaa3dfe675a65ee2L; S[533] = 0xcb2aadbd5ca47af5L; S[534] = -1L; S[535] = 0x0L;
        S[536] = 0x2e663b3c7555a6c3L; S[537] = 0xcc9b8b0a0deff5d4L; S[538] = -1L; S[539] = 0x0L;
        S[540] = 0x3c540a9eec47af38L; S[541] = 0xce0bea206fcf9192L; S[542] = -1L; S[543] = 0x0L;
        S[544] = 0xa81290bdbaad62e4L; S[545] = 0xcf7bca1d476c516dL; S[546] = -1L; S[547] = 0x0L;
        S[548] = 0xb9302788604e88f1L; S[549] = 0xd0eb2a1da855fefdL; S[550] = -1L; S[551] = 0x0L;
        S[552] = 0x721fc87ba1d42456L; S[553] = 0xd25a093ef50f2482L; S[554] = -1L; S[555] = 0x0L;
        S[556] = 0x87967926fdcecec4L; S[557] = 0xd3c8669edf98d680L; S[558] = -1L; S[559] = 0x0L;
        S[560] = 0x1df22346611c6b4bL; S[561] = 0xd536415b69fe4c54L; S[562] = -1L; S[563] = 0x0L;
        S[564] = 0x3090d44db12c418cL; S[565] = 0xd6a39892e6e04764L; S[566] = -1L; S[567] = 0x0L;
        S[568] = 0xa573f2aa90434ba5L; S[569] = 0xd8106b63fa0048a0L; S[570] = -1L; S[571] = 0x0L;
        S[572] = 0x2e349483e3fb2a6aL; S[573] = 0xd97cb8ed98cb93f5L; S[574] = -1L; S[575] = 0x0L;
        S[576] = 0x362cb974182e3030L; S[577] = 0xdae8804f0ae6015bL; S[578] = -1L; S[579] = 0x0L;
        S[580] = 0x3ccca3982328ed8bL; S[581] = 0xdc53c0a7eab49b35L; S[582] = -1L; S[583] = 0x0L;
        S[584] = 0x1a5bd9269d408d7eL; S[585] = 0xddbe791825e8099eL; S[586] = -1L; S[587] = 0x0L;
        S[588] = 0xcce2634be2bf54dfL; S[589] = 0xdf28a8bffe06ca56L; S[590] = -1L; S[591] = 0x0L;
        S[592] = 0x8aa895d5bf3e84eaL; S[593] = 0xe0924ec008f734fdL; S[594] = -1L; S[595] = 0x0L;
        S[596] = 0xf7a1f9bd9ba13b6bL; S[597] = 0xe1fb6a3931894b38L; S[598] = -1L; S[599] = 0x0L;
        S[600] = 0x7b32c72e31824e51L; S[601] = 0xe363fa4cb8005482L; S[602] = -1L; S[603] = 0x0L;
        S[604] = 0xd40e9e6b989f89e5L; S[605] = 0xe4cbfe1c329c453aL; S[606] = -1L; S[607] = 0x0L;
        S[608] = 0x2872ce1bfc7ad1cdL; S[609] = 0xe63374c98e22f0b4L; S[610] = -1L; S[611] = 0x0L;
        S[612] = 0xf1b65cc5fd780262L; S[613] = 0xe79a5d770e6905dcL; S[614] = -1L; S[615] = 0x0L;
        S[616] = 0x431626c10485bddaL; S[617] = 0xe900b7474edad637L; S[618] = -1L; S[619] = 0x0L;
        S[620] = 0x0cc39cfcc29960b1L; S[621] = 0xea66815d4304e6c8L; S[622] = -1L; S[623] = 0x0L;
        S[624] = 0x1d90f780ae951140L; S[625] = 0xebcbbadc371c4aaaL; S[626] = -1L; S[627] = 0x0L;
        S[628] = 0xc71debc372b6f9d4L; S[629] = 0xed3062e7d086c6f0L; S[630] = -1L; S[631] = 0x0L;
        S[632] = 0x2a24164daec85ccbL; S[633] = 0xee9478a40e62bf86L; S[634] = -1L; S[635] = 0x0L;
        S[636] = 0x527233b40d3432bbL; S[637] = 0xeff7fb354a0eecb1L; S[638] = -1L; S[639] = 0x0L;
        S[640] = 0x6c48e9e3420b0f1eL; S[641] = 0xf15ae9c037b1d8f0L; S[642] = -1L; S[643] = 0x0L;
        S[644] = 0x7f232aee178c6323L; S[645] = 0xf2bd4369e6c126d3L; S[646] = -1L; S[647] = 0x0L;
        S[648] = 0x3c7f10db458c337cL; S[649] = 0xf41f0757c2889e84L; S[650] = -1L; S[651] = 0x0L;
        S[652] = 0x93fa6107c4327527L; S[653] = 0xf58034af92b102a7L; S[654] = -1L; S[655] = 0x0L;
        S[656] = 0xe1079824233fef46L; S[657] = 0xf6e0ca977bc6ac45L; S[658] = -1L; S[659] = 0x0L;
        S[660] = 0xa9a56012067c570cL; S[661] = 0xf840c835ffbfed66L; S[662] = -1L; S[663] = 0x0L;
        S[664] = 0x08da894471de1a18L; S[665] = 0xf9a02cb1fe833a0dL; S[666] = -1L; S[667] = 0x0L;
        S[668] = 0x0343fbf4a7d42af3L; S[669] = 0xfafef732b66d1742L; S[670] = -1L; S[671] = 0x0L;
        S[672] = 0x27c07c911290b8d1L; S[673] = 0xfc5d26dfc4d5cfdaL; S[674] = -1L; S[675] = 0x0L;
        S[676] = 0x02377c3799c052faL; S[677] = 0xfdbabae12696eea4L; S[678] = -1L; S[679] = 0x0L;
        S[680] = 0x0a9c6ba50490539fL; S[681] = 0xff17b25f38907dadL; S[682] = -1L; S[683] = 0x0L;
        S[684] = 0x6f53873e2f1477ffL; S[685] = 0x803a06415c170525L; S[686] = 0L; S[687] = 0x0L;
        S[688] = 0x5ca183dc973abc22L; S[689] = 0x80e7e43a61f5b6cbL; S[690] = 0L; S[691] = 0x0L;
        S[692] = 0x9fba97fdf0c4d24cL; S[693] = 0x819572af6decac84L; S[694] = 0L; S[695] = 0x0L;
        S[696] = 0x6fb2123fedfa6e22L; S[697] = 0x8242b1357110d372L; S[698] = 0L; S[699] = 0x0L;
        S[700] = 0x91a965931f1a200aL; S[701] = 0x82ef9f618dc5b70eL; S[702] = 0L; S[703] = 0x0L;
        S[704] = 0xbfd79717f2880abfL; S[705] = 0x839c3cc917ff6cb4L; S[706] = 0L; S[707] = 0x0L;
        S[708] = 0x246efcff30cb064aL; S[709] = 0x8448890195846099L; S[710] = 0L; S[711] = 0x0L;
        S[712] = 0x51917cac857fd5f5L; S[713] = 0x84f483a0be2f0403L; S[714] = 0L; S[715] = 0x0L;
        S[716] = 0x327888fe4b62687bL; S[717] = 0x85a02c3c7c2f5ca5L; S[718] = 0L; S[719] = 0x0L;
        S[720] = 0x85043222c9bdd18dL; S[721] = 0x864b826aec4c74e5L; S[722] = 0L; S[723] = 0x0L;
        S[724] = 0x7e0b9b07548471a2L; S[725] = 0x86f685c25e25acf5L; S[726] = 0L; S[727] = 0x0L;
        S[728] = 0x4e091160e2430712L; S[729] = 0x87a135d95473ec89L; S[730] = 0L; S[731] = 0x0L;
        S[732] = 0x4f14c8afe4560291L; S[733] = 0x884b9246854ab50bL; S[734] = 0L; S[735] = 0x0L;
        S[736] = 0xb892ca8361d8c84cL; S[737] = 0x88f59aa0da591421L; S[738] = 0L; S[739] = 0x0L;
        S[740] = 0xc88302a31afce54aL; S[741] = 0x899f4e7f712a765eL; S[742] = 0L; S[743] = 0x0L;
        S[744] = 0x660558a02136130aL; S[745] = 0x8a48ad799b6759f3L; S[746] = 0L; S[747] = 0x0L;
        S[748] = 0x545f7d79ead8fa19L; S[749] = 0x8af1b726df15e13cL; S[750] = 0L; S[751] = 0x0L;
        S[752] = 0x21a6675f51580bc4L; S[753] = 0x8b9a6b1ef6da4502L; S[754] = 0L; S[755] = 0x0L;
        S[756] = 0x101a5adbcb9ffb43L; S[757] = 0x8c42c8f9d2372644L; S[758] = 0L; S[759] = 0x0L;
        S[760] = 0x4d49cbaf15aecd80L; S[761] = 0x8cead04f95cdbf66L; S[762] = 0L; S[763] = 0x0L;
        S[764] = 0xde2d43c6b67a7cbeL; S[765] = 0x8d9280b89b9df49bL; S[766] = 0L; S[767] = 0x0L;
        S[768] = 0xbba4cfecbff54867L; S[769] = 0x8e39d9cd73464364L; S[770] = 0L; S[771] = 0x0L;
        S[772] = 0xaf0e2345f3bd24b4L; S[773] = 0x8ee0db26e24390f8L; S[774] = 0L; S[775] = 0x0L;
        S[776] = 0x9311a82459aa0f72L; S[777] = 0x8f87845de430d777L; S[778] = 0L; S[779] = 0x0L;
        S[780] = 0xb144016c7a30b39aL; S[781] = 0x902dd50bab06b1b7L; S[782] = 0L; S[783] = 0x0L;
        S[784] = 0x09d1072e09b72292L; S[785] = 0x90d3ccc99f5ac58bL; S[786] = 0L; S[787] = 0x0L;
        S[788] = 0x6714fe6925b78cc4L; S[789] = 0x91796b31609f0c54L; S[790] = 0L; S[791] = 0x0L;
        S[792] = 0x33d0a284a8c954adL; S[793] = 0x921eafdcc560f9c5L; S[794] = 0L; S[795] = 0x0L;
        S[796] = 0x1f8481e704e4a767L; S[797] = 0x92c39a65db88809dL; S[798] = 0L; S[799] = 0x0L;
        S[800] = 0xb17821911e71c16eL; S[801] = 0x93682a66e896f544L; S[802] = 0L; S[803] = 0x0L;
        S[804] = 0x0001489a97671a42L; S[805] = 0x940c5f7a69e5ce1cL; S[806] = 0L; S[807] = 0x0L;
        S[808] = 0xd6c7af02d5c16fd9L; S[809] = 0x94b0393b14e54156L; S[810] = 0L; S[811] = 0x0L;
        S[812] = 0xac0106650f4ef023L; S[813] = 0x9553b743d75ac03fL; S[814] = 0L; S[815] = 0x0L;
        S[816] = 0xd9f8e1a446e973b9L; S[817] = 0x95f6d92fd79f4fbaL; S[818] = 0L; S[819] = 0x0L;
        S[820] = 0xa7a7556c3b33abc1L; S[821] = 0x96999e9a74ddbde3L; S[822] = 0L; S[823] = 0x0L;
        S[824] = 0xc0a03934f0cce19bL; S[825] = 0x973c071f4750b49cL; S[826] = 0L; S[827] = 0x0L;
        S[828] = 0xd243aa0843a2c144L; S[829] = 0x97de125a2080a8edL; S[830] = 0L; S[831] = 0x0L;
        S[832] = 0x19cec845ac87a5c6L; S[833] = 0x987fbfe70b81a708L; S[834] = 0L; S[835] = 0x0L;
        S[836] = 0xc4b992a37fb9b9bdL; S[837] = 0x99210f624d30facbL; S[838] = 0L; S[839] = 0x0L;
        S[840] = 0x1ab42d43235757b6L; S[841] = 0x99c200686472b4a8L; S[842] = 0L; S[843] = 0x0L;
        S[844] = 0x7e92c655656e6b85L; S[845] = 0x9a6292960a6f0ab0L; S[846] = 0L; S[847] = 0x0L;
        S[848] = 0x698b94f50326a043L; S[849] = 0x9b02c58832cf95c0L; S[850] = 0L; S[851] = 0x0L;
        S[852] = 0x9a5614e8ffbeac6fL; S[853] = 0x9ba298dc0bfc6a88L; S[854] = 0L; S[855] = 0x0L;
        S[856] = 0xc7fd954194e6d8aaL; S[857] = 0x9c420c2eff590e5fL; S[858] = 0L; S[859] = 0x0L;
        S[860] = 0x3e93627de8fd5779L; S[861] = 0x9ce11f1eb18147b1L; S[862] = 0L; S[863] = 0x0L;
        S[864] = 0xe25e39549638ae68L; S[865] = 0x9d7fd1490285c9e3L; S[866] = 0L; S[867] = 0x0L;
        S[868] = 0x2cad377d5c9c35d8L; S[869] = 0x9e1e224c0e28bc94L; S[870] = 0L; S[871] = 0x0L;
        S[872] = 0xcc141e10c6460c8bL; S[873] = 0x9ebc11c62c1a1dfbL; S[874] = 0L; S[875] = 0x0L;
        S[876] = 0xa88d5f46834bbf8dL; S[877] = 0x9f599f55f0340061L; S[878] = 0L; S[879] = 0x0L;
        S[880] = 0x22cc118a0c118aa0L; S[881] = 0x9ff6ca9a2ab6a26dL; S[882] = 0L; S[883] = 0x0L;
        S[884] = 0x7cec6df5bea167cfL; S[885] = 0xa0939331e8846237L; S[886] = 0L; S[887] = 0x0L;
        S[888] = 0x71acea2819360c35L; S[889] = 0xa12ff8bc735d8af6L; S[890] = 0L; S[891] = 0x0L;
        S[892] = 0x166c36e7bb3c402fL; S[893] = 0xa1cbfad9521bfd1bL; S[894] = 0L; S[895] = 0x0L;
        S[896] = 0x3b5167ee359a234eL; S[897] = 0xa267992848eeb0c0L; S[898] = 0L; S[899] = 0x0L;
        S[900] = 0x9443372e20d4377cL; S[901] = 0xa302d34959951243L; S[902] = 0L; S[903] = 0x0L;
        S[904] = 0x0ca9a8a720d4c69cL; S[905] = 0xa39da8dcc39a38e5L; S[906] = 0L; S[907] = 0x0L;
        S[908] = 0xbf623cf5301a2ddeL; S[909] = 0xa4381983048ff747L; S[910] = 0L; S[911] = 0x0L;
        S[912] = 0x23d251cc8d7975ccL; S[913] = 0xa4d224dcd849c5b0L; S[914] = 0L; S[915] = 0x0L;
        S[916] = 0x189d39ffe11aaa2bL; S[917] = 0xa56bca8b391785dbL; S[918] = 0L; S[919] = 0x0L;
        S[920] = 0x8c33ebf3aa8501fbL; S[921] = 0xa6050a2f60002049L; S[922] = 0L; S[923] = 0x0L;
        S[924] = 0x9b3ad6e4022183d9L; S[925] = 0xa69de36ac4fbfadcL; S[926] = 0L; S[927] = 0x0L;
        S[928] = 0x149f6e75993468a3L; S[929] = 0xa73655df1f2f489eL; S[930] = 0L; S[931] = 0x0L;
        S[932] = 0x6b2a39f856a69781L; S[933] = 0xa7ce612e65243291L; S[934] = 0L; S[935] = 0x0L;
        S[936] = 0x3463a2c2e6e9cc55L; S[937] = 0xa86604facd04d969L; S[938] = 0L; S[939] = 0x0L;
        S[940] = 0x6cc14c4f53e2e82dL; S[941] = 0xa8fd40e6ccd52ffdL; S[942] = 0L; S[943] = 0x0L;
        S[944] = 0xd147625fda929af8L; S[945] = 0xa99414951aacae5eL; S[946] = 0L; S[947] = 0x0L;
        S[948] = 0xb714ee81b53b4b9dL; S[949] = 0xaa2a7fa8acefdd63L; S[950] = 0L; S[951] = 0x0L;
        S[952] = 0xe1b3dfc4dbda9bfdL; S[953] = 0xaac081c4ba89ba8aL; S[954] = 0L; S[955] = 0x0L;
        S[956] = 0xf17cee69b0d2ecdeL; S[957] = 0xab561a8cbb24f410L; S[958] = 0L; S[959] = 0x0L;
        S[960] = 0x1becda8089c1a94cL; S[961] = 0xabeb49a46764fd15L; S[962] = 0L; S[963] = 0x0L;
        S[964] = 0xf86ba0dde982fb59L; S[965] = 0xac800eafb91ef9a9L; S[966] = 0L; S[967] = 0x0L;
        S[968] = 0x44bf16268608db96L; S[969] = 0xad146952eb9282afL; S[970] = 0L; S[971] = 0x0L;
        S[972] = 0x9d30d4cfeb04f1fbL; S[973] = 0xada859327ba24151L; S[974] = 0L; S[975] = 0x0L;
        S[976] = 0x3d53817865422565L; S[977] = 0xae3bddf3280c620dL; S[978] = 0L; S[979] = 0x0L;
        S[980] = 0xf74d099042e8f326L; S[981] = 0xaecef739f1a2df10L; S[982] = 0L; S[983] = 0x0L;
        S[984] = 0xa89a9b8f726b95bfL; S[985] = 0xaf61a4ac1b83a1deL; S[986] = 0L; S[987] = 0x0L;
        S[988] = 0x8c679e67fc462d51L; S[989] = 0xaff3e5ef2b507c06L; S[990] = 0L; S[991] = 0x0L;
        S[992] = 0xe4cad00d5c94bcd2L; S[993] = 0xb085baa8e966f6daL; S[994] = 0L; S[995] = 0x0L;
        S[996] = 0x8d8be132d576e614L; S[997] = 0xb117227f6117f9f9L; S[998] = 0L; S[999] = 0x0L;
        S[1000] = 0x24784f32c3e3e5bdL; S[1001] = 0xb1a81d18e0df4889L; S[1002] = 0L; S[1003] = 0x0L;
        S[1004] = 0x8cc7d4bd05ffd5aeL; S[1005] = 0xb238aa1bfa9ad507L; S[1006] = 0L; S[1007] = 0x0L;
        S[1008] = 0xac9f7ebbc469ef59L; S[1009] = 0xb2c8c92f83c1eb87L; S[1010] = 0L; S[1011] = 0x0L;
        S[1012] = 0x5d6635109164f740L; S[1013] = 0xb35879fa959c323cL; S[1014] = 0L; S[1015] = 0x0L;
        S[1016] = 0xa156468ef6c18c60L; S[1017] = 0xb3e7bc248d78802eL; S[1018] = 0L; S[1019] = 0x0L;
        S[1020] = 0x4a85350f69018c55L; S[1021] = 0xb4768f550ce389fdL; S[1022] = 0L; S[1023] = 0x0L;
    }

    /** C: 256 entries; index 4×i = lo, +1 = hi, +2 = ex, +3 = sgn. */
    private static final long[] C = new long[1024];
    static {
        C[0] = 0x0000000000000000L; C[1] = 0x8000000000000000L; C[2] = 1L; C[3] = 0x0L;
        C[4] = 0x3031437d7eccb9dfL; C[5] = 0xffffb10b10e80e95L; C[6] = 0L; C[7] = 0x0L;
        C[8] = 0x38e310779edfec68L; C[9] = 0xfffec42c7454926bL; C[10] = 0L; C[11] = 0x0L;
        C[12] = 0x69fff9ae0dedb047L; C[13] = 0xfffd3964bc6275baL; C[14] = 0L; C[15] = 0x0L;
        C[16] = 0xb47903f7a19f8ee2L; C[17] = 0xfffb10b4dc96dabbL; C[18] = 0L; C[19] = 0x0L;
        C[20] = 0x8cc193c5d508e13fL; C[21] = 0xfff84a1e29de8571L; C[22] = 0L; C[23] = 0x0L;
        C[24] = 0x43366df666fd54ffL; C[25] = 0xfff4e5a25a8d095bL; C[26] = 0L; C[27] = 0x0L;
        C[28] = 0x5428ed0647c9e5d1L; C[29] = 0xfff0e343865bbb13L; C[30] = 0L; C[31] = 0x0L;
        C[32] = 0x5657552366961732L; C[33] = 0xffec4304266865d9L; C[34] = 0L; C[35] = 0x0L;
        C[36] = 0x53aa9423bb0adc21L; C[37] = 0xffe704e71533c508L; C[38] = 0L; C[39] = 0x0L;
        C[40] = 0x7d209f32d42d864eL; C[41] = 0xffe128ef8e9fc17aL; C[42] = 0L; C[43] = 0x0L;
        C[44] = 0x4fd8f038449ec436L; C[45] = 0xffdaaf212fed72dbL; C[46] = 0L; C[47] = 0x0L;
        C[48] = 0x664649b4d541b9c5L; C[49] = 0xffd3977ff7bae4e9L; C[50] = 0L; C[51] = 0x0L;
        C[52] = 0x5595ca3f421ae09cL; C[53] = 0xffcbe2104600a0a9L; C[54] = 0L; C[55] = 0x0L;
        C[56] = 0x1c676208aa3be545L; C[57] = 0xffc38ed6dc0ef98bL; C[58] = 0L; C[59] = 0x0L;
        C[60] = 0xccfed60a91097c48L; C[61] = 0xffba9dd8dc8b1e83L; C[62] = 0L; C[63] = 0x0L;
        C[64] = 0x421e8edaaf59453eL; C[65] = 0xffb10f1bcb6bef1dL; C[66] = 0L; C[67] = 0x0L;
        C[68] = 0xd2c665c2da3e7844L; C[69] = 0xffa6e2a58df6947dL; C[70] = 0L; C[71] = 0x0L;
        C[72] = 0x1e1862cca089938bL; C[73] = 0xff9c187c6abade6aL; C[74] = 0L; C[75] = 0x0L;
        C[76] = 0x2dabd3195a05710fL; C[77] = 0xff90b0a7098f6443L; C[78] = 0L; C[79] = 0x0L;
        C[80] = 0x519c314973ccae6bL; C[81] = 0xff84ab2c738d6a03L; C[82] = 0L; C[83] = 0x0L;
        C[84] = 0x3ea4f30adda3016fL; C[85] = 0xff780814130c893cL; C[86] = 0L; C[87] = 0x0L;
        C[88] = 0x1b9d5851979f28fbL; C[89] = 0xff6ac765b39e1e19L; C[90] = 0L; C[91] = 0x0L;
        C[92] = 0x50a7bb6a6ee3b0f1L; C[93] = 0xff5ce92982087867L; C[94] = 0L; C[95] = 0x0L;
        C[96] = 0x0f668633f1ab858aL; C[97] = 0xff4e6d680c41d0a9L; C[98] = 0L; C[99] = 0x0L;
        C[100] = 0xb085c1828f69296aL; C[101] = 0xff3f542a416b0134L; C[102] = 0L; C[103] = 0x0L;
        C[104] = 0x27e31939e2eec09cL; C[105] = 0xff2f9d7971ca0364L; C[106] = 0L; C[107] = 0x0L;
        C[108] = 0xf5971326a3540ea9L; C[109] = 0xff1f495f4ec430d7L; C[110] = 0L; C[111] = 0x0L;
        C[112] = 0x1f1901544271c3f8L; C[113] = 0xff0e57e5ead848d1L; C[114] = 0L; C[115] = 0x0L;
        C[116] = 0xe0abd3a9b64df725L; C[117] = 0xfefcc917b99839a5L; C[118] = 0L; C[119] = 0x0L;
        C[120] = 0xec34413e87ef2740L; C[121] = 0xfeea9cff8fa2ae54L; C[122] = 0L; C[123] = 0x0L;
        C[124] = 0x2f88b949a72ff96cL; C[125] = 0xfed7d3a8a29c603bL; C[126] = 0L; C[127] = 0x0L;
        C[128] = 0x41390efdc726e9efL; C[129] = 0xfec46d1e89292cf0L; C[130] = 0L; C[131] = 0x0L;
        C[132] = 0xb7b6cc53c3abc817L; C[133] = 0xfeb0696d3ae4f04dL; C[134] = 0L; C[135] = 0x0L;
        C[136] = 0xd3af6ee4f2101c20L; C[137] = 0xfe9bc8a1105c22a5L; C[138] = 0L; C[139] = 0x0L;
        C[140] = 0x0b4f70c910505e10L; C[141] = 0xfe868ac6c3043b2eL; C[142] = 0L; C[143] = 0x0L;
        C[144] = 0x2907cf2b3f6feac2L; C[145] = 0xfe70afeb6d33d6a2L; C[146] = 0L; C[147] = 0x0L;
        C[148] = 0xd54faa364b7da8f6L; C[149] = 0xfe5a381c8a1aa224L; C[150] = 0L; C[151] = 0x0L;
        C[152] = 0x87b8875373a818a4L; C[153] = 0xfe432367f5b90a62L; C[154] = 0L; C[155] = 0x0L;
        C[156] = 0x008598c2c429caf7L; C[157] = 0xfe2b71dbecd7aefcL; C[158] = 0L; C[159] = 0x0L;
        C[160] = 0x90cd1d959db674efL; C[161] = 0xfe1323870cfe9a3dL; C[162] = 0L; C[163] = 0x0L;
        C[164] = 0x9bfe5c51e91cbdcdL; C[165] = 0xfdfa3878546c3d28L; C[166] = 0L; C[167] = 0x0L;
        C[168] = 0xe276d247626a23fdL; C[169] = 0xfde0b0bf220c2fd4L; C[170] = 0L; C[171] = 0x0L;
        C[172] = 0x499ddb331d19539dL; C[173] = 0xfdc68c6b356db62fL; C[174] = 0L; C[175] = 0x0L;
        C[176] = 0xfac7397cc07a6470L; C[177] = 0xfdabcb8caeba091bL; C[178] = 0L; C[179] = 0x0L;
        C[180] = 0xd6e270740a186977L; C[181] = 0xfd906e340eaa6401L; C[182] = 0L; C[183] = 0x0L;
        C[184] = 0x61beb8cd2696fc78L; C[185] = 0xfd747472367dd6c5L; C[186] = 0L; C[187] = 0x0L;
        C[188] = 0x6c696582f346fd91L; C[189] = 0xfd57de5867eedc39L; C[190] = 0L; C[191] = 0x0L;
        C[192] = 0xeae6bd951c1dabbeL; C[193] = 0xfd3aabf84528b50bL; C[194] = 0L; C[195] = 0x0L;
        C[196] = 0x863b87258f11ad7eL; C[197] = 0xfd1cdd63d0bc8735L; C[198] = 0L; C[199] = 0x0L;
        C[200] = 0xa06fab9f9d106709L; C[201] = 0xfcfe72ad6d9641f2L; C[202] = 0L; C[203] = 0x0L;
        C[204] = 0xa4e064308f4999f4L; C[205] = 0xfcdf6be7def1464cL; C[206] = 0L; C[207] = 0x0L;
        C[208] = 0xa3e22b4d38917e73L; C[209] = 0xfcbfc926484cd43aL; C[210] = 0L; C[211] = 0x0L;
        C[212] = 0x5d582cac7cb4391cL; C[213] = 0xfc9f8a7c2d603c60L; C[214] = 0L; C[215] = 0x0L;
        C[216] = 0x02880268f2e62955L; C[217] = 0xfc7eaffd720ed673L; C[218] = 0L; C[219] = 0x0L;
        C[220] = 0x1c0d254b6c8da4bdL; C[221] = 0xfc5d39be5a5bbc4bL; C[222] = 0L; C[223] = 0x0L;
        C[224] = 0x256778ffcb5c1769L; C[225] = 0xfc3b27d38a5d49abL; C[226] = 0L; C[227] = 0x0L;
        C[228] = 0x9433b49289417ea2L; C[229] = 0xfc187a52063060c2L; C[230] = 0L; C[231] = 0x0L;
        C[232] = 0x25aafd7fdba12c5fL; C[233] = 0xfbf5314f31eb7375L; C[234] = 0L; C[235] = 0x0L;
        C[236] = 0x7190c94899dff1b8L; C[237] = 0xfbd14ce0d191516eL; C[238] = 0L; C[239] = 0x0L;
        C[240] = 0xe63ae8632b84473cL; C[241] = 0xfbaccd1d0903bb09L; C[242] = 0L; C[243] = 0x0L;
        C[244] = 0x75df66f0ec3dd459L; C[245] = 0xfb87b21a5bf5b917L; C[246] = 0L; C[247] = 0x0L;
        C[248] = 0x61ce9d5ef5a81487L; C[249] = 0xfb61fbefadddb985L; C[250] = 0L; C[251] = 0x0L;
        C[252] = 0xb4b54683879c9c17L; C[253] = 0xfb3baab441e770f7L; C[254] = 0L; C[255] = 0x0L;
        C[256] = 0x2172a361fd2a722fL; C[257] = 0xfb14be7fbae58156L; C[258] = 0L; C[259] = 0x0L;
        C[260] = 0x2079880c450348acL; C[261] = 0xfaed376a1b42e559L; C[262] = 0L; C[263] = 0x0L;
        C[264] = 0x4a188aa367f90ab1L; C[265] = 0xfac5158bc4f4211fL; C[266] = 0L; C[267] = 0x0L;
        C[268] = 0x10655ecd5cc771d8L; C[269] = 0xfa9c58fd796837d4L; C[270] = 0L; C[271] = 0x0L;
        C[272] = 0x1fe196a53fb5b237L; C[273] = 0xfa7301d859796671L; C[274] = 0L; C[275] = 0x0L;
        C[276] = 0xd24377c77a591e24L; C[277] = 0xfa491035e55da3a3L; C[278] = 0L; C[279] = 0x0L;
        C[280] = 0x431c393c7f62da65L; C[281] = 0xfa1e842ffc96e4e0L; C[282] = 0L; C[283] = 0x0L;
        C[284] = 0xba5dbf4510eddc8fL; C[285] = 0xf9f35de0dde328abL; C[286] = 0L; C[287] = 0x0L;
        C[288] = 0x4504ae08d19b2980L; C[289] = 0xf9c79d63272c4628L; C[290] = 0L; C[291] = 0x0L;
        C[292] = 0x78685d850f80ecdcL; C[293] = 0xf99b42d1d57781ebL; C[294] = 0L; C[295] = 0x0L;
        C[296] = 0x80e8c17bf80e8f02L; C[297] = 0xf96e4e4844d4e82aL; C[298] = 0L; C[299] = 0x0L;
        C[300] = 0xc0e2a1352ed7f292L; C[301] = 0xf940bfe2304e6c45L; C[302] = 0L; C[303] = 0x0L;
        C[304] = 0x68fc6e4d6a920bd2L; C[305] = 0xf91297bbb1d6cdbeL; C[306] = 0L; C[307] = 0x0L;
        C[308] = 0x9701914c7f8fbcd7L; C[309] = 0xf8e3d5f1423842a0L; C[310] = 0L; C[311] = 0x0L;
        C[312] = 0xac9f07f54ff5bc14L; C[313] = 0xf8b47a9fb902e76cL; C[314] = 0L; C[315] = 0x0L;
        C[316] = 0xb36a9dfaadafc1e1L; C[317] = 0xf88485e44c7af48aL; C[318] = 0L; C[319] = 0x0L;
        C[320] = 0xc7adc6b4988891bbL; C[321] = 0xf853f7dc9186b952L; C[322] = 0L; C[323] = 0x0L;
        C[324] = 0xa776175bd284fe05L; C[325] = 0xf822d0a67b9c5cb5L; C[326] = 0L; C[327] = 0x0L;
        C[328] = 0xa76f7efc19aed41cL; C[329] = 0xf7f110605caf6390L; C[330] = 0L; C[331] = 0x0L;
        C[332] = 0x730785813f78aa1eL; C[333] = 0xf7beb728e51dfcb8L; C[334] = 0L; C[335] = 0x0L;
        C[336] = 0x214cffcee9dd33caL; C[337] = 0xf78bc51f239e12c6L; C[338] = 0L; C[339] = 0x0L;
        C[340] = 0x4becad887680c197L; C[341] = 0xf7583a62852a23b2L; C[342] = 0L; C[343] = 0x0L;
        C[344] = 0xf99107e50d631330L; C[345] = 0xf7241712d4edde49L; C[346] = 0L; C[347] = 0x0L;
        C[348] = 0x50ca117eb18beed7L; C[349] = 0xf6ef5b503c328589L; C[350] = 0L; C[351] = 0x0L;
        C[352] = 0x2c791f59cc1ffc23L; C[353] = 0xf6ba073b424b19e8L; C[354] = 0L; C[355] = 0x0L;
        C[356] = 0xce8c455197cdf8a7L; C[357] = 0xf6841af4cc8048a4L; C[358] = 0L; C[359] = 0x0L;
        C[360] = 0x119d358de0493956L; C[361] = 0xf64d969e1dfc2119L; C[362] = 0L; C[363] = 0x0L;
        C[364] = 0x9dc7e5954c5a8f24L; C[365] = 0xf6167a58d7b59026L; C[366] = 0L; C[367] = 0x0L;
        C[368] = 0xc8c615e72768d6b5L; C[369] = 0xf5dec646f85ba1c6L; C[370] = 0L; C[371] = 0x0L;
        C[372] = 0xed0dd4bf62edd13fL; C[373] = 0xf5a67a8adc4088caL; C[374] = 0L; C[375] = 0x0L;
        C[376] = 0x275a2bbb2bab6c8aL; C[377] = 0xf56d97473d446cdaL; C[378] = 0L; C[379] = 0x0L;
        C[380] = 0x8da64484aaa0febcL; C[381] = 0xf5341c9f32bffeb9L; C[382] = 0L; C[383] = 0x0L;
        C[384] = 0x163c5c7f03b718c5L; C[385] = 0xf4fa0ab6316ed2ecL; C[386] = 0L; C[387] = 0x0L;
        C[388] = 0x890ac4aafa6a37bfL; C[389] = 0xf4bf61b00b5982b7L; C[390] = 0L; C[391] = 0x0L;
        C[392] = 0xf8f9d3b87d11fd52L; C[393] = 0xf48421b0efbf939bL; C[394] = 0L; C[395] = 0x0L;
        C[396] = 0x667e06866c07c369L; C[397] = 0xf4484add6b01254bL; C[398] = 0L; C[399] = 0x0L;
        C[400] = 0x5019794a1f5896e5L; C[401] = 0xf40bdd5a6688662fL; C[402] = 0L; C[403] = 0x0L;
        C[404] = 0x18ef535a7ffa7a3dL; C[405] = 0xf3ced94d28b2ce8aL; C[406] = 0L; C[407] = 0x0L;
        C[408] = 0x50f29b4b49f31c37L; C[409] = 0xf3913edb54ba2242L; C[410] = 0L; C[411] = 0x0L;
        C[412] = 0x0d981acdcf6bc3e4L; C[413] = 0xf3530e2aea9d3966L; C[414] = 0L; C[415] = 0x0L;
        C[416] = 0xa5486bdc455d56a2L; C[417] = 0xf314476247088f74L; C[418] = 0L; C[419] = 0x0L;
        C[420] = 0x431be53f92ece9e6L; C[421] = 0xf2d4eaa8233e997dL; C[422] = 0L; C[423] = 0x0L;
        C[424] = 0xebadcdbf915e8f6cL; C[425] = 0xf294f82394ffe320L; C[426] = 0L; C[427] = 0x0L;
        C[428] = 0xaf0eed81e8c51e55L; C[429] = 0xf2546ffc0e72f286L; C[430] = 0L; C[431] = 0x0L;
        C[432] = 0xe7112e89103cc0c7L; C[433] = 0xf21352595e0bf350L; C[434] = 0L; C[435] = 0x0L;
        C[436] = 0x844e6a35ddc2b713L; C[437] = 0xf1d19f63ae7428a2L; C[438] = 0L; C[439] = 0x0L;
        C[440] = 0x8f6bac72988088b0L; C[441] = 0xf18f574386712643L; C[442] = 0L; C[443] = 0x0L;
        C[444] = 0x2730081c758fb42bL; C[445] = 0xf14c7a21c8cbd0f4L; C[446] = 0L; C[447] = 0x0L;
        C[448] = 0x67127db35b287316L; C[449] = 0xf1090827b43725fdL; C[450] = 0L; C[451] = 0x0L;
        C[452] = 0xc4e557b119ef3185L; C[453] = 0xf0c5017ee336ca0fL; C[454] = 0L; C[455] = 0x0L;
        C[456] = 0x973ea9903ed5125fL; C[457] = 0xf08066514c055f7eL; C[458] = 0L; C[459] = 0x0L;
        C[460] = 0x992d39ec5c561d28L; C[461] = 0xf03b36c9407aa3e8L; C[462] = 0L; C[463] = 0x0L;
        C[464] = 0x62aef7b55319d1d4L; C[465] = 0xeff573116df1555dL; C[466] = 0L; C[467] = 0x0L;
        C[468] = 0xf03a18a5e16ab641L; C[469] = 0xefaf1b54dd2cdf0fL; C[470] = 0L; C[471] = 0x0L;
        C[472] = 0x767c0e8ad33bc085L; C[473] = 0xef682fbef23ecda6L; C[474] = 0L; C[475] = 0x0L;
        C[476] = 0xe2398bf0eeb28cdeL; C[477] = 0xef20b07b6c6c0b37L; C[478] = 0L; C[479] = 0x0L;
        C[480] = 0x86f8c20fb664b01bL; C[481] = 0xeed89db66611e307L; C[482] = 0L; C[483] = 0x0L;
        C[484] = 0xa1d2c3d018a9279fL; C[485] = 0xee8ff79c548acd0fL; C[486] = 0L; C[487] = 0x0L;
        C[488] = 0x7872773830d368beL; C[489] = 0xee46be5a0813016bL; C[490] = 0L; C[491] = 0x0L;
        C[492] = 0xfee6a1eebfa13b4aL; C[493] = 0xedfcf21cabacd3b1L; C[494] = 0L; C[495] = 0x0L;
        C[496] = 0x11815196b9fbf5dfL; C[497] = 0xedb29311c504d652L; C[498] = 0L; C[499] = 0x0L;
        C[500] = 0x7289102076a125e5L; C[501] = 0xed67a1673455c601L; C[502] = 0L; C[503] = 0x0L;
        C[504] = 0xddffe98c4f8aa031L; C[505] = 0xed1c1d4b344c3d4fL; C[506] = 0L; C[507] = 0x0L;
        C[508] = 0xa8392eb238578ab0L; C[509] = 0xecd006ec59ea306fL; C[510] = 0L; C[511] = 0x0L;
        C[512] = 0x7e610231ac1d6181L; C[513] = 0xec835e79946a3145L; C[514] = 0L; C[515] = 0x0L;
        C[516] = 0x0278047ae3dd0889L; C[517] = 0xec3624222d227bd1L; C[518] = 0L; C[519] = 0x0L;
        C[520] = 0x1e99ccb9adc62ca6L; C[521] = 0xebe85815c767cb00L; C[522] = 0L; C[523] = 0x0L;
        C[524] = 0x0dae311e656e0661L; C[525] = 0xeb99fa84606ff5ffL; C[526] = 0L; C[527] = 0x0L;
        C[528] = 0x39e39c6c2ab3655dL; C[529] = 0xeb4b0b9e4f345617L; C[530] = 0L; C[531] = 0x0L;
        C[532] = 0x3383bbb5156bf1d7L; C[533] = 0xeafb8b944453f52fL; C[534] = 0L; C[535] = 0x0L;
        C[536] = 0x24db98ad3a0647a1L; C[537] = 0xeaab7a9749f584feL; C[538] = 0L; C[539] = 0x0L;
        C[540] = 0x4a0ca5ea449b1c83L; C[541] = 0xea5ad8d8c3a91f05L; C[542] = 0L; C[543] = 0x0L;
        C[544] = 0x15ad45b4a1b5e823L; C[545] = 0xea09a68a6e49cd62L; C[546] = 0L; C[547] = 0x0L;
        C[548] = 0xcd24d4bd1056c826L; C[549] = 0xe9b7e3de5fdedc8bL; C[550] = 0L; C[551] = 0x0L;
        C[552] = 0x89a92b199adfbafaL; C[553] = 0xe9659107077cf60fL; C[554] = 0L; C[555] = 0x0L;
        C[556] = 0xacb1c26a06e5ae02L; C[557] = 0xe912ae372d27045dL; C[558] = 0L; C[559] = 0x0L;
        C[560] = 0xf8972affb3d98e1fL; C[561] = 0xe8bf3ba1f1aedfbbL; C[562] = 0L; C[563] = 0x0L;
        C[564] = 0x9fec1e78c4376186L; C[565] = 0xe86b397ace95c46fL; C[566] = 0L; C[567] = 0x0L;
        C[568] = 0xbfe8378abfb87b6fL; C[569] = 0xe816a7f595ec9232L; C[570] = 0L; C[571] = 0x0L;
        C[572] = 0xdbfb0fe56c6f80feL; C[573] = 0xe7c187467233d508L; C[574] = 0L; C[575] = 0x0L;
        C[576] = 0x125129529d48a92fL; C[577] = 0xe76bd7a1e63b9786L; C[578] = 0L; C[579] = 0x0L;
        C[580] = 0xe2ba81b9ce96e02eL; C[581] = 0xe715993ccd02fe9cL; C[582] = 0L; C[583] = 0x0L;
        C[584] = 0x82fcedb4c6434d76L; C[585] = 0xe6becc4c5997af06L; C[586] = 0L; C[587] = 0x0L;
        C[588] = 0xdd2a3e32c3859960L; C[589] = 0xe667710616f4fc59L; C[590] = 0L; C[591] = 0x0L;
        C[592] = 0x7613b68f6ab03130L; C[593] = 0xe60f879fe7e2e1e5L; C[594] = 0L; C[595] = 0x0L;
        C[596] = 0x9b695cd67c93bd79L; C[597] = 0xe5b7105006d4c560L; C[598] = 0L; C[599] = 0x0L;
        C[600] = 0x5a7c210a3a15e7eaL; C[601] = 0xe55e0b4d05c80388L; C[602] = 0L; C[603] = 0x0L;
        C[604] = 0xe1f5a58c80292554L; C[605] = 0xe50478cdce2246bcL; C[606] = 0L; C[607] = 0x0L;
        C[608] = 0x122785ae67f5515dL; C[609] = 0xe4aa5909a08fa7b4L; C[610] = 0L; C[611] = 0x0L;
        C[612] = 0x20d63b5b9e3cd6acL; C[613] = 0xe44fac3814e09856L; C[614] = 0L; C[615] = 0x0L;
        C[616] = 0x56992551ae074e99L; C[617] = 0xe3f4729119e798d9L; C[618] = 0L; C[619] = 0x0L;
        C[620] = 0x0d1197dc12c63176L; C[621] = 0xe398ac4cf556b732L; C[622] = 0L; C[623] = 0x0L;
        C[624] = 0x36563e2ffad8351aL; C[625] = 0xe33c59a4439cd8ecL; C[626] = 0L; C[627] = 0x0L;
        C[628] = 0xd6fe4dd22e60a4a2L; C[629] = 0xe2df7acff7c2cf83L; C[630] = 0L; C[631] = 0x0L;
        C[632] = 0xfd39138aa2d508edL; C[633] = 0xe28210095b483751L; C[634] = 0L; C[635] = 0x0L;
        C[636] = 0xe0521df01a1be6f5L; C[637] = 0xe224198a0e002123L; C[638] = 0L; C[639] = 0x0L;
        C[640] = 0xf4e8a8372f8c5810L; C[641] = 0xe1c5978c05ed8691L; C[642] = 0L; C[643] = 0x0L;
        C[644] = 0xe2f9d4600f4d0325L; C[645] = 0xe1668a498f1f892cL; C[646] = 0L; C[647] = 0x0L;
        C[648] = 0x6ba8a9d9ba877899L; C[649] = 0xe106f1fd4b8d7c96L; C[650] = 0L; C[651] = 0x0L;
        C[652] = 0x6d6c98fe79817946L; C[653] = 0xe0a6cee232f2bb9cL; C[654] = 0L; C[655] = 0x0L;
        C[656] = 0x55ff6038a5197367L; C[657] = 0xe046213392aa486cL; C[658] = 0L; C[659] = 0x0L;
        C[660] = 0x720588ff6547d884L; C[661] = 0xdfe4e92d0d8a37f5L; C[662] = 0L; C[663] = 0x0L;
        C[664] = 0xab01350f013d78ddL; C[665] = 0xdf83270a9bbee890L; C[666] = 0L; C[667] = 0x0L;
        C[668] = 0x64a58b2f103485ddL; C[669] = 0xdf20db088aa60404L; C[670] = 0L; C[671] = 0x0L;
        C[672] = 0x4b19aa71fec3ae6dL; C[673] = 0xdebe05637ca94cfbL; C[674] = 0L; C[675] = 0x0L;
        C[676] = 0x04248f15548f69caL; C[677] = 0xde5aa65869193805L; C[678] = 0L; C[679] = 0x0L;
        C[680] = 0xd597b10a01676659L; C[681] = 0xddf6be249c075037L; C[682] = 0L; C[683] = 0x0L;
        C[684] = 0x739c45b982193b5eL; C[685] = 0xdd924d05b620678aL; C[686] = 0L; C[687] = 0x0L;
        C[688] = 0x49c6e0ea76cbcaacL; C[689] = 0xdd2d5339ac8692fdL; C[690] = 0L; C[691] = 0x0L;
        C[692] = 0xb2069fd0b482b4e8L; C[693] = 0xdcc7d0fec8aaf2aaL; C[694] = 0L; C[695] = 0x0L;
        C[696] = 0xaca8017e375b64e5L; C[697] = 0xdc61c693a82745d5L; C[698] = 0L; C[699] = 0x0L;
        C[700] = 0xccb7fd40d543f4a1L; C[701] = 0xdbfb34373c974b0eL; C[702] = 0L; C[703] = 0x0L;
        C[704] = 0x2c19b63253da43fcL; C[705] = 0xdb941a28cb71ec87L; C[706] = 0L; C[707] = 0x0L;
        C[708] = 0x5a98479cbef2ecbcL; C[709] = 0xdb2c78a7ede238a9L; C[710] = 0L; C[711] = 0x0L;
        C[712] = 0x5b267c1bcff0ab62L; C[713] = 0xdac44ff490a02710L; C[714] = 0L; C[715] = 0x0L;
        C[716] = 0xe257bde73d83dc1aL; C[717] = 0xda5ba04ef3c929f4L; C[718] = 0L; C[719] = 0x0L;
        C[720] = 0x28e81dcb6dab91acL; C[721] = 0xd9f269f7aab88c29L; C[722] = 0L; C[723] = 0x0L;
        C[724] = 0xc4e4dc69fc2fff6fL; C[725] = 0xd988ad2f9bdf9bbbL; C[726] = 0L; C[727] = 0x0L;
        C[728] = 0x1bb35ad6d2e74b67L; C[729] = 0xd91e6a38009da15aL; C[730] = 0L; C[731] = 0x0L;
        C[732] = 0x1ed1a8ff78f1b632L; C[733] = 0xd8b3a1526517a48bL; C[734] = 0L; C[735] = 0x0L;
        C[736] = 0x24b9fe00663574a4L; C[737] = 0xd84852c0a80ffcdbL; C[738] = 0L; C[739] = 0x0L;
        C[740] = 0xced12d2899b803dbL; C[741] = 0xd7dc7ec4fabdb011L; C[742] = 0L; C[743] = 0x0L;
        C[744] = 0x0cb78e80e67ba1b8L; C[745] = 0xd77025a1e0a39d8bL; C[746] = 0L; C[747] = 0x0L;
        C[748] = 0x6cb3bfd65b38562bL; C[749] = 0xd703479a2f6776ccL; C[750] = 0L; C[751] = 0x0L;
        C[752] = 0x083f082b570611d7L; C[753] = 0xd695e4f10ea88570L; C[754] = 0L; C[755] = 0x0L;
        C[756] = 0x7afbefc05e9f7d99L; C[757] = 0xd627fde9f7d63e7eL; C[758] = 0L; C[759] = 0x0L;
        C[760] = 0x7190b755535d4f18L; C[761] = 0xd5b992c8b606a351L; C[762] = 0L; C[763] = 0x0L;
        C[764] = 0x7d00ae97abaa4096L; C[765] = 0xd54aa3d165cc7018L; C[766] = 0L; C[767] = 0x0L;
        C[768] = 0xf630e8b6dac83e69L; C[769] = 0xd4db3148750d1819L; C[770] = 0L; C[771] = 0x0L;
        C[772] = 0xdc4663a3168698d2L; C[773] = 0xd46b3b72a2d68fc9L; C[774] = 0L; C[775] = 0x0L;
        C[776] = 0xb77d4f6bd0ee8591L; C[777] = 0xd3fac294ff34e4d0L; C[778] = 0L; C[779] = 0x0L;
        C[780] = 0xa8faac741a6394dcL; C[781] = 0xd389c6f4eb07a41cL; C[782] = 0L; C[783] = 0x0L;
        C[784] = 0xeeeaddb72f00e0ddL; C[785] = 0xd31848d817d70e16L; C[786] = 0L; C[787] = 0x0L;
        C[788] = 0x4300fd1c1ce507e5L; C[789] = 0xd2a6488487a91918L; C[790] = 0L; C[791] = 0x0L;
        C[792] = 0x981ba7e42537275fL; C[793] = 0xd233c6408cd64236L; C[794] = 0L; C[795] = 0x0L;
        C[796] = 0xda7485a5aeffeb4cL; C[797] = 0xd1c0c252c9de2c86L; C[798] = 0L; C[799] = 0x0L;
        C[800] = 0x744fea20e8abef92L; C[801] = 0xd14d3d02313c0eedL; C[802] = 0L; C[803] = 0x0L;
        C[804] = 0x77a18eb13d2ecde5L; C[805] = 0xd0d93696053af098L; C[806] = 0L; C[807] = 0x0L;
        C[808] = 0x6b8a685f6cb61c21L; C[809] = 0xd064af55d7c9b43eL; C[810] = 0L; C[811] = 0x0L;
        C[812] = 0xdaf200dd81212d10L; C[813] = 0xcfefa7898a4ef23cL; C[814] = 0L; C[815] = 0x0L;
        C[816] = 0xdfcb60445c1bf973L; C[817] = 0xcf7a1f794d7ca1b1L; C[818] = 0L; C[819] = 0x0L;
        C[820] = 0x04d27090f10c454eL; C[821] = 0xcf04176da12390acL; C[822] = 0L; C[823] = 0x0L;
        C[824] = 0xf5babff66def7892L; C[825] = 0xce8d8faf5406ab8bL; C[826] = 0L; C[827] = 0x0L;
        C[828] = 0x93e391861a034684L; C[829] = 0xce16888783ae13b3L; C[830] = 0L; C[831] = 0x0L;
        C[832] = 0x23af31db7179a4aaL; C[833] = 0xcd9f023f9c3a059eL; C[834] = 0L; C[835] = 0x0L;
        C[836] = 0x649474e36b8db9d3L; C[837] = 0xcd26fd2158358e7dL; C[838] = 0L; C[839] = 0x0L;
        C[840] = 0x83e907fbd7aaf0b0L; C[841] = 0xccae7976c0691177L; C[842] = 0L; C[843] = 0x0L;
        C[844] = 0xf839ce18e08bfb50L; C[845] = 0xcc35778a2bac9ca1L; C[846] = 0L; C[847] = 0x0L;
        C[848] = 0x70cbb7f3343451beL; C[849] = 0xcbbbf7a63eba0dd5L; C[850] = 0L; C[851] = 0x0L;
        C[852] = 0x2293661be51140abL; C[853] = 0xcb41fa15ebff0777L; C[854] = 0L; C[855] = 0x0L;
        C[856] = 0xd9944be1631846d8L; C[857] = 0xcac77f24736eb553L; C[858] = 0L; C[859] = 0x0L;
        C[860] = 0x5328edeb3e6784deL; C[861] = 0xca4c871d625361a9L; C[862] = 0L; C[863] = 0x0L;
        C[864] = 0x8335241be1693225L; C[865] = 0xc9d1124c931fda7aL; C[866] = 0L; C[867] = 0x0L;
        C[868] = 0x83b0e96e1249c2b0L; C[869] = 0xc95520fe2d40a74bL; C[870] = 0L; C[871] = 0x0L;
        C[872] = 0x0b562c00b34ee771L; C[873] = 0xc8d8b37ea4ed0f62L; C[874] = 0L; C[875] = 0x0L;
        C[876] = 0x65862939b83382e0L; C[877] = 0xc85bca1abaf7f0a7L; C[878] = 0L; C[879] = 0x0L;
        C[880] = 0x02b31bc86877fd2cL; C[881] = 0xc7de651f7ca06749L; C[882] = 0L; C[883] = 0x0L;
        C[884] = 0xd5c149509e9059f1L; C[885] = 0xc76084da43624634L; C[886] = 0L; C[887] = 0x0L;
        C[888] = 0xcfe6c1b1a6b4e2a4L; C[889] = 0xc6e22998b4c6608eL; C[890] = 0L; C[891] = 0x0L;
        C[892] = 0xe993503baf5afb41L; C[893] = 0xc66353a8c232a43cL; C[894] = 0L; C[895] = 0x0L;
        C[896] = 0x43da25d99267326bL; C[897] = 0xc5e40358a8ba05a7L; C[898] = 0L; C[899] = 0x0L;
        C[900] = 0x0ab4906075507e74L; C[901] = 0xc56438f6f0ec3ccaL; C[902] = 0L; C[903] = 0x0L;
        C[904] = 0xdd40950cf1ed92faL; C[905] = 0xc4e3f4d26ea553b6L; C[906] = 0L; C[907] = 0x0L;
        C[908] = 0x9dd768f30ca8e85cL; C[909] = 0xc463373a40dd06a3L; C[910] = 0L; C[911] = 0x0L;
        C[912] = 0xa87e78136665cdb2L; C[913] = 0xc3e2007dd175f5a4L; C[914] = 0L; C[915] = 0x0L;
        C[916] = 0x8ac9e1386e4cbabbL; C[917] = 0xc36050ecd50ca830L; C[918] = 0L; C[919] = 0x0L;
        C[920] = 0x74c8f010d986a9e0L; C[921] = 0xc2de28d74ac6628bL; C[922] = 0L; C[923] = 0x0L;
        C[924] = 0xb7041e9bc8c18b0dL; C[925] = 0xc25b888d7c1fcd38L; C[926] = 0L; C[927] = 0x0L;
        C[928] = 0xbdf0715cb8b20bd7L; C[929] = 0xc1d8705ffcbb6e90L; C[930] = 0L; C[931] = 0x0L;
        C[932] = 0x17858573216e0a22L; C[933] = 0xc154e09faa2ff69aL; C[934] = 0L; C[935] = 0x0L;
        C[936] = 0x2bda5328933c854aL; C[937] = 0xc0d0d99dabd65d44L; C[938] = 0L; C[939] = 0x0L;
        C[940] = 0x6dd06968e0ed1957L; C[941] = 0xc04c5bab7297d322L; C[942] = 0L; C[943] = 0x0L;
        C[944] = 0xe4e62d86dd136e78L; C[945] = 0xbfc7671ab8bb84c6L; C[946] = 0L; C[947] = 0x0L;
        C[948] = 0x0d46655d6b012455L; C[949] = 0xbf41fc3d81b430dbL; C[950] = 0L; C[951] = 0x0L;
        C[952] = 0x2715ef03f8543355L; C[953] = 0xbebc1b6619ed9116L; C[954] = 0L; C[955] = 0x0L;
        C[956] = 0x29d7f7b67d43b177L; C[957] = 0xbe35c4e716999630L; C[958] = 0L; C[959] = 0x0L;
        C[960] = 0xac85320f528d6d5dL; C[961] = 0xbdaef913557d76f0L; C[962] = 0L; C[963] = 0x0L;
        C[964] = 0x2ea36923d5d8e213L; C[965] = 0xbd27b83dfcbe9279L; C[966] = 0L; C[967] = 0x0L;
        C[968] = 0x4a48496734be336dL; C[969] = 0xbca002ba7aaf25eaL; C[970] = 0L; C[971] = 0x0L;
        C[972] = 0x727c405ffc73af56L; C[973] = 0xbc17d8dc859ad583L; C[974] = 0L; C[975] = 0x0L;
        C[976] = 0xfce8d84068e825b6L; C[977] = 0xbb8f3af81b93095cL; C[978] = 0L; C[979] = 0x0L;
        C[980] = 0x5120e35e1c1a250cL; C[981] = 0xbb062961823b1ddcL; C[982] = 0L; C[983] = 0x0L;
        C[984] = 0x33201477347447d8L; C[985] = 0xba7ca46d46946802L; C[986] = 0L; C[987] = 0x0L;
        C[988] = 0x39db32d014440024L; C[989] = 0xb9f2ac703cca0db3L; C[990] = 0L; C[991] = 0x0L;
        C[992] = 0x9de1e3b22b8bf4dbL; C[993] = 0xb96841bf7ffcb21aL; C[994] = 0L; C[995] = 0x0L;
        C[996] = 0xa726f4f0828585c9L; C[997] = 0xb8dd64b0720df647L; C[998] = 0L; C[999] = 0x0L;
        C[1000] = 0x1c041d1ea5fb3fdbL; C[1001] = 0xb8521598bb6bce26L; C[1002] = 0L; C[1003] = 0x0L;
        C[1004] = 0x2e7a35723f3ed035L; C[1005] = 0xb7c654ce4adba9f2L; C[1006] = 0L; C[1007] = 0x0L;
        C[1008] = 0x7f86f63bb23f496aL; C[1009] = 0xb73a22a755457448L; C[1010] = 0L; C[1011] = 0x0L;
        C[1012] = 0xeb2d28ef943dc88cL; C[1013] = 0xb6ad7f7a557e64f2L; C[1014] = 0L; C[1015] = 0x0L;
        C[1016] = 0xea7c015f12b987f7L; C[1017] = 0xb6206b9e0c13a892L; C[1018] = 0L; C[1019] = 0x0L;
        C[1020] = 0x737dd2824b608d13L; C[1021] = 0xb592e7697f14dd4aL; C[1022] = 0L; C[1023] = 0x0L;
    }

    private static final double[] PSfast = new double[5];
    static {
        PSfast[0] = Double.longBitsToDouble(0x401921fb54442d18L);
        PSfast[1] = Double.longBitsToDouble(0x3cb1a62645446203L);
        PSfast[2] = Double.longBitsToDouble(0xc044abbce625be53L);
        PSfast[3] = Double.longBitsToDouble(0x405466bc678d8d63L);
        PSfast[4] = Double.longBitsToDouble(0xc05331554ca19669L);
    }

    private static final double[] PCfast = new double[5];
    static {
        PCfast[0] = Double.longBitsToDouble(0x3ff0000000000000L);
        PCfast[1] = Double.longBitsToDouble(0xbb2923015c000000L);
        PCfast[2] = Double.longBitsToDouble(0xc033bd3cc9be45deL);
        PCfast[3] = Double.longBitsToDouble(0x40503c1f080ad892L);
        PCfast[4] = Double.longBitsToDouble(0xc0555a5c590f9e6aL);
    }

    /** PS: 6 entries; index 4×i = lo, +1 = hi, +2 = ex, +3 = sgn. */
    private static final long[] PS = new long[24];
    static {
        PS[0] = 0xc4c6628b80dc1cd1L; PS[1] = 0xc90fdaa22168c234L; PS[2] = 3L; PS[3] = 0x0L;
        PS[4] = 0x5dc72f712aa57db4L; PS[5] = 0xa55de7312df295f5L; PS[6] = 6L; PS[7] = 0x1L;
        PS[8] = 0x3f33be0021aa54d2L; PS[9] = 0xa335e33bad570e92L; PS[10] = 7L; PS[11] = 0x0L;
        PS[12] = 0xe59d6ab8509a2025L; PS[13] = 0x9969667315ec2d9dL; PS[14] = 7L; PS[15] = 0x1L;
        PS[16] = 0x7d5f8f76fa7d74edL; PS[17] = 0xa83c1a43bf1c6485L; PS[18] = 6L; PS[19] = 0x0L;
        PS[20] = 0xa7f0339113b8b3c5L; PS[21] = 0xf16ab2898eae62f9L; PS[22] = 4L; PS[23] = 0x1L;
    }

    /** PC: 6 entries; index 4×i = lo, +1 = hi, +2 = ex, +3 = sgn. */
    private static final long[] PC = new long[24];
    static {
        PC[0] = 0x0000000000000000L; PC[1] = 0x8000000000000000L; PC[2] = 1L; PC[3] = 0x0L;
        PC[4] = 0x56e26cd9808c1949L; PC[5] = 0x9de9e64df22ef2d2L; PC[6] = 5L; PC[7] = 0x1L;
        PC[8] = 0x9980f00630cb655eL; PC[9] = 0x81e0f840dad61d9aL; PC[10] = 7L; PC[11] = 0x0L;
        PC[12] = 0xa508509534006249L; PC[13] = 0xaae9e3f1e5ffcfe2L; PC[14] = 7L; PC[15] = 0x1L;
        PC[16] = 0x0e0603ce7044eebaL; PC[17] = 0xf0fa83448dd1e094L; PC[18] = 6L; PC[19] = 0x0L;
        PC[20] = 0x0ec63157807ebffaL; PC[21] = 0xd368f6f4207cfe49L; PC[22] = 5L; PC[23] = 0x1L;
    }

    /** SC[256][3] flat: index 3×i + {0,1,2}. */
    private static final double[] SC = new double[256 * 3];
    static {
        SC[0] = Double.longBitsToDouble(0x0000000000000000L); SC[1] = Double.longBitsToDouble(0x0000000000000000L); SC[2] = Double.longBitsToDouble(0x3ff0000000000000L);
        SC[3] = Double.longBitsToDouble(0xbdcc0f6c00000000L); SC[4] = Double.longBitsToDouble(0x3f6921f892b900feL); SC[5] = Double.longBitsToDouble(0x3feffff621623fa0L);
        SC[6] = Double.longBitsToDouble(0xbdc9c7935e000000L); SC[7] = Double.longBitsToDouble(0x3f7921f0ea27ce01L); SC[8] = Double.longBitsToDouble(0x3fefffd8858eca2eL);
        SC[9] = Double.longBitsToDouble(0xbddd14d1ac000000L); SC[10] = Double.longBitsToDouble(0x3f82d96af779b0bbL); SC[11] = Double.longBitsToDouble(0x3fefffa72c986392L);
        SC[12] = Double.longBitsToDouble(0xbdedba8f6a800000L); SC[13] = Double.longBitsToDouble(0x3f8921d1ce2d0a1cL); SC[14] = Double.longBitsToDouble(0x3fefff62169dddaaL);
        SC[15] = Double.longBitsToDouble(0x3dfa6b7cdf000000L); SC[16] = Double.longBitsToDouble(0x3f8f6a29bdb73770L); SC[17] = Double.longBitsToDouble(0x3fefff0943c02419L);
        SC[18] = Double.longBitsToDouble(0x3deb49618d000000L); SC[19] = Double.longBitsToDouble(0x3f92d936d1506f3dL); SC[20] = Double.longBitsToDouble(0x3feffe9cb44829c0L);
        SC[21] = Double.longBitsToDouble(0xbdc398d6fc000000L); SC[22] = Double.longBitsToDouble(0x3f95fd4d1e21de6dL); SC[23] = Double.longBitsToDouble(0x3feffe1c687174b1L);
        SC[24] = Double.longBitsToDouble(0xbe0e9e9a8c800000L); SC[25] = Double.longBitsToDouble(0x3f99215597791e0aL); SC[26] = Double.longBitsToDouble(0x3feffd886097afcfL);
        SC[27] = Double.longBitsToDouble(0xbdf34e844c000000L); SC[28] = Double.longBitsToDouble(0x3f9c454f2e9480c7L); SC[29] = Double.longBitsToDouble(0x3feffce09ce95933L);
        SC[30] = Double.longBitsToDouble(0xbdf989a8a4000000L); SC[31] = Double.longBitsToDouble(0x3f9f693709b94f92L); SC[32] = Double.longBitsToDouble(0x3feffc251dfbac0cL);
        SC[33] = Double.longBitsToDouble(0x3e104a9b99000000L); SC[34] = Double.longBitsToDouble(0x3fa146860e69a571L); SC[35] = Double.longBitsToDouble(0x3feffb55e40a5c43L);
        SC[36] = Double.longBitsToDouble(0xbdb56947c0000000L); SC[37] = Double.longBitsToDouble(0x3fa2d865748774adL); SC[38] = Double.longBitsToDouble(0x3feffa72efff95d1L);
        SC[39] = Double.longBitsToDouble(0xbdcc348768000000L); SC[40] = Double.longBitsToDouble(0x3fa46a396d34121aL); SC[41] = Double.longBitsToDouble(0x3feff97c420a8451L);
        SC[42] = Double.longBitsToDouble(0x3df9e80552000000L); SC[43] = Double.longBitsToDouble(0x3fa5fc00e6e4c65cL); SC[44] = Double.longBitsToDouble(0x3feff871dacd8761L);
        SC[45] = Double.longBitsToDouble(0x3dd3f11d74000000L); SC[46] = Double.longBitsToDouble(0x3fa78dbaa97099ebL); SC[47] = Double.longBitsToDouble(0x3feff753bb18af95L);
        SC[48] = Double.longBitsToDouble(0x3dec039af4000000L); SC[49] = Double.longBitsToDouble(0x3fa91f65fc0abc0aL); SC[50] = Double.longBitsToDouble(0x3feff621e370ca7aL);
        SC[51] = Double.longBitsToDouble(0x3dc53e1f80000000L); SC[52] = Double.longBitsToDouble(0x3faab101bf74ac2eL); SC[53] = Double.longBitsToDouble(0x3feff4dc54b00181L);
        SC[54] = Double.longBitsToDouble(0x3e2114a649000000L); SC[55] = Double.longBitsToDouble(0x3fac428d7de920e9L); SC[56] = Double.longBitsToDouble(0x3feff3830f2e9043L);
        SC[57] = Double.longBitsToDouble(0x3e0adf0ef4000000L); SC[58] = Double.longBitsToDouble(0x3fadd40723a3cdfbL); SC[59] = Double.longBitsToDouble(0x3feff21614b9d9adL);
        SC[60] = Double.longBitsToDouble(0xbe1d21f591800000L); SC[61] = Double.longBitsToDouble(0x3faf656e1e9e59cdL); SC[62] = Double.longBitsToDouble(0x3feff09565e83d77L);
        SC[63] = Double.longBitsToDouble(0xbe14f54d70800000L); SC[64] = Double.longBitsToDouble(0x3fb07b612d6be078L); SC[65] = Double.longBitsToDouble(0x3fefef0102c634e3L);
        SC[66] = Double.longBitsToDouble(0xbe11efec9a000000L); SC[67] = Double.longBitsToDouble(0x3fb1440118ba7bd0L); SC[68] = Double.longBitsToDouble(0x3fefed58ecf342daL);
        SC[69] = Double.longBitsToDouble(0x3e2cc17ba8800000L); SC[70] = Double.longBitsToDouble(0x3fb20c96cf0a7eedL); SC[71] = Double.longBitsToDouble(0x3fefeb9d24646fa6L);
        SC[72] = Double.longBitsToDouble(0x3de121dbe4000000L); SC[73] = Double.longBitsToDouble(0x3fb2d5209628edf0L); SC[74] = Double.longBitsToDouble(0x3fefe9cdacf99cffL);
        SC[75] = Double.longBitsToDouble(0xbdd9ecf610000000L); SC[76] = Double.longBitsToDouble(0x3fb39d9f103bf7f7L); SC[77] = Double.longBitsToDouble(0x3fefe7ea854e6b08L);
        SC[78] = Double.longBitsToDouble(0xbe004ede8e000000L); SC[79] = Double.longBitsToDouble(0x3fb466116c629e5cL); SC[80] = Double.longBitsToDouble(0x3fefe5f3af4ee201L);
        SC[81] = Double.longBitsToDouble(0xbe01821cec000000L); SC[82] = Double.longBitsToDouble(0x3fb52e773c9920c7L); SC[83] = Double.longBitsToDouble(0x3fefe3e92c0e4108L);
        SC[84] = Double.longBitsToDouble(0x3e0cdec726000000L); SC[85] = Double.longBitsToDouble(0x3fb5f6d02131f0b2L); SC[86] = Double.longBitsToDouble(0x3fefe1cafc7f1a24L);
        SC[87] = Double.longBitsToDouble(0xbe0edece4d000000L); SC[88] = Double.longBitsToDouble(0x3fb6bf1b2653648cL); SC[89] = Double.longBitsToDouble(0x3fefdf99233c230cL);
        SC[90] = Double.longBitsToDouble(0xbe02aa4d1c000000L); SC[91] = Double.longBitsToDouble(0x3fb787585bc45f0fL); SC[92] = Double.longBitsToDouble(0x3fefdd53a01d11d9L);
        SC[93] = Double.longBitsToDouble(0x3dfd461592000000L); SC[94] = Double.longBitsToDouble(0x3fb84f871e32cf68L); SC[95] = Double.longBitsToDouble(0x3fefdafa74f16482L);
        SC[96] = Double.longBitsToDouble(0x3e2f0cbd72800000L); SC[97] = Double.longBitsToDouble(0x3fb917a71d3d2956L); SC[98] = Double.longBitsToDouble(0x3fefd88da29f302eL);
        SC[99] = Double.longBitsToDouble(0xbe15832470000000L); SC[100] = Double.longBitsToDouble(0x3fb9dfb6c9865b06L); SC[101] = Double.longBitsToDouble(0x3fefd60d2e14a6b1L);
        SC[102] = Double.longBitsToDouble(0xbe12e81bf4000000L); SC[103] = Double.longBitsToDouble(0x3fbaa7b706bfdbbaL); SC[104] = Double.longBitsToDouble(0x3fefd3791484ff50L);
        SC[105] = Double.longBitsToDouble(0xbe31394141800000L); SC[106] = Double.longBitsToDouble(0x3fbb6fa680a05c27L); SC[107] = Double.longBitsToDouble(0x3fefd0d15a4b8471L);
        SC[108] = Double.longBitsToDouble(0x3e171098ff000000L); SC[109] = Double.longBitsToDouble(0x3fbc3785eba12b42L); SC[110] = Double.longBitsToDouble(0x3fefce15fceddccfL);
        SC[111] = Double.longBitsToDouble(0xbdfc3519e8000000L); SC[112] = Double.longBitsToDouble(0x3fbcff53302f0590L); SC[113] = Double.longBitsToDouble(0x3fefcb4703b969e1L);
        SC[114] = Double.longBitsToDouble(0x3e42f522a5000000L); SC[115] = Double.longBitsToDouble(0x3fbdc70fb84af16eL); SC[116] = Double.longBitsToDouble(0x3fefc8646987fc1dL);
        SC[117] = Double.longBitsToDouble(0xbdeae9bed8000000L); SC[118] = Double.longBitsToDouble(0x3fbe8eb7f8a589e2L); SC[119] = Double.longBitsToDouble(0x3fefc56e3b91ca3aL);
        SC[120] = Double.longBitsToDouble(0x3e1f8868b2000000L); SC[121] = Double.longBitsToDouble(0x3fbf564e87d2330fL); SC[122] = Double.longBitsToDouble(0x3fefc264701f9a09L);
        SC[123] = Double.longBitsToDouble(0xbe2b07985f800000L); SC[124] = Double.longBitsToDouble(0x3fc00ee8835051f4L); SC[125] = Double.longBitsToDouble(0x3fefbf47105f7439L);
        SC[126] = Double.longBitsToDouble(0x3e1cbdaa94000000L); SC[127] = Double.longBitsToDouble(0x3fc072a05e1d4d8eL); SC[128] = Double.longBitsToDouble(0x3fefbc16172a9e36L);
        SC[129] = Double.longBitsToDouble(0x3e337c5b90800000L); SC[130] = Double.longBitsToDouble(0x3fc0d64df9619f0dL); SC[131] = Double.longBitsToDouble(0x3fefb8d18b635327L);
        SC[132] = Double.longBitsToDouble(0xbe3068b5fc800000L); SC[133] = Double.longBitsToDouble(0x3fc139f09bc617f5L); SC[134] = Double.longBitsToDouble(0x3fefb5797351da85L);
        SC[135] = Double.longBitsToDouble(0xbe28ea6681800000L); SC[136] = Double.longBitsToDouble(0x3fc19d8919fa4ec8L); SC[137] = Double.longBitsToDouble(0x3fefb20dc7da8affL);
        SC[138] = Double.longBitsToDouble(0x3e36278ceb800000L); SC[139] = Double.longBitsToDouble(0x3fc2011719d50b87L); SC[140] = Double.longBitsToDouble(0x3fefae8e8bd4427fL);
        SC[141] = Double.longBitsToDouble(0xbe2096df84000000L); SC[142] = Double.longBitsToDouble(0x3fc264993433763aL); SC[143] = Double.longBitsToDouble(0x3fefaafbcbfca356L);
        SC[144] = Double.longBitsToDouble(0x3e29b2534f000000L); SC[145] = Double.longBitsToDouble(0x3fc2c810967bbf70L); SC[146] = Double.longBitsToDouble(0x3fefa7557d8d987eL);
        SC[147] = Double.longBitsToDouble(0x3dd215b4e0000000L); SC[148] = Double.longBitsToDouble(0x3fc32b7bfa25c91bL); SC[149] = Double.longBitsToDouble(0x3fefa39bac71954bL);
        SC[150] = Double.longBitsToDouble(0xbe194db891000000L); SC[151] = Double.longBitsToDouble(0x3fc38edb9d29b39dL); SC[152] = Double.longBitsToDouble(0x3fef9fce56700a6dL);
        SC[153] = Double.longBitsToDouble(0x3e27727f7b800000L); SC[154] = Double.longBitsToDouble(0x3fc3f22f7c3cce3aL); SC[155] = Double.longBitsToDouble(0x3fef9bed7b8c8d8cL);
        SC[156] = Double.longBitsToDouble(0xbe20cb3303800000L); SC[157] = Double.longBitsToDouble(0x3fc45576971dd530L); SC[158] = Double.longBitsToDouble(0x3fef97f925d53c83L);
        SC[159] = Double.longBitsToDouble(0xbe09071106000000L); SC[160] = Double.longBitsToDouble(0x3fc4b8b175c71e22L); SC[161] = Double.longBitsToDouble(0x3fef93f14feb8022L);
        SC[162] = Double.longBitsToDouble(0x3e262741e7800000L); SC[163] = Double.longBitsToDouble(0x3fc51bdfa7ea30d5L); SC[164] = Double.longBitsToDouble(0x3fef8fd5fe3efac8L);
        SC[165] = Double.longBitsToDouble(0x3e3f8e16d0c00000L); SC[166] = Double.longBitsToDouble(0x3fc57f00e80e6e12L); SC[167] = Double.longBitsToDouble(0x3fef8ba733a1ceb1L);
        SC[168] = Double.longBitsToDouble(0xbe076acbca000000L); SC[169] = Double.longBitsToDouble(0x3fc5e2143b7bc1c2L); SC[170] = Double.longBitsToDouble(0x3fef8764fad5e9bfL);
        SC[171] = Double.longBitsToDouble(0xbe10a0f73a000000L); SC[172] = Double.longBitsToDouble(0x3fc6451a76411746L); SC[173] = Double.longBitsToDouble(0x3fef830f4ad232d8L);
        SC[174] = Double.longBitsToDouble(0x3e3ca11d1bc00000L); SC[175] = Double.longBitsToDouble(0x3fc6a8135d7bd143L); SC[176] = Double.longBitsToDouble(0x3fef7ea625eb5af7L);
        SC[177] = Double.longBitsToDouble(0xbe202f2362800000L); SC[178] = Double.longBitsToDouble(0x3fc70afd74071191L); SC[179] = Double.longBitsToDouble(0x3fef7a299d3f182aL);
        SC[180] = Double.longBitsToDouble(0x3e2b34dcb8000000L); SC[181] = Double.longBitsToDouble(0x3fc76dda08544b5cL); SC[182] = Double.longBitsToDouble(0x3fef7599a1ac7ecdL);
        SC[183] = Double.longBitsToDouble(0x3df161ff40000000L); SC[184] = Double.longBitsToDouble(0x3fc7d0a7bf2d4abaL); SC[185] = Double.longBitsToDouble(0x3fef70f64322da74L);
        SC[186] = Double.longBitsToDouble(0xbe0c49b8b4000000L); SC[187] = Double.longBitsToDouble(0x3fc83366ddb3de23L); SC[188] = Double.longBitsToDouble(0x3fef6c3f7e7c2707L);
        SC[189] = Double.longBitsToDouble(0x3e221da851000000L); SC[190] = Double.longBitsToDouble(0x3fc8961743b14290L); SC[191] = Double.longBitsToDouble(0x3fef6775552a6ba2L);
        SC[192] = Double.longBitsToDouble(0x3e1ac63eda000000L); SC[193] = Double.longBitsToDouble(0x3fc8f8b851098588L); SC[194] = Double.longBitsToDouble(0x3fef6297cef0cdd6L);
        SC[195] = Double.longBitsToDouble(0x3e427ef489c00000L); SC[196] = Double.longBitsToDouble(0x3fc95b4a5b9f2cebL); SC[197] = Double.longBitsToDouble(0x3fef5da6e7820551L);
        SC[198] = Double.longBitsToDouble(0x3e1ae89370000000L); SC[199] = Double.longBitsToDouble(0x3fc9bdcc07900146L); SC[200] = Double.longBitsToDouble(0x3fef58a2b0689c82L);
        SC[201] = Double.longBitsToDouble(0x3e2eb48c7e000000L); SC[202] = Double.longBitsToDouble(0x3fca203e4a4f950eL); SC[203] = Double.longBitsToDouble(0x3fef538b1d392049L);
        SC[204] = Double.longBitsToDouble(0xbe2bfd282f000000L); SC[205] = Double.longBitsToDouble(0x3fca829ffaad0d79L); SC[206] = Double.longBitsToDouble(0x3fef4e603d51f1aaL);
        SC[207] = Double.longBitsToDouble(0x3e27ccf638000000L); SC[208] = Double.longBitsToDouble(0x3fcae4f1fa80e1b5L); SC[209] = Double.longBitsToDouble(0x3fef492204c5ef9eL);
        SC[210] = Double.longBitsToDouble(0xbe32435c57800000L); SC[211] = Double.longBitsToDouble(0x3fcb4732b72ebc86L); SC[212] = Double.longBitsToDouble(0x3fef43d0890e1e72L);
        SC[213] = Double.longBitsToDouble(0x3e10293fec000000L); SC[214] = Double.longBitsToDouble(0x3fcba9634155f866L); SC[215] = Double.longBitsToDouble(0x3fef3e6bbb6c2ea4L);
        SC[216] = Double.longBitsToDouble(0xbe27bb1f92000000L); SC[217] = Double.longBitsToDouble(0x3fcc0b82461f65e0L); SC[218] = Double.longBitsToDouble(0x3fef38f3ae6f9afcL);
        SC[219] = Double.longBitsToDouble(0x3e227aaebc000000L); SC[220] = Double.longBitsToDouble(0x3fcc6d906faacf65L); SC[221] = Double.longBitsToDouble(0x3fef3368589e17a2L);
        SC[222] = Double.longBitsToDouble(0xbe42e2bcd5000000L); SC[223] = Double.longBitsToDouble(0x3fcccf8c3f74a6c9L); SC[224] = Double.longBitsToDouble(0x3fef2dc9cfb5fa74L);
        SC[225] = Double.longBitsToDouble(0xbe16f070ac000000L); SC[226] = Double.longBitsToDouble(0x3fcd31773ba218a8L); SC[227] = Double.longBitsToDouble(0x3fef2817fd4d045bL);
        SC[228] = Double.longBitsToDouble(0x3e2469adfc000000L); SC[229] = Double.longBitsToDouble(0x3fcd935004779e57L); SC[230] = Double.longBitsToDouble(0x3fef2252f59c122dL);
        SC[231] = Double.longBitsToDouble(0x3df4f51c18000000L); SC[232] = Double.longBitsToDouble(0x3fcdf5164301377aL); SC[233] = Double.longBitsToDouble(0x3fef1c7abdeaa3efL);
        SC[234] = Double.longBitsToDouble(0x3e278e44da000000L); SC[235] = Double.longBitsToDouble(0x3fce56ca4202807cL); SC[236] = Double.longBitsToDouble(0x3fef168f51c5d5d5L);
        SC[237] = Double.longBitsToDouble(0x3df49bb5f8000000L); SC[238] = Double.longBitsToDouble(0x3fceb86b4a1b7e9bL); SC[239] = Double.longBitsToDouble(0x3fef1090bc4b6800L);
        SC[240] = Double.longBitsToDouble(0xbe367ba541000000L); SC[241] = Double.longBitsToDouble(0x3fcf19f9369d5e93L); SC[242] = Double.longBitsToDouble(0x3fef0a7effdc937fL);
        SC[243] = Double.longBitsToDouble(0x3e2c0cab95000000L); SC[244] = Double.longBitsToDouble(0x3fcf7b74ab7219d2L); SC[245] = Double.longBitsToDouble(0x3fef045a1219e594L);
        SC[246] = Double.longBitsToDouble(0xbe12b77e32000000L); SC[247] = Double.longBitsToDouble(0x3fcfdcdc0ca3288dL); SC[248] = Double.longBitsToDouble(0x3feefe220cf5c751L);
        SC[249] = Double.longBitsToDouble(0xbdee0d8cb0000000L); SC[250] = Double.longBitsToDouble(0x3fd01f18054c8362L); SC[251] = Double.longBitsToDouble(0x3feef7d6e54c347dL);
        SC[252] = Double.longBitsToDouble(0xbe2ecd5b9c000000L); SC[253] = Double.longBitsToDouble(0x3fd04fb7f6d35d68L); SC[254] = Double.longBitsToDouble(0x3feef178a6f9a987L);
        SC[255] = Double.longBitsToDouble(0x3e2eb24de5000000L); SC[256] = Double.longBitsToDouble(0x3fd0804e1d369ff2L); SC[257] = Double.longBitsToDouble(0x3feeeb074934fdf0L);
        SC[258] = Double.longBitsToDouble(0x3e14a897c4000000L); SC[259] = Double.longBitsToDouble(0x3fd0b0d9d7b0d042L); SC[260] = Double.longBitsToDouble(0x3feee482e14bcde0L);
        SC[261] = Double.longBitsToDouble(0x3e1336c376000000L); SC[262] = Double.longBitsToDouble(0x3fd0e15b555e7becL); SC[263] = Double.longBitsToDouble(0x3feeddeb6908ca8cL);
        SC[264] = Double.longBitsToDouble(0xbe03952d90000000L); SC[265] = Double.longBitsToDouble(0x3fd111d25efd48b8L); SC[266] = Double.longBitsToDouble(0x3feed740e7eb8dd6L);
        SC[267] = Double.longBitsToDouble(0x3e0fc2a5d4000000L); SC[268] = Double.longBitsToDouble(0x3fd1423ef5c7e1bdL); SC[269] = Double.longBitsToDouble(0x3feed0835dc24e89L);
        SC[270] = Double.longBitsToDouble(0x3e2a88ed37000000L); SC[271] = Double.longBitsToDouble(0x3fd172a0eb8361daL); SC[272] = Double.longBitsToDouble(0x3feec9b2d0ec8288L);
        SC[273] = Double.longBitsToDouble(0xbe48ca4cb9400000L); SC[274] = Double.longBitsToDouble(0x3fd1a2f7b10b6d70L); SC[275] = Double.longBitsToDouble(0x3feec2cf55d6117cL);
        SC[276] = Double.longBitsToDouble(0x3e40144524000000L); SC[277] = Double.longBitsToDouble(0x3fd1d3446fd0cd3fL); SC[278] = Double.longBitsToDouble(0x3feebbd8c1d62f96L);
        SC[279] = Double.longBitsToDouble(0xbe3abf810c000000L); SC[280] = Double.longBitsToDouble(0x3fd203855b85f89aL); SC[281] = Double.longBitsToDouble(0x3feeb4cf57454132L);
        SC[282] = Double.longBitsToDouble(0x3e35d4c5d5800000L); SC[283] = Double.longBitsToDouble(0x3fd233bbcca40561L); SC[284] = Double.longBitsToDouble(0x3feeadb2e40746caL);
        SC[285] = Double.longBitsToDouble(0xbe2a1b0c58000000L); SC[286] = Double.longBitsToDouble(0x3fd263e685b1d714L); SC[287] = Double.longBitsToDouble(0x3feea68396d87754L);
        SC[288] = Double.longBitsToDouble(0xbe277c8dac000000L); SC[289] = Double.longBitsToDouble(0x3fd294061d2eb611L); SC[290] = Double.longBitsToDouble(0x3fee9f41597393c8L);
        SC[291] = Double.longBitsToDouble(0x3e1915540e000000L); SC[292] = Double.longBitsToDouble(0x3fd2c41a580014cfL); SC[293] = Double.longBitsToDouble(0x3fee97ec348fb87fL);
        SC[294] = Double.longBitsToDouble(0xbe3abb6d9b000000L); SC[295] = Double.longBitsToDouble(0x3fd2f422b2d0990cL); SC[296] = Double.longBitsToDouble(0x3fee90843c55b996L);
        SC[297] = Double.longBitsToDouble(0xbe3b8ee5d5800000L); SC[298] = Double.longBitsToDouble(0x3fd3241f8cea2836L); SC[299] = Double.longBitsToDouble(0x3fee890962268c49L);
        SC[300] = Double.longBitsToDouble(0xbe31cd2982800000L); SC[301] = Double.longBitsToDouble(0x3fd35410a8396266L); SC[302] = Double.longBitsToDouble(0x3fee817baf85c094L);
        SC[303] = Double.longBitsToDouble(0xbdfe216af0000000L); SC[304] = Double.longBitsToDouble(0x3fd383f5e08283e2L); SC[305] = Double.longBitsToDouble(0x3fee79db2a188b0aL);
        SC[306] = Double.longBitsToDouble(0xbe024afc30000000L); SC[307] = Double.longBitsToDouble(0x3fd3b3cef6993c0bL); SC[308] = Double.longBitsToDouble(0x3fee7227dbf82004L);
        SC[309] = Double.longBitsToDouble(0xbe0aa1657c000000L); SC[310] = Double.longBitsToDouble(0x3fd3e39be4767224L); SC[311] = Double.longBitsToDouble(0x3fee6a61c62d5274L);
        SC[312] = Double.longBitsToDouble(0xbe1c5b65fa000000L); SC[313] = Double.longBitsToDouble(0x3fd4135c898485bbL); SC[314] = Double.longBitsToDouble(0x3fee6288ee07fea5L);
        SC[315] = Double.longBitsToDouble(0x3df23e8978000000L); SC[316] = Double.longBitsToDouble(0x3fd44310de3c284bL); SC[317] = Double.longBitsToDouble(0x3fee5a9d54bbd26cL);
        SC[318] = Double.longBitsToDouble(0xbe22b1d77a000000L); SC[319] = Double.longBitsToDouble(0x3fd472b8976d498dL); SC[320] = Double.longBitsToDouble(0x3fee529f06cb187dL);
        SC[321] = Double.longBitsToDouble(0xbe0daaa348000000L); SC[322] = Double.longBitsToDouble(0x3fd4a253cb97efd1L); SC[323] = Double.longBitsToDouble(0x3fee4a8e007231a2L);
        SC[324] = Double.longBitsToDouble(0xbe3322f570800000L); SC[325] = Double.longBitsToDouble(0x3fd4d1e2260c3422L); SC[326] = Double.longBitsToDouble(0x3fee426a500f6e33L);
        SC[327] = Double.longBitsToDouble(0x3e264758e8000000L); SC[328] = Double.longBitsToDouble(0x3fd50163eca0b337L); SC[329] = Double.longBitsToDouble(0x3fee3a33e996b722L);
        SC[330] = Double.longBitsToDouble(0x3e31248627800000L); SC[331] = Double.longBitsToDouble(0x3fd530d89a17e007L); SC[332] = Double.longBitsToDouble(0x3fee31eae3fb917bL);
        SC[333] = Double.longBitsToDouble(0xbe46c3416cc00000L); SC[334] = Double.longBitsToDouble(0x3fd5603fcf8cd8a3L); SC[335] = Double.longBitsToDouble(0x3fee298f502a579bL);
        SC[336] = Double.longBitsToDouble(0x3e2ab481ff000000L); SC[337] = Double.longBitsToDouble(0x3fd58f9a896aa209L); SC[338] = Double.longBitsToDouble(0x3fee2121016e14fcL);
        SC[339] = Double.longBitsToDouble(0xbe26eb838b000000L); SC[340] = Double.longBitsToDouble(0x3fd5bee77aaf890bL); SC[341] = Double.longBitsToDouble(0x3fee18a032eb4df5L);
        SC[342] = Double.longBitsToDouble(0xbdfd159b80000000L); SC[343] = Double.longBitsToDouble(0x3fd5ee2734efeef5L); SC[344] = Double.longBitsToDouble(0x3fee100ccaa6bd78L);
        SC[345] = Double.longBitsToDouble(0xbdda42e4a0000000L); SC[346] = Double.longBitsToDouble(0x3fd61d595bedeabcL); SC[347] = Double.longBitsToDouble(0x3fee0766d944915eL);
        SC[348] = Double.longBitsToDouble(0xbe143d0dc0000000L); SC[349] = Double.longBitsToDouble(0x3fd64c7dd5cc0cd1L); SC[350] = Double.longBitsToDouble(0x3fedfeae63903034L);
        SC[351] = Double.longBitsToDouble(0xbe48c7bdb7000000L); SC[352] = Double.longBitsToDouble(0x3fd67b9453ca2122L); SC[353] = Double.longBitsToDouble(0x3fedf5e378482eaeL);
        SC[354] = Double.longBitsToDouble(0x3e11c0ead6000000L); SC[355] = Double.longBitsToDouble(0x3fd6aa9d844c980aL); SC[356] = Double.longBitsToDouble(0x3feded05f6a23a52L);
        SC[357] = Double.longBitsToDouble(0x3e07d52600000000L); SC[358] = Double.longBitsToDouble(0x3fd6d99867e90d92L); SC[359] = Double.longBitsToDouble(0x3fede4160e97b2e2L);
        SC[360] = Double.longBitsToDouble(0x3e3924e036800000L); SC[361] = Double.longBitsToDouble(0x3fd7088555d3c816L); SC[362] = Double.longBitsToDouble(0x3feddb13afb14e37L);
        SC[363] = Double.longBitsToDouble(0xbe174b7c3e000000L); SC[364] = Double.longBitsToDouble(0x3fd73763c09fba09L); SC[365] = Double.longBitsToDouble(0x3fedd1fef5335416L);
        SC[366] = Double.longBitsToDouble(0xbe17943ad0000000L); SC[367] = Double.longBitsToDouble(0x3fd766340685c982L); SC[368] = Double.longBitsToDouble(0x3fedc8d7ccf2567aL);
        SC[369] = Double.longBitsToDouble(0x3e279dd614000000L); SC[370] = Double.longBitsToDouble(0x3fd794f5f7522b88L); SC[371] = Double.longBitsToDouble(0x3fedbf9e402aa5c3L);
        SC[372] = Double.longBitsToDouble(0x3e17b64f32000000L); SC[373] = Double.longBitsToDouble(0x3fd7c3a939c32d81L); SC[374] = Double.longBitsToDouble(0x3fedb652607e0db1L);
        SC[375] = Double.longBitsToDouble(0xbe32bea5ce800000L); SC[376] = Double.longBitsToDouble(0x3fd7f24db825141cL); SC[377] = Double.longBitsToDouble(0x3fedacf43268b5b0L);
        SC[378] = Double.longBitsToDouble(0x3e1733c024000000L); SC[379] = Double.longBitsToDouble(0x3fd820e3b8bf15a0L); SC[380] = Double.longBitsToDouble(0x3feda383a7aed887L);
        SC[381] = Double.longBitsToDouble(0xbe4eac0fc9400000L); SC[382] = Double.longBitsToDouble(0x3fd84f6a51d077b3L); SC[383] = Double.longBitsToDouble(0x3fed9a00efd84537L);
        SC[384] = Double.longBitsToDouble(0x3e4aca3733800000L); SC[385] = Double.longBitsToDouble(0x3fd87de2f4704f98L); SC[386] = Double.longBitsToDouble(0x3fed906bbf17f4daL);
        SC[387] = Double.longBitsToDouble(0xbe1910c4f0000000L); SC[388] = Double.longBitsToDouble(0x3fd8ac4b7dc0d986L); SC[389] = Double.longBitsToDouble(0x3fed86c4862b5d6eL);
        SC[390] = Double.longBitsToDouble(0xbe033bb860000000L); SC[391] = Double.longBitsToDouble(0x3fd8daa52b4dc041L); SC[392] = Double.longBitsToDouble(0x3fed7d0b0374a559L);
        SC[393] = Double.longBitsToDouble(0xbe469e1507000000L); SC[394] = Double.longBitsToDouble(0x3fd908ef408ad220L); SC[395] = Double.longBitsToDouble(0x3fed733f5e71c3bcL);
        SC[396] = Double.longBitsToDouble(0x3e4cffacf0800000L); SC[397] = Double.longBitsToDouble(0x3fd9372ab7784d36L); SC[398] = Double.longBitsToDouble(0x3fed696161d786c9L);
        SC[399] = Double.longBitsToDouble(0xbe58629d9f000000L); SC[400] = Double.longBitsToDouble(0x3fd965552b0849abL); SC[401] = Double.longBitsToDouble(0x3fed5f7190eeae23L);
        SC[402] = Double.longBitsToDouble(0x3e14150000000000L); SC[403] = Double.longBitsToDouble(0x3fd99371687c64f3L); SC[404] = Double.longBitsToDouble(0x3fed556f5155d9ddL);
        SC[405] = Double.longBitsToDouble(0xbe4bd37aad800000L); SC[406] = Double.longBitsToDouble(0x3fd9c17cf40715cbL); SC[407] = Double.longBitsToDouble(0x3fed4b5b2caf8386L);
        SC[408] = Double.longBitsToDouble(0x3e5d02cde7000000L); SC[409] = Double.longBitsToDouble(0x3fd9ef79ea4d995dL); SC[410] = Double.longBitsToDouble(0x3fed4134ac5eb246L);
        SC[411] = Double.longBitsToDouble(0xbe110547ac000000L); SC[412] = Double.longBitsToDouble(0x3fda1d653d9adf5eL); SC[413] = Double.longBitsToDouble(0x3fed36fc7d291602L);
        SC[414] = Double.longBitsToDouble(0xbe401a1a22800000L); SC[415] = Double.longBitsToDouble(0x3fda4b40f9c0120bL); SC[416] = Double.longBitsToDouble(0x3fed2cb22b45236bL);
        SC[417] = Double.longBitsToDouble(0x3e23ce2bac000000L); SC[418] = Double.longBitsToDouble(0x3fda790ce2056b9aL); SC[419] = Double.longBitsToDouble(0x3fed2255c3ae11a5L);
        SC[420] = Double.longBitsToDouble(0xbdfccb4a60000000L); SC[421] = Double.longBitsToDouble(0x3fdaa6c828db4ea8L); SC[422] = Double.longBitsToDouble(0x3fed17e774d4e3e2L);
        SC[423] = Double.longBitsToDouble(0x3e25db4b00000000L); SC[424] = Double.longBitsToDouble(0x3fdad47321f29847L); SC[425] = Double.longBitsToDouble(0x3fed0d672bc0b122L);
        SC[426] = Double.longBitsToDouble(0x3e232f6a6e000000L); SC[427] = Double.longBitsToDouble(0x3fdb020d7a285e23L); SC[428] = Double.longBitsToDouble(0x3fed02d4fb84d334L);
        SC[429] = Double.longBitsToDouble(0x3e5cf8e39bc00000L); SC[430] = Double.longBitsToDouble(0x3fdb2f97c27f7494L); SC[431] = Double.longBitsToDouble(0x3fecf830c2248c5eL);
        SC[432] = Double.longBitsToDouble(0x3e18927bb0000000L); SC[433] = Double.longBitsToDouble(0x3fdb5d10129a750aL); SC[434] = Double.longBitsToDouble(0x3feced7af22cb105L);
        SC[435] = Double.longBitsToDouble(0xbe33dec3c1000000L); SC[436] = Double.longBitsToDouble(0x3fdb8a77f8d0bbc5L); SC[437] = Double.longBitsToDouble(0x3fece2b32e50d6cdL);
        SC[438] = Double.longBitsToDouble(0xbe326ba536000000L); SC[439] = Double.longBitsToDouble(0x3fdbb7cf08f0290dL); SC[440] = Double.longBitsToDouble(0x3fecd7d98fcf3b1eL);
        SC[441] = Double.longBitsToDouble(0x3e223c568e000000L); SC[442] = Double.longBitsToDouble(0x3fdbe51524e3aa53L); SC[443] = Double.longBitsToDouble(0x3fecccee1da3d56eL);
        SC[444] = Double.longBitsToDouble(0xbe2f3b3af0000000L); SC[445] = Double.longBitsToDouble(0x3fdc1249c1f5f2f6L); SC[446] = Double.longBitsToDouble(0x3fecc1f0f95e1e24L);
        SC[447] = Double.longBitsToDouble(0xbe31286a47000000L); SC[448] = Double.longBitsToDouble(0x3fdc3f6d2ef7054bL); SC[449] = Double.longBitsToDouble(0x3fecb6e20ff37e81L);
        SC[450] = Double.longBitsToDouble(0x3e2641214e000000L); SC[451] = Double.longBitsToDouble(0x3fdc6c7f594003d9L); SC[452] = Double.longBitsToDouble(0x3fecabc165bf1b60L);
        SC[453] = Double.longBitsToDouble(0x3e40cda7c9000000L); SC[454] = Double.longBitsToDouble(0x3fdc997ff2bffccbL); SC[455] = Double.longBitsToDouble(0x3feca08f0dee434cL);
        SC[456] = Double.longBitsToDouble(0xbe35557ac9000000L); SC[457] = Double.longBitsToDouble(0x3fdcc66e7b42e8f1L); SC[458] = Double.longBitsToDouble(0x3fec954b28bca62eL);
        SC[459] = Double.longBitsToDouble(0x3e3555eb62000000L); SC[460] = Double.longBitsToDouble(0x3fdcf34bccc567a1L); SC[461] = Double.longBitsToDouble(0x3fec89f57f6e20f3L);
        SC[462] = Double.longBitsToDouble(0xbe34e0e361000000L); SC[463] = Double.longBitsToDouble(0x3fdd2016cbb5e39aL); SC[464] = Double.longBitsToDouble(0x3fec7e8e59999e1fL);
        SC[465] = Double.longBitsToDouble(0x3e2446da1e000000L); SC[466] = Double.longBitsToDouble(0x3fdd4cd039d0ed05L); SC[467] = Double.longBitsToDouble(0x3fec731585f970ebL);
        SC[468] = Double.longBitsToDouble(0x3e2103d328000000L); SC[469] = Double.longBitsToDouble(0x3fdd797767638decL); SC[470] = Double.longBitsToDouble(0x3fec678b3174afe1L);
        SC[471] = Double.longBitsToDouble(0x3e35814d60000000L); SC[472] = Double.longBitsToDouble(0x3fdda60c7ae9dc22L); SC[473] = Double.longBitsToDouble(0x3fec5bef522be6fbL);
        SC[474] = Double.longBitsToDouble(0xbe25e2321e000000L); SC[475] = Double.longBitsToDouble(0x3fddd28f054cbb3fL); SC[476] = Double.longBitsToDouble(0x3fec5042052c8c42L);
        SC[477] = Double.longBitsToDouble(0xbe2a259ffe000000L); SC[478] = Double.longBitsToDouble(0x3fddfeff54854631L); SC[479] = Double.longBitsToDouble(0x3fec44833611bc7dL);
        SC[480] = Double.longBitsToDouble(0xbe04f28d80000000L); SC[481] = Double.longBitsToDouble(0x3fde2b5d34665b35L); SC[482] = Double.longBitsToDouble(0x3fec38b2f278ea7eL);
        SC[483] = Double.longBitsToDouble(0xbdbde57100000000L); SC[484] = Double.longBitsToDouble(0x3fde57a86d137f20L); SC[485] = Double.longBitsToDouble(0x3fec2cd1493d05c2L);
        SC[486] = Double.longBitsToDouble(0x3e2e0d8d14000000L); SC[487] = Double.longBitsToDouble(0x3fde83e0ffb7bfb4L); SC[488] = Double.longBitsToDouble(0x3fec20de3a08ea07L);
        SC[489] = Double.longBitsToDouble(0xbe312a858e000000L); SC[490] = Double.longBitsToDouble(0x3fdeb0067e48baf4L); SC[491] = Double.longBitsToDouble(0x3fec14d9e2bd511eL);
        SC[492] = Double.longBitsToDouble(0x3e49a17403000000L); SC[493] = Double.longBitsToDouble(0x3fdedc19997a4431L); SC[494] = Double.longBitsToDouble(0x3fec08c413089b2eL);
        SC[495] = Double.longBitsToDouble(0x3e268c8636000000L); SC[496] = Double.longBitsToDouble(0x3fdf0819163d1bc0L); SC[497] = Double.longBitsToDouble(0x3febfc9d21568f32L);
        SC[498] = Double.longBitsToDouble(0x3e24cc5eb8000000L); SC[499] = Double.longBitsToDouble(0x3fdf3405a482e11dL); SC[500] = Double.longBitsToDouble(0x3febf064dd580fc9L);
        SC[501] = Double.longBitsToDouble(0xbe4fce7cd8000000L); SC[502] = Double.longBitsToDouble(0x3fdf5fde8f3f11d4L); SC[503] = Double.longBitsToDouble(0x3febe41b798f6b97L);
        SC[504] = Double.longBitsToDouble(0xbe2af81690000000L); SC[505] = Double.longBitsToDouble(0x3fdf8ba4c98a9816L); SC[506] = Double.longBitsToDouble(0x3febd7c0b1a7f14bL);
        SC[507] = Double.longBitsToDouble(0x3de6e39e20000000L); SC[508] = Double.longBitsToDouble(0x3fdfb7575d1ea750L); SC[509] = Double.longBitsToDouble(0x3febcb54cac5dde5L);
        SC[510] = Double.longBitsToDouble(0x3e330f9256000000L); SC[511] = Double.longBitsToDouble(0x3fdfe2f665dcd168L); SC[512] = Double.longBitsToDouble(0x3febbed7bd1e17b0L);
        SC[513] = Double.longBitsToDouble(0x3e0626de20000000L); SC[514] = Double.longBitsToDouble(0x3fe00740ca0d5fbbL); SC[515] = Double.longBitsToDouble(0x3febb2499f9fe7a3L);
        SC[516] = Double.longBitsToDouble(0x3e15cc7030000000L); SC[517] = Double.longBitsToDouble(0x3fe01cfc8afeea0eL); SC[518] = Double.longBitsToDouble(0x3feba5aa650dd495L);
        SC[519] = Double.longBitsToDouble(0xbdf6191e60000000L); SC[520] = Double.longBitsToDouble(0x3fe032ae54fe4057L); SC[521] = Double.longBitsToDouble(0x3feb98fa2065a5e6L);
        SC[522] = Double.longBitsToDouble(0xbe06b14850000000L); SC[523] = Double.longBitsToDouble(0x3fe0485624c328c8L); SC[524] = Double.longBitsToDouble(0x3feb8c38d39737bcL);
        SC[525] = Double.longBitsToDouble(0xbe211fbc3a000000L); SC[526] = Double.longBitsToDouble(0x3fe05df3e66a716dL); SC[527] = Double.longBitsToDouble(0x3feb7f668a580fd0L);
        SC[528] = Double.longBitsToDouble(0xbe40eca7f0000000L); SC[529] = Double.longBitsToDouble(0x3fe07387825589ecL); SC[530] = Double.longBitsToDouble(0x3feb728352c44517L);
        SC[531] = Double.longBitsToDouble(0xbe68073bc9e00000L); SC[532] = Double.longBitsToDouble(0x3fe089109ef1284dL); SC[533] = Double.longBitsToDouble(0x3feb658f630112edL);
        SC[534] = Double.longBitsToDouble(0xbe49dcf0ad000000L); SC[535] = Double.longBitsToDouble(0x3fe09e9051603e29L); SC[536] = Double.longBitsToDouble(0x3feb588a13ab750fL);
        SC[537] = Double.longBitsToDouble(0xbe206ea9f0000000L); SC[538] = Double.longBitsToDouble(0x3fe0b405820e78e7L); SC[539] = Double.longBitsToDouble(0x3feb4b740d3cc07bL);
        SC[540] = Double.longBitsToDouble(0xbe136a8d0c000000L); SC[541] = Double.longBitsToDouble(0x3fe0c9704a1ea4e5L); SC[542] = Double.longBitsToDouble(0x3feb3e4d40f5524dL);
        SC[543] = Double.longBitsToDouble(0x3e163d1f30000000L); SC[544] = Double.longBitsToDouble(0x3fe0ded0bc01a533L); SC[545] = Double.longBitsToDouble(0x3feb3115a3a628afL);
        SC[546] = Double.longBitsToDouble(0x3e5f3181f1400000L); SC[547] = Double.longBitsToDouble(0x3fe0f4270e4787bfL); SC[548] = Double.longBitsToDouble(0x3feb23cd1314c779L);
        SC[549] = Double.longBitsToDouble(0xbe2f269b78000000L); SC[550] = Double.longBitsToDouble(0x3fe109723e75c5cfL); SC[551] = Double.longBitsToDouble(0x3feb167430cfebdbL);
        SC[552] = Double.longBitsToDouble(0x3e41d84dc0800000L); SC[553] = Double.longBitsToDouble(0x3fe11eb36bc9db52L); SC[554] = Double.longBitsToDouble(0x3feb090a4915ee88L);
        SC[555] = Double.longBitsToDouble(0xbe408e6006800000L); SC[556] = Double.longBitsToDouble(0x3fe133e9ba0061d8L); SC[557] = Double.longBitsToDouble(0x3feafb8fe69a6527L);
        SC[558] = Double.longBitsToDouble(0x3e4cda72ab000000L); SC[559] = Double.longBitsToDouble(0x3fe14915d557a7c9L); SC[560] = Double.longBitsToDouble(0x3feaee049bc0aee0L);
        SC[561] = Double.longBitsToDouble(0xbe1f32f950000000L); SC[562] = Double.longBitsToDouble(0x3fe15e36dfb6bb55L); SC[563] = Double.longBitsToDouble(0x3feae068f6991699L);
        SC[564] = Double.longBitsToDouble(0x3e3138092d000000L); SC[565] = Double.longBitsToDouble(0x3fe1734d6f34d7f0L); SC[566] = Double.longBitsToDouble(0x3fead2bc96c1e1f5L);
        SC[567] = Double.longBitsToDouble(0x3e56b382dd400000L); SC[568] = Double.longBitsToDouble(0x3fe188595ae376a5L); SC[569] = Double.longBitsToDouble(0x3feac4ff962bdb6dL);
        SC[570] = Double.longBitsToDouble(0xbe3f12fafa000000L); SC[571] = Double.longBitsToDouble(0x3fe19d59f592a587L); SC[572] = Double.longBitsToDouble(0x3feab7326685eb57L);
        SC[573] = Double.longBitsToDouble(0xbe32909e5a000000L); SC[574] = Double.longBitsToDouble(0x3fe1b2500aed7ac6L); SC[575] = Double.longBitsToDouble(0x3feaa954823cf815L);
        SC[576] = Double.longBitsToDouble(0xbe6d66a897800000L); SC[577] = Double.longBitsToDouble(0x3fe1c73aa0150cf9L); SC[578] = Double.longBitsToDouble(0x3fea9b668fb0503fL);
        SC[579] = Double.longBitsToDouble(0x3e4311ea86000000L); SC[580] = Double.longBitsToDouble(0x3fe1dc1b7db74db1L); SC[581] = Double.longBitsToDouble(0x3fea8d675d9c6cc8L);
        SC[582] = Double.longBitsToDouble(0xbe041c02b8000000L); SC[583] = Double.longBitsToDouble(0x3fe1f0f08a1a06a4L); SC[584] = Double.longBitsToDouble(0x3fea7f5853bb4309L);
        SC[585] = Double.longBitsToDouble(0xbe5ca1f4ed000000L); SC[586] = Double.longBitsToDouble(0x3fe205ba57211271L); SC[587] = Double.longBitsToDouble(0x3fea71391146958fL);
        SC[588] = Double.longBitsToDouble(0xbe3910ce77000000L); SC[589] = Double.longBitsToDouble(0x3fe21a7988f8326bL); SC[590] = Double.longBitsToDouble(0x3fea63092626202fL);
        SC[591] = Double.longBitsToDouble(0x3e62bfadbee00000L); SC[592] = Double.longBitsToDouble(0x3fe22f2dc71afab6L); SC[593] = Double.longBitsToDouble(0x3fea54c8cd9fd0d9L);
        SC[594] = Double.longBitsToDouble(0xbe45f1c02a800000L); SC[595] = Double.longBitsToDouble(0x3fe243d5df4afb93L); SC[596] = Double.longBitsToDouble(0x3fea4678dbbe5e73L);
        SC[597] = Double.longBitsToDouble(0xbe1db12b90000000L); SC[598] = Double.longBitsToDouble(0x3fe2587347f493a4L); SC[599] = Double.longBitsToDouble(0x3fea38184db0df23L);
        SC[600] = Double.longBitsToDouble(0xbe17b29e00000000L); SC[601] = Double.longBitsToDouble(0x3fe26d05490f2f61L); SC[602] = Double.longBitsToDouble(0x3fea29a7a2f40b49L);
        SC[603] = Double.longBitsToDouble(0xbe2b3ddca4000000L); SC[604] = Double.longBitsToDouble(0x3fe2818be6930629L); SC[605] = Double.longBitsToDouble(0x3fea1b26d8f070d7L);
        SC[606] = Double.longBitsToDouble(0x3e2e112744000000L); SC[607] = Double.longBitsToDouble(0x3fe2960730ff2bcdL); SC[608] = Double.longBitsToDouble(0x3fea0c95e3df5e0eL);
        SC[609] = Double.longBitsToDouble(0xbe35269766000000L); SC[610] = Double.longBitsToDouble(0x3fe2aa76dafcbbf4L); SC[611] = Double.longBitsToDouble(0x3fe9fdf4fae1df6fL);
        SC[612] = Double.longBitsToDouble(0xbe309777e1000000L); SC[613] = Double.longBitsToDouble(0x3fe2bedb1b6b4e15L); SC[614] = Double.longBitsToDouble(0x3fe9ef43f6cbe162L);
        SC[615] = Double.longBitsToDouble(0x3e3ae2051f000000L); SC[616] = Double.longBitsToDouble(0x3fe2d333e4617f25L); SC[617] = Double.longBitsToDouble(0x3fe9e082e148680eL);
        SC[618] = Double.longBitsToDouble(0xbe436f6ced800000L); SC[619] = Double.longBitsToDouble(0x3fe2e780cb47180eL); SC[620] = Double.longBitsToDouble(0x3fe9d1b207f383c3L);
        SC[621] = Double.longBitsToDouble(0xbe323fdc6b000000L); SC[622] = Double.longBitsToDouble(0x3fe2fbc23fba2f44L); SC[623] = Double.longBitsToDouble(0x3fe9c2d1197130a7L);
        SC[624] = Double.longBitsToDouble(0x3debc540e0000000L); SC[625] = Double.longBitsToDouble(0x3fe30ff7fd6d967dL); SC[626] = Double.longBitsToDouble(0x3fe9b3e0478b961bL);
        SC[627] = Double.longBitsToDouble(0xbe3cfb4ed7000000L); SC[628] = Double.longBitsToDouble(0x3fe32421da0bf0e9L); SC[629] = Double.longBitsToDouble(0x3fe9a4dfb1c89326L);
        SC[630] = Double.longBitsToDouble(0x3e555802aec00000L); SC[631] = Double.longBitsToDouble(0x3fe3384042a92b1dL); SC[632] = Double.longBitsToDouble(0x3fe995cf06920d11L);
        SC[633] = Double.longBitsToDouble(0x3e360719e4000000L); SC[634] = Double.longBitsToDouble(0x3fe34c52608e3a92L); SC[635] = Double.longBitsToDouble(0x3fe986aee6d6837eL);
        SC[636] = Double.longBitsToDouble(0xbe1cbf2e48000000L); SC[637] = Double.longBitsToDouble(0x3fe36058ac8863b6L); SC[638] = Double.longBitsToDouble(0x3fe9777ef832c986L);
        SC[639] = Double.longBitsToDouble(0x3e49061c32000000L); SC[640] = Double.longBitsToDouble(0x3fe374533ab707d0L); SC[641] = Double.longBitsToDouble(0x3fe9683f2ad7e2ecL);
        SC[642] = Double.longBitsToDouble(0xbe4da84dfe000000L); SC[643] = Double.longBitsToDouble(0x3fe3884160f9488fL); SC[644] = Double.longBitsToDouble(0x3fe958f000fdd50aL);
        SC[645] = Double.longBitsToDouble(0x3e292e8a74000000L); SC[646] = Double.longBitsToDouble(0x3fe39c23eba6b22aL); SC[647] = Double.longBitsToDouble(0x3fe94990dd9cee51L);
        SC[648] = Double.longBitsToDouble(0xbe2bff5d9a000000L); SC[649] = Double.longBitsToDouble(0x3fe3affa20756bddL); SC[650] = Double.longBitsToDouble(0x3fe93a225056084aL);
        SC[651] = Double.longBitsToDouble(0x3db4c46200000000L); SC[652] = Double.longBitsToDouble(0x3fe3c3c4498e98ebL); SC[653] = Double.longBitsToDouble(0x3fe92aa41fbb951cL);
        SC[654] = Double.longBitsToDouble(0xbe3e4613e9000000L); SC[655] = Double.longBitsToDouble(0x3fe3d782261dff62L); SC[656] = Double.longBitsToDouble(0x3fe91b167e92d706L);
        SC[657] = Double.longBitsToDouble(0x3e10eb2964000000L); SC[658] = Double.longBitsToDouble(0x3fe3eb33ed579bbeL); SC[659] = Double.longBitsToDouble(0x3fe90b794146043cL);
        SC[660] = Double.longBitsToDouble(0xbe260abec2000000L); SC[661] = Double.longBitsToDouble(0x3fe3fed94c834d8aL); SC[662] = Double.longBitsToDouble(0x3fe8fbcca9583479L);
        SC[663] = Double.longBitsToDouble(0x3e46954977000000L); SC[664] = Double.longBitsToDouble(0x3fe4127281ddac03L); SC[665] = Double.longBitsToDouble(0x3fe8ec1085083553L);
        SC[666] = Double.longBitsToDouble(0x3e2a16fec2000000L); SC[667] = Double.longBitsToDouble(0x3fe425ff1f841235L); SC[668] = Double.longBitsToDouble(0x3fe8dc452ca328d3L);
        SC[669] = Double.longBitsToDouble(0xbe427bcdd3000000L); SC[670] = Double.longBitsToDouble(0x3fe4397f44aa44f2L); SC[671] = Double.longBitsToDouble(0x3fe8cc6a8771e165L);
        SC[672] = Double.longBitsToDouble(0xbe360dded4000000L); SC[673] = Double.longBitsToDouble(0x3fe44cf317a563dbL); SC[674] = Double.longBitsToDouble(0x3fe8bc8076122736L);
        SC[675] = Double.longBitsToDouble(0xbe59a8f405c00000L); SC[676] = Double.longBitsToDouble(0x3fe4605a2b02d705L); SC[677] = Double.longBitsToDouble(0x3fe8ac875232f3efL);
        SC[678] = Double.longBitsToDouble(0x3e432777dc000000L); SC[679] = Double.longBitsToDouble(0x3fe473b532bc5a67L); SC[680] = Double.longBitsToDouble(0x3fe89c7e8713120cL);
        SC[681] = Double.longBitsToDouble(0xbe51418a7b000000L); SC[682] = Double.longBitsToDouble(0x3fe4870306ca20e2L); SC[683] = Double.longBitsToDouble(0x3fe88c670a0ea774L);
        SC[684] = Double.longBitsToDouble(0xbe3fed182e000000L); SC[685] = Double.longBitsToDouble(0x3fe49a44886b5340L); SC[686] = Double.longBitsToDouble(0x3fe87c401fdf05e5L);
        SC[687] = Double.longBitsToDouble(0x3e486144d8000000L); SC[688] = Double.longBitsToDouble(0x3fe4ad796ea1410cL); SC[689] = Double.longBitsToDouble(0x3fe86c0a04dbacc5L);
        SC[690] = Double.longBitsToDouble(0x3de1bc2e60000000L); SC[691] = Double.longBitsToDouble(0x3fe4c0a14640d2afL); SC[692] = Double.longBitsToDouble(0x3fe85bc51aa114c2L);
        SC[693] = Double.longBitsToDouble(0xbe3f53d2fe000000L); SC[694] = Double.longBitsToDouble(0x3fe4d3bc5aaa8cd5L); SC[695] = Double.longBitsToDouble(0x3fe84b7121b30a13L);
        SC[696] = Double.longBitsToDouble(0xbe12e100a0000000L); SC[697] = Double.longBitsToDouble(0x3fe4e6cab91556beL); SC[698] = Double.longBitsToDouble(0x3fe83b0e0e6b6cccL);
        SC[699] = Double.longBitsToDouble(0xbe2fa58c62000000L); SC[700] = Double.longBitsToDouble(0x3fe4f9cc1c69fddeL); SC[701] = Double.longBitsToDouble(0x3fe82a9c1c1ab463L);
        SC[702] = Double.longBitsToDouble(0x3debb491e0000000L); SC[703] = Double.longBitsToDouble(0x3fe50cc09fdcbd92L); SC[704] = Double.longBitsToDouble(0x3fe81a1b3342f858L);
        SC[705] = Double.longBitsToDouble(0x3e3a115410000000L); SC[706] = Double.longBitsToDouble(0x3fe51fa82c3aa029L); SC[707] = Double.longBitsToDouble(0x3fe8098b67ea8509L);
        SC[708] = Double.longBitsToDouble(0x3e4ab0a5d3000000L); SC[709] = Double.longBitsToDouble(0x3fe53282b20b96b6L); SC[710] = Double.longBitsToDouble(0x3fe7f8ecc7919530L);
        SC[711] = Double.longBitsToDouble(0xbe3cba0438000000L); SC[712] = Double.longBitsToDouble(0x3fe5454fe43a7d7cL); SC[713] = Double.longBitsToDouble(0x3fe7e83f96af78a0L);
        SC[714] = Double.longBitsToDouble(0xbe20dd83a4000000L); SC[715] = Double.longBitsToDouble(0x3fe5581033a81573L); SC[716] = Double.longBitsToDouble(0x3fe7d783712e20ecL);
        SC[717] = Double.longBitsToDouble(0xbe3e9a8299000000L); SC[718] = Double.longBitsToDouble(0x3fe56ac33fbb8253L); SC[719] = Double.longBitsToDouble(0x3fe7c6b8acf90fa6L);
        SC[720] = Double.longBitsToDouble(0x3e2225c4aa000000L); SC[721] = Double.longBitsToDouble(0x3fe57d6939d4b513L); SC[722] = Double.longBitsToDouble(0x3fe7b5df1da18065L);
        SC[723] = Double.longBitsToDouble(0xbe482e66e0000000L); SC[724] = Double.longBitsToDouble(0x3fe59001b9e64d79L); SC[725] = Double.longBitsToDouble(0x3fe7a4f72157cfdfL);
        SC[726] = Double.longBitsToDouble(0x3e551a6a35400000L); SC[727] = Double.longBitsToDouble(0x3fe5a28d5b36d597L); SC[728] = Double.longBitsToDouble(0x3fe794002a7c9023L);
        SC[729] = Double.longBitsToDouble(0x3e513917f4000000L); SC[730] = Double.longBitsToDouble(0x3fe5b50b4e10bec1L); SC[731] = Double.longBitsToDouble(0x3fe782faf6dc7ba2L);
        SC[732] = Double.longBitsToDouble(0x3e149310cc000000L); SC[733] = Double.longBitsToDouble(0x3fe5c77bc15ab4efL); SC[734] = Double.longBitsToDouble(0x3fe771e75c43942eL);
        SC[735] = Double.longBitsToDouble(0x3e124d493c000000L); SC[736] = Double.longBitsToDouble(0x3fe5d9dee9de49dbL); SC[737] = Double.longBitsToDouble(0x3fe760c529bc17b0L);
        SC[738] = Double.longBitsToDouble(0xbe504638f7000000L); SC[739] = Double.longBitsToDouble(0x3fe5ec347044e0f4L); SC[740] = Double.longBitsToDouble(0x3fe74f94b0af9720L);
        SC[741] = Double.longBitsToDouble(0xbe23f41b28000000L); SC[742] = Double.longBitsToDouble(0x3fe5fe7cb834600cL); SC[743] = Double.longBitsToDouble(0x3fe73e55936a5160L);
        SC[744] = Double.longBitsToDouble(0xbe1a5f6f5c000000L); SC[745] = Double.longBitsToDouble(0x3fe610b7515d1562L); SC[746] = Double.longBitsToDouble(0x3fe72d083b8214ebL);
        SC[747] = Double.longBitsToDouble(0x3e319fb2e0000000L); SC[748] = Double.longBitsToDouble(0x3fe622e459eafbc1L); SC[749] = Double.longBitsToDouble(0x3fe71bac8c7b0592L);
        SC[750] = Double.longBitsToDouble(0xbe356d2c2b000000L); SC[751] = Double.longBitsToDouble(0x3fe6350396fe4e62L); SC[752] = Double.longBitsToDouble(0x3fe70a42bec51665L);
        SC[753] = Double.longBitsToDouble(0xbe33c156c2000000L); SC[754] = Double.longBitsToDouble(0x3fe64715385bed93L); SC[755] = Double.longBitsToDouble(0x3fe6f8caa4969708L);
        SC[756] = Double.longBitsToDouble(0xbe2f23e576000000L); SC[757] = Double.longBitsToDouble(0x3fe659191d2fd57fL); SC[758] = Double.longBitsToDouble(0x3fe6e7445d74f711L);
        SC[759] = Double.longBitsToDouble(0x3e11e4be38000000L); SC[760] = Double.longBitsToDouble(0x3fe66b0f41d484c4L); SC[761] = Double.longBitsToDouble(0x3fe6d5afecd4938dL);
        SC[762] = Double.longBitsToDouble(0xbe4397cc8d800000L); SC[763] = Double.longBitsToDouble(0x3fe67cf76eac73dfL); SC[764] = Double.longBitsToDouble(0x3fe6c40d89625f63L);
        SC[765] = Double.longBitsToDouble(0xbe3202f686000000L); SC[766] = Double.longBitsToDouble(0x3fe68ed1e0990551L); SC[767] = Double.longBitsToDouble(0x3fe6b25cf728c350L);
    }

}
