// migration-harness/cpp/probes/math/interpolations/sabr_interpolation_probe.cpp
// Reference values for SABRInterpolation construction with Null<Real>
// guesses. Verifies that defaultValues() correctly fills in the
// sentinel slots (β=0.5, α=0.2*F^(1-β) when β<0.9999, ν=√0.4, ρ=0).
//
// This probe exercises *only* the construction path — it does not call
// update() / calibrate. The post-construction params reflect what
// XABRCoeffHolder's ctor assigns via SABRSpecs::defaultValues() before
// any optimization runs.

#include <ql/version.hpp>
#include <ql/math/interpolations/sabrinterpolation.hpp>
#include <ql/utilities/null.hpp>
#include "../../common.hpp"

#include <cmath>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("math/interpolations/sabr_interpolation", QL_VERSION,
                        "sabr_interpolation_probe");

    // Use a small 4-strike grid for deterministic construction.
    std::vector<Real> strikes = {0.05, 0.06, 0.07, 0.08};
    std::vector<Real> volatilities = {0.30, 0.28, 0.27, 0.27};
    const Real expiry = 1.0;
    const Real forward = 0.06;

    // Construct with Null<Real> guesses for all four params, IsFixed=false
    // (so paramIsFixed_ remains false because the params equal Null<Real>).
    SABRInterpolation sabr(strikes.begin(), strikes.end(), volatilities.begin(),
                            expiry, forward,
                            Null<Real>(), Null<Real>(),
                            Null<Real>(), Null<Real>(),
                            false, false, false, false,  // *IsFixed
                            false,  // vegaWeighted
                            ext::shared_ptr<EndCriteria>(),
                            ext::shared_ptr<OptimizationMethod>());

    // Capture post-construction param values (after defaultValues()),
    // BEFORE any update()/calibration.
    json inputs = {
        {"strikes", strikes},
        {"volatilities", volatilities},
        {"expiry", expiry},
        {"forward", forward},
        {"alphaGuess", "Null<Real>"},
        {"betaGuess",  "Null<Real>"},
        {"nuGuess",    "Null<Real>"},
        {"rhoGuess",   "Null<Real>"}
    };
    json expected = {
        {"alpha_post_default", sabr.alpha()},
        {"beta_post_default",  sabr.beta()},
        {"nu_post_default",    sabr.nu()},
        {"rho_post_default",   sabr.rho()}
    };
    out.addCase("nullguess_defaults", inputs, expected);

    // Case 2: high-beta defaults — exercises the alpha-default arm where
    // beta >= 0.9999 (C++ formula returns 0.2 * 1.0 = 0.2). Pin beta=1.0
    // with betaIsFixed=true so beta stays at 1.0 through construction.
    SABRInterpolation sabrHi(strikes.begin(), strikes.end(), volatilities.begin(),
                            expiry, forward,
                            Null<Real>(), 1.0,
                            Null<Real>(), Null<Real>(),
                            false, true, false, false,    // betaIsFixed=true
                            false,
                            ext::shared_ptr<EndCriteria>(),
                            ext::shared_ptr<OptimizationMethod>());
    json inputs2 = {
        {"strikes", strikes},
        {"volatilities", volatilities},
        {"expiry", expiry},
        {"forward", forward},
        {"alphaGuess", "Null<Real>"},
        {"betaGuess",  1.0},
        {"nuGuess",    "Null<Real>"},
        {"rhoGuess",   "Null<Real>"},
        {"betaIsFixed", true}
    };
    json expected2 = {
        {"alpha_post_default", sabrHi.alpha()},
        {"beta_post_default",  sabrHi.beta()},
        {"nu_post_default",    sabrHi.nu()},
        {"rho_post_default",   sabrHi.rho()}
    };
    out.addCase("highbeta_defaults", inputs2, expected2);

    out.write();
    return 0;
}
