// migration-harness/cpp/probes/math/optimization/leastsquare_probe.cpp
// Reference values for LeastSquareFunction value/values/gradient/valueAndGradient.
// Uses a tiny hand-rolled LeastSquareProblem so the expected outputs are
// easy to verify by inspection.

#include <ql/version.hpp>
#include <ql/math/optimization/leastsquare.hpp>
#include <ql/math/matrix.hpp>
#include "../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// Tiny concrete problem:
//   target = [1, 2]
//   f(x) = [2*x[0], x[0] + x[1]]    (x is 2-dimensional)
//   Jacobian ∂f/∂x = [[2, 0], [1, 1]]
//
// When x = [0.5, 1.5]: f = [1, 2] = target; diff = [0,0]; value = 0.
// When x = [0, 0]:     f = [0, 0]; diff = [1, 2]; value = 1+4 = 5.
// At x = [0, 0], gradient_r(x) = -2 * J^T * diff
//   J^T = [[2, 1], [0, 1]]
//   J^T * [1, 2] = [2*1+1*2, 0*1+1*2] = [4, 2]
//   grad = [-8, -4]
class TinyProblem : public LeastSquareProblem {
public:
    Size size() override { return 2; }

    void targetAndValue(const Array& x, Array& target, Array& fct2fit) override {
        target[0] = 1.0; target[1] = 2.0;
        fct2fit[0] = 2.0 * x[0];
        fct2fit[1] = x[0] + x[1];
    }

    void targetValueAndGradient(const Array& x, Matrix& grad_fct2fit,
                                Array& target, Array& fct2fit) override {
        targetAndValue(x, target, fct2fit);
        grad_fct2fit[0][0] = 2.0; grad_fct2fit[0][1] = 0.0;
        grad_fct2fit[1][0] = 1.0; grad_fct2fit[1][1] = 1.0;
    }
};

json arrayToJson(const Array& a) {
    json out = json::array();
    for (Size i = 0; i < a.size(); ++i) out.push_back(a[i]);
    return out;
}

} // namespace

int main() {
    ReferenceWriter out("math/optimization/leastsquare", QL_VERSION, "leastsquare_probe");

    TinyProblem tp;
    LeastSquareFunction lsf(tp);

    // ----- value at perfect fit -----
    {
        Array x(2);
        x[0] = 0.5; x[1] = 1.5;
        Real v = lsf.value(x);
        out.addCase("value_perfectFit",
                    json{{"x", arrayToJson(x)}, {"description", "target - f(x) = 0 everywhere"}},
                    json(v));
    }
    // ----- value at origin -----
    {
        Array x(2);
        x[0] = 0.0; x[1] = 0.0;
        Real v = lsf.value(x);
        out.addCase("value_atOrigin",
                    json{{"x", arrayToJson(x)}, {"description", "diff = [1,2]; sum of squares"}},
                    json(v));
    }
    // ----- values (element-wise squared diff) at origin -----
    {
        Array x(2);
        x[0] = 0.0; x[1] = 0.0;
        Array vs = lsf.values(x);
        out.addCase("values_atOrigin",
                    json{{"x", arrayToJson(x)}, {"description", "element-wise (target - f(x))^2"}},
                    arrayToJson(vs));
    }
    // ----- gradient at origin -----
    {
        Array x(2);
        x[0] = 0.0; x[1] = 0.0;
        Array g(2);
        lsf.gradient(g, x);
        out.addCase("gradient_atOrigin",
                    json{{"x", arrayToJson(x)}, {"formula", "-2 * J^T * diff"}},
                    arrayToJson(g));
    }
    // ----- valueAndGradient -----
    {
        Array x(2);
        x[0] = 0.1; x[1] = 0.1;
        // f = [0.2, 0.2], diff = [1 - 0.2, 2 - 0.2] = [0.8, 1.8]
        // value = 0.64 + 3.24 = 3.88
        // J^T = [[2,1],[0,1]], J^T * [0.8, 1.8] = [2*0.8+1*1.8, 0*0.8+1*1.8]
        //     = [3.4, 1.8]; grad = [-6.8, -3.6]
        Array g(2);
        Real v = lsf.valueAndGradient(g, x);
        out.addCase("valueAndGradient_atOneTenth",
                    json{{"x", arrayToJson(x)}},
                    json{{"value", v}, {"gradient", arrayToJson(g)}});
    }

    out.write();
    return 0;
}
