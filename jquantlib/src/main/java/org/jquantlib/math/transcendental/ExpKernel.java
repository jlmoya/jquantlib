package org.jquantlib.math.transcendental;

/**
 * Correctly-rounded {@code exp(x)} for IEEE-754 binary64.
 *
 * <p>Algorithm and constants ported verbatim from CORE-MATH
 * ({@code src/binary64/exp/exp.c}, Copyright (c) 2022-2023 Alexei Sibidanov; MIT-licensed; see
 * {@code https://core-math.gitlabpages.inria.fr/}).
 *
 * <p>The CORE-MATH implementation produces the unique correctly-rounded
 * (round-to-nearest-even) IEEE-754 binary64 result for every input, matching Apple libm's {@code std::exp} bit-exact on
 * macOS arm64. JVM {@code Math.exp} carries up to ~1 ULP of slack relative to libc++ which propagates through the
 * QuantLib port; this kernel removes that floor for the EXACT tolerance tier across transcendental-bearing tests.
 *
 * <p>The fast path uses argument reduction
 * {@code x = (k + i/2^6 + l/2^12) * ln(2) + r} with two 64-entry double-double lookup tables for the {@code 2^(i/2^6)}
 * and {@code 2^(l/2^12)} factors and a cubic polynomial in the small remainder {@code r}. An error bound of
 * {@code 1.64e-19} is rounded against the test boundary; if the boundary is straddled the accurate path is taken: a
 * degree-7 polynomial in double-double, with a triple-double split of {@code ln(2)} for the argument reduction, and
 * finally a 51-entry hard-cases database that nudges the last ulp for inputs whose correctly-rounded result lies on a
 * rounding boundary.
 */
final class ExpKernel {

    /** 51-entry sorted database of inputs whose correctly-rounded exp lies on a rounding boundary. */
    private static final long[] DB;
    // Per-case correction sign: bit m of S_SIGN is the sign of the dr nudge for entry m.
    private static final long S_SIGN = 333811522313371L;

    // ---------------- Accurate path ----------------
    // 2-bit per case low-2-bits target, packed into two 64-bit words (32 cases each).
    private static final long S2_0 = 0x57f5fe2e5bde4075L;

    // ---------------- Hard-cases database ----------------
    private static final long S2_1 = 0x3c1f16b8edL;
    /** Accurate-path polynomial coefficients (double-double pairs, [hi, lo]). */
    private static final double[][] CH_DD = new double[][] { { 0x1p+0, 0.0 }, { 0x1p-1, 0x1.712f72ecec2cfp-99 },
            { 0x1.5555555555555p-3, 0x1.5555555554d07p-57 }, { 0x1.5555555555555p-5, 0x1.55194d28275dap-59 },
            { 0x1.1111111111111p-7, 0x1.12faa0e1c0f7bp-63 }, { 0x1.6c16c16da6973p-10, -0x1.4ba45ab25d2a3p-64 },
            { 0x1.a01a019eb7f31p-13, -0x1.9091d845ecd36p-67 }, };
    /** {@code 2^(i/64)} table — 64 double-double entries [lo, hi]. Stored as two parallel arrays. */
    private static final double[] T0_LO = new double[64];
    private static final double[] T0_HI = new double[64];
    /** {@code 2^(i/4096)} table — 64 double-double entries [lo, hi]. */
    private static final double[] T1_LO = new double[64];
    private static final double[] T1_HI = new double[64];

    // ---------------- Double-double helpers ----------------

