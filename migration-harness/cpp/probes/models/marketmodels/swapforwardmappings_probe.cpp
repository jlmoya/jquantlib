// migration-harness/cpp/probes/models/marketmodels/swapforwardmappings_probe.cpp
// Phase 5e.5b-CFC-d-202: probe for SwapForwardMappings tests.
//
// Reproduces the MarketModelData fixture from
// test-suite/swapforwardmappings.cpp v1.42.1 and emits:
//   (a) the input vectors (rateTimes, forwards, displacements,
//       discountFactors, volatilities) so the Java test can hard-code an
//       identical fixture without depending on QL Calendar/Schedule.
//   (b) the analytic Jacobians for testForwardSwapJacobians
//       (coinitialSwapForwardJacobian + cmSwapForwardJacobian for every
//       spanningForwards in [1, nbRates)).
//   (c) the analytic swaptionImpliedVolatility values for the loop
//       (startIndex = 1, 6, 11, ...) used in testSwaptionImpliedVolatility.
//
// The Java test does NOT need to re-run the MC simulation; the implied-vol
// formula is fully analytic and that is what the Java port must match
// bit-for-bit. The C++ Monte-Carlo loop merely empirically validates the
// freeze-coefficient approximation — that's research methodology, not an
// implementation invariant.

#include <ql/models/marketmodels/swapforwardmappings.hpp>
#include <ql/models/marketmodels/curvestates/lmmcurvestate.hpp>
#include <ql/models/marketmodels/correlations/expcorrelations.hpp>
#include <ql/models/marketmodels/correlations/timehomogeneousforwardcorrelation.hpp>
#include <ql/models/marketmodels/models/flatvol.hpp>
#include <ql/models/marketmodels/products/multistep/multistepswaption.hpp>
#include <ql/models/marketmodels/evolutiondescription.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/simpledaycounter.hpp>
#include <ql/time/schedule.hpp>
#include <ql/time/period.hpp>
#include <ql/settings.hpp>
#include <ql/version.hpp>

#include "common.hpp"

#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

struct MarketModelData {
    std::vector<Time> rateTimes;
    std::vector<Time> accruals;
    std::vector<Rate> forwards;
    std::vector<Spread> displacements;
    std::vector<DiscountFactor> discountFactors;
    std::vector<Volatility> volatilities;
    Size nbRates;

    MarketModelData() {
        Calendar calendar = NullCalendar();
        Date todaysDate = Settings::instance().evaluationDate();
        Date endDate = todaysDate + 9 * Years;
        Schedule dates(todaysDate, endDate, Period(Semiannual),
                       calendar, Following, Following,
                       DateGeneration::Backward, false);
        nbRates = dates.size() - 2;
        rateTimes.assign(nbRates + 1, 0.0);
        accruals.assign(nbRates, 0.0);
        DayCounter dayCounter = SimpleDayCounter();
        for (Size i = 1; i < nbRates + 2; ++i)
            rateTimes[i - 1] = dayCounter.yearFraction(todaysDate, dates[i]);
        displacements.assign(nbRates, 0.0);
        forwards.assign(nbRates, 0.0);
        discountFactors.assign(nbRates + 1, 0.0);
        discountFactors[0] = 1.0;
        for (Size i = 0; i < nbRates; ++i) {
            forwards[i] = 0.03 + 0.0010 * i;
            accruals[i] = rateTimes[i + 1] - rateTimes[i];
            discountFactors[i + 1] = discountFactors[i] / (1.0 + forwards[i] * accruals[i]);
        }
        Volatility mktVols[] = {
            0.15541283, 0.18719678, 0.20890740, 0.22318179, 0.23212717,
            0.23731450, 0.23988649, 0.24066384, 0.24023111, 0.23900189,
            0.23726699, 0.23522952, 0.23303022, 0.23076564, 0.22850101,
            0.22627951, 0.22412881, 0.22206569, 0.22009939
        };
        volatilities.assign(nbRates, 0.0);
        for (Size i = 0; i < nbRates; ++i) volatilities[i] = mktVols[i];
    }
};

