package org.jquantlib.math.transcendental;

/**
 * Pure-Java port of CORE-MATH's correctly-rounded {@code cr_log}.
 *
 * <p>Source: CORE-MATH {@code src/binary64/log/log.c} (Paul Zimmermann
 * and Tom Hubrecht, 2022; INRIA / CERN; MIT-licensed). The accurate-path dint64 helpers come from
 * {@code src/binary64/log/dint.h} (Tom Hubrecht, 2022) — frozen since CORE-MATH 2022 because log.c was authored before
 * the canonical pow/dint.h split. This Java port respects that history: the dint primitives implemented here
 * (logAddDint, logMulDint, logMulDint2) match {@code log_dint.h} bit-exact, NOT sin/cos's {@link Dint64} (which uses a
 * different mul/add bit pattern).
 *
 * <p>Algorithm overview:
 * <ol>
 *   <li>IEEE-754 dispatch: NaN / ±inf / x ≤ 0 / subnormal special-case
 *       handling.</li>
 *   <li>Normalise mantissa into [1, 2); split as {@code 2^e * m}.</li>
 *   <li>Fast path (~99.95% of inputs): table-lookup {@code r ≈ 1/m},
 *       compute {@code z = fma(r, m, -1) ∈ [-2.12e-3, 2.12e-3]},
 *       evaluate degree-6 Sollya polynomial {@code P(z)} in
 *       double precision with FMA, then assemble the result as
 *       {@code e*log(2) + (-log r) + P(z)} using fast-two-sum.</li>
 *   <li>Error-bound check: {@code 0x1.b6p-69} (Gappa-proven). If the
 *       fast-path output straddles a rounding boundary, fall through.</li>
 *   <li>Accurate path (~2^-11.5 of inputs): convert x to dint64; apply
 *       a 240-entry dint64 reciprocal table; evaluate a degree-13
 *       dint64 polynomial; combine with E·log(2) + (-log r) tables.</li>
 * </ol>
 *
 * <p>Java translation notes:
 * <ul>
 *   <li>{@code __builtin_fma(a, b, c)} → {@link Math#fma}</li>
 *   <li>{@code __builtin_clzll} → {@link Long#numberOfLeadingZeros}</li>
 *   <li>{@code uint64_t}/{@code uint128_t} → Java {@code long} pairs
 *       with {@link Long#compareUnsigned} and {@code >>>} for logical shift</li>
 *   <li>{@code static const dint64_t} tables → parallel
 *       {@code long[]} arrays keyed by index; instances assembled
 *       on demand via the {@link #loadDint(long[], long[], long[],
 *       long[], int)} helper</li>
 *   <li>{@code volatile}, {@code __attribute__((cold))} → dropped</li>
 * </ul>
 */
final class LogKernel {

    private static final long M_ONE_HI = 0x8000000000000000L;

    // ============================================================
    //  Main entry point
    // ============================================================
    private static final long M_ONE_LO = 0x0L;
    private static final long M_ONE_EX = 0L;

    // ============================================================
    //  Fast path — double-double approximation
    // ============================================================
    private static final long M_ONE_SGN = 1L;

    // ============================================================
    //  Accurate path — dint64 precision
    // ============================================================
    private static final long LOG2_HI = 0xb17217f7d1cf79abL;

    // ============================================================
    //  log_2 — accurate-path heart
    // ============================================================
    private static final long LOG2_LO = 0xc9e3b39803f2f6afL;
    private static final long LOG2_EX = -1L;

    // ============================================================
    //  dint64 primitives — port of log_dint.h's frozen 2022 algorithms
    // ============================================================

    // Each dint instance is represented as long[4] = {hi, lo, ex, sgn}.
    private static final long LOG2_SGN = 0L;
    // _INVERSE[363] from log.c lines 70-123 (10-bit reciprocals).
    private static final double[] INVERSE = new double[363];
    // _LOG_INV[363][2] split into LOG_INV_H + LOG_INV_L.
    private static final double[] LOG_INV_H = new double[363];
    private static final double[] LOG_INV_L = new double[363];
    // P[6] degree-6 Sollya polynomial coefficients.
    private static final double[] P_COEF = new double[6];
    // _INVERSE_2[240] dint64_t reciprocal table for accurate path.
    private static final long[] INVERSE_2_HI = new long[240];

    // ============================================================
    //  dint_fromd / dint_tod — log.c-private versions (lines 780-818)
    // ============================================================
    private static final long[] INVERSE_2_LO = new long[240];
    private static final long[] INVERSE_2_EX = new long[240];

    // ============================================================
    //  u128 unsigned-128-bit arithmetic helpers
    // ============================================================
    //  All operations on a (hi, lo) pair representing a 128-bit unsigned
    //  integer. Returns are long[2] = {hi, lo} for ergonomic chaining.
    private static final long[] INVERSE_2_SGN = new long[240];
    // _LOG_INV_2[240] dint64_t -log(reciprocal) table.
    private static final long[] LOG_INV_2_HI = new long[240];
    private static final long[] LOG_INV_2_LO = new long[240];
    private static final long[] LOG_INV_2_EX = new long[240];
    private static final long[] LOG_INV_2_SGN = new long[240];
    // P_2[13] dint64_t accurate-path polynomial coefficients.
    private static final long[] P_2_HI = new long[13];
    private static final long[] P_2_LO = new long[13];

    // ============================================================
    //  Constants  (from log_dint.h:97-108)
    // ============================================================
    private static final long[] P_2_EX = new long[13];
    private static final long[] P_2_SGN = new long[13];

    static {
        long[] bits = { 0x3ff6980000000000L, 0x3ff6880000000000L, 0x3ff6780000000000L, 0x3ff6680000000000L,
                0x3ff6580000000000L, 0x3ff6480000000000L, 0x3ff6380000000000L, 0x3ff6300000000000L, 0x3ff6200000000000L,
                0x3ff6100000000000L, 0x3ff6000000000000L, 0x3ff5f00000000000L, 0x3ff5e00000000000L, 0x3ff5d00000000000L,
                0x3ff5c00000000000L, 0x3ff5b00000000000L, 0x3ff5a80000000000L, 0x3ff5980000000000L, 0x3ff5880000000000L,
                0x3ff5780000000000L, 0x3ff5680000000000L, 0x3ff5600000000000L, 0x3ff5500000000000L, 0x3ff5400000000000L,
                0x3ff5300000000000L, 0x3ff5200000000000L, 0x3ff5180000000000L, 0x3ff5080000000000L, 0x3ff4f80000000000L,
                0x3ff4f00000000000L, 0x3ff4e00000000000L, 0x3ff4d00000000000L, 0x3ff4c00000000000L, 0x3ff4b80000000000L,
                0x3ff4a80000000000L, 0x3ff4a00000000000L, 0x3ff4900000000000L, 0x3ff4800000000000L, 0x3ff4780000000000L,
                0x3ff4680000000000L, 0x3ff4580000000000L, 0x3ff4500000000000L, 0x3ff4400000000000L, 0x3ff4300000000000L,
                0x3ff4280000000000L, 0x3ff4180000000000L, 0x3ff4100000000000L, 0x3ff4000000000000L, 0x3ff3f80000000000L,
                0x3ff3e80000000000L, 0x3ff3e00000000000L, 0x3ff3d00000000000L, 0x3ff3c00000000000L, 0x3ff3b80000000000L,
                0x3ff3a80000000000L, 0x3ff3a00000000000L, 0x3ff3900000000000L, 0x3ff3880000000000L, 0x3ff3780000000000L,
                0x3ff3700000000000L, 0x3ff3600000000000L, 0x3ff3580000000000L, 0x3ff3500000000000L, 0x3ff3400000000000L,
                0x3ff3380000000000L, 0x3ff3280000000000L, 0x3ff3200000000000L, 0x3ff3100000000000L, 0x3ff3080000000000L,
                0x3ff3000000000000L, 0x3ff2f00000000000L, 0x3ff2e80000000000L, 0x3ff2d80000000000L, 0x3ff2d00000000000L,
                0x3ff2c80000000000L, 0x3ff2b80000000000L, 0x3ff2b00000000000L, 0x3ff2a00000000000L, 0x3ff2980000000000L,
                0x3ff2900000000000L, 0x3ff2800000000000L, 0x3ff2780000000000L, 0x3ff2700000000000L, 0x3ff2600000000000L,
                0x3ff2580000000000L, 0x3ff2500000000000L, 0x3ff2400000000000L, 0x3ff2380000000000L, 0x3ff2300000000000L,
                0x3ff2280000000000L, 0x3ff2180000000000L, 0x3ff2100000000000L, 0x3ff2080000000000L, 0x3ff2000000000000L,
                0x3ff1f00000000000L, 0x3ff1e80000000000L, 0x3ff1e00000000000L, 0x3ff1d00000000000L, 0x3ff1c80000000000L,
                0x3ff1c00000000000L, 0x3ff1b80000000000L, 0x3ff1b00000000000L, 0x3ff1a00000000000L, 0x3ff1980000000000L,
                0x3ff1900000000000L, 0x3ff1880000000000L, 0x3ff1800000000000L, 0x3ff1700000000000L, 0x3ff1680000000000L,
                0x3ff1600000000000L, 0x3ff1580000000000L, 0x3ff1500000000000L, 0x3ff1400000000000L, 0x3ff1380000000000L,
                0x3ff1300000000000L, 0x3ff1280000000000L, 0x3ff1200000000000L, 0x3ff1180000000000L, 0x3ff1100000000000L,
                0x3ff1000000000000L, 0x3ff0f80000000000L, 0x3ff0f00000000000L, 0x3ff0e80000000000L, 0x3ff0e00000000000L,
                0x3ff0d80000000000L, 0x3ff0d00000000000L, 0x3ff0c80000000000L, 0x3ff0c00000000000L, 0x3ff0b00000000000L,
                0x3ff0a80000000000L, 0x3ff0a00000000000L, 0x3ff0980000000000L, 0x3ff0900000000000L, 0x3ff0880000000000L,
                0x3ff0800000000000L, 0x3ff0780000000000L, 0x3ff0700000000000L, 0x3ff0680000000000L, 0x3ff0600000000000L,
                0x3ff0580000000000L, 0x3ff0500000000000L, 0x3ff0480000000000L, 0x3ff0400000000000L, 0x3ff0380000000000L,
                0x3ff0300000000000L, 0x3ff0280000000000L, 0x3ff0200000000000L, 0x3ff0180000000000L, 0x3ff0100000000000L,
                0x3ff0080000000000L, 0x3feff80000000000L, 0x3fefe80000000000L, 0x3fefd80000000000L, 0x3fefc80000000000L,
                0x3fefb80000000000L, 0x3fefa80000000000L, 0x3fef980000000000L, 0x3fef880000000000L, 0x3fef780000000000L,
                0x3fef680000000000L, 0x3fef580000000000L, 0x3fef500000000000L, 0x3fef400000000000L, 0x3fef300000000000L,
                0x3fef200000000000L, 0x3fef100000000000L, 0x3fef000000000000L, 0x3feef00000000000L, 0x3feee00000000000L,
                0x3feed00000000000L, 0x3feec80000000000L, 0x3feeb80000000000L, 0x3feea80000000000L, 0x3fee980000000000L,
                0x3fee880000000000L, 0x3fee780000000000L, 0x3fee700000000000L, 0x3fee600000000000L, 0x3fee500000000000L,
                0x3fee400000000000L, 0x3fee300000000000L, 0x3fee280000000000L, 0x3fee180000000000L, 0x3fee080000000000L,
                0x3fedf80000000000L, 0x3fedf00000000000L, 0x3fede00000000000L, 0x3fedd00000000000L, 0x3fedc00000000000L,
                0x3fedb80000000000L, 0x3feda80000000000L, 0x3fed980000000000L, 0x3fed900000000000L, 0x3fed800000000000L,
                0x3fed700000000000L, 0x3fed600000000000L, 0x3fed580000000000L, 0x3fed480000000000L, 0x3fed380000000000L,
                0x3fed300000000000L, 0x3fed200000000000L, 0x3fed100000000000L, 0x3fed080000000000L, 0x3fecf80000000000L,
                0x3fece80000000000L, 0x3fece00000000000L, 0x3fecd00000000000L, 0x3fecc80000000000L, 0x3fecb80000000000L,
                0x3feca80000000000L, 0x3feca00000000000L, 0x3fec900000000000L, 0x3fec880000000000L, 0x3fec780000000000L,
                0x3fec680000000000L, 0x3fec600000000000L, 0x3fec500000000000L, 0x3fec480000000000L, 0x3fec380000000000L,
                0x3fec300000000000L, 0x3fec200000000000L, 0x3fec180000000000L, 0x3fec080000000000L, 0x3febf80000000000L,
                0x3febf00000000000L, 0x3febe00000000000L, 0x3febd80000000000L, 0x3febc80000000000L, 0x3febc00000000000L,
                0x3febb00000000000L, 0x3feba80000000000L, 0x3feb980000000000L, 0x3feb900000000000L, 0x3feb800000000000L,
                0x3feb780000000000L, 0x3feb680000000000L, 0x3feb600000000000L, 0x3feb580000000000L, 0x3feb480000000000L,
                0x3feb400000000000L, 0x3feb300000000000L, 0x3feb280000000000L, 0x3feb180000000000L, 0x3feb100000000000L,
                0x3feb000000000000L, 0x3feaf80000000000L, 0x3feaf00000000000L, 0x3feae00000000000L, 0x3fead80000000000L,
                0x3feac80000000000L, 0x3feac00000000000L, 0x3feab80000000000L, 0x3feaa80000000000L, 0x3feaa00000000000L,
                0x3fea900000000000L, 0x3fea880000000000L, 0x3fea800000000000L, 0x3fea700000000000L, 0x3fea680000000000L,
                0x3fea600000000000L, 0x3fea500000000000L, 0x3fea480000000000L, 0x3fea400000000000L, 0x3fea300000000000L,
                0x3fea280000000000L, 0x3fea200000000000L, 0x3fea100000000000L, 0x3fea080000000000L, 0x3fea000000000000L,
                0x3fe9f00000000000L, 0x3fe9e80000000000L, 0x3fe9e00000000000L, 0x3fe9d00000000000L, 0x3fe9c80000000000L,
                0x3fe9c00000000000L, 0x3fe9b00000000000L, 0x3fe9a80000000000L, 0x3fe9a00000000000L, 0x3fe9980000000000L,
                0x3fe9880000000000L, 0x3fe9800000000000L, 0x3fe9780000000000L, 0x3fe9680000000000L, 0x3fe9600000000000L,
                0x3fe9580000000000L, 0x3fe9500000000000L, 0x3fe9400000000000L, 0x3fe9380000000000L, 0x3fe9300000000000L,
                0x3fe9280000000000L, 0x3fe9200000000000L, 0x3fe9100000000000L, 0x3fe9080000000000L, 0x3fe9000000000000L,
                0x3fe8f80000000000L, 0x3fe8e80000000000L, 0x3fe8e00000000000L, 0x3fe8d80000000000L, 0x3fe8d00000000000L,
                0x3fe8c80000000000L, 0x3fe8b80000000000L, 0x3fe8b00000000000L, 0x3fe8a80000000000L, 0x3fe8a00000000000L,
                0x3fe8980000000000L, 0x3fe8880000000000L, 0x3fe8800000000000L, 0x3fe8780000000000L, 0x3fe8700000000000L,
                0x3fe8680000000000L, 0x3fe8600000000000L, 0x3fe8500000000000L, 0x3fe8480000000000L, 0x3fe8400000000000L,
                0x3fe8380000000000L, 0x3fe8300000000000L, 0x3fe8280000000000L, 0x3fe8200000000000L, 0x3fe8100000000000L,
                0x3fe8080000000000L, 0x3fe8000000000000L, 0x3fe7f80000000000L, 0x3fe7f00000000000L, 0x3fe7e80000000000L,
                0x3fe7e00000000000L, 0x3fe7d80000000000L, 0x3fe7c80000000000L, 0x3fe7c00000000000L, 0x3fe7b80000000000L,
                0x3fe7b00000000000L, 0x3fe7a80000000000L, 0x3fe7a00000000000L, 0x3fe7980000000000L, 0x3fe7900000000000L,
                0x3fe7880000000000L, 0x3fe7800000000000L, 0x3fe7780000000000L, 0x3fe7700000000000L, 0x3fe7600000000000L,
                0x3fe7580000000000L, 0x3fe7500000000000L, 0x3fe7480000000000L, 0x3fe7400000000000L, 0x3fe7380000000000L,
                0x3fe7300000000000L, 0x3fe7280000000000L, 0x3fe7200000000000L, 0x3fe7180000000000L, 0x3fe7100000000000L,
                0x3fe7080000000000L, 0x3fe7000000000000L, 0x3fe6f80000000000L, 0x3fe6f00000000000L, 0x3fe6e80000000000L,
                0x3fe6e00000000000L, 0x3fe6d80000000000L, 0x3fe6d00000000000L, 0x3fe6c80000000000L, 0x3fe6c00000000000L,
                0x3fe6b80000000000L, 0x3fe6b00000000000L, 0x3fe6a80000000000L, 0x3fe6a00000000000L, };
        for ( int i = 0; i < bits.length; i++ )
            INVERSE[i] = Double.longBitsToDouble(bits[i]);
    }