    static {
        DB = new long[] {
                // positive 21 entries
                Double.doubleToRawLongBits(0x1.fffffffffffffp-53), Double.doubleToRawLongBits(0x1.ba07d73250de7p-14),
                Double.doubleToRawLongBits(0x1.6a4d1af9cc989p-8), Double.doubleToRawLongBits(0x1.5a75293a5dcdap-6),
                Double.doubleToRawLongBits(0x1.42ea46949b3c7p-5), Double.doubleToRawLongBits(0x1.7c8bb0cf5d160p-5),
                Double.doubleToRawLongBits(0x1.0948d39a41695p-3), Double.doubleToRawLongBits(0x1.a065fefae814fp-3),
                Double.doubleToRawLongBits(0x1.f6e4c3ced7c72p-3), Double.doubleToRawLongBits(0x1.1a0408712e00ap-2),
                Double.doubleToRawLongBits(0x1.bcab27d05abdep-2), Double.doubleToRawLongBits(0x1.005ae04256babp-1),
                Double.doubleToRawLongBits(0x1.273c188aa7b14p+2), Double.doubleToRawLongBits(0x1.83d4bcdebb3f4p+2),
                Double.doubleToRawLongBits(0x1.08f51434652c3p+4), Double.doubleToRawLongBits(0x1.1d5c2daebe367p+4),
                Double.doubleToRawLongBits(0x1.c44ce0d716a1ap+4), Double.doubleToRawLongBits(0x1.e07e71bfcf06fp+5),
                Double.doubleToRawLongBits(0x1.f7216c4b435c9p+5), Double.doubleToRawLongBits(0x1.54cd1fea7663ap+7),
                Double.doubleToRawLongBits(0x1.d6479eba7c971p+8),
                // negative 30 entries
                Double.doubleToRawLongBits(-0x1.664716b68a409p-14), Double.doubleToRawLongBits(-0x1.a2fefefd580dfp-13),
                Double.doubleToRawLongBits(-0x1.ce3f638d0c742p-12), Double.doubleToRawLongBits(-0x1.ceff32831e2c2p-12),
                Double.doubleToRawLongBits(-0x1.33accae78b371p-11), Double.doubleToRawLongBits(-0x1.d792b60084f92p-11),
                Double.doubleToRawLongBits(-0x1.7fb235d76cce7p-8), Double.doubleToRawLongBits(-0x1.1ff9b8e8b38bep-7),
                Double.doubleToRawLongBits(-0x1.54511e930898cp-7), Double.doubleToRawLongBits(-0x1.5c5ed0ec83666p-6),
                Double.doubleToRawLongBits(-0x1.8c56ff5326197p-6), Double.doubleToRawLongBits(-0x1.a4187f2ca71f9p-6),
                Double.doubleToRawLongBits(-0x1.a8f783d749a8fp-4), Double.doubleToRawLongBits(-0x1.bd44fdaed819fp-4),
                Double.doubleToRawLongBits(-0x1.daf693d64fadap-4), Double.doubleToRawLongBits(-0x1.290ea09e36479p-3),
                Double.doubleToRawLongBits(-0x1.8aeb636f3ce35p-3), Double.doubleToRawLongBits(-0x1.d3f3799439415p-3),
                Double.doubleToRawLongBits(-0x1.ea16274b0109bp-3), Double.doubleToRawLongBits(-0x1.22e24fa3d5cf9p-1),
                Double.doubleToRawLongBits(-0x1.85068c07fbbf6p-1), Double.doubleToRawLongBits(-0x1.bdc7955d1482cp-1),
                Double.doubleToRawLongBits(-0x1.2a9cad9998262p+0), Double.doubleToRawLongBits(-0x1.cc37ef7de7501p+0),
                Double.doubleToRawLongBits(-0x1.02393d5976769p+1), Double.doubleToRawLongBits(-0x1.65061daf79a78p+1),
                Double.doubleToRawLongBits(-0x1.e8bdbfcd9144ep+3), Double.doubleToRawLongBits(-0x1.8f80e06f3a04cp+4),
                Double.doubleToRawLongBits(-0x1.59f038076039cp+6), Double.doubleToRawLongBits(-0x1.981587ad4542fp+7), };
    }

