// migration-harness/cpp/probes/math/randomnumbers/sobol_rsg_probe.cpp
// Phase 5e.5b-CFC-d-145: cross-validate the Java SobolRsg against C++ v1.42.1.
//
// Three cases:
//
//   1) "jaeckel_dim33_first15"
//      C++ SobolRsg(33, /*seed*/=0, DirectionIntegers::Jaeckel, /*useGrayCode*/=true).
//      First 15 normalized samples in 33 dimensions. Mirrors the
//      homogeneity sub-test of test-suite/lowdiscrepancysequences.cpp
//      (lines 245-264). Used by LowDiscrepancySequencesTest to verify
//      the Jaeckel-init divergence fix at low draw indices.
//
//   2) "skipTo_jaeckel_dim8_skip16"
//      C++ SobolRsg(8, 0, Jaeckel, true).skipTo(16). Verifies the
//      Gray-coded skipTo accumulator. Also used by testSobolSkipping.
//
//   3) "discrepancy_jaeckel_dim_grid"
//      DiscrepancyStatistics at 1023 samples in dim {2,3,5,10,15,30,50,100}
//      for Jaeckel direction integers. Pivot values for
//      testJackelSobolDiscrepancy.
//
//   4) "discrepancy_sobollevitan_dim_grid"
//      Same grid, DirectionIntegers::SobolLevitan.
//
//   5) "discrepancy_sobollevitanlemieux_dim_grid"
//      Same grid, DirectionIntegers::SobolLevitanLemieux.
//
//   6) "discrepancy_unit_dim_grid"
//      Same grid, DirectionIntegers::Unit.

#include <ql/version.hpp>
#include <ql/math/randomnumbers/sobolrsg.hpp>
#include <ql/math/statistics/discrepancystatistics.hpp>
#include "../../common.hpp"

#include <vector>
#include <cmath>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

json runDiscrepancyGrid(SobolRsg::DirectionIntegers di) {
    const std::vector<int> dims = {2, 3, 5, 10, 15, 30, 50, 100};
    const unsigned long seed = 123456UL;
    const int j = 10; // 2^10 - 1 = 1023 samples
    const int points = (int)std::pow(2.0, j) - 1;

    json out = json::array();
    for (int dim : dims) {
        SobolRsg rsg(dim, seed, di, /*useGrayCode*/ true);
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
    ReferenceWriter out("math/randomnumbers/sobol_rsg", QL_VERSION,
                        "sobol_rsg_probe");

    // ----- (1) Jaeckel dim=33, first 15 samples -----
    {
        const Size dim = 33;
        const int N = 15;
        SobolRsg rsg(dim, 0UL, SobolRsg::Jaeckel, /*useGrayCode*/ true);
        json sequence = json::array();
        for (int i = 0; i < N; ++i) {
            const auto& s = rsg.nextSequence();
            json row = json::array();
            for (Size d = 0; d < dim; ++d) row.push_back(s.value[d]);
            sequence.push_back(row);
        }
        out.addCase("jaeckel_dim33_first15",
                    json{{"dimensionality", dim}, {"count", N},
                         {"directionIntegers", "Jaeckel"},
                         {"useGrayCode", true}},
                    json{{"sequence", sequence}});
    }

    // ----- (2) skipTo Jaeckel dim=8 skip=16 -----
    {
        const Size dim = 8;
        SobolRsg rsg(dim, 0UL, SobolRsg::Jaeckel, /*useGrayCode*/ true);
        const auto& v = rsg.skipTo(16);
        json arr = json::array();
        for (Size d = 0; d < dim; ++d)
            arr.push_back(static_cast<std::uint64_t>(v[d]));
        // also next sequence after skipTo
        const auto& s17 = rsg.nextSequence();
        json next17 = json::array();
        for (Size d = 0; d < dim; ++d) next17.push_back(s17.value[d]);
        out.addCase("skipTo_jaeckel_dim8_skip16",
                    json{{"dimensionality", dim}, {"skip", 16},
                         {"directionIntegers", "Jaeckel"},
                         {"useGrayCode", true}},
                    json{{"int_at_skip16", arr},
                         {"sample_at_17", next17}});
    }

    // ----- (3)-(6) Discrepancy grids -----
    out.addCase("discrepancy_jaeckel_dim_grid",
                json{{"directionIntegers", "Jaeckel"}, {"seed", 123456}},
                json{{"grid", runDiscrepancyGrid(SobolRsg::Jaeckel)}});
    out.addCase("discrepancy_sobollevitan_dim_grid",
                json{{"directionIntegers", "SobolLevitan"}, {"seed", 123456}},
                json{{"grid", runDiscrepancyGrid(SobolRsg::SobolLevitan)}});
    out.addCase("discrepancy_sobollevitanlemieux_dim_grid",
                json{{"directionIntegers", "SobolLevitanLemieux"}, {"seed", 123456}},
                json{{"grid", runDiscrepancyGrid(SobolRsg::SobolLevitanLemieux)}});
    out.addCase("discrepancy_unit_dim_grid",
                json{{"directionIntegers", "Unit"}, {"seed", 123456}},
                json{{"grid", runDiscrepancyGrid(SobolRsg::Unit)}});

    out.write();
    return 0;
}
