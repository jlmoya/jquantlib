// migration-harness/cpp/probes/math/v143_flatextrapolation_probe.cpp
//
// Reference values for FlatExtrapolator — the header-only 1-D interpolation
// decorator introduced in C++ QuantLib v1.43
// (ql/math/interpolations/flatextrapolation.hpp).
//
// The decorator clamps the abscissa into [xMin, xMax] before delegating, and
// overrides the calculus accordingly:
//
//   value(x)            = decorated(clamp(x, xMin, xMax), allowExtrapolation=true)
//   derivative(x)       = 0                       if x < xMin || x > xMax
//                       = decorated.derivative(x) otherwise
//   secondDerivative(x) = 0                       if x < xMin || x > xMax
//                       = decorated.secondDerivative(x) otherwise
//   primitive(x)        = decorated.primitive(xMin) + decorated(xMin)*(x - xMin)   [x < xMin]
//                       = decorated.primitive(xMax) + decorated(xMax)*(x - xMax)   [x > xMax]
//                       = decorated.primitive(x)                                   [otherwise]
//
// WHAT NEEDS PINNING, AND WHY
// ---------------------------
//   * The endpoint boundary is STRICT (`x < xMin || x > xMax`). At exactly
//     xMin and xMax the derivative and second derivative come from the
//     underlying interpolation, NOT from the flat branch. A port that writes
//     `<=` / `>=` there silently zeroes the endpoint slopes, and a natural
//     spline (second derivative 0 at both ends by construction) would hide the
//     bug — so a NOT-A-KNOT spline, whose endpoint second derivative is
//     nonzero, is probed as well.
//   * The primitive must EXTEND LINEARLY outside the range (constant
//     integrand => affine antiderivative), not stay flat and not clamp.
//   * The decorator has its OWN Extrapolator flag. Enabling extrapolation on
//     the decorated interpolation does not enable it on the decorator, so
//     FlatExtrapolator still throws out of range until its own
//     enableExtrapolation() is called. (Internally it always passes
//     allowExtrapolation = true down to the decorated object.)
//   * Three different underlyings are decorated (natural cubic spline,
//     not-a-knot cubic spline, linear) plus a raw-underlying reference block,
//     so a failure can be localised to the decorator vs the interpolator.
//
// Mirrors test-suite/interpolations.cpp @ v1.43 (testFlatExtrapolation) for
// the natural-spline data, and extends it.

#include <ql/version.hpp>

#include <ql/math/interpolations/cubicinterpolation.hpp>
#include <ql/math/interpolations/flatextrapolation.hpp>
#include <ql/math/interpolations/linearinterpolation.hpp>
#include <ql/shared_ptr.hpp>
#include <ql/utilities/null.hpp>

#include "common.hpp"

#include <iterator>
#include <string>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// --------------------------------------------------------------------------
// Data. Namespace scope so it outlives every interpolation built on it
// (Interpolation stores iterators, not copies).
// --------------------------------------------------------------------------

// Upstream testFlatExtrapolation data.
const Real kX[] = {0.0, 1.0, 2.0, 3.0, 4.0};
const Real kY[] = {5.0, 3.0, 4.0, 2.0, 1.0};

// Non-collinear data for the linear-interpolation underlying.
const Real kXL[] = {0.0, 1.0, 2.0, 3.0, 4.0};
const Real kYL[] = {1.0, 2.5, 2.0, 4.0, 3.5};

// Probe abscissae.
const std::vector<Real> kNodes = {0.0, 1.0, 2.0, 3.0, 4.0};
const std::vector<Real> kMidpoints = {0.5, 1.5, 2.5, 3.5};
const std::vector<Real> kBelow = {-100.0, -10.0, -2.0, -1.0, -0.01};
const std::vector<Real> kAbove = {4.01, 5.0, 10.0, 100.0};
const std::vector<Real> kEndpoints = {0.0, 4.0};

// --------------------------------------------------------------------------
// Helpers
// --------------------------------------------------------------------------

json vec(const std::vector<Real>& v) {
    json j = json::array();
    for (Real r : v)
        j.push_back(r);
    return j;
}

using Sampler = Real (*)(const Interpolation&, Real);

Real sampleValue(const Interpolation& i, Real x) { return i(x); }
Real sampleDerivative(const Interpolation& i, Real x) { return i.derivative(x); }
Real sampleSecondDerivative(const Interpolation& i, Real x) { return i.secondDerivative(x); }
Real samplePrimitive(const Interpolation& i, Real x) { return i.primitive(x); }

