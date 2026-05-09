// migration-harness/cpp/probes/termstructures/volatility/capfloor_term_vol_probe.cpp
// Reference values for CapFloorTermVolCurve and CapFloorTermVolSurface.
// Phase 5f.5 production port.
//
// Curve uses CubicInterpolation::Spline + SecondDerivative natural BC.
// Surface uses Bilinear (with extrapolation enabled).

#include <cstdio>
#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/termstructures/volatility/capfloor/capfloortermvolcurve.hpp>
#include <ql/termstructures/volatility/capfloor/capfloortermvolsurface.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/math/matrix.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("termstructures/volatility/capfloor_term_vol",
                        QL_VERSION, "capfloor_term_vol_probe");

    DayCounter dc = Actual365Fixed();
    Calendar cal = TARGET();
    BusinessDayConvention bdc = Following;
    Date refDate(2, January, 2020);

    // -------------------------------------------------------------------------
    // Scenario A: CapFloorTermVolCurve, fixed reference date + fixed market data.
    // option tenors: 1Y, 2Y, 3Y, 5Y, 7Y, 10Y
    // vols:          0.18, 0.17, 0.16, 0.15, 0.145, 0.14
    // -------------------------------------------------------------------------
    {
        std::vector<Period> optionT = {
            1*Years, 2*Years, 3*Years, 5*Years, 7*Years, 10*Years};
        std::vector<Volatility> vols = {0.18, 0.17, 0.16, 0.15, 0.145, 0.14};

        CapFloorTermVolCurve curve(refDate, cal, bdc, optionT, vols, dc);

        json inp{{"scenario","curve_A"},{"nTenors",6}};

        out.addCase("curve_A_maxDate_serial", inp,
                    json{{"value", (long)curve.maxDate().serialNumber()}});
        out.addCase("curve_A_minStrike", inp, json{{"value", curve.minStrike()}});
        out.addCase("curve_A_maxStrike", inp, json{{"value", curve.maxStrike()}});

        for (size_t i = 0; i < 6; ++i) {
            char ibuf[8];
            std::snprintf(ibuf, sizeof(ibuf), "%d", (int)i);
            out.addCase(std::string("curve_A_optionTime_i") + ibuf, inp,
                        json{{"value", curve.optionTimes()[i]}});
            out.addCase(std::string("curve_A_optionDate_serial_i") + ibuf, inp,
                        json{{"value", (long)curve.optionDates()[i].serialNumber()}});
            // At node: vol must equal the input
            const double t = curve.optionTimes()[i];
            out.addCase(std::string("curve_A_vol_node_i") + ibuf,
                        json{{"i",(int)i},{"t",t}},
                        json{{"value", curve.volatility(t, 0.05)}});
        }

        // Cubic-spline interior probes between nodes
        for (size_t i = 0; i + 1 < 6; ++i) {
            const double t = (curve.optionTimes()[i] + curve.optionTimes()[i+1]) * 0.5;
            char ibuf[8];
            std::snprintf(ibuf, sizeof(ibuf), "i%d", (int)i);
            out.addCase(std::string("curve_A_vol_mid_") + ibuf,
                        json{{"t",t}},
                        json{{"value", curve.volatility(t, 0.05)}});
        }
    }

    // -------------------------------------------------------------------------
    // Scenario B: CapFloorTermVolSurface — fixed ref date, fixed Matrix data.
    // option tenors: 1Y, 2Y, 5Y, 10Y; strikes: 0.02, 0.04, 0.06, 0.08
    // vols[i,j]: 0.20 + 0.005*i - 0.005*j   (decreasing skew, gentle term slope)
    // -------------------------------------------------------------------------
    {
        std::vector<Period> optionT = {1*Years, 2*Years, 5*Years, 10*Years};
        std::vector<Rate>   strikes = {0.02, 0.04, 0.06, 0.08};
        Matrix vols(4, 4);
        for (int i = 0; i < 4; ++i)
            for (int j = 0; j < 4; ++j)
                vols[i][j] = 0.20 + 0.005*i - 0.005*j;

        CapFloorTermVolSurface surf(refDate, cal, bdc, optionT, strikes, vols, dc);

        json inp{{"scenario","surf_B"},{"nTenors",4},{"nStrikes",4}};

        out.addCase("surf_B_maxDate_serial", inp,
                    json{{"value", (long)surf.maxDate().serialNumber()}});
        out.addCase("surf_B_minStrike", inp, json{{"value", surf.minStrike()}});
        out.addCase("surf_B_maxStrike", inp, json{{"value", surf.maxStrike()}});

        // Node lookups: vol[i][j] at corresponding (t_i, k_j)
        for (int i = 0; i < 4; ++i) {
            for (int j = 0; j < 4; ++j) {
                const double t = surf.optionTimes()[i];
                const double k = strikes[j];
                char buf[16];
                std::snprintf(buf, sizeof(buf), "i%dj%d", i, j);
                json q{{"i",i},{"j",j},{"t",t},{"strike",k}};
                out.addCase(std::string("surf_B_vol_node_") + buf, q,
                            json{{"value", surf.volatility(t, k)}});
            }
        }

        // Interior bilinear: between (t0,k0) and (t1,k1)
        {
            const double t = (surf.optionTimes()[0] + surf.optionTimes()[1]) * 0.5;
            const double k = (strikes[0] + strikes[1]) * 0.5;
            json q{{"t",t},{"strike",k}};
            out.addCase("surf_B_vol_mid_lo", q,
                        json{{"value", surf.volatility(t, k)}});
        }
        {
            const double t = (surf.optionTimes()[2] + surf.optionTimes()[3]) * 0.5;
            const double k = (strikes[2] + strikes[3]) * 0.5;
            json q{{"t",t},{"strike",k}};
            out.addCase("surf_B_vol_mid_hi", q,
                        json{{"value", surf.volatility(t, k)}});
        }

        // Optional times reported for Java to compare
        for (int i = 0; i < 4; ++i) {
            char ibuf[8];
            std::snprintf(ibuf, sizeof(ibuf), "%d", i);
            out.addCase(std::string("surf_B_optionTime_i") + ibuf, inp,
                        json{{"value", surf.optionTimes()[i]}});
        }
    }

    out.write();
    return 0;
}
