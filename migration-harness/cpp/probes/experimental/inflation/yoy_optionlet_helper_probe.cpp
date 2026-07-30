// migration-harness/cpp/probes/experimental/inflation/yoy_optionlet_helper_probe.cpp
// Reference values for YoYOptionletHelper
// (ql/experimental/inflation/yoyoptionlethelpers.{hpp,cpp}).
//
// The helper is a BootstrapHelper<YoYOptionletVolatilitySurface>. We can
// drive it directly without needing a YoYCapFloorTermPriceSurface (which is
// Phase 2s Track C territory):
//   1. Build a YoY index with a known YoY term structure.
//   2. Build a flat-vol ConstantYoYOptionletVolatility.
//   3. Build a Black YoY cap-floor engine.
//   4. Build a YoYOptionletHelper for a specific (price, K, n).
//   5. Set the term structure on the helper -> setVolatility(...) on engine.
//   6. Capture impliedQuote() (i.e. NPV of the cap/floor under the vol surface).
//      This is what the helper feeds back to the bootstrap loop.
//
// We probe a small grid of (n, K, capFloor type, vol level) so the Java side
// can reproduce.

#include <cstdio>
#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/currencies/europe.hpp>
#include <ql/experimental/inflation/genericindexes.hpp>
#include <ql/experimental/inflation/yoyoptionlethelpers.hpp>
#include <ql/indexes/region.hpp>
#include <ql/instruments/inflationcapfloor.hpp>
#include <ql/instruments/makeyoyinflationcapfloor.hpp>
#include <ql/pricingengines/inflation/inflationcapfloorengines.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/inflation/inflationhelpers.hpp>
#include <ql/termstructures/inflation/interpolatedyoyinflationcurve.hpp>
#include <ql/termstructures/volatility/inflation/yoyinflationoptionletvolatilitystructure.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/unitedkingdom.hpp>
#include <ql/time/daycounters/actualactual.hpp>
#include <ql/math/interpolations/linearinterpolation.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("experimental/inflation/yoy_optionlet_helper",
                        QL_VERSION,
                        "yoy_optionlet_helper_probe");

    // ---------- common setup (mirrors InflationCapFloorEnginesTest setup) ----------
    Date evalDate(13, August, 2007);
    Settings::instance().evaluationDate() = evalDate;

    Calendar cal = UnitedKingdom();
    BusinessDayConvention bdc = ModifiedFollowing;
    DayCounter dc = ActualActual(ActualActual::ISDA);
    Frequency freq = Monthly;
    Period observationLag(3, Months);
    Date refDate = cal.adjust(evalDate, bdc);

    // YoY curve
    std::vector<Date> nodeDates = {
        Date(1,  May,    2007),
        Date(13, August, 2008),
        Date(13, August, 2009),
        Date(13, August, 2010),
        Date(13, August, 2012),
        Date(13, August, 2017)
    };
    std::vector<Rate> nodeRates = {0.025, 0.027, 0.029, 0.031, 0.034, 0.036};
    ext::shared_ptr<YoYInflationTermStructure> yoyCurve(
        new InterpolatedYoYInflationCurve<Linear>(refDate, nodeDates,
                                                  nodeRates, freq, dc));
    yoyCurve->enableExtrapolation();
    Handle<YoYInflationTermStructure> ts(yoyCurve);
    // Construct YoY index manually with 2-month availability lag (matches
    // Java YYUKRPI default; mirrors the Phase 2r probe pattern for
    // inflation_cap_floor_engines_probe so Java reproduces the result).
    ext::shared_ptr<YoYInflationIndex> yyIndex(
        new YoYInflationIndex("YY_RPI", UKRegion(), false,
                              Monthly, Period(2, Months),
                              GBPCurrency(), ts));

    // Seed historic fixings (mirror the cap-floor-engines probe set)
    std::vector<Date> fixDates = {
        Date(1, January,   2005), Date(1, February,  2005),
        Date(1, March,     2005), Date(1, April,     2005),
        Date(1, May,       2005), Date(1, June,      2005),
        Date(1, July,      2005), Date(1, August,    2005),
        Date(1, September, 2005), Date(1, October,   2005),
        Date(1, November,  2005), Date(1, December,  2005),
        Date(1, January,   2006), Date(1, February,  2006),
        Date(1, March,     2006), Date(1, April,     2006),
        Date(1, May,       2006), Date(1, June,      2006),
        Date(1, July,      2006), Date(1, August,    2006),
        Date(1, September, 2006), Date(1, October,   2006),
        Date(1, November,  2006), Date(1, December,  2006),
        Date(1, January,   2007), Date(1, February,  2007),
        Date(1, March,     2007), Date(1, April,     2007),
        Date(1, May,       2007), Date(1, June,      2007),
        Date(1, July,      2007),
    };
    for (const Date& d : fixDates) {
        yyIndex->addFixing(d, 0.025, true);
    }

    // Nominal curve (flat 5%) for engine
    Handle<YieldTermStructure> nominalTS(
        ext::shared_ptr<YieldTermStructure>(
            new FlatForward(refDate, 0.05, dc, Compounded, Annual)));

    // Flat vol surface (reusable across helpers)
    Volatility flatVol = 0.20;
    ext::shared_ptr<YoYOptionletVolatilitySurface> volSurface(
        new ConstantYoYOptionletVolatility(flatVol, 0, cal, bdc, dc,
                                           observationLag, freq, false));
    Handle<YoYOptionletVolatilitySurface> hVS(volSurface);

    // Black cap/floor engine
    ext::shared_ptr<YoYInflationCapFloorEngine> blackEngine(
        new YoYInflationBlackCapFloorEngine(yyIndex, hVS, nominalTS));

    // ---------- probe scenarios ----------
    // For each (capFloorType, n, K), construct a YoYOptionletHelper with a
    // dummy quote of 1.0 and capture impliedQuote(). The Java side
    // reproduces the same setup with the same input data.

    struct Scenario {
        std::string label;
        YoYInflationCapFloor::Type type;
        int n;          // number of payments
        Rate strike;
    };

    std::vector<Scenario> scenarios = {
        {"cap_n1_k0p03", YoYInflationCapFloor::Cap, 1, 0.03},
        {"cap_n2_k0p03", YoYInflationCapFloor::Cap, 2, 0.03},
        {"cap_n3_k0p03", YoYInflationCapFloor::Cap, 3, 0.03},
        {"cap_n2_k0p02", YoYInflationCapFloor::Cap, 2, 0.02},
        {"cap_n2_k0p04", YoYInflationCapFloor::Cap, 2, 0.04},
        {"floor_n2_k0p02", YoYInflationCapFloor::Floor, 2, 0.02},
        {"floor_n2_k0p03", YoYInflationCapFloor::Floor, 2, 0.03},
    };

    Real notional = 10000.0;  // bps
    int fixingDays = 0;
    Period helperLag = observationLag;

    for (const auto& sc : scenarios) {
        Handle<Quote> dummyQuote(ext::shared_ptr<Quote>(new SimpleQuote(1.0)));

        ext::shared_ptr<YoYOptionletHelper> helper(
            new YoYOptionletHelper(dummyQuote, notional, sc.type,
                                   helperLag, dc, cal, fixingDays,
                                   yyIndex, CPI::Flat,
                                   sc.strike, sc.n, blackEngine));

        // Set the term structure -> the engine vol surface gets reset.
        helper->setTermStructure(volSurface.get());

        Real iq = helper->impliedQuote();

        json inp = {
            {"capFloorType", sc.type == YoYInflationCapFloor::Cap ? "Cap" : "Floor"},
            {"n", sc.n},
            {"strike", sc.strike},
            {"notional", notional},
            {"flatVol", flatVol},
            {"observationLag_months", helperLag.length()},
            {"fixingDays", fixingDays}
        };

        json expected = { {"impliedQuote", iq} };

        out.addCase(sc.label, inp, expected);
    }

    out.write();
    return 0;
}
