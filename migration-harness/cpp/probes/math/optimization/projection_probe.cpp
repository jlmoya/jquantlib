// migration-harness/cpp/probes/math/optimization/projection_probe.cpp
// Reference values for Projection + ProjectedCostFunction.
// The C++ design (v1.42.1) split Projection into its own class (ql/math/optimization/projection.hpp)
// that ProjectedCostFunction multiply-inherits from. Java mirrors this by composition.

#include <ql/version.hpp>
#include <ql/math/optimization/projection.hpp>
#include <ql/math/optimization/projectedcostfunction.hpp>
#include <ql/math/optimization/costfunction.hpp>
#include <ql/math/array.hpp>
#include <vector>
#include "../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {
// A trivial CostFunction for deterministic testing: value = sum of parameters;
// values returns the parameters unchanged.
class SumCostFunction : public CostFunction {
public:
    Real value(const Array& x) const override {
        Real s = 0.0;
        for (Size i = 0; i < x.size(); ++i) s += x[i];
        return s;
    }
    Array values(const Array& x) const override { return x; }
};

json arrayToJson(const Array& a) {
    json out = json::array();
    for (Size i = 0; i < a.size(); ++i) out.push_back(a[i]);
    return out;
}
} // namespace

int main() {
    ReferenceWriter out("math/optimization/projection", QL_VERSION, "projection_probe");

    // Scenario A: 4 parameters, 2 fixed (indices 1 and 3).
    // parameterValues: [1, 2, 3, 4]; fixParameters: [false, true, false, true]
    Array paramsA(4);
    paramsA[0] = 1.0; paramsA[1] = 2.0; paramsA[2] = 3.0; paramsA[3] = 4.0;
    std::vector<bool> fixA = { false, true, false, true };
    Projection projA(paramsA, fixA);

    // project([1,2,3,4]) -> [1, 3] (the free indices)
    {
        Array result = projA.project(paramsA);
        out.addCase("project_4params_2fixed",
                    json{{"params", arrayToJson(paramsA)}, {"fixParameters", {false, true, false, true}}},
                    arrayToJson(result));
    }

    // include([10, 30]) -> [10, 2, 30, 4] (reinsert into fixed slots)
    {
        Array free(2);
        free[0] = 10.0; free[1] = 30.0;
        Array result = projA.include(free);
        out.addCase("include_4params_2fixed",
                    json{{"freeValues", arrayToJson(free)}, {"fixParameters", {false, true, false, true}}},
                    arrayToJson(result));
    }

    // Scenario B: numberOfFreeParameters = all free (fixParameters all false)
    {
        Array paramsB(3);
        paramsB[0] = 10.0; paramsB[1] = 20.0; paramsB[2] = 30.0;
        std::vector<bool> fixB = { false, false, false };
        Projection projB(paramsB, fixB);
        Array result = projB.project(paramsB);
        out.addCase("project_allFree",
                    json{{"params", arrayToJson(paramsB)}, {"fixParameters", {false, false, false}}},
                    arrayToJson(result));
    }

    // Scenario C: empty fixParameters defaults to all-false (C++ behavior).
    // Java doesn't support defaulted empty vector the same way; Java probe tests
    // the explicit-all-false form but Java users MUST pass a non-null, correctly
    // sized array.
    {
        Array paramsC(2);
        paramsC[0] = 5.0; paramsC[1] = 7.0;
        std::vector<bool> emptyFix;  // empty -> defaults to all-false
        Projection projC(paramsC, emptyFix);
        Array result = projC.project(paramsC);
        out.addCase("project_defaultFix",
                    json{{"params", arrayToJson(paramsC)}, {"fixParameters", "defaultAllFalse"}},
                    arrayToJson(result));
    }

    // ProjectedCostFunction.value: delegates to inner cost function on
    // re-composed parameter array.
    {
        auto costFn = std::make_shared<SumCostFunction>();
        ProjectedCostFunction pcf(*costFn, paramsA, fixA);
        Array free(2);
        free[0] = 100.0; free[1] = 300.0;
        // mapFreeParameters sets actualParameters_ = [100, 2, 300, 4]; sum = 406
        Real v = pcf.value(free);
        out.addCase("projectedCostFunction_value",
                    json{{"params", arrayToJson(paramsA)}, {"fixParameters", {false, true, false, true}},
                         {"freeValues", arrayToJson(free)}, {"innerCost", "sum"}},
                    json(v));
    }

    out.write();
    return 0;
}
