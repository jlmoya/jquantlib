// migration-harness/cpp/probes/methods/montecarlo/multipath_himalaya_probe.cpp
// Phase 5e.5b-CFC-d-20 — multi-asset MC pipeline divergence investigation.
//
// Mirrors the test-suite/himalayaoption.cpp testCached fixture exactly
// (4 BSM assets, 5 fixing dates, correlation matrix, seed=86421,
// fixedSamples=1023) but with a pinned evaluation date for cross-language
// reproducibility, and dumps every intermediate value the MT-driven
// multi-path pipeline produces, so a Java probe with identical setup can
// be diff'd value-by-value to pinpoint where divergence first appears.
//
// Dumps (per case):
//   - First 5 raw MT uint32 outputs (unchained, single-stream MT seeded with 86421)
//   - First 20 normal-distributed outputs (after InverseCumulativeNormal)
//   - sqrtCorrelation_ (pseudoSqrt(correlation, Spectral)) — full 4x4 matrix
//   - For path 0:
//       * The 16 normals consumed (4 assets x 4 time-step increments)
//       * The 16 dz values (sqrtCorrelation * dw) per time-step
//       * Final asset values at t=4 (after 4 evolutions) for each of 4 assets
//   - The MakeMCHimalayaEngine NPV (under fixedSamples=1023, seed=86421)
//     using the pinned evaluation date.

#include <ql/version.hpp>
#include <ql/experimental/exoticoptions/himalayaoption.hpp>
#include <ql/experimental/exoticoptions/mchimalayaengine.hpp>
#include <ql/processes/blackscholesprocess.hpp>
#include <ql/processes/stochasticprocessarray.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/termstructures/volatility/equityfx/blackconstantvol.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/math/randomnumbers/mt19937uniformrng.hpp>
#include <ql/math/randomnumbers/randomsequencegenerator.hpp>
#include <ql/math/randomnumbers/inversecumulativersg.hpp>
#include <ql/math/distributions/normaldistribution.hpp>
#include <ql/math/randomnumbers/rngtraits.hpp>
#include <ql/math/matrixutilities/pseudosqrt.hpp>
#include <ql/methods/montecarlo/multipathgenerator.hpp>
#include <ql/methods/montecarlo/multipath.hpp>
#include <ql/timegrid.hpp>
#include <ql/settings.hpp>
#include "../../common.hpp"

#include <vector>
#include <memory>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// Helper: build a flat-rate yield term structure handle.
ext::shared_ptr<YieldTermStructure>
flatYTS(const Date& today, Real rate, const DayCounter& dc) {
    return ext::shared_ptr<YieldTermStructure>(
        new FlatForward(today, rate, dc));
}

// Helper: build a flat-vol black term structure handle.
ext::shared_ptr<BlackVolTermStructure>
flatBVTS(const Date& today, Real vol, const DayCounter& dc) {
    return ext::shared_ptr<BlackVolTermStructure>(
        new BlackConstantVol(today, NullCalendar(), vol, dc));
}

