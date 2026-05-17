// migration-harness/cpp/probes/experimental/crosscurrency_probe.cpp
// Reference values for QuantLib v1.42.1 CrossCurrencyRateHelpersTests
// (test-suite/crosscurrencyratehelpers.cpp). Phase 5e.5b-CFC-d-81.

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/experimental/termstructures/crosscurrencyratehelpers.hpp>
#include <ql/cashflows/iborcoupon.hpp>
#include <ql/cashflows/cashflows.hpp>
#include <ql/cashflows/simplecashflow.hpp>
#include <ql/cashflows/fixedratecoupon.hpp>
#include <ql/indexes/ibor/eonia.hpp>
#include <ql/indexes/ibor/sofr.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/indexes/ibor/usdlibor.hpp>
#include <ql/math/interpolations/loginterpolation.hpp>
#include <ql/pricingengines/swap/discountingswapengine.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/shared_ptr.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/termstructures/yield/piecewiseyieldcurve.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/daycounters/thirty360.hpp>

using namespace jqml_harness;
using namespace QuantLib;

namespace {

struct XccyTestDatum {
    Integer n;
    TimeUnit units;
    Spread basis;
    XccyTestDatum(Integer n, TimeUnit units, Spread basis)
        : n(n), units(units), basis(basis) {}
};

struct CommonVars {
    Real basisPoint;
    Real fxSpot;
    Natural instrumentSettlementDays, curveSettlementDays;
    Date today, instrumentSettlementDt, curveSettlementDt;
    Calendar calendar;
    BusinessDayConvention businessConvention;
    DayCounter dayCount;
    bool endOfMonth;
    ext::shared_ptr<IborIndex> baseCcyIdx;
    ext::shared_ptr<IborIndex> quoteCcyIdx;
    ext::shared_ptr<IborIndex> baseOvernightIndex;
    ext::shared_ptr<IborIndex> quoteOvernightIndex;
    RelinkableHandle<YieldTermStructure> baseCcyIdxHandle;
    RelinkableHandle<YieldTermStructure> quoteCcyIdxHandle;
    std::vector<XccyTestDatum> basisData;

    ext::shared_ptr<RateHelper>
    constantNotionalXccyRateHelper(const XccyTestDatum& q,
                                   const Handle<YieldTermStructure>& collateralHandle,
                                   bool isFxBaseCurrencyCollateralCurrency,
                                   bool isBasisOnFxBaseCurrencyLeg) const {
        Handle<Quote> quoteHandle(ext::make_shared<SimpleQuote>(q.basis * basisPoint));
        Period tenor(q.n, q.units);
        return ext::shared_ptr<RateHelper>(new ConstNotionalCrossCurrencyBasisSwapRateHelper(
            quoteHandle, tenor, instrumentSettlementDays, calendar, businessConvention,
            endOfMonth, baseCcyIdx, quoteCcyIdx, collateralHandle,
            isFxBaseCurrencyCollateralCurrency, isBasisOnFxBaseCurrencyLeg));
    }

    std::vector<ext::shared_ptr<RateHelper> >
    buildConstantNotionalXccyRateHelpers(const std::vector<XccyTestDatum>& xccyData,
                                         const Handle<YieldTermStructure>& collateralHandle,
                                         bool isFxBaseCurrencyCollateralCurrency,
                                         bool isBasisOnFxBaseCurrencyLeg) const {
        std::vector<ext::shared_ptr<RateHelper> > instruments;
        instruments.reserve(xccyData.size());
        for (const auto& i : xccyData) {
            instruments.push_back(constantNotionalXccyRateHelper(
                i, collateralHandle, isFxBaseCurrencyCollateralCurrency,
                isBasisOnFxBaseCurrencyLeg));
        }
        return instruments;
    }

    ext::shared_ptr<RateHelper>
    resettingXccyRateHelper(const XccyTestDatum& q,
                            const Handle<YieldTermStructure>& collateralHandle,
                            bool isFxBaseCurrencyCollateralCurrency,
                            bool isBasisOnFxBaseCurrencyLeg,
                            bool isFxBaseCurrencyLegResettable,
                            Frequency paymentFrequency = NoFrequency,
                            Integer paymentLag = 0,
                            bool useOvernightIndex = false) const {
        Handle<Quote> quoteHandle(ext::make_shared<SimpleQuote>(q.basis * basisPoint));
        Period tenor(q.n, q.units);
        ext::shared_ptr<IborIndex> baseIndex, quoteIndex;
        if (useOvernightIndex) {
            baseIndex = baseOvernightIndex;
            quoteIndex = quoteOvernightIndex;
        } else {
            baseIndex = baseCcyIdx;
            quoteIndex = quoteCcyIdx;
        }
        return ext::shared_ptr<RateHelper>(new MtMCrossCurrencyBasisSwapRateHelper(
            quoteHandle, tenor, instrumentSettlementDays, calendar, businessConvention,
            endOfMonth, baseIndex, quoteIndex, collateralHandle,
            isFxBaseCurrencyCollateralCurrency, isBasisOnFxBaseCurrencyLeg,
            isFxBaseCurrencyLegResettable, paymentFrequency, paymentLag));
    }

