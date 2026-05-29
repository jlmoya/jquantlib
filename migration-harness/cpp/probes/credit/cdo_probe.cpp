// migration-harness/cpp/probes/credit/cdo_probe.cpp
// Reference values for ql/experimental/credit/cdo.{hpp,cpp} (QuantLib::CDO),
// the Hull-White probability-bucketing CDO *instrument* (distinct from
// SyntheticCDO + MidPoint/Integral engines).
//
// We construct a CDO directly on a small homogeneous basket of flat-hazard-rate
// names with a one-factor Gaussian copula and emit the deterministic public
// outputs: premiumValue, protectionValue, fairPremium, NPV, error count.
// These are fully deterministic (no Monte-Carlo) -> TIGHT tolerance on the
// Java side.

#include <ql/version.hpp>
#include <ql/experimental/credit/cdo.hpp>
#include <ql/experimental/credit/onefactorgaussiancopula.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/credit/flathazardrate.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/time/daycounters/actualactual.hpp>
#include <ql/time/schedule.hpp>
#include <ql/settings.hpp>
#include "../common.hpp"

#include <string>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// Builds a CDO and emits its deterministic outputs for one (attach, detach,
// correlation) configuration.
void emitCdoCase(ReferenceWriter& out, const std::string& name, Real attachment, Real detachment,
                 Real correlation, bool protectionSeller, Size poolSize, Real lambda, Real rate,
                 Real recovery, Real premium, Real upfront, Size nBuckets) {
    const DayCounter daycount = Actual360();
    const Date asofDate(31, August, 2006);
    Settings::instance().evaluationDate() = asofDate;

    ext::shared_ptr<YieldTermStructure> yieldPtr(
        new FlatForward(asofDate, rate, daycount, Continuous));
    Handle<YieldTermStructure> yieldHandle(yieldPtr);

    Handle<Quote> hazardRate(ext::shared_ptr<Quote>(new SimpleQuote(lambda)));
    ext::shared_ptr<DefaultProbabilityTermStructure> defPtr(
        new FlatHazardRate(asofDate, hazardRate, ActualActual(ActualActual::ISDA)));

    std::vector<Handle<DefaultProbabilityTermStructure>> basket;
    std::vector<Real> nominals;
    for (Size i = 0; i < poolSize; ++i) {
        basket.emplace_back(defPtr);
        nominals.push_back(100.0);
    }

    ext::shared_ptr<SimpleQuote> correl(new SimpleQuote(correlation));
    Handle<OneFactorCopula> copula(
        ext::shared_ptr<OneFactorCopula>(new OneFactorGaussianCopula(Handle<Quote>(correl))));

    Schedule schedule = MakeSchedule()
                            .from(Date(1, September, 2006))
                            .to(Date(1, September, 2011))
                            .withTenor(Period(3, Months))
                            .withCalendar(TARGET());

    CDO cdo(attachment, detachment, nominals, basket, copula, protectionSeller, schedule, premium,
            daycount, recovery, upfront, yieldHandle, nBuckets, Period(1, Years));

    json inputs = {
        {"asof", asofDate.serialNumber()},
        {"attachment", attachment},
        {"detachment", detachment},
        {"correlation", correlation},
        {"protectionSeller", protectionSeller},
        {"poolSize", (double) poolSize},
        {"lambda", lambda},
        {"rate", rate},
        {"recovery", recovery},
        {"premium", premium},
        {"upfront", upfront},
        {"nBuckets", (double) nBuckets},
        {"integrationStepYears", 1.0}
    };

    out.addCase(name + "_premiumValue", inputs, (double) cdo.premiumValue());
    out.addCase(name + "_protectionValue", inputs, (double) cdo.protectionValue());
    out.addCase(name + "_fairPremium", inputs, (double) cdo.fairPremium());
    out.addCase(name + "_NPV", inputs, (double) cdo.NPV());
    out.addCase(name + "_error", inputs, (double) cdo.error());
    out.addCase(name + "_nominal", inputs, (double) cdo.nominal());
    out.addCase(name + "_lgd", inputs, (double) cdo.lgd());
}

} // namespace

int main() {
    ReferenceWriter out("credit/cdo", QL_VERSION, "cdo_probe.cpp");

    // Small homogeneous baskets (poolSize 10) so the bucketing runs fast.
    // Equity tranche 0-3% with two correlation levels.
    emitCdoCase(out, "equity_0_3_corr10", 0.00, 0.03, 0.1, true, 10, 0.01, 0.05, 0.4, 0.02, 0.0, 100);
    emitCdoCase(out, "equity_0_3_corr30", 0.00, 0.03, 0.3, true, 10, 0.01, 0.05, 0.4, 0.02, 0.0, 100);
    // Mezzanine 3-6%
    emitCdoCase(out, "mezz_3_6_corr30", 0.03, 0.06, 0.3, true, 10, 0.01, 0.05, 0.4, 0.02, 0.0, 100);
    // Senior 10-100%
    emitCdoCase(out, "senior_10_100_corr30", 0.10, 1.00, 0.3, true, 10, 0.01, 0.05, 0.4, 0.02, 0.0, 100);
    // Purchased protection (protectionSeller=false) on the equity tranche.
    emitCdoCase(out, "equity_0_3_corr30_buyer", 0.00, 0.03, 0.3, false, 10, 0.01, 0.05, 0.4, 0.02, 0.0, 100);

    out.write();
    return 0;
}
