// migration-harness/cpp/probes/math/general_linear_least_squares_multi_probe.cpp
// Phase MC-extras — emit C++ v1.42.1 GeneralLinearLeastSquares reference values
// for the multi-variate (Array-state) instantiation, so the Java port's
// new multi-variate constructor can be cross-validated bit-exactly against
// SVD-based pseudo-inverse output.
//
// This drives the multi-asset Longstaff-Schwartz path of
// LongstaffSchwartzPathPricer<MultiPath> (American basket / max-of-N options).

#include <ql/version.hpp>
#include <ql/math/generallinearleastsquares.hpp>
#include <ql/math/array.hpp>
#include <ql/methods/montecarlo/lsmbasissystem.hpp>
#include "../common.hpp"

#include <functional>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// Linear-affine 2-D regression: y = c0 + c1*x0 + c2*x1 (3 basis functions).
// Use 8 deterministic samples; recover coefficients exactly (no noise).
void emitLinear2D(ReferenceWriter& out) {
    const Size n = 8;
    const Real true_c0 = 1.5, true_c1 = -2.0, true_c2 = 3.0;

    std::vector<Array> xs;
    std::vector<Real> ys;
    json inputs_x = json::array();
    for (Size i = 0; i < n; ++i) {
        Array a(2);
        a[0] = 0.1 * i - 0.3;
        a[1] = 0.2 * i + 0.5;
        xs.push_back(a);
        ys.push_back(true_c0 + true_c1 * a[0] + true_c2 * a[1]);
        json row = json::array();
        row.push_back(a[0]);
        row.push_back(a[1]);
        inputs_x.push_back(row);
    }

    std::vector<std::function<Real(Array)>> v;
    v.emplace_back([](Array a) -> Real { return 1.0; });
    v.emplace_back([](Array a) -> Real { return a[0]; });
    v.emplace_back([](Array a) -> Real { return a[1]; });

    GeneralLinearLeastSquares lse(xs, ys, v);
    const Array& coef = lse.coefficients();
    const Array& res = lse.residuals();

    json out_coef = json::array();
    for (Size i = 0; i < coef.size(); ++i) out_coef.push_back(coef[i]);
    json out_res = json::array();
    for (Size i = 0; i < res.size(); ++i) out_res.push_back(res[i]);

    json inputs = json::object();
    inputs["n"] = n;
    inputs["dim"] = 2;
    inputs["basis_size"] = v.size();
    inputs["x"] = inputs_x;
    json ys_json = json::array();
    for (Real y : ys) ys_json.push_back(y);
    inputs["y"] = ys_json;
    inputs["true_coefficients"] = json::array({true_c0, true_c1, true_c2});

    json expected = json::object();
    expected["coefficients"] = out_coef;
    expected["residuals"] = out_res;
    expected["dim"] = lse.dim();
    expected["size"] = lse.size();

    out.addCase("linear_2d_exact_recovery", inputs, expected);
}

