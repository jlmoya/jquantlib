// migration-harness/cpp/probes/experimental/shortrate/generalized_ou_process_probe.cpp
// Reference values for GeneralizedOrnsteinUhlenbeckProcess
// (ql/experimental/shortrate/generalizedornsteinuhlenbeckprocess.{hpp,cpp}).
//
// Mixes of constant and time-varying speed/vol functions to exercise the
// drift, diffusion, expectation, stdDeviation and variance overrides.

#include <cstdio>
#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/experimental/shortrate/generalizedornsteinuhlenbeckprocess.hpp>

using namespace jqml_harness;
using namespace QuantLib;

namespace {
    struct ConstSpeed { Real value; Real operator()(Time) const { return value; } };
    struct ConstVol   { Volatility value; Real operator()(Time) const { return value; } };
    // Linearly varying coefficients: c0 + c1*t.
    struct LinearSpeed { Real c0, c1; Real operator()(Time t) const { return c0 + c1 * t; } };
    struct LinearVol   { Real c0, c1; Real operator()(Time t) const { return c0 + c1 * t; } };
}

int main() {
    ReferenceWriter out("experimental/shortrate/generalized_ou_process",
                        QL_VERSION,
                        "generalized_ou_process_probe");

    // Scenario 1: classical constant-coefficient OU (matches OrnsteinUhlenbeckProcess).
    {
        Real a = 0.5, sigma = 0.1, x0 = 0.04, level = 0.03;
        GeneralizedOrnsteinUhlenbeckProcess proc(
            ConstSpeed{a}, ConstVol{sigma}, x0, level);

        Real t = 1.0, x = 0.05, dt = 0.25;
        json inp = {
            {"profile", "constant"},
            {"a", a}, {"sigma", sigma}, {"x0", x0}, {"level", level},
            {"t", t}, {"x", x}, {"dt", dt}
        };
        json expected = {
            {"x0", proc.x0()},
            {"level", proc.level()},
            {"speed_t", proc.speed(t)},
            {"vol_t", proc.volatility(t)},
            {"drift", proc.drift(t, x)},
            {"diffusion", proc.diffusion(t, x)},
            {"expectation", proc.expectation(t, x, dt)},
            {"std_dev", proc.stdDeviation(t, x, dt)},
            {"variance", proc.variance(t, x, dt)}
        };
        out.addCase("constant_a_0.5_sigma_0.1", inp, expected);
    }

    // Scenario 2: linear speed/vol. speed(t) = 0.2 + 0.1*t, vol(t) = 0.05 + 0.02*t.
    {
        Real x0 = 0.0, level = 0.025;
        GeneralizedOrnsteinUhlenbeckProcess proc(
            LinearSpeed{0.2, 0.1}, LinearVol{0.05, 0.02}, x0, level);

        for (Time t : {0.5, 1.0, 2.0, 3.0}) {
            Real x = 0.04;
            Time dt = 0.5;
            char name[64];
            std::snprintf(name, sizeof(name), "linear_t_%.2f", t);
            json inp = {
                {"profile", "linear"},
                {"speed_c0", 0.2}, {"speed_c1", 0.1},
                {"vol_c0", 0.05}, {"vol_c1", 0.02},
                {"x0", x0}, {"level", level}, {"t", t}, {"x", x}, {"dt", dt}
            };
            json expected = {
                {"speed_t", proc.speed(t)},
                {"vol_t", proc.volatility(t)},
                {"drift", proc.drift(t, x)},
                {"diffusion", proc.diffusion(t, x)},
                {"expectation", proc.expectation(t, x, dt)},
                {"std_dev", proc.stdDeviation(t, x, dt)},
                {"variance", proc.variance(t, x, dt)}
            };
            out.addCase(name, inp, expected);
        }
    }

    // Scenario 3: very small speed (algebraic-limit branch in variance).
    {
        Real a = 1e-9, sigma = 0.07, x0 = 0.0, level = 0.0;
        GeneralizedOrnsteinUhlenbeckProcess proc(
            ConstSpeed{a}, ConstVol{sigma}, x0, level);

        Real t = 1.0, x = 0.0, dt = 0.5;
        json inp = {
            {"profile", "small_speed_limit"},
            {"a", a}, {"sigma", sigma}, {"t", t}, {"x", x}, {"dt", dt}
        };
        json expected = {
            {"variance", proc.variance(t, x, dt)},
            {"std_dev", proc.stdDeviation(t, x, dt)}
        };
        out.addCase("small_speed_limit", inp, expected);
    }

    out.write();
    return 0;
}
