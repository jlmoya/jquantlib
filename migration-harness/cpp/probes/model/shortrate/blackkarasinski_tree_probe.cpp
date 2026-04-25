// migration-harness/cpp/probes/model/shortrate/blackkarasinski_tree_probe.cpp
// Reference values for BlackKarasinski::tree(grid). Captures the full
// (i, j) discount/underlying fingerprint of a 4-step grid for a flat
// 4% term structure with a=0.1, sigma=0.01. Phase 2c WI-5: unstubs
// BK.tree(grid) on the Java side (replaces `numericTree = null;` with
// the calibrating ShortRateTree built via the 3-arg ctor + the per-
// step Brent loop in BK::tree itself).

#include <ql/version.hpp>
#include <ql/models/shortrate/onefactormodels/blackkarasinski.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/timegrid.hpp>
#include "../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("model/shortrate/blackkarasinski_tree", QL_VERSION,
                        "blackkarasinski_tree_probe");

    Settings::instance().evaluationDate() = Date(22, April, 2026);
    Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(Date(22, April, 2026), 0.04, Actual365Fixed()));

    BlackKarasinski model(ts, 0.1, 0.01);
    TimeGrid grid(/*end*/1.0, /*steps*/4);
    auto lattice = model.tree(grid);
    auto tree = ext::dynamic_pointer_cast<OneFactorModel::ShortRateTree>(lattice);

    // BK calibrates phi(t) for t = grid[0..size-2]; the terminal grid
    // point has no fitted phi, so discount(size-1, j) would assert
    // "fitting parameter not set!". Restrict the fingerprint to the
    // calibrated cells.
    json sampleArr = json::array();
    for (Size i = 0; i + 1 < grid.size(); ++i) {
        for (Size j = 0; j < tree->size(i); ++j) {
            sampleArr.push_back({{"i", i}, {"j", j},
                                 {"discount",   tree->discount(i, j)},
                                 {"underlying", tree->underlying(i, j)}});
        }
    }

    out.addCase("bk_tree_grid_4steps",
        json{{"r_curve", 0.04}, {"a", 0.1}, {"sigma", 0.01},
             {"grid_end", 1.0}, {"grid_steps", 4}},
        json{{"samples", sampleArr}});

    out.write();
    return 0;
}