// Quadratic surface: y = a + b*x0 + c*x1 + d*x0^2 + e*x0*x1 + f*x1^2 (6 basis).
// 12 samples — full rank when the points are non-collinear.
void emitQuadratic2D(ReferenceWriter& out) {
    const Size n = 12;
    const Real ca = 0.5, cb = -1.0, cc = 1.5, cd = 2.0, ce = -0.5, cf = 0.25;

    std::vector<Array> xs;
    std::vector<Real> ys;
    json inputs_x = json::array();
    for (Size i = 0; i < n; ++i) {
        Array a(2);
        a[0] = 0.1 * (i % 4) + 0.05 * i;  // produces non-collinear points
        a[1] = 0.05 * i - 0.2;
        xs.push_back(a);
        const Real y = ca + cb * a[0] + cc * a[1]
                     + cd * a[0] * a[0] + ce * a[0] * a[1] + cf * a[1] * a[1];
        ys.push_back(y);
        json row = json::array();
        row.push_back(a[0]);
        row.push_back(a[1]);
        inputs_x.push_back(row);
    }

    std::vector<std::function<Real(Array)>> v;
    v.emplace_back([](Array a) -> Real { return 1.0; });
    v.emplace_back([](Array a) -> Real { return a[0]; });
    v.emplace_back([](Array a) -> Real { return a[1]; });
    v.emplace_back([](Array a) -> Real { return a[0] * a[0]; });
    v.emplace_back([](Array a) -> Real { return a[0] * a[1]; });
    v.emplace_back([](Array a) -> Real { return a[1] * a[1]; });

    GeneralLinearLeastSquares lse(xs, ys, v);
    const Array& coef = lse.coefficients();
    const Array& res = lse.residuals();

    json out_coef = json::array();
    for (Size i = 0; i < coef.size(); ++i) out_coef.push_back(coef[i]);
    json out_res = json::array();
    for (Size i = 0; i < res.size(); ++i) out_res.push_back(res[i]);

    json inputs = json::object();
    inputs["n"] = n;
    inputs["dim"] = 2;
    inputs["basis_size"] = v.size();
    inputs["x"] = inputs_x;
    json ys_json = json::array();
    for (Real y : ys) ys_json.push_back(y);
    inputs["y"] = ys_json;
    inputs["true_coefficients"] = json::array({ca, cb, cc, cd, ce, cf});

    json expected = json::object();
    expected["coefficients"] = out_coef;
    expected["residuals"] = out_res;
    expected["dim"] = lse.dim();
    expected["size"] = lse.size();

    out.addCase("quadratic_2d_full_basis_exact", inputs, expected);
}

// Use the actual multiPathBasisSystem with Monomial polynomials at dim=2,
// order=2 — the same basis set that AmericanMaxPathPricer uses.
// 12 samples; exact recovery for a linear-in-state target.
void emitMultiPathBasisFit(ReferenceWriter& out) {
    const Size n = 12;
    auto v = LsmBasisSystem::multiPathBasisSystem(2, 2, LsmBasisSystem::Monomial);

    // Synthetic target: y = 0.7 - 0.3*x0 + 1.2*x1 (in the basis span).
    std::vector<Array> xs;
    std::vector<Real> ys;
    json inputs_x = json::array();
    for (Size i = 0; i < n; ++i) {
        Array a(2);
        a[0] = 0.1 * (i % 4) + 0.05 * i;
        a[1] = 0.07 * i - 0.3;
        xs.push_back(a);
        ys.push_back(0.7 - 0.3 * a[0] + 1.2 * a[1]);
        json row = json::array();
        row.push_back(a[0]);
        row.push_back(a[1]);
        inputs_x.push_back(row);
    }

    GeneralLinearLeastSquares lse(xs, ys, v);
    const Array& coef = lse.coefficients();
    const Array& res = lse.residuals();

    json out_coef = json::array();
    for (Size i = 0; i < coef.size(); ++i) out_coef.push_back(coef[i]);
    json out_res = json::array();
    for (Size i = 0; i < res.size(); ++i) out_res.push_back(res[i]);

    json inputs = json::object();
    inputs["n"] = n;
    inputs["dim"] = 2;
    inputs["order"] = 2;
    inputs["polynomial_type"] = "Monomial";
    inputs["basis_size"] = v.size();
    inputs["x"] = inputs_x;
    json ys_json = json::array();
    for (Real y : ys) ys_json.push_back(y);
    inputs["y"] = ys_json;

    json expected = json::object();
    expected["coefficients"] = out_coef;
    expected["residuals"] = out_res;
    expected["dim"] = lse.dim();
    expected["size"] = lse.size();

    out.addCase("multipath_basis_dim2_order2_monomial", inputs, expected);
}

} // namespace

int main() {
    ReferenceWriter out("math/general_linear_least_squares_multi", QL_VERSION,
                        "general_linear_least_squares_multi_probe");

    emitLinear2D(out);
    emitQuadratic2D(out);
    emitMultiPathBasisFit(out);

    out.write();
    return 0;
}
