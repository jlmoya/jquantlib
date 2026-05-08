// migration-harness/cpp/probes/methods/finitedifferences/step_conditions_probe.cpp
// Reference values for FdmAmericanStepCondition, FdmBermudanStepCondition,
// and FdmDividendHandler vs QuantLib C++ v1.42.1.

#include <ql/version.hpp>
#include <ql/time/date.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/methods/finitedifferences/operators/fdmlinearoplayout.hpp>
#include <ql/methods/finitedifferences/meshers/fdmmeshercomposite.hpp>
#include <ql/methods/finitedifferences/meshers/uniform1dmesher.hpp>
#include <ql/methods/finitedifferences/utilities/fdminnervaluecalculator.hpp>
#include <ql/methods/finitedifferences/utilities/fdmdividendhandler.hpp>
#include <ql/methods/finitedifferences/stepconditions/fdmamericanstepcondition.hpp>
#include <ql/methods/finitedifferences/stepconditions/fdmbermudanstepcondition.hpp>
#include <ql/instruments/dividendschedule.hpp>
#include <ql/cashflows/dividend.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/math/array.hpp>
#include <algorithm>
#include <cmath>
#include "../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// A simple inner value calculator: returns payoff = max(loc0 - strike, 0)
// for the first direction mesh location (call payoff on log-S if needed).
// Here we use the raw location directly (not log), for simplicity.
class SimpleCallInnerValue : public FdmInnerValueCalculator {
public:
    SimpleCallInnerValue(ext::shared_ptr<FdmMesher> mesher,
                         double strike)
    : mesher_(std::move(mesher)), strike_(strike) {}

    Real innerValue(const FdmLinearOpIterator& iter, Time) override {
        const double loc = mesher_->location(iter, 0);
        return std::max(loc - strike_, 0.0);
    }
    Real avgInnerValue(const FdmLinearOpIterator& iter, Time t) override {
        return innerValue(iter, t);
    }
private:
    ext::shared_ptr<FdmMesher> mesher_;
    double strike_;
};

} // namespace

