// migration-harness/cpp/probes/math/optimization/linesearch_probe.cpp
// Reference values for LineSearch::update(). LineSearch itself is abstract
// (pure virtual operator()); only update() is a concrete member, so that's
// what we can validate directly.

#include <ql/version.hpp>
#include <ql/math/array.hpp>
#include <ql/math/optimization/linesearch.hpp>
#include <ql/math/optimization/constraint.hpp>
#include "../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {
// Minimal concrete LineSearch just to instantiate for probing update().
// operator() must be overridden; body intentionally unused.
class StubLineSearch : public LineSearch {
public:
    Real operator()(Problem&, EndCriteria::Type&, const EndCriteria&, Real) override {
        return 0.0;
    }
};

json arrayToJson(const Array& a) {
    json out = json::array();
    for (Size i = 0; i < a.size(); ++i) out.push_back(a[i]);
    return out;
}
} // namespace

int main() {
    ReferenceWriter out("math/optimization/linesearch", QL_VERSION, "linesearch_probe");

    // Case A: unbounded constraint + nonzero beta → no halving; params mutated
    // to params + beta * direction; returned diff equals beta.
    {
        StubLineSearch ls;
        Array params(3);
        params[0] = 1.0; params[1] = 2.0; params[2] = 3.0;
        Array direction(3);
        direction[0] = 0.1; direction[1] = 0.2; direction[2] = 0.3;
        NoConstraint nc;
        Real beta = 2.0;
        Array paramsInSnapshot = params;
        Real diff = ls.update(params, direction, beta, nc);
        out.addCase("update_noConstraint",
                    json{{"paramsIn", arrayToJson(paramsInSnapshot)},
                         {"direction", arrayToJson(direction)},
                         {"beta", beta}},
                    json{{"diff", diff}, {"paramsOut", arrayToJson(params)}});
    }

    // Case B: constraint that only accepts nonnegative values. With
    // params=[1,1] and direction=[-1,-1] and beta=3, direct step → [-2,-2]
    // fails. Algorithm halves beta until params+diff*direction is valid.
    //   beta=3: [-2,-2] invalid → beta=1.5: [-0.5,-0.5] invalid → beta=0.75:
    //   [0.25, 0.25] valid. Expected diff=0.75; params mutated to [0.25,0.25].
    {
        StubLineSearch ls;
        Array params(2);
        params[0] = 1.0; params[1] = 1.0;
        Array direction(2);
        direction[0] = -1.0; direction[1] = -1.0;
        PositiveConstraint pc;
        Real beta = 3.0;
        Array paramsInSnapshot = params;
        Real diff = ls.update(params, direction, beta, pc);
        out.addCase("update_positiveConstraint",
                    json{{"paramsIn", arrayToJson(paramsInSnapshot)},
                         {"direction", arrayToJson(direction)},
                         {"beta", beta}},
                    json{{"diff", diff}, {"paramsOut", arrayToJson(params)}});
    }

    out.write();
    return 0;
}
