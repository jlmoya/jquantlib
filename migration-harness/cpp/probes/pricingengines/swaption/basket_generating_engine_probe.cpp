// migration-harness/cpp/probes/pricingengines/swaption/basket_generating_engine_probe.cpp
//
// Phase 2k Track B — BasketGeneratingEngine calibrationBasket() fingerprint.
// Oracle: C++ QuantLib v1.42.1.
//
// Builds a Gsr Gaussian1d model + NonstandardSwaptionEngine and calls
// calibrationBasket() to capture the basket helpers' structural parameters.
//
// Fixture: eval=2026-01-15, FlatForward 3% Continuous Actual365Fixed on TARGET,
// Euribor3M floating leg, 30/360 European fixed leg (Annual).
// Vol surface: ConstantSwaptionVolatility (15%, ShiftedLognormal, shift=0.0).
// SwapIndex: 1Y EUR-CMS-ANNUAL based on Euribor3M.
//
// For Naive basket: captures (helper count, vol, helper maturity/structure).
// For MaturityStrikeByDeltaGamma: captures (helper count, vol, nominal, strike).
// ~30 cases across basket types + exercise configurations.
//
// Output: JSON with basket_size, per-helper vol, strike, nominal.

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/indexes/swap/euriborswap.hpp>
#include <ql/models/shortrate/calibrationhelpers/swaptionhelper.hpp>
#include <ql/instruments/nonstandardswap.hpp>
#include <ql/instruments/nonstandardswaption.hpp>
#include <ql/models/shortrate/onefactormodels/gsr.hpp>
#include <ql/pricingengines/swaption/gaussian1dnonstandardswaptionengine.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/volatility/swaption/swaptionconstantvol.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/daycounters/thirty360.hpp>
#include <ql/time/schedule.hpp>

#include "common.hpp"

#include <cstdio>
#include <vector>
#include <string>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

std::vector<Real> flatNominal(int n, Real nom) {
    return std::vector<Real>(n, nom);
}

} // namespace

