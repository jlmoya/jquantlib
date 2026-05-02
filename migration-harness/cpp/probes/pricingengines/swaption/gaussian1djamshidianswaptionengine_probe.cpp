// migration-harness/cpp/probes/pricingengines/swaption/gaussian1djamshidianswaptionengine_probe.cpp
//
// Probe for Phase 2j WI-3.1: Gaussian1dJamshidianSwaptionEngine NPV fingerprint.
//
// Cross-validates Swaption NPV priced under a GSR model using Jamshidian's
// bond-option decomposition against the Java Gaussian1dJamshidianSwaptionEngine port.
//
// All test cases use:
//   eval     = 2026-01-15
//   yts      = FlatForward(eval, 5%, Continuous, Annual, Actual365Fixed)
//   calendar = TARGET
//   index    = Euribor3M
//
// GSR params:
//   volStepDates = [eval+2Y, eval+5Y]
//   volatilities = [0.0070, 0.0080, 0.0085]
//   reversion    = 0.02
//
// Test matrix:
//   - Fixed leg frequency: Annual
//   - Float leg tenor: 3M
//   - 5Y x 5Y ATM payer swaption
//   - 5Y x 5Y ATM receiver swaption
//   - 1Y x 5Y ATM payer swaption
//   - 2Y x 10Y ATM payer swaption
//   - 5Y x 5Y 100bp ITM payer swaption (fixed rate = atmRate - 100bps)
//   - 5Y x 5Y 100bp OTM payer swaption (fixed rate = atmRate + 100bps)

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/makevanillaswap.hpp>
#include <ql/instruments/swaption.hpp>
#include <ql/instruments/vanillaswap.hpp>
#include <ql/models/shortrate/onefactormodels/gsr.hpp>
#include <ql/pricingengines/swap/discountingswapengine.hpp>
#include <ql/pricingengines/swaption/gaussian1djamshidianswaptionengine.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/daycounters/thirty360.hpp>
#include <ql/time/schedule.hpp>

#include "common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

// Build and price a swaption under Gaussian1dJamshidianSwaptionEngine.
// Returns the NPV.
static Real priceG1dJamshidianSwaption(
        const Handle<YieldTermStructure>& ts,
        const ext::shared_ptr<IborIndex>& idx,
        const ext::shared_ptr<Gaussian1dJamshidianSwaptionEngine>& engine,
        const Calendar& cal,
        const DayCounter& fixedDc,
        const DayCounter& floatDc,
        const Period& exerciseTenor,    // e.g. 5Y
        const Period& swapTenor,        // e.g. 5Y
        VanillaSwap::Type swapType,
        Real rateDelta = 0.0            // offset from ATM
) {
    const Date eval = Settings::instance().evaluationDate();
    const Date exerciseDate = cal.advance(eval, exerciseTenor);
    const Date startDate = cal.advance(exerciseDate, 2, Days);
    const Date maturity = cal.advance(startDate, swapTenor);

    Schedule fixedSchedule(startDate, maturity, Period(1, Years), cal,
                           ModifiedFollowing, ModifiedFollowing,
                           DateGeneration::Forward, false);
    Schedule floatSchedule(startDate, maturity, idx->tenor(), cal,
                           ModifiedFollowing, ModifiedFollowing,
                           DateGeneration::Forward, false);

    const Real nominal = 1.0;
    // ATM dummy swap
    auto swap0 = ext::make_shared<VanillaSwap>(
        VanillaSwap::Payer, nominal, fixedSchedule, 0.05, fixedDc,
        floatSchedule, idx, 0.0, floatDc);
    swap0->setPricingEngine(ext::make_shared<DiscountingSwapEngine>(ts));
    const Rate atmRate = swap0->fairRate();

    const Rate fixedRate = atmRate + rateDelta;

    auto swap = ext::make_shared<VanillaSwap>(
        swapType, nominal, fixedSchedule, fixedRate, fixedDc,
        floatSchedule, idx, 0.0, floatDc);

    const auto exercise = ext::make_shared<EuropeanExercise>(exerciseDate);
    Swaption swaption(swap, exercise);
    swaption.setPricingEngine(engine);
    return swaption.NPV();
}

