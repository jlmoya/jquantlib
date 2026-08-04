// migration-harness/cpp/probes/math/v143_lbfgsb_probe.cpp
//
// Reference values for LBFGSB — the limited-memory bound-constrained
// quasi-Newton optimizer introduced in C++ QuantLib v1.43
// (ql/math/optimization/lbfgsb.{hpp,cpp}, Byrd/Lu/Nocedal/Zhu 1995).
//
// WHAT NEEDS PINNING, AND WHY
// ---------------------------
// The two places a port silently diverges are the *generalized Cauchy point*
// (Algorithm CP: walking the breakpoints of the projected steepest-descent
// path and pinning variables as they hit a bound) and the *subspace
// minimization* (direct primal method over the free variables, then truncation
// back into the box). Both live in an anonymous namespace inside lbfgsb.cpp,
// so neither is directly observable from outside the translation unit.
//
// Compensating strategy — three independent layers:
//
//   1. MANY DISTINCT BOUND/START CONFIGURATIONS. Interior optimum, optimum
//      clamped on one bound, all-bounds-active corner (empty free set),
//      two bounds reached through *distinct* breakpoints (so the Cauchy walk
//      must traverse more than one), a pinned coordinate (lower == upper),
//      an infeasible start that must be clipped, a start sitting exactly on
//      the bound the gradient pushes into (the `t_i <= 0` branch, for both
//      the upper and the lower bound), a start already at the constrained
//      optimum (zero iterations), and mixed bounded/unbounded coordinates
//      (the +/-0.5*DBL_MAX "no bound" sentinel test). A wrong inner loop
//      cannot pass all of these by luck.
//
//   2. TRUNCATED-ITERATION TRAJECTORY. EndCriteria::maxIterations caps the
//      outer loop, and the iterate is published via Problem::setCurrentValue
//      at the end of every iteration. Running the same problem with
//      maxIterations = 3, 4, ... 12 therefore exposes the iterate sequence
//      one step at a time, which pins Cauchy point + subspace step
//      *indirectly but tightly*.
//
//   3. FULL OBJECTIVE-EVALUATION TRACE. LBFGSB reaches the cost function only
//      through Problem::valueAndGradient, so a recording CostFunction
//      captures every trial point in order: the initial point, then every
//      line-search probe. This pins the search direction and the Wolfe line
//      search itself (c1 = 1e-4, c2 = 0.9, bisection bracket, "accept best
//      sufficient decrease" fallback).
//
// Every case additionally records the terminal EndCriteria::Type, the
// projected-gradient infinity norm (the KKT residual of a box-constrained
// problem), Problem::gradientNormValue() (which LBFGSB sets to pgInf^2, NOT
// to |g|^2), and the function/gradient evaluation counters. The counters are
// structural diagnostics: if x matches but the counts do not, the port's
// inner loop differs and the agreement is luck.
//
// The cost functions and the canonical case setups mirror
// test-suite/optimizers.cpp @ v1.43 (testLBFGSB / testLBFGSBActiveBounds /
// testLBFGSBCoverage) so the values are directly comparable with upstream.

#include <ql/version.hpp>

#include <ql/errors.hpp>
#include <ql/math/array.hpp>
#include <ql/math/optimization/constraint.hpp>
#include <ql/math/optimization/costfunction.hpp>
#include <ql/math/optimization/endcriteria.hpp>
#include <ql/math/optimization/lbfgsb.hpp>
#include <ql/math/optimization/problem.hpp>

#include "common.hpp"

#include <algorithm>
#include <cmath>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// --------------------------------------------------------------------------
// JSON helpers
// --------------------------------------------------------------------------

json arr(const Array& a) {
    json j = json::array();
    for (Size i = 0; i < a.size(); ++i)
        j.push_back(a[i]);
    return j;
}

std::string ecName(EndCriteria::Type t) {
    std::ostringstream os;
    os << t; // QuantLib::operator<<(std::ostream&, EndCriteria::Type)
    return os.str();
}

// --------------------------------------------------------------------------
// Cost functions (verbatim from test-suite/optimizers.cpp @ v1.43)
// --------------------------------------------------------------------------

// Extended Rosenbrock with analytic gradient; global minimum 0 at (1,...,1).
class RosenbrockFunction : public CostFunction {
  public:
    Real value(const Array& x) const override {
        Real f = 0.0;
        for (Size i = 0; i + 1 < x.size(); ++i)
            f += 100.0 * std::pow(x[i + 1] - x[i] * x[i], 2) + std::pow(1.0 - x[i], 2);
        return f;
    }
    Array values(const Array& x) const override { return Array(1, value(x)); }
    void gradient(Array& grad, const Array& x) const override {
        std::fill(grad.begin(), grad.end(), 0.0);
        for (Size i = 0; i + 1 < x.size(); ++i) {
            grad[i] += -400.0 * x[i] * (x[i + 1] - x[i] * x[i]) - 2.0 * (1.0 - x[i]);
            grad[i + 1] += 200.0 * (x[i + 1] - x[i] * x[i]);
        }
    }
    Real valueAndGradient(Array& grad, const Array& x) const override {
        gradient(grad, x);
        return value(x);
    }
};

