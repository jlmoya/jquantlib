// migration-harness/cpp/probes/transcendental/sin_probe.cpp
// Phase 2i.5 WI-1.1 — emit bit-exact CORE-MATH cr_sin(x) for a curated input set.
//
// Oracle: CORE-MATH cr_sin (correctly-rounded by design), NOT std::sin.

#include <ql/version.hpp>
#include "../common.hpp"

extern "C" {
#include "coremath/sin.c"
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

void addSinCase(ReferenceWriter& out, const std::string& name, double x) {
    const double y = cr_sin(x);
    out.addCase(name,
        json{{"x", encodeX(x)}},
        json{{"y_bits", hexBits(y)}});
}

} // namespace

int main() {
    ReferenceWriter out("math/transcendental/sin", QL_VERSION, "sin_probe");

    const double PI = 3.141592653589793;

    // IEEE-754 specials
    addSinCase(out, "pos_zero", +0.0);
    addSinCase(out, "neg_zero", -0.0);
    addSinCase(out, "pos_inf", std::numeric_limits<double>::infinity());
    addSinCase(out, "neg_inf", -std::numeric_limits<double>::infinity());
    addSinCase(out, "qnan", std::numeric_limits<double>::quiet_NaN());
    addSinCase(out, "min_subnormal", std::numeric_limits<double>::denorm_min());
    addSinCase(out, "neg_min_subnormal", -std::numeric_limits<double>::denorm_min());
    addSinCase(out, "min_normal", std::numeric_limits<double>::min());
    addSinCase(out, "neg_min_normal", -std::numeric_limits<double>::min());

    // Exact-result inputs (sin extrema/roots)
    addSinCase(out, "pos_one", 1.0);
    addSinCase(out, "neg_one", -1.0);
    addSinCase(out, "pi_over_2", PI / 2.0);
    addSinCase(out, "neg_pi_over_2", -PI / 2.0);
    addSinCase(out, "pi", PI);
    addSinCase(out, "neg_pi", -PI);
    addSinCase(out, "two_pi", 2.0 * PI);
    addSinCase(out, "neg_two_pi", -2.0 * PI);
    addSinCase(out, "three_pi_over_2", 3.0 * PI / 2.0);

    // Symmetric inputs
    addSinCase(out, "pi_over_6", PI / 6.0);
    addSinCase(out, "pi_over_4", PI / 4.0);
    addSinCase(out, "pi_over_3", PI / 3.0);
    addSinCase(out, "neg_pi_over_6", -PI / 6.0);
    addSinCase(out, "neg_pi_over_4", -PI / 4.0);
    addSinCase(out, "neg_pi_over_3", -PI / 3.0);

    // Payne-Hanek stress: π · 2^k for k=10..50
    int idx = 0;
    for (int k = 10; k <= 50; ++k) {
        const double x = PI * std::ldexp(1.0, k);
        char nm[32]; std::snprintf(nm, sizeof nm, "ph_pos_%02d", idx);
        addSinCase(out, nm, x);
        std::snprintf(nm, sizeof nm, "ph_neg_%02d", idx);
        addSinCase(out, nm, -x);
        ++idx;
    }

    // Dense [-2π, 2π] @ 0.01
    idx = 0;
    const int n = static_cast<int>(2.0 * PI / 0.01);
    for (int k = -n; k <= n; ++k) {
        const double x = k * 0.01;
        char nm[32]; std::snprintf(nm, sizeof nm, "dense_%04d", idx++);
        addSinCase(out, nm, x);
    }

    // Tiny inputs (round-to-input regime: |x| <= 0x1.7137449123ef6p-26)
    addSinCase(out, "tiny_pos_2pm54", std::ldexp(1.0, -54));
    addSinCase(out, "tiny_neg_2pm54", -std::ldexp(1.0, -54));
    addSinCase(out, "tiny_pos_2pm30", std::ldexp(1.0, -30));
    addSinCase(out, "tiny_neg_2pm30", -std::ldexp(1.0, -30));
    {
        const auto fromBits = [](std::uint64_t b) -> double {
            double d; std::memcpy(&d, &b, sizeof d); return d;
        };
        addSinCase(out, "below_thresh", fromBits(0x3e57137449123ef6ULL));
        addSinCase(out, "at_thresh",    fromBits(0x3e57137449123ef7ULL));
    }

    // Hard cases from sin_accurate exception table (2 entries)
    {
        const auto fromBits = [](std::uint64_t b) -> double {
            double d; std::memcpy(&d, &b, sizeof d); return d;
        };
        // 0x1.e0000000001c2p-20  — exp=0x3eb, frac=0xe0000000001c2
        addSinCase(out, "exc_sin_0_pos", fromBits(0x3ebe0000000001c2ULL));
        addSinCase(out, "exc_sin_0_neg", -fromBits(0x3ebe0000000001c2ULL));
        // 0x1.6ac5b262ca1ffp+849  — exp=(1023+849)=0x750, frac=0x6ac5b262ca1ff
        addSinCase(out, "exc_sin_1_pos", fromBits(0x7506ac5b262ca1ffULL));
        addSinCase(out, "exc_sin_1_neg", -fromBits(0x7506ac5b262ca1ffULL));
    }

    // Worst-case Payne-Hanek points
    {
        const auto fromBits = [](std::uint64_t b) -> double {
            double d; std::memcpy(&d, &b, sizeof d); return d;
        };
        // 0x1.6ac5b262ca1ffp+851 — exp=0x752, frac=0x6ac5b262ca1ff
        addSinCase(out, "ph_worst_851",     fromBits(0x7526ac5b262ca1ffULL));
        addSinCase(out, "ph_worst_851_neg", -fromBits(0x7526ac5b262ca1ffULL));
        // 0x1.61a3db8c8d129p+1023 (mentioned in source comments)
        addSinCase(out, "ph_huge_1023",     fromBits(0x7fe61a3db8c8d129ULL));
    }

    out.write();
    return 0;
}
