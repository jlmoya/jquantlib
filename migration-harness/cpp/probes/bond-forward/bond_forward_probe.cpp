// migration-harness/cpp/probes/bond-forward/bond_forward_probe.cpp
// Reference values for BondForward against QuantLib v1.42.1.
// Phase 5d.5-Bonds.
//
// Reproduces the bondforward.cpp testFuturesPriceReplication /
// testCleanForwardPriceReplication / testThatForwardValueIsEqualToSpotValueIfNoIncome
// fixture and emits the BondForward primitive numbers (forwardValue,
// forwardPrice, cleanForwardPrice, spotValue, spotIncome) plus the
// underlying-bond accruedAmount and dirtyPrice for the Java port to
// cross-validate against.

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/instruments/bondforward.hpp>
#include <ql/instruments/bonds/fixedratebond.hpp>
#include <ql/pricingengines/bond/discountingbondengine.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/daycounters/actualactual.hpp>
#include <ql/settings.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("instruments/bond_forward",
                        QL_VERSION,
                        "bond_forward_probe");

    // CommonVars setup.
    Date today(7, March, 2022);
    Settings::instance().evaluationDate() = today;
    auto flatTs = ext::make_shared<FlatForward>(today, 0.0004977, Actual365Fixed());
    Handle<YieldTermStructure> curveHandle(flatTs);

    // Bond setup.
    Date issue(15, August, 2015);
    Date maturity(15, August, 2046);
    Rate cpn = 0.025;
    Schedule sch(issue, maturity, Period(Annual), TARGET(),
                 Following, Following,
                 DateGeneration::Backward, false);
    auto bnd = ext::make_shared<FixedRateBond>(2, 1.e5, sch,
                                               std::vector<Rate>(1, cpn),
                                               ActualActual(ActualActual::ISDA));
    bnd->setPricingEngine(ext::make_shared<DiscountingBondEngine>(curveHandle));

    // Forward setup.
    Date delivery(10, March, 2022);
    auto bndFwd = ext::make_shared<BondForward>(today, delivery,
                                                Position::Long, 0.0, 2,
                                                ActualActual(ActualActual::ISDA),
                                                TARGET(), Following,
                                                bnd, curveHandle, curveHandle);

    Real conversionFactor = 0.76871;

    Real fwdValue = bndFwd->forwardValue();
    Real fwdPrice = bndFwd->forwardPrice();
    Real cleanFwd = bndFwd->cleanForwardPrice();
    Real spotIncome = bndFwd->spotIncome(curveHandle);
    Real spotValue = bndFwd->spotValue();
    Real bondDirtyPrice = bnd->dirtyPrice();
    Real accruedAtDelivery = bnd->accruedAmount(delivery);
    Real impliedFutures = cleanFwd / conversionFactor;
    Real bondNpv = bnd->NPV();

    json inp{
        {"today_serial", today.serialNumber()},
        {"issue_serial", issue.serialNumber()},
        {"maturity_serial", maturity.serialNumber()},
        {"delivery_serial", delivery.serialNumber()},
        {"coupon", cpn},
        {"flatRate", 0.0004977},
        {"faceAmount", 1.0e5},
        {"settlementDays", 2},
        {"calendar", "TARGET"},
        {"dayCounter", "ActualActual.ISDA"},
        {"flatRateDayCounter", "Actual365Fixed"},
        {"position", "Long"},
        {"strike", 0.0},
        {"conversionFactor", conversionFactor}
    };
    json exp{
        {"forwardValue", fwdValue},
        {"forwardPrice", fwdPrice},
        {"cleanForwardPrice", cleanFwd},
        {"spotIncome", spotIncome},
        {"spotValue", spotValue},
        {"bondDirtyPrice", bondDirtyPrice},
        {"accruedAtDelivery", accruedAtDelivery},
        {"impliedFuturesPrice", impliedFutures},
        {"bondNPV", bondNpv}
    };
    out.addCase("flat_curve_long_position", inp, exp);

    out.write();
    return 0;
}
