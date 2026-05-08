package org.jquantlib.math.transcendental;

/**
 * Pure-Java port of CORE-MATH's correctly-rounded {@code cr_pow(double, double)}.
 *
 * <p><b>Status:</b> Phase 2n A.1.b/1 — specials path (bit-exact),
 * Stage 1 fast path (Hubrecht/Jeannerod/Zimmermann ARITH 2023 algorithm
 * phase_1) ported. When Stage 1's Ziv error-bound test passes (~99.95%
 * of inputs), the result is bit-exact against CORE-MATH cr_pow. When
 * the test fails (Ziv fall-through), we currently fall back to
 * {@link Math#pow}; the upcoming Stage 2 (Dint64) and Stage 3 (Qint64
 * with exact_pow rounding-boundary detection) follow-up commits will
 * close that gap.
 *
 * <p>Source: CORE-MATH {@code src/binary64/pow/pow.c} (Tom Hubrecht and
 * Paul Zimmermann; CERN/INRIA; MIT-licensed). Algorithm reference is
 * Hubrecht, Jeannerod, Zimmermann, "Towards a correctly-rounded and fast
 * power function in binary64 arithmetic", ARITH 2023, with detailed
 * proofs in HAL hal-04159652. Tables (_INVERSE, _LOG_INV, T1, T2, P_1,
 * Q_1) extracted via {@code migration-harness/tools/extract-pow-tables.py}.
 *
 * <p>Specials handling mirrors the C source verbatim — IEEE 754-2019
 * Section 9.2.1 dispatch on (x, y) including ±0, ±inf, NaN, integer-y,
 * odd-integer-y discrimination, and pow(1, NaN) = 1 / pow(NaN, 0) = 1
 * exemptions.
 *
 * <p>The Stage 1 fast path computes log(x) via a 182-entry reciprocal
 * table reduction + degree-8 Sollya polynomial (in p_1), multiplies by y
 * to get y*log(x), then computes exp via a 64x64 product table
 * decomposition (T1, T2) + degree-4 polynomial (in q_1). The Ziv
 * rounding test (Lemma 5 from reference [5]) compares
 * {@code res_h + fma(err, ±res_h, res_l)} for opposite-sign err
 * perturbations; bit-exact return requires {@code res_min == res_max}.
 *
 * <p>For inputs where Stage 1's err test fails — typically inputs where
 * {@code y * log(x)} is near a rounding boundary — we currently call
 * {@code Math.pow}. JVM's Math.pow has its own ~1 ULP floor and may
 * differ from cr_pow by up to a few ULPs in the worst-rounding cases.
 * The test suite at {@code JQuantMathPowTest} validates the categories
 * we have already ported bit-exact and is structured to incrementally
 * widen its scope as Stage 2 + Stage 3 land.
 */
final class PowKernel {

    private PowKernel() {}

    /** IEEE-754 double exponent mask (bits 52-62). */
    private static final long EXP_MASK = 0x7ff0000000000000L;

    /**
     * Pure-Java cr_pow(x, y).
     *
     * <p><b>Specials path (bit-exact):</b> per pow.c lines 1495-1702.
     *
     * <p><b>Finite path:</b> Stage 1 fast path with Ziv error-bound
     * fallthrough to {@link Math#pow} pending Stage 2/3 port.
     */
    static double pow(double x, double y) {
        final long xb = Double.doubleToRawLongBits(x);
        final long yb = Double.doubleToRawLongBits(y);

        // ============================================================
        // Specials dispatch — pow.c:1502-1612
        // ============================================================
        if (Long.compareUnsigned(xb, 0x7ff0000000000000L) >= 0
                || Long.compareUnsigned(yb, 0x7ff0000000000000L) >= 0) {
            // x is NaN
            if (Double.isNaN(x)) {
                if (y == 0.0 && !isSignaling(xb)) return 1.0;
                return x + x;
            }
            // y is NaN
            if (Double.isNaN(y)) {
                if (x == 1.0 && !isSignaling(yb)) return 1.0;
                return y + y;
            }

            // x = +inf
            if (xb == 0x7ff0000000000000L) {
                if (y == 0.0) return 1.0;
                if (y < 0.0) return 0.0;
                if (y > 0.0) return Double.POSITIVE_INFINITY;
            }
            // x = -inf
            if (xb == 0xfff0000000000000L) {
                final boolean yIsOddInt = isInt(y) && !isInt(y * 0.5);
                if (yIsOddInt) {
                    if (y < 0.0) return -0.0;
                    return Double.NEGATIVE_INFINITY;
                }
                if (y < 0.0) return 0.0;
                if (y > 0.0) return Double.POSITIVE_INFINITY;
            }

            // y = +inf
            if (yb == 0x7ff0000000000000L) {
                if (x == 0.0) return 0.0;
                if (x == -1.0 || x == 1.0) return 1.0;
                if (-1.0 < x && x < 1.0) return 0.0;
                return Double.POSITIVE_INFINITY;
            }
            // y = -inf
            if (yb == 0xfff0000000000000L) {
                if (x == 0.0) return Double.POSITIVE_INFINITY;
                if (x == -1.0 || x == 1.0) return 1.0;
                if (-1.0 < x && x < 1.0) return Double.POSITIVE_INFINITY;
                return 0.0;
            }
        }
        // From here, x and y are finite.

        // ============================================================
        // Negative or zero base — pow.c:1615-1702
        // ============================================================
        double s = 1.0; // sign of result; -1 only when x<0 finite and y is odd integer

        if (x <= 0.0) {
            if (y == 0.0) return 1.0;

            if (xb == 0x0L) {
                final boolean yIsOddInt = isInt(y) && !isInt(y * 0.5);
                if (yIsOddInt) {
                    return (y < 0.0) ? Double.POSITIVE_INFINITY : 0.0;
                }
                if (y > 0.0) return 0.0;
                return Double.POSITIVE_INFINITY;
            }
            if (xb == 0x8000000000000000L) {
                final boolean yIsOddInt = isInt(y) && !isInt(y * 0.5);
                if (yIsOddInt) {
                    return (y < 0.0) ? Double.NEGATIVE_INFINITY : -0.0;
                }
                if (y > 0.0) return 0.0;
                return Double.POSITIVE_INFINITY;
            }
            // x < 0 finite
            if (!isInt(y)) {
                return Double.NaN;
            }
            // y is integer; sign = (-1)^y, with parity unreliable for |y| >= 2^53.
            final double absY = Math.abs(y);
            final long yParity = (absY >= 0x1p53) ? 0L : ((long) y) & 0x1L;
            s = (yParity == 0L) ? 1.0 : -1.0;
            x = -x; // operate on |x| from here on
        }

        // ============================================================
        // Stage 1 fast path — pow.c:1704-1793  (algorithm phase_1)
        // ============================================================
        // Approximate log(x) -> (lh, ll).
        final double[] hl = new double[2];
        final boolean cancel = log1(hl, x);
        double lh = hl[0], ll = hl[1];

        // Avoid spurious underflow/overflow in y*log(x):
        //   underflow: |log(x)| can be as small as 2^-53 (for x=1-2^-53), require |y| >= 2^-969
        //   overflow:  |log(x)| < 745 (for x=2^-1074), require |y| < 2^1014
        final int ey = (int) ((yb >>> 52) & 0x7ffL);
        if (ey < 0x36 || ey >= 0x7f5) {
            lh = ll = Double.NaN;
        }

        // y * (lh + ll) -> (rh, rl)  (s_mul)
        final double[] rhl = new double[2];
        sMul(rhl, y, lh, ll);
        final double rh = rhl[0], rl = rhl[1];

        // exp(rh+rl) * s -> (eh, el)  (exp_1)
        final double[] el = new double[2];
        exp1(el, rh, rl, s);

        // Ziv rounding test
        // err[0] = 2^-63.797 (when 1/sqrt(2) >= x or x >= sqrt(2))
        // err[1] = 2^-57.579 (when 1/sqrt(2) < x < sqrt(2)), i.e. cancel=true.
        final double errBound = cancel ? 0x1.57p-58 : 0x1.27p-64;
        final double res_h = el[0];
        final double res_l = el[1];
        final double res_min = res_h + Math.fma(errBound, -res_h, res_l);
        final double res_max = res_h + Math.fma(errBound, res_h, res_l);

        if (res_min == res_max) {
            return res_max;
        }

        // Easy short-circuits — pow.c:1772-1793.
        if (y == 1.0) return s * x;
        if (y == 2.0) return x * x;
        if (y == 0.5) return Math.sqrt(x);
        if (y == 0.0) return 1.0;

        // Stage 1 didn't satisfy the rounding test. Until Stages 2/3
        // land, fall back to JVM's Math.pow. This may differ from cr_pow
        // by up to a few ULPs on the rounding-boundary cases (~2^-11.5
        // of finite inputs).
        // Note: x has already been negated above when it was <0 finite,
        // and s holds the result-sign; reconstruct the original input
        // via s * sign(x_orig). For x>0 originally, s=1 so sign is fine.
        return s * Math.pow(x, y);
    }

