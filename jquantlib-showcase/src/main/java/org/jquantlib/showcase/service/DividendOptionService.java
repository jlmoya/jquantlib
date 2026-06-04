package org.jquantlib.showcase.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

import org.jquantlib.helpers.CRRAmericanDividendOptionHelper;
import org.jquantlib.helpers.CRREuropeanDividendOptionHelper;
import org.jquantlib.instruments.DividendVanillaOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.time.Date;
import org.springframework.stereotype.Service;

import org.jquantlib.showcase.dto.DividendResponse;
import org.jquantlib.showcase.dto.DividendResponse.DividendPayment;
import org.jquantlib.showcase.dto.DividendResponse.Greeks;
import org.jquantlib.showcase.dto.DividendResponse.Sweep;

/**
 * Prices options that pay <em>discrete cash dividends</em> using the
 * jquantlib-helpers binomial (Cox-Ross-Rubinstein) dividend engines — something the plain
 * Black–Scholes–Merton process (continuous dividend yield) cannot express. Shown
 * against the otherwise-identical no-dividend option, with Greeks and a
 * value-vs-spot sweep.
 */
@Service
public class DividendOptionService {

    private static final int SWEEP_POINTS = 21;

    public DividendResponse price(final String typeStr, final String style, final double spot, final double strike,
                                  final double r, final double q, final double vol, final int days,
                                  final double dividendAmount, final int dividendCount) {
        final Option.Type type = parseType(typeStr);
        final boolean american = style != null && style.trim().toLowerCase().startsWith("a");
        final Date today = Date.todaysDate();
        return Quant.withEvaluationDate(today, () -> {
            final Date settlement = today;
            final Date expiry = today.add(days);

            final List<Date> divDates = new ArrayList<>();
            final List<Double> divs = new ArrayList<>();
            final List<DividendPayment> schedule = new ArrayList<>();
            for (int i = 1; i <= dividendCount; i++) {
                final int offset = (int) Math.round((double) days * i / (dividendCount + 1)); // strictly before expiry
                final Date d = today.add(offset);
                divDates.add(d);
                divs.add(dividendAmount);
                schedule.add(new DividendPayment(d.isoDate().toString(), round(dividendAmount)));
            }

            final DividendVanillaOption opt = helper(american, type, spot, strike, r, q, vol, settlement, expiry, divDates, divs);
            final double npv = opt.NPV();
            final Greeks greeks = new Greeks(
                    round(tryG(opt::delta)), round(tryG(opt::gamma)),
                    round(tryG(opt::theta)), round(tryG(opt::vega)), round(tryG(opt::rho)));

            final DividendVanillaOption noDiv = helper(american, type, spot, strike, r, q, vol, settlement, expiry, List.of(), List.of());
            final double npvNoDiv = noDiv.NPV();

            final List<Double> sx = new ArrayList<>();
            final List<Double> withDiv = new ArrayList<>();
            final List<Double> noDivCurve = new ArrayList<>();
            final double lo = Math.max(0.01, 0.5 * strike);
            final double hi = 1.5 * strike;
            for (int i = 0; i < SWEEP_POINTS; i++) {
                final double x = lo + (hi - lo) * i / (SWEEP_POINTS - 1);
                sx.add(round(x));
                withDiv.add(round(helper(american, type, x, strike, r, q, vol, settlement, expiry, divDates, divs).NPV()));
                noDivCurve.add(round(helper(american, type, x, strike, r, q, vol, settlement, expiry, List.of(), List.of()).NPV()));
            }

            final String summary = ("%s %s option paying %d discrete dividend(s) of %.2f. "
                    + "Value %.4f vs %.4f without dividends (a drag of %.4f).")
                    .formatted(american ? "American" : "European", type, dividendCount, dividendAmount,
                            npv, npvNoDiv, npvNoDiv - npv);
            return new DividendResponse(summary, round(npv), round(npvNoDiv), greeks, schedule,
                    new Sweep(sx, withDiv, noDivCurve));
        });
    }

    private DividendVanillaOption helper(final boolean american, final Option.Type type, final double u,
                                         final double strike, final double r, final double q, final double vol,
                                         final Date settlement, final Date expiry,
                                         final List<Date> dates, final List<Double> dividends) {
        // Use the binomial (CRR) dividend engines: the FD American dividend
        // helper silently ignores the dividends (a jquantlib-helpers bug), whereas
        // the BinomialDividendVanillaEngine applies them for both styles.
        return american
                ? new CRRAmericanDividendOptionHelper(type, u, strike, r, q, vol, settlement, expiry, dates, dividends)
                : new CRREuropeanDividendOptionHelper(type, u, strike, r, q, vol, settlement, expiry, dates, dividends);
    }

    private static double tryG(final DoubleSupplier g) {
        try {
            return g.getAsDouble();
        } catch (final RuntimeException e) {
            return Double.NaN;
        }
    }

    private Option.Type parseType(final String s) {
        return s != null && s.toLowerCase().startsWith("p") ? Option.Type.Put : Option.Type.Call;
    }

    private static double round(final double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return v;
        }
        return Math.round(v * 1e6) / 1e6;
    }
}