    static {
        long[] hi = { 0xbfd615ddb4bec000L, 0xbfd5e87b20c29000L, 0xbfd5baf846aa2000L, 0xbfd58d54f86e0000L,
                0xbfd55f9107a44000L, 0xbfd531ac457ee000L, 0xbfd503a682cb2000L, 0xbfd4ec9732600000L, 0xbfd4be5f95778000L,
                0xbfd4900680401000L, 0xbfd4618bc21c6000L, 0xbfd432ef2a04f000L, 0xbfd404308686a000L, 0xbfd3d54fa5c1f000L,
                0xbfd3a64c55694000L, 0xbfd3772662bfe000L, 0xbfd35f865c933000L, 0xbfd3302c16586000L, 0xbfd300aead063000L,
                0xbfd2d10dec508000L, 0xbfd2a1499f763000L, 0xbfd2895a13de8000L, 0xbfd2596010df7000L, 0xbfd22941fbcf8000L,
                0xbfd1f8ff9e48a000L, 0xbfd1c898c169a000L, 0xbfd1b05791f08000L, 0xbfd17fb98e151000L, 0xbfd14ef67f887000L,
                0xbfd136870293b000L, 0xbfd1058bf9ae5000L, 0xbfd0d46b579ab000L, 0xbfd0a324e2739000L, 0xbfd08a73667c5000L,
                0xbfd058f3c703f000L, 0xbfd0402594b4d000L, 0xbfd00e6c45ad5000L, 0xbfcfb9186d5e4000L, 0xbfcf871b28956000L,
                0xbfcf22e5e72f2000L, 0xbfcebe61f4dd8000L, 0xbfce8c0252aa6000L, 0xbfce27076e2b0000L, 0xbfcdc1bca0abe000L,
                0xbfcd8ef91af32000L, 0xbfcd293581b6c000L, 0xbfccf6354e09c000L, 0xbfcc8ff7c79aa000L, 0xbfcc5cba543ae000L,
                0xbfcbf601bb0e4000L, 0xbfcbc286742d8000L, 0xbfcb5b519e8fc000L, 0xbfcaf3c94e80c000L, 0xbfcabfe5ae462000L,
                0xbfca57df28244000L, 0xbfca23bc1fe2c000L, 0xbfc9bb362e7e0000L, 0xbfc986d322818000L, 0xbfc91dcc8c340000L,
                0xbfc8e928de886000L, 0xbfc87fa06520c000L, 0xbfc84abb75866000L, 0xbfc815c0a1436000L, 0xbfc7ab890210e000L,
                0xbfc7764c128f2000L, 0xbfc70b8f97a1a000L, 0xbfc6d60fe719e000L, 0xbfc66acd4272a000L, 0xbfc6350a28aaa000L,
                0xbfc5ff3070a7a000L, 0xbfc59338d9982000L, 0xbfc55d1ad4232000L, 0xbfc4f099f4a24000L, 0xbfc4ba36f39a6000L,
                0xbfc483bccce6e000L, 0xbfc41682bf728000L, 0xbfc3dfc2b0ecc000L, 0xbfc371fc201e8000L, 0xbfc33af575770000L,
                0xbfc303d718e48000L, 0xbfc29552f8200000L, 0xbfc25ded0abc6000L, 0xbfc2266f190a6000L, 0xbfc1b72ad52f6000L,
                0xbfc17f6458fca000L, 0xbfc1478584674000L, 0xbfc0d77e7cd08000L, 0xbfc09f561ee72000L, 0xbfc0671512ca6000L,
                0xbfc02ebb42bf4000L, 0xbfbf7b79fec38000L, 0xbfbf0a30c0118000L, 0xbfbe98b549670000L, 0xbfbe27076e2b0000L,
                0xbfbd4313d66cc000L, 0xbfbcd0cdbf8c0000L, 0xbfbc5e548f5bc000L, 0xbfbb78c82bb10000L, 0xbfbb05b49bee4000L,
                0xbfba926d3a4ac000L, 0xbfba1ef1d8060000L, 0xbfb9ab4246204000L, 0xbfb8c345d6318000L, 0xbfb84ef898e84000L,
                0xbfb7da766d7b0000L, 0xbfb765bf23a6c000L, 0xbfb6f0d28ae58000L, 0xbfb60658a9374000L, 0xbfb590cafdf00000L,
                0xbfb51b073f060000L, 0xbfb4a50d3aa1c000L, 0xbfb42edcbea64000L, 0xbfb341d7961bc000L, 0xbfb2cb0283f5c000L,
                0xbfb253f62f0a0000L, 0xbfb1dcb263db0000L, 0xbfb16536eea38000L, 0xbfb0ed839b554000L, 0xbfb0759835990000L,
                0xbfaf0a30c0118000L, 0xbfae19070c278000L, 0xbfad276b8adb0000L, 0xbfac355dd0920000L, 0xbfab42dd71198000L,
                0xbfaa4fe9ffa40000L, 0xbfa95c830ec90000L, 0xbfa868a830840000L, 0xbfa77458f6330000L, 0xbfa58a5bafc90000L,
                0xbfa494acc34d8000L, 0xbfa39e87b9fe8000L, 0xbfa2a7ec22150000L, 0xbfa1b0d989240000L, 0xbfa0b94f7c198000L,
                0xbf9f829b0e780000L, 0xbf9d91a66c540000L, 0xbf9b9fc027b00000L, 0xbf99ace7551d0000L, 0xbf97b91b07d60000L,
                0xbf95c45a51b90000L, 0xbf93cea443470000L, 0xbf91d7f7eb9f0000L, 0xbf8fc0a8b0fc0000L, 0xbf8bcf712c740000L,
                0xbf87dc475f820000L, 0xbf83e7295d260000L, 0xbf7fe02a6b100000L, 0xbf77ee11ebd80000L, 0xbf6ff00aa2b00000L,
                0xbf5ff802a9b00000L, 0x3f50020055600000L, 0x3f68090482880000L, 0x3f740c8a74780000L, 0x3f7c189cbb100000L,
                0x3f82145e939e0000L, 0x3f861e77e8b60000L, 0x3f8a2a9c6c180000L, 0x3f8e38ce30340000L, 0x3f912487a5500000L,
                0x3f932db0ea130000L, 0x3f9537e3f45f0000L, 0x3f963d6178690000L, 0x3f98492528c90000L, 0x3f9a55f548c60000L,
                0x3f9c63d2ec150000L, 0x3f9e72bf28140000L, 0x3fa0415d89e78000L, 0x3fa149e3e4008000L, 0x3fa252f32f8d0000L,
                0x3fa35c8bfaa10000L, 0x3fa3e18c1ca08000L, 0x3fa4ebf4334a0000L, 0x3fa5f6e730790000L, 0x3fa70265a5510000L,
                0x3fa80e7023d90000L, 0x3fa91b073efd8000L, 0x3fa9a187b5740000L, 0x3faaaef2d0fb0000L, 0x3fabbcebfc690000L,
                0x3faccb73cddd8000L, 0x3fadda8adc680000L, 0x3fae624c4a0b8000L, 0x3faf723b51800000L, 0x3fb0415d89e74000L,
                0x3fb0c9e615ac4000L, 0x3fb10e45b3cb0000L, 0x3fb1973bd1464000L, 0x3fb2207b5c784000L, 0x3fb2aa04a4470000L,
                0x3fb2eee507b40000L, 0x3fb378dd7f748000L, 0x3fb403207b414000L, 0x3fb4485e03dbc000L, 0x3fb4d3115d208000L,
                0x3fb55e10050e0000L, 0x3fb5e95a4d978000L, 0x3fb62f1be7d78000L, 0x3fb6bad83c188000L, 0x3fb746e100228000L,
                0x3fb78d02263d8000L, 0x3fb8197e2f410000L, 0x3fb8a6477a91c000L, 0x3fb8ecc933aec000L, 0x3fb97a07024cc000L,
                0x3fba0792e9278000L, 0x3fba4e7640b1c000L, 0x3fbadc77ee5b0000L, 0x3fbb23965a530000L, 0x3fbbb20e936d8000L,
                0x3fbc40d6425a4000L, 0x3fbc885801bc4000L, 0x3fbd179788218000L, 0x3fbd5f5565920000L, 0x3fbdef0d8d468000L,
                0x3fbe7f1691a34000L, 0x3fbec739830a0000L, 0x3fbf57bc7d900000L, 0x3fbfa01c9db58000L, 0x3fc0188d2ecf6000L,
                0x3fc03cdc0a51e000L, 0x3fc08598b59e4000L, 0x3fc0aa0691268000L, 0x3fc0f301717d0000L, 0x3fc13c2605c3a000L,
                0x3fc160c8024b2000L, 0x3fc1aa2b7e240000L, 0x3fc1ceed09854000L, 0x3fc2188fd9808000L, 0x3fc23d712a49c000L,
                0x3fc28753bc11a000L, 0x3fc2ac55095f6000L, 0x3fc2f677cbbc0000L, 0x3fc31b994d3a4000L, 0x3fc365fcb015a000L,
                0x3fc38b3e9e028000L, 0x3fc3d5e3126bc000L, 0x3fc3fb45a5992000L, 0x3fc420b327410000L, 0x3fc46baf0f9f6000L,
                0x3fc4913d8333c000L, 0x3fc4dc7b897bc000L, 0x3fc5022b292f6000L, 0x3fc54dabc2610000L, 0x3fc5737cc9018000L,
                0x3fc5bf406b544000L, 0x3fc5e533144c2000L, 0x3fc60b3100b0a000L, 0x3fc6574ebe8c2000L, 0x3fc67d6e9d786000L,
                0x3fc6c9d07d204000L, 0x3fc6f0128b756000L, 0x3fc716600c914000L, 0x3fc7631d82936000L, 0x3fc7898d85444000L,
                0x3fc7d6903caf6000L, 0x3fc7fd22ff59a000L, 0x3fc823c16551a000L, 0x3fc871213750e000L, 0x3fc897e2b17b2000L,
                0x3fc8beafeb390000L, 0x3fc90c6db9fcc000L, 0x3fc9335e5d594000L, 0x3fc95a5adcf70000L, 0x3fc9a8778deba000L,
                0x3fc9cf97cdce0000L, 0x3fc9f6c40708a000L, 0x3fca454082e6a000L, 0x3fca6c90d44b8000L, 0x3fca93ed3c8ae000L,
                0x3fcae2ca6f672000L, 0x3fcb0a4b48fc2000L, 0x3fcb31d8575bc000L, 0x3fcb811730b82000L, 0x3fcba8c90ae4a000L,
                0x3fcbd087383be000L, 0x3fcc2028ab180000L, 0x3fcc480c0005c000L, 0x3fcc6ffbc6f00000L, 0x3fcc97f8079d4000L,
                0x3fcce816157f2000L, 0x3fcd1037f2656000L, 0x3fcd386668720000L, 0x3fcd88e93fb30000L, 0x3fcdb13db0d48000L,
                0x3fcdd99edaf6e000L, 0x3fce020cc6236000L, 0x3fce530effe72000L, 0x3fce7ba35eb78000L, 0x3fcea4449f04a000L,
                0x3fceccf2c8fea000L, 0x3fcef5ade4dd0000L, 0x3fcf474b134e0000L, 0x3fcf702d36778000L, 0x3fcf991c6cb3c000L,
                0x3fcfc218be620000L, 0x3fd00a1c6adda000L, 0x3fd01eae5626c000L, 0x3fd03346e0106000L, 0x3fd047e60cde8000L,
                0x3fd05c8be0d96000L, 0x3fd085eb8f8ae000L, 0x3fd09aa572e6c000L, 0x3fd0af660eb9e000L, 0x3fd0c42d67616000L,
                0x3fd0d8fb813eb000L, 0x3fd102ac0a35d000L, 0x3fd1178e8227e000L, 0x3fd12c77cd007000L, 0x3fd14167ef367000L,
                0x3fd1565eed456000L, 0x3fd16b5ccbad0000L, 0x3fd1956d3b9bc000L, 0x3fd1aa7fd638d000L, 0x3fd1bf99635a7000L,
                0x3fd1d4b9e796c000L, 0x3fd1e9e16788a000L, 0x3fd1ff0fe7cf4000L, 0x3fd214456d0ec000L, 0x3fd23ec5991ec000L,
                0x3fd25410494e5000L, 0x3fd269621134e000L, 0x3fd27ebaf58d9000L, 0x3fd2941afb187000L, 0x3fd2a982269a4000L,
                0x3fd2bef07cdc9000L, 0x3fd2d46602add000L, 0x3fd2ff66b04eb000L, 0x3fd314f1e1d36000L, 0x3fd32a8456512000L,
                0x3fd3401e12aed000L, 0x3fd355bf1bd83000L, 0x3fd36b6776be1000L, 0x3fd3811728565000L, 0x3fd396ce359bc000L,
                0x3fd3ac8ca38e6000L, 0x3fd3c25277333000L, 0x3fd3d81fb5947000L, 0x3fd3edf463c17000L, 0x3fd419b423d5f000L,
                0x3fd42f9f3ff62000L, 0x3fd44591e053a000L, 0x3fd45b8c0a17e000L, 0x3fd4718dc271c000L, 0x3fd487970e958000L,
                0x3fd49da7f3bcc000L, 0x3fd4b3c077268000L, 0x3fd4c9e09e173000L, 0x3fd4e0086dd8c000L, 0x3fd4f637ebbaa000L,
                0x3fd50c6f1d11c000L, 0x3fd522ae0738a000L, 0x3fd538f4af8f7000L, 0x3fd54f431b7be000L, 0x3fd5659950695000L,
                0x3fd57bf753c8d000L, 0x3fd5925d2b113000L, 0x3fd5a8cadbbee000L, 0x3fd5bf406b544000L, 0x3fd5d5bddf596000L,
                0x3fd5ec433d5c3000L, 0x3fd602d08af09000L, 0x3fd61965cdb03000L, 0x3fd630030b3ab000L, };
        long[] lo = { 0xbd13c7ca90bc04b2L, 0xbd3527d18f7738faL, 0x3d339ae8f873fa41L, 0xbd2791f30a795215L,
                0x3d11e64778df4a62L, 0xbd3df83b7d931501L, 0x3d2a68c8f16f9b5dL, 0xbd234d7aaf04d104L, 0x3d3d7c92cd9ad824L,
                0x3d38bccffe1a0f8cL, 0x3d13d82f484c84ccL, 0x3d3fb129931715adL, 0xbd3f8ef43049f7d3L, 0xbd3c3e1cd9a395e3L,
                0xbd37a71cbcd735d0L, 0x3d3e9436ac53b023L, 0x3d3b07de4ea1a54aL, 0xbd36217dc2a3e08bL, 0xbd342f568b75fcacL,
                0xbd360c61f7088353L, 0x3d30dbbf51f3aadcL, 0xbd3a8d7ad24c13f0L, 0xbd38e7bc224ea3e3L, 0x3d3a6976f5eb0963L,
                0xbd27946c040cbe77L, 0x3d381410e5c62affL, 0x3d32dd466dc55e2dL, 0x3d3a8a8ba74a2684L, 0x3d3e97a65dfc9794L,
                0x3d3d3e8499d67123L, 0x3d34ab9d817d52cdL, 0xbd3d2c81f640e1e6L, 0xbd0c6bee7ef4030eL, 0xbd3ebc1d40c5a329L,
                0x3d30e866bcd236adL, 0xbcf036b89ef42d7fL, 0xbcdcc68d52e01203L, 0x3d0d572aab993c87L, 0x3d3f75fd6a526efeL,
                0x3d3f454f1417e41fL, 0x3d23d45330fdca4dL, 0x3d26805b80e8e6ffL, 0x3d3a342c2af0003cL, 0xbd38fac1a628ccc6L,
                0x3d15105fc364c784L, 0x3d383270128aaa5fL, 0xbd2771239a07d55bL, 0x3d27794f689f8434L, 0xbd20929decb454fcL,
                0xbd2386a947c378b5L, 0xbd39ac53f39d121cL, 0x3d34b722ec011f31L, 0x3cba4e633fcd9066L, 0x3d3b68f5395f139dL,
                0xbd3b99c8ca1d9abbL, 0x3d3539cd91dc9f0bL, 0x3d21f2a8a1ce0ffcL, 0xbcf93b564dd44000L, 0xbd37bc6abddeff46L,
                0xbd3a8154b13d72d5L, 0xbd322120401202fcL, 0x3d3d8daadf4e2bd2L, 0x3d302a52f9201ce8L, 0x3d2bdb9072534a58L,
                0xbd0274903479e3d1L, 0xbd34ea64f6a95befL, 0x3d3bc6e557134767L, 0xbd3aa1bdbfc6c785L, 0xbd2d5ec0ab8163afL,
                0x3d38586f183bebf2L, 0xbcf0ba68b7555d4aL, 0xbd3add94dda647e8L, 0x3d3e9bf2fafeaf27L, 0x3d34354bb3f219e5L,
                0xbd1eea52723f6369L, 0x3d210047081f849dL, 0xbd28a72a62b8c13fL, 0xbd3ee8779b2d8abcL, 0xbd3c9ecca2fe72a5L,
                0x3cd680b5ce3ecb05L, 0x3d35b967f4471dfcL, 0xbd35a3854f176449L, 0x3d24d20ab840e7f6L, 0xbd2e80a41811a396L,
                0xbd2843fad093c8dcL, 0xbd1563451027c750L, 0xbd3cb2cd2ee2f482L, 0x3d28f3057157d1a8L, 0x3d2a47579cdc0a3dL,
                0x3d15a8fa5ce00e5dL, 0x3d010987e897ed01L, 0x3d3d599e83368e91L, 0xbd34677489c50e97L, 0x3d2a342c2af0003cL,
                0x3d29454379135713L, 0xbd33e14db50dd743L, 0xbd1d0c57585fbe06L, 0x3d325ef7bc3987e7L, 0xbd0ff22c18f84a5eL,
                0xbd3563650bd22a9cL, 0xbd3cd4176df97bcbL, 0x3d28a64826787061L, 0xbd3b20f5acb42a66L, 0x3d37d5cd246977c9L,
                0xbd32cc844480c89bL, 0x3cfecbc035c4256aL, 0x3d34b4641b664613L, 0xbd30c3b1dee9c4f8L, 0xbd3c284f5722abaaL,
                0xbd383f69278e686aL, 0x3d2f7fe1308973e2L, 0xbd1bc0eeea7c9acdL, 0xbd31d09299837610L, 0xbd3e1ee2ca657021L,
                0xbd3416f8fb69a701L, 0xbd39444f5e9e8981L, 0x3d147c5e768fa309L, 0x3d3901f46d48abb4L, 0x3d3b8ecfe4b59987L,
                0x3d2d599e83368e91L, 0x3d2fea4664629e86L, 0xbd16a423c78a64b0L, 0xbd2f2ccc9abf8388L, 0x3d1c827ae5d6704cL,
                0x3d36e584a0402925L, 0x3d2c148297c5feb8L, 0x3d12623a134ac693L, 0x3d3181dce586af09L, 0x3d2b2b739570ad39L,
                0xbd211c78a56fd247L, 0xbd3eafd480ad9015L, 0x3d278ce77a9163feL, 0x3d33401e9ae889bbL, 0x3d2e89896f022783L,
                0xbd2980267c7e09e4L, 0xbd2e61f1658cfb9aL, 0x3d3b9a010ae6922aL, 0x3d2d75d97ec7c410L, 0x3d33b955b602ace4L,
                0x3d263bb6216d87d8L, 0x3d36a2c432d6a40bL, 0x3d14193a83fcc7a6L, 0xbcdf1e7cf6d3a69cL, 0xbd1c25e097bd9771L,
                0x3d3eb1245b5da1f5L, 0x3d2609c1ff29a114L, 0xbd19e23f0dda40e4L, 0xbd0749d3c2d23a07L, 0xbd20bc04a086b56aL,
                0x3d33bc661d61c5ebL, 0x3d356224cd5f35f8L, 0x3d285c0696a70c0cL, 0x3d1e3871df070002L, 0xbd3d805512588560L,
                0x3d3e3d1238c4ea00L, 0xbd38073eeaf8eaf3L, 0xbd3f73bc4d6d3472L, 0xbd39de88a3da281aL, 0x3d3fdbe5fed4b393L,
                0x3d2710cb130895fcL, 0x3d2ab259d2d7f253L, 0x3d07abf389596542L, 0xbd2aa0ba325a0c34L, 0xbd2de0709f2d03c9L,
                0xbd35439ce030a687L, 0xbd28d75149774d47L, 0xbd3dddc7f461c516L, 0xbd32b98a9a4168fdL, 0x3d283e9ae021b67bL,
                0x3d38357d5ef9eb35L, 0x3d3748ed3f6e378eL, 0xbd2d9150f73be773L, 0xbd20485a8012494cL, 0xbd2888df11fd5ce7L,
                0xbd399dc16f28bf45L, 0xbd19d7c53f76ca96L, 0xbd30c22e4ec4d90dL, 0x3d20fc1a353bb42eL, 0xbd17bf868c317c2aL,
                0x3d3965c36e09f5feL, 0xbd21b1ac64d9e42fL, 0xbd30f25c74676689L, 0xbd3d6eb0dd5610d3L, 0x3d1111c05cf1d753L,
                0x3d2c2da80974d976L, 0xbd37cf69284a3465L, 0x3d3566d154f930b3L, 0x3d349d8cfc10c7bfL, 0x3d37a48ba8b1cb41L,
                0x3d08081edd77c860L, 0x3d37141128f1facaL, 0x3d26fd84aa8157c0L, 0x3d3fad46e8d26ab7L, 0xbcf53a2582f4e1efL,
                0x3d0c1d740c53c72eL, 0x3d31cb7ce1d17171L, 0xbd2179957ed63c4eL, 0x3d0daf3cc08926aeL, 0xbd3126d16e1e21d2L,
                0x3d069b5794b69fb7L, 0xbd3c0fe460d20041L, 0x3d3c28c0af9bd6dfL, 0xbd222f39be67f7aaL, 0xbcf8bcc1732093ceL,
                0xbd0a9ce6c9ad51bfL, 0xbd0e42b6b94407c8L, 0xbd3573b209c31904L, 0xbceff64eea137079L, 0xbd368ba835459b8eL,
                0x3d3cb1121d1930ddL, 0x3d2646d1c65aacd3L, 0x3d336433b5efbeedL, 0x3d30e239cc185469L, 0xbd324750412e9a74L,
                0xbd32c1c59bc77bfaL, 0x3d311fcba80cdd10L, 0x3d176a6c9ea8b04eL, 0xbd08f351fa48a730L, 0x3d03f9651cff9dfeL,
                0x3d381a9cf169fc5cL, 0xbd27e5dd7009902cL, 0xbd345519d7032129L, 0xbd3e09b441ae86c5L, 0xbd2cf5fdd94f6509L,
                0x3d2ec2d2a9009e3dL, 0xbd31ac38dde3b366L, 0xbd315c1c39192af9L, 0xbd3b3a1e7f50c701L, 0x3d100d238fd3df5cL,
                0x3d37494e359302e6L, 0xbd1d3466d0c6c8a8L, 0x3d352b302160f40dL, 0x3d3f098ee3a50810L, 0xbd3fd3a0afb9691bL,
                0xbd370ef0545c17f9L, 0x3d13fb2f85096c4bL, 0x3d319713c0cae559L, 0xbd116282c85a0884L, 0xbd1249cd0790841aL,
                0xbd353e43558124c4L, 0x3d0c79b60ae1ff0fL, 0x3d348a05ff36a25bL, 0x3d2746fee5c8d0d8L, 0x3d39baa7a6b887f6L,
                0xbd127023eb68981cL, 0xbd31ce0bf3b290eaL, 0xbd371456c988f814L, 0xbd398c1d34f0f462L, 0xbd311e8830a706d3L,
                0xbcdc73fafd9b2dcaL, 0x3d3577390d31ef0fL, 0x3ce51b157cec3838L, 0xbd25e77dc7c5f3e1L, 0x3d38e67be3dbaf3fL,
                0xbd24c06b17c301d7L, 0xbd158bebf457b7d2L, 0x3d1e0ddb9a631e83L, 0x3d3328eb42f9af75L, 0xbd296b37380cbe9eL,
                0xbd073d54aae92cd1L, 0xbd1935f57718d7caL, 0x3d33115c3abd47daL, 0x3d07f22858a0ff6fL, 0x3d3470fa3efec390L,
                0x3d3d862f10c414e3L, 0xbd3337d94bcd3f43L, 0x3d360a77c81f7171L, 0xbd3f63b7f037b0c6L, 0xbd28724350562169L,
                0x3d37a8d5ae54f550L, 0xbd22e72d5c3998edL, 0x3d3c794e562a63cbL, 0x3d1e90683b9cd768L, 0x3d3a32e7f44432daL,
                0xbd2d4bc4595412b6L, 0xbd292e0ee55c7ac6L, 0x3d39a294d5e44e76L, 0x3d3ee138d3a69d43L, 0x3d23b161a8c6e6c5L,
                0xbd29e0aba2099515L, 0xbd084a7e75b6f6e4L, 0xbd373650b38932bcL, 0xbd375f280234bf51L, 0x3d32806a847527e6L,
                0xbd302ec669c756ebL, 0xbd252b00adb91424L, 0xbd3fdbdbb13f7c18L, 0xbd0d5eee23793649L, 0x3d35e91663732a36L,
                0xbd3bec63a3e75640L, 0xbcca211565bb8e11L, 0xbd3bae49f1df7b5eL, 0xbd10819516673e23L, 0xbd390d04cd7cc834L,
                0x3d34bba46f1cf6a0L, 0x3d31cd8d688b9e18L, 0x3d3a43dcfade85aeL, 0x3cf89ff8a966395cL, 0x3d2dbdf10d397f3cL,
                0x3d2ad0f1c77ccb58L, 0x3d3e5d513f45fe7bL, 0x3d3b50a1e1734342L, 0x3d23c7c3f528d80aL, 0x3d27188b163ceae9L,
                0x3d1ee8c88753fa35L, 0xbd2f1fbddfdfd686L, 0x3d31ef78ce2d07f2L, 0x3d13b2948a11f797L, 0x3d3e0c07824daaf5L,
                0xbcee75adfb6aba25L, 0xbd323299042d74bfL, 0x3d27d2f73ad1aa14L, 0x3d29f60a9616f7a0L, 0xbd31ac89575c2125L,
                0x3d222a667c42e56dL, 0xbd382eaed3c8b65eL, 0x3d3e9d5b513ff0c1L, 0xbd3caf0428b728a3L, 0xbd36dbe448a2e522L,
                0x3d3b1d7ac0ef77f2L, 0xbd31b61f10522625L, 0xbd2b198800b4bda7L, 0xbd3210c2b730e28bL, 0xbd22058e557285cfL,
                0x3d2a9cfa4a5004f4L, 0xbd288d0ddcd54196L, 0xbd38aed2541e6e2eL, 0xbd28e27ad3213cb8L, 0x3d04f928139af5d6L,
                0xbd317c73556e291dL, 0xbd2ba99b8964f0e8L, 0x3d116ecdb0f177c8L, 0xbd2a71e493a0702bL, 0xbd05839c5663663dL,
                0xbd2d0befbc02be4aL, 0x3d183b54b606bd5cL, 0xbd222c7c2a9d37a4L, 0xbd3f067c297f2c3fL, 0xbd3ce379226de3ecL,
                0x3d3906440f7d3354L, 0xbd06e95892923d88L, 0xbd0d9120e7d0a853L, 0x3d306c18fb4c14c5L, 0x3d3dc1b8465cf25fL,
                0x3d307b334daf4b9aL, 0xbd165b4681052b9fL, 0xbd2e20891b0ad8a4L, 0xbd34d692a1e44788L, 0xbd3fc158cb3124b9L,
                0xbd3a0e6b7e827c2cL, 0x3d2ebe708164c759L, 0x3d27ec02e45547ceL, 0x3d1a8954c0910952L, 0x3d14c5fd2badc774L,
                0x3d1fadedee5d40efL, 0xbd369bf5a7a56f34L, 0xbcf7c79b0af7ecf8L, 0xbd227023eb68981cL, 0xbd0a0b2a08a465dcL,
                0x3d36b71a1229d17fL, 0x3d1ebe9176df3f65L, 0xbd2f08ad603c488eL, 0xbd2db623e731ae00L, };
        for ( int i = 0; i < hi.length; i++ ) {
            LOG_INV_H[i] = Double.longBitsToDouble(hi[i]);
            LOG_INV_L[i] = Double.longBitsToDouble(lo[i]);
        }
    }

