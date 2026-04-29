package org.jquantlib.math.transcendental;

/**
 * Pure-Java emulation of CORE-MATH's {@code dint64_t} extended-precision
 * type — a 128-bit mantissa + signed exponent + sign bit, used by CORE-MATH's
 * accurate-path correctly-rounded transcendental algorithms.
 *
 * <p>Layout matches CORE-MATH's C struct (see
 * {@code migration-harness/cpp/probes/transcendental/coremath/dint.h},
 * extracted from CORE-MATH {@code src/binary64/sin/sin.c}):
 * <pre>
 *   typedef struct {
 *     uint64_t lo, hi;   // 128-bit mantissa low and high words
 *     int64_t  ex;       // base-2 exponent
 *     uint64_t sgn;      // 0 = positive, 1 = negative
 *   } dint64_t;
 *
 *   value = (-1)^sgn * (hi/2^64 + lo/2^128) * 2^ex
 * </pre>
 *
 * <p>This class is package-private — used internally by future
 * {@code SinCosKernel}, {@code LogKernel}, {@code PowKernel}, etc. Public API
 * surface is solely the {@link JQuantMath} facade.
 *
 * <p>Source: CORE-MATH (Sibidanov / Zimmermann / Hubrecht et al., Inria;
 * MIT-licensed; {@code https://core-math.gitlabpages.inria.fr/}). The dint
 * support primitives live inline in CORE-MATH's sin.c and cos.c per their
 * "code copied from dint.h and pow.[ch]" header comment; the Java port is
 * transcribed faithfully and cross-validated bit-exact via the
 * {@code dint64_probe} reference at
 * {@code migration-harness/references/math/transcendental/dint64.json}.
 *
 * <p><b>Mutability:</b> instances are mutable for performance — CORE-MATH
 * uses out-parameters (e.g. {@code add_dint(*r, *a, *b)}); the Java port
 * follows suit. Arithmetic methods write into the receiver. Use fresh
 * instances per intermediate to keep code readable.
 *
 * <p><b>Unsigned semantics:</b> the {@code lo}, {@code hi}, and {@code sgn}
 * fields are conceptually unsigned 64-bit. Java has no unsigned long, so
 * we store the raw bits in a signed {@code long} and use
 * {@code Long.compareUnsigned}, {@code Long.numberOfLeadingZeros}, and
 * {@code >>>} (logical shift) where the C reference uses unsigned semantics.
 * The {@code ex} field is signed; {@code >>} (arithmetic shift) is used.
 */
final class Dint64 {

    /** Low 64 bits of the 128-bit mantissa (unsigned, encoded as {@code long}). */
    long lo;
    /** High 64 bits of the 128-bit mantissa (unsigned). */
    long hi;
    /** Base-2 exponent (signed). */
    long ex;
    /** Sign bit: 0 = positive, 1 = negative. */
    long sgn;

    /** ZERO sentinel (matches CORE-MATH {@code ZERO}). */
    private static final long ZERO_LO = 0L;
    private static final long ZERO_HI = 0L;
    private static final long ZERO_EX = -1076L;
    private static final long ZERO_SGN = 0L;

    Dint64() {
        // Default zero state.
    }

    Dint64(long lo, long hi, long ex, long sgn) {
        this.lo = lo;
        this.hi = hi;
        this.ex = ex;
        this.sgn = sgn;
    }

    // -------------------------------------------------------------------
    // Conversion
    // -------------------------------------------------------------------

    /**
     * Set this dint from a non-zero finite double, matching CORE-MATH
     * {@code dint_fromd}. Behaviour is undefined for ±0, ±inf, NaN; production
     * callers branch out beforehand (the same as the C reference).
     */
    void fromDouble(double a) {
        // fast_extract: extract biased exponent + raw mantissa from the IEEE-754 bits.
        final long bits = Double.doubleToRawLongBits(a);
        long e = (bits >>> 52) & 0x7ffL;
        long m = (bits & (~0L >>> 12)) + ((e != 0) ? (1L << 52) : 0L);
        e = e - 0x3feL;

        // dint_fromd body:
        //   uint32_t t = clzll(hi);  hi <<= t;
        //   ex = e - (t > 11 ? t - 12 : 0);
        //   sgn = (b < 0); lo = 0;
        // Note: m here is the result of fast_extract; for a normal double m has
        // its leading 1 in bit 52 (so 11 leading zeros), giving t=11 and no
        // exponent adjustment. For subnormals m has more leading zeros and ex
        // is decremented by (t-12).
        final int t = Long.numberOfLeadingZeros(m);
        this.sgn = (a < 0.0) ? 1L : 0L;
        this.hi = m << t;
        this.ex = e - ((t > 11) ? (t - 12) : 0);
        this.lo = 0L;
    }

