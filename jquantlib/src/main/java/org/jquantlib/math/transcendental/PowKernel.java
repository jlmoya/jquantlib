package org.jquantlib.math.transcendental;

/**
 * Pure-Java port of CORE-MATH's correctly-rounded {@code cr_pow(double, double)}.
 *
 * <p><b>Status:</b> Phase 2n A.1.c — specials path (bit-exact), Stage 1
 * fast path (Hubrecht/Jeannerod/Zimmermann ARITH 2023 algorithm phase_1), AND Stage 2 (Dint64 / 128-bit Ziv-2 chain)
 * ported. When Stage 1's Ziv error-bound test passes (~99.93% of inputs), the result is bit-exact against CORE-MATH
 * cr_pow. The remaining ~0.07% of fall-through cases are now handled by the Stage 2 Dint64 chain (log_2 → mul_dint_21 →
 * exp_2 with a 28-ulp rounding test). Stage 3 (Qint64 with exact_pow rounding-boundary detection) is deferred to Phase
 * 2o; it triggers only for inputs at sub-2^-113 distance from rounding boundaries (extremely rare in financial
 * workloads).
 *
 * <p>Source: CORE-MATH {@code src/binary64/pow/pow.c} (Tom Hubrecht and
 * Paul Zimmermann; CERN/INRIA; MIT-licensed). Algorithm reference is Hubrecht, Jeannerod, Zimmermann, "Towards a
 * correctly-rounded and fast power function in binary64 arithmetic", ARITH 2023, with detailed proofs in HAL
 * hal-04159652. Tables (_INVERSE, _LOG_INV, T1, T2, P_1, Q_1, _INVERSE_2_1, _INVERSE_2_2, _LOG_INV_2_1, _LOG_INV_2_2,
 * T1_2, T2_2, P_2, Q_2) extracted via {@code migration-harness/tools/extract-pow-tables.py}.
 *
 * <p>Specials handling mirrors the C source verbatim — IEEE 754-2019
 * Section 9.2.1 dispatch on (x, y) including ±0, ±inf, NaN, integer-y, odd-integer-y discrimination, and pow(1, NaN) =
 * 1 / pow(NaN, 0) = 1 exemptions.
 *
 * <p>The Stage 1 fast path computes log(x) via a 182-entry reciprocal
 * table reduction + degree-8 Sollya polynomial (in p_1), multiplies by y to get y*log(x), then computes exp via a 64x64
 * product table decomposition (T1, T2) + degree-4 polynomial (in q_1). The Ziv rounding test (Lemma 5 from reference
 * [5]) compares {@code res_h + fma(err, ±res_h, res_l)} for opposite-sign err perturbations; bit-exact return requires
 * {@code res_min == res_max}.
 *
 * <p>The Stage 2 accurate path (per pow.c lines 1799-1876) converts x
 * and y into {@code dint64_t} (128-bit mantissa + signed exponent + sign), computes {@code log_2(R, X)} with relative
 * error 2^-122.88, multiplies by Y, then runs {@code exp_2(R, R)} with relative error 2^-121.70, totaling 2^-113.17
 * (~29126 ulps). The 28-ulp rounding test checks the bits past the round bit; success returns the rounded dint_tod
 * result. Failure (extremely rare) currently still defers to {@link Math#pow} pending the Stage 3 Qint64 port.
 *
 * <p><b>Dint primitives (pow_dint.h convention):</b> stage-2 uses
 * pow_dint.h's specific {@code mul_dint}/{@code add_dint} which differ from sin/cos's canonical {@code dint.h} by 1 in
 * the exponent adjustment (pow_dint.h: {@code r->ex = a->ex + b->ex + ex} vs dint.h:
 * {@code r->ex = a->ex + b->ex + ex - 1}). The pow stage-2 primitives are therefore implemented inline in this class as
 * {@code long[4] = {hi, lo, ex, sgn}} arrays — they do NOT reuse the existing {@link Dint64} class (which encodes the
 * sin/cos canonical convention used by {@link SinCosKernel}).
 */
final class PowKernel {

    /** IEEE-754 double exponent mask (bits 52-62). */
    private static final long EXP_MASK = 0x7ff0000000000000L;
    // ===== pow.h: _INVERSE[182] =====
    private static final double[] _INVERSE = new double[182];
    // ===== pow.h: _LOG_INV[182][2] =====
    private static final double[] _LOG_INV_H = new double[182];

    // ============================================================
    //  log_1 — fast path log(x) approximation  pow.c:581-648
    // ============================================================
    private static final double[] _LOG_INV_L = new double[182];

    // ============================================================
    //  p_1 — log(1+z)-z poly, pow.c:323-334
    // ============================================================
    // ===== pow.h: T1[64][2] =====
    private static final double[] _T1_H = new double[64];

    // ============================================================
    //  s_mul: a * (bh + bl) -> (hi, lo)
    // ============================================================
    private static final double[] _T1_L = new double[64];

    // ============================================================
    //  exp_1 — pow.c:953-1057
    // ============================================================
    // ===== pow.h: T2[64][2] =====
    private static final double[] _T2_H = new double[64];

    // ============================================================
    //  q_1 — pow.c:120-132
    // ============================================================
    private static final double[] _T2_L = new double[64];

    // ============================================================
    //  d_mul: (ah + al) * (bh + bl) - (al * bl)
    // ============================================================
    // ===== pow.h: P_1[6] =====
    private static final double[] P_1 = new double[6];

    // ============================================================
    //  Helpers
    // ============================================================
    // ===== pow.h: Q_1[5] =====
    private static final double[] Q_1 = new double[5];
    // ===== pow_dint.h: dint64_t scalar constants =====
    private static final long ONE_D_HI = 0x8000000000000000L;

    // ============================================================
    //  Tables — extracted by migration-harness/tools/extract-pow-tables.py
    //  Source: CORE-MATH coremath/pow.h. Do not hand-edit.
    // ============================================================
    private static final long ONE_D_LO = 0L;
    private static final long ONE_D_EX = 0L;
    private static final long ONE_D_SGN = 0L;
    private static final long M_ONE_D_HI = 0x8000000000000000L;
    private static final long M_ONE_D_LO = 0L;
    private static final long M_ONE_D_EX = 0L;
    private static final long M_ONE_D_SGN = 0x1L;
    private static final long LOG2_HI = 0xb17217f7d1cf79abL;
    private static final long LOG2_LO = 0xc9e3b39803f2f6afL;
    private static final long LOG2_EX = -1L;
    private static final long LOG2_SGN = 0L;
    private static final long LOG2_INV_HI = 0xb8aa3b295c17f0bcL;
    private static final long LOG2_INV_LO = 0L;
    private static final long LOG2_INV_EX = 12L;
    private static final long LOG2_INV_SGN = 0L;

    // ============================================================
    //  Stage 2 (Phase 2n A.1.c)
    //  Dint64 / 128-bit Ziv-2 chain — pow.c:1799-1876.
    //  Each dint instance is encoded as long[4] = {hi, lo, ex, sgn}
    //  using pow_dint.h's specific mul/add convention (which differs
    //  from sin/cos's canonical dint.h by a "-1" in the exponent).
    // ============================================================

    // ----- log_2 (dint64 log) — pow.c:651-799 -----
    private static final long ZERO_D_HI = 0L;

    // ----- p_2: 9-step polynomial — pow.c:343-472 -----
    private static final long ZERO_D_LO = 0L;

    // ----- exp_2 (dint64 exp) — pow.c:1061-1128 -----
    private static final long ZERO_D_EX = -1076L;

    // ----- q_2: degree-7 polynomial — pow.c:137-240 -----
    private static final long ZERO_D_SGN = 0L;

    // ============================================================
    //  Dint64 primitives — pow_dint.h convention
    // ============================================================
    // ===== pow_dint.h: _INVERSE_2_1[92] =====
    private static final long[] _INVERSE_2_1_HI = new long[92];
    private static final long[] _INVERSE_2_1_LO = new long[92];
    private static final long[] _INVERSE_2_1_EX = new long[92];
    private static final long[] _INVERSE_2_1_SGN = new long[92];

    // ----- add_dint (pow_dint.h:197-295) -----
    // ===== pow_dint.h: _INVERSE_2_2[129] =====
    private static final long[] _INVERSE_2_2_HI = new long[129];

    // ----- add_dint_11 (pow_dint.h:299-395) — high-only add -----
    private static final long[] _INVERSE_2_2_LO = new long[129];

    // ----- mul_dint (pow_dint.h:400-428) -----
    private static final long[] _INVERSE_2_2_EX = new long[129];

    // ----- mul_dint_21 (pow_dint.h:432-454) -----
    private static final long[] _INVERSE_2_2_SGN = new long[129];

    // ----- mul_dint_11 (pow_dint.h:489-503) -----
    // ===== pow_dint.h: _LOG_INV_2_1[92] =====
    private static final long[] _LOG_INV_2_1_HI = new long[92];

    // ----- mul_dint_int64 (pow_dint.h:508-545) -----
    private static final long[] _LOG_INV_2_1_LO = new long[92];

    // ============================================================
    //  Internal arithmetic helpers
    // ============================================================
    private static final long[] _LOG_INV_2_1_EX = new long[92];
    private static final long[] _LOG_INV_2_1_SGN = new long[92];
    // ===== pow_dint.h: _LOG_INV_2_2[129] =====
    private static final long[] _LOG_INV_2_2_HI = new long[129];
    private static final long[] _LOG_INV_2_2_LO = new long[129];
    private static final long[] _LOG_INV_2_2_EX = new long[129];
    private static final long[] _LOG_INV_2_2_SGN = new long[129];
    // ===== pow_dint.h: T1_2[64] =====
    private static final long[] _T1_2_HI = new long[64];
    private static final long[] _T1_2_LO = new long[64];
    private static final long[] _T1_2_EX = new long[64];
    private static final long[] _T1_2_SGN = new long[64];

    // ============================================================
    //  Stage-2 tables (pow_dint.h, extracted by extract-pow-tables.py)
    // ============================================================
    // ===== pow_dint.h: T2_2[64] =====
    private static final long[] _T2_2_HI = new long[64];
    private static final long[] _T2_2_LO = new long[64];
    private static final long[] _T2_2_EX = new long[64];
    private static final long[] _T2_2_SGN = new long[64];
    // ===== pow_dint.h: P_2[9] =====
    private static final long[] _P_2_HI = new long[9];
    private static final long[] _P_2_LO = new long[9];
    private static final long[] _P_2_EX = new long[9];
    private static final long[] _P_2_SGN = new long[9];
    // ===== pow_dint.h: Q_2[8] =====
    private static final long[] _Q_2_HI = new long[8];
    private static final long[] _Q_2_LO = new long[8];
    private static final long[] _Q_2_EX = new long[8];
    private static final long[] _Q_2_SGN = new long[8];

    static {
        long[] bits = { 0x3ff6900000000000L, 0x3ff6700000000000L, 0x3ff6500000000000L, 0x3ff6300000000000L,
                0x3ff6100000000000L, 0x3ff5f00000000000L, 0x3ff5e00000000000L, 0x3ff5c00000000000L, 0x3ff5a00000000000L,
                0x3ff5800000000000L, 0x3ff5600000000000L, 0x3ff5400000000000L, 0x3ff5300000000000L, 0x3ff5100000000000L,
                0x3ff4f00000000000L, 0x3ff4e00000000000L, 0x3ff4c00000000000L, 0x3ff4a00000000000L, 0x3ff4800000000000L,
                0x3ff4700000000000L, 0x3ff4500000000000L, 0x3ff4400000000000L, 0x3ff4200000000000L, 0x3ff4000000000000L,
                0x3ff3f00000000000L, 0x3ff3d00000000000L, 0x3ff3c00000000000L, 0x3ff3a00000000000L, 0x3ff3900000000000L,
                0x3ff3700000000000L, 0x3ff3600000000000L, 0x3ff3400000000000L, 0x3ff3300000000000L, 0x3ff3200000000000L,
                0x3ff3000000000000L, 0x3ff2f00000000000L, 0x3ff2d00000000000L, 0x3ff2c00000000000L, 0x3ff2b00000000000L,
                0x3ff2900000000000L, 0x3ff2800000000000L, 0x3ff2700000000000L, 0x3ff2500000000000L, 0x3ff2400000000000L,
                0x3ff2300000000000L, 0x3ff2100000000000L, 0x3ff2000000000000L, 0x3ff1f00000000000L, 0x3ff1e00000000000L,
                0x3ff1c00000000000L, 0x3ff1b00000000000L, 0x3ff1a00000000000L, 0x3ff1900000000000L, 0x3ff1700000000000L,
                0x3ff1600000000000L, 0x3ff1500000000000L, 0x3ff1400000000000L, 0x3ff1300000000000L, 0x3ff1200000000000L,
                0x3ff1000000000000L, 0x3ff0f00000000000L, 0x3ff0e00000000000L, 0x3ff0d00000000000L, 0x3ff0c00000000000L,
                0x3ff0b00000000000L, 0x3ff0a00000000000L, 0x3ff0900000000000L, 0x3ff0800000000000L, 0x3ff0700000000000L,
                0x3ff0600000000000L, 0x3ff0500000000000L, 0x3ff0400000000000L, 0x3ff0300000000000L, 0x3ff0200000000000L,
                0x3ff0000000000000L, 0x3ff0000000000000L, 0x3fefd00000000000L, 0x3fefb00000000000L, 0x3fef900000000000L,
                0x3fef700000000000L, 0x3fef500000000000L, 0x3fef300000000000L, 0x3fef100000000000L, 0x3fef000000000000L,
                0x3feee00000000000L, 0x3feec00000000000L, 0x3feea00000000000L, 0x3fee800000000000L, 0x3fee600000000000L,
                0x3fee500000000000L, 0x3fee300000000000L, 0x3fee100000000000L, 0x3fedf00000000000L, 0x3fedd00000000000L,
                0x3fedc00000000000L, 0x3feda00000000000L, 0x3fed800000000000L, 0x3fed700000000000L, 0x3fed500000000000L,
                0x3fed300000000000L, 0x3fed200000000000L, 0x3fed000000000000L, 0x3fece00000000000L, 0x3fecd00000000000L,
                0x3fecb00000000000L, 0x3fec900000000000L, 0x3fec800000000000L, 0x3fec600000000000L, 0x3fec500000000000L,
                0x3fec300000000000L, 0x3fec200000000000L, 0x3fec000000000000L, 0x3febf00000000000L, 0x3febd00000000000L,
                0x3febc00000000000L, 0x3feba00000000000L, 0x3feb900000000000L, 0x3feb700000000000L, 0x3feb600000000000L,
                0x3feb400000000000L, 0x3feb300000000000L, 0x3feb100000000000L, 0x3feb000000000000L, 0x3feae00000000000L,
                0x3fead00000000000L, 0x3feac00000000000L, 0x3feaa00000000000L, 0x3fea900000000000L, 0x3fea700000000000L,
                0x3fea600000000000L, 0x3fea500000000000L, 0x3fea300000000000L, 0x3fea200000000000L, 0x3fea100000000000L,
                0x3fe9f00000000000L, 0x3fe9e00000000000L, 0x3fe9d00000000000L, 0x3fe9c00000000000L, 0x3fe9a00000000000L,
                0x3fe9900000000000L, 0x3fe9800000000000L, 0x3fe9600000000000L, 0x3fe9500000000000L, 0x3fe9400000000000L,
                0x3fe9300000000000L, 0x3fe9100000000000L, 0x3fe9000000000000L, 0x3fe8f00000000000L, 0x3fe8e00000000000L,
                0x3fe8d00000000000L, 0x3fe8b00000000000L, 0x3fe8a00000000000L, 0x3fe8900000000000L, 0x3fe8800000000000L,
                0x3fe8700000000000L, 0x3fe8600000000000L, 0x3fe8400000000000L, 0x3fe8300000000000L, 0x3fe8200000000000L,
                0x3fe8100000000000L, 0x3fe8000000000000L, 0x3fe7f00000000000L, 0x3fe7e00000000000L, 0x3fe7c00000000000L,
                0x3fe7b00000000000L, 0x3fe7a00000000000L, 0x3fe7900000000000L, 0x3fe7800000000000L, 0x3fe7700000000000L,
                0x3fe7600000000000L, 0x3fe7500000000000L, 0x3fe7400000000000L, 0x3fe7300000000000L, 0x3fe7200000000000L,
                0x3fe7100000000000L, 0x3fe7000000000000L, 0x3fe6f00000000000L, 0x3fe6e00000000000L, 0x3fe6d00000000000L,
                0x3fe6c00000000000L, 0x3fe6b00000000000L, 0x3fe6a00000000000L, };
        for ( int i = 0; i < bits.length; i++ )
            _INVERSE[i] = Double.longBitsToDouble(bits[i]);
    }