    static {
        long[] bits = { 0x3ff0000000000000L, 0xbfdffffffffffffaL, 0x3fd555555554f4d8L, 0xbfd0000000537df6L,
                0x3fc999a14758b084L, 0xbfc55362255e0f63L, };
        for ( int i = 0; i < bits.length; i++ )
            P_COEF[i] = Double.longBitsToDouble(bits[i]);
    }

    static {
        long[] hi = { 0x8000000000000000L, 0xfe03f80fe03f80ffL, 0xfc0fc0fc0fc0fc10L, 0xfa232cf252138ac0L,
                0xf83e0f83e0f83e10L, 0xf6603d980f6603daL, 0xf4898d5f85bb3951L, 0xf2b9d6480f2b9d65L, 0xf0f0f0f0f0f0f0f1L,
                0xef2eb71fc4345239L, 0xed7303b5cc0ed731L, 0xebbdb2a5c1619c8cL, 0xea0ea0ea0ea0ea0fL, 0xe865ac7b7603a197L,
                0xe6c2b4481cd8568aL, 0xe525982af70c880fL, 0xe38e38e38e38e38fL, 0xe1fc780e1fc780e2L, 0xe070381c0e070382L,
                0xdee95c4ca037ba58L, 0xdd67c8a60dd67c8bL, 0xdbeb61eed19c5958L, 0xda740da740da740eL, 0xd901b2036406c80eL,
                0xd79435e50d79435fL, 0xd62b80d62b80d62cL, 0xd4c77b03531dec0eL, 0xd3680d3680d3680eL, 0xd20d20d20d20d20eL,
                0xd0b69fcbd2580d0cL, 0xcf6474a8819ec8eaL, 0xce168a7725080ce2L, 0xcccccccccccccccdL, 0xcb8727c065c393e1L,
                0xca4587e6b74f032aL, 0xc907da4e871146adL, 0xc7ce0c7ce0c7ce0dL, 0xc6980c6980c6980dL, 0xc565c87b5f9d4d1cL,
                0xc4372f855d824ca6L, 0xc30c30c30c30c30dL, 0xc1e4bbd595f6e948L, 0xc0c0c0c0c0c0c0c1L, 0xbfa02fe80bfa02ffL,
                0xbe82fa0be82fa0bfL, 0xbd69104707661aa3L, 0xbc52640bc52640bdL, 0xbb3ee721a54d880cL, 0xba2e8ba2e8ba2e8cL,
                0xb92143fa36f5e02fL, 0xb81702e05c0b8171L, 0xb70fbb5a19be3659L, 0xb60b60b60b60b60cL, 0xb509e68a9b948220L,
                0xb40b40b40b40b40cL, 0xb30f63528917c80cL, 0xb21642c8590b2165L, 0xb11fd3b80b11fd3cL, 0xb02c0b02c0b02c0cL,
                0xaf3addc680af3adeL, 0xae4c415c9882b932L, 0xad602b580ad602b6L, 0xac7691840ac76919L, 0xab8f69e28359cd12L,
                0xaaaaaaaaaaaaaaabL, 0xa9c84a47a07f5638L, 0xa8e83f5717c0a8e9L, 0xa80a80a80a80a80bL, 0xa72f05397829cbc2L,
                0xa655c4392d7b73a8L, 0xa57eb50295fad40bL, 0xa4a9cf1d96833752L, 0xa3d70a3d70a3d70bL, 0xa3065e3fae7cd0e1L,
                0xa237c32b16cfd773L, 0xa16b312ea8fc377dL, 0xa0a0a0a0a0a0a0a1L, 0x9fd809fd809fd80aL, 0x9f1165e7254813e3L,
                0x9e4cad23dd5f3a21L, 0x9d89d89d89d89d8aL, 0x9cc8e160c3fb19b9L, 0x9c09c09c09c09c0aL, 0x9b4c6f9ef03a3caaL,
                0x9a90e7d95bc609aaL, 0x99d722dabde58f07L, 0x991f1a515885fb38L, 0x9868c809868c8099L, 0x97b425ed097b425fL,
                0x97012e025c04b80aL, 0x964fda6c0964fda7L, 0x95a02568095a0257L, 0x94f2094f2094f20aL, 0x9445809445809446L,
                0x939a85c40939a85dL, 0x92f113840497889dL, 0x924924924924924aL, 0x91a2b3c4d5e6f80aL, 0x90fdbc090fdbc091L,
                0x905a38633e06c43bL, 0x8fb823ee08fb823fL, 0x8f1779d9fdc3a219L, 0x8e78356d1408e784L, 0x8dda520237694809L,
                0x8d3dcb08d3dcb08eL, 0x8ca29c046514e024L, 0x8c08c08c08c08c09L, 0x8b70344a139bc75bL, 0x8ad8f2fba9386823L,
                0x8a42f8705669db47L, 0x89ae4089ae4089afL, 0x891ac73ae9819b51L, 0x8888888888888889L, 0x87f78087f78087f8L,
                0x8767ab5f34e47ef2L, 0x86d905447a34acc7L, 0x864b8a7de6d1d609L, 0x85bf37612cee3c9bL, 0x8534085340853409L,
                0x84a9f9c8084a9f9dL, 0x8421084210842109L, 0x839930523fbe3368L, 0x83126e978d4fdf3cL, 0x828cbfbeb9a020a4L,
                0x8208208208208209L, 0x81848da8faf0d278L, 0x8102040810204082L, 0x8000000000000000L, 0x8000000000000000L,
                0xff00ff00ff00ff02L, 0xfe03f80fe03f80ffL, 0xfd08e5500fd08e56L, 0xfc0fc0fc0fc0fc11L, 0xfb18856506ddaba7L,
                0xfa232cf252138ac1L, 0xf92fb2211855a866L, 0xf83e0f83e0f83e11L, 0xf74e3fc22c700f76L, 0xf6603d980f6603dbL,
                0xf57403d5d00f5741L, 0xf4898d5f85bb3951L, 0xf3a0d52cba872337L, 0xf2b9d6480f2b9d66L, 0xf1d48bcee0d399fbL,
                0xf0f0f0f0f0f0f0f2L, 0xf00f00f00f00f010L, 0xef2eb71fc4345239L, 0xee500ee500ee5010L, 0xed7303b5cc0ed731L,
                0xec979118f3fc4da3L, 0xebbdb2a5c1619c8dL, 0xeae56403ab959010L, 0xea0ea0ea0ea0ea10L, 0xe939651fe2d8d35dL,
                0xe865ac7b7603a198L, 0xe79372e225fe30daL, 0xe6c2b4481cd8568aL, 0xe5f36cb00e5f36ccL, 0xe525982af70c880fL,
                0xe45932d7dc52100fL, 0xe38e38e38e38e38fL, 0xe2c4a6886a4c2e11L, 0xe1fc780e1fc780e3L, 0xe135a9c97500e137L,
                0xe070381c0e070383L, 0xdfac1f74346c5760L, 0xdee95c4ca037ba58L, 0xde27eb2c41f3d9d2L, 0xdd67c8a60dd67c8bL,
                0xdca8f158c7f91ab9L, 0xdbeb61eed19c5959L, 0xdb2f171df770291aL, 0xda740da740da740fL, 0xd9ba4256c0366e92L,
                0xd901b2036406c80fL, 0xd84a598ec9151f44L, 0xd79435e50d79435fL, 0xd6df43fca482f00eL, 0xd62b80d62b80d62dL,
                0xd578e97c3f5fe552L, 0xd4c77b03531dec0eL, 0xd4173289870ac52fL, 0xd3680d3680d3680eL, 0xd2ba083b445250acL,
                0xd20d20d20d20d20eL, 0xd161543e28e50275L, 0xd0b69fcbd2580d0cL, 0xd00d00d00d00d00eL, 0xcf6474a8819ec8eaL,
                0xcebcf8bb5b4169ccL, 0xce168a7725080ce2L, 0xcd712752a886d243L, 0xccccccccccccccceL, 0xcc29786c7607f9a0L,
                0xcb8727c065c393e1L, 0xcae5d85f1bbd6c96L, 0xca4587e6b74f032aL, 0xc9a633fcd967300eL, 0xc907da4e871146aeL,
                0xc86a78900c86a78aL, 0xc7ce0c7ce0c7ce0dL, 0xc73293d789b9f839L, 0xc6980c6980c6980dL, 0xc5fe740317f9d00dL,
                0xc565c87b5f9d4d1dL, 0xc4ce07b00c4ce07cL, 0xc4372f855d824ca7L, 0xc3a13de60495c774L, 0xc30c30c30c30c30dL,
                0xc2780613c0309e03L, 0xc1e4bbd595f6e948L, 0xc152500c152500c2L, 0xc0c0c0c0c0c0c0c2L, 0xc0300c0300c0300dL,
                0xbfa02fe80bfa0300L, 0xbf112a8ad278e8deL, 0xbe82fa0be82fa0c0L, 0xbdf59c91700bdf5bL, 0xbd69104707661aa4L,
                0xbcdd535db1cc5b7cL, 0xbc52640bc52640bdL, 0xbbc8408cd63069a2L, 0xbb3ee721a54d880dL, 0xbab656100bab6562L,
                0xba2e8ba2e8ba2e8dL, 0xb9a7862a0ff46589L, 0xb92143fa36f5e02fL, 0xb89bc36ce3e0453bL, 0xb81702e05c0b8171L,
                0xb79300b79300b794L, 0xb70fbb5a19be365aL, 0xb68d31340e4307d9L, 0xb60b60b60b60b60cL, 0xb58a485518d1e7e5L,
                0xb509e68a9b948220L, 0xb48a39d44685fe98L, 0xb40b40b40b40b40cL, 0xb38cf9b00b38cf9cL, 0xb30f63528917c80cL,
                0xb2927c29da5519d0L, };
        long[] lo = { 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
                0x0000000000000000L, };
        long[] ex = { 1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L,
                -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L,
                -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L,
                -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L,
                -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L,
                -1L, -1L, -1L, -1L, -1L, -1L, -1L, };
        long[] sgn = { 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, };
        for ( int i = 0; i < hi.length; i++ ) {
            INVERSE_2_HI[i] = hi[i];
            INVERSE_2_LO[i] = lo[i];
            INVERSE_2_EX[i] = ex[i];
            INVERSE_2_SGN[i] = sgn[i];
        }
    }