    // ============================================================
    //  log_1 — fast path log(x) approximation  pow.c:581-648
    // ============================================================

    /**
     * Compute (h, l) double-double approximating log(x), x &gt; 0 finite.
     * Returns {@code true} when the special "_e == 0 and |l| &gt; |h|*2^-24"
     * cancellation case fired (CORE-MATH's `cancel`); used by the
     * outer Ziv test to pick a wider error bound.
     */
    private static boolean log1(double[] out_hl, double x) {
        final long u = Double.doubleToRawLongBits(x);
        long m = u & 0x000fffffffffffffL;
        long e = (u >>> 52) & 0x7ffL;

        long tBits;
        if (e != 0L) {
            tBits = m | (0x3ffL << 52);
            m += (1L << 52);
            e -= 0x3ffL;
        } else {
            // subnormal
            final int k = Long.numberOfLeadingZeros(m) - 11;
            e = -0x3feL - (long) k;
            m <<= k;
            tBits = m | (0x3ffL << 52);
        }

        double t = Double.longBitsToDouble(tBits);

        // c = 1 if m >= 0x16a09e667f3bcd (sqrt(2) test)
        final long c = (Long.compareUnsigned(m, 0x16a09e667f3bcdL) >= 0) ? 1L : 0L;
        e += c;

        final double E = (double) e;
        // i = m >> cm[c]; cm = {44, 45}
        final int shift = (c == 0L) ? 44 : 45;
        final long i = m >>> shift;

        // t *= cy[c]; cy = {1.0, 0.5}
        if (c != 0L) t *= 0.5;

        // _INVERSE[i-181], _LOG_INV[i-181]
        final int idx = (int) (i - 181L);
        final double r = _INVERSE[idx];
        final double l1 = _LOG_INV_H[idx];
        final double l2 = _LOG_INV_L[idx];

        final double z = Math.fma(r, t, -1.0);

        // LOG2 split: high (integer*2^-42) + low.
        final double LOG2_H = 0x1.62e42fefa38p-1;
        final double LOG2_L = 0x1.ef35793c7673p-45;

        final double th = Math.fma(E, LOG2_H, l1);
        final double tl = Math.fma(E, LOG2_L, l2);

        // fast_sum(h, l, th, z, tl)
        // fast_two_sum then add tl into l.
        double h0 = th + z;
        double err0 = h0 - th;
        double l0 = z - err0;
        l0 += tl;

        // p_1(ph, pl, z)
        final double[] phl = new double[2];
        p1(phl, z);
        final double ph = phl[0], pl = phl[1];

        // fast_sum(h, l, h, ph, l + pl)
        final double sumLp = l0 + pl;
        double h1 = h0 + ph;
        double err1 = h1 - h0;
        double l1f = ph - err1;
        l1f += sumLp;

        // cancellation test (only if e == 0 AND |l| > |h|*2^-24)
        boolean cancelOut = false;
        if (e == 0L && Math.abs(l1f) > Math.abs(h1) * 0x1p-24) {
            // fast_two_sum(h, l, h, l)
            double h2 = h1 + l1f;
            double err2 = h2 - h1;
            double l2f = l1f - err2;
            h1 = h2;
            l1f = l2f;
            cancelOut = true;
        }

        out_hl[0] = h1;
        out_hl[1] = l1f;
        return cancelOut;
    }

    // ============================================================
    //  p_1 — log(1+z)-z poly, pow.c:323-334
    // ============================================================

    private static void p1(double[] out_phl, double z) {
        // a_mul(wh, wl, z, z)
        final double wh = z * z;
        final double wl = Math.fma(z, z, -wh);

        double t = Math.fma(P_1[5], z, P_1[4]);
        double u = Math.fma(P_1[3], z, P_1[2]);
        double v = Math.fma(P_1[1], z, P_1[0]);
        u = Math.fma(t, wh, u);
        v = Math.fma(u, wh, v);
        u = v * wh;
        final double ph = -0.5 * wh;
        final double pl = Math.fma(u, z, -0.5 * wl);
        out_phl[0] = ph;
        out_phl[1] = pl;
    }

