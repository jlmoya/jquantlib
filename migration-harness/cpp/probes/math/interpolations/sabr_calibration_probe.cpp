// migration-harness/cpp/probes/math/interpolations/sabr_calibration_probe.cpp
// Probe for Phase 2d WI-3: end-to-end SABR calibration cross-validation.
// Mirrors C++ test-suite/interpolations.cpp::testSabrInterpolation EXACTLY:
//   * non-null guesses: alphaGuess=sqrt(0.2), betaGuess=0.5,
//     nuGuess=sqrt(0.4), rhoGuess=0.0 (passed only when the corresponding
//     IsFixed flag is false; otherwise the initial true value is used)
//   * errorAccept=1e-10 (NOT the 0.0020 default — without this the Halton
//     restart loop accepts the first iteration's local minimum and the
//     5e-8 calibrationTolerance assertion fails)
//   * useMaxError=false, maxGuesses=50 (defaults), shift=0.0 (default)
//
// Two fixtures match the two currently-@Ignore'd Java tests:
//   fixture_1: SABRInterpolationTest::testSABRInterpolationTest
//   fixture_2: InterpolationTest::testSabrInterpolation
// Both Java tests share the identical 31-strike fixture (the deterministic
// SABR vols generated from initialAlpha=0.3, beta=0.6, nu=0.02, rho=0.01,
// forward=0.039, expiry=1.0). Storing them as separate cases keeps each
// Java test's reference lookup independent.
//
// For each fixture we iterate the same 16 IsFixed × 2 vegaWeighted × 2
// optimizer combinations the Java tests exercise (64 combos per fixture).
// Each combination is stored as its own sub-object inside the case's
// "expected" map so the Java side can read them by key.

#include <ql/version.hpp>
#include <ql/math/interpolations/sabrinterpolation.hpp>
#include <ql/math/optimization/levenbergmarquardt.hpp>
#include <ql/math/optimization/simplex.hpp>
#include <ql/math/optimization/endcriteria.hpp>
#include "../../common.hpp"

#include <cmath>
#include <vector>
#include <string>
#include <sstream>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

std::string endCriteriaToString(EndCriteria::Type t) {
    std::ostringstream os;
    os << t;
    return os.str();
}

json calibrateOnce(const std::vector<Real>& strikes,
                   const std::vector<Real>& vols,
                   Real expiry, Real forward,
                   Real alpha, Real beta, Real nu, Real rho,
                   bool aFixed, bool bFixed, bool nFixed, bool rFixed,
                   bool vegaWeighted,
                   const std::string& methodName) {
    ext::shared_ptr<EndCriteria> ec =
        ext::make_shared<EndCriteria>(100000, 100, 1e-8, 1e-8, 1e-8);
    ext::shared_ptr<OptimizationMethod> om;
    if (methodName == "Simplex") {
        om = ext::make_shared<Simplex>(0.01);
    } else {
        om = ext::make_shared<LevenbergMarquardt>(1e-8, 1e-8, 1e-8);
    }

    // errorAccept=1e-10 to match C++ test-suite/interpolations.cpp:1378
    // ("method, 1E-10)" — see C++ comment lines 1364-1370 explaining why
    // the default 0.0020 is too loose for the 5e-8 calibration tolerance).
    SABRInterpolation sabr(strikes.begin(), strikes.end(), vols.begin(),
                           expiry, forward,
                           alpha, beta, nu, rho,
                           aFixed, bFixed, nFixed, rFixed,
                           vegaWeighted, ec, om,
                           /*errorAccept*/ 1e-10,
                           /*useMaxError*/ false,
                           /*maxGuesses*/  50,
                           /*shift*/       0.0);
    sabr.update();

    // Fitted vols at the input strikes
    json fitted = json::array();
    for (auto k : strikes) fitted.push_back(sabr(k));

    return json{
        {"alpha", sabr.alpha()},
        {"beta",  sabr.beta()},
        {"nu",    sabr.nu()},
        {"rho",   sabr.rho()},
        {"error", sabr.rmsError()},
        {"maxError", sabr.maxError()},
        {"endCriteria", endCriteriaToString(sabr.endCriteria())},
        {"fittedVols", fitted}
    };
}