    static {
        // T0: 2^(i/64), i in [0,63]
        int k = 0;
        T0_LO[k] = 0x0p+0;
        T0_HI[k++] = 0x1p+0;
        T0_LO[k] = -0x1.19083535b085ep-56;
        T0_HI[k++] = 0x1.02c9a3e778061p+0;
        T0_LO[k] = 0x1.d73e2a475b466p-55;
        T0_HI[k++] = 0x1.059b0d3158574p+0;
        T0_LO[k] = 0x1.186be4bb285p-57;
        T0_HI[k++] = 0x1.0874518759bc8p+0;
        T0_LO[k] = 0x1.8a62e4adc610ap-54;
        T0_HI[k++] = 0x1.0b5586cf9890fp+0;
        T0_LO[k] = 0x1.03a1727c57b52p-59;
        T0_HI[k++] = 0x1.0e3ec32d3d1a2p+0;
        T0_LO[k] = -0x1.6c51039449b3ap-54;
        T0_HI[k++] = 0x1.11301d0125b51p+0;
        T0_LO[k] = -0x1.32fbf9af1369ep-54;
        T0_HI[k++] = 0x1.1429aaea92de0p+0;
        T0_LO[k] = -0x1.19041b9d78a76p-55;
        T0_HI[k++] = 0x1.172b83c7d517bp+0;
        T0_LO[k] = 0x1.e5b4c7b4968e4p-55;
        T0_HI[k++] = 0x1.1a35beb6fcb75p+0;
        T0_LO[k] = 0x1.e016e00a2643cp-54;
        T0_HI[k++] = 0x1.1d4873168b9aap+0;
        T0_LO[k] = 0x1.dc775814a8494p-55;
        T0_HI[k++] = 0x1.2063b88628cd6p+0;
        T0_LO[k] = 0x1.9b07eb6c70572p-54;
        T0_HI[k++] = 0x1.2387a6e756238p+0;
        T0_LO[k] = 0x1.2bd339940e9dap-55;
        T0_HI[k++] = 0x1.26b4565e27cddp+0;
        T0_LO[k] = 0x1.612e8afad1256p-55;
        T0_HI[k++] = 0x1.29e9df51fdee1p+0;
        T0_LO[k] = 0x1.0024754db41d4p-54;
        T0_HI[k++] = 0x1.2d285a6e4030bp+0;
        T0_LO[k] = 0x1.6f46ad23182e4p-55;
        T0_HI[k++] = 0x1.306fe0a31b715p+0;
        T0_LO[k] = 0x1.32721843659a6p-54;
        T0_HI[k++] = 0x1.33c08b26416ffp+0;
        T0_LO[k] = -0x1.63aeabf42eae2p-54;
        T0_HI[k++] = 0x1.371a7373aa9cbp+0;
        T0_LO[k] = -0x1.5e436d661f5e2p-56;
        T0_HI[k++] = 0x1.3a7db34e59ff7p+0;
        T0_LO[k] = 0x1.ada0911f09ebcp-55;
        T0_HI[k++] = 0x1.3dea64c123422p+0;
        T0_LO[k] = -0x1.ef3691c309278p-58;
        T0_HI[k++] = 0x1.4160a21f72e2ap+0;
        T0_LO[k] = 0x1.89b7a04ef80dp-59;
        T0_HI[k++] = 0x1.44e086061892dp+0;
        T0_LO[k] = 0x1.3c1a3b69062fp-56;
        T0_HI[k++] = 0x1.486a2b5c13cd0p+0;
        T0_LO[k] = 0x1.d4397afec42e2p-56;
        T0_HI[k++] = 0x1.4bfdad5362a27p+0;
        T0_LO[k] = -0x1.4b309d25957e4p-54;
        T0_HI[k++] = 0x1.4f9b2769d2ca7p+0;
        T0_LO[k] = -0x1.07abe1db13cacp-55;
        T0_HI[k++] = 0x1.5342b569d4f82p+0;
        T0_LO[k] = 0x1.9bb2c011d93acp-54;
        T0_HI[k++] = 0x1.56f4736b527dap+0;
        T0_LO[k] = 0x1.6324c054647acp-54;
        T0_HI[k++] = 0x1.5ab07dd485429p+0;
        T0_LO[k] = 0x1.ba6f93080e65ep-54;
        T0_HI[k++] = 0x1.5e76f15ad2148p+0;
        T0_LO[k] = -0x1.383c17e40b496p-54;
        T0_HI[k++] = 0x1.6247eb03a5585p+0;
        T0_LO[k] = -0x1.bb60987591c34p-54;
        T0_HI[k++] = 0x1.6623882552225p+0;
        T0_LO[k] = -0x1.bdd3413b26456p-54;
        T0_HI[k++] = 0x1.6a09e667f3bcdp+0;
        T0_LO[k] = -0x1.bbe3a683c88aap-57;
        T0_HI[k++] = 0x1.6dfb23c651a2fp+0;
        T0_LO[k] = -0x1.16e4786887a9ap-55;
        T0_HI[k++] = 0x1.71f75e8ec5f74p+0;
        T0_LO[k] = -0x1.0245957316dd4p-54;
        T0_HI[k++] = 0x1.75feb564267c9p+0;
        T0_LO[k] = -0x1.41577ee04993p-55;
        T0_HI[k++] = 0x1.7a11473eb0187p+0;
        T0_LO[k] = 0x1.05d02ba15797ep-56;
        T0_HI[k++] = 0x1.7e2f336cf4e62p+0;
        T0_LO[k] = -0x1.d4c1dd41532d8p-54;
        T0_HI[k++] = 0x1.82589994cce13p+0;
        T0_LO[k] = -0x1.fc6f89bd4f6bap-54;
        T0_HI[k++] = 0x1.868d99b4492edp+0;
        T0_LO[k] = 0x1.6e9f156864b26p-54;
        T0_HI[k++] = 0x1.8ace5422aa0dbp+0;
        T0_LO[k] = 0x1.5cc13a2e3976cp-55;
        T0_HI[k++] = 0x1.8f1ae99157736p+0;
        T0_LO[k] = -0x1.75fc781b57ebcp-57;
        T0_HI[k++] = 0x1.93737b0cdc5e5p+0;
        T0_LO[k] = -0x1.d185b7c1b85dp-54;
        T0_HI[k++] = 0x1.97d829fde4e50p+0;
        T0_LO[k] = 0x1.c7c46b071f2bep-56;
        T0_HI[k++] = 0x1.9c49182a3f090p+0;
        T0_LO[k] = -0x1.359495d1cd532p-54;
        T0_HI[k++] = 0x1.a0c667b5de565p+0;
        T0_LO[k] = -0x1.d2f6edb8d41e2p-54;
        T0_HI[k++] = 0x1.a5503b23e255dp+0;
        T0_LO[k] = 0x1.0fac90ef7fd32p-54;
        T0_HI[k++] = 0x1.a9e6b5579fdbfp+0;
        T0_LO[k] = 0x1.7a1cd345dcc82p-54;
        T0_HI[k++] = 0x1.ae89f995ad3adp+0;
        T0_LO[k] = -0x1.2805e3084d708p-57;
        T0_HI[k++] = 0x1.b33a2b84f15fbp+0;
        T0_LO[k] = -0x1.5584f7e54ac3ap-56;
        T0_HI[k++] = 0x1.b7f76f2fb5e47p+0;
        T0_LO[k] = 0x1.23dd07a2d9e84p-55;
        T0_HI[k++] = 0x1.bcc1e904bc1d2p+0;
        T0_LO[k] = 0x1.11065895048dep-55;
        T0_HI[k++] = 0x1.c199bdd85529cp+0;
        T0_LO[k] = 0x1.2884dff483cacp-54;
        T0_HI[k++] = 0x1.c67f12e57d14bp+0;
        T0_LO[k] = 0x1.503cbd1e949dcp-56;
        T0_HI[k++] = 0x1.cb720dcef9069p+0;
        T0_LO[k] = -0x1.cbc3743797a9cp-54;
        T0_HI[k++] = 0x1.d072d4a07897cp+0;
        T0_LO[k] = 0x1.2ed02d75b3706p-55;
        T0_HI[k++] = 0x1.d5818dcfba487p+0;
        T0_LO[k] = 0x1.c2300696db532p-54;
        T0_HI[k++] = 0x1.da9e603db3285p+0;
        T0_LO[k] = -0x1.1a5cd4f184b5cp-54;
        T0_HI[k++] = 0x1.dfc97337b9b5fp+0;
        T0_LO[k] = 0x1.39e8980a9cc9p-55;
        T0_HI[k++] = 0x1.e502ee78b3ff6p+0;
        T0_LO[k] = -0x1.e9c23179c2894p-54;
        T0_HI[k++] = 0x1.ea4afa2a490dap+0;
        T0_LO[k] = 0x1.dc7f486a4b6bp-54;
        T0_HI[k++] = 0x1.efa1bee615a27p+0;
        T0_LO[k] = 0x1.9d3e12dd8a18ap-54;
        T0_HI[k++] = 0x1.f50765b6e4540p+0;
        T0_LO[k] = 0x1.74853f3a5931ep-55;
        T0_HI[k++] = 0x1.fa7c1819e90d8p+0;

        // T1: 2^(i/4096), i in [0,63]
        k = 0;
        T1_LO[k] = 0x0p+0;
        T1_HI[k++] = 0x1p+0;
        T1_LO[k] = 0x1.ae8e38c59c72ap-54;
        T1_HI[k++] = 0x1.000b175effdc7p+0;
        T1_LO[k] = -0x1.7b5d0d58ea8f4p-58;
        T1_HI[k++] = 0x1.00162f3904052p+0;
        T1_LO[k] = 0x1.4115cb6b16a8ep-54;
        T1_HI[k++] = 0x1.0021478e11ce6p+0;
        T1_LO[k] = -0x1.d7c96f201bb2ep-55;
        T1_HI[k++] = 0x1.002c605e2e8cfp+0;
        T1_LO[k] = 0x1.84711d4c35eap-54;
        T1_HI[k++] = 0x1.003779a95f959p+0;
        T1_LO[k] = -0x1.0484245243778p-55;
        T1_HI[k++] = 0x1.0042936faa3d8p+0;
        T1_LO[k] = -0x1.4b237da2025fap-54;
        T1_HI[k++] = 0x1.004dadb113da0p+0;
        T1_LO[k] = -0x1.5e00e62d6b30ep-56;
        T1_HI[k++] = 0x1.0058c86da1c0ap+0;
        T1_LO[k] = 0x1.a1d6cedbb948p-54;
        T1_HI[k++] = 0x1.0063e3a559473p+0;
        T1_LO[k] = -0x1.4acf197a00142p-54;
        T1_HI[k++] = 0x1.006eff583fc3dp+0;
        T1_LO[k] = -0x1.eaf2ea42391a6p-57;
        T1_HI[k++] = 0x1.007a1b865a8cap+0;
        T1_LO[k] = 0x1.da93f90835f76p-56;
        T1_HI[k++] = 0x1.0085382faef83p+0;
        T1_LO[k] = -0x1.6a79084ab093cp-55;
        T1_HI[k++] = 0x1.00905554425d4p+0;
        T1_LO[k] = 0x1.86364f8fbe8f8p-54;
        T1_HI[k++] = 0x1.009b72f41a12bp+0;
        T1_LO[k] = -0x1.82e8e14e3110ep-55;
        T1_HI[k++] = 0x1.00a6910f3b6fdp+0;
        T1_LO[k] = -0x1.4f6b2a7609f72p-55;
        T1_HI[k++] = 0x1.00b1afa5abcbfp+0;
        T1_LO[k] = -0x1.e1a258ea8f71ap-56;
        T1_HI[k++] = 0x1.00bcceb7707ecp+0;
        T1_LO[k] = 0x1.4362ca5bc26f2p-56;
        T1_HI[k++] = 0x1.00c7ee448ee02p+0;
        T1_LO[k] = 0x1.095a56c919d02p-54;
        T1_HI[k++] = 0x1.00d30e4d0c483p+0;
        T1_LO[k] = -0x1.406ac4e81a646p-57;
        T1_HI[k++] = 0x1.00de2ed0ee0f5p+0;
        T1_LO[k] = 0x1.b5a6902767e08p-54;
        T1_HI[k++] = 0x1.00e94fd0398e0p+0;
        T1_LO[k] = -0x1.91b206085932p-54;
        T1_HI[k++] = 0x1.00f4714af41d3p+0;
        T1_LO[k] = 0x1.427068ab22306p-55;
        T1_HI[k++] = 0x1.00ff93412315cp+0;
        T1_LO[k] = 0x1.c1d0660524e08p-54;
        T1_HI[k++] = 0x1.010ab5b2cbd11p+0;
        T1_LO[k] = -0x1.e7bdfb3204be8p-54;
        T1_HI[k++] = 0x1.0115d89ff3a8bp+0;
        T1_LO[k] = 0x1.843aa8b9cbbc6p-55;
        T1_HI[k++] = 0x1.0120fc089ff63p+0;
        T1_LO[k] = -0x1.34104ee7edae8p-56;
        T1_HI[k++] = 0x1.012c1fecd613bp+0;
        T1_LO[k] = -0x1.2b6aeb6176892p-56;
        T1_HI[k++] = 0x1.0137444c9b5b5p+0;
        T1_LO[k] = 0x1.a8cd33b8a1bb2p-56;
        T1_HI[k++] = 0x1.01426927f5278p+0;
        T1_LO[k] = 0x1.2edc08e5da99ap-56;
        T1_HI[k++] = 0x1.014d8e7ee8d2fp+0;
        T1_LO[k] = 0x1.57ba2dc7e0c72p-55;
        T1_HI[k++] = 0x1.0158b4517bb88p+0;
        T1_LO[k] = 0x1.b61299ab8cdb8p-54;
        T1_HI[k++] = 0x1.0163da9fb3335p+0;
        T1_LO[k] = -0x1.90565902c5f44p-54;
        T1_HI[k++] = 0x1.016f0169949edp+0;
        T1_LO[k] = 0x1.70fc41c5c2d54p-55;
        T1_HI[k++] = 0x1.017a28af25567p+0;
        T1_LO[k] = 0x1.4b9a6e145d76cp-54;
        T1_HI[k++] = 0x1.018550706ab62p+0;
        T1_LO[k] = -0x1.008eff5142bfap-56;
        T1_HI[k++] = 0x1.019078ad6a19fp+0;
        T1_LO[k] = -0x1.77669f033c7dep-54;
        T1_HI[k++] = 0x1.019ba16628de2p+0;
        T1_LO[k] = -0x1.09bb78eeead0ap-54;
        T1_HI[k++] = 0x1.01a6ca9aac5f3p+0;
        T1_LO[k] = 0x1.371231477ece6p-54;
        T1_HI[k++] = 0x1.01b1f44af9f9ep+0;
        T1_LO[k] = 0x1.5e7626621eb5ap-56;
        T1_HI[k++] = 0x1.01bd1e77170b4p+0;
        T1_LO[k] = -0x1.bc72b100828a4p-54;
        T1_HI[k++] = 0x1.01c8491f08f08p+0;
        T1_LO[k] = -0x1.ce39cbbab8bbep-57;
        T1_HI[k++] = 0x1.01d37442d5070p+0;
        T1_LO[k] = 0x1.16996709da2e2p-55;
        T1_HI[k++] = 0x1.01de9fe280ac8p+0;
        T1_LO[k] = -0x1.c11f5239bf536p-55;
        T1_HI[k++] = 0x1.01e9cbfe113efp+0;
        T1_LO[k] = 0x1.e1d4eb5edc6b4p-55;
        T1_HI[k++] = 0x1.01f4f8958c1c6p+0;
        T1_LO[k] = -0x1.afb99946ee3fp-54;
        T1_HI[k++] = 0x1.020025a8f6a35p+0;
        T1_LO[k] = -0x1.8f06d8a148a32p-54;
        T1_HI[k++] = 0x1.020b533856324p+0;
        T1_LO[k] = -0x1.2bf310fc54eb6p-55;
        T1_HI[k++] = 0x1.02168143b0281p+0;
        T1_LO[k] = -0x1.c95a035eb4176p-54;
        T1_HI[k++] = 0x1.0221afcb09e3ep+0;
        T1_LO[k] = -0x1.491793e46834cp-54;
        T1_HI[k++] = 0x1.022cdece68c4fp+0;
        T1_LO[k] = -0x1.3e8d0d9c4909p-56;
        T1_HI[k++] = 0x1.02380e4dd22adp+0;
        T1_LO[k] = -0x1.314aa16278aa4p-54;
        T1_HI[k++] = 0x1.02433e494b755p+0;
        T1_LO[k] = 0x1.48daf888e965p-55;
        T1_HI[k++] = 0x1.024e6ec0da046p+0;
        T1_LO[k] = 0x1.56dc8046821f4p-55;
        T1_HI[k++] = 0x1.02599fb483385p+0;
        T1_LO[k] = 0x1.45b42356b9d46p-54;
        T1_HI[k++] = 0x1.0264d1244c719p+0;
        T1_LO[k] = -0x1.082ef51b61d7ep-56;
        T1_HI[k++] = 0x1.027003103b10ep+0;
        T1_LO[k] = 0x1.2106ed0920a34p-56;
        T1_HI[k++] = 0x1.027b357854772p+0;
        T1_LO[k] = -0x1.fd4cf26ea5d0ep-54;
        T1_HI[k++] = 0x1.0286685c9e059p+0;
        T1_LO[k] = -0x1.09f8775e78084p-54;
        T1_HI[k++] = 0x1.02919bbd1d1d8p+0;
        T1_LO[k] = 0x1.64cbba902ca28p-58;
        T1_HI[k++] = 0x1.029ccf99d720ap+0;
        T1_LO[k] = 0x1.4383ef231d206p-54;
        T1_HI[k++] = 0x1.02a803f2d170dp+0;
        T1_LO[k] = 0x1.4a47a505b3a46p-54;
        T1_HI[k++] = 0x1.02b338c811703p+0;
        T1_LO[k] = 0x1.e47120223468p-54;
        T1_HI[k++] = 0x1.02be6e199c811p+0;
    }

