// migration-harness/cpp/probes/experimental/inflation/yoy_cap_floor_term_price_surface_probe.cpp
// Reference values for InterpolatedYoYCapFloorTermPriceSurface (ql/experimental/inflation/yoycapfloortermpricesurface.hpp).
//
// Replicates the EU YoY surface fixture from test-suite/inflationvolatility.cpp.
// Uses Bicubic 2D interpolator and Cubic 1D interpolator (same as C++ test).
// Captures direct cap/floor prices at grid points + ATM YoY swap rates.

#include <ql/version.hpp>
#include <ql/experimental/inflation/yoycapfloortermpricesurface.hpp>
#include <ql/indexes/inflation/euhicp.hpp>
#include <ql/termstructures/inflation/interpolatedyoyinflationcurve.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/math/interpolations/bicubicsplineinterpolation.hpp>
#include <ql/math/interpolations/cubicinterpolation.hpp>
#include <ql/math/interpolations/linearinterpolation.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/calendars/target.hpp>
#include "../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("experimental/inflation/yoy_cap_floor_term_price_surface",
                        QL_VERSION,
                        "yoy_cap_floor_term_price_surface_probe");

    // Eval date
    Date eval = Date(23, November, 2007);
    Settings::instance().evaluationDate() = eval;

    RelinkableHandle<YoYInflationTermStructure> yoyEU;
    auto yoyIndexEU = ext::make_shared<YoYInflationIndex>(
        ext::make_shared<EUHICP>(), yoyEU);

    // Nominal yield curve (interpolated)
    Real timesEUR[] = {0.0109589, 0.0684932, 0.263014, 0.317808, 0.567123, 0.816438,
                       1.06575, 1.31507, 1.56438, 2.0137, 3.01918, 4.01644,
                       5.01644, 6.01644, 7.01644, 8.01644, 9.02192, 10.0192,
                       12.0192, 15.0247, 20.0301, 25.0356, 30.0329, 40.0384,
                       50.0466};
    Real ratesEUR[] = {0.0415600, 0.0426840, 0.0470980, 0.0458506, 0.0449550, 0.0439784,
                       0.0431887, 0.0426604, 0.0422925, 0.0424591, 0.0421477, 0.0421853,
                       0.0424016, 0.0426969, 0.0430804, 0.0435011, 0.0439368, 0.0443825,
                       0.0452589, 0.0463389, 0.0472636, 0.0473401, 0.0470629, 0.0461092,
                       0.0450794};

    (void) timesEUR;
    (void) ratesEUR;
    // Use FlatForward 4.5% (averages the EU yield curve closely; what matters
    // here is reproducibility between C++ and Java probes/tests).
    auto euriborTS = ext::make_shared<FlatForward>(
        eval, 0.045, Actual365Fixed(),
        Continuous, Annual);
    Handle<YieldTermStructure> nominalEUR(euriborTS, false);

    // YoY rates curve
    Real yoyEUrates[] = {0.0237951,
                         0.0238749, 0.0240334, 0.0241934, 0.0243567, 0.0245323,
                         0.0247213, 0.0249348, 0.0251768, 0.0254337, 0.0257258,
                         0.0260217, 0.0263006, 0.0265538, 0.0267803, 0.0269378,
                         0.0270608, 0.0271363, 0.0272, 0.0272512, 0.0272927,
                         0.027317, 0.0273615, 0.0273811, 0.0274063, 0.0274307,
                         0.0274625, 0.027527, 0.0275952, 0.0276734, 0.027794};

    std::vector<Date> d;
    std::vector<Real> r;
    Date baseDate = inflationPeriod(eval - 1 * Months, yoyIndexEU->frequency()).first;
    d.push_back(baseDate);
    r.push_back(yoyEUrates[0]);
    Date capStartDate = TARGET().advance(eval, -2, Months, ModifiedFollowing);
    for (Size i = 1; i < std::size(yoyEUrates); i++) {
        Date dd = TARGET().advance(capStartDate, i, Years, ModifiedFollowing);
        d.push_back(dd);
        r.push_back(yoyEUrates[i]);
    }
    auto pYTSEU = ext::make_shared<InterpolatedYoYInflationCurve<Linear>>(
        eval, d, r, Monthly, Actual365Fixed());
    yoyEU.linkTo(pYTSEU);

    // Cap/floor data
    const Size ncStrikesEU = 6;
    const Size nfStrikesEU = 6;
    const Size ncfMaturitiesEU = 7;
    Real capStrikesEU[ncStrikesEU] = {0.02, 0.025, 0.03, 0.035, 0.04, 0.05};
    Period capMaturitiesEU[ncfMaturitiesEU] = {3 * Years, 5 * Years, 7 * Years,
                                                10 * Years, 15 * Years, 20 * Years, 30 * Years};
    Real capPricesEU[ncStrikesEU][ncfMaturitiesEU] =
        {{116.225, 204.945, 296.285, 434.29, 654.47, 844.775, 1132.33},
         {34.305, 71.575, 114.1, 184.33, 307.595, 421.395, 602.35},
         {6.37, 19.085, 35.635, 66.42, 127.69, 189.685, 296.195},
         {1.325, 5.745, 12.585, 26.945, 58.95, 94.08, 158.985},
         {0.501, 2.37, 5.38, 13.065, 31.91, 53.95, 96.97},
         {0.501, 0.695, 1.47, 4.415, 12.86, 23.75, 46.7}};

    Real floorStrikesEU[nfStrikesEU] = {-0.01, 0.00, 0.005, 0.01, 0.015, 0.02};
    Real floorPricesEU[nfStrikesEU][ncfMaturitiesEU] =
        {{0.501, 0.851, 2.44, 6.645, 16.23, 26.85, 46.365},
         {0.501, 2.236, 5.555, 13.075, 28.46, 44.525, 73.08},
         {1.025, 3.935, 9.095, 19.64, 39.93, 60.375, 96.02},
         {2.465, 7.885, 16.155, 31.6, 59.34, 86.21, 132.045},
         {6.9, 17.92, 32.085, 56.08, 95.95, 132.85, 194.18},
         {23.52, 47.625, 74.085, 114.355, 175.72, 229.565, 316.285}};

    std::vector<Rate> cStrikes;
    std::vector<Rate> fStrikes;
    std::vector<Period> cfMaturities;
    for (Real& i : capStrikesEU) cStrikes.push_back(i);
    for (Real& i : floorStrikesEU) fStrikes.push_back(i);
    for (auto& i : capMaturitiesEU) cfMaturities.push_back(i);

    Matrix cPrice(ncStrikesEU, ncfMaturitiesEU);
    Matrix fPrice(nfStrikesEU, ncfMaturitiesEU);
    for (Size i = 0; i < ncStrikesEU; i++) {
        for (Size j = 0; j < ncfMaturitiesEU; j++) {
            cPrice[i][j] = capPricesEU[i][j];
        }
    }
    for (Size i = 0; i < nfStrikesEU; i++) {
        for (Size j = 0; j < ncfMaturitiesEU; j++) {
            fPrice[i][j] = floorPricesEU[i][j];
        }
    }

    Natural fixingDays = 0;
    Period yyLag = Period(3, Months);
    DayCounter dc = Actual365Fixed();
    TARGET cal;
    BusinessDayConvention bdc = ModifiedFollowing;
    const ext::shared_ptr<QuantLib::YieldTermStructure>& pn = nominalEUR.currentLink();
    Handle<QuantLib::YieldTermStructure> n(pn, false);

    InterpolatedYoYCapFloorTermPriceSurface<Bicubic, Cubic> surf(
        fixingDays, yyLag, yoyIndexEU, CPI::Linear, n, dc, cal, bdc,
        cStrikes, fStrikes, cfMaturities, cPrice, fPrice);

    // Cases
    json setup = {
        {"evaluation_date", "2007-11-23"},
        {"observation_lag_months", 3},
        {"interpolation", "Linear"},
        {"interpolator2d", "Bicubic"},
        {"interpolator1d", "Cubic"}
    };

    // Grid points: cap prices (rows=strikes, cols=maturities)
    // Use Date variant directly because the Period overload is hidden by override.
    for (Size i = 0; i < cStrikes.size(); i++) {
        for (Size j = 0; j < cfMaturities.size(); j++) {
            char nm[64];
            snprintf(nm, sizeof(nm), "cap_grid_s%zu_t%zu", i, j);
            json inputs = setup;
            inputs["strike"] = cStrikes[i];
            inputs["maturity_period_years"] = cfMaturities[j].length();
            Date matDate = surf.yoyOptionDateFromTenor(cfMaturities[j]);
            Real v = surf.capPrice(matDate, cStrikes[i]);
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
            Date matDate = surf.yoyOptionDateFromTenor(cfMaturities[j]);
            Real v = surf.floorPrice(matDate, fStrikes[i]);
            out.addCase(nm, inputs, v);
        }
    }

    // ATM YoY swap rates at maturities (computed from put/call parity intersection)
    auto atmRates = surf.atmYoYSwapTimeRates();
    for (Size i = 0; i < atmRates.first.size(); i++) {
        char nm[64];
        snprintf(nm, sizeof(nm), "atm_swap_rate_t%zu", i);
        json inputs = setup;
        inputs["t"] = atmRates.first[i];
        out.addCase(nm, inputs, atmRates.second[i]);
    }

    // Metadata
    json mInputs = setup;
    mInputs["query"] = "metadata";
    json expected = {
        {"min_strike", surf.minStrike()},
        {"max_strike", surf.maxStrike()},
        {"num_strikes", static_cast<int>(surf.strikes().size())},
        {"observation_lag_months", surf.observationLag().length()}
    };
    out.addCase("metadata", mInputs, expected);

    out.write();
    return 0;
}
