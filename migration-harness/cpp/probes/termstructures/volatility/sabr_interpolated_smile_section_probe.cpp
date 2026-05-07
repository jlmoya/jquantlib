// migration-harness/cpp/probes/termstructures/volatility/sabr_interpolated_smile_section_probe.cpp
// Reference values for SabrInterpolatedSmileSection: fitted SABR params + volatility.
// Phase 2k Track A (QuantLib v1.42.1)
//
// Scenarios:
//   A: 7 market strikes, unshifted, all params free (alpha/beta/nu/rho initial guesses)
//   B: same strikes, fixed beta=0.5
//   C: shifted (shift=0.02), 6 strikes, all params free
//   D: minimal 4 strikes, all params free
//   E: higher vol surface (ATM vol=0.30), wider moneyness
//
// For each scenario we capture:
//   - fitted alpha, beta, nu, rho
//   - rmsError, maxError
//   - endCriteria (int)
//   - volatility at several probe strikes (including ATM and wings)
//   - varianceImpl at ATM

#include <cstdio>
#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/termstructures/volatility/sabrinterpolatedsmilesection.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/settings.hpp>

using namespace jqml_harness;
using namespace QuantLib;

// Helper: probe a SabrInterpolatedSmileSection, emitting all standard outputs
static void probeSection(
        ReferenceWriter& out,
        const std::string& prefix,
        const json& inp,
        SabrInterpolatedSmileSection& sec,
        const std::vector<Rate>& probeStrikes)
{
    out.addCase(prefix + "_alpha",    inp, json{{"value", sec.alpha()}});
    out.addCase(prefix + "_beta",     inp, json{{"value", sec.beta()}});
    out.addCase(prefix + "_nu",       inp, json{{"value", sec.nu()}});
    out.addCase(prefix + "_rho",      inp, json{{"value", sec.rho()}});
    out.addCase(prefix + "_rmsError", inp, json{{"value", sec.rmsError()}});
    out.addCase(prefix + "_maxError", inp, json{{"value", sec.maxError()}});
    out.addCase(prefix + "_endCriteria", inp,
                json{{"value", static_cast<int>(sec.endCriteria())}});
    out.addCase(prefix + "_atmLevel", inp, json{{"value", sec.atmLevel()}});
    out.addCase(prefix + "_minStrike", inp, json{{"value", sec.minStrike()}});
    out.addCase(prefix + "_maxStrike", inp, json{{"value", sec.maxStrike()}});
    for (Rate k : probeStrikes) {
        std::ostringstream kname;
        kname << k;
        out.addCase(prefix + "_vol_" + kname.str(),
                    inp, json{{"strike", k}, {"value", sec.volatility(k)}});
        out.addCase(prefix + "_var_" + kname.str(),
                    inp, json{{"strike", k}, {"value", sec.variance(k)}});
    }
}