int main() {
    ReferenceWriter out("methods/finitedifferences/step_conditions",
                        QL_VERSION,
                        "step_conditions_probe");

    // ========== Part 1: FdmAmericanStepCondition ==========
    // 1D mesh: locations [0, 25, 50, 75, 100], strike=60
    // Inner value at each cell: max(loc - 60, 0) = [0,0,0,15,40]
    // Initial array: [5, 10, 20, 10, 30]
    // After applyTo(t=0.5, exerciseStart=0.0): each cell = max(array[i], innerValue[i])
    // Result: [5, 10, 20, 15, 40]
    {
        auto mesher1d = ext::make_shared<Uniform1dMesher>(0.0, 100.0, 5);
        auto mesher = ext::make_shared<FdmMesherComposite>(mesher1d);
        auto calc = ext::make_shared<SimpleCallInnerValue>(mesher, 60.0);

        FdmAmericanStepCondition american(mesher, calc, 0.0);

        Array a = {5.0, 10.0, 20.0, 10.0, 30.0};
        american.applyTo(a, 0.5);

        out.addCase("american_applyTo_basic",
                    json{{"n", 5}, {"start", 0.0}, {"end", 100.0},
                         {"strike", 60.0}, {"t", 0.5}, {"exerciseStart", 0.0}},
                    json{a[0], a[1], a[2], a[3], a[4]});
    }

    // American: t < exerciseStart -> no change
    {
        auto mesher1d = ext::make_shared<Uniform1dMesher>(0.0, 100.0, 5);
        auto mesher = ext::make_shared<FdmMesherComposite>(mesher1d);
        auto calc = ext::make_shared<SimpleCallInnerValue>(mesher, 60.0);

        FdmAmericanStepCondition american(mesher, calc, 1.0); // exerciseStart=1.0

        Array a = {5.0, 10.0, 20.0, 10.0, 30.0};
        american.applyTo(a, 0.5); // t=0.5 < exerciseStart=1.0 => no change

        out.addCase("american_applyTo_before_start",
                    json{{"n", 5}, {"start", 0.0}, {"end", 100.0},
                         {"strike", 60.0}, {"t", 0.5}, {"exerciseStart", 1.0}},
                    json{a[0], a[1], a[2], a[3], a[4]});
    }

    // American: t == exerciseStart -> apply
    {
        auto mesher1d = ext::make_shared<Uniform1dMesher>(0.0, 100.0, 5);
        auto mesher = ext::make_shared<FdmMesherComposite>(mesher1d);
        auto calc = ext::make_shared<SimpleCallInnerValue>(mesher, 60.0);

        FdmAmericanStepCondition american(mesher, calc, 0.5);

        Array a = {5.0, 10.0, 20.0, 10.0, 30.0};
        american.applyTo(a, 0.5); // t == exerciseStart -> applies

        out.addCase("american_applyTo_at_start",
                    json{{"n", 5}, {"start", 0.0}, {"end", 100.0},
                         {"strike", 60.0}, {"t", 0.5}, {"exerciseStart", 0.5}},
                    json{a[0], a[1], a[2], a[3], a[4]});
    }

    // American: inner value higher everywhere -> full replacement
    {
        auto mesher1d = ext::make_shared<Uniform1dMesher>(50.0, 150.0, 5);
        auto mesher = ext::make_shared<FdmMesherComposite>(mesher1d);
        auto calc = ext::make_shared<SimpleCallInnerValue>(mesher, 0.0);

        FdmAmericanStepCondition american(mesher, calc, 0.0);

        Array a = {1.0, 1.0, 1.0, 1.0, 1.0};
        american.applyTo(a, 0.0);

        out.addCase("american_applyTo_all_replaced",
                    json{{"n", 5}, {"start", 50.0}, {"end", 150.0},
                         {"strike", 0.0}, {"t", 0.0}, {"exerciseStart", 0.0}},
                    json{a[0], a[1], a[2], a[3], a[4]});
    }

    // American: 7-cell grid, put payoff = max(strike - loc, 0), strike=60
    {
        auto mesher1d = ext::make_shared<Uniform1dMesher>(0.0, 120.0, 7);
        auto mesher = ext::make_shared<FdmMesherComposite>(mesher1d);
        // Simulate put: use negative location trick via inner value
        // We use strike=60, but the calculator returns max(loc - 60, 0).
        // For a real put test, let's just use a call with strike=40 to get more nonzero.
        auto calc = ext::make_shared<SimpleCallInnerValue>(mesher, 40.0);

        FdmAmericanStepCondition american(mesher, calc, 0.0);

        Array a = {0.0, 0.0, 5.0, 10.0, 15.0, 20.0, 25.0};
        american.applyTo(a, 1.0);

        out.addCase("american_applyTo_7cell",
                    json{{"n", 7}, {"start", 0.0}, {"end", 120.0},
                         {"strike", 40.0}, {"t", 1.0}, {"exerciseStart", 0.0}},
                    json{a[0], a[1], a[2], a[3], a[4], a[5], a[6]});
    }

    // ========== Part 2: FdmBermudanStepCondition ==========
    // exerciseDates = [refDate + 1Y, refDate + 2Y]; check at exact exercise times.
    {
        const Date refDate(15, January, 2026);
        const DayCounter dc = Actual365Fixed();
        const Date ex1(15, January, 2027);
        const Date ex2(15, January, 2028);
        const std::vector<Date> exDates = {ex1, ex2};

        const Time t1 = dc.yearFraction(refDate, ex1);
        const Time t2 = dc.yearFraction(refDate, ex2);
        const Time tMid = 0.5 * (t1 + t2); // Not an exercise time

        auto mesher1d = ext::make_shared<Uniform1dMesher>(0.0, 100.0, 5);
        auto mesher = ext::make_shared<FdmMesherComposite>(mesher1d);
        auto calc = ext::make_shared<SimpleCallInnerValue>(mesher, 60.0);

        FdmBermudanStepCondition bermudan(exDates, refDate, dc, mesher, calc);

        // Emit the exercise times themselves as a reference
        const auto& times = bermudan.exerciseTimes();
        out.addCase("bermudan_exerciseTimes",
                    json{{"refDate", "2026-01-15"}, {"ex1", "2027-01-15"}, {"ex2", "2028-01-15"}},
                    json{times[0], times[1]});

        // applyTo at t = t1 (first exercise) -> modifies array
        {
            Array a = {5.0, 10.0, 20.0, 10.0, 30.0};
            bermudan.applyTo(a, t1);
            out.addCase("bermudan_applyTo_at_t1",
                        json{{"t", t1}, {"isExercise", true}},
                        json{a[0], a[1], a[2], a[3], a[4]});
        }

        // applyTo at t = t2 (second exercise) -> modifies array
        {
            Array a = {5.0, 10.0, 20.0, 10.0, 30.0};
            bermudan.applyTo(a, t2);
            out.addCase("bermudan_applyTo_at_t2",
                        json{{"t", t2}, {"isExercise", true}},
                        json{a[0], a[1], a[2], a[3], a[4]});
        }

        // applyTo at t = tMid (non-exercise) -> no change
        {
            Array a = {5.0, 10.0, 20.0, 10.0, 30.0};
            bermudan.applyTo(a, tMid);
            out.addCase("bermudan_applyTo_at_tmid",
                        json{{"t", tMid}, {"isExercise", false}},
                        json{a[0], a[1], a[2], a[3], a[4]});
        }
    }

    // Bermudan: 3 exercise dates, 7-cell grid
    {
        const Date refDate(1, January, 2025);
        const DayCounter dc = Actual365Fixed();
        const Date ex1(1, January, 2026);
        const Date ex2(1, January, 2027);
        const Date ex3(1, January, 2028);
        const std::vector<Date> exDates = {ex1, ex2, ex3};

        const Time t1 = dc.yearFraction(refDate, ex1);
        const Time t2 = dc.yearFraction(refDate, ex2);
        const Time t3 = dc.yearFraction(refDate, ex3);

        auto mesher1d = ext::make_shared<Uniform1dMesher>(0.0, 120.0, 7);
        auto mesher = ext::make_shared<FdmMesherComposite>(mesher1d);
        auto calc = ext::make_shared<SimpleCallInnerValue>(mesher, 60.0);

        FdmBermudanStepCondition bermudan(exDates, refDate, dc, mesher, calc);

        // applyTo at t3 (3rd exercise)
        Array a = {0.0, 0.0, 5.0, 10.0, 15.0, 20.0, 25.0};
        bermudan.applyTo(a, t3);

        out.addCase("bermudan_applyTo_3dates_at_t3",
                    json{{"n", 7}, {"start", 0.0}, {"end", 120.0},
                         {"strike", 60.0}, {"t", t3}, {"numDates", 3}},
                    json{a[0], a[1], a[2], a[3], a[4], a[5], a[6]});
    }

    // ========== Part 3: FdmDividendHandler ==========
    // 1D grid, 5 cells, locations are exp(linspace(log(50), log(150), 5))
    // We use a uniform mesher in log-space: locations are log(S), x_ = exp(loc).
    // Dividend at t=0.5 of amount 5.0.
    // Initial array: value at each node. After applyTo: linear-interpolated
    // shifted values.
    {
        const Date refDate(1, January, 2026);
        const DayCounter dc = Actual365Fixed();
        const Date divDate(1, July, 2026);  // ~0.5 years
        const Time divTime = dc.yearFraction(refDate, divDate);

        // Build a 1D mesher in log-space: log(50) to log(150), 5 cells
        // x_ = exp(locations)
        const double logLo = std::log(50.0);
        const double logHi = std::log(150.0);
        auto mesher1d = ext::make_shared<Uniform1dMesher>(logLo, logHi, 5);
        auto mesher = ext::make_shared<FdmMesherComposite>(mesher1d);

        // Build a DividendSchedule with one FixedDividend of amount 5.0
        DividendSchedule schedule;
        schedule.push_back(ext::make_shared<FixedDividend>(5.0, divDate));

        FdmDividendHandler handler(schedule, mesher, refDate, dc, 0);

        // Emit dividendTimes
        out.addCase("dividendHandler_dividendTimes",
                    json{{"refDate", "2026-01-01"}, {"divDate", "2026-07-01"}},
                    json{handler.dividendTimes()[0]});

        // Emit dividends
        out.addCase("dividendHandler_dividends",
                    json{{"amount", 5.0}},
                    json{handler.dividends()[0]});

        // Array values before applying: call payoff approximation [0,0,0,5,10]
        Array a = {0.0, 0.0, 0.0, 5.0, 10.0};
        Array aBefore = a;
        handler.applyTo(a, divTime);

        out.addCase("dividendHandler_applyTo_at_divTime",
                    json{{"n", 5}, {"logLo", logLo}, {"logHi", logHi},
                         {"dividend", 5.0}, {"t", divTime}},
                    json{a[0], a[1], a[2], a[3], a[4]});

        // Non-dividend time -> no change
        Array a2 = {0.0, 0.0, 0.0, 5.0, 10.0};
        handler.applyTo(a2, divTime + 0.1);

        out.addCase("dividendHandler_applyTo_nonDiv_noChange",
                    json{{"t", divTime + 0.1}},
                    json{a2[0], a2[1], a2[2], a2[3], a2[4]});
    }

    // DividendHandler: 2D mesher (equityDirection=0), dividend=10
    {
        const Date refDate(1, January, 2026);
        const DayCounter dc = Actual365Fixed();
        const Date divDate(1, April, 2026);
        const Time divTime = dc.yearFraction(refDate, divDate);

        const double logLo = std::log(80.0);
        const double logHi = std::log(120.0);
        auto mesher0 = ext::make_shared<Uniform1dMesher>(logLo, logHi, 4);
        auto mesher1 = ext::make_shared<Uniform1dMesher>(0.0, 1.0, 3);
        auto mesher = ext::make_shared<FdmMesherComposite>(mesher0, mesher1);

        DividendSchedule schedule;
        schedule.push_back(ext::make_shared<FixedDividend>(10.0, divDate));

        FdmDividendHandler handler(schedule, mesher, refDate, dc, 0);

        // 4x3 = 12 cells, a[i] = i * 2.0 (arbitrary)
        const int N = mesher->layout()->size();
        Array a(N);
        for (int i = 0; i < N; ++i) a[i] = i * 2.0;

        handler.applyTo(a, divTime);

        json arr = json::array();
        for (int i = 0; i < N; ++i) arr.push_back(a[i]);

        out.addCase("dividendHandler_2d_applyTo",
                    json{{"dims", {4, 3}}, {"logLo", logLo}, {"logHi", logHi},
                         {"dividend", 10.0}, {"t", divTime}},
                    arr);
    }

    // DividendHandler: 2 dividends, apply at both times
    {
        const Date refDate(1, January, 2026);
        const DayCounter dc = Actual365Fixed();
        const Date div1Date(1, April, 2026);
        const Date div2Date(1, October, 2026);
        const Time t1 = dc.yearFraction(refDate, div1Date);
        const Time t2 = dc.yearFraction(refDate, div2Date);

        const double logLo = std::log(50.0);
        const double logHi = std::log(150.0);
        auto mesher1d = ext::make_shared<Uniform1dMesher>(logLo, logHi, 6);
        auto mesher = ext::make_shared<FdmMesherComposite>(mesher1d);

        DividendSchedule schedule;
        schedule.push_back(ext::make_shared<FixedDividend>(3.0, div1Date));
        schedule.push_back(ext::make_shared<FixedDividend>(7.0, div2Date));

        FdmDividendHandler handler(schedule, mesher, refDate, dc, 0);

        Array a = {0.0, 2.0, 4.0, 6.0, 8.0, 10.0};
        handler.applyTo(a, t1);

        out.addCase("dividendHandler_twoDivs_applyAt_t1",
                    json{{"n", 6}, {"dividend1", 3.0}, {"t", t1}},
                    json{a[0], a[1], a[2], a[3], a[4], a[5]});

        // Apply at t2 on the already-shifted array
        handler.applyTo(a, t2);

        out.addCase("dividendHandler_twoDivs_applyAt_t2",
                    json{{"n", 6}, {"dividend2", 7.0}, {"t", t2}},
                    json{a[0], a[1], a[2], a[3], a[4], a[5]});
    }

    out.write();
    return 0;
}