json sampleAt(const Interpolation& interp, const std::vector<Real>& xs, Sampler s) {
    json j = json::array();
    for (Real x : xs)
        j.push_back(s(interp, x));
    return j;
}

// Emit one {"x": [...], "values": [...]} case.
void addSampled(ReferenceWriter& out,
                const std::string& name,
                const Interpolation& interp,
                const std::vector<Real>& xs,
                Sampler s,
                const std::string& quantity,
                const std::string& underlying) {
    out.addCase(name,
                json{{"underlying", underlying}, {"quantity", quantity}, {"x", vec(xs)}},
                json{{"values", sampleAt(interp, xs, s)}});
}

json accessors(const Interpolation& interp) {
    const std::vector<Real> xs = interp.xValues();
    const std::vector<Real> ys = interp.yValues();
    return json{
        {"xMin", interp.xMin()},
        {"xMax", interp.xMax()},
        {"xValues", vec(xs)},
        {"yValues", vec(ys)},
        {"size", static_cast<int>(xs.size())},
        {"empty", interp.empty()},
        {"allowsExtrapolation", interp.allowsExtrapolation()},
        {"isInRange_xMin", interp.isInRange(interp.xMin())},
        {"isInRange_xMax", interp.isInRange(interp.xMax())},
        {"isInRange_2", interp.isInRange(2.0)},
        {"isInRange_minus1", interp.isInRange(-1.0)},
        {"isInRange_5", interp.isInRange(5.0)},
    };
}

} // namespace

