package org.jquantlib.math.transcendental;

/**
 * Pure-Java emulation of CORE-MATH's {@code qint64_t} extended-precision type — a 256-bit signed-magnitude mantissa
 * (4&nbsp;&times;&nbsp;64-bit unsigned limbs) plus signed 64-bit base-2 exponent and a sign bit. Used by CORE-MATH's
 * accurate-path correctly-rounded transcendental algorithms in the third Ziv iteration (see {@code pow.c} →
 * {@code log_3 / exp_3 / q_3 / p_3}).
 *
 * <p>Layout matches CORE-MATH's C struct in
 * {@code migration-harness/cpp/probes/transcendental/coremath/qint.h}:
 * <pre>
 *   typedef union {
 *     uint64_t ll, lh, hl, hh;   // four 64-bit limbs, little-endian
 *     int64_t  ex;               // base-2 exponent
 *     uint64_t sgn;              // 0 = positive, 1 = negative
 *   } qint64_t;
 *
 *   value = (-1)^sgn * (hh/2^64 + hl/2^128 + lh/2^192 + ll/2^256) * 2^ex
 * </pre>
 *
 * <p>This class is package-private — used internally by the future
 * {@code PowKernel} (sub-layer 2n A.1). Public API surface is solely the {@link JQuantMath} facade.
 *
 * <p>Source: CORE-MATH (Sibidanov / Zimmermann / Hubrecht et al., Inria;
 * MIT-licensed; {@code https://core-math.gitlabpages.inria.fr/}). The Java port is transcribed faithfully from
 * {@code qint.h} and cross-validated bit-exact via the {@code qint64_probe} reference at
 * {@code migration-harness/references/math/transcendental/qint64.json}.
 *
 * <p><b>Mutability:</b> instances are mutable for performance — CORE-MATH
 * uses out-parameters (e.g. {@code add_qint(*r, *a, *b)}); the Java port follows suit. Arithmetic methods write into
 * the receiver. Aliasing of the destination with one of the source operands is supported by the C reference for several
 * operations (e.g. {@code mul_qint_2(r, k, &K)} where {@code r == &K}); to mirror that, all operations capture inputs
 * into locals before writing the destination.
 *
 * <p><b>Unsigned semantics:</b> the limb fields and {@code sgn} are
 * conceptually unsigned 64-bit. Java has no unsigned long, so we store the raw bits in a signed {@code long} and use
 * {@code Long.compareUnsigned}, {@code Long.numberOfLeadingZeros}, and {@code >>>} (logical shift) where the C
 * reference uses unsigned semantics. The {@code ex} field is signed.
 *
 * <p><b>JDK target:</b> the project targets Java 11, which lacks
 * {@code Math.unsignedMultiplyHigh}. We provide an inline 64&times;64 &rarr; high-64 helper, mirroring the one in
 * {@link Dint64}.
 */
final class Qint64 {

    /** ZERO sentinel (matches CORE-MATH {@code ZERO_Q}). */
    static final Qint64 ZERO_Q = new Qint64(0L, 0L, 0L, 0L, 0L, 0L);
    /** Encodes +1 exactly (matches CORE-MATH {@code ONE_Q}). */
    static final Qint64 ONE_Q = new Qint64(0L, 0L, 0L, 0x8000000000000000L, 0L, 0L);
    /** Encodes -1 exactly (matches CORE-MATH {@code M_ONE_Q}). */
    static final Qint64 M_ONE_Q = new Qint64(0L, 0L, 0L, 0x8000000000000000L, 0L, 1L);
    /**
     * Approximation of log(2) with absolute error &lt; 2^-256.14 (matches CORE-MATH {@code LOG2_Q}).
     */
    static final Qint64 LOG2_Q = new Qint64(0x8a0d175b8baafa2bL, 0x40f343267298b62dL, 0xc9e3b39803f2f6afL,
            0xb17217f7d1cf79abL, -1L, 0L);
    /**
     * Approximation of 2^12/log(2) with absolute error &lt; 2^-52.96 (matches CORE-MATH {@code LOG2_INV_Q}).
     */
    static final Qint64 LOG2_INV_Q = new Qint64(0L, 0L, 0L, 0xb8aa3b295c17f0bcL, 12L, 0L);
    /** Lower-low 64 bits of the 256-bit mantissa (least significant). */
    long ll;

    // -------------------------------------------------------------------
    // Constants — package-private fresh instances. Callers MUST NOT mutate
    // these. Defensive copies via copyFrom are cheap.
    //
    // Mirrors CORE-MATH's `static const qint64_t` definitions in qint.h.
    // -------------------------------------------------------------------
    /** Upper-low 64 bits. */
    long lh;
    /** Lower-high 64 bits. */
    long hl;
    /** Upper-high 64 bits (most significant). */
    long hh;
    /** Base-2 exponent (signed). */
    long ex;
    /** Sign bit: 0 = positive, 1 = negative. */
    long sgn;

    Qint64() {
        // Default zero state.
    }

    Qint64(long ll, long lh, long hl, long hh, long ex, long sgn) {
        this.ll = ll;
        this.lh = lh;
        this.hl = hl;
        this.hh = hh;
        this.ex = ex;
        this.sgn = sgn;
    }

    // -------------------------------------------------------------------
    // Conversion
    // -------------------------------------------------------------------

    /**
     * Compare absolute values of {@code a} and {@code b}, matching CORE-MATH {@code cmp_qint}: returns -1 if
     * {@code |a| < |b|}, 0 if equal, +1 if {@code |a| > |b|}.
     *
     * <p>Note: ordering is exponent-first then 256-bit mantissa
     * (high-to-low). Same convention as Dint64.cmpAbs.
     */
    static int cmpQint(Qint64 a, Qint64 b) {
        final int c1 = Long.compare(a.ex, b.ex);
        if ( c1 != 0 ) {
            return c1;
        }
        final int c2 = Long.compareUnsigned(a.hh, b.hh);
        if ( c2 != 0 ) {
            return c2;
        }
        final int c3 = Long.compareUnsigned(a.hl, b.hl);
        if ( c3 != 0 ) {
            return c3;
        }
        final int c4 = Long.compareUnsigned(a.lh, b.lh);
        if ( c4 != 0 ) {
            return c4;
        }
        return Long.compareUnsigned(a.ll, b.ll);
    }

    /**
     * Same as {@link #cmpQint} but only compares the upper 2 limbs (matches CORE-MATH {@code cmp_qint_22}).
     */
    static int cmpQint22(Qint64 a, Qint64 b) {
        final int c1 = Long.compare(a.ex, b.ex);
        if ( c1 != 0 ) {
            return c1;
        }
        final int c2 = Long.compareUnsigned(a.hh, b.hh);
        if ( c2 != 0 ) {
            return c2;
        }
        return Long.compareUnsigned(a.hl, b.hl);
    }

