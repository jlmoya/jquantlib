// migration-harness/cpp/probes/math/optimization/minpack_qrsolv_probe.cpp
// Reference values for MINPACK::qrsolv (damped least-squares solve on a
// QR-factored upper-triangular system). Two cases: a plain 3x3 triangular
// system, and a 4x4 with non-trivial diag damping.

#include <ql/version.hpp>
#include <ql/math/optimization/lmdif.hpp>
#include "../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

json vec(const double* p, int n) {
    json j = json::array();
    for (int i = 0; i < n; ++i) j.push_back(p[i]);
    return j;
}

} // namespace

int main() {
    ReferenceWriter out("math/optimization/minpack_qrsolv", QL_VERSION,
                        "minpack_qrsolv_probe");

    // Case A: 3x3 triangular r, identity-like permutation, zero diag
    // (the "already-QR" path). qrsolv should solve r*z = qtb exactly.
    {
        const int n = 3, ldr = 3;
        // r column-major 3x3, upper-triangular:
        //   [2  1  3]
        //   [0  4  5]
        //   [0  0  6]
        // column-major: col0={2,0,0}, col1={1,4,0}, col2={3,5,6}
        double r[]     = { 2.0, 0.0, 0.0,
                           1.0, 4.0, 0.0,
                           3.0, 5.0, 6.0 };
        int ipvt[]     = { 0, 1, 2 };
        double diag[]  = { 0.0, 0.0, 0.0 };
        double qtb[]   = { 10.0, 20.0, 30.0 };
        double x[3]    = { 0.0, 0.0, 0.0 };
        double sdiag[3]= { 0.0, 0.0, 0.0 };
        double wa[3]   = { 0.0, 0.0, 0.0 };
        MINPACK::qrsolv(n, r, ldr, ipvt, diag, qtb, x, sdiag, wa);
        out.addCase("qrsolv_3x3_zeroDiag",
            json{{"n", n}, {"ldr", ldr},
                 {"r_in", {2.0,0.0,0.0, 1.0,4.0,0.0, 3.0,5.0,6.0}},
                 {"ipvt", {0, 1, 2}},
                 {"diag", {0.0, 0.0, 0.0}},
                 {"qtb", {10.0, 20.0, 30.0}}},
            json{{"r_out", vec(r, n*n)},
                 {"x", vec(x, n)},
                 {"sdiag", vec(sdiag, n)}});
    }

    // Case B: 4x4 with non-trivial diag damping. r is upper-triangular,
    // with a non-identity permutation and positive damping values.
    {
        const int n = 4, ldr = 4;
        // r column-major 4x4:
        //   [5 2 1 3]
        //   [0 4 2 1]
        //   [0 0 3 5]
        //   [0 0 0 2]
        double r[]     = { 5.0, 0.0, 0.0, 0.0,
                           2.0, 4.0, 0.0, 0.0,
                           1.0, 2.0, 3.0, 0.0,
                           3.0, 1.0, 5.0, 2.0 };
        int ipvt[]     = { 2, 0, 3, 1 };
        double diag[]  = { 0.5, 1.0, 0.25, 0.75 };
        double qtb[]   = { 7.0, -3.0, 5.0, 1.5 };
        double x[4]    = { 0.0, 0.0, 0.0, 0.0 };
        double sdiag[4]= { 0.0, 0.0, 0.0, 0.0 };
        double wa[4]   = { 0.0, 0.0, 0.0, 0.0 };
        MINPACK::qrsolv(n, r, ldr, ipvt, diag, qtb, x, sdiag, wa);
        out.addCase("qrsolv_4x4_damped",
            json{{"n", n}, {"ldr", ldr},
                 {"r_in", {5.0,0.0,0.0,0.0, 2.0,4.0,0.0,0.0,
                           1.0,2.0,3.0,0.0, 3.0,1.0,5.0,2.0}},
                 {"ipvt", {2, 0, 3, 1}},
                 {"diag", {0.5, 1.0, 0.25, 0.75}},
                 {"qtb", {7.0, -3.0, 5.0, 1.5}}},
            json{{"r_out", vec(r, n*n)},
                 {"x", vec(x, n)},
                 {"sdiag", vec(sdiag, n)}});
    }

    out.write();
    return 0;
}
