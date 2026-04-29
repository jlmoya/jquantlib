// migration-harness/cpp/probes/transcendental/cos_probe.cpp
// Phase 2i.5 WI-1.1 — emit bit-exact CORE-MATH cr_cos(x) for a curated input set.
//
// Oracle: CORE-MATH cr_cos (correctly-rounded by design), NOT std::cos.
// Per Phase 2i A3: Apple libm std::cos is not always correctly-rounded at
// hard-rounding boundaries; CORE-MATH cr_cos is the only reliable EXACT-tier
// reference.

#include <ql/version.hpp>
#include "../common.hpp"

extern "C" {
#include "coremath/cos.c"
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

json encodeX(double x) {
    if (std::isnan(x)) return json("NaN");
    if (std::isinf(x)) return json(x > 0 ? "Infinity" : "-Infinity");
    return json(x);
}

void addCosCase(ReferenceWriter& out, const std::string& name, double x) {
    const double y = cr_cos(x);
    out.addCase(name,
        json{{"x", encodeX(x)}},
        json{{"y_bits", hexBits(y)}});
}

} // namespace

int main() {
    ReferenceWriter out("math/transcendental/cos", QL_VERSION, "cos_probe");

    const double PI = 3.141592653589793;

    // IEEE-754 specials
    addCosCase(out, "pos_zero", +0.0);
    addCosCase(out, "neg_zero", -0.0);
    addCosCase(out, "pos_inf", std::numeric_limits<double>::infinity());
    addCosCase(out, "neg_inf", -std::numeric_limits<double>::infinity());
    addCosCase(out, "qnan", std::numeric_limits<double>::quiet_NaN());
    addCosCase(out, "min_subnormal", std::numeric_limits<double>::denorm_min());
    addCosCase(out, "neg_min_subnormal", -std::numeric_limits<double>::denorm_min());
    addCosCase(out, "min_normal", std::numeric_limits<double>::min());
    addCosCase(out, "neg_min_normal", -std::numeric_limits<double>::min());

    // Exact-result inputs
    addCosCase(out, "pos_one", 1.0);
    addCosCase(out, "neg_one", -1.0);
    addCosCase(out, "pi_over_2", PI / 2.0);
    addCosCase(out, "neg_pi_over_2", -PI / 2.0);
    addCosCase(out, "pi", PI);
    addCosCase(out, "neg_pi", -PI);
    addCosCase(out, "two_pi", 2.0 * PI);
    addCosCase(out, "neg_two_pi", -2.0 * PI);
    addCosCase(out, "three_pi_over_2", 3.0 * PI / 2.0);

    // Symmetric inputs
    addCosCase(out, "pi_over_6", PI / 6.0);
    addCosCase(out, "pi_over_4", PI / 4.0);
    addCosCase(out, "pi_over_3", PI / 3.0);
    addCosCase(out, "neg_pi_over_6", -PI / 6.0);
    addCosCase(out, "neg_pi_over_4", -PI / 4.0);
    addCosCase(out, "neg_pi_over_3", -PI / 3.0);

    // Payne-Hanek stress: π · 2^k for k=10..50
    int idx = 0;
    for (int k = 10; k <= 50; ++k) {
        const double x = PI * std::ldexp(1.0, k);
        char nm[32]; std::snprintf(nm, sizeof nm, "ph_pos_%02d", idx);
        addCosCase(out, nm, x);
        std::snprintf(nm, sizeof nm, "ph_neg_%02d", idx);
        addCosCase(out, nm, -x);
        ++idx;
    }

    // Dense [-2π, 2π] @ 0.01
    idx = 0;
    const int n = static_cast<int>(2.0 * PI / 0.01);
    for (int k = -n; k <= n; ++k) {
        const double x = k * 0.01;
        char nm[32]; std::snprintf(nm, sizeof nm, "dense_%04d", idx++);
        addCosCase(out, nm, x);
    }

    // Tiny inputs (round-to-input regime)
    addCosCase(out, "tiny_pos_2pm54", std::ldexp(1.0, -54));
    addCosCase(out, "tiny_neg_2pm54", -std::ldexp(1.0, -54));
    addCosCase(out, "tiny_pos_2pm30", std::ldexp(1.0, -30));
    addCosCase(out, "tiny_neg_2pm30", -std::ldexp(1.0, -30));

    // Below/at the |x| <= 0x1.6a09e667f3bccp-27 fast-return threshold
    {
        std::uint64_t b = 0x3e46a09e667f3bccULL;
        double d; std::memcpy(&d, &b, sizeof d);
        addCosCase(out, "below_thresh", d);
        b = 0x3e46a09e667f3bcdULL;
        std::memcpy(&d, &b, sizeof d);
        addCosCase(out, "at_thresh", d);
    }

    // Hard cases from cos_accurate exception table (5 entries)
    {
        const auto fromBits = [](std::uint64_t b) -> double {
            double d; std::memcpy(&d, &b, sizeof d); return d;
        };
        addCosCase(out, "exc_cos_0_pos", fromBits(0x3e88000000000009ULL)); // 0x1.8000000000009p-23
        addCosCase(out, "exc_cos_0_neg", -fromBits(0x3e88000000000009ULL));
        addCosCase(out, "exc_cos_1_pos", fromBits(0x3e98000000000024ULL)); // 0x1.8000000000024p-22
        addCosCase(out, "exc_cos_1_neg", -fromBits(0x3e98000000000024ULL));
        addCosCase(out, "exc_cos_2_pos", fromBits(0x3ea8000000000090ULL)); // 0x1.800000000009p-21
        addCosCase(out, "exc_cos_2_neg", -fromBits(0x3ea8000000000090ULL));
        addCosCase(out, "exc_cos_3_pos", fromBits(0x3eb20000000000f3ULL)); // 0x1.20000000000f3p-20
        addCosCase(out, "exc_cos_3_neg", -fromBits(0x3eb20000000000f3ULL));
        addCosCase(out, "exc_cos_4_pos", fromBits(0x3eb8000000000240ULL)); // 0x1.800000000024p-20
        addCosCase(out, "exc_cos_4_neg", -fromBits(0x3eb8000000000240ULL));
    }

    // Worst-case Payne-Hanek (reported in CORE-MATH source comments)
    {
        std::uint64_t b = 0x6a86ac5b262ca1ffULL;  // 0x1.6ac5b262ca1ffp+851
        double d; std::memcpy(&d, &b, sizeof d);
        addCosCase(out, "ph_worst_851", d);
        addCosCase(out, "ph_worst_851_neg", -d);
    }

    out.write();
    return 0;
}
