package org.jquantlib.showcase.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.AmericanExercise;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.EuropeanOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.methods.lattices.CoxRossRubinstein;
import org.jquantlib.methods.lattices.LeisenReimer;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.vanilla.BaroneAdesiWhaleyApproximationEngine;
import org.jquantlib.pricingengines.vanilla.BinomialVanillaEngine;
import org.jquantlib.pricingengines.vanilla.BjerksundStenslandApproximationEngine;
import org.jquantlib.pricingengines.vanilla.IntegralEngine;
import org.jquantlib.pricingengines.vanilla.JuQuadraticApproximationEngine;
import org.jquantlib.pricingengines.vanilla.MCEuropeanEngine;
import org.jquantlib.pricingengines.vanilla.MCEuropeanEngineLowDiscrepancy;
import org.jquantlib.pricingengines.vanilla.finitedifferences.FDAmericanEngine;
import org.jquantlib.pricingengines.vanilla.finitedifferences.FDEuropeanEngine;
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

import org.jquantlib.showcase.dto.ImpliedVolResponse;
import org.jquantlib.showcase.dto.MonteCarloResponse;
import org.jquantlib.showcase.dto.MonteCarloResponse.McRow;
import org.jquantlib.showcase.dto.OptionResponse;
import org.jquantlib.showcase.dto.OptionResponse.EngineQuote;
import org.jquantlib.showcase.dto.OptionResponse.GreeksDto;
import org.jquantlib.showcase.dto.OptionResponse.SweepDto;

/**
 * Prices European and American vanilla equity options directly against the
 * JQuantLib library: its Black–Scholes–Merton process, vanilla-option
 * instruments, and the full range of pricing engines. Exposes analytic Greeks,
 * a cross-engine comparison (closed-form, lattice, PDE and Monte Carlo), a
 * spot-sweep for charting, and a Monte Carlo convergence study.
 */
@Service
public class OptionPricingService {

    private static final int SWEEP_POINTS = 41;
    private static final long[] MC_SAMPLE_GRID =
            {500, 1000, 2000, 4000, 8000, 16000, 32000, 64000, 128000};

    private final DayCounter dayCounter = new Actual365Fixed();
    private final Calendar calendar = new Target();

    /** A fully-wired pricing context whose spot quote can be re-set for sweeps. */
    private record Ctx(SimpleQuote spotQuote, VanillaOption option, BlackScholesMertonProcess process) {
    }

