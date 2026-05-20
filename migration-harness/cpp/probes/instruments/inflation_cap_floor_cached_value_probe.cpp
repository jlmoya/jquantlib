// migration-harness/cpp/probes/instruments/inflation_cap_floor_cached_value_probe.cpp
// Diagnostic probe to capture per-coupon details (fixingDate, forward, stdDev,
// optionlet values) for the cached-value scenario from
// test-suite/inflationcapfloor.cpp::testCachedValue.
// Phase 2x — diagnostic for the YoYInflationCoupon convention divergence.
//
// Reproduces the exact CommonVars setup of the testCachedValue C++ test and
// dumps internal state so we can compare against Java.

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/cashflows/cashflows.hpp>
#include <ql/cashflows/cashflowvectors.hpp>
#include <ql/cashflows/inflationcouponpricer.hpp>
#include <ql/cashflows/yoyinflationcoupon.hpp>
#include <ql/indexes/inflation/ukrpi.hpp>
#include <ql/instruments/inflationcapfloor.hpp>
#include <ql/pricingengines/blackformula.hpp>
#include <ql/pricingengines/inflation/inflationcapfloorengines.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/inflation/inflationhelpers.hpp>
#include <ql/termstructures/inflation/piecewiseyoyinflationcurve.hpp>
#include <ql/termstructures/volatility/inflation/yoyinflationoptionletvolatilitystructure.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/unitedkingdom.hpp>
#include <ql/time/daycounters/actualactual.hpp>
#include <ql/time/daycounters/thirty360.hpp>
#include <ql/time/schedule.hpp>

using namespace jqml_harness;
using namespace QuantLib;

namespace {
struct Datum { Date date; Rate rate; };

// Type alias flattens the chained template close-brackets so static C++ parsers
// without the QL include-path can still tokenize the function signature.
typedef BootstrapHelper<YoYInflationTermStructure> YoYHelperT;
typedef ext::shared_ptr<YoYHelperT>                YoYHelperPtr;
typedef std::vector<YoYHelperPtr>                  YoYHelperList;

YoYHelperList makeHelpers(
        const std::vector<Datum>& iiData,
        const ext::shared_ptr<YoYInflationIndex>& ii,
        CPI::InterpolationType interpolation,
        const Period& obsLag,
        const Calendar& cal,
        const BusinessDayConvention& bdc,
        const DayCounter& dc,
        const Handle<YieldTermStructure>& discount) {
    YoYHelperList v;
    for (Datum d : iiData) {
        Handle<Quote> q(ext::make_shared<SimpleQuote>(d.rate/100.0));
        v.push_back(ext::make_shared<YearOnYearInflationSwapHelper>(
            q, obsLag, d.date, cal, bdc, dc, ii, interpolation, discount));
    }
    return v;
}
} // namespace