    static {
        long[] hi = { 0xb17217f7d1cf79abL, 0xaf74155120c9011dL, 0xad7a02e1b24efd32L, 0xab83d135dc633301L,
                0xa991713433c2b999L, 0xa7a2d41ad270c9d7L, 0xa5b7eb7cb860fb89L, 0xa3d0a93f45169a4bL, 0xa1ecff97c91e267bL,
                0xa00ce1092e5498c4L, 0x9e304061b5fda91aL, 0x9c5710b8cbb73a42L, 0x9a81456cec642e10L, 0x98aed221a03458b6L,
                0x96dfaabd86fa1647L, 0x9513c36876083696L, 0x934b1089a6dc93c2L, 0x918586c5f5e4bf01L, 0x8fc31afe30b2c6deL,
                0x8e03c24d7300395aL, 0x8c47720791e53314L, 0x8a8e1fb794b09134L, 0x88d7c11e3ad53cdcL, 0x87244c308e670a66L,
                0x8573b71682a7d21bL, 0x83c5f8299e2b4091L, 0x821b05f3b01d6774L, 0x8072d72d903d588cL, 0xfd9ac57bd2442180L,
                0xfa553f7018c966f4L, 0xf7150ab5a09f27f6L, 0xf3da161eed6b9ab1L, 0xf0a450d139366ca7L, 0xed73aa4264b0adebL,
                0xea481236f7d35bb2L, 0xe72178c0323a1a0fL, 0xe3ffce3a2aa64923L, 0xe0e30349fd1cec82L, 0xddcb08dc0717d85cL,
                0xdab7d02231484a93L, 0xd7a94a92466e833cL, 0xd49f69e456cf1b7bL, 0xd19a201127d3c646L, 0xce995f50af69d863L,
                0xcb9d1a189ab56e77L, 0xc8a5431adfb44ca6L, 0xc5b1cd44596fa51fL, 0xc2c2abbb6e5fd570L, 0xbfd7d1dec0a8df70L,
                0xbcf13343e7d9ec7fL, 0xba0ec3b633dd8b0bL, 0xb730773578cb90b3L, 0xb45641f4e350a0d4L, 0xb1801859d56249deL,
                0xaeadeefacaf97d37L, 0xabdfba9e468fd6f9L, 0xa9157039c51ebe72L, 0xa64f04f0b961df78L, 0xa38c6e138e20d834L,
                0xa0cda11eaf46390eL, 0x9e1293b9998c1dadL, 0x9b5b3bb5f088b768L, 0x98a78f0e9ae71d87L, 0x95f783e6e49a9cfcL,
                0x934b1089a6dc93c2L, 0x90a22b6875c6a1f8L, 0x8dfccb1ad35ca6efL, 0x8b5ae65d67db9acfL, 0x88bc74113f23def3L,
                0x86216b3b0b17188cL, 0x8389c3026ac3139dL, 0x80f572b1363487bcL, 0xfcc8e3659d9bcbf1L, 0xf7ad6f26e7ff2efcL,
                0xf29877ff38809097L, 0xed89ed86a44a01abL, 0xe881bf932af3dac3L, 0xe37fde37807b84e3L, 0xde8439c1dec5687cL,
                0xd98ec2bade71e53eL, 0xd49f69e456cf1b7aL, 0xcfb6203844b3209bL, 0xcad2d6e7b80bf915L, 0xc5f57f59c7f46156L,
                0xc11e0b2a8d1e0de1L, 0xbc4c6c2a226399f6L, 0xb780945bab55dceaL, 0xb2ba75f46099cf8fL, 0xadfa035aa1ed8fddL,
                0xa93f2f250dac67d5L, 0xa489ec199dab06f4L, 0x9fda2d2cc9465c52L, 0x9b2fe580ac80b182L, 0x968b08643409ceb9L,
                0x91eb89524e100d28L, 0x8d515bf11fb94f22L, 0x88bc74113f23def7L, 0x842cc5acf1d0344bL, 0xff4489cedeab2ca6L,
                0xf639cc185088fe62L, 0xed393b1c22351281L, 0xe442c00de2591b4cL, 0xdb56446d6ad8df09L, 0xd273b2058de1bd4bL,
                0xc99af2eaca4c457bL, 0xc0cbf17a071f80e9L, 0xb8069857560707a7L, 0xaf4ad26cbc8e5befL, 0xa6988ae903f562f1L,
                0x9defad3e8f732186L, 0x9550252238bd2468L, 0x8cb9de8a32ab3694L, 0x842cc5acf1d0344cL, 0xf7518e0035c3dd92L,
                0xe65b9e6eed965c4fL, 0xd5779687d887e0eeL, 0xc4a550a4fd9a19bbL, 0xb3e4a796a5dac213L, 0xa33576a16f1f4c79L,
                0x9297997c68c1f4e6L, 0x820aec4f3a222397L, 0xe31e9760a5578c6dL, 0xc24929464655f482L, 0xa195492cc0660519L,
                0x8102b2c49ac23a86L, 0xc122451c45155150L, 0x8080abac46f389c4L, 0x0000000000000000L, 0x0000000000000000L,
                0xff805515885e014eL, 0xff015358833c4762L, 0xbee23afc0853b6a8L, 0xfe054587e01f1e2bL, 0x9e75221a352ba751L,
                0xbdc8d83ead88d518L, 0xdcfe013d7c8cbfc5L, 0xfc14d873c1980236L, 0x8d86cc491ecbfe03L, 0x9cf43dcff5eafd2fL,
                0xac52dd7e4726a456L, 0xbba2c7b196e7e224L, 0xcae41876471f5bdeL, 0xda16eb88cb8df5fbL, 0xe93b5c56d85a9083L,
                0xf85186008b1532f9L, 0x83acc1acc7238978L, 0x8b29b7751bd7073bL, 0x929fb17850a0b7beL, 0x9a0ebcb0de8e848eL,
                0xa176e5f5323781d2L, 0xa8d839f830c1fb40L, 0xb032c549ba861d83L, 0xb78694572b5a5cd3L, 0xbed3b36bd8966419L,
                0xc61a2eb18cd907a1L, 0xcd5a1231019d66d7L, 0xd49369d256ab1b1fL, 0xdbc6415d876d0839L, 0xe2f2a47ade3a18a8L,
                0xea189eb3659aeaebL, 0xf1383b7157972f48L, 0xf85186008b153302L, 0xff64898edf55d548L, 0x8338a89652cb714aL,
                0x86bbf3e68472cb2fL, 0x8a3c2c233a156341L, 0x8db956a97b3d0143L, 0x913378c852d65be6L, 0x94aa97c0ffa91a5dL,
                0x981eb8c723fe97f2L, 0x9b8fe100f47ba1d8L, 0x9efe158766314e4fL, 0xa2695b665be8f338L, 0xa5d1b79cd2af2acaL,
                0xa9372f1d0da1bd10L, 0xac99c6ccc1042e94L, 0xaff983853c9e9e40L, 0xb3566a13956a86f4L, 0xb6b07f38ce90e463L,
                0xba07c7aa01bd2648L, 0xbd5c481086c848dbL, 0xc0ae050a1abf56adL, 0xc3fd03290648847dL, 0xc74946f4436a054eL,
                0xca92d4e7a2b5a3adL, 0xcdd9b173efdc1aaaL, 0xd11de0ff15ab18c6L, 0xd45f67e44178c612L, 0xd79e4a7405ff96c3L,
                0xdada8cf47dad236dL, 0xde1433a16c66b14cL, 0xe14b42ac60c60512L, 0xe47fbe3cd4d10d5bL, 0xe7b1aa704e2ee240L,
                0xeae10b5a7ddc8ad8L, 0xee0de5055f63eb01L, 0xf1383b7157972f4aL, 0xf460129552d2ff41L, 0xf7856e5ee2c9b28aL,
                0xfaa852b25bd9b833L, 0xfdc8c36af1f15468L, 0x8073622d6a80e631L, 0x82012ca5a68206d5L, 0x838dc2fe6ac868e7L,
                0x851927139c871af8L, 0x86a35abcd5ba5901L, 0x882c5fcd7256a8c1L, 0x89b438149d4582f5L, 0x8b3ae55d5d30701aL,
                0x8cc0696ea11b7b36L, 0x8e44c60b4ccfd7dcL, 0x8fc7fcf24517946aL, 0x914a0fde7bcb2d0eL, 0x92cb0086fbb1cf75L,
                0x944ad09ef4351af1L, 0x95c981d5c4e924eaL, 0x974715d708e984ddL, 0x98c38e4aa20c27d2L, 0x9a3eecd4c3eaa6aeL,
                0x9bb93315fec2d790L, 0x9d3262ab4a2f4e37L, 0x9eaa7d2e0fb87c35L, 0xa0218434353f1de4L, 0xa197795027409daaL,
                0xa30c5e10e2f613e4L, 0xa4803402004e865cL, 0xa5f2fcabbbc506d8L, 0xa764b99300134d79L, 0xa8d56c396fc1684cL,
                0xaa45161d6e93167bL, 0xabb3b8ba2ad362a1L, 0xad215587a67f0cdfL, 0xae8dedfac04e5282L, 0xaff983853c9e9e3fL,
                0xb1641795ce3ca978L, 0xb2cdab981f0f940bL, 0xb43640f4d8a5761fL, 0xb59dd911aca1ec48L, 0xb70475515d0f1c5eL,
                0xb86a1713c491aeaaL, };
        long[] lo = { 0xc9e3b39803f2f6afL, 0x046d235ee63073dcL, 0x160864fd949b4bd3L, 0xffe6607ba902ef3bL,
                0x0ba4aea614d05700L, 0xcd362382a7688479L, 0x7b6a62a0dec6e072L, 0x09594fab088c0d64L, 0x1b7efae08e597e16L,
                0x69879c5a30cd1241L, 0x04603d87b6df81acL, 0xaa554b2dd4619e63L, 0x4d49f9aaea3cb5e0L, 0x732f89321647b358L,
                0xd61188fbc94e2f14L, 0xb5cbc416a2418011L, 0xbf5bb3b60554e151L, 0x9f92199ed1a4bab0L, 0xe300bf167e95da66L,
                0xcddae1ccce247837L, 0x762ad19415fe25a5L, 0x9eb628dba173c82dL, 0x8a3111a707b6de2cL, 0x85e005d06dbfa8f7L,
                0xb21f9f89c1ab80b2L, 0xb8f6fafe8fbb68b8L, 0xdb0d58c3f7e2ea1eL, 0x7dd1b09c70c40109L, 0xaf05924d258c14c4L,
                0x2780a545a1b54dceL, 0x0a470250d40ebe8eL, 0x248d42f78d3e65d2L, 0x7c66eb6408ff6432L, 0x5391cf4b33e42996L,
                0x39a767a80d6d97e6L, 0xcc4e1653e71d9973L, 0x8eadb651b49ac539L, 0x03e8e1802aba24d5L, 0x940a666c87842842L,
                0xbec20cca6efe2ac4L, 0xcd88bba7d0cee8dfL, 0x7f53bd2e406e66e6L, 0x279d79f51dcc7301L, 0x432f3f4f861ad6a8L,
                0x7d7e9307c70c0667L, 0x048ce7c1a75e341aL, 0xf218fb8f9f9ef27fL, 0x03337789d592e296L, 0x37eda996244bccafL,
                0x2afd17781bb3afeaL, 0x91dc60b2b059a609L, 0xaa1116c3466beb6cL, 0xe756eba00bc33976L, 0x98ce51fff99479cbL,
                0x9dd6e688ebb13b01L, 0x472ea07749ce6bd1L, 0xe164c759686a2207L, 0x54f5275c2d15c21eL, 0xd698298adddd7f30L,
                0x632438273918db7dL, 0x3b035eae273a855cL, 0x5078bbe3d392be24L, 0x64dec34784707838L, 0x025004f3ef063312L,
                0xdf5bb3b60554e151L, 0x8e91aeba609c8876L, 0x9947bdb6ddcaf59aL, 0x7ba5168126a58b99L, 0xbc5a0fe396f40f1cL,
                0x363ceae88f720f1dL, 0x6adda9d2270fa1f3L, 0xedbd0b5b3479d5f2L, 0x8a0cdf301431b60bL, 0x9cd2238f75f969adL,
                0x2b020fa1820c948dL, 0x09d49f96cb88317aL, 0x2524848e3443e03fL, 0x5e9a750b6b68781cL, 0x9d57da945b5d0aa6L,
                0xd0a98f2ad65bee96L, 0x5f53bd2e406e66e7L, 0x18cb02f33f79c16bL, 0xcc507fb7a3d0bf69L, 0x9a8b6997a402bf30L,
                0xda631e830fd308feL, 0x276ebcfb2016a433L, 0xb4c7bc3d32750fd9L, 0x243c2e77904afa76L, 0x549767e410316d2bL,
                0x9ad2fb8d48054addL, 0x59fb6cf0ecb411b7L, 0x6b2b9565f5355180L, 0x011a5b944aca8705L, 0xd5c0da506a088482L,
                0xbfd3df5c52d67e77L, 0xa0713268840cbcbbL, 0x9c5a0fe396f40f19L, 0x6fecdfa819b96092L, 0xe17bd40d8d9291ecL,
                0x5066e87f2c0f733dL, 0xff4e2e660317d55fL, 0xe96ab34ce0bccd10L, 0x28112e35a60e636fL, 0x36bbf837b4d320c6L,
                0xeaf51f66692844b2L, 0x396ffdf76a147cc2L, 0x0a677b4c8bec22e0L, 0x9e8b8b88a14ff0c9L, 0x7e858f08597b3a68L,
                0x476d3b5b45f6ca02L, 0x658e5a0b811c596dL, 0x97c9859530a4514cL, 0x1fecdfa819b96094L, 0x606d89093278a931L,
                0x609f5fe2058d5ff2L, 0x49dda17056e45ebbL, 0x3e97660a23cc5402L, 0x07cca0bcc06c2f8eL, 0x121016bd904dc95aL,
                0x610db3d4dd423bc9L, 0xb9e3aea6c444eef6L, 0xf9eb2f284f31c35aL, 0xda5f3cc0b3251da6L, 0x4a18dff7cdb4ae33L,
                0x91d082dce3ddcd08L, 0xb16137f09a002b0eL, 0x662d417ced0079c9L, 0x0000000000000000L, 0x0000000000000000L,
                0x435ab4da6a5bb50fL, 0xbb481c8ee1416999L, 0xa89782c20df350c2L, 0xf6d3a69bd5eab72fL, 0x452b7ea62f2198eaL,
                0x7faa638b5e00ee90L, 0x632dbac46f30d009L, 0xc7e09e3de453f5fcL, 0xf1776453b7e82558L, 0x2ad90155c8a7236aL,
                0xa47a963a91bb3018L, 0xe7950f7252c163cfL, 0x91d00a417e330f8eL, 0x28a63ecfb66e94c0L, 0xce2992bfea38e76bL,
                0xe64b8b7759978998L, 0x5a5333c45b7f442eL, 0x02e0b9ee992f2372L, 0x5b4d3807660516a4L, 0x2c1bb082689ba814L,
                0xdcf935996c92e8d4L, 0x4c7343517c8ac264L, 0x774e27bc92ce3373L, 0x24cdcf68cdb2067cL, 0x7c0644d7d9ed08b4L,
                0xe5a1532f6d5a1ac1L, 0x761e3e7b171e44b2L, 0x9e9154e1d5263cdaL, 0x3e33c0c9f8824f54L, 0xa0bf7c0b0d8bb4efL,
                0x93b2a3b21f448259L, 0x543fff0ff4f0aaf1L, 0x5e4b8b7759978993L, 0x428ccfc99271dffaL, 0xb247eb86498c2ce7L,
                0x0b8bd20615747126L, 0x9027c74fe0e6f64fL, 0xf023472cd739f9e1L, 0x977e3013d10f7525L, 0x4ee3880fb7d34429L,
                0x1f1c134fb702d433L, 0x04b62af189fcba0dL, 0x4d71827efe892fc8L, 0x4eca87c3f0f06211L, 0x8837986ceabfbed6L,
                0x580eb71e58cd36e5L, 0x3dd557528315838dL, 0x5f105039091dd7f5L, 0x471b1e1574d9fd55L, 0x7bb2e265d0de37e1L,
                0x43f9d57b324bd05fL, 0xbb596b5030403242L, 0x2f7f8c5fa9c50d76L, 0x30480bee4cbbd698L, 0xf4f5cb531201c0d3L,
                0xc983a9c5c4b3b135L, 0x8863e007c184a1e7L, 0xd88d83d4cc613f21L, 0x5486e73c615158b4L, 0x1300c9be67ae5da0L,
                0xdffb833c3409ee7eL, 0xde744870f54f0f18L, 0x4e38eb8092a01f06L, 0x2ec0f797fdcd125cL, 0xb40faab6d2ad0841L,
                0x806b2fc9a8038790L, 0x90a33316df83ba5aL, 0xb43fff0ff4f0aaf1L, 0xe62e3201bb2bbdceL, 0x76f2a1b84190a7dcL,
                0xa6dbfa03186e0666L, 0x0a3361bca696504aL, 0xe897009015316073L, 0x8fde85afdd2bc88aL, 0x1a3fcbdef40100cbL,
                0x67bd00c38061c51fL, 0x5481c3cbd925ccd2L, 0x39055a6598e7c29eL, 0x34531dba493eb5a6L, 0xc63eab8837170480L,
                0x94361c9a28d38a6aL, 0x1473aa01c7778679L, 0x380cbe769f2c6793L, 0xc429ed3aea197a60L, 0xa29d47c50b1182d0L,
                0xa49827e081cb16baL, 0x45404f5aa577d6b4L, 0x6648d42840d9e6fbL, 0x846767ec990d7333L, 0xdb3a7f6e6087b947L,
                0x7f589fba0865790fL, 0xa1ae6ba06846fae0L, 0xff472bc6ce648a7dL, 0xd493efa632530accL, 0x1dd1d4a6df960357L,
                0x9bd9bd99e39a20b3L, 0x31cbe0e8824116cdL, 0x68ca4fb7ec323d74L, 0x0d04d10474301862L, 0x01eb067d578c4756L,
                0x9b081cf72249f5b2L, 0x1db6506cc17a01f5L, 0xe890422cb86b7cb1L, 0xac707b8ffc22b3e8L, 0xc5105039091dd7f8L,
                0xfaf915300e517393L, 0xc857c77dc1df600fL, 0xf5f080a71c34b25dL, 0x1d2664cf09a0c1bfL, 0x4c98c6b8be17818dL,
                0xd37ee2872a6f1cd6L, };
        long[] ex = { -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L,
                -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L,
                -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L,
                -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L,
                -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -4L, -4L, -4L, -4L, -4L,
                -4L, -4L, -4L, -4L, -4L, -4L, -4L, -4L, -4L, -4L, -5L, -5L, -5L, -5L, -5L, -5L, -5L, -5L, -6L, -6L, -6L,
                -6L, -7L, -7L, 127L, 127L, -9L, -8L, -7L, -7L, -6L, -6L, -6L, -6L, -5L, -5L, -5L, -5L, -5L, -5L, -5L,
                -5L, -4L, -4L, -4L, -4L, -4L, -4L, -4L, -4L, -4L, -4L, -4L, -4L, -4L, -4L, -4L, -4L, -4L, -4L, -3L, -3L,
                -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L,
                -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -3L, -2L, -2L, -2L, -2L, -2L, -2L,
                -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L,
                -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, -2L, };
        long[] sgn = { 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L,
                1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L,
                1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L,
                1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L,
                1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, };
        for ( int i = 0; i < hi.length; i++ ) {
            LOG_INV_2_HI[i] = hi[i];
            LOG_INV_2_LO[i] = lo[i];
            LOG_INV_2_EX[i] = ex[i];
            LOG_INV_2_SGN[i] = sgn[i];
        }
    }

