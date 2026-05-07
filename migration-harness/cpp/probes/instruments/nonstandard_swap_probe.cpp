// migration-harness/cpp/probes/instruments/nonstandard_swap_probe.cpp
//
// Phase 2j.5 Track A.1 — NonstandardSwap structural fingerprint.
// Oracle: C++ QuantLib v1.42.1.
//
// Builds NonstandardSwap instances with deterministic per-coupon
// notionals and rates, prices each under DiscountingSwapEngine, and
// emits fingerprints:
//   - fixedLegNPV     : payer_[0] * legNPV_[0]
//   - floatingLegNPV  : payer_[1] * legNPV_[1]
//   - npv             : total NPV (fixedLeg + floatingLeg)
//   - nFixedCoupons   : number of cashflows on the fixed leg
//   - nFloatCoupons   : number of cashflows on the floating leg
//   - fixedRate_0     : fixedRate()[0]
//   - fixedNominal_0  : fixedNominal()[0]
//   - spreadScalar    : spread() when singleSpreadAndGearing_==true
//   - gearingScalar   : gearing() when singleSpreadAndGearing_==true
//
// Fixture: eval=2026-01-15, FlatForward 4% Continuous Actual365Fixed,
// TARGET calendar, Euribor3M floating index, 30/360-European fixed DC,
// ModifiedFollowing payment convention.

#include <ql/version.hpp>

#include <ql/cashflows/cashflows.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/nonstandardswap.hpp>
#include <ql/instruments/vanillaswap.hpp>
#include <ql/pricingengines/swap/discountingswapengine.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/daycounters/thirty360.hpp>
#include <ql/time/schedule.hpp>

#include "common.hpp"

#include <vector>
#include <string>
#include <sstream>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

const Date EVAL(15, January, 2026);
const DayCounter DC = Actual365Fixed();
const Calendar CAL = TARGET();
const DayCounter FIXED_DC = Thirty360(Thirty360::European);
const Real FLAT_RATE = 0.04;

Handle<YieldTermStructure> makeTS() {
    return Handle<YieldTermStructure>(
        ext::make_shared<FlatForward>(EVAL, FLAT_RATE, DC, Continuous, Annual));
}

// Build schedule starting spotLag business days after startDate with
// given tenor and period.
Schedule makeSchedule(const Date& start, int years, const Period& tenor) {
    Date end = CAL.advance(start, Period(years, Years));
    return Schedule(start, end, tenor, CAL,
                    ModifiedFollowing, ModifiedFollowing,
                    DateGeneration::Forward, false);
}

struct SwapResult {
    double fixedLegNPV;
    double floatingLegNPV;
    double npv;
    int nFixed;
    int nFloat;
    double fixedRate0;
    double fixedNominal0;
};

SwapResult priceSwap(NonstandardSwap& swap,
                     const Handle<YieldTermStructure>& ts) {
    auto engine = ext::make_shared<DiscountingSwapEngine>(ts);
    swap.setPricingEngine(engine);
    SwapResult r;
    r.npv          = swap.NPV();
    r.fixedLegNPV  = swap.legNPV(0);
    r.floatingLegNPV = swap.legNPV(1);
    r.nFixed       = (int)swap.fixedLeg().size();
    r.nFloat       = (int)swap.floatingLeg().size();
    r.fixedRate0   = swap.fixedRate()[0];
    r.fixedNominal0= swap.fixedNominal()[0];
    return r;
}

} // namespace

