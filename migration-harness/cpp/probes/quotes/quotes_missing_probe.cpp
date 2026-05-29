// migration-harness/cpp/probes/quotes/quotes_missing_probe.cpp
//
// Reference values for the three ql/quotes classes that require building real
// QuantLib objects (curve / index / swap / fixing history) and therefore cannot
// be reproduced from a closed-form alone:
//
//   * EurodollarFuturesImpliedStdDevQuote (eurodollarfuturesquote.{hpp,cpp})
//       — inverts blackFormulaImpliedStdDev on (100-strike, 100-forward, price).
//         We emit one call-side case (strike < forward => uses call price, Put)
//         and one put-side case (strike > forward => uses put price, Call).
//   * ForwardSwapQuote (forwardswapquote.{hpp,cpp})
//       — fair fixed rate of a forward-starting EuriborSwapIsdaFixA swap on a
//         flat-forward curve, with and without a spread.
//   * LastFixingQuote (lastfixingquote.{hpp,cpp})
//       — index fixing at min(lastFixingDate, evaluationDate) from a fixing
//         history. We emit the case where evalDate is after the last fixing
//         (=> referenceDate == lastFixingDate) and where evalDate sits between
//         two fixings (=> referenceDate == evalDate).
//
// DerivedQuote is NOT probed here: its expected values (x->a*x+b, x->x*x over a
// SimpleQuote) are transcribed inline in the Java test with a
// derivedquote.hpp citation (deterministic closed form, EXACT tier).
//
// The Java test rebuilds the identical objects, instantiates the same quotes,
// and compares each scalar at the documented tolerance tier.

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/quotes/simplequote.hpp>
#include <ql/quotes/eurodollarfuturesquote.hpp>
#include <ql/quotes/forwardswapquote.hpp>
#include <ql/quotes/lastfixingquote.hpp>
#include <ql/indexes/swap/euriborswap.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/time/daycounters/actualactual.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("quotes/quotes_missing",
                        QL_VERSION, "quotes_missing_probe");

    // ==================================================================
    // EurodollarFuturesImpliedStdDevQuote
    // ==================================================================
    // strike_ = 100 - strike ; forwardValue = 100 - forward->value().
    // call-side: strike < forward  => strike_ > forwardValue is FALSE
    //            => uses callPrice, inverts as Option::Put.
    // put-side : strike > forward  => strike_ > forwardValue is TRUE
    //            => uses putPrice,  inverts as Option::Call.
    {
        // forward future price 95.0 (rate 5.0); strike future price 95.5
        // => strikeRate = 4.5 < forwardRate 5.0 => call-side (Option::Put).
        const Real strike = 95.5;
        auto forward   = ext::make_shared<SimpleQuote>(95.0);
        auto callPrice = ext::make_shared<SimpleQuote>(0.30);
        auto putPrice  = ext::make_shared<SimpleQuote>(0.55);
        Handle<Quote> fH(forward), cH(callPrice), pH(putPrice);
        EurodollarFuturesImpliedStdDevQuote q(fH, cH, pH, strike, 0.15, 1.0e-8, 100);
        json inp{
            {"strike",    strike},
            {"forward",   95.0},
            {"callPrice", 0.30},
            {"putPrice",  0.55},
            {"guess",     0.15},
            {"accuracy",  1.0e-8},
            {"side",      "call_price_used_Put"}
        };
        out.addCase("eurodollar_call_side", inp, json(q.value()));
    }
    {
        // forward future price 95.0 (rate 5.0); strike future price 94.0
        // => strikeRate = 6.0 > forwardRate 5.0 => put-side (Option::Call).
        const Real strike = 94.0;
        auto forward   = ext::make_shared<SimpleQuote>(95.0);
        auto callPrice = ext::make_shared<SimpleQuote>(0.45);
        auto putPrice  = ext::make_shared<SimpleQuote>(0.20);
        Handle<Quote> fH(forward), cH(callPrice), pH(putPrice);
        EurodollarFuturesImpliedStdDevQuote q(fH, cH, pH, strike, 0.15, 1.0e-8, 100);
        json inp{
            {"strike",    strike},
            {"forward",   95.0},
            {"callPrice", 0.45},
            {"putPrice",  0.20},
            {"guess",     0.15},
            {"accuracy",  1.0e-8},
            {"side",      "put_price_used_Call"}
        };
        out.addCase("eurodollar_put_side", inp, json(q.value()));
    }

    // ==================================================================
    // ForwardSwapQuote
    // ==================================================================
    {
        const Date evalDate(15, June, 2020);
        Settings::instance().evaluationDate() = evalDate;

        DayCounter dc = Actual360();
        Handle<YieldTermStructure> curve(
            ext::make_shared<FlatForward>(evalDate, 0.03, dc));

        // 5Y EuriborSwapIsdaFixA on the flat curve, 2Y forward start.
        auto swapIndex = ext::make_shared<EuriborSwapIsdaFixA>(
            Period(5, Years), curve);
        Period fwdStart(2, Years);

        // no spread
        {
            Handle<Quote> noSpread;
            ForwardSwapQuote q(swapIndex, noSpread, fwdStart);
            json inp{
                {"evalDate",   "2020-06-15"},
                {"flatRate",   0.03},
                {"swapTenor",  "5Y"},
                {"fwdStart",   "2Y"},
                {"spread",     nullptr}
            };
            json exp{
                {"value",      q.value()},
                {"valueDate",  static_cast<long>(q.valueDate().serialNumber())},
                {"startDate",  static_cast<long>(q.startDate().serialNumber())},
                {"fixingDate", static_cast<long>(q.fixingDate().serialNumber())}
            };
            out.addCase("forward_swap_no_spread", inp, exp);
        }
        // with spread = 10bp
        {
            auto spread = ext::make_shared<SimpleQuote>(0.0010);
            Handle<Quote> spreadH(spread);
            ForwardSwapQuote q(swapIndex, spreadH, fwdStart);
            json inp{
                {"evalDate",   "2020-06-15"},
                {"flatRate",   0.03},
                {"swapTenor",  "5Y"},
                {"fwdStart",   "2Y"},
                {"spread",     0.0010}
            };
            json exp{
                {"value",      q.value()},
                {"valueDate",  static_cast<long>(q.valueDate().serialNumber())},
                {"startDate",  static_cast<long>(q.startDate().serialNumber())},
                {"fixingDate", static_cast<long>(q.fixingDate().serialNumber())}
            };
            out.addCase("forward_swap_spread_10bp", inp, exp);
        }
    }

    // ==================================================================
    // LastFixingQuote
    // ==================================================================
    {
        DayCounter dc = Actual360();
        Handle<YieldTermStructure> curve(
            ext::make_shared<FlatForward>(Date(1, January, 2020), 0.02, dc));

        // 6M Euribor with a small fixing history.
        auto euribor = ext::make_shared<Euribor>(Period(6, Months), curve);
        euribor->clearFixings();
        // Three historical fixings on business days.
        const Date f1(13, January, 2020);
        const Date f2(13, February, 2020);
        const Date f3(13, March, 2020);
        euribor->addFixing(f1, 0.0150);
        euribor->addFixing(f2, 0.0160);
        euribor->addFixing(f3, 0.0170);

        // Case A: evalDate after last fixing => referenceDate == lastDate (f3).
        {
            Settings::instance().evaluationDate() = Date(20, March, 2020);
            LastFixingQuote q(euribor);
            json inp{
                {"index",       "Euribor6M"},
                {"evalDate",    "2020-03-20"},
                {"lastFixing",  "2020-03-13"},
                {"lastValue",   0.0170}
            };
            json exp{
                {"value",         q.value()},
                {"referenceDate", static_cast<long>(q.referenceDate().serialNumber())},
                {"isValid",       q.isValid()}
            };
            out.addCase("last_fixing_eval_after_last", inp, exp);
        }
        // Case B: evalDate between f2 and f3 => referenceDate == evalDate
        //         (min(f3, evalDate) = evalDate), reads fixing at evalDate.
        //         Pick evalDate == f2 so a stored fixing exists there.
        {
            Settings::instance().evaluationDate() = f2;  // 2020-02-13
            LastFixingQuote q(euribor);
            json inp{
                {"index",       "Euribor6M"},
                {"evalDate",    "2020-02-13"},
                {"lastFixing",  "2020-03-13"},
                {"valueAtEval", 0.0160}
            };
            json exp{
                {"value",         q.value()},
                {"referenceDate", static_cast<long>(q.referenceDate().serialNumber())},
                {"isValid",       q.isValid()}
            };
            out.addCase("last_fixing_eval_between", inp, exp);
        }
        euribor->clearFixings();
    }

    out.write();
    return 0;
}