    static {
        long[] hi = { 0x99df88a0430813caL, 0xaaa02d43f696c3e4L, 0xba2e7a1eaf856174L, 0xccccccb9ec017492L,
                0xe38e38e3807cfa4bL, 0xfffffffffff924ccL, 0x924924924924911dL, 0xaaaaaaaaaaaaaaaaL, 0xccccccccccccccccL,
                0xffffffffffffffffL, 0xaaaaaaaaaaaaaaaaL, 0xffffffffffffffffL, 0x8000000000000000L, };
        long[] lo = { 0xa1cffb6e966a70f6L, 0x4dbe754667b6bc48L, 0x70e5c5a5ebbe0226L, 0xf934e28d924e76d4L,
                0xc976e6cbd22e203fL, 0x05b308e39fa7dfb5L, 0x862bc3d33abb3649L, 0x6637fd4b19743eecL, 0xccc2ca18b08fe343L,
                0xffffff2245823ae0L, 0xaaaaaaaaa5c48b54L, 0xffffffffffffebd8L, 0x0000000000000000L, };
        long[] ex = { -4L, -4L, -4L, -4L, -4L, -4L, -3L, -3L, -3L, -3L, -2L, -2L, 0L, };
        long[] sgn = { 0L, 1L, 0L, 1L, 0L, 1L, 0L, 1L, 0L, 1L, 0L, 1L, 0L, };
        for ( int i = 0; i < hi.length; i++ ) {
            P_2_HI[i] = hi[i];
            P_2_LO[i] = lo[i];
            P_2_EX[i] = ex[i];
            P_2_SGN[i] = sgn[i];
        }
    }

