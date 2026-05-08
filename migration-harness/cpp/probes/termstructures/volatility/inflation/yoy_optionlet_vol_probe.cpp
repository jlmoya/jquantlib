// migration-harness/cpp/probes/termstructures/volatility/inflation/yoy_optionlet_vol_probe.cpp
// Reference values for YoY-inflation optionlet volatility surfaces.
// Phase 2r Track B — vol structures (QuantLib v1.42.1).
//
// Two scenario groups:
//   C_*: ConstantYoYOptionletVolatility — flat surface, no T or K dependence.
//   I_*: InterpolatedYoYOptionletVolatilityCurve<Linear> — T-interpolated,
//        flat in K. Validates volatility(date,strike) and totalVariance(date,
//        strike) against the linear interpolation of (date,vol) pillars,
//        plus baseDate / baseLevel / maxDate / observationLag inspectors.
//
// The C++ class lives under ql/experimental/inflation/.

#include <cstdio>
#include <ql/version.hpp>
#include "../../../common.hpp"

#include <ql/experimental/inflation/yoyinflationoptionletvolatilitystructure2.hpp>
#include <ql/math/interpolations/linearinterpolation.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/inflationtermstructure.hpp>
#include <ql/termstructures/volatility/inflation/yoyinflationoptionletvolatilitystructure.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("termstructures/volatility/inflation/yoy_optionlet_vol",
                        QL_VERSION, "yoy_optionlet_vol_probe");

    // ---------- Common setup ----------
    Date evalDate(13, August, 2007);
    Settings::instance().evaluationDate() = evalDate;

    Calendar calendar = TARGET();
    BusinessDayConvention bdc = ModifiedFollowing;
    DayCounter dc = Actual365Fixed();
    Period observationLag(2, Months);
    Frequency freq = Monthly;
    Natural settlementDays = 0;

    // -----------------------------------------------------------------------
    // Scenario C — ConstantYoYOptionletVolatility
    //   indexIsInterpolated = false (period-snapped path).
    // -----------------------------------------------------------------------
    {
        const Volatility v = 0.18;
        ConstantYoYOptionletVolatility constVol(
            v, settlementDays, calendar, bdc, dc,
            observationLag, freq, /*indexIsInterpolated*/ false);

        json inp{{"scenario","C"},{"v_input", v},
                 {"observationLag_months", 2},
                 {"frequency","Monthly"},
                 {"indexIsInterpolated", false},
                 {"dayCounter","Actual365Fixed"}};

        out.addCase("C_referenceDate_serial", inp,
                    json{{"value", constVol.referenceDate().serialNumber()}});
        out.addCase("C_baseDate_serial", inp,
                    json{{"value", constVol.baseDate().serialNumber()}});
        out.addCase("C_minStrike", inp, json{{"value", constVol.minStrike()}});
        out.addCase("C_maxStrike", inp, json{{"value", constVol.maxStrike()}});
        out.addCase("C_observationLag_days", inp,
                    json{{"value", static_cast<int>(
                        constVol.observationLag().length())}});
        out.addCase("C_frequency_int", inp,
                    json{{"value", static_cast<int>(constVol.frequency())}});
        out.addCase("C_indexIsInterpolated", inp,
                    json{{"value", constVol.indexIsInterpolated()}});

        // Probe volatility & totalVariance on a tenor x strike grid.
        Period tenors[] = {
            Period(1, Years), Period(2, Years), Period(5, Years), Period(10, Years)
        };
        Rate strikes[] = { -0.02, 0.0, 0.02, 0.04, 0.06 };

        for (size_t i = 0; i < sizeof(tenors)/sizeof(Period); ++i) {
            for (size_t j = 0; j < sizeof(strikes)/sizeof(Rate); ++j) {
                char nm[64];
                std::snprintf(nm, sizeof(nm),
                              "C_volatility_t%zu_k%zu", i, j);
                Date matDate = constVol.optionDateFromTenor(tenors[i]);
                json ki{{"tenor_years", tenors[i].length()},
                        {"strike", strikes[j]},
                        {"matDate_serial", matDate.serialNumber()}};
                out.addCase(nm, ki, json{{"value",
                            constVol.volatility(matDate, strikes[j])}});

                std::snprintf(nm, sizeof(nm),
                              "C_totalVariance_t%zu_k%zu", i, j);
                out.addCase(nm, ki, json{{"value",
                            constVol.totalVariance(matDate, strikes[j])}});
            }
        }
    }

    // -----------------------------------------------------------------------
    // Scenario I — InterpolatedYoYOptionletVolatilityCurve<Linear>
    //   pillar (date, vol) grid, indexIsInterpolated = true (no period-snap).
    //   Smile is flat — strike is ignored at the surface lookup step.
    // -----------------------------------------------------------------------
    {
        // Build a simple piecewise-linear vol curve. Pillars start at
        // refDate - observationLag so that volatility queries at any
        // tenor >= 0M (after lag-shift) land within the interpolation
        // range — the inner Interpolation does not honor the surface's
        // extrapolation flag (C++ behaviour mirror, line 152 of the .hpp
        // shows setBaseLevel passes true to the interpolation explicitly).
        Date refDate = calendar.adjust(evalDate, bdc);
        std::vector<Date> dates = {
            refDate - observationLag,
            refDate + Period(1, Years),
            refDate + Period(2, Years),
            refDate + Period(5, Years),
            refDate + Period(10, Years),
            refDate + Period(20, Years)
        };
        std::vector<Volatility> vols = { 0.14, 0.15, 0.17, 0.20, 0.23, 0.26 };
        Rate minStrike = -0.10;
        Rate maxStrike = 0.50;

        InterpolatedYoYOptionletVolatilityCurve<Linear> curve(
            settlementDays, calendar, bdc, dc,
            observationLag, freq, /*indexIsInterpolated*/ true,
            dates, vols, minStrike, maxStrike, Linear());
        curve.enableExtrapolation();

        json inp{{"scenario","I"},
                 {"observationLag_months", 2},
                 {"frequency","Monthly"},
                 {"indexIsInterpolated", true},
                 {"dayCounter","Actual365Fixed"},
                 {"interpolator","Linear"}};

        out.addCase("I_referenceDate_serial", inp,
                    json{{"value", curve.referenceDate().serialNumber()}});
        out.addCase("I_baseDate_serial", inp,
                    json{{"value", curve.baseDate().serialNumber()}});
        out.addCase("I_baseLevel", inp,
                    json{{"value", curve.baseLevel()}});
        out.addCase("I_minStrike", inp, json{{"value", curve.minStrike()}});
        out.addCase("I_maxStrike", inp, json{{"value", curve.maxStrike()}});
        out.addCase("I_observationLag_months", inp,
                    json{{"value", static_cast<int>(
                        curve.observationLag().length())}});
        out.addCase("I_frequency_int", inp,
                    json{{"value", static_cast<int>(curve.frequency())}});

        // Pillar dates serials and vols (round-trip inspectors)
        json ds = json::array();
        for (const Date& d : curve.dates()) ds.push_back(d.serialNumber());
        out.addCase("I_dates_serials", inp, json{{"values", ds}});

        json ts = json::array();
        for (Time t : curve.times()) ts.push_back(t);
        out.addCase("I_times", inp, json{{"values", ts}});

        json ds_vols = json::array();
        for (Real v : curve.data()) ds_vols.push_back(v);
        out.addCase("I_data", inp, json{{"values", ds_vols}});

        // Probe volatility & totalVariance on a tenor x strike grid.
        // Use date directly (matDate = refDate + tenor) instead of
        // optionDateFromTenor to make the Java equivalent simple to produce.
        Period tenors[] = {
            Period(1, Years), Period(2, Years), Period(5, Years), Period(10, Years)
        };
        Rate strikes[] = { -0.02, 0.0, 0.02, 0.04, 0.06 };

        for (size_t i = 0; i < sizeof(tenors)/sizeof(Period); ++i) {
            for (size_t j = 0; j < sizeof(strikes)/sizeof(Rate); ++j) {
                char nm[64];
                Date matDate = curve.optionDateFromTenor(tenors[i]);
                json ki{{"tenor_years", tenors[i].length()},
                        {"strike", strikes[j]},
                        {"matDate_serial", matDate.serialNumber()}};

                std::snprintf(nm, sizeof(nm),
                              "I_volatility_t%zu_k%zu", i, j);
                out.addCase(nm, ki, json{{"value",
                            curve.volatility(matDate, strikes[j])}});

                std::snprintf(nm, sizeof(nm),
                              "I_totalVariance_t%zu_k%zu", i, j);
                out.addCase(nm, ki, json{{"value",
                            curve.totalVariance(matDate, strikes[j])}});
            }
        }

        // Probe inter-pillar dates (mid-tenors) to exercise interpolation.
        Period interTenors[] = {
            Period(18, Months), Period(3, Years), Period(7, Years), Period(15, Years)
        };
        for (size_t i = 0; i < sizeof(interTenors)/sizeof(Period); ++i) {
            char nm[64];
            Date matDate = curve.optionDateFromTenor(interTenors[i]);
            json ki{{"tenor_months", interTenors[i].length()},
                    {"matDate_serial", matDate.serialNumber()}};
            std::snprintf(nm, sizeof(nm), "I_volatility_inter_%zu", i);
            out.addCase(nm, ki, json{{"value",
                        curve.volatility(matDate, 0.02)}});
            std::snprintf(nm, sizeof(nm), "I_totalVariance_inter_%zu", i);
            out.addCase(nm, ki, json{{"value",
                        curve.totalVariance(matDate, 0.02)}});
        }
    }

    out.write();
    return 0;
}
