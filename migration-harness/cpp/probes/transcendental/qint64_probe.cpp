// migration-harness/cpp/probes/transcendental/qint64_probe.cpp
// Phase 2n A.0 — emit CORE-MATH qint64_t operation results for pure-Java
// port cross-validation. Each test case captures input(s) and the
// bit-exact output of a single qint64_t operation, so the Java Qint64
// class can be checked field-for-field (ll, lh, hl, hh, ex, sgn) and,
// for qint_tod, byte-for-byte against the C reference.
//
// Output: migration-harness/references/math/transcendental/qint64.json
//
// The qint64 support functions live in CORE-MATH's qint.h. To dodge the
// C++ tokenizer's rejection of pre-C99 string concatenation in qint.h's
// print_qint debug helper, we don't include qint.h directly here. Instead
// we link against qint64_shim.c (compiled as C), which provides the
// extern "C" wrappers declared in qint64_shim.h.
//
// Schema per case:
//   inputs.op  ∈ {"fromDouble", "toDouble", "toLong", "copy",
//                 "cmpQint", "cmpQint22",
//                 "add", "add22",
//                 "mul", "mul_11", "mul_21", "mul_22", "mul_31",
//                 "mul_33", "mul_41", "mul2"}
//
// Oracle: CORE-MATH qint.h (canonical Inria version, MIT-licensed).
// The Java port lives at org.jquantlib.math.transcendental.Qint64 and
// underpins PowKernel (sub-layer 2n A.1).

#include <ql/version.hpp>
#include "../common.hpp"

#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <limits>
#include <string>

extern "C" {
#include "qint64_shim.h"
}

using namespace jqml_harness;