    // (Tables follow; see /tmp/extract_log_tables.py — content concatenated
    // verbatim below this comment by the build script.)
    // ============================================================
    //  Tables — extracted from coremath/log.c + coremath/log_dint.h
    //  via /tmp/extract_log_tables.py (Phase 2i.6 P2I6 pattern,
    //  generated 2026-04-29). Do not hand-edit; re-run the script.
    // ============================================================

    private LogKernel() {
    }

    static double log(double x) {
        final long u = Double.doubleToRawLongBits(x);
        // C: int e = (v.u >> 52) - 0x3ff;
        // The C code uses `int` so e wraps mod 2^32 for very large biased
        // exponents, giving a value with magnitude up to ~0xfff. Java `int`
        // wraps the same way; cast unsigned long to int after extracting.
        int e = (int) ((u >>> 52) & 0xfffL) - 0x3ff;

        // Special branches: x ≤ 0, NaN/Inf, or subnormal.
        // C: if (e >= 0x400 || e == -0x3ff)
        if ( e >= 0x400 || e == -0x3ff ) {
            // x <= 0?  C uses: if (x <= 0.0) — covers ±0 and negatives
            //   (not NaN, since NaN comparisons are false).
            if ( x <= 0.0 ) {
                if ( x < 0.0 ) {
                    return 0.0 / 0.0; // NaN
                } else {
                    return 1.0 / -0.0; // -Inf, raises DivByZero
                }
            }
            if ( e == 0x400 || e == 0xc00 ) {
                // +Inf or NaN — return x as-is.
                return x;
            }
            if ( e == -0x3ff ) {
                // Subnormal: scale up by 2^52 so the mantissa shifts into the
                // normal range, then re-extract e.
                final double xs = x * 0x1p52;
                final long us = Double.doubleToRawLongBits(xs);
                e = (int) ((us >>> 52) & 0xfffL) - 0x3ff - 52;
                return logFinite(xs, us, e);
            }
        }
        return logFinite(x, u, e);
    }

