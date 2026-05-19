// migration-harness/cpp/probes/instruments/cds_isda_engine_probe.cpp
// Phase 5e.5b-CFC-d-286 — reproduce the ISDA-compliant USD discount curve
// from test-suite/creditdefaultswap.cpp::testIsdaEngine (May 2009 Markit
// rates), and emit the per-pillar (date, discount-factor) pairs plus the
// 20 expected upfront values. Used as the reference oracle for the Java
// CreditDefaultSwapTest.testIsdaEngine drift investigation.
//
// Oracle: QuantLib v1.42.1.

#include <ql/version.hpp>
#include <ql/cashflows/iborcoupon.hpp>
#include <ql/currencies/america.hpp>
#include <ql/instruments/creditdefaultswap.hpp>
#include <ql/instruments/makecds.hpp>
#include <ql/pricingengines/credit/isdacdsengine.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/credit/flathazardrate.hpp>
#include <ql/termstructures/yield/piecewiseyieldcurve.hpp>
#include <ql/termstructures/yield/ratehelpers.hpp>
#include <ql/time/calendars/weekendsonly.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/daycounters/thirty360.hpp>

#include "../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

void emit(ReferenceWriter& out) {
    Date tradeDate(21, May, 2009);
    Settings::instance().evaluationDate() = tradeDate;

    int dep_tenors[] = {1, 2, 3, 6, 9, 12};
    double dep_quotes[] = {0.003081, 0.005525, 0.007163,
                           0.012413, 0.014, 0.015488};
    int swap_tenors[] = {2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 25, 30};
    double swap_quotes[] = {0.011907, 0.01699, 0.021198, 0.02444,
                            0.026937, 0.028967, 0.030504, 0.031719, 0.03279,
                            0.034535, 0.036217, 0.036981, 0.037246, 0.037605};

    std::vector<ext::shared_ptr<RateHelper>> helpers;
    for (size_t i = 0; i < sizeof(dep_tenors)/sizeof(int); i++) {
        helpers.push_back(ext::make_shared<DepositRateHelper>(
            dep_quotes[i], dep_tenors[i]*Months, 2,
            WeekendsOnly(), ModifiedFollowing, false, Actual360()));
    }
    auto isdaIbor = ext::make_shared<IborIndex>(
        "IsdaIbor", 3*Months, 2, USDCurrency(), WeekendsOnly(),
        ModifiedFollowing, false, Actual360());
    for (size_t i = 0; i < sizeof(swap_tenors)/sizeof(int); i++) {
        helpers.push_back(ext::make_shared<SwapRateHelper>(
            swap_quotes[i], swap_tenors[i]*Years, WeekendsOnly(),
            Semiannual, ModifiedFollowing,
            Thirty360(Thirty360::BondBasis), isdaIbor));
    }

    auto curve = ext::make_shared<PiecewiseYieldCurve<Discount, LogLinear>>(
        0, WeekendsOnly(), helpers, Actual365Fixed());
    RelinkableHandle<YieldTermStructure> discountCurve(curve);

    // ---- helper dates (per-pillar diagnostics) ----
    json helperDates = json::array();
    for (size_t i = 0; i < helpers.size(); i++) {
        auto& h = helpers[i];
        json entry;
        entry["index"] = (int)i;
        entry["earliestDate"] = h->earliestDate().serialNumber();
        entry["maturityDate"] = h->maturityDate().serialNumber();
        entry["pillarDate"]   = h->pillarDate().serialNumber();
        entry["latestDate"]   = h->latestDate().serialNumber();
        entry["latestRelevantDate"] = h->latestRelevantDate().serialNumber();
        helperDates.push_back(entry);
    }

    // ---- discount-curve nodes (date, discount) ----
    json nodes = json::array();
    auto dates = curve->dates();
    auto times = curve->times();
    auto data  = curve->data();   // discount factors
    for (size_t i = 0; i < dates.size(); i++) {
        json n;
        n["index"]    = (int)i;
        n["date"]     = dates[i].serialNumber();
        n["time"]     = times[i];
        n["discount"] = data[i];
        nodes.push_back(n);
    }

    // ---- canonical probe DF samples (anchor dates) ----
    // Mostly the term dates that drive the upfront sweep below.
    json sampleDFs = json::array();
    Date sampleDates[] = {
        Date(20, June, 2010),
        Date(20, June, 2011),
        Date(20, June, 2012),
        Date(20, June, 2016),
        Date(20, June, 2019)
    };
    for (auto d : sampleDates) {
        json s;
        s["date"]     = d.serialNumber();
        s["discount"] = curve->discount(d);
        sampleDFs.push_back(s);
    }

    // ---- full upfront sweep, with intermediate hazard rate ----
    Date termDates[] = {
        Date(20, June, 2010), Date(20, June, 2011),
        Date(20, June, 2012), Date(20, June, 2016),
        Date(20, June, 2019)
    };
    Rate spreads[]     = {0.001, 0.1};
    Rate recoveries[]  = {0.2, 0.4};

    json sweep = json::array();
    RelinkableHandle<DefaultProbabilityTermStructure> probabilityCurve;
    int l = 0;
    for (auto termDate : termDates) {
        for (auto spread : spreads) {
            for (auto recovery : recoveries) {
                ext::shared_ptr<CreditDefaultSwap> quotedTrade =
                    MakeCreditDefaultSwap(termDate, spread)
                        .withNominal(10000000.);
                Rate h = quotedTrade->impliedHazardRate(
                    0., discountCurve, Actual365Fixed(),
                    recovery, 1e-10, CreditDefaultSwap::ISDA);

                probabilityCurve.linkTo(ext::make_shared<FlatHazardRate>(
                    0, WeekendsOnly(), h, Actual365Fixed()));

                auto engine = ext::make_shared<IsdaCdsEngine>(
                    probabilityCurve, recovery, discountCurve, ext::nullopt,
                    IsdaCdsEngine::Taylor, IsdaCdsEngine::HalfDayBias,
                    IsdaCdsEngine::Piecewise);

                ext::shared_ptr<CreditDefaultSwap> conventionalTrade =
                    MakeCreditDefaultSwap(termDate, 0.01)
                        .withNominal(10000000.)
                        .withPricingEngine(engine);

                double fairUpfront    = conventionalTrade->fairUpfront();
                double upfrontPayment =
                    conventionalTrade->notional() * fairUpfront;

                json row;
                row["index"]         = l;
                row["termDate"]      = termDate.serialNumber();
                row["spread"]        = spread;
                row["recovery"]      = recovery;
                row["hazardRate"]    = h;
                row["fairUpfront"]   = fairUpfront;
                row["notionalUpfront"] = upfrontPayment;
                sweep.push_back(row);
                l++;
            }
        }
    }

    json inputs;
    inputs["tradeDate"] = tradeDate.serialNumber();
    inputs["depTenors"]  = std::vector<int>(dep_tenors, dep_tenors + 6);
    inputs["depQuotes"]  = std::vector<double>(dep_quotes, dep_quotes + 6);
    inputs["swapTenors"] = std::vector<int>(swap_tenors, swap_tenors + 14);
    inputs["swapQuotes"] = std::vector<double>(swap_quotes, swap_quotes + 14);

    json expected;
    expected["helperDates"] = helperDates;
    expected["curveNodes"]  = nodes;
    expected["sampleDiscounts"] = sampleDFs;
    expected["upfrontSweep"] = sweep;
    expected["usingAtParCoupons"] =
        IborCoupon::Settings::instance().usingAtParCoupons();

    out.addCase("testIsdaEngine_usdCurveAndSweep", inputs, expected);
}

} // namespace

int main() {
    ReferenceWriter out("instruments/cds_isda_engine",
                        QL_VERSION,
                        "migration-harness/cpp/probes/instruments/cds_isda_engine_probe.cpp");
    emit(out);
    out.write();
    return 0;
}
