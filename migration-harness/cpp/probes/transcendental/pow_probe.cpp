// migration-harness/cpp/probes/transcendental/pow_probe.cpp
// Phase 2n A.1 — emit bit-exact CORE-MATH cr_pow(x, y) for a curated input
// set covering the empirical-leverage fan-out plus rounding-boundary stress.
//
// Oracle: CORE-MATH cr_pow (correctly-rounded by design), NOT std::pow.
// Apple libm and glibc disagree on hard-rounding cases; this probe gives us
// a single canonical reference per (x, y) pair.
//
// We can't include coremath/pow.c directly here because (a) it pulls in
// pow.h which references the curated dint.h (sin/cos-flavoured) instead of
// the full pow_dint.h (with _INVERSE_2_1, T1_2, etc. tables), and (b) some
// of pow.c's debug branches use printf string concatenation that the C++
// tokenizer rejects.
//
// Instead we link against pow_shim.c, which compiles as C and exposes
// cr_pow through the extern "C" interface declared in pow_shim.h.
//
// Schema per case:
//   inputs: {"x": <hex bits or special string>, "y": <hex bits>, "description": <text>}
//   expected: {"y_bits": <hex>}

#include <ql/version.hpp>
#include "../common.hpp"

#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <limits>

extern "C" {
#include "pow_shim.h"
}

using namespace jqml_harness;

namespace {

std::string hex64(double y) {
    std::uint64_t bits;
    std::memcpy(&bits, &y, sizeof bits);
    char buf[24];
    std::snprintf(buf, sizeof buf, "0x%016llx", (unsigned long long) bits);
    return std::string(buf);
}

double fromBits(std::uint64_t bits) {
    double d;
    std::memcpy(&d, &bits, sizeof d);
    return d;
}

json encodeX(double x) {
    if (std::isnan(x)) return json("NaN");
    if (std::isinf(x)) return json(x > 0 ? "Infinity" : "-Infinity");
    return json(x);
}

void addPowCase(ReferenceWriter& out, const std::string& name, double x, double y) {
    const double r = pow_shim_cr_pow(x, y);
    json inputs = {
        {"x", encodeX(x)},
        {"y", encodeX(y)},
        {"x_bits", hex64(x)},
        {"y_bits_in", hex64(y)},
    };
    out.addCase(name, inputs, json{{"y_bits", hex64(r)}});
}

} // namespace

