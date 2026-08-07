// migration-harness/cpp/probes/math/interpolations/zabr_interpolation_probe.cpp
//
// Emits the smile produced by QuantLib::Zabr — the ZABR interpolation
// factory/traits class at ql/math/interpolations/zabrinterpolation.hpp:169 —
// via its interpolate() method, i.e. through exactly the path the factory
// exists to provide.
//
// WHY THIS EXISTS: `Zabr` itself carries no arithmetic; it stores the fifteen
// calibration arguments and forwards them to ZabrInterpolation<Evaluation>. Its
// four structural siblings (SABR, NoArbSabr, VannaVolga, LinearFlat) are
// allowlisted in the coverage gate as traits-factory tags, but the ZABR one had
// never been ported, and — more to the point — the interpolation it produces
// had never been cross-validated either: the existing ZABR references pin
// ZabrModel (experimental/volatility/zabr_model), the closed-form smile, not
// ZabrInterpolation's XABR path with its parameter transformations and
// calibration loop.
//
// Two shapes are emitted for each fixture:
//   * "_fixed": every parameter fixed, so no optimisation runs and the values
//     are a deterministic function of the inputs. This isolates the smile
//     evaluation and the direct/inverse parameter transformations.
//   * "_calibrated": alpha, nu and rho free, so the XABR least-squares loop
//     runs. This pins the calibration path as well; the fitted parameters and
//     the fit error are emitted alongside the smile so a disagreement can be
//     attributed to the optimiser rather than to the model.
//
// The strike grids stay inside roughly +/-2 standard deviations of the forward:
// the ZABR short-maturity expansion loses accuracy in the far wings, and a wing
// value would record the expansion's breakdown rather than the port's fidelity.

#include <ql/version.hpp>

#include <ql/math/interpolations/zabrinterpolation.hpp>
#include <ql/math/optimization/levenbergmarquardt.hpp>

#include <cmath>
#include <string>
#include <vector>

#include "common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

struct Fixture {
    const char* key;
    Time t;
    Real forward;
    Real alpha, beta, nu, rho, gamma;
    std::vector<Real> strikes;
    std::vector<Volatility> vols;
};

void emit(ReferenceWriter& out,
          const std::string& key,
          const Fixture& fx,
          bool alphaIsFixed,
          bool betaIsFixed,
          bool nuIsFixed,
          bool rhoIsFixed,
          bool gammaIsFixed) {
    // ZabrShortMaturityLognormal is the evaluation the Java port pins; the
    // kernel affects smile evaluation only, not the calibration.
    Zabr<ZabrShortMaturityLognormal> factory(
        fx.t, fx.forward, fx.alpha, fx.beta, fx.nu, fx.rho, fx.gamma,
        alphaIsFixed, betaIsFixed, nuIsFixed, rhoIsFixed, gammaIsFixed,
        /*vegaWeighted=*/false,
        ext::make_shared<EndCriteria>(60000, 100, 1e-8, 1e-8, 1e-8),
        ext::make_shared<LevenbergMarquardt>(1e-8, 1e-8, 1e-8));

    Interpolation interp = factory.interpolate(fx.strikes.begin(), fx.strikes.end(), fx.vols.begin());
    interp.update();
    interp.enableExtrapolation();

    // Recover the fitted coefficients through the concrete type, which is what
    // interpolate() actually built.
    ZabrInterpolation<ZabrShortMaturityLognormal> concrete(
        fx.strikes.begin(), fx.strikes.end(), fx.vols.begin(),
        fx.t, fx.forward, fx.alpha, fx.beta, fx.nu, fx.rho, fx.gamma,
        alphaIsFixed, betaIsFixed, nuIsFixed, rhoIsFixed, gammaIsFixed,
        /*vegaWeighted=*/false,
        ext::make_shared<EndCriteria>(60000, 100, 1e-8, 1e-8, 1e-8),
        ext::make_shared<LevenbergMarquardt>(1e-8, 1e-8, 1e-8));
    concrete.update();

    json rows = json::array();
    const Real lo = fx.strikes.front();
    const Real hi = fx.strikes.back();
    for (int i = 0; i <= 24; ++i) {
        const Real k = lo + (hi - lo) * i / 24.0;
        rows.push_back(json{{"k", k}, {"vol", interp(k, /*allowExtrapolation=*/true)}});
    }

    out.addCase(key,
                json{{"t", fx.t},
                     {"forward", fx.forward},
                     {"alpha", fx.alpha},
                     {"beta", fx.beta},
                     {"nu", fx.nu},
                     {"rho", fx.rho},
                     {"gamma", fx.gamma},
                     {"alphaIsFixed", alphaIsFixed},
                     {"betaIsFixed", betaIsFixed},
                     {"nuIsFixed", nuIsFixed},
                     {"rhoIsFixed", rhoIsFixed},
                     {"gammaIsFixed", gammaIsFixed},
                     {"strikes", fx.strikes},
                     {"vols", fx.vols}},
                json{{"global", Zabr<ZabrShortMaturityLognormal>::global},
                     {"fittedAlpha", concrete.alpha()},
                     {"fittedBeta", concrete.beta()},
                     {"fittedNu", concrete.nu()},
                     {"fittedRho", concrete.rho()},
                     {"fittedGamma", concrete.gamma()},
                     {"rmsError", concrete.rmsError()},
                     {"rows", rows}});
}

} // namespace

int main() {
    ReferenceWriter out("math/interpolations/zabr_interpolation", QL_VERSION,
                        "zabr_interpolation_probe");

    const Fixture atmSkew{"atm_skew", 5.0, 0.03,
                          0.08, 0.70, 0.20, -0.30, 1.00,
                          {0.01, 0.02, 0.03, 0.04, 0.05, 0.06},
                          {0.2942, 0.2317, 0.1978, 0.1852, 0.1809, 0.1812}};

    const Fixture gammaLow{"gamma_low", 2.0, 0.05,
                           0.12, 0.60, 0.35, -0.20, 0.70,
                           {0.02, 0.035, 0.05, 0.065, 0.08},
                           {0.2600, 0.2200, 0.2000, 0.1950, 0.1980}};

    emit(out, "atm_skew_fixed", atmSkew, true, true, true, true, true);
    emit(out, "gamma_low_fixed", gammaLow, true, true, true, true, true);
    // alpha, nu and rho free; beta and gamma pinned, as ZABR fits are usually run
    emit(out, "atm_skew_calibrated", atmSkew, false, true, false, false, true);

    out.write();
    return 0;
}
