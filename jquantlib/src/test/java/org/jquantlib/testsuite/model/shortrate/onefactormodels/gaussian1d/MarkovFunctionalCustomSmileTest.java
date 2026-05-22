// Phase 2k Track C.2 — CustomSmileFactory inner class + MF CustomSmile branch wiring
package org.jquantlib.testsuite.model.shortrate.onefactormodels.gaussian1d;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.indexes.EurLiborSwapIsdaFixA;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.Ops;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.MarkovFunctional;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.FlatSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.termstructures.volatilities.swaption.ConstantSwaptionVolatility;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Verifies that {@link MarkovFunctional.CustomSmileFactory} /
 * {@link MarkovFunctional.CustomSmileSection} can be wired into MF and that
 * the CustomSmile branch of {@code validate()} + {@code updateSmiles()} +
 * {@code updateNumeraireTabulation()} executes without error.
 *
 * <p>This is a structural/integration test (no C++ cross-validation oracle —
 * CustomSmile is caller-supplied). Collect-all-failures pattern; TIGHT tier
 * where numerical values are checked.
 *
 * <p>Phase 2k Track C.2.
 */
public class MarkovFunctionalCustomSmileTest {

    private static final Date REFERENCE_DATE = new Date(14, Month.November, 2012);

    // -----------------------------------------------------------------------
    //  Stub CustomSmileSection: wraps a FlatSmileSection, providing
    //  inverseDigitalCall via Brent solve on the digital price.
    // -----------------------------------------------------------------------

    /**
     * Minimal concrete {@link MarkovFunctional.CustomSmileSection} for testing.
     * Delegates volatility to an underlying {@link FlatSmileSection}; implements
     * {@link #inverseDigitalCall} by Brent root-finding on the digital call price.
     */
    private static final class StubCustomSmileSection
            extends MarkovFunctional.CustomSmileSection {

        private final FlatSmileSection flat_;

        StubCustomSmileSection(final SmileSection source, final double atm) {
            // Use the source's exercise time + a flat vol of 0.20 for simplicity.
            super(source.exerciseTime(), new Actual365Fixed());
            flat_ = new FlatSmileSection(
                    source.exerciseTime(),
                    0.20,             // flat vol
                    new Actual365Fixed(),
                    atm);
        }

        @Override
        public double minStrike() { return flat_.minStrike(); }

        @Override
        public double maxStrike() { return flat_.maxStrike(); }

        @Override
        public double atmLevel() { return flat_.atmLevel(); }

        @Override
        protected double volatilityImpl(final double strike) {
            return flat_.volatilityImpl(strike);
        }

        /**
         * Invert the digital call price to a rate using Brent root-finding.
         * {@code f(r) = digitalOptionPrice(r - shift, Call, discount, 1e-5) - price == 0}.
         */
        @Override
        public double inverseDigitalCall(final double price, final double discount) {
            final double atm = atmLevel();
            final double lo = 0.0;
            final double hi = 2.0;
            // At lo: digital ≈ discount (rate very low → deep ITM call → digital ≈ 1 × discount).
            // At hi: digital ≈ 0        (rate very high → deep OTM call → digital ≈ 0).
            // Clamp to boundary if outside range.
            final double diLo = digitalOptionPrice(lo - shift(), Option.Type.Call, discount, 1e-5);
            final double diHi = digitalOptionPrice(hi - shift(), Option.Type.Call, discount, 1e-5);
            if (price >= diLo) return lo;
            if (price <= diHi) return hi;
            final Brent brent = new Brent();
            final StubCustomSmileSection self = this;
            return brent.solve(new Ops.DoubleOp() {
                @Override public double op(final double strike) {
                    return self.digitalOptionPrice(strike - self.shift(), Option.Type.Call, discount, 1e-5) - price;
                }
            }, 1e-7, atm, lo, hi);
        }
    }

    // -----------------------------------------------------------------------
    //  Stub CustomSmileFactory
    // -----------------------------------------------------------------------

