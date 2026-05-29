// migration-harness/cpp/probes/cashflows/digital_coupons_probe.cpp
//
// Reference values for DigitalIborCoupon and DigitalCmsCoupon against
// QuantLib v1.42.1. These two classes are thin DigitalCoupon subclasses over
// an IborCoupon / CmsCoupon underlying; all pricing flows through the shared
// DigitalCoupon call/put-spread replication, so the cross-validation targets
// are the rate(), callOptionRate(), putOptionRate() and amount() outputs.
//
// Gap-cashflows port.
//
// Ibor leg setup mirrors test-suite/digitalcoupon.cpp CommonVars: Euribor6M on
// a flat 5% Actual365Fixed curve, fixingDays=2, nominal=1e6, priced with a
// BlackIborCouponPricer at a constant caplet vol.
//
// Cms setup mirrors the cmsspread probe's TestData: EuriborSwapIsdaFixA(10Y)
// on a flat 2% Actual365Fixed curve, priced with a LinearTsrPricer over a
// ConstantSwaptionVolatility (shifted-lognormal, vol=0.20).
//
// The Java test rebuilds the identical setup and compares each scalar at TIGHT
// (abs 1e-14, rel 1e-12).

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/cashflows/digitaliborcoupon.hpp>
#include <ql/cashflows/digitalcmscoupon.hpp>
#include <ql/cashflows/digitalcoupon.hpp>
#include <ql/cashflows/iborcoupon.hpp>
#include <ql/cashflows/cmscoupon.hpp>
#include <ql/cashflows/couponpricer.hpp>
#include <ql/cashflows/lineartsrpricer.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/indexes/swap/euriborswap.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/volatility/optionlet/constantoptionletvol.hpp>
#include <ql/termstructures/volatility/swaption/swaptionconstantvol.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/utilities/null.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("cashflows/digital_coupons",
                        QL_VERSION, "digital_coupons_probe");

    const Real nullstrike = Null<Rate>();

    // ==================================================================
    // Section A: DigitalIborCoupon (Euribor6M, flat 5%)
    // ==================================================================
    {
        const Natural fixingDays = 2;
        const Real nominal = 1.0e6;
        RelinkableHandle<YieldTermStructure> termStructure;
        auto index = ext::make_shared<Euribor6M>(termStructure);
        Calendar calendar = index->fixingCalendar();
        Date today = calendar.adjust(Date(13, February, 2026));
        Settings::instance().evaluationDate() = today;
        Date settlement = calendar.advance(today, fixingDays, Days);
        termStructure.linkTo(ext::make_shared<FlatForward>(
            settlement, 0.05, Actual365Fixed()));

        const Real capletVol = 0.15;
        RelinkableHandle<OptionletVolatilityStructure> vol;
        vol.linkTo(ext::make_shared<ConstantOptionletVolatility>(
            today, calendar, Following, capletVol, Actual360()));

        // A coupon ~10y out so the option has time value.
        Date startDate = calendar.advance(settlement, Period(10, Years));
        Date endDate = calendar.advance(settlement, Period(11, Years));
        Date paymentDate = endDate;

        const Real gearing = 1.0;
        const Real spread = 0.0;
        const Real gap = 1.0e-4;
        auto replication = ext::make_shared<DigitalReplication>(
            Replication::Central, gap);

        // --- Cash-or-nothing call (long), cash rate 0.04, strike 0.05 ---
        const Real cashRate = 0.04;
        const Real strike = 0.05;
        {
            auto underlying = ext::make_shared<IborCoupon>(
                paymentDate, nominal, startDate, endDate, fixingDays, index,
                gearing, spread);
            auto digital = ext::make_shared<DigitalIborCoupon>(
                underlying, strike, Position::Long, false, cashRate,
                nullstrike, Position::Long, false, nullstrike,
                replication, false);
            auto pricer = ext::make_shared<BlackIborCouponPricer>(vol);
            digital->setPricer(pricer);

            out.addCase("ibor_cash_call_long",
                {
                    {"index", "Euribor6M"},
                    {"flatForward", 0.05},
                    {"capletVol", capletVol},
                    {"strike", strike},
                    {"cashRate", cashRate},
                    {"gap", gap},
                    {"replication", "Central"}
                },
                {
                    {"underlyingRate", underlying->rate()},
                    {"rate", digital->rate()},
                    {"callOptionRate", digital->callOptionRate()},
                    {"putOptionRate", digital->putOptionRate()},
                    {"amount", digital->amount()},
                    {"accrualPeriod", underlying->accrualPeriod()}
                });
        }

        // --- Asset-or-nothing put (long), strike 0.06 ---
        {
            const Real putStrike = 0.06;
            auto underlying = ext::make_shared<IborCoupon>(
                paymentDate, nominal, startDate, endDate, fixingDays, index,
                gearing, spread);
            auto digital = ext::make_shared<DigitalIborCoupon>(
                underlying, nullstrike, Position::Long, false, nullstrike,
                putStrike, Position::Long, false, nullstrike,
                replication, false);
            auto pricer = ext::make_shared<BlackIborCouponPricer>(vol);
            digital->setPricer(pricer);

            out.addCase("ibor_asset_put_long",
                {
                    {"putStrike", putStrike}
                },
                {
                    {"underlyingRate", underlying->rate()},
                    {"rate", digital->rate()},
                    {"callOptionRate", digital->callOptionRate()},
                    {"putOptionRate", digital->putOptionRate()},
                    {"amount", digital->amount()}
                });
        }

        // --- Collar: cash call long @0.05 + cash put short @0.03 ---
        {
            auto underlying = ext::make_shared<IborCoupon>(
                paymentDate, nominal, startDate, endDate, fixingDays, index,
                gearing, spread);
            auto digital = ext::make_shared<DigitalIborCoupon>(
                underlying, 0.05, Position::Long, false, cashRate,
                0.03, Position::Short, false, cashRate,
                replication, false);
            auto pricer = ext::make_shared<BlackIborCouponPricer>(vol);
            digital->setPricer(pricer);

            out.addCase("ibor_cash_collar",
                {
                    {"callStrike", 0.05},
                    {"putStrike", 0.03},
                    {"cashRate", cashRate}
                },
                {
                    {"underlyingRate", underlying->rate()},
                    {"rate", digital->rate()},
                    {"callOptionRate", digital->callOptionRate()},
                    {"putOptionRate", digital->putOptionRate()},
                    {"amount", digital->amount()}
                });
        }
    }

    // ==================================================================
    // Section B: DigitalCmsCoupon (EuriborSwapIsdaFixA 10Y, flat 2%)
    // ==================================================================
    {
        const Date refDate(23, February, 2018);
        Settings::instance().evaluationDate() = refDate;

        Handle<YieldTermStructure> yts2(
            ext::make_shared<FlatForward>(refDate, 0.02, Actual365Fixed()));

        Handle<SwaptionVolatilityStructure> swLn(
            ext::make_shared<ConstantSwaptionVolatility>(
                refDate, TARGET(), Following, 0.20, Actual365Fixed(),
                ShiftedLognormal, 0.0));
        Handle<Quote> reversion(ext::make_shared<SimpleQuote>(0.01));
        auto cmsPricer = ext::make_shared<LinearTsrPricer>(swLn, reversion, yts2);

        auto cms10y = ext::make_shared<EuriborSwapIsdaFixA>(
            10 * Years, yts2, yts2);

        Date startDate(23, February, 2028);
        Date endDate(23, February, 2029);
        Date paymentDate = endDate;
        const Real nominal = 10000.0;
        const Real gearing = 1.0;
        const Real spread = 0.0;
        const Real gap = 1.0e-4;
        auto replication = ext::make_shared<DigitalReplication>(
            Replication::Central, gap);

        // --- Cash-or-nothing call (long), cash rate 0.02, strike 0.03 ---
        {
            const Real strike = 0.03;
            const Real cashRate = 0.02;
            auto underlying = ext::make_shared<CmsCoupon>(
                paymentDate, nominal, startDate, endDate, 2, cms10y,
                gearing, spread, Date(), Date(), Actual360(), false);
            auto digital = ext::make_shared<DigitalCmsCoupon>(
                underlying, strike, Position::Long, false, cashRate,
                nullstrike, Position::Long, false, nullstrike,
                replication, false);
            underlying->setPricer(cmsPricer);
            digital->setPricer(cmsPricer);

            out.addCase("cms_cash_call_long",
                {
                    {"index", "EuriborSwapIsdaFixA10Y"},
                    {"flatForward", 0.02},
                    {"swaptionVol", 0.20},
                    {"reversion", 0.01},
                    {"strike", strike},
                    {"cashRate", cashRate},
                    {"gap", gap}
                },
                {
                    {"underlyingRate", underlying->rate()},
                    {"rate", digital->rate()},
                    {"callOptionRate", digital->callOptionRate()},
                    {"putOptionRate", digital->putOptionRate()},
                    {"amount", digital->amount()},
                    {"accrualPeriod", underlying->accrualPeriod()}
                });
        }

        // --- Asset-or-nothing put (long), strike 0.05 ---
        {
            const Real putStrike = 0.05;
            auto underlying = ext::make_shared<CmsCoupon>(
                paymentDate, nominal, startDate, endDate, 2, cms10y,
                gearing, spread, Date(), Date(), Actual360(), false);
            auto digital = ext::make_shared<DigitalCmsCoupon>(
                underlying, nullstrike, Position::Long, false, nullstrike,
                putStrike, Position::Long, false, nullstrike,
                replication, false);
            underlying->setPricer(cmsPricer);
            digital->setPricer(cmsPricer);

            out.addCase("cms_asset_put_long",
                {
                    {"putStrike", putStrike}
                },
                {
                    {"underlyingRate", underlying->rate()},
                    {"rate", digital->rate()},
                    {"callOptionRate", digital->callOptionRate()},
                    {"putOptionRate", digital->putOptionRate()},
                    {"amount", digital->amount()}
                });
        }
    }

    out.write();
    std::printf("digital_coupons_probe: cases written to references/cashflows/digital_coupons.json\n");
    return 0;
}
