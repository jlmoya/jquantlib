// migration-harness/cpp/probes/math/interpolations/xabr_restart_loop_probe.cpp
// Probe for Phase 2d WI-3: XABRInterpolationImpl Halton multi-restart loop
// cross-validation. Captures (final params, error, maxError, end criteria)
// for two SABR-backed cases:
//
//   1) "single_iter_deterministic": maxGuesses=1, paramIsFixed all false,
//      well-posed initial guess. Exercises the first-iteration path that
//      uses XABRCoeffHolder defaultValues and does not consult Halton.
//   2) "multi_iter_convergence": uses the canonical 31-strike SABR test
//      smile (vols generated from alpha=0.3, beta=0.6, nu=0.02, rho=0.01;
//      forward=0.039, expiry=1.0). With errorAccept=1e-10 and
//      maxGuesses=50 the Halton restart loop must run enough iterations
//      to recover the true (alpha, beta, nu, rho). This is the mechanism
//      that lets the un-skipped SABR tests pass at calibrationTolerance
//      5e-8 — a single LM/Simplex run from the default-guess gets stuck
//      in a local minimum near alpha~0.299, error~5.5e-7.

#include <ql/version.hpp>
#include <ql/math/interpolations/sabrinterpolation.hpp>
#include <ql/math/optimization/levenbergmarquardt.hpp>
#include <ql/math/optimization/endcriteria.hpp>
#include "../../common.hpp"

#include <cmath>
#include <vector>
#include <sstream>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

std::string endCriteriaToString(EndCriteria::Type t) {
    std::ostringstream os;
    os << t;
    return os.str();
}

json runCase(const std::string& name,
             const std::vector<Real>& strikes,
             const std::vector<Real>& vols,
             Real expiry, Real forward,
             Real alpha, Real beta, Real nu, Real rho,
             bool aFixed, bool bFixed, bool nFixed, bool rFixed,
             Real errorAccept, bool useMaxError, Size maxGuesses) {
    ext::shared_ptr<EndCriteria> ec =
        ext::make_shared<EndCriteria>(60000, 100, 1e-8, 1e-8, 1e-8);
    ext::shared_ptr<OptimizationMethod> om =
        ext::make_shared<LevenbergMarquardt>(1e-8, 1e-8, 1e-8);

    SABRInterpolation sabr(strikes.begin(), strikes.end(), vols.begin(),
                           expiry, forward,
                           alpha, beta, nu, rho,
                           aFixed, bFixed, nFixed, rFixed,
                           false, // vegaWeighted
                           ec, om,
                           errorAccept, useMaxError, maxGuesses);
    sabr.update();

    json out = {
        {"alpha", sabr.alpha()},
        {"beta",  sabr.beta()},
        {"nu",    sabr.nu()},
        {"rho",   sabr.rho()},
        {"error", sabr.rmsError()},
        {"maxError", sabr.maxError()},
        {"endCriteria", endCriteriaToString(sabr.endCriteria())}
    };
    return out;
}

} // namespace

int main() {
    ReferenceWriter out("math/interpolations/xabr_restart_loop", QL_VERSION,
                        "xabr_restart_loop_probe");

    // Common smile fixture: 5 strikes, lognormal SABR vols generated from a
    // well-posed parameter set. Forward = 0.04, expiry = 2.0.
    const std::vector<Real> strikes = {0.03, 0.035, 0.04, 0.045, 0.05};
    const std::vector<Real> vols    = {0.4, 0.38, 0.36, 0.34, 0.33};
    const Real expiry  = 2.0;
    const Real forward = 0.04;

    // Case 1: single iteration, deterministic (no Halton noise).
    // Initial guess close to the "true" SABR fit but loose enough that the
    // optimizer still has work to do.
    {
        json inputs = {
            {"strikes", strikes},
            {"volatilities", vols},
            {"expiry", expiry},
            {"forward", forward},
            {"alpha", 0.4}, {"beta", 0.5},
            {"nu", 0.5}, {"rho", 0.0},
            {"alphaIsFixed", false}, {"betaIsFixed", false},
            {"nuIsFixed", false}, {"rhoIsFixed", false},
            {"errorAccept", 0.0020},
            {"useMaxError", false},
            {"maxGuesses", 1}
        };
        json expected = runCase("single_iter_deterministic", strikes, vols,
                                expiry, forward,
                                0.4, 0.5, 0.5, 0.0,
                                false, false, false, false,
                                0.0020, false, 1);
        out.addCase("single_iter_deterministic", inputs, expected);
    }

    // Case 2: multi-iteration convergence on the canonical 31-strike SABR
    // smile. Halton restarts (errorAccept=1e-10, maxGuesses=50) must
    // recover the true params from the C++ default guesses
    // (alpha=sqrt(0.2), beta=0.5, nu=sqrt(0.4), rho=0.0).
    {
        std::vector<Real> bigStrikes(31);
        for (int i = 0; i < 31; ++i) bigStrikes[i] = 0.03 + 0.002 * i;
        const std::vector<Real> bigVols = {
            1.16725837321531, 1.15226075991385, 1.13829711098834,
            1.12524190877505, 1.11299079244474, 1.10145609357162,
            1.09056348513411, 1.08024942745106, 1.07045919457758,
            1.06114533019077, 1.05226642581503, 1.04378614411707,
            1.03567243073732, 1.0278968727451,  1.02043417226345,
            1.01326171139321, 1.00635919013311, 0.999708323124949,
            0.993292584155381, 0.987096989695393, 0.98110791455717,
            0.975312934134512, 0.969700688771689, 0.964260766651027,
            0.958983602256592, 0.953860388001395, 0.948882997029509,
            0.944043915545469, 0.939336183299237, 0.934753341079515,
            0.930289384251337
        };
        const Real bigExpiry  = 1.0;
        const Real bigForward = 0.039;
        const Real alphaGuess = std::sqrt(0.2);
        const Real betaGuess  = 0.5;
        const Real nuGuess    = std::sqrt(0.4);
        const Real rhoGuess   = 0.0;

        json inputs = {
            {"strikes", bigStrikes},
            {"volatilities", bigVols},
            {"expiry", bigExpiry},
            {"forward", bigForward},
            {"alpha", alphaGuess}, {"beta", betaGuess},
            {"nu", nuGuess}, {"rho", rhoGuess},
            {"alphaIsFixed", false}, {"betaIsFixed", false},
            {"nuIsFixed", false}, {"rhoIsFixed", false},
            {"errorAccept", 1e-10},
            {"useMaxError", false},
            {"maxGuesses", 50}
        };
        json expected = runCase("multi_iter_convergence", bigStrikes, bigVols,
                                bigExpiry, bigForward,
                                alphaGuess, betaGuess, nuGuess, rhoGuess,
                                false, false, false, false,
                                1e-10, false, 50);
        out.addCase("multi_iter_convergence", inputs, expected);
    }

    out.write();
    return 0;
}
