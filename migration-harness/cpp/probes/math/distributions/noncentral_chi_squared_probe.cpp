// migration-harness/cpp/probes/math/distributions/noncentral_chi_squared_probe.cpp
// Reference values for the NonCentralCumulativeChiSquare* family — CDF
// and inverse CDF — at a grid of (degrees, ncp, x) tuples covering the
// AS-275-style series across small/medium/large non-centrality regimes.
//
// Phase 2f WI-3 C.1 extends the original Phase 2c grid with eight
// additional fixtures targeting the four candidate drift sources:
//   (a) Sankaran/Patnaik switching threshold — points near
//       (df+ncp) where x2 ≈ f2 to land on the asymptotic branch
//   (b) Series convergence boundary — small x with f_x_2n flipping sign
//       early vs. late
//   (c) FMA accumulation pattern — stress points with very long
//       Poisson series (large ncp + small df)
//   (d) Bessel/log-gamma tail — extreme f2 values
//
// Note: QuantLib v1.42.1 does NOT define a non-central chi-squared PDF
// distribution class. The chisquaredistribution.hpp public surface is
// CumulativeChiSquareDistribution (central CDF),
// NonCentralCumulativeChiSquareDistribution (non-central CDF series),
// NonCentralCumulativeChiSquareSankaranApprox (non-central CDF normal
// approximation), and InverseNonCentralCumulativeChiSquareDistribution
// (Brent root-finder on the CDF). The probe emits CDF and inv-CDF
// only; the WI-1 plan's PDF column is omitted accordingly.

#include <ql/version.hpp>
#include <ql/math/distributions/chisquaredistribution.hpp>
#include "../../common.hpp"

#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {
struct Point {
    Real degrees;
    Real ncp;
    Real x;
};
} // namespace

int main() {
    ReferenceWriter out("math/distributions/noncentral_chi_squared", QL_VERSION,
                        "noncentral_chi_squared_probe");

    std::vector<Point> points = {
        // --- Phase 2c original grid -----------------------------------
        { 1.0,    0.0,   0.5  },   // central chi-squared boundary (ncp=0)
        { 2.5,    1.5,   3.0  },   // small ncp
        { 5.0,   10.0,   8.0  },   // small/medium ncp
        { 8.0,  100.0,  60.0  },   // medium-large ncp
        { 4.0,  500.0, 250.0  },   // large ncp
        {10.0,   50.0,  65.0  },   // medium ncp, near (df+ncp) mean
        // --- Phase 2f WI-3 C.1 regression suite -----------------------
        // (a) Series convergence boundary (Patnaik series): f_x_2n
        //     = df - x flips sign after several iterations. Pick x
        //     close to df+ncp so the bracket-search phase (flag=false)
        //     runs through several u/v steps before flag flips true.
        { 3.0,     2.0,    5.0  },
        { 7.0,    20.0,   25.0  },
        // (b) Long Poisson series — ncp ≫ df pushes lambda = 0.5*ncp
        //     large so the geometric u *= lam/n term needs many
        //     iterations to decay below errmax.
        { 0.5,   200.0,  150.0  },
        { 1.0,  1000.0,  900.0  },
        // (c) log-gamma tail — small f2 stresses the GammaFunction
        //     logValue(f2 + 1) call (Stirling-vs-Lanczos sensitivity).
        { 0.25,    0.1,   0.01  },
        { 0.1,     5.0,   2.5   },
        // (d) Asymmetric x — small x with big ncp lands in the deep
        //     left tail where the series term t starts tiny and the
        //     Poisson reweighting v dominates the accumulation.
        { 4.0,    25.0,   2.0   },
        { 6.0,    80.0,  10.0   },
    };

    json arr = json::array();
    for (const auto& p : points) {
        NonCentralCumulativeChiSquareDistribution cdf(p.degrees, p.ncp);
        // The default maxEvaluations=10 in v1.42.1 is too small for the
        // larger-ncp samples (the 2x-bracket-expansion phase exhausts the
        // budget before Brent runs). Use a larger budget that matches
        // common downstream use (e.g. squarerootclvmodel.cpp passes 100).
        // accuracy=1e-13 gets Brent close enough to the true root that
        // both Java and C++ Brent implementations converge to bit-near
        // values, allowing the round-trip to pass at tight tier.
        InverseNonCentralCumulativeChiSquareDistribution icdf(
            p.degrees, p.ncp, /*maxEvaluations=*/100, /*accuracy=*/1e-13);
        const Real cdf_x  = cdf(p.x);
        arr.push_back({{"degrees", p.degrees},
                       {"ncp",     p.ncp},
                       {"x",       p.x},
                       {"cdf",     cdf_x},
                       {"inv_cdf_at_cdf_x", icdf(cdf_x)}});
    }

    out.addCase("noncentral_chi_squared_grid",
                json{{"points", points.size()}},
                json{{"samples", arr}});

    out.write();
    return 0;
}