int main() {
    ReferenceWriter out("math/transcendental/pow", QL_VERSION, "pow_probe");

    // ============================================================
    // 1. IEEE-754 specials (~30 cases)
    // ============================================================
    const double pInf = std::numeric_limits<double>::infinity();
    const double nInf = -pInf;
    const double qNaN = std::numeric_limits<double>::quiet_NaN();

    addPowCase(out, "special_zero_pos_zero",   +0.0, +0.0);
    addPowCase(out, "special_zero_neg_zero",   +0.0, -0.0);
    addPowCase(out, "special_neg_zero_pos_zero", -0.0, +0.0);
    addPowCase(out, "special_neg_zero_neg_zero", -0.0, -0.0);

    addPowCase(out, "special_one_pinf",   1.0, pInf);
    addPowCase(out, "special_one_ninf",   1.0, nInf);
    addPowCase(out, "special_one_nan",    1.0, qNaN);

    addPowCase(out, "special_pinf_zero",  pInf, 0.0);
    addPowCase(out, "special_ninf_zero",  nInf, 0.0);

    addPowCase(out, "special_pinf_one",   pInf, 1.0);
    addPowCase(out, "special_pinf_neg_one", pInf, -1.0);
    addPowCase(out, "special_pinf_two",   pInf, 2.0);

    addPowCase(out, "special_ninf_one",   nInf, 1.0);
    addPowCase(out, "special_ninf_two",   nInf, 2.0);
    addPowCase(out, "special_ninf_three", nInf, 3.0);
    addPowCase(out, "special_ninf_neg_one", nInf, -1.0);
    addPowCase(out, "special_ninf_neg_two", nInf, -2.0);
    addPowCase(out, "special_ninf_half", nInf, 0.5);

    addPowCase(out, "special_pos_zero_pos_finite", 0.0, 1.5);
    addPowCase(out, "special_pos_zero_neg_finite", 0.0, -1.5);
    addPowCase(out, "special_pos_zero_neg_int",    0.0, -3.0);
    addPowCase(out, "special_pos_zero_pos_odd_int", 0.0, 3.0);
    addPowCase(out, "special_neg_zero_pos_odd_int", -0.0, 3.0);
    addPowCase(out, "special_neg_zero_pos_even_int", -0.0, 2.0);
    addPowCase(out, "special_neg_zero_neg_odd_int", -0.0, -3.0);
    addPowCase(out, "special_neg_zero_neg_even_int", -0.0, -2.0);

    addPowCase(out, "special_nan_zero",   qNaN, 0.0);
    addPowCase(out, "special_nan_one",    qNaN, 1.0);
    addPowCase(out, "special_one_nan2",   1.0, qNaN);
    addPowCase(out, "special_two_nan",    2.0, qNaN);
    addPowCase(out, "special_nan_nan",    qNaN, qNaN);

    addPowCase(out, "special_neg_one_pinf", -1.0, pInf);
    addPowCase(out, "special_neg_one_ninf", -1.0, nInf);
    addPowCase(out, "special_neg_one_int",  -1.0, 5.0);
    addPowCase(out, "special_neg_one_int_even", -1.0, 6.0);
    addPowCase(out, "special_neg_finite_non_int", -2.5, 0.5);

    addPowCase(out, "special_y_pinf_x_lt_neg1", -2.0, pInf);
    addPowCase(out, "special_y_pinf_x_in_open", 0.5, pInf);
    addPowCase(out, "special_y_pinf_x_gt_1",   2.0, pInf);
    addPowCase(out, "special_y_ninf_x_in_open", 0.5, nInf);
    addPowCase(out, "special_y_ninf_x_gt_1",   2.0, nInf);

    // ============================================================
    // 2. Integer exponents pow(x, k) for x ∈ {2, 0.5, e, π}, k = -50..50
    // ============================================================
    {
        int idx = 0;
        for (int k = -50; k <= 50; ++k) {
            char nm[64];
            const double y = (double) k;
            std::snprintf(nm, sizeof nm, "int_pow2_y%05d", idx++);
            addPowCase(out, nm, 2.0, y);
        }
        idx = 0;
        for (int k = -50; k <= 50; ++k) {
            char nm[64];
            const double y = (double) k;
            std::snprintf(nm, sizeof nm, "int_half_y%05d", idx++);
            addPowCase(out, nm, 0.5, y);
        }
        idx = 0;
        for (int k = -50; k <= 50; ++k) {
            char nm[64];
            const double y = (double) k;
            std::snprintf(nm, sizeof nm, "int_e_y%05d", idx++);
            addPowCase(out, nm, 2.718281828459045, y);
        }
        idx = 0;
        for (int k = -20; k <= 20; ++k) {
            char nm[64];
            const double y = (double) k;
            std::snprintf(nm, sizeof nm, "int_pi_y%05d", idx++);
            addPowCase(out, nm, 3.141592653589793, y);
        }
        idx = 0;
        for (int k = -10; k <= 10; ++k) {
            char nm[64];
            const double y = (double) k;
            std::snprintf(nm, sizeof nm, "int_neg2_y%05d", idx++);
            addPowCase(out, nm, -2.0, y);
        }
    }

    // ============================================================
    // 3. Dense fractional grid (~500): bases × exponents [-10, 10] step 0.1
    // ============================================================
    {
        const double bases[] = {0.1, 0.5, 1.0001, 1.5, 2.0, 2.718281828459045,
                                3.141592653589793, 0.9999, 10.0, 100.0};
        int idx = 0;
        for (double b : bases) {
            for (int k = -100; k <= 100; ++k) {
                const double y = k * 0.1;
                char nm[64];
                std::snprintf(nm, sizeof nm, "dense_b%g_y%05d", b, idx++);
                addPowCase(out, nm, b, y);
            }
        }
    }

    // ============================================================
    // 4. SABR pricing path (~50): forwards × {0.5, 0.7, 0.3, 0.6, 1.5}
    // ============================================================
    {
        // SABR uses pow(forward+shift, 1-beta), pow(f, 2*beta),
        // pow(forward*strike, 1-beta), pow(skewHint, 1.5)
        const double sabr_bases[] = {0.001, 0.01, 0.05, 0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 100.0};
        const double sabr_exps[] = {0.5, 0.7, 0.3, 0.6, 1.5, 1.0 - 0.7, 2.0 * 0.7};
        int idx = 0;
        for (double b : sabr_bases) {
            for (double e : sabr_exps) {
                char nm[64];
                std::snprintf(nm, sizeof nm, "sabr_b%g_e%g_%05d", b, e, idx++);
                addPowCase(out, nm, b, e);
            }
        }
    }

    // ============================================================
    // 5. Vanilla engines (~30): AnalyticBarrier μ, m±λ; BjerksundStensland λ
    // ============================================================
    {
        const double bases[] = {0.5, 1.0, 1.5, 2.0, 3.0, 5.0};
        const double exps[] = {0.3, 0.7, 1.0, 1.3, 1.5, 1.7};
        int idx = 0;
        for (double b : bases) {
            for (double e : exps) {
                char nm[64];
                std::snprintf(nm, sizeof nm, "vanilla_b%g_e%g_%05d", b, e, idx++);
                addPowCase(out, nm, b, e);
            }
        }
    }

    // ============================================================
    // 6. AdaptiveRungeKutta exponents -0.2 (PSHRINK), 0.25 (PGROW)
    // ============================================================
    {
        const double bases[] = {1e-12, 1e-9, 1e-6, 1e-3, 1.0, 1e3, 1e6, 1e9};
        int idx = 0;
        for (double b : bases) {
            char nm[64];
            std::snprintf(nm, sizeof nm, "rk_pshrink_%05d", idx++);
            addPowCase(out, nm, b, -0.2);
            std::snprintf(nm, sizeof nm, "rk_pgrow_%05d", idx);
            addPowCase(out, nm, b, 0.25);
        }
    }

    // ============================================================
    // 7. InterestRate compounding pow(1 + r/freq, freq*t)
    // ============================================================
    {
        const double bases[] = {1.0001, 1.001, 1.005, 1.01, 1.02, 1.05, 1.10, 1.25, 1.50};
        const double exps[] = {0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.0, 4.0, 12.0, 365.0};
        int idx = 0;
        for (double b : bases) {
            for (double e : exps) {
                char nm[64];
                std::snprintf(nm, sizeof nm, "compound_b%g_e%g_%05d", b, e, idx++);
                addPowCase(out, nm, b, e);
            }
        }
    }

    // ============================================================
    // 8. Large stress (~20)
    // ============================================================
    addPowCase(out, "stress_huge_iter",     1.0001,    100000.0);
    addPowCase(out, "stress_neg_huge_iter", 0.9999,   -100000.0);
    addPowCase(out, "stress_pow2_1023",     2.0,       1023.0);
    addPowCase(out, "stress_pow2_neg1022",  2.0,      -1022.0);
    addPowCase(out, "stress_subnormal_15",  0x1p-1022, 1.5);
    addPowCase(out, "stress_max_995",       0x1.0p1023, 0.999999);
    addPowCase(out, "stress_min_neg1022",   0x1p-1022, -1.0);
    addPowCase(out, "stress_two_500",       2.0, 500.0);
    addPowCase(out, "stress_two_neg500",    2.0, -500.0);
    addPowCase(out, "stress_huge_pos",      1.7976931348623157e+308, 0.5);
    addPowCase(out, "stress_huge_neg",      1.7976931348623157e+308, -0.5);
    addPowCase(out, "stress_overflow_just_under", 2.0, 1023.5);
    addPowCase(out, "stress_overflow_just_over",  2.0, 1024.5);
    addPowCase(out, "stress_underflow_just_under", 0.5, 1022.5);
    addPowCase(out, "stress_underflow_just_over",  0.5, 1075.0);
    addPowCase(out, "stress_subnormal_result", 2.0, -1075.0);

    // ============================================================
    // 9. Subnormal + boundary (~50)
    // ============================================================
    {
        // x near 2^-1022, 2^1023; y values that drift result into subnormal/overflow
        const std::uint64_t bases_bits[] = {
            0x0010000000000000ULL,      // min normal
            0x000fffffffffffffULL,      // largest subnormal
            0x0008000000000000ULL,      // mid subnormal
            0x0000000000000001ULL,      // min subnormal
            0x7fefffffffffffffULL,      // max normal
            0x7fdfffffffffffffULL,      // ~half max
            0x3ff0000000000000ULL,      // 1.0
            0x4000000000000000ULL,      // 2.0
            0x3fe0000000000000ULL,      // 0.5
        };
        const double exps[] = {-2.0, -1.5, -1.0, -0.5, 0.5, 1.0, 1.5, 2.0};
        int idx = 0;
        for (std::uint64_t b_bits : bases_bits) {
            for (double e : exps) {
                char nm[64];
                std::snprintf(nm, sizeof nm, "subn_b%016llx_e%g_%05d",
                              (unsigned long long) b_bits, e, idx++);
                addPowCase(out, nm, fromBits(b_bits), e);
            }
        }
    }

    // ============================================================
    // 10. Hard-rounding-boundary cases — drives ZIV3 stage
    //     (CORE-MATH worst-case search outputs)
    // ============================================================
    // From CORE-MATH pow.c sources: bases,exps cited in test files
    addPowCase(out, "hard_e_one",        2.718281828459045, 1.0);
    addPowCase(out, "hard_pi_e",         3.141592653589793, 2.718281828459045);
    addPowCase(out, "hard_e_pi",         2.718281828459045, 3.141592653589793);
    addPowCase(out, "hard_phi",          1.6180339887498949, 1.6180339887498949);
    addPowCase(out, "hard_sqrt2",        std::sqrt(2.0), 0.5);
    addPowCase(out, "hard_3p33",         3.0, 33.0);
    addPowCase(out, "hard_3p34",         3.0, 34.0);
    addPowCase(out, "hard_2p1023",       2.0, 1023.0);
    addPowCase(out, "hard_3pneg33",      3.0, -33.0);
    addPowCase(out, "hard_close_one_y_huge", 0x1.0000000000001p+0, 1e15);
    addPowCase(out, "hard_close_one_y_huge_neg", 0x1.fffffffffffffp-1, 1e15);

    // ============================================================
    // 11. Common-use grid: pow(x, 2), pow(x, 3) for arithmetic
    // ============================================================
    {
        const double bases[] = {1.5, 2.5, 3.5, 4.5, 5.5, 7.5, 10.5,
                                100.0, 1000.0, 1e10, 1e-10, 1e-100};
        const double exps[] = {2.0, 3.0, 4.0};
        int idx = 0;
        for (double b : bases) {
            for (double e : exps) {
                char nm[64];
                std::snprintf(nm, sizeof nm, "small_int_b%g_e%g_%05d", b, e, idx++);
                addPowCase(out, nm, b, e);
            }
        }
    }

    out.write();
    return 0;
}
