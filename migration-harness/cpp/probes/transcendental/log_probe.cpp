// migration-harness/cpp/probes/transcendental/log_probe.cpp
// Phase 2i.6 WI-1 — emit bit-exact CORE-MATH cr_log(x) for a curated input
// set.
//
// Oracle: CORE-MATH cr_log (correctly-rounded by design), NOT std::log. Per
// Phase 2i A3 (commit a61b920): Apple libm std::log is not always
// correctly-rounded at hard-rounding boundaries.
//
// Note: log uses its own self-contained dint64 helper (log_dint.h, frozen
// since CORE-MATH 2022) — different from the dint.h shared between sin/cos
// because log.c was authored before the canonical pow/dint.h split. The
// log.c file in this directory has been minimally patched to include
// log_dint.h instead of dint.h to avoid clashing with sin/cos's dint.h.

#include <ql/version.hpp>
#include "../common.hpp"

extern "C" {
    #include "coremath/log.c"
}

#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <limits>

using namespace jqml_harness;

namespace {

std::string hexBits(double y) {
    std::uint64_t bits;
    std::memcpy(&bits, &y, sizeof bits);
    char buf[32];
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

void addLogCase(ReferenceWriter& out, const std::string& name, double x) {
    out.addCase(name,
        json{{"x", encodeX(x)}},
        json{{"y_bits", hexBits(cr_log(x))}});
}

} // namespace

int main() {
    ReferenceWriter out("math/transcendental/log", QL_VERSION, "log_probe");

    // IEEE-754 specials
    addLogCase(out, "pos_zero", +0.0);
    addLogCase(out, "neg_zero", -0.0);
    addLogCase(out, "pos_inf", std::numeric_limits<double>::infinity());
    addLogCase(out, "neg_inf", -std::numeric_limits<double>::infinity());
    addLogCase(out, "qnan", std::numeric_limits<double>::quiet_NaN());

    addLogCase(out, "min_subnormal", std::numeric_limits<double>::denorm_min());
    addLogCase(out, "min_normal", std::numeric_limits<double>::min());
    addLogCase(out, "max_normal", std::numeric_limits<double>::max());

    // Negative inputs (all → NaN)
    addLogCase(out, "neg_one", -1.0);
    addLogCase(out, "neg_pi", -3.141592653589793);
    addLogCase(out, "neg_half", -0.5);
    addLogCase(out, "neg_min_subnormal", -std::numeric_limits<double>::denorm_min());

    // Exact-or-near-exact result inputs
    addLogCase(out, "one", 1.0);
    addLogCase(out, "e", 2.718281828459045);

    // Powers of 2 (sample subset).
    int idx = 0;
    for (int k : {-1074, -1000, -100, -50, -10, -2, -1, 0, 1, 2, 10, 50, 100, 1000, 1023}) {
        const double x = std::ldexp(1.0, k);
        char nm[32]; std::snprintf(nm, sizeof nm, "pow2_%05d", idx++);
        addLogCase(out, nm, x);
    }

    // Dense (0, 10] @ 0.01
    idx = 0;
    for (int k = 1; k <= 1000; ++k) {
        const double x = k * 0.01;
        char nm[32]; std::snprintf(nm, sizeof nm, "dense_%04d", idx++);
        addLogCase(out, nm, x);
    }

    // Sparse logarithmic (0, 1e308] — 10^k for k=-300..300 step 10
    idx = 0;
    for (int k = -300; k <= 300; k += 10) {
        const double x = std::pow(10.0, (double) k);
        char nm[32]; std::snprintf(nm, sizeof nm, "log10_%05d", idx++);
        addLogCase(out, nm, x);
    }

    // Tiny-near-1 inputs (where log(x) ≈ x - 1; argument-reduction stress)
    addLogCase(out, "near1_pos_2pm52", 1.0 + std::ldexp(1.0, -52));
    addLogCase(out, "near1_neg_2pm52", 1.0 - std::ldexp(1.0, -52));
    addLogCase(out, "near1_pos_2pm30", 1.0 + std::ldexp(1.0, -30));
    addLogCase(out, "near1_neg_2pm30", 1.0 - std::ldexp(1.0, -30));
    addLogCase(out, "near1_pos_2pm5",  1.0 + std::ldexp(1.0, -5));
    addLogCase(out, "near1_neg_2pm5",  1.0 - std::ldexp(1.0, -5));

    // Boundary near sqrt(2) — fast-path cy[c] selector flips at x >=
    // 0x16a09e667f3bcd in the m representation.
    addLogCase(out, "near_sqrt2_lo", fromBits(0x3ff6a09e667f3bccULL));
    addLogCase(out, "near_sqrt2",    fromBits(0x3ff6a09e667f3bcdULL));
    addLogCase(out, "near_sqrt2_hi", fromBits(0x3ff6a09e667f3bceULL));

    // Subnormal range — log handles subnormals by scaling by 2^52.
    addLogCase(out, "subnormal_small", fromBits(0x0000000000000010ULL));
    addLogCase(out, "subnormal_mid",   fromBits(0x0008000000000000ULL));
    addLogCase(out, "subnormal_large", fromBits(0x000fffffffffffffULL));

    // Worst-case input cited in CORE-MATH log.c source comments
    // (line ~680: "if we replace the 0x1.b6p-69 bound by 0x1.3fp-69, it
    // fails for x=0x1.71f7c59ede8ep+125 (rndz)").
    addLogCase(out, "ph_worst_125", fromBits(0x47c71f7c59ede8e0ULL));

    // Random-ish dense pre-1 sweep (where x < 1 ⇒ log x < 0): add some
    // extra coverage around the right boundary [0.99, 1.01].
    idx = 0;
    for (int k = -50; k <= 50; ++k) {
        if (k == 0) continue; // already covered as "one"
        const double x = 1.0 + k * 1e-4;
        char nm[40]; std::snprintf(nm, sizeof nm, "near1_band_%04d", idx++);
        addLogCase(out, nm, x);
    }

    // Per Phase 2i.5 lesson: log.c (current master, 818 LOC) does NOT
    // ship an explicit hard-cases / exception table on the fast path —
    // instead it falls back to the dint64 accurate path whenever the
    // [left, right] error interval straddles a rounding boundary
    // (~2^-11.5 of inputs). Older 2022 log.c had a 27-entry T[][] table;
    // it was removed in commit ab6ee9e (Sep 2022) when Gappa proved the
    // tighter 0x1.b6p-69 error bound made the table redundant. We
    // therefore have no per-entry hard-cases to enumerate here — coverage
    // comes from the dense + log10 + ph_worst sweeps above which already
    // exercise the accurate-path entry.

    out.write();
    return 0;
}