    private ExpKernel() {
    }

    /** Correctly-rounded {@code exp(x)} matching Apple libm bit-exact. */
    static double exp(double x) {
        final long ixu = Double.doubleToRawLongBits(x);
        final long aix = ixu & 0x7fffffffffffffffL;

        // Special / overflow / underflow handling.
        if ( Long.compareUnsigned(aix, 0x40862e42fefa39f0L) >= 0 ) {
            if ( Long.compareUnsigned(aix, 0x7ff0000000000000L) > 0 )
                return x;          // NaN
            if ( aix == 0x7ff0000000000000L )
                return (ixu < 0) ? 0.0 : x;                 // +/-inf
            if ( ixu >= 0 ) {                                                              // overflow
                final double z = 0x1p1023;
                return z * z;
            }
            if ( Long.compareUnsigned(aix, 0x40874910d52d3052L) >= 0 ) {
                return 0x1.5p-1022 * 0x1p-55;                                            // underflow flush
            }
            // else: fall through to fast path (denormal/very-small-output regime)
        }

        // Fast path.
        final double s = 0x1.71547652b82fep+12;
        final double t = Math.rint(x * s);
        final long jt = (long) t;
        final int i0 = (int) ((jt >> 6) & 0x3fL);
        final int i1 = (int) (jt & 0x3fL);
        final long ie = jt >> 12;

        final double t0h = T0_HI[i0];
        final double t0l = T0_LO[i0];
        final double t1h = T1_HI[i1];
        final double t1l = T1_LO[i1];

        final double[] lo = new double[1];
        final double th = muldd(t0h, t0l, t1h, t1l, lo);
        final double tl = lo[0];

        final double l2h = 0x1.62e42ffp-13;
        final double l2l = 0x1.718432a1b0e26p-47;
        final double dx = (x - l2h * t) + l2l * t;
        final double dx2 = dx * dx;
        // Polynomial coefficients for the fast path (degree 3).
        final double p = (1.0 + dx * 0x1p-1) + dx2 * (0x1.55555557e54ffp-3 + dx * 0x1.55555553a12f4p-5);
        double fh = th;
        final double tx = th * dx;
        final double fl = tl + tx * p;
        final double eps = 1.64e-19;

        if ( Long.compareUnsigned(ixu, 0xc086232bdd7abcd2L) > 0 ) {
            // Result is denormal: assemble result with explicit bias.
            final double bias = Double.longBitsToDouble((1L - ie) << 52);
            final double[] e = new double[1];
            final double fh2 = fastTwoSum(bias, fh, e);
            final double fl2 = fl + e[0];
            final double ub = fh2 + (fl2 + eps);
            final double lb = fh2 + (fl2 - eps);
            if ( ub != lb )
                return expAccurate(x);
            return toDenormal(lb);
        } else {
            final double ub = fh + (fl + eps);
            final double lb = fh + (fl - eps);
            if ( ub != lb )
                return expAccurate(x);
            return ldexpBits(lb, ie);
        }
    }

