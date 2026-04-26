// migration-harness/cpp/probes/model/shortrate/calibrationhelpers/swaptionhelper_probe.cpp
//
// Probe for Phase 2e WI-3: SwaptionHelper marketValue / blackPrice / modelValue
// fingerprint, exercising the full helper API end-to-end.
//
// Cross-validates a 5Y x 5Y ATM payer swaption helper built from a Period
// (5Y maturity, 5Y length, vol=20%, ATM strike inferred from the curve)
// using a HullWhite (a=0.1, sigma=0.01) tree (100 steps) for modelValue.

#include <ql/version.hpp>

#include <ql/indexes/ibor/euribor.hpp>
#include <ql/models/shortrate/calibrationhelpers/swaptionhelper.hpp>
#include <ql/models/shortrate/onefactormodels/hullwhite.hpp>
#include <ql/pricingengines/swaption/treeswaptionengine.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/daycounters/thirty360.hpp>

#include "common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("model/shortrate/calibrationhelpers/swaptionhelper",
                        QL_VERSION, "swaptionhelper_probe");

    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;

    const DayCounter dc = Actual365Fixed();
    const Real flatRate = 0.05;
    const Real vol = 0.20;
    const Real hwA = 0.1;
    const Real hwSigma = 0.01;
    const Size timeSteps = 100;
    const Real nominal = 1.0;

    const Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(eval, flatRate, dc, Continuous, Annual));

    const auto idx = ext::make_shared<Euribor3M>(ts);
    const Handle<Quote> volHandle(ext::make_shared<SimpleQuote>(vol));

    const DayCounter fixedDc = Thirty360(Thirty360::European);
    const Period maturity = 5 * Years;
    const Period length = 5 * Years;
    const Period fixedLegTenor = 1 * Years;

    SwaptionHelper helper(maturity, length, volHandle, idx, fixedLegTenor,
                          fixedDc, dc, ts, BlackCalibrationHelper::RelativePriceError,
                          Null<Real>(), nominal, ShiftedLognormal, 0.0);
    auto hw = ext::make_shared<HullWhite>(ts, hwA, hwSigma);
    helper.setPricingEngine(
        ext::make_shared<TreeSwaptionEngine>(hw, timeSteps, ts));

    const Real marketValue = helper.marketValue();
    const Real blackPriceAtVol = helper.blackPrice(vol);
    const Real modelValue = helper.modelValue();

    json inputs = {
        {"eval_date", "2026-01-15"},
        {"flat_rate", flatRate},
        {"vol", vol},
        {"hw_a", hwA},
        {"hw_sigma", hwSigma},
        {"time_steps", timeSteps},
        {"maturity_years", 5},
        {"length_years", 5},
        {"fixed_leg_tenor", "1Y"},
        {"fixed_day_counter", "30/360 European"},
        {"floating_day_counter", "Actual/365 Fixed"},
        {"yts_day_counter", "Actual/365 Fixed"},
        {"index", "Euribor3M"},
        {"nominal", nominal},
        {"strike", "ATM"},
        {"calibration_error_type", "RelativePriceError"},
        {"vol_type", "ShiftedLognormal"},
        {"shift", 0.0}
    };
    json expected = {
        {"market_value", marketValue},
        {"black_price_at_vol", blackPriceAtVol},
        {"model_value", modelValue}
    };

    out.addCase("atm_5y5y_hw_tree", inputs, expected);
    out.write();
    return 0;
}
