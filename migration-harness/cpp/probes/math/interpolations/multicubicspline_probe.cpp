// migration-harness/cpp/probes/math/interpolations/multicubicspline_probe.cpp
//
// Emits QuantLib::MultiCubicSpline<N>(grid, y)(x) for N = 2, 3 and 5, at
// off-node query points.
//
// WHY THIS EXISTS: C++ builds the N-dimensional spline out of three
// template-metaprogramming scaffolds in ql/math/interpolations/
// multicubicspline.hpp — detail::DataTable (:48, the recursively nested value
// table), detail::Point (:122, the compile-time cons-list encoding the
// argument/result/dimension tuples) and detail::Int2Type (:372, the
// integer->type dispatch that picks the recursion depth, and the reason C++
// caps out at 15 dimensions). JQuantLib replaces all three with a flat
// row-major double[] plus a stride table and runtime recursion. That
// substitution is only sound if the numbers agree, and until now the only Java
// coverage was InterpolationTest#testMultiSpline, which checks the spline
// against the ANALYTIC function it was built from — a self-consistency check
// that a differently-wired stride table could still pass at the 1.7e-4
// off-node tolerance the C++ test uses.
//
// Here the expected values are C++'s own spline output, so the comparison is
// port-vs-port at the tight tier rather than port-vs-smooth-function at 1.7e-4.
//
// The query points are deliberately OFF the knots: on a knot the spline
// reproduces the tabulated value by construction, which would pin the table
// lookup but not the tensor-product evaluation.

#include <ql/version.hpp>

#include <ql/math/interpolations/multicubicspline.hpp>

#include <cmath>
#include <string>
#include <vector>

#include "common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// A smooth, non-polynomial, non-separable test function: non-separable matters
// because a separable one would hide an axis-ordering bug in the strides.
Real f(Real a, Real b) {
    return std::sin(a) * std::exp(0.3 * b) + 0.25 * a * b;
}
Real f(Real a, Real b, Real c) {
    return std::sin(a + 0.5 * c) * std::exp(0.3 * b) + 0.25 * a * b * c;
}
Real f(Real a, Real b, Real c, Real d, Real e) {
    return std::sqrt(a * std::sinh(std::log(b)) + std::exp(std::sin(c) * std::sin(3 * d))
                     + std::sinh(std::log(d * e)));
}

std::vector<Real> axis(Real start, Real step, Size n) {
    std::vector<Real> v;
    v.reserve(n);
    for (Size i = 0; i < n; ++i)
        v.push_back(start + step * i);
    return v;
}

// Flatten the nested DataTable into the row-major (last axis fastest) order
// that the Java port's constructor expects, so both sides index the same table.
json flatten2(const MultiCubicSpline<2>::data_table& y, const std::vector<Size>& dim) {
    json out = json::array();
    for (Size i = 0; i < dim[0]; ++i)
        for (Size j = 0; j < dim[1]; ++j)
            out.push_back(y[i][j]);
    return out;
}
json flatten3(const MultiCubicSpline<3>::data_table& y, const std::vector<Size>& dim) {
    json out = json::array();
    for (Size i = 0; i < dim[0]; ++i)
        for (Size j = 0; j < dim[1]; ++j)
            for (Size k = 0; k < dim[2]; ++k)
                out.push_back(y[i][j][k]);
    return out;
}
json flatten5(const MultiCubicSpline<5>::data_table& y, const std::vector<Size>& dim) {
    json out = json::array();
    for (Size i = 0; i < dim[0]; ++i)
        for (Size j = 0; j < dim[1]; ++j)
            for (Size k = 0; k < dim[2]; ++k)
                for (Size l = 0; l < dim[3]; ++l)
                    for (Size m = 0; m < dim[4]; ++m)
                        out.push_back(y[i][j][k][l][m]);
    return out;
}

json gridJson(const SplineGrid& grid) {
    json out = json::array();
    for (const auto& ax : grid)
        out.push_back(ax);
    return out;
}

void add2d(ReferenceWriter& out) {
    const std::vector<Size> dim{6, 5};
    SplineGrid grid(2);
    grid[0] = axis(0.10, 0.35, dim[0]);
    grid[1] = axis(1.00, 0.40, dim[1]);

    MultiCubicSpline<2>::data_table y(dim);
    for (Size i = 0; i < dim[0]; ++i)
        for (Size j = 0; j < dim[1]; ++j)
            y[i][j] = f(grid[0][i], grid[1][j]);

    MultiCubicSpline<2> cs(grid, y);

    json rows = json::array();
    // 7 x 6 off-knot lattice strictly inside the interpolable region
    for (int a = 0; a < 7; ++a) {
        for (int b = 0; b < 6; ++b) {
            std::vector<Real> x{grid[0][1] + (grid[0][dim[0] - 2] - grid[0][1]) * a / 6.0 + 0.017,
                                grid[1][1] + (grid[1][dim[1] - 2] - grid[1][1]) * b / 5.0 + 0.013};
            rows.push_back(json{{"x", x}, {"value", cs(x)}});
        }
    }
    out.addCase("dim2", json{{"grid", gridJson(grid)}, {"values", flatten2(y, dim)}},
                json{{"rows", rows}});
}

