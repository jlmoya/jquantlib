// migration-harness/cpp/probes/processes/hhw_callable_equity_probe.cpp
// Reference Monte-Carlo NPV for the auto-callable equity structure priced by
// test-suite/hybridhestonhullwhiteprocess.cpp::testCallableEquityPricing
// (Giese, 2006).
//
// The C++ test uses Date::todaysDate() which makes the cached expected = 0.938
// non-reproducible. The schedule generation is purely date-based (so a
// different today changes the schedule), but the test then overwrites times
// with the integer sequence {0..maturity}, so the only real today-dependence
// is hwProcess->setForwardMeasureTime(yearFraction(today, today+8Y)) which
// varies by 0..2 leap-days over the 8-year span.
//
// This probe pins today = Date(15, July, 2026) — matching the other
// HybridHestonHullWhiteProcess tests body-filled under Phase 5e.5b — and
// emits the resulting MC mean / errorEstimate so the Java port can pin
// the expected to the v1.42.1 ground-truth on that exact date.

#include <ql/version.hpp>
#include <ql/processes/hestonprocess.hpp>
#include <ql/processes/hullwhiteprocess.hpp>
#include <ql/processes/hybridhestonhullwhiteprocess.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/schedule.hpp>
#include <ql/time/period.hpp>
#include <ql/methods/montecarlo/multipathgenerator.hpp>
#include <ql/math/randomnumbers/rngtraits.hpp>
#include <ql/math/statistics/generalstatistics.hpp>
#include "../common.hpp"

#include <algorithm>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("processes/hhw_callable_equity", QL_VERSION,
                        "hhw_callable_equity_probe");

    const Size maturity = 7;
    DayCounter dc = Actual365Fixed();
    // Pinned deterministic evaluation date (matches the sister
    // body-fill HHW process tests in the Java port).
    const Date today(15, July, 2026);
    Settings::instance().evaluationDate() = today;

    Handle<Quote> spot(ext::shared_ptr<Quote>(new SimpleQuote(100.0)));
    ext::shared_ptr<SimpleQuote> qRate(new SimpleQuote(0.04));
    Handle<YieldTermStructure> qTS(
        ext::shared_ptr<YieldTermStructure>(
            new FlatForward(today, Handle<Quote>(qRate), dc)));
    ext::shared_ptr<SimpleQuote> rRate(new SimpleQuote(0.04));
    Handle<YieldTermStructure> rTS(
        ext::shared_ptr<YieldTermStructure>(
            new FlatForward(today, Handle<Quote>(rRate), dc)));

    const ext::shared_ptr<HestonProcess> hestonProcess(
            new HestonProcess(rTS, qTS, spot, 0.0625, 1.0,
                              0.24*0.24, 1e-4, 0.0));
    const ext::shared_ptr<HullWhiteForwardProcess> hwProcess(
            new HullWhiteForwardProcess(rTS, 0.00883, 0.00526));
    hwProcess->setForwardMeasureTime(
                      dc.yearFraction(today, today+Period(maturity+1, Years)));

    const ext::shared_ptr<HybridHestonHullWhiteProcess> jointProcess(
        new HybridHestonHullWhiteProcess(hestonProcess, hwProcess, -0.4));

    Schedule schedule(today, today + Period(maturity, Years),
                      Period(1, Years), TARGET(),
                      Following, Following,
                      DateGeneration::Forward, false);

    std::vector<Time> times(maturity+1);
    std::transform(schedule.begin(), schedule.end(), times.begin(),
                   [&](const Date& d) { return dc.yearFraction(today, d); });

    // Per C++ test (line 657-658) — overwrite with integer year fractions.
    for (Size i=0; i<=maturity; ++i)
        times[i] = static_cast<Time>(i);

    TimeGrid grid(times.begin(), times.end());

    std::vector<Real> redemption(maturity);
    for (Size i=0; i < maturity; ++i) {
        redemption[i] = 1.07 + 0.03*i;
    }

    typedef PseudoRandom::rsg_type rsg_type;
    typedef MultiPathGenerator<rsg_type>::sample_type sample_type;

    BigNatural seed = 42;
    rsg_type rsg = PseudoRandom::make_sequence_generator(
                              jointProcess->factors()*(grid.size()-1), seed);

    MultiPathGenerator<rsg_type> generator(jointProcess, grid, rsg, false);
    GeneralStatistics stat;

    Real antitheticPayoff=0;
    const Size nrTrails = 40000;
    for (Size i=0; i < nrTrails; ++i) {
        const bool antithetic = (i % 2) != 0;

        sample_type path = antithetic ? generator.antithetic()
                                      : generator.next();

        Real payoff=0;
        for (Size j=1; j <= maturity; ++j) {
            if (path.value[0][j] > spot->value()) {
                Array states(3);
                for (Size k=0; k < 3; ++k) {
                    states[k] = path.value[k][j];
                }
                payoff = redemption[j-1]
                    / jointProcess->numeraire(grid[j], states);
                break;
            }
            else if (j == maturity) {
                Array states(3);
                for (Size k=0; k < 3; ++k) {
                    states[k] = path.value[k][j];
                }
                payoff = 1.0 / jointProcess->numeraire(grid[j], states);
            }
        }

        if (antithetic) {
            stat.add(0.5*(antitheticPayoff + payoff));
        }
        else {
            antitheticPayoff = payoff;
        }
    }

    const Real mean = stat.mean();
    const Real err  = stat.errorEstimate();
    const Real fwdMeasure =
        dc.yearFraction(today, today+Period(maturity+1, Years));

    json inputs = {
        {"today",          "2026-07-15"},
        {"daycounter",     "Actual365Fixed"},
        {"maturity_years", maturity},
        {"spot",           100.0},
        {"qRate",          0.04},
        {"rRate",          0.04},
        {"heston_v0",      0.0625},
        {"heston_kappa",   1.0},
        {"heston_theta",   0.24*0.24},
        {"heston_sigma",   1e-4},
        {"heston_rho",     0.0},
        {"hw_a",           0.00883},
        {"hw_sigma",       0.00526},
        {"hw_fwd_measure_t", fwdMeasure},
        {"joint_rho_eqr",  -0.4},
        {"times",          {0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0}},
        {"redemption",     {1.07, 1.10, 1.13, 1.16, 1.19, 1.22, 1.25}},
        {"seed",           42},
        {"nrTrails",       40000},
        {"antithetic",     true}
    };
    json expected = {
        {"mean",          mean},
        {"errorEstimate", err}
    };
    out.addCase("giese_autocallable_seed42_40k", inputs, expected);

    out.write();
    return 0;
}
