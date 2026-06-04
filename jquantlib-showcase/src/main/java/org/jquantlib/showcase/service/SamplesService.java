package org.jquantlib.showcase.service;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.stereotype.Service;

import org.jquantlib.showcase.dto.SampleInfo;
import org.jquantlib.showcase.dto.SampleRun;

/**
 * Runs the bundled <em>jquantlib-samples</em> example programs and captures
 * their console output so it can be displayed in the browser. This surfaces the
 * original example suite — the canonical, hand-written demonstrations of the
 * library — directly inside the showcase.
 *
 * <p>Each sample exposes a {@code public static void main(String[])} that runs
 * the demo; we invoke it reflectively while {@code System.out} is redirected to
 * an in-memory buffer. The redirect touches process-global state, so runs are
 * serialised behind a lock.
 */
@Service
public class SamplesService {

    private static final Object OUT_LOCK = new Object();

    /**
     * Curated catalog of console-based samples. GUI samples (e.g. the JFreeChart
     * {@code SobolChartSample}) and the aggregate runners are intentionally
     * excluded — they don't render meaningfully as captured text.
     */
    private static final List<SampleInfo> CATALOG = List.of(
            new SampleInfo("equity-options", "org.jquantlib.samples.EquityOptions", "Equity Options",
                    "Prices European, Bermudan and American options across Black-Scholes, six binomial trees "
                            + "(Jarrow-Rudd, Cox-Ross-Rubinstein, Tian, Trigeorgis, Leisen-Reimer, Joshi), the "
                            + "integral method and finite differences."),
            new SampleInfo("bonds", "org.jquantlib.samples.Bonds", "Bonds",
                    "Bootstraps a discounting curve from deposits, swaps and bonds, then prices zero-coupon, "
                            + "fixed-rate and floating-rate bonds: NPV, clean/dirty price, yield and accrued interest."),
            new SampleInfo("swap", "org.jquantlib.samples.Swap", "Interest-Rate Swap",
                    "Prices a vanilla fixed-vs-floating interest-rate swap on bootstrapped depo/swap curves; "
                            + "reports fair fixed rate, fair spread and NPV."),
            new SampleInfo("fra", "org.jquantlib.samples.FRA", "Forward-Rate Agreement",
                    "Builds a forward-rate-agreement and prices it off a bootstrapped term structure."),
            new SampleInfo("repo", "org.jquantlib.samples.Repo", "Bond Repo",
                    "Prices a fixed-coupon bond repurchase agreement."),
            new SampleInfo("convertible-bonds", "org.jquantlib.samples.ConvertibleBonds", "Convertible Bonds",
                    "Prices a convertible bond with a binomial tree (Tsiveriotis-Fernandes), alongside the "
                            + "equivalent vanilla bond and option."),
            new SampleInfo("discrete-hedging", "org.jquantlib.samples.DiscreteHedging", "Discrete Hedging",
                    "Monte Carlo study of a discretely re-hedged option: the replication-error distribution and "
                            + "how it tightens as the re-hedging frequency rises (Derman & Kamal)."),
            new SampleInfo("bermudan-swaption", "org.jquantlib.samples.BermudanSwaption", "Bermudan Swaption",
                    "Calibrates short-rate models (Hull-White, Black-Karasinski, G2) to a swaption volatility "
                            + "matrix and prices a Bermudan swaption. (Calibration — may take a few seconds.)"),
            new SampleInfo("yield-curves", "org.jquantlib.samples.YieldCurveTermStructures", "Yield Term Structures",
                    "Flat-forward, forward-spreaded and implied yield term structures: discount factors, forward, "
                            + "zero and par rates."),
            new SampleInfo("vol-term-structures", "org.jquantlib.samples.VolatilityTermStructures", "Volatility Structures",
                    "Black volatility term structures and surfaces."),
            new SampleInfo("processes", "org.jquantlib.samples.Processes", "Stochastic Processes",
                    "Demonstrates stochastic-process construction and discretisation."),
            new SampleInfo("calendars", "org.jquantlib.samples.Calendars", "Calendars",
                    "Business-day logic across exchange and government-bond calendars: holiday lists, business-day "
                            + "counts, conventions and joint calendars."),
            new SampleInfo("dates", "org.jquantlib.samples.Dates", "Dates",
                    "Date arithmetic, periods, weekday/leap logic and day-count conventions."),
            new SampleInfo("crr-hull-white", "org.jquantlib.samples.CoxRossWithHullWhite", "CRR + Hull-White",
                    "A Cox-Ross-Rubinstein lattice combined with a Hull-White short-rate model."));

    public List<SampleInfo> catalog() {
        return CATALOG;
    }

    public SampleRun run(final String id) {
        final SampleInfo info = CATALOG.stream()
                .filter(s -> s.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown sample id: " + id));

        synchronized (OUT_LOCK) {
            final PrintStream original = System.out;
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            final long start = System.nanoTime();
            boolean ok = true;
            try {
                System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
                final Class<?> cls = Class.forName(info.className());
                cls.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            } catch (final Throwable t) {
                ok = false;
                final Throwable cause = (t.getCause() != null) ? t.getCause() : t;
                buffer.writeBytes(("\n[sample raised " + cause.getClass().getSimpleName() + ": "
                        + cause.getMessage() + "]\n").getBytes(StandardCharsets.UTF_8));
            } finally {
                System.setOut(original);
            }
            final long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            String out = buffer.toString(StandardCharsets.UTF_8);
            if (out.isBlank()) {
                out = "(this sample produced no System.out output — its results are logged via SLF4J)";
            }
            return new SampleRun(info.id(), info.title(), info.className(), out, elapsedMs, ok);
        }
    }
}