    static {
        long[] hi = { 0xbfd5ff3070a79000L, 0xbfd5a42ab0f4d000L, 0xbfd548a2c3add000L, 0xbfd4ec9732600000L,
                0xbfd4900680401000L, 0xbfd432ef2a04f000L, 0xbfd404308686a000L, 0xbfd3a64c55694000L, 0xbfd347dd9a988000L,
                0xbfd2e8e2bae12000L, 0xbfd2895a13de8000L, 0xbfd22941fbcf8000L, 0xbfd1f8ff9e48a000L, 0xbfd1980d2dd42000L,
                0xbfd136870293b000L, 0xbfd1058bf9ae5000L, 0xbfd0a324e2739000L, 0xbfd0402594b4d000L, 0xbfcfb9186d5e4000L,
                0xbfcf550a564b8000L, 0xbfce8c0252aa6000L, 0xbfce27076e2b0000L, 0xbfcd5c216b4fc000L, 0xbfcc8ff7c79aa000L,
                0xbfcc2968558c2000L, 0xbfcb5b519e8fc000L, 0xbfcaf3c94e80c000L, 0xbfca23bc1fe2c000L, 0xbfc9bb362e7e0000L,
                0xbfc8e928de886000L, 0xbfc87fa06520c000L, 0xbfc7ab890210e000L, 0xbfc740f8f5404000L, 0xbfc6d60fe719e000L,
                0xbfc5ff3070a7a000L, 0xbfc59338d9982000L, 0xbfc4ba36f39a6000L, 0xbfc44d2b6ccb8000L, 0xbfc3dfc2b0ecc000L,
                0xbfc303d718e48000L, 0xbfc29552f8200000L, 0xbfc2266f190a6000L, 0xbfc1478584674000L, 0xbfc0d77e7cd08000L,
                0xbfc0671512ca6000L, 0xbfbf0a30c0118000L, 0xbfbe27076e2b0000L, 0xbfbd4313d66cc000L, 0xbfbc5e548f5bc000L,
                0xbfba926d3a4ac000L, 0xbfb9ab4246204000L, 0xbfb8c345d6318000L, 0xbfb7da766d7b0000L, 0xbfb60658a9374000L,
                0xbfb51b073f060000L, 0xbfb42edcbea64000L, 0xbfb341d7961bc000L, 0xbfb253f62f0a0000L, 0xbfb16536eea38000L,
                0xbfaf0a30c0118000L, 0xbfad276b8adb0000L, 0xbfab42dd71198000L, 0xbfa95c830ec90000L, 0xbfa77458f6330000L,
                0xbfa58a5bafc90000L, 0xbfa39e87b9fe8000L, 0xbfa1b0d989240000L, 0xbf9f829b0e780000L, 0xbf9b9fc027b00000L,
                0xbf97b91b07d60000L, 0xbf93cea443470000L, 0xbf8fc0a8b0fc0000L, 0xbf87dc475f820000L, 0xbf7fe02a6b100000L,
                0x0000000000000000L, 0x0000000000000000L, 0x3f78121214580000L, 0x3f841929f9680000L, 0x3f8c317384c80000L,
                0x3f9228fb1fea0000L, 0x3f963d6178690000L, 0x3f9a55f548c60000L, 0x3f9e72bf28140000L, 0x3fa0415d89e78000L,
                0x3fa252f32f8d0000L, 0x3fa466aed42e0000L, 0x3fa67c94f2d48000L, 0x3fa894aa149f8000L, 0x3faaaef2d0fb0000L,
                0x3fabbcebfc690000L, 0x3fadda8adc680000L, 0x3faffa6911ab8000L, 0x3fb10e45b3cb0000L, 0x3fb2207b5c784000L,
                0x3fb2aa04a4470000L, 0x3fb3bdf5a7d20000L, 0x3fb4d3115d208000L, 0x3fb55e10050e0000L, 0x3fb674f089364000L,
                0x3fb78d02263d8000L, 0x3fb8197e2f410000L, 0x3fb9335e5d594000L, 0x3fba4e7640b1c000L, 0x3fbadc77ee5b0000L,
                0x3fbbf968769fc000L, 0x3fbd179788218000L, 0x3fbda72763844000L, 0x3fbec739830a0000L, 0x3fbf57bc7d900000L,
                0x3fc03cdc0a51e000L, 0x3fc08598b59e4000L, 0x3fc1178e8227e000L, 0x3fc160c8024b2000L, 0x3fc1f3b925f26000L,
                0x3fc23d712a49c000L, 0x3fc2d1610c868000L, 0x3fc31b994d3a4000L, 0x3fc3b08b67580000L, 0x3fc3fb45a5992000L,
                0x3fc4913d8333c000L, 0x3fc4dc7b897bc000L, 0x3fc5737cc9018000L, 0x3fc5bf406b544000L, 0x3fc6574ebe8c2000L,
                0x3fc6a399dabbe000L, 0x3fc6f0128b756000L, 0x3fc7898d85444000L, 0x3fc7d6903caf6000L, 0x3fc871213750e000L,
                0x3fc8beafeb390000L, 0x3fc90c6db9fcc000L, 0x3fc9a8778deba000L, 0x3fc9f6c40708a000L, 0x3fca454082e6a000L,
                0x3fcae2ca6f672000L, 0x3fcb31d8575bc000L, 0x3fcb811730b82000L, 0x3fcbd087383be000L, 0x3fcc6ffbc6f00000L,
                0x3fccc000c9db4000L, 0x3fcd1037f2656000L, 0x3fcdb13db0d48000L, 0x3fce020cc6236000L, 0x3fce530effe72000L,
                0x3fcea4449f04a000L, 0x3fcf474b134e0000L, 0x3fcf991c6cb3c000L, 0x3fcfeb2233ea0000L, 0x3fd01eae5626c000L,
                0x3fd047e60cde8000L, 0x3fd09aa572e6c000L, 0x3fd0c42d67616000L, 0x3fd0edd060b78000L, 0x3fd1178e8227e000L,
                0x3fd14167ef367000L, 0x3fd16b5ccbad0000L, 0x3fd1bf99635a7000L, 0x3fd1e9e16788a000L, 0x3fd214456d0ec000L,
                0x3fd23ec5991ec000L, 0x3fd269621134e000L, 0x3fd2941afb187000L, 0x3fd2bef07cdc9000L, 0x3fd314f1e1d36000L,
                0x3fd3401e12aed000L, 0x3fd36b6776be1000L, 0x3fd396ce359bc000L, 0x3fd3c25277333000L, 0x3fd3edf463c17000L,
                0x3fd419b423d5f000L, 0x3fd44591e053a000L, 0x3fd4718dc271c000L, 0x3fd49da7f3bcc000L, 0x3fd4c9e09e173000L,
                0x3fd4f637ebbaa000L, 0x3fd522ae0738a000L, 0x3fd54f431b7be000L, 0x3fd57bf753c8d000L, 0x3fd5a8cadbbee000L,
                0x3fd5d5bddf596000L, 0x3fd602d08af09000L, 0x3fd630030b3ab000L, };
        long[] lo = { 0xbd2e9e439f105039L, 0x3cde63af2df7ba69L, 0xbd23167e63081cf7L, 0xbd234d7aaf04d104L,
                0x3d38bccffe1a0f8cL, 0x3d3fb129931715adL, 0xbd3f8ef43049f7d3L, 0xbd37a71cbcd735d0L, 0x3d25594dd4c58092L,
                0x3d267b1e99b72bd8L, 0xbd3a8d7ad24c13f0L, 0x3d3a6976f5eb0963L, 0xbd27946c040cbe77L, 0xbd2b7b3a7a361c9aL,
                0x3d3d3e8499d67123L, 0x3d34ab9d817d52cdL, 0xbd0c6bee7ef4030eL, 0xbcf036b89ef42d7fL, 0x3d0d572aab993c87L,
                0x3d2323e3a09202feL, 0x3d26805b80e8e6ffL, 0x3d3a342c2af0003cL, 0x3d21ba91bbca681bL, 0x3d27794f689f8434L,
                0x3d2cfd73dee38a40L, 0x3d34b722ec011f31L, 0x3cba4e633fcd9066L, 0x3d3539cd91dc9f0bL, 0x3d21f2a8a1ce0ffcL,
                0xbd3a8154b13d72d5L, 0xbd322120401202fcL, 0x3d2bdb9072534a58L, 0x3d30b66c99018aa1L, 0x3d3bc6e557134767L,
                0x3d38586f183bebf2L, 0xbcf0ba68b7555d4aL, 0x3d34354bb3f219e5L, 0x3d170cc16135783cL, 0xbd28a72a62b8c13fL,
                0x3cd680b5ce3ecb05L, 0x3d35b967f4471dfcL, 0x3d24d20ab840e7f6L, 0xbd1563451027c750L, 0xbd3cb2cd2ee2f482L,
                0x3d2a47579cdc0a3dL, 0x3d3d599e83368e91L, 0x3d2a342c2af0003cL, 0x3d29454379135713L, 0xbd1d0c57585fbe06L,
                0xbd3563650bd22a9cL, 0x3d28a64826787061L, 0xbd3b20f5acb42a66L, 0xbd32cc844480c89bL, 0xbd30c3b1dee9c4f8L,
                0xbd383f69278e686aL, 0xbd1bc0eeea7c9acdL, 0xbd31d09299837610L, 0xbd3416f8fb69a701L, 0x3d147c5e768fa309L,
                0x3d2d599e83368e91L, 0xbd16a423c78a64b0L, 0x3d1c827ae5d6704cL, 0x3d2c148297c5feb8L, 0x3d3181dce586af09L,
                0x3d2b2b739570ad39L, 0xbd3eafd480ad9015L, 0x3d33401e9ae889bbL, 0xbd2980267c7e09e4L, 0x3d3b9a010ae6922aL,
                0x3d33b955b602ace4L, 0x3d36a2c432d6a40bL, 0xbcdf1e7cf6d3a69cL, 0x3d3eb1245b5da1f5L, 0xbd19e23f0dda40e4L,
                0x0000000000000000L, 0x0000000000000000L, 0x3d1ad50382973f27L, 0x3d1977c755d01368L, 0xbd341f33fcefb9feL,
                0x3d2713e3284991feL, 0x3d07abf389596542L, 0xbd2de0709f2d03c9L, 0xbd28d75149774d47L, 0xbd3dddc7f461c516L,
                0x3d283e9ae021b67bL, 0xbd2c167375bdfd28L, 0x3d3dac20827cca0cL, 0x3d39a19a8be97661L, 0x3d20fc1a353bb42eL,
                0xbd17bf868c317c2aL, 0xbd21b1ac64d9e42fL, 0x3d23008c98381a8fL, 0xbd37cf69284a3465L, 0x3d349d8cfc10c7bfL,
                0x3d37a48ba8b1cb41L, 0xbd319bd0ad125895L, 0xbcf53a2582f4e1efL, 0x3d0c1d740c53c72eL, 0x3d3a79994c9d3302L,
                0x3d069b5794b69fb7L, 0xbd3c0fe460d20041L, 0x3d23115c3abd47daL, 0xbd0e42b6b94407c8L, 0xbd3573b209c31904L,
                0x3d24218c8d824283L, 0x3d336433b5efbeedL, 0x3d1a89401fa71733L, 0x3d311fcba80cdd10L, 0x3d176a6c9ea8b04eL,
                0x3d381a9cf169fc5cL, 0xbd27e5dd7009902cL, 0x3d21ef78ce2d07f2L, 0x3d2ec2d2a9009e3dL, 0xbd15f74e9b083633L,
                0x3d100d238fd3df5cL, 0x3d039d6ccb81b4a1L, 0x3d3f098ee3a50810L, 0xbd3aade8f29320fbL, 0x3d319713c0cae559L,
                0xbd353e43558124c4L, 0x3d0c79b60ae1ff0fL, 0x3d39baa7a6b887f6L, 0xbd127023eb68981cL, 0xbd398c1d34f0f462L,
                0xbd38f934e66a15a6L, 0x3d3577390d31ef0fL, 0x3d38e67be3dbaf3fL, 0xbd24c06b17c301d7L, 0x3d3328eb42f9af75L,
                0xbd073d54aae92cd1L, 0xbd1935f57718d7caL, 0x3d3470fa3efec390L, 0xbd3337d94bcd3f43L, 0x3d360a77c81f7171L,
                0x3d37a8d5ae54f550L, 0x3d3c794e562a63cbL, 0x3d1e90683b9cd768L, 0xbd2d4bc4595412b6L, 0x3d3ee138d3a69d43L,
                0xbd1d6d585d57aff9L, 0xbd084a7e75b6f6e4L, 0x3d32806a847527e6L, 0xbd252b00adb91424L, 0xbd3fdbdbb13f7c18L,
                0x3d35e91663732a36L, 0xbd3bae49f1df7b5eL, 0xbd390d04cd7cc834L, 0x3d2f3418de00938bL, 0x3d3a43dcfade85aeL,
                0x3d2dbdf10d397f3cL, 0x3d3b50a1e1734342L, 0x3d27188b163ceae9L, 0x3d0019b52d8435f5L, 0x3d31ef78ce2d07f2L,
                0x3d3e0c07824daaf5L, 0xbd323299042d74bfL, 0xbd31ac89575c2125L, 0xbd382eaed3c8b65eL, 0xbd3caf0428b728a3L,
                0xbd36dbe448a2e522L, 0xbd31b61f10522625L, 0xbd3210c2b730e28bL, 0x3d2a9cfa4a5004f4L, 0xbd28e27ad3213cb8L,
                0xbd317c73556e291dL, 0x3d116ecdb0f177c8L, 0xbd05839c5663663dL, 0x3d183b54b606bd5cL, 0xbd3f067c297f2c3fL,
                0xbd3ce379226de3ecL, 0xbd06e95892923d88L, 0x3d306c18fb4c14c5L, 0x3d307b334daf4b9aL, 0xbd2e20891b0ad8a4L,
                0xbd3fc158cb3124b9L, 0x3d2ebe708164c759L, 0x3d1a8954c0910952L, 0x3d1fadedee5d40efL, 0xbcf7c79b0af7ecf8L,
                0xbd0a0b2a08a465dcL, 0x3d1ebe9176df3f65L, 0xbd2db623e731ae00L, };
        for ( int i = 0; i < hi.length; i++ ) {
            _LOG_INV_H[i] = Double.longBitsToDouble(hi[i]);
            _LOG_INV_L[i] = Double.longBitsToDouble(lo[i]);
        }
    }

    static {
        long[] hi = { 0x3ff0000000000000L, 0x3ff02c9a3e778061L, 0x3ff059b0d3158574L, 0x3ff0874518759bc8L,
                0x3ff0b5586cf9890fL, 0x3ff0e3ec32d3d1a2L, 0x3ff11301d0125b51L, 0x3ff1429aaea92de0L, 0x3ff172b83c7d517bL,
                0x3ff1a35beb6fcb75L, 0x3ff1d4873168b9aaL, 0x3ff2063b88628cd6L, 0x3ff2387a6e756238L, 0x3ff26b4565e27cddL,
                0x3ff29e9df51fdee1L, 0x3ff2d285a6e4030bL, 0x3ff306fe0a31b715L, 0x3ff33c08b26416ffL, 0x3ff371a7373aa9cbL,
                0x3ff3a7db34e59ff7L, 0x3ff3dea64c123422L, 0x3ff4160a21f72e2aL, 0x3ff44e086061892dL, 0x3ff486a2b5c13cd0L,
                0x3ff4bfdad5362a27L, 0x3ff4f9b2769d2ca7L, 0x3ff5342b569d4f82L, 0x3ff56f4736b527daL, 0x3ff5ab07dd485429L,
                0x3ff5e76f15ad2148L, 0x3ff6247eb03a5585L, 0x3ff6623882552225L, 0x3ff6a09e667f3bcdL, 0x3ff6dfb23c651a2fL,
                0x3ff71f75e8ec5f74L, 0x3ff75feb564267c9L, 0x3ff7a11473eb0187L, 0x3ff7e2f336cf4e62L, 0x3ff82589994cce13L,
                0x3ff868d99b4492edL, 0x3ff8ace5422aa0dbL, 0x3ff8f1ae99157736L, 0x3ff93737b0cdc5e5L, 0x3ff97d829fde4e50L,
                0x3ff9c49182a3f090L, 0x3ffa0c667b5de565L, 0x3ffa5503b23e255dL, 0x3ffa9e6b5579fdbfL, 0x3ffae89f995ad3adL,
                0x3ffb33a2b84f15fbL, 0x3ffb7f76f2fb5e47L, 0x3ffbcc1e904bc1d2L, 0x3ffc199bdd85529cL, 0x3ffc67f12e57d14bL,
                0x3ffcb720dcef9069L, 0x3ffd072d4a07897cL, 0x3ffd5818dcfba487L, 0x3ffda9e603db3285L, 0x3ffdfc97337b9b5fL,
                0x3ffe502ee78b3ff6L, 0x3ffea4afa2a490daL, 0x3ffefa1bee615a27L, 0x3fff50765b6e4540L,
                0x3fffa7c1819e90d8L, };
        long[] lo = { 0x0000000000000000L, 0xbc719083535b085dL, 0x3c8d73e2a475b465L, 0x3c6186be4bb284ffL,
                0x3c98a62e4adc610bL, 0x3c403a1727c57b53L, 0xbc96c51039449b3aL, 0xbc932fbf9af1369eL, 0xbc819041b9d78a76L,
                0x3c8e5b4c7b4968e4L, 0x3c9e016e00a2643cL, 0x3c8dc775814a8495L, 0x3c99b07eb6c70573L, 0x3c82bd339940e9d9L,
                0x3c8612e8afad1255L, 0x3c90024754db41d5L, 0x3c86f46ad23182e4L, 0x3c932721843659a6L, 0xbc963aeabf42eae2L,
                0xbc75e436d661f5e3L, 0x3c8ada0911f09ebcL, 0xbc5ef3691c309278L, 0x3c489b7a04ef80d0L, 0x3c73c1a3b69062f0L,
                0x3c7d4397afec42e2L, 0xbc94b309d25957e3L, 0xbc807abe1db13cadL, 0x3c99bb2c011d93adL, 0x3c96324c054647adL,
                0x3c9ba6f93080e65eL, 0xbc9383c17e40b497L, 0xbc9bb60987591c34L, 0xbc9bdd3413b26456L, 0xbc6bbe3a683c88abL,
                0xbc816e4786887a99L, 0xbc90245957316dd3L, 0xbc841577ee04992fL, 0x3c705d02ba15797eL, 0xbc9d4c1dd41532d8L,
                0xbc9fc6f89bd4f6baL, 0x3c96e9f156864b27L, 0x3c85cc13a2e3976cL, 0xbc675fc781b57ebcL, 0xbc9d185b7c1b85d1L,
                0x3c7c7c46b071f2beL, 0xbc9359495d1cd533L, 0xbc9d2f6edb8d41e1L, 0x3c90fac90ef7fd31L, 0x3c97a1cd345dcc81L,
                0xbc62805e3084d708L, 0xbc75584f7e54ac3bL, 0x3c823dd07a2d9e84L, 0x3c811065895048ddL, 0x3c92884dff483cadL,
                0x3c7503cbd1e949dbL, 0xbc9cbc3743797a9cL, 0x3c82ed02d75b3707L, 0x3c9c2300696db532L, 0xbc91a5cd4f184b5cL,
                0x3c839e8980a9cc8fL, 0xbc9e9c23179c2893L, 0x3c9dc7f486a4b6b0L, 0x3c99d3e12dd8a18bL,
                0x3c874853f3a5931eL, };
        for ( int i = 0; i < hi.length; i++ ) {
            _T1_H[i] = Double.longBitsToDouble(hi[i]);
            _T1_L[i] = Double.longBitsToDouble(lo[i]);
        }
    }

