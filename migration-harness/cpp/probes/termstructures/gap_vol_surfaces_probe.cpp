// migration-harness/cpp/probes/termstructures/gap_vol_surfaces_probe.cpp
// Reference values for the 6 gap-ported volatility term-structure classes:
//   ConstantCapFloorTermVolatility, ConstantCPIVolatility (+ CPIVolatilitySurface base),
//   GridModelLocalVolSurface, and the deterministic CmsMarketCalibration transform functions.
//
// All cases here are DETERMINISTIC (flat-vol lookups, fixed-grid local vol, pure-math
// transforms). The optimizer-driven CmsMarketCalibration.compute() and the full
// CmsMarket.reprice()/weightedError() pricing stack are intentionally NOT probed here
// (they depend on the CMS pricing stack + optimizer path; see the Java test's javadoc).

#include <cstdio>
#include <cmath>
#include <vector>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/volatility/capfloor/constantcapfloortermvol.hpp>
#include <ql/termstructures/volatility/inflation/constantcpivolatility.hpp>
#include <ql/termstructures/volatility/equityfx/gridmodellocalvolsurface.hpp>
#include <ql/termstructures/volatility/swaption/cmsmarketcalibration.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/frequency.hpp>
#include <ql/settings.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("termstructures/gap_vol_surfaces",
                        QL_VERSION, "gap_vol_surfaces_probe");

    DayCounter dc = Actual365Fixed();
    Calendar cal = TARGET();
    BusinessDayConvention bdc = Following;
    Date refDate(2, January, 2020);
    Settings::instance().evaluationDate() = refDate;

    // -------------------------------------------------------------------------
    // ConstantCapFloorTermVolatility — fixed ref date, fixed market data (vol=0.18).
    // Flat: every (t, strike) lookup must return the constant.
    // -------------------------------------------------------------------------
    {
        const Volatility v = 0.18;
        ConstantCapFloorTermVolatility cfv(refDate, cal, bdc, v, dc);

        out.addCase("cf_maxDate_serial", json{{"v",v}},
                    json{{"value", (long)cfv.maxDate().serialNumber()}});
        out.addCase("cf_minStrike", json{{"v",v}}, json{{"value", cfv.minStrike()}});
        out.addCase("cf_maxStrike", json{{"v",v}}, json{{"value", cfv.maxStrike()}});

        const double ts[] = {0.5, 1.0, 2.5, 5.0, 10.0};
        const double ks[] = {0.01, 0.03, 0.05};
        int idx = 0;
        for (double t : ts) {
            for (double k : ks) {
                char buf[24];
                std::snprintf(buf, sizeof(buf), "cf_vol_%d", idx++);
                out.addCase(buf, json{{"t",t},{"strike",k}},
                            json{{"value", cfv.volatility(t, k, true)}});
            }
        }
    }

    // -------------------------------------------------------------------------
    // ConstantCPIVolatility — fixed market data (vol=0.045), monthly, lag 3M,
    // non-interpolated index. Probe vol(time,strike) (bare impl) + vol(date,strike)
    // + totalVariance(date,strike). Flat ⇒ vol is the constant; totalVariance scales by
    // timeFromBase.
    // -------------------------------------------------------------------------
    {
        const Volatility v = 0.045;
        const Natural settlementDays = 0;
        const Period obsLag = Period(3, Months);
        const Frequency freq = Monthly;
        const bool interp = false;
        ConstantCPIVolatility cpi(v, settlementDays, cal, bdc, dc, obsLag, freq, interp);

        out.addCase("cpi_maxDate_serial", json{{"v",v}},
                    json{{"value", (long)cpi.maxDate().serialNumber()}});
        out.addCase("cpi_minStrike", json{{"v",v}}, json{{"value", cpi.minStrike()}});
        out.addCase("cpi_maxStrike", json{{"v",v}}, json{{"value", cpi.maxStrike()}});
        out.addCase("cpi_baseDate_serial", json{{"v",v}},
                    json{{"value", (long)cpi.baseDate().serialNumber()}});

        // bare time-keyed vol (volatilityImpl) — flat
        const double ts[] = {0.25, 1.0, 3.0};
        int idx = 0;
        for (double t : ts) {
            char buf[28];
            std::snprintf(buf, sizeof(buf), "cpi_vol_time_%d", idx++);
            out.addCase(buf, json{{"t",t},{"strike",0.02}},
                        json{{"value", cpi.volatility(t, 0.02)}});
        }

        // date-keyed vol + totalVariance at several maturities (use surface's own lag)
        const Period tenors[] = {Period(1, Years), Period(2, Years), Period(5, Years)};
        idx = 0;
        for (const Period& T : tenors) {
            Date mat = refDate + T;
            char b1[32], b2[32], b3[32];
            std::snprintf(b1, sizeof(b1), "cpi_vol_date_%d", idx);
            std::snprintf(b2, sizeof(b2), "cpi_totVar_date_%d", idx);
            std::snprintf(b3, sizeof(b3), "cpi_timeFromBase_%d", idx);
            json q{{"maturity_serial", (long)mat.serialNumber()},{"strike",0.02}};
            out.addCase(b1, q, json{{"value", cpi.volatility(mat, 0.02, Period(-1,Days), true)}});
            out.addCase(b2, q, json{{"value", cpi.totalVariance(mat, 0.02, Period(-1,Days), true)}});
            out.addCase(b3, q, json{{"value", cpi.timeFromBase(mat)}});
            ++idx;
        }
    }

    // -------------------------------------------------------------------------
    // GridModelLocalVolSurface — fixed grid, default params (all 1.0) then a set
    // params() vector. localVol(t, strike) interpolated over the fixed grid.
    // dates: +1Y, +2Y, +3Y ; strikes (shared): {80, 90, 100, 110, 120}.
    // -------------------------------------------------------------------------
    {
        std::vector<Date> dates = {refDate + Period(1, Years),
                                   refDate + Period(2, Years),
                                   refDate + Period(3, Years)};
        auto strikeVec = ext::make_shared<std::vector<Real> >(
            std::vector<Real>{80.0, 90.0, 100.0, 110.0, 120.0});
        std::vector<ext::shared_ptr<std::vector<Real> > > strikes;
        for (size_t i = 0; i < dates.size(); ++i) strikes.push_back(strikeVec);

        GridModelLocalVolSurface surf(refDate, dates, strikes, dc);

        // Default: every parameter = 1.0 ⇒ localVol == 1.0 everywhere.
        out.addCase("grid_default_minStrike", json{}, json{{"value", surf.minStrike()}});
        out.addCase("grid_default_maxStrike", json{}, json{{"value", surf.maxStrike()}});
        out.addCase("grid_default_maxTime", json{}, json{{"value", surf.maxTime()}});
        out.addCase("grid_default_maxDate_serial", json{},
                    json{{"value", (long)surf.maxDate().serialNumber()}});

        {
            const double tq[] = {0.5, 1.0, 1.5, 2.0, 2.5};
            const double kq[] = {85.0, 100.0, 115.0};
            int idx = 0;
            for (double t : tq) {
                for (double k : kq) {
                    char buf[32];
                    std::snprintf(buf, sizeof(buf), "grid_default_lv_%d", idx++);
                    out.addCase(buf, json{{"t",t},{"strike",k}},
                                json{{"value", surf.localVol(t, k, true)}});
                }
            }
        }

        // Now set a non-trivial parameter vector. Parameter layout is row-major over
        // (nStrikes rows x nTimes cols): param[r*nTimes + c] = vol at (strike r, time c).
        // We build a smile-by-term grid: vol(r,c) = 0.20 + 0.01*r - 0.005*c.
        const Size nStrikes = 5, nTimes = 3;
        Array p(nStrikes * nTimes);
        for (Size r = 0; r < nStrikes; ++r)
            for (Size c = 0; c < nTimes; ++c)
                p[r * nTimes + c] = 0.20 + 0.01 * (Real)r - 0.005 * (Real)c;
        surf.setParams(p);

        {
            // At-node lookups (exact grid points) must reproduce the matrix value.
            const double times[] = {0.0, 0.0, 0.0};   // filled below from surf grid via maturity dates
            (void)times;
            const double kNodes[] = {80.0, 100.0, 120.0};
            // option times correspond to year-fractions of the grid dates
            std::vector<double> gridTimes;
            for (const Date& d : dates) gridTimes.push_back(dc.yearFraction(refDate, d));

            int idx = 0;
            for (double t : gridTimes) {
                for (double k : kNodes) {
                    char buf[32];
                    std::snprintf(buf, sizeof(buf), "grid_set_node_%d", idx++);
                    out.addCase(buf, json{{"t",t},{"strike",k}},
                                json{{"value", surf.localVol(t, k, true)}});
                }
            }

            // Interior interpolation points
            const double ti[] = {0.5, 1.5, 2.5};
            const double ki[] = {85.0, 105.0};
            idx = 0;
            for (double t : ti) {
                for (double k : ki) {
                    char buf[32];
                    std::snprintf(buf, sizeof(buf), "grid_set_interior_%d", idx++);
                    out.addCase(buf, json{{"t",t},{"strike",k}},
                                json{{"value", surf.localVol(t, k, true)}});
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // CmsMarketCalibration static transform functions (pure math, TIGHT).
    //   betaTransformDirect(y), betaTransformInverse(beta),
    //   reversionTransformDirect(y), reversionTransformInverse(reversion).
    // -------------------------------------------------------------------------
    {
        // betaTransformDirect uses y^2 internally, so negative y are valid.
        const double ys[] = {-3.0, -1.0, -0.5, 0.0, 0.5, 1.0, 2.0, 3.0, 15.0};
        int idx = 0;
        for (double y : ys) {
            char b1[40];
            std::snprintf(b1, sizeof(b1), "betaTransformDirect_%d", idx++);
            out.addCase(b1, json{{"y",y}},
                        json{{"value", CmsMarketCalibration::betaTransformDirect(y)}});
        }
        // reversionTransformDirect(y) = sqrt(y): probe non-negative inputs only
        // (negative y yields NaN, a degenerate case never produced by the calibration,
        // which always feeds reversionTransformInverse(reversion)=reversion^2 >= 0).
        const double yrev[] = {0.0, 0.01, 0.25, 1.0, 4.0, 9.0};
        idx = 0;
        for (double y : yrev) {
            char b2[40];
            std::snprintf(b2, sizeof(b2), "reversionTransformDirect_%d", idx++);
            out.addCase(b2, json{{"y",y}},
                        json{{"value", CmsMarketCalibration::reversionTransformDirect(y)}});
        }
        const double betas[] = {0.1, 0.3, 0.5, 0.7, 0.9};
        idx = 0;
        for (double b : betas) {
            char b1[40];
            std::snprintf(b1, sizeof(b1), "betaTransformInverse_%d", idx++);
            out.addCase(b1, json{{"beta",b}},
                        json{{"value", CmsMarketCalibration::betaTransformInverse(b)}});
        }
        const double revs[] = {0.0, 0.01, 0.05, 0.5, 2.0};
        idx = 0;
        for (double rv : revs) {
            char b1[44];
            std::snprintf(b1, sizeof(b1), "reversionTransformInverse_%d", idx++);
            out.addCase(b1, json{{"reversion",rv}},
                        json{{"value", CmsMarketCalibration::reversionTransformInverse(rv)}});
        }
    }

    out.write();
    return 0;
}
