// migration-harness/cpp/probes/math/optimization/minpack_qrfac_probe.cpp
// Reference values for MINPACK::qrfac (QR factorisation with column pivoting).
// Bit-exact: qrfac is deterministic linear algebra — any Java-side drift in
// the last ulp means something structural is wrong. See phase2a-design §3.2.

#include <ql/version.hpp>
#include <ql/math/optimization/lmdif.hpp>
#include "../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// Pack a raw double* as a JSON array.
json vec(const double* p, int n) {
    json j = json::array();
    for (int i = 0; i < n; ++i) j.push_back(p[i]);
    return j;
}

// Pack a raw int* as a JSON array.
json ivec(const int* p, int n) {
    json j = json::array();
    for (int i = 0; i < n; ++i) j.push_back(p[i]);
    return j;
}

} // namespace

int main() {
    ReferenceWriter out("math/optimization/minpack_qrfac", QL_VERSION,
                        "minpack_qrfac_probe");

    // Case A: 3x3 full-rank matrix, column-major. Each column is stored
    // contiguously: col0 = {1,4,7}, col1 = {2,5,8.1}, col2 = {3,6.2,9.3}.
    {
        const int m = 3, n = 3;
        double a[] = { 1.0, 4.0, 7.0,
                       2.0, 5.0, 8.1,
                       3.0, 6.2, 9.3 };
        int ipvt[3]   = { 0, 0, 0 };
        double rdiag[3]  = { 0.0, 0.0, 0.0 };
        double acnorm[3] = { 0.0, 0.0, 0.0 };
        double wa[3]     = { 0.0, 0.0, 0.0 };
        MINPACK::qrfac(m, n, a, m, 1, ipvt, n, rdiag, acnorm, wa);
        out.addCase("qrfac_3x3_fullrank",
            json{
                {"m", m}, {"n", n},
                {"a_in", {1.0, 4.0, 7.0, 2.0, 5.0, 8.1, 3.0, 6.2, 9.3}},
                {"lda", m}, {"pivot", 1}, {"lipvt", n}
            },
            json{
                {"a_out", vec(a, m * n)},
                {"ipvt", ivec(ipvt, n)},
                {"rdiag", vec(rdiag, n)},
                {"acnorm", vec(acnorm, n)}
            });
    }

    // Case B: 4x2 tall matrix. col0 = {1,2,3,4}, col1 = {5,6,7,8.5}.
    {
        const int m = 4, n = 2;
        double a[] = { 1.0, 2.0, 3.0, 4.0,
                       5.0, 6.0, 7.0, 8.5 };
        int ipvt[2]   = { 0, 0 };
        double rdiag[2]  = { 0.0, 0.0 };
        double acnorm[2] = { 0.0, 0.0 };
        double wa[2]     = { 0.0, 0.0 };
        MINPACK::qrfac(m, n, a, m, 1, ipvt, n, rdiag, acnorm, wa);
        out.addCase("qrfac_4x2_tall",
            json{
                {"m", m}, {"n", n},
                {"a_in", {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.5}},
                {"lda", m}, {"pivot", 1}, {"lipvt", n}
            },
            json{
                {"a_out", vec(a, m * n)},
                {"ipvt", ivec(ipvt, n)},
                {"rdiag", vec(rdiag, n)},
                {"acnorm", vec(acnorm, n)}
            });
    }

    out.write();
    return 0;
}