json matrixToJson(const Matrix& m) {
    json rows = json::array();
    for (Size i = 0; i < m.rows(); ++i) {
        json row = json::array();
        for (Size j = 0; j < m.columns(); ++j) row.push_back(m[i][j]);
        rows.push_back(row);
    }
    return rows;
}

json vecToJson(const std::vector<Real>& v) {
    json a = json::array();
    for (Real x : v) a.push_back(x);
    return a;
}

} // namespace

int main() {
    ReferenceWriter out("models/marketmodels/swapforwardmappings",
                        QL_VERSION, "swapforwardmappings_probe");

    MarketModelData md;
    const Size nbRates = md.nbRates;

    // === fixture: the inputs the Java test will hard-code ===
    {
        out.addCase("fixture",
                    json::object(),
                    json{{"nbRates",         (int)nbRates},
                         {"rateTimes",       vecToJson(md.rateTimes)},
                         {"accruals",        vecToJson(md.accruals)},
                         {"forwards",        vecToJson(md.forwards)},
                         {"displacements",   vecToJson(md.displacements)},
                         {"discountFactors", vecToJson(md.discountFactors)},
                         {"volatilities",    vecToJson(md.volatilities)}});
    }

    // === testForwardSwapJacobians: coinitial + CMS Jacobians ===
    LMMCurveState lmm(md.rateTimes);
    lmm.setOnForwardRates(md.forwards);

    {
        Matrix j = SwapForwardMappings::coinitialSwapForwardJacobian(lmm);
        out.addCase("coinitialSwapForwardJacobian",
                    json::object(),
                    json{{"jacobian", matrixToJson(j)}});
    }

    for (Size sp = 1; sp < nbRates; ++sp) {
        Matrix j = SwapForwardMappings::cmSwapForwardJacobian(lmm, sp);
        out.addCase("cmSwapForwardJacobian_span" + std::to_string(sp),
                    json{{"spanningForwards", (int)sp}},
                    json{{"jacobian", matrixToJson(j)}});
    }

    // === testSwaptionImpliedVolatility: emit implied-vol per startIndex ===
    {
        const Real longTermCorr = 0.5;
        const Real beta = 0.2;

        for (Size startIndex = 1; startIndex + 2 < nbRates; startIndex += 5) {
            Size endIndex = nbRates - 2;

            ext::shared_ptr<StrikedTypePayoff> payoff(
                new PlainVanillaPayoff(Option::Call, 0.03));
            MultiStepSwaption product(md.rateTimes, startIndex, endIndex, payoff);

            const EvolutionDescription& evolution = product.evolution();
            const Size numberOfFactors = nbRates;
            Spread displacement = md.displacements.front();

            Matrix correlations =
                exponentialCorrelations(evolution.rateTimes(), longTermCorr, beta);
            ext::shared_ptr<PiecewiseConstantCorrelation> corr(
                new TimeHomogeneousForwardCorrelation(correlations, md.rateTimes));
            ext::shared_ptr<MarketModel> lmmMarketModel(new FlatVol(
                md.volatilities, corr, evolution, numberOfFactors,
                lmm.forwardRates(), md.displacements));

            Real impliedVol = SwapForwardMappings::swaptionImpliedVolatility(
                *lmmMarketModel, startIndex, endIndex);

            Real swapRate = lmm.cmSwapRate(startIndex, endIndex - startIndex);
            Real swapAnnuity = lmm.cmSwapAnnuity(startIndex, startIndex,
                                                 endIndex - startIndex)
                               * md.discountFactors[startIndex];

            out.addCase("swaptionImpliedVolatility_start" + std::to_string(startIndex),
                        json{{"startIndex", (int)startIndex},
                             {"endIndex",   (int)endIndex},
                             {"strike",     0.03},
                             {"longTermCorr", longTermCorr},
                             {"beta",         beta},
                             {"displacement", displacement}},
                        json{{"impliedVol",  impliedVol},
                             {"swapRate",    swapRate},
                             {"swapAnnuity", swapAnnuity}});
        }
    }

    out.write();
    return 0;
}
