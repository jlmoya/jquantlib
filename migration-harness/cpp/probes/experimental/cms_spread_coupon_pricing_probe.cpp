// migration-harness/cpp/probes/experimental/cms_spread_coupon_pricing_probe.cpp
//
// Reference values for CmsSpreadCoupon pricing — LognormalCmsSpreadPricer
// over LinearTsrPricer underlying CMS coupon-pricer, mirroring the C++
// test-suite testCouponPricing (test-suite/cmsspread.cpp). Phase 5e.5b-CFC-d-88.
//
// The C++ test internally builds an MC reference (1M Sobol samples, Hermite-
// quadrature on a bivariate (shifted-)lognormal / normal joint distribution
// of the two underlying swap rates) and tolerates 1e-6 absolute differences.
// On the Java side we cannot reproduce the MC reference bit-for-bit (Sobol +
// boost::accumulators path differences), so we instead pin the Java
// LognormalCmsSpreadPricer rates directly to the C++ LognormalCmsSpreadPricer
// rates — both implementations are deterministic Gauss-Hermite quadratures
// over the same Brigo-Mercurio closed-form integrand and should agree at
// tight tolerance (1e-12 rel) modulo ConstantSwaptionVolatility behaviour.
//
// Cases emitted:
//
//   identity_ln_{no_fixings, one_fixing, both_fixings} —
//       cpn1->rate() and cpn1a->rate() - cpn1b->rate() under the Ln pricer,
//       for the three fixing-history states from the first half of
//       testCouponPricing. The Java test asserts cpn1.rate() == cpn1a.rate()
//       - cpn1b.rate() at the C++ eqTol (100*QL_EPSILON).
//
//   {plain,capped,floored,collared}_{ln,sln,n} —
//       second half of testCouponPricing: 4 cap/floor configurations times 3
//       volatility regimes = 12 rate references for direct cross-check.

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/cashflows/cmscoupon.hpp>
#include <ql/cashflows/lineartsrpricer.hpp>
#include <ql/experimental/coupons/cmsspreadcoupon.hpp>
#include <ql/experimental/coupons/lognormalcmsspreadpricer.hpp>
#include <ql/indexes/swap/euriborswap.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/volatility/swaption/swaptionconstantvol.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/utilities/null.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("experimental/cms_spread_coupon_pricing",
                        QL_VERSION, "cms_spread_coupon_pricing_probe");

    // ------------------------------------------------------------------
    // TestData fixture (matches test-suite/cmsspread.cpp TestData)
    // ------------------------------------------------------------------
    const Date refDate(23, February, 2018);
    Settings::instance().evaluationDate() = refDate;

    Handle<YieldTermStructure> yts2(
        ext::make_shared<FlatForward>(refDate, 0.02, Actual365Fixed()));

    Handle<SwaptionVolatilityStructure> swLn(
        ext::make_shared<ConstantSwaptionVolatility>(
            refDate, TARGET(), Following, 0.20, Actual365Fixed(),
            ShiftedLognormal, 0.0));
    Handle<SwaptionVolatilityStructure> swSln(
        ext::make_shared<ConstantSwaptionVolatility>(
            refDate, TARGET(), Following, 0.10, Actual365Fixed(),
            ShiftedLognormal, 0.01));
    Handle<SwaptionVolatilityStructure> swN(
        ext::make_shared<ConstantSwaptionVolatility>(
            refDate, TARGET(), Following, 0.0075, Actual365Fixed(), Normal,
            0.01));

    Handle<Quote> reversion(ext::make_shared<SimpleQuote>(0.01));
    auto cmsPricerLn  = ext::make_shared<LinearTsrPricer>(swLn,  reversion, yts2);
    auto cmsPricerSln = ext::make_shared<LinearTsrPricer>(swSln, reversion, yts2);
    auto cmsPricerN   = ext::make_shared<LinearTsrPricer>(swN,   reversion, yts2);

    Handle<Quote> correlation(ext::make_shared<SimpleQuote>(0.6));
    auto cmsspPricerLn  = ext::make_shared<LognormalCmsSpreadPricer>(
        cmsPricerLn,  correlation, yts2, 32);
    auto cmsspPricerSln = ext::make_shared<LognormalCmsSpreadPricer>(
        cmsPricerSln, correlation, yts2, 32);
    auto cmsspPricerN   = ext::make_shared<LognormalCmsSpreadPricer>(
        cmsPricerN,   correlation, yts2, 32);

    // ------------------------------------------------------------------
    // Section A: identity rate cpn1->rate() == cpn1a->rate() - cpn1b->rate()
    // ------------------------------------------------------------------
    auto cms10y = ext::make_shared<EuriborSwapIsdaFixA>(10 * Years, yts2, yts2);
    auto cms2y  = ext::make_shared<EuriborSwapIsdaFixA>( 2 * Years, yts2, yts2);
    auto cms10y2y = ext::make_shared<SwapSpreadIndex>("cms10y2y", cms10y, cms2y);

    Date valueDate = cms10y2y->valueDate(refDate);
    Date payDate   = valueDate + 1 * Years;
    auto cpn1a = ext::make_shared<CmsCoupon>(
        payDate, 10000.0, valueDate, payDate, cms10y->fixingDays(), cms10y,
        1.0, 0.0, Date(), Date(), Actual360(), false);
    auto cpn1b = ext::make_shared<CmsCoupon>(
        payDate, 10000.0, valueDate, payDate, cms2y->fixingDays(), cms2y,
        1.0, 0.0, Date(), Date(), Actual360(), false);
    auto cpn1 = ext::make_shared<CmsSpreadCoupon>(
        payDate, 10000.0, valueDate, payDate, cms10y2y->fixingDays(),
        cms10y2y, 1.0, 0.0, Date(), Date(), Actual360(), false);
    cpn1a->setPricer(cmsPricerLn);
    cpn1b->setPricer(cmsPricerLn);
    cpn1->setPricer(cmsspPricerLn);

    auto emitIdentity = [&](const char* tag) {
        const Real r1  = cpn1->rate();
        const Real r1a = cpn1a->rate();
        const Real r1b = cpn1b->rate();
        json inp{ {"state", tag} };
        json exp{
            {"spread_rate",     r1},
            {"component_diff",  r1a - r1b},
            {"cpn1a_rate",      r1a},
            {"cpn1b_rate",      r1b}
        };
        out.addCase(std::string("identity_ln_") + tag, inp, exp);
    };

    emitIdentity("no_fixings");
    cms10y->addFixing(refDate, 0.05);
    emitIdentity("one_fixing");
    cms2y->addFixing(refDate, 0.03);
    emitIdentity("both_fixings");
    IndexManager::instance().clearHistories();

    // ------------------------------------------------------------------
    // Section B: plain/capped/floored/collared rates across 3 vol regimes
    // ------------------------------------------------------------------
    auto cpn2a = ext::make_shared<CmsCoupon>(
        Date(23, February, 2029), 10000.0,
        Date(23, February, 2028), Date(23, February, 2029), 2,
        cms10y, 1.0, 0.0, Date(), Date(), Actual360(), false);
    auto cpn2b = ext::make_shared<CmsCoupon>(
        Date(23, February, 2029), 10000.0,
        Date(23, February, 2028), Date(23, February, 2029), 2,
        cms2y, 1.0, 0.0, Date(), Date(), Actual360(), false);

    auto plainCpn = ext::make_shared<CappedFlooredCmsSpreadCoupon>(
        Date(23, February, 2029), 10000.0, Date(23, February, 2028),
        Date(23, February, 2029), 2, cms10y2y, 1.0, 0.0,
        Null<Rate>(), Null<Rate>(), Date(), Date(), Actual360(), false);
    auto cappedCpn = ext::make_shared<CappedFlooredCmsSpreadCoupon>(
        Date(23, February, 2029), 10000.0, Date(23, February, 2028),
        Date(23, February, 2029), 2, cms10y2y, 1.0, 0.0,
        0.03, Null<Rate>(), Date(), Date(), Actual360(), false);
    auto flooredCpn = ext::make_shared<CappedFlooredCmsSpreadCoupon>(
        Date(23, February, 2029), 10000.0, Date(23, February, 2028),
        Date(23, February, 2029), 2, cms10y2y, 1.0, 0.0,
        Null<Rate>(), 0.01, Date(), Date(), Actual360(), false);
    auto collaredCpn = ext::make_shared<CappedFlooredCmsSpreadCoupon>(
        Date(23, February, 2029), 10000.0, Date(23, February, 2028),
        Date(23, February, 2029), 2, cms10y2y, 1.0, 0.0,
        0.03, 0.01, Date(), Date(), Actual360(), false);

    struct PricerTriple {
        const char* tag;
        ext::shared_ptr<CmsCouponPricer>        cms;
        ext::shared_ptr<CmsSpreadCouponPricer>  spread;
    };

    PricerTriple triples[] = {
        {"ln",  cmsPricerLn,  cmsspPricerLn},
        {"sln", cmsPricerSln, cmsspPricerSln},
        {"n",   cmsPricerN,   cmsspPricerN},
    };

    for (const auto& t : triples) {
        cpn2a->setPricer(t.cms);
        cpn2b->setPricer(t.cms);
        plainCpn->setPricer(t.spread);
        cappedCpn->setPricer(t.spread);
        flooredCpn->setPricer(t.spread);
        collaredCpn->setPricer(t.spread);

        const Real plain    = plainCpn->rate();
        const Real capped   = cappedCpn->rate();
        const Real floored  = flooredCpn->rate();
        const Real collared = collaredCpn->rate();

        json inp_p{ {"pricer", t.tag}, {"configuration", "plain"} };
        json inp_c{ {"pricer", t.tag}, {"configuration", "capped_at_0.03"} };
        json inp_f{ {"pricer", t.tag}, {"configuration", "floored_at_0.01"} };
        json inp_x{ {"pricer", t.tag}, {"configuration", "collared_0.01_0.03"} };

        out.addCase(std::string("plain_")    + t.tag, inp_p, json{{"rate", plain}});
        out.addCase(std::string("capped_")   + t.tag, inp_c, json{{"rate", capped}});
        out.addCase(std::string("floored_")  + t.tag, inp_f, json{{"rate", floored}});
        out.addCase(std::string("collared_") + t.tag, inp_x, json{{"rate", collared}});
    }

    out.write();
    return 0;
}
