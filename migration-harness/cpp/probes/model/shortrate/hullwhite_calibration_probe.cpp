// migration-harness/cpp/probes/model/shortrate/hullwhite_calibration_probe.cpp
// Reference values for HullWhite discountBondOption fingerprint at three
// (strike, maturity, bondMaturity) tuples. Verifies the arguments_-
// indirection wiring carries through the Parameter system under
// HullWhite's time-dependent drift.

#include <ql/version.hpp>
#include <ql/models/shortrate/onefactormodels/hullwhite.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include "../../common.hpp"

#include <vector>
#include <tuple>

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("model/shortrate/hullwhite_calibration", QL_VERSION,
                        "hullwhite_calibration_probe");

    Settings::instance().evaluationDate() = Date(22, April, 2026);
    Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(Date(22, April, 2026), 0.04, Actual365Fixed()));

    const Real a = 0.1, sigma = 0.01;
    HullWhite model(ts, a, sigma);

    json sampleArr = json::array();
    for (auto t : {std::make_tuple(0.95, 0.5, 1.0),
                   std::make_tuple(1.00, 1.0, 2.0),
                   std::make_tuple(1.05, 2.0, 5.0)}) {
        const Real strike = std::get<0>(t);
        const Time mat = std::get<1>(t);
        const Time bMat = std::get<2>(t);
        sampleArr.push_back({{"strike", strike}, {"maturity", mat},
                             {"bondMaturity", bMat},
                             {"call", model.discountBondOption(Option::Call, strike, mat, bMat)},
                             {"put",  model.discountBondOption(Option::Put,  strike, mat, bMat)}});
    }

    out.addCase("hullwhite_round_trip",
        json{{"r_curve", 0.04}, {"a", a}, {"sigma", sigma}},
        json{{"samples", sampleArr}});

    // 5-arg discountBondOption(strike, maturity, bondStart, bondMaturity).
    // Verifies the forward-starting bond-option overload added in Phase 2c WI-4.1.
    json sample5Arr = json::array();
    for (auto t : {std::make_tuple(0.95, 0.5, 1.0, 2.0),
                   std::make_tuple(1.00, 1.0, 2.0, 3.0),
                   std::make_tuple(1.05, 2.0, 3.0, 5.0)}) {
        const Real strike = std::get<0>(t);
        const Time mat = std::get<1>(t);
        const Time bStart = std::get<2>(t);
        const Time bMat = std::get<3>(t);
        sample5Arr.push_back({{"strike", strike}, {"maturity", mat},
                              {"bondStart", bStart}, {"bondMaturity", bMat},
                              {"call", model.discountBondOption(Option::Call, strike, mat, bStart, bMat)},
                              {"put",  model.discountBondOption(Option::Put,  strike, mat, bStart, bMat)}});
    }

    out.addCase("hullwhite_fwd_starting_bond_option",
        json{{"r_curve", 0.04}, {"a", a}, {"sigma", sigma}},
        json{{"samples", sample5Arr}});

    // convexityBias static-method fingerprint at varying mean-reversion `a`,
    // including a near-zero value to exercise the small-a Taylor fallback
    // (a < QL_EPSILON) and the deltaT < QL_EPSILON early-return branch.
    json convexityArr = json::array();
    for (auto t : {std::make_tuple(94.0, 1.0, 1.25,  0.005, 1e-12),  // small-a fallback
                   std::make_tuple(95.0, 0.5, 0.75,  0.01,  0.05),
                   std::make_tuple(97.0, 2.0, 2.25,  0.02,  0.5)}) {
        const Real fp = std::get<0>(t);
        const Time tt = std::get<1>(t);
        const Time TT = std::get<2>(t);
        const Real sg = std::get<3>(t);
        const Real aa = std::get<4>(t);
        convexityArr.push_back({{"futurePrice", fp}, {"t", tt}, {"T", TT},
                                {"sigma", sg}, {"a", aa},
                                {"bias", HullWhite::convexityBias(fp, tt, TT, sg, aa)}});
    }
    out.addCase("hullwhite_convexity_bias",
        json{{"note", "static method, varies (a, t, T, sigma)"}},
        json{{"samples", convexityArr}});

    out.write();
    return 0;
}
