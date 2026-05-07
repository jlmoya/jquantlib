// migration-harness/cpp/probes/instruments/floatfloat_swap_probe.cpp
//
// Phase 2j.5 Track B.1 — FloatFloatSwap structural fingerprint.
// Oracle: C++ QuantLib v1.42.1.
//
// Builds FloatFloatSwap instances with deterministic IborIndex legs
// (both legs Euribor-based, different tenors) with spreads/gearings,
// prices each under DiscountingSwapEngine, and emits:
//   - leg1NPV, leg2NPV, npv
//   - nLeg1Coupons, nLeg2Coupons
//
// NOTE: cap/floor cases are omitted from NPV probing because
//       CappedFlooredIborCoupon.amount() requires a pricer which
//       DiscountingSwapEngine doesn't set; the Gaussian1d engine
//       accesses cappedRate/flooredRate from Arguments instead.
//       Cap/floor correctness is verified structurally (nLeg counts).
//
// Fixture: eval=2026-01-15, FlatForward 4% Continuous Actual365Fixed,
// TARGET calendar, Euribor3M + Euribor6M indexes.
// Only IborIndex legs tested (SwapSpreadIndex not in Java).

#include <ql/version.hpp>

#include <ql/cashflows/cashflows.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/floatfloatswap.hpp>
#include <ql/pricingengines/swap/discountingswapengine.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
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
const Real FLAT_RATE = 0.04;

Handle<YieldTermStructure> makeTS() {
    return Handle<YieldTermStructure>(
        ext::make_shared<FlatForward>(EVAL, FLAT_RATE, DC, Continuous, Annual));
}

Schedule makeSchedule(const Date& start, int years, const Period& tenor) {
    Date end = CAL.advance(start, Period(years, Years));
    return Schedule(start, end, tenor, CAL,
                    ModifiedFollowing, ModifiedFollowing,
                    DateGeneration::Forward, false);
}

struct SwapResult {
    double leg1NPV;
    double leg2NPV;
    double npv;
    int nLeg1;
    int nLeg2;
};

SwapResult priceSwap(FloatFloatSwap& sw, const Handle<YieldTermStructure>& ts) {
    auto engine = ext::make_shared<DiscountingSwapEngine>(ts);
    sw.setPricingEngine(engine);
    SwapResult r;
    r.npv    = sw.NPV();
    r.leg1NPV = sw.legNPV(0);
    r.leg2NPV = sw.legNPV(1);
    r.nLeg1  = (int)sw.leg1().size();
    r.nLeg2  = (int)sw.leg2().size();
    return r;
}

// Return only structural metrics without calling NPV (for cap/floor cases).
SwapResult structOnly(FloatFloatSwap& sw) {
    SwapResult r;
    r.leg1NPV = 0.0;
    r.leg2NPV = 0.0;
    r.npv     = 0.0;
    r.nLeg1   = (int)sw.leg1().size();
    r.nLeg2   = (int)sw.leg2().size();
    return r;
}

} // namespace

