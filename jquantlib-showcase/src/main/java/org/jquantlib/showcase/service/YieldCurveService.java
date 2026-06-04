package org.jquantlib.showcase.service;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.yieldcurves.InterpolatedZeroCurve;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.springframework.stereotype.Service;

import org.jquantlib.showcase.dto.CurveResponse;
import org.jquantlib.showcase.dto.CurveResponse.TenorPoint;

/**
 * Builds a linearly-interpolated zero-rate yield curve from pillar points and
 * samples the library's term-structure outputs across its horizon: discount
 * factors, zero rates, and forward rates.
 */
@Service
public class YieldCurveService {

    private static final int[] PILLAR_YEARS = {0, 1, 2, 5, 10, 30};

    /** Pillar zero rates (percent) at 0/1/2/5/10/30 years. */
    public CurveResponse build(final double front, final double y1, final double y2,
                               final double y5, final double y10, final double y30) {
        final double[] pct = {front, y1, y2, y5, y10, y30};
        final Date today = Date.todaysDate();
        return Quant.withEvaluationDate(today, () -> {
            final DayCounter dc = new Actual365Fixed();

            final Date[] dates = new Date[PILLAR_YEARS.length];
            final double[] zeros = new double[PILLAR_YEARS.length];
            final List<TenorPoint> inputs = new ArrayList<>();
            for (int i = 0; i < PILLAR_YEARS.length; i++) {
                dates[i] = PILLAR_YEARS[i] == 0 ? today : today.add(new Period(PILLAR_YEARS[i], TimeUnit.Years));
                zeros[i] = pct[i] / 100.0;
                inputs.add(new TenorPoint(PILLAR_YEARS[i], pct[i]));
            }

            final InterpolatedZeroCurve<Linear> curve =
                    new InterpolatedZeroCurve<Linear>(Linear.class, dates, zeros, dc);
            // Allow queries slightly past the 30y back pillar (the tail forward-rate
            // window reaches just beyond it); otherwise the library throws
            // "date is past max curve".
            curve.enableExtrapolation();

            final List<Double> ts = new ArrayList<>();
            final List<Double> dfs = new ArrayList<>();
            final List<Double> zrs = new ArrayList<>();
            final List<Double> fwds = new ArrayList<>();
            // Sample quarterly across 30 years.
            for (int m = 3; m <= 30 * 12; m += 3) {
                final Date d = today.add(new Period(m, TimeUnit.Months));
                final Date d2 = today.add(new Period(m + 3, TimeUnit.Months));
                ts.add(round(dc.yearFraction(today, d)));
                dfs.add(round(curve.discount(d)));
                zrs.add(round(curve.zeroRate(d, dc, Compounding.Continuous).rate() * 100.0));
                fwds.add(round(curve.forwardRate(d, d2, dc, Compounding.Continuous).rate() * 100.0));
            }

            final String summary = ("Linearly-interpolated zero curve through %d pillars "
                    + "(front %.2f%% → 30y %.2f%%). Discount factor at 30y = %.4f.")
                    .formatted(PILLAR_YEARS.length, front, y30, dfs.get(dfs.size() - 1));
            return new CurveResponse(summary, inputs, ts, dfs, zrs, fwds);
        });
    }

    private static double round(final double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return v;
        }
        return Math.round(v * 1e8) / 1e8;
    }
}
