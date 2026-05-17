// migration-harness/cpp/probes/math/randomnumbers/sobol_brownian_bridge_rsg_probe.cpp
// Phase 5e.5b-CFC-d-163: cross-validate Java SobolBrownianBridgeRsg against
// C++ v1.42.1.
//
// Emits the first few sequences from SobolBrownianBridgeRsg for several
// (factors, steps, ordering, seed, directionIntegers) configurations so the
// Java port can assert exact step-major arrays.
//
// Note: Java DirectionIntegers does not yet include JoeKuoD7; we therefore
// emit references with DirectionIntegers::Jaeckel (the only one the Java
// SobolRsg currently supports beyond Unit/SobolLevitan/SobolLevitanLemieux).

#include <ql/version.hpp>
#include <ql/math/randomnumbers/sobolbrownianbridgersg.hpp>
#include "../../common.hpp"

#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

json emitSequences(SobolBrownianBridgeRsg& rsg, int nSeq) {
    json arr = json::array();
    for (int k = 0; k < nSeq; ++k) {
        const auto& s = rsg.nextSequence();
        json row = json::array();
        for (Real v : s.value) row.push_back(v);
        arr.push_back(row);
    }
    return arr;
}

} // namespace

int main() {
    ReferenceWriter out("math/randomnumbers/sobol_brownian_bridge_rsg",
                        QL_VERSION,
                        "sobol_brownian_bridge_rsg_probe");

    // (1) factors=2 steps=4 Diagonal seed=0 Jaeckel
    {
        SobolBrownianBridgeRsg rsg(2, 4, SobolBrownianGenerator::Diagonal, 0UL,
                                   SobolRsg::Jaeckel);
        out.addCase("f2s4_diagonal_seed0_jaeckel",
                    json{{"factors", 2}, {"steps", 4},
                         {"ordering", "Diagonal"}, {"seed", 0},
                         {"directionIntegers", "Jaeckel"},
                         {"nSequences", 5}},
                    json{{"dimension", rsg.dimension()},
                         {"sequences", emitSequences(rsg, 5)}});
    }

    // (2) factors=2 steps=4 Factors seed=42 Jaeckel
    {
        SobolBrownianBridgeRsg rsg(2, 4, SobolBrownianGenerator::Factors, 42UL,
                                   SobolRsg::Jaeckel);
        out.addCase("f2s4_factors_seed42_jaeckel",
                    json{{"factors", 2}, {"steps", 4},
                         {"ordering", "Factors"}, {"seed", 42},
                         {"directionIntegers", "Jaeckel"},
                         {"nSequences", 3}},
                    json{{"dimension", rsg.dimension()},
                         {"sequences", emitSequences(rsg, 3)}});
    }

    // (3) factors=3 steps=8 Steps seed=12345 Jaeckel
    {
        SobolBrownianBridgeRsg rsg(3, 8, SobolBrownianGenerator::Steps, 12345UL,
                                   SobolRsg::Jaeckel);
        out.addCase("f3s8_steps_seed12345_jaeckel",
                    json{{"factors", 3}, {"steps", 8},
                         {"ordering", "Steps"}, {"seed", 12345},
                         {"directionIntegers", "Jaeckel"},
                         {"nSequences", 3}},
                    json{{"dimension", rsg.dimension()},
                         {"sequences", emitSequences(rsg, 3)}});
    }

    out.write();
    return 0;
}
