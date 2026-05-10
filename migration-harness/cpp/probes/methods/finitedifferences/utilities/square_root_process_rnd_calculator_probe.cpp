// migration-harness/cpp/probes/methods/finitedifferences/utilities/square_root_process_rnd_calculator_probe.cpp
// Reference values for SquareRootProcessRNDCalculator vs QuantLib C++ v1.42.1.

#include <ql/version.hpp>
#include <ql/methods/finitedifferences/utilities/squarerootprocessrndcalculator.hpp>
#include "../../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("methods/finitedifferences/utilities/square_root_process_rnd_calculator",
                        QL_VERSION,
                        "square_root_process_rnd_calculator_probe");

    // Single set of CIR-style parameters: v0=0.04, kappa=2.0, theta=0.04, sigma=0.30
    const Real v0 = 0.04, kappa = 2.0, theta = 0.04, sigma = 0.30;
    SquareRootProcessRNDCalculator calc(v0, kappa, theta, sigma);

    struct CaseSpec { double v; double t; const char* name; };
    const CaseSpec specs[] = {
        {0.005, 0.5, "v005_t05"},
        {0.020, 0.5, "v020_t05"},
        {0.040, 0.5, "v040_t05"},
        {0.080, 0.5, "v080_t05"},
        {0.005, 1.0, "v005_t10"},
        {0.020, 1.0, "v020_t10"},
        {0.040, 1.0, "v040_t10"},
        {0.080, 1.0, "v080_t10"},
        {0.005, 5.0, "v005_t50"},
        {0.020, 5.0, "v020_t50"},
        {0.040, 5.0, "v040_t50"},
        {0.080, 5.0, "v080_t50"},
    };

    for (const auto& s : specs) {
        const Real pdf = calc.pdf(s.v, s.t);
        const Real cdf = calc.cdf(s.v, s.t);
        const Real invcdf = calc.invcdf(cdf, s.t);

        out.addCase(s.name,
            json{ {"v", s.v}, {"t", s.t} },
            json{ {"pdf", pdf}, {"cdf", cdf}, {"invcdf", invcdf} });
    }

    // Stationary cases
    const Real vs[] = {0.01, 0.02, 0.04, 0.08, 0.16};
    for (Real v : vs) {
        char name[32];
        std::snprintf(name, sizeof(name), "stationary_v%03d", int(v * 1000 + 0.5));
        const Real spdf = calc.stationary_pdf(v);
        const Real scdf = calc.stationary_cdf(v);
        const Real sinv = calc.stationary_invcdf(scdf);
        out.addCase(name,
            json{ {"v", v}, {"stationary", true} },
            json{ {"pdf", spdf}, {"cdf", scdf}, {"invcdf", sinv} });
    }

    // Stationary invcdf-only at fixed quantiles
    const Real qs[] = {0.05, 0.25, 0.5, 0.75, 0.95};
    for (Real q : qs) {
        char name[40];
        std::snprintf(name, sizeof(name), "stationary_invcdf_q%02d",
                      int(q * 100 + 0.5));
        out.addCase(name,
            json{ {"q", q}, {"stationary_invcdf", true} },
            json{ {"invcdf", calc.stationary_invcdf(q)} });
    }

    out.write();
    return 0;
}