    static {
        long[] hi = { 0x3ff0000000000000L, 0x3ff000b175effdc7L, 0x3ff00162f3904052L, 0x3ff0021478e11ce6L,
                0x3ff002c605e2e8cfL, 0x3ff003779a95f959L, 0x3ff0042936faa3d8L, 0x3ff004dadb113da0L, 0x3ff0058c86da1c0aL,
                0x3ff0063e3a559473L, 0x3ff006eff583fc3dL, 0x3ff007a1b865a8caL, 0x3ff0085382faef83L, 0x3ff00905554425d4L,
                0x3ff009b72f41a12bL, 0x3ff00a6910f3b6fdL, 0x3ff00b1afa5abcbfL, 0x3ff00bcceb7707ecL, 0x3ff00c7ee448ee02L,
                0x3ff00d30e4d0c483L, 0x3ff00de2ed0ee0f5L, 0x3ff00e94fd0398e0L, 0x3ff00f4714af41d3L, 0x3ff00ff93412315cL,
                0x3ff010ab5b2cbd11L, 0x3ff0115d89ff3a8bL, 0x3ff0120fc089ff63L, 0x3ff012c1fecd613bL, 0x3ff0137444c9b5b5L,
                0x3ff01426927f5278L, 0x3ff014d8e7ee8d2fL, 0x3ff0158b4517bb88L, 0x3ff0163da9fb3335L, 0x3ff016f0169949edL,
                0x3ff017a28af25567L, 0x3ff018550706ab62L, 0x3ff019078ad6a19fL, 0x3ff019ba16628de2L, 0x3ff01a6ca9aac5f3L,
                0x3ff01b1f44af9f9eL, 0x3ff01bd1e77170b4L, 0x3ff01c8491f08f08L, 0x3ff01d37442d5070L, 0x3ff01de9fe280ac8L,
                0x3ff01e9cbfe113efL, 0x3ff01f4f8958c1c6L, 0x3ff020025a8f6a35L, 0x3ff020b533856324L, 0x3ff02168143b0281L,
                0x3ff0221afcb09e3eL, 0x3ff022cdece68c4fL, 0x3ff02380e4dd22adL, 0x3ff02433e494b755L, 0x3ff024e6ec0da046L,
                0x3ff02599fb483385L, 0x3ff0264d1244c719L, 0x3ff027003103b10eL, 0x3ff027b357854772L, 0x3ff0286685c9e059L,
                0x3ff02919bbd1d1d8L, 0x3ff029ccf99d720aL, 0x3ff02a803f2d170dL, 0x3ff02b338c811703L,
                0x3ff02be6e199c811L, };
        long[] lo = { 0x0000000000000000L, 0x3c9ae8e38c59c72aL, 0xbc57b5d0d58ea8f4L, 0x3c94115cb6b16a8eL,
                0xbc8d7c96f201bb2fL, 0x3c984711d4c35e9fL, 0xbc80484245243777L, 0xbc94b237da2025f9L, 0xbc75e00e62d6b30dL,
                0x3c9a1d6cedbb9481L, 0xbc94acf197a00142L, 0xbc6eaf2ea42391a5L, 0x3c7da93f90835f75L, 0xbc86a79084ab093cL,
                0x3c986364f8fbe8f8L, 0xbc882e8e14e3110eL, 0xbc84f6b2a7609f71L, 0xbc7e1a258ea8f71bL, 0x3c74362ca5bc26f1L,
                0x3c9095a56c919d02L, 0xbc6406ac4e81a645L, 0x3c9b5a6902767e09L, 0xbc991b2060859321L, 0x3c8427068ab22306L,
                0x3c9c1d0660524e08L, 0xbc9e7bdfb3204be8L, 0x3c8843aa8b9cbbc6L, 0xbc734104ee7edae9L, 0xbc72b6aeb6176892L,
                0x3c7a8cd33b8a1bb3L, 0x3c72edc08e5da99aL, 0x3c857ba2dc7e0c73L, 0x3c9b61299ab8cdb7L, 0xbc990565902c5f44L,
                0x3c870fc41c5c2d53L, 0x3c94b9a6e145d76cL, 0xbc7008eff5142bf9L, 0xbc977669f033c7deL, 0xbc909bb78eeead0aL,
                0x3c9371231477ece5L, 0x3c75e7626621eb5bL, 0xbc9bc72b100828a5L, 0xbc6ce39cbbab8bbeL, 0x3c816996709da2e2L,
                0xbc8c11f5239bf535L, 0x3c8e1d4eb5edc6b3L, 0xbc9afb99946ee3f0L, 0xbc98f06d8a148a32L, 0xbc82bf310fc54eb6L,
                0xbc9c95a035eb4175L, 0xbc9491793e46834dL, 0xbc73e8d0d9c49091L, 0xbc9314aa16278aa3L, 0x3c848daf888e9651L,
                0x3c856dc8046821f4L, 0x3c945b42356b9d47L, 0xbc7082ef51b61d7eL, 0x3c72106ed0920a34L, 0xbc9fd4cf26ea5d0fL,
                0xbc909f8775e78084L, 0x3c564cbba902ca27L, 0x3c94383ef231d207L, 0x3c94a47a505b3a47L,
                0x3c9e47120223467fL, };
        for ( int i = 0; i < hi.length; i++ ) {
            _T2_H[i] = Double.longBitsToDouble(hi[i]);
            _T2_L[i] = Double.longBitsToDouble(lo[i]);
        }
    }

    static {
        long[] bits = { 0x3fd5555555555558L, 0xbfd0000000000003L, 0x3fc999999981f535L, 0xbfc55555553d1eb4L,
                0x3fc2494526fd4a06L, 0xbfc0001f0c80e8ceL, };
        for ( int i = 0; i < bits.length; i++ )
            P_1[i] = Double.longBitsToDouble(bits[i]);
    }

    static {
        long[] bits = { 0x3ff0000000000000L, 0x3ff0000000000000L, 0x3fe0000000000000L, 0x3fc5555555997996L,
                0x3fa5555555849d8dL, };
        for ( int i = 0; i < bits.length; i++ )
            Q_1[i] = Double.longBitsToDouble(bits[i]);
    }

    static {
        long[][] data = { { 0xb500000000000000L, 0L, 0L, 0L }, { 0xb300000000000000L, 0L, 0L, 0L },
                { 0xb100000000000000L, 0L, 0L, 0L }, { 0xaf00000000000000L, 0L, 0L, 0L },
                { 0xad80000000000000L, 0L, 0L, 0L }, { 0xab80000000000000L, 0L, 0L, 0L },
                { 0xaa00000000000000L, 0L, 0L, 0L }, { 0xa800000000000000L, 0L, 0L, 0L },
                { 0xa680000000000000L, 0L, 0L, 0L }, { 0xa480000000000000L, 0L, 0L, 0L },
                { 0xa300000000000000L, 0L, 0L, 0L }, { 0xa180000000000000L, 0L, 0L, 0L },
                { 0xa000000000000000L, 0L, 0L, 0L }, { 0x9e80000000000000L, 0L, 0L, 0L },
                { 0x9d00000000000000L, 0L, 0L, 0L }, { 0x9b80000000000000L, 0L, 0L, 0L },
                { 0x9a00000000000000L, 0L, 0L, 0L }, { 0x9880000000000000L, 0L, 0L, 0L },
                { 0x9700000000000000L, 0L, 0L, 0L }, { 0x9580000000000000L, 0L, 0L, 0L },
                { 0x9480000000000000L, 0L, 0L, 0L }, { 0x9300000000000000L, 0L, 0L, 0L },
                { 0x9180000000000000L, 0L, 0L, 0L }, { 0x9080000000000000L, 0L, 0L, 0L },
                { 0x8f00000000000000L, 0L, 0L, 0L }, { 0x8e00000000000000L, 0L, 0L, 0L },
                { 0x8c80000000000000L, 0L, 0L, 0L }, { 0x8b80000000000000L, 0L, 0L, 0L },
                { 0x8a80000000000000L, 0L, 0L, 0L }, { 0x8900000000000000L, 0L, 0L, 0L },
                { 0x8800000000000000L, 0L, 0L, 0L }, { 0x8700000000000000L, 0L, 0L, 0L },
                { 0x8580000000000000L, 0L, 0L, 0L }, { 0x8480000000000000L, 0L, 0L, 0L },
                { 0x8380000000000000L, 0L, 0L, 0L }, { 0x8280000000000000L, 0L, 0L, 0L },
                { 0x8180000000000000L, 0L, 0L, 0L }, { 0x8000000000000000L, 0L, 0L, 0L },
                { 0x8000000000000000L, 0L, 0L, 0L }, { 0xfd00000000000000L, 0L, -1L, 0L },
                { 0xfb00000000000000L, 0L, -1L, 0L }, { 0xf900000000000000L, 0L, -1L, 0L },
                { 0xf780000000000000L, 0L, -1L, 0L }, { 0xf580000000000000L, 0L, -1L, 0L },
                { 0xf380000000000000L, 0L, -1L, 0L }, { 0xf200000000000000L, 0L, -1L, 0L },
                { 0xf000000000000000L, 0L, -1L, 0L }, { 0xee80000000000000L, 0L, -1L, 0L },
                { 0xec80000000000000L, 0L, -1L, 0L }, { 0xeb00000000000000L, 0L, -1L, 0L },
                { 0xe900000000000000L, 0L, -1L, 0L }, { 0xe780000000000000L, 0L, -1L, 0L },
                { 0xe600000000000000L, 0L, -1L, 0L }, { 0xe480000000000000L, 0L, -1L, 0L },
                { 0xe300000000000000L, 0L, -1L, 0L }, { 0xe100000000000000L, 0L, -1L, 0L },
                { 0xdf80000000000000L, 0L, -1L, 0L }, { 0xde00000000000000L, 0L, -1L, 0L },
                { 0xdc80000000000000L, 0L, -1L, 0L }, { 0xdb00000000000000L, 0L, -1L, 0L },
                { 0xd980000000000000L, 0L, -1L, 0L }, { 0xd880000000000000L, 0L, -1L, 0L },
                { 0xd700000000000000L, 0L, -1L, 0L }, { 0xd580000000000000L, 0L, -1L, 0L },
                { 0xd400000000000000L, 0L, -1L, 0L }, { 0xd280000000000000L, 0L, -1L, 0L },
                { 0xd180000000000000L, 0L, -1L, 0L }, { 0xd000000000000000L, 0L, -1L, 0L },
                { 0xce80000000000000L, 0L, -1L, 0L }, { 0xcd80000000000000L, 0L, -1L, 0L },
                { 0xcc00000000000000L, 0L, -1L, 0L }, { 0xcb00000000000000L, 0L, -1L, 0L },
                { 0xc980000000000000L, 0L, -1L, 0L }, { 0xc880000000000000L, 0L, -1L, 0L },
                { 0xc700000000000000L, 0L, -1L, 0L }, { 0xc600000000000000L, 0L, -1L, 0L },
                { 0xc500000000000000L, 0L, -1L, 0L }, { 0xc380000000000000L, 0L, -1L, 0L },
                { 0xc280000000000000L, 0L, -1L, 0L }, { 0xc180000000000000L, 0L, -1L, 0L },
                { 0xc000000000000000L, 0L, -1L, 0L }, { 0xbf00000000000000L, 0L, -1L, 0L },
                { 0xbe00000000000000L, 0L, -1L, 0L }, { 0xbd00000000000000L, 0L, -1L, 0L },
                { 0xbc00000000000000L, 0L, -1L, 0L }, { 0xba80000000000000L, 0L, -1L, 0L },
                { 0xb980000000000000L, 0L, -1L, 0L }, { 0xb880000000000000L, 0L, -1L, 0L },
                { 0xb780000000000000L, 0L, -1L, 0L }, { 0xb680000000000000L, 0L, -1L, 0L },
                { 0xb580000000000000L, 0L, -1L, 0L }, { 0xb480000000000000L, 0L, -1L, 0L }, };
        for ( int i = 0; i < data.length; i++ ) {
            _INVERSE_2_1_HI[i] = data[i][0];
            _INVERSE_2_1_LO[i] = data[i][1];
            _INVERSE_2_1_EX[i] = data[i][2];
            _INVERSE_2_1_SGN[i] = data[i][3];
        }
    }