void add3d(ReferenceWriter& out) {
    const std::vector<Size> dim{5, 6, 4};
    SplineGrid grid(3);
    grid[0] = axis(0.20, 0.50, dim[0]);
    grid[1] = axis(1.10, 0.30, dim[1]);
    grid[2] = axis(-0.40, 0.45, dim[2]);

    MultiCubicSpline<3>::data_table y(dim);
    for (Size i = 0; i < dim[0]; ++i)
        for (Size j = 0; j < dim[1]; ++j)
            for (Size k = 0; k < dim[2]; ++k)
                y[i][j][k] = f(grid[0][i], grid[1][j], grid[2][k]);

    MultiCubicSpline<3> cs(grid, y);

    json rows = json::array();
    for (int a = 0; a < 4; ++a)
        for (int b = 0; b < 4; ++b)
            for (int c = 0; c < 3; ++c) {
                std::vector<Real> x{
                    grid[0][1] + (grid[0][dim[0] - 2] - grid[0][1]) * a / 3.0 + 0.011,
                    grid[1][1] + (grid[1][dim[1] - 2] - grid[1][1]) * b / 3.0 + 0.007,
                    grid[2][1] + (grid[2][dim[2] - 2] - grid[2][1]) * c / 2.0 + 0.019};
                rows.push_back(json{{"x", x}, {"value", cs(x)}});
            }
    out.addCase("dim3", json{{"grid", gridJson(grid)}, {"values", flatten3(y, dim)}},
                json{{"rows", rows}});
}

// The 5-d case reuses the exact grid, offsets and function of the upstream
// test-suite case (test-suite/interpolations.cpp:879 testMultiSpline), so any
// disagreement is directly attributable rather than an artefact of a grid
// chosen here.
void add5d(ReferenceWriter& out) {
    const std::vector<Size> dim{6, 5, 5, 6, 4};
    const std::vector<Real> offsets{1.005, 14.0, 33.005, 35.025, 19.025};
    const Real r = 0.15;

    SplineGrid grid(5);
    for (Size i = 0; i < 5; ++i)
        grid[i] = axis(offsets[i], r, dim[i]);

    MultiCubicSpline<5>::data_table y(dim);
    for (Size i = 0; i < dim[0]; ++i)
        for (Size j = 0; j < dim[1]; ++j)
            for (Size k = 0; k < dim[2]; ++k)
                for (Size l = 0; l < dim[3]; ++l)
                    for (Size m = 0; m < dim[4]; ++m)
                        y[i][j][k][l][m] =
                            f(grid[0][i], grid[1][j], grid[2][k], grid[3][l], grid[4][m]);

    MultiCubicSpline<5> cs(grid, y);

    json rows = json::array();
    // on-knot (interior) points: the spline must reproduce the table exactly
    for (Size i = 1; i + 1 < dim[0]; ++i)
        for (Size j = 1; j + 1 < dim[1]; ++j) {
            std::vector<Real> x{grid[0][i], grid[1][j], grid[2][2], grid[3][2], grid[4][1]};
            rows.push_back(json{{"x", x}, {"value", cs(x)}});
        }
    // off-knot points: the actual tensor-product evaluation
    for (int a = 0; a < 3; ++a)
        for (int b = 0; b < 3; ++b)
            for (int c = 0; c < 3; ++c) {
                std::vector<Real> x{grid[0][1] + 0.5 * r * a, grid[1][1] + 0.5 * r * b,
                                    grid[2][1] + 0.5 * r * c, grid[3][2] + 0.07,
                                    grid[4][1] + 0.03};
                rows.push_back(json{{"x", x}, {"value", cs(x)}});
            }
    out.addCase("dim5", json{{"grid", gridJson(grid)}, {"values", flatten5(y, dim)}},
                json{{"rows", rows}});
}

} // namespace

int main() {
    ReferenceWriter out("math/interpolations/multicubicspline", QL_VERSION,
                        "multicubicspline_probe");
    add2d(out);
    add3d(out);
    add5d(out);
    out.write();
    return 0;
}
