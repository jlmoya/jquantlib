// migration-harness/cpp/probes/math/randomnumbers/halton_discrepancy_probe.cpp
// Phase 5e.5b-CFC-d-145: cross-validate Java HaltonRsg discrepancy with C++
// for the random-start, random-shift, and random-start+shift cases.
//
// Each case runs DiscrepancyStatistics over 1023 samples in
// dim {2,3,5,10,15,30,50,100}, mirroring testGeneratorDiscrepancy in
// test-suite/lowdiscrepancysequences.cpp.

#include <ql/version.hpp>
#include <ql/math/randomnumbers/haltonrsg.hpp>
#include <ql/math/statistics/discrepancystatistics.hpp>
#include "../../common.hpp"

#include <vector>
#include <cmath>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

json runDiscrepancyGrid(bool randomStart, bool randomShift) {
    const std::vector<int> dims = {2, 3, 5, 10, 15, 30, 50, 100};
    const unsigned long seed = 123456UL;
    const int j = 10; // 2^10 - 1 = 1023 samples
    const int points = (int)std::pow(2.0, j) - 1;

    json out = json::array();
    for (int dim : dims) {
        HaltonRsg rsg(dim, seed, randomStart, randomShift);
        DiscrepancyStatistics stat(dim);
        for (int i = 0; i < points; ++i) {
            const auto& s = rsg.nextSequence();
            stat.add(s.value);
        }
        out.push_back({
            {"dim", dim},
            {"points", points},
            {"discrepancy", stat.discrepancy()}
        });
    }
    return out;
}

} // namespace

int main() {
    ReferenceWriter out("math/randomnumbers/halton_discrepancy", QL_VERSION,
                        "halton_discrepancy_probe");

    out.addCase("randomstart",
                json{{"randomStart", true}, {"randomShift", false},
                     {"seed", 123456}},
                json{{"grid", runDiscrepancyGrid(true, false)}});
    out.addCase("randomshift",
                json{{"randomStart", false}, {"randomShift", true},
                     {"seed", 123456}},
                json{{"grid", runDiscrepancyGrid(false, true)}});
    out.addCase("randomstart_randomshift",
                json{{"randomStart", true}, {"randomShift", true},
                     {"seed", 123456}},
                json{{"grid", runDiscrepancyGrid(true, true)}});

    out.write();
    return 0;
}
