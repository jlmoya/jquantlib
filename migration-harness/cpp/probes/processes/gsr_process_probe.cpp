// migration-harness/cpp/probes/processes/gsr_process_probe.cpp
// Reference values for GsrProcessCore + GsrProcess:
//   drift, diffusion, expectation, variance
// Phase 2j WI-1.2  (QuantLib v1.42.1)

#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/processes/gsrprocess.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/quotes/simplequote.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("processes/gsr_process", QL_VERSION, "gsr_process_probe");

    const Date today(15, May, 2026);
    Settings::instance().evaluationDate() = today;

    // piecewise vol: [0,1) -> 0.01, [1,2) -> 0.012, [2,inf) -> 0.015
    Array volStepTimes(2);
    volStepTimes[0] = 1.0;
    volStepTimes[1] = 2.0;
    Array vols(3);
    vols[0] = 0.01;
    vols[1] = 0.012;
    vols[2] = 0.015;
    Array reversions(1);
    reversions[0] = 0.01;
    const Real T_horizon = 10.0;

    GsrProcess proc(volStepTimes, vols, reversions, T_horizon);

    // t grid and x grid
    Real ts[] = {0.0, 0.5, 1.0, 1.5, 2.0, 3.0, 5.0};
    int n_ts = 7;
    Real xs[] = {-0.02, -0.01, 0.0, 0.01, 0.02};
    int n_xs = 5;

    // ---- Drift mu(t, x) ----
    {
        int idx = 0;
        for (int i = 0; i < n_ts; i++) {
            Real t = ts[i];
            for (int j = 0; j < n_xs; j++) {
                Real x = xs[j];
                Real mu = proc.drift(t, x);
                char nm[32]; std::snprintf(nm, sizeof nm, "drift_%03d", idx++);
                out.addCase(nm, json{{"t", t}, {"x", x}}, json{{"value", mu}});
            }
        }
    }

    // ---- Diffusion sigma(t, x) ----
    {
        int idx = 0;
        for (int i = 0; i < n_ts; i++) {
            Real t = ts[i];
            for (int j = 0; j < n_xs; j++) {
                Real x = xs[j];
                Real sig = proc.diffusion(t, x);
                char nm[32]; std::snprintf(nm, sizeof nm, "diff_%03d", idx++);
                out.addCase(nm, json{{"t", t}, {"x", x}}, json{{"value", sig}});
            }
        }
    }

    // ---- E[X(t+dt)|X(t)=x] ----
    {
        Real e_ts[] = {0.0, 1.0, 2.0};
        Real e_dts[] = {0.5, 1.0, 2.0, 5.0};
        Real e_xs[] = {-0.01, 0.0, 0.01};
        int idx = 0;
        for (Real et : e_ts) {
            for (Real dt : e_dts) {
                // skip if et+dt > T_horizon
                if (et + dt > T_horizon) continue;
                for (Real ex : e_xs) {
                    Real e = proc.expectation(et, ex, dt);
                    char nm[32]; std::snprintf(nm, sizeof nm, "exp_%03d", idx++);
                    out.addCase(nm, json{{"t", et}, {"dt", dt}, {"x", ex}},
                                json{{"value", e}});
                }
            }
        }
    }

    // ---- Var[X(t+dt)|X(t)=x] ----
    {
        Real e_ts[] = {0.0, 1.0, 2.0};
        Real e_dts[] = {0.5, 1.0, 2.0, 5.0};
        Real e_xs[] = {-0.01, 0.0, 0.01};
        int idx = 0;
        for (Real et : e_ts) {
            for (Real dt : e_dts) {
                if (et + dt > T_horizon) continue;
                for (Real ex : e_xs) {
                    Real v = proc.variance(et, ex, dt);
                    char nm[32]; std::snprintf(nm, sizeof nm, "var_%03d", idx++);
                    out.addCase(nm, json{{"t", et}, {"dt", dt}, {"x", ex}},
                                json{{"value", v}});
                }
            }
        }
    }

    // ---- sigma(t) and y(t) spot checks ----
    {
        Real sig_ts[] = {0.0, 0.5, 1.0, 1.5, 2.0, 3.0};
        int idx = 0;
        for (Real t : sig_ts) {
            Real s = proc.sigma(t);
            char nm[32]; std::snprintf(nm, sizeof nm, "sigma_%03d", idx++);
            out.addCase(nm, json{{"t", t}}, json{{"value", s}});
        }
    }
    {
        Real y_ts[] = {0.0, 0.5, 1.0, 1.5, 2.0, 3.0, 5.0};
        int idx = 0;
        for (Real t : y_ts) {
            Real y = proc.y(t);
            char nm[32]; std::snprintf(nm, sizeof nm, "y_%03d", idx++);
            out.addCase(nm, json{{"t", t}}, json{{"value", y}});
        }
    }

    // ---- G(t, T) spot checks ----
    {
        struct GTCase { Real t; Real T; };
        GTCase gt_cases[] = {
            {0.0, 1.0}, {0.0, 5.0}, {0.0, 10.0},
            {1.0, 2.0}, {1.0, 5.0}, {1.0, 10.0},
            {2.0, 3.0}, {2.0, 5.0}, {2.0, 10.0},
            {5.0, 6.0}, {5.0, 10.0}
        };
        int idx = 0;
        for (const auto& c : gt_cases) {
            Real g = proc.G(c.t, c.T, 0.0);
            char nm[32]; std::snprintf(nm, sizeof nm, "G_%03d", idx++);
            out.addCase(nm, json{{"t", c.t}, {"T", c.T}}, json{{"value", g}});
        }
    }

    out.write();
    return 0;
}