int main() {
    ReferenceWriter out("pricingengines/swaption/gaussian1djamshidianswaptionengine",
                        QL_VERSION, "gaussian1djamshidianswaptionengine_probe");

    // --- Common fixture --------------------------------------------------
    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;

    const DayCounter yieldDc = Actual365Fixed();
    const DayCounter fixedDc = Thirty360(Thirty360::European);
    const Calendar cal = TARGET();

    const Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(eval, 0.05, yieldDc, Continuous, Annual));

    const auto idx = ext::make_shared<Euribor3M>(ts);

    // GSR model: 3-piece vol, constant reversion
    std::vector<Date> volStepDates;
    volStepDates.push_back(cal.advance(eval, Period(2, Years)));
    volStepDates.push_back(cal.advance(eval, Period(5, Years)));
    std::vector<Real> vols = {0.0070, 0.0080, 0.0085};
    Real reversion = 0.02;
    auto gsr = ext::make_shared<Gsr>(ts, volStepDates, vols, reversion);
    auto engine = ext::make_shared<Gaussian1dJamshidianSwaptionEngine>(gsr);

    // --- Case 1: 5Y x 5Y ATM payer swaption ---
    {
        Real npv = priceG1dJamshidianSwaption(
            ts, idx, engine, cal, fixedDc, yieldDc,
            Period(5, Years), Period(5, Years), VanillaSwap::Payer, 0.0);
        out.addCase("atm_payer_5y5y",
            json{{"eval_date","2026-01-15"},{"exercise_years",5},{"swap_years",5},
                 {"type","payer"},{"rate_delta",0.0},{"gsr_reversion",reversion}},
            json{{"npv", npv}});
    }

    // --- Case 2: 5Y x 5Y ATM receiver swaption ---
    {
        Real npv = priceG1dJamshidianSwaption(
            ts, idx, engine, cal, fixedDc, yieldDc,
            Period(5, Years), Period(5, Years), VanillaSwap::Receiver, 0.0);
        out.addCase("atm_receiver_5y5y",
            json{{"eval_date","2026-01-15"},{"exercise_years",5},{"swap_years",5},
                 {"type","receiver"},{"rate_delta",0.0},{"gsr_reversion",reversion}},
            json{{"npv", npv}});
    }

    // --- Case 3: 1Y x 5Y ATM payer swaption ---
    {
        Real npv = priceG1dJamshidianSwaption(
            ts, idx, engine, cal, fixedDc, yieldDc,
            Period(1, Years), Period(5, Years), VanillaSwap::Payer, 0.0);
        out.addCase("atm_payer_1y5y",
            json{{"eval_date","2026-01-15"},{"exercise_years",1},{"swap_years",5},
                 {"type","payer"},{"rate_delta",0.0},{"gsr_reversion",reversion}},
            json{{"npv", npv}});
    }

    // --- Case 4: 2Y x 10Y ATM payer swaption ---
    {
        Real npv = priceG1dJamshidianSwaption(
            ts, idx, engine, cal, fixedDc, yieldDc,
            Period(2, Years), Period(10, Years), VanillaSwap::Payer, 0.0);
        out.addCase("atm_payer_2y10y",
            json{{"eval_date","2026-01-15"},{"exercise_years",2},{"swap_years",10},
                 {"type","payer"},{"rate_delta",0.0},{"gsr_reversion",reversion}},
            json{{"npv", npv}});
    }

    // --- Case 5: 5Y x 5Y 100bp ITM payer (lower fixed rate) ---
    {
        Real npv = priceG1dJamshidianSwaption(
            ts, idx, engine, cal, fixedDc, yieldDc,
            Period(5, Years), Period(5, Years), VanillaSwap::Payer, -0.01);
        out.addCase("itm_payer_5y5y",
            json{{"eval_date","2026-01-15"},{"exercise_years",5},{"swap_years",5},
                 {"type","payer"},{"rate_delta",-0.01},{"gsr_reversion",reversion}},
            json{{"npv", npv}});
    }

    // --- Case 6: 5Y x 5Y 100bp OTM payer (higher fixed rate) ---
    {
        Real npv = priceG1dJamshidianSwaption(
            ts, idx, engine, cal, fixedDc, yieldDc,
            Period(5, Years), Period(5, Years), VanillaSwap::Payer, 0.01);
        out.addCase("otm_payer_5y5y",
            json{{"eval_date","2026-01-15"},{"exercise_years",5},{"swap_years",5},
                 {"type","payer"},{"rate_delta",0.01},{"gsr_reversion",reversion}},
            json{{"npv", npv}});
    }

    // --- Case 7: ATM receiver 1Y x 5Y ---
    {
        Real npv = priceG1dJamshidianSwaption(
            ts, idx, engine, cal, fixedDc, yieldDc,
            Period(1, Years), Period(5, Years), VanillaSwap::Receiver, 0.0);
        out.addCase("atm_receiver_1y5y",
            json{{"eval_date","2026-01-15"},{"exercise_years",1},{"swap_years",5},
                 {"type","receiver"},{"rate_delta",0.0},{"gsr_reversion",reversion}},
            json{{"npv", npv}});
    }

    // --- Case 8: ATM receiver 2Y x 10Y ---
    {
        Real npv = priceG1dJamshidianSwaption(
            ts, idx, engine, cal, fixedDc, yieldDc,
            Period(2, Years), Period(10, Years), VanillaSwap::Receiver, 0.0);
        out.addCase("atm_receiver_2y10y",
            json{{"eval_date","2026-01-15"},{"exercise_years",2},{"swap_years",10},
                 {"type","receiver"},{"rate_delta",0.0},{"gsr_reversion",reversion}},
            json{{"npv", npv}});
    }

    // --- Case 9: ITM receiver 5Y x 5Y (+100bp) ---
    {
        Real npv = priceG1dJamshidianSwaption(
            ts, idx, engine, cal, fixedDc, yieldDc,
            Period(5, Years), Period(5, Years), VanillaSwap::Receiver, 0.01);
        out.addCase("itm_receiver_5y5y",
            json{{"eval_date","2026-01-15"},{"exercise_years",5},{"swap_years",5},
                 {"type","receiver"},{"rate_delta",0.01},{"gsr_reversion",reversion}},
            json{{"npv", npv}});
    }

    // --- Case 10: OTM receiver 5Y x 5Y (-100bp) ---
    {
        Real npv = priceG1dJamshidianSwaption(
            ts, idx, engine, cal, fixedDc, yieldDc,
            Period(5, Years), Period(5, Years), VanillaSwap::Receiver, -0.01);
        out.addCase("otm_receiver_5y5y",
            json{{"eval_date","2026-01-15"},{"exercise_years",5},{"swap_years",5},
                 {"type","receiver"},{"rate_delta",-0.01},{"gsr_reversion",reversion}},
            json{{"npv", npv}});
    }

    out.write();
    return 0;
}