    std::vector<ext::shared_ptr<RateHelper> >
    buildResettingXccyRateHelpers(const std::vector<XccyTestDatum>& xccyData,
                                  const Handle<YieldTermStructure>& collateralHandle,
                                  bool isFxBaseCurrencyCollateralCurrency,
                                  bool isBasisOnFxBaseCurrencyLeg,
                                  bool isFxBaseCurrencyLegResettable,
                                  Frequency paymentFrequency = NoFrequency,
                                  Integer paymentLag = 0,
                                  bool useOvernightQuoteIndex = false) const {
        std::vector<ext::shared_ptr<RateHelper> > instruments;
        instruments.reserve(xccyData.size());
        for (const auto& i : xccyData) {
            instruments.push_back(resettingXccyRateHelper(
                i, collateralHandle, isFxBaseCurrencyCollateralCurrency,
                isBasisOnFxBaseCurrencyLeg, isFxBaseCurrencyLegResettable,
                paymentFrequency, paymentLag, useOvernightQuoteIndex));
        }
        return instruments;
    }

    CommonVars() {
        curveSettlementDays = 0;
        instrumentSettlementDays = 2;
        businessConvention = Following;
        calendar = TARGET();
        dayCount = Actual365Fixed();
        endOfMonth = false;
        basisPoint = 1.0e-4;
        fxSpot = 1.25;
        baseCcyIdx = ext::shared_ptr<IborIndex>(new Euribor3M(baseCcyIdxHandle));
        quoteCcyIdx = ext::shared_ptr<IborIndex>(new USDLibor(3 * Months, quoteCcyIdxHandle));
        baseOvernightIndex = ext::shared_ptr<IborIndex>(new Eonia(baseCcyIdxHandle));
        quoteOvernightIndex = ext::shared_ptr<IborIndex>(new Sofr(quoteCcyIdxHandle));
        basisData.emplace_back(1, Years, -14.5);
        basisData.emplace_back(18, Months, -18.5);
        basisData.emplace_back(2, Years, -20.5);
        basisData.emplace_back(3, Years, -23.75);
        basisData.emplace_back(4, Years, -25.5);
        basisData.emplace_back(5, Years, -26.5);
        basisData.emplace_back(7, Years, -26.75);
        basisData.emplace_back(10, Years, -26.25);
        basisData.emplace_back(15, Years, -24.75);
        basisData.emplace_back(20, Years, -23.25);
        basisData.emplace_back(30, Years, -20.50);
        today = calendar.adjust(Date(6, September, 2013));
        Settings::instance().evaluationDate() = today;
        instrumentSettlementDt = calendar.advance(today, instrumentSettlementDays, Days);
        curveSettlementDt = calendar.advance(today, curveSettlementDays, Days);
        Handle<YieldTermStructure> baseFlat(ext::make_shared<FlatForward>(
            curveSettlementDt, 0.007, dayCount));
        Handle<YieldTermStructure> quoteFlat(ext::make_shared<FlatForward>(
            curveSettlementDt, 0.015, dayCount));
        baseCcyIdxHandle.linkTo(*baseFlat);
        quoteCcyIdxHandle.linkTo(*quoteFlat);
    }
};

json captureCurvePoints(const ext::shared_ptr<YieldTermStructure>& curve,
                        const std::vector<ext::shared_ptr<RateHelper> >& instruments,
                        const DayCounter& dc) {
    json arr = json::array();
    for (Size i = 0; i < instruments.size(); ++i) {
        Date mat = instruments[i]->maturityDate();
        Real df = curve->discount(mat);
        Real zr = curve->zeroRate(mat, dc, Continuous).rate();
        arr.push_back({
            {"i", static_cast<int>(i)},
            {"maturity_serial", mat.serialNumber()},
            {"discount", df},
            {"zero_continuous", zr}
        });
    }
    return arr;
}

void runConstNotionalCase(ReferenceWriter& out,
                          const std::string& name,
                          bool isFxBaseCurrencyCollateralCurrency,
                          bool isBasisOnFxBaseCurrencyLeg) {
    CommonVars vars;
    Handle<YieldTermStructure> collateralHandle =
        isFxBaseCurrencyCollateralCurrency ? vars.baseCcyIdxHandle : vars.quoteCcyIdxHandle;
    std::vector<ext::shared_ptr<RateHelper> > instruments =
        vars.buildConstantNotionalXccyRateHelpers(vars.basisData, collateralHandle,
                                                  isFxBaseCurrencyCollateralCurrency,
                                                  isBasisOnFxBaseCurrencyLeg);
    ext::shared_ptr<YieldTermStructure> curve(
        new PiecewiseYieldCurve<Discount, LogLinear>(
            vars.curveSettlementDt, instruments, vars.dayCount));
    curve->enableExtrapolation();
    json inp{
        {"today_serial", vars.today.serialNumber()},
        {"curveSettlement_serial", vars.curveSettlementDt.serialNumber()},
        {"isFxBaseCurrencyCollateralCurrency", isFxBaseCurrencyCollateralCurrency},
        {"isBasisOnFxBaseCurrencyLeg", isBasisOnFxBaseCurrencyLeg}
    };
    json exp{
        {"curve_points", captureCurvePoints(curve, instruments, vars.dayCount)}
    };
    out.addCase(name, inp, exp);
}

void runResettingCase(ReferenceWriter& out,
                      const std::string& name,
                      bool isFxBaseCurrencyCollateralCurrency,
                      bool isBasisOnFxBaseCurrencyLeg,
                      bool isFxBaseCurrencyLegResettable,
                      Frequency paymentFrequency = NoFrequency,
                      Integer paymentLag = 0,
                      bool useOvernightIndex = false) {
    CommonVars vars;
    Handle<YieldTermStructure> collateralHandle =
        isFxBaseCurrencyCollateralCurrency ? vars.baseCcyIdxHandle : vars.quoteCcyIdxHandle;
    std::vector<ext::shared_ptr<RateHelper> > resettingInstruments =
        vars.buildResettingXccyRateHelpers(
            vars.basisData, collateralHandle, isFxBaseCurrencyCollateralCurrency,
            isBasisOnFxBaseCurrencyLeg, isFxBaseCurrencyLegResettable,
            paymentFrequency, paymentLag, useOvernightIndex);
    std::vector<ext::shared_ptr<RateHelper> > constNotionalInstruments =
        vars.buildConstantNotionalXccyRateHelpers(vars.basisData, collateralHandle,
                                                  isFxBaseCurrencyCollateralCurrency,
                                                  isBasisOnFxBaseCurrencyLeg);
    ext::shared_ptr<YieldTermStructure> resettingCurve(
        new PiecewiseYieldCurve<Discount, LogLinear>(
            vars.curveSettlementDt, resettingInstruments, vars.dayCount));
    resettingCurve->enableExtrapolation();
    ext::shared_ptr<YieldTermStructure> constNotionalCurve(
        new PiecewiseYieldCurve<Discount, LogLinear>(
            vars.curveSettlementDt, constNotionalInstruments, vars.dayCount));
    constNotionalCurve->enableExtrapolation();
    json arr = json::array();
    for (Size i = 0; i < resettingInstruments.size(); ++i) {
        Date matR = resettingInstruments[i]->maturityDate();
        Date matC = constNotionalInstruments[i]->maturityDate();
        Real zR = resettingCurve->zeroRate(matR, vars.dayCount, Continuous).rate();
        Real zC = constNotionalCurve->zeroRate(matC, vars.dayCount, Continuous).rate();
        arr.push_back({
            {"i", static_cast<int>(i)},
            {"maturity_resetting_serial", matR.serialNumber()},
            {"maturity_constNotional_serial", matC.serialNumber()},
            {"zero_resetting", zR},
            {"zero_constNotional", zC},
            {"diff", zR - zC}
        });
    }
    json inp{
        {"today_serial", vars.today.serialNumber()},
        {"curveSettlement_serial", vars.curveSettlementDt.serialNumber()},
        {"isFxBaseCurrencyCollateralCurrency", isFxBaseCurrencyCollateralCurrency},
        {"isBasisOnFxBaseCurrencyLeg", isBasisOnFxBaseCurrencyLeg},
        {"isFxBaseCurrencyLegResettable", isFxBaseCurrencyLegResettable},
        {"paymentFrequency", static_cast<int>(paymentFrequency)},
        {"paymentLag", paymentLag},
        {"useOvernightIndex", useOvernightIndex}
    };
    json exp{
        {"zero_pairs", arr}
    };
    out.addCase(name, inp, exp);
}

} // namespace