    static {
        long[][] data = { { 0x8100000000000000L, 0L, 0L, 0L }, { 0x80fc000000000000L, 0L, 0L, 0L },
                { 0x80f8000000000000L, 0L, 0L, 0L }, { 0x80f4000000000000L, 0L, 0L, 0L },
                { 0x80f0000000000000L, 0L, 0L, 0L }, { 0x80ec000000000000L, 0L, 0L, 0L },
                { 0x80e8000000000000L, 0L, 0L, 0L }, { 0x80e4000000000000L, 0L, 0L, 0L },
                { 0x80e0000000000000L, 0L, 0L, 0L }, { 0x80dc000000000000L, 0L, 0L, 0L },
                { 0x80d8000000000000L, 0L, 0L, 0L }, { 0x80d4000000000000L, 0L, 0L, 0L },
                { 0x80d0000000000000L, 0L, 0L, 0L }, { 0x80cc000000000000L, 0L, 0L, 0L },
                { 0x80c8000000000000L, 0L, 0L, 0L }, { 0x80c4000000000000L, 0L, 0L, 0L },
                { 0x80c0000000000000L, 0L, 0L, 0L }, { 0x80bc000000000000L, 0L, 0L, 0L },
                { 0x80b8000000000000L, 0L, 0L, 0L }, { 0x80b4000000000000L, 0L, 0L, 0L },
                { 0x80b0000000000000L, 0L, 0L, 0L }, { 0x80ac000000000000L, 0L, 0L, 0L },
                { 0x80a8000000000000L, 0L, 0L, 0L }, { 0x80a4000000000000L, 0L, 0L, 0L },
                { 0x80a0000000000000L, 0L, 0L, 0L }, { 0x809c000000000000L, 0L, 0L, 0L },
                { 0x8098000000000000L, 0L, 0L, 0L }, { 0x8094000000000000L, 0L, 0L, 0L },
                { 0x8090000000000000L, 0L, 0L, 0L }, { 0x808c000000000000L, 0L, 0L, 0L },
                { 0x8088000000000000L, 0L, 0L, 0L }, { 0x8084000000000000L, 0L, 0L, 0L },
                { 0x8080000000000000L, 0L, 0L, 0L }, { 0x807c000000000000L, 0L, 0L, 0L },
                { 0x8078000000000000L, 0L, 0L, 0L }, { 0x8074000000000000L, 0L, 0L, 0L },
                { 0x8070000000000000L, 0L, 0L, 0L }, { 0x806c000000000000L, 0L, 0L, 0L },
                { 0x8068000000000000L, 0L, 0L, 0L }, { 0x8064000000000000L, 0L, 0L, 0L },
                { 0x8060000000000000L, 0L, 0L, 0L }, { 0x805c000000000000L, 0L, 0L, 0L },
                { 0x8058000000000000L, 0L, 0L, 0L }, { 0x8054000000000000L, 0L, 0L, 0L },
                { 0x8050000000000000L, 0L, 0L, 0L }, { 0x804c000000000000L, 0L, 0L, 0L },
                { 0x8048000000000000L, 0L, 0L, 0L }, { 0x8044000000000000L, 0L, 0L, 0L },
                { 0x8040000000000000L, 0L, 0L, 0L }, { 0x803c000000000000L, 0L, 0L, 0L },
                { 0x8038000000000000L, 0L, 0L, 0L }, { 0x8034000000000000L, 0L, 0L, 0L },
                { 0x8030000000000000L, 0L, 0L, 0L }, { 0x802c000000000000L, 0L, 0L, 0L },
                { 0x8028000000000000L, 0L, 0L, 0L }, { 0x8024000000000000L, 0L, 0L, 0L },
                { 0x8020000000000000L, 0L, 0L, 0L }, { 0x801c000000000000L, 0L, 0L, 0L },
                { 0x8018000000000000L, 0L, 0L, 0L }, { 0x8014000000000000L, 0L, 0L, 0L },
                { 0x8010000000000000L, 0L, 0L, 0L }, { 0x800c000000000000L, 0L, 0L, 0L },
                { 0x8008000000000000L, 0L, 0L, 0L }, { 0x8000000000000000L, 0L, 0L, 0L },
                { 0x8000000000000000L, 0L, 0L, 0L }, { 0xfff4000000000000L, 0L, -1L, 0L },
                { 0xffec000000000000L, 0L, -1L, 0L }, { 0xffe4000000000000L, 0L, -1L, 0L },
                { 0xffdc000000000000L, 0L, -1L, 0L }, { 0xffd4000000000000L, 0L, -1L, 0L },
                { 0xffcc000000000000L, 0L, -1L, 0L }, { 0xffc4000000000000L, 0L, -1L, 0L },
                { 0xffbc000000000000L, 0L, -1L, 0L }, { 0xffb4000000000000L, 0L, -1L, 0L },
                { 0xffac000000000000L, 0L, -1L, 0L }, { 0xffa4000000000000L, 0L, -1L, 0L },
                { 0xff9c000000000000L, 0L, -1L, 0L }, { 0xff94000000000000L, 0L, -1L, 0L },
                { 0xff8c000000000000L, 0L, -1L, 0L }, { 0xff84000000000000L, 0L, -1L, 0L },
                { 0xff7c000000000000L, 0L, -1L, 0L }, { 0xff74000000000000L, 0L, -1L, 0L },
                { 0xff6c000000000000L, 0L, -1L, 0L }, { 0xff64000000000000L, 0L, -1L, 0L },
                { 0xff5c000000000000L, 0L, -1L, 0L }, { 0xff54000000000000L, 0L, -1L, 0L },
                { 0xff4c000000000000L, 0L, -1L, 0L }, { 0xff44000000000000L, 0L, -1L, 0L },
                { 0xff3c000000000000L, 0L, -1L, 0L }, { 0xff34000000000000L, 0L, -1L, 0L },
                { 0xff2c000000000000L, 0L, -1L, 0L }, { 0xff24000000000000L, 0L, -1L, 0L },
                { 0xff1c000000000000L, 0L, -1L, 0L }, { 0xff14000000000000L, 0L, -1L, 0L },
                { 0xff0c000000000000L, 0L, -1L, 0L }, { 0xff04000000000000L, 0L, -1L, 0L },
                { 0xfefc000000000000L, 0L, -1L, 0L }, { 0xfef4000000000000L, 0L, -1L, 0L },
                { 0xfeec000000000000L, 0L, -1L, 0L }, { 0xfee4000000000000L, 0L, -1L, 0L },
                { 0xfedc000000000000L, 0L, -1L, 0L }, { 0xfed4000000000000L, 0L, -1L, 0L },
                { 0xfecc000000000000L, 0L, -1L, 0L }, { 0xfec4000000000000L, 0L, -1L, 0L },
                { 0xfebc000000000000L, 0L, -1L, 0L }, { 0xfeb4000000000000L, 0L, -1L, 0L },
                { 0xfeac000000000000L, 0L, -1L, 0L }, { 0xfea4000000000000L, 0L, -1L, 0L },
                { 0xfe9c000000000000L, 0L, -1L, 0L }, { 0xfe98000000000000L, 0L, -1L, 0L },
                { 0xfe90000000000000L, 0L, -1L, 0L }, { 0xfe88000000000000L, 0L, -1L, 0L },
                { 0xfe80000000000000L, 0L, -1L, 0L }, { 0xfe78000000000000L, 0L, -1L, 0L },
                { 0xfe70000000000000L, 0L, -1L, 0L }, { 0xfe68000000000000L, 0L, -1L, 0L },
                { 0xfe60000000000000L, 0L, -1L, 0L }, { 0xfe58000000000000L, 0L, -1L, 0L },
                { 0xfe50000000000000L, 0L, -1L, 0L }, { 0xfe48000000000000L, 0L, -1L, 0L },
                { 0xfe40000000000000L, 0L, -1L, 0L }, { 0xfe38000000000000L, 0L, -1L, 0L },
                { 0xfe30000000000000L, 0L, -1L, 0L }, { 0xfe28000000000000L, 0L, -1L, 0L },
                { 0xfe20000000000000L, 0L, -1L, 0L }, { 0xfe18000000000000L, 0L, -1L, 0L },
                { 0xfe10000000000000L, 0L, -1L, 0L }, { 0xfe08000000000000L, 0L, -1L, 0L },
                { 0xfe00000000000000L, 0L, -1L, 0L }, };
        for ( int i = 0; i < data.length; i++ ) {
            _INVERSE_2_2_HI[i] = data[i][0];
            _INVERSE_2_2_LO[i] = data[i][1];
            _INVERSE_2_2_EX[i] = data[i][2];
            _INVERSE_2_2_SGN[i] = data[i][3];
        }
    }

    static {
        long[][] data = { { 0xb1641795ce3ca97bL, 0x7af915300e517391L, -2L, 0x1L },
                { 0xabb3b8ba2ad362a4L, 0xd5b6506cc17a01f1L, -2L, 0x1L },
                { 0xa5f2fcabbbc506daL, 0x64ca4fb7ec323d73L, -2L, 0x1L },
                { 0xa0218434353f1de8L, 0x6093efa632530ac8L, -2L, 0x1L },
                { 0x9bb93315fec2d792L, 0xa7589fba0865790eL, -2L, 0x1L },
                { 0x95c981d5c4e924edL, 0x29404f5aa577d6b2L, -2L, 0x1L },
                { 0x914a0fde7bcb2d12L, 0x1429ed3aea197a5dL, -2L, 0x1L },
                { 0x8b3ae55d5d30701cL, 0xe63eab883717047eL, -2L, 0x1L },
                { 0x86a35abcd5ba5903L, 0xec81c3cbd925cccfL, -2L, 0x1L },
                { 0x8073622d6a80e634L, 0x6a97009015316071L, -2L, 0x1L },
                { 0xf7856e5ee2c9b290L, 0xc6f2a1b84190a7d7L, -3L, 0x1L },
                { 0xee0de5055f63eb06L, 0x98a33316df83ba57L, -3L, 0x1L },
                { 0xe47fbe3cd4d10d61L, 0x2ec0f797fdcd1257L, -3L, 0x1L },
                { 0xdada8cf47dad2374L, 0x4ffb833c3409ee78L, -3L, 0x1L },
                { 0xd11de0ff15ab18c9L, 0xb88d83d4cc613f20L, -3L, 0x1L },
                { 0xc74946f4436a0552L, 0xc4f5cb531201c0d1L, -3L, 0x1L },
                { 0xbd5c481086c848dfL, 0x1b596b5030403240L, -3L, 0x1L },
                { 0xb3566a13956a86f6L, 0xff1b1e1574d9fd54L, -3L, 0x1L },
                { 0xa9372f1d0da1bd17L, 0x200eb71e58cd36deL, -3L, 0x1L },
                { 0x9efe158766314e54L, 0xc571827efe892fc4L, -3L, 0x1L },
                { 0x981eb8c723fe97f4L, 0xa31c134fb702d432L, -3L, 0x1L },
                { 0x8db956a97b3d0148L, 0x3023472cd739f9deL, -3L, 0x1L },
                { 0x8338a89652cb7150L, 0xc647eb86498c2ce1L, -3L, 0x1L },
                { 0xf85186008b15330bL, 0xe64b8b775997898dL, -4L, 0x1L },
                { 0xe2f2a47ade3a18aeL, 0xb0bf7c0b0d8bb4edL, -4L, 0x1L },
                { 0xd49369d256ab1b28L, 0x5e9154e1d5263cd5L, -4L, 0x1L },
                { 0xbed3b36bd8966422L, 0x240644d7d9ed08afL, -4L, 0x1L },
                { 0xb032c549ba861d8eL, 0xf74e27bc92ce336aL, -4L, 0x1L },
                { 0xa176e5f5323781ddL, 0xd4f935996c92e8ccL, -4L, 0x1L },
                { 0x8b29b7751bd70743L, 0x12e0b9ee992f236dL, -4L, 0x1L },
                { 0xf85186008b15330bL, 0xe64b8b775997898dL, -5L, 0x1L },
                { 0xda16eb88cb8df614L, 0x68a63ecfb66e94acL, -5L, 0x1L },
                { 0xac52dd7e4726a463L, 0x547a963a91bb3012L, -5L, 0x1L },
                { 0x8d86cc491ecbfe16L, 0x51776453b7e8254dL, -5L, 0x1L },
                { 0xdcfe013d7c8cbfdeL, 0xa32dbac46f30cfffL, -6L, 0x1L },
                { 0x9e75221a352ba779L, 0xa52b7ea62f2198d0L, -6L, 0x1L },
                { 0xbee23afc0853b6e9L, 0x289782c20df350a1L, -7L, 0x1L }, { 0L, 0L, 127L, 0x1L }, { 0L, 0L, 127L, 0x1L },
                { 0xc122451c45155104L, 0xb16137f09a002b3cL, -7L, 0L },
                { 0xa195492cc06604e6L, 0x4a18dff7cdb4ae5cL, -6L, 0L },
                { 0xe31e9760a5578c63L, 0xf9eb2f284f31c35cL, -6L, 0L },
                { 0x8a4f1f2002d46756L, 0x5be970314148c645L, -5L, 0L },
                { 0xab8ae2601e777722L, 0x3b89d7f254f8d4dL, -5L, 0L },
                { 0xcd0c3dab9ef3dd1bL, 0x13b26f298aa357c8L, -5L, 0L },
                { 0xe65b9e6eed965c36L, 0xe09f5fe2058d6006L, -5L, 0L },
                { 0x842cc5acf1d03445L, 0x1fecdfa819b96098L, -4L, 0L },
                { 0x9103dae3c2a4ec67L, 0xe0863df62ab5671aL, -4L, 0L },
                { 0xa242f01edefd6a37L, 0x469355b78dc796e3L, -4L, 0L },
                { 0xaf4ad26cbc8e5be7L, 0xe8b8b88a14ff0ceL, -4L, 0L },
                { 0xc0cbf17a071f80dcL, 0xf96ffdf76a147cccL, -4L, 0L },
                { 0xce06196a692a41fbL, 0xbe3ccc15326765fL, -4L, 0L },
                { 0xdb56446d6ad8deffL, 0xa8112e35a60e6375L, -4L, 0L },
                { 0xe8bcbc410c9b219dL, 0xaf7df76ad29e5b60L, -4L, 0L },
                { 0xf639cc185088fe5dL, 0x4066e87f2c0f7340L, -4L, 0L },
                { 0x842cc5acf1d03445L, 0x1fecdfa819b96098L, -3L, 0L },
                { 0x8b064012593d85a5L, 0x52013c7a80ad089bL, -3L, 0L },
                { 0x91eb89524e100d23L, 0x8fd3df5c52d67e7bL, -3L, 0L },
                { 0x98dcca69d27c263bL, 0x8e94203f336fc8c5L, -3L, 0L },
                { 0x9fda2d2cc9465c4fL, 0x32b9565f5355182L, -3L, 0L },
                { 0xa6e3dc4bde0e3cdbL, 0x570ff874170d2a9L, -3L, 0L },
                { 0xab9be6480c66ea9eL, 0x9ae21fd871b8d27cL, -3L, 0L },
                { 0xb2ba75f46099cf8bL, 0x2c3c2e77904afa78L, -3L, 0L },
                { 0xb9e5c83a7e8a655bL, 0xcbffe9661fe72421L, -3L, 0L },
                { 0xc11e0b2a8d1e0ddbL, 0x9a631e830fd30904L, -3L, 0L },
                { 0xc8636dcfe5e6ca0aL, 0x88e72835b3292d50L, -3L, 0L },
                { 0xcd43bc6f5d51c3e8L, 0xfbfb0e3f0fd23074L, -3L, 0L },
                { 0xd49f69e456cf1b79L, 0x5f53bd2e406e66e7L, -3L, 0L },
                { 0xdc08b985c11e9068L, 0x3b9cd767c3b1ac53L, -3L, 0L },
                { 0xe1014558bfcda3e2L, 0x35470a74be1230ecL, -3L, 0L },
                { 0xe881bf932af3dac0L, 0xc524848e3443e040L, -3L, 0L },
                { 0xed89ed86a44a01aaL, 0x11d49f96cb88317bL, -3L, 0L },
                { 0xf52224f82557a459L, 0x8dcca8d7f17fa2a9L, -3L, 0L },
                { 0xfa3a589a6f9146d8L, 0x388212895529a6fbL, -3L, 0L },
                { 0x80f572b1363487b9L, 0xf5bd0b5b3479d5f4L, -2L, 0L },
                { 0x8389c3026ac3139bL, 0x62dda9d2270fa1f4L, -2L, 0L },
                { 0x86216b3b0b17188bL, 0x163ceae88f720f1eL, -2L, 0L },
                { 0x8a0b3f79b3bc180fL, 0x49b55ea7d3730d7L, -2L, 0L },
                { 0x8cab69dcde17d2f7L, 0x3ad1aa142b94f16aL, -2L, 0L },
                { 0x8f4f0b3c44cfa2a2L, 0x586e9343c9cfdbacL, -2L, 0L },
                { 0x934b1089a6dc93c1L, 0xdf5bb3b60554e152L, -2L, 0L },
                { 0x95f783e6e49a9cfaL, 0x4a5004f3ef063313L, -2L, 0L },
                { 0x98a78f0e9ae71d85L, 0x2cdec34784707839L, -2L, 0L },
                { 0x9b5b3bb5f088b766L, 0xd878bbe3d392be25L, -2L, 0L },
                { 0x9e1293b9998c1daaL, 0x5b035eae273a855fL, -2L, 0L },
                { 0xa22c8f029cfa45a9L, 0xdb5b709e0b69e773L, -2L, 0L },
                { 0xa4ed3f9de620f666L, 0x9b5e973353638c11L, -2L, 0L },
                { 0xa7b1bf5dd4c07d4eL, 0x699db68db75e9a7fL, -2L, 0L },
                { 0xaa7a18dbdf0d44aaL, 0x604884a8dd76d08aL, -2L, 0L },
                { 0xad4656ddf6fd070cL, 0x9ea10260fe452ba2L, -2L, 0L },
                { 0xb0168457848f5f48L, 0xbb6f9fb246068d52L, -2L, 0L },
                { 0xb2eaac6a67005513L, 0xf4b716f6fec8156bL, -2L, 0L }, };
        for ( int i = 0; i < data.length; i++ ) {
            _LOG_INV_2_1_HI[i] = data[i][0];
            _LOG_INV_2_1_LO[i] = data[i][1];
            _LOG_INV_2_1_EX[i] = data[i][2];
            _LOG_INV_2_1_SGN[i] = data[i][3];
        }
    }

