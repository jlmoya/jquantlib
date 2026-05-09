// migration-harness/cpp/probes/termstructures/volatility/swaption_vol_matrix_probe.cpp
// Reference values for SwaptionVolatilityMatrix (Phase 5f.5 production port).
// Covers two scenarios using Matrix-input constructors with fixed reference dates:
//   A: vanilla bilinear (3x3), no flat extrapolation, ShiftedLognormal/no shift
//   B: shifted (4x4), flat extrapolation, with non-zero shifts matrix
// Probe queries:
//   - maxDate, minStrike, maxStrike, maxSwapTenor (enums via days)
//   - volatility(optionTime, swapLength, strike) at corner / interior / extrapolation points
//   - shift(...) at same points
//   - blackVariance(...) at one interior point per scenario
//   - locate(...) returning lower indexes (i, j)

#include <cstdio>
#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/termstructures/volatility/swaption/swaptionvolmatrix.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/math/matrix.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("termstructures/volatility/swaption_vol_matrix",
                        QL_VERSION, "swaption_vol_matrix_probe");

    DayCounter dc = Actual365Fixed();
    Calendar cal = TARGET();
    BusinessDayConvention bdc = Following;
    Date refDate(2, January, 2020);

    // -------------------------------------------------------------------------
    // Scenario A: 3x3 matrix, no flat extrapolation, ShiftedLognormal / no shift
    // optionTenors: 1Y, 5Y, 10Y; swapTenors: 1Y, 5Y, 10Y
    // vol[i,j] (rows=option, cols=swap):
    //   [0.18 0.20 0.22]
    //   [0.16 0.18 0.20]
    //   [0.14 0.16 0.18]
    // -------------------------------------------------------------------------
    {
        std::vector<Period> optionT = {1*Years, 5*Years, 10*Years};
        std::vector<Period> swapT   = {1*Years, 5*Years, 10*Years};
        Matrix vols(3, 3);
        vols[0][0]=0.18; vols[0][1]=0.20; vols[0][2]=0.22;
        vols[1][0]=0.16; vols[1][1]=0.18; vols[1][2]=0.20;
        vols[2][0]=0.14; vols[2][1]=0.16; vols[2][2]=0.18;

        SwaptionVolatilityMatrix svm(refDate, cal, bdc, optionT, swapT,
                                     vols, dc, false,
                                     ShiftedLognormal, Matrix());

        json inp{{"scenario","A"},
                 {"optionTenors","1Y,5Y,10Y"},
                 {"swapTenors","1Y,5Y,10Y"},
                 {"flatExtrap",false},{"type","ShiftedLognormal"}};

        out.addCase("A_maxDate_serial", inp, json{{"value", (long)svm.maxDate().serialNumber()}});
        out.addCase("A_minStrike",      inp, json{{"value", svm.minStrike()}});
        out.addCase("A_maxStrike",      inp, json{{"value", svm.maxStrike()}});
        out.addCase("A_maxSwapLen",     inp, json{{"value", svm.maxSwapLength()}});

        // option times derived from refDate using TARGET cal + Following bdc + A365F dc:
        // 1Y → ~1.005479; 5Y → ~5.024658; 10Y → ~10.043836  (A365F gives 367/365, etc.)
        // We let the probe report; the Java test will pull these from the JSON.
        const double optTimes[3] = {
            svm.optionTimes()[0], svm.optionTimes()[1], svm.optionTimes()[2]};
        const double swapLens[3] = {
            svm.swapLengths()[0], svm.swapLengths()[1], svm.swapLengths()[2]};

        for (int i = 0; i < 3; ++i) {
            char ibuf[8];
            std::snprintf(ibuf, sizeof(ibuf), "%d", i);
            std::string ti = ibuf;
            out.addCase("A_optionTime_i" + ti, inp, json{{"value", optTimes[i]}});
            out.addCase("A_swapLength_j" + ti, inp, json{{"value", swapLens[i]}});
        }

        // Volatility at every node (should equal the matrix)
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                char buf[16];
                std::snprintf(buf, sizeof(buf), "i%dj%d", i, j);
                json q{{"i",i},{"j",j},
                       {"optionTime",optTimes[i]},{"swapLen",swapLens[j]}};
                out.addCase(std::string("A_vol_node_") + buf, q,
                            json{{"value", svm.volatility(optTimes[i], swapLens[j], 0.05)}});
            }
        }

        // Interior bilinear at (3Y option, 3Y swap) → optionTime~3.0..., swapLen~3.0
        {
            const double tOpt = (optTimes[0] + optTimes[1]) * 0.5;  // midpoint between 1Y,5Y
            const double sLen = (swapLens[0] + swapLens[1]) * 0.5;
            json q{{"optionTime",tOpt},{"swapLen",sLen}};
            out.addCase("A_vol_interior_mid", q,
                        json{{"value", svm.volatility(tOpt, sLen, 0.05)}});
            out.addCase("A_blackVar_interior_mid", q,
                        json{{"value", svm.blackVariance(tOpt, sLen, 0.05)}});
            out.addCase("A_shift_interior_mid", q,
                        json{{"value", svm.shift(tOpt, sLen)}});
        }

        // locate at interior midpoint
        {
            const double tOpt = (optTimes[0] + optTimes[1]) * 0.5;
            const double sLen = (swapLens[0] + swapLens[1]) * 0.5;
            auto p = svm.locate(tOpt, sLen);
            out.addCase("A_locate_mid_i", json{{"optionTime",tOpt},{"swapLen",sLen}},
                        json{{"value", (int)p.first}});
            out.addCase("A_locate_mid_j", json{{"optionTime",tOpt},{"swapLen",sLen}},
                        json{{"value", (int)p.second}});
        }

        out.addCase("A_volatilityType_int", inp,
                    json{{"value", (int)svm.volatilityType()}});  // 0 = ShiftedLognormal in QL enum
    }

    // -------------------------------------------------------------------------
    // Scenario B: 4x4 matrix, flat extrapolation = true, with shifts.
    // -------------------------------------------------------------------------
    {
        std::vector<Period> optionT = {1*Years, 2*Years, 5*Years, 10*Years};
        std::vector<Period> swapT   = {1*Years, 2*Years, 5*Years, 10*Years};
        Matrix vols(4, 4);
        for (int i = 0; i < 4; ++i)
            for (int j = 0; j < 4; ++j)
                vols[i][j] = 0.10 + 0.01*i + 0.01*j;
        Matrix shifts(4, 4, 0.0);
        for (int i = 0; i < 4; ++i)
            for (int j = 0; j < 4; ++j)
                shifts[i][j] = 0.01 + 0.001*i + 0.001*j;

        SwaptionVolatilityMatrix svm(refDate, cal, bdc, optionT, swapT,
                                     vols, dc, /*flatExtrap*/true,
                                     ShiftedLognormal, shifts);

        json inp{{"scenario","B"},
                 {"flatExtrap",true},{"shifts","present"}};

        // Volatility at every node (should equal the matrix)
        for (int i = 0; i < 4; ++i) {
            for (int j = 0; j < 4; ++j) {
                const double tOpt = svm.optionTimes()[i];
                const double sLen = svm.swapLengths()[j];
                char buf[16];
                std::snprintf(buf, sizeof(buf), "i%dj%d", i, j);
                json q{{"i",i},{"j",j},{"optionTime",tOpt},{"swapLen",sLen}};
                out.addCase(std::string("B_vol_node_") + buf, q,
                            json{{"value", svm.volatility(tOpt, sLen, 0.05)}});
                out.addCase(std::string("B_shift_node_") + buf, q,
                            json{{"value", svm.shift(tOpt, sLen)}});
            }
        }

        // Flat extrapolation: x past last → clamp.
        {
            const double tOpt = svm.optionTimes().back() + 5.0;
            const double sLen = svm.swapLengths().back() + 5.0;
            json q{{"optionTime",tOpt},{"swapLen",sLen}};
            out.addCase("B_vol_extrap_far", q,
                        json{{"value", svm.volatility(tOpt, sLen, 0.05, true)}});
            out.addCase("B_shift_extrap_far", q,
                        json{{"value", svm.shift(tOpt, sLen, true)}});
        }
        // Flat extrapolation: x before first → clamp.
        {
            const double tOpt = svm.optionTimes().front() - 0.5;
            const double sLen = svm.swapLengths().front() - 0.5;
            json q{{"optionTime",tOpt},{"swapLen",sLen}};
            out.addCase("B_vol_extrap_before", q,
                        json{{"value", svm.volatility(tOpt, sLen, 0.05, true)}});
        }
    }

    // -------------------------------------------------------------------------
    // Scenario C: optionDates-based constructor (4-arg fixed-data variant)
    // -------------------------------------------------------------------------
    {
        std::vector<Date> optionDates = {
            Date(2, January, 2021), Date(3, January, 2022), Date(2, January, 2025)};
        std::vector<Period> swapT = {1*Years, 5*Years, 10*Years};
        Matrix vols(3, 3);
        vols[0][0]=0.21; vols[0][1]=0.22; vols[0][2]=0.23;
        vols[1][0]=0.18; vols[1][1]=0.19; vols[1][2]=0.20;
        vols[2][0]=0.15; vols[2][1]=0.16; vols[2][2]=0.17;

        SwaptionVolatilityMatrix svm(refDate, cal, bdc, optionDates, swapT,
                                     vols, dc, false,
                                     ShiftedLognormal, Matrix());

        json inp{{"scenario","C"},{"ctor","optionDates"}};

        out.addCase("C_maxDate_serial", inp, json{{"value", (long)svm.maxDate().serialNumber()}});
        for (int i = 0; i < 3; ++i) {
            const double tOpt = svm.optionTimes()[i];
            char ibuf[8];
            std::snprintf(ibuf, sizeof(ibuf), "%d", i);
            out.addCase(std::string("C_optionTime_i") + ibuf, inp,
                        json{{"value", tOpt}});
        }
        // One interior probe
        {
            const double tOpt = (svm.optionTimes()[0] + svm.optionTimes()[1]) * 0.5;
            const double sLen = (svm.swapLengths()[0] + svm.swapLengths()[1]) * 0.5;
            out.addCase("C_vol_interior", json{{"optionTime",tOpt},{"swapLen",sLen}},
                        json{{"value", svm.volatility(tOpt, sLen, 0.05)}});
        }
    }

    out.write();
    return 0;
}
