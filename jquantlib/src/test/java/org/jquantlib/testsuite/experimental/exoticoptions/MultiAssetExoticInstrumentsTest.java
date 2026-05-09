/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.exoticoptions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.experimental.exoticoptions.EverestOption;
import org.jquantlib.experimental.exoticoptions.HimalayaOption;
import org.jquantlib.experimental.exoticoptions.PagodaOption;
import org.jquantlib.instruments.NullPayoff;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

/**
 * Phase 4h smoke tests for the multi-asset exotic option instrument
 * classes (HimalayaOption / EverestOption / PagodaOption).
 *
 * <p>These cover constructor wiring, payoff/exercise selection,
 * setupArguments contracts (via reflection-free engine-level
 * argument allocation), and validate() invariants.
 *
 * <p>Engine-level (NPV) tests cannot be exercised here — Phase 4h.5
 * still needs to bring the multi-asset Monte Carlo infrastructure
 * (MultiPath / MultiPathGenerator / MCMultiVariateEngine /
 * StochasticProcessArray.size()-aware path pricer) across before any
 * MCHimalayaEngine / MCEverestEngine / MCPagodaEngine can run.
 */
public class MultiAssetExoticInstrumentsTest {

    private static List<Date> threeFixings() {
        final List<Date> dates = new ArrayList<Date>();
        dates.add(new Date(15, Month.January,  2027));
        dates.add(new Date(15, Month.July,     2027));
        dates.add(new Date(15, Month.January,  2028));
        return dates;
    }

    // ---------- HimalayaOption ----------

    @Test
    public void himalayaCallStrikeWiresEuropeanExerciseAtLastFixing() {
        final List<Date> fixings = threeFixings();
        final HimalayaOption h = new HimalayaOption(fixings, 100.0);

        assertNotNull("payoff", h.payoff());
        assertTrue("payoff is plain vanilla", h.payoff() instanceof PlainVanillaPayoff);

        final PlainVanillaPayoff pv = (PlainVanillaPayoff) h.payoff();
        assertEquals(100.0, pv.strike(), 0.0);

        assertTrue("exercise is European", h.exercise() instanceof EuropeanExercise);
        assertEquals("exercise.lastDate matches last fixing",
                fixings.get(fixings.size() - 1), h.exercise().lastDate());
    }

    @Test
    public void himalayaArgumentsValidateAcceptsThreeFixings() {
        final HimalayaOption.ArgumentsImpl args = new HimalayaOption.ArgumentsImpl();
        args.payoff   = new PlainVanillaPayoff(org.jquantlib.instruments.Option.Type.Call, 100.0);
        args.exercise = new EuropeanExercise(new Date(1, Month.January, 2030));
        args.fixingDates = threeFixings();
        args.validate(); // must not throw
    }

    @Test
    public void himalayaArgumentsRejectEmptyFixings() {
        final HimalayaOption.ArgumentsImpl args = new HimalayaOption.ArgumentsImpl();
        args.payoff   = new PlainVanillaPayoff(org.jquantlib.instruments.Option.Type.Call, 100.0);
        args.exercise = new EuropeanExercise(new Date(1, Month.January, 2030));
        args.fixingDates = Collections.<Date>emptyList();
        try {
            args.validate();
            fail("validate() must reject empty fixingDates");
        } catch (final LibraryException expected) {
            assertTrue("message mentions fixing", expected.getMessage().toLowerCase().contains("fixing"));
        }
    }

    // ---------- EverestOption ----------

    @Test
    public void everestUsesNullPayoffAndProvidedExercise() {
        final Exercise eu = new EuropeanExercise(new Date(15, Month.December, 2030));
        final EverestOption e = new EverestOption(1_000_000.0, 0.03, eu);

        assertTrue("payoff is null payoff", e.payoff() instanceof NullPayoff);
        assertSame("exercise pass-through", eu, e.exercise());
    }

    @Test
    public void everestArgumentsValidate() {
        final EverestOption.ArgumentsImpl a = new EverestOption.ArgumentsImpl();
        a.payoff   = new NullPayoff();
        a.exercise = new EuropeanExercise(new Date(15, Month.December, 2030));
        a.notional = 100_000.0;
        a.guarantee = 0.025;
        a.validate();
    }

    @Test
    public void everestArgumentsRejectMissingNotional() {
        final EverestOption.ArgumentsImpl a = new EverestOption.ArgumentsImpl();
        a.payoff   = new NullPayoff();
        a.exercise = new EuropeanExercise(new Date(15, Month.December, 2030));
        // notional not set => stays NULL_REAL
        a.guarantee = 0.025;
        try {
            a.validate();
            fail("validate() must reject missing notional");
        } catch (final LibraryException expected) {
            assertTrue("message mentions notional",
                    expected.getMessage().toLowerCase().contains("notional"));
        }
    }

    @Test
    public void everestArgumentsRejectZeroNotional() {
        final EverestOption.ArgumentsImpl a = new EverestOption.ArgumentsImpl();
        a.payoff   = new NullPayoff();
        a.exercise = new EuropeanExercise(new Date(15, Month.December, 2030));
        a.notional = 0.0;
        a.guarantee = 0.025;
        try {
            a.validate();
            fail("validate() must reject null (zero) notional");
        } catch (final LibraryException expected) {
            assertTrue("message mentions notional",
                    expected.getMessage().toLowerCase().contains("notional"));
        }
    }

    // ---------- PagodaOption ----------

    @Test
    public void pagodaUsesNullPayoffAndEuropeanExerciseAtLastFixing() {
        final List<Date> fixings = threeFixings();
        final PagodaOption p = new PagodaOption(fixings, 0.5, 0.25);

        assertTrue("payoff is null payoff", p.payoff() instanceof NullPayoff);
        assertTrue("exercise is European", p.exercise() instanceof EuropeanExercise);
        assertEquals(fixings.get(fixings.size() - 1), p.exercise().lastDate());
    }

    @Test
    public void pagodaArgumentsValidate() {
        final PagodaOption.ArgumentsImpl a = new PagodaOption.ArgumentsImpl();
        a.payoff   = new NullPayoff();
        a.exercise = new EuropeanExercise(new Date(15, Month.December, 2030));
        a.fixingDates = threeFixings();
        a.roof = 0.5;
        a.fraction = 0.25;
        a.validate();
    }

    @Test
    public void pagodaArgumentsRejectMissingRoof() {
        final PagodaOption.ArgumentsImpl a = new PagodaOption.ArgumentsImpl();
        a.payoff   = new NullPayoff();
        a.exercise = new EuropeanExercise(new Date(15, Month.December, 2030));
        a.fixingDates = threeFixings();
        // roof not set
        a.fraction = 0.25;
        try {
            a.validate();
            fail("validate() must reject missing roof");
        } catch (final LibraryException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("roof"));
        }
    }

}