int main() {
    ReferenceWriter out(
        "pricingengines/swaption/basket_generating_engine",
        QL_VERSION,
        "basket_generating_engine_probe");

    // --- Fixture -----------------------------------------------------------
    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;

    const DayCounter dc     = Actual365Fixed();
    const DayCounter fixedDc = Thirty360(Thirty360::European);
    const Calendar   cal    = TARGET();
    const Real flatRate     = 0.03;
    const Real reversion    = 0.01;
    const Real nominal      = 100.0;

    const Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(eval, flatRate, dc, Continuous, Annual));
    auto idx = ext::make_shared<Euribor3M>(ts);

    // GSR model: single vol step at 1Y
    std::vector<Date> volStepDates;
    volStepDates.push_back(eval + Period(1, Years));
    volStepDates.push_back(eval + Period(2, Years));
    volStepDates.push_back(eval + Period(5, Years));
    std::vector<Real> vols = {0.01, 0.012, 0.014, 0.016};
    auto gsr = ext::make_shared<Gsr>(ts, volStepDates, vols, reversion);

    // Swaption engine (default params)
    auto engine = ext::make_shared<Gaussian1dNonstandardSwaptionEngine>(
        gsr, 64, 7.0, true, false,
        Handle<Quote>(),
        Handle<YieldTermStructure>(),
        Gaussian1dNonstandardSwaptionEngine::None);

    // Standard swap index for basket: 1Y EUR annual fixed vs Euribor3M
    // We use EuriborSwapIsdaFixA which is EUR annual vs 6M; for our purposes
    // we just need a SwapIndex with associated ts.
    // Using EuriborSwapIsdaFixA(1Y, ts) for the standardSwapBase.
    auto swapIdx = ext::make_shared<EuriborSwapIsdaFixA>(Period(1, Years), ts, ts);

    // Constant swaption vol surface: 15% ShiftedLognormal, shift=0
    const Real swVol = 0.15;
    auto constVol = ext::make_shared<ConstantSwaptionVolatility>(
        eval, cal, Following, swVol, dc);

    int caseIdx = 0;

    // --- Grid: basketType x exerciseType x swapConfig ---
    struct ExerciseSpec {
        const char* tag;
        std::vector<int> exerciseDeltaYears; // years from eval for each exercise date
        int swapYears;
        Swap::Type swapType;
        Real fixedRate;
    };

    // Exercise specs: Bermudan (3 dates) and European (1 date) variants
    ExerciseSpec specs[] = {
        // Bermudan 3 exercise dates, 3Y underlying
        {"berm_3y_payer",   {1, 2, 3}, 4, Swap::Payer,    0.03},
        {"berm_3y_recv",    {1, 2, 3}, 4, Swap::Receiver, 0.03},
        {"berm_5y_payer",   {1, 2, 3, 4, 5}, 6, Swap::Payer, 0.025},
        {"berm_5y_recv",    {1, 2, 3, 4, 5}, 6, Swap::Receiver, 0.035},
        // European (single exercise date)
        {"euro_1y_payer",   {1}, 3, Swap::Payer,    0.02},
        {"euro_1y_recv",    {1}, 3, Swap::Receiver, 0.04},
        {"euro_2y_payer",   {2}, 5, Swap::Payer,    0.03},
        {"euro_2y_recv",    {2}, 5, Swap::Receiver, 0.03},
        // Deeper in-money / out-of-money
        {"euro_1y_payer_itm", {1}, 3, Swap::Payer,    0.01},
        {"euro_1y_recv_otm",  {1}, 3, Swap::Receiver, 0.06},
    };
    const int nSpecs = static_cast<int>(sizeof(specs) / sizeof(specs[0]));

    const char* basketTypeNames[] = {"Naive", "MaturityStrikeByDeltaGamma"};
    BasketGeneratingEngine::CalibrationBasketType basketTypes[] = {
        BasketGeneratingEngine::Naive,
        BasketGeneratingEngine::MaturityStrikeByDeltaGamma
    };
    const int nBasketTypes = 2;

    for (int bt = 0; bt < nBasketTypes; bt++) {
        for (int si = 0; si < nSpecs; si++) {
            const ExerciseSpec& spec = specs[si];
            const char* btName = basketTypeNames[bt];

            // Build exercise
            std::vector<Date> exDates;
            for (int dy : spec.exerciseDeltaYears)
                exDates.push_back(cal.advance(eval, dy * Years));
            auto exercise = ext::make_shared<BermudanExercise>(exDates);

            // Build underlying NonstandardSwap
            const Date firstEx = exDates.front();
            const Date startDate = cal.advance(firstEx, 2, Days);
            const Date maturity  = cal.advance(startDate, spec.swapYears * Years);

            Schedule fixedSch(startDate, maturity, Period(1, Years), cal,
                              ModifiedFollowing, ModifiedFollowing,
                              DateGeneration::Forward, false);
            Schedule floatSch(startDate, maturity, Period(3, Months), cal,
                              ModifiedFollowing, ModifiedFollowing,
                              DateGeneration::Forward, false);

            int nFixed = (int)fixedSch.size() - 1;
            int nFloat = (int)floatSch.size() - 1;

            std::vector<Real> fixedNoms = flatNominal(nFixed, nominal);
            std::vector<Real> floatNoms = flatNominal(nFloat, nominal);
            std::vector<Real> fixedRates(nFixed, spec.fixedRate);

            auto swap = ext::make_shared<NonstandardSwap>(
                spec.swapType,
                fixedNoms, floatNoms,
                fixedSch, fixedRates, fixedDc,
                floatSch, idx,
                1.0, 0.0, dc, false, false);

            auto swaption = ext::make_shared<NonstandardSwaption>(swap, exercise);
            swaption->setPricingEngine(engine);

            // Generate calibration basket
            // NonstandardSwaption::calibrationBasket uses the exercise embedded in the swaption
            auto basket = swaption->calibrationBasket(
                swapIdx, constVol, basketTypes[bt]);

            // Capture basket structure
            const int basketSize = (int)basket.size();

            // Per-helper: capture market vol (from volatility handle) and
            // the helper's model price / market price (after pricing with Black engine)
            std::vector<double> helperVols(basketSize);
            std::vector<double> helperStrikes(basketSize);
            std::vector<double> helperNominals(basketSize);

            for (int h = 0; h < basketSize; h++) {
                // volatility() triggers performCalculations
                helperVols[h] = basket[h]->volatility()->value();
                helperStrikes[h] = 0.0; // filled below
                helperNominals[h] = 0.0;
                // Cast to SwaptionHelper to extract strike/nominal
                auto sh = ext::dynamic_pointer_cast<SwaptionHelper>(basket[h]);
                if (sh) {
                    // Access underlying swap to get fixed rate (= strike for ATM)
                    // and nominal
                    auto ul = sh->underlying();
                    if (ul) {
                        helperStrikes[h] = ul->fixedRate();
                        helperNominals[h] = ul->nominal();
                    }
                }
            }

            char nm[256];
            std::snprintf(nm, sizeof nm, "%s_%s_%03d",
                btName, spec.tag, caseIdx++);

            json helperVolsJson = json::array();
            json helperStrikesJson = json::array();
            json helperNominalsJson = json::array();
            for (int h = 0; h < basketSize; h++) {
                helperVolsJson.push_back(helperVols[h]);
                helperStrikesJson.push_back(helperStrikes[h]);
                helperNominalsJson.push_back(helperNominals[h]);
            }

            out.addCase(nm,
                json{
                    {"basket_type", btName},
                    {"spec_tag",    spec.tag},
                    {"n_exercise_dates", (int)exDates.size()},
                    {"swap_years",  spec.swapYears},
                    {"swap_type",   spec.swapType == Swap::Payer ? "Payer" : "Receiver"},
                    {"fixed_rate",  spec.fixedRate}
                },
                json{
                    {"basket_size",    basketSize},
                    {"helper_vols",    helperVolsJson},
                    {"helper_strikes", helperStrikesJson},
                    {"helper_nominals",helperNominalsJson}
                });
        }
    }

    out.write();
    return 0;
}