int main() {
    Settings::instance().evaluationDate() = EVAL;

    ReferenceWriter out("instruments/nonstandard_swap",
                        QL_VERSION, "nonstandard_swap_probe");

    auto ts = makeTS();
    auto idx = ext::make_shared<Euribor3M>(ts);

    // ─── Case group A: from VanillaSwap conversion ───────────────────────────
    // Build a VanillaSwap and wrap it as NonstandardSwap via the copy ctor.
    // We test different tenors and types (payer / receiver).
    {
        struct VC { int tenor; VanillaSwap::Type type; Real rate; Real nominal; Real spread; };
        std::vector<VC> vcs = {
            {3, VanillaSwap::Payer,    0.03, 100.0, 0.0 },
            {5, VanillaSwap::Payer,    0.04, 200.0, 0.001},
            {5, VanillaSwap::Receiver, 0.035,150.0, 0.0 },
            {2, VanillaSwap::Receiver, 0.025,100.0,-0.001},
        };
        int idx2 = 0;
        for (auto& vc : vcs) {
            const Date start = CAL.advance(EVAL, Period(2, Days));
            Schedule fixedSch = makeSchedule(start, vc.tenor, Period(1, Years));
            Schedule floatSch = makeSchedule(start, vc.tenor, Period(3, Months));

            VanillaSwap vanilla(vc.type, vc.nominal, fixedSch, vc.rate,
                                FIXED_DC, floatSch, idx, vc.spread, DC);
            NonstandardSwap ns(vanilla);

            auto dse = ext::make_shared<DiscountingSwapEngine>(ts);
            ns.setPricingEngine(dse);

            std::ostringstream name;
            name << "from_vanilla_" << idx2++;
            double spreadScalar = ns.spread();
            double gearingScalar = ns.gearing();
            out.addCase(name.str(),
                {{"tenor_years", vc.tenor},
                 {"type",   vc.type == VanillaSwap::Payer ? "Payer" : "Receiver"},
                 {"rate",   vc.rate},
                 {"nominal",vc.nominal},
                 {"spread", vc.spread}},
                {{"fixedLegNPV",    ns.legNPV(0)},
                 {"floatingLegNPV", ns.legNPV(1)},
                 {"npv",            ns.NPV()},
                 {"nFixedCoupons",  (int)ns.fixedLeg().size()},
                 {"nFloatCoupons",  (int)ns.floatingLeg().size()},
                 {"fixedRate0",     ns.fixedRate()[0]},
                 {"fixedNominal0",  ns.fixedNominal()[0]},
                 {"spreadScalar",   spreadScalar},
                 {"gearingScalar",  gearingScalar}});
        }
    }

    // ─── Case group B: per-coupon notionals (amortising), scalar gearing/spread ─
    {
        struct PC {
            int tenor;
            VanillaSwap::Type type;
            std::vector<Real> fixedNom;
            std::vector<Real> floatNom;
            std::vector<Real> fixedRates;
            Real gearing;
            Real spread;
        };
        const Date start = CAL.advance(EVAL, Period(2, Days));
        Schedule fixedSch3 = makeSchedule(start, 3, Period(1, Years));
        Schedule floatSch3 = makeSchedule(start, 3, Period(3, Months));
        Schedule fixedSch5 = makeSchedule(start, 5, Period(1, Years));
        Schedule floatSch5 = makeSchedule(start, 5, Period(3, Months));

        std::vector<PC> pcs = {
            // 3Y amortising: 100→80→60
            {3, VanillaSwap::Payer,
             {100.0, 80.0, 60.0},
             {100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0},
             {0.03, 0.03, 0.03},
             1.0, 0.0},
            // 3Y accreting: 50→75→100
            {3, VanillaSwap::Receiver,
             {50.0, 75.0, 100.0},
             {50.0, 50.0, 50.0, 62.5, 62.5, 62.5, 87.5, 87.5, 87.5, 100.0, 100.0, 100.0},
             {0.04, 0.04, 0.04},
             1.0, 0.0},
            // 5Y flat notional, varying rates
            {5, VanillaSwap::Payer,
             {100.0, 100.0, 100.0, 100.0, 100.0},
             {100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0,
              100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0},
             {0.025, 0.03, 0.035, 0.04, 0.045},
             1.0, 0.001},
            // 5Y with gearing != 1
            {5, VanillaSwap::Receiver,
             {100.0, 100.0, 100.0, 100.0, 100.0},
             {100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0,
              100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0},
             {0.04, 0.04, 0.04, 0.04, 0.04},
             0.5, -0.001},
        };

        for (std::size_t i = 0; i < pcs.size(); ++i) {
            const auto& pc = pcs[i];
            Schedule& fixedSch = (pc.tenor == 3) ? fixedSch3 : fixedSch5;
            Schedule& floatSch = (pc.tenor == 3) ? floatSch3 : floatSch5;

            NonstandardSwap ns(pc.type == VanillaSwap::Payer ? Swap::Payer : Swap::Receiver,
                               pc.fixedNom, pc.floatNom, fixedSch, pc.fixedRates,
                               FIXED_DC, floatSch, idx, pc.gearing, pc.spread, DC);

            auto dse = ext::make_shared<DiscountingSwapEngine>(ts);
            ns.setPricingEngine(dse);

            std::ostringstream name;
            name << "per_coupon_scalar_" << i;
            out.addCase(name.str(),
                {{"tenor_years", pc.tenor},
                 {"type",   pc.type == VanillaSwap::Payer ? "Payer" : "Receiver"},
                 {"fixedRate0",  pc.fixedRates[0]},
                 {"fixedNom0",   pc.fixedNom[0]},
                 {"gearing",     pc.gearing},
                 {"spread",      pc.spread}},
                {{"fixedLegNPV",    ns.legNPV(0)},
                 {"floatingLegNPV", ns.legNPV(1)},
                 {"npv",            ns.NPV()},
                 {"nFixedCoupons",  (int)ns.fixedLeg().size()},
                 {"nFloatCoupons",  (int)ns.floatingLeg().size()},
                 {"fixedRate0",     ns.fixedRate()[0]},
                 {"fixedNominal0",  ns.fixedNominal()[0]},
                 {"spreadScalar",   pc.spread},
                 {"gearingScalar",  pc.gearing}});
        }
    }

    // ─── Case group C: per-coupon gearings and spreads (vector overload) ──────
    {
        const Date start = CAL.advance(EVAL, Period(2, Days));
        Schedule fixedSch = makeSchedule(start, 3, Period(1, Years));
        Schedule floatSch = makeSchedule(start, 3, Period(3, Months));

        // 3Y: 3 fixed, 12 floating
        std::vector<Real> fixedNom  = {100.0, 90.0, 80.0};
        std::vector<Real> floatNom  = {100.0, 100.0, 100.0,
                                        90.0,  90.0,  90.0,
                                        80.0,  80.0,  80.0,
                                        75.0,  75.0,  75.0};
        std::vector<Real> fixedRates = {0.03, 0.035, 0.04};
        std::vector<Real> gearings  = std::vector<Real>(12, 1.0);
        gearings[0] = 1.1; gearings[6] = 0.9;  // non-uniform gearing
        std::vector<Spread> spreads = std::vector<Spread>(12, 0.001);
        spreads[3] = 0.0015; spreads[9] = 0.0005; // non-uniform spread

        NonstandardSwap ns(Swap::Payer, fixedNom, floatNom, fixedSch,
                           fixedRates, FIXED_DC, floatSch, idx,
                           gearings, spreads, DC);
        auto dse = ext::make_shared<DiscountingSwapEngine>(ts);
        ns.setPricingEngine(dse);

        out.addCase("vector_gearing_spread",
            {{"fixedRate0",  fixedRates[0]},
             {"fixedNom0",   fixedNom[0]},
             {"gearing0",    gearings[0]},
             {"spread0",     spreads[0]}},
            {{"fixedLegNPV",    ns.legNPV(0)},
             {"floatingLegNPV", ns.legNPV(1)},
             {"npv",            ns.NPV()},
             {"nFixedCoupons",  (int)ns.fixedLeg().size()},
             {"nFloatCoupons",  (int)ns.floatingLeg().size()},
             {"fixedRate0",     ns.fixedRate()[0]},
             {"fixedNominal0",  ns.fixedNominal()[0]}});

        // Receiver variant
        NonstandardSwap ns2(Swap::Receiver, fixedNom, floatNom, fixedSch,
                            fixedRates, FIXED_DC, floatSch, idx,
                            gearings, spreads, DC);
        auto dse2 = ext::make_shared<DiscountingSwapEngine>(ts);
        ns2.setPricingEngine(dse2);
        out.addCase("vector_gearing_spread_receiver",
            {{"fixedRate0",  fixedRates[0]},
             {"fixedNom0",   fixedNom[0]}},
            {{"fixedLegNPV",    ns2.legNPV(0)},
             {"floatingLegNPV", ns2.legNPV(1)},
             {"npv",            ns2.NPV()},
             {"nFixedCoupons",  (int)ns2.fixedLeg().size()},
             {"nFloatCoupons",  (int)ns2.floatingLeg().size()}});
    }

    // ─── Case group D: intermediateCapitalExchange = true ────────────────────
    {
        const Date start = CAL.advance(EVAL, Period(2, Days));
        Schedule fixedSch = makeSchedule(start, 3, Period(1, Years));
        Schedule floatSch = makeSchedule(start, 3, Period(3, Months));

        std::vector<Real> fixedNom  = {100.0, 70.0, 50.0};  // amortising
        std::vector<Real> floatNom  = {100.0, 100.0, 100.0,
                                        70.0,  70.0,  70.0,
                                        50.0,  50.0,  50.0,
                                        40.0,  40.0,  40.0};
        std::vector<Real> fixedRates = {0.03, 0.035, 0.04};

        // With intermediateCapitalExchange
        NonstandardSwap ns(Swap::Payer, fixedNom, floatNom, fixedSch,
                           fixedRates, FIXED_DC, floatSch, idx, 1.0, 0.0, DC,
                           true /* intermediateCapitalExchange */,
                           false /* finalCapitalExchange */);
        auto dse = ext::make_shared<DiscountingSwapEngine>(ts);
        ns.setPricingEngine(dse);
        out.addCase("intermediate_capital_exchange",
            {{"intermediateCapitalExchange", true}, {"finalCapitalExchange", false}},
            {{"fixedLegNPV",    ns.legNPV(0)},
             {"floatingLegNPV", ns.legNPV(1)},
             {"npv",            ns.NPV()},
             {"nFixedCoupons",  (int)ns.fixedLeg().size()},
             {"nFloatCoupons",  (int)ns.floatingLeg().size()}});
    }

    // ─── Case group E: finalCapitalExchange = true ────────────────────────────
    {
        const Date start = CAL.advance(EVAL, Period(2, Days));
        Schedule fixedSch = makeSchedule(start, 3, Period(1, Years));
        Schedule floatSch = makeSchedule(start, 3, Period(3, Months));

        std::vector<Real> fixedNom  = {100.0, 70.0, 50.0};
        std::vector<Real> floatNom  = {100.0, 100.0, 100.0,
                                        70.0,  70.0,  70.0,
                                        50.0,  50.0,  50.0,
                                        40.0,  40.0,  40.0};
        std::vector<Real> fixedRates = {0.03, 0.035, 0.04};

        NonstandardSwap ns(Swap::Payer, fixedNom, floatNom, fixedSch,
                           fixedRates, FIXED_DC, floatSch, idx, 1.0, 0.0, DC,
                           false /* intermediateCapitalExchange */,
                           true /* finalCapitalExchange */);
        auto dse = ext::make_shared<DiscountingSwapEngine>(ts);
        ns.setPricingEngine(dse);
        out.addCase("final_capital_exchange",
            {{"intermediateCapitalExchange", false}, {"finalCapitalExchange", true}},
            {{"fixedLegNPV",    ns.legNPV(0)},
             {"floatingLegNPV", ns.legNPV(1)},
             {"npv",            ns.NPV()},
             {"nFixedCoupons",  (int)ns.fixedLeg().size()},
             {"nFloatCoupons",  (int)ns.floatingLeg().size()}});
    }

    // ─── Case group F: both capital exchanges ────────────────────────────────
    {
        const Date start = CAL.advance(EVAL, Period(2, Days));
        Schedule fixedSch = makeSchedule(start, 3, Period(1, Years));
        Schedule floatSch = makeSchedule(start, 3, Period(3, Months));

        std::vector<Real> fixedNom  = {100.0, 70.0, 50.0};
        std::vector<Real> floatNom  = {100.0, 100.0, 100.0,
                                        70.0,  70.0,  70.0,
                                        50.0,  50.0,  50.0,
                                        40.0,  40.0,  40.0};
        std::vector<Real> fixedRates = {0.03, 0.035, 0.04};

        NonstandardSwap ns(Swap::Payer, fixedNom, floatNom, fixedSch,
                           fixedRates, FIXED_DC, floatSch, idx, 1.0, 0.0, DC,
                           true /* intermediateCapitalExchange */,
                           true /* finalCapitalExchange */);
        auto dse = ext::make_shared<DiscountingSwapEngine>(ts);
        ns.setPricingEngine(dse);
        out.addCase("both_capital_exchanges",
            {{"intermediateCapitalExchange", true}, {"finalCapitalExchange", true}},
            {{"fixedLegNPV",    ns.legNPV(0)},
             {"floatingLegNPV", ns.legNPV(1)},
             {"npv",            ns.NPV()},
             {"nFixedCoupons",  (int)ns.fixedLeg().size()},
             {"nFloatCoupons",  (int)ns.floatingLeg().size()}});
    }

    // ─── Case group G: varying flat rates  ────────────────────────────────────
    // Single-notional, varying fixed rates (scalar gearing), varied tenors.
    {
        struct GC { double rate; int tenor; double spread; };
        std::vector<GC> gcs = {
            {0.02, 2, 0.0},
            {0.03, 3, 0.0},
            {0.04, 5, 0.0},
            {0.05, 5, 0.002},
            {0.06, 7, 0.0},
            {0.035, 10, 0.001},
        };
        const Date start = CAL.advance(EVAL, Period(2, Days));
        for (std::size_t i = 0; i < gcs.size(); ++i) {
            auto& gc = gcs[i];
            Schedule fixedSch = makeSchedule(start, gc.tenor, Period(1, Years));
            Schedule floatSch = makeSchedule(start, gc.tenor, Period(3, Months));
            int nFixed = fixedSch.size() - 1;
            int nFloat = floatSch.size() - 1;
            std::vector<Real> fixedNom(nFixed, 100.0);
            std::vector<Real> floatNom(nFloat, 100.0);
            std::vector<Real> fixedRates(nFixed, gc.rate);

            NonstandardSwap ns(Swap::Payer, fixedNom, floatNom, fixedSch,
                               fixedRates, FIXED_DC, floatSch, idx, 1.0, gc.spread, DC);
            auto dse = ext::make_shared<DiscountingSwapEngine>(ts);
            ns.setPricingEngine(dse);

            std::ostringstream name;
            name << "flat_rate_" << i;
            out.addCase(name.str(),
                {{"tenor_years", gc.tenor},
                 {"rate",        gc.rate},
                 {"spread",      gc.spread}},
                {{"fixedLegNPV",    ns.legNPV(0)},
                 {"floatingLegNPV", ns.legNPV(1)},
                 {"npv",            ns.NPV()},
                 {"nFixedCoupons",  (int)ns.fixedLeg().size()},
                 {"nFloatCoupons",  (int)ns.floatingLeg().size()}});
        }
    }

    out.write();
    return 0;
}