    private Ctx build(final Option.Type type, final boolean american, final Date today,
                      final double spot, final double strike, final double vol,
                      final double rate, final double div, final int days) {
        final SimpleQuote spotQuote = new SimpleQuote(spot);
        final Handle<Quote> spotH = new Handle<>(spotQuote);
        final Handle<YieldTermStructure> divH = new Handle<>(new FlatForward(today, div, dayCounter));
        final Handle<YieldTermStructure> rfH = new Handle<>(new FlatForward(today, rate, dayCounter));
        final Handle<BlackVolTermStructure> volH = new Handle<>(new BlackConstantVol(today, calendar, vol, dayCounter));
        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(spotH, divH, rfH, volH);

        final Date maturity = today.add(days);
        final Exercise exercise = american ? new AmericanExercise(today, maturity) : new EuropeanExercise(maturity);
        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);
        final VanillaOption option = american
                ? new VanillaOption(payoff, exercise)
                : new EuropeanOption(payoff, exercise);
        return new Ctx(spotQuote, option, process);
    }

    /** Price a European option: analytic NPV + Greeks, engine comparison (incl. Monte Carlo), and a spot sweep. */
    public OptionResponse priceEuropean(final String typeStr, final double spot, final double strike,
                                        final double vol, final double rate, final double div, final int days) {
        final Option.Type type = parseType(typeStr);
        final Date today = Date.todaysDate();
        return Quant.withEvaluationDate(today, () -> {
            final Ctx ctx = build(type, false, today, spot, strike, vol, rate, div, days);

            // Cross-engine comparison first (it swaps engines on the option)...
            final List<EngineQuote> engines = europeanEngines(ctx);

            // ...then settle on the fast analytic engine for the headline value,
            // Greeks, and the spot-sweep that drives the charts.
            ctx.option().setPricingEngine(new AnalyticEuropeanEngine(ctx.process()));
            final double npv = ctx.option().NPV();
            final GreeksDto greeks = new GreeksDto(
                    ctx.option().delta(), ctx.option().gamma(), ctx.option().vega(),
                    ctx.option().thetaPerDay(), ctx.option().rho(), ctx.option().dividendRho());
            final SweepDto sweep = sweep(ctx, type, strike, spot, true);

            final String summary = "%s European option — analytic Black–Scholes–Merton. Spot %.2f, strike %.2f, %.1f%% vol, %.2f%% rate, %.2f%% div, %d days."
                    .formatted(type, spot, strike, vol * 100, rate * 100, div * 100, days);
            return new OptionResponse(summary, npv, greeks, engines, sweep);
        });
    }

    /** Price an American option across approximation, lattice and FD engines, vs the European value. */
    public OptionResponse priceAmerican(final String typeStr, final double spot, final double strike,
                                        final double vol, final double rate, final double div, final int days) {
        final Option.Type type = parseType(typeStr);
        final Date today = Date.todaysDate();
        return Quant.withEvaluationDate(today, () -> {
            final Ctx amer = build(type, true, today, spot, strike, vol, rate, div, days);

            final List<EngineQuote> engines = new ArrayList<>();
            final Ctx euro = build(type, false, today, spot, strike, vol, rate, div, days);
            euro.option().setPricingEngine(new AnalyticEuropeanEngine(euro.process()));
            final double euroNpv = euro.option().NPV();
            engines.add(new EngineQuote("European (analytic, no early exercise)", "reference", round(euroNpv), null));

            engines.add(priceWith(amer, "Barone-Adesi / Whaley", "approximation",
                    () -> new BaroneAdesiWhaleyApproximationEngine(amer.process())));
            engines.add(priceWith(amer, "Bjerksund / Stensland", "approximation",
                    () -> new BjerksundStenslandApproximationEngine(amer.process())));
            engines.add(priceWith(amer, "Ju quadratic", "approximation",
                    () -> new JuQuadraticApproximationEngine(amer.process())));
            engines.add(priceWith(amer, "Binomial (Cox-Ross-Rubinstein, 401 steps)", "lattice",
                    () -> new BinomialVanillaEngine<>(CoxRossRubinstein.class, amer.process(), 401)));
            engines.add(priceWith(amer, "Finite differences (401×400)", "PDE",
                    () -> new FDAmericanEngine(amer.process(), 401, 400, false)));

            amer.option().setPricingEngine(new BaroneAdesiWhaleyApproximationEngine(amer.process()));
            final double amerNpv = amer.option().NPV();
            final SweepDto sweep = sweep(amer, type, strike, spot, false);

            final String summary = ("%s American option. Early-exercise premium over European ≈ %.4f "
                    + "(American %.4f − European %.4f).")
                    .formatted(type, amerNpv - euroNpv, amerNpv, euroNpv);
            return new OptionResponse(summary, amerNpv, null, engines, sweep);
        });
    }

    /**
     * Monte Carlo convergence study for a European option: prices the same option
     * with growing sample counts using both a pseudo-random generator (with its
     * statistical standard error and 95% confidence band) and a Sobol
     * low-discrepancy generator, against the analytic Black–Scholes reference.
     */
    public MonteCarloResponse monteCarloConvergence(final String typeStr, final double spot, final double strike,
                                                    final double vol, final double rate, final double div, final int days) {
        final Option.Type type = parseType(typeStr);
        final Date today = Date.todaysDate();
        return Quant.withEvaluationDate(today, () -> {
            final Ctx ctx = build(type, false, today, spot, strike, vol, rate, div, days);

            ctx.option().setPricingEngine(new AnalyticEuropeanEngine(ctx.process()));
            final double analytic = ctx.option().NPV();

            final List<McRow> rows = new ArrayList<>();
            for (final long n : MC_SAMPLE_GRID) {
                final int samples = (int) n;

                // Pseudo-random (no antithetic, so the reported error is the plain
                // sample standard error scaling like 1/sqrt(N)).
                ctx.option().setPricingEngine(new MCEuropeanEngine(ctx.process(), 1, McSimulation.NULL_SAMPLES,
                        false, false, samples, McSimulation.NULL_TOLERANCE, McSimulation.NULL_SAMPLES, 42L));
                final double pPrice = ctx.option().NPV();
                final double pErr = ctx.option().errorEstimate();

                // Sobol low-discrepancy.
                ctx.option().setPricingEngine(new MCEuropeanEngineLowDiscrepancy(ctx.process(), 1, McSimulation.NULL_SAMPLES,
                        false, false, samples, McSimulation.NULL_TOLERANCE, McSimulation.NULL_SAMPLES, 0L));
                final double sPrice = ctx.option().NPV();

                rows.add(new McRow(n,
                        round(pPrice), round(pErr),
                        round(pPrice - 1.96 * pErr), round(pPrice + 1.96 * pErr),
                        round(Math.abs(pPrice - analytic)),
                        round(sPrice), round(Math.abs(sPrice - analytic))));
            }

            final String summary = ("%s European option, Monte Carlo convergence. Analytic reference = %.4f. "
                    + "Pseudo-random error shrinks ~1/√N; Sobol (low-discrepancy) converges faster.")
                    .formatted(type, analytic);
            return new MonteCarloResponse(summary, round(analytic), rows);
        });
    }

    /**
     * Inverts the Black–Scholes price for the volatility implied by a market
     * price, using the library's {@code VanillaOption.impliedVolatility} solver,
     * and returns a price-vs-volatility curve for visualisation.
     */
    public ImpliedVolResponse impliedVolatility(final String typeStr, final double spot, final double strike,
                                                final double rate, final double div, final int days,
                                                final double marketPrice) {
        final Option.Type type = parseType(typeStr);
        final Date today = Date.todaysDate();
        return Quant.withEvaluationDate(today, () -> {
            final Ctx ctx = build(type, false, today, spot, strike, 0.20, rate, div, days);
            ctx.option().setPricingEngine(new AnalyticEuropeanEngine(ctx.process()));

            double iv = Double.NaN;
            double recovered = Double.NaN;
            String note = null;
            try {
                iv = ctx.option().impliedVolatility(marketPrice, ctx.process(), 1.0e-6, 200);
                final Ctx check = build(type, false, today, spot, strike, iv, rate, div, days);
                check.option().setPricingEngine(new AnalyticEuropeanEngine(check.process()));
                recovered = check.option().NPV();
            } catch (final RuntimeException e) {
                note = "No implied volatility found for that price: " + e.getMessage();
            }

            final List<Double> volAxis = new ArrayList<>();
            final List<Double> priceAxis = new ArrayList<>();
            for (int i = 0; i <= 50; i++) {
                final double v = 0.01 + (1.20 - 0.01) * i / 50.0;
                final Ctx c = build(type, false, today, spot, strike, v, rate, div, days);
                c.option().setPricingEngine(new AnalyticEuropeanEngine(c.process()));
                volAxis.add(round(v * 100.0));
                priceAxis.add(round(c.option().NPV()));
            }

            final String summary = Double.isNaN(iv)
                    ? note
                    : "%s option: a market price of %.4f implies %.2f%% volatility (which reprices to %.4f)."
                            .formatted(type, marketPrice, iv * 100.0, recovered);
            return new ImpliedVolResponse(summary, Double.isNaN(iv) ? Double.NaN : round(iv * 100.0),
                    round(marketPrice), round(recovered), volAxis, priceAxis);
        });
    }

    private List<EngineQuote> europeanEngines(final Ctx ctx) {
        final List<EngineQuote> out = new ArrayList<>();
        out.add(priceWith(ctx, "Analytic Black–Scholes", "closed-form",
                () -> new AnalyticEuropeanEngine(ctx.process())));
        out.add(priceWith(ctx, "Integral", "numerical integration",
                () -> new IntegralEngine(ctx.process())));
        out.add(priceWith(ctx, "Binomial (Cox-Ross-Rubinstein, 401 steps)", "lattice",
                () -> new BinomialVanillaEngine<>(CoxRossRubinstein.class, ctx.process(), 401)));
        out.add(priceWith(ctx, "Binomial (Leisen-Reimer, 401 steps)", "lattice",
                () -> new BinomialVanillaEngine<>(LeisenReimer.class, ctx.process(), 401)));
        out.add(priceWith(ctx, "Finite differences (401×400)", "PDE",
                () -> new FDEuropeanEngine(ctx.process(), 401, 400, false)));
        out.add(mcQuote(ctx, "Monte Carlo — pseudo-random (50k paths, antithetic)", false, 50_000));
        out.add(mcQuote(ctx, "Monte Carlo — Sobol low-discrepancy (50k paths)", true, 50_000));
        return out;
    }

    private EngineQuote mcQuote(final Ctx ctx, final String label, final boolean sobol, final int samples) {
        try {
            final PricingEngine eng = sobol
                    ? new MCEuropeanEngineLowDiscrepancy(ctx.process(), 1, McSimulation.NULL_SAMPLES,
                            false, false, samples, McSimulation.NULL_TOLERANCE, McSimulation.NULL_SAMPLES, 0L)
                    : new MCEuropeanEngine(ctx.process(), 1, McSimulation.NULL_SAMPLES,
                            true, true, samples, McSimulation.NULL_TOLERANCE, McSimulation.NULL_SAMPLES, 42L);
            ctx.option().setPricingEngine(eng);
            final double npv = ctx.option().NPV();
            String note;
            try {
                final double se = ctx.option().errorEstimate();
                note = sobol ? samples + " Sobol paths" : "std err ±%.4f (95%% CI ±%.4f)".formatted(se, 1.96 * se);
            } catch (final RuntimeException e) {
                note = samples + " paths";
            }
            return new EngineQuote(label, "Monte Carlo", round(npv), note);
        } catch (final RuntimeException e) {
            return new EngineQuote(label, "Monte Carlo", null, "n/a: " + e.getClass().getSimpleName());
        }
    }

    private EngineQuote priceWith(final Ctx ctx, final String label, final String category,
                                  final Supplier<PricingEngine> engine) {
        try {
            ctx.option().setPricingEngine(engine.get());
            return new EngineQuote(label, category, round(ctx.option().NPV()), null);
        } catch (final RuntimeException e) {
            return new EngineQuote(label, category, null, "n/a: " + e.getClass().getSimpleName());
        }
    }

    private SweepDto sweep(final Ctx ctx, final Option.Type type, final double strike,
                           final double spot0, final boolean withGreeks) {
        final List<Double> sList = new ArrayList<>();
        final List<Double> pList = new ArrayList<>();
        final List<Double> dList = new ArrayList<>();
        final List<Double> gList = new ArrayList<>();
        final List<Double> iList = new ArrayList<>();

        final double lo = Math.max(0.01, 0.40 * strike);
        final double hi = 1.60 * strike;
        for (int i = 0; i < SWEEP_POINTS; i++) {
            final double s = lo + (hi - lo) * i / (SWEEP_POINTS - 1);
            ctx.spotQuote().setValue(s);
            sList.add(round(s));
            pList.add(round(ctx.option().NPV()));
            iList.add(round(type == Option.Type.Call ? Math.max(s - strike, 0.0) : Math.max(strike - s, 0.0)));
            if (withGreeks) {
                dList.add(tryGreek(ctx.option()::delta));
                gList.add(tryGreek(ctx.option()::gamma));
            }
        }
        ctx.spotQuote().setValue(spot0);
        return new SweepDto(sList, pList,
                withGreeks ? dList : null,
                withGreeks ? gList : null,
                iList);
    }

    private static Double tryGreek(final DoubleSupplier g) {
        try {
            return round(g.getAsDouble());
        } catch (final RuntimeException e) {
            return null;
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