int main() {
    ReferenceWriter out("instruments/inflation_cap_floor_cached_value",
                        QL_VERSION, "inflation_cap_floor_cached_value_probe");

    // ----------- CommonVars (verbatim from testCachedValue setup) -----------
    Frequency frequency = Annual;
    Calendar calendar = UnitedKingdom();
    BusinessDayConvention convention = ModifiedFollowing;
    Date today(13, August, 2007);
    Date evalDate = calendar.adjust(today);
    Settings::instance().evaluationDate() = evalDate;
    Natural settlementDays = 0;
    Natural fixingDays = 0;
    DayCounter dc = Thirty360(Thirty360::BondBasis);

    RelinkableHandle<YoYInflationTermStructure> hy;

    Date from(1, January, 2005);
    Date to(13, August, 2007);
    Schedule rpiSchedule = MakeSchedule().from(from).to(to)
        .withTenor(1*Months)
        .withCalendar(UnitedKingdom())
        .withConvention(ModifiedFollowing);
    Real fixData[] = { 189.9, 189.9, 189.6, 190.5, 191.6, 192.0,
                       192.2, 192.2, 192.6, 193.1, 193.3, 193.6,
                       194.1, 193.4, 194.2, 195.0, 196.5, 197.7,
                       198.5, 198.5, 199.2, 200.1, 200.4, 201.1,
                       202.7, 201.6, 203.1, 204.4, 205.4, 206.2,
                       207.3, -999.0, -999 };
    auto rpi = ext::make_shared<UKRPI>();
    for (Size i=0; i<rpiSchedule.size(); i++) {
        rpi->addFixing(rpiSchedule[i], fixData[i]);
    }
    auto iir = ext::make_shared<YoYInflationIndex>(rpi, hy);

    RelinkableHandle<YieldTermStructure> nominalTS;
    nominalTS.linkTo(ext::make_shared<FlatForward>(evalDate, 0.05,
        ActualActual(ActualActual::ISDA)));

    Period observationLag = Period(2, Months);
    std::vector<Datum> yyData = {
        {Date(13, August, 2008), 2.95},
        {Date(13, August, 2009), 2.95},
        {Date(13, August, 2010), 2.93},
        {Date(15, August, 2011), 2.955},
        {Date(13, August, 2012), 2.945},
        {Date(13, August, 2013), 2.985},
        {Date(13, August, 2014), 3.01},
        {Date(13, August, 2015), 3.035},
        {Date(13, August, 2016), 3.055},
        {Date(13, August, 2017), 3.075},
        {Date(13, August, 2019), 3.105},
        {Date(15, August, 2022), 3.135},
        {Date(13, August, 2027), 3.155},
        {Date(13, August, 2032), 3.145},
        {Date(13, August, 2037), 3.145}
    };

    auto helpers = makeHelpers(yyData, iir, CPI::Flat, observationLag,
                               calendar, convention, dc,
                               Handle<YieldTermStructure>(nominalTS));
    Date baseDate = rpi->lastFixingDate();
    Rate baseYY = yyData[0].rate/100.0;
    auto pYYTS = ext::make_shared<PiecewiseYoYInflationCurve<Linear>>(
        evalDate, baseDate, baseYY, iir->frequency(), dc, helpers);
    auto yoyTS = ext::dynamic_pointer_cast<YoYInflationTermStructure>(pYYTS);
    hy.linkTo(pYYTS);

    // -------------- Build the leg the test builds (j=2 years) --------------
    Size j = 2;
    Date endDate = calendar.advance(evalDate, j*Years, Unadjusted);
    Schedule schedule(evalDate, endDate, Period(frequency), calendar,
                      Unadjusted, Unadjusted, DateGeneration::Forward, false);
    Leg leg = yoyInflationLeg(schedule, calendar, iir, observationLag, CPI::Flat)
        .withNotionals(std::vector<Real>(1, 1000000.0))
        .withPaymentDayCounter(dc)
        .withPaymentAdjustment(convention);

    // ----------- Bootstrapped curve nodes -----------
    {
        json nodes = json::array();
        auto ns = pYYTS->nodes();
        for (auto& p : ns) {
            std::ostringstream isoStream;
            isoStream << io::iso_date(p.first);
            nodes.push_back(json{
                {"date_serial", (Integer)p.first.serialNumber()},
                {"date_iso", isoStream.str()},
                {"rate", p.second}
            });
        }
        json inp{};
        json exp{{"nodes", nodes},
                 {"baseDate_serial", (Integer)pYYTS->baseDate().serialNumber()},
                 {"baseRate", pYYTS->baseRate()},
                 {"refDate_serial", (Integer)pYYTS->referenceDate().serialNumber()}};
        out.addCase("curve_nodes", inp, exp);
    }

    // ----------- Per-coupon details -----------
    {
        json coupons = json::array();
        for (Size i = 0; i < leg.size(); ++i) {
            auto cf = leg[i];
            auto cpn = ext::dynamic_pointer_cast<YoYInflationCoupon>(cf);
            Date fixDate = cpn->fixingDate();
            Rate forward = yoyTS->yoyRate(fixDate);
            std::ostringstream s1, s2, s3, s4;
            s1 << io::iso_date(cpn->accrualStartDate());
            s2 << io::iso_date(cpn->accrualEndDate());
            s3 << io::iso_date(cpn->date());
            s4 << io::iso_date(fixDate);
            coupons.push_back(json{
                {"i", (Integer)i},
                {"accrualStart_serial", (Integer)cpn->accrualStartDate().serialNumber()},
                {"accrualStart_iso", s1.str()},
                {"accrualEnd_serial", (Integer)cpn->accrualEndDate().serialNumber()},
                {"accrualEnd_iso", s2.str()},
                {"payDate_serial", (Integer)cpn->date().serialNumber()},
                {"payDate_iso", s3.str()},
                {"fixingDate_serial", (Integer)fixDate.serialNumber()},
                {"fixingDate_iso", s4.str()},
                {"observationLag_months", cpn->observationLag().length()},
                {"accrualPeriod", cpn->accrualPeriod()},
                {"forward_rate_at_fixing", forward},
                {"nominal_discount_at_pay", nominalTS->discount(cpn->date())}
            });
        }
        json inp{};
        json exp{{"coupons", coupons}};
        out.addCase("leg_coupons", inp, exp);
    }

    // ----------- Cap / Floor NPVs (Black) -----------
    {
        Real K = 0.0295;
        Real vol = 0.01;

        Handle<YoYOptionletVolatilitySurface> volH(
            ext::make_shared<ConstantYoYOptionletVolatility>(
                vol, settlementDays, calendar, convention, dc,
                observationLag, frequency, iir->interpolated()));

        for (int which = 0; which < 3; ++which) {
            const char* names[3] = {"black", "dd", "bachelier"};
            ext::shared_ptr<PricingEngine> eng;
            if (which == 0) eng = ext::make_shared<YoYInflationBlackCapFloorEngine>(
                iir, volH, Handle<YieldTermStructure>(nominalTS));
            else if (which == 1) eng = ext::make_shared<YoYInflationUnitDisplacedBlackCapFloorEngine>(
                iir, volH, Handle<YieldTermStructure>(nominalTS));
            else eng = ext::make_shared<YoYInflationBachelierCapFloorEngine>(
                iir, volH, Handle<YieldTermStructure>(nominalTS));

            auto cap = ext::make_shared<YoYInflationCapFloor>(
                YoYInflationCapFloor::Cap, leg, std::vector<Rate>(1, K));
            cap->setPricingEngine(eng);
            auto floor = ext::make_shared<YoYInflationCapFloor>(
                YoYInflationCapFloor::Floor, leg, std::vector<Rate>(1, K));
            floor->setPricingEngine(eng);

            Real capNPV = cap->NPV();
            Real floorNPV = floor->NPV();

            // Get per-optionlet decomposition (additionalResults)
            json capOptPrice = json::array();
            json capOptFwd = json::array();
            json capOptStd = json::array();
            const auto& capAR = cap->additionalResults();
            if (capAR.count("optionletsPrice")) {
                auto v = ext::any_cast<std::vector<Real>>(capAR.at("optionletsPrice"));
                for (Real x : v) capOptPrice.push_back(x);
            }
            if (capAR.count("optionletsAtmForward")) {
                auto v = ext::any_cast<std::vector<Real>>(capAR.at("optionletsAtmForward"));
                for (Real x : v) capOptFwd.push_back(x);
            }
            if (capAR.count("optionletsStdDev")) {
                auto v = ext::any_cast<std::vector<Real>>(capAR.at("optionletsStdDev"));
                for (Real x : v) capOptStd.push_back(x);
            }

            json floorOptPrice = json::array();
            const auto& floorAR = floor->additionalResults();
            if (floorAR.count("optionletsPrice")) {
                auto v = ext::any_cast<std::vector<Real>>(floorAR.at("optionletsPrice"));
                for (Real x : v) floorOptPrice.push_back(x);
            }

            json inp{
                {"engine", names[which]},
                {"strike", K},
                {"vol", vol}
            };
            json exp{
                {"cap_npv", capNPV},
                {"floor_npv", floorNPV},
                {"cap_optionletsPrice", capOptPrice},
                {"floor_optionletsPrice", floorOptPrice},
                {"optionletsAtmForward", capOptFwd},
                {"optionletsStdDev", capOptStd}
            };
            out.addCase(std::string("npv_") + names[which], inp, exp);
        }
    }

    out.write();
    return 0;
}