    /**
     * Convert this dint to a double with correct rounding, matching CORE-MATH
     * {@code dint_tod}. Note: this method <b>mutates</b> the receiver via
     * {@link #subnormalize}, mirroring the C reference's in-place behaviour.
     */
    double toDouble() {
        subnormalize();

        // f64_u r = {.u = (hi >> 11) | (0x3ffll << 52)};
        long rBits = (this.hi >>> 11) | (0x3ffL << 52);

        double rd = 0.0;
        if (((this.hi >>> 10) & 0x1L) != 0L) {
            rd += 0x1p-53;
        }
        if (((this.hi & 0x3ffL) != 0L) || (this.lo != 0L)) {
            rd += 0x1p-54;
        }
        if (this.sgn != 0L) {
            rd = -rd;
        }

        rBits = rBits | (this.sgn << 63);
        double r = Double.longBitsToDouble(rBits) + rd;

        double e;
        if (this.ex > -1022L) {
            // Normal-double regime.
            if (this.ex > 1024L) {
                if (this.ex == 1025L) {
                    r = r * 0x1p+1;
                    e = 0x1p+1023;
                } else {
                    r = 0x1.fffffffffffffp+1023;
                    e = 0x1.fffffffffffffp+1023;
                }
            } else {
                final long eBits = ((this.ex + 1022L) & 0x7ffL) << 52;
                e = Double.longBitsToDouble(eBits);
            }
        } else {
            if (this.ex < -1073L) {
                if (this.ex == -1074L) {
                    r = r * 0x1p-1;
                    e = 0x1p-1074;
                } else {
                    r = 0x0.0000000000001p-1022;
                    e = 0x0.0000000000001p-1022;
                }
            } else {
                // C: e.u = 1l << (ex + 1073)
                // ex ∈ [-1073, -1022] → shift ∈ [0, 51]
                final long eBits = 1L << (this.ex + 1073L);
                e = Double.longBitsToDouble(eBits);
            }
        }

        return r * e;
    }

    /** Copy from another dint, matching CORE-MATH {@code cp_dint}. */
    void copyFrom(Dint64 src) {
        this.ex = src.ex;
        this.lo = src.lo;
        this.hi = src.hi;
        this.sgn = src.sgn;
    }

    // -------------------------------------------------------------------
    // Comparison
    // -------------------------------------------------------------------

    /**
     * Return 1 if this represents zero (i.e. {@code hi == 0}, matching
     * CORE-MATH {@code dint_zero_p}). Note: only checks {@code hi}, mirroring
     * the canonical CORE-MATH semantics.
     */
    boolean isZero() {
        return this.hi == 0L;
    }

    /**
     * Compare absolute values of {@code a} and {@code b}, matching CORE-MATH
     * {@code cmp_dint_abs}: returns -1 if {@code |a| < |b|}, 0 if equal,
     * +1 if {@code |a| > |b|}.
     */
    static int cmpAbs(Dint64 a, Dint64 b) {
        final boolean aZero = a.isZero();
        final boolean bZero = b.isZero();
        if (aZero) {
            return bZero ? 0 : -1;
        }
        if (bZero) {
            return +1;
        }
        // Compare exponents first, then 128-bit mantissa.
        final int c1 = Long.compare(a.ex, b.ex);
        if (c1 != 0) {
            return c1;
        }
        final int hiCmp = Long.compareUnsigned(a.hi, b.hi);
        if (hiCmp != 0) {
            return hiCmp;
        }
        return Long.compareUnsigned(a.lo, b.lo);
    }

    // -------------------------------------------------------------------
    // Arithmetic
    // -------------------------------------------------------------------