    static {
        long[][] data = { { 0xff015358833c47e1L, 0xbb481c8ee141695aL, -8L, 0x1L },
                { 0xfb0933b732572a6dL, 0x214cca3dd1d4796aL, -8L, 0x1L },
                { 0xf710f492711d9d26L, 0xfbc7b38b17b2019L, -8L, 0x1L },
                { 0xf31895e84b1a6be6L, 0xb76782b9e88c84cbL, -8L, 0x1L },
                { 0xef2017b6cba9cf9aL, 0x2dc85881664025b5L, -8L, 0x1L },
                { 0xeb2779fbfdf96874L, 0xce4ab4e678d0ed03L, -8L, 0x1L },
                { 0xe72ebcb5ed08382bL, 0xb60585f4c4bb6062L, -8L, 0x1L },
                { 0xe335dfe2a3a69c2bL, 0x59bcffe9d5650564L, -8L, 0x1L },
                { 0xdf3ce3802c7647cdL, 0x3602021fa93b1e18L, -8L, 0x1L },
                { 0xdb43c78c91ea3e8cL, 0x9944002534d09b3dL, -8L, 0x1L },
                { 0xd74a8c05de46ce3aL, 0x87aa95782311a277L, -8L, 0x1L },
                { 0xd35130ea1ba18930L, 0xb88be10313a1303dL, -8L, 0x1L },
                { 0xcf57b63753e14083L, 0xad54bc31433dddbaL, -8L, 0x1L },
                { 0xcb5e1beb90bdfe33L, 0xe1b7d813e3f825e1L, -8L, 0x1L },
                { 0xc7646204dbc0ff5eL, 0x14f8c1be7370f219L, -8L, 0x1L },
                { 0xc36a88813e44ae6aL, 0xac27c5a6139cd30cL, -8L, 0x1L },
                { 0xbf708f5ec1749d3cL, 0x2d23a0744e00f594L, -8L, 0x1L },
                { 0xbb76769b6e4d7f5cL, 0xd235e25fb9644c31L, -8L, 0x1L },
                { 0xb77c3e354d9d242bL, 0x361ee0bcb5db0449L, -8L, 0x1L },
                { 0xb381e62a68027106L, 0x18660815da3d7963L, -8L, 0x1L },
                { 0xaf876e78c5ed5b77L, 0x39c357b6bfdf81b5L, -8L, 0x1L },
                { 0xab8cd71e6f9ee35dL, 0x5076c62c951204f6L, -8L, 0x1L },
                { 0xa79220196d290d15L, 0x146244d643f7fa2bL, -8L, 0x1L },
                { 0xa3974967c66edba1L, 0x62bb0f3208d9a1bbL, -8L, 0x1L },
                { 0x9f9c530783244ad2L, 0x7926e92808bd580dL, -8L, 0x1L },
                { 0x9ba13cf6aace496cL, 0x4819e620d5fcc068L, -8L, 0x1L },
                { 0x97a6073344c2b34bL, 0xdc494943d427214eL, -8L, 0x1L },
                { 0x93aab1bb58284b8bL, 0xdf0805c4161e404cL, -8L, 0x1L },
                { 0x8faf3c8cebf6b6a8L, 0x2d615caaa0514c3cL, -8L, 0x1L },
                { 0x8bb3a7a606f674a0L, 0x85c60c12eca0aedcL, -8L, 0x1L },
                { 0x87b7f304afc0db1aL, 0x4c207a522524f8deL, -8L, 0x1L },
                { 0x83bc1ea6ecc00f81L, 0x64243e02c6215a4fL, -8L, 0x1L },
                { 0xff805515885e0250L, 0x435ab4da6a5bb48dL, -9L, 0x1L },
                { 0xf7882d5c7832c6ccL, 0x9e06fc84b6ea5e24L, -9L, 0x1L },
                { 0xef8fc61eb4b74f6eL, 0x91ab122ee427cfb5L, -9L, 0x1L },
                { 0xe7971f584945efaeL, 0x5f832513e3211643L, -9L, 0x1L },
                { 0xdf9e390540da5fbeL, 0x5e7b48cfeeb85aa8L, -9L, 0x1L },
                { 0xd7a51321a611b0c1L, 0xb36a9f58eb4ccd08L, -9L, 0x1L },
                { 0xcfabada9832a4101L, 0x3360751e43c7af35L, -9L, 0x1L },
                { 0xc7b20898e203b01eL, 0x6fab78aca91193cbL, -9L, 0x1L },
                { 0xbfb823ebcc1ed344L, 0xeb432409cffdad8dL, -9L, 0x1L },
                { 0xb7bdff9e4a9da959L, 0x793b5acf3a336462L, -9L, 0x1L },
                { 0xafc39bac66434f27L, 0xc3ea2cd93f316b34L, -9L, 0x1L },
                { 0xa7c8f8122773f38dL, 0xfc679a28e9d9f212L, -9L, 0x1L },
                { 0x9fce14cb9634cba6L, 0xb20f215bd3b58c61L, -9L, 0x1L },
                { 0x97d2f1d4ba2c06f0L, 0xd1aacedcefe9d377L, -9L, 0x1L },
                { 0x8fd78f299aa0c375L, 0xcbef6fac33691e95L, -9L, 0x1L },
                { 0x87dbecc63e7b01edL, 0xe2f1775134c8da75L, -9L, 0x1L },
                { 0xffc0154d588733c5L, 0x3c742a7c76356396L, -10L, 0x1L },
                { 0xefc7d18dd4485b9eL, 0xca47c52b7d7ffce2L, -10L, 0x1L },
                { 0xdfcf0e45fbce3e80L, 0x7e4cfbd830393b88L, -10L, 0x1L },
                { 0xcfd5cb6dd9ef05ddL, 0x7370ae83f9e72748L, -10L, 0x1L },
                { 0xbfdc08fd78c229b9L, 0xe6dbb624f9739782L, -10L, 0x1L },
                { 0xafe1c6ece1a058ddL, 0x97fa2fd0c9dc723eL, -10L, 0x1L },
                { 0x9fe705341d236102L, 0x7199cd06ae5d39b3L, -10L, 0x1L },
                { 0x8febc3cb332616ffL, 0x7b6d1248c3e1fd40L, -10L, 0x1L },
                { 0xffe0055455887de0L, 0x26828c92649a3a39L, -11L, 0x1L },
                { 0xdfe7839214b4e8aeL, 0xda6959f7f0e01bf0L, -11L, 0x1L },
                { 0xbfee023faf0c2480L, 0xb47505bfa5a03b06L, -11L, 0x1L },
                { 0x9ff3814d2e4a36b2L, 0xa8740b91c95df537L, -11L, 0x1L },
                { 0xfff0015535588833L, 0x3c56c598c659c2a3L, -12L, 0x1L },
                { 0xbff7008ff5e0c257L, 0x379eba7e6465ff63L, -12L, 0x1L },
                { 0xfff8005551558885L, 0xde026e271ee0549dL, -13L, 0x1L }, { 0L, 0L, 127L, 0x1L },
                { 0L, 0L, 127L, 0x1L }, { 0xc004802401440c26L, 0xdfeb485085f6f454L, -13L, 0L },
                { 0xa00640535a37a37aL, 0x6bc1e20eac8448b4L, -12L, 0L },
                { 0xe00c40e4bd6e4efdL, 0xc72446cc1bf728bdL, -12L, 0L },
                { 0x900a20f319a3e273L, 0x569b26aaa485ea5cL, -11L, 0L },
                { 0xb00f21bbe3e388eeL, 0x5f69768284463b9bL, -11L, 0L },
                { 0xd01522dcc4f87991L, 0x14d9d76196d8043aL, -11L, 0L },
                { 0xf01c2465c5e61b6fL, 0x661e135f49a47c40L, -11L, 0L },
                { 0x881213337898871eL, 0x9a31ba0cbc030353L, -10L, 0L },
                { 0x98169478296fad41L, 0x7ad1e9c315328f7eL, -10L, 0L },
                { 0xa81b9608fc3c50ecL, 0xf105b66ec4703edeL, -10L, 0L },
                { 0xb82117edf8832797L, 0xd6aef30cd312169aL, -10L, 0L },
                { 0xc8271a2f2689e388L, 0xe6e2acf8f4d4c24aL, -10L, 0L },
                { 0xd82d9cd48f574c00L, 0x28bb3cd9f2a65fb5L, -10L, 0L },
                { 0xe8349fe63cb35564L, 0x224a96f5a7471c46L, -10L, 0L },
                { 0xf83c236c39273972L, 0xd462b63756c87e80L, -10L, 0L },
                { 0x842213b747fec7bbL, 0x3ff51287882500edL, -9L, 0L },
                { 0x8c2655faa6a1323fL, 0x1ab9679b55f78a6bL, -9L, 0L },
                { 0x942ad8843ee1a9cdL, 0x17e4b7ac6c600cb4L, -9L, 0L },
                { 0x9c2f9b581787cf0dL, 0xfd1a09c848e3950eL, -9L, 0L },
                { 0xa4349e7a37bc21edL, 0x318b2ddd9d0a33b4L, -9L, 0L },
                { 0xac39e1eea7080dbcL, 0x9dd91e52c79fd070L, -9L, 0L },
                { 0xb43f65b96d55f55aL, 0x72de1d99ce252efdL, -9L, 0L },
                { 0xbc4529de92f13f58L, 0xd7bd1d62ef25480dL, -9L, 0L },
                { 0xc44b2e6220866227L, 0x7f921124f1ecb59eL, -9L, 0L },
                { 0xcc5173481f22f03fL, 0x271ee1cd6d5cdf9eL, -9L, 0L },
                { 0xd457f8949835a44eL, 0xfad0cc8b5faea8ccL, -9L, 0L },
                { 0xdc5ebe4b958e6d6bL, 0xe57a0acb9d5cd4dfL, -9L, 0L },
                { 0xe465c471215e7b41L, 0xc81bb5a8d789f444L, -9L, 0L },
                { 0xec6d0b0946384a46L, 0x9b1beb40437575f5L, -9L, 0L },
                { 0xf47492180f0fafefL, 0x7944509046652d99L, -9L, 0L },
                { 0xfc7c59a18739e6e7L, 0x94e51ebff53a2f15L, -9L, 0L },
                { 0x824230d4dd36cda4L, 0x8bbc7f765b13ebbeL, -8L, 0L },
                { 0x8646551a5a617b6bL, 0xf61305ef7390939cL, -8L, 0L },
                { 0x8a4a99a34159d69fL, 0x3abc32a78afd4b7bL, -8L, 0L },
                { 0x8e4efe71988d8426L, 0x17596a598cb29436L, -8L, 0L },
                { 0x92538387669afa1bL, 0x1c890bee9a9d743cL, -8L, 0L },
                { 0x965828e6b25185ecL, 0xeaafbd07b543145dL, -8L, 0L },
                { 0x9a5cee9182b15280L, 0x6517bc4112d64b17L, -8L, 0L },
                { 0x9e61d489deeb6e53L, 0xdb94a1dfd653d3a5L, -8L, 0L },
                { 0xa266dad1ce61d1a3L, 0x2ada01ce7ed36080L, -8L, 0L },
                { 0xa66c016b58a7648cL, 0xd3b36c029ea7bb5dL, -8L, 0L },
                { 0xaa71485885800538L, 0x94c529f32403828L, -8L, 0L },
                { 0xae76af9b5ce08dfbL, 0xb6b6676248bba139L, -8L, 0L },
                { 0xb27c3735e6eedb86L, 0x7bdd0c2a9c7a679aL, -8L, 0L },
                { 0xb47f0724b1906935L, 0x23deb274e953a259L, -8L, 0L },
                { 0xb884bf4697559ffaL, 0xdae7e343fa859415L, -8L, 0L },
                { 0xbc8a97c544fdd5ebL, 0x17759bff5c717993L, -8L, 0L },
                { 0xc09090a2c35aa070L, 0x52e7e4dde874daceL, -8L, 0L },
                { 0xc496a9e11b6eb30cL, 0xa88971f8277a4d11L, -8L, 0L },
                { 0xc89ce382566de587L, 0x269de85f0df92588L, -8L, 0L },
                { 0xcca33d887dbd3a1aL, 0x180d255422c3377cL, -8L, 0L },
                { 0xd0a9b7f59af2e3a2L, 0x46da70925ee85c05L, -8L, 0L },
                { 0xd4b052cbb7d64bcfL, 0x37968ceafaf7b453L, -8L, 0L },
                { 0xd8b70e0cde601954L, 0x5dfba4cfdd38a059L, -8L, 0L },
                { 0xdcbde9bb18ba361bL, 0x4ae21abe75d5a19bL, -8L, 0L },
                { 0xe0c4e5d8713fd576L, 0xd3bd4fd98a1e6fe5L, -8L, 0L },
                { 0xe4cc0266f27d7a57L, 0x33cf7d5ebfb93ad3L, -8L, 0L },
                { 0xe8d33f68a730fd7fL, 0x2743c805a4928087L, -8L, 0L },
                { 0xecda9cdf9a4993baL, 0x5dbeb9795455a5L, -8L, 0L },
                { 0xf0e21acdd6e7d412L, 0xb6ed80852ae6fd63L, -8L, 0L },
                { 0xf4e9b935685dbe0bL, 0xf237cff1acb306b3L, -8L, 0L },
                { 0xf8f178185a2ebfd9L, 0xd81648249cece4cL, -8L, 0L },
                { 0xfcf95778b80fbc98L, 0x176cd56887ac7fe9L, -8L, 0L },
                { 0x8080abac46f38946L, 0x662d417ced007a46L, -7L, 0L }, };
        for ( int i = 0; i < data.length; i++ ) {
            _LOG_INV_2_2_HI[i] = data[i][0];
            _LOG_INV_2_2_LO[i] = data[i][1];
            _LOG_INV_2_2_EX[i] = data[i][2];
            _LOG_INV_2_2_SGN[i] = data[i][3];
        }
    }

    static {
        long[][] data = { { 0x8000000000000000L, 0L, 0L, 0L }, { 0x8164d1f3bc030773L, 0x7be56527bd14def5L, 0L, 0L },
                { 0x82cd8698ac2ba1d7L, 0x3e2a475b46520bffL, 0L, 0L },
                { 0x843a28c3acde4046L, 0x1af92eca13fd1582L, 0L, 0L },
                { 0x85aac367cc487b14L, 0xc5c95b8c2154c1b2L, 0L, 0L },
                { 0x871f61969e8d1010L, 0x3a1727c57b52a956L, 0L, 0L },
                { 0x88980e8092da8527L, 0x5df8d76c98c67563L, 0L, 0L },
                { 0x8a14d575496efd9aL, 0x80ca1d92c3680c2L, 0L, 0L },
                { 0x8b95c1e3ea8bd6e6L, 0xfbe4628758a53c90L, 0L, 0L },
                { 0x8d1adf5b7e5ba9e5L, 0xb4c7b4968e41ad36L, 0L, 0L },
                { 0x8ea4398b45cd53c0L, 0x2dc0144c8783d4c6L, 0L, 0L },
                { 0x9031dc431466b1dcL, 0x775814a8494e87e2L, 0L, 0L },
                { 0x91c3d373ab11c336L, 0xfd6d8e0ae5ac9d8L, 0L, 0L },
                { 0x935a2b2f13e6e92bL, 0xd339940e9d924ee7L, 0L, 0L },
                { 0x94f4efa8fef70961L, 0x2e8afad12551de54L, 0L, 0L },
                { 0x96942d3720185a00L, 0x48ea9b683a9c22c5L, 0L, 0L },
                { 0x9837f0518db8a96fL, 0x46ad23182e42f6f6L, 0L, 0L },
                { 0x99e0459320b7fa64L, 0xe43086cb34b5fcafL, 0L, 0L },
                { 0x9b8d39b9d54e5538L, 0xa2a817a2a3cc3f1fL, 0L, 0L },
                { 0x9d3ed9a72cffb750L, 0xde494cf050e99b0bL, 0L, 0L },
                { 0x9ef5326091a111adL, 0xa0911f09ebb9fdd1L, 0L, 0L },
                { 0xa0b0510fb9714fc2L, 0x192dc79edb0fd9a9L, 0L, 0L },
                { 0xa27043030c496818L, 0x9b7a04ef80cfdea8L, 0L, 0L },
                { 0xa43515ae09e6809eL, 0xd1db4831781e1efL, 0L, 0L },
                { 0xa5fed6a9b15138eaL, 0x1cbd7f621710701bL, 0L, 0L },
                { 0xa7cd93b4e9653569L, 0x9ec5b4d5039f72afL, 0L, 0L },
                { 0xa9a15ab4ea7c0ef8L, 0x541e24ec3531fa73L, 0L, 0L },
                { 0xab7a39b5a93ed337L, 0x658023b2759e0079L, 0L, 0L },
                { 0xad583eea42a14ac6L, 0x4980a8c8f59a2ec4L, 0L, 0L },
                { 0xaf3b78ad690a4374L, 0xdf26101ccbb35033L, 0L, 0L },
                { 0xb123f581d2ac258fL, 0x87d037e96d215d8eL, 0L, 0L },
                { 0xb311c412a9112489L, 0x3ecf14dc798a519cL, 0L, 0L },
                { 0xb504f333f9de6484L, 0x597d89b3754abe9fL, 0L, 0L },
                { 0xb6fd91e328d17791L, 0x7165f0ddd541a5aL, 0L, 0L },
                { 0xb8fbaf4762fb9ee9L, 0x1b879778566b65a2L, 0L, 0L },
                { 0xbaff5ab2133e45fbL, 0x74d519d24593838cL, 0L, 0L },
                { 0xbd08a39f580c36beL, 0xa8811fb66d0faf7aL, 0L, 0L },
                { 0xbf1799b67a731082L, 0xe815d0abcbf0b851L, 0L, 0L },
                { 0xc12c4cca66709456L, 0x7c457d59a50087b5L, 0L, 0L },
                { 0xc346ccda24976407L, 0x20ec856128b83a42L, 0L, 0L },
                { 0xc5672a115506daddL, 0x3e2ad0c964dd9f37L, 0L, 0L },
                { 0xc78d74c8abb9b15cL, 0xc13a2e3976c0277eL, 0L, 0L },
                { 0xc9b9bd866e2f27a2L, 0x80e1f92a0511697eL, 0L, 0L },
                { 0xcbec14fef2727c5cL, 0xf4907c8f45ebf6ddL, 0L, 0L },
                { 0xce248c151f8480e3L, 0xe235838f95f2c6edL, 0L, 0L },
                { 0xd06333daef2b2594L, 0xd6d45c6559a4d502L, 0L, 0L },
                { 0xd2a81d91f12ae45aL, 0x12248e57c3de4028L, 0L, 0L },
                { 0xd4f35aabcfedfa1fL, 0x5921deffa6262c5bL, 0L, 0L },
                { 0xd744fccad69d6af4L, 0x39a68bb9902d3fdeL, 0L, 0L },
                { 0xd99d15c278afd7b5L, 0xfe873deca3e12bacL, 0L, 0L },
                { 0xdbfbb797daf23755L, 0x3d840d5a9e29aa64L, 0L, 0L },
                { 0xde60f4825e0e9123L, 0xdd07a2d9e8466859L, 0L, 0L },
                { 0xe0ccdeec2a94e111L, 0x65895048dd333caL, 0L, 0L },
                { 0xe33f8972be8a5a51L, 0x9bfe90795980eedL, 0L, 0L },
                { 0xe5b906e77c8348a8L, 0x1e5e8f4a4edbb0edL, 0L, 0L },
                { 0xe8396a503c4bdc68L, 0x791790d0ac70c7deL, 0L, 0L },
                { 0xeac0c6e7dd24392eL, 0xd02d75b3706e54fbL, 0L, 0L },
                { 0xed4f301ed9942b84L, 0x600d2db6a64bfb12L, 0L, 0L },
                { 0xefe4b99bdcdaf5cbL, 0x46561cf6948db913L, 0L, 0L },
                { 0xf281773c59ffb139L, 0xe8980a9cc8f47a4bL, 0L, 0L },
                { 0xf5257d152486cc2cL, 0x7b9d0c7aed980fc3L, 0L, 0L },
                { 0xf7d0df730ad13bb8L, 0xfe90d496d60fb6ebL, 0L, 0L },
                { 0xfa83b2db722a033aL, 0x7c25bb14315d7fcdL, 0L, 0L },
                { 0xfd3e0c0cf486c174L, 0x853f3a5931e0ee03L, 0L, 0L }, };
        for ( int i = 0; i < data.length; i++ ) {
            _T1_2_HI[i] = data[i][0];
            _T1_2_LO[i] = data[i][1];
            _T1_2_EX[i] = data[i][2];
            _T1_2_SGN[i] = data[i][3];
        }
    }