    private static final class StubCustomSmileFactory
            extends MarkovFunctional.CustomSmileFactory {

        @Override
        public MarkovFunctional.CustomSmileSection smileSection(
                final SmileSection source, final double atm) {
            return new StubCustomSmileSection(source, atm);
        }
    }

    // -----------------------------------------------------------------------
    //  Test
    // -----------------------------------------------------------------------

    @Test
    public void customSmile_acceptsUserSuppliedSmileSection() {
        new Settings().setEvaluationDate(REFERENCE_DATE);

        final List<String> failures = new ArrayList<>();

        // ModelSettings (to be passed into MF constructor, which calls validate internally).
        final MarkovFunctional.ModelSettings settings = new MarkovFunctional.ModelSettings()
                .withYGridPoints(32)                     // smaller grid → faster test
                .withYStdDevs(5.0)
                .withGaussHermitePoints(16)
                .withDigitalGap(1e-5)
                .withMarketRateAccuracy(1e-7)
                .withLowerRateBound(0.0)
                .withUpperRateBound(2.0)
                .withAdjustments(MarkovFunctional.CUSTOM_SMILE)
                .withCustomSmileFactory(new StubCustomSmileFactory());

        // 2. Build MF with CustomSmile mode — construction triggers updateSmiles()
        //    and updateNumeraireTabulation() internally.
        final Handle<YieldTermStructure> flatYts = new Handle<YieldTermStructure>(
                new FlatForward(REFERENCE_DATE, 0.03, new Actual365Fixed()));

        final Handle<SwaptionVolatilityStructure> flatSwaptionVts =
                new Handle<SwaptionVolatilityStructure>(
                        new ConstantSwaptionVolatility(
                                0, new Target(),
                                BusinessDayConvention.ModifiedFollowing,
                                0.20, new Actual365Fixed()));

        final SwapIndex swapIndexBase = new EurLiborSwapIsdaFixA(
                new Period(1, TimeUnit.Years));

        final List<Date> volStepDates = new ArrayList<>();
        final double[] vols = new double[]{0.01};

        final List<Date> expiries = new ArrayList<>();
        expiries.add(REFERENCE_DATE.add(new Period(5, TimeUnit.Years)));
        final List<Period> tenors = new ArrayList<>();
        tenors.add(new Period(10, TimeUnit.Years));

        MarkovFunctional mf = null;
        try {
            mf = new MarkovFunctional(
                    flatYts, 0.01, volStepDates, vols, flatSwaptionVts,
                    expiries, tenors, swapIndexBase, settings);
        } catch (final Exception ex) {
            failures.add("MarkovFunctional construction (CustomSmile) threw: " + ex.getMessage());
        }

        // 3. If construction succeeded, check numeraireTime is positive and finite.
        if (mf != null) {
            final double nTime = mf.numeraireTime();
            if (!(nTime > 0.0 && Double.isFinite(nTime))) {
                failures.add("numeraireTime expected > 0 finite, got " + nTime);
            }
        }

        // 4. Construction MUST throw when CustomSmile mode + NO factory (validate rejects it).
        final MarkovFunctional.ModelSettings settingsNoFactory = new MarkovFunctional.ModelSettings()
                .withYGridPoints(32)
                .withYStdDevs(5.0)
                .withGaussHermitePoints(16)
                .withDigitalGap(1e-5)
                .withMarketRateAccuracy(1e-7)
                .withLowerRateBound(0.0)
                .withUpperRateBound(2.0)
                .withAdjustments(MarkovFunctional.CUSTOM_SMILE)
                /* intentionally no .withCustomSmileFactory(...) */;
        boolean threwWithoutFactory = false;
        try {
            new MarkovFunctional(
                    flatYts, 0.01, volStepDates, vols, flatSwaptionVts,
                    expiries, tenors, swapIndexBase, settingsNoFactory);
        } catch (final Exception ex) {
            threwWithoutFactory = true;
        }
        if (!threwWithoutFactory) {
            failures.add("MarkovFunctional construction should have thrown when CustomSmile set but no factory");
        }

        if (!failures.isEmpty()) {
            fail("CustomSmile wiring failures:\n  " + String.join("\n  ", failures));
        }
    }
}
