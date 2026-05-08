// migration-harness/cpp/probes/experimental/inflation/cpi_cap_floor_engines_probe.cpp
// Reference values for InterpolatingCPICapFloorEngine (ql/experimental/inflation/cpicapfloorengines.hpp).
//
// Replicates the UK RPI fixture from test-suite/inflationcpicapfloor.cpp (cpicapfloorpricer).
// Builds a CPICapFloor instrument, prices via InterpolatingCPICapFloorEngine.
// Captures resulting NPVs.

#include <ql/version.hpp>
#include <ql/experimental/inflation/cpicapfloorengines.hpp>
#include <ql/experimental/inflation/cpicapfloortermpricesurface.hpp>
#include <ql/instruments/cpicapfloor.hpp>
#include <ql/indexes/inflation/ukrpi.hpp>
#include <ql/termstructures/inflation/piecewisezeroinflationcurve.hpp>
#include <ql/termstructures/inflation/inflationhelpers.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/math/interpolations/bilinearinterpolation.hpp>
#include <ql/math/interpolations/linearinterpolation.hpp>
#include <ql/time/calendars/unitedkingdom.hpp>
#include <ql/time/daycounters/actualactual.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/instruments/zerocouponinflationswap.hpp>
#include <ql/time/schedule.hpp>
#include "../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

template <class T, class U, class I>
std::vector<ext::shared_ptr<BootstrapHelper<T> > > makeHelpers(
        std::pair<Date, Rate>* iiData, Size N,
        const ext::shared_ptr<I>& ii, const Period& observationLag,
        const Calendar& calendar,
        const BusinessDayConvention& bdc,
        const DayCounter& dc) {

    std::vector<ext::shared_ptr<BootstrapHelper<T> > > instruments;
    for (Size i = 0; i < N; i++) {
        Date maturity = iiData[i].first;
        Handle<Quote> quote(ext::shared_ptr<Quote>(
                                new SimpleQuote(iiData[i].second / 100.0)));
        auto inst = ext::make_shared<U>(quote, observationLag, maturity,
                                         calendar, bdc, dc, ii, CPI::AsIndex);
        instruments.push_back(inst);
    }
    return instruments;
}