// Separable quadratic sum_i w_i (x_i - c_i)^2; unconstrained minimum at c.
class WeightedQuadratic : public CostFunction {
  public:
    WeightedQuadratic(Array center, Array weight)
    : center_(std::move(center)), weight_(std::move(weight)) {}
    Real value(const Array& x) const override {
        Real f = 0.0;
        for (Size i = 0; i < x.size(); ++i)
            f += weight_[i] * std::pow(x[i] - center_[i], 2);
        return f;
    }
    Array values(const Array& x) const override { return Array(1, value(x)); }
    void gradient(Array& grad, const Array& x) const override {
        for (Size i = 0; i < x.size(); ++i)
            grad[i] = 2.0 * weight_[i] * (x[i] - center_[i]);
    }
    Real valueAndGradient(Array& grad, const Array& x) const override {
        gradient(grad, x);
        return value(x);
    }
  private:
    Array center_, weight_;
};

// Same quadratic with no analytic gradient, forcing the optimizer onto
// CostFunction's central-difference gradient (finiteDifferenceEpsilon 1e-8).
class WeightedQuadraticValueOnly : public CostFunction {
  public:
    WeightedQuadraticValueOnly(Array center, Array weight)
    : center_(std::move(center)), weight_(std::move(weight)) {}
    Real value(const Array& x) const override {
        Real f = 0.0;
        for (Size i = 0; i < x.size(); ++i)
            f += weight_[i] * std::pow(x[i] - center_[i], 2);
        return f;
    }
    Array values(const Array& x) const override { return Array(1, value(x)); }
  private:
    Array center_, weight_;
};

// Decorator recording every Problem::valueAndGradient call, in order.
// LBFGSB never calls Problem::value or Problem::gradient, so this captures
// the complete objective-evaluation trace of the algorithm.
template <class Base>
class Traced : public Base {
  public:
    using Base::Base;
    Real valueAndGradient(Array& grad, const Array& x) const override {
        const Real f = Base::valueAndGradient(grad, x);
        xs_.push_back(x);
        fs_.push_back(f);
        return f;
    }
    const std::vector<Array>& tracedX() const { return xs_; }
    const std::vector<Real>& tracedF() const { return fs_; }
  private:
    mutable std::vector<Array> xs_;
    mutable std::vector<Real> fs_;
};

// --------------------------------------------------------------------------
// Shared constants and setups
// --------------------------------------------------------------------------

// The EndCriteria used by every upstream LBFGSB test case.
EndCriteria stdEndCriteria() {
    return EndCriteria(1000, 100, 1e-12, 1e-12, 1e-10);
}

const Real INF_BOUND = QL_MAX_REAL; // the "no bound" sentinel, == DBL_MAX

// Canonical quadratic of the upstream test suite.
Array qCenter() { return Array{3.0, -2.0, 0.5}; }
Array qWeight() { return Array{1.0, 4.0, 0.25}; }

// --------------------------------------------------------------------------
// Result description
// --------------------------------------------------------------------------

// Infinity norm of the projected gradient P(x - g, l, u) - x, the quantity
// that vanishes at a KKT point of a box-constrained problem. Recomputed here
// from the cost function's own gradient so it is independent of whatever the
// optimizer happened to cache.
Real projectedGradientNorm(const Array& x, const Array& g, const Array& lo, const Array& hi) {
    Real norm = 0.0;
    for (Size i = 0; i < x.size(); ++i) {
        const Real proj = std::min(std::max(x[i] - g[i], lo[i]), hi[i]) - x[i];
        norm = std::max(norm, std::fabs(proj));
    }
    return norm;
}

json describe(Problem& p, EndCriteria::Type ec, const CostFunction& f,
              const Array& lo, const Array& hi) {
    const Array x = p.currentValue();
    Array g(x.size(), 0.0);
    f.gradient(g, x); // direct call: does not disturb the Problem counters

    return json{
        {"x", arr(x)},
        {"functionValue", p.functionValue()},
        {"gradientNormValue", p.gradientNormValue()}, // LBFGSB stores pgInf^2 here
        {"projectedGradientInfNorm", projectedGradientNorm(x, g, lo, hi)},
        {"gradientAtSolution", arr(g)},
        {"endCriteriaType", static_cast<int>(ec)},
        {"endCriteriaName", ecName(ec)},
        {"functionEvaluations", static_cast<int>(p.functionEvaluation())},
        {"gradientEvaluations", static_cast<int>(p.gradientEvaluation())},
    };
}