int main() {
    ReferenceWriter out("experimental/crosscurrency",
                        QL_VERSION,
                        "crosscurrency_probe");

    runConstNotionalCase(out, "constNotional_collat_quote_basis_base",
                         false, true);
    runConstNotionalCase(out, "constNotional_collat_base_basis_quote",
                         true, false);
    runConstNotionalCase(out, "constNotional_collat_base_basis_base",
                         true, true);
    runConstNotionalCase(out, "constNotional_collat_quote_basis_quote",
                         false, false);

    runResettingCase(out, "resetting_collat_quote_basis_base",
                     false, true, false);
    runResettingCase(out, "resetting_collat_base_basis_quote",
                     true, false, true);
    runResettingCase(out, "resetting_collat_base_basis_base",
                     true, true,  true);
    runResettingCase(out, "resetting_collat_quote_basis_quote",
                     false, false, false);

    runResettingCase(out, "resetting_arbitraryFreq",
                     false, true, false, Weekly);
    runResettingCase(out, "resetting_paymentLag",
                     false, true, false, NoFrequency, 2);
    runResettingCase(out, "resetting_overnightIndex",
                     false, true, false, Quarterly, 0, true);

    {
        CommonVars vars;
        std::vector<XccyTestDatum> data{{1, Months, 10.0}};
        Handle<YieldTermStructure> collateralHandle;
        bool threw = false;
        try {
            auto rh = vars.buildConstantNotionalXccyRateHelpers(data, collateralHandle, true, true);
            (void)rh;
        } catch (std::exception&) {
            threw = true;
        }
        json inp{{"tenor", "1M"}, {"index_freq", "3M"}};
        json exp{{"throws", threw}};
        out.addCase("tenorShorterThanIndexFreq_throws", inp, exp);
    }

    {
        CommonVars vars;
        Handle<YieldTermStructure> collateralHandle = vars.quoteCcyIdxHandle;
        bool threw = false;
        try {
            std::vector<ext::shared_ptr<RateHelper> > resettingInstruments =
                vars.buildResettingXccyRateHelpers(
                    vars.basisData, collateralHandle,
                    false, true, false,
                    NoFrequency, 0, true);
            ext::shared_ptr<YieldTermStructure> curve(
                new PiecewiseYieldCurve<Discount, LogLinear>(
                    vars.curveSettlementDt, resettingInstruments, vars.dayCount));
            curve->enableExtrapolation();
            (void)curve->discount(resettingInstruments.back()->maturityDate());
        } catch (std::exception&) {
            threw = true;
        }
        json inp{{"paymentFrequency", "NoFrequency"}, {"useOvernightIndex", true}};
        json exp{{"throws", threw}};
        out.addCase("resetting_overnightIndex_exception", inp, exp);
    }

    {
        SavedSettings backup;
        Date today(15, January, 2026);
        Settings::instance().evaluationDate() = today;
        RelinkableHandle<YieldTermStructure> usdCollat;
        usdCollat.linkTo(ext::make_shared<FlatForward>(today, 0.02, Actual365Fixed()));
        Handle<YieldTermStructure> eurFwd(
            ext::make_shared<FlatForward>(today, 0.017, Actual365Fixed()));
        ext::shared_ptr<IborIndex> euribor3m = ext::make_shared<Euribor3M>(eurFwd);
        Handle<Quote> q(ext::make_shared<SimpleQuote>(0.018));
        ConstNotionalCrossCurrencySwapRateHelper h(
            q, Period(5, Years), 2, TARGET(), Following, true, Annual,
            Thirty360(Thirty360::BondBasis), euribor3m, usdCollat, true);
        RelinkableHandle<YieldTermStructure> bootstrapCurve;
        bootstrapCurve.linkTo(ext::make_shared<FlatForward>(today, 0.02, Actual360()));
        h.setTermStructure(bootstrapCurve.currentLink().get());
        Real oldQuote = h.impliedQuote();
        usdCollat.linkTo(ext::make_shared<FlatForward>(today, 0.03, Actual365Fixed()));
        Real newQuote = h.impliedQuote();
        json inp{
            {"today_serial", today.serialNumber()},
            {"oldCollatRate", 0.02},
            {"newCollatRate", 0.03}
        };
        json exp{
            {"oldQuote", oldQuote},
            {"newQuote", newQuote},
            {"distinct", oldQuote != newQuote}
        };
        out.addCase("relinking_oldVsNew", inp, exp);
    }

    out.write();
    std::printf("crosscurrency_probe: wrote references/experimental/crosscurrency.json\n");
    return 0;
}
