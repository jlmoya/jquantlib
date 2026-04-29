// migration-harness/cpp/probes/transcendental/dint64_probe.cpp
// Phase 2i.5 WI-1.0 — emit CORE-MATH dint64_t operation results for
// pure-Java port cross-validation. Each test case captures input(s) and
// the bit-exact output of a single dint64_t operation, so the Java
// Dint64 class can be checked field-for-field (lo, hi, ex, sgn) and,
// for dint_tod, byte-for-byte against the C reference.
//
// Output: migration-harness/references/math/transcendental/dint64.json
//
// Schema per case:
//   inputs.op  ∈ {"fromDouble", "toDouble", "add", "mul", "mul21", "copy",
//                 "cmpAbs"}
//   inputs.{a,b}      doubles (for fromDouble / add / mul / mul21)
//   inputs.{lo,hi,...} hex-encoded fields (for toDouble / cmpAbs cases that
//                                          start from a synthetic dint)
//   expected           depends on op:
//     fromDouble | add | mul | mul21 | copy → {lo, hi, ex, sgn} hex/long
//     toDouble  → {y_bits} hex
//     cmpAbs    → {result} integer in {-1, 0, +1}
//
// Oracle: CORE-MATH dint64_t support extracted from sin.c (canonical Inria
// version, MIT-licensed). This same support code is reused by sin/cos/log/pow
// in CORE-MATH; the Java port lives at org.jquantlib.math.transcendental.Dint64
// and underpins SinCosKernel (sub-layer 1.1) and future Phase 2j primitives.

#include <ql/version.hpp>
#include "../common.hpp"

#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <limits>
#include <string>

extern "C" {
#include "coremath/dint.h"
}

using namespace jqml_harness;

namespace {

std::string hex64(std::uint64_t v) {
    char buf[24];
    std::snprintf(buf, sizeof buf, "0x%016llx", (unsigned long long) v);
    return std::string(buf);
}

json dintToJson(const dint64_t& d) {
    return json{
        {"lo", hex64(d.lo)},
        {"hi", hex64(d.hi)},
        {"ex", (long long) d.ex},
        {"sgn", (unsigned long long) d.sgn}
    };
}

// Encode a possibly non-finite double as JSON: nlohmann/json maps NaN/inf
// to JSON null which loses information. Use canonical strings instead;
// the Java side recovers them via Double.parseDouble.
json encodeX(double x) {
    if (std::isnan(x)) return json("NaN");
    if (std::isinf(x)) return json(x > 0 ? "Infinity" : "-Infinity");
    return json(x);
}

void addFromDouble(ReferenceWriter& out, const std::string& name, double a) {
    dint64_t r;
    // dint_fromd is documented "non-zero double". For zero/NaN/inf inputs
    // the production CORE-MATH callers branch out before calling
    // dint_fromd; the Java port mirrors that. We still want to capture
    // CORE-MATH's bit-exact output for finite normal/subnormal inputs to
    // exercise dint_fromd's full code path (mantissa extraction, leading-
    // zero shift, exponent adjustment for subnormals).
    if (a == 0.0 || !std::isfinite(a)) {
        // Skip — these inputs are not part of dint_fromd's contract.
        return;
    }
    dint_fromd(&r, a);
    out.addCase(name,
        json{{"op", "fromDouble"}, {"a", encodeX(a)}},
        dintToJson(r));
}

void addToDouble(ReferenceWriter& out, const std::string& name,
                 std::uint64_t lo, std::uint64_t hi,
                 std::int64_t ex, std::uint64_t sgn) {
    dint64_t a = {};
    a.lo = lo; a.hi = hi; a.ex = ex; a.sgn = sgn;
    // dint_tod mutates `a` (subnormalize_dint). Copy so the input we
    // record matches the actual dint passed in.
    dint64_t in = a;
    double y = dint_tod(&a);
    std::uint64_t bits;
    std::memcpy(&bits, &y, sizeof bits);
    out.addCase(name,
        json{
            {"op", "toDouble"},
            {"lo", hex64(in.lo)},
            {"hi", hex64(in.hi)},
            {"ex", (long long) in.ex},
            {"sgn", (unsigned long long) in.sgn}
        },
        json{{"y_bits", hex64(bits)}});
}

void addToDoubleFromDouble(ReferenceWriter& out, const std::string& name, double a) {
    // Round-trip: double → dint → double should be exact for finite inputs
    // that fit cleanly in dint's representation.
    if (a == 0.0 || !std::isfinite(a)) return;
    dint64_t d;
    dint_fromd(&d, a);
    addToDouble(out, name, d.lo, d.hi, d.ex, d.sgn);
}

void addAdd(ReferenceWriter& out, const std::string& name, double a, double b) {
    if (a == 0.0 || b == 0.0 || !std::isfinite(a) || !std::isfinite(b)) return;
    dint64_t da, db, dr;
    dint_fromd(&da, a);
    dint_fromd(&db, b);
    add_dint(&dr, &da, &db);
    out.addCase(name,
        json{{"op", "add"}, {"a", encodeX(a)}, {"b", encodeX(b)}},
        dintToJson(dr));
}

void addMul(ReferenceWriter& out, const std::string& name, double a, double b) {
    if (a == 0.0 || b == 0.0 || !std::isfinite(a) || !std::isfinite(b)) return;
    dint64_t da, db, dr;
    dint_fromd(&da, a);
    dint_fromd(&db, b);
    mul_dint(&dr, &da, &db);
    out.addCase(name,
        json{{"op", "mul"}, {"a", encodeX(a)}, {"b", encodeX(b)}},
        dintToJson(dr));
}

void addMul21(ReferenceWriter& out, const std::string& name, double a, double b) {
    // mul_dint_21 assumes b->lo is zero; dint_fromd always produces lo=0
    // so this is just a regular CORE-MATH call sequence.
    if (a == 0.0 || b == 0.0 || !std::isfinite(a) || !std::isfinite(b)) return;
    dint64_t da, db, dr;
    dint_fromd(&da, a);
    dint_fromd(&db, b);
    mul_dint_21(&dr, &da, &db);
    out.addCase(name,
        json{{"op", "mul21"}, {"a", encodeX(a)}, {"b", encodeX(b)}},
        dintToJson(dr));
}

void addCmpAbs(ReferenceWriter& out, const std::string& name, double a, double b) {
    if (a == 0.0 || b == 0.0 || !std::isfinite(a) || !std::isfinite(b)) return;
    dint64_t da, db;
    dint_fromd(&da, a);
    dint_fromd(&db, b);
    int r = cmp_dint_abs(&da, &db);
    out.addCase(name,
        json{{"op", "cmpAbs"}, {"a", encodeX(a)}, {"b", encodeX(b)}},
        json{{"result", (long long) r}});
}

void addCopy(ReferenceWriter& out, const std::string& name, double a) {
    if (a == 0.0 || !std::isfinite(a)) return;
    dint64_t da, dr;
    dint_fromd(&da, a);
    cp_dint(&dr, &da);
    out.addCase(name,
        json{{"op", "copy"}, {"a", encodeX(a)}},
        dintToJson(dr));
}

} // namespace