json buildFixture(const std::vector<Real>& strikes,
                  const std::vector<Real>& vols,
                  Real expiry, Real forward,
                  Real initialAlpha, Real initialBeta,
                  Real initialNu, Real initialRho) {
    // Mirrors C++ test-suite/interpolations.cpp lines 1331-1334.
    const Real alphaGuess = std::sqrt(0.2);
    const Real betaGuess  = 0.5;
    const Real nuGuess    = std::sqrt(0.4);
    const Real rhoGuess   = 0.0;

    json combos = json::object();
    const std::vector<std::string> methods = {"Simplex", "LM"};
    for (const auto& method : methods) {
        for (int vw = 0; vw < 2; ++vw) {
            for (int ka = 0; ka < 2; ++ka) {
                for (int kb = 0; kb < 2; ++kb) {
                    for (int kn = 0; kn < 2; ++kn) {
                        for (int kr = 0; kr < 2; ++kr) {
                            const bool aFixed = (ka == 0);
                            const bool bFixed = (kb == 0);
                            const bool nFixed = (kn == 0);
                            const bool rFixed = (kr == 0);
                            const bool vegaWeighted = (vw == 0);
                            // C++ test-suite/interpolations.cpp:1372-1376:
                            // when *IsFixed is true, seed with the known
                            // initial value; else use the explicit guess.
                            const Real alpha = aFixed ? initialAlpha : alphaGuess;
                            const Real beta  = bFixed ? initialBeta  : betaGuess;
                            const Real nu    = nFixed ? initialNu    : nuGuess;
                            const Real rho   = rFixed ? initialRho   : rhoGuess;
                            std::ostringstream key;
                            key << method
                                << "_vw" << (vegaWeighted ? 1 : 0)
                                << "_a" << (aFixed ? 1 : 0)
                                << "_b" << (bFixed ? 1 : 0)
                                << "_n" << (nFixed ? 1 : 0)
                                << "_r" << (rFixed ? 1 : 0);
                            combos[key.str()] = calibrateOnce(
                                strikes, vols, expiry, forward,
                                alpha, beta, nu, rho,
                                aFixed, bFixed, nFixed, rFixed,
                                vegaWeighted, method);
                        }
                    }
                }
            }
        }
    }
    return combos;
}

} // namespace

int main() {
    ReferenceWriter out("math/interpolations/sabr_calibration", QL_VERSION,
                        "sabr_calibration_probe");

    // Fixture from SABRInterpolationTest / InterpolationTest.testSabrInterpolation:
    // 31-strike SABR smile generated with α=0.3, β=0.6, ν=0.02, ρ=0.01,
    // forward=0.039, expiry=1.0.
    std::vector<Real> strikes(31);
    for (int i = 0; i < 31; ++i) strikes[i] = 0.03 + 0.002 * i;

    std::vector<Real> vols = {
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
    const Real expiry  = 1.0;
    const Real forward = 0.039;
    const Real initialAlpha = 0.3;
    const Real initialBeta  = 0.6;
    const Real initialNu    = 0.02;
    const Real initialRho   = 0.01;

    json inputs = {
        {"strikes", strikes},
        {"volatilities", vols},
        {"expiry", expiry},
        {"forward", forward},
        {"initialAlpha", initialAlpha},
        {"initialBeta", initialBeta},
        {"initialNu", initialNu},
        {"initialRho", initialRho}
    };

    // fixture_1 and fixture_2 share the same numeric inputs because the two
    // un-skipped Java tests calibrate the same smile. Storing them as
    // separate cases keeps each Java test's reference lookup independent.
    out.addCase("fixture_1", inputs,
                buildFixture(strikes, vols, expiry, forward,
                             initialAlpha, initialBeta, initialNu, initialRho));
    out.addCase("fixture_2", inputs,
                buildFixture(strikes, vols, expiry, forward,
                             initialAlpha, initialBeta, initialNu, initialRho));

    out.write();
    return 0;
}
