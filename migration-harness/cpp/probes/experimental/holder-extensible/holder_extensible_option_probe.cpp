// migration-harness/cpp/probes/experimental/holder-extensible/holder_extensible_option_probe.cpp
//
// Probe for Phase 4h.5b: AnalyticHolderExtensibleOptionEngine.
// Reproduces the Haug textbook benchmark used by test-suite/extensibleoptions.cpp::
// testAnalyticHolderExtensibleOptionEngine and adds related cross-validation
// cases to exercise the M2 / N2 / Newton-Raphson branches.

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/instruments/holderextensibleoption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/pricingengines/exotic/analyticholderextensibleoptionengine.hpp>
#include <ql/processes/blackscholesprocess.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/termstructures/volatility/equityfx/blackconstantvol.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual360.hpp>

#include "common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

ext::shared_ptr<GeneralizedBlackScholesProcess>
makeGBS(const Date& eval, double S, double r, double q, double vol, const DayCounter& dc) {
    const Handle<Quote> spot(ext::make_shared<SimpleQuote>(S));
    const Handle<YieldTermStructure> rTS(
        ext::make_shared<FlatForward>(eval, r, dc, Continuous, NoFrequency));
    const Handle<YieldTermStructure> qTS(
        ext::make_shared<FlatForward>(eval, q, dc, Continuous, NoFrequency));
    const Handle<BlackVolTermStructure> volTS(
        ext::make_shared<BlackConstantVol>(eval, NullCalendar(), vol, dc));
    return ext::make_shared<GeneralizedBlackScholesProcess>(spot, qTS, rTS, volTS);
}

void addCase(ReferenceWriter& out,
             const std::string& name,
             double S, double r, double q, double vol,
             double strike1, double strike2, double premium,
             int days1, int days2,
             Option::Type type,
             const Date& eval, const DayCounter& dc) {

    const auto process = makeGBS(eval, S, r, q, vol, dc);

    const Date exDate1 = eval + days1;
    const Date exDate2 = eval + days2;

    const auto payoff = ext::make_shared<PlainVanillaPayoff>(type, strike1);
    const auto exercise = ext::make_shared<EuropeanExercise>(exDate1);

    HolderExtensibleOption option(type, premium, exDate2, strike2,
                                  payoff, exercise);
    option.setPricingEngine(
        ext::make_shared<AnalyticHolderExtensibleOptionEngine>(process));

    json inputs = {
        {"S", S}, {"r", r}, {"q", q}, {"vol", vol},
        {"strike1", strike1}, {"strike2", strike2}, {"premium", premium},
        {"days1", days1}, {"days2", days2},
        {"option_type", type == Option::Call ? "Call" : "Put"}
    };

    json expected = {
        {"npv", option.NPV()}
    };
    out.addCase(name, inputs, expected);
}

} // anonymous namespace

int main() {
    ReferenceWriter out("experimental/holder-extensible/holder_extensible_option",
                        QL_VERSION,
                        "holder_extensible_option_probe");

    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;
    const DayCounter dc = Actual360();

    // Haug textbook benchmark from test-suite/extensibleoptions.cpp
    // (testAnalyticHolderExtensibleOptionEngine): expected NPV = 9.4233
    addCase(out, "haug_call_S100_X1100_X2105_v25_r8_T180_T270_A1",
            100.0, 0.08, 0.0, 0.25, 100.0, 105.0, 1.0, 180, 270,
            Option::Call, eval, dc);

    // Cross-validation cases — same shape, varied params.
    addCase(out, "call_atm_lower_vol",
            100.0, 0.08, 0.0, 0.20, 100.0, 105.0, 1.0, 180, 270,
            Option::Call, eval, dc);
    addCase(out, "call_otm",
            100.0, 0.08, 0.0, 0.25, 110.0, 115.0, 1.0, 180, 270,
            Option::Call, eval, dc);
    addCase(out, "call_itm",
            100.0, 0.08, 0.0, 0.25, 90.0,  95.0,  1.0, 180, 270,
            Option::Call, eval, dc);
    addCase(out, "call_low_premium",
            100.0, 0.08, 0.0, 0.25, 100.0, 105.0, 0.5, 180, 270,
            Option::Call, eval, dc);
    addCase(out, "call_high_premium",
            100.0, 0.08, 0.0, 0.25, 100.0, 105.0, 2.0, 180, 270,
            Option::Call, eval, dc);
    addCase(out, "call_with_dividend",
            100.0, 0.08, 0.03, 0.25, 100.0, 105.0, 1.0, 180, 270,
            Option::Call, eval, dc);
    addCase(out, "call_T1_short",
            100.0, 0.08, 0.0, 0.25, 100.0, 105.0, 1.0, 90, 180,
            Option::Call, eval, dc);

    // Put variants
    addCase(out, "put_atm",
            100.0, 0.08, 0.0, 0.25, 100.0, 105.0, 1.0, 180, 270,
            Option::Put, eval, dc);
    addCase(out, "put_otm",
            100.0, 0.08, 0.0, 0.25, 90.0,  85.0,  1.0, 180, 270,
            Option::Put, eval, dc);
    addCase(out, "put_itm",
            100.0, 0.08, 0.0, 0.25, 110.0, 115.0, 1.0, 180, 270,
            Option::Put, eval, dc);

    out.write();
    return 0;
}
