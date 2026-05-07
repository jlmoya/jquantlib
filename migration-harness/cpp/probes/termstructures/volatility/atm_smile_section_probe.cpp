// migration-harness/cpp/probes/termstructures/volatility/atm_smile_section_probe.cpp
// Reference values for AtmSmileSection: atmLevel, minStrike, maxStrike,
// volatility, variance at various strikes.
// Phase 2j.5 Track C.2 (QuantLib v1.42.1)
//
// Wraps FlatSmileSection (simplest concrete SmileSection) in AtmSmileSection.
// Three scenario groups:
//   A: atm=Null<Real>() → inherits source atmLevel
//   B: explicit atm=0.06 → overrides source atmLevel
//   C: FlatSmileSection constructed by exerciseTime (no Date) + explicit atm

#include <cstdio>
#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/termstructures/volatility/atmsmilesection.hpp>
#include <ql/termstructures/volatility/flatsmilesection.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("termstructures/volatility/atm_smile_section",
                        QL_VERSION, "atm_smile_section_probe");

    DayCounter dc = Actual365Fixed();
    Date refDate(1, January, 2020);
    Date exDate(2, January, 2021);  // 367/365 year fraction

    const double strikes[] = {
        0.01, 0.02, 0.03, 0.04, 0.05,
        0.06, 0.07, 0.08, 0.10, 0.15,
        0.20, 0.25, 0.30
    };
    const int nStrikes = 13;

    // -----------------------------------------------------------------------
    // Scenario A: atm inherited from source (Null<Real>())
    // FlatSmileSection: vol=0.20, atmLevel=0.05
    // -----------------------------------------------------------------------
    {
        auto src = ext::make_shared<FlatSmileSection>(
                        exDate, 0.20, dc, refDate, 0.05);
        AtmSmileSection atm_sec(src);   // atm = Null<Real>() → 0.05

        json inp{{"scenario","A"},{"srcAtm",0.05},{"srcVol",0.20},
                 {"overrideAtm",nullptr},{"shift",0.0}};

        out.addCase("A_atmLevel",   inp, json{{"value", atm_sec.atmLevel()}});
        out.addCase("A_minStrike",  inp, json{{"value", atm_sec.minStrike()}});
        out.addCase("A_maxStrike",  inp, json{{"value", atm_sec.maxStrike()}});
        out.addCase("A_exerciseTime", inp, json{{"value", atm_sec.exerciseTime()}});
        out.addCase("A_shift",      inp, json{{"value", atm_sec.shift()}});

        for (int i = 0; i < nStrikes; ++i) {
            double k = strikes[i];
            char kbuf[32];
            std::snprintf(kbuf, sizeof(kbuf), "%.2f", k);
            std::string kn(kbuf);

            out.addCase("A_vol_k"  + kn, inp,
                        json{{"strike", k}, {"value", atm_sec.volatility(k)}});
            out.addCase("A_var_k"  + kn, inp,
                        json{{"strike", k}, {"value", atm_sec.variance(k)}});
        }
        // ATM queries (no strike / Null)
        out.addCase("A_vol_atm", inp,
                    json{{"strike", nullptr}, {"value", atm_sec.volatility(Null<Real>())}});
        out.addCase("A_var_atm", inp,
                    json{{"strike", nullptr}, {"value", atm_sec.variance(Null<Real>())}});
    }

    // -----------------------------------------------------------------------
    // Scenario B: explicit atm override = 0.06, source atmLevel = 0.05
    // -----------------------------------------------------------------------
    {
        auto src = ext::make_shared<FlatSmileSection>(
                        exDate, 0.20, dc, refDate, 0.05);
        AtmSmileSection atm_sec(src, 0.06);

        json inp{{"scenario","B"},{"srcAtm",0.05},{"srcVol",0.20},
                 {"overrideAtm",0.06},{"shift",0.0}};

        out.addCase("B_atmLevel",   inp, json{{"value", atm_sec.atmLevel()}});
        out.addCase("B_minStrike",  inp, json{{"value", atm_sec.minStrike()}});
        out.addCase("B_maxStrike",  inp, json{{"value", atm_sec.maxStrike()}});

        for (int i = 0; i < nStrikes; ++i) {
            double k = strikes[i];
            char kbuf[32];
            std::snprintf(kbuf, sizeof(kbuf), "%.2f", k);
            std::string kn(kbuf);

            out.addCase("B_vol_k"  + kn, inp,
                        json{{"strike", k}, {"value", atm_sec.volatility(k)}});
            out.addCase("B_var_k"  + kn, inp,
                        json{{"strike", k}, {"value", atm_sec.variance(k)}});
        }
        out.addCase("B_vol_atm", inp,
                    json{{"strike", nullptr}, {"value", atm_sec.volatility(Null<Real>())}});
        out.addCase("B_var_atm", inp,
                    json{{"strike", nullptr}, {"value", atm_sec.variance(Null<Real>())}});
    }

    // -----------------------------------------------------------------------
    // Scenario C: FlatSmileSection by exerciseTime (double), explicit atm override
    // exerciseTime = 367.0/365.0, vol=0.15, srcAtm=0.04, override=0.045
    // -----------------------------------------------------------------------
    {
        const double T = 367.0 / 365.0;
        auto src = ext::make_shared<FlatSmileSection>(T, 0.15, dc, 0.04);
        AtmSmileSection atm_sec(src, 0.045);

        json inp{{"scenario","C"},{"srcAtm",0.04},{"srcVol",0.15},
                 {"overrideAtm",0.045},{"shift",0.0},{"T",T}};

        out.addCase("C_atmLevel",   inp, json{{"value", atm_sec.atmLevel()}});
        out.addCase("C_exerciseTime", inp, json{{"value", atm_sec.exerciseTime()}});

        for (int i = 0; i < nStrikes; ++i) {
            double k = strikes[i];
            char kbuf[32];
            std::snprintf(kbuf, sizeof(kbuf), "%.2f", k);
            std::string kn(kbuf);

            out.addCase("C_vol_k"  + kn, inp,
                        json{{"strike", k}, {"value", atm_sec.volatility(k)}});
            out.addCase("C_var_k"  + kn, inp,
                        json{{"strike", k}, {"value", atm_sec.variance(k)}});
        }
        out.addCase("C_vol_atm", inp,
                    json{{"strike", nullptr}, {"value", atm_sec.volatility(Null<Real>())}});
        out.addCase("C_var_atm", inp,
                    json{{"strike", nullptr}, {"value", atm_sec.variance(Null<Real>())}});
    }

    // -----------------------------------------------------------------------
    // Scenario D: shifted FlatSmileSection (shift=0.01), atm inherited
    // -----------------------------------------------------------------------
    {
        const double T = 1.0;
        auto src = ext::make_shared<FlatSmileSection>(T, 0.18, dc, 0.03,
                        VolatilityType::ShiftedLognormal, 0.01);
        AtmSmileSection atm_sec(src);   // atm = Null<Real>() → 0.03

        json inp{{"scenario","D"},{"srcAtm",0.03},{"srcVol",0.18},
                 {"overrideAtm",nullptr},{"shift",0.01},{"T",T}};

        out.addCase("D_atmLevel",  inp, json{{"value", atm_sec.atmLevel()}});
        out.addCase("D_shift",     inp, json{{"value", atm_sec.shift()}});

        const double kd[] = {0.01, 0.02, 0.03, 0.04, 0.05, 0.07, 0.10, 0.15};
        for (double k : kd) {
            char kbuf[32];
            std::snprintf(kbuf, sizeof(kbuf), "%.2f", k);
            std::string kn(kbuf);

            out.addCase("D_vol_k"  + kn, inp,
                        json{{"strike", k}, {"value", atm_sec.volatility(k)}});
            out.addCase("D_var_k"  + kn, inp,
                        json{{"strike", k}, {"value", atm_sec.variance(k)}});
        }
        out.addCase("D_vol_atm", inp,
                    json{{"strike", nullptr}, {"value", atm_sec.volatility(Null<Real>())}});
    }

    out.write();
    return 0;
}
