// migration-harness/cpp/probes/methods/finitedifferences/utilities/bsm_rnd_calculator_probe.cpp
// Reference values for BSMRNDCalculator vs QuantLib C++ v1.42.1.

#include <ql/version.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/termstructures/volatility/equityfx/blackconstantvol.hpp>
#include <ql/processes/blackscholesprocess.hpp>
#include <ql/methods/finitedifferences/utilities/bsmrndcalculator.hpp>
#include "../../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("methods/finitedifferences/utilities/bsm_rnd_calculator",
                        QL_VERSION,
                        "bsm_rnd_calculator_probe");

    // Setup: BSM process with constant vol = 0.20, r = 0.05, q = 0.02, S0 = 100
    const Date today(15, January, 2026);
    const DayCounter dc = Actual365Fixed();

    auto spot   = ext::make_shared<SimpleQuote>(100.0);
    auto rTS    = Handle<YieldTermStructure>(
                      ext::make_shared<FlatForward>(today, 0.05, dc));
    auto qTS    = Handle<YieldTermStructure>(
                      ext::make_shared<FlatForward>(today, 0.02, dc));
    auto volTS  = Handle<BlackVolTermStructure>(
                      ext::make_shared<BlackConstantVol>(today, NullCalendar(), 0.20, dc));

    auto process = ext::make_shared<GeneralizedBlackScholesProcess>(
        Handle<Quote>(spot), qTS, rTS, volTS);

    BSMRNDCalculator calc(process);

    // Cases at maturities t=0.5, 1.0, 2.0; x = ln(spot * exp(k * stdDev))
    struct CaseSpec { double t; double k; const char* name; };
    const CaseSpec specs[] = {
        {0.5, -2.0, "t05_km2"},
        {0.5, -1.0, "t05_km1"},
        {0.5,  0.0, "t05_atm"},
        {0.5,  1.0, "t05_kp1"},
        {0.5,  2.0, "t05_kp2"},
        {1.0, -2.0, "t10_km2"},
        {1.0,  0.0, "t10_atm"},
        {1.0,  1.0, "t10_kp1"},
        {1.0,  2.0, "t10_kp2"},
        {2.0, -1.0, "t20_km1"},
        {2.0,  0.0, "t20_atm"},
        {2.0,  1.0, "t20_kp1"},
    };

    for (const auto& s : specs) {
        const Real stdDev = 0.20 * std::sqrt(s.t);
        const Real x = std::log(100.0) + s.k * stdDev;

        const Real pdf = calc.pdf(x, s.t);
        const Real cdf = calc.cdf(x, s.t);
        // Use the case-CDF as the q for invcdf to verify round-trip.
        const Real invcdf = calc.invcdf(cdf, s.t);

        out.addCase(s.name,
            json{ {"x", x}, {"t", s.t}, {"q_for_invcdf", cdf} },
            json{ {"pdf", pdf}, {"cdf", cdf}, {"invcdf", invcdf} });
    }

    // Additional invcdf cases at q=0.01,0.25,0.5,0.75,0.99
    const Real qs[] = {0.01, 0.25, 0.5, 0.75, 0.99};
    for (Real q : qs) {
        char name[32];
        std::snprintf(name, sizeof(name), "invcdf_t10_q%02d",
                      int(q * 100 + 0.5));
        out.addCase(name,
            json{ {"q", q}, {"t", 1.0} },
            json{ {"invcdf", calc.invcdf(q, 1.0)} });
    }

    out.write();
    return 0;
}
