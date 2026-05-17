// migration-harness/cpp/probes/experimental/callablebonds_probe.cpp
// Reference values for QuantLib v1.42.1 CallableBondTests
// (test-suite/callablebonds.cpp). Phase 5e.5b-CFC-d-61.
//
// Probes:
//  * testInterplay  — case 1/2/3/4 expected analytic value (discount-factor
//                     formula) + tree-engine settlement value.
//  * testConsistency — plain / callable / puttable clean prices.
//  * testObservability — NPV before / after curve-quote bump.
//  * testDegenerate — plain ZeroCouponBond / FixedRateBond clean prices and
//                     their callable counterparts (no callability and
//                     out-of-the-money callability cases).
//  * testCached     — 3 stored callable / puttable / mixed prices.
//  * testBlackEngine — cached price 74.54521578.
//  * testBlackEngineDeepInTheMoney — analytic expected + tree-computed price.
//  * testImpliedVol — dirty/clean target-price implied vols (round trip).
//  * testCallableFixedRateBondWithArbitrarySchedule — clean-price smoke value.
//  * testSnappingExerciseDate2ClosestCouponDate — NPV of fixed vs callable
//    at the sweep call dates (i in [-10..10] business-day-filtered).
//
// All cases run under the SAME evaluationDate / fixture conventions as the
// matching C++ test so the Java port can pin them directly.

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/experimental/callablebonds/callablebond.hpp>
#include <ql/experimental/callablebonds/treecallablebondengine.hpp>
#include <ql/experimental/callablebonds/blackcallablebondengine.hpp>
#include <ql/instruments/bonds/zerocouponbond.hpp>
#include <ql/instruments/bonds/fixedratebond.hpp>
#include <ql/pricingengines/bond/discountingbondengine.hpp>
#include <ql/time/daycounters/thirty360.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/calendars/unitedstates.hpp>
#include <ql/time/schedule.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/models/shortrate/onefactormodels/hullwhite.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/shared_ptr.hpp>

using namespace jqml_harness;
using namespace QuantLib;

namespace {

struct Globals {
    Date today, settlement;
    Calendar calendar;
    DayCounter dayCounter;
    BusinessDayConvention rollingConvention;

    RelinkableHandle<YieldTermStructure> termStructure;
    RelinkableHandle<ShortRateModel> model;

    Date issueDate() const { return calendar.adjust(today - 100*Days); }
    Date maturityDate() const { return calendar.advance(issueDate(), 10, Years); }

    std::vector<Date> evenYears() const {
        std::vector<Date> dates;
        for (Size i = 2; i < 10; i += 2)
            dates.push_back(calendar.advance(issueDate(), i, Years));
        return dates;
    }
    std::vector<Date> oddYears() const {
        std::vector<Date> dates;
        for (Size i = 1; i < 10; i += 2)
            dates.push_back(calendar.advance(issueDate(), i, Years));
        return dates;
    }

    template <class R>
    ext::shared_ptr<YieldTermStructure> makeFlatCurve(const R& r) const {
        return ext::shared_ptr<YieldTermStructure>(
                new FlatForward(settlement, r, dayCounter));
    }

    Globals(Date pinned_today) {
        calendar = TARGET();
        dayCounter = Actual365Fixed();
        rollingConvention = ModifiedFollowing;
        today = pinned_today;
        Settings::instance().evaluationDate() = today;
        settlement = calendar.advance(today, 2, Days);
    }
};

} // namespace