int main() {
    ReferenceWriter out("math/transcendental/dint64", QL_VERSION, "dint64_probe");

    // --- fromDouble cases ---
    addFromDouble(out, "from_one",                 1.0);
    addFromDouble(out, "from_neg_one",            -1.0);
    addFromDouble(out, "from_two",                 2.0);
    addFromDouble(out, "from_half",                0.5);
    addFromDouble(out, "from_pi",                  3.141592653589793);
    addFromDouble(out, "from_neg_pi",             -3.141592653589793);
    addFromDouble(out, "from_e",                   2.718281828459045);
    addFromDouble(out, "from_two_pi",              6.283185307179586);
    addFromDouble(out, "from_inv_two_pi",          0.15915494309189535);
    addFromDouble(out, "from_sqrt2",               1.4142135623730951);
    addFromDouble(out, "from_min_normal",          std::numeric_limits<double>::min());
    addFromDouble(out, "from_neg_min_normal",     -std::numeric_limits<double>::min());
    addFromDouble(out, "from_max",                 std::numeric_limits<double>::max());
    addFromDouble(out, "from_neg_max",            -std::numeric_limits<double>::max());
    addFromDouble(out, "from_smallest_subnormal",  std::numeric_limits<double>::denorm_min());
    addFromDouble(out, "from_neg_smallest_subnormal", -std::numeric_limits<double>::denorm_min());
    addFromDouble(out, "from_pow2_pos20",          1048576.0);          // 2^20
    addFromDouble(out, "from_pow2_neg20",          1.0 / 1048576.0);    // 2^-20
    addFromDouble(out, "from_pow2_pos1023",        0x1p+1023);
    addFromDouble(out, "from_pow2_neg1022",        0x1p-1022);
    addFromDouble(out, "from_neg_pow2_pos1023",   -0x1p+1023);
    addFromDouble(out, "from_just_above_one",      1.0000000000000002);
    addFromDouble(out, "from_just_below_one",      0.9999999999999999);

    // --- toDouble round-trip cases (double → dint → double; should be exact) ---
    addToDoubleFromDouble(out, "tod_one",         1.0);
    addToDoubleFromDouble(out, "tod_neg_one",    -1.0);
    addToDoubleFromDouble(out, "tod_pi",          3.141592653589793);
    addToDoubleFromDouble(out, "tod_neg_pi",     -3.141592653589793);
    addToDoubleFromDouble(out, "tod_e",           2.718281828459045);
    addToDoubleFromDouble(out, "tod_inv_two_pi",  0.15915494309189535);
    addToDoubleFromDouble(out, "tod_max",         std::numeric_limits<double>::max());
    addToDoubleFromDouble(out, "tod_min_normal",  std::numeric_limits<double>::min());
    addToDoubleFromDouble(out, "tod_subnormal",   std::numeric_limits<double>::denorm_min());
    addToDoubleFromDouble(out, "tod_pow2_pos20",  1048576.0);
    addToDoubleFromDouble(out, "tod_pow2_neg20",  1.0 / 1048576.0);

    // toDouble overflow / underflow path coverage — synthetic dint inputs
    // crafted at exponent extremes.
    // ZERO sentinel:
    addToDouble(out, "tod_zero", 0x0ULL, 0x0ULL, -1076, 0x0ULL);
    // Just past overflow (ex=1025):
    addToDouble(out, "tod_overflow_just",  0x8000000000000000ULL, 0x0ULL, 1025, 0x0ULL);
    addToDouble(out, "tod_overflow_far",   0x8000000000000000ULL, 0x0ULL, 1100, 0x0ULL);
    // Just past underflow (ex=-1074, -1100):
    addToDouble(out, "tod_underflow_neg1074", 0x8000000000000000ULL, 0x0ULL, -1074, 0x0ULL);
    addToDouble(out, "tod_underflow_far",     0x8000000000000000ULL, 0x0ULL, -1100, 0x0ULL);
    // Negative-sign forms:
    addToDouble(out, "tod_neg_one_synth", 0x8000000000000000ULL, 0x0ULL, 1, 0x1ULL);
    // Subnormal-result regime (ex in [-1073, -1023]):
    addToDouble(out, "tod_subnormal_ex_-1023", 0x8000000000000000ULL, 0x0ULL, -1023, 0x0ULL);
    addToDouble(out, "tod_subnormal_ex_-1050", 0x8000000000000000ULL, 0x0ULL, -1050, 0x0ULL);
    addToDouble(out, "tod_subnormal_ex_-1073", 0x8000000000000000ULL, 0x0ULL, -1073, 0x0ULL);

    // --- add cases ---
    addAdd(out, "add_one_one",               1.0, 1.0);
    addAdd(out, "add_one_neg_one",           1.0, -1.0);
    addAdd(out, "add_neg_one_one",          -1.0, 1.0);
    addAdd(out, "add_pi_e",                  3.141592653589793, 2.718281828459045);
    addAdd(out, "add_pi_neg_e",              3.141592653589793, -2.718281828459045);
    addAdd(out, "add_neg_pi_e",             -3.141592653589793, 2.718281828459045);
    addAdd(out, "add_neg_pi_neg_e",         -3.141592653589793, -2.718281828459045);
    addAdd(out, "add_one_tiny",              1.0, 0x1p-50);
    addAdd(out, "add_one_neg_tiny",          1.0, -0x1p-50);
    addAdd(out, "add_one_huge",              1.0, 0x1p+50);
    addAdd(out, "add_a_lt_b",                0x1p-10, 0x1p+10);              // |a| < |b|
    addAdd(out, "add_a_gt_b",                0x1p+10, 0x1p-10);              // |a| > |b|
    addAdd(out, "add_sterbenz",              1.5, -1.0);                     // Sterbenz cancellation
    addAdd(out, "add_sterbenz_neg",         -1.5, 1.0);
    addAdd(out, "add_far_separation_pos",    0x1p+100, 0x1p-100);
    addAdd(out, "add_far_separation_neg",   -0x1p+100, 0x1p-100);
    addAdd(out, "add_equal_mag_diff_sign",   0x1.5p+10, -0x1.5p+10);         // exact zero result
    addAdd(out, "add_equal_mag_same_sign",   0x1.5p+10, 0x1.5p+10);          // exponent bump
    addAdd(out, "add_close_diff_sign_loss",  1.0, -0x1.fffffffffffffp-1);    // major cancellation
    addAdd(out, "add_pi_half_minus",         1.5707963267948966, -1.5707963267948965);
    // 128-bit-overflow boundary: probe cases that exercise the
    // ((carry == 1L) && (sumHi == aHi)) branch in addAssign overflow detection.
    // Same-sign large-mantissa inputs — sumLo likely wraps, and the high-word
    // comparison selects the carry-only path or the unsignedLessThan path.
    addAdd(out, "add_max_normal_pos",       0x1.fffffffffffffp+1023, 0x1.fffffffffffffp+1023);  // → +Inf
    addAdd(out, "add_max_normal_neg",      -0x1.fffffffffffffp+1023, -0x1.fffffffffffffp+1023); // → -Inf
    addAdd(out, "add_just_under_max_pos",   0x1p+1023, 0x1p+1023);             // finite, max-exponent
    addAdd(out, "add_carry_chain_a",        0x1.fffffffffffffp+0, 0x1.0000000000001p+0);        // adjacent mantissas
    addAdd(out, "add_carry_chain_b",        0x1.8p+0, 0x1.8p+0);              // identical large mantissas

    // --- mul cases ---
    addMul(out, "mul_one_one",          1.0, 1.0);
    addMul(out, "mul_one_neg_one",      1.0, -1.0);
    addMul(out, "mul_neg_one_neg_one", -1.0, -1.0);
    addMul(out, "mul_pi_e",             3.141592653589793, 2.718281828459045);
    addMul(out, "mul_pi_inv_two_pi",    3.141592653589793, 0.15915494309189535);
    addMul(out, "mul_two_pi_inv",       6.283185307179586, 0.15915494309189535);
    addMul(out, "mul_two_half",         2.0, 0.5);
    addMul(out, "mul_pow2_pos_neg",     0x1p+50, 0x1p-50);
    addMul(out, "mul_overflow_borderline", 0x1p+512, 0x1p+512);
    addMul(out, "mul_underflow_borderline", 0x1p-512, 0x1p-512);
    addMul(out, "mul_small_huge",       0x1p-100, 0x1p+100);
    addMul(out, "mul_neg_small_huge",  -0x1p-100, 0x1p+100);
    addMul(out, "mul_sqrt2_sqrt2",      1.4142135623730951, 1.4142135623730951);

    // --- mul21 cases (b->lo is zero, true after dint_fromd) ---
    addMul21(out, "mul21_one_one",       1.0, 1.0);
    addMul21(out, "mul21_pi_e",          3.141592653589793, 2.718281828459045);
    addMul21(out, "mul21_pow2",          0x1p+10, 0x1p+10);
    addMul21(out, "mul21_neg_pos",      -2.5, 4.0);
    // Additional coverage mirroring mul's sign/magnitude sweep:
    addMul21(out, "mul21_neg_one_neg_one",   -1.0, -1.0);                     // both negative → positive
    addMul21(out, "mul21_pow2_pos_neg",       0x1p+10, -0x1p-5);              // sign mismatch, magnitude differ
    addMul21(out, "mul21_sqrt2_sqrt2",        1.4142135623730951, 1.4142135623730951); // equal magnitude, near 2
    addMul21(out, "mul21_overflow_borderline", 0x1p+512, 0x1p+512);            // large exponent
    addMul21(out, "mul21_underflow_borderline", 0x1p-512, 0x1p-512);          // small exponent

    // --- copy cases (sanity) ---
    addCopy(out, "copy_one",  1.0);
    addCopy(out, "copy_pi",   3.141592653589793);
    addCopy(out, "copy_max",  std::numeric_limits<double>::max());

    // --- cmp_dint_abs cases ---
    addCmpAbs(out, "cmpabs_eq",          1.0, 1.0);              // 0
    addCmpAbs(out, "cmpabs_pos_neg_eq",  1.0, -1.0);             // 0 (abs values equal)
    addCmpAbs(out, "cmpabs_lt",          1.0, 2.0);              // -1
    addCmpAbs(out, "cmpabs_gt",          2.0, 1.0);              // +1
    addCmpAbs(out, "cmpabs_neg_lt",     -1.0, 2.0);              // -1 (still on |.|)
    addCmpAbs(out, "cmpabs_neg_gt",     -2.0, 1.0);              // +1
    addCmpAbs(out, "cmpabs_close",       1.0, 1.0000000000000002); // -1

    out.write();
    return 0;
}
