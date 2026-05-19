// migration-harness/cpp/probes/instruments/callable_bond_black_engine_probe.cpp
// Phase 5e.5b-CFC-d-271 — capture intermediate state of
// BlackCallableFixedRateBondEngine::calculate() under the testBlackEngine
// fixture so we can pinpoint where Java's BlackCallableZeroCouponBondEngine
// (a thin subclass of BlackCallableFixedRateBondEngine) diverges from C++
// by ~0.81 on cleanPrice. Replicates exactly the engine's internal compute
// chain (CashFlows::npv at settle / referenceDate / exerciseDate; spotIncome
// loop; CashFlows::yield / duration; volatility lookup; blackFormula).

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/cashflows/cashflows.hpp>
#include <ql/experimental/callablebonds/callablebond.hpp>
#include <ql/experimental/callablebonds/blackcallablebondengine.hpp>
#include <ql/experimental/callablebonds/callablebondconstantvol.hpp>
#include <ql/instruments/bonds/zerocouponbond.hpp>
#include <ql/pricingengines/blackformula.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/shared_ptr.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/daycounters/thirty360.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("instruments/callable_bond_black_engine",
                        QL_VERSION,
                        "callable_bond_black_engine_probe");

    // Mirror testBlackEngine fixture from test-suite/callablebonds.cpp.
    Calendar calendar = TARGET();
    DayCounter dayCounter = Actual365Fixed();
    BusinessDayConvention rollingConvention = ModifiedFollowing;
    Date today(20, September, 2022);
    Settings::instance().evaluationDate() = today;
    Date settlement = calendar.advance(today, 2, Days);

    Real flatRate = 0.03;
    Real vol = 0.3;
    Real faceAmount = 10000.0;
    Real redemption = 100.0;
    Natural settlementDays = 3;

    Handle<YieldTermStructure> termStructure(
        ext::make_shared<FlatForward>(settlement, flatRate, dayCounter));

    Date issueDate = calendar.adjust(today - 100 * Days);
    Date maturityDate = calendar.advance(issueDate, 10, Years);
    Date callDate = calendar.advance(issueDate, 4, Years);

    CallabilitySchedule cbs;
    cbs.push_back(ext::make_shared<Callability>(
        Bond::Price(100.0, Bond::Price::Clean),
        Callability::Call,
        callDate));

    CallableZeroCouponBond bond(settlementDays, faceAmount, calendar,
                                maturityDate, Thirty360(Thirty360::BondBasis),
                                rollingConvention, redemption,
                                issueDate, cbs);

    Handle<Quote> volH(ext::make_shared<SimpleQuote>(vol));
    auto engine = ext::make_shared<BlackCallableZeroCouponBondEngine>(
        volH, termStructure);
    bond.setPricingEngine(engine);

    // Trigger calculation so engine internals are populated.
    Real cleanPrice = bond.cleanPrice();
    Real dirtyPrice = bond.dirtyPrice();
    Real npvBond = bond.NPV();
    Real settlementValueBond = bond.settlementValue();
    Date bondSettlement = bond.settlementDate();

    // --- Manually replicate engine internals exactly ---
    // The engine uses `arguments_.settlementDate` which IS the bond's
    // settlement date (settlementDays=3 ⇒ Sep 23), NOT vars.settlement
    // (Sep 22 = curve refDate). Replicate that exactly.
    const Leg& fixedLeg = bond.cashflows();
    Date engineSettle = bondSettlement;

    // CashFlows::npv at settle, at curve reference date, at exerciseDate.
    // Engine uses the 4-arg overload — npvDate defaults to settlementDate.
    Real value = CashFlows::npv(fixedLeg, **termStructure, false, engineSettle);
    Real npv   = CashFlows::npv(fixedLeg, **termStructure, false,
                                termStructure->referenceDate());
    Real fwdNpv = CashFlows::npv(fixedLeg, **termStructure, false, callDate);

    // spotIncome — zero coupon, so income should be 0; capture loop trace for sanity.
    Real income = 0.0;
    json cf_trace = json::array();
    for (Size i = 0; i + 1 < fixedLeg.size(); ++i) {
        const auto& c = fixedLeg[i];
        bool occSettle = c->hasOccurred(engineSettle, false);
        bool occCall = c->hasOccurred(callDate, false);
        Real amt = c->amount();
        Real df = termStructure->discount(c->date());
        cf_trace.push_back({
            {"idx", (int)i},
            {"date_serial", c->date().serialNumber()},
            {"amount", amt},
            {"discount_to_cf_date", df},
            {"hasOccurred_settle", occSettle},
            {"hasOccurred_call", occCall}
        });
        if (!occSettle) {
            if (occCall) income += amt * df;
            else break;
        }
    }
    Real spotIncomeVal = income / termStructure->discount(engineSettle);

    Real dfSettle = termStructure->discount(engineSettle);
    Real dfCall = termStructure->discount(callDate);
    Real fwdCashPrice = (value - spotIncomeVal) / dfCall;
    Real cashStrike = 100.0 * faceAmount / 100.0;

    // forwardPriceVolatility chain: ytm @ exerciseDate, duration @ exerciseDate.
    DayCounter paymentDc = Thirty360(Thirty360::BondBasis);
    // CallableZeroCouponBond sets frequency_ = Once (==0); engine remaps
    // NoFrequency|Once → Annual.
    Frequency freq = Once;
    if (freq == NoFrequency || freq == Once) freq = Annual;
    Rate fwdYtm = CashFlows::yield(fixedLeg, fwdNpv, paymentDc,
                                   Compounded, freq, false, callDate);
    InterestRate fwdRate(fwdYtm, paymentDc, Compounded, freq);
    Time fwdDur = CashFlows::duration(fixedLeg, fwdRate,
                                      Duration::Modified, false, callDate);

    // CallableBondConstantVolatility wraps the quote with Actual365Fixed +
    // NullCalendar + 0 settlement days at construction (see ctor in cpp file).
    DayCounter volDc = Actual365Fixed();
    NullCalendar nullCal;
    Date volRefDate = nullCal.adjust(today);
    Time exerciseTime = volDc.yearFraction(volRefDate, callDate);
    Time maturityTime = volDc.yearFraction(volRefDate, maturityDate);
    // For CallableBondConstantVolatility the lookup just returns the quote val.
    Volatility yieldVol = vol;
    Volatility fwdPriceVol = yieldVol * fwdDur * fwdYtm;
    Real bsVol = fwdPriceVol * std::sqrt(exerciseTime);

    Option::Type type = Option::Call;
    Real embeddedOptionValue =
        blackFormula(type, cashStrike, fwdCashPrice, bsVol);

    Real discountToSettlement = dfCall / dfSettle;
    Real expectedValue       = npv   - embeddedOptionValue * dfCall;
    Real expectedSettlement  = value - embeddedOptionValue * discountToSettlement;

    json inp = {
        {"today_serial", today.serialNumber()},
        {"settlement_serial", settlement.serialNumber()},
        {"bond_settlement_serial", bondSettlement.serialNumber()},
        {"issueDate_serial", issueDate.serialNumber()},
        {"maturityDate_serial", maturityDate.serialNumber()},
        {"callDate_serial", callDate.serialNumber()},
        {"flatRate", flatRate},
        {"vol", vol},
        {"faceAmount", faceAmount},
        {"redemption", redemption},
        {"settlementDays", (int)settlementDays}
    };
    json exp = {
        {"cleanPrice", cleanPrice},
        {"dirtyPrice", dirtyPrice},
        {"bond_NPV", npvBond},
        {"bond_settlementValue", settlementValueBond},
        {"engine_value",     value},
        {"engine_npv",       npv},
        {"engine_fwdNpv",    fwdNpv},
        {"engine_spotIncome", spotIncomeVal},
        {"engine_df_settle", dfSettle},
        {"engine_df_call",   dfCall},
        {"engine_fwdCashPrice", fwdCashPrice},
        {"engine_cashStrike",   cashStrike},
        {"engine_fwdYtm",   fwdYtm},
        {"engine_fwdDur",   fwdDur},
        {"engine_freq_used", (int)freq},
        {"engine_exerciseTime", exerciseTime},
        {"engine_maturityTime", maturityTime},
        {"engine_volRefDate_serial", volRefDate.serialNumber()},
        {"engine_yieldVol", yieldVol},
        {"engine_fwdPriceVol", fwdPriceVol},
        {"engine_bsVolArg", bsVol},
        {"engine_blackFormula", embeddedOptionValue},
        {"engine_discountToSettlement", discountToSettlement},
        {"engine_expectedValue", expectedValue},
        {"engine_expectedSettlementValue", expectedSettlement},
        {"leg_size", (int)fixedLeg.size()},
        {"leg_cashflows", cf_trace}
    };
    out.addCase("testBlackEngine_internals", inp, exp);

    out.write();
    std::printf("callable_bond_black_engine_probe: wrote "
                "references/instruments/callable_bond_black_engine.json\n");
    return 0;
}