    // ============================================================
    //  s_mul: a * (bh + bl) -> (hi, lo)
    // ============================================================

    private static void sMul(double[] out_hl, double a, double bh, double bl) {
        // a_mul(hi, s, a, bh)
        final double hi = a * bh;
        final double s_ = Math.fma(a, bh, -hi);
        // lo = fma(a, bl, s)
        final double lo = Math.fma(a, bl, s_);
        out_hl[0] = hi;
        out_hl[1] = lo;
    }

    // ============================================================
    //  exp_1 — pow.c:953-1057
    // ============================================================

    /**
     * exp(rh+rl) * s -> (eh, el), with the special boundary handling
     * matching pow.c. When rh is outside the "always overflow/underflow
     * resolves" band, sets eh=el=NaN to force Ziv fall-through.
     */
    private static void exp1(double[] out_eh, double rh, double rl, double s) {
        // Boundaries in pow.c:
        //   RHO0 = -0x1.74910ee4e8a27p+9
        //   RHO1 = -0x1.483b8cca421afp+9
        //   RHO2 =  0x1.62e42e709a95bp+9
        //   RHO3 =  0x1.62e4316ea5df9p+9
        final double RHO0 = -0x1.74910ee4e8a27p+9;
        final double RHO1 = -0x1.483b8cca421afp+9;
        final double RHO2 = 0x1.62e42e709a95bp+9;
        final double RHO3 = 0x1.62e4316ea5df9p+9;

        if (Double.isNaN(rh) || rh > RHO2) {
            if (!Double.isNaN(rh) && rh > RHO3) {
                // overflow regime: return ±DBL_MAX (or ±inf via rounding).
                out_eh[0] = 0x1.fffffffffffffp+1023 * s;
                out_eh[1] = 0x1.fffffffffffffp+1023 * s;
            } else {
                // intermediate region — defer to phase 2
                out_eh[0] = Double.NaN;
                out_eh[1] = Double.NaN;
            }
            return;
        }
        if (rh < RHO1) {
            if (rh < RHO0) {
                // underflow regime
                out_eh[0] = +0.0 * s;
                out_eh[1] = 0x1p-1074 * (0.5 * s);
            } else {
                out_eh[0] = Double.NaN;
                out_eh[1] = Double.NaN;
            }
            return;
        }

        final double INVLOG2 = 0x1.71547652b82fep+12;
        // k = roundeven(rh * INVLOG2) (round half to even)
        final double k = Math.rint(rh * INVLOG2);

        final double LOG2H = 0x1.62e42fefa39efp-13;
        final double LOG2L = 0x1.abc9e3b39803fp-68;

        final double zh = Math.fma(LOG2H, -k, rh);
        final double zl = Math.fma(LOG2L, -k, rl);

        final long K = (long) k;
        final long M = (K >> 12) + 0x3ffL;
        final int i2 = (int) ((K >> 6) & 0x3fL);
        final int i1 = (int) (K & 0x3fL);

        final double t1h = _T1_H[i2], t1l = _T1_L[i2];
        final double t2h = _T2_H[i1], t2l = _T2_L[i1];

        // d_mul(eh, el, t2h, t2l, t1h, t1l)
        final double[] em = new double[2];
        dMul(em, t2h, t2l, t1h, t1l);
        double eh = em[0], el = em[1];

        // q_1(qh, ql, zh + zl)
        final double[] qhl = new double[2];
        q1(qhl, zh + zl);
        final double qh = qhl[0], ql = qhl[1];

        // d_mul(eh, el, eh, el, qh, ql)
        dMul(em, eh, el, qh, ql);
        eh = em[0]; el = em[1];

        final double dscaleBits = Double.longBitsToDouble(M << 52);
        final double dscale = dscaleBits * s;
        eh *= dscale;
        el *= dscale;
        out_eh[0] = eh;
        out_eh[1] = el;
    }

    // ============================================================
    //  q_1 — pow.c:120-132
    // ============================================================

    private static void q1(double[] out_qhl, double z) {
        double q = Math.fma(Q_1[4], z, Q_1[3]);
        q = Math.fma(q, z, Q_1[2]);
        final double h0 = Math.fma(q, z, Q_1[1]);
        // a_mul(h1, l1, z, h0)
        final double h1 = z * h0;
        final double l1 = Math.fma(z, h0, -h1);
        // fast_sum(qh, ql, Q_1[0], h1, l1)
        final double qh0 = Q_1[0] + h1;
        final double err = qh0 - Q_1[0];
        double ql0 = h1 - err;
        ql0 += l1;
        out_qhl[0] = qh0;
        out_qhl[1] = ql0;
    }

    // ============================================================
    //  d_mul: (ah + al) * (bh + bl) - (al * bl)
    // ============================================================

    private static void dMul(double[] out_hl, double ah, double al, double bh, double bl) {
        final double hi = ah * bh;
        final double s_ = Math.fma(ah, bh, -hi);
        final double t = Math.fma(al, bh, s_);
        final double lo = Math.fma(ah, bl, t);
        out_hl[0] = hi;
        out_hl[1] = lo;
    }

    // ============================================================
    //  Helpers
    // ============================================================

    /** True iff x is an integer (including ±0). */
    private static boolean isInt(double x) {
        return x == Math.rint(x);
    }

    /** Returns true if the encoded NaN is signaling (bit 51 = 0). */
    private static boolean isSignaling(long bits) {
        return ((bits & EXP_MASK) == EXP_MASK)
            && ((bits & 0x000fffffffffffffL) != 0L)
            && ((bits & (1L << 51)) == 0L);
    }

    // ============================================================
    //  Tables — extracted by migration-harness/tools/extract-pow-tables.py
    //  Source: CORE-MATH coremath/pow.h. Do not hand-edit.
    // ============================================================

