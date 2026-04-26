// migration-harness/cpp/probes/model/shortrate/twofactormodels/g2_probe.cpp
// Reference values for G2++ closed-form discount, discountBondOption, and
// the 2D tree fingerprint. Phase 2e WI-1: cross-validates the freshly
// ported G2 body (closed-form analytics + Dynamics + FittingParameter)
// against C++ v1.42.1.

#include <ql/version.hpp>
#include <ql/models/shortrate/twofactormodels/g2.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/timegrid.hpp>
#include "../../../common.hpp"

#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("model/shortrate/twofactormodels/g2", QL_VERSION,
                        "g2_probe");

    Settings::instance().evaluationDate() = Date(15, January, 2026);
    Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(Date(15, January, 2026), 0.05, Actual365Fixed()));

    const Real a = 0.1, sigma = 0.01, b = 0.1, eta = 0.005, rho = -0.5;
    G2 model(ts, a, sigma, b, eta, rho);

    // ----- Closed-form discount fingerprint (model.discount(t)) -----
    json discArr = json::array();
    for (Time t : {0.5, 1.0, 2.0, 5.0, 10.0}) {
        discArr.push_back({{"t", t}, {"discount", model.discount(t)}});
    }
    out.addCase("g2_discount_fingerprint",
        json{{"r_curve", 0.05}, {"a", a}, {"sigma", sigma},
             {"b", b}, {"eta", eta}, {"rho", rho}},
        json{{"samples", discArr}});

    // ----- discountBondOption(Call, k, 5.0, 10.0) -----
    json optArr = json::array();
    for (Real k : {0.95, 1.0, 1.05}) {
        optArr.push_back({{"strike", k}, {"maturity", 5.0}, {"bondMaturity", 10.0},
            {"call", model.discountBondOption(Option::Call, k, 5.0, 10.0)},
            {"put",  model.discountBondOption(Option::Put,  k, 5.0, 10.0)}});
    }
    out.addCase("g2_discountBondOption_fingerprint",
        json{{"r_curve", 0.05}, {"a", a}, {"sigma", sigma},
             {"b", b}, {"eta", eta}, {"rho", rho}},
        json{{"samples", optArr}});

    // ----- 2D tree fingerprint -----
    // TimeGrid(end=10.0, steps=5). Capture tree.discount(i, index) over
    // the full 2D state (index spans tree1.size(i) * tree2.size(i)).
    // Cast to TwoFactorModel::ShortRateTree to access the discount(i, j)
    // member (Lattice base only exposes timeGrid()).
    {
        TimeGrid grid(/*end*/10.0, /*steps*/5);
        auto lattice = model.tree(grid);
        auto tree = ext::dynamic_pointer_cast<TwoFactorModel::ShortRateTree>(lattice);

        // Walk i = 0 .. grid.size()-2: the terminal grid node has
        // no dt(i) defined (TimeGrid stores size()-1 dt values), so
        // discount(size-1, ...) is UB on the C++ side. The Java
        // ShortRateTree.discount mirrors the same dt(i) read and
        // throws out-of-bounds. Match the BK / HullWhite tree-probe
        // convention: skip the terminal cell.
        json treeArr = json::array();
        for (Size i = 0; i + 1 < grid.size(); ++i) {
            const Size sz = tree->size(i);
            for (Size index = 0; index < sz; ++index) {
                treeArr.push_back({{"i", i}, {"index", index},
                                   {"discount", tree->discount(i, index)}});
            }
        }
        out.addCase("g2_tree_fingerprint",
            json{{"r_curve", 0.05}, {"a", a}, {"sigma", sigma},
                 {"b", b}, {"eta", eta}, {"rho", rho},
                 {"grid_end", 10.0}, {"grid_steps", 5}},
            json{{"samples", treeArr}});
    }

    out.write();
    return 0;
}