json boundsInput(const Array& lo, const Array& hi) {
    return json{{"lowerBound", arr(lo)}, {"upperBound", arr(hi)}};
}

json lbfgsbInput(Size memory, Real pgTol, Real fTol) {
    return json{
        {"memory", static_cast<int>(memory)},
        {"pgTol", pgTol},
        {"fTol", fTol},
        // LBFGSB stores factr_ = fTol / QL_EPSILON and later tests
        // (fOld - f) <= factr_ * QL_EPSILON * denom. A port must reproduce
        // that round-trip literally, not collapse it to fTol * denom.
        {"factr", fTol / QL_EPSILON},
    };
}

json endCriteriaInput(const EndCriteria& ec) {
    return json{
        {"maxIterations", static_cast<int>(ec.maxIterations())},
        {"maxStationaryStateIterations", static_cast<int>(ec.maxStationaryStateIterations())},
        {"rootEpsilon", ec.rootEpsilon()},
        {"functionEpsilon", ec.functionEpsilon()},
        {"gradientNormEpsilon", ec.gradientNormEpsilon()},
    };
}

// --------------------------------------------------------------------------
// Runners
// --------------------------------------------------------------------------

// Box-constrained run driven by a NonhomogeneousBoundaryConstraint.
void runBounded(ReferenceWriter& out,
                const std::string& name,
                CostFunction& f,
                const Array& lo,
                const Array& hi,
                const Array& x0,
                Size memory,
                Real pgTol,
                Real fTol,
                const EndCriteria& endCriteria,
                const json& extraInputs = json::object()) {
    NonhomogeneousBoundaryConstraint c(lo, hi);
    Problem problem(f, c, x0);
    LBFGSB optimizer(memory, pgTol, fTol);
    const EndCriteria::Type ec = optimizer.minimize(problem, endCriteria);

    // "objective" is a default; extraInputs may overwrite it in the loop below.
    json inputs = json{
        {"objective", "weighted_quadratic"},
        {"x0", arr(x0)},
        {"constraint", "NonhomogeneousBoundaryConstraint"},
        {"lbfgsb", lbfgsbInput(memory, pgTol, fTol)},
        {"endCriteria", endCriteriaInput(endCriteria)},
    };
    inputs.update(boundsInput(lo, hi));
    for (auto it = extraInputs.begin(); it != extraInputs.end(); ++it)
        inputs[it.key()] = it.value();

    out.addCase(name, inputs, describe(problem, ec, f, lo, hi));
}

// Unconstrained run (NoConstraint => bounds are -/+ DBL_MAX everywhere).
void runUnconstrained(ReferenceWriter& out,
                      const std::string& name,
                      CostFunction& f,
                      const Array& x0,
                      Size memory,
                      Real pgTol,
                      Real fTol,
                      const EndCriteria& endCriteria,
                      const json& extraInputs = json::object()) {
    NoConstraint c;
    Problem problem(f, c, x0);
    LBFGSB optimizer(memory, pgTol, fTol);
    const EndCriteria::Type ec = optimizer.minimize(problem, endCriteria);

    const Array lo = c.lowerBound(x0);
    const Array hi = c.upperBound(x0);

    // "objective" is a default; extraInputs may overwrite it in the loop below.
    json inputs = json{
        {"objective", "rosenbrock"},
        {"n", static_cast<int>(x0.size())},
        {"x0", arr(x0)},
        {"constraint", "NoConstraint"},
        {"lbfgsb", lbfgsbInput(memory, pgTol, fTol)},
        {"endCriteria", endCriteriaInput(endCriteria)},
    };
    for (auto it = extraInputs.begin(); it != extraInputs.end(); ++it)
        inputs[it.key()] = it.value();

    out.addCase(name, inputs, describe(problem, ec, f, lo, hi));
}

} // namespace