    // ===== pow.h: _INVERSE[182] =====
    private static final double[] _INVERSE = new double[182];
    static {
        long[] bits = {
            0x3ff6900000000000L, 0x3ff6700000000000L, 0x3ff6500000000000L, 0x3ff6300000000000L,
            0x3ff6100000000000L, 0x3ff5f00000000000L, 0x3ff5e00000000000L, 0x3ff5c00000000000L,
            0x3ff5a00000000000L, 0x3ff5800000000000L, 0x3ff5600000000000L, 0x3ff5400000000000L,
            0x3ff5300000000000L, 0x3ff5100000000000L, 0x3ff4f00000000000L, 0x3ff4e00000000000L,
            0x3ff4c00000000000L, 0x3ff4a00000000000L, 0x3ff4800000000000L, 0x3ff4700000000000L,
            0x3ff4500000000000L, 0x3ff4400000000000L, 0x3ff4200000000000L, 0x3ff4000000000000L,
            0x3ff3f00000000000L, 0x3ff3d00000000000L, 0x3ff3c00000000000L, 0x3ff3a00000000000L,
            0x3ff3900000000000L, 0x3ff3700000000000L, 0x3ff3600000000000L, 0x3ff3400000000000L,
            0x3ff3300000000000L, 0x3ff3200000000000L, 0x3ff3000000000000L, 0x3ff2f00000000000L,
            0x3ff2d00000000000L, 0x3ff2c00000000000L, 0x3ff2b00000000000L, 0x3ff2900000000000L,
            0x3ff2800000000000L, 0x3ff2700000000000L, 0x3ff2500000000000L, 0x3ff2400000000000L,
            0x3ff2300000000000L, 0x3ff2100000000000L, 0x3ff2000000000000L, 0x3ff1f00000000000L,
            0x3ff1e00000000000L, 0x3ff1c00000000000L, 0x3ff1b00000000000L, 0x3ff1a00000000000L,
            0x3ff1900000000000L, 0x3ff1700000000000L, 0x3ff1600000000000L, 0x3ff1500000000000L,
            0x3ff1400000000000L, 0x3ff1300000000000L, 0x3ff1200000000000L, 0x3ff1000000000000L,
            0x3ff0f00000000000L, 0x3ff0e00000000000L, 0x3ff0d00000000000L, 0x3ff0c00000000000L,
            0x3ff0b00000000000L, 0x3ff0a00000000000L, 0x3ff0900000000000L, 0x3ff0800000000000L,
            0x3ff0700000000000L, 0x3ff0600000000000L, 0x3ff0500000000000L, 0x3ff0400000000000L,
            0x3ff0300000000000L, 0x3ff0200000000000L, 0x3ff0000000000000L, 0x3ff0000000000000L,
            0x3fefd00000000000L, 0x3fefb00000000000L, 0x3fef900000000000L, 0x3fef700000000000L,
            0x3fef500000000000L, 0x3fef300000000000L, 0x3fef100000000000L, 0x3fef000000000000L,
            0x3feee00000000000L, 0x3feec00000000000L, 0x3feea00000000000L, 0x3fee800000000000L,
            0x3fee600000000000L, 0x3fee500000000000L, 0x3fee300000000000L, 0x3fee100000000000L,
            0x3fedf00000000000L, 0x3fedd00000000000L, 0x3fedc00000000000L, 0x3feda00000000000L,
            0x3fed800000000000L, 0x3fed700000000000L, 0x3fed500000000000L, 0x3fed300000000000L,
            0x3fed200000000000L, 0x3fed000000000000L, 0x3fece00000000000L, 0x3fecd00000000000L,
            0x3fecb00000000000L, 0x3fec900000000000L, 0x3fec800000000000L, 0x3fec600000000000L,
            0x3fec500000000000L, 0x3fec300000000000L, 0x3fec200000000000L, 0x3fec000000000000L,
            0x3febf00000000000L, 0x3febd00000000000L, 0x3febc00000000000L, 0x3feba00000000000L,
            0x3feb900000000000L, 0x3feb700000000000L, 0x3feb600000000000L, 0x3feb400000000000L,
            0x3feb300000000000L, 0x3feb100000000000L, 0x3feb000000000000L, 0x3feae00000000000L,
            0x3fead00000000000L, 0x3feac00000000000L, 0x3feaa00000000000L, 0x3fea900000000000L,
            0x3fea700000000000L, 0x3fea600000000000L, 0x3fea500000000000L, 0x3fea300000000000L,
            0x3fea200000000000L, 0x3fea100000000000L, 0x3fe9f00000000000L, 0x3fe9e00000000000L,
            0x3fe9d00000000000L, 0x3fe9c00000000000L, 0x3fe9a00000000000L, 0x3fe9900000000000L,
            0x3fe9800000000000L, 0x3fe9600000000000L, 0x3fe9500000000000L, 0x3fe9400000000000L,
            0x3fe9300000000000L, 0x3fe9100000000000L, 0x3fe9000000000000L, 0x3fe8f00000000000L,
            0x3fe8e00000000000L, 0x3fe8d00000000000L, 0x3fe8b00000000000L, 0x3fe8a00000000000L,
            0x3fe8900000000000L, 0x3fe8800000000000L, 0x3fe8700000000000L, 0x3fe8600000000000L,
            0x3fe8400000000000L, 0x3fe8300000000000L, 0x3fe8200000000000L, 0x3fe8100000000000L,
            0x3fe8000000000000L, 0x3fe7f00000000000L, 0x3fe7e00000000000L, 0x3fe7c00000000000L,
            0x3fe7b00000000000L, 0x3fe7a00000000000L, 0x3fe7900000000000L, 0x3fe7800000000000L,
            0x3fe7700000000000L, 0x3fe7600000000000L, 0x3fe7500000000000L, 0x3fe7400000000000L,
            0x3fe7300000000000L, 0x3fe7200000000000L, 0x3fe7100000000000L, 0x3fe7000000000000L,
            0x3fe6f00000000000L, 0x3fe6e00000000000L, 0x3fe6d00000000000L, 0x3fe6c00000000000L,
            0x3fe6b00000000000L, 0x3fe6a00000000000L,
        };
        for (int i = 0; i < bits.length; i++) _INVERSE[i] = Double.longBitsToDouble(bits[i]);
    }