int main() {
    ReferenceWriter out("experimental/callablebonds",
                        QL_VERSION,
                        "callablebonds_probe");

    // --- testInterplay (today pinned to 3-June-2004 for repeatability) ---
    {
        Globals vars(Date(3, June, 2004));
        vars.termStructure.linkTo(vars.makeFlatCurve(0.03));
        vars.model.linkTo(ext::make_shared<HullWhite>(vars.termStructure));
        Size timeSteps = 240;
        auto engine = ext::make_shared<TreeCallableZeroCouponBondEngine>(
                *(vars.model), timeSteps, vars.termStructure);

        // Case 1
        CallabilitySchedule cb1;
        cb1.push_back(ext::make_shared<Callability>(
            Bond::Price(100.0, Bond::Price::Clean), Callability::Call,
            vars.calendar.advance(vars.issueDate(), 4, Years)));
        cb1.push_back(ext::make_shared<Callability>(
            Bond::Price(1000.0, Bond::Price::Clean), Callability::Put,
            vars.calendar.advance(vars.issueDate(), 6, Years)));
        CallableZeroCouponBond bond1(3, 100.0, vars.calendar, vars.maturityDate(),
                                     Thirty360(Thirty360::BondBasis),
                                     vars.rollingConvention, 100.0,
                                     vars.issueDate(), cb1);
        bond1.setPricingEngine(engine);
        Real expected1 = cb1[0]->price().amount()
            * vars.termStructure->discount(cb1[0]->date())
            / vars.termStructure->discount(bond1.settlementDate());
        Real npv1 = bond1.settlementValue();

        // Case 2 (add a later callability — should not change result)
        CallabilitySchedule cb2 = cb1;
        cb2.push_back(ext::make_shared<Callability>(
            Bond::Price(100.0, Bond::Price::Clean), Callability::Call,
            vars.calendar.advance(vars.issueDate(), 8, Years)));
        CallableZeroCouponBond bond2(3, 100.0, vars.calendar, vars.maturityDate(),
                                     Thirty360(Thirty360::BondBasis),
                                     vars.rollingConvention, 100.0,
                                     vars.issueDate(), cb2);
        bond2.setPricingEngine(engine);
        Real npv2 = bond2.settlementValue();

        // Case 3 (put then call)
        CallabilitySchedule cb3;
        cb3.push_back(ext::make_shared<Callability>(
            Bond::Price(100.0, Bond::Price::Clean), Callability::Put,
            vars.calendar.advance(vars.issueDate(), 4, Years)));
        cb3.push_back(ext::make_shared<Callability>(
            Bond::Price(10.0, Bond::Price::Clean), Callability::Call,
            vars.calendar.advance(vars.issueDate(), 6, Years)));
        CallableZeroCouponBond bond3(3, 100.0, vars.calendar, vars.maturityDate(),
                                     Thirty360(Thirty360::BondBasis),
                                     vars.rollingConvention, 100.0,
                                     vars.issueDate(), cb3);
        bond3.setPricingEngine(engine);
        Real expected3 = cb3[0]->price().amount()
            * vars.termStructure->discount(cb3[0]->date())
            / vars.termStructure->discount(bond3.settlementDate());
        Real npv3 = bond3.settlementValue();

        // Case 4 (add a later put — should not change)
        CallabilitySchedule cb4 = cb3;
        cb4.push_back(ext::make_shared<Callability>(
            Bond::Price(100.0, Bond::Price::Clean), Callability::Put,
            vars.calendar.advance(vars.issueDate(), 8, Years)));
        CallableZeroCouponBond bond4(3, 100.0, vars.calendar, vars.maturityDate(),
                                     Thirty360(Thirty360::BondBasis),
                                     vars.rollingConvention, 100.0,
                                     vars.issueDate(), cb4);
        bond4.setPricingEngine(engine);
        Real npv4 = bond4.settlementValue();

        json inp{
            {"today_serial", vars.today.serialNumber()},
            {"flatRate", 0.03},
            {"timeSteps", 240}
        };
        json exp{
            {"case1_expected_settlement", expected1},
            {"case1_npv_settlement", npv1},
            {"case2_npv_settlement", npv2},
            {"case3_expected_settlement", expected3},
            {"case3_npv_settlement", npv3},
            {"case4_npv_settlement", npv4}
        };
        out.addCase("testInterplay", inp, exp);
    }

    // --- testConsistency ---
    {
        Globals vars(Date(3, June, 2004));
        vars.termStructure.linkTo(vars.makeFlatCurve(0.032));
        vars.model.linkTo(ext::make_shared<HullWhite>(vars.termStructure));

        Schedule schedule = MakeSchedule()
            .from(vars.issueDate()).to(vars.maturityDate())
            .withCalendar(vars.calendar).withFrequency(Semiannual)
            .withConvention(vars.rollingConvention)
            .withRule(DateGeneration::Backward);

        std::vector<Rate> coupons(1, 0.05);
        FixedRateBond plain(3, 100.0, schedule, coupons,
                            Thirty360(Thirty360::BondBasis));
        plain.setPricingEngine(
            ext::make_shared<DiscountingBondEngine>(vars.termStructure));

        CallabilitySchedule cbs, pbs;
        for (auto& d : vars.evenYears())
            cbs.push_back(ext::make_shared<Callability>(
                Bond::Price(110.0, Bond::Price::Clean), Callability::Call, d));
        for (auto& d : vars.oddYears())
            pbs.push_back(ext::make_shared<Callability>(
                Bond::Price(90.0, Bond::Price::Clean), Callability::Put, d));

        Size timeSteps = 240;
        auto engine = ext::make_shared<TreeCallableFixedRateBondEngine>(
                *(vars.model), timeSteps, vars.termStructure);

        CallableFixedRateBond callable(3, 100.0, schedule, coupons,
                                       Thirty360(Thirty360::BondBasis),
                                       vars.rollingConvention, 100.0,
                                       vars.issueDate(), cbs);
        callable.setPricingEngine(engine);

        CallableFixedRateBond puttable(3, 100.0, schedule, coupons,
                                       Thirty360(Thirty360::BondBasis),
                                       vars.rollingConvention, 100.0,
                                       vars.issueDate(), pbs);
        puttable.setPricingEngine(engine);

        json inp{
            {"today_serial", vars.today.serialNumber()},
            {"flatRate", 0.032},
            {"timeSteps", 240}
        };
        json exp{
            {"plain_cleanPrice", plain.cleanPrice()},
            {"callable_cleanPrice", callable.cleanPrice()},
            {"puttable_cleanPrice", puttable.cleanPrice()}
        };
        out.addCase("testConsistency", inp, exp);
    }

    // --- testObservability ---
    {
        Globals vars(Date(3, June, 2004));
        auto observable = ext::make_shared<SimpleQuote>(0.03);
        Handle<Quote> h(observable);
        vars.termStructure.linkTo(vars.makeFlatCurve(h));
        vars.model.linkTo(ext::make_shared<HullWhite>(vars.termStructure));

        Schedule schedule = MakeSchedule()
            .from(vars.issueDate()).to(vars.maturityDate())
            .withCalendar(vars.calendar).withFrequency(Semiannual)
            .withConvention(vars.rollingConvention)
            .withRule(DateGeneration::Backward);

        CallabilitySchedule cbs;
        for (auto& d : vars.evenYears())
            cbs.push_back(ext::make_shared<Callability>(
                Bond::Price(110.0, Bond::Price::Clean), Callability::Call, d));
        for (auto& d : vars.oddYears())
            cbs.push_back(ext::make_shared<Callability>(
                Bond::Price(90.0, Bond::Price::Clean), Callability::Put, d));

        CallableZeroCouponBond bond(3, 100.0, vars.calendar, vars.maturityDate(),
                                    Thirty360(Thirty360::BondBasis),
                                    vars.rollingConvention, 100.0,
                                    vars.issueDate(), cbs);
        Size timeSteps = 240;
        bond.setPricingEngine(
            ext::make_shared<TreeCallableFixedRateBondEngine>(
                *(vars.model), timeSteps, vars.termStructure));
        Real npv_at_3pct = bond.NPV();
        observable->setValue(0.04);
        Real npv_at_4pct = bond.NPV();

        json inp{{"today_serial", vars.today.serialNumber()}, {"timeSteps", 240}};
        json exp{
            {"npv_at_0.03", npv_at_3pct},
            {"npv_at_0.04", npv_at_4pct}
        };
        out.addCase("testObservability", inp, exp);
    }

    // --- testDegenerate ---
    {
        Globals vars(Date(3, June, 2004));
        vars.termStructure.linkTo(vars.makeFlatCurve(0.034));
        vars.model.linkTo(ext::make_shared<HullWhite>(vars.termStructure));

        Schedule schedule = MakeSchedule()
            .from(vars.issueDate()).to(vars.maturityDate())
            .withCalendar(vars.calendar).withFrequency(Semiannual)
            .withConvention(vars.rollingConvention)
            .withRule(DateGeneration::Backward);
        std::vector<Rate> coupons(1, 0.05);

        ZeroCouponBond zcb(3, vars.calendar, 100.0, vars.maturityDate(),
                           vars.rollingConvention);
        FixedRateBond frb(3, 100.0, schedule, coupons,
                          Thirty360(Thirty360::BondBasis));
        auto disc = ext::make_shared<DiscountingBondEngine>(vars.termStructure);
        zcb.setPricingEngine(disc);
        frb.setPricingEngine(disc);

        Size timeSteps = 240;
        auto tree = ext::make_shared<TreeCallableFixedRateBondEngine>(
                *(vars.model), timeSteps, vars.termStructure);

        // empty callability
        CallabilitySchedule empty;
        CallableZeroCouponBond czb_empty(3, 100.0, vars.calendar, vars.maturityDate(),
                                         Thirty360(Thirty360::BondBasis),
                                         vars.rollingConvention, 100.0,
                                         vars.issueDate(), empty);
        CallableFixedRateBond cfb_empty(3, 100.0, schedule, coupons,
                                        Thirty360(Thirty360::BondBasis),
                                        vars.rollingConvention, 100.0,
                                        vars.issueDate(), empty);
        czb_empty.setPricingEngine(tree);
        cfb_empty.setPricingEngine(tree);

        // out-of-the-money
        CallabilitySchedule oom;
        for (auto& d : vars.evenYears())
            oom.push_back(ext::make_shared<Callability>(
                Bond::Price(10000.0, Bond::Price::Clean), Callability::Call, d));
        for (auto& d : vars.oddYears())
            oom.push_back(ext::make_shared<Callability>(
                Bond::Price(0.0, Bond::Price::Clean), Callability::Put, d));

        CallableZeroCouponBond czb_oom(3, 100.0, vars.calendar, vars.maturityDate(),
                                       Thirty360(Thirty360::BondBasis),
                                       vars.rollingConvention, 100.0,
                                       vars.issueDate(), oom);
        CallableFixedRateBond cfb_oom(3, 100.0, schedule, coupons,
                                      Thirty360(Thirty360::BondBasis),
                                      vars.rollingConvention, 100.0,
                                      vars.issueDate(), oom);
        czb_oom.setPricingEngine(tree);
        cfb_oom.setPricingEngine(tree);

        json inp{{"today_serial", vars.today.serialNumber()}, {"timeSteps", 240}};
        json exp{
            {"zero_cleanPrice", zcb.cleanPrice()},
            {"fixed_cleanPrice", frb.cleanPrice()},
            {"czb_empty_cleanPrice", czb_empty.cleanPrice()},
            {"cfb_empty_cleanPrice", cfb_empty.cleanPrice()},
            {"czb_oom_cleanPrice", czb_oom.cleanPrice()},
            {"cfb_oom_cleanPrice", cfb_oom.cleanPrice()}
        };
        out.addCase("testDegenerate", inp, exp);
    }

    // --- testCached ---
    {
        Globals vars(Date(3, June, 2004));
        vars.termStructure.linkTo(vars.makeFlatCurve(0.032));
        vars.model.linkTo(ext::make_shared<HullWhite>(vars.termStructure));

        Schedule schedule = MakeSchedule()
            .from(vars.issueDate()).to(vars.maturityDate())
            .withCalendar(vars.calendar).withFrequency(Semiannual)
            .withConvention(vars.rollingConvention)
            .withRule(DateGeneration::Backward);

        std::vector<Rate> coupons(1, 0.05);
        CallabilitySchedule cbs, pbs, all;
        for (auto& d : vars.evenYears()) {
            auto e = ext::make_shared<Callability>(
                Bond::Price(110.0, Bond::Price::Clean), Callability::Call, d);
            cbs.push_back(e); all.push_back(e);
        }
        for (auto& d : vars.oddYears()) {
            auto e = ext::make_shared<Callability>(
                Bond::Price(100.0, Bond::Price::Clean), Callability::Put, d);
            pbs.push_back(e); all.push_back(e);
        }

        Size timeSteps = 240;
        auto engine = ext::make_shared<TreeCallableFixedRateBondEngine>(
                *(vars.model), timeSteps, vars.termStructure);

        CallableFixedRateBond b1(3, 10000.0, schedule, coupons,
                                 Thirty360(Thirty360::BondBasis),
                                 vars.rollingConvention, 100.0,
                                 vars.issueDate(), cbs);
        CallableFixedRateBond b2(3, 10000.0, schedule, coupons,
                                 Thirty360(Thirty360::BondBasis),
                                 vars.rollingConvention, 100.0,
                                 vars.issueDate(), pbs);
        CallableFixedRateBond b3(3, 10000.0, schedule, coupons,
                                 Thirty360(Thirty360::BondBasis),
                                 vars.rollingConvention, 100.0,
                                 vars.issueDate(), all);
        b1.setPricingEngine(engine);
        b2.setPricingEngine(engine);
        b3.setPricingEngine(engine);

        json inp{{"today_serial", vars.today.serialNumber()},
                 {"flatRate", 0.032}, {"timeSteps", 240}};
        json exp{
            {"callable_cleanPrice", b1.cleanPrice()},
            {"puttable_cleanPrice", b2.cleanPrice()},
            {"mixed_cleanPrice", b3.cleanPrice()}
        };
        out.addCase("testCached", inp, exp);
    }

    // --- testBlackEngine ---
    {
        Globals vars(Date(20, September, 2022));
        vars.termStructure.linkTo(vars.makeFlatCurve(0.03));
        CallabilitySchedule cbs;
        cbs.push_back(ext::make_shared<Callability>(
            Bond::Price(100.0, Bond::Price::Clean), Callability::Call,
            vars.calendar.advance(vars.issueDate(), 4, Years)));
        CallableZeroCouponBond bond(3, 10000.0, vars.calendar, vars.maturityDate(),
                                    Thirty360(Thirty360::BondBasis),
                                    vars.rollingConvention, 100.0,
                                    vars.issueDate(), cbs);
        bond.setPricingEngine(ext::make_shared<BlackCallableZeroCouponBondEngine>(
            Handle<Quote>(ext::make_shared<SimpleQuote>(0.3)), vars.termStructure));
        json inp{{"today_serial", vars.today.serialNumber()},
                 {"flatRate", 0.03}, {"vol", 0.3}};
        json exp{{"cleanPrice", bond.cleanPrice()}};
        out.addCase("testBlackEngine", inp, exp);
    }

    // --- testImpliedVol ---
    {
        Globals vars(Date(3, June, 2004));
        vars.termStructure.linkTo(vars.makeFlatCurve(0.03));

        Schedule schedule = MakeSchedule()
            .from(vars.issueDate()).to(vars.maturityDate())
            .withCalendar(vars.calendar).withFrequency(Semiannual)
            .withConvention(vars.rollingConvention)
            .withRule(DateGeneration::Backward);
        std::vector<Rate> coupons = { 0.01 };

        CallabilitySchedule cbs;
        cbs.push_back(ext::make_shared<Callability>(
            Bond::Price(100.0, Bond::Price::Clean), Callability::Call,
            schedule.at(8)));

        CallableFixedRateBond bond(3, 10000.0, schedule, coupons,
                                   Thirty360(Thirty360::BondBasis),
                                   vars.rollingConvention, 100.0,
                                   vars.issueDate(), cbs);

        auto targetPriceDirty = Bond::Price(78.50, Bond::Price::Dirty);
        Real volDirty = bond.impliedVolatility(targetPriceDirty,
                            vars.termStructure, 1e-8, 200, 1e-4, 1.0);
        bond.setPricingEngine(ext::make_shared<BlackCallableFixedRateBondEngine>(
            Handle<Quote>(ext::make_shared<SimpleQuote>(volDirty)),
            vars.termStructure));
        Real dirtyAfter = bond.dirtyPrice();

        auto targetPriceClean = Bond::Price(78.50, Bond::Price::Clean);
        Real volClean = bond.impliedVolatility(targetPriceClean,
                            vars.termStructure, 1e-8, 200, 1e-4, 1.0);
        bond.setPricingEngine(ext::make_shared<BlackCallableFixedRateBondEngine>(
            Handle<Quote>(ext::make_shared<SimpleQuote>(volClean)),
            vars.termStructure));
        Real cleanAfter = bond.cleanPrice();

        json inp{{"today_serial", vars.today.serialNumber()},
                 {"flatRate", 0.03}, {"targetPrice", 78.50}};
        json exp{
            {"vol_for_dirty_target", volDirty},
            {"dirty_after", dirtyAfter},
            {"vol_for_clean_target", volClean},
            {"clean_after", cleanAfter}
        };
        out.addCase("testImpliedVol", inp, exp);
    }

    // --- testBlackEngineDeepInTheMoney ---
    {
        Globals vars(Date(20, September, 2022));
        vars.termStructure.linkTo(vars.makeFlatCurve(0.05));

        Schedule schedule = MakeSchedule()
            .from(vars.issueDate()).to(vars.maturityDate())
            .withCalendar(vars.calendar).withFrequency(Semiannual)
            .withConvention(vars.rollingConvention)
            .withRule(DateGeneration::Backward);
        std::vector<Rate> coupons = { 0.0 };

        Date callDate = schedule.at(6);
        CallabilitySchedule cbs;
        cbs.push_back(ext::make_shared<Callability>(
            Bond::Price(50.0, Bond::Price::Clean), Callability::Call, callDate));
        CallableFixedRateBond bond(3, 10000.0, schedule, coupons,
                                   Thirty360(Thirty360::BondBasis),
                                   vars.rollingConvention, 100.0,
                                   vars.issueDate(), cbs);
        Volatility vol = 1e-10;
        bond.setPricingEngine(ext::make_shared<BlackCallableFixedRateBondEngine>(
            Handle<Quote>(ext::make_shared<SimpleQuote>(vol)), vars.termStructure));
        Real expected = 50.0 * vars.termStructure->discount(callDate)
                              / vars.termStructure->discount(bond.settlementDate());
        Real calculated = bond.cleanPrice();
        json inp{{"today_serial", vars.today.serialNumber()},
                 {"flatRate", 0.05}, {"vol", vol}, {"strike", 50.0}};
        json exp{
            {"expected", expected},
            {"calculated_cleanPrice", calculated},
            {"settlement_serial", bond.settlementDate().serialNumber()},
            {"callDate_serial", callDate.serialNumber()}
        };
        out.addCase("testBlackEngineDeepInTheMoney", inp, exp);
    }

    // --- testCallableFixedRateBondWithArbitrarySchedule ---
    {
        Globals vars(Date(10, January, 2020));
        vars.termStructure.linkTo(vars.makeFlatCurve(0.03));
        vars.model.linkTo(ext::make_shared<HullWhite>(vars.termStructure));

        Size timeSteps = 240;
        auto engine = ext::make_shared<TreeCallableFixedRateBondEngine>(
            *(vars.model), timeSteps, vars.termStructure);

        std::vector<Date> dates(4);
        dates[0] = Date(20, February, 2020);
        dates[1] = Date(15, August, 2020);
        dates[2] = Date(25, September, 2021);
        dates[3] = Date(27, January, 2022);
        Schedule schedule(dates, vars.calendar, Unadjusted);

        CallabilitySchedule cbs;
        cbs.push_back(ext::make_shared<Callability>(
            Bond::Price(100.0, Bond::Price::Clean), Callability::Call, dates[2]));
        std::vector<Rate> coupons(1, 0.06);

        CallableFixedRateBond bond(2, 100.0, schedule, coupons, vars.dayCounter,
                                   vars.rollingConvention, 100.0,
                                   vars.issueDate(), cbs);
        bond.setPricingEngine(engine);
        Real cp = bond.cleanPrice();
        json inp{{"today_serial", vars.today.serialNumber()}, {"timeSteps", 240}};
        json exp{{"cleanPrice", cp}};
        out.addCase("testCallableFixedRateBondWithArbitrarySchedule", inp, exp);
    }

    // --- testSnappingExerciseDate2ClosestCouponDate (no OAS, just NPV) ---
    {
        Date today(18, May, 2021);
        Settings::instance().evaluationDate() = today;
        UnitedStates calendar(UnitedStates::FederalReserve);
        DayCounter dc = Thirty360(Thirty360::USA);
        Frequency frequency = Semiannual;
        Handle<YieldTermStructure> termStructure(
            ext::make_shared<FlatForward>(today, 0.02, Actual365Fixed()));

        Date initialCallDate(14, February, 2022);
        json npv_pairs = json::array();
        for (int i = -10; i < 11; i++) {
            Date callDate = initialCallDate + i * Days;
            if (!calendar.isBusinessDay(callDate)) continue;

            Natural settlementDays = 2;
            Date settlementDate(20, May, 2021);
            Real coupon = 0.05;
            Real faceAmount = 100.0;
            Real redemption = faceAmount;
            Date maturityDate(14, February, 2026);
            Date issueDate = settlementDate - 2 * 366 * Days;
            Schedule schedule = MakeSchedule()
                .from(issueDate).to(maturityDate).withFrequency(frequency)
                .withCalendar(calendar).withConvention(Unadjusted)
                .withTerminationDateConvention(Unadjusted)
                .backwards().endOfMonth(false);
            std::vector<Rate> coupons(schedule.size() - 1, coupon);

            CallabilitySchedule cbs;
            cbs.push_back(ext::make_shared<Callability>(
                Bond::Price(faceAmount, Bond::Price::Clean),
                Callability::Type::Call, callDate));

            auto callableBond = ext::make_shared<CallableFixedRateBond>(
                settlementDays, faceAmount, schedule, coupons, dc,
                BusinessDayConvention::Following, redemption, issueDate, cbs);
            auto model = ext::make_shared<HullWhite>(termStructure, 1e-12, 0.003);
            auto treeEngine = ext::make_shared<TreeCallableFixedRateBondEngine>(model, 40);
            callableBond->setPricingEngine(treeEngine);

            auto frbSchedule = schedule.until(callDate);
            std::vector<Rate> frbCoupons(schedule.size() - 1, coupon);
            auto fixedBond = ext::make_shared<FixedRateBond>(
                settlementDays, faceAmount, frbSchedule, frbCoupons, dc,
                BusinessDayConvention::Following, redemption, issueDate);
            fixedBond->setPricingEngine(
                ext::make_shared<DiscountingBondEngine>(termStructure));

            Real npvCallable = callableBond->NPV();
            Real npvFixed = fixedBond->NPV();
            npv_pairs.push_back({
                {"i", i},
                {"callDate_serial", callDate.serialNumber()},
                {"npv_callable", npvCallable},
                {"npv_fixed", npvFixed},
                {"diff", std::fabs(npvCallable - npvFixed)}
            });
        }
        json inp{{"today_serial", today.serialNumber()}};
        json exp{{"npv_pairs", npv_pairs}};
        out.addCase("testSnappingExerciseDate2ClosestCouponDate", inp, exp);
    }

    out.write();
    std::printf("callablebonds_probe: wrote references/experimental/callablebonds.json\n");
    return 0;
}
