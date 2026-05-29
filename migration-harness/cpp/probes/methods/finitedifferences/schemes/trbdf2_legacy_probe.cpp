// migration-harness/cpp/probes/methods/finitedifferences/schemes/trbdf2_legacy_probe.cpp
//
// Structural cross-validation for the gap-fdm port of the LEGACY
// template<class Operator> class TRBDF2 (ql/methods/finitedifferences/trbdf2.hpp).
//
// This is the dead-upstream legacy scheme (zero instantiations in v1.42.1),
// ported for legacy-family completeness. We instantiate it on a trivial,
// time-constant linear operator (a TridiagonalOperator) with an EMPTY
// boundary-condition set and compare the two-stage TR-BDF2 stepped vector
// against the Java port for the same operator / dt / initial vector.
//
// Operator L (size 5): a scaled discrete second-difference (heat-like)
//   first row : ( -2,  1 )         * c
//   mid rows  : (  1, -2,  1 )     * c
//   last row  : (  1, -2 )         * c
// with c = 0.5. dt = 0.1. Initial vector a = {1,2,3,4,5}.
//
// One step at t = 1.0 (irrelevant for a time-constant operator, but exercises
// the setTime calls). TIGHT tier.

#include <ql/version.hpp>
#include <ql/methods/finitedifferences/trbdf2.hpp>
#include <ql/methods/finitedifferences/tridiagonaloperator.hpp>
#include <ql/methods/finitedifferences/boundarycondition.hpp>
#include <ql/math/array.hpp>
#include <ql/shared_ptr.hpp>
#include "../../../common.hpp"

#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

json arrayToJson(const Array& v) {
    json a = json::array();
    for (Size i = 0; i < v.size(); ++i) a.push_back(v[i]);
    return a;
}

TridiagonalOperator makeOp(Size n, Real c) {
    TridiagonalOperator L(n);
    L.setFirstRow(-2.0 * c, 1.0 * c);
    L.setMidRows(1.0 * c, -2.0 * c, 1.0 * c);
    L.setLastRow(1.0 * c, -2.0 * c);
    return L;
}

} // namespace

int main() {
    ReferenceWriter out("methods/finitedifferences/schemes/trbdf2_legacy",
                        QL_VERSION,
                        "trbdf2_legacy_probe");

    typedef OperatorTraits<TridiagonalOperator>::bc_set bc_set;

    const Size n = 5;
    const Real c = 0.5;
    const Time dt = 0.1;

    // ---- single step ----
    {
        TridiagonalOperator L = makeOp(n, c);
        bc_set bcs; // empty

        TRBDF2<TridiagonalOperator> scheme(L, bcs);
        scheme.setStep(dt);

        Array a(n);
        for (Size i = 0; i < n; ++i) a[i] = static_cast<Real>(i + 1); // {1..5}

        scheme.step(a, 1.0);

        out.addCase("single_step_n5_c0.5_dt0.1",
            json{ {"n", (long long)n}, {"c", c}, {"dt", dt},
                  {"alpha", 2.0 - std::sqrt(2.0)},
                  {"initial", json::array({1.0, 2.0, 3.0, 4.0, 5.0})}, {"t", 1.0} },
            json{ {"stepped", arrayToJson(a)} });
    }

    // ---- three sequential steps (re-uses same dt) ----
    {
        TridiagonalOperator L = makeOp(n, c);
        bc_set bcs;

        TRBDF2<TridiagonalOperator> scheme(L, bcs);
        scheme.setStep(dt);

        Array a(n);
        for (Size i = 0; i < n; ++i) a[i] = static_cast<Real>(i + 1);

        Time t = 1.0;
        for (int s = 0; s < 3; ++s, t -= dt) {
            scheme.step(a, t);
        }

        out.addCase("three_steps_n5_c0.5_dt0.1",
            json{ {"n", (long long)n}, {"c", c}, {"dt", dt}, {"steps", 3},
                  {"initial", json::array({1.0, 2.0, 3.0, 4.0, 5.0})}, {"t0", 1.0} },
            json{ {"stepped", arrayToJson(a)} });
    }

    out.write();
    return 0;
}
