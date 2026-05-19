// migration-harness/cpp/probes/methods/finitedifferences/meshers/fdm_heston_variance_mesher_low_sigma_probe.cpp
//
// Reference values for FdmHestonVarianceMesher at the very-low-sigma regime
// (sigma_v = 1e-6) used by HybridHestonHullWhiteProcessTest::testFdmHestonHullWhiteEngine.
//
// Goal: capture C++ v1.42.1's actual mesh locations at sigma=1e-6 so the
// Java port can be aligned with it (instead of falling through to its
// uniform-mesh fallback path).
//
// Phase 5e.5b-CFC-d-288.

#include <ql/version.hpp>
#include <ql/methods/finitedifferences/meshers/fdmhestonvariancemesher.hpp>
#include <ql/processes/hestonprocess.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/math/distributions/chisquaredistribution.hpp>
#include "../../../common.hpp"

#include <cmath>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

ext::shared_ptr<HestonProcess> makeHestonProcess(double sigma) {
    const Date today(28, March, 2004);
    Settings::instance().evaluationDate() = today;

    const DayCounter dc = Actual365Fixed();
    Handle<Quote> s0(ext::shared_ptr<Quote>(new SimpleQuote(100.0)));
    Handle<YieldTermStructure> rTS(
        ext::shared_ptr<YieldTermStructure>(new FlatForward(today, 0.05, dc)));
    Handle<YieldTermStructure> qTS(
        ext::shared_ptr<YieldTermStructure>(new FlatForward(today, 0.02, dc)));

    const double vol = 0.30;
    const double v0 = vol * vol;
    return ext::shared_ptr<HestonProcess>(
        new HestonProcess(rTS, qTS, s0, v0, 1.0, v0, sigma, 0.0));
}

json meshToJson(const FdmHestonVarianceMesher& m, std::size_t size) {
    json locs = json::array();
    json dplus = json::array();
    json dminus = json::array();
    for (std::size_t i = 0; i < size; ++i) {
        locs.push_back(m.location(i));
        dplus.push_back(std::isnan(m.dplus(i)) ? json(nullptr) : json(m.dplus(i)));
        dminus.push_back(std::isnan(m.dminus(i)) ? json(nullptr) : json(m.dminus(i)));
    }
    return json{
        {"locations", locs},
        {"dplus", dplus},
        {"dminus", dminus},
        {"volaEstimate", m.volaEstimate()}
    };
}

} // namespace

int main() {
    ReferenceWriter out("methods/finitedifferences/meshers/fdm_heston_variance_mesher_low_sigma",
                        QL_VERSION,
                        "fdm_heston_variance_mesher_low_sigma_probe");

    // Case 1: sigma = 1e-6 (testFdmHestonHullWhiteEngine reproducer).
    // size=10, maturity=8, tAvgSteps=10, epsilon=1e-4, mixingFactor=1.0.
    {
        const std::size_t size = 10;
        const double maturity = 8.0;
        auto hp = makeHestonProcess(1e-6);
        FdmHestonVarianceMesher mesh(size, hp, maturity, 10, 1e-4, 1.0);

        json inputs = {
            {"size", size},
            {"maturity", maturity},
            {"tAvgSteps", 10},
            {"epsilon", 1e-4},
            {"mixingFactor", 1.0},
            {"v0", hp->v0()},
            {"kappa", hp->kappa()},
            {"theta", hp->theta()},
            {"sigma", hp->sigma()}
        };
        out.addCase("sigma_1e-6_size10_T8", inputs, meshToJson(mesh, size));
    }

    // Case 2: sigma = 1e-6, smaller size (sanity check / control).
    {
        const std::size_t size = 5;
        const double maturity = 8.0;
        auto hp = makeHestonProcess(1e-6);
        FdmHestonVarianceMesher mesh(size, hp, maturity, 10, 1e-4, 1.0);

        json inputs = {
            {"size", size},
            {"maturity", maturity},
            {"tAvgSteps", 10},
            {"epsilon", 1e-4},
            {"mixingFactor", 1.0},
            {"v0", hp->v0()},
            {"sigma", hp->sigma()}
        };
        out.addCase("sigma_1e-6_size5_T8", inputs, meshToJson(mesh, size));
    }

    // Case 3: sigma = 0.3 (sane regime, control).
    {
        const std::size_t size = 10;
        const double maturity = 8.0;
        auto hp = makeHestonProcess(0.3);
        FdmHestonVarianceMesher mesh(size, hp, maturity, 10, 1e-4, 1.0);

        json inputs = {
            {"size", size},
            {"maturity", maturity},
            {"tAvgSteps", 10},
            {"epsilon", 1e-4},
            {"mixingFactor", 1.0},
            {"v0", hp->v0()},
            {"sigma", hp->sigma()}
        };
        out.addCase("sigma_0_3_size10_T8", inputs, meshToJson(mesh, size));
    }

    // Case 4: sigma = 1e-4 (boundary case).
    {
        const std::size_t size = 10;
        const double maturity = 8.0;
        auto hp = makeHestonProcess(1e-4);
        FdmHestonVarianceMesher mesh(size, hp, maturity, 10, 1e-4, 1.0);

        json inputs = {
            {"size", size},
            {"maturity", maturity},
            {"tAvgSteps", 10},
            {"epsilon", 1e-4},
            {"mixingFactor", 1.0},
            {"v0", hp->v0()},
            {"sigma", hp->sigma()}
        };
        out.addCase("sigma_1e-4_size10_T8", inputs, meshToJson(mesh, size));
    }

    // Case 5: Inspect raw chi-square CDF/inverse output for sigma=1e-6,
    // slice 1 (t=0.8) — to see what C++ actually computes for the
    // probabilities and quantiles in the degenerate regime.
    {
        // Replicate the inner-loop quantities. sigma=1e-6, kappa=1, theta=0.09, v0=0.09, t=0.8.
        const double sigma = 1e-6, kappa = 1.0, theta = 0.09, v0 = 0.09;
        const double t = 0.8;
        const double ekt = std::exp(-kappa * t);
        const double k = sigma * sigma * (1.0 - ekt) / (4.0 * kappa);
        const double df = 4.0 * theta * kappa / (sigma * sigma);
        const double ncp = 4.0 * kappa * ekt / (sigma * sigma * (1.0 - ekt)) * v0;

        NonCentralCumulativeChiSquareDistribution cdf(df, ncp);
        InverseNonCentralCumulativeChiSquareDistribution inv(df, ncp, 100, 1e-8);

        json cdfvals = json::object();
        std::vector<double> xs = { 0.5*(df+ncp), 0.99*(df+ncp), df+ncp, 1.01*(df+ncp), 1.5*(df+ncp), 2.0*(df+ncp) };
        for (double x : xs) {
            cdfvals[std::to_string(x)] = cdf(x);
        }

        json invvals = json::object();
        std::vector<double> ps = { 1e-4, 0.1, 0.25, 0.5, 0.75, 0.9, 0.9999 };
        for (double p : ps) {
            try {
                invvals[std::to_string(p)] = inv(p);
            } catch (const std::exception& e) {
                invvals[std::to_string(p)] = std::string("EXCEPTION: ") + e.what();
            }
        }

        json inputs = {
            {"df", df},
            {"ncp", ncp},
            {"k", k},
            {"mu_chi", df+ncp},
            {"sigma_chi", std::sqrt(2.0*(df+2.0*ncp))}
        };
        json expected = {
            {"cdf_samples", cdfvals},
            {"inv_samples", invvals}
        };
        out.addCase("cpp_internal_diag_slice1", inputs, expected);
    }

    out.write();
    return 0;
}
