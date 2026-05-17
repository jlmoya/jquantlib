// migration-harness/cpp/probes/models/garch11_probe.cpp
// Phase 5e.5b-CFC-d-109: probe for Garch11.
// Emits the exact recurrence output (dates + sigmas) for the testCalculation
// fixture, plus the calibration reference values for testCalibration.

#include <ql/models/volatility/garch.hpp>
#include <ql/time/date.hpp>
#include <ql/timeseries.hpp>
#include <ql/version.hpp>
#include <ql/math/optimization/endcriteria.hpp>
#include <ql/math/optimization/levenbergmarquardt.hpp>
#include <ql/math/optimization/problem.hpp>
#include <ql/math/randomnumbers/inversecumulativerng.hpp>
#include <ql/math/randomnumbers/mt19937uniformrng.hpp>
#include <ql/math/distributions/normaldistribution.hpp>

#include "common.hpp"

#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

class DummyOptimizationMethod : public OptimizationMethod {
  public:
    EndCriteria::Type minimize(Problem& P, const EndCriteria& /*ec*/) override {
        P.setFunctionValue(P.value(P.currentValue()));
        return EndCriteria::None;
    }
};

typedef InverseCumulativeRng<MersenneTwisterUniformRng,
                             InverseCumulativeNormal>
        GaussianGenerator;

} // namespace

int main() {
    ReferenceWriter out("models/volatility/garch11", QL_VERSION, "garch11_probe");

    // === testCalculation reference ===
    // C++: Garch11(0.2, 0.3, 0.4), constant TimeSeries r=0.1 over 10 days
    // starting Date(7, July, 1962). Capture all keys and sigmas.
    {
        Date d(7, July, 1962);
        TimeSeries<Volatility> ts;
        for (std::size_t i = 0; i < 10; ++i, d += 1) {
            ts[d] = 0.1;
        }
        Garch11 garch(0.2, 0.3, 0.4);
        TimeSeries<Volatility> tsout = garch.calculate(ts);
        json keys = json::array();
        json sigmas = json::array();
        for (auto it = tsout.cbegin(); it != tsout.cend(); ++it) {
            keys.push_back(it->first.serialNumber());
            sigmas.push_back(it->second);
        }
        out.addCase("testCalculation_output",
                    json{{"alpha", 0.2}, {"beta", 0.3}, {"vl", 0.4},
                         {"first_input_serial", 22835},
                         {"n_input", 10},
                         {"const_r", 0.1}},
                    json{{"keys", keys}, {"sigmas", sigmas}});
    }

    // === testCalibration reference ===
    {
        Date d(7, July, 1962);
        TimeSeries<Volatility> ts;
        Garch11 garch(0.2, 0.3, 0.4);
        GaussianGenerator rng(MersenneTwisterUniformRng(48));
        Volatility r = 0.0, v = 0.0;
        for (std::size_t i = 0; i < 50000; ++i, d += 1) {
            v = garch.forecast(r, v);
            r = rng.next().value * std::sqrt(v);
            ts[d] = r;
        }

        // Default calibration
        Garch11 cgarch1(ts);
        out.addCase("default_calibration",
                    json::object(),
                    json{{"alpha", cgarch1.alpha()},
                         {"beta",  cgarch1.beta()},
                         {"omega", cgarch1.omega()},
                         {"logLikelihood", cgarch1.logLikelihood()}});

        Garch11 cgarch2(ts, Garch11::MomentMatchingGuess);
        DummyOptimizationMethod m;
        cgarch2.calibrate(ts, m, EndCriteria(3, 2, 0.0, 0.0, 0.0));
        out.addCase("m1_dummy",
                    json::object(),
                    json{{"alpha", cgarch2.alpha()},
                         {"beta",  cgarch2.beta()},
                         {"omega", cgarch2.omega()},
                         {"logLikelihood", cgarch2.logLikelihood()}});

        cgarch2.calibrate(ts);
        out.addCase("m1_simplex",
                    json::object(),
                    json{{"alpha", cgarch2.alpha()},
                         {"beta",  cgarch2.beta()},
                         {"omega", cgarch2.omega()},
                         {"logLikelihood", cgarch2.logLikelihood()}});

        Garch11 cgarch3(ts, Garch11::GammaGuess);
        cgarch3.calibrate(ts, m, EndCriteria(3, 2, 0.0, 0.0, 0.0));
        out.addCase("m2_dummy",
                    json::object(),
                    json{{"alpha", cgarch3.alpha()},
                         {"beta",  cgarch3.beta()},
                         {"omega", cgarch3.omega()},
                         {"logLikelihood", cgarch3.logLikelihood()}});

        cgarch3.calibrate(ts);
        out.addCase("m2_simplex",
                    json::object(),
                    json{{"alpha", cgarch3.alpha()},
                         {"beta",  cgarch3.beta()},
                         {"omega", cgarch3.omega()},
                         {"logLikelihood", cgarch3.logLikelihood()}});

        Garch11 cgarch4(ts, Garch11::DoubleOptimization);
        cgarch4.calibrate(ts);
        out.addCase("double_optimization",
                    json::object(),
                    json{{"alpha", cgarch4.alpha()},
                         {"beta",  cgarch4.beta()},
                         {"omega", cgarch4.omega()},
                         {"logLikelihood", cgarch4.logLikelihood()}});

        LevenbergMarquardt lm;
        cgarch4.calibrate(ts, lm, EndCriteria(100000, 500, 1e-8, 1e-8, 1e-8));
        out.addCase("levenberg_marquardt",
                    json::object(),
                    json{{"alpha", cgarch4.alpha()},
                         {"beta",  cgarch4.beta()},
                         {"omega", cgarch4.omega()},
                         {"logLikelihood", cgarch4.logLikelihood()}});
    }

    out.write();
    return 0;
}
