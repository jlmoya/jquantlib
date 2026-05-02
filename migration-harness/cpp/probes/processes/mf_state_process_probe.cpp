// migration-harness/cpp/probes/processes/mf_state_process_probe.cpp
// Reference values for MfStateProcess: drift, diffusion, expectation,
// variance, stdDeviation.
// Phase 2j WI-4.0a (QuantLib v1.42.1)

#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/processes/mfstateprocess.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("processes/mf_state_process", QL_VERSION, "mf_state_process_probe");

    // Standard process: reversion=0.03, times={1.0, 2.0, 3.0}, vols={0.01, 0.012, 0.015, 0.018}
    // (times.size()==3 == vols.size()-1==3 ✓)
    Array times_std(3);
    times_std[0] = 1.0;
    times_std[1] = 2.0;
    times_std[2] = 3.0;
    Array vols_std(4);
    vols_std[0] = 0.010;
    vols_std[1] = 0.012;
    vols_std[2] = 0.015;
    vols_std[3] = 0.018;

    MfStateProcess proc(0.03, times_std, vols_std);

    // Also a zero-reversion process (reversionZero_ path)
    MfStateProcess proc_rev0(0.0, times_std, vols_std);

    // And a single-vol process (empty times array)
    Array times_empty(0);
    Array vols_single(1);
    vols_single[0] = 0.020;
    MfStateProcess proc_single(0.03, times_empty, vols_single);

    // And a single-vol zero-reversion process
    MfStateProcess proc_single_rev0(0.0, times_empty, vols_single);

    // t values for drift/diffusion (x doesn't matter — drift is always 0)
    Real ts[]  = {0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0};
    int n_ts = 8;
    Real xs[] = {-0.05, -0.01, 0.0, 0.01, 0.05};
    int n_xs = 5;

    // ---- x0 ----
    {
        out.addCase("x0", json{{}}, json{{"value", proc.x0()}});
    }

    // ---- drift (always 0, but confirm) ----
    {
        int idx = 0;
        for (int i = 0; i < n_ts; i++) {
            for (int j = 0; j < n_xs; j++) {
                Real mu = proc.drift(ts[i], xs[j]);
                char nm[32]; std::snprintf(nm, sizeof nm, "drift_%03d", idx++);
                out.addCase(nm, json{{"t", ts[i]}, {"x", xs[j]}},
                            json{{"value", mu}});
            }
        }
    }

    // ---- diffusion: standard process ----
    {
        int idx = 0;
        for (int i = 0; i < n_ts; i++) {
            Real sig = proc.diffusion(ts[i], 0.0);
            char nm[32]; std::snprintf(nm, sizeof nm, "diff_%03d", idx++);
            out.addCase(nm, json{{"t", ts[i]}, {"x", 0.0}},
                        json{{"value", sig}});
        }
    }

    // ---- expectation: always x0 returned ----
    {
        Real e_ts[]  = {0.0, 0.5, 1.0, 2.0, 3.0};
        Real e_dts[] = {0.1, 0.5, 1.0, 2.0};
        Real e_xs[]  = {-0.05, 0.0, 0.05};
        int idx = 0;
        for (Real et : e_ts) {
            for (Real dt : e_dts) {
                for (Real ex : e_xs) {
                    Real e = proc.expectation(et, ex, dt);
                    char nm[32]; std::snprintf(nm, sizeof nm, "exp_%03d", idx++);
                    out.addCase(nm, json{{"t", et}, {"dt", dt}, {"x", ex}},
                                json{{"value", e}});
                }
            }
        }
    }

    // ---- variance: standard process (nonzero reversion) ----
    {
        Real v_ts[]  = {0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0};
        Real v_dts[] = {0.5, 1.0, 2.0, 3.0};
        int idx = 0;
        for (Real vt : v_ts) {
            for (Real dt : v_dts) {
                Real v = proc.variance(vt, 0.0, dt);
                char nm[32]; std::snprintf(nm, sizeof nm, "var_%03d", idx++);
                out.addCase(nm, json{{"t", vt}, {"dt", dt}},
                            json{{"value", v}});
            }
        }
    }

    // ---- variance: zero reversion process ----
    {
        Real v_ts[]  = {0.0, 0.5, 1.0, 1.5, 2.0};
        Real v_dts[] = {0.5, 1.0, 2.0};
        int idx = 0;
        for (Real vt : v_ts) {
            for (Real dt : v_dts) {
                Real v = proc_rev0.variance(vt, 0.0, dt);
                char nm[32]; std::snprintf(nm, sizeof nm, "var_rev0_%03d", idx++);
                out.addCase(nm, json{{"t", vt}, {"dt", dt}},
                            json{{"value", v}});
            }
        }
    }

    // ---- variance: single-vol process (empty times) ----
    {
        Real v_ts[]  = {0.0, 1.0, 2.0};
        Real v_dts[] = {0.5, 1.0, 2.0};
        int idx = 0;
        for (Real vt : v_ts) {
            for (Real dt : v_dts) {
                Real v = proc_single.variance(vt, 0.0, dt);
                char nm[32]; std::snprintf(nm, sizeof nm, "var_single_%03d", idx++);
                out.addCase(nm, json{{"t", vt}, {"dt", dt}},
                            json{{"value", v}});
            }
        }
    }

    // ---- variance: single-vol zero-reversion ----
    {
        Real v_ts[]  = {0.0, 1.0};
        Real v_dts[] = {0.5, 1.0, 2.0};
        int idx = 0;
        for (Real vt : v_ts) {
            for (Real dt : v_dts) {
                Real v = proc_single_rev0.variance(vt, 0.0, dt);
                char nm[32]; std::snprintf(nm, sizeof nm, "var_single_rev0_%03d", idx++);
                out.addCase(nm, json{{"t", vt}, {"dt", dt}},
                            json{{"value", v}});
            }
        }
    }

    // ---- stdDeviation: standard process ----
    {
        Real v_ts[]  = {0.0, 1.0, 2.0};
        Real v_dts[] = {1.0, 2.0};
        int idx = 0;
        for (Real vt : v_ts) {
            for (Real dt : v_dts) {
                Real sd = proc.stdDeviation(vt, 0.0, dt);
                char nm[32]; std::snprintf(nm, sizeof nm, "std_%03d", idx++);
                out.addCase(nm, json{{"t", vt}, {"dt", dt}},
                            json{{"value", sd}});
            }
        }
    }

    // ---- dt < epsilon edge case ----
    {
        Real v = proc.variance(0.5, 0.0, 0.0);
        out.addCase("var_dt_zero", json{{"t", 0.5}, {"dt", 0.0}},
                    json{{"value", v}});
    }

    out.write();
    return 0;
}