    /**
     * Common path for finite positive x. Caller has already extracted the unbiased exponent {@code e}; {@code u} holds
     * the raw IEEE-754 bits of {@code x} (or of the scaled-up subnormal). Normalises x into [1, 2) via mantissa rebias,
     * runs the fast path, and falls through to the accurate path if the error band straddles a rounding boundary.
     */
    private static double logFinite(double xOrig, long u, int e) {
        // Normalise mantissa to [1, 2): set biased exponent to 0x3ff.
        final long vBits = (0x3ffL << 52) | (u & 0xfffffffffffffL);
        final double v = Double.longBitsToDouble(vBits);

        final double[] hl = new double[2];
        crLogFast(hl, e, v, vBits);
        final double h = hl[0];
        final double l = hl[1];

        // err = 0x1.b6p-69 (Gappa-proven absolute error bound).
        final double err = 0x1.b6p-69;
        final double left = h + (l - err);
        final double right = h + (l + err);
        if ( left == right )
            return left;

        // Accurate path — uses the original x (not the scaled subnormal copy)
        // because dint_fromd extracts e from x's IEEE-754 bits directly. For
        // subnormals, the C reference passes the same scaled value v, then
        // dint_fromd's fast_extract figures out e via __builtin_clzll. The
        // Java port mirrors that.
        return crLogAccurate(xOrig);
    }

    /**
     * cr_log_fast: produces {@code (h, l)} double-double approximation of {@code log(2^e * v)} where
     * {@code 1 <= v < 2}. Output absolute error ≤ 0x1.b6p-69.
     */
    private static void crLogFast(double[] out_hl, int e, double v, long vBits) {
        // C: uint64_t m = 0x10000000000000 + (v.u & 0xfffffffffffff);
        //    The leading-1 bit makes m = mantissa with implicit-1 prepended,
        //    so x = m / 2^52 (in the original v's scale).
        long m = 0x10000000000000L + (vBits & 0xfffffffffffffL);

        // C: int c = m >= 0x16a09e667f3bcd; (sqrt(2) test)
        //    If m is past sqrt(2), divide by 2 (incrementing e) so r * (m/2)
        //    stays close to 1 and z stays small.
        final int c = (Long.compareUnsigned(m, 0x16a09e667f3bcdL) >= 0) ? 1 : 0;
        e += c;

        // cy[c]: 1.0 if c==0 else 0.5 ; cm[c]: 43 if c==0 else 44.
        final double cyC = (c == 0) ? 1.0 : 0.5;
        final int cmC = (c == 0) ? 43 : 44;

        final int i = (int) (m >>> cmC);
        final double y = v * cyC;

        // OFFSET = 362, table indexed at i - OFFSET.
        final int idx = i - 362;
        final double r = INVERSE[idx];
        final double l1 = LOG_INV_H[idx];
        final double l2 = LOG_INV_L[idx];

        // z = fma(r, y, -1) — proven exact in CORE-MATH source
        final double z = Math.fma(r, y, -1.0);
        final double z2 = z * z;

        // Polynomial P(z): P[0]=1, then degree 2..6.
        final double p45 = Math.fma(P_COEF[5], z, P_COEF[4]);
        final double p23 = Math.fma(P_COEF[3], z, P_COEF[2]);
        double ph = Math.fma(p45, z2, p23);
        ph = Math.fma(ph, z, P_COEF[1]);
        ph = ph * z2;

        // log(2) split: high (integer*2^-42) + low.
        final double log2_h = 0x1.62e42fefa38p-1;
        final double log2_l = 0x1.ef35793c7673p-45;

        final double ee = e;
        // fast_two_sum(h, l, fma(ee, log2_h, l1), z)
        final double a = Math.fma(ee, log2_h, l1);
        final double b = z;
        final double hOut = a + b;
        final double err = hOut - a;
        double lOut = b - err;

        // l = ph + (l + l2)
        lOut = ph + (lOut + l2);
        // l = fma(ee, log2_l, l)
        lOut = Math.fma(ee, log2_l, lOut);

        out_hl[0] = hOut;
        out_hl[1] = lOut;
    }

    /**
     * cr_log_accurate: dint64-precision fallback. Converts x to dint64, applies a 240-entry reciprocal table to reduce,
     * evaluates a 13-term dint64 polynomial, and assembles the final result.
     */
    private static double crLogAccurate(double x) {
        if ( x == 1.0 )
            return 0.0;

        final long[] X = new long[4]; // {hi, lo, ex, sgn}
        dintFromD(X, x);
        // log_2 reads X.ex and writes Y = log(2^E * x_normalized).
        final long[] Y = new long[4];
        log2Inner(Y, X);
        return dintToD(Y);
    }

    /**
     * Mirrors CORE-MATH's {@code log_2}: takes {@code x} as dint64 (in {@code X}), computes {@code log(x)} into
     * {@code R}.
     *
     * <p>Note: this routine mutates {@code X.ex} (matching the C reference's
     * pointer-mutating behaviour). Callers don't reuse {@code X} afterwards.
     */
    private static void log2Inner(long[] R, long[] X) {
        long E = X[2]; // X.ex
        // C: i = x->hi >> 55; → top 9 bits of hi as int (fits in uint16_t).
        int i = (int) ((X[0] >>> 55) & 0x1ffL);

        // C: if (x->hi > 0xb504f333f9de6484) { E++; i = i >> 1; }
        if ( Long.compareUnsigned(X[0], 0xb504f333f9de6484L) > 0 ) {
            E++;
            i = i >> 1;
        }
        X[2] = X[2] - E;

        // z = x * _INVERSE_2[i - 128]
        final long[] z = new long[4];
        final long[] inv = loadDint(INVERSE_2_HI, INVERSE_2_LO, INVERSE_2_EX, INVERSE_2_SGN, i - 128);
        logMulDint(z, X, inv);

        // z = z + M_ONE  (i.e. z - 1)
        final long[] mone = { M_ONE_HI, M_ONE_LO, M_ONE_EX, M_ONE_SGN };
        // For add_dint, we pass M_ONE first (C: add_dint(&z, &M_ONE, &z)).
        // Result alias-safe? C's add_dint treats r as separate output; we
        // implement defensively (caller uses fresh long[4] for r).
        final long[] zNext = new long[4];
        logAddDint(zNext, mone, z);
        z[0] = zNext[0];
        z[1] = zNext[1];
        z[2] = zNext[2];
        z[3] = zNext[3];

        // R = E * LOG2  (mul_dint_2)
        final long[] log2 = { LOG2_HI, LOG2_LO, LOG2_EX, LOG2_SGN };
        logMulDint2(R, E, log2);

        // p = polynomial(z) via the 13-step Horner chain p_2(p, z)
        final long[] p = new long[4];
        polyP2(p, z);

        // p = p + _LOG_INV_2[i - 128]
        final long[] logInv2 = loadDint(LOG_INV_2_HI, LOG_INV_2_LO, LOG_INV_2_EX, LOG_INV_2_SGN, i - 128);
        final long[] pNext = new long[4];
        logAddDint(pNext, logInv2, p);
        p[0] = pNext[0];
        p[1] = pNext[1];
        p[2] = pNext[2];
        p[3] = pNext[3];

        // R = p + R
        final long[] rNext = new long[4];
        logAddDint(rNext, p, R);
        R[0] = rNext[0];
        R[1] = rNext[1];
        R[2] = rNext[2];
        R[3] = rNext[3];
    }

    /**
     * 13-step Horner-form polynomial in dint64: r = (((P_2[0]*z + P_2[1])*z + P_2[2])*z + ... + P_2[12])*z. Mutates r
     * at each step (matching C behavior — mul_dint then add_dint into r).
     */
    private static void polyP2(long[] r, long[] z) {
        // r = P_2[0]
        copyDintFromTable(r, P_2_HI, P_2_LO, P_2_EX, P_2_SGN, 0);

        // For each step i = 1..12: r = r*z + P_2[i]
        // mul_dint(r, z, r) then add_dint(r, &P_2[i], r)
        final long[] mulOut = new long[4];
        final long[] addOut = new long[4];
        final long[] coeff = new long[4];
        for ( int i = 1; i <= 12; i++ ) {
            logMulDint(mulOut, z, r);
            copyDintFromTable(coeff, P_2_HI, P_2_LO, P_2_EX, P_2_SGN, i);
            logAddDint(addOut, coeff, mulOut);
            r[0] = addOut[0];
            r[1] = addOut[1];
            r[2] = addOut[2];
            r[3] = addOut[3];
        }
        // Final: mul_dint(r, z, r) (no add).
        logMulDint(mulOut, z, r);
        r[0] = mulOut[0];
        r[1] = mulOut[1];
        r[2] = mulOut[2];
        r[3] = mulOut[3];
    }

    private static void copyDintFromTable(long[] out, long[] hiTbl, long[] loTbl, long[] exTbl, long[] sgnTbl, int i) {
        out[0] = hiTbl[i];
        out[1] = loTbl[i];
        out[2] = exTbl[i];
        out[3] = sgnTbl[i];
    }

    private static long[] loadDint(long[] hiTbl, long[] loTbl, long[] exTbl, long[] sgnTbl, int i) {
        return new long[] { hiTbl[i], loTbl[i], exTbl[i], sgnTbl[i] };
    }

    /** cmp_dint: lexicographic by (ex, hi, lo). Matches log_dint.h:122. */
    private static int cmpDint(long aHi, long aLo, long aEx, long bHi, long bLo, long bEx) {
        final int c1 = Long.compare(aEx, bEx);
        if ( c1 != 0 )
            return c1;
        final int c2 = Long.compareUnsigned(aHi, bHi);
        if ( c2 != 0 )
            return c2;
        return Long.compareUnsigned(aLo, bLo);
    }