int main() {
    ReferenceWriter out("math/v143_lbfgsb", QL_VERSION, "v143_lbfgsb_probe");

    const EndCriteria endCriteria = stdEndCriteria();

    // The "tight" optimizer settings used by almost every upstream case.
    const Size kMem = 10;
    const Real kPgTol = 1e-10;
    const Real kFTol = 1e1 * QL_EPSILON;

    // ======================================================================
    // 1-4. Unconstrained equivalence: with no active bounds LBFGSB must
    //      degrade to plain limited-memory BFGS and find (1,...,1).
    // ======================================================================
    {
        RosenbrockFunction f;
        runUnconstrained(out, "rosenbrock_2d_unconstrained", f, Array(2, -1.0),
                         kMem, kPgTol, kFTol, endCriteria);
    }
    {
        RosenbrockFunction f;
        runUnconstrained(out, "rosenbrock_10d_unconstrained", f, Array(10, -1.0),
                         kMem, kPgTol, kFTol, endCriteria);
    }
    {
        // memory (3) < dimension (20): forces eviction of correction pairs
        // and repeated rebuilds of the compact representation.
        RosenbrockFunction f;
        runUnconstrained(out, "rosenbrock_20d_small_memory", f, Array(20, -1.0),
                         /*memory*/ 3, 1e-8, kFTol, endCriteria);
    }
    {
        // memory == 1: the minimal non-empty compact representation (col = 1,
        // so W is n x 2 and M is 2 x 2). Isolates the compact-representation
        // algebra from the eviction logic.
        RosenbrockFunction f;
        runUnconstrained(out, "rosenbrock_2d_memory_one", f, Array(2, -1.0),
                         /*memory*/ 1, kPgTol, kFTol, endCriteria);
    }

    // ======================================================================
    // 5. Interior optimum: bounds wide enough to enclose the unconstrained
    //    minimizer, so the answer must equal the unconstrained one.
    // ======================================================================
    {
        WeightedQuadratic f(qCenter(), qWeight());
        runBounded(out, "quadratic_interior_optimum", f,
                   Array(3, -10.0), Array(3, 10.0), Array(3, 0.0),
                   kMem, kPgTol, kFTol, endCriteria,
                   json{{"center", arr(qCenter())}, {"weight", arr(qWeight())}});
    }

    // ======================================================================
    // 6. Active-bound optimum with the DEFAULT constructor
    //    LBFGSB() == LBFGSB(10, 1e-8, 1e7 * QL_EPSILON). The box [0,1]^3
    //    clips (3,-2,0.5) to (1,0,0.5). Upstream asserts the operative stop
    //    is ZeroGradientNorm (the KKT test), not the factr fallback.
    // ======================================================================
    {
        WeightedQuadratic f(qCenter(), qWeight());
        const Array lo(3, 0.0), hi(3, 1.0), x0(3, 0.5);
        NonhomogeneousBoundaryConstraint c(lo, hi);
        Problem problem(f, c, x0);
        LBFGSB optimizer; // defaults
        const EndCriteria::Type ec = optimizer.minimize(problem, endCriteria);

        json inputs = json{
            {"objective", "weighted_quadratic"},
            {"center", arr(qCenter())},
            {"weight", arr(qWeight())},
            {"x0", arr(x0)},
            {"constraint", "NonhomogeneousBoundaryConstraint"},
            {"lbfgsb", lbfgsbInput(10, 1e-8, 1e7 * QL_EPSILON)},
            {"lbfgsbConstructor", "LBFGSB() default arguments"},
            {"endCriteria", endCriteriaInput(endCriteria)},
        };
        inputs.update(boundsInput(lo, hi));
        out.addCase("quadratic_active_bounds_default_ctor", inputs,
                    describe(problem, ec, f, lo, hi));
    }

    // ======================================================================
    // 7. Bound-constrained Rosenbrock: [-2,0.5]^2 clips the optimum;
    //    SciPy's L-BFGS-B converges to (0.5, 0.25) on the boundary.
    // ======================================================================
    {
        RosenbrockFunction f;
        runBounded(out, "rosenbrock_2d_bounded", f,
                   Array(2, -2.0), Array(2, 0.5), Array(2, -1.0),
                   kMem, kPgTol, kFTol, endCriteria, json{{"objective", "rosenbrock"}});
    }

    // ======================================================================
    // 8. All bounds active at a corner: (5,5,5) lies outside [0,1]^3, so
    //    every coordinate is pinned and the free set is EMPTY. Exercises the
    //    nf == 0 early return of the subspace minimization.
    // ======================================================================
    {
        WeightedQuadratic f(Array{5.0, 5.0, 5.0}, Array{1.0, 1.0, 1.0});
        runBounded(out, "quadratic_all_active_corner", f,
                   Array(3, 0.0), Array(3, 1.0), Array(3, 0.5),
                   kMem, kPgTol, kFTol, endCriteria,
                   json{{"center", json::array({5.0, 5.0, 5.0})},
                        {"weight", json::array({1.0, 1.0, 1.0})}});
    }

    // ======================================================================
    // 9. Two bounds reached through DISTINCT breakpoints: the disparate
    //    weights make the projected steepest-descent path hit the two upper
    //    bounds at different step lengths, so the Cauchy walk must traverse
    //    more than one breakpoint (the while(dtMin >= dt) loop body runs).
    // ======================================================================
    {
        WeightedQuadratic f(Array{10.0, 10.0}, Array{1.0, 100.0});
        runBounded(out, "quadratic_two_active_distinct_breakpoints", f,
                   Array(2, 0.0), Array(2, 1.0), Array{0.9, 0.1},
                   kMem, kPgTol, kFTol, endCriteria,
                   json{{"center", json::array({10.0, 10.0})},
                        {"weight", json::array({1.0, 100.0})}});
    }

    // ======================================================================
    // 10. n = 1 with an active bound: min (x-5)^2 over [0,1] is x = 1.
    // ======================================================================
    {
        WeightedQuadratic f(Array{5.0}, Array{1.0});
        runBounded(out, "quadratic_1d_active_bound", f,
                   Array(1, 0.0), Array(1, 1.0), Array(1, 0.5),
                   kMem, kPgTol, kFTol, endCriteria,
                   json{{"center", json::array({5.0})}, {"weight", json::array({1.0})}});
    }

    // ======================================================================
    // 11. Pinned coordinate (lower == upper): the third variable is frozen
    //     at 0.25 while the first two reach their unconstrained optima.
    // ======================================================================
    {
        WeightedQuadratic f(qCenter(), qWeight());
        runBounded(out, "quadratic_pinned_coordinate", f,
                   Array{-10.0, -10.0, 0.25}, Array{10.0, 10.0, 0.25}, Array(3, 0.0),
                   kMem, kPgTol, kFTol, endCriteria,
                   json{{"center", arr(qCenter())}, {"weight", arr(qWeight())}});
    }

    // ======================================================================
    // 12. Infeasible start: x0 = (5,5,5) lies outside [0,1]^3 and must be
    //     clipped into the box BEFORE the first objective evaluation.
    // ======================================================================
    {
        WeightedQuadratic f(qCenter(), qWeight());
        runBounded(out, "quadratic_infeasible_start", f,
                   Array(3, 0.0), Array(3, 1.0), Array(3, 5.0),
                   kMem, kPgTol, kFTol, endCriteria,
                   json{{"center", arr(qCenter())},
                        {"weight", arr(qWeight())},
                        {"note", "x0 outside the box; LBFGSB clips it first"}});
    }

    // ======================================================================
    // 13. Finite-difference gradient with active bounds: the cost function
    //     exposes no analytic gradient, so CostFunction's central-difference
    //     path (eps = 1e-8) runs INSIDE the cost function and therefore does
    //     NOT bump Problem's evaluation counters.
    // ======================================================================
    {
        WeightedQuadraticValueOnly f(Array{3.0, -2.0, 0.7}, Array{1.0, 4.0, 0.25});
        runBounded(out, "quadratic_finite_difference_gradient", f,
                   Array(3, 0.0), Array(3, 1.0), Array(3, 0.5),
                   kMem, /*pgTol*/ 1e-6, /*fTol*/ 1e7 * QL_EPSILON, endCriteria,
                   json{{"objective", "weighted_quadratic_value_only"},
                        {"center", json::array({3.0, -2.0, 0.7})},
                        {"weight", json::array({1.0, 4.0, 0.25})},
                        {"finiteDifferenceEpsilon", 1e-8}});
    }

    // ======================================================================
    // 14-15. Start point ON the bound the gradient pushes into. The Cauchy
    //        breakpoint is t_i = 0 (or -0.0), so the `t_i <= 0` branch fires
    //        and the coordinate is pinned before the walk even starts.
    //        Both the upper- and the lower-bound branch are covered.
    // ======================================================================
    {
        // x0[0] = 1.0 == hi[0], g[0] = 2*(1-5) = -8 < 0 => t_0 = -0.0 <= 0.
        WeightedQuadratic f(Array{5.0, 5.0}, Array{1.0, 1.0});
        runBounded(out, "quadratic_start_on_upper_bound_gradient_pushes_out", f,
                   Array(2, 0.0), Array(2, 1.0), Array{1.0, 0.5},
                   kMem, kPgTol, kFTol, endCriteria,
                   json{{"center", json::array({5.0, 5.0})},
                        {"weight", json::array({1.0, 1.0})},
                        {"note", "x0[0] sits exactly on its upper bound"}});
    }
    {
        // x0[0] = 0.0 == lo[0], g[0] = 2*(0+5) = 10 > 0 => t_0 = 0 <= 0.
        WeightedQuadratic f(Array{-5.0, -5.0}, Array{1.0, 1.0});
        runBounded(out, "quadratic_start_on_lower_bound_gradient_pushes_out", f,
                   Array(2, 0.0), Array(2, 1.0), Array{0.0, 0.5},
                   kMem, kPgTol, kFTol, endCriteria,
                   json{{"center", json::array({-5.0, -5.0})},
                        {"weight", json::array({1.0, 1.0})},
                        {"note", "x0[0] sits exactly on its lower bound"}});
    }

    // ======================================================================
    // 16. Start already AT the constrained optimum: pgInf == 0 on the very
    //     first check, so the loop exits with ZeroGradientNorm after exactly
    //     one objective evaluation and zero iterations.
    // ======================================================================
    {
        WeightedQuadratic f(Array{5.0, 5.0}, Array{1.0, 1.0});
        runBounded(out, "quadratic_start_at_optimum_corner", f,
                   Array(2, 0.0), Array(2, 1.0), Array(2, 1.0),
                   kMem, kPgTol, kFTol, endCriteria,
                   json{{"center", json::array({5.0, 5.0})},
                        {"weight", json::array({1.0, 1.0})},
                        {"note", "zero-iteration path: pgInf == 0 at x0"}});
    }

    // ======================================================================
    // 17. Mixed bounded / unbounded coordinates. Coordinates 0 is free on
    //     both sides (+/-DBL_MAX), coordinate 1 is boxed in [0,1] and
    //     coordinate 2 has only an upper bound. Pins the noUpper/noLower
    //     sentinel test, which is `u >= 0.5*DBL_MAX` / `l <= -0.5*DBL_MAX`
    //     (the 0.5 guards against overflow in x - bound), NOT `u == DBL_MAX`.
    //     Expected optimum: (3, 0, 0.25).
    // ======================================================================
    {
        WeightedQuadratic f(qCenter(), qWeight());
        runBounded(out, "quadratic_mixed_bounded_unbounded", f,
                   Array{-INF_BOUND, 0.0, -INF_BOUND},
                   Array{INF_BOUND, 1.0, 0.25},
                   Array{0.0, 0.5, 0.0},
                   kMem, kPgTol, kFTol, endCriteria,
                   json{{"center", arr(qCenter())},
                        {"weight", arr(qWeight())},
                        {"note", "+/-QL_MAX_REAL marks an absent bound"}});
    }

    // ======================================================================
    // 18-19. The two-Array constructor OVERRIDES the problem's constraint —
    //        it does not intersect with it. Case 18 narrows an unconstrained
    //        problem; case 19 WIDENS a [0,1]^3 constraint back to [-10,10]^3
    //        and recovers the interior optimum, which an intersecting
    //        implementation could never do.
    // ======================================================================
    {
        WeightedQuadratic f(qCenter(), qWeight());
        const Array lo(3, 0.0), hi(3, 1.0), x0(3, 0.5);
        NoConstraint c; // says "unbounded"
        Problem problem(f, c, x0);
        LBFGSB optimizer(lo, hi, kMem, kPgTol, kFTol);
        const EndCriteria::Type ec = optimizer.minimize(problem, endCriteria);

        json inputs = json{
            {"objective", "weighted_quadratic"},
            {"center", arr(qCenter())},
            {"weight", arr(qWeight())},
            {"x0", arr(x0)},
            {"constraint", "NoConstraint"},
            {"lbfgsb", lbfgsbInput(kMem, kPgTol, kFTol)},
            {"lbfgsbConstructor", "LBFGSB(lowerBound, upperBound, memory, pgTol, fTol)"},
            {"endCriteria", endCriteriaInput(endCriteria)},
        };
        inputs.update(boundsInput(lo, hi));
        out.addCase("explicit_bounds_ctor_overrides_constraint", inputs,
                    describe(problem, ec, f, lo, hi));
    }
    {
        WeightedQuadratic f(qCenter(), qWeight());
        const Array ctorLo(3, -10.0), ctorHi(3, 10.0), x0(3, 0.5);
        NonhomogeneousBoundaryConstraint c(Array(3, 0.0), Array(3, 1.0)); // ignored
        Problem problem(f, c, x0);
        LBFGSB optimizer(ctorLo, ctorHi, kMem, kPgTol, kFTol);
        const EndCriteria::Type ec = optimizer.minimize(problem, endCriteria);

        json inputs = json{
            {"objective", "weighted_quadratic"},
            {"center", arr(qCenter())},
            {"weight", arr(qWeight())},
            {"x0", arr(x0)},
            {"constraint", "NonhomogeneousBoundaryConstraint([0,1]^3) - overridden"},
            {"constraintLowerBound", arr(Array(3, 0.0))},
            {"constraintUpperBound", arr(Array(3, 1.0))},
            {"lbfgsb", lbfgsbInput(kMem, kPgTol, kFTol)},
            {"lbfgsbConstructor", "LBFGSB(lowerBound, upperBound, memory, pgTol, fTol)"},
            {"endCriteria", endCriteriaInput(endCriteria)},
        };
        inputs.update(boundsInput(ctorLo, ctorHi));
        out.addCase("explicit_bounds_ctor_widens_constraint", inputs,
                    describe(problem, ec, f, ctorLo, ctorHi));
    }

    // ======================================================================
    // 20. EndCriteria::maxIterations stop. maxStationaryStateIterations must
    //     be > 1 and < maxIterations, so 3 is the smallest usable cap.
    // ======================================================================
    {
        RosenbrockFunction f;
        const EndCriteria capped(3, 2, 1e-12, 1e-12, 1e-10);
        runUnconstrained(out, "rosenbrock_10d_max_iterations_stop", f, Array(10, -1.0),
                         kMem, kPgTol, kFTol, capped,
                         json{{"note", "expects EndCriteria::MaxIterations"}});
    }

    // ======================================================================
    // 21. Truncated-iteration TRAJECTORY on the bound-constrained Rosenbrock.
    //     Running the same problem with maxIterations = 3..12 exposes the
    //     iterate sequence step by step. This is the strongest available
    //     proxy for the (private) Cauchy point + subspace minimization:
    //     a wrong inner loop diverges from the very first captured iterate.
    // ======================================================================
    {
        const Array lo(2, -2.0), hi(2, 0.5), x0(2, -1.0);
        json steps = json::array();
        for (Size k = 3; k <= 12; ++k) {
            RosenbrockFunction f;
            NonhomogeneousBoundaryConstraint c(lo, hi);
            Problem problem(f, c, x0);
            LBFGSB optimizer(kMem, kPgTol, kFTol);
            const EndCriteria capped(k, 2, 1e-12, 1e-12, 1e-10);
            const EndCriteria::Type ec = optimizer.minimize(problem, capped);
            steps.push_back(json{
                {"maxIterations", static_cast<int>(k)},
                {"x", arr(problem.currentValue())},
                {"functionValue", problem.functionValue()},
                {"gradientNormValue", problem.gradientNormValue()},
                {"endCriteriaType", static_cast<int>(ec)},
                {"endCriteriaName", ecName(ec)},
                {"functionEvaluations", static_cast<int>(problem.functionEvaluation())},
            });
        }
        json inputs = json{
            {"objective", "rosenbrock"},
            {"x0", arr(x0)},
            {"constraint", "NonhomogeneousBoundaryConstraint"},
            {"lbfgsb", lbfgsbInput(kMem, kPgTol, kFTol)},
            {"maxIterationsSweep", json::array({3, 4, 5, 6, 7, 8, 9, 10, 11, 12})},
            {"endCriteriaTemplate", "EndCriteria(k, 2, 1e-12, 1e-12, 1e-10)"},
        };
        inputs.update(boundsInput(lo, hi));
        out.addCase("rosenbrock_2d_bounded_iterate_trajectory", inputs,
                    json{{"steps", steps}});
    }

    // ======================================================================
    // 22. EndCriteria::checkZeroGradientNorm stop, distinct from LBFGSB's own
    //     pgTol_ test. pgTol_ is set to 1e-12 while gradientNormEpsilon is
    //     1e-2, so the *EndCriteria* branch fires first. Both branches report
    //     ZeroGradientNorm, so only the resulting x distinguishes them.
    // ======================================================================
    {
        WeightedQuadratic f(qCenter(), qWeight());
        const EndCriteria loose(1000, 100, 1e-12, 1e-12, /*gradientNormEpsilon*/ 1e-2);
        runBounded(out, "quadratic_endcriteria_gradient_norm_stop", f,
                   Array(3, -10.0), Array(3, 10.0), Array(3, 0.0),
                   kMem, /*pgTol*/ 1e-12, kFTol, loose,
                   json{{"center", arr(qCenter())},
                        {"weight", arr(qWeight())},
                        {"note", "stops on EndCriteria.gradientNormEpsilon, not on pgTol"}});
    }

    // ======================================================================
    // 23-24. Full objective-evaluation traces. Entry 0 is the initial
    //        evaluation at the (clipped) start point; every later entry is a
    //        Wolfe line-search trial point, in call order. This pins the
    //        search direction produced by the Cauchy point + subspace step
    //        AND the line search (c1 = 1e-4, c2 = 0.9, doubling expansion
    //        capped at the largest feasible step, then bisection).
    // ======================================================================
    {
        Traced<WeightedQuadratic> f(Array{10.0, 10.0}, Array{1.0, 100.0});
        const Array lo(2, 0.0), hi(2, 1.0), x0{0.9, 0.1};
        NonhomogeneousBoundaryConstraint c(lo, hi);
        Problem problem(f, c, x0);
        LBFGSB optimizer(kMem, kPgTol, kFTol);
        const EndCriteria::Type ec = optimizer.minimize(problem, endCriteria);

        json trace = json::array();
        for (Size i = 0; i < f.tracedX().size(); ++i)
            trace.push_back(json{{"x", arr(f.tracedX()[i])}, {"f", f.tracedF()[i]}});

        json expected = describe(problem, ec, f, lo, hi);
        expected["evaluationCount"] = static_cast<int>(f.tracedX().size());
        expected["evaluations"] = trace;

        json inputs = json{
            {"objective", "weighted_quadratic"},
            {"center", json::array({10.0, 10.0})},
            {"weight", json::array({1.0, 100.0})},
            {"x0", arr(x0)},
            {"constraint", "NonhomogeneousBoundaryConstraint"},
            {"lbfgsb", lbfgsbInput(kMem, kPgTol, kFTol)},
            {"endCriteria", endCriteriaInput(endCriteria)},
            {"note", "every Problem::valueAndGradient call, in order"},
        };
        inputs.update(boundsInput(lo, hi));
        out.addCase("quadratic_two_active_eval_trace", inputs, expected);
    }
    {
        Traced<RosenbrockFunction> f;
        const Array lo(2, -2.0), hi(2, 0.5), x0(2, -1.0);
        NonhomogeneousBoundaryConstraint c(lo, hi);
        Problem problem(f, c, x0);
        LBFGSB optimizer(kMem, kPgTol, kFTol);
        const EndCriteria::Type ec = optimizer.minimize(problem, endCriteria);

        json trace = json::array();
        for (Size i = 0; i < f.tracedX().size(); ++i)
            trace.push_back(json{{"x", arr(f.tracedX()[i])}, {"f", f.tracedF()[i]}});

        json expected = describe(problem, ec, f, lo, hi);
        expected["evaluationCount"] = static_cast<int>(f.tracedX().size());
        expected["evaluations"] = trace;

        json inputs = json{
            {"objective", "rosenbrock"},
            {"x0", arr(x0)},
            {"constraint", "NonhomogeneousBoundaryConstraint"},
            {"lbfgsb", lbfgsbInput(kMem, kPgTol, kFTol)},
            {"endCriteria", endCriteriaInput(endCriteria)},
            {"note", "every Problem::valueAndGradient call, in order"},
        };
        inputs.update(boundsInput(lo, hi));
        out.addCase("rosenbrock_2d_bounded_eval_trace", inputs, expected);
    }

    // ======================================================================
    // 25. Constructor argument validation. Message text is checked by
    //     substring so the case stays independent of whether the build
    //     prefixes errors with file/line.
    // ======================================================================
    {
        bool zeroMemoryThrows = false, zeroMemoryMessageMatches = false;
        try {
            LBFGSB bad(0);
            (void)bad;
        } catch (const std::exception& e) {
            zeroMemoryThrows = true;
            zeroMemoryMessageMatches =
                std::string(e.what()).find("memory must be positive") != std::string::npos;
        }

        bool mismatchedBoundsThrows = false, mismatchedBoundsMessageMatches = false;
        try {
            LBFGSB bad(Array(2, 0.0), Array(3, 1.0), 10);
            (void)bad;
        } catch (const std::exception& e) {
            mismatchedBoundsThrows = true;
            mismatchedBoundsMessageMatches =
                std::string(e.what()).find("lower and upper bound sizes are inconsistent") !=
                std::string::npos;
        }

        // Bounds whose size disagrees with the problem dimension is rejected
        // inside minimize(), not by the constructor.
        bool wrongDimensionThrows = false, wrongDimensionMessageMatches = false;
        try {
            WeightedQuadratic f(Array{1.0, 2.0}, Array{1.0, 1.0});
            NoConstraint c;
            Problem problem(f, c, Array(2, 0.0));
            LBFGSB bad(Array(3, 0.0), Array(3, 1.0));
            bad.minimize(problem, stdEndCriteria());
        } catch (const std::exception& e) {
            wrongDimensionThrows = true;
            wrongDimensionMessageMatches =
                std::string(e.what()).find("bounds size does not match the number of variables") !=
                std::string::npos;
        }

        out.addCase("constructor_argument_validation",
                    json{{"zeroMemory", "LBFGSB(0)"},
                         {"mismatchedBounds", "LBFGSB(Array(2), Array(3), 10)"},
                         {"wrongDimension", "LBFGSB(Array(3), Array(3)).minimize(2-variable problem)"}},
                    json{{"zeroMemoryThrows", zeroMemoryThrows},
                         {"zeroMemoryMessageMatches", zeroMemoryMessageMatches},
                         {"mismatchedBoundsThrows", mismatchedBoundsThrows},
                         {"mismatchedBoundsMessageMatches", mismatchedBoundsMessageMatches},
                         {"wrongDimensionThrows", wrongDimensionThrows},
                         {"wrongDimensionMessageMatches", wrongDimensionMessageMatches}});
    }

    out.write();
    return 0;
}