int main() {
    Settings::instance().evaluationDate() = EVAL;

    ReferenceWriter out("instruments/floatfloat_swap",
                        QL_VERSION, "floatfloat_swap_probe");

    auto ts   = makeTS();
    auto idx3m = ext::make_shared<Euribor3M>(ts);
    auto idx6m = ext::make_shared<Euribor6M>(ts);

    const Date start = CAL.advance(EVAL, Period(2, Days));

    // ─── Group A: Payer/Receiver, scalar nominals, same 3M index ────────────
    // Leg1 = 3M schedule, Leg2 = 3M schedule. No caps/floors.
    {
        struct AC {
            Swap::Type type;
            int tenor;
            Real nom1, nom2;
            Real g1, s1, g2, s2;
        };
        std::vector<AC> cases = {
            {Swap::Payer,    3, 100.0, 100.0, 1.0, 0.0,    1.0, 0.0   },
            {Swap::Receiver, 3, 100.0, 100.0, 1.0, 0.001,  1.0, 0.0   },
            {Swap::Payer,    5, 200.0, 150.0, 1.0, 0.0,    1.0, 0.002  },
            {Swap::Receiver, 5, 150.0, 100.0, 0.5, 0.0,    2.0, 0.0   },
            {Swap::Payer,    2, 100.0, 100.0, 1.2, 0.001,  0.8, -0.001},
        };
        for (std::size_t i = 0; i < cases.size(); ++i) {
            const auto& c = cases[i];
            Schedule sch1 = makeSchedule(start, c.tenor, Period(3, Months));
            Schedule sch2 = makeSchedule(start, c.tenor, Period(3, Months));

            FloatFloatSwap sw(c.type, c.nom1, c.nom2,
                              sch1, idx3m, DC,
                              sch2, idx3m, DC,
                              false, false,
                              c.g1, c.s1, Null<Real>(), Null<Real>(),
                              c.g2, c.s2, Null<Real>(), Null<Real>());
            auto r = priceSwap(sw, ts);

            std::ostringstream name;
            name << "basic_same3m_" << i;
            out.addCase(name.str(),
                {{"type",  c.type == Swap::Payer ? "Payer" : "Receiver"},
                 {"tenor", c.tenor},
                 {"nom1",  c.nom1},
                 {"nom2",  c.nom2},
                 {"g1",    c.g1},
                 {"s1",    c.s1},
                 {"g2",    c.g2},
                 {"s2",    c.s2}},
                {{"leg1NPV", r.leg1NPV},
                 {"leg2NPV", r.leg2NPV},
                 {"npv",     r.npv},
                 {"nLeg1",   r.nLeg1},
                 {"nLeg2",   r.nLeg2}});
        }
    }

    // ─── Group B: Mixed Ibor indexes (3M leg1 + 6M leg2) ────────────────────
    {
        struct BC { Swap::Type type; int tenor; Real g1, s1, g2, s2; };
        std::vector<BC> cases = {
            {Swap::Payer,    3, 1.0, 0.0,   1.0, 0.0  },
            {Swap::Receiver, 5, 1.0, 0.001, 1.0,-0.001},
            {Swap::Payer,    5, 0.9, 0.002, 1.1, 0.001},
            {Swap::Payer,    7, 1.0, 0.0,   1.0, 0.0  },
            {Swap::Receiver, 2, 1.5, 0.0,   0.5, 0.001},
        };
        for (std::size_t i = 0; i < cases.size(); ++i) {
            const auto& c = cases[i];
            Schedule sch1 = makeSchedule(start, c.tenor, Period(3, Months));
            Schedule sch2 = makeSchedule(start, c.tenor, Period(6, Months));

            FloatFloatSwap sw(c.type, 100.0, 100.0,
                              sch1, idx3m, DC,
                              sch2, idx6m, DC,
                              false, false,
                              c.g1, c.s1, Null<Real>(), Null<Real>(),
                              c.g2, c.s2, Null<Real>(), Null<Real>());
            auto r = priceSwap(sw, ts);

            std::ostringstream name;
            name << "mixed_3m6m_" << i;
            out.addCase(name.str(),
                {{"type",  c.type == Swap::Payer ? "Payer" : "Receiver"},
                 {"tenor", c.tenor},
                 {"g1",    c.g1},
                 {"s1",    c.s1},
                 {"g2",    c.g2},
                 {"s2",    c.s2}},
                {{"leg1NPV", r.leg1NPV},
                 {"leg2NPV", r.leg2NPV},
                 {"npv",     r.npv},
                 {"nLeg1",   r.nLeg1},
                 {"nLeg2",   r.nLeg2}});
        }
    }

    // ─── Group C: Vector nominals, amortising ────────────────────────────────
    {
        // 3Y, 3M leg1 (12 coupons) and 6M leg2 (6 coupons)
        Schedule sch1 = makeSchedule(start, 3, Period(3, Months));
        Schedule sch2 = makeSchedule(start, 3, Period(6, Months));
        std::vector<Real> nom1 = {100,100,100, 80,80,80, 60,60,60, 50,50,50};
        std::vector<Real> nom2 = {100,100, 80,80, 60,60};

        FloatFloatSwap sw(Swap::Payer, nom1, nom2,
                          sch1, idx3m, DC,
                          sch2, idx6m, DC,
                          false, false,
                          {}, {}, {}, {},
                          {}, {}, {}, {});
        auto r = priceSwap(sw, ts);
        out.addCase("vector_nom_amortising",
            {{"nom1_0", nom1[0]}, {"nom2_0", nom2[0]},
             {"n1", (int)nom1.size()}, {"n2", (int)nom2.size()}},
            {{"leg1NPV", r.leg1NPV},
             {"leg2NPV", r.leg2NPV},
             {"npv",     r.npv},
             {"nLeg1",   r.nLeg1},
             {"nLeg2",   r.nLeg2}});
    }

    // ─── Group D: Vector nominals, accreting ────────────────────────────────
    {
        Schedule sch1 = makeSchedule(start, 3, Period(3, Months));
        Schedule sch2 = makeSchedule(start, 3, Period(3, Months));
        std::vector<Real> nom1 = {50,50,50, 75,75,75, 100,100,100, 100,100,100};
        std::vector<Real> nom2 = {50,50,50, 75,75,75, 100,100,100, 100,100,100};

        FloatFloatSwap sw(Swap::Receiver, nom1, nom2,
                          sch1, idx3m, DC,
                          sch2, idx3m, DC,
                          false, false,
                          {}, {}, {}, {},
                          {}, {}, {}, {});
        auto r = priceSwap(sw, ts);
        out.addCase("vector_nom_accreting",
            {{"nom1_0", nom1[0]}, {"n1", (int)nom1.size()}},
            {{"leg1NPV", r.leg1NPV},
             {"leg2NPV", r.leg2NPV},
             {"npv",     r.npv},
             {"nLeg1",   r.nLeg1},
             {"nLeg2",   r.nLeg2}});
    }

    // ─── Group E: Per-coupon gearings / spreads ───────────────────────────────
    {
        Schedule sch1 = makeSchedule(start, 2, Period(3, Months));
        Schedule sch2 = makeSchedule(start, 2, Period(3, Months));
        int n = sch1.size() - 1;  // 8

        std::vector<Real> g1(n, 1.0); g1[0] = 1.1; g1[4] = 0.9;
        std::vector<Real> s1(n, 0.001); s1[3] = 0.002;
        std::vector<Real> g2(n, 1.0);
        std::vector<Real> s2(n, 0.0); s2[2] = -0.001;

        FloatFloatSwap sw(Swap::Payer,
                          std::vector<Real>(n, 100.0),
                          std::vector<Real>(n, 100.0),
                          sch1, idx3m, DC,
                          sch2, idx3m, DC,
                          false, false,
                          g1, s1, {}, {},
                          g2, s2, {}, {});
        auto r = priceSwap(sw, ts);
        out.addCase("vector_gearing_spread",
            {{"n", n}, {"g1_0", g1[0]}, {"s1_0", s1[0]}, {"g2_0", g2[0]}},
            {{"leg1NPV", r.leg1NPV},
             {"leg2NPV", r.leg2NPV},
             {"npv",     r.npv},
             {"nLeg1",   r.nLeg1},
             {"nLeg2",   r.nLeg2}});
    }

    // ─── Group F: finalCapitalExchange = true (flat nominal) ─────────────────
    {
        Schedule sch1 = makeSchedule(start, 3, Period(3, Months));
        Schedule sch2 = makeSchedule(start, 3, Period(3, Months));

        FloatFloatSwap sw(Swap::Payer, 100.0, 100.0,
                          sch1, idx3m, DC,
                          sch2, idx3m, DC,
                          false /* intermediate */, true /* final */,
                          1.0, 0.0, Null<Real>(), Null<Real>(),
                          1.0, 0.0, Null<Real>(), Null<Real>());
        auto r = priceSwap(sw, ts);
        out.addCase("final_capital_exchange",
            {{"finalCapitalExchange", 1}},
            {{"leg1NPV", r.leg1NPV},
             {"leg2NPV", r.leg2NPV},
             {"npv",     r.npv},
             {"nLeg1",   r.nLeg1},
             {"nLeg2",   r.nLeg2}});
    }

    // ─── Group G: intermediateCapitalExchange with amortising notionals ───────
    {
        Schedule sch1 = makeSchedule(start, 3, Period(3, Months));
        Schedule sch2 = makeSchedule(start, 3, Period(3, Months));
        std::vector<Real> nom1 = {100,100,100, 80,80,80, 60,60,60, 50,50,50};
        std::vector<Real> nom2 = {100,100,100, 80,80,80, 60,60,60, 50,50,50};

        FloatFloatSwap sw(Swap::Payer, nom1, nom2,
                          sch1, idx3m, DC,
                          sch2, idx3m, DC,
                          true /* intermediate */, false /* final */,
                          {}, {}, {}, {},
                          {}, {}, {}, {});
        auto r = priceSwap(sw, ts);
        out.addCase("intermediate_capital_exchange",
            {{"intermediateCapitalExchange", 1}},
            {{"leg1NPV", r.leg1NPV},
             {"leg2NPV", r.leg2NPV},
             {"npv",     r.npv},
             {"nLeg1",   r.nLeg1},
             {"nLeg2",   r.nLeg2}});
    }

    // ─── Group H: Both capital exchanges ────────────────────────────────────
    {
        Schedule sch1 = makeSchedule(start, 3, Period(3, Months));
        Schedule sch2 = makeSchedule(start, 3, Period(3, Months));
        std::vector<Real> nom1 = {100,100,100, 80,80,80, 60,60,60, 50,50,50};
        std::vector<Real> nom2 = {100,100,100, 80,80,80, 60,60,60, 50,50,50};

        FloatFloatSwap sw(Swap::Payer, nom1, nom2,
                          sch1, idx3m, DC,
                          sch2, idx3m, DC,
                          true /* intermediate */, true /* final */,
                          {}, {}, {}, {},
                          {}, {}, {}, {});
        auto r = priceSwap(sw, ts);
        out.addCase("both_capital_exchanges",
            {{"intermediateCapitalExchange", 1}, {"finalCapitalExchange", 1}},
            {{"leg1NPV", r.leg1NPV},
             {"leg2NPV", r.leg2NPV},
             {"npv",     r.npv},
             {"nLeg1",   r.nLeg1},
             {"nLeg2",   r.nLeg2}});
    }

    // ─── Group I: Various tenors (3M/3M same index) ────────────────────────
    {
        std::vector<int> tenors = {1, 2, 5, 7, 10};
        for (int tenor : tenors) {
            Schedule sch1 = makeSchedule(start, tenor, Period(3, Months));
            Schedule sch2 = makeSchedule(start, tenor, Period(3, Months));
            int n1 = sch1.size() - 1;
            int n2 = sch2.size() - 1;

            FloatFloatSwap sw(Swap::Payer, 100.0, 100.0,
                              sch1, idx3m, DC,
                              sch2, idx3m, DC,
                              false, false,
                              1.0, 0.001, Null<Real>(), Null<Real>(),
                              1.0, 0.0,   Null<Real>(), Null<Real>());
            auto r = priceSwap(sw, ts);

            std::ostringstream name;
            name << "tenor_" << tenor << "y";
            out.addCase(name.str(),
                {{"tenor_years", tenor}, {"n1", n1}, {"n2", n2}},
                {{"leg1NPV", r.leg1NPV},
                 {"leg2NPV", r.leg2NPV},
                 {"npv",     r.npv},
                 {"nLeg1",   r.nLeg1},
                 {"nLeg2",   r.nLeg2}});
        }
    }

    // ─── Group J: Cap/floor leg1 — structural only (nLeg counts) ────────────
    // We only record nLeg1/nLeg2 (no NPV) to avoid pricer-not-set error.
    {
        struct CF { Real cap; Real floor; };
        std::vector<CF> cases = {
            {0.06,        Null<Real>()},   // cap only
            {Null<Real>(), 0.02       },   // floor only
            {0.07,         0.02       },   // collar
        };
        for (std::size_t i = 0; i < cases.size(); ++i) {
            const auto& c = cases[i];
            Schedule sch1 = makeSchedule(start, 3, Period(3, Months));
            Schedule sch2 = makeSchedule(start, 3, Period(3, Months));

            FloatFloatSwap sw(Swap::Payer, 100.0, 100.0,
                              sch1, idx3m, DC,
                              sch2, idx3m, DC,
                              false, false,
                              1.0, 0.0, c.cap, c.floor,
                              1.0, 0.0, Null<Real>(), Null<Real>());
            auto r = structOnly(sw);

            bool hasCap   = (c.cap   != Null<Real>());
            bool hasFloor = (c.floor != Null<Real>());
            std::ostringstream name;
            name << "capfloor_leg1_struct_" << i;
            out.addCase(name.str(),
                {{"hasCap",   hasCap   ? 1 : 0},
                 {"hasFloor", hasFloor ? 1 : 0}},
                {{"nLeg1", r.nLeg1},
                 {"nLeg2", r.nLeg2}});
        }
    }

    // ─── Group K: Cap/floor leg2 — structural only ───────────────────────────
    {
        struct CF { Real cap; Real floor; };
        std::vector<CF> cases = {
            {0.06,        Null<Real>()},
            {Null<Real>(), 0.02       },
            {0.07,         0.02       },
        };
        for (std::size_t i = 0; i < cases.size(); ++i) {
            const auto& c = cases[i];
            Schedule sch1 = makeSchedule(start, 3, Period(3, Months));
            Schedule sch2 = makeSchedule(start, 3, Period(3, Months));

            FloatFloatSwap sw(Swap::Receiver, 100.0, 100.0,
                              sch1, idx3m, DC,
                              sch2, idx3m, DC,
                              false, false,
                              1.0, 0.0, Null<Real>(), Null<Real>(),
                              1.0, 0.0, c.cap, c.floor);
            auto r = structOnly(sw);

            bool hasCap   = (c.cap   != Null<Real>());
            bool hasFloor = (c.floor != Null<Real>());
            std::ostringstream name;
            name << "capfloor_leg2_struct_" << i;
            out.addCase(name.str(),
                {{"hasCap",   hasCap   ? 1 : 0},
                 {"hasFloor", hasFloor ? 1 : 0}},
                {{"nLeg1", r.nLeg1},
                 {"nLeg2", r.nLeg2}});
        }
    }

    out.write();
    return 0;
}
