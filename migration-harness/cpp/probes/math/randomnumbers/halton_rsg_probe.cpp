// migration-harness/cpp/probes/math/randomnumbers/halton_rsg_probe.cpp
// Probe for Phase 2d WI-3: HaltonRsg first-100 sequences cross-validation.
//
// Generates the first 100 4-dimensional Halton samples with
// (dim=4, seed=42, randomStart=false, randomShift=false). With both flags
// off, seed is unused — the output is fully deterministic and depends only
// on the van-der-Corput inversion in primes 2, 3, 5, 7. The Java port must
// reproduce these numbers bit-exactly.

#include <ql/version.hpp>
#include <ql/math/randomnumbers/haltonrsg.hpp>
#include "../../common.hpp"

#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("math/randomnumbers/halton_rsg", QL_VERSION,
                        "halton_rsg_probe");

    const Size dim = 4;
    const unsigned long seed = 42UL;
    const bool randomStart = false;
    const bool randomShift = false;
    const int N = 100;

    HaltonRsg gen(dim, seed, randomStart, randomShift);
    json sequence = json::array();
    for (int i = 0; i < N; ++i) {
        const auto& s = gen.nextSequence();
        json row = json::array();
        for (Size j = 0; j < dim; ++j) {
            row.push_back(s.value[j]);
        }
        sequence.push_back(row);
    }

    json inputs = {
        {"dimensionality", dim},
        {"seed", seed},
        {"randomStart", randomStart},
        {"randomShift", randomShift},
        {"count", N}
    };
    json expected = {
        {"sequence", sequence}
    };
    out.addCase("first_100_dim4", inputs, expected);

    out.write();
    return 0;
}
