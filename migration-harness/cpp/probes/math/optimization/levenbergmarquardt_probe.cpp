// migration-harness/cpp/probes/math/optimization/levenbergmarquardt_probe.cpp
// Reference values for MINPACK::lmdif — the top-level Levenberg-Marquardt
// driver. Four cases (phase2a-plan §Task 2.6):
//   lm_linear_fit       (tight on params)
//   lm_quadratic_fit    (tight on params)
//   lm_rosenbrock       (tight on info/nfev, loose on params)
//   lm_maxfev_earlystop (exact info=5, tight on nfev, tight on params)
//
// Each case calls MINPACK::lmdif with an empty analytic-Jacobian callback,
// forcing the use of fdjac2, which matches the Java LevenbergMarquardt
// facade. Captures: x, fvec, info, nfev, fjac, ipvt, qtf, diag_out.

#include <ql/version.hpp>
#include <ql/math/optimization/lmdif.hpp>
#include "../../common.hpp"

#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

json vec(const double* p, int n) {
    json j = json::array();
    for (int i = 0; i < n; ++i) j.push_back(p[i]);
    return j;
}

json ivec(const int* p, int n) {
    json j = json::array();
    for (int i = 0; i < n; ++i) j.push_back(p[i]);
    return j;
}

struct LmdifCase {
    std::string name;
    int m, n;
    std::vector<double> x0;
    double ftol, xtol, gtol;
    int maxfev;
    double epsfcn;
    std::vector<double> diag;
    int mode;
    double factor;
    int nprint;
    MINPACK::LmdifCostFunction fcn;
};

void runAndEmit(ReferenceWriter& out, const LmdifCase& tc) {
    const int m = tc.m, n = tc.n;
    const int ldfjac = m;
    std::vector<double> x = tc.x0;
    std::vector<double> fvec(m, 0.0);
    std::vector<double> diag = tc.diag;
    std::vector<double> fjac(m * n, 0.0);
    std::vector<int>    ipvt(n, 0);
    std::vector<double> qtf(n, 0.0);
    std::vector<double> wa1(n, 0.0), wa2(n, 0.0), wa3(n, 0.0), wa4(m, 0.0);
    int info = 0, nfev = 0;

    MINPACK::LmdifCostFunction emptyJac;  // empty ⇒ lmdif uses fdjac2

    MINPACK::lmdif(m, n, x.data(), fvec.data(), tc.ftol, tc.xtol, tc.gtol,
                   tc.maxfev, tc.epsfcn, diag.data(), tc.mode, tc.factor,
                   tc.nprint, &info, &nfev, fjac.data(), ldfjac, ipvt.data(),
                   qtf.data(), wa1.data(), wa2.data(), wa3.data(), wa4.data(),
                   tc.fcn, emptyJac);

    json inputs = {
        {"m", m}, {"n", n},
        {"x0", tc.x0},
        {"ftol", tc.ftol}, {"xtol", tc.xtol}, {"gtol", tc.gtol},
        {"maxfev", tc.maxfev}, {"epsfcn", tc.epsfcn},
        {"diag_in", tc.diag},
        {"mode", tc.mode}, {"factor", tc.factor}, {"nprint", tc.nprint}
    };
    json expected = {
        {"x",        vec(x.data(), n)},
        {"fvec",     vec(fvec.data(), m)},
        {"info",     info},
        {"nfev",     nfev},
        {"fjac",     vec(fjac.data(), m * n)},
        {"ipvt",     ivec(ipvt.data(), n)},
        {"qtf",      vec(qtf.data(), n)},
        {"diag_out", vec(diag.data(), n)}
    };
    out.addCase(tc.name, inputs, expected);
}

} // namespace

