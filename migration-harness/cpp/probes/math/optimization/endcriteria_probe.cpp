// migration-harness/cpp/probes/math/optimization/endcriteria_probe.cpp
// Reference values for EndCriteria behavior — captures v1.42.1 output for
// each check method, the operator() chain, and the succeeded() helper.
// Each case exercises a specific branch; Java tests mirror them exactly.

#include <ql/version.hpp>
#include <ql/math/optimization/endcriteria.hpp>
#include <ql/utilities/null.hpp>
#include "../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("math/optimization/endcriteria", QL_VERSION, "endcriteria_probe");

    // Standard builder — matches our Java default so all cases share state shape.
    auto make = []() {
        return EndCriteria(100, 10, 1e-8, 1e-8, 1e-8);
    };

    // ----- checkMaxIterations -----

    // Below limit → returns false, ecType unchanged.
    {
        const EndCriteria ec = make();
        EndCriteria::Type t = EndCriteria::None;
        bool result = ec.checkMaxIterations(5, t);
        out.addCase("checkMaxIterations_below",
                    json{{"iteration", 5}, {"maxIterations", 100}, {"ecTypeIn", "None"}},
                    json{{"result", result}, {"ecTypeOut", static_cast<int>(t)}});
    }
    // At limit → returns true, ecType becomes MaxIterations (=1 in enum order).
    {
        const EndCriteria ec = make();
        EndCriteria::Type t = EndCriteria::None;
        bool result = ec.checkMaxIterations(100, t);
        out.addCase("checkMaxIterations_atLimit",
                    json{{"iteration", 100}, {"maxIterations", 100}, {"ecTypeIn", "None"}},
                    json{{"result", result}, {"ecTypeOut", static_cast<int>(t)}});
    }
    // Above limit → returns true, same ecType.
    {
        const EndCriteria ec = make();
        EndCriteria::Type t = EndCriteria::None;
        bool result = ec.checkMaxIterations(200, t);
        out.addCase("checkMaxIterations_above",
                    json{{"iteration", 200}, {"maxIterations", 100}, {"ecTypeIn", "None"}},
                    json{{"result", result}, {"ecTypeOut", static_cast<int>(t)}});
    }

    // ----- checkStationaryPoint -----

    // Large x diff → resets counter, returns false.
    {
        const EndCriteria ec = make();
        Size stat = 5;
        EndCriteria::Type t = EndCriteria::None;
        bool result = ec.checkStationaryPoint(1.0, 2.0, stat, t);
        out.addCase("checkStationaryPoint_largeDiff",
                    json{{"xOld", 1.0}, {"xNew", 2.0}, {"rootEps", 1e-8}, {"statIn", 5}},
                    json{{"result", result}, {"statOut", static_cast<int>(stat)},
                         {"ecTypeOut", static_cast<int>(t)}});
    }
    // Small diff, below maxStat → increments counter, returns false.
    {
        const EndCriteria ec = make();
        Size stat = 5;
        EndCriteria::Type t = EndCriteria::None;
        bool result = ec.checkStationaryPoint(1.0, 1.0 + 1e-10, stat, t);
        out.addCase("checkStationaryPoint_accumulates",
                    json{{"xOld", 1.0}, {"xNew", 1.0000000001}, {"rootEps", 1e-8}, {"statIn", 5}},
                    json{{"result", result}, {"statOut", static_cast<int>(stat)},
                         {"ecTypeOut", static_cast<int>(t)}});
    }
    // Small diff, above maxStat → returns true, ecType = StationaryPoint.
    {
        const EndCriteria ec = make();
        Size stat = 10;  // maxStationaryStateIterations is 10, so post-increment = 11 > 10 triggers
        EndCriteria::Type t = EndCriteria::None;
        bool result = ec.checkStationaryPoint(1.0, 1.0 + 1e-10, stat, t);
        out.addCase("checkStationaryPoint_triggers",
                    json{{"xOld", 1.0}, {"xNew", 1.0000000001}, {"maxStat", 10}, {"statIn", 10}},
                    json{{"result", result}, {"statOut", static_cast<int>(stat)},
                         {"ecTypeOut", static_cast<int>(t)}});
    }

    // ----- checkStationaryFunctionValue -----

    // Large f diff → resets, returns false.
    {
        const EndCriteria ec = make();
        Size stat = 3;
        EndCriteria::Type t = EndCriteria::None;
        bool result = ec.checkStationaryFunctionValue(5.0, 2.0, stat, t);
        out.addCase("checkStationaryFunctionValue_largeDiff",
                    json{{"fxOld", 5.0}, {"fxNew", 2.0}, {"statIn", 3}},
                    json{{"result", result}, {"statOut", static_cast<int>(stat)},
                         {"ecTypeOut", static_cast<int>(t)}});
    }
    // Small f diff, above maxStat → triggers with StationaryFunctionValue.
    {
        const EndCriteria ec = make();
        Size stat = 10;
        EndCriteria::Type t = EndCriteria::None;
        bool result = ec.checkStationaryFunctionValue(2.0, 2.0 + 1e-10, stat, t);
        out.addCase("checkStationaryFunctionValue_triggers",
                    json{{"fxOld", 2.0}, {"fxNew", 2.0000000001}, {"maxStat", 10}, {"statIn", 10}},
                    json{{"result", result}, {"statOut", static_cast<int>(stat)},
                         {"ecTypeOut", static_cast<int>(t)}});
    }

    // ----- checkStationaryFunctionAccuracy -----

    // positiveOptimization=false → always false.
    {
        const EndCriteria ec = make();
        EndCriteria::Type t = EndCriteria::None;
        bool result = ec.checkStationaryFunctionAccuracy(1e-20, false, t);
        out.addCase("checkStationaryFunctionAccuracy_notPositive",
                    json{{"f", 1e-20}, {"positiveOpt", false}},
                    json{{"result", result}, {"ecTypeOut", static_cast<int>(t)}});
    }
    // positiveOptimization=true, f >= eps → false.
    {
        const EndCriteria ec = make();
        EndCriteria::Type t = EndCriteria::None;
        bool result = ec.checkStationaryFunctionAccuracy(1.0, true, t);
        out.addCase("checkStationaryFunctionAccuracy_largeF",
                    json{{"f", 1.0}, {"positiveOpt", true}, {"eps", 1e-8}},
                    json{{"result", result}, {"ecTypeOut", static_cast<int>(t)}});
    }
    // positiveOptimization=true, f < eps → true, StationaryFunctionAccuracy.
    {
        const EndCriteria ec = make();
        EndCriteria::Type t = EndCriteria::None;
        bool result = ec.checkStationaryFunctionAccuracy(1e-20, true, t);
        out.addCase("checkStationaryFunctionAccuracy_triggers",
                    json{{"f", 1e-20}, {"positiveOpt", true}, {"eps", 1e-8}},
                    json{{"result", result}, {"ecTypeOut", static_cast<int>(t)}});
    }

    // ----- checkZeroGradientNorm -----

    // Above eps → false.
    {
        const EndCriteria ec = make();
        EndCriteria::Type t = EndCriteria::None;
        bool result = ec.checkZeroGradientNorm(1.0, t);
        out.addCase("checkZeroGradientNorm_aboveEps",
                    json{{"gradientNorm", 1.0}, {"eps", 1e-8}},
                    json{{"result", result}, {"ecTypeOut", static_cast<int>(t)}});
    }
    // Below eps → true, ZeroGradientNorm.
    {
        const EndCriteria ec = make();
        EndCriteria::Type t = EndCriteria::None;
        bool result = ec.checkZeroGradientNorm(1e-20, t);
        out.addCase("checkZeroGradientNorm_belowEps",
                    json{{"gradientNorm", 1e-20}, {"eps", 1e-8}},
                    json{{"result", result}, {"ecTypeOut", static_cast<int>(t)}});
    }

    // ----- operator() — the combined check -----

    // Nothing fires → false, ecType unchanged.
    {
        const EndCriteria ec = make();
        Size stat = 0;
        EndCriteria::Type t = EndCriteria::None;
        bool result = ec(5, stat, false, 5.0, 2.0, 2.0, 1.0, t);
        out.addCase("operator_call_noTrigger",
                    json{{"iteration", 5}, {"fold", 5.0}, {"fnew", 2.0},
                         {"normgnew", 1.0}, {"positiveOpt", false}},
                    json{{"result", result}, {"statOut", static_cast<int>(stat)},
                         {"ecTypeOut", static_cast<int>(t)}});
    }
    // MaxIterations fires first → true, MaxIterations.
    {
        const EndCriteria ec = make();
        Size stat = 0;
        EndCriteria::Type t = EndCriteria::None;
        bool result = ec(100, stat, false, 5.0, 2.0, 2.0, 1.0, t);
        out.addCase("operator_call_maxIter",
                    json{{"iteration", 100}, {"maxIterations", 100}},
                    json{{"result", result}, {"statOut", static_cast<int>(stat)},
                         {"ecTypeOut", static_cast<int>(t)}});
    }

    // ----- succeeded(Type) -----

    // StationaryPoint is a "succeeded" terminal.
    out.addCase("succeeded_StationaryPoint",
                json{{"ecType", "StationaryPoint"}},
                json(EndCriteria::succeeded(EndCriteria::StationaryPoint)));
    // MaxIterations is NOT a success — the optimizer ran out of time.
    out.addCase("succeeded_MaxIterations",
                json{{"ecType", "MaxIterations"}},
                json(EndCriteria::succeeded(EndCriteria::MaxIterations)));
    // StationaryFunctionAccuracy is a success.
    out.addCase("succeeded_StationaryFunctionAccuracy",
                json{{"ecType", "StationaryFunctionAccuracy"}},
                json(EndCriteria::succeeded(EndCriteria::StationaryFunctionAccuracy)));
    // None is not a success.
    out.addCase("succeeded_None",
                json{{"ecType", "None"}},
                json(EndCriteria::succeeded(EndCriteria::None)));

    // ----- Enum value ordinals (for cross-checking Java enum order) -----
    // Java enum order must match C++ or the int casts above won't cross-validate.
    out.addCase("enum_order",
                json{{"description", "enum values in declaration order"}},
                json::array({
                    static_cast<int>(EndCriteria::None),
                    static_cast<int>(EndCriteria::MaxIterations),
                    static_cast<int>(EndCriteria::StationaryPoint),
                    static_cast<int>(EndCriteria::StationaryFunctionValue),
                    static_cast<int>(EndCriteria::StationaryFunctionAccuracy),
                    static_cast<int>(EndCriteria::ZeroGradientNorm),
                    static_cast<int>(EndCriteria::FunctionEpsilonTooSmall),
                    static_cast<int>(EndCriteria::Unknown)
                }));

    out.write();
    return 0;
}