    /**
     * {@code this = a + b}, matching CORE-MATH {@code add_dint}. Error bounded
     * by 2 ulps of the 128-bit mantissa (1 ulp when same-sign).
     *
     * <p><b>Aliasing:</b> behaviour is undefined if {@code this == a} or
     * {@code this == b}. Callers must use a separate destination instance.
     * (CORE-MATH callers also pass a fresh out-parameter; this constraint
     * matches the C reference's call sites.)
     */
    void addAssign(Dint64 a, Dint64 b) {
        // CORE-MATH special-case: if a is the additive identity (hi|lo = 0),
        // result is just b.
        if ((a.hi | a.lo) == 0L) {
            this.copyFrom(b);
            return;
        }

        final int c = cmpAbs(a, b);
        if (c == 0) {
            if ((a.sgn ^ b.sgn) != 0L) {
                // Equal magnitude, opposite signs → exact zero.
                this.lo = ZERO_LO;
                this.hi = ZERO_HI;
                this.ex = ZERO_EX;
                this.sgn = ZERO_SGN;
                return;
            }
            // Equal magnitude, same sign → 2× the operand.
            this.copyFrom(a);
            this.ex++;
            return;
        }
        if (c < 0) {
            // |a| < |b| — swap so that a holds the larger magnitude.
            final Dint64 tmp = a;
            a = b;
            b = tmp;
        }
        // From here: |a| >= |b|, so a.ex >= b.ex.

        long aLo = a.lo;
        long aHi = a.hi;
        long bLo = b.lo;
        long bHi = b.hi;
        final long k = a.ex - b.ex;

        // Right-shift B by k bits (C: B = (k < 128) ? B >> k : 0;).
        if (k > 0) {
            if (k >= 128) {
                bLo = 0L;
                bHi = 0L;
            } else if (k >= 64) {
                final int sh = (int) (k - 64);
                bLo = (sh == 0) ? bHi : (bHi >>> sh);
                bHi = 0L;
            } else { // 0 < k < 64
                final int sh = (int) k;
                bLo = (bLo >>> sh) | (bHi << (64 - sh));
                bHi = bHi >>> sh;
            }
        }

        long cLo;
        long cHi;
        final long sgnOut = a.sgn;
        long exOut = a.ex; // tentative exponent for the result

        if ((a.sgn ^ b.sgn) != 0L) {
            // Different signs: C = A - B.
            // 128-bit subtraction with borrow propagation.
            final long bw = unsignedLessThan(aLo, bLo) ? 1L : 0L;
            cLo = aLo - bLo;
            cHi = aHi - bHi - bw;

            // Count leading zeros of the 128-bit C.
            int ex;
            if (cHi != 0L) {
                ex = Long.numberOfLeadingZeros(cHi);
            } else {
                // cHi == 0; total clz = 64 + clz(cLo).
                ex = 64 + Long.numberOfLeadingZeros(cLo);
            }

            if (ex > 0) {
                if (k == 1L) {
                    // Sterbenz: C = (A << ex) - (b->r << (ex - 1)).
                    final long[] aShifted = shiftLeft128(aHi, aLo, ex);
                    final long[] bShifted = shiftLeft128(b.hi, b.lo, ex - 1);
                    final long bw2 = unsignedLessThan(aShifted[0], bShifted[0]) ? 1L : 0L;
                    cLo = aShifted[0] - bShifted[0];
                    cHi = aShifted[1] - bShifted[1] - bw2;
                } else {
                    // C = (A << ex) - (B << ex).
                    final long[] aShifted = shiftLeft128(aHi, aLo, ex);
                    final long[] bShifted = shiftLeft128(bHi, bLo, ex);
                    final long bw2 = unsignedLessThan(aShifted[0], bShifted[0]) ? 1L : 0L;
                    cLo = aShifted[0] - bShifted[0];
                    cHi = aShifted[1] - bShifted[1] - bw2;
                }
                exOut -= ex;
                ex = Long.numberOfLeadingZeros(cHi);
                // fall through to the ex == 0 final shift
            }
            // Final left shift by ex (which is now the leading-zero count of cHi).
            final long[] cShifted = shiftLeft128(cHi, cLo, ex);
            cLo = cShifted[0];
            cHi = cShifted[1];
            exOut -= ex;
        } else {
            // Same sign: C = A + B.
            // 128-bit addition with carry detection.
            final long sumLo = aLo + bLo;
            final long carry = (Long.compareUnsigned(sumLo, aLo) < 0) ? 1L : 0L;
            final long sumHi = aHi + bHi + carry;
            // Did the high word wrap? That tells us if the 128-bit sum overflowed.
            // Test: sumHi < aHi (unsigned) implies overflow (with carry contribution).
            final boolean overflow = unsignedLessThan(sumHi, aHi)
                                  || (carry == 1L && sumHi == aHi);
            if (overflow) {
                // C = (1<<127) | (C >> 1)
                final long shiftedLo = (sumLo >>> 1) | (sumHi << 63);
                final long shiftedHi = (sumHi >>> 1) | (1L << 63);
                cLo = shiftedLo;
                cHi = shiftedHi;
                exOut++;
            } else {
                cLo = sumLo;
                cHi = sumHi;
            }
        }

        this.sgn = sgnOut;
        this.lo = cLo;
        this.hi = cHi;
        this.ex = exOut;
    }

