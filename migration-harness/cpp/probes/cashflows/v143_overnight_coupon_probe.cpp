// migration-harness/cpp/probes/cashflows/v143_overnight_coupon_probe.cpp
//
// Reference values for the overnight-coupon machinery reworked in C++
// QuantLib v1.43:
//
//   * ql/cashflows/overnightindexedcoupon.{hpp,cpp}
//       - value dates built with Calendar::businessDayList() instead of a
//         daily MakeSchedule, anchored on the *rate-computation* dates
//       - interestDates front/back pinned to the rate-computation dates, so a
//         period end landing on a fixing holiday still accrues to that end
//       - observation shift no longer rewrites interestDates; instead dt_ is
//         measured over the value dates when a lookback is present
//       - new exCouponDate and optional roundingPrecision ctor arguments
//       - new `startDate < endDate` precondition
//   * ql/cashflows/overnightindexedcouponpricer.cpp
//       - determineNumberOfFixings() clamped unconditionally
//       - a single growthFactor() covering both fixed and projected periods
//       - telescopic range now bounded by a start index (partial first period
//         when the first interest date is a fixing holiday) and an end index
//         (lockout and partial last period)
//       - the annualisation denominator is the compounded span itself rather
//         than the coupon's accrued period
//       - explicit guard when the forecast curve cannot cover the coupon
//
// Design notes
// ------------
// The upstream test-suite pins these behaviours against two hand-written
// fixing tables (one of 153 rows). Copying those into the Java port would make
// the reference depend on transcription accuracy as much as on the coupon
// logic. Instead this probe seeds the SOFR series from a closed-form rule of
// the date serial number, which is trivially reproducible in any port:
//
//     fixing(d) = 0.0400 + 0.00001 * (d.serialNumber() % 37)
//
// The modulus makes the series non-constant, so an off-by-one in the fixing
// dates shows up as a rate mismatch rather than cancelling out.
//
// What is pinned per coupon: the full valueDates / interestDates / fixingDates
// lists, dt, indexFixings, rate(), amount(), and accruedAmount() at a spread
// of dates around both period ends. The date lists are the part that actually
// catches a wrong schedule -- a rate or an NPV alone can match while two
// errors cancel. Quantities that legitimately throw (missing curve, curve too
// narrow) are emitted as JSON null so the consuming test can assert that the
// port throws in exactly the same places.
//
// Deliberately covered edge paths:
//   * accrual end on a fixing holiday (Good Friday: a SOFR-calendar holiday
//     that is a Federal Reserve business day), with and without lockout,
//     lookback and observation shift
//   * accrual start on a fixing holiday, and a period spanning one
//   * partially fixed coupons (period straddling the evaluation date)
//   * rate-computation dates decoupled from accrual dates (in-advance coupons)
//   * a rate-computation start date on a fixing holiday
//   * ex-coupon dates before and after the accrual end date
//   * amount rounding
//   * a forecast curve that is absent, too narrow, exactly wide enough, or
//     too narrow but extrapolating
//   * a weekend stub period in an OvernightLeg

#include <ql/version.hpp>

#include <ql/cashflows/cashflows.hpp>
#include <ql/cashflows/overnightindexedcoupon.hpp>
#include <ql/cashflows/overnightindexedcouponpricer.hpp>
#include <ql/cashflows/rateaveraging.hpp>
#include <ql/indexes/ibor/sofr.hpp>
#include <ql/math/rounding.hpp>
#include <ql/optional.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/discountcurve.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/unitedstates.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/schedule.hpp>
#include <ql/utilities/null.hpp>

#include <functional>
#include <string>
#include <vector>

#include "common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// --- deterministic fixing series --------------------------------------------
//
// Closed form over the date serial number so any port can regenerate it
// exactly. The modulus keeps the series non-constant.

Rate syntheticFixing(const Date& d) {
    return 0.0400 + 0.00001 * static_cast<Real>(d.serialNumber() % 37);
}