    static {
        long[][] data = { { 0x8000000000000000L, 0L, 0L, 0L }, { 0x80058baf7fee3b5dL, 0x1c718b38e549cb93L, 0L, 0L },
                { 0x800b179c82028fd0L, 0x945e54e2ae18f2f0L, 0L, 0L },
                { 0x8010a3c708e73282L, 0x2b96d62d51c15a07L, 0L, 0L },
                { 0x8016302f17467628L, 0x3690dfe44d11d008L, 0L, 0L },
                { 0x801bbcd4afcacb08L, 0xe23a986bd3e626f0L, 0L, 0L },
                { 0x802149b7d51ebefbL, 0x7bdbadbc888aeb29L, 0L, 0L },
                { 0x8026d6d889ecfd69L, 0xb904bbfb40d3a2b7L, 0L, 0L },
                { 0x802c6436d0e04f50L, 0xff8ce94a6797b3ceL, 0L, 0L },
                { 0x8031f1d2aca39b43L, 0xad9db772901d96b6L, 0L, 0L },
                { 0x80377fac1fe1e56aL, 0x61cd0bffd7cfc683L, 0L, 0L },
                { 0x803d0dc32d464f85L, 0x43456f71b96affd4L, 0L, 0L },
                { 0x80429c17d77c18edL, 0x49fc841afba9c3c6L, 0L, 0L },
                { 0x80482aaa212e9e95L, 0x86f7b54f6c45c85eL, 0L, 0L },
                { 0x804db97a0d095b0cL, 0x6c9f1f7d1efcfe68L, 0L, 0L },
                { 0x805348879db7e67dL, 0x171eb1ceef1d1f28L, 0L, 0L },
                { 0x8058d7d2d5e5f6b0L, 0x94d589f608ee4aa2L, 0L, 0L },
                { 0x805e675bb83f5f0fL, 0x2ed38ab8472b2144L, 0L, 0L },
                { 0x8063f722477010a1L, 0xb1652de1378af1a1L, 0L, 0L },
                { 0x8069872686241a12L, 0xb4ad9233a0390cadL, 0L, 0L },
                { 0x806f17687707a7afL, 0xe54ec5f966eb1872L, 0L, 0L },
                { 0x8074a7e81cc7036bL, 0x4d204ecfc11f4aabL, 0L, 0L },
                { 0x807a38a57a0e94dcL, 0x9bf3ef4d9be2d1e4L, 0L, 0L },
                { 0x807fc9a0918ae142L, 0x7068ab2230585d13L, 0L, 0L },
                { 0x80855ad965e88b83L, 0xa0cc0a49c10ea66bL, 0L, 0L },
                { 0x808aec4ff9d45430L, 0x84099bf6830f2768L, 0L, 0L },
                { 0x80907e044ffb1984L, 0x3aa8b9cbbc65a8abL, 0L, 0L },
                { 0x80960ff66b09d765L, 0xf7d88c0928ba3947L, 0L, 0L },
                { 0x809ba2264dada76aL, 0x4a8a4f44bb703db6L, 0L, 0L },
                { 0x80a13493fa93c0d4L, 0x6699dc50dd96b774L, 0L, 0L },
                { 0x80a6c73f74697897L, 0x6e0472ed4ccfa2e0L, 0L, 0L },
                { 0x80ac5a28bddc4157L, 0xba2dc7e0c72e51baL, 0L, 0L },
                { 0x80b1ed4fd999ab6cL, 0x25335719b6e6fd20L, 0L, 0L },
                { 0x80b780b4ca4f64dfL, 0x534dfa7417846aa4L, 0L, 0L },
                { 0x80bd145792ab3970L, 0xfc41c5c2d5336cccL, 0L, 0L },
                { 0x80c2a838355b1297L, 0x34dc28baed8f3fdeL, 0L, 0L },
                { 0x80c83c56b50cf77fL, 0xb880575ea03548c1L, 0L, 0L },
                { 0x80cdd0b3146f0d11L, 0x32c1f98704428c71L, 0L, 0L },
                { 0x80d3654d562f95ecL, 0x890e222a5eb95372L, 0L, 0L },
                { 0x80d8fa257cfcf26eL, 0x24628efd9ca9d59bL, 0L, 0L },
                { 0x80de8f3b8b85a0afL, 0x3b13310f5ad57fb1L, 0L, 0L },
                { 0x80e4248f84783c87L, 0x1a9dfefaeb616564L, 0L, 0L },
                { 0x80e9ba216a837f8cL, 0x718d1151d109bf98L, 0L, 0L },
                { 0x80ef4ff140564116L, 0x996709da2e25f04cL, 0L, 0L },
                { 0x80f4e5ff089f763eL, 0xe0adc640acaa6b0bL, 0L, 0L },
                { 0x80fa7c4ac60e31e1L, 0xd4eb5edc6b341283L, 0L, 0L },
                { 0x810012d47b51a4a0L, 0x8ccd7223820719e3L, 0L, 0L },
                { 0x8105a99c2b191ce1L, 0xf24ebd6eb9ca4292L, 0L, 0L },
                { 0x810b40a1d81406d4L, 0xcef03ab14a66550L, 0L, 0L },
                { 0x8110d7e584f1ec6dL, 0x4bf94297d1519822L, 0L, 0L },
                { 0x81166f673462756dL, 0xd0d8372f966cf15eL, 0L, 0L },
                { 0x811c0726e9156760L, 0xb97931db7b7be2ecL, 0L, 0L },
                { 0x81219f24a5baa59dL, 0x6abd3b0eab9c7048L, 0L, 0L },
                { 0x812737606d023148L, 0xdaf888e96508151aL, 0L, 0L },
                { 0x812ccfda419c2956L, 0xdc8046821f46122eL, 0L, 0L },
                { 0x813268922638ca8bL, 0x6846ad73a8d9027fL, 0L, 0L },
                { 0x813801881d886f7bL, 0xe885724f14131287L, 0L, 0L },
                { 0x813d9abc2a3b9090L, 0x83768490519df895L, 0L, 0L },
                { 0x8143342e4f02c405L, 0x661b22b45e25de18L, 0L, 0L },
                { 0x8148cdde8e8ebdecL, 0xf11430fef78c6eeL, 0L, 0L },
                { 0x814e67cceb90502cL, 0x99775205944eadc4L, 0L, 0L },
                { 0x815401f968b86a87L, 0x7de463a40d18261L, 0L, 0L },
                { 0x81599c6408b81a94L, 0x8f4a0b6748df7960L, 0L, 0L },
                { 0x815f370cce408bc8L, 0xe2404468cfe5ab9fL, 0L, 0L }, };
        for ( int i = 0; i < data.length; i++ ) {
            _T2_2_HI[i] = data[i][0];
            _T2_2_LO[i] = data[i][1];
            _T2_2_EX[i] = data[i][2];
            _T2_2_SGN[i] = data[i][3];
        }
    }

    static {
        long[][] data = { { 0xe38e3954a09e560eL, 0L, -4L, 0L }, { 0x800000399d09d767L, 0L, -3L, 0x1L },
                { 0x9249249249248676L, 0L, -3L, 0L }, { 0xaaaaaaaaaaaa9fddL, 0L, -3L, 0x1L },
                { 0xccccccccccccccccL, 0xcccdc5fe0ef93b8dL, -3L, 0L },
                { 0x8000000000000000L, 0x600135b960d8L, -2L, 0x1L },
                { 0xaaaaaaaaaaaaaaaaL, 0xaaaaaaaaaaa77b5eL, -2L, 0L },
                { 0xffffffffffffffffL, 0xfffffffffffe33caL, -2L, 0x1L }, { 0x8000000000000000L, 0L, 0L, 0L }, };
        for ( int i = 0; i < data.length; i++ ) {
            _P_2_HI[i] = data[i][0];
            _P_2_LO[i] = data[i][1];
            _P_2_EX[i] = data[i][2];
            _P_2_SGN[i] = data[i][3];
        }
    }

    static {
        long[][] data = { { 0xd00d00cd98416862L, 0L, -13L, 0L }, { 0xb60b60b932146a54L, 0L, -10L, 0L },
                { 0x8888888888888897L, 0L, -7L, 0L }, { 0xaaaaaaaaaaaaaaa3L, 0L, -5L, 0L },
                { 0xaaaaaaaaaaaaaaaaL, 0xaaaaaa6a1e0776aeL, -3L, 0L }, { 0x8000000000000000L, 0xc06f3cd29L, -1L, 0L },
                { 0x8000000000000000L, 0x88L, 0L, 0L }, { 0xffffffffffffffffL, 0xffffffffffffffd0L, -1L, 0L }, };
        for ( int i = 0; i < data.length; i++ ) {
            _Q_2_HI[i] = data[i][0];
            _Q_2_LO[i] = data[i][1];
            _Q_2_EX[i] = data[i][2];
            _Q_2_SGN[i] = data[i][3];
        }
    }
    private PowKernel() {
    }

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
        if ( Long.compareUnsigned(xb, 0x7ff0000000000000L) >= 0
                || Long.compareUnsigned(yb, 0x7ff0000000000000L) >= 0 ) {
            // x is NaN
            if ( Double.isNaN(x) ) {
                if ( y == 0.0 && !isSignaling(xb) )
                    return 1.0;
                return x + x;
            }
            // y is NaN
            if ( Double.isNaN(y) ) {
                if ( x == 1.0 && !isSignaling(yb) )
                    return 1.0;
                return y + y;
            }

            // x = +inf
            if ( xb == 0x7ff0000000000000L ) {
                if ( y == 0.0 )
                    return 1.0;
                if ( y < 0.0 )
                    return 0.0;
                if ( y > 0.0 )
                    return Double.POSITIVE_INFINITY;
            }
            // x = -inf
            if ( xb == 0xfff0000000000000L ) {
                final boolean yIsOddInt = isInt(y) && !isInt(y * 0.5);
                if ( yIsOddInt ) {
                    if ( y < 0.0 )
                        return -0.0;
                    return Double.NEGATIVE_INFINITY;
                }
                if ( y < 0.0 )
                    return 0.0;
                if ( y > 0.0 )
                    return Double.POSITIVE_INFINITY;
            }

            // y = +inf
            if ( yb == 0x7ff0000000000000L ) {
                if ( x == 0.0 )
                    return 0.0;
                if ( x == -1.0 || x == 1.0 )
                    return 1.0;
                if ( -1.0 < x && x < 1.0 )
                    return 0.0;
                return Double.POSITIVE_INFINITY;
            }
            // y = -inf
            if ( yb == 0xfff0000000000000L ) {
                if ( x == 0.0 )
                    return Double.POSITIVE_INFINITY;
                if ( x == -1.0 || x == 1.0 )
                    return 1.0;
                if ( -1.0 < x && x < 1.0 )
                    return Double.POSITIVE_INFINITY;
                return 0.0;
            }
        }
        // From here, x and y are finite.

        // ============================================================
        // Negative or zero base — pow.c:1615-1702
        // ============================================================
        double s = 1.0; // sign of result; -1 only when x<0 finite and y is odd integer

