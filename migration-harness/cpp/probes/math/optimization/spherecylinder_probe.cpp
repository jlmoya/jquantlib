// migration-harness/cpp/probes/math/optimization/spherecylinder_probe.cpp
// Reference values for SphereCylinderOptimizer — finds the closest point
// on the intersection of a sphere and a vertical cylinder to a given
// reference point in R^3.

#include <ql/version.hpp>
#include <ql/math/optimization/spherecylinder.hpp>
#include "../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("math/optimization/spherecylinder", QL_VERSION, "spherecylinder_probe");

    // Case 1: classic non-empty intersection.
    //   sphere centred at O, r=1.0
    //   vertical cylinder centred at (alpha=0.8, 0), s=0.3
    //   reference point z=(0.2, 0.0, 0.5), zweight=1.0
    {
        Real r = 1.0, s = 0.3, alpha = 0.8;
        Real z1 = 0.2, z2 = 0.0, z3 = 0.5;
        SphereCylinderOptimizer opt(r, s, alpha, z1, z2, z3);
        out.addCase("isIntersectionNonEmpty_easy",
                    json{{"r", r}, {"s", s}, {"alpha", alpha}},
                    json(opt.isIntersectionNonEmpty()));

        Real y1, y2, y3;
        bool found = opt.findByProjection(y1, y2, y3);
        out.addCase("findByProjection_easy",
                    json{{"r", r}, {"s", s}, {"alpha", alpha},
                         {"z", {z1, z2, z3}}},
                    json{{"found", found}, {"y", {y1, y2, y3}}});

        opt.findClosest(100, 1e-12, y1, y2, y3);
        out.addCase("findClosest_easy",
                    json{{"r", r}, {"s", s}, {"alpha", alpha},
                         {"z", {z1, z2, z3}},
                         {"maxIter", 100}, {"tol", 1e-12}},
                    json{{"y", {y1, y2, y3}}});
    }

    // Case 2: empty intersection (cylinder too far from origin relative to sphere radius).
    //   r=1.0, s=0.1, alpha=2.0 (|alpha - s| = 1.9 > r)
    {
        Real r = 1.0, s = 0.1, alpha = 2.0;
        SphereCylinderOptimizer opt(r, s, alpha, 0.0, 0.0, 0.0);
        out.addCase("isIntersectionNonEmpty_emptyFarCyl",
                    json{{"r", r}, {"s", s}, {"alpha", alpha}},
                    json(opt.isIntersectionNonEmpty()));
    }

    // Case 3: sphereCylinderOptimizerClosest helper (by projection, maxIter=0)
    {
        std::vector<Real> y = sphereCylinderOptimizerClosest(
                1.0, 0.3, 0.8, 0.2, 0.0, 0.5, 0, 1e-10);
        out.addCase("helperClosest_byProjection",
                    json{{"r", 1.0}, {"s", 0.3}, {"alpha", 0.8},
                         {"z", {0.2, 0.0, 0.5}}, {"maxIter", 0}},
                    json::array({y[0], y[1], y[2]}));
    }

    // Case 4: sphereCylinderOptimizerClosest helper with 100 Brent iterations
    {
        std::vector<Real> y = sphereCylinderOptimizerClosest(
                1.0, 0.3, 0.8, 0.2, 0.0, 0.5, 100, 1e-12);
        out.addCase("helperClosest_fullIter",
                    json{{"r", 1.0}, {"s", 0.3}, {"alpha", 0.8},
                         {"z", {0.2, 0.0, 0.5}}, {"maxIter", 100}, {"tol", 1e-12}},
                    json::array({y[0], y[1], y[2]}));
    }

    out.write();
    return 0;
}
