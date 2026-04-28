// migration-harness/cpp/probes/transcendental/exp_probe.cpp
// Phase 2i WI-1.1 — emit bit-exact CORE-MATH cr_exp(x) for a curated input set
// covering IEEE-754 special cases, argument-reduction breakpoints, and
// dense/sparse coverage of the representable domain, plus the full 51-entry
// hard-to-round DB.
//
// Output: migration-harness/references/math/transcendental/exp.json
// Schema: each case has "x" (double) and "y_bits" (hex string of cr_exp(x) raw bits).
//
// Oracle: CORE-MATH cr_exp (correctly-rounded exp for binary64), included
// directly as a single-TU compilation. Phase 2i design names CORE-MATH as
// the source of truth; Apple libm std::exp on macOS arm64 is NOT correctly
// rounded for ~8 of the DB hard cases (verified via 300-bit mpmath), so the
// platform libm cannot be used as the oracle.

#include <ql/version.hpp>
#include "../common.hpp"

#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <limits>

// CORE-MATH cr_exp source (MIT-licensed, Copyright (c) 2022-2023 Alexei Sibidanov).
// Single-TU include keeps the probe self-contained and avoids CMake glob churn.
extern "C" {
#include "coremath/exp.c"
}

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
    // Use CORE-MATH cr_exp (correctly-rounded) as the oracle. NaN inputs
    // are passed through unchanged (cr_exp's NaN handling matches IEEE-754).
    const double y = cr_exp(x);
    out.addCase(name,
        json{{"x", encodeX(x)}},
        json{{"y_bits", hexBits(y)}});
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

    // Hard-to-round cases from CORE-MATH's 51-entry database (ExpKernel.DB[]).
    // Each input is one of the 51 x values stored in the DB; feeding them to
    // cr_exp produces the correctly-rounded reference, which the Java
    // ExpKernel matches bit-for-bit. Bit patterns transcribed from the DB[]
    // initialiser in ExpKernel.java (which in turn comes from CORE-MATH
    // src/binary64/exp/exp.c, MIT-licensed, Copyright (c) 2022-2023 Alexei
    // Sibidanov).
    //
    // All 51/51 entries are present. With the oracle now CORE-MATH cr_exp
    // (not Apple libm std::exp), the 8 entries that previously diverged
    // 1 ULP from libm — db_01, db_02, db_03, db_11, db_12, db_21, db_32,
    // db_41 — are bit-exact against the Java port (A3 finding: the Java
    // port is correctly-rounded; libm was the wrong reference).
    {
        const auto fromBits = [](std::uint64_t b) -> double {
            double d; std::memcpy(&d, &b, sizeof d); return d;
        };
        // db_00: +0x1.fffffffffffffp-53
        addExpCase(out, "db_00", fromBits(0x3cafffffffffffffULL));
        // db_01: +0x1.ba07d73250de7p-14
        addExpCase(out, "db_01", fromBits(0x3f1ba07d73250de7ULL));
        // db_02: +0x1.6a4d1af9cc989p-8
        addExpCase(out, "db_02", fromBits(0x3f76a4d1af9cc989ULL));
        // db_03: +0x1.5a75293a5dcdap-6
        addExpCase(out, "db_03", fromBits(0x3f95a75293a5dcdaULL));
        // db_04: +0x1.42ea46949b3c7p-5
        addExpCase(out, "db_04", fromBits(0x3fa42ea46949b3c7ULL));
        // db_05: +0x1.7c8bb0cf5d160p-5
        addExpCase(out, "db_05", fromBits(0x3fa7c8bb0cf5d160ULL));
        // db_06: +0x1.0948d39a41695p-3
        addExpCase(out, "db_06", fromBits(0x3fc0948d39a41695ULL));
        // db_07: +0x1.a065fefae814fp-3
        addExpCase(out, "db_07", fromBits(0x3fca065fefae814fULL));
        // db_08: +0x1.f6e4c3ced7c72p-3
        addExpCase(out, "db_08", fromBits(0x3fcf6e4c3ced7c72ULL));
        // db_09: +0x1.1a0408712e00ap-2
        addExpCase(out, "db_09", fromBits(0x3fd1a0408712e00aULL));
        // db_10: +0x1.bcab27d05abdep-2
        addExpCase(out, "db_10", fromBits(0x3fdbcab27d05abdeULL));
        // db_11: +0x1.005ae04256babp-1
        addExpCase(out, "db_11", fromBits(0x3fe005ae04256babULL));
        // db_12: +0x1.273c188aa7b14p+2
        addExpCase(out, "db_12", fromBits(0x401273c188aa7b14ULL));
        // db_13: +0x1.83d4bcdebb3f4p+2
        addExpCase(out, "db_13", fromBits(0x40183d4bcdebb3f4ULL));
        // db_14: +0x1.08f51434652c3p+4
        addExpCase(out, "db_14", fromBits(0x40308f51434652c3ULL));
        // db_15: +0x1.1d5c2daebe367p+4
        addExpCase(out, "db_15", fromBits(0x4031d5c2daebe367ULL));
        // db_16: +0x1.c44ce0d716a1ap+4
        addExpCase(out, "db_16", fromBits(0x403c44ce0d716a1aULL));
        // db_17: +0x1.e07e71bfcf06fp+5
        addExpCase(out, "db_17", fromBits(0x404e07e71bfcf06fULL));
        // db_18: +0x1.f7216c4b435c9p+5
        addExpCase(out, "db_18", fromBits(0x404f7216c4b435c9ULL));
        // db_19: +0x1.54cd1fea7663ap+7
        addExpCase(out, "db_19", fromBits(0x40654cd1fea7663aULL));
        // db_20: +0x1.d6479eba7c971p+8
        addExpCase(out, "db_20", fromBits(0x407d6479eba7c971ULL));
        // db_21: -0x1.664716b68a409p-14
        addExpCase(out, "db_21", fromBits(0xbf1664716b68a409ULL));
        // db_22: -0x1.a2fefefd580dfp-13
        addExpCase(out, "db_22", fromBits(0xbf2a2fefefd580dfULL));
        // db_23: -0x1.ce3f638d0c742p-12
        addExpCase(out, "db_23", fromBits(0xbf3ce3f638d0c742ULL));
        // db_24: -0x1.ceff32831e2c2p-12
        addExpCase(out, "db_24", fromBits(0xbf3ceff32831e2c2ULL));
        // db_25: -0x1.33accae78b371p-11
        addExpCase(out, "db_25", fromBits(0xbf433accae78b371ULL));
        // db_26: -0x1.d792b60084f92p-11
        addExpCase(out, "db_26", fromBits(0xbf4d792b60084f92ULL));
        // db_27: -0x1.7fb235d76cce7p-8
        addExpCase(out, "db_27", fromBits(0xbf77fb235d76cce7ULL));
        // db_28: -0x1.1ff9b8e8b38bep-7
        addExpCase(out, "db_28", fromBits(0xbf81ff9b8e8b38beULL));
        // db_29: -0x1.54511e930898cp-7
        addExpCase(out, "db_29", fromBits(0xbf854511e930898cULL));
        // db_30: -0x1.5c5ed0ec83666p-6
        addExpCase(out, "db_30", fromBits(0xbf95c5ed0ec83666ULL));
        // db_31: -0x1.8c56ff5326197p-6
        addExpCase(out, "db_31", fromBits(0xbf98c56ff5326197ULL));
        // db_32: -0x1.a4187f2ca71f9p-6
        addExpCase(out, "db_32", fromBits(0xbf9a4187f2ca71f9ULL));
        // db_33: -0x1.a8f783d749a8fp-4
        addExpCase(out, "db_33", fromBits(0xbfba8f783d749a8fULL));
        // db_34: -0x1.bd44fdaed819fp-4
        addExpCase(out, "db_34", fromBits(0xbfbbd44fdaed819fULL));
        // db_35: -0x1.daf693d64fadap-4
        addExpCase(out, "db_35", fromBits(0xbfbdaf693d64fadaULL));
        // db_36: -0x1.290ea09e36479p-3
        addExpCase(out, "db_36", fromBits(0xbfc290ea09e36479ULL));
        // db_37: -0x1.8aeb636f3ce35p-3
        addExpCase(out, "db_37", fromBits(0xbfc8aeb636f3ce35ULL));
        // db_38: -0x1.d3f3799439415p-3
        addExpCase(out, "db_38", fromBits(0xbfcd3f3799439415ULL));
        // db_39: -0x1.ea16274b0109bp-3
        addExpCase(out, "db_39", fromBits(0xbfcea16274b0109bULL));
        // db_40: -0x1.22e24fa3d5cf9p-1
        addExpCase(out, "db_40", fromBits(0xbfe22e24fa3d5cf9ULL));
        // db_41: -0x1.85068c07fbbf6p-1
        addExpCase(out, "db_41", fromBits(0xbfe85068c07fbbf6ULL));
        // db_42: -0x1.bdc7955d1482cp-1
        addExpCase(out, "db_42", fromBits(0xbfebdc7955d1482cULL));
        // db_43: -0x1.2a9cad9998262p+0
        addExpCase(out, "db_43", fromBits(0xbff2a9cad9998262ULL));
        // db_44: -0x1.cc37ef7de7501p+0
        addExpCase(out, "db_44", fromBits(0xbffcc37ef7de7501ULL));
        // db_45: -0x1.02393d5976769p+1
        addExpCase(out, "db_45", fromBits(0xc0002393d5976769ULL));
        // db_46: -0x1.65061daf79a78p+1
        addExpCase(out, "db_46", fromBits(0xc0065061daf79a78ULL));
        // db_47: -0x1.e8bdbfcd9144ep+3
        addExpCase(out, "db_47", fromBits(0xc02e8bdbfcd9144eULL));
        // db_48: -0x1.8f80e06f3a04cp+4
        addExpCase(out, "db_48", fromBits(0xc038f80e06f3a04cULL));
        // db_49: -0x1.59f038076039cp+6
        addExpCase(out, "db_49", fromBits(0xc0559f038076039cULL));
        // db_50: -0x1.981587ad4542fp+7
        addExpCase(out, "db_50", fromBits(0xc06981587ad4542fULL));
    }

    out.write();
    return 0;
}
