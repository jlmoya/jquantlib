// migration-harness/cpp/probes/methods/finitedifferences/utilities/cev_rnd_calculator_pdf_probe.cpp
// Reference values for CEVRNDCalculator::pdf vs QuantLib C++ v1.42.1.
//
// CEV process: df_t = alpha * f_t^beta * dW_t.
// Java port adds CEVRNDCalculator.pdf(f, t) — this probe pins reference
// values for both the delta < 2 (heavier tail) and delta >= 2 cases.
//
// References were initially produced via /tmp/cev_pdf_compute.cpp (boost
// only, mirroring v1.42.1 cevrndcalculator.cpp pdf() verbatim) and pinned
// in references/methods/finitedifferences/utilities/cev_rnd_calculator_pdf.json.
// This probe regenerates them deterministically when re-run via the harness.

#include <ql/version.hpp>
#include <ql/methods/finitedifferences/utilities/cevrndcalculator.hpp>
#include "../../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("methods/finitedifferences/utilities/cev_rnd_calculator_pdf",
                        QL_VERSION,
                        "cev_rnd_calculator_pdf_probe");

    struct Spec {
        double f0, alpha, beta;
        double f, t;
        const char* name;
    };

    const Spec specs[] = {
        // --- Case A: beta = 0.5, alpha = 2.0, f0 = 100 (delta = 0 < 2) ---
        {100.0, 2.0, 0.5,  60.0, 0.5, "A_f60_t05" },
        {100.0, 2.0, 0.5,  80.0, 0.5, "A_f80_t05" },
        {100.0, 2.0, 0.5, 100.0, 0.5, "A_f100_t05"},
        {100.0, 2.0, 0.5, 120.0, 0.5, "A_f120_t05"},
        {100.0, 2.0, 0.5, 140.0, 0.5, "A_f140_t05"},
        {100.0, 2.0, 0.5,  60.0, 1.0, "A_f60_t10" },
        {100.0, 2.0, 0.5, 100.0, 1.0, "A_f100_t10"},
        {100.0, 2.0, 0.5, 140.0, 1.0, "A_f140_t10"},
        {100.0, 2.0, 0.5,  80.0, 2.0, "A_f80_t20" },
        {100.0, 2.0, 0.5, 100.0, 2.0, "A_f100_t20"},
        {100.0, 2.0, 0.5, 120.0, 2.0, "A_f120_t20"},
        // --- Case B: beta = 1.5, alpha = 0.05, f0 = 100 (delta = 4 >= 2) ---
        {100.0, 0.05, 1.5,  50.0, 0.5, "B_f50_t05" },
        {100.0, 0.05, 1.5,  80.0, 0.5, "B_f80_t05" },
        {100.0, 0.05, 1.5, 100.0, 0.5, "B_f100_t05"},
        {100.0, 0.05, 1.5, 120.0, 0.5, "B_f120_t05"},
        {100.0, 0.05, 1.5, 150.0, 0.5, "B_f150_t05"},
        {100.0, 0.05, 1.5,  50.0, 1.0, "B_f50_t10" },
        {100.0, 0.05, 1.5, 100.0, 1.0, "B_f100_t10"},
        {100.0, 0.05, 1.5, 150.0, 1.0, "B_f150_t10"},
        // --- Case C: beta = 0.7, alpha = 2.0, f0 = 50 (delta < 2) ---
        { 50.0, 2.0, 0.7,  30.0, 0.5, "C_f30_t05" },
        { 50.0, 2.0, 0.7,  50.0, 0.5, "C_f50_t05" },
        { 50.0, 2.0, 0.7,  70.0, 0.5, "C_f70_t05" },
        { 50.0, 2.0, 0.7,  30.0, 1.0, "C_f30_t10" },
        { 50.0, 2.0, 0.7,  50.0, 1.0, "C_f50_t10" },
        { 50.0, 2.0, 0.7,  70.0, 1.0, "C_f70_t10" },
    };

    for (const auto& s : specs) {
        CEVRNDCalculator calc(s.f0, s.alpha, s.beta);
        const Real pdf = calc.pdf(s.f, s.t);
        out.addCase(s.name,
            json{ {"f0", s.f0}, {"alpha", s.alpha}, {"beta", s.beta},
                  {"f", s.f}, {"t", s.t} },
            json{ {"pdf", pdf} });
    }

    out.write();
    return 0;
}
