// migration-harness/cpp/probes/termstructures/volatility/smile-section/smile_section_probe.cpp
//
// Phase 5g.5b WI-2 + WI-3: emit C++ v1.42.1 reference values for
// InterpolatedSmileSection<Cubic> and SpreadedSmileSection. Java port
// matches at TIGHT tier (1e-9 abs / 1e-12 rel) for analytic queries.

#include <ql/version.hpp>
#include <ql/termstructures/volatility/interpolatedsmilesection.hpp>
#include <ql/termstructures/volatility/spreadedsmilesection.hpp>
#include <ql/termstructures/volatility/flatsmilesection.hpp>
#include <ql/math/interpolations/cubicinterpolation.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/handle.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>

#include "../../../common.hpp"

#include <vector>
#include <string>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

void runInterpolatedCase(ReferenceWriter& out) {
    Time tte = 1.5;
    std::vector<Rate> strikes = {0.02, 0.03, 0.04, 0.05, 0.06, 0.07};
    // stdDevs = vol * sqrt(tte). vol curve: 0.18, 0.16, 0.15, 0.14, 0.155, 0.17
    std::vector<Real> stdDevs;
    std::vector<Volatility> vols = {0.18, 0.16, 0.15, 0.14, 0.155, 0.17};
    for (Real v : vols) stdDevs.push_back(v * std::sqrt(tte));
    Real atmLevel = 0.05;

    InterpolatedSmileSection<Cubic> sec(
        tte, strikes, stdDevs, atmLevel,
        Cubic(), Actual365Fixed(),
        ShiftedLognormal, 0.0, false);

    std::vector<Real> queryStrikes = {0.02, 0.025, 0.03, 0.035, 0.04, 0.045,
                                       0.05, 0.055, 0.06, 0.065, 0.07};

    json strikeArr = json::array();
    json volArr    = json::array();
    json varArr    = json::array();
    for (Real k : queryStrikes) {
        strikeArr.push_back(k);
        volArr.push_back(sec.volatility(k));
        varArr.push_back(sec.variance(k));
    }
    json inputs = {
        {"tte",       tte},
        {"strikes",   strikes},
        {"vols",      vols},
        {"atm_level", atmLevel},
        {"shift",     0.0},
        {"flat_extrap", false}
    };
    json expected = {
        {"query_strikes", strikeArr},
        {"vol",           volArr},
        {"variance",      varArr},
        {"min_strike",    sec.minStrike()},
        {"max_strike",    sec.maxStrike()},
        {"atm_query",     sec.atmLevel()}
    };
    out.addCase("interpolated_cubic_5y_grid", inputs, expected);
}

void runInterpolatedFlatExtrapCase(ReferenceWriter& out) {
    Time tte = 2.0;
    std::vector<Rate> strikes = {0.03, 0.04, 0.05, 0.06, 0.07};
    std::vector<Volatility> vols = {0.20, 0.18, 0.17, 0.18, 0.19};
    std::vector<Real> stdDevs;
    for (Real v : vols) stdDevs.push_back(v * std::sqrt(tte));
    Real atmLevel = 0.05;

    InterpolatedSmileSection<Cubic> sec(
        tte, strikes, stdDevs, atmLevel,
        Cubic(), Actual365Fixed(),
        ShiftedLognormal, 0.0, true);  // flat extrapolation = true

    // Query strikes outside [min, max] to exercise flat-extrap branch
    std::vector<Real> queryStrikes = {0.01, 0.02, 0.03, 0.05, 0.07, 0.08, 0.10};

    json strikeArr = json::array();
    json volArr    = json::array();
    for (Real k : queryStrikes) {
        strikeArr.push_back(k);
        volArr.push_back(sec.volatility(k));
    }
    json inputs = {
        {"tte",       tte},
        {"strikes",   strikes},
        {"vols",      vols},
        {"atm_level", atmLevel},
        {"flat_extrap", true}
    };
    json expected = {
        {"query_strikes", strikeArr},
        {"vol",           volArr}
    };
    out.addCase("interpolated_cubic_flat_extrap", inputs, expected);
}

void runSpreadedCase(ReferenceWriter& out) {
    Time tte = 1.0;
    Real flatVol = 0.20;
    Real spread  = 0.025;
    auto base = ext::make_shared<FlatSmileSection>(
        tte, flatVol, Actual365Fixed(), 0.05);
    Handle<Quote> spreadQuote(ext::make_shared<SimpleQuote>(spread));
    SpreadedSmileSection sec(base, spreadQuote);

    std::vector<Real> queryStrikes = {0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08};
    json strikeArr = json::array();
    json volArr    = json::array();
    for (Real k : queryStrikes) {
        strikeArr.push_back(k);
        volArr.push_back(sec.volatility(k));
    }

    json inputs = {
        {"tte",      tte},
        {"flat_vol", flatVol},
        {"spread",   spread},
        {"atm",      0.05}
    };
    json expected = {
        {"query_strikes", strikeArr},
        {"vol",           volArr},
        {"min_strike",    sec.minStrike()},
        {"max_strike",    sec.maxStrike()},
        {"atm_level",     sec.atmLevel()}
    };
    out.addCase("spreaded_flat_section", inputs, expected);
}

} // namespace

int main() {
    ReferenceWriter out("termstructures/volatility/smile-section/smile_section",
                        QL_VERSION, "smile_section_probe");
    runInterpolatedCase(out);
    runInterpolatedFlatExtrapCase(out);
    runSpreadedCase(out);
    out.write();
    return 0;
}