    // ===== pow.h: _LOG_INV[182][2] =====
    private static final double[] _LOG_INV_H = new double[182];
    private static final double[] _LOG_INV_L = new double[182];
    static {
        long[] hi = {
            0xbfd5ff3070a79000L, 0xbfd5a42ab0f4d000L, 0xbfd548a2c3add000L, 0xbfd4ec9732600000L,
            0xbfd4900680401000L, 0xbfd432ef2a04f000L, 0xbfd404308686a000L, 0xbfd3a64c55694000L,
            0xbfd347dd9a988000L, 0xbfd2e8e2bae12000L, 0xbfd2895a13de8000L, 0xbfd22941fbcf8000L,
            0xbfd1f8ff9e48a000L, 0xbfd1980d2dd42000L, 0xbfd136870293b000L, 0xbfd1058bf9ae5000L,
            0xbfd0a324e2739000L, 0xbfd0402594b4d000L, 0xbfcfb9186d5e4000L, 0xbfcf550a564b8000L,
            0xbfce8c0252aa6000L, 0xbfce27076e2b0000L, 0xbfcd5c216b4fc000L, 0xbfcc8ff7c79aa000L,
            0xbfcc2968558c2000L, 0xbfcb5b519e8fc000L, 0xbfcaf3c94e80c000L, 0xbfca23bc1fe2c000L,
            0xbfc9bb362e7e0000L, 0xbfc8e928de886000L, 0xbfc87fa06520c000L, 0xbfc7ab890210e000L,
            0xbfc740f8f5404000L, 0xbfc6d60fe719e000L, 0xbfc5ff3070a7a000L, 0xbfc59338d9982000L,
            0xbfc4ba36f39a6000L, 0xbfc44d2b6ccb8000L, 0xbfc3dfc2b0ecc000L, 0xbfc303d718e48000L,
            0xbfc29552f8200000L, 0xbfc2266f190a6000L, 0xbfc1478584674000L, 0xbfc0d77e7cd08000L,
            0xbfc0671512ca6000L, 0xbfbf0a30c0118000L, 0xbfbe27076e2b0000L, 0xbfbd4313d66cc000L,
            0xbfbc5e548f5bc000L, 0xbfba926d3a4ac000L, 0xbfb9ab4246204000L, 0xbfb8c345d6318000L,
            0xbfb7da766d7b0000L, 0xbfb60658a9374000L, 0xbfb51b073f060000L, 0xbfb42edcbea64000L,
            0xbfb341d7961bc000L, 0xbfb253f62f0a0000L, 0xbfb16536eea38000L, 0xbfaf0a30c0118000L,
            0xbfad276b8adb0000L, 0xbfab42dd71198000L, 0xbfa95c830ec90000L, 0xbfa77458f6330000L,
            0xbfa58a5bafc90000L, 0xbfa39e87b9fe8000L, 0xbfa1b0d989240000L, 0xbf9f829b0e780000L,
            0xbf9b9fc027b00000L, 0xbf97b91b07d60000L, 0xbf93cea443470000L, 0xbf8fc0a8b0fc0000L,
            0xbf87dc475f820000L, 0xbf7fe02a6b100000L, 0x0000000000000000L, 0x0000000000000000L,
            0x3f78121214580000L, 0x3f841929f9680000L, 0x3f8c317384c80000L, 0x3f9228fb1fea0000L,
            0x3f963d6178690000L, 0x3f9a55f548c60000L, 0x3f9e72bf28140000L, 0x3fa0415d89e78000L,
            0x3fa252f32f8d0000L, 0x3fa466aed42e0000L, 0x3fa67c94f2d48000L, 0x3fa894aa149f8000L,
            0x3faaaef2d0fb0000L, 0x3fabbcebfc690000L, 0x3fadda8adc680000L, 0x3faffa6911ab8000L,
            0x3fb10e45b3cb0000L, 0x3fb2207b5c784000L, 0x3fb2aa04a4470000L, 0x3fb3bdf5a7d20000L,
            0x3fb4d3115d208000L, 0x3fb55e10050e0000L, 0x3fb674f089364000L, 0x3fb78d02263d8000L,
            0x3fb8197e2f410000L, 0x3fb9335e5d594000L, 0x3fba4e7640b1c000L, 0x3fbadc77ee5b0000L,
            0x3fbbf968769fc000L, 0x3fbd179788218000L, 0x3fbda72763844000L, 0x3fbec739830a0000L,
            0x3fbf57bc7d900000L, 0x3fc03cdc0a51e000L, 0x3fc08598b59e4000L, 0x3fc1178e8227e000L,
            0x3fc160c8024b2000L, 0x3fc1f3b925f26000L, 0x3fc23d712a49c000L, 0x3fc2d1610c868000L,
            0x3fc31b994d3a4000L, 0x3fc3b08b67580000L, 0x3fc3fb45a5992000L, 0x3fc4913d8333c000L,
            0x3fc4dc7b897bc000L, 0x3fc5737cc9018000L, 0x3fc5bf406b544000L, 0x3fc6574ebe8c2000L,
            0x3fc6a399dabbe000L, 0x3fc6f0128b756000L, 0x3fc7898d85444000L, 0x3fc7d6903caf6000L,
            0x3fc871213750e000L, 0x3fc8beafeb390000L, 0x3fc90c6db9fcc000L, 0x3fc9a8778deba000L,
            0x3fc9f6c40708a000L, 0x3fca454082e6a000L, 0x3fcae2ca6f672000L, 0x3fcb31d8575bc000L,
            0x3fcb811730b82000L, 0x3fcbd087383be000L, 0x3fcc6ffbc6f00000L, 0x3fccc000c9db4000L,
            0x3fcd1037f2656000L, 0x3fcdb13db0d48000L, 0x3fce020cc6236000L, 0x3fce530effe72000L,
            0x3fcea4449f04a000L, 0x3fcf474b134e0000L, 0x3fcf991c6cb3c000L, 0x3fcfeb2233ea0000L,
            0x3fd01eae5626c000L, 0x3fd047e60cde8000L, 0x3fd09aa572e6c000L, 0x3fd0c42d67616000L,
            0x3fd0edd060b78000L, 0x3fd1178e8227e000L, 0x3fd14167ef367000L, 0x3fd16b5ccbad0000L,
            0x3fd1bf99635a7000L, 0x3fd1e9e16788a000L, 0x3fd214456d0ec000L, 0x3fd23ec5991ec000L,
            0x3fd269621134e000L, 0x3fd2941afb187000L, 0x3fd2bef07cdc9000L, 0x3fd314f1e1d36000L,
            0x3fd3401e12aed000L, 0x3fd36b6776be1000L, 0x3fd396ce359bc000L, 0x3fd3c25277333000L,
            0x3fd3edf463c17000L, 0x3fd419b423d5f000L, 0x3fd44591e053a000L, 0x3fd4718dc271c000L,
            0x3fd49da7f3bcc000L, 0x3fd4c9e09e173000L, 0x3fd4f637ebbaa000L, 0x3fd522ae0738a000L,
            0x3fd54f431b7be000L, 0x3fd57bf753c8d000L, 0x3fd5a8cadbbee000L, 0x3fd5d5bddf596000L,
            0x3fd602d08af09000L, 0x3fd630030b3ab000L,
        };
        long[] lo = {
            0xbd2e9e439f105039L, 0x3cde63af2df7ba69L, 0xbd23167e63081cf7L, 0xbd234d7aaf04d104L,
            0x3d38bccffe1a0f8cL, 0x3d3fb129931715adL, 0xbd3f8ef43049f7d3L, 0xbd37a71cbcd735d0L,
            0x3d25594dd4c58092L, 0x3d267b1e99b72bd8L, 0xbd3a8d7ad24c13f0L, 0x3d3a6976f5eb0963L,
            0xbd27946c040cbe77L, 0xbd2b7b3a7a361c9aL, 0x3d3d3e8499d67123L, 0x3d34ab9d817d52cdL,
            0xbd0c6bee7ef4030eL, 0xbcf036b89ef42d7fL, 0x3d0d572aab993c87L, 0x3d2323e3a09202feL,
            0x3d26805b80e8e6ffL, 0x3d3a342c2af0003cL, 0x3d21ba91bbca681bL, 0x3d27794f689f8434L,
            0x3d2cfd73dee38a40L, 0x3d34b722ec011f31L, 0x3cba4e633fcd9066L, 0x3d3539cd91dc9f0bL,
            0x3d21f2a8a1ce0ffcL, 0xbd3a8154b13d72d5L, 0xbd322120401202fcL, 0x3d2bdb9072534a58L,
            0x3d30b66c99018aa1L, 0x3d3bc6e557134767L, 0x3d38586f183bebf2L, 0xbcf0ba68b7555d4aL,
            0x3d34354bb3f219e5L, 0x3d170cc16135783cL, 0xbd28a72a62b8c13fL, 0x3cd680b5ce3ecb05L,
            0x3d35b967f4471dfcL, 0x3d24d20ab840e7f6L, 0xbd1563451027c750L, 0xbd3cb2cd2ee2f482L,
            0x3d2a47579cdc0a3dL, 0x3d3d599e83368e91L, 0x3d2a342c2af0003cL, 0x3d29454379135713L,
            0xbd1d0c57585fbe06L, 0xbd3563650bd22a9cL, 0x3d28a64826787061L, 0xbd3b20f5acb42a66L,
            0xbd32cc844480c89bL, 0xbd30c3b1dee9c4f8L, 0xbd383f69278e686aL, 0xbd1bc0eeea7c9acdL,
            0xbd31d09299837610L, 0xbd3416f8fb69a701L, 0x3d147c5e768fa309L, 0x3d2d599e83368e91L,
            0xbd16a423c78a64b0L, 0x3d1c827ae5d6704cL, 0x3d2c148297c5feb8L, 0x3d3181dce586af09L,
            0x3d2b2b739570ad39L, 0xbd3eafd480ad9015L, 0x3d33401e9ae889bbL, 0xbd2980267c7e09e4L,
            0x3d3b9a010ae6922aL, 0x3d33b955b602ace4L, 0x3d36a2c432d6a40bL, 0xbcdf1e7cf6d3a69cL,
            0x3d3eb1245b5da1f5L, 0xbd19e23f0dda40e4L, 0x0000000000000000L, 0x0000000000000000L,
            0x3d1ad50382973f27L, 0x3d1977c755d01368L, 0xbd341f33fcefb9feL, 0x3d2713e3284991feL,
            0x3d07abf389596542L, 0xbd2de0709f2d03c9L, 0xbd28d75149774d47L, 0xbd3dddc7f461c516L,
            0x3d283e9ae021b67bL, 0xbd2c167375bdfd28L, 0x3d3dac20827cca0cL, 0x3d39a19a8be97661L,
            0x3d20fc1a353bb42eL, 0xbd17bf868c317c2aL, 0xbd21b1ac64d9e42fL, 0x3d23008c98381a8fL,
            0xbd37cf69284a3465L, 0x3d349d8cfc10c7bfL, 0x3d37a48ba8b1cb41L, 0xbd319bd0ad125895L,
            0xbcf53a2582f4e1efL, 0x3d0c1d740c53c72eL, 0x3d3a79994c9d3302L, 0x3d069b5794b69fb7L,
            0xbd3c0fe460d20041L, 0x3d23115c3abd47daL, 0xbd0e42b6b94407c8L, 0xbd3573b209c31904L,
            0x3d24218c8d824283L, 0x3d336433b5efbeedL, 0x3d1a89401fa71733L, 0x3d311fcba80cdd10L,
            0x3d176a6c9ea8b04eL, 0x3d381a9cf169fc5cL, 0xbd27e5dd7009902cL, 0x3d21ef78ce2d07f2L,
            0x3d2ec2d2a9009e3dL, 0xbd15f74e9b083633L, 0x3d100d238fd3df5cL, 0x3d039d6ccb81b4a1L,
            0x3d3f098ee3a50810L, 0xbd3aade8f29320fbL, 0x3d319713c0cae559L, 0xbd353e43558124c4L,
            0x3d0c79b60ae1ff0fL, 0x3d39baa7a6b887f6L, 0xbd127023eb68981cL, 0xbd398c1d34f0f462L,
            0xbd38f934e66a15a6L, 0x3d3577390d31ef0fL, 0x3d38e67be3dbaf3fL, 0xbd24c06b17c301d7L,
            0x3d3328eb42f9af75L, 0xbd073d54aae92cd1L, 0xbd1935f57718d7caL, 0x3d3470fa3efec390L,
            0xbd3337d94bcd3f43L, 0x3d360a77c81f7171L, 0x3d37a8d5ae54f550L, 0x3d3c794e562a63cbL,
            0x3d1e90683b9cd768L, 0xbd2d4bc4595412b6L, 0x3d3ee138d3a69d43L, 0xbd1d6d585d57aff9L,
            0xbd084a7e75b6f6e4L, 0x3d32806a847527e6L, 0xbd252b00adb91424L, 0xbd3fdbdbb13f7c18L,
            0x3d35e91663732a36L, 0xbd3bae49f1df7b5eL, 0xbd390d04cd7cc834L, 0x3d2f3418de00938bL,
            0x3d3a43dcfade85aeL, 0x3d2dbdf10d397f3cL, 0x3d3b50a1e1734342L, 0x3d27188b163ceae9L,
            0x3d0019b52d8435f5L, 0x3d31ef78ce2d07f2L, 0x3d3e0c07824daaf5L, 0xbd323299042d74bfL,
            0xbd31ac89575c2125L, 0xbd382eaed3c8b65eL, 0xbd3caf0428b728a3L, 0xbd36dbe448a2e522L,
            0xbd31b61f10522625L, 0xbd3210c2b730e28bL, 0x3d2a9cfa4a5004f4L, 0xbd28e27ad3213cb8L,
            0xbd317c73556e291dL, 0x3d116ecdb0f177c8L, 0xbd05839c5663663dL, 0x3d183b54b606bd5cL,
            0xbd3f067c297f2c3fL, 0xbd3ce379226de3ecL, 0xbd06e95892923d88L, 0x3d306c18fb4c14c5L,
            0x3d307b334daf4b9aL, 0xbd2e20891b0ad8a4L, 0xbd3fc158cb3124b9L, 0x3d2ebe708164c759L,
            0x3d1a8954c0910952L, 0x3d1fadedee5d40efL, 0xbcf7c79b0af7ecf8L, 0xbd0a0b2a08a465dcL,
            0x3d1ebe9176df3f65L, 0xbd2db623e731ae00L,
        };
        for (int i = 0; i < hi.length; i++) {
            _LOG_INV_H[i] = Double.longBitsToDouble(hi[i]);
            _LOG_INV_L[i] = Double.longBitsToDouble(lo[i]);
        }
    }