    /**
     * add_dint: matches log_dint.h:128-195. Adds two dint64 values into out. Recursion
     * {@code case -1: add_dint(r, b, a)} is unrolled into a swap.
     */
    private static void logAddDint(long[] r, long[] a, long[] b) {
        // Locals so we can swap (a,b) without aliasing issues.
        long aHi = a[0], aLo = a[1], aEx = a[2], aSgn = a[3];
        long bHi = b[0], bLo = b[1], bEx = b[2], bSgn = b[3];

        // C: if (!(a->hi | a->lo)) { cp_dint(r, b); return; }
        if ( (aHi | aLo) == 0L ) {
            r[0] = bHi;
            r[1] = bLo;
            r[2] = bEx;
            r[3] = bSgn;
            return;
        }
        if ( (bHi | bLo) == 0L ) {
            r[0] = aHi;
            r[1] = aLo;
            r[2] = aEx;
            r[3] = aSgn;
            return;
        }

        final int c = cmpDint(aHi, aLo, aEx, bHi, bLo, bEx);
        if ( c == 0 ) {
            if ( (aSgn ^ bSgn) != 0L ) {
                // ZERO entry (per log_dint.h:108)
                r[0] = 0L;
                r[1] = 0L;
                r[2] = 0L;
                r[3] = 0L;
                return;
            }
            r[0] = aHi;
            r[1] = aLo;
            r[2] = aEx + 1L;
            r[3] = aSgn;
            return;
        }
        if ( c < 0 ) {
            // Swap
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
        // From now on |A| > |B| (lexicographically).

        long AHi = aHi, ALo = aLo;
        long BHi = bHi, BLo = bLo;
        long m_ex = aEx;

        if ( aEx > bEx ) {
            // C: int sh = a->ex - b->ex;
            //    if (sh <= 128) B.r += 0x1 & (B.r >> (sh - 1));
            //    if (sh <  128) B.r = B.r >> sh; else B.r = 0;
            // (round-to-nearest before shift)
            long sh = aEx - bEx;
            if ( sh <= 128L ) {
                // u128 round-bit: bit (sh-1). For sh in [1, 128], the round
                // bit is at position sh-1 of B (zero-indexed from LSB).
                final long roundBit = u128GetBit(BHi, BLo, (int) (sh - 1L));
                if ( roundBit != 0L ) {
                    final long[] sum = u128Add(BHi, BLo, 0L, 1L);
                    BHi = sum[0];
                    BLo = sum[1];
                }
            }
            if ( sh < 128L ) {
                final long[] shr = u128ShiftRight(BHi, BLo, (int) sh);
                BHi = shr[0];
                BLo = shr[1];
            } else {
                BHi = 0L;
                BLo = 0L;
            }
        }

        long CHi, CLo;
        long sgn = aSgn;

        if ( (aSgn ^ bSgn) != 0L ) {
            // Different signs: C = A - B
            final long[] sub = u128Sub(AHi, ALo, BHi, BLo);
            CHi = sub[0];
            CLo = sub[1];
        } else {
            // Same signs: C = A + B; check overflow.
            final long[] sum = u128Add(AHi, ALo, BHi, BLo);
            // Overflow detection: did the high-word add carry past bit 127?
            // C: if (addu_128(A, B, &C)) { C.r += C.l & 0x1; C.r = ((u128)1 << 127) | (C.r >> 1); m_ex++; }
            // addu_128 returns 1 if the 128-bit add produced a carry-out.
            final boolean overflow = u128AddCarry(AHi, ALo, BHi, BLo);
            CHi = sum[0];
            CLo = sum[1];
            if ( overflow ) {
                // C.r += C.l & 0x1
                if ( (CLo & 0x1L) != 0L ) {
                    final long[] inc = u128Add(CHi, CLo, 0L, 1L);
                    CHi = inc[0];
                    CLo = inc[1];
                }
                // C.r = (1 << 127) | (C.r >> 1)
                final long[] shr = u128ShiftRight(CHi, CLo, 1);
                CHi = shr[0] | (1L << 63);
                CLo = shr[1];
                m_ex++;
            }
        }

        // ex = C.h ? clz(C.h) : 64 + (C.l ? clz(C.l) : a->ex);
        long ex;
        if ( CHi != 0L ) {
            ex = Long.numberOfLeadingZeros(CHi);
        } else if ( CLo != 0L ) {
            ex = 64L + Long.numberOfLeadingZeros(CLo);
        } else {
            // Both halves zero — match the C path (which would shift by m_ex,
            // producing zero output).
            ex = 64L + aEx;
        }

        // Shift left by ex.
        final long[] shl = u128ShiftLeft(CHi, CLo, (int) ex);
        CHi = shl[0];
        CLo = shl[1];

        r[0] = CHi;
        r[1] = CLo;
        r[2] = m_ex - ex;
        r[3] = sgn;
    }

    /**
     * mul_dint: matches log_dint.h:198-223. 128-bit precision multiply.
     */
    private static void logMulDint(long[] r, long[] a, long[] b) {
        final long aHi = a[0], aLo = a[1];
        final long bHi = b[0], bLo = b[1];

        // t = aHi * bHi (128-bit)
        long tHi = unsignedMulHigh(aHi, bHi);
        long tLo = aHi * bHi;

        // m1 = aHi * bLo (128-bit)
        final long m1Hi = unsignedMulHigh(aHi, bLo);
        final long m1Lo = aHi * bLo;
        // m2 = aLo * bHi (128-bit)
        final long m2Hi = unsignedMulHigh(aLo, bHi);
        final long m2Lo = aLo * bHi;

        // m = m1 + m2 (128-bit, with overflow into t.h)
        final long[] m = u128Add(m1Hi, m1Lo, m2Hi, m2Lo);
        final boolean mOverflow = u128AddCarry(m1Hi, m1Lo, m2Hi, m2Lo);
        final long mHi = m[0], mLo = m[1];

        // C: t.h += addu_128(m1, m2, &m); — overflow flag added to t.h.
        if ( mOverflow ) {
            tHi += 1L;
        }

        // t.r += m.h  — adds m.h into t's low word (128-bit add).
        // Equivalent to: t = t + (m.h promoted to 128-bit with low=m.h, high=0).
        // But m.h alone is uint64_t; "t.r += m.h" treats it as adding to the
        // 128-bit t value. So tLo += mHi, with carry into tHi.
        final long newLo = tLo + mHi;
        if ( Long.compareUnsigned(newLo, tLo) < 0 ) {
            tHi += 1L;
        }
        tLo = newLo;

        // C: ex = !(t.h >> 63); if (ex) t.r = t.r << 1;
        final long topBit = (tHi >>> 63) & 1L;
        long ex = (topBit == 0L) ? 1L : 0L;
        if ( ex == 1L ) {
            // Left shift t by 1.
            tHi = (tHi << 1) | (tLo >>> 63);
            tLo = tLo << 1;
        }

        // C: t.r += (m.l >> 63);
        final long roundBit = mLo >>> 63;
        if ( roundBit != 0L ) {
            final long sumLo = tLo + 1L;
            if ( Long.compareUnsigned(sumLo, tLo) < 0 ) {
                tHi += 1L;
            }
            tLo = sumLo;
        }

        r[0] = tHi;
        r[1] = tLo;
        // C: r->ex = a->ex + b->ex - ex + 1;
        r[2] = a[2] + b[2] - ex + 1L;
        r[3] = a[3] ^ b[3];
    }

    /**
     * mul_dint_2: integer × dint64. Matches log_dint.h:226-256.
     */
    private static void logMulDint2(long[] r, long b, long[] a) {
        if ( b == 0L ) {
            r[0] = 0L;
            r[1] = 0L;
            r[2] = 0L;
            r[3] = 0L;
            return;
        }

        final long aHi = a[0], aLo = a[1], aEx = a[2], aSgn = a[3];
        final long c = (b < 0L) ? -b : b;
        // sgn = b<0 ? !aSgn : aSgn
        r[3] = (b < 0L) ? (aSgn ^ 1L) : aSgn;

        // t = aHi * c (128-bit)
        long tHi = unsignedMulHigh(aHi, c);
        long tLo = aHi * c;

        // C: int m = t.h ? __builtin_clzl(t.h) : 64;
        // m is the leading-zero count of the high word; clamp at 64 if t.h==0.
        // Java numberOfLeadingZeros(0) == 64 already.
        int m = Long.numberOfLeadingZeros(tHi);

        // t.r = t.r << m
        final long[] tShifted = u128ShiftLeft(tHi, tLo, m);
        tHi = tShifted[0];
        tLo = tShifted[1];

        // l.r = (aLo * c) (128-bit), then l.r = (l.r << (m - 1)) >> 63.
        final long lProdHi = unsignedMulHigh(aLo, c);
        final long lProdLo = aLo * c;
        // C: l.r = (l.r << (m-1)) >> 63;
        // For m == 0 the shift is `<< -1` in C — undefined. The CORE-MATH
        // routine always produces m >= 1 in practice (since a->hi has the
        // top bit set after dint_fromd normalization). But m == 0 happens
        // when t.h's top bit is 1 (no left shift needed). When m == 0, the
        // first shift is by -1 which is UB; in CORE-MATH this is "TODO:
        // FIXME". For our purposes, m >= 0 always. When m == 0 we treat
        // (l.r << -1) >> 63 as effectively zero (CORE-MATH behavior).
        long lFinal;
        if ( m == 0 ) {
            // Edge case — CORE-MATH's UB; approximate as 0 (matches typical
            // x86_64 behavior where `<< -1` is `<< 63`, then `>> 63` retains
            // the top bit). In practice the call site never produces m==0
            // for the operands we feed; we degenerate to 0 and rely on the
            // probe oracle to catch any case where this matters.
            lFinal = 0L;
        } else {
            // (l.r << (m - 1)) >> 63 — single bit at position 64-m of the
            // pre-shift value, projected to bit 0.
            // l.r << (m-1): shifts left, then >> 63 projects the top bit.
            // After left shift by (m-1), the bit that ends up at position
            // 127 of the 128-bit u128 is the original bit at position 128-m.
            // Then >> 63 turns that into the LSB.
            // Implementation: take the two-word l, shift left by (m-1),
            // extract bit 127 (top bit of the new high word), and that's
            // the answer (0 or 1).
            final long[] lShifted = u128ShiftLeft(lProdHi, lProdLo, m - 1);
            lFinal = lShifted[0] >>> 63;
        }

        // t = l + t  → adds lFinal (128-bit value with high=lFinal, low=0?
        // No — l after the shift+>>63 is a single-bit u128 with value either
        // 0 or 1, in its LOW word. Convention: l.r = either 0 or 1. So we
        // add 1 to t (low word) if lFinal == 1).
        // C: if (addu_128(l, t, &t)) { ... rounding step ... }
        // Add (lFinal_lo=lFinal, lFinal_hi=0) to (tHi, tLo).
        final long[] sum = u128Add(tHi, tLo, 0L, lFinal);
        final boolean overflow = u128AddCarry(tHi, tLo, 0L, lFinal);
        tHi = sum[0];
        tLo = sum[1];
        if ( overflow ) {
            // C: t.r += t.r & 0x1; t.r = ((u128)1 << 127) | (t.r >> 1); m--;
            if ( (tLo & 0x1L) != 0L ) {
                final long[] inc = u128Add(tHi, tLo, 0L, 1L);
                tHi = inc[0];
                tLo = inc[1];
            }
            final long[] shr = u128ShiftRight(tHi, tLo, 1);
            tHi = shr[0] | (1L << 63);
            tLo = shr[1];
            m--;
        }

        r[0] = tHi;
        r[1] = tLo;
        r[2] = aEx + 64L - m;
        // r[3] already set above.
    }

    /**
     * dint_fromd matching log.c:780-789 (NOT log_dint.h's). The log.c version uses {@code 0x3ff} bias (in the local
     * {@code fast_extract} at lines 770-777), giving exponents in the range [-0x3ff, 0x3ff].
     */
    private static void dintFromD(long[] a, double b) {
        // fast_extract (log.c local, line 770):
        //   e = (u >> 52) & 0x7ff
        //   m = (u & ~0ul>>12) + (e ? 1<<52 : 0)
        //   e = e - 0x3ff
        final long u = Double.doubleToRawLongBits(b);
        long e = (u >>> 52) & 0x7ffL;
        long m = (u & 0x000fffffffffffffL) + ((e != 0L) ? (1L << 52) : 0L);
        e = e - 0x3ffL;

        // dint_fromd body: clz, shift, set sgn, sub from e
        final int t = Long.numberOfLeadingZeros(m);
        a[3] = (b < 0.0) ? 1L : 0L;
        a[0] = m << t;
        a[2] = e - ((t > 11) ? (long) (t - 12) : 0L);
        a[1] = 0L;
    }

    /**
     * dint_tod matching log.c:793-818 (NOT log_dint.h's). log's local version does NOT subnormalize and assumes ex ∈
     * (-1023, 1023] — which holds for log's outputs by construction.
     */
    private static double dintToD(long[] a) {
        final long aHi = a[0], aLo = a[1], aEx = a[2], aSgn = a[3];
        // r = (a.hi >> 11) | (0x3ff << 52)
        long rBits = (aHi >>> 11) | (0x3ffL << 52);

        double rd = 0.0;
        if ( ((aHi >>> 10) & 0x1L) != 0L ) {
            rd += 0x1p-53;
        }
        if ( (aHi & 0x3ffL) != 0L || aLo != 0L ) {
            rd += 0x1p-54;
        }

        rBits = rBits | (aSgn << 63);
        double rf = Double.longBitsToDouble(rBits);
        rf += (aSgn == 0L) ? rd : -rd;

        // C: e.u = ((a->ex + 1023) & 0x7ff) << 52;
        final long eBits = ((aEx + 1023L) & 0x7ffL) << 52;
        final double e = Double.longBitsToDouble(eBits);

        return rf * e;
    }

    /**
     * Returns {hi, lo} of (aHi:aLo) + (bHi:bLo). Carry-out is dropped here; use {@link #u128AddCarry} to detect it
     * explicitly.
     */
    private static long[] u128Add(long aHi, long aLo, long bHi, long bLo) {
        final long sumLo = aLo + bLo;
        final long carry = (Long.compareUnsigned(sumLo, aLo) < 0) ? 1L : 0L;
        final long sumHi = aHi + bHi + carry;
        return new long[] { sumHi, sumLo };
    }

    /** Returns true if (aHi:aLo) + (bHi:bLo) overflows past bit 127. */
    private static boolean u128AddCarry(long aHi, long aLo, long bHi, long bLo) {
        final long sumLo = aLo + bLo;
        final long carry = (Long.compareUnsigned(sumLo, aLo) < 0) ? 1L : 0L;
        final long sumHi = aHi + bHi + carry;
        // Overflow iff sumHi < aHi (unsigned), or carry was set and aHi+bHi == sumHi-carry.
        // Equivalent: top bit of sum is determined by overflow detection on the high words.
        if ( Long.compareUnsigned(sumHi, aHi) < 0 )
            return true;
        return carry != 0L && Long.compareUnsigned(sumHi, aHi) == 0;
    }

    /** Returns {hi, lo} of (aHi:aLo) - (bHi:bLo). */
    private static long[] u128Sub(long aHi, long aLo, long bHi, long bLo) {
        final long borrow = (Long.compareUnsigned(aLo, bLo) < 0) ? 1L : 0L;
        final long diffLo = aLo - bLo;
        final long diffHi = aHi - bHi - borrow;
        return new long[] { diffHi, diffLo };
    }

    /** Returns {hi, lo} of (aHi:aLo) >> n. n must be in [0, 128]. */
    private static long[] u128ShiftRight(long aHi, long aLo, int n) {
        if ( n == 0 )
            return new long[] { aHi, aLo };
        if ( n >= 128 )
            return new long[] { 0L, 0L };
        if ( n >= 64 ) {
            final int sh = n - 64;
            final long newLo = (sh == 0) ? aHi : (aHi >>> sh);
            return new long[] { 0L, newLo };
        }
        // 0 < n < 64
        final long newLo = (aLo >>> n) | (aHi << (64 - n));
        final long newHi = aHi >>> n;
        return new long[] { newHi, newLo };
    }

    /** Returns {hi, lo} of (aHi:aLo) << n. n must be in [0, 128]. */
    private static long[] u128ShiftLeft(long aHi, long aLo, int n) {
        if ( n == 0 )
            return new long[] { aHi, aLo };
        if ( n >= 128 )
            return new long[] { 0L, 0L };
        if ( n >= 64 ) {
            final int sh = n - 64;
            final long newHi = (sh == 0) ? aLo : (aLo << sh);
            return new long[] { newHi, 0L };
        }
        // 0 < n < 64
        final long newHi = (aHi << n) | (aLo >>> (64 - n));
        final long newLo = aLo << n;
        return new long[] { newHi, newLo };
    }

    /** Returns bit n of (aHi:aLo) as 0 or 1. n in [0, 127]. */
    private static long u128GetBit(long aHi, long aLo, int n) {
        if ( n < 64 ) {
            return (aLo >>> n) & 1L;
        }
        return (aHi >>> (n - 64)) & 1L;
    }

    /** Unsigned 64×64 → high-64 bits. */
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

}