    /**
     * {@code this = a * b}, matching CORE-MATH {@code mul_dint}. Error bounded
     * by 6 ulps of the 128-bit mantissa.
     *
     * <p><b>Aliasing:</b> behaviour is undefined if {@code this == a} or
     * {@code this == b}. Callers must use a separate destination instance.
     * (CORE-MATH callers also pass a fresh out-parameter; this constraint
     * matches the C reference's call sites.)
     */
    void mulAssign(Dint64 a, Dint64 b) {
        final long aHi = a.hi, aLo = a.lo;
        final long bHi = b.hi, bLo = b.lo;

        // m1 = a.hi * b.lo  (128-bit product)
        final long m1Lo = aHi * bLo;
        final long m1Hi = unsignedMulHigh(aHi, bLo);
        // m2 = a.lo * b.hi
        final long m2Lo = aLo * bHi;
        final long m2Hi = unsignedMulHigh(aLo, bHi);
        // r = a.hi * b.hi  (128-bit, becomes the result base)
        long rLo = aHi * bHi;
        long rHi = unsignedMulHigh(aHi, bHi);

        // r += (m1 >> 64) + (m2 >> 64)  — i.e. add the high words of m1 and m2 into r's low word.
        // Two sequential 128-bit adds with carry into rHi.
        long sum = rLo + m1Hi;
        long carry = (Long.compareUnsigned(sum, rLo) < 0) ? 1L : 0L;
        rLo = sum;
        rHi += carry;
        sum = rLo + m2Hi;
        carry = (Long.compareUnsigned(sum, rLo) < 0) ? 1L : 0L;
        rLo = sum;
        rHi += carry;

        // ex = r.hi >> 63;  r <<= (1 - ex);
        final long topBit = (rHi >>> 63) & 1L;
        if (topBit == 0L) {
            // 1 - ex == 1: shift left by 1.
            rHi = (rHi << 1) | (rLo >>> 63);
            rLo = rLo << 1;
        }
        // else topBit == 1: no shift.

        this.lo = rLo;
        this.hi = rHi;
        this.ex = a.ex + b.ex + topBit - 1L;
        this.sgn = a.sgn ^ b.sgn;
    }

    /**
     * {@code this = a * b}, assuming {@code b.lo == 0}, matching CORE-MATH
     * {@code mul_dint_21}. Error bounded by 2 ulps. Faster than
     * {@link #mulAssign} when {@code b} comes directly from {@link #fromDouble}
     * (which always sets {@code lo=0}).
     *
     * <p><b>Aliasing:</b> behaviour is undefined if {@code this == a} or
     * {@code this == b}. Callers must use a separate destination instance.
     * (CORE-MATH callers also pass a fresh out-parameter; this constraint
     * matches the C reference's call sites.)
     */
    void mul21Assign(Dint64 a, Dint64 b) {
        final long aHi = a.hi, aLo = a.lo;
        final long bHi = b.hi;

        // hi = a.hi * b.hi (128-bit)
        long rLo = aHi * bHi;
        long rHi = unsignedMulHigh(aHi, bHi);
        // lo = a.lo * b.hi (128-bit) — only its high word feeds in.
        final long loProdHi = unsignedMulHigh(aLo, bHi);

        // r += lo >> 64 (i.e. add loProdHi into r's low word).
        final long sum = rLo + loProdHi;
        final long carry = (Long.compareUnsigned(sum, rLo) < 0) ? 1L : 0L;
        rLo = sum;
        rHi += carry;

        // Normalize: if r.hi top bit is 0, shift left by 1.
        final long topBit = (rHi >>> 63) & 1L;
        if (topBit == 0L) {
            rHi = (rHi << 1) | (rLo >>> 63);
            rLo = rLo << 1;
        }

        this.lo = rLo;
        this.hi = rHi;
        this.ex = a.ex + b.ex + topBit - 1L;
        this.sgn = a.sgn ^ b.sgn;
    }

