// migration-harness/cpp/probes/transcendental/exp_probe.cpp
// Phase 2i WI-1.1 — emit bit-exact std::exp(x) for a curated input set
// covering IEEE-754 special cases, argument-reduction breakpoints, and
// dense/sparse coverage of the representable domain.
//
// Output: migration-harness/references/math/transcendental/exp.json
// Schema: each case has "x" (double) and "y_bits" (hex string of std::exp(x) raw bits).

#include <ql/version.hpp>
#include "../common.hpp"

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

// nlohmann/json serialises non-finite doubles as JSON null, which loses
// information. Emit them as canonical strings ("Infinity", "-Infinity",
// "NaN") so the Java side can recover them via Double.parseDouble.
json encodeX(double x) {
    if (std::isnan(x)) return json("NaN");
    if (std::isinf(x)) return json(x > 0 ? "Infinity" : "-Infinity");
    return json(x);
}

void addExpCase(ReferenceWriter& out, const std::string& name, double x) {
    out.addCase(name,
        json{{"x", encodeX(x)}},
        json{{"y_bits", hexBits(std::exp(x))}});
}

} // namespace

int main() {
    ReferenceWriter out("math/transcendental/exp", QL_VERSION, "exp_probe");

    // Special values
    addExpCase(out, "pos_zero", +0.0);
    addExpCase(out, "neg_zero", -0.0);
    addExpCase(out, "pos_inf", std::numeric_limits<double>::infinity());
    addExpCase(out, "neg_inf", -std::numeric_limits<double>::infinity());
    addExpCase(out, "qnan", std::numeric_limits<double>::quiet_NaN());

    addExpCase(out, "min_subnormal", std::numeric_limits<double>::denorm_min());
    addExpCase(out, "neg_min_subnormal", -std::numeric_limits<double>::denorm_min());
    addExpCase(out, "min_normal", std::numeric_limits<double>::min());
    addExpCase(out, "neg_min_normal", -std::numeric_limits<double>::min());

    // Argument-reduction breakpoints
    addExpCase(out, "pos_one", 1.0);
    addExpCase(out, "neg_one", -1.0);
    addExpCase(out, "pos_ln2", 0.6931471805599453);
    addExpCase(out, "neg_ln2", -0.6931471805599453);
    addExpCase(out, "pos_ln2_half", 0.34657359027997264);
    addExpCase(out, "neg_ln2_half", -0.34657359027997264);
    addExpCase(out, "pos_ln2_x32", 22.180709777891857);
    addExpCase(out, "neg_ln2_x32", -22.180709777891857);

    // Pi multiples
    addExpCase(out, "pos_pi", 3.141592653589793);
    addExpCase(out, "neg_pi", -3.141592653589793);
    addExpCase(out, "pos_pi_half", 1.5707963267948966);
    addExpCase(out, "neg_pi_half", -1.5707963267948966);

    // Overflow / underflow boundaries
    addExpCase(out, "single_prec_overflow_boundary", 88.0);
    addExpCase(out, "neg_single_prec_overflow_boundary", -88.0);
    addExpCase(out, "double_overflow_just_under", 709.78);
    addExpCase(out, "double_overflow_just_over", 709.79);
    addExpCase(out, "double_underflow_just_under", -745.13);
    addExpCase(out, "double_underflow_just_over", -745.14);

    // Dense [-10, 10] @ 0.05
    int idx = 0;
    for (int k = -200; k <= 200; ++k) {
        const double x = k * 0.05;
        char nm[32]; std::snprintf(nm, sizeof nm, "dense_%04d", idx++);
        addExpCase(out, nm, x);
    }

    // Sparse [-700, 700] @ 50
    idx = 0;
    for (int k = -14; k <= 14; ++k) {
        const double x = k * 50.0;
        char nm[32]; std::snprintf(nm, sizeof nm, "sparse_%04d", idx++);
        addExpCase(out, nm, x);
    }

    out.write();
    return 0;
}
