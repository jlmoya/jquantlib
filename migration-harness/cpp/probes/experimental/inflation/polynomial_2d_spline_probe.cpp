// migration-harness/cpp/probes/experimental/inflation/polynomial_2d_spline_probe.cpp
// Reference values for Polynomial2DSpline (ql/experimental/inflation/polynomial2Dspline.hpp).
//
// Polynomial2DSpline: for each column i in the z-matrix, fits a Parabolic
// (local cubic with parabolic first-derivative approx) interpolation over y.
// Then, for a query (x,y), evaluates each column polynomial at y to get a
// cross-section, and fits a natural cubic spline over x on that cross-section.
//
// Test grid: x in {1,2,3,4}, y in {10,20,30}, z[row=y, col=x].

#include <ql/version.hpp>
#include <ql/experimental/inflation/polynomial2Dspline.hpp>
#include <ql/math/array.hpp>
#include <ql/math/matrix.hpp>
#include "../../common.hpp"

#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("experimental/inflation/polynomial_2d_spline",
                        QL_VERSION,
                        "polynomial_2d_spline_probe");

    // x axis (4 points)
    std::vector<Real> xs = {1.0, 2.0, 3.0, 4.0};
    // y axis (3 points)
    std::vector<Real> ys = {10.0, 20.0, 30.0};

    // z matrix: rows indexed by y, columns indexed by x
    // z[row][col] = z at (x[col], y[row])
    // Use a smooth quadratic: z(x,y) = x + 0.1*y + 0.01*x*y
    // Row 0 (y=10): [1+1+0.1, 2+1+0.2, 3+1+0.3, 4+1+0.4] = [2.1, 3.2, 4.3, 5.4]
    // Row 1 (y=20): [1+2+0.2, 2+2+0.4, 3+2+0.6, 4+2+0.8] = [3.2, 4.4, 5.6, 6.8]
    // Row 2 (y=30): [1+3+0.3, 2+3+0.6, 3+3+0.9, 4+3+1.2] = [4.3, 5.6, 6.9, 8.2]
    Matrix zMatrix(3, 4);
    zMatrix[0][0] = 2.1;  zMatrix[0][1] = 3.2;  zMatrix[0][2] = 4.3;  zMatrix[0][3] = 5.4;
    zMatrix[1][0] = 3.2;  zMatrix[1][1] = 4.4;  zMatrix[1][2] = 5.6;  zMatrix[1][3] = 6.8;
    zMatrix[2][0] = 4.3;  zMatrix[2][1] = 5.6;  zMatrix[2][2] = 6.9;  zMatrix[2][3] = 8.2;

    Polynomial2DSpline spline(xs.begin(), xs.end(),
                              ys.begin(), ys.end(),
                              zMatrix);

    json inputs = {
        {"xs", xs},
        {"ys", ys},
        {"z00", zMatrix[0][0]}, {"z01", zMatrix[0][1]}, {"z02", zMatrix[0][2]}, {"z03", zMatrix[0][3]},
        {"z10", zMatrix[1][0]}, {"z11", zMatrix[1][1]}, {"z12", zMatrix[1][2]}, {"z13", zMatrix[1][3]},
        {"z20", zMatrix[2][0]}, {"z21", zMatrix[2][1]}, {"z22", zMatrix[2][2]}, {"z23", zMatrix[2][3]}
    };

    // Grid-point evaluations (TIGHT — exact interpolation at knots)
    for (int row = 0; row < 3; ++row) {
        for (int col = 0; col < 4; ++col) {
            double xq = xs[col];
            double yq = ys[row];
            double val = spline(xq, yq, /*extrapolate=*/true);
            std::string name = "grid_x" + std::to_string(col) + "_y" + std::to_string(row);
            out.addCase(name, inputs, val);
        }
    }

    // Interior point evaluations (LOOSE — cubic/parabolic off-grid)
    std::vector<std::pair<Real,Real>> queries = {
        {1.5, 15.0},
        {2.5, 20.0},
        {3.0, 25.0},
        {2.0, 15.0},
        {3.5, 22.0}
    };
    for (auto& q : queries) {
        double xq = q.first;
        double yq = q.second;
        double val = spline(xq, yq, /*extrapolate=*/true);
        std::string name = "interior_x" + std::to_string((int)(xq*10)) + "_y" + std::to_string((int)(yq*10));
        out.addCase(name, inputs, val);
    }

    out.write();
    return 0;
}
