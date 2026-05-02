// migration-harness/cpp/probes/models/shortrate/onefactormodels/gsr_probe.cpp
// Phase 2j WI-1.3 — emit Gsr concrete-model behaviors (parameter readback,
// numeraireImpl, zerobondImpl, numeraireTime).
// Oracle: C++ QuantLib v1.42.1 Gsr (gsr.{hpp,cpp}).

#include <ql/version.hpp>
#include "../../../common.hpp"

#include <ql/models/shortrate/onefactormodels/gsr.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/quotes/simplequote.hpp>

#include <vector>
#include <cstdio>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("models/shortrate/onefactormodels/gsr",
                        QL_VERSION, "gsr_probe");

    // Same setup as gaussian1d_model_probe so behaviour can be cross-checked.
    const Date today(15, May, 2026);
    Settings::instance().evaluationDate() = today;
    Handle<YieldTermStructure> yts(ext::shared_ptr<YieldTermStructure>(
        new FlatForward(today, 0.03, Actual360())));

    std::vector<Date> volStepDates;
    volStepDates.push_back(today + Period(1, Years));
    volStepDates.push_back(today + Period(2, Years));
    std::vector<Real> volatilities;
    volatilities.push_back(0.01);
    volatilities.push_back(0.012);
    volatilities.push_back(0.015);
    Real reversion = 0.01;

    ext::shared_ptr<Gsr> gsr(new Gsr(yts, volStepDates, volatilities, reversion));

    // ── Parameter readback (constant-reversion ctor → reversion size 1, sigma size 3)
    {
        const Array& vol = gsr->volatility();
        const Array& rev = gsr->reversion();
        out.addCase("param_vol_size",
            json{}, json{{"value", static_cast<long>(vol.size())}});
        out.addCase("param_rev_size",
            json{}, json{{"value", static_cast<long>(rev.size())}});
        for (Size i = 0; i < vol.size(); ++i) {
            char nm[32]; std::snprintf(nm, sizeof nm, "param_vol_%02zu", i);
            out.addCase(nm, json{{"i", static_cast<long>(i)}},
                        json{{"value", vol[i]}});
        }
        for (Size i = 0; i < rev.size(); ++i) {
            char nm[32]; std::snprintf(nm, sizeof nm, "param_rev_%02zu", i);
            out.addCase(nm, json{{"i", static_cast<long>(i)}},
                        json{{"value", rev[i]}});
        }
        out.addCase("numeraire_time",
            json{}, json{{"value", gsr->numeraireTime()}});
    }

    // ── Discount bond P(t, T, x) [zerobondImpl path]
    {
        int idx = 0;
        Real ts[] = {0.0, 0.5, 1.0, 2.0, 3.0};
        Real Ts[] = {1.0, 2.0, 5.0, 10.0};
        Real xs[] = {-0.02, -0.01, 0.0, 0.01, 0.02};
        for (Real t : ts) {
            for (Real T : Ts) {
                if (T <= t) continue;
                for (Real x : xs) {
                    Real p = gsr->zerobond(T, t, x);
                    char nm[32]; std::snprintf(nm, sizeof nm, "zb_%03d", idx++);
                    out.addCase(nm,
                        json{{"t", t}, {"T", T}, {"x", x}},
                        json{{"value", p}});
                }
            }
        }
    }

    // ── Numeraire N(t, x) [numeraireImpl path] — distinct from base fingerprints.
    // Numeraire grids the (t, x) plane more densely.
    {
        int idx = 0;
        Real ts[] = {0.0, 0.25, 0.5, 1.0, 1.5, 2.0, 3.0, 5.0, 10.0};
        Real xs[] = {-0.03, -0.02, -0.01, 0.0, 0.01, 0.02, 0.03};
        for (Real t : ts) {
            for (Real x : xs) {
                Real n = gsr->numeraire(t, x);
                char nm[32]; std::snprintf(nm, sizeof nm, "num_%03d", idx++);
                out.addCase(nm,
                    json{{"t", t}, {"x", x}},
                    json{{"value", n}});
            }
        }
    }

    // ── Constructor variant 2: piecewise reversion (matches volstepdates).
    // Per gsr.cpp, reversions size must be 1 OR volsteptimes.size() + 1 (= 3).
    {
        std::vector<Real> revs;
        revs.push_back(0.01);
        revs.push_back(0.015);
        revs.push_back(0.02);
        ext::shared_ptr<Gsr> gsr2(new Gsr(yts, volStepDates, volatilities, revs));
        const Array& rev = gsr2->reversion();
        out.addCase("pw_rev_size",
            json{}, json{{"value", static_cast<long>(rev.size())}});
        for (Size i = 0; i < rev.size(); ++i) {
            char nm[32]; std::snprintf(nm, sizeof nm, "pw_rev_%02zu", i);
            out.addCase(nm, json{{"i", static_cast<long>(i)}},
                        json{{"value", rev[i]}});
        }
        // Sample a few zerobonds with the piecewise-reversion model.
        int idx = 0;
        Real ts[]  = {0.0, 1.0, 2.0};
        Real Ts[]  = {2.0, 5.0, 10.0};
        Real xs[]  = {-0.01, 0.0, 0.01};
        for (Real t : ts) {
            for (Real T : Ts) {
                if (T <= t) continue;
                for (Real x : xs) {
                    Real p = gsr2->zerobond(T, t, x);
                    char nm[32]; std::snprintf(nm, sizeof nm, "pw_zb_%02d", idx++);
                    out.addCase(nm,
                        json{{"t", t}, {"T", T}, {"x", x}},
                        json{{"value", p}});
                }
            }
        }
    }

    // ── numeraireTime mutator
    {
        ext::shared_ptr<Gsr> gsr3(new Gsr(yts, volStepDates, volatilities, reversion, 30.0));
        out.addCase("numeraire_time_30",
            json{}, json{{"value", gsr3->numeraireTime()}});
        gsr3->numeraireTime(45.0);
        out.addCase("numeraire_time_after_set",
            json{}, json{{"value", gsr3->numeraireTime()}});
        // Verify the mutator changed the discount: zerobond at T=20, t=0 will
        // pass through numeraireImpl-like logic relative to the fwd measure.
        Real p = gsr3->zerobond(20.0, 0.0, 0.0);
        out.addCase("numeraire_time_zb_20",
            json{{"T", 20.0}, {"t", 0.0}, {"x", 0.0}},
            json{{"value", p}});
    }

    out.write();
    return 0;
}