int main() {
    ReferenceWriter out("math/v143_flatextrapolation", QL_VERSION, "v143_flatextrapolation_probe");

    // ======================================================================
    // Underlying 1: natural cubic spline (SecondDerivative 0 at both ends),
    // exactly as in upstream testFlatExtrapolation.
    // ======================================================================
    auto naturalCubic = ext::make_shared<CubicInterpolation>(
        std::begin(kX), std::end(kX), std::begin(kY),
        CubicInterpolation::Spline, false,
        CubicInterpolation::SecondDerivative, 0.0,
        CubicInterpolation::SecondDerivative, 0.0);
    naturalCubic->enableExtrapolation();

    FlatExtrapolator flatCubic(naturalCubic);

    // ---- 1. Extrapolation is NOT allowed by default -----------------------
    // The decorator carries its own Extrapolator flag; the decorated object
    // already has extrapolation enabled and that does not propagate.
    {
        bool valueBelowThrows = false, valueAboveThrows = false;
        bool derivativeBelowThrows = false, secondDerivativeAboveThrows = false;
        bool primitiveBelowThrows = false;
        bool valueInRangeThrows = false;

        try { flatCubic(-1.0); } catch (const std::exception&) { valueBelowThrows = true; }
        try { flatCubic(5.0); } catch (const std::exception&) { valueAboveThrows = true; }
        try { flatCubic.derivative(-1.0); } catch (const std::exception&) { derivativeBelowThrows = true; }
        try { flatCubic.secondDerivative(5.0); } catch (const std::exception&) { secondDerivativeAboveThrows = true; }
        try { flatCubic.primitive(-1.0); } catch (const std::exception&) { primitiveBelowThrows = true; }
        try { flatCubic(2.0); } catch (const std::exception&) { valueInRangeThrows = true; }

        out.addCase("cubic_extrapolation_not_allowed_by_default",
                    json{{"underlying", "natural_cubic_spline"},
                         {"note", "decorated interpolation already has extrapolation enabled"}},
                    json{{"decoratorAllowsExtrapolation", flatCubic.allowsExtrapolation()},
                         {"decoratedAllowsExtrapolation", naturalCubic->allowsExtrapolation()},
                         {"valueBelowThrows", valueBelowThrows},
                         {"valueAboveThrows", valueAboveThrows},
                         {"derivativeBelowThrows", derivativeBelowThrows},
                         {"secondDerivativeAboveThrows", secondDerivativeAboveThrows},
                         {"primitiveBelowThrows", primitiveBelowThrows},
                         {"valueInRangeThrows", valueInRangeThrows}});
    }

    flatCubic.enableExtrapolation();

    // ---- 2-6. value ------------------------------------------------------
    addSampled(out, "cubic_value_at_nodes", flatCubic, kNodes, sampleValue,
               "value", "natural_cubic_spline");
    addSampled(out, "cubic_value_in_range_midpoints", flatCubic, kMidpoints, sampleValue,
               "value", "natural_cubic_spline");
    addSampled(out, "cubic_value_below_range", flatCubic, kBelow, sampleValue,
               "value", "natural_cubic_spline");
    addSampled(out, "cubic_value_above_range", flatCubic, kAbove, sampleValue,
               "value", "natural_cubic_spline");
    addSampled(out, "cubic_value_at_endpoints", flatCubic, kEndpoints, sampleValue,
               "value", "natural_cubic_spline");

    // ---- 7-9. derivative -------------------------------------------------
    addSampled(out, "cubic_derivative_in_range", flatCubic, kMidpoints, sampleDerivative,
               "derivative", "natural_cubic_spline");
    // Strict-inequality boundary: at exactly xMin/xMax the underlying slope
    // is used, NOT the flat 0.
    addSampled(out, "cubic_derivative_at_endpoints", flatCubic, kEndpoints, sampleDerivative,
               "derivative", "natural_cubic_spline");
    {
        std::vector<Real> xs = kBelow;
        xs.insert(xs.end(), kAbove.begin(), kAbove.end());
        addSampled(out, "cubic_derivative_outside_range", flatCubic, xs, sampleDerivative,
                   "derivative", "natural_cubic_spline");
    }

    // ---- 10-12. second derivative ----------------------------------------
    addSampled(out, "cubic_second_derivative_in_range", flatCubic, kMidpoints,
               sampleSecondDerivative, "secondDerivative", "natural_cubic_spline");
    // For a NATURAL spline this is 0 by construction and therefore does not
    // discriminate the boundary branch; the not-a-knot block below does.
    addSampled(out, "cubic_second_derivative_at_endpoints", flatCubic, kEndpoints,
               sampleSecondDerivative, "secondDerivative", "natural_cubic_spline");
    {
        std::vector<Real> xs = kBelow;
        xs.insert(xs.end(), kAbove.begin(), kAbove.end());
        addSampled(out, "cubic_second_derivative_outside_range", flatCubic, xs,
                   sampleSecondDerivative, "secondDerivative", "natural_cubic_spline");
    }

    // ---- 13-16. primitive ------------------------------------------------
    addSampled(out, "cubic_primitive_in_range", flatCubic, kMidpoints, samplePrimitive,
               "primitive", "natural_cubic_spline");
    addSampled(out, "cubic_primitive_at_endpoints", flatCubic, kEndpoints, samplePrimitive,
               "primitive", "natural_cubic_spline");
    // Linear extension with slope y[0] below and y[N-1] above.
    addSampled(out, "cubic_primitive_below_range", flatCubic, kBelow, samplePrimitive,
               "primitive", "natural_cubic_spline");
    addSampled(out, "cubic_primitive_above_range", flatCubic, kAbove, samplePrimitive,
               "primitive", "natural_cubic_spline");

    // ---- 17. accessors + update ------------------------------------------
    {
        json j = accessors(flatCubic);
        const Real before = flatCubic(2.0);
        flatCubic.update(); // delegates to the decorated interpolation
        j["valueAt2BeforeUpdate"] = before;
        j["valueAt2AfterUpdate"] = flatCubic(2.0);
        out.addCase("cubic_accessors",
                    json{{"underlying", "natural_cubic_spline"},
                         {"x", vec(kNodes)},
                         {"y", vec(std::vector<Real>(std::begin(kY), std::end(kY)))}},
                    j);
    }

    // ---- 18. raw underlying, for failure localisation ---------------------
    {
        std::vector<Real> inRange = kNodes;
        inRange.insert(inRange.end(), kMidpoints.begin(), kMidpoints.end());
        out.addCase("cubic_underlying_spline_reference",
                    json{{"underlying", "natural_cubic_spline"},
                         {"note", "raw CubicInterpolation, no decorator"},
                         {"x", vec(inRange)}},
                    json{{"value", sampleAt(*naturalCubic, inRange, sampleValue)},
                         {"derivative", sampleAt(*naturalCubic, inRange, sampleDerivative)},
                         {"secondDerivative",
                          sampleAt(*naturalCubic, inRange, sampleSecondDerivative)},
                         {"primitive", sampleAt(*naturalCubic, inRange, samplePrimitive)}});
    }

    // ======================================================================
    // Underlying 2: NOT-A-KNOT cubic spline. Its second derivative at the
    // endpoints is nonzero, so it discriminates the strict `x > xMax` /
    // `x < xMin` boundary that the natural spline cannot.
    // ======================================================================
    auto notAKnotCubic = ext::make_shared<CubicInterpolation>(
        std::begin(kX), std::end(kX), std::begin(kY),
        CubicInterpolation::Spline, false,
        CubicInterpolation::NotAKnot, Null<Real>(),
        CubicInterpolation::NotAKnot, Null<Real>());
    notAKnotCubic->enableExtrapolation();

    FlatExtrapolator flatNotAKnot(notAKnotCubic);
    flatNotAKnot.enableExtrapolation();

    addSampled(out, "notaknot_value_outside_range", flatNotAKnot,
               {-5.0, -1.0, 4.5, 9.0}, sampleValue, "value", "notaknot_cubic_spline");
    addSampled(out, "notaknot_derivative_at_endpoints", flatNotAKnot, kEndpoints,
               sampleDerivative, "derivative", "notaknot_cubic_spline");
    // THE discriminating case: nonzero at exactly xMin/xMax, zero just outside.
    addSampled(out, "notaknot_second_derivative_at_endpoints", flatNotAKnot, kEndpoints,
               sampleSecondDerivative, "secondDerivative", "notaknot_cubic_spline");
    addSampled(out, "notaknot_second_derivative_outside_range", flatNotAKnot,
               {-1.0, -0.01, 4.01, 5.0}, sampleSecondDerivative,
               "secondDerivative", "notaknot_cubic_spline");
    addSampled(out, "notaknot_primitive_outside_range", flatNotAKnot,
               {-5.0, -1.0, 4.5, 9.0}, samplePrimitive, "primitive", "notaknot_cubic_spline");
    {
        std::vector<Real> inRange = kNodes;
        inRange.insert(inRange.end(), kMidpoints.begin(), kMidpoints.end());
        out.addCase("notaknot_underlying_spline_reference",
                    json{{"underlying", "notaknot_cubic_spline"},
                         {"note", "raw CubicInterpolation, no decorator"},
                         {"x", vec(inRange)}},
                    json{{"value", sampleAt(*notAKnotCubic, inRange, sampleValue)},
                         {"derivative", sampleAt(*notAKnotCubic, inRange, sampleDerivative)},
                         {"secondDerivative",
                          sampleAt(*notAKnotCubic, inRange, sampleSecondDerivative)},
                         {"primitive", sampleAt(*notAKnotCubic, inRange, samplePrimitive)}});
    }

    // ======================================================================
    // Underlying 3: linear interpolation. Independent of the spline solver,
    // so the decorator's own algebra is verifiable by hand:
    //   value below   = y[0] = 1.0,      value above = y[4] = 3.5
    //   primitive below = P(x0) + y[0]*(x - x0) = 0 + 1.0*x
    //   primitive above = P(x4) + y[4]*(x - x4)
    // ======================================================================
    auto linear = ext::make_shared<LinearInterpolation>(
        std::begin(kXL), std::end(kXL), std::begin(kYL));
    linear->enableExtrapolation();

    FlatExtrapolator flatLinear(linear);
    flatLinear.enableExtrapolation();

    {
        std::vector<Real> xs = kBelow;
        xs.insert(xs.end(), kNodes.begin(), kNodes.end());
        xs.insert(xs.end(), kMidpoints.begin(), kMidpoints.end());
        xs.insert(xs.end(), kAbove.begin(), kAbove.end());
        addSampled(out, "linear_value_below_in_above_range", flatLinear, xs, sampleValue,
                   "value", "linear");
        addSampled(out, "linear_derivative", flatLinear, xs, sampleDerivative,
                   "derivative", "linear");
        addSampled(out, "linear_second_derivative", flatLinear, xs, sampleSecondDerivative,
                   "secondDerivative", "linear");
        addSampled(out, "linear_primitive", flatLinear, xs, samplePrimitive,
                   "primitive", "linear");
    }
    out.addCase("linear_accessors",
                json{{"underlying", "linear"},
                     {"x", vec(std::vector<Real>(std::begin(kXL), std::end(kXL)))},
                     {"y", vec(std::vector<Real>(std::begin(kYL), std::end(kYL)))}},
                accessors(flatLinear));
    {
        std::vector<Real> inRange = kNodes;
        inRange.insert(inRange.end(), kMidpoints.begin(), kMidpoints.end());
        out.addCase("linear_underlying_reference",
                    json{{"underlying", "linear"},
                         {"note", "raw LinearInterpolation, no decorator"},
                         {"x", vec(inRange)}},
                    json{{"value", sampleAt(*linear, inRange, sampleValue)},
                         {"derivative", sampleAt(*linear, inRange, sampleDerivative)},
                         {"secondDerivative", sampleAt(*linear, inRange, sampleSecondDerivative)},
                         {"primitive", sampleAt(*linear, inRange, samplePrimitive)}});
    }

    out.write();
    return 0;
}
