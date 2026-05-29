// migration-harness/cpp/probes/methods/finitedifferences/utilities/fdm_gap_boundary_probe.cpp
//
// Reference values for the gap-fdm port of:
//   - FdmIndicesOnBoundary  (boundary flat-index sets)
//   - UniformGridMesher     (uniform multi-dim grid: locations / dx)
//   - FdmDirichletBoundary  (constant-value Dirichlet BC: applyAfterApplying,
//                            applyAfterSolving, and the scalar (x,value) overload)
//
// Deterministic — TIGHT/EXACT tier.
//
// Fixture: a 2-D layout of dim {4, 3} (4 points in dir 0, 3 in dir 1),
// uniform grid over [0,3] x [10,12], i.e. dx = {1.0, 1.0}.

#include <ql/version.hpp>
#include <ql/methods/finitedifferences/operators/fdmlinearoplayout.hpp>
#include <ql/methods/finitedifferences/meshers/uniformgridmesher.hpp>
#include <ql/methods/finitedifferences/utilities/fdmindicesonboundary.hpp>
#include <ql/methods/finitedifferences/utilities/fdmdirichletboundary.hpp>
#include <ql/math/array.hpp>
#include "../../../common.hpp"

#include <vector>
#include <utility>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

json sizeVecToJson(const std::vector<Size>& v) {
    json a = json::array();
    for (Size x : v) a.push_back(static_cast<long long>(x));
    return a;
}

json arrayToJson(const Array& v) {
    json a = json::array();
    for (Size i = 0; i < v.size(); ++i) a.push_back(v[i]);
    return a;
}

} // namespace

int main() {
    ReferenceWriter out("methods/finitedifferences/utilities/fdm_gap_boundary",
                        QL_VERSION,
                        "fdm_gap_boundary_probe");

    std::vector<Size> dim = {4, 3};
    auto layout = ext::make_shared<FdmLinearOpLayout>(dim);

    std::vector<std::pair<Real, Real> > boundaries;
    boundaries.emplace_back(0.0, 3.0);    // dir 0: [0,3], dx=1
    boundaries.emplace_back(10.0, 12.0);  // dir 1: [10,12], dx=1
    auto mesher = ext::make_shared<UniformGridMesher>(layout, boundaries);

    // ---- FdmIndicesOnBoundary: all 4 (direction, side) combinations ----
    {
        struct Spec { Size dir; FdmDirichletBoundary::Side side; const char* name; };
        const Spec specs[] = {
            {0, FdmDirichletBoundary::Lower, "dir0_lower"},
            {0, FdmDirichletBoundary::Upper, "dir0_upper"},
            {1, FdmDirichletBoundary::Lower, "dir1_lower"},
            {1, FdmDirichletBoundary::Upper, "dir1_upper"},
        };
        for (const auto& s : specs) {
            FdmIndicesOnBoundary ib(layout, s.dir, s.side);
            json idx = json::array();
            for (Size v : ib.getIndices()) idx.push_back(static_cast<long long>(v));
            out.addCase(std::string("indices_") + s.name,
                        json{ {"dim", sizeVecToJson(dim)},
                              {"direction", static_cast<long long>(s.dir)},
                              {"side", (s.side == FdmDirichletBoundary::Lower) ? "Lower" : "Upper"} },
                        json{ {"indices", idx} });
        }
    }

    // ---- UniformGridMesher: locations per direction + dx + per-cell samples ----
    {
        json locs0 = arrayToJson(mesher->locations(0));
        json locs1 = arrayToJson(mesher->locations(1));

        // dx via dplus/dminus at the first cell (uniform => constant)
        const auto& l = *layout;
        auto it = l.begin();
        const Real dx0 = mesher->dplus(it, 0);
        const Real dx1 = mesher->dplus(it, 1);

        // per-cell location samples for all cells (index-ordered)
        json cellLoc0 = json::array();
        json cellLoc1 = json::array();
        for (const auto& iter : l) {
            cellLoc0.push_back(mesher->location(iter, 0));
            cellLoc1.push_back(mesher->location(iter, 1));
        }

        out.addCase("uniform_grid_4x3",
                    json{ {"dim", sizeVecToJson(dim)},
                          {"bounds0", json::array({0.0, 3.0})},
                          {"bounds1", json::array({10.0, 12.0})} },
                    json{ {"locations0", locs0},
                          {"locations1", locs1},
                          {"dx0", dx0}, {"dx1", dx1},
                          {"cellLoc0", cellLoc0},
                          {"cellLoc1", cellLoc1} });
    }

    // ---- FdmDirichletBoundary: apply to a ramp vector ----
    {
        const Size n = layout->size(); // 12
        // dir 0, Upper boundary, value 99.0
        FdmDirichletBoundary bcUpper0(mesher, 99.0, 0, FdmDirichletBoundary::Upper);
        Array a(n);
        for (Size i = 0; i < n; ++i) a[i] = static_cast<Real>(i);
        bcUpper0.applyAfterApplying(a);
        json afterApply = arrayToJson(a);

        // applyAfterSolving on a fresh ramp -> same effect
        Array b(n);
        for (Size i = 0; i < n; ++i) b[i] = static_cast<Real>(i) * 10.0;
        bcUpper0.applyAfterSolving(b);
        json afterSolve = arrayToJson(b);

        out.addCase("dirichlet_dir0_upper_v99",
                    json{ {"dim", sizeVecToJson(dim)},
                          {"direction", 0}, {"side", "Upper"}, {"value", 99.0} },
                    json{ {"afterApplying_ramp_i", afterApply},
                          {"afterSolving_ramp_10i", afterSolve} });
    }
    {
        const Size n = layout->size();
        // dir 1, Lower boundary, value -5.0
        FdmDirichletBoundary bcLower1(mesher, -5.0, 1, FdmDirichletBoundary::Lower);
        Array a(n);
        for (Size i = 0; i < n; ++i) a[i] = static_cast<Real>(i);
        bcLower1.applyAfterApplying(a);

        // scalar overload: clamp values strictly beyond the boundary extreme.
        // dir 1 Lower => xExtreme = locations(1)[0] = 10.0. Lower clamps x < 10.0.
        json scalar = json::array();
        std::vector<std::pair<Real,Real>> xv = {
            {9.0, 1.0}, {10.0, 2.0}, {11.0, 3.0}
        };
        for (const auto& p : xv) {
            scalar.push_back(bcLower1.applyAfterApplying(p.first, p.second));
        }

        out.addCase("dirichlet_dir1_lower_vneg5",
                    json{ {"dim", sizeVecToJson(dim)},
                          {"direction", 1}, {"side", "Lower"}, {"value", -5.0},
                          {"scalar_x", json::array({9.0, 10.0, 11.0})},
                          {"scalar_value", json::array({1.0, 2.0, 3.0})} },
                    json{ {"afterApplying_ramp_i", arrayToJson(a)},
                          {"scalar_applyAfterApplying", scalar} });
    }

    out.write();
    return 0;
}