int main() {
    ReferenceWriter out("experimental/inflation/cpi_cap_floor_engines",
                        QL_VERSION,
                        "cpi_cap_floor_engines_probe");

    // ===========================================================
    // Setup mirroring test-suite/inflationcpicapfloor.cpp::CommonVars
    // ===========================================================
    Calendar calendar = UnitedKingdom();
    BusinessDayConvention convention = ModifiedFollowing;
    Date today(1, June, 2010);
    Date evaluationDate = calendar.adjust(today);
    Settings::instance().evaluationDate() = evaluationDate;
    DayCounter dcZCIIS = ActualActual(ActualActual::ISDA);
    DayCounter dcNominal = ActualActual(ActualActual::ISDA);

    Schedule rpiSchedule = MakeSchedule()
        .from(Date(1, July, 2007))
        .to(Date(1, April, 2010))
        .withFrequency(Monthly);
    Real fixData[] = {
        206.1, 207.3, 208.0, 208.9, 209.7, 210.9,
        209.8, 211.4, 212.1, 214.0, 215.1, 216.8,
        216.5, 217.2, 218.4, 217.7, 216.0, 212.9,
        210.1, 211.4, 211.3, 211.5, 212.8, 213.4,
        213.4, 214.4, 215.3, 216.0, 216.6, 218.0,
        217.9, 219.2, 220.7, 222.8
    };

    RelinkableHandle<ZeroInflationTermStructure> hcpi;
    auto ii = ext::make_shared<UKRPI>(hcpi);
    for (Size i = 0; i < rpiSchedule.size(); i++) {
        ii->addFixing(rpiSchedule[i], fixData[i], true);
    }

    std::pair<Date, Rate> nominalData[] = {
        {Date(2, June, 2010), 0.499997},
        {Date(3, June, 2010), 0.524992},
        {Date(8, June, 2010), 0.524974},
        {Date(15, June, 2010), 0.549942},
        {Date(22, June, 2010), 0.549913},
        {Date(1, July, 2010), 0.574864},
        {Date(2, August, 2010), 0.624668},
        {Date(1, September, 2010), 0.724338},
        {Date(16, September, 2010), 0.769461},
        {Date(1, December, 2010), 0.997501},
        {Date(17, March, 2011), 0.916996},
        {Date(16, June, 2011), 0.984339},
        {Date(22, September, 2011), 1.06085},
        {Date(22, December, 2011), 1.141788},
        {Date(1, June, 2012), 1.504426},
        {Date(3, June, 2013), 1.92064},
        {Date(2, June, 2014), 2.290824},
        {Date(1, June, 2015), 2.614394},
        {Date(1, June, 2016), 2.887445},
        {Date(1, June, 2017), 3.122128},
        {Date(1, June, 2018), 3.322511},
        {Date(3, June, 2019), 3.483997},
        {Date(1, June, 2020), 3.616896},
        {Date(1, June, 2022), 3.8281},
        {Date(2, June, 2025), 4.0341},
        {Date(3, June, 2030), 4.070854},
        {Date(1, June, 2035), 4.023202},
        {Date(1, June, 2040), 3.954748},
        {Date(1, June, 2050), 3.870953},
        {Date(1, June, 2060), 3.85298},
        {Date(2, June, 2070), 3.757542},
        {Date(3, June, 2080), 3.651379}
    };

    (void) nominalData;
    auto nominalTS = ext::make_shared<FlatForward>(
        evaluationDate, 0.05, dcNominal,
        Continuous, Annual);
    RelinkableHandle<YieldTermStructure> nominalUK;
    nominalUK.linkTo(nominalTS);

    Period observationLag = Period(2, Months);

    std::pair<Date, Rate> zciisData[] = {
        {Date(1, June, 2011), 3.087},
        {Date(1, June, 2012), 3.12},
        {Date(1, June, 2013), 3.059},
        {Date(1, June, 2014), 3.11},
        {Date(1, June, 2015), 3.15},
        {Date(1, June, 2016), 3.207},
        {Date(1, June, 2017), 3.253},
        {Date(1, June, 2018), 3.288},
        {Date(1, June, 2019), 3.314},
        {Date(1, June, 2020), 3.401},
        {Date(1, June, 2022), 3.458},
        {Date(1, June, 2025), 3.52},
        {Date(1, June, 2030), 3.655},
        {Date(1, June, 2035), 3.668},
        {Date(1, June, 2040), 3.695},
        {Date(1, June, 2050), 3.634},
        {Date(1, June, 2060), 3.629}
    };
    Size zciisDataLength = 17;

    auto helpers = makeHelpers<ZeroInflationTermStructure, ZeroCouponInflationSwapHelper,
                                ZeroInflationIndex>(zciisData, zciisDataLength, ii,
                                                    observationLag,
                                                    calendar, convention, dcZCIIS);

    Rate baseZeroRate = zciisData[0].second / 100.0;
    Date baseDate = ii->lastFixingDate();
    auto pCPIts = ext::make_shared<PiecewiseZeroInflationCurve<Linear>>(
        evaluationDate, baseDate, ii->frequency(), dcZCIIS, helpers);
    pCPIts->recalculate();
    hcpi.linkTo(pCPIts);

    // Cap/floor surface (same as in surface probe)
    std::vector<Period> cfMaturities = {3 * Years, 5 * Years, 7 * Years,
                                          10 * Years, 15 * Years, 20 * Years, 30 * Years};
    std::vector<Rate> cStrikes = {0.03, 0.04, 0.05, 0.06};
    std::vector<Rate> fStrikes = {-0.01, 0, 0.01, 0.02};
    Size ncStrikes = 4, nfStrikes = 4, ncfMaturities = 7;

    Real cPriceData[7][4] = {
        {227.6, 100.27, 38.8, 14.94},
        {345.32, 127.9, 40.59, 14.11},
        {477.95, 170.19, 50.62, 16.88},
        {757.81, 303.95, 107.62, 43.61},
        {1140.73, 481.89, 168.4, 63.65},
        {1537.6, 607.72, 172.27, 54.87},
        {2211.67, 839.24, 184.75, 45.03}
    };
    Real fPriceData[7][4] = {
        {15.62, 28.38, 53.61, 104.6},
        {21.45, 36.73, 66.66, 129.6},
        {24.45, 42.08, 77.04, 152.24},
        {39.25, 63.52, 109.2, 203.44},
        {36.82, 63.62, 116.97, 232.73},
        {39.7, 67.47, 121.79, 238.56},
        {41.48, 73.9, 139.75, 286.75}
    };

    Matrix cPrice(ncStrikes, ncfMaturities);
    Matrix fPrice(nfStrikes, ncfMaturities);
    for (Size i = 0; i < ncStrikes; i++)
        for (Size j = 0; j < ncfMaturities; j++)
            cPrice[i][j] = cPriceData[j][i] / 10000.0;
    for (Size i = 0; i < nfStrikes; i++)
        for (Size j = 0; j < ncfMaturities; j++)
            fPrice[i][j] = fPriceData[j][i] / 10000.0;

    Real nominal = 1.0;
    auto cpiCFpriceSurf = ext::make_shared<InterpolatedCPICapFloorTermPriceSurface<Bilinear>>(
        nominal, baseZeroRate, observationLag, calendar,
        convention, dcZCIIS, ii, CPI::Flat, nominalUK,
        cStrikes, fStrikes, cfMaturities, cPrice, fPrice);

    // Build a CPICapFloor instrument and price it via the engine
    Date startDate = Settings::instance().evaluationDate();
    Calendar fixCalendar = UnitedKingdom(), payCalendar = UnitedKingdom();
    BusinessDayConvention fixConvention(Unadjusted), payConvention(ModifiedFollowing);
    CPI::InterpolationType observationInterpolation = CPI::AsIndex;
    Real baseCPI = CPI::laggedFixing(ii, startDate, observationLag, observationInterpolation);

    Handle<CPICapFloorTermPriceSurface> cpiCFsurfUKh(cpiCFpriceSurf);
    auto engine = ext::make_shared<InterpolatingCPICapFloorEngine>(cpiCFsurfUKh);

    json setup = {
        {"evaluation_date", "2010-06-01"},
        {"surface_observation_lag_months", 2},
        {"observation_interpolation", "AsIndex"},
        {"engine", "InterpolatingCPICapFloorEngine"},
        {"baseCPI", baseCPI}
    };

    // Test cases: vary type / strike / maturity. C++ test asserts that the
    // 3y cap at 0.03 strike returns 227.6 bps (cPrice[0][0])
    struct Spec { const char* name; Option::Type type; Rate strike; Period mat; };
    std::vector<Spec> specs = {
        {"cap_3y_s003",   Option::Call, 0.03, 3 * Years},
        {"cap_5y_s003",   Option::Call, 0.03, 5 * Years},
        {"cap_7y_s004",   Option::Call, 0.04, 7 * Years},
        {"cap_10y_s005",  Option::Call, 0.05, 10 * Years},
        {"floor_3y_s001", Option::Put,  0.01, 3 * Years},
        {"floor_5y_s000", Option::Put,  0.00, 5 * Years},
        {"floor_7y_sm001",Option::Put, -0.01, 7 * Years},
        {"floor_15y_s002",Option::Put,  0.02, 15 * Years},
    };

    for (auto& s : specs) {
        Date maturity = startDate + s.mat;
        CPICapFloor cap(s.type,
                        nominal,
                        startDate,
                        baseCPI,
                        maturity,
                        fixCalendar,
                        fixConvention,
                        payCalendar,
                        payConvention,
                        s.strike,
                        ii,
                        observationLag,
                        observationInterpolation);
        cap.setPricingEngine(engine);
        Real npv = cap.NPV();

        json inputs = setup;
        inputs["type"] = (s.type == Option::Call) ? "Call" : "Put";
        inputs["strike"] = s.strike;
        inputs["maturity_years"] = s.mat.length();
        out.addCase(s.name, inputs, npv);
    }

    out.write();
    return 0;
}