    private static double expAccurate(double x) {
        final long ixu = Double.doubleToRawLongBits(x);
        // For very small |x| (biased exponent < 0x3c9), exp(x) rounds to 1+x.
        if ( ((ixu >>> 52) & 0x7ffL) < 0x3c9L )
            return 1.0 + x;

        final double s = 0x1.71547652b82fep+12;
        final double t = Math.rint(x * s);
        final long jt = (long) t;
        final int i0 = (int) ((jt >> 6) & 0x3fL);
        final int i1 = (int) (jt & 0x3fL);
        final long ie = jt >> 12;

        final double t0h = T0_HI[i0];
        final double t0l = T0_LO[i0];
        final double t1h = T1_HI[i1];
        final double t1l = T1_LO[i1];

        final double[] lo = new double[1];
        double th = muldd(t0h, t0l, t1h, t1l, lo);
        double tl = lo[0];

        final double l2h = 0x1.62e42ffp-13;
        final double l2l = 0x1.718432a1b0e26p-47;
        final double l2ll = 0x1.9ff0342542fc3p-102;

        final double dx = x - l2h * t;
        double dxl = l2l * t;
        final double dxll = l2ll * t + Math.fma(l2l, t, -dxl);
        double dxh = dx + dxl;
        dxl = (dx - dxh) + dxl + dxll;

        // Polynomial degree-7 in (dxh, dxl) double-double.
        double fh = opolydd(dxh, dxl, CH_DD, lo);
        double fl = lo[0];
        fh = muldd(dxh, dxl, fh, fl, lo);
        fl = lo[0];

        if ( Long.compareUnsigned(ixu, 0xc086232bdd7abcd2L) > 0 ) {
            final double bias = Double.longBitsToDouble((1L - ie) << 52);
            fh = muldd(fh, fl, th, tl, lo);
            fl = lo[0];
            // fh = fastsum(th,tl, fh,fl, &fl)
            final double sumHi = fastTwoSum(th, fh, lo);
            final double sumLo = lo[0];
            fl = (tl + fl) + sumLo;
            fh = sumHi;

            final double[] e = new double[1];
            fh = fastTwoSum(bias, fh, e);
            fl += e[0];
            return toDenormal(fh + fl);
        } else {
            if ( th == 1.0 ) {
                final double[] e = new double[1];
                fh = fastTwoSum(th, fh, e);
                final double e0 = e[0];
                fl = fastTwoSum(e0, fl, e);
                final double e1 = e[0];
                long ixb = Double.doubleToRawLongBits(fl);
                if ( (ixb & 0x000fffffffffffffL) == 0L ) {
                    final long vu = Double.doubleToRawLongBits(e1);
                    // d = ((sign(fl) ^ sign(e1)) << 1) + 1
                    final long signFl = ixb >> 63;
                    final long signE1 = vu >> 63;
                    final long d = ((signFl ^ signE1) << 1) + 1L;
                    ixb += d;
                    fl = Double.longBitsToDouble(ixb);
                }
            } else {
                fh = muldd(fh, fl, th, tl, lo);
                fl = lo[0];
                // fh = fastsum(th,tl, fh,fl, &fl)
                final double sumHi = fastTwoSum(th, fh, lo);
                final double sumLo = lo[0];
                fl = (tl + fl) + sumLo;
                fh = sumHi;
            }
            final double[] e = new double[1];
            fh = fastTwoSum(fh, fl, e);
            fl = e[0];
            final long ixb = Double.doubleToRawLongBits(fl);
            final long d = (ixb + 2) & 0x000fffffffffffffL;
            if ( Long.compareUnsigned(d, 2L) <= 0 ) {
                fh = expDatabase(x, fh);
            }
            return ldexpBits(fh, ie);
        }
    }

