// migration-harness/cpp/probes/termstructures/volatility/kahale_smile_section_probe.cpp
// Reference values for KahaleSmileSection: optionPrice, volatility.
// Phase 2j WI-4.0c (QuantLib v1.42.1)
//
// Uses Date-based FlatSmileSection so that referenceDate() delegate works.
// B_i1 uses a small custom moneyness grid to avoid Boost.Math signal in quantile.

#include <cstdio>
#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/termstructures/volatility/kahalesmilesection.hpp>
#include <ql/termstructures/volatility/flatsmilesection.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>

using namespace jqml_harness;
using namespace QuantLib;

void probe_scenario(
        ReferenceWriter& out,
        const std::string& prefix,
        const json& inp,
        const ext::shared_ptr<SmileSection>& sec,
        bool interpolate,
        bool expExtra,
        const double* strikes,
        const char** strikeNames,
        int nStrikes)
{
    KahaleSmileSection ks(sec, Null<Real>(), interpolate, expExtra, false);
    auto ci = ks.coreIndices();
    out.addCase(prefix + "_atmLevel",  inp, json{{"value", ks.atmLevel()}});
    out.addCase(prefix + "_coreLeft",  inp, json{{"value", (int)ci.first}});
    out.addCase(prefix + "_coreRight", inp, json{{"value", (int)ci.second}});
    for (int i = 0; i < nStrikes; ++i) {
        double k = strikes[i];
        std::string kn = strikeNames[i];
        out.addCase(prefix + "_vol_k" + kn,
                    inp, json{{"strike", k}, {"value", ks.volatility(k)}});
        out.addCase(prefix + "_call_k" + kn,
                    inp, json{{"strike", k}, {"value", ks.optionPrice(k, Option::Call, 1.0)}});
    }
}

int main() {
    ReferenceWriter out("termstructures/volatility/kahale_smile_section",
                        QL_VERSION, "kahale_smile_section_probe");

    DayCounter dc = Actual365Fixed();
    Date refDate(1, January, 2020);
    Date exDate(2, January, 2021);

    // Unshifted probe strikes
    const double kArr0[] = {0.01, 0.02, 0.03, 0.04, 0.05,
                            0.06, 0.07, 0.08, 0.10, 0.15};
    const char* kNames0[] = {"0.01","0.02","0.03","0.04","0.05",
                              "0.06","0.07","0.08","0.10","0.15"};

    // -----------------------------------------------------------------------
    // Scenarios A, C: non-interpolate (safe with default grid)
    // -----------------------------------------------------------------------
    {
        auto sec0 = ext::make_shared<FlatSmileSection>(exDate, 0.20, dc, refDate, 0.05);
        json inp0{{"atm",0.05},{"vol",0.20},{"T",1.0},{"shift",0.0},{"grid","default"}};

        // A: interpolate=false, expExtra=false
        probe_scenario(out, "A_i0_e0", inp0, sec0, false, false, kArr0, kNames0, 10);
        // C: interpolate=false, expExtra=true
        probe_scenario(out, "C_i0_e1", inp0, sec0, false, true,  kArr0, kNames0, 10);
    }

    // -----------------------------------------------------------------------
    // Scenarios B, D: interpolate=true — use compact custom moneyness grid
    // to keep the quantile computation numerically stable (Boost.Math signals
    // for values at/near 0 or 1 with Release-mode policy on macOS ARM64).
    // Grid: {0.5, 0.75, 1.0, 1.25, 1.5, 2.0} — same as SmileSectionUtils tests.
    // -----------------------------------------------------------------------
    {
        const std::vector<Real> cg = {0.5, 0.75, 1.0, 1.25, 1.5, 2.0};
        auto sec1 = ext::make_shared<FlatSmileSection>(exDate, 0.20, dc, refDate, 0.05);
        // Probe strikes: within the grid range
        const double kArrI[] = {0.025, 0.0375, 0.05, 0.0625, 0.075, 0.10};
        const char* kNamesI[] = {"0.025","0.0375","0.05","0.0625","0.075","0.10"};
        json inp1{{"atm",0.05},{"vol",0.20},{"T",1.0},{"shift",0.0},{"grid","compact"}};

        KahaleSmileSection ksB(sec1, Null<Real>(), true, false, false, cg);
        auto ciB = ksB.coreIndices();
        out.addCase("B_i1_e0_atmLevel", inp1, json{{"value", ksB.atmLevel()}});
        out.addCase("B_i1_e0_coreLeft",  inp1, json{{"value", (int)ciB.first}});
        out.addCase("B_i1_e0_coreRight", inp1, json{{"value", (int)ciB.second}});
        for (int i = 0; i < 6; ++i) {
            double k = kArrI[i];
            std::string kn = kNamesI[i];
            out.addCase("B_i1_e0_vol_k" + kn,
                        inp1, json{{"strike",k}, {"value", ksB.volatility(k)}});
            out.addCase("B_i1_e0_call_k" + kn,
                        inp1, json{{"strike",k}, {"value", ksB.optionPrice(k, Option::Call, 1.0)}});
        }

        KahaleSmileSection ksD(sec1, Null<Real>(), true, true, false, cg);
        auto ciD = ksD.coreIndices();
        out.addCase("D_i1_e1_atmLevel", inp1, json{{"value", ksD.atmLevel()}});
        out.addCase("D_i1_e1_coreLeft",  inp1, json{{"value", (int)ciD.first}});
        out.addCase("D_i1_e1_coreRight", inp1, json{{"value", (int)ciD.second}});
        for (int i = 0; i < 6; ++i) {
            double k = kArrI[i];
            std::string kn = kNamesI[i];
            out.addCase("D_i1_e1_vol_k" + kn,
                        inp1, json{{"strike",k}, {"value", ksD.volatility(k)}});
            out.addCase("D_i1_e1_call_k" + kn,
                        inp1, json{{"strike",k}, {"value", ksD.optionPrice(k, Option::Call, 1.0)}});
        }
    }

    // -----------------------------------------------------------------------
    // Scenario E: shifted lognormal shift=0.02, ATM=0.02, vol=0.15, interp=false
    // -----------------------------------------------------------------------
    {
        const double kArrE[] = {-0.01, 0.00, 0.01, 0.02, 0.03,
                                  0.04, 0.05, 0.06, 0.08, 0.12};
        const char* kNamesE[] = {"m0.01","0.00","0.01","0.02","0.03",
                                   "0.04","0.05","0.06","0.08","0.12"};
        auto secE = ext::make_shared<FlatSmileSection>(
            exDate, 0.15, dc, refDate, 0.02, ShiftedLognormal, 0.02);
        json inpE{{"atm",0.02},{"vol",0.15},{"T",1.0},{"shift",0.02}};
        probe_scenario(out, "E_shift002_i0_e0", inpE, secE, false, false, kArrE, kNamesE, 10);
    }

    out.write();
    return 0;
}