int main() {
    ReferenceWriter out("termstructures/volatility/sabr_interpolated_smile_section",
                        QL_VERSION, "sabr_interpolated_smile_section_probe");

    Settings::instance().evaluationDate() = Date(1, January, 2020);
    Date refDate(1, January, 2020);
    Date exDate(2, January, 2021);       // T = 367/365 = 1.00548...
    DayCounter dc = Actual365Fixed();

    // -----------------------------------------------------------------------
    // Scenario A: 7 market strikes, unshifted, all params free
    // forward = 0.05, atmVol = 0.20
    // strikes = {0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08}
    // vols    = {0.25, 0.22, 0.20, 0.20, 0.21, 0.22, 0.24}
    // initial: alpha=0.20, beta=0.50, nu=0.40, rho=0.00
    // -----------------------------------------------------------------------
    {
        std::vector<Rate>      strikes = {0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08};
        std::vector<Volatility> vols   = {0.25, 0.22, 0.20, 0.20, 0.21, 0.22, 0.24};
        Rate   fwd    = 0.05;
        Real   atmVol = 0.20;
        Real   shift  = 0.0;

        json inp{{"scenario","A"},{"forward",fwd},{"atmVol",atmVol},{"shift",shift},
                 {"beta_fixed",false},{"alpha_init",0.20},{"beta_init",0.50},
                 {"nu_init",0.40},{"rho_init",0.00}};

        SabrInterpolatedSmileSection sec(
            exDate, fwd, strikes, false, atmVol, vols,
            0.20, 0.50, 0.40, 0.00,      // alpha, beta, nu, rho
            false, false, false, false,   // all free
            true,                         // vegaWeighted
            ext::shared_ptr<EndCriteria>(),
            ext::shared_ptr<OptimizationMethod>(),
            dc, shift);

        std::vector<Rate> probeK = {0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08,
                                    0.01, 0.09, 0.10};
        probeSection(out, "A", inp, sec, probeK);
    }

    // -----------------------------------------------------------------------
    // Scenario B: same market data as A but beta FIXED = 0.50
    // -----------------------------------------------------------------------
    {
        std::vector<Rate>      strikes = {0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08};
        std::vector<Volatility> vols   = {0.25, 0.22, 0.20, 0.20, 0.21, 0.22, 0.24};
        Rate   fwd    = 0.05;
        Real   atmVol = 0.20;
        Real   shift  = 0.0;

        json inp{{"scenario","B"},{"forward",fwd},{"atmVol",atmVol},{"shift",shift},
                 {"beta_fixed",true},{"beta_val",0.50}};

        SabrInterpolatedSmileSection sec(
            exDate, fwd, strikes, false, atmVol, vols,
            0.20, 0.50, 0.40, 0.00,
            false, true /*isBetaFixed*/, false, false,
            true,
            ext::shared_ptr<EndCriteria>(),
            ext::shared_ptr<OptimizationMethod>(),
            dc, shift);

        std::vector<Rate> probeK = {0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08};
        probeSection(out, "B", inp, sec, probeK);
    }

    // -----------------------------------------------------------------------
    // Scenario C: shifted (shift=0.02), 6 strikes, all free
    // forward = 0.02, atmVol = 0.15
    // strikes = {-0.01, 0.00, 0.01, 0.02, 0.03, 0.04}
    // vols    = {0.20,  0.18, 0.16, 0.15, 0.16, 0.18}
    // -----------------------------------------------------------------------
    {
        std::vector<Rate>      strikes = {-0.01, 0.00, 0.01, 0.02, 0.03, 0.04};
        std::vector<Volatility> vols   = {0.20,  0.18, 0.16, 0.15, 0.16, 0.18};
        Rate   fwd    = 0.02;
        Real   atmVol = 0.15;
        Real   shift  = 0.02;

        json inp{{"scenario","C"},{"forward",fwd},{"atmVol",atmVol},{"shift",shift},
                 {"beta_fixed",false}};

        SabrInterpolatedSmileSection sec(
            exDate, fwd, strikes, false, atmVol, vols,
            0.20, 0.50, 0.40, 0.00,
            false, false, false, false,
            true,
            ext::shared_ptr<EndCriteria>(),
            ext::shared_ptr<OptimizationMethod>(),
            dc, shift);

        std::vector<Rate> probeK = {-0.01, 0.00, 0.01, 0.02, 0.03, 0.04};
        probeSection(out, "C", inp, sec, probeK);
    }

    // -----------------------------------------------------------------------
    // Scenario D: minimal 4 strikes, all free
    // forward = 0.05, atmVol = 0.20
    // -----------------------------------------------------------------------
    {
        std::vector<Rate>      strikes = {0.03, 0.04, 0.05, 0.07};
        std::vector<Volatility> vols   = {0.22, 0.20, 0.20, 0.23};
        Rate   fwd    = 0.05;
        Real   atmVol = 0.20;
        Real   shift  = 0.0;

        json inp{{"scenario","D"},{"forward",fwd},{"atmVol",atmVol},{"shift",shift},
                 {"beta_fixed",false},{"nStrikes",4}};

        SabrInterpolatedSmileSection sec(
            exDate, fwd, strikes, false, atmVol, vols,
            0.20, 0.50, 0.40, 0.00,
            false, false, false, false,
            true,
            ext::shared_ptr<EndCriteria>(),
            ext::shared_ptr<OptimizationMethod>(),
            dc, shift);

        std::vector<Rate> probeK = {0.03, 0.04, 0.05, 0.07};
        probeSection(out, "D", inp, sec, probeK);
    }

    // -----------------------------------------------------------------------
    // Scenario E: higher-vol surface, wider moneyness
    // forward = 0.05, atmVol = 0.30
    // strikes = {0.01, 0.02, 0.03, 0.05, 0.07, 0.09, 0.12}
    // vols    = {0.40, 0.35, 0.31, 0.30, 0.32, 0.36, 0.42}
    // -----------------------------------------------------------------------
    {
        std::vector<Rate>      strikes = {0.01, 0.02, 0.03, 0.05, 0.07, 0.09, 0.12};
        std::vector<Volatility> vols   = {0.40, 0.35, 0.31, 0.30, 0.32, 0.36, 0.42};
        Rate   fwd    = 0.05;
        Real   atmVol = 0.30;
        Real   shift  = 0.0;

        json inp{{"scenario","E"},{"forward",fwd},{"atmVol",atmVol},{"shift",shift},
                 {"beta_fixed",false}};

        SabrInterpolatedSmileSection sec(
            exDate, fwd, strikes, false, atmVol, vols,
            0.20, 0.50, 0.40, 0.00,
            false, false, false, false,
            true,
            ext::shared_ptr<EndCriteria>(),
            ext::shared_ptr<OptimizationMethod>(),
            dc, shift);

        std::vector<Rate> probeK = {0.01, 0.02, 0.03, 0.05, 0.07, 0.09, 0.12,
                                    0.04, 0.06, 0.10};
        probeSection(out, "E", inp, sec, probeK);
    }

    out.write();
    return 0;
}
