// migration-harness/cpp/probes/math/distributions/noncentral_chi_squared_pdf_probe.cpp
// Reference values for the non-central chi-squared PROBABILITY DENSITY
// FUNCTION (PDF) — produced by Boost's
//   boost::math::pdf(non_central_chi_squared_distribution<Real>(df, ncp), x)
// which is what QuantLib v1.42.1 (e.g. SquareRootProcessRNDCalculator::pdf)
// calls under the hood.
//
// Phase 5h.5-SLV-d: ports an exact PDF for the JQuantLib
// NonCentralCumulativeChiSquaredDistribution class so the SLV-related
// derivative tests no longer rely on CDF central differences (~1e-4 slack).
// The fixture grid intentionally overlaps with noncentral_chi_squared_probe
// (CDF) so the same (df, ncp, x) triples test all three callouts.
//
// Boost's PDF dispatches between two regimes:
//   * lambda <= 50 → Bessel form
//       f(x; df, lambda) = (1/2) * exp(-(x+lambda)/2) * (x/lambda)^((df-2)/4)
//                         * I_{(df-2)/2}(sqrt(lambda*x))
//     with a fall-through to the Poisson series form when the prefactor
//     exp argument would over-/under-flow.
//   * lambda > 50  → Poisson series form (sum of central chi-squared PDFs
//                    weighted by Poisson probabilities centred at floor(l/2)).
// Both branches are exercised by the grid below.

#include <ql/version.hpp>
#include <ql/types.hpp>
#include <boost/math/distributions/non_central_chi_squared.hpp>
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
    ReferenceWriter out("math/distributions/noncentral_chi_squared_pdf",
                        QL_VERSION,
                        "noncentral_chi_squared_pdf_probe");

    std::vector<Point> points = {
        // --- Bessel-form regime (lambda = ncp <= 50) --------------------
        // Original CDF-grid points reused so the same (df, ncp, x) triples
        // exercise all three callouts. Plus extra small-lambda points to
        // stress the Bessel-I_v argument range.
        { 1.0,    0.0,    0.5  },   // ncp=0 boundary → central chi-squared
        { 2.5,    1.5,    3.0  },   // small ncp
        { 5.0,   10.0,    8.0  },   // small/medium ncp
        { 3.0,    2.0,    5.0  },   // small ncp / mid-x
        { 7.0,   20.0,   25.0  },   // medium ncp
        { 4.0,   25.0,    2.0  },   // small-x deep left tail
        { 6.0,   45.0,   40.0  },   // boundary lambda just below 50

        // --- Series-form regime (lambda = ncp > 50) ---------------------
        { 8.0,  100.0,   60.0  },
        {10.0,   50.0,   65.0  },   // exactly at the boundary lambda=50
        { 4.0,  500.0,  250.0  },   // large ncp
        { 6.0,   80.0,   10.0  },
        { 0.5,  200.0,  150.0  },   // long Poisson series, small df
        { 1.0, 1000.0,  900.0  },   // very long series

        // --- SLV / Square-root process derivative-stress points --------
        // Mirrors the fixtures used by the C++ testSquareRootZeroFlowBC
        // and testSquareRootFokkerPlanckFwdEquation drivers, where
        // SquareRootProcessRNDCalculator::pdf is called with
        //   kappa=1, theta=0.4, sigma=0.8, v0=0.1, t=1
        //   d = 4*kappa/sigma^2 = 6.25
        //   df = d*theta = 2.5
        //   e = exp(-kappa*t) ≈ 0.36787944117144233
        //   k = d/(1-e) ≈ 9.887354417933288
        //   ncp = k*v0*e ≈ 0.36373544179332895
        // Sample at v = 0.0005..0.0045 and centred-difference offsets
        // ±h, ±2h with h=0.0001; x = v*k.
        { 2.5,  0.36373544179332895,  0.004943677208966644 },
        { 2.5,  0.36373544179332895,  0.014831031626899933 },
        { 2.5,  0.36373544179332895,  0.024718386044833222 },
        { 2.5,  0.36373544179332895,  0.034605740462766510 },
        { 2.5,  0.36373544179332895,  0.044493094880699800 },

        // --- log-gamma tail stresses -----------------------------------
        { 0.25,   0.1,     0.01 },
        { 0.1,    5.0,     2.5  },
    };

    json arr = json::array();
    for (const auto& p : points) {
        const boost::math::non_central_chi_squared_distribution<Real>
            dist(p.degrees, p.ncp);
        const Real pdf_x = boost::math::pdf(dist, p.x);
        arr.push_back({{"degrees", p.degrees},
                       {"ncp",     p.ncp},
                       {"x",       p.x},
                       {"pdf",     pdf_x}});
    }

    out.addCase("noncentral_chi_squared_pdf_grid",
                json{{"points", points.size()}},
                json{{"samples", arr}});

    out.write();
    return 0;
}