ext::shared_ptr<Sofr> makeIndex(const Date& today,
                                const Handle<YieldTermStructure>& curve,
                                const Date& fixingsFrom) {
    Settings::instance().evaluationDate() = today;
    auto sofr = ext::make_shared<Sofr>(curve);
    sofr->clearFixings();
    const Calendar cal = sofr->fixingCalendar();
    for (Date d = fixingsFrom; d <= today; ++d) {
        if (cal.isBusinessDay(d))
            sofr->addFixing(d, syntheticFixing(d));
    }
    return sofr;
}

Handle<YieldTermStructure> flatCurve(const Date& today, Rate r) {
    return Handle<YieldTermStructure>(
        ext::make_shared<FlatForward>(today, r, Actual360()));
}

// --- result extraction ------------------------------------------------------

json dateList(const std::vector<Date>& v) {
    json a = json::array();
    for (const auto& d : v)
        a.push_back(d.serialNumber());
    return a;
}

// Emit `null` instead of aborting when a quantity legitimately throws, so the
// consuming test can assert the port throws in the same places.
json orNull(const std::function<Real()>& f) {
    try {
        return json(f());
    } catch (const std::exception&) {
        return json(nullptr);
    }
}

json orNullList(const std::function<std::vector<Rate>()>& f) {
    try {
        return json(f());
    } catch (const std::exception&) {
        return json(nullptr);
    }
}

// Accrual probe dates: both period ends bracketed, plus a midpoint and the
// payment date. Emitted with their serials so the consuming test iterates the
// reference rather than recomputing the list.
std::vector<Date> accrualProbes(const Date& start, const Date& end, const Date& pay) {
    std::vector<Date> v;
    v.push_back(start - 1);
    for (Date::serial_type i = 0; i <= 4; ++i)
        v.push_back(start + i);
    v.push_back(start + (end - start) / 2);
    for (Date::serial_type i = 3; i >= 1; --i)
        v.push_back(end - i);
    v.push_back(end);
    v.push_back(end + 1);
    v.push_back(pay);
    v.push_back(pay + 1);
    return v;
}

json describeCoupon(const ext::shared_ptr<OvernightIndexedCoupon>& c) {
    const std::vector<Date> probes =
        accrualProbes(c->accrualStartDate(), c->accrualEndDate(), c->date());

    json accruals = json::array();
    for (const auto& d : probes) {
        accruals.push_back(json{
            {"dateSerial", d.serialNumber()},
            {"accrued", orNull([&] { return c->accruedAmount(d); })},
        });
    }

    return json{
        {"paymentDateSerial", c->date().serialNumber()},
        {"accrualStartSerial", c->accrualStartDate().serialNumber()},
        {"accrualEndSerial", c->accrualEndDate().serialNumber()},
        {"accrualPeriod", c->accrualPeriod()},
        {"nominal", c->nominal()},
        {"fixingDaysResolved", static_cast<Integer>(c->fixingDays())},
        {"n", static_cast<Integer>(c->valueDates().size() - 1)},
        {"valueDates", dateList(c->valueDates())},
        {"interestDates", dateList(c->interestDates())},
        {"fixingDates", dateList(c->fixingDates())},
        {"dt", c->dt()},
        {"indexFixings", orNullList([&] { return c->indexFixings(); })},
        {"rate", orNull([&] { return c->rate(); })},
        {"amount", orNull([&] { return c->amount(); })},
        {"accruals", accruals},
    };
}

// --- coupon builder ---------------------------------------------------------

struct CouponSpec {
    std::string name;
    Date start, end;
    Natural lookbackDays;   // 0 = none; Null<Natural>() = index default
    bool observationShift;
    Natural lockoutDays;
    bool act365;            // false -> the index day counter (Act/360)
    bool telescopic;
};

const Real kNotional = 10'000'000.0;

ext::shared_ptr<OvernightIndexedCoupon> makeCoupon(const ext::shared_ptr<Sofr>& sofr,
                                                   const CouponSpec& s) {
    const Calendar fedCal = UnitedStates(UnitedStates::FederalReserve);
    const Date payment = fedCal.advance(s.end, 2, Days);
    const DayCounter dc =
        s.act365 ? DayCounter(Actual365Fixed()) : DayCounter(Actual360());
    return ext::make_shared<OvernightIndexedCoupon>(
        payment, kNotional, s.start, s.end, sofr,
        /*gearing*/ 1.0, /*spread*/ 0.0, Date(), Date(), dc,
        s.telescopic, RateAveraging::Compound, s.lookbackDays, s.lockoutDays,
        s.observationShift, /*compoundSpread*/ false);
}

