package org.jquantlib.showcase.service;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.instruments.AverageType;
import org.jquantlib.instruments.BarrierOption;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.DiscreteAveragingAsianOption;
import org.jquantlib.instruments.EuropeanOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.pricingengines.asian.AnalyticDiscreteGeometricAveragePriceAsianEngine;
import org.jquantlib.pricingengines.barrier.AnalyticBarrierEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.Target;
import org.springframework.stereotype.Service;

import org.jquantlib.showcase.dto.ExoticResponse;
import org.jquantlib.showcase.dto.ExoticResponse.Sweep;

/**
 * Prices path-dependent options with JQuantLib: barrier options (analytic
 * knock-in/out) and discrete geometric-average Asian options, each contrasted
 * with the corresponding vanilla European option.
 */
@Service
public class ExoticOptionService {

    private static final int SWEEP_POINTS = 41;

    private final DayCounter dayCounter = new Actual365Fixed();
    private final Calendar calendar = new Target();

    private record Ctx(SimpleQuote spotQuote, BlackScholesMertonProcess process) {
    }

    private Ctx process(final Date today, final double spot, final double vol, final double rate, final double div) {
        final SimpleQuote spotQuote = new SimpleQuote(spot);
        final Handle<YieldTermStructure> divH = new Handle<>(new FlatForward(today, div, dayCounter));
        final Handle<YieldTermStructure> rfH = new Handle<>(new FlatForward(today, rate, dayCounter));
        final Handle<BlackVolTermStructure> volH = new Handle<>(new BlackConstantVol(today, calendar, vol, dayCounter));
        final BlackScholesMertonProcess p = new BlackScholesMertonProcess(new Handle<Quote>(spotQuote), divH, rfH, volH);
        return new Ctx(spotQuote, p);
    }

    public ExoticResponse barrier(final String typeStr, final String barrierTypeStr, final double spot,
                                  final double strike, final double barrier, final double rebate,
                                  final double vol, final double rate, final double div, final int days) {
        final Option.Type type = parseType(typeStr);
        final BarrierType bt = parseBarrier(barrierTypeStr);
        final Date today = Date.todaysDate();
        return Quant.withEvaluationDate(today, () -> {
            final Ctx ctx = process(today, spot, vol, rate, div);
            final EuropeanExercise exercise = new EuropeanExercise(today.add(days));
            final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);

            final BarrierOption opt = new BarrierOption(bt, barrier, rebate, payoff, exercise);
            opt.setPricingEngine(new AnalyticBarrierEngine(ctx.process()));
            final EuropeanOption vanilla = new EuropeanOption(payoff, exercise);
            vanilla.setPricingEngine(new AnalyticEuropeanEngine(ctx.process()));

            final double npv = opt.NPV();
            final double van = vanilla.NPV();
            final Sweep sweep = sweep(ctx, type, strike, spot,
                    s -> opt.NPV(), s -> vanilla.NPV());

            final String summary = ("%s %s barrier option (strike %.2f). Barrier %.2f, rebate %.2f. "
                    + "Value %.4f vs vanilla European %.4f.")
                    .formatted(bt, type, strike, barrier, rebate, npv, van);
            return new ExoticResponse(summary, round(npv), round(van), barrier, sweep);
        });
    }

    public ExoticResponse asian(final String typeStr, final double spot, final double strike,
                                final double vol, final double rate, final double div, final int days, final int fixings) {
        final Option.Type type = parseType(typeStr);
        final Date today = Date.todaysDate();
        return Quant.withEvaluationDate(today, () -> {
            final Ctx ctx = process(today, spot, vol, rate, div);
            final EuropeanExercise exercise = new EuropeanExercise(today.add(days));
            final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);

            final List<Date> fixingDates = new ArrayList<>();
            for (int i = 1; i <= fixings; i++) {
                fixingDates.add(today.add((int) Math.round((double) days * i / fixings)));
            }
            final DiscreteAveragingAsianOption opt = new DiscreteAveragingAsianOption(
                    AverageType.Geometric, 1.0, 0, fixingDates, payoff, exercise);
            opt.setPricingEngine(new AnalyticDiscreteGeometricAveragePriceAsianEngine(ctx.process()));
            final EuropeanOption vanilla = new EuropeanOption(payoff, exercise);
            vanilla.setPricingEngine(new AnalyticEuropeanEngine(ctx.process()));

            final double npv = opt.NPV();
            final double van = vanilla.NPV();
            final Sweep sweep = sweep(ctx, type, strike, spot,
                    s -> opt.NPV(), s -> vanilla.NPV());

            final String summary = ("%s discrete geometric-average Asian option over %d fixings. "
                    + "Value %.4f vs vanilla European %.4f (averaging lowers effective volatility).")
                    .formatted(type, fixings, npv, van);
            return new ExoticResponse(summary, round(npv), round(van), null, sweep);
        });
    }

    private Sweep sweep(final Ctx ctx, final Option.Type type, final double strike, final double spot0,
                        final java.util.function.DoubleUnaryOperator exotic,
                        final java.util.function.DoubleUnaryOperator vanilla) {
        final List<Double> s = new ArrayList<>();
        final List<Double> ex = new ArrayList<>();
        final List<Double> va = new ArrayList<>();
        final List<Double> in = new ArrayList<>();
        final double lo = Math.max(0.01, 0.40 * strike);
        final double hi = 1.60 * strike;
        for (int i = 0; i < SWEEP_POINTS; i++) {
            final double x = lo + (hi - lo) * i / (SWEEP_POINTS - 1);
            ctx.spotQuote().setValue(x);
            s.add(round(x));
            ex.add(safe(exotic, x));
            va.add(safe(vanilla, x));
            in.add(round(type == Option.Type.Call ? Math.max(x - strike, 0.0) : Math.max(strike - x, 0.0)));
        }
        ctx.spotQuote().setValue(spot0);
        return new Sweep(s, ex, va, in);
    }

    private static Double safe(final java.util.function.DoubleUnaryOperator f, final double x) {
        try {
            return round(f.applyAsDouble(x));
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private Option.Type parseType(final String s) {
        return s != null && s.toLowerCase().startsWith("p") ? Option.Type.Put : Option.Type.Call;
    }

    private BarrierType parseBarrier(final String s) {
        if (s == null) {
            return BarrierType.DownOut;
        }
        return switch (s.trim().toLowerCase()) {
            case "downin", "down-in" -> BarrierType.DownIn;
            case "upin", "up-in" -> BarrierType.UpIn;
            case "upout", "up-out" -> BarrierType.UpOut;
            default -> BarrierType.DownOut;
        };
    }

    private static double round(final double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return v;
        }
        return Math.round(v * 1e6) / 1e6;
    }
}
