// migration-harness/cpp/probes/credit/base_correlation_loss_model_probe.cpp
// Reference values for ql/experimental/credit/basecorrelationlossmodel.hpp
// (QuantLib::BaseCorrelationLossModel, vanilla typedef GaussianLHPFlatBCLM =
//  BaseCorrelationLossModel<GaussianLHPLossModel, BilinearInterpolation>).
//
// We build a base-correlation surface (bilinear), attach a GaussianLHPFlatBCLM
// to a homogeneous basket of flat-hazard names, and emit the model's
// expectedTrancheLoss at several dates. Fully deterministic (analytic LHP
// kernel + bilinear interpolation) -> TIGHT tolerance on the Java side.

#include <ql/version.hpp>
#include <ql/experimental/credit/basecorrelationlossmodel.hpp>
#include <ql/experimental/credit/basket.hpp>
#include <ql/experimental/credit/defaultprobabilitykey.hpp>
#include <ql/experimental/credit/pool.hpp>
#include <ql/currencies/europe.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/credit/flathazardrate.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/time/daycounters/actualactual.hpp>
#include <ql/settings.hpp>
#include "../common.hpp"

#include <sstream>
#include <string>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("credit/base_correlation_loss_model", QL_VERSION,
                        "base_correlation_loss_model_probe.cpp");

    const Date asof(15, June, 2010);
    Settings::instance().evaluationDate() = asof;

    const Calendar cal = NullCalendar();
    const DayCounter dc = Actual360();
    const BusinessDayConvention bdc = ModifiedFollowing;
    const Natural settlementDays = 0;

    const Size poolSize = 20;
    const Real lambda = 0.01;
    const Real recovery = 0.4;

    // --- Base-correlation surface (bilinear, 4 tenors x 3 loss levels) -------
    std::vector<Period> tenors = {Period(12, Months), Period(36, Months),
                                  Period(60, Months), Period(84, Months)};
    std::vector<Real> losses = {0.03, 0.10, 0.20};
    // correls[iLoss][iTenor]
    std::vector<std::vector<Real>> correlVals = {
        {0.20, 0.22, 0.25, 0.27},   // loss = 0.03
        {0.35, 0.37, 0.40, 0.42},   // loss = 0.10
        {0.55, 0.57, 0.60, 0.63}    // loss = 0.20
    };
    std::vector<std::vector<Handle<Quote>>> hs(losses.size(),
            std::vector<Handle<Quote>>(tenors.size()));
    for (Size i = 0; i < losses.size(); ++i)
        for (Size j = 0; j < tenors.size(); ++j)
            hs[i][j] = Handle<Quote>(ext::shared_ptr<Quote>(new SimpleQuote(correlVals[i][j])));

    ext::shared_ptr<BaseCorrelationTermStructure<BilinearInterpolation>> correlSurface(
        new BaseCorrelationTermStructure<BilinearInterpolation>(
            settlementDays, cal, bdc, tenors, losses, hs, dc));
    Handle<BaseCorrelationTermStructure<BilinearInterpolation>> correlHandle(correlSurface);

    // --- Pool of homogeneous flat-hazard names --------------------------------
    Handle<Quote> hazardRate(ext::shared_ptr<Quote>(new SimpleQuote(lambda)));
    ext::shared_ptr<DefaultProbabilityTermStructure> defPtr(
        new FlatHazardRate(asof, hazardRate, ActualActual(ActualActual::ISDA)));

    ext::shared_ptr<Pool> pool(new Pool());
    std::vector<std::string> names;
    std::vector<Real> nominals;
    std::vector<std::pair<DefaultProbKey, Handle<DefaultProbabilityTermStructure>>> probabilities;
    probabilities.emplace_back(
        NorthAmericaCorpDefaultKey(EURCurrency(), SeniorSec, Period(0, Weeks), 10.),
        Handle<DefaultProbabilityTermStructure>(defPtr));
    for (Size i = 0; i < poolSize; ++i) {
        std::ostringstream o;
        o << "issuer-" << i;
        names.push_back(o.str());
        nominals.push_back(100.0);
        Issuer issuer(probabilities);
        pool->add(names.back(), issuer,
                  NorthAmericaCorpDefaultKey(EURCurrency(), SeniorSec, Period(), 1.));
    }

    std::vector<Real> recoveries(poolSize, recovery);

    // Helper: build a fresh basket + model for one tranche and emit ETL.
    auto emitTranche = [&](const std::string& name, Real attach, Real detach) {
        ext::shared_ptr<Basket> basket(
            new Basket(asof, names, nominals, pool, attach, detach));
        ext::shared_ptr<GaussianLHPFlatBCLM> model(
            new GaussianLHPFlatBCLM(correlHandle, recoveries));
        basket->setLossModel(model);

        json inputs = {
            {"asof", asof.serialNumber()},
            {"poolSize", (double) poolSize},
            {"lambda", lambda},
            {"recovery", recovery},
            {"attach", attach},
            {"detach", detach},
            {"tenors_months", json::array()},
            {"losses", losses},
            {"correlsRowMajor", json::array()}
        };
        for (auto& t : tenors) inputs["tenors_months"].push_back(t.length());
        for (auto& row : correlVals)
            for (auto v : row) inputs["correlsRowMajor"].push_back(v);

        // ETL at 1y, 3y, 5y.
        std::vector<std::pair<std::string, Date>> evalDates = {
            {"1y", asof + Period(12, Months)},
            {"3y", asof + Period(36, Months)},
            {"5y", asof + Period(60, Months)}
        };
        for (auto& [tag, d] : evalDates) {
            out.addCase(name + "_etl_" + tag, inputs, (double) basket->expectedTrancheLoss(d));
        }
    };

    emitTranche("equity_0_3", 0.00, 0.03);
    emitTranche("mezz_3_10", 0.03, 0.10);
    emitTranche("senior_10_20", 0.10, 0.20);

    out.write();
    return 0;
}