        if ( x <= 0.0 ) {
            if ( y == 0.0 )
                return 1.0;

            if ( xb == 0x0L ) {
                final boolean yIsOddInt = isInt(y) && !isInt(y * 0.5);
                if ( yIsOddInt ) {
                    return (y < 0.0) ? Double.POSITIVE_INFINITY : 0.0;
                }
                if ( y > 0.0 )
                    return 0.0;
                return Double.POSITIVE_INFINITY;
            }
            if ( xb == 0x8000000000000000L ) {
                final boolean yIsOddInt = isInt(y) && !isInt(y * 0.5);
                if ( yIsOddInt ) {
                    return (y < 0.0) ? Double.NEGATIVE_INFINITY : -0.0;
                }
                if ( y > 0.0 )
                    return 0.0;
                return Double.POSITIVE_INFINITY;
            }
            // x < 0 finite
            if ( !isInt(y) ) {
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
        if ( ey < 0x36 || ey >= 0x7f5 ) {
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

        if ( res_min == res_max ) {
            return res_max;
        }

        // Easy short-circuits — pow.c:1772-1793.
        if ( y == 1.0 )
            return s * x;
        if ( y == 2.0 )
            return x * x;
        if ( y == 0.5 )
            return Math.sqrt(x);
        if ( y == 0.0 )
            return 1.0;

        // ============================================================
        // Stage 2 accurate path — pow.c:1799-1876 (ENABLE_ZIV2 branch)
        // ============================================================
        // x and y are converted to dint64; |x| is used (X.sgn := 0).
        final long[] X = new long[4];
        final long[] Y = new long[4];
        dintFromD(X, x);
        dintFromD(Y, y);
        X[3] = 0L; // force sign of X to +1 (we already extracted s above)

        final long[] R = new long[4];
        log2Inner(R, X);                 // R = log|x|, rel.err < 2^-122.88
        final long[] Rmul = new long[4];
        pMulDint21(Rmul, R, Y);          // R *= Y, rel.err < 2*2^-127
        // exp_2 mutates its input/output destination; pass Rmul as both
        final long[] Rexp = new long[4];
        exp2Inner(Rexp, Rmul);           // R = exp(R), rel.err < 2^-121.70
        // Total relative error < 2^-113.17 (~29126 ulps).

        // Rounding test — pow.c:1830-1870
        final long Rex = Rexp[2];
        long rd; // 1 if we are NOT bit-determined (need stage 3)

        // Underflow case
        if ( Rex < -1075L ) {
            // pow.c:1834-1839: return 0.5 * (s * 0x1p-1074)
            return 0.5 * (s * 0x1p-1074);
        }

        if ( Rex < -1022L ) {
            // Subnormal regime: pow.c:1841-1860
            final long ex = -(1022L + Rex); // 1 <= ex <= 53
            // m = R.lo >> (10 + ex) | R.hi << (54 - ex)
            final long m = (Rexp[1] >>> (int) (10L + ex)) | (Rexp[0] << (int) (54L - ex));
            // rd = m + 14 > 28 ; uses unsigned compare since m may be near 2^64
            rd = (Long.compareUnsigned(m + 14L, 28L) > 0) ? 1L : 0L;
        } else {
            // Normal regime: pow.c:1862-1870
            // lo = R.lo >> 10 | R.hi << 54  (the 64 bits past the round bit)
            final long lo = (Rexp[1] >>> 10) | (Rexp[0] << 54);
            // rd = lo + 28 > 56 ; unsigned compare to capture the wrap-around band.
            rd = (Long.compareUnsigned(lo + 28L, 56L) > 0) ? 1L : 0L;
        }

        // Restore sign for s == -1 (negative-base, odd-integer-y case)
        Rexp[3] = (s < 0.0) ? 1L : 0L;

        if ( rd != 0L ) {
            // Stage-2 succeeded — round dint to double.
            return dintTod(Rexp);
        }

        // Stage-2 ambiguous (within 28 ulps of rounding boundary). Stage 3
        // (exact_pow + Qint64 chain) is deferred to Phase 2o. Until then,
        // fall through to JVM's Math.pow which may give a 1-ulp deviation
        // on these ultra-rare cases.
        return s * Math.pow(x, y);
    }

    /**
     * Compute (h, l) double-double approximating log(x), x &gt; 0 finite. Returns {@code true} when the special "_e ==
     * 0 and |l| &gt; |h|*2^-24" cancellation case fired (CORE-MATH's `cancel`); used by the outer Ziv test to pick a
     * wider error bound.
     */
    private static boolean log1(double[] out_hl, double x) {
        final long u = Double.doubleToRawLongBits(x);
        long m = u & 0x000fffffffffffffL;
        long e = (u >>> 52) & 0x7ffL;

        long tBits;
        if ( e != 0L ) {
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
        if ( c != 0L )
            t *= 0.5;

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
        if ( e == 0L && Math.abs(l1f) > Math.abs(h1) * 0x1p-24 ) {
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

    private static void sMul(double[] out_hl, double a, double bh, double bl) {
        // a_mul(hi, s, a, bh)
        final double hi = a * bh;
        final double s_ = Math.fma(a, bh, -hi);
        // lo = fma(a, bl, s)
        final double lo = Math.fma(a, bl, s_);
        out_hl[0] = hi;
        out_hl[1] = lo;
    }

    /**
     * exp(rh+rl) * s -> (eh, el), with the special boundary handling matching pow.c. When rh is outside the "always
     * overflow/underflow resolves" band, sets eh=el=NaN to force Ziv fall-through.
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

        if ( Double.isNaN(rh) || rh > RHO2 ) {
            if ( !Double.isNaN(rh) && rh > RHO3 ) {
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
        if ( rh < RHO1 ) {
            if ( rh < RHO0 ) {
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
        eh = em[0];
        el = em[1];

        final double dscaleBits = Double.longBitsToDouble(M << 52);
        final double dscale = dscaleBits * s;
        eh *= dscale;
        el *= dscale;
        out_eh[0] = eh;
        out_eh[1] = el;
    }

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

    private static void dMul(double[] out_hl, double ah, double al, double bh, double bl) {
        final double hi = ah * bh;
        final double s_ = Math.fma(ah, bh, -hi);
        final double t = Math.fma(al, bh, s_);
        final double lo = Math.fma(ah, bl, t);
        out_hl[0] = hi;
        out_hl[1] = lo;
    }

    /** True iff x is an integer (including ±0). */
    private static boolean isInt(double x) {
        return x == Math.rint(x);
    }

    /** Returns true if the encoded NaN is signaling (bit 51 = 0). */
    private static boolean isSignaling(long bits) {
        return ((bits & EXP_MASK) == EXP_MASK) && ((bits & 0x000fffffffffffffL) != 0L) && ((bits & (1L << 51)) == 0L);
    }

    /**
     * log_2: dint64 log(x). Mutates x.ex (matches pow.c). Caller does not reuse x after the call. Stores result in r as
     * long[4]={hi,lo,ex,sgn}.
     */
    private static void log2Inner(long[] r, long[] x) {
        long E = x[2];
        int i;
        if ( Long.compareUnsigned(x[0], 0xb504f333f9de6484L) > 0 ) {
            E++;
            i = (int) (x[0] >>> (63 + 1 - 7));
        } else {
            i = (int) (x[0] >>> (63 - 7));
        }
        x[2] = x[2] - E;

        // z = x * _INVERSE_2_1[i - 90]
        final long[] z = new long[4];
        final long[] inv1 = loadDint(_INVERSE_2_1_HI, _INVERSE_2_1_LO, _INVERSE_2_1_EX, _INVERSE_2_1_SGN, i - 90);
        pMulDint11(z, x, inv1);

        // 2nd lookup
        final int j = (int) (z[0] >>> (63 - 13 - (int) z[2]));

        // z = z * _INVERSE_2_2[j - 8128]  (alias-safe in pMulDint11)
        final long[] inv2 = loadDint(_INVERSE_2_2_HI, _INVERSE_2_2_LO, _INVERSE_2_2_EX, _INVERSE_2_2_SGN, j - 8128);
        pMulDint11(z, z, inv2);

        // z = z - 1  (alias-safe in pAddDint)
        final long[] mone = { M_ONE_D_HI, M_ONE_D_LO, M_ONE_D_EX, M_ONE_D_SGN };
        pAddDint(z, mone, z);

        // r = E * LOG2 (mul_dint_int64)
        final long[] log2 = { LOG2_HI, LOG2_LO, LOG2_EX, LOG2_SGN };
        pMulDintInt64(r, log2, E);

        // p = polynomial(z) via 9-step Horner chain p_2(p, z)
        final long[] p = new long[4];
        polyP2(p, z);

        // p = p + _LOG_INV_2_2[j - 8128]
        final long[] logInv22 = loadDint(_LOG_INV_2_2_HI, _LOG_INV_2_2_LO, _LOG_INV_2_2_EX, _LOG_INV_2_2_SGN, j - 8128);
        pAddDint(p, logInv22, p);

        // p = p + _LOG_INV_2_1[i - 90]
        final long[] logInv21 = loadDint(_LOG_INV_2_1_HI, _LOG_INV_2_1_LO, _LOG_INV_2_1_EX, _LOG_INV_2_1_SGN, i - 90);
        pAddDint(p, logInv21, p);

        // r = p + r
        pAddDint(r, p, r);
    }

    /**
     * <p><b>Aliasing note:</b> CORE-MATH's {@code add_dint_11(r, a, r)}
     * writes only {@code r->hi}/{@code ex}/{@code sgn}, leaving {@code r->lo} untouched. To match this contract, the
     * polynomial chains call {@code pAddDint11(r, coeff, r)} directly (with r as both destination and the second
     * operand) — both pAddDint11 and pAddDint here are alias-safe (they capture all input fields into locals at
     * entry).
     */
    private static void polyP2(long[] r, long[] z) {
        final long[] coeff = new long[4];
        // r = mul_dint_11(z, P_2[0])
        copyDintFromTable(coeff, _P_2_HI, _P_2_LO, _P_2_EX, _P_2_SGN, 0);
        pMulDint11(r, z, coeff);

        // r = add_dint_11(P_2[1], r)  — alias: r serves as both dest and arg b
        copyDintFromTable(coeff, _P_2_HI, _P_2_LO, _P_2_EX, _P_2_SGN, 1);
        pAddDint11(r, coeff, r);

        // mul_dint_11(r, z, r) — alias-safe in pMulDint11
        pMulDint11(r, z, r);
        copyDintFromTable(coeff, _P_2_HI, _P_2_LO, _P_2_EX, _P_2_SGN, 2);
        pAddDint11(r, coeff, r);

        pMulDint11(r, z, r);
        copyDintFromTable(coeff, _P_2_HI, _P_2_LO, _P_2_EX, _P_2_SGN, 3);
        pAddDint11(r, coeff, r);

        pMulDint11(r, z, r);
        copyDintFromTable(coeff, _P_2_HI, _P_2_LO, _P_2_EX, _P_2_SGN, 4);
        pAddDint(r, coeff, r);

        pMulDint21(r, r, z);
        copyDintFromTable(coeff, _P_2_HI, _P_2_LO, _P_2_EX, _P_2_SGN, 5);
        pAddDint(r, coeff, r);

        pMulDint21(r, r, z);
        copyDintFromTable(coeff, _P_2_HI, _P_2_LO, _P_2_EX, _P_2_SGN, 6);
        pAddDint(r, coeff, r);

        pMulDint21(r, r, z);
        copyDintFromTable(coeff, _P_2_HI, _P_2_LO, _P_2_EX, _P_2_SGN, 7);
        pAddDint(r, coeff, r);

        pMulDint21(r, r, z);
        copyDintFromTable(coeff, _P_2_HI, _P_2_LO, _P_2_EX, _P_2_SGN, 8);
        pAddDint(r, coeff, r);

        // final mul_dint_21(r, z) (no add)
        pMulDint21(r, r, z);
    }

    /**
     * exp_2: dint64 exp(x). For |x| < 744.45 with relative error < 2^-121.70. Stores result in r. (Aliasing: r and x
     * may be the same ref.)
     */
    private static void exp2Inner(long[] r, long[] x) {
        // C: if (x->ex >= 10) overflow/underflow stub returning ±exp_max
        if ( x[2] >= 10L ) {
            cp4(r, x);
            r[2] = (x[3] == 0x1L) ? -1076L : 1025L;
            r[3] = 0L;
            return;
        }

        // K = mul_dint_11(x, LOG2_INV)  — approx x * 2^12 / log(2)
        final long[] log2Inv = { LOG2_INV_HI, LOG2_INV_LO, LOG2_INV_EX, LOG2_INV_SGN };
        final long[] K = new long[4];
        pMulDint11(K, x, log2Inv);

        // k = trunc(K)
        final long k = pDintToi(K);

        // K = mul_dint_int64(LOG2, k)  — alias-safe (r==a OK; b is scalar)
        final long[] log2 = { LOG2_HI, LOG2_LO, LOG2_EX, LOG2_SGN };
        pMulDintInt64(K, log2, k);
        K[2] -= 12L;
        K[3] = K[3] ^ 1L; // flip sign

        // y = x + K  (Sterbenz exact, alias-safe)
        final long[] y = new long[4];
        pAddDint(y, x, K);

        final long M = k >> 12;
        final int i2 = (int) ((k >> 6) & 0x3fL);
        final int i1 = (int) (k & 0x3fL);

        // q_2(r, y)
        polyQ2(r, y);

        // r = mul_dint(T1_2[i2], r) (alias-safe)
        final long[] t1 = loadDint(_T1_2_HI, _T1_2_LO, _T1_2_EX, _T1_2_SGN, i2);
        pMulDint(r, t1, r);

        // r = mul_dint(T2_2[i1], r)
        final long[] t2 = loadDint(_T2_2_HI, _T2_2_LO, _T2_2_EX, _T2_2_SGN, i1);
        pMulDint(r, t2, r);

        // r->ex += M (exact)
        r[2] += M;
    }

    /**
     * Aliasing note: see polyP2 — chains use r as both dest and arg b to match CORE-MATH's add_dint_11 lo-preservation
     * semantics.
     */
    private static void polyQ2(long[] r, long[] y) {
        final long[] coeff = new long[4];

        copyDintFromTable(coeff, _Q_2_HI, _Q_2_LO, _Q_2_EX, _Q_2_SGN, 0);
        pMulDint11(r, y, coeff);
        copyDintFromTable(coeff, _Q_2_HI, _Q_2_LO, _Q_2_EX, _Q_2_SGN, 1);
        pAddDint11(r, coeff, r);

        pMulDint11(r, y, r);
        copyDintFromTable(coeff, _Q_2_HI, _Q_2_LO, _Q_2_EX, _Q_2_SGN, 2);
        pAddDint11(r, coeff, r);

        pMulDint11(r, y, r);
        copyDintFromTable(coeff, _Q_2_HI, _Q_2_LO, _Q_2_EX, _Q_2_SGN, 3);
        pAddDint(r, coeff, r);

        pMulDint(r, y, r);
        copyDintFromTable(coeff, _Q_2_HI, _Q_2_LO, _Q_2_EX, _Q_2_SGN, 4);
        pAddDint(r, coeff, r);

        pMulDint(r, y, r);
        copyDintFromTable(coeff, _Q_2_HI, _Q_2_LO, _Q_2_EX, _Q_2_SGN, 5);
        pAddDint(r, coeff, r);

        pMulDint(r, y, r);
        copyDintFromTable(coeff, _Q_2_HI, _Q_2_LO, _Q_2_EX, _Q_2_SGN, 6);
        pAddDint(r, coeff, r);

        pMulDint(r, y, r);
        copyDintFromTable(coeff, _Q_2_HI, _Q_2_LO, _Q_2_EX, _Q_2_SGN, 7);
        pAddDint(r, coeff, r);
    }

    /** dint_fromd: convert non-zero finite double to dint64. */
    private static void dintFromD(long[] a, double b) {
        // fast_extract (pow.h:46): e = ((u>>52)&0x7ff) - 0x3ff;
        //   m = (u & ~0ull>>12) + (e ? 1<<52 : 0)
        final long u = Double.doubleToRawLongBits(b);
        long e = (u >>> 52) & 0x7ffL;
        long m = (u & 0x000fffffffffffffL) + ((e != 0L) ? (1L << 52) : 0L);
        e = e - 0x3ffL;

        final int t = Long.numberOfLeadingZeros(m);
        a[3] = (b < 0.0) ? 1L : 0L;
        a[0] = m << t;
        a[2] = e - ((t > 11) ? (long) (t - 12) : 0L);
        a[1] = 0L;
    }

    /** dint_tod (pow.h:304-368): convert dint to double. Mutates a. */
    private static double dintTod(long[] a) {
        if ( a[2] < -1022L )
            return dintTodSubnormal(a);

        long rBits = (a[0] >>> 11) | (0x3ffL << 52);
        double rd = 0.0;
        if ( ((a[0] >>> 10) & 0x1L) != 0L )
            rd += 0x1p-53;
        if ( ((a[0] & 0x3ffL) != 0L) || (a[1] != 0L) )
            rd += 0x1p-54;
        if ( a[3] != 0L )
            rd = -rd;

        rBits = rBits | (a[3] << 63);
        double r = Double.longBitsToDouble(rBits) + rd;

        double e;
        if ( a[2] > -1023L ) {
            // normal case
            if ( a[2] > 1023L ) {
                if ( a[2] == 1024L ) {
                    r = r * 0x1p+1;
                    e = 0x1p+1023;
                } else {
                    r = 0x1.fffffffffffffp+1023;
                    e = 0x1.fffffffffffffp+1023;
                }
            } else {
                final long eBits = ((a[2] + 1023L) & 0x7ffL) << 52;
                e = Double.longBitsToDouble(eBits);
            }
        } else {
            // subnormal
            if ( a[2] < -1074L ) {
                if ( a[2] == -1075L ) {
                    r = r * 0x1p-1;
                    e = 0x1p-1074;
                } else {
                    r = 0x0.0000000000001p-1022;
                    e = 0x0.0000000000001p-1022;
                }
            } else {
                final long eBits = 1L << (int) (a[2] + 1074L);
                e = Double.longBitsToDouble(eBits);
            }
        }
        return r * e;
    }

    /** dint_tod_subnormal (pow.h:223-300). Round-to-nearest only. */
    private static double dintTodSubnormal(long[] a) {
        final long ex = -(1011L + a[2]); // ex >= 12
        if ( ex >= 64 ) {
            // all bits disappear: |a| < 2^-1074
            // FE_TONEAREST: rb = (hi >> 63); sb = (hi << 1) | lo;
            // ret = (ex > 64 || rb == 0 || sb == 0) ? +0.0 : 0x1p-1074;
            final long rb = (a[0] >>> 63) & 0x1L;
            final long sb = ((a[0] << 1) | a[1]);
            double ret = (ex > 64L || rb == 0L || sb == 0L) ? +0.0 : 0x1p-1074;
            return (a[3] != 0L) ? -ret : ret;
        }

        long hi = a[0] >>> (int) ex;
        final long rb = (a[0] >>> (int) (ex - 1L)) & 0x1L;
        // sb = (a.hi << (65 - ex)) || a.lo (logical OR coerced to {0,1})
        final long sbShift = 65L - ex;
        final long sbHi = (sbShift >= 64L) ? 0L : (a[0] << (int) sbShift);
        final long sb = ((sbHi != 0L) || (a[1] != 0L)) ? 1L : 0L;

        // FE_TONEAREST: hi += sb ? rb : hi & rb
        if ( sb != 0L )
            hi += rb;
        else
            hi += hi & rb;

        long bits = hi | (a[3] << 63);
        return Double.longBitsToDouble(bits);
    }

    /** dint_toi (pow.h:212-219): trunc-toward-zero conversion. */
    private static long pDintToi(long[] a) {
        if ( a[2] < 0L )
            return 0L;
        // r = a.hi >> (63 - a.ex)
        final int sh = (int) (63L - a[2]);
        final long r = (sh >= 64) ? 0L : (a[0] >>> sh);
        return (a[3] != 0L) ? -r : r;
    }

    /**
     * pAddDint: full 128-bit add. r and a/b may NOT alias (caller passes fresh r).
     */
    private static void pAddDint(long[] r, long[] a, long[] b) {
        long aHi = a[0], aLo = a[1], aEx = a[2], aSgn = a[3];
        long bHi = b[0], bLo = b[1], bEx = b[2], bSgn = b[3];

        // C: if (!(a.hi | a.lo)) { cp_dint(r, b); return; }
        if ( (aHi | aLo) == 0L ) {
            r[0] = bHi;
            r[1] = bLo;
            r[2] = bEx;
            r[3] = bSgn;
            return;
        }

        // cmp_dint_abs
        final int c = cmpDintAbs(aHi, aLo, aEx, bHi, bLo, bEx);
        if ( c == 0 ) {
            if ( (aSgn ^ bSgn) != 0L ) {
                r[0] = ZERO_D_HI;
                r[1] = ZERO_D_LO;
                r[2] = ZERO_D_EX;
                r[3] = ZERO_D_SGN;
                return;
            }
            r[0] = aHi;
            r[1] = aLo;
            r[2] = aEx + 1L;
            r[3] = aSgn;
            return;
        }
        if ( c < 0 ) {
            // swap operands
            long t;
            t = aHi;
            aHi = bHi;
            bHi = t;
            t = aLo;
            aLo = bLo;
            bLo = t;
            t = aEx;
            aEx = bEx;
            bEx = t;
            t = aSgn;
            aSgn = bSgn;
            bSgn = t;
        }

        // |A| > |B|, so a.ex >= b.ex
        final long sgn = aSgn;
        long rEx = aEx;
        long ABhi = bHi, ABlo = bLo;
        final long k = aEx - bEx;
        if ( k > 0L ) {
            if ( k < 128L ) {
                // shift-right (B.r >> k)
                final long[] shr = u128ShiftRight(bHi, bLo, (int) k);
                ABhi = shr[0];
                ABlo = shr[1];
            } else {
                ABhi = 0L;
                ABlo = 0L;
            }
        }

        long Chi, Clo;
        if ( (aSgn ^ bSgn) != 0L ) {
            // Different signs: C = A - B
            final long[] sub = u128Sub(aHi, aLo, ABhi, ABlo);
            Chi = sub[0];
            Clo = sub[1];
            long ex;
            if ( Chi != 0L )
                ex = Long.numberOfLeadingZeros(Chi);
            else
                ex = 64L + Long.numberOfLeadingZeros(Clo);
            if ( ex > 0L ) {
                if ( k == 1L ) {
                    // Sterbenz: C = (A << ex) - (b->r << (ex - 1))
                    final long[] aShl = u128ShiftLeft(aHi, aLo, (int) ex);
                    final long[] bShl = u128ShiftLeft(bHi, bLo, (int) (ex - 1L));
                    final long[] s2 = u128Sub(aShl[0], aShl[1], bShl[0], bShl[1]);
                    Chi = s2[0];
                    Clo = s2[1];
                } else {
                    final long[] aShl = u128ShiftLeft(aHi, aLo, (int) ex);
                    final long[] bShl = u128ShiftLeft(ABhi, ABlo, (int) ex);
                    final long[] s2 = u128Sub(aShl[0], aShl[1], bShl[0], bShl[1]);
                    Chi = s2[0];
                    Clo = s2[1];
                }
                rEx -= ex;
                ex = (Chi != 0L) ? Long.numberOfLeadingZeros(Chi) : 0L;
                // fall through to the final shift
            }
            final long[] shl = u128ShiftLeft(Chi, Clo, (int) ex);
            Chi = shl[0];
            Clo = shl[1];
            rEx -= ex;
        } else {
            // Same signs: C = A + B
            final long sumLo = aLo + ABlo;
            final long carryLo = (Long.compareUnsigned(sumLo, aLo) < 0) ? 1L : 0L;
            final long sumHi = aHi + ABhi + carryLo;
            // Detect overflow of the 128-bit add: top-carry-out.
            // We had carry-out if the sum's high word is "less than" the
            // larger input's high word (with adjusted carry). Use explicit
            // check matching pow_dint.h's `if (C < A)`.
            final boolean overflow = u128LessThan(sumHi, sumLo, aHi, aLo);
            Chi = sumHi;
            Clo = sumLo;
            if ( overflow ) {
                // C = ((u128)1 << 127) | (C >> 1)
                final long[] shr = u128ShiftRight(Chi, Clo, 1);
                Chi = shr[0] | (1L << 63);
                Clo = shr[1];
                rEx++;
            }
        }

        r[0] = Chi;
        r[1] = Clo;
        r[2] = rEx;
        r[3] = sgn;
    }

    /**
     * pAddDint11: 64-bit add (assumes a.lo == b.lo == 0).
     *
     * <p><b>IMPORTANT</b>: pow_dint.h's add_dint_11 only writes {@code r->hi}
     * (and {@code r->ex}/{@code r->sgn}); {@code r->lo} is left untouched — the caller's prior {@code r.lo} value is
     * preserved across the call. This is a subtle but load-bearing detail used by q_2/p_2's Horner chains. Java port
     * preserves it: {@code r[1]} is NOT assigned unless we explicitly need to (zero-result case, swap-of-operand case
     * where we copy from b).
     */
    private static void pAddDint11(long[] r, long[] a, long[] b) {
        long aHi = a[0], aLo = a[1], aEx = a[2], aSgn = a[3];
        long bHi = b[0], bLo = b[1], bEx = b[2], bSgn = b[3];

        // C: if (!a->hi) { cp_dint(r, b); return; }  → copies all four fields including lo
        if ( aHi == 0L ) {
            r[0] = bHi;
            r[1] = bLo;
            r[2] = bEx;
            r[3] = bSgn;
            return;
        }
        if ( bHi == 0L ) {
            r[0] = aHi;
            r[1] = aLo;
            r[2] = aEx;
            r[3] = aSgn;
            return;
        }

        // cmp_dint_11(a, b): exponent then hi
        int c1 = Long.compare(aEx, bEx);
        int c = (c1 != 0) ? c1 : Long.compareUnsigned(aHi, bHi);
        if ( c == 0 ) {
            if ( (aSgn ^ bSgn) != 0L ) {
                // ZERO entry — overwrites all fields (matching cp_dint(r, &ZERO))
                r[0] = ZERO_D_HI;
                r[1] = ZERO_D_LO;
                r[2] = ZERO_D_EX;
                r[3] = ZERO_D_SGN;
                return;
            }
            // C: cp_dint(r, a); r->ex++;  → also overwrites lo from a.
            r[0] = aHi;
            r[1] = aLo;
            r[2] = aEx + 1L;
            r[3] = aSgn;
            return;
        }
        if ( c < 0 ) {
            long t;
            t = aHi;
            aHi = bHi;
            bHi = t;
            t = aLo;
            aLo = bLo;
            bLo = t;
            t = aEx;
            aEx = bEx;
            bEx = t;
            t = aSgn;
            aSgn = bSgn;
            bSgn = t;
        }

        long A = aHi, B = bHi;
        final long sgn = aSgn;
        long rEx = aEx;
        if ( aEx > bEx ) {
            final long k = aEx - bEx;
            B = (k < 64L) ? (B >>> (int) k) : 0L;
        }

        long C;
        if ( (aSgn ^ bSgn) != 0L ) {
            // Different signs: C = A - B
            C = A - B;
            long ex = Long.numberOfLeadingZeros(C);
            if ( ex > 0L ) {
                C = (A << (int) ex) - (B << (int) ex);
                rEx -= ex;
                ex = Long.numberOfLeadingZeros(C);
            }
            C = C << (int) ex;
            rEx -= ex;
        } else {
            // Same signs: C = A + B (with overflow detection)
            C = A + B;
            if ( Long.compareUnsigned(C, A) < 0 ) {
                // overflow
                C = (1L << 63) | (C >>> 1);
                rEx++;
            }
        }

        // C: r->hi = C; r->ex = rEx; r->sgn = sgn;
        // Note: r->lo is NOT modified by pow_dint.h's add_dint_11 — it
        // retains the value from before the call. We mirror that here.
        r[0] = C;
        r[2] = rEx;
        r[3] = sgn;
        // r[1] intentionally not assigned.
    }

    /**
     * pMulDint: full 128-bit×128-bit multiply. pow_dint.h convention: r->ex = a->ex + b->ex + ex (NO -1 like dint.h).
     *
     * <p>The C reference computes {@code (m1>>64) + (m2>>64)} as a 128-bit
     * value, which can carry past 64 bits when both summands are near {@code 0xffff...}. The Java port must preserve
     * that carry into {@code r->hi}.
     */
    private static void pMulDint(long[] r, long[] a, long[] b) {
        final long aHi = a[0], aLo = a[1];
        final long bHi = b[0], bLo = b[1];

        // m1 = a.hi * b.lo (only the high 64 bits matter)
        final long m1Hi = unsignedMulHigh(aHi, bLo);
        // m2 = a.lo * b.hi (only the high 64 bits matter)
        final long m2Hi = unsignedMulHigh(aLo, bHi);
        // r = a.hi * b.hi (128-bit)
        long rHi = unsignedMulHigh(aHi, bHi);
        long rLo = aHi * bHi;

        // C: r->r += (m1 >> 64) + (m2 >> 64);
        // The right side is a 128-bit sum (since each summand is u64, their
        // sum can be up to 2^65 - 2). Compute as a {midHi, midLo} pair.
        final long midLo = m1Hi + m2Hi;
        final long midHi = (Long.compareUnsigned(midLo, m1Hi) < 0) ? 1L : 0L;
        // Now add (midHi, midLo) to (rHi, rLo) — full 128-bit add.
        final long newRLo = rLo + midLo;
        final long carry1 = (Long.compareUnsigned(newRLo, rLo) < 0) ? 1L : 0L;
        rLo = newRLo;
        rHi = rHi + midHi + carry1;

        // ex = r.hi >> 63; r.r = r.r << (1 - ex);
        final long topBit = (rHi >>> 63) & 1L;
        if ( topBit == 0L ) {
            rHi = (rHi << 1) | (rLo >>> 63);
            rLo = rLo << 1;
        }

        r[0] = rHi;
        r[1] = rLo;
        r[2] = a[2] + b[2] + topBit;
        r[3] = a[3] ^ b[3];
    }

    /** pMulDint21: assumes b.lo == 0; output has non-zero lo. */
    private static void pMulDint21(long[] r, long[] a, long[] b) {
        final long aHi = a[0], aLo = a[1];
        final long bHi = b[0];

        // hi = a.hi * b.hi (128-bit)
        long rHi = unsignedMulHigh(aHi, bHi);
        long rLo = aHi * bHi;
        // lo = a.lo * b.hi (128-bit) → only its high word feeds in
        final long loHi = unsignedMulHigh(aLo, bHi);

        // r += loHi
        final long newRLo = rLo + loHi;
        final long carry = (Long.compareUnsigned(newRLo, rLo) < 0) ? 1L : 0L;
        rLo = newRLo;
        rHi += carry;

        // Normalize: ex = r.hi >> 63; shift left by (1 - ex)
        final long topBit = (rHi >>> 63) & 1L;
        if ( topBit == 0L ) {
            rHi = (rHi << 1) | (rLo >>> 63);
            rLo = rLo << 1;
        }

        r[0] = rHi;
        r[1] = rLo;
        r[2] = a[2] + b[2] + topBit;
        r[3] = a[3] ^ b[3];
    }

    /** pMulDint11: exact, assumes a.lo == b.lo == 0. */
    private static void pMulDint11(long[] r, long[] a, long[] b) {
        final long aHi = a[0];
        final long bHi = b[0];

        // r = a.hi * b.hi (128-bit)
        long rHi = unsignedMulHigh(aHi, bHi);
        long rLo = aHi * bHi;

        final long topBit = (rHi >>> 63) & 1L;
        if ( topBit == 0L ) {
            rHi = (rHi << 1) | (rLo >>> 63);
            rLo = rLo << 1;
        }

        r[0] = rHi;
        r[1] = rLo;
        r[2] = a[2] + b[2] + topBit;
        r[3] = a[3] ^ b[3];
    }

    /** pMulDintInt64: multiply dint × signed int64. */
    private static void pMulDintInt64(long[] r, long[] a, long b) {
        if ( b == 0L ) {
            r[0] = ZERO_D_HI;
            r[1] = ZERO_D_LO;
            r[2] = ZERO_D_EX;
            r[3] = ZERO_D_SGN;
            return;
        }
        final long c = (b < 0L) ? -b : b;
        r[3] = (b < 0L) ? (a[3] ^ 1L) : a[3];
        r[2] = a[2] + 64L;

        // r = a.hi * c (128-bit)
        long rHi = unsignedMulHigh(a[0], c);
        long rLo = a[0] * c;

        // Warning: if c=1, rHi might be 0
        int m = (rHi != 0L) ? Long.numberOfLeadingZeros(rHi) : 64;
        // r.r = r.r << m  (128-bit left shift)
        final long[] shifted = u128ShiftLeft(rHi, rLo, m);
        rHi = shifted[0];
        rLo = shifted[1];
        r[2] -= m;

        // C: u128 l = (u128) a->lo * (u128) c;       // 128-bit unsigned product
        //    l = (l << (m - 1)) >> 63;               // align with hi*c contribution
        // The C semantics: a 128-bit `<< (m-1)` then a 128-bit `>> 63`. That's
        // a 128-bit value, NOT a single bit — needs full 128-bit handling.
        final long lProdHi = unsignedMulHigh(a[1], c);
        final long lProdLo = a[1] * c;
        long lHi, lLo;
        if ( m == 0 ) {
            // C undefined behaviour for m==0 (the spec note in pow_dint.h says
            // "TODO: FIXME"); CORE-MATH never triggers it for valid inputs.
            // Approximate as zero.
            lHi = 0L;
            lLo = 0L;
        } else {
            // Step 1: 128-bit left shift by (m-1)
            final long[] shl = u128ShiftLeft(lProdHi, lProdLo, m - 1);
            // Step 2: 128-bit right shift by 63
            final long[] shr = u128ShiftRight(shl[0], shl[1], 63);
            lHi = shr[0];
            lLo = shr[1];
        }

        // C: r->r += l;          // 128-bit add
        //    if (r->r < l) { ... overflow handling ... }
        // 128-bit add of (rHi, rLo) and (lHi, lLo)
        final long newRLo = rLo + lLo;
        final long carryLo = (Long.compareUnsigned(newRLo, rLo) < 0) ? 1L : 0L;
        final long newRHi = rHi + lHi + carryLo;
        // Overflow: did the 128-bit sum exceed 2^128?
        // Detect via: post < pre (lexicographic 128-bit unsigned).
        final boolean overflow = u128LessThan(newRHi, newRLo, rHi, rLo);
        rHi = newRHi;
        rLo = newRLo;
        if ( overflow ) {
            // C: r->r = ((u128)1 << 127) | (r->r >> 1); r->ex++;
            final long[] shr = u128ShiftRight(rHi, rLo, 1);
            rHi = shr[0] | (1L << 63);
            rLo = shr[1];
            r[2]++;
        }

        r[0] = rHi;
        r[1] = rLo;
        // r[3] already set
    }

    /** cmp_dint_abs: lexicographic on (ex, hi, lo). */
    private static int cmpDintAbs(long aHi, long aLo, long aEx, long bHi, long bLo, long bEx) {
        // dint_zero_p: hi == 0
        final boolean aZ = aHi == 0L;
        final boolean bZ = bHi == 0L;
        if ( aZ )
            return bZ ? 0 : -1;
        if ( bZ )
            return +1;
        final int c1 = Long.compare(aEx, bEx);
        if ( c1 != 0 )
            return c1;
        final int c2 = Long.compareUnsigned(aHi, bHi);
        if ( c2 != 0 )
            return c2;
        return Long.compareUnsigned(aLo, bLo);
    }

    /** loadDint: read a dint64 instance from parallel tables. */
    private static long[] loadDint(long[] hiTbl, long[] loTbl, long[] exTbl, long[] sgnTbl, int i) {
        return new long[] { hiTbl[i], loTbl[i], exTbl[i], sgnTbl[i] };
    }

    private static void copyDintFromTable(long[] out, long[] hiTbl, long[] loTbl, long[] exTbl, long[] sgnTbl, int i) {
        out[0] = hiTbl[i];
        out[1] = loTbl[i];
        out[2] = exTbl[i];
        out[3] = sgnTbl[i];
    }

    private static void copy4(long[] dst, long[] src) {
        dst[0] = src[0];
        dst[1] = src[1];
        dst[2] = src[2];
        dst[3] = src[3];
    }

    private static void cp4(long[] dst, long[] src) {
        copy4(dst, src);
    }

    /** Unsigned 64×64 → high 64 bits of 128-bit product. */
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

    /** 128-bit logical left shift; returns {newHi, newLo}. */
    private static long[] u128ShiftLeft(long hi, long lo, int n) {
        if ( n == 0 )
            return new long[] { hi, lo };
        if ( n >= 128 )
            return new long[] { 0L, 0L };
        if ( n >= 64 ) {
            final int sh = n - 64;
            final long newHi = (sh == 0) ? lo : (lo << sh);
            return new long[] { newHi, 0L };
        }
        // 0 < n < 64
        final long newHi = (hi << n) | (lo >>> (64 - n));
        final long newLo = lo << n;
        return new long[] { newHi, newLo };
    }

    /** 128-bit logical right shift; returns {newHi, newLo}. */
    private static long[] u128ShiftRight(long hi, long lo, int n) {
        if ( n == 0 )
            return new long[] { hi, lo };
        if ( n >= 128 )
            return new long[] { 0L, 0L };
        if ( n >= 64 ) {
            final int sh = n - 64;
            final long newLo = (sh == 0) ? hi : (hi >>> sh);
            return new long[] { 0L, newLo };
        }
        final long newLo = (lo >>> n) | (hi << (64 - n));
        final long newHi = hi >>> n;
        return new long[] { newHi, newLo };
    }

    /** 128-bit subtract; returns {newHi, newLo}. */
    private static long[] u128Sub(long aHi, long aLo, long bHi, long bLo) {
        final long borrow = (Long.compareUnsigned(aLo, bLo) < 0) ? 1L : 0L;
        final long resLo = aLo - bLo;
        final long resHi = aHi - bHi - borrow;
        return new long[] { resHi, resLo };
    }

    /** 128-bit unsigned less-than. */
    private static boolean u128LessThan(long aHi, long aLo, long bHi, long bLo) {
        final int hcmp = Long.compareUnsigned(aHi, bHi);
        if ( hcmp != 0 )
            return hcmp < 0;
        return Long.compareUnsigned(aLo, bLo) < 0;
    }
}