namespace {

// Use a typedef alias so we don't have to spell `qint64_t_shim` everywhere.
using qint = qint64_t_shim;

std::string hex64(std::uint64_t v) {
    char buf[24];
    std::snprintf(buf, sizeof buf, "0x%016llx", (unsigned long long) v);
    return std::string(buf);
}

json qintToJson(const qint& q) {
    return json{
        {"ll", hex64(q.ll)},
        {"lh", hex64(q.lh)},
        {"hl", hex64(q.hl)},
        {"hh", hex64(q.hh)},
        {"ex", (long long) q.ex},
        {"sgn", (unsigned long long) q.sgn}
    };
}

json qintInputJson(const qint& q) {
    json j = qintToJson(q);
    return j;
}

json encodeX(double x) { return json(x); }

void zero(qint& q) {
    q.ll = q.lh = q.hl = q.hh = 0; q.ex = 0; q.sgn = 0;
}

// --- fromDouble ---

void addFromDouble(ReferenceWriter& out, const std::string& name, double a) {
    if (a == 0.0 || !std::isfinite(a)) return;
    qint r{}; zero(r);
    shim_qint_fromd_ext(&r, a);
    out.addCase(name,
        json{{"op", "fromDouble"}, {"a", encodeX(a)}},
        qintToJson(r));
}

// --- copy ---

void addCopy(ReferenceWriter& out, const std::string& name, double a) {
    if (a == 0.0 || !std::isfinite(a)) return;
    qint qa{}, r{}; zero(qa); zero(r);
    shim_qint_fromd_ext(&qa, a);
    shim_cp_qint(&r, &qa);
    out.addCase(name,
        json{{"op", "copy"}, {"a", encodeX(a)}},
        qintToJson(r));
}

// --- cmpQint ---

void addCmpQint(ReferenceWriter& out, const std::string& name, double a, double b) {
    if (a == 0.0 || b == 0.0 || !std::isfinite(a) || !std::isfinite(b)) return;
    qint qa{}, qb{}; zero(qa); zero(qb);
    shim_qint_fromd_ext(&qa, a);
    shim_qint_fromd_ext(&qb, b);
    int r = shim_cmp_qint(&qa, &qb);
    out.addCase(name,
        json{{"op", "cmpQint"}, {"a", encodeX(a)}, {"b", encodeX(b)}},
        json{{"result", (long long) r}});
}

void addCmpQint22(ReferenceWriter& out, const std::string& name, double a, double b) {
    if (a == 0.0 || b == 0.0 || !std::isfinite(a) || !std::isfinite(b)) return;
    qint qa{}, qb{}; zero(qa); zero(qb);
    shim_qint_fromd_ext(&qa, a);
    shim_qint_fromd_ext(&qb, b);
    int r = shim_cmp_qint_22(&qa, &qb);
    out.addCase(name,
        json{{"op", "cmpQint22"}, {"a", encodeX(a)}, {"b", encodeX(b)}},
        json{{"result", (long long) r}});
}

// --- toLong ---

void addToLong(ReferenceWriter& out, const std::string& name, double a) {
    if (a == 0.0 || !std::isfinite(a)) return;
    qint qa{}; zero(qa);
    shim_qint_fromd_ext(&qa, a);
    int64_t r = shim_qint_toi_ext(&qa);
    json inputs = qintInputJson(qa);
    inputs["op"] = "toLong";
    out.addCase(name, inputs,
        json{{"result", (long long) r}});
}

void addToLongSynth(ReferenceWriter& out, const std::string& name,
                    std::uint64_t hh, std::uint64_t hl,
                    std::uint64_t lh, std::uint64_t ll,
                    std::int64_t ex, std::uint64_t sgn) {
    qint qa{}; zero(qa);
    qa.hh = hh; qa.hl = hl; qa.lh = lh; qa.ll = ll;
    qa.ex = ex; qa.sgn = sgn;
    int64_t r = shim_qint_toi_ext(&qa);
    json inputs = qintInputJson(qa);
    inputs["op"] = "toLong";
    out.addCase(name, inputs,
        json{{"result", (long long) r}});
}

// --- toDouble ---

void addToDoubleSynth(ReferenceWriter& out, const std::string& name,
                      std::uint64_t hh, std::uint64_t hl,
                      std::uint64_t lh, std::uint64_t ll,
                      std::int64_t ex, std::uint64_t sgn) {
    qint a{}; zero(a);
    a.hh = hh; a.hl = hl; a.lh = lh; a.ll = ll;
    a.ex = ex; a.sgn = sgn;
    qint in = a;  // shim_qint_tod_ext mutates `a`; record the input.
    double y = shim_qint_tod_ext(&a);
    std::uint64_t bits;
    std::memcpy(&bits, &y, sizeof bits);
    json inputs = qintInputJson(in);
    inputs["op"] = "toDouble";
    out.addCase(name, inputs,
        json{{"y_bits", hex64(bits)}});
}

void addToDoubleFromDouble(ReferenceWriter& out, const std::string& name, double a) {
    if (a == 0.0 || !std::isfinite(a)) return;
    qint q{}; zero(q);
    shim_qint_fromd_ext(&q, a);
    addToDoubleSynth(out, name, q.hh, q.hl, q.lh, q.ll, q.ex, q.sgn);
}

// --- add / add22 ---

void addAdd(ReferenceWriter& out, const std::string& name, double a, double b) {
    if (a == 0.0 || b == 0.0 || !std::isfinite(a) || !std::isfinite(b)) return;
    qint qa{}, qb{}, r{}; zero(qa); zero(qb); zero(r);
    shim_qint_fromd_ext(&qa, a);
    shim_qint_fromd_ext(&qb, b);
    shim_add_qint(&r, &qa, &qb);
    out.addCase(name,
        json{{"op", "add"}, {"a", encodeX(a)}, {"b", encodeX(b)}},
        qintToJson(r));
}

void addAdd22(ReferenceWriter& out, const std::string& name, double a, double b) {
    if (a == 0.0 || b == 0.0 || !std::isfinite(a) || !std::isfinite(b)) return;
    qint qa{}, qb{}, r{}; zero(qa); zero(qb); zero(r);
    shim_qint_fromd_ext(&qa, a);
    shim_qint_fromd_ext(&qb, b);
    shim_add_qint_22(&r, &qa, &qb);
    out.addCase(name,
        json{{"op", "add22"}, {"a", encodeX(a)}, {"b", encodeX(b)}},
        qintToJson(r));
}

// --- mul* ---

#define DEFINE_MUL(opname, opfn)                                           \
void opname(ReferenceWriter& out, const std::string& name,                 \
            double a, double b) {                                          \
    if (a == 0.0 || b == 0.0 || !std::isfinite(a) || !std::isfinite(b))   \
        return;                                                            \
    qint qa{}, qb{}, r{}; zero(qa); zero(qb); zero(r);                    \
    shim_qint_fromd_ext(&qa, a);                                          \
    shim_qint_fromd_ext(&qb, b);                                          \
    opfn(&r, &qa, &qb);                                                    \
    out.addCase(name,                                                      \
        json{{"op", #opname + std::string("").substr(0, 0) + "mul"        \
              + std::string(#opfn).substr(8)},                             \
             {"a", encodeX(a)}, {"b", encodeX(b)}},                       \
        qintToJson(r));                                                    \
}

// We don't use the macro — the op-name mapping is complicated. Inline each.

void addMul(ReferenceWriter& out, const std::string& name, double a, double b) {
    if (a == 0.0 || b == 0.0 || !std::isfinite(a) || !std::isfinite(b)) return;
    qint qa{}, qb{}, r{}; zero(qa); zero(qb); zero(r);
    shim_qint_fromd_ext(&qa, a);
    shim_qint_fromd_ext(&qb, b);
    shim_mul_qint(&r, &qa, &qb);
    out.addCase(name, json{{"op","mul"}, {"a",encodeX(a)}, {"b",encodeX(b)}}, qintToJson(r));
}

void addMul11(ReferenceWriter& out, const std::string& name, double a, double b) {
    if (a == 0.0 || b == 0.0 || !std::isfinite(a) || !std::isfinite(b)) return;
    qint qa{}, qb{}, r{}; zero(qa); zero(qb); zero(r);
    shim_qint_fromd_ext(&qa, a);
    shim_qint_fromd_ext(&qb, b);
    shim_mul_qint_11(&r, &qa, &qb);
    out.addCase(name, json{{"op","mul_11"}, {"a",encodeX(a)}, {"b",encodeX(b)}}, qintToJson(r));
}

void addMul21(ReferenceWriter& out, const std::string& name, double a, double b) {
    if (a == 0.0 || b == 0.0 || !std::isfinite(a) || !std::isfinite(b)) return;
    qint qa{}, qb{}, r{}; zero(qa); zero(qb); zero(r);
    shim_qint_fromd_ext(&qa, a);
    shim_qint_fromd_ext(&qb, b);
    shim_mul_qint_21(&r, &qa, &qb);
    out.addCase(name, json{{"op","mul_21"}, {"a",encodeX(a)}, {"b",encodeX(b)}}, qintToJson(r));
}

void addMul22(ReferenceWriter& out, const std::string& name, double a, double b) {
    if (a == 0.0 || b == 0.0 || !std::isfinite(a) || !std::isfinite(b)) return;
    qint qa{}, qb{}, r{}; zero(qa); zero(qb); zero(r);
    shim_qint_fromd_ext(&qa, a);
    shim_qint_fromd_ext(&qb, b);
    shim_mul_qint_22(&r, &qa, &qb);
    out.addCase(name, json{{"op","mul_22"}, {"a",encodeX(a)}, {"b",encodeX(b)}}, qintToJson(r));
}

void addMul31(ReferenceWriter& out, const std::string& name, double a, double b) {
    if (a == 0.0 || b == 0.0 || !std::isfinite(a) || !std::isfinite(b)) return;
    qint qa{}, qb{}, r{}; zero(qa); zero(qb); zero(r);
    shim_qint_fromd_ext(&qa, a);
    shim_qint_fromd_ext(&qb, b);
    shim_mul_qint_31(&r, &qa, &qb);
    out.addCase(name, json{{"op","mul_31"}, {"a",encodeX(a)}, {"b",encodeX(b)}}, qintToJson(r));
}

void addMul33(ReferenceWriter& out, const std::string& name, double a, double b) {
    if (a == 0.0 || b == 0.0 || !std::isfinite(a) || !std::isfinite(b)) return;
    qint qa{}, qb{}, r{}; zero(qa); zero(qb); zero(r);
    shim_qint_fromd_ext(&qa, a);
    shim_qint_fromd_ext(&qb, b);
    shim_mul_qint_33(&r, &qa, &qb);
    out.addCase(name, json{{"op","mul_33"}, {"a",encodeX(a)}, {"b",encodeX(b)}}, qintToJson(r));
}

void addMul41(ReferenceWriter& out, const std::string& name, double a, double b) {
    if (a == 0.0 || b == 0.0 || !std::isfinite(a) || !std::isfinite(b)) return;
    qint qa{}, qb{}, r{}; zero(qa); zero(qb); zero(r);
    shim_qint_fromd_ext(&qa, a);
    shim_qint_fromd_ext(&qb, b);
    shim_mul_qint_41(&r, &qa, &qb);
    out.addCase(name, json{{"op","mul_41"}, {"a",encodeX(a)}, {"b",encodeX(b)}}, qintToJson(r));
}

void addMul2(ReferenceWriter& out, const std::string& name,
             std::int64_t b, double a) {
    if (a == 0.0 || !std::isfinite(a)) return;
    qint qa{}, r{}; zero(qa); zero(r);
    shim_qint_fromd_ext(&qa, a);
    shim_mul_qint_2(&r, b, &qa);
    out.addCase(name,
        json{{"op", "mul2"}, {"b", (long long) b}, {"a", encodeX(a)}},
        qintToJson(r));
}

const double DOUBLES[] = {
    1.0, -1.0, 2.0, -2.0, 0.5, -0.5,
    3.141592653589793, -3.141592653589793,
    2.718281828459045, -2.718281828459045,
    6.283185307179586, 0.15915494309189535,
    1.4142135623730951,
    1.0000000000000002, 0.9999999999999999,
    1048576.0, -1048576.0, 1.0 / 1048576.0,
    0x1p+50, 0x1p-50, 0x1p+100, 0x1p-100,
    0x1p+512, 0x1p-512,
    0x1p+1023, 0x1p-1022,
    0x1.5p+10, -0x1.5p+10,
    0x1.fffffffffffffp-1,
    1.5707963267948966,
    -1.5707963267948965
};
constexpr int N_DOUBLES = sizeof(DOUBLES) / sizeof(DOUBLES[0]);

} // namespace

int main() {
    ReferenceWriter out("math/transcendental/qint64", QL_VERSION, "qint64_probe");

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
    addFromDouble(out, "from_pow2_pos20",          1048576.0);
    addFromDouble(out, "from_pow2_neg20",          1.0 / 1048576.0);
    addFromDouble(out, "from_pow2_pos1023",        0x1p+1023);
    addFromDouble(out, "from_pow2_neg1022",        0x1p-1022);
    addFromDouble(out, "from_neg_pow2_pos1023",   -0x1p+1023);
    addFromDouble(out, "from_just_above_one",      1.0000000000000002);
    addFromDouble(out, "from_just_below_one",      0.9999999999999999);
    for (int i = 0; i < N_DOUBLES; i++) {
        char nm[64]; std::snprintf(nm, sizeof nm, "from_grid_%d", i);
        addFromDouble(out, nm, DOUBLES[i]);
    }

    // --- copy cases ---
    addCopy(out, "copy_one",  1.0);
    addCopy(out, "copy_pi",   3.141592653589793);
    addCopy(out, "copy_max",  std::numeric_limits<double>::max());
    addCopy(out, "copy_neg",  -1.4142135623730951);

    // --- cmpQint cases ---
    addCmpQint(out, "cmp_eq",          1.0, 1.0);
    addCmpQint(out, "cmp_pos_neg_eq",  1.0, -1.0);
    addCmpQint(out, "cmp_lt_ex",       1.0, 2.0);
    addCmpQint(out, "cmp_gt_ex",       2.0, 1.0);
    addCmpQint(out, "cmp_lt_mant",     1.0, 1.0000000000000002);
    addCmpQint(out, "cmp_gt_mant",     1.0000000000000002, 1.0);
    addCmpQint(out, "cmp_neg_lt",      -1.0, 2.0);
    addCmpQint(out, "cmp_neg_gt",      -2.0, 1.0);
    addCmpQint(out, "cmp_close",       1.0, 1.0000000000000002);
    addCmpQint(out, "cmp_pi_e",        3.141592653589793, 2.718281828459045);

    // --- cmpQint22 cases ---
    addCmpQint22(out, "cmp22_eq",       1.0, 1.0);
    addCmpQint22(out, "cmp22_lt",       1.0, 2.0);
    addCmpQint22(out, "cmp22_gt",       2.0, 1.0);
    addCmpQint22(out, "cmp22_pi_e",     3.141592653589793, 2.718281828459045);
    addCmpQint22(out, "cmp22_close",    1.0, 1.0000000000000002);

    // --- toLong cases ---
    addToLong(out, "tolong_zero_ex_neg",  0.5);
    addToLong(out, "tolong_one",          1.0);
    addToLong(out, "tolong_two",          2.0);
    addToLong(out, "tolong_neg_three",   -3.0);
    addToLong(out, "tolong_pow2_20",      1048576.0);
    addToLong(out, "tolong_pi_trunc",     3.141592653589793);
    addToLong(out, "tolong_neg_pi",      -3.141592653589793);

    addToLongSynth(out, "tolong_synth_zero",
        0x8000000000000000ULL, 0, 0, 0, -1, 0);
    addToLongSynth(out, "tolong_synth_max63",
        0xffffffffffffffffULL, 0, 0, 0, 63, 0);
    addToLongSynth(out, "tolong_synth_pow2_30",
        0x8000000000000000ULL, 0, 0, 0, 30, 0);
    addToLongSynth(out, "tolong_synth_neg_pow2_10",
        0x8000000000000000ULL, 0, 0, 0, 10, 1);

    // --- toDouble round-trip cases ---
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

    addToDoubleSynth(out, "tod_zero", 0, 0, 0, 0, 0, 0);
    addToDoubleSynth(out, "tod_overflow_just",
        0x8000000000000000ULL, 0, 0, 0, 1024, 0);
    addToDoubleSynth(out, "tod_overflow_far",
        0x8000000000000000ULL, 0, 0, 0, 1100, 0);
    addToDoubleSynth(out, "tod_underflow_neg1075",
        0x8000000000000000ULL, 0, 0, 0, -1075, 0);
    addToDoubleSynth(out, "tod_underflow_far",
        0x8000000000000000ULL, 0, 0, 0, -1200, 0);
    addToDoubleSynth(out, "tod_neg_one_synth",
        0x8000000000000000ULL, 0, 0, 0, 0, 1);
    addToDoubleSynth(out, "tod_subnormal_ex_-1023",
        0x8000000000000000ULL, 0, 0, 0, -1023, 0);
    addToDoubleSynth(out, "tod_subnormal_ex_-1050",
        0x8000000000000000ULL, 0, 0, 0, -1050, 0);
    addToDoubleSynth(out, "tod_subnormal_ex_-1074",
        0x8000000000000000ULL, 0, 0, 0, -1074, 0);

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
    addAdd(out, "add_a_lt_b",                0x1p-10, 0x1p+10);
    addAdd(out, "add_a_gt_b",                0x1p+10, 0x1p-10);
    addAdd(out, "add_sterbenz",              1.5, -1.0);
    addAdd(out, "add_sterbenz_neg",         -1.5, 1.0);
    addAdd(out, "add_far_separation_pos",    0x1p+100, 0x1p-100);
    addAdd(out, "add_far_separation_neg",   -0x1p+100, 0x1p-100);
    addAdd(out, "add_equal_mag_diff_sign",   0x1.5p+10, -0x1.5p+10);
    addAdd(out, "add_equal_mag_same_sign",   0x1.5p+10, 0x1.5p+10);
    addAdd(out, "add_close_diff_sign_loss",  1.0, -0x1.fffffffffffffp-1);
    addAdd(out, "add_pi_half_minus",         1.5707963267948966, -1.5707963267948965);
    addAdd(out, "add_max_normal_pos",        0x1.fffffffffffffp+1023, 0x1.fffffffffffffp+1023);
    addAdd(out, "add_max_normal_neg",       -0x1.fffffffffffffp+1023, -0x1.fffffffffffffp+1023);
    addAdd(out, "add_just_under_max_pos",    0x1p+1023, 0x1p+1023);
    addAdd(out, "add_carry_chain_a",         0x1.fffffffffffffp+0, 0x1.0000000000001p+0);
    addAdd(out, "add_carry_chain_b",         0x1.8p+0, 0x1.8p+0);
    for (int i = 0; i < 8; i++) {
        for (int j = 0; j < 8; j++) {
            char nm[64]; std::snprintf(nm, sizeof nm, "add_grid_%d_%d", i, j);
            addAdd(out, nm, DOUBLES[i], DOUBLES[j]);
        }
    }

    // --- add22 cases ---
    addAdd22(out, "add22_one_one",             1.0, 1.0);
    addAdd22(out, "add22_one_neg_one",         1.0, -1.0);
    addAdd22(out, "add22_pi_e",                3.141592653589793, 2.718281828459045);
    addAdd22(out, "add22_pi_neg_e",            3.141592653589793, -2.718281828459045);
    addAdd22(out, "add22_far_pos",             0x1p+100, 0x1p-100);
    addAdd22(out, "add22_sterbenz",            1.5, -1.0);
    addAdd22(out, "add22_equal_mag_diff_sign", 0x1.5p+10, -0x1.5p+10);
    addAdd22(out, "add22_equal_mag_same_sign", 0x1.5p+10, 0x1.5p+10);
    addAdd22(out, "add22_one_tiny",            1.0, 0x1p-50);
    addAdd22(out, "add22_max_pos",             0x1p+1023, 0x1p+1023);

    // --- mul cases ---
    addMul(out, "mul_one_one",          1.0, 1.0);
    addMul(out, "mul_one_neg_one",      1.0, -1.0);
    addMul(out, "mul_neg_one_neg_one", -1.0, -1.0);
    addMul(out, "mul_pi_e",             3.141592653589793, 2.718281828459045);
    addMul(out, "mul_pi_inv_two_pi",    3.141592653589793, 0.15915494309189535);
    addMul(out, "mul_two_half",         2.0, 0.5);
    addMul(out, "mul_pow2_pos_neg",     0x1p+50, 0x1p-50);
    addMul(out, "mul_overflow_borderline", 0x1p+512, 0x1p+512);
    addMul(out, "mul_underflow_borderline", 0x1p-512, 0x1p-512);
    addMul(out, "mul_small_huge",       0x1p-100, 0x1p+100);
    addMul(out, "mul_neg_small_huge",  -0x1p-100, 0x1p+100);
    addMul(out, "mul_sqrt2_sqrt2",      1.4142135623730951, 1.4142135623730951);
    for (int i = 0; i < 8; i++) {
        for (int j = 0; j < 8; j++) {
            char nm[64]; std::snprintf(nm, sizeof nm, "mul_grid_%d_%d", i, j);
            addMul(out, nm, DOUBLES[i], DOUBLES[j]);
        }
    }

    // --- mul11 cases ---
    addMul11(out, "mul11_one_one",          1.0, 1.0);
    addMul11(out, "mul11_pi_e",             3.141592653589793, 2.718281828459045);
    addMul11(out, "mul11_pow2",             0x1p+10, 0x1p+10);
    addMul11(out, "mul11_neg_pos",         -2.5, 4.0);
    addMul11(out, "mul11_sqrt2_sqrt2",      1.4142135623730951, 1.4142135623730951);
    addMul11(out, "mul11_overflow_borderline", 0x1p+512, 0x1p+512);
    addMul11(out, "mul11_underflow_borderline", 0x1p-512, 0x1p-512);

    // --- mul21 cases ---
    addMul21(out, "mul21_one_one",          1.0, 1.0);
    addMul21(out, "mul21_pi_e",             3.141592653589793, 2.718281828459045);
    addMul21(out, "mul21_pow2",             0x1p+10, 0x1p+10);
    addMul21(out, "mul21_neg_pos",         -2.5, 4.0);
    addMul21(out, "mul21_sqrt2_sqrt2",      1.4142135623730951, 1.4142135623730951);

    // --- mul22 cases ---
    addMul22(out, "mul22_one_one",          1.0, 1.0);
    addMul22(out, "mul22_pi_e",             3.141592653589793, 2.718281828459045);
    addMul22(out, "mul22_pow2",             0x1p+10, 0x1p+10);
    addMul22(out, "mul22_neg_pos",         -2.5, 4.0);
    addMul22(out, "mul22_sqrt2_sqrt2",      1.4142135623730951, 1.4142135623730951);
    addMul22(out, "mul22_overflow_borderline", 0x1p+512, 0x1p+512);

    // --- mul31 cases ---
    addMul31(out, "mul31_one_one",          1.0, 1.0);
    addMul31(out, "mul31_pi_e",             3.141592653589793, 2.718281828459045);
    addMul31(out, "mul31_pow2",             0x1p+10, 0x1p+10);
    addMul31(out, "mul31_neg_pos",         -2.5, 4.0);
    addMul31(out, "mul31_sqrt2_sqrt2",      1.4142135623730951, 1.4142135623730951);

    // --- mul33 cases ---
    addMul33(out, "mul33_one_one",          1.0, 1.0);
    addMul33(out, "mul33_pi_e",             3.141592653589793, 2.718281828459045);
    addMul33(out, "mul33_pow2",             0x1p+10, 0x1p+10);
    addMul33(out, "mul33_sqrt2_sqrt2",      1.4142135623730951, 1.4142135623730951);
    addMul33(out, "mul33_overflow_borderline", 0x1p+512, 0x1p+512);

    // --- mul41 cases ---
    addMul41(out, "mul41_one_one",          1.0, 1.0);
    addMul41(out, "mul41_pi_e",             3.141592653589793, 2.718281828459045);
    addMul41(out, "mul41_pow2",             0x1p+10, 0x1p+10);
    addMul41(out, "mul41_neg_pos",         -2.5, 4.0);
    addMul41(out, "mul41_sqrt2_sqrt2",      1.4142135623730951, 1.4142135623730951);

    // --- mul2 cases ---
    addMul2(out, "mul2_one_one",        1, 1.0);
    addMul2(out, "mul2_neg_one_one",   -1, 1.0);
    addMul2(out, "mul2_zero_pi",        0, 3.141592653589793);
    addMul2(out, "mul2_two_pi",         2, 3.141592653589793);
    addMul2(out, "mul2_three_e",        3, 2.718281828459045);
    addMul2(out, "mul2_neg_three_e",   -3, 2.718281828459045);
    addMul2(out, "mul2_big",            1000000, 1.4142135623730951);
    addMul2(out, "mul2_neg_big",       -1000000, 1.4142135623730951);
    addMul2(out, "mul2_max_pos_int",    0x7fffffffffffffffLL, 1.0);
    addMul2(out, "mul2_min_neg_int",    -0x7fffffffffffffffLL, 1.0);

    out.write();
    return 0;
}