    /**
     * Unsigned 64×64 → high 64 bits of the 128-bit product. Java 11 has no {@code Math.unsignedMultiplyHigh}; this
     * matches that semantics.
     */
    private static long unsignedMulHigh(long a, long b) {
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

    /**
     * 128-bit add: {@code (aHi,aLo) + (bHi,bLo)}. Returns {@code [sumLo, sumHi, carry]} where carry is 0 or 1.
     */
    private static long[] add128(long aHi, long aLo, long bHi, long bLo) {
        final long sumLo = aLo + bLo;
        final long carryLo = (Long.compareUnsigned(sumLo, aLo) < 0) ? 1L : 0L;
        final long sumHi = aHi + bHi + carryLo;
        // Did the high addition wrap? Mirror Dint64's overflow logic.
        final long carry;
        if ( carryLo != 0L ) {
            // sumHi = aHi + bHi + 1; wraps if sumHi <= aHi (unsigned) AND we
            // carried in (so equality is also overflow — aHi + bHi = ~0L).
            carry = (Long.compareUnsigned(sumHi, aHi) <= 0) ? 1L : 0L;
        } else {
            carry = (Long.compareUnsigned(sumHi, aHi) < 0) ? 1L : 0L;
        }
        return new long[] { sumLo, sumHi, carry };
    }

    /**
     * 128-bit subtract: {@code (aHi,aLo) - (bHi,bLo)}. Returns {@code [diffLo, diffHi, borrow]} where borrow is 0 or
     * 1.
     */
    private static long[] sub128(long aHi, long aLo, long bHi, long bLo) {
        final long diffLo = aLo - bLo;
        final long borrowLo = (Long.compareUnsigned(aLo, bLo) < 0) ? 1L : 0L;
        final long diffHi = aHi - bHi - borrowLo;
        final long borrow;
        if ( borrowLo != 0L ) {
            // diffHi = aHi - bHi - 1; borrows if aHi <= bHi (unsigned).
            borrow = (Long.compareUnsigned(aHi, bHi) <= 0) ? 1L : 0L;
        } else {
            borrow = (Long.compareUnsigned(aHi, bHi) < 0) ? 1L : 0L;
        }
        return new long[] { diffLo, diffHi, borrow };
    }

    // -------------------------------------------------------------------
    // Comparison
    // -------------------------------------------------------------------

    /**
     * Logical left shift of the 128-bit value {@code (hi, lo)} by {@code n} bits, returning {@code [newLo, newHi]}.
     *
     * <p>Java semantic note: {@code x << 64} is undefined-by-spec (it
     * actually shifts by {@code n & 63}), so the {@code n == 0} and {@code n >= 64} branches are explicit.
     */
    private static long[] shiftLeft128(long hi, long lo, int n) {
        if ( n == 0 ) {
            return new long[] { lo, hi };
        }
        if ( n >= 128 ) {
            return new long[] { 0L, 0L };
        }
        if ( n >= 64 ) {
            final int sh = n - 64;
            final long newHi = (sh == 0) ? lo : (lo << sh);
            return new long[] { 0L, newHi };
        }
        // 0 < n < 64
        final long newHi = (hi << n) | (lo >>> (64 - n));
        final long newLo = lo << n;
        return new long[] { newLo, newHi };
    }

    /**
     * Logical right shift of the 128-bit value {@code (hi, lo)} by {@code n} bits, returning {@code [newLo, newHi]}.
     */
    private static long[] shiftRight128(long hi, long lo, int n) {
        if ( n == 0 ) {
            return new long[] { lo, hi };
        }
        if ( n >= 128 ) {
            return new long[] { 0L, 0L };
        }
        if ( n >= 64 ) {
            final int sh = n - 64;
            final long newLo = (sh == 0) ? hi : (hi >>> sh);
            return new long[] { newLo, 0L };
        }
        // 0 < n < 64
        final long newLo = (lo >>> n) | (hi << (64 - n));
        final long newHi = hi >>> n;
        return new long[] { newLo, newHi };
    }

    // -------------------------------------------------------------------
    // Arithmetic — addition
    // -------------------------------------------------------------------

    /**
     * Set this qint from a non-zero finite double, matching CORE-MATH {@code qint_fromd}. Behaviour is undefined for
     * ±0, ±inf, NaN; production callers (cr_pow) branch out beforehand. Result is exact.
     */
    void fromDouble(double b) {
        // fast_extract: extract biased exponent + raw mantissa from the IEEE-754 bits.
        // Mirrors fast_extract in pow.h:
        //   uint64_t bits = ...;
        //   *e = ((bits >> 52) & 0x7ff) - 0x3fe;
        //   *m = (bits & ((1<<52)-1)) | (e_was_nonzero ? (1<<52) : 0);
        final long bits = Double.doubleToRawLongBits(b);
        long e = (bits >>> 52) & 0x7ffL;
        final long m = (bits & (~0L >>> 12)) + ((e != 0) ? (1L << 52) : 0L);
        // qint_fromd uses 0x3ff bias (vs dint_fromd's 0x3fe).
        e = e - 0x3ffL;

        // qint_fromd body:
        //   t = clzll(hh);  hh <<= t;
        //   ex = e - (t > 11 ? t - 12 : 0);
        //   sgn = (b < 0); hl = lh = ll = 0;
        // After fast_extract, m has the implicit leading 1 in bit 52 (so 11
        // leading zeros for normals → t=11 → no exponent adjustment).
        final int t = Long.numberOfLeadingZeros(m);
        this.sgn = (b < 0.0) ? 1L : 0L;
        this.hh = m << t;
        this.ex = e - ((t > 11) ? (t - 12) : 0);
        this.hl = 0L;
        this.lh = 0L;
        this.ll = 0L;
    }

    /**
     * Convert this qint to a 64-bit signed integer, truncating toward zero, matching CORE-MATH {@code qint_toi}. Used
     * by exp_3 to extract the integer part of the reduction. Returns 0 if {@code ex < 0}.
     */
    long toLong() {
        if ( this.ex < 0L ) {
            return 0L;
        }
        // C: int64_t r = a->hh >> (63 - a->ex);
        // Note: C uses arithmetic right shift on a uint64_t, but the top bit
        // of hh is 1 for normalized values so we use logical shift to match
        // the C semantics (zero-fill from the left).
        final long r = this.hh >>> (int) (63L - this.ex);
        return (this.sgn != 0L) ? -r : r;
    }

    // -------------------------------------------------------------------
    // Arithmetic — multiplication
    // -------------------------------------------------------------------

    /**
     * In-place subnormalisation, matching CORE-MATH {@code subnormalize_qint}. No-op if {@code ex > -1023}; otherwise
     * rounds the mantissa toward subnormal-double range using round-to-nearest-even.
     *
     * <p>The C reference branches on the FP rounding mode via {@code fegetround()};
     * Java's IEEE-754 stack is fixed at {@code FE_TONEAREST}, so we hard-code that branch (round-to-nearest-even).
     */
    void subnormalize() {
        if ( this.ex > -1023L ) {
            return;
        }

        // C: uint64_t ex = -(1011 + a->ex);
        // C's body shifts a uint64_t by `ex` bits — when ex >= 64 the C spec
        // calls this UB, but on every supported platform (x86, arm64, arm32,
        // riscv) the dynamic-shift instruction silently masks the count to
        // mod 64. Java's `>>>` operator does exactly the same masking
        // (JLS §15.19: "If the promoted type of the left-hand operand is
        // long, then only the six lowest-order bits of the right-hand
        // operand are used as the shift distance"). So we mirror the C
        // body verbatim, relying on Java's mod-64 shift semantics to match
        // the platform-defined C behaviour bit-for-bit.
        final long ex = -(1011L + this.ex);
        final int sh = (int) ex; // Java masks to & 63 in the shift below

        long hi = this.hh >>> sh;
        long md = (this.hh >>> (sh - 1)) & 0x1L;
        // C: `lo = (a.hh & (~0ull >> ex)) || a.hl || a.lh || a.ll;`
        // (logical-or coerces to {0,1})
        long mask = ~0L >>> sh;
        long lo = (((this.hh & mask) != 0L) || (this.hl != 0L) || (this.lh != 0L) || (this.ll != 0L)) ? 1L : 0L;

        // Round to nearest even (FE_TONEAREST):
        //   hi += lo ? md : hi & md;
        if ( lo != 0L ) {
            hi += md;
        } else {
            hi += (hi & md);
        }

        // a.hh = hi << ex;  a.hl = a.lh = a.ll = 0;
        // Java's `<<` masks to & 63 too (JLS §15.19), matching the C body's
        // platform-defined behaviour.
        this.hh = hi << sh;
        this.hl = 0L;
        this.lh = 0L;
        this.ll = 0L;

        if ( this.hh == 0L ) {
            this.ex++;
            this.hh = 1L << 63;
        }
    }

    /**
     * Convert this qint to a double with correct rounding, matching CORE-MATH {@code qint_tod}. Note: this method
     * <b>mutates</b> the receiver via {@link #subnormalize}, mirroring the C reference's in-place behaviour.
     */
    double toDouble() {
        subnormalize();

        // f64_u r = {.u = (hh >> 11) | (0x3ffll << 52)};
        long rBits = (this.hh >>> 11) | (0x3ffL << 52);

        double rd = 0.0;
        if ( (this.hh & 0x400L) != 0L ) {
            rd += 0x1p-53;
        }
        if ( ((this.hh & 0x3ffL) != 0L) || (this.hl != 0L) || (this.lh != 0L) || (this.ll != 0L) ) {
            rd += 0x1p-54;
        }
        if ( this.sgn != 0L ) {
            rd = -rd;
        }

        rBits = rBits | (this.sgn << 63);
        double r = Double.longBitsToDouble(rBits) + rd;

        double e;
        if ( this.ex > -1023L ) {
            // Normal-double regime.
            if ( this.ex > 1023L ) {
                if ( this.ex == 1024L ) {
                    r = r * 0x1p+1;
                    e = 0x1p+1023;
                } else {
                    r = 0x1.fffffffffffffp+1023;
                    e = 0x1.fffffffffffffp+1023;
                }
            } else {
                final long eBits = ((this.ex + 1023L) & 0x7ffL) << 52;
                e = Double.longBitsToDouble(eBits);
            }
        } else {
            // Subnormal regime.
            if ( this.ex < -1074L ) {
                if ( this.ex == -1075L ) {
                    r = r * 0x1p-1;
                    e = 0x1p-1074;
                } else {
                    r = 0x0.0000000000001p-1022;
                    e = 0x0.0000000000001p-1022;
                }
            } else {
                // C: e.u = 1ll << (ex + 1074)
                // ex ∈ [-1074, -1023] → shift ∈ [0, 51]
                final long eBits = 1L << (this.ex + 1074L);
                e = Double.longBitsToDouble(eBits);
            }
        }

        return r * e;
    }

    /** Copy from another qint, matching CORE-MATH {@code cp_qint}. */
    void copyFrom(Qint64 src) {
        this.ex = src.ex;
        this.hh = src.hh;
        this.hl = src.hl;
        this.lh = src.lh;
        this.ll = src.ll;
        this.sgn = src.sgn;
    }

    /**
     * {@code this = a + b}, matching CORE-MATH {@code add_qint}. Error bounded by 2 ulps_256 (1 ulp same-sign, exact
     * under Sterbenz).
     *
     * <p>The destination may alias one of the operands — inputs are
     * snapshot into locals before the destination is written.
     */
    void addAssign(Qint64 a, Qint64 b) {
        // CORE-MATH special-cases: if a or b is zero (rh|rl == 0), copy the other.
        if ( a.hh == 0L && a.hl == 0L && a.lh == 0L && a.ll == 0L ) {
            this.copyFrom(b);
            return;
        }
        if ( b.hh == 0L && b.hl == 0L && b.lh == 0L && b.ll == 0L ) {
            this.copyFrom(a);
            return;
        }

        // Compare absolute values.
        final int c = cmpQint(a, b);
        if ( c == 0 ) {
            // |a| == |b|.
            if ( (a.sgn ^ b.sgn) != 0L ) {
                // Opposite signs → exact zero.
                this.copyFrom(ZERO_Q);
                return;
            }
            // Same signs → 2× the operand.
            this.copyFrom(a);
            this.ex++;
            return;
        }
        if ( c < 0 ) {
            // |a| < |b| — recursive call swaps order.
            addAssign(b, a);
            return;
        }
        // From here: |a| > |b|.

        // Snapshot inputs so we can safely write into `this`.
        // Use a 256-bit (rh:hh|hl, rl:lh|ll) representation as 4 longs.
        long ahHi = a.hh, ahLo = a.hl, alHi = a.lh, alLo = a.ll;
        long bhHi = b.hh, bhLo = b.hl, blHi = b.lh, blLo = b.ll;

        long mEx = a.ex;
        final long k = a.ex - b.ex;

        // Right-shift B by k bits (treating (bhHi:bhLo:blHi:blLo) as a 256-bit
        // number). The C reference uses a u128 abstraction: rh and rl are 128
        // bits each, and shifts by k mix between them.
        if ( k > 0L ) {
            if ( k >= 256L ) {
                // Entire B drops below precision.
                bhHi = 0L;
                bhLo = 0L;
                blHi = 0L;
                blLo = 0L;
            } else if ( k >= 128L ) {
                // bl = (k < 256) ? bh >> (k - 128) : 0;  bh = 0;
                final int sh = (int) (k - 128L);
                final long[] shifted = shiftRight128(bhHi, bhLo, sh);
                blHi = shifted[1];
                blLo = shifted[0];
                bhHi = 0L;
                bhLo = 0L;
            } else { // 1 <= k <= 127
                // bl = (bl >> k) | (bh << (128 - k));  bh = bh >> k;
                final int sh = (int) k;
                final long[] blShifted = shiftRight128(blHi, blLo, sh);
                final long[] bhShiftedLeft = shiftLeft128(bhHi, bhLo, 128 - sh);
                final long newBlHi = blShifted[1] | bhShiftedLeft[1];
                final long newBlLo = blShifted[0] | bhShiftedLeft[0];
                final long[] bhShiftedRight = shiftRight128(bhHi, bhLo, sh);
                blHi = newBlHi;
                blLo = newBlLo;
                bhHi = bhShiftedRight[1];
                bhLo = bhShiftedRight[0];
            }
        }

        // Now we have to add (ah, al) + (bh, bl), where each is 128 bits.
        long chHi, chLo, clHi, clLo;
        long sgn = a.sgn;
        long exOut = mEx;

        if ( (a.sgn ^ b.sgn) != 0L ) {
            // Subtraction: C = A + (-B), |A| > |B|.
            //   ch = ah - bh;
            //   if (subu128(al, bl, &cl)) ch--;
            // We need the low-128 borrow into the high-128 subtraction.
            final long[] clSub = sub128(alHi, alLo, blHi, blLo);
            final long borrowLo = clSub[2];
            clHi = clSub[1];
            clLo = clSub[0];
            final long[] chSub = sub128(ahHi, ahLo, bhHi, bhLo);
            // Apply the borrow from the low subtraction.
            chHi = chSub[1];
            chLo = chSub[0];
            if ( borrowLo != 0L ) {
                final long[] dec = sub128(chHi, chLo, 0L, 1L);
                chHi = dec[1];
                chLo = dec[0];
            }

            // Count leading zeros of the 256-bit C = (chHi:chLo:clHi:clLo).
            int cex;
            if ( chHi != 0L ) {
                cex = Long.numberOfLeadingZeros(chHi);
            } else if ( chLo != 0L ) {
                cex = 64 + Long.numberOfLeadingZeros(chLo);
            } else if ( clHi != 0L ) {
                cex = 128 + Long.numberOfLeadingZeros(clHi);
            } else {
                cex = 192 + Long.numberOfLeadingZeros(clLo);
            }
            // cex < 256 because |A| > |B| implies C != 0.

            if ( cex > 0 ) {
                // Recompute C with full precision: shift A by `cex` bits left,
                // and B by `cex - k` bits left/right.
                long sah, sal_lo, sal_hi;
                {
                    // shift A by cex bits to the left (256-bit shift, but A's
                    // low half is (al)).
                    if ( cex >= 128 ) {
                        // ah = al << (cex - 128); al = 0;
                        final long[] shifted = shiftLeft128(alHi, alLo, cex - 128);
                        ahHi = shifted[1];
                        ahLo = shifted[0];
                        alHi = 0L;
                        alLo = 0L;
                    } else { // 1 <= cex < 128
                        // ah = (ah << cex) | (al >> (128 - cex));
                        // al = al << cex;
                        final long[] ahLeft = shiftLeft128(ahHi, ahLo, cex);
                        final long[] alRight = shiftRight128(alHi, alLo, 128 - cex);
                        ahHi = ahLeft[1] | alRight[1];
                        ahLo = ahLeft[0] | alRight[0];
                        final long[] alLeft = shiftLeft128(alHi, alLo, cex);
                        alHi = alLeft[1];
                        alLo = alLeft[0];
                    }
                }

                final long sh = (long) cex - k;
                // Reset bh,bl from b's pristine values.
                bhHi = b.hh;
                bhLo = b.hl;
                blHi = b.lh;
                blLo = b.ll;
                if ( sh >= 0L ) {
                    if ( sh >= 128L ) {
                        // bh = bl << (sh - 128);  bl = 0;
                        final long[] shifted = shiftLeft128(blHi, blLo, (int) (sh - 128));
                        bhHi = shifted[1];
                        bhLo = shifted[0];
                        blHi = 0L;
                        blLo = 0L;
                    } else if ( sh > 0L ) { // 1 <= sh < 128
                        // bh = (bh << sh) | (bl >> (128 - sh));
                        // bl = bl << sh;
                        final int sh_i = (int) sh;
                        final long[] bhLeft = shiftLeft128(bhHi, bhLo, sh_i);
                        final long[] blRight = shiftRight128(blHi, blLo, 128 - sh_i);
                        bhHi = bhLeft[1] | blRight[1];
                        bhLo = bhLeft[0] | blRight[0];
                        final long[] blLeft = shiftLeft128(blHi, blLo, sh_i);
                        blHi = blLeft[1];
                        blLo = blLeft[0];
                    }
                    // sh == 0: no shift needed.
                } else {
                    // sh < 0: shift b by -sh bits to the right.
                    final long j = -sh;
                    if ( j >= 128L ) {
                        // bl = bh >> (j - 128); bh = 0;
                        final long[] shifted = shiftRight128(bhHi, bhLo, (int) (j - 128));
                        blHi = shifted[1];
                        blLo = shifted[0];
                        bhHi = 0L;
                        bhLo = 0L;
                    } else { // 0 < j < 128
                        // bl = (bh << (128 - j)) | (bl >> j);
                        // bh = bh >> j;
                        final int j_i = (int) j;
                        final long[] bhLeft = shiftLeft128(bhHi, bhLo, 128 - j_i);
                        final long[] blRight = shiftRight128(blHi, blLo, j_i);
                        final long newBlHi = bhLeft[1] | blRight[1];
                        final long newBlLo = bhLeft[0] | blRight[0];
                        final long[] bhRight = shiftRight128(bhHi, bhLo, j_i);
                        bhHi = bhRight[1];
                        bhLo = bhRight[0];
                        blHi = newBlHi;
                        blLo = newBlLo;
                    }
                }

                exOut -= cex;

                // Recompute C = A - B (with the low/high borrow chain again).
                final long[] clSub2 = sub128(alHi, alLo, blHi, blLo);
                final long borrowLo2 = clSub2[2];
                clHi = clSub2[1];
                clLo = clSub2[0];
                final long[] chSub2 = sub128(ahHi, ahLo, bhHi, bhLo);
                chHi = chSub2[1];
                chLo = chSub2[0];
                if ( borrowLo2 != 0L ) {
                    final long[] dec = sub128(chHi, chLo, 0L, 1L);
                    chHi = dec[1];
                    chLo = dec[0];
                }

                // Recount leading zeros (for the final left shift).
                if ( chHi != 0L ) {
                    cex = Long.numberOfLeadingZeros(chHi);
                } else if ( chLo != 0L ) {
                    cex = 64 + Long.numberOfLeadingZeros(chLo);
                } else if ( clHi != 0L ) {
                    cex = 128 + Long.numberOfLeadingZeros(clHi);
                } else {
                    cex = 192 + Long.numberOfLeadingZeros(clLo);
                }
            }
            // Final left shift by `cex` bits.
            if ( cex > 0 ) {
                // ch = (ch << ex) | (cl >> (128 - ex)); cl = cl << ex;
                if ( cex >= 128 ) {
                    // 128-bit chunk crossover: ch becomes cl shifted left.
                    final long[] shifted = shiftLeft128(clHi, clLo, cex - 128);
                    chHi = shifted[1];
                    chLo = shifted[0];
                    clHi = 0L;
                    clLo = 0L;
                } else { // 1 <= cex < 128
                    final long[] chLeft = shiftLeft128(chHi, chLo, cex);
                    final long[] clRight = shiftRight128(clHi, clLo, 128 - cex);
                    chHi = chLeft[1] | clRight[1];
                    chLo = chLeft[0] | clRight[0];
                    final long[] clLeft = shiftLeft128(clHi, clLo, cex);
                    clHi = clLeft[1];
                    clLo = clLeft[0];
                }
                exOut -= cex;
            }
        } else {
            // Same signs: C = A + B.
            //   cy = addu128(ah, bh, &ch);
            //   if (addu128(al, bl, &cl)) cy += !(++ch);
            // Add low halves first to compute the carry into the high addition.
            final long[] clAdd = add128(alHi, alLo, blHi, blLo);
            final long carryLo = clAdd[2];
            clHi = clAdd[1];
            clLo = clAdd[0];

            final long[] chAdd = add128(ahHi, ahLo, bhHi, bhLo);
            long cy = chAdd[2];
            chHi = chAdd[1];
            chLo = chAdd[0];

            if ( carryLo != 0L ) {
                // ++ch and possibly bump cy if ch wrapped from all-ones.
                final long[] inc = add128(chHi, chLo, 0L, 1L);
                final long incCarry = inc[2];
                chHi = inc[1];
                chLo = inc[0];
                if ( incCarry != 0L ) {
                    cy++;
                }
            }

            if ( cy != 0L ) {
                // Carry in the 256-bit addition: shift right by 1 and OR in
                // the top bit.
                //   cl = (ch << 127) | (cl >> 1);
                //   ch = ((u128)1 << 127) | (ch >> 1);
                // Shift the (chHi:chLo:clHi:clLo) 256-bit value right by 1,
                // OR-in the implicit carry as the top bit of ch.
                clLo = (clLo >>> 1) | (clHi << 63);
                clHi = (clHi >>> 1) | (chLo << 63);
                chLo = (chLo >>> 1) | (chHi << 63);
                chHi = (chHi >>> 1) | (1L << 63);
                exOut++;
            }
        }

        this.sgn = sgn;
        this.hh = chHi;
        this.hl = chLo;
        this.lh = clHi;
        this.ll = clLo;
        this.ex = exOut;
    }

    /**
     * Same as {@link #addAssign} but only considers the upper 2 limbs of a and b (matches CORE-MATH
     * {@code add_qint_22}). Error bounded by 2 ulps_128.
     *
     * <p>The destination may alias one of the operands.
     */
    void addAssign22(Qint64 a, Qint64 b) {
        if ( a.hh == 0L ) {
            this.copyFrom(b);
            return;
        }
        if ( b.hh == 0L ) {
            this.copyFrom(a);
            return;
        }

        final int c = cmpQint22(a, b);
        if ( c == 0 ) {
            if ( (a.sgn ^ b.sgn) != 0L ) {
                this.copyFrom(ZERO_Q);
                return;
            }
            this.copyFrom(a);
            this.ex++;
            return;
        }
        if ( c < 0 ) {
            addAssign22(b, a);
            return;
        }
        // |a| > |b|.

        long ahHi = a.hh, ahLo = a.hl;
        long bhHi = b.hh, bhLo = b.hl;
        // Snapshot pristine bh for the recompute branch.
        final long bhHi0 = b.hh, bhLo0 = b.hl;

        long mEx = a.ex;
        final long k = a.ex - b.ex;

        if ( k > 0L ) {
            // bh = (k >= 128) ? 0 : bh >> k;
            if ( k >= 128L ) {
                bhHi = 0L;
                bhLo = 0L;
            } else {
                final long[] shifted = shiftRight128(bhHi, bhLo, (int) k);
                bhHi = shifted[1];
                bhLo = shifted[0];
            }
        }

        long chHi, chLo;
        long sgn = a.sgn;
        long exOut = mEx;

        if ( (a.sgn ^ b.sgn) != 0L ) {
            // Subtraction: ch = ah - bh.
            final long[] sub = sub128(ahHi, ahLo, bhHi, bhLo);
            chHi = sub[1];
            chLo = sub[0];

            int cex;
            if ( chHi != 0L ) {
                cex = Long.numberOfLeadingZeros(chHi);
            } else {
                cex = 64 + Long.numberOfLeadingZeros(chLo);
            }
            // cex < 128 because |A| > |B|.

            if ( cex > 0 ) {
                // ah <<= cex
                final long[] ahLeft = shiftLeft128(ahHi, ahLo, cex);
                ahHi = ahLeft[1];
                ahLo = ahLeft[0];

                // bh = (cex >= k) ? b->rh << (cex - k) : b->rh >> (k - cex);
                if ( (long) cex >= k ) {
                    final long[] shifted = shiftLeft128(bhHi0, bhLo0, (int) (cex - k));
                    bhHi = shifted[1];
                    bhLo = shifted[0];
                } else {
                    final long[] shifted = shiftRight128(bhHi0, bhLo0, (int) (k - cex));
                    bhHi = shifted[1];
                    bhLo = shifted[0];
                }
                exOut -= cex;

                final long[] sub2 = sub128(ahHi, ahLo, bhHi, bhLo);
                chHi = sub2[1];
                chLo = sub2[0];

                // Recount leading zeros.
                if ( chHi != 0L ) {
                    cex = Long.numberOfLeadingZeros(chHi);
                } else {
                    cex = 64 + Long.numberOfLeadingZeros(chLo);
                }
            }
            // ch <<= cex
            if ( cex > 0 ) {
                final long[] shifted = shiftLeft128(chHi, chLo, cex);
                chHi = shifted[1];
                chLo = shifted[0];
                exOut -= cex;
            }
        } else {
            // Addition: ch = ah + bh, cy = carry.
            final long[] add = add128(ahHi, ahLo, bhHi, bhLo);
            final long cy = add[2];
            chHi = add[1];
            chLo = add[0];

            if ( cy != 0L ) {
                // ch = ((u128) 1 << 127) | (ch >> 1);
                chLo = (chLo >>> 1) | (chHi << 63);
                chHi = (chHi >>> 1) | (1L << 63);
                exOut++;
            }
        }

        this.sgn = sgn;
        this.hh = chHi;
        this.hl = chLo;
        this.lh = 0L;
        this.ll = 0L;
        this.ex = exOut;
    }

    /**
     * {@code this = a * b}, full 4&times;4 limb mul (matches CORE-MATH {@code mul_qint}). Error bounded by 14
     * ulps_256.
     *
     * <p>Inputs are snapshot before writing the destination, so aliasing
     * is supported (e.g. {@code mul_qint(r, r, x)}).
     */
    void mulAssign(Qint64 a, Qint64 b) {
        // Snapshot to permit aliasing.
        final long aHh = a.hh, aHl = a.hl, aLh = a.lh, aLl = a.ll;
        final long bHh = b.hh, bHl = b.hl, bLh = b.lh, bLl = b.ll;
        final long aex = a.ex, bex = b.ex;
        final long asgn = a.sgn, bsgn = b.sgn;

        // Compute all 10 partial products. Each rij is a 128-bit product.
        // We represent each u128 as (lo, hi) pair.
        final long r33Lo = aHh * bHh, r33Hi = unsignedMulHigh(aHh, bHh);
        final long r32Lo = aHh * bHl, r32Hi = unsignedMulHigh(aHh, bHl);
        final long r23Lo = aHl * bHh, r23Hi = unsignedMulHigh(aHl, bHh);
        final long r31Lo = aHh * bLh, r31Hi = unsignedMulHigh(aHh, bLh);
        final long r13Lo = aLh * bHh, r13Hi = unsignedMulHigh(aLh, bHh);
        final long r22Lo = aHl * bHl, r22Hi = unsignedMulHigh(aHl, bHl);
        final long r30Lo = aHh * bLl, r30Hi = unsignedMulHigh(aHh, bLl);
        final long r03Lo = aLl * bHh, r03Hi = unsignedMulHigh(aLl, bHh);
        final long r21Lo = aHl * bLh, r21Hi = unsignedMulHigh(aHl, bLh);
        final long r12Lo = aLh * bHl, r12Hi = unsignedMulHigh(aLh, bHl);

        // t3 = (r12 >> 64) + (r21 >> 64) + (r03 >> 64) + (r30 >> 64)
        // — a 128-bit value (sum of four 64-bit highs, < 2^66).
        long t3Lo;
        long t3Hi = 0L;
        long sum;
        sum = r12Hi + r21Hi;
        long c = (Long.compareUnsigned(sum, r12Hi) < 0) ? 1L : 0L;
        t3Lo = sum;
        t3Hi += c;
        sum = t3Lo + r03Hi;
        c = (Long.compareUnsigned(sum, t3Lo) < 0) ? 1L : 0L;
        t3Lo = sum;
        t3Hi += c;
        sum = t3Lo + r30Hi;
        c = (Long.compareUnsigned(sum, t3Lo) < 0) ? 1L : 0L;
        t3Lo = sum;
        t3Hi += c;

        // t4 = r22 + t3, with carry into c4.
        long t4Lo, t4Hi;
        long c4 = 0L;
        {
            final long[] add = add128(r22Hi, r22Lo, t3Hi, t3Lo);
            t4Hi = add[1];
            t4Lo = add[0];
            c4 += add[2];
        }
        // t4 += r13
        {
            final long[] add = add128(t4Hi, t4Lo, r13Hi, r13Lo);
            t4Hi = add[1];
            t4Lo = add[0];
            c4 += add[2];
        }
        // t4 += r31
        {
            final long[] add = add128(t4Hi, t4Lo, r31Hi, r31Lo);
            t4Hi = add[1];
            t4Lo = add[0];
            c4 += add[2];
        }

        // t5 = r23 + (t4 >> 64), c5 = carry.
        // (t4 >> 64) means: shift the 128-bit t4 right by 64 → low becomes t4Hi, high becomes 0.
        long t5Lo, t5Hi;
        long c5 = 0L;
        {
            final long[] add = add128(0L, t4Hi, r23Hi, r23Lo);
            t5Hi = add[1];
            t5Lo = add[0];
            c5 += add[2];
        }
        // t5 += r32
        {
            final long[] add = add128(t5Hi, t5Lo, r32Hi, r32Lo);
            t5Hi = add[1];
            t5Lo = add[0];
            c5 += add[2];
        }

        // t6 = r33 + ((c5 << 64) | (t5 >> 64)) + c4
        // Build the (c5 << 64 | t5 >> 64) 128-bit value.
        long midLo = t5Hi;
        long midHi = c5;
        // t6 = r33 + mid + c4
        long t6Lo, t6Hi;
        {
            final long[] add = add128(r33Hi, r33Lo, midHi, midLo);
            t6Hi = add[1];
            t6Lo = add[0];
            // ignore final carry — full product is bounded by 2^512.
        }
        {
            final long[] add = add128(t6Hi, t6Lo, 0L, c4);
            t6Hi = add[1];
            t6Lo = add[0];
        }

        // ex = !(t6 >> 127); i.e. ex = (top bit of t6Hi == 0) ? 1 : 0
        final long ex = ((t6Hi >>> 63) & 1L) == 0L ? 1L : 0L;

        // t5 = (t5 << 64) | (t4 & 0xffffffffffffffff)
        // → 128-bit value where high = t5Lo and low = t4Lo (the low 64 of t4).
        long t5_newLo = t4Lo;
        long t5_newHi = t5Lo;
        t5Lo = t5_newLo;
        t5Hi = t5_newHi;

        if ( ex != 0L ) {
            // r->rh = (t6 << 1) | (t5 >> 127);
            // r->rl = t5 << 1;
            final long rhLo = (t6Lo << 1);
            final long rhHi = (t6Hi << 1) | (t6Lo >>> 63);
            // (t5 >> 127): low bit is the top bit of t5Hi.
            final long t5TopBit = (t5Hi >>> 63) & 1L;
            this.hh = rhHi;
            this.hl = rhLo | t5TopBit;
            // r->rl = t5 << 1
            this.lh = (t5Hi << 1) | (t5Lo >>> 63);
            this.ll = t5Lo << 1;
        } else {
            this.hh = t6Hi;
            this.hl = t6Lo;
            this.lh = t5Hi;
            this.ll = t5Lo;
        }

        this.ex = aex + bex + 1L - ex;
        this.sgn = asgn ^ bsgn;
    }

    /**
     * Same as {@link #mulAssign} but considering only the upper 3 limbs of each operand (matches CORE-MATH
     * {@code mul_qint_33}). Error bounded by 6 ulps_256.
     */
    void mulAssign33(Qint64 a, Qint64 b) {
        final long aHh = a.hh, aHl = a.hl, aLh = a.lh;
        final long bHh = b.hh, bHl = b.hl, bLh = b.lh;
        final long aex = a.ex, bex = b.ex;
        final long asgn = a.sgn, bsgn = b.sgn;

        final long r33Lo = aHh * bHh, r33Hi = unsignedMulHigh(aHh, bHh);
        final long r32Lo = aHh * bHl, r32Hi = unsignedMulHigh(aHh, bHl);
        final long r23Lo = aHl * bHh, r23Hi = unsignedMulHigh(aHl, bHh);
        final long r31Lo = aHh * bLh, r31Hi = unsignedMulHigh(aHh, bLh);
        final long r13Lo = aLh * bHh, r13Hi = unsignedMulHigh(aLh, bHh);
        final long r22Lo = aHl * bHl, r22Hi = unsignedMulHigh(aHl, bHl);
        final long r21Lo = aHl * bLh, r21Hi = unsignedMulHigh(aHl, bLh);
        final long r12Lo = aLh * bHl, r12Hi = unsignedMulHigh(aLh, bHl);

        // t3 = (r12 >> 64) + (r21 >> 64) — < 2^65.
        long t3Lo, t3Hi = 0L, sum, c;
        sum = r12Hi + r21Hi;
        c = (Long.compareUnsigned(sum, r12Hi) < 0) ? 1L : 0L;
        t3Lo = sum;
        t3Hi += c;

        long t4Lo, t4Hi, c4 = 0L;
        {
            final long[] add = add128(r22Hi, r22Lo, t3Hi, t3Lo);
            t4Hi = add[1];
            t4Lo = add[0];
            c4 += add[2];
        }
        {
            final long[] add = add128(t4Hi, t4Lo, r13Hi, r13Lo);
            t4Hi = add[1];
            t4Lo = add[0];
            c4 += add[2];
        }
        {
            final long[] add = add128(t4Hi, t4Lo, r31Hi, r31Lo);
            t4Hi = add[1];
            t4Lo = add[0];
            c4 += add[2];
        }

        long t5Lo, t5Hi, c5 = 0L;
        {
            final long[] add = add128(0L, t4Hi, r23Hi, r23Lo);
            t5Hi = add[1];
            t5Lo = add[0];
            c5 += add[2];
        }
        {
            final long[] add = add128(t5Hi, t5Lo, r32Hi, r32Lo);
            t5Hi = add[1];
            t5Lo = add[0];
            c5 += add[2];
        }

        long midLo = t5Hi, midHi = c5;
        long t6Lo, t6Hi;
        {
            final long[] add = add128(r33Hi, r33Lo, midHi, midLo);
            t6Hi = add[1];
            t6Lo = add[0];
        }
        {
            final long[] add = add128(t6Hi, t6Lo, 0L, c4);
            t6Hi = add[1];
            t6Lo = add[0];
        }

        final long ex = ((t6Hi >>> 63) & 1L) == 0L ? 1L : 0L;

        long t5_newLo = t4Lo, t5_newHi = t5Lo;
        t5Lo = t5_newLo;
        t5Hi = t5_newHi;

        if ( ex != 0L ) {
            final long rhLo = (t6Lo << 1);
            final long rhHi = (t6Hi << 1) | (t6Lo >>> 63);
            final long t5TopBit = (t5Hi >>> 63) & 1L;
            this.hh = rhHi;
            this.hl = rhLo | t5TopBit;
            this.lh = (t5Hi << 1) | (t5Lo >>> 63);
            this.ll = t5Lo << 1;
        } else {
            this.hh = t6Hi;
            this.hl = t6Lo;
            this.lh = t5Hi;
            this.ll = t5Lo;
        }
        this.ex = aex + bex + 1L - ex;
        this.sgn = asgn ^ bsgn;
    }

    /**
     * Same as {@link #mulAssign} but considering only the upper limb of b (matches CORE-MATH {@code mul_qint_41}).
     * Error bounded by 2 ulps_256.
     */
    void mulAssign41(Qint64 a, Qint64 b) {
        final long aHh = a.hh, aHl = a.hl, aLh = a.lh, aLl = a.ll;
        final long bHh = b.hh;
        final long aex = a.ex, bex = b.ex;
        final long asgn = a.sgn, bsgn = b.sgn;

        final long r33Lo = aHh * bHh, r33Hi = unsignedMulHigh(aHh, bHh);
        final long r23Lo = aHl * bHh, r23Hi = unsignedMulHigh(aHl, bHh);
        final long r13Lo = aLh * bHh, r13Hi = unsignedMulHigh(aLh, bHh);
        final long r03Lo = aLl * bHh, r03Hi = unsignedMulHigh(aLl, bHh);

        // t3 = r03 >> 64
        long t3Lo = r03Hi, t3Hi = 0L;

        long t4Lo, t4Hi, c4 = 0L;
        {
            final long[] add = add128(r13Hi, r13Lo, t3Hi, t3Lo);
            t4Hi = add[1];
            t4Lo = add[0];
            c4 += add[2];
        }

        long t5Lo, t5Hi, c5 = 0L;
        {
            final long[] add = add128(0L, t4Hi, r23Hi, r23Lo);
            t5Hi = add[1];
            t5Lo = add[0];
            c5 += add[2];
        }

        long midLo = t5Hi, midHi = c5;
        long t6Lo, t6Hi;
        {
            final long[] add = add128(r33Hi, r33Lo, midHi, midLo);
            t6Hi = add[1];
            t6Lo = add[0];
        }
        {
            final long[] add = add128(t6Hi, t6Lo, 0L, c4);
            t6Hi = add[1];
            t6Lo = add[0];
        }

        final long ex = ((t6Hi >>> 63) & 1L) == 0L ? 1L : 0L;

        long t5_newLo = t4Lo, t5_newHi = t5Lo;
        t5Lo = t5_newLo;
        t5Hi = t5_newHi;

        if ( ex != 0L ) {
            final long rhLo = (t6Lo << 1);
            final long rhHi = (t6Hi << 1) | (t6Lo >>> 63);
            final long t5TopBit = (t5Hi >>> 63) & 1L;
            this.hh = rhHi;
            this.hl = rhLo | t5TopBit;
            this.lh = (t5Hi << 1) | (t5Lo >>> 63);
            this.ll = t5Lo << 1;
        } else {
            this.hh = t6Hi;
            this.hl = t6Lo;
            this.lh = t5Hi;
            this.ll = t5Lo;
        }
        this.ex = aex + bex + 1L - ex;
        this.sgn = asgn ^ bsgn;
    }

    // -------------------------------------------------------------------
    // Internal 128-bit primitives
    // -------------------------------------------------------------------

    /**
     * Same as {@link #mulAssign} but considering only the upper 3 limbs of a and the upper limb of b (matches CORE-MATH
     * {@code mul_qint_31}). Exact product (the full result fits in 256 bits).
     */
    void mulAssign31(Qint64 a, Qint64 b) {
        final long aHh = a.hh, aHl = a.hl, aLh = a.lh;
        final long bHh = b.hh;
        final long aex = a.ex, bex = b.ex;
        final long asgn = a.sgn, bsgn = b.sgn;

        final long r33Lo = aHh * bHh, r33Hi = unsignedMulHigh(aHh, bHh);
        final long r23Lo = aHl * bHh, r23Hi = unsignedMulHigh(aHl, bHh);
        final long r13Lo = aLh * bHh, r13Hi = unsignedMulHigh(aLh, bHh);

        // t4 = r13
        long t4Lo = r13Lo, t4Hi = r13Hi;

        long t5Lo, t5Hi, c5 = 0L;
        {
            final long[] add = add128(0L, t4Hi, r23Hi, r23Lo);
            t5Hi = add[1];
            t5Lo = add[0];
            c5 += add[2];
        }

        long midLo = t5Hi, midHi = c5;
        long t6Lo, t6Hi;
        {
            final long[] add = add128(r33Hi, r33Lo, midHi, midLo);
            t6Hi = add[1];
            t6Lo = add[0];
        }

        final long ex = ((t6Hi >>> 63) & 1L) == 0L ? 1L : 0L;

        long t5_newLo = t4Lo, t5_newHi = t5Lo;
        t5Lo = t5_newLo;
        t5Hi = t5_newHi;

        if ( ex != 0L ) {
            final long rhLo = (t6Lo << 1);
            final long rhHi = (t6Hi << 1) | (t6Lo >>> 63);
            final long t5TopBit = (t5Hi >>> 63) & 1L;
            this.hh = rhHi;
            this.hl = rhLo | t5TopBit;
            this.lh = (t5Hi << 1) | (t5Lo >>> 63);
            this.ll = t5Lo << 1;
        } else {
            this.hh = t6Hi;
            this.hl = t6Lo;
            this.lh = t5Hi;
            this.ll = t5Lo;
        }
        this.ex = aex + bex + 1L - ex;
        this.sgn = asgn ^ bsgn;
    }

    /**
     * Same as {@link #mulAssign} but considering only the upper 2 limbs of each operand (matches CORE-MATH
     * {@code mul_qint_22}). Exact product.
     */
    void mulAssign22(Qint64 a, Qint64 b) {
        final long aHh = a.hh, aHl = a.hl;
        final long bHh = b.hh, bHl = b.hl;
        final long aex = a.ex, bex = b.ex;
        final long asgn = a.sgn, bsgn = b.sgn;

        final long r33Lo = aHh * bHh, r33Hi = unsignedMulHigh(aHh, bHh);
        final long r32Lo = aHh * bHl, r32Hi = unsignedMulHigh(aHh, bHl);
        final long r23Lo = aHl * bHh, r23Hi = unsignedMulHigh(aHl, bHh);
        final long r22Lo = aHl * bHl, r22Hi = unsignedMulHigh(aHl, bHl);

        // t4 = r22
        long t4Lo = r22Lo, t4Hi = r22Hi;

        long t5Lo, t5Hi, c5 = 0L;
        {
            final long[] add = add128(0L, t4Hi, r23Hi, r23Lo);
            t5Hi = add[1];
            t5Lo = add[0];
            c5 += add[2];
        }
        {
            final long[] add = add128(t5Hi, t5Lo, r32Hi, r32Lo);
            t5Hi = add[1];
            t5Lo = add[0];
            c5 += add[2];
        }

        long midLo = t5Hi, midHi = c5;
        long t6Lo, t6Hi;
        {
            final long[] add = add128(r33Hi, r33Lo, midHi, midLo);
            t6Hi = add[1];
            t6Lo = add[0];
        }

        final long ex = ((t6Hi >>> 63) & 1L) == 0L ? 1L : 0L;

        long t5_newLo = t4Lo, t5_newHi = t5Lo;
        t5Lo = t5_newLo;
        t5Hi = t5_newHi;

        if ( ex != 0L ) {
            final long rhLo = (t6Lo << 1);
            final long rhHi = (t6Hi << 1) | (t6Lo >>> 63);
            final long t5TopBit = (t5Hi >>> 63) & 1L;
            this.hh = rhHi;
            this.hl = rhLo | t5TopBit;
            this.lh = (t5Hi << 1) | (t5Lo >>> 63);
            this.ll = t5Lo << 1;
        } else {
            this.hh = t6Hi;
            this.hl = t6Lo;
            this.lh = t5Hi;
            this.ll = t5Lo;
        }
        this.ex = aex + bex + 1L - ex;
        this.sgn = asgn ^ bsgn;
    }

    /**
     * Same as {@link #mulAssign} but considering only the upper 2 limbs of a and the upper limb of b (matches CORE-MATH
     * {@code mul_qint_21}). Exact product.
     */
    void mulAssign21(Qint64 a, Qint64 b) {
        final long aHh = a.hh, aHl = a.hl;
        final long bHh = b.hh;
        final long aex = a.ex, bex = b.ex;
        final long asgn = a.sgn, bsgn = b.sgn;

        // r33 = aHh * bHh (128-bit)
        // r23 = aHl * bHh (128-bit)
        // t6 = r33 + (r23 >> 64) — i.e. 128-bit add of the 128-bit r33 with
        //   the high half of r23 (low half goes to t5 below).
        final long r33Lo = aHh * bHh, r33Hi = unsignedMulHigh(aHh, bHh);
        final long r23Lo = aHl * bHh, r23Hi = unsignedMulHigh(aHl, bHh);

        long t6Lo, t6Hi;
        {
            // (r23 >> 64) → low = r23Hi, high = 0
            final long[] add = add128(r33Hi, r33Lo, 0L, r23Hi);
            t6Hi = add[1];
            t6Lo = add[0];
        }
        // ex = !(t6 >> 127)
        final long ex = ((t6Hi >>> 63) & 1L) == 0L ? 1L : 0L;

        // t5 = r23 << 64
        // (low becomes 0, high becomes r23Lo as a 128-bit value)
        long t5Lo = 0L, t5Hi = r23Lo;

        if ( ex != 0L ) {
            final long rhLo = (t6Lo << 1);
            final long rhHi = (t6Hi << 1) | (t6Lo >>> 63);
            final long t5TopBit = (t5Hi >>> 63) & 1L;
            this.hh = rhHi;
            this.hl = rhLo | t5TopBit;
            this.lh = (t5Hi << 1) | (t5Lo >>> 63);
            this.ll = t5Lo << 1;
        } else {
            this.hh = t6Hi;
            this.hl = t6Lo;
            this.lh = t5Hi;
            this.ll = t5Lo;
        }
        this.ex = aex + bex + 1L - ex;
        this.sgn = asgn ^ bsgn;
    }

    /**
     * Same as {@link #mulAssign} but considering only the upper limb of each operand (matches CORE-MATH
     * {@code mul_qint_11}). Exact product.
     */
    void mulAssign11(Qint64 a, Qint64 b) {
        final long aHh = a.hh, bHh = b.hh;
        final long aex = a.ex, bex = b.ex;
        final long asgn = a.sgn, bsgn = b.sgn;

        // t6 = aHh * bHh (128-bit)
        final long t6Lo = aHh * bHh, t6Hi = unsignedMulHigh(aHh, bHh);
        // ex = !(t6 >> 127)
        final long ex = ((t6Hi >>> 63) & 1L) == 0L ? 1L : 0L;

        if ( ex != 0L ) {
            // r->rh = t6 << 1
            this.hh = (t6Hi << 1) | (t6Lo >>> 63);
            this.hl = t6Lo << 1;
        } else {
            this.hh = t6Hi;
            this.hl = t6Lo;
        }
        this.lh = 0L;
        this.ll = 0L;
        this.ex = aex + bex + 1L - ex;
        this.sgn = asgn ^ bsgn;
    }

    /**
     * {@code this = b * a}, where b is a signed 64-bit integer (matches CORE-MATH {@code mul_qint_2}). Error bounded by
     * 2 ulps_256.
     */
    void mulAssign2(long b, Qint64 a) {
        if ( b == 0L ) {
            this.copyFrom(ZERO_Q);
            return;
        }
        // Snapshot a in case of aliasing.
        final long aHh = a.hh, aHl = a.hl, aLh = a.lh, aLl = a.ll;
        final long aex = a.ex;
        final long asgn = a.sgn;

        long c = (b < 0L) ? -b : b;
        if ( c == 1L ) {
            // r = a, but possibly flipped sign.
            this.hh = aHh;
            this.hl = aHl;
            this.lh = aLh;
            this.ll = aLl;
            this.ex = aex;
            this.sgn = ((b < 0L) ? 1L : 0L) ^ asgn;
            return;
        }

        long sgnOut = ((b < 0L) ? 1L : 0L) ^ asgn;
        long exOut = aex + 64L;

        // scale c so that 2^63 <= c < 2^64
        final int k = Long.numberOfLeadingZeros(c);
        c = c << k;
        exOut -= k;

        // t3 = aHh * c, t2 = aHl * c, t1 = aLh * c, t0 = aLl * c (128-bit each)
        final long t3Lo = aHh * c, t3HiInit = unsignedMulHigh(aHh, c);
        final long t2Lo = aHl * c, t2Hi = unsignedMulHigh(aHl, c);
        final long t1Lo = aLh * c, t1Hi = unsignedMulHigh(aLh, c);
        final long t0Lo = aLl * c, t0Hi = unsignedMulHigh(aLl, c);

        // t = t0 >> 64 → 128-bit value with low = t0Hi, high = 0.
        long tLo = t0Hi, tHi = 0L;

        // (cy:1, t1:128) = t + t1
        long t1NewLo, t1NewHi, cy;
        {
            final long[] add = add128(t1Hi, t1Lo, tHi, tLo);
            t1NewHi = add[1];
            t1NewLo = add[0];
            cy = add[2];
        }

        // t = ((u128) cy << 64) | (t1 >> 64) → low = t1NewHi, high = cy
        tLo = t1NewHi;
        tHi = cy;

        // (cy:1, t2:128) = t + t2
        long t2NewLo, t2NewHi;
        {
            final long[] add = add128(t2Hi, t2Lo, tHi, tLo);
            t2NewHi = add[1];
            t2NewLo = add[0];
            cy = add[2];
        }

        // t3 += ((u128) cy << 64) | (t2 >> 64)
        // ⇒ add to t3 the 128-bit value (cy:1 in high, t2NewHi in low).
        long t3Lo_v = t3Lo, t3Hi_v = t3HiInit;
        {
            final long[] add = add128(t3Hi_v, t3Lo_v, cy, t2NewHi);
            t3Hi_v = add[1];
            t3Lo_v = add[0];
            // ignore final carry — full product bounded above.
        }

        // ex = clz(t3 >> 64) — i.e., clz(t3Hi_v) (treating t3 >> 64 as 64-bit).
        final int ex = Long.numberOfLeadingZeros(t3Hi_v);

        // t2 = (t2 << 64) | (t1 & 0xffff...)
        // → 128-bit value where low = t1NewLo (low 64 of t1), high = t2NewLo.
        long t2_newLo = t1NewLo, t2_newHi = t2NewLo;

        if ( ex != 0 ) {
            // r->rh = (t3 << 1) | (t2 >> 127)
            // r->rl = t2 << 1
            // ex == 1 in CORE-MATH because both operands are normalized.
            // But it's possible to be larger if c == 1 was hit earlier (we
            // handled that above). For safety, support arbitrary ex up to 63.
            // Note: per the C reference, ex is 0 or 1 because both a and c
            // are normalized.
            // shift t3 left by ex
            final long t3ShLo = t3Lo_v << ex;
            final long t3ShHi = (t3Hi_v << ex) | ((ex == 0) ? 0L : (t3Lo_v >>> (64 - ex)));
            // (t2 >> (128 - ex)) → low ex bits of t2Hi.
            // For ex == 1: (t2 >> 127) is the top bit of t2_newHi.
            final long t2TopBits = (ex == 0) ? 0L : (t2_newHi >>> (64 - ex));
            this.hh = t3ShHi;
            this.hl = t3ShLo | t2TopBits;
            // r->rl = t2 << ex
            this.lh = (t2_newHi << ex) | ((ex == 0) ? 0L : (t2_newLo >>> (64 - ex)));
            this.ll = t2_newLo << ex;
            this.ex = exOut - 1L;
        } else {
            this.hh = t3Hi_v;
            this.hl = t3Lo_v;
            this.lh = t2_newHi;
            this.ll = t2_newLo;
            this.ex = exOut;
        }
        this.sgn = sgnOut;
    }
}