    // ---------------- Tables ----------------

    private static double expDatabase(double x, double f) {
        final long ix = Double.doubleToRawLongBits(x);
        int a = 0, b = DB.length - 1, m = (a + b) >>> 1;
        while ( a <= b ) {
            final long cm = DB[m];
            final int cmp = Long.compareUnsigned(cm, ix);
            if ( cmp < 0 ) {
                a = m + 1;
            } else if ( cm == ix ) {
                final long s2 = (m < 32) ? S2_0 : S2_1;
                final long jfu = Double.doubleToRawLongBits(f);
                final long drBits = (((S_SIGN >>> m) & 1L) << 63) | 0x3c90000000000000L;
                final double dr = Double.longBitsToDouble(drBits);
                final long t = (s2 >>> ((m << 1) & 63)) & 3L;
                for ( long k = -1; k <= 1; k++ ) {
                    final long ru = jfu + k;
                    if ( (ru & 3L) == t ) {
                        return Double.longBitsToDouble(ru) + dr;
                    }
                }
                break;
            } else {
                b = m - 1;
            }
            m = (a + b) >>> 1;
        }
        return f;
    }

    /** {@code s = a + b; e = b - (s - a); return s} (assumes |a| >= |b|). */
    private static double fastTwoSum(double a, double b, double[] eOut) {
        final double s = a + b;
        final double z = s - a;
        eOut[0] = b - z;
        return s;
    }