    /**
     * In-place subnormalisation, matching CORE-MATH {@code subnormalize_dint}.
     * No-op if {@code ex > -1023}; otherwise rounds the mantissa to fit in a
     * subnormal-double's representable range.
     *
     * <p>The C reference branches on the FP rounding mode via {@code fegetround()};
     * Java's IEEE-754 stack is fixed at {@code FE_TONEAREST}, so we hard-code
     * that branch (round-to-nearest-even).
     */
    void subnormalize() {
        if (this.ex > -1023L) {
            return;
        }

        // C: uint64_t ex = -(1011 + a->ex);
        final long ex = -(1011L + this.ex);
        // ex is in [12, 64+...] for the underflow regime; clamp the shift to
        // avoid Java's modulo-64 shift-amount semantics blowing up at ex==64.
        // The CORE-MATH algorithm handles ex up to ~64 cleanly within this
        // routine; for ex >= 64 the result effectively underflows to 0/min.
        // The C ref relies on `a->hi >> ex` returning 0 for ex >= 64 (which
        // is undefined behaviour in C but happens to work on the architectures
        // CORE-MATH targets). Java's `>>>` is well-defined modulo 64, so we
        // need explicit clamping for that branch.

        long hi;
        long md;
        long lo;
        if (ex >= 64) {
            hi = 0L;
            md = (ex == 64L) ? (this.hi >>> 63) & 0x1L : 0L;
            // For ex > 64, even md=0; but the original "low" mass — i.e. the
            // bits below the rounding point — must include both `a.hi` (if
            // not already absorbed into md) and `a.lo`.
            lo = (this.hi != 0L || this.lo != 0L) ? 1L : 0L;
        } else {
            final int sh = (int) ex;
            hi = this.hi >>> sh;
            md = (this.hi >>> (sh - 1)) & 0x1L;
            // C: `lo = (a.hi & (~0ull >> ex)) || a.lo;`  (logical-or coerces to {0,1})
            final long mask = ~0L >>> sh;
            lo = (((this.hi & mask) != 0L) || (this.lo != 0L)) ? 1L : 0L;
        }

        // Round to nearest even (FE_TONEAREST).
        // C: hi += lo ? md : hi & md;
        if (lo != 0L) {
            hi += md;
        } else {
            hi += (hi & md);
        }

        // a.hi = hi << ex;  a.lo = 0;
        if (ex >= 64) {
            this.hi = 0L;
        } else {
            this.hi = hi << (int) ex;
        }
        this.lo = 0L;

        if (this.hi == 0L) {
            this.ex++;
            this.hi = 1L << 63;
        }
    }

    // -------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------

    /** Unsigned 64×64 → high 64 bits of the 128-bit product. Java 11 has no
     *  {@code Math.unsignedMultiplyHigh}; this matches that semantics. */
    private static long unsignedMulHigh(long a, long b) {
        final long aLo = a & 0xffffffffL;
        final long aHi = a >>> 32;
        final long bLo = b & 0xffffffffL;
        final long bHi = b >>> 32;

        final long ll = aLo * bLo;
        final long lh = aLo * bHi;
        final long hl = aHi * bLo;
        final long hh = aHi * bHi;

        // Combine: (aHi*bHi) + carry from middle terms.
        // mid = (ll >>> 32) + (lh & 0xffffffffL) + (hl & 0xffffffffL)
        // high = hh + (lh >>> 32) + (hl >>> 32) + (mid >>> 32)
        final long mid = (ll >>> 32) + (lh & 0xffffffffL) + (hl & 0xffffffffL);
        return hh + (lh >>> 32) + (hl >>> 32) + (mid >>> 32);
    }

    /** Unsigned-less-than for two 64-bit longs. */
    private static boolean unsignedLessThan(long a, long b) {
        return Long.compareUnsigned(a, b) < 0;
    }

    /**
     * Logical left shift of the 128-bit value {@code (hi, lo)} by {@code n}
     * bits, returning {@code [newLo, newHi]} (note the order — caller takes
     * {@code [0]=lo, [1]=hi} for ergonomic consistency with the C reference's
     * little-endian struct layout).
     *
     * <p>Java semantic note: {@code x << 64} is undefined-by-spec (it
     * actually shifts by {@code n & 63}), so the {@code n == 0} and
     * {@code n >= 64} branches are explicit.
     */
    private static long[] shiftLeft128(long hi, long lo, int n) {
        if (n == 0) {
            return new long[]{lo, hi};
        }
        if (n >= 128) {
            return new long[]{0L, 0L};
        }
        if (n >= 64) {
            final int sh = n - 64;
            final long newHi = (sh == 0) ? lo : (lo << sh);
            return new long[]{0L, newHi};
        }
        // 0 < n < 64
        final long newHi = (hi << n) | (lo >>> (64 - n));
        final long newLo = lo << n;
        return new long[]{newLo, newHi};
    }
}