    // ===== pow.h: T1[64][2] =====
    private static final double[] _T1_H = new double[64];
    private static final double[] _T1_L = new double[64];
    static {
        long[] hi = {
            0x3ff0000000000000L, 0x3ff02c9a3e778061L, 0x3ff059b0d3158574L, 0x3ff0874518759bc8L,
            0x3ff0b5586cf9890fL, 0x3ff0e3ec32d3d1a2L, 0x3ff11301d0125b51L, 0x3ff1429aaea92de0L,
            0x3ff172b83c7d517bL, 0x3ff1a35beb6fcb75L, 0x3ff1d4873168b9aaL, 0x3ff2063b88628cd6L,
            0x3ff2387a6e756238L, 0x3ff26b4565e27cddL, 0x3ff29e9df51fdee1L, 0x3ff2d285a6e4030bL,
            0x3ff306fe0a31b715L, 0x3ff33c08b26416ffL, 0x3ff371a7373aa9cbL, 0x3ff3a7db34e59ff7L,
            0x3ff3dea64c123422L, 0x3ff4160a21f72e2aL, 0x3ff44e086061892dL, 0x3ff486a2b5c13cd0L,
            0x3ff4bfdad5362a27L, 0x3ff4f9b2769d2ca7L, 0x3ff5342b569d4f82L, 0x3ff56f4736b527daL,
            0x3ff5ab07dd485429L, 0x3ff5e76f15ad2148L, 0x3ff6247eb03a5585L, 0x3ff6623882552225L,
            0x3ff6a09e667f3bcdL, 0x3ff6dfb23c651a2fL, 0x3ff71f75e8ec5f74L, 0x3ff75feb564267c9L,
            0x3ff7a11473eb0187L, 0x3ff7e2f336cf4e62L, 0x3ff82589994cce13L, 0x3ff868d99b4492edL,
            0x3ff8ace5422aa0dbL, 0x3ff8f1ae99157736L, 0x3ff93737b0cdc5e5L, 0x3ff97d829fde4e50L,
            0x3ff9c49182a3f090L, 0x3ffa0c667b5de565L, 0x3ffa5503b23e255dL, 0x3ffa9e6b5579fdbfL,
            0x3ffae89f995ad3adL, 0x3ffb33a2b84f15fbL, 0x3ffb7f76f2fb5e47L, 0x3ffbcc1e904bc1d2L,
            0x3ffc199bdd85529cL, 0x3ffc67f12e57d14bL, 0x3ffcb720dcef9069L, 0x3ffd072d4a07897cL,
            0x3ffd5818dcfba487L, 0x3ffda9e603db3285L, 0x3ffdfc97337b9b5fL, 0x3ffe502ee78b3ff6L,
            0x3ffea4afa2a490daL, 0x3ffefa1bee615a27L, 0x3fff50765b6e4540L, 0x3fffa7c1819e90d8L,
        };
        long[] lo = {
            0x0000000000000000L, 0xbc719083535b085dL, 0x3c8d73e2a475b465L, 0x3c6186be4bb284ffL,
            0x3c98a62e4adc610bL, 0x3c403a1727c57b53L, 0xbc96c51039449b3aL, 0xbc932fbf9af1369eL,
            0xbc819041b9d78a76L, 0x3c8e5b4c7b4968e4L, 0x3c9e016e00a2643cL, 0x3c8dc775814a8495L,
            0x3c99b07eb6c70573L, 0x3c82bd339940e9d9L, 0x3c8612e8afad1255L, 0x3c90024754db41d5L,
            0x3c86f46ad23182e4L, 0x3c932721843659a6L, 0xbc963aeabf42eae2L, 0xbc75e436d661f5e3L,
            0x3c8ada0911f09ebcL, 0xbc5ef3691c309278L, 0x3c489b7a04ef80d0L, 0x3c73c1a3b69062f0L,
            0x3c7d4397afec42e2L, 0xbc94b309d25957e3L, 0xbc807abe1db13cadL, 0x3c99bb2c011d93adL,
            0x3c96324c054647adL, 0x3c9ba6f93080e65eL, 0xbc9383c17e40b497L, 0xbc9bb60987591c34L,
            0xbc9bdd3413b26456L, 0xbc6bbe3a683c88abL, 0xbc816e4786887a99L, 0xbc90245957316dd3L,
            0xbc841577ee04992fL, 0x3c705d02ba15797eL, 0xbc9d4c1dd41532d8L, 0xbc9fc6f89bd4f6baL,
            0x3c96e9f156864b27L, 0x3c85cc13a2e3976cL, 0xbc675fc781b57ebcL, 0xbc9d185b7c1b85d1L,
            0x3c7c7c46b071f2beL, 0xbc9359495d1cd533L, 0xbc9d2f6edb8d41e1L, 0x3c90fac90ef7fd31L,
            0x3c97a1cd345dcc81L, 0xbc62805e3084d708L, 0xbc75584f7e54ac3bL, 0x3c823dd07a2d9e84L,
            0x3c811065895048ddL, 0x3c92884dff483cadL, 0x3c7503cbd1e949dbL, 0xbc9cbc3743797a9cL,
            0x3c82ed02d75b3707L, 0x3c9c2300696db532L, 0xbc91a5cd4f184b5cL, 0x3c839e8980a9cc8fL,
            0xbc9e9c23179c2893L, 0x3c9dc7f486a4b6b0L, 0x3c99d3e12dd8a18bL, 0x3c874853f3a5931eL,
        };
        for (int i = 0; i < hi.length; i++) {
            _T1_H[i] = Double.longBitsToDouble(hi[i]);
            _T1_L[i] = Double.longBitsToDouble(lo[i]);
        }
    }

