// migration-harness/cpp/probes/methods/finitedifferences/utilities/heston_rnd_calculator_probe.cpp
// Reference values for HestonRNDCalculator vs QuantLib C++ v1.42.1.

#include <ql/version.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/processes/hestonprocess.hpp>
#include <ql/methods/finitedifferences/utilities/hestonrndcalculator.hpp>
#include "../../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("methods/finitedifferences/utilities/heston_rnd_calculator",
                        QL_VERSION,
                        "heston_rnd_calculator_probe");

    // Heston: S0=100, v0=0.04, kappa=2.0, theta=0.04, sigma=0.30, rho=-0.5
    // r=0.05, q=0.02
    const Date today(15, January, 2026);
    const DayCounter dc = Actual365Fixed();

    auto spot   = ext::make_shared<SimpleQuote>(100.0);
    auto rTS    = Handle<YieldTermStructure>(
                      ext::make_shared<FlatForward>(today, 0.05, dc));
    auto qTS    = Handle<YieldTermStructure>(
                      ext::make_shared<FlatForward>(today, 0.02, dc));

    auto process = ext::make_shared<HestonProcess>(
        rTS, qTS, Handle<Quote>(spot),
        0.04, 2.0, 0.04, 0.30, -0.5);

    HestonRNDCalculator calc(process);

    struct CaseSpec { double t; double k; const char* name; };
    const CaseSpec specs[] = {
        {0.5, -1.0, "t05_km1"},
        {0.5,  0.0, "t05_atm"},
        {0.5,  1.0, "t05_kp1"},
        {1.0, -1.0, "t10_km1"},
        {1.0,  0.0, "t10_atm"},
        {1.0,  1.0, "t10_kp1"},
        {2.0,  0.0, "t20_atm"},
    };

    for (const auto& s : specs) {
        const Real stdDev = std::sqrt(0.04 * s.t);   // approx, just for grid
        const Real x = std::log(100.0) + s.k * stdDev;

        const Real pdf = calc.pdf(x, s.t);
        const Real cdf = calc.cdf(x, s.t);

        out.addCase(s.name,
            json{ {"x", x}, {"t", s.t} },
            json{ {"pdf", pdf}, {"cdf", cdf} });
    }

    // invcdf at q in {0.25, 0.5, 0.75} for t=1.0
    const Real qs[] = {0.25, 0.5, 0.75};
    for (Real q : qs) {
        char name[40];
        std::snprintf(name, sizeof(name), "invcdf_t10_q%02d", int(q * 100 + 0.5));
        out.addCase(name,
            json{ {"q", q}, {"t", 1.0} },
            json{ {"invcdf", calc.invcdf(q, 1.0)} });
    }

    out.write();
    return 0;
}
