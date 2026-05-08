// migration-harness/cpp/probes/experimental/inflation/cpi_cap_floor_term_price_surface_probe.cpp
// Reference values for InterpolatedCPICapFloorTermPriceSurface (ql/experimental/inflation/cpicapfloortermpricesurface.hpp).
//
// Replicates the UK RPI fixture from test-suite/inflationcpicapfloor.cpp (cpicapfloorpricesurface).
// Uses Bilinear 2D interpolator. Captures direct cap/floor prices at grid points
// and interior points + the price() function which picks cap/floor by ATM.

#include <ql/version.hpp>
#include <ql/experimental/inflation/cpicapfloortermpricesurface.hpp>
#include <ql/indexes/inflation/ukrpi.hpp>
#include <ql/termstructures/inflation/piecewisezeroinflationcurve.hpp>
#include <ql/termstructures/inflation/inflationhelpers.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/math/interpolations/bilinearinterpolation.hpp>
#include <ql/math/interpolations/linearinterpolation.hpp>
#include <ql/time/calendars/unitedkingdom.hpp>
#include <ql/time/daycounters/actualactual.hpp>
#include <ql/time/calendars/target.hpp>
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
    ReferenceWriter out("experimental/inflation/cpi_cap_floor_term_price_surface",
                        QL_VERSION,
                        "cpi_cap_floor_term_price_surface_probe");

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

    // UK RPI index fixing data
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

    // Nominal yield curve
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

    // Replaced: C++ test uses InterpolatedZeroCurve<Linear>(nomD, nomR, ...)
    // but Java's InterpolatedZeroCurve has a divergent constructor (requires
    // yields[0] == 1.0, treating data as discount factors). To get a clean
    // cross-validation, both probe and Java test use FlatForward at 5% (constant
    // nominal rate). This is independent of surface arithmetic and lets us
    // verify the surface math without touching Java's yield-curve port.
    (void) nominalData;
    auto nominalTS = ext::make_shared<FlatForward>(
        evaluationDate, 0.05, dcNominal,
        Continuous, Annual);
    RelinkableHandle<YieldTermStructure> nominalUK;
    nominalUK.linkTo(nominalTS);

    // Build zero inflation curve
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

    // ===========================================================
    // Cap/Floor surface data
    // ===========================================================
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
    InterpolatedCPICapFloorTermPriceSurface<Bilinear> surf(
        nominal, baseZeroRate, observationLag, calendar,
        convention, dcZCIIS, ii, CPI::Flat, nominalUK,
        cStrikes, fStrikes, cfMaturities, cPrice, fPrice);

    // ===========================================================
    // Generate cases
    // ===========================================================
    json setup = {
        {"evaluation_date", "2010-06-01"},
        {"observation_lag_months", 2},
        {"interpolation", "Flat"},
        {"interpolator2d", "Bilinear"},
        {"nominal", nominal},
        {"baseRate", baseZeroRate}
    };

    // Grid-point reproduction: cap prices at maturity j and cap strike i
    // should equal cPrice[i][j] (the input data)
    for (Size i = 0; i < cStrikes.size(); i++) {
        for (Size j = 0; j < cfMaturities.size(); j++) {
            char nm[64];
            snprintf(nm, sizeof(nm), "cap_grid_s%zu_t%zu", i, j);
            json inputs = setup;
            inputs["strike"] = cStrikes[i];
            inputs["maturity_period_years"] = cfMaturities[j].length();
            Real v = surf.capPrice(cfMaturities[j], cStrikes[i]);
            out.addCase(nm, inputs, v);
        }
    }

    for (Size i = 0; i < fStrikes.size(); i++) {
        for (Size j = 0; j < cfMaturities.size(); j++) {
            char nm[64];
            snprintf(nm, sizeof(nm), "floor_grid_s%zu_t%zu", i, j);
            json inputs = setup;
            inputs["strike"] = fStrikes[i];
            inputs["maturity_period_years"] = cfMaturities[j].length();
            Real v = surf.floorPrice(cfMaturities[j], fStrikes[i]);
            out.addCase(nm, inputs, v);
        }
    }

    // Interior interpolation cases
    {
        json inputs = setup;
        inputs["strike"] = 0.025;
        inputs["maturity_period_years"] = 5;
        Real v = surf.capPrice(5 * Years, 0.025);
        out.addCase("cap_interior_s0025_t5y", inputs, v);
    }
    {
        json inputs = setup;
        inputs["strike"] = 0.005;
        inputs["maturity_period_years"] = 7;
        Real v = surf.floorPrice(7 * Years, 0.005);
        out.addCase("floor_interior_s0005_t7y", inputs, v);
    }
    {
        json inputs = setup;
        inputs["strike"] = 0.045;
        inputs["maturity_period_years"] = 10;
        Real v = surf.capPrice(10 * Years, 0.045);
        out.addCase("cap_interior_s0045_t10y", inputs, v);
    }
    {
        json inputs = setup;
        inputs["strike"] = 0.015;
        inputs["maturity_period_years"] = 15;
        Real v = surf.floorPrice(15 * Years, 0.015);
        out.addCase("floor_interior_s0015_t15y", inputs, v);
    }

    // price() picks cap or floor by ATM (1% < ATM ⇒ floor at 1%, 3y ⇒ 53.61 bps)
    {
        json inputs = setup;
        inputs["strike"] = 0.01;
        inputs["maturity_period_years"] = 3;
        Real v = surf.price(3 * Years, 0.01);
        out.addCase("price_floor_3y_s001", inputs, v);
    }

    // ATM rate at 5y
    {
        json inputs = setup;
        inputs["maturity_period_years"] = 5;
        Date d = surf.cpiOptionDateFromTenor(5 * Years);
        Real v = surf.atmRate(d);
        out.addCase("atm_rate_5y", inputs, v);
    }

    // strikes() and maturities() metadata
    {
        json inputs = setup;
        inputs["query"] = "metadata";
        json expected = {
            {"min_strike", surf.minStrike()},
            {"max_strike", surf.maxStrike()},
            {"num_strikes", static_cast<int>(surf.strikes().size())},
            {"observation_lag_months", surf.observationLag().length()}
        };
        out.addCase("metadata", inputs, expected);
    }

    out.write();
    return 0;
}
