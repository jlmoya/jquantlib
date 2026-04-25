// migration-harness/cpp/probes/model/shortrate/coxingersollross_calibration_probe.cpp
// Reference values for CoxIngersollRoss discountBond and discountBondOption
// fingerprints across three (t, s) tuples. discountBond verifies the
// arguments_-indirection wiring (Phase 2b WI-3); discountBondOption
// (call + put) cross-validates the Phase 2c WI-1 stub-fill that wires
// the new NonCentralCumulativeChiSquaredDistribution into the option
// formula.

#include <ql/version.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/models/shortrate/onefactormodels/coxingersollross.hpp>
#include "../../common.hpp"

#include <vector>
#include <tuple>

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("model/shortrate/coxingersollross_calibration", QL_VERSION,
                        "coxingersollross_calibration_probe");

    const Real r0 = 0.05, theta = 0.06, k = 0.5, sigma = 0.04;
    CoxIngersollRoss model(r0, theta, k, sigma);

    json sampleArr = json::array();
    // Three (t, s) pairs covering the discountBondOption regime
    // (option matures at t, underlying bond matures at s>t). The
    // first tuple has t=0.5 to exercise the non-trivial chi-squared
    // path; the t=0 boundary case is intentionally omitted because
    // the option payoff degenerates to intrinsic.
    const Real strike = 0.95;
    for (auto pair : {std::make_tuple(0.5, 1.0),
                      std::make_tuple(0.5, 2.0),
                      std::make_tuple(1.0, 5.0)}) {
        const Time t = std::get<0>(pair);
        const Time T = std::get<1>(pair);
        const Real call = model.discountBondOption(Option::Call, strike, t, T);
        const Real put  = model.discountBondOption(Option::Put,  strike, t, T);
        sampleArr.push_back({{"t", t}, {"T", T},
                             {"discountBond", model.discountBond(t, T, r0)},
                             {"discountBondOptionCall", call},
                             {"discountBondOptionPut",  put}});
    }

    out.addCase("cir_discountbond_fingerprint",
        json{{"r0", r0}, {"theta", theta}, {"k", k}, {"sigma", sigma},
             {"strike", strike}},
        json{{"samples", sampleArr}});

    out.write();
    return 0;
}