int main() {
    ReferenceWriter out("math/optimization/levenbergmarquardt", QL_VERSION,
                        "levenbergmarquardt_probe");

    // --- Case 1: linear fit with noise (inexact residuals, so fvec never
    //             collapses to exactly zero and the convergence path stays
    //             on xtol rather than flipping to the gnorm branch — see
    //             phase2a-progress.md on JVM-vs-C++ FMA divergences). -----
    {
        std::vector<double> xs = { 0.0, 1.0, 2.0, 3.0, 4.0 };
        std::vector<double> ys = { -1.1, 0.9, 3.2, 4.8, 7.1 };
        LmdifCase tc;
        tc.name = "lm_linear_fit";
        tc.m = 5; tc.n = 2;
        tc.x0 = { 0.0, 0.0 };
        tc.ftol = 1.0e-10; tc.xtol = 1.0e-10; tc.gtol = 0.0;
        tc.maxfev = 200; tc.epsfcn = 0.0;
        tc.diag = std::vector<double>(tc.n, 1.0);
        tc.mode = 1; tc.factor = 100.0; tc.nprint = 0;
        tc.fcn = [xs, ys](int mm, int /*nn*/, Real* xx, Real* fvec, int* /*iflag*/) {
            for (int i = 0; i < mm; ++i) {
                fvec[i] = ys[i] - (xx[0] * xs[i] + xx[1]);
            }
        };
        runAndEmit(out, tc);
    }

    // --- Case 2: quadratic fit y = x^2 - 2*x + 3 --------------------------
    {
        std::vector<double> xs = { -2.0, -1.0, 0.0, 1.0, 2.0 };
        std::vector<double> ys = { 11.0, 6.0, 3.0, 2.0, 3.0 };
        LmdifCase tc;
        tc.name = "lm_quadratic_fit";
        tc.m = 5; tc.n = 3;
        tc.x0 = { 0.0, 0.0, 0.0 };
        tc.ftol = 1.0e-10; tc.xtol = 1.0e-10; tc.gtol = 0.0;
        tc.maxfev = 200; tc.epsfcn = 0.0;
        tc.diag = std::vector<double>(tc.n, 1.0);
        tc.mode = 1; tc.factor = 100.0; tc.nprint = 0;
        tc.fcn = [xs, ys](int mm, int /*nn*/, Real* xx, Real* fvec, int* /*iflag*/) {
            for (int i = 0; i < mm; ++i) {
                const double xi = xs[i];
                fvec[i] = ys[i] - (xx[0]*xi*xi + xx[1]*xi + xx[2]);
            }
        };
        runAndEmit(out, tc);
    }

    // --- Case 3: Rosenbrock-style ill-conditioned (minimum at (1,1)) ------
    {
        LmdifCase tc;
        tc.name = "lm_rosenbrock";
        tc.m = 2; tc.n = 2;
        tc.x0 = { -1.2, 1.0 };
        tc.ftol = 1.0e-10; tc.xtol = 1.0e-10; tc.gtol = 0.0;
        tc.maxfev = 500; tc.epsfcn = 0.0;
        tc.diag = std::vector<double>(tc.n, 1.0);
        tc.mode = 1; tc.factor = 100.0; tc.nprint = 0;
        tc.fcn = [](int /*mm*/, int /*nn*/, Real* xx, Real* fvec, int* /*iflag*/) {
            fvec[0] = 10.0 * (xx[1] - xx[0]*xx[0]);
            fvec[1] = 1.0 - xx[0];
        };
        runAndEmit(out, tc);
    }

    // --- Case 4: forced maxfev early-stop (expect info = 5). Same noisy
    //             dataset as Case 1 for consistency.
    {
        std::vector<double> xs = { 0.0, 1.0, 2.0, 3.0, 4.0 };
        std::vector<double> ys = { -1.1, 0.9, 3.2, 4.8, 7.1 };
        LmdifCase tc;
        tc.name = "lm_maxfev_earlystop";
        tc.m = 5; tc.n = 2;
        tc.x0 = { 0.0, 0.0 };
        tc.ftol = 1.0e-10; tc.xtol = 1.0e-10; tc.gtol = 0.0;
        tc.maxfev = 3; tc.epsfcn = 0.0;
        tc.diag = std::vector<double>(tc.n, 1.0);
        tc.mode = 1; tc.factor = 100.0; tc.nprint = 0;
        tc.fcn = [xs, ys](int mm, int /*nn*/, Real* xx, Real* fvec, int* /*iflag*/) {
            for (int i = 0; i < mm; ++i) {
                fvec[i] = ys[i] - (xx[0] * xs[i] + xx[1]);
            }
        };
        runAndEmit(out, tc);
    }

    out.write();
    return 0;
}
