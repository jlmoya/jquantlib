// migration-harness/cpp/probes/credit/random_default_model_probe.cpp
// Reference values for ql/experimental/credit/randomdefaultmodel.{hpp,cpp}
// (QuantLib::RandomDefaultModel / GaussianRandomDefaultModel).
//
// GaussianRandomDefaultModel::nextSequence() is Monte-Carlo (RNG-driven) and
// therefore NOT deterministically cross-validatable bit-for-bit across the
// C++ Mersenne-Twister and the JQuantLib one without matching the RNG exactly.
// Per the migration guidance we cross-validate the DETERMINISTIC
// sub-computations that nextSequence() performs for each name:
//
//   1. conditional default probability  p = Phi(y),  where
//        y = a*M + sqrt(1-a^2)*Z,  a = sqrt(correlation)
//      (the copula draw -> implied default probability step).
//
//   2. default-time inversion: the time t solving
//        dts->defaultProbability(t) = p
//      found by Brent on [0, tmax] (exactly the solver call inside
//      nextSequence). For a flat hazard rate lambda (Actual/Actual ISDA),
//      defaultProbability(t) = 1 - exp(-lambda * tau(t)), so this is a true
//      ground-truth on the inversion path the model uses.
//
// These two pieces fully determine the default time for a *given* copula draw,
// so asserting them is a faithful deterministic cross-check of the model
// without any MC path-count brittleness.

#include <ql/version.hpp>
#include <ql/experimental/credit/onefactorgaussiancopula.hpp>
#include <ql/math/distributions/normaldistribution.hpp>
#include <ql/math/solvers1d/brent.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/credit/flathazardrate.hpp>
#include <ql/time/daycounters/actualactual.hpp>
#include <ql/settings.hpp>
#include "../common.hpp"

#include <cmath>
#include <string>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// Mirrors the anonymous Root functor in randomdefaultmodel.cpp.
class Root {
  public:
    Root(Handle<DefaultProbabilityTermStructure> dts, Real pd)
    : dts_(std::move(dts)), pd_(pd) {}
    Real operator()(Real t) const {
        return dts_->defaultProbability(t, true) - pd_;
    }
  private:
    const Handle<DefaultProbabilityTermStructure> dts_;
    Real pd_;
};

} // namespace

int main() {
    ReferenceWriter out("credit/random_default_model", QL_VERSION,
                        "random_default_model_probe.cpp");

    const Date asof(31, August, 2006);
    Settings::instance().evaluationDate() = asof;

    // ---- 1. conditional default probability p = Phi(y) ----------------------
    // y = a*M + sqrt(1-a^2)*Z ; we emit Phi(y) for fixed (correlation, M, Z).
    {
        CumulativeNormalDistribution phi;
        struct Draw { Real correlation; Real m; Real z; };
        std::vector<std::pair<std::string, Draw>> draws = {
            {"corr30_m0_z0",      {0.30,  0.0,  0.0}},
            {"corr30_mp1_zm05",   {0.30,  1.0, -0.5}},
            {"corr30_mm15_zp2",   {0.30, -1.5,  2.0}},
            {"corr10_mp05_zp05",  {0.10,  0.5,  0.5}},
            {"corr50_mm2_zm1",    {0.50, -2.0, -1.0}}
        };
        for (auto& [name, d] : draws) {
            const Real a = std::sqrt(d.correlation);
            const Real y = a * d.m + std::sqrt(1.0 - a * a) * d.z;
            const Real p = phi(y);
            out.addCase("condprob_" + name,
                        {{"correlation", d.correlation}, {"M", d.m}, {"Z", d.z}, {"y", y}},
                        (double) p);
        }
    }

    // ---- 2. default-time inversion via Brent (flat hazard) ------------------
    // For a flat hazard rate lambda, invert dts->defaultProbability(t) = p.
    {
        const Real lambda = 0.01;
        const Real tmax = 5.0;
        Handle<Quote> hazardRate(ext::shared_ptr<Quote>(new SimpleQuote(lambda)));
        ext::shared_ptr<DefaultProbabilityTermStructure> defPtr(
            new FlatHazardRate(asof, hazardRate, ActualActual(ActualActual::ISDA)));
        Handle<DefaultProbabilityTermStructure> dts(defPtr);

        const Real accuracy = 1.0e-8;
        std::vector<std::pair<std::string, Real>> targets = {
            {"p_001", 0.001},
            {"p_005", 0.005},
            {"p_01",  0.01},
            {"p_02",  0.02},
            {"p_03",  0.03}
        };
        for (auto& [name, p] : targets) {
            // exactly the solver call inside nextSequence():
            //   Brent with lower=0, upper=tmax, guess=tmax/2, step=1.0
            Brent brent;
            brent.setLowerBound(0.0);
            brent.setUpperBound(tmax);
            const Real t = brent.solve(Root(dts, p), accuracy, tmax / 2.0, 1.0);
            out.addCase("invtime_" + name,
                        {{"lambda", lambda}, {"tmax", tmax}, {"p", p}, {"accuracy", accuracy},
                         {"asof", asof.serialNumber()}},
                        (double) t);
        }

        // Also emit the default probability at tmax (used by nextSequence to
        // decide whether the name defaults within the horizon at all).
        out.addCase("defprob_at_tmax",
                    {{"lambda", lambda}, {"tmax", tmax}},
                    (double) dts->defaultProbability(tmax, true));
    }

    out.write();
    return 0;
}