    /** Multiply double-double {@code (xh,xl) * (ch,cl)} producing double-double {@code (hi, lo)}. */
    private static double muldd(double xh, double xl, double ch, double cl, double[] lOut) {
        final double ahhh = ch * xh;
        lOut[0] = (ch * xl + cl * xh) + Math.fma(ch, xh, -ahhh);
        return ahhh;
    }

    /** Horner double-double polynomial evaluation: c[n-1] + xh*( c[n-2] + xh*(...) ). */
    private static double opolydd(double xh, double xl, double[][] c, double[] lOut) {
        int i = c.length - 1;
        double ch = c[i][0];
        double cl = c[i][1];
        final double[] tmp = new double[1];
        while ( --i >= 0 ) {
            ch = muldd(xh, xl, ch, cl, tmp);
            cl = tmp[0];
            final double th = ch + c[i][0];
            final double tl = (c[i][0] - th) + ch;
            ch = th;
            cl += tl + c[i][1];
        }
        lOut[0] = cl;
        return ch;
    }

    /** Multiply by {@code 2^i} via direct exponent-field add. Caller guarantees the result is normal. */
    private static double ldexpBits(double x, long i) {
        final long ix = Double.doubleToRawLongBits(x);
        return Double.longBitsToDouble(ix + (i << 52));
    }

    /** Mask off sign + exponent fields, leaving only the 52-bit mantissa as the bit pattern. */
    private static double toDenormal(double x) {
        return Double.longBitsToDouble(Double.doubleToRawLongBits(x) & 0x000fffffffffffffL);
    }
}