void emitMtPipeline(ReferenceWriter& out) {
    // ---- Pin evaluation date for cross-language reproducibility ----
    Date today(15, January, 2024);
    Settings::instance().evaluationDate() = today;

    DayCounter dc = Actual360();

    // ---- Build fixing dates: today + i*90 for i in [0..5) ----
    std::vector<Date> fixingDates;
    fixingDates.reserve(5);
    for (Size i = 0; i < 5; ++i)
        fixingDates.push_back(today + i * 90);

    Real strike = 101.0;

    // ---- Build the 4 BSM processes ----
    Handle<YieldTermStructure> riskFreeRate(flatYTS(today, 0.05, dc));

    std::vector<ext::shared_ptr<StochasticProcess1D>> processes(4);
    processes[0] = ext::shared_ptr<StochasticProcess1D>(
        new BlackScholesMertonProcess(
            Handle<Quote>(ext::shared_ptr<Quote>(new SimpleQuote(100.0))),
            Handle<YieldTermStructure>(flatYTS(today, 0.01, dc)),
            riskFreeRate,
            Handle<BlackVolTermStructure>(flatBVTS(today, 0.30, dc))));
    processes[1] = ext::shared_ptr<StochasticProcess1D>(
        new BlackScholesMertonProcess(
            Handle<Quote>(ext::shared_ptr<Quote>(new SimpleQuote(110.0))),
            Handle<YieldTermStructure>(flatYTS(today, 0.05, dc)),
            riskFreeRate,
            Handle<BlackVolTermStructure>(flatBVTS(today, 0.35, dc))));
    processes[2] = ext::shared_ptr<StochasticProcess1D>(
        new BlackScholesMertonProcess(
            Handle<Quote>(ext::shared_ptr<Quote>(new SimpleQuote(90.0))),
            Handle<YieldTermStructure>(flatYTS(today, 0.04, dc)),
            riskFreeRate,
            Handle<BlackVolTermStructure>(flatBVTS(today, 0.25, dc))));
    processes[3] = ext::shared_ptr<StochasticProcess1D>(
        new BlackScholesMertonProcess(
            Handle<Quote>(ext::shared_ptr<Quote>(new SimpleQuote(105.0))),
            Handle<YieldTermStructure>(flatYTS(today, 0.03, dc)),
            riskFreeRate,
            Handle<BlackVolTermStructure>(flatBVTS(today, 0.20, dc))));

    // ---- Correlation matrix (matches C++ test-suite testCached) ----
    Matrix correlation(4, 4);
    correlation[0][0] = 1.00; correlation[0][1] = 0.50; correlation[0][2] = 0.30; correlation[0][3] = 0.10;
    correlation[1][0] = 0.50; correlation[1][1] = 1.00; correlation[1][2] = 0.20; correlation[1][3] = 0.40;
    correlation[2][0] = 0.30; correlation[2][1] = 0.20; correlation[2][2] = 1.00; correlation[2][3] = 0.60;
    correlation[3][0] = 0.10; correlation[3][1] = 0.40; correlation[3][2] = 0.60; correlation[3][3] = 1.00;

    BigNatural seed = 86421;
    Size fixedSamples = 1023;

    ext::shared_ptr<StochasticProcessArray> processArray(
        new StochasticProcessArray(processes, correlation));

    // ---- Per-fixingDate times (used for grid construction) ----
    std::vector<Time> fixingTimes;
    for (const Date& d : fixingDates) {
        fixingTimes.push_back(processArray->time(d));
    }
    TimeGrid grid(fixingTimes.begin(), fixingTimes.end());

    // dimensions = factors * (grid.size() - 1) = 4 * 4 = 16
    Size numAssets = processArray->size();
    Size numFactors = processArray->factors();
    Size dimensions = numFactors * (grid.size() - 1);

    // ---- (1) Raw MT uint32 outputs (single-stream) — first 20 ----
    json mt_uint32_array = json::array();
    {
        MersenneTwisterUniformRng mt(seed);
        for (Size i = 0; i < 20; ++i) {
            unsigned long u = mt.nextInt32();
            // store as decimal; Java will compare as long
            mt_uint32_array.push_back(static_cast<uint64_t>(u));
        }
    }

    // ---- (2) First 20 normal-distributed outputs (raw MT -> Real -> ICN)
    // This mirrors what RandomSequenceGenerator + InverseCumulativeRsg do
    // for the first dimension's worth of samples.
    json mt_uniform_array = json::array();
    json mt_normal_array = json::array();
    {
        MersenneTwisterUniformRng mt(seed);
        InverseCumulativeNormal icn;
        for (Size i = 0; i < 20; ++i) {
            Sample<Real> u = mt.next();
            mt_uniform_array.push_back(u.value);
            Real n = icn(u.value);
            mt_normal_array.push_back(n);
        }
    }

    // ---- (3) sqrtCorrelation (pseudoSqrt Spectral) — full 4x4 matrix ----
    Matrix sqrtCorr = pseudoSqrt(correlation, SalvagingAlgorithm::Spectral);
    json sqrtCorr_json = json::array();
    for (Size i = 0; i < sqrtCorr.rows(); ++i) {
        json row = json::array();
        for (Size j = 0; j < sqrtCorr.columns(); ++j) {
            row.push_back(sqrtCorr[i][j]);
        }
        sqrtCorr_json.push_back(row);
    }

    // ---- (4) Path 0: consumed normals + dz + final asset values ----
    // Re-run the MT pipeline through MultiPathGenerator (with PseudoRandom traits)
    // and capture the FIRST sample's intermediate values.
    typedef PseudoRandom::rsg_type rsg_type;
    rsg_type rsg = PseudoRandom::make_sequence_generator(dimensions, seed);
    MultiPathGenerator<rsg_type> pathGen(processArray, grid, rsg, false);

    // Also build a parallel "dump" pipeline: re-construct the MT + RSG +
    // InverseCumulativeRsg and step through it manually so we can record
    // the per-dimension uniform / normal values that path 0 consumes.
    // (The pathGen above will consume the same sequence; we extract its
    // first sample, then separately verify by re-seeding.)
    json path0_normals = json::array();
    {
        MersenneTwisterUniformRng mt(seed);
        InverseCumulativeNormal icn;
        // Generate dimensions = 16 values for path 0
        for (Size i = 0; i < dimensions; ++i) {
            Sample<Real> u = mt.next();
            Real n = icn(u.value);
            path0_normals.push_back(n);
        }
    }

    // Compute dz = sqrtCorrelation * dw for each time-step.
    json path0_dz = json::array();
    {
        for (Size step = 0; step < grid.size() - 1; ++step) {
            Array dw(numFactors);
            for (Size k = 0; k < numFactors; ++k) {
                dw[k] = path0_normals[step * numFactors + k].get<double>();
            }
            Array dz = sqrtCorr * dw;
            json step_dz = json::array();
            for (Size k = 0; k < numFactors; ++k) {
                step_dz.push_back(dz[k]);
            }
            path0_dz.push_back(step_dz);
        }
    }

    // Run the actual MultiPathGenerator path 0 and capture per-asset
    // final values + per-asset values at every time-step.
    json path0_values = json::array();        // [asset][time]
    json path0_finals = json::array();        // [asset]
    {
        const auto& sample = pathGen.next();
        const MultiPath& path = sample.value;
        for (Size a = 0; a < numAssets; ++a) {
            const Path& subPath = path[a];
            json row = json::array();
            for (Size t = 0; t < subPath.length(); ++t) {
                row.push_back(subPath[t]);
            }
            path0_values.push_back(row);
            path0_finals.push_back(subPath[subPath.length() - 1]);
        }
    }

    // ---- (5) Final pricing: NPV via MakeMCHimalayaEngine ----
    HimalayaOption option(fixingDates, strike);
    option.setPricingEngine(MakeMCHimalayaEngine<PseudoRandom>(processArray)
                                .withSamples(static_cast<Size>(fixedSamples))
                                .withSeed(seed));
    Real npv = option.NPV();

    // ---- Build inputs / expected JSON ----
    json inputs = json::object();
    inputs["seed"] = static_cast<uint64_t>(seed);
    inputs["fixed_samples"] = static_cast<uint64_t>(fixedSamples);
    inputs["strike"] = strike;
    inputs["evaluation_date"] = "2024-01-15";
    inputs["num_assets"] = static_cast<uint64_t>(numAssets);
    inputs["num_factors"] = static_cast<uint64_t>(numFactors);
    inputs["dimensions"] = static_cast<uint64_t>(dimensions);
    json fixingTimes_json = json::array();
    for (Time t : fixingTimes) fixingTimes_json.push_back(t);
    inputs["fixing_times"] = fixingTimes_json;

    json expected = json::object();
    expected["mt_uint32_first20"] = mt_uint32_array;
    expected["mt_uniform_first20"] = mt_uniform_array;
    expected["mt_normal_first20"] = mt_normal_array;
    expected["sqrt_correlation_4x4"] = sqrtCorr_json;
    expected["path0_normals_consumed"] = path0_normals;
    expected["path0_dz_per_step"] = path0_dz;
    expected["path0_asset_values"] = path0_values;
    expected["path0_final_assets"] = path0_finals;
    expected["npv_seed_86421_samples_1023"] = npv;

    out.addCase("himalaya_4assets_5fixings_seed_86421_samples_1023", inputs, expected);
}

} // namespace

int main() {
    ReferenceWriter out("methods/montecarlo/multipath_himalaya", QL_VERSION,
                        "multipath_himalaya_probe");

    emitMtPipeline(out);

    out.write();
    return 0;
}