json specInputs(const CouponSpec& s) {
    return json{
        {"startSerial", s.start.serialNumber()},
        {"endSerial", s.end.serialNumber()},
        {"lookbackDays", s.lookbackDays == Null<Natural>()
                             ? json(nullptr)
                             : json(static_cast<Integer>(s.lookbackDays))},
        {"observationShift", s.observationShift},
        {"lockoutDays", static_cast<Integer>(s.lockoutDays)},
        {"dayCounter", s.act365 ? "Actual365Fixed" : "Actual360"},
        {"telescopicValueDates", s.telescopic},
        {"notional", kNotional},
    };
}

} // namespace

int main() {
    ReferenceWriter out("cashflows/v143_overnight_coupon", QL_VERSION,
                        "v143_overnight_coupon_probe");

    // =======================================================================
    // Group A -- schedules and accruals around fixing holidays.
    //
    // Good Friday is the one day that is a holiday in the SOFR fixing calendar
    // but a business day in the Federal Reserve calendar used to roll SOFR
    // swap coupons, so it is the natural probe for the value-date rework.
    // =======================================================================
    {
        const Date today(29, May, 2025);
        const Handle<YieldTermStructure> curve = flatCurve(today, 0.0433);
        auto sofr = makeIndex(today, curve, Date(1, September, 2024));

        const CouponSpec specs[] = {
            // accrual end on Good Friday 2025-04-18
            {"end_on_fixing_holiday", Date(18, October, 2024), Date(18, April, 2025),
             0, false, 0, false, false},
            {"end_on_fixing_holiday_telescopic", Date(18, October, 2024), Date(18, April, 2025),
             0, false, 0, false, true},
            {"end_on_fixing_holiday_obsshift", Date(18, October, 2024), Date(18, April, 2025),
             0, true, 0, false, false},
            {"end_on_fixing_holiday_lockout4", Date(18, October, 2024), Date(18, April, 2025),
             0, false, 4, false, false},
            {"end_on_fixing_holiday_lockout4_telescopic", Date(18, October, 2024),
             Date(18, April, 2025), 0, false, 4, false, true},
            {"end_on_fixing_holiday_lookback5", Date(18, October, 2024), Date(18, April, 2025),
             5, false, 0, false, false},
            {"end_on_fixing_holiday_lookback5_obsshift", Date(18, October, 2024),
             Date(18, April, 2025), 5, true, 0, false, false},
            {"end_on_fixing_holiday_lookback5_lockout4", Date(18, October, 2024),
             Date(18, April, 2025), 5, false, 4, false, false},
            // accrual start on Good Friday 2025-04-18
            {"start_on_fixing_holiday", Date(18, April, 2025), Date(19, May, 2025),
             0, false, 0, false, false},
            {"start_on_fixing_holiday_act365_lookback5_obsshift", Date(18, April, 2025),
             Date(19, May, 2025), 5, true, 0, true, false},
            {"start_on_fixing_holiday_act365", Date(18, April, 2025), Date(19, May, 2025),
             0, false, 0, true, false},
            // lookback window landing on the fixing holiday
            {"lookback5_over_fixing_holiday", Date(25, April, 2025), Date(27, May, 2025),
             5, false, 0, false, false},
            {"lookback5_obsshift_over_fixing_holiday", Date(25, April, 2025), Date(27, May, 2025),
             5, true, 0, false, false},
            // period spanning the fixing holiday
            {"spans_fixing_holiday", Date(14, April, 2025), Date(14, May, 2025),
             0, false, 0, false, false},
            // partially fixed (period straddles the evaluation date)
            {"partially_fixed", Date(14, April, 2025), Date(16, June, 2025),
             0, false, 0, false, false},
            {"partially_fixed_lockout1", Date(14, April, 2025), Date(16, June, 2025),
             0, false, 1, false, false},
            {"partially_fixed_lockout5", Date(14, April, 2025), Date(16, June, 2025),
             0, false, 5, false, false},
            {"partially_fixed_lookback5", Date(23, April, 2025), Date(23, June, 2025),
             5, false, 0, false, false},
            {"partially_fixed_lookback5_obsshift", Date(23, April, 2025), Date(23, June, 2025),
             5, true, 0, false, false},
            {"partially_fixed_lookback5_obsshift_lockout4", Date(23, April, 2025),
             Date(23, June, 2025), 5, true, 4, false, false},
            // fully forward, period starting / ending on Good Friday 2027-03-26
            {"forward_starts_on_holiday", Date(26, March, 2027), Date(28, June, 2027),
             0, false, 0, false, false},
            {"forward_starts_on_holiday_telescopic", Date(26, March, 2027), Date(28, June, 2027),
             0, false, 0, false, true},
            {"forward_ends_on_holiday_lockout1", Date(26, February, 2027), Date(26, March, 2027),
             0, false, 1, false, false},
        };

        for (const auto& s : specs) {
            auto c = makeCoupon(sofr, s);
            json inputs = specInputs(s);
            inputs["todaySerial"] = today.serialNumber();
            inputs["flatForwardRate"] = 0.0433;
            inputs["fixingsFromSerial"] = Date(1, September, 2024).serialNumber();
            out.addCase(s.name, inputs, describeCoupon(c));
        }
    }

    // =======================================================================
    // Group B -- a coupon split at a fixing holiday must compound to the same
    // growth factor as the undivided coupon (upstream
    // testInterestCalculatedAccrualDateFixingHoliday). Pinning the three rates
    // is a stronger statement than pinning the identity they satisfy.
    // =======================================================================
    {
        const Date today(19, April, 2023);
        const Handle<YieldTermStructure> curve = flatCurve(today, 0.0432);
        auto sofr = makeIndex(today, curve, Date(1, September, 2021));
        const Calendar fixingCal = sofr->fixingCalendar();

        struct SplitCase {
            std::string label;
            Date start, end, splitAt;
        };
        const SplitCase cases[] = {
            {"split_fixed", Date(15, October, 2021), Date(17, April, 2023), Date(15, April, 2022)},
            {"split_forward", Date(18, October, 2024), Date(20, April, 2026), Date(18, April, 2025)},
        };

        for (const auto& tc : cases) {
            const Date payEnd = fixingCal.advance(tc.end, 2, Days);
            const Date paySplit = fixingCal.advance(tc.splitAt, 2, Days);

            auto total = ext::make_shared<OvernightIndexedCoupon>(
                payEnd, kNotional, tc.start, tc.end, sofr);
            auto left = ext::make_shared<OvernightIndexedCoupon>(
                paySplit, kNotional, tc.start, tc.splitAt, sofr);
            auto right = ext::make_shared<OvernightIndexedCoupon>(
                payEnd, kNotional, tc.splitAt, tc.end, sofr);

            json inputs{
                {"todaySerial", today.serialNumber()},
                {"flatForwardRate", 0.0432},
                {"fixingsFromSerial", Date(1, September, 2021).serialNumber()},
                {"startSerial", tc.start.serialNumber()},
                {"endSerial", tc.end.serialNumber()},
                {"splitAtSerial", tc.splitAt.serialNumber()},
                {"splitAtIsFixingHoliday", fixingCal.isHoliday(tc.splitAt)},
                {"notional", kNotional},
            };
            out.addCase(tc.label + "_total", inputs, describeCoupon(total));
            out.addCase(tc.label + "_left", inputs, describeCoupon(left));
            out.addCase(tc.label + "_right", inputs, describeCoupon(right));
        }
    }

    // =======================================================================
    // Group C -- amount rounding (new v1.43 roundingPrecision argument).
    // =======================================================================
    {
        const Date today(23, November, 2021);
        const Handle<YieldTermStructure> curve = flatCurve(today, 0.0010);
        auto sofr = makeIndex(today, curve, Date(1, September, 2021));

        const Date start(10, December, 2021), end(10, January, 2022);
        const Real notional = 10'000.0;

        auto unrounded = ext::make_shared<OvernightIndexedCoupon>(
            end, notional, start, end, sofr);
        auto rounded = ext::make_shared<OvernightIndexedCoupon>(
            end, notional, start, end, sofr, 1.0, 0.0, Date(), Date(), DayCounter(),
            /*telescopic*/ false, RateAveraging::Compound, Null<Natural>(), 0,
            /*obsShift*/ false, /*compoundSpread*/ false, Date(), Date(), Date(),
            ext::optional<Integer>(5));

        json inputs{
            {"todaySerial", today.serialNumber()},
            {"flatForwardRate", 0.0010},
            {"fixingsFromSerial", Date(1, September, 2021).serialNumber()},
            {"startSerial", start.serialNumber()},
            {"endSerial", end.serialNumber()},
            {"notional", notional},
            {"roundingPrecision", 5},
        };

        json unroundedJson = describeCoupon(unrounded);
        json roundedJson = describeCoupon(rounded);
        roundedJson["roundedRate"] = ClosestRounding(5)(unrounded->rate());
        roundedJson["unroundedRate"] = unrounded->rate();
        roundedJson["unroundedAmount"] = unrounded->amount();

        out.addCase("amount_rounding_unrounded", inputs, unroundedJson);
        out.addCase("amount_rounding_precision5", inputs, roundedJson);
    }

    // =======================================================================
    // Group D -- the forecast curve must cover the coupon (new v1.43 guard).
    // Four curve configurations: absent, one day too narrow, exactly wide
    // enough, and one day too narrow but extrapolating.
    // =======================================================================
    {
        const Date today(26, March, 2026);
        RelinkableHandle<YieldTermStructure> curve;
        auto sofr = makeIndex(today, curve, Date(1, January, 2026));

        const Date start(31, March, 2026), end(31, March, 2027);
        auto coupon = ext::make_shared<OvernightIndexedCoupon>(
            Date(2, April, 2027), 1.0, start, end, sofr, 1.0, 0.0, Date(), Date(),
            DayCounter(), /*telescopic*/ true, RateAveraging::Compound,
            Null<Natural>(), 0, false, false);

        const auto discountTo = [&](const Date& maturity, bool extrapolate) {
            auto c = ext::make_shared<DiscountCurve>(
                std::vector<Date>{today, maturity},
                std::vector<DiscountFactor>{1.0, 0.9}, Actual360());
            if (extrapolate)
                c->enableExtrapolation();
            return c;
        };

        json result{
            {"valueDates", dateList(coupon->valueDates())},
            {"interestDates", dateList(coupon->interestDates())},
            {"fixingDates", dateList(coupon->fixingDates())},
        };

        // (1) no curve at all
        result["rateWithNoCurve"] = orNull([&] { return coupon->rate(); });

        // (2) curve ending one day before the accrual end
        curve.linkTo(discountTo(end - 1, false));
        result["rateWithNarrowCurve"] = orNull([&] { return coupon->rate(); });

        // (3) curve reaching exactly the accrual end
        curve.linkTo(discountTo(end, false));
        result["rateWithExactCurve"] = orNull([&] { return coupon->rate(); });

        // (4) narrow curve, but extrapolating
        curve.linkTo(discountTo(end - 1, true));
        result["rateWithNarrowExtrapolatingCurve"] = orNull([&] { return coupon->rate(); });

        out.addCase("curve_range_guard",
                    json{{"todaySerial", today.serialNumber()},
                         {"startSerial", start.serialNumber()},
                         {"endSerial", end.serialNumber()},
                         {"paymentSerial", Date(2, April, 2027).serialNumber()},
                         {"notional", 1.0},
                         {"telescopicValueDates", true},
                         {"curveDiscountFactorAtMaturity", 0.9}},
                    result);
    }

    // =======================================================================
    // Group E -- ex-coupon accrued amounts either side of the accrual end
    // (new v1.43 exCouponDate ctor argument).
    // =======================================================================
    {
        const Date today(26, March, 2026);
        const Handle<YieldTermStructure> curve = flatCurve(today, 0.04);
        auto sofr = makeIndex(today, curve, Date(1, January, 2026));

        const Date start(31, March, 2026), end(31, March, 2027), pay(2, April, 2027);

        struct ExCase {
            std::string name;
            Date exCouponDate;
            std::vector<Date> probes;
        };
        const ExCase cases[] = {
            {"excoupon_before_accrual_end",
             Date(27, March, 2027),
             {Date(26, March, 2027), Date(27, March, 2027), Date(30, March, 2027),
              Date(31, March, 2027), Date(1, April, 2027), Date(2, April, 2027),
              Date(3, April, 2027)}},
            {"excoupon_after_accrual_end",
             Date(1, April, 2027),
             {Date(31, March, 2027), Date(1, April, 2027), Date(2, April, 2027),
              Date(3, April, 2027)}},
        };

        for (const auto& tc : cases) {
            auto coupon = ext::make_shared<OvernightIndexedCoupon>(
                pay, 100.0, start, end, sofr, 1.0, 0.0, Date(), Date(), DayCounter(),
                /*telescopic*/ true, RateAveraging::Compound, Null<Natural>(), 0,
                false, false, Date(), Date(), tc.exCouponDate);

            json probes = json::array();
            for (const auto& d : tc.probes) {
                probes.push_back(json{
                    {"dateSerial", d.serialNumber()},
                    {"tradingExCoupon", coupon->tradingExCoupon(d)},
                    {"accruedPeriod", coupon->accruedPeriod(d)},
                    {"accrued", orNull([&] { return coupon->accruedAmount(d); })},
                });
            }

            json result = describeCoupon(coupon);
            result["exCouponDateSerial"] = coupon->exCouponDate().serialNumber();
            result["exCouponProbes"] = probes;

            out.addCase(tc.name,
                        json{{"todaySerial", today.serialNumber()},
                             {"flatForwardRate", 0.04},
                             {"startSerial", start.serialNumber()},
                             {"endSerial", end.serialNumber()},
                             {"paymentSerial", pay.serialNumber()},
                             {"exCouponSerial", tc.exCouponDate.serialNumber()},
                             {"notional", 100.0},
                             {"telescopicValueDates", true}},
                        result);
        }
    }

    // =======================================================================
    // Group F -- in-advance compounding: rate-computation dates decoupled from
    // the accrual dates. The in-advance coupon must reproduce the schedule and
    // rate of the in-arrears coupon written on the computation period.
    // =======================================================================
    {
        const Date today(21, April, 2026);
        const Handle<YieldTermStructure> curve = flatCurve(today, 0.04);
        auto sofr = makeIndex(today, curve, Date(1, January, 2026));

        const Date accrualStart(23, April, 2027), accrualEnd(23, April, 2028);
        const Date rateStart(23, April, 2026), rateEnd(23, April, 2027);

        auto advance = ext::make_shared<OvernightIndexedCoupon>(
            Date(25, April, 2028), kNotional, accrualStart, accrualEnd, sofr, 1.0, 0.0,
            Date(), Date(), DayCounter(), /*telescopic*/ true, RateAveraging::Compound,
            Null<Natural>(), 0, false, false, rateStart, rateEnd);
        auto arrears = ext::make_shared<OvernightIndexedCoupon>(
            rateEnd, kNotional, rateStart, rateEnd, sofr, 1.0, 0.0, Date(), Date(),
            DayCounter(), /*telescopic*/ true, RateAveraging::Compound,
            Null<Natural>(), 0, false, false);

        json inputs{
            {"todaySerial", today.serialNumber()},
            {"flatForwardRate", 0.04},
            {"accrualStartSerial", accrualStart.serialNumber()},
            {"accrualEndSerial", accrualEnd.serialNumber()},
            {"rateComputationStartSerial", rateStart.serialNumber()},
            {"rateComputationEndSerial", rateEnd.serialNumber()},
            {"paymentSerial", Date(25, April, 2028).serialNumber()},
            {"notional", kNotional},
            {"telescopicValueDates", true},
        };

        json advanceJson = describeCoupon(advance);
        advanceJson["rateComputationStartSerial"] =
            advance->rateComputationStartDate().serialNumber();
        advanceJson["rateComputationEndSerial"] =
            advance->rateComputationEndDate().serialNumber();

        out.addCase("in_advance", inputs, advanceJson);
        out.addCase("in_arrears_reference", inputs, describeCoupon(arrears));
    }

    // =======================================================================
    // Group G -- rate-computation start date on a fixing holiday.
    // =======================================================================
    {
        const Date today(16, April, 2025);
        const Handle<YieldTermStructure> curve = flatCurve(today, 0.04);
        auto sofr = makeIndex(today, curve, Date(1, January, 2025));

        const Date accrualStart(21, April, 2025), accrualEnd(21, April, 2026);
        const Date rateStart(18, April, 2025), rateEnd(20, April, 2026);

        auto coupon = ext::make_shared<OvernightIndexedCoupon>(
            Date(23, April, 2026), kNotional, accrualStart, accrualEnd, sofr, 1.0, 0.0,
            Date(), Date(), DayCounter(), /*telescopic*/ true, RateAveraging::Compound,
            Null<Natural>(), 0, false, false, rateStart, rateEnd);

        json result = describeCoupon(coupon);
        result["rateComputationStartIsFixingHoliday"] =
            sofr->fixingCalendar().isHoliday(coupon->rateComputationStartDate());
        result["fixingOn2025_04_17"] = sofr->fixing(Date(17, April, 2025));
        result["fixingOn2025_04_21"] = sofr->fixing(Date(21, April, 2025));

        out.addCase("rate_computation_start_fixing_holiday",
                    json{{"todaySerial", today.serialNumber()},
                         {"flatForwardRate", 0.04},
                         {"accrualStartSerial", accrualStart.serialNumber()},
                         {"accrualEndSerial", accrualEnd.serialNumber()},
                         {"rateComputationStartSerial", rateStart.serialNumber()},
                         {"rateComputationEndSerial", rateEnd.serialNumber()},
                         {"paymentSerial", Date(23, April, 2026).serialNumber()},
                         {"notional", kNotional},
                         {"telescopicValueDates", true}},
                    result);
    }

    // =======================================================================
    // Group H -- OvernightLeg over a schedule with a weekend stub. The middle
    // date is a Saturday, so the first coupon is a single business day and the
    // second spans the weekend.
    // =======================================================================
    {
        const Date today(16, April, 2025);
        const Handle<YieldTermStructure> curve = flatCurve(today, 0.04);
        auto sofr = makeIndex(today, curve, Date(1, January, 2025));

        const Schedule schedule({
            Date(27, March, 2026), // Friday
            Date(28, March, 2026), // Saturday
            Date(30, March, 2026), // Monday
        });
        const Leg leg = OvernightLeg(schedule, sofr).withNotionals(kNotional);

        const auto discount = ext::make_shared<FlatForward>(today, 0.0015, Actual360());

        json coupons = json::array();
        Real npv = 0.0;
        for (const auto& cf : leg) {
            auto c = ext::dynamic_pointer_cast<OvernightIndexedCoupon>(cf);
            QL_REQUIRE(c, "unexpected cashflow type in overnight leg");
            coupons.push_back(describeCoupon(c));
            npv += cf->amount() * discount->discount(cf->date());
        }

        json legAccruals = json::array();
        for (Date d(27, March, 2026); d <= Date(31, March, 2026); ++d) {
            legAccruals.push_back(json{
                {"dateSerial", d.serialNumber()},
                {"accrued", orNull([&] {
                     return CashFlows::accruedAmount(leg, true, d);
                 })},
            });
        }

        out.addCase("overnight_leg_weekend_stub",
                    json{{"todaySerial", today.serialNumber()},
                         {"flatForwardRate", 0.04},
                         {"discountRate", 0.0015},
                         {"scheduleSerials", json::array({
                              Date(27, March, 2026).serialNumber(),
                              Date(28, March, 2026).serialNumber(),
                              Date(30, March, 2026).serialNumber()})},
                         {"notional", kNotional}},
                    json{{"legSize", static_cast<Integer>(leg.size())},
                         {"coupons", coupons},
                         {"legAccruedAmounts", legAccruals},
                         {"legNpv", npv},
                         {"oneDayInterest",
                          sofr->fixing(Date(27, March, 2026)) / 360.0 * kNotional}});
    }

    out.write();
    return 0;
}