    // ===== pow.h: T2[64][2] =====
    private static final double[] _T2_H = new double[64];
    private static final double[] _T2_L = new double[64];
    static {
        long[] hi = {
            0x3ff0000000000000L, 0x3ff000b175effdc7L, 0x3ff00162f3904052L, 0x3ff0021478e11ce6L,
            0x3ff002c605e2e8cfL, 0x3ff003779a95f959L, 0x3ff0042936faa3d8L, 0x3ff004dadb113da0L,
            0x3ff0058c86da1c0aL, 0x3ff0063e3a559473L, 0x3ff006eff583fc3dL, 0x3ff007a1b865a8caL,
            0x3ff0085382faef83L, 0x3ff00905554425d4L, 0x3ff009b72f41a12bL, 0x3ff00a6910f3b6fdL,
            0x3ff00b1afa5abcbfL, 0x3ff00bcceb7707ecL, 0x3ff00c7ee448ee02L, 0x3ff00d30e4d0c483L,
            0x3ff00de2ed0ee0f5L, 0x3ff00e94fd0398e0L, 0x3ff00f4714af41d3L, 0x3ff00ff93412315cL,
            0x3ff010ab5b2cbd11L, 0x3ff0115d89ff3a8bL, 0x3ff0120fc089ff63L, 0x3ff012c1fecd613bL,
            0x3ff0137444c9b5b5L, 0x3ff01426927f5278L, 0x3ff014d8e7ee8d2fL, 0x3ff0158b4517bb88L,
            0x3ff0163da9fb3335L, 0x3ff016f0169949edL, 0x3ff017a28af25567L, 0x3ff018550706ab62L,
            0x3ff019078ad6a19fL, 0x3ff019ba16628de2L, 0x3ff01a6ca9aac5f3L, 0x3ff01b1f44af9f9eL,
            0x3ff01bd1e77170b4L, 0x3ff01c8491f08f08L, 0x3ff01d37442d5070L, 0x3ff01de9fe280ac8L,
            0x3ff01e9cbfe113efL, 0x3ff01f4f8958c1c6L, 0x3ff020025a8f6a35L, 0x3ff020b533856324L,
            0x3ff02168143b0281L, 0x3ff0221afcb09e3eL, 0x3ff022cdece68c4fL, 0x3ff02380e4dd22adL,
            0x3ff02433e494b755L, 0x3ff024e6ec0da046L, 0x3ff02599fb483385L, 0x3ff0264d1244c719L,
            0x3ff027003103b10eL, 0x3ff027b357854772L, 0x3ff0286685c9e059L, 0x3ff02919bbd1d1d8L,
            0x3ff029ccf99d720aL, 0x3ff02a803f2d170dL, 0x3ff02b338c811703L, 0x3ff02be6e199c811L,
        };
        long[] lo = {
            0x0000000000000000L, 0x3c9ae8e38c59c72aL, 0xbc57b5d0d58ea8f4L, 0x3c94115cb6b16a8eL,
            0xbc8d7c96f201bb2fL, 0x3c984711d4c35e9fL, 0xbc80484245243777L, 0xbc94b237da2025f9L,
            0xbc75e00e62d6b30dL, 0x3c9a1d6cedbb9481L, 0xbc94acf197a00142L, 0xbc6eaf2ea42391a5L,
            0x3c7da93f90835f75L, 0xbc86a79084ab093cL, 0x3c986364f8fbe8f8L, 0xbc882e8e14e3110eL,
            0xbc84f6b2a7609f71L, 0xbc7e1a258ea8f71bL, 0x3c74362ca5bc26f1L, 0x3c9095a56c919d02L,
            0xbc6406ac4e81a645L, 0x3c9b5a6902767e09L, 0xbc991b2060859321L, 0x3c8427068ab22306L,
            0x3c9c1d0660524e08L, 0xbc9e7bdfb3204be8L, 0x3c8843aa8b9cbbc6L, 0xbc734104ee7edae9L,
            0xbc72b6aeb6176892L, 0x3c7a8cd33b8a1bb3L, 0x3c72edc08e5da99aL, 0x3c857ba2dc7e0c73L,
            0x3c9b61299ab8cdb7L, 0xbc990565902c5f44L, 0x3c870fc41c5c2d53L, 0x3c94b9a6e145d76cL,
            0xbc7008eff5142bf9L, 0xbc977669f033c7deL, 0xbc909bb78eeead0aL, 0x3c9371231477ece5L,
            0x3c75e7626621eb5bL, 0xbc9bc72b100828a5L, 0xbc6ce39cbbab8bbeL, 0x3c816996709da2e2L,
            0xbc8c11f5239bf535L, 0x3c8e1d4eb5edc6b3L, 0xbc9afb99946ee3f0L, 0xbc98f06d8a148a32L,
            0xbc82bf310fc54eb6L, 0xbc9c95a035eb4175L, 0xbc9491793e46834dL, 0xbc73e8d0d9c49091L,
            0xbc9314aa16278aa3L, 0x3c848daf888e9651L, 0x3c856dc8046821f4L, 0x3c945b42356b9d47L,
            0xbc7082ef51b61d7eL, 0x3c72106ed0920a34L, 0xbc9fd4cf26ea5d0fL, 0xbc909f8775e78084L,
            0x3c564cbba902ca27L, 0x3c94383ef231d207L, 0x3c94a47a505b3a47L, 0x3c9e47120223467fL,
        };
        for (int i = 0; i < hi.length; i++) {
            _T2_H[i] = Double.longBitsToDouble(hi[i]);
            _T2_L[i] = Double.longBitsToDouble(lo[i]);
        }
    }

    // ===== pow.h: P_1[6] =====
    private static final double[] P_1 = new double[6];
    static {
        long[] bits = {
            0x3fd5555555555558L, 0xbfd0000000000003L, 0x3fc999999981f535L, 0xbfc55555553d1eb4L,
            0x3fc2494526fd4a06L, 0xbfc0001f0c80e8ceL,
        };
        for (int i = 0; i < bits.length; i++) P_1[i] = Double.longBitsToDouble(bits[i]);
    }

    // ===== pow.h: Q_1[5] =====
    private static final double[] Q_1 = new double[5];
    static {
        long[] bits = {
            0x3ff0000000000000L, 0x3ff0000000000000L, 0x3fe0000000000000L, 0x3fc5555555997996L,
            0x3fa5555555849d8dL,
        };
        for (int i = 0; i < bits.length; i++) Q_1[i] = Double.longBitsToDouble(bits[i]);
    }
}
