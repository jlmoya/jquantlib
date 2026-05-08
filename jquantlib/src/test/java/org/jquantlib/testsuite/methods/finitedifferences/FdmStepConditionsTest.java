/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.methods.finitedifferences;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.cashflow.FixedDividend;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Uniform1dMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmAmericanStepCondition;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmBermudanStepCondition;
import org.jquantlib.methods.finitedifferences.utilities.FdmDividendHandler;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.json.JSONArray;
import org.junit.Test;

/**
 * Phase 2l Track B fingerprint test for
 * {@link FdmAmericanStepCondition}, {@link FdmBermudanStepCondition},
 * and {@link FdmDividendHandler}.
 *
 * <p>Cross-validates {@code applyTo()} results against
 * {@code migration-harness/references/methods/finitedifferences/step_conditions.json}
 * (oracle: C++ QuantLib v1.42.1 via step_conditions_probe.cpp).
 *
 * <p>Tier: TIGHT (abs 1e-14 + rel 1e-12). All three classes are pure
 * arithmetic / linear interpolation with no transcendental accumulation.
 * Single {@code @Test} with collect-all-failures pattern.
 */
public class FdmStepConditionsTest {

    /** Simple call payoff inner value calculator: max(location[direction=0] - strike, 0). */
    private static FdmInnerValueCalculator callCalc(final FdmMesherComposite mesher,
                                                    final double strike) {
        return new FdmInnerValueCalculator() {
            @Override
            public double innerValue(final FdmLinearOpIterator iter, final double t) {
                return Math.max(mesher.location(iter, 0) - strike, 0.0);
            }
            @Override
            public double avgInnerValue(final FdmLinearOpIterator iter, final double t) {
                return innerValue(iter, t);
            }
        };
    }

    @Test
    public void fdmStepConditions_matchesCpp() {
        final ReferenceReader reader = ReferenceReader.load(
                "methods/finitedifferences/step_conditions");

        final List<String> failures = new ArrayList<>();

        // ========== FdmAmericanStepCondition ==========

        // Case: american_applyTo_basic
        {
            final String name = "american_applyTo_basic";
            final Uniform1dMesher m1d = new Uniform1dMesher(0.0, 100.0, 5);
            final FdmMesherComposite mesher = new FdmMesherComposite(m1d);
            final FdmAmericanStepCondition cond =
                    new FdmAmericanStepCondition(mesher, callCalc(mesher, 60.0), 0.0);
            final Array a = new Array(new double[]{5.0, 10.0, 20.0, 10.0, 30.0});
            cond.applyTo(a, 0.5);
            checkArrayCase(name, a, reader, failures);
        }

        // Case: american_applyTo_before_start (t < exerciseStart -> no change)
        {
            final String name = "american_applyTo_before_start";
            final Uniform1dMesher m1d = new Uniform1dMesher(0.0, 100.0, 5);
            final FdmMesherComposite mesher = new FdmMesherComposite(m1d);
            final FdmAmericanStepCondition cond =
                    new FdmAmericanStepCondition(mesher, callCalc(mesher, 60.0), 1.0);
            final Array a = new Array(new double[]{5.0, 10.0, 20.0, 10.0, 30.0});
            cond.applyTo(a, 0.5); // t < exerciseStart -> no change
            checkArrayCase(name, a, reader, failures);
        }

        // Case: american_applyTo_at_start (t == exerciseStart -> applies)
        {
            final String name = "american_applyTo_at_start";
            final Uniform1dMesher m1d = new Uniform1dMesher(0.0, 100.0, 5);
            final FdmMesherComposite mesher = new FdmMesherComposite(m1d);
            final FdmAmericanStepCondition cond =
                    new FdmAmericanStepCondition(mesher, callCalc(mesher, 60.0), 0.5);
            final Array a = new Array(new double[]{5.0, 10.0, 20.0, 10.0, 30.0});
            cond.applyTo(a, 0.5);
            checkArrayCase(name, a, reader, failures);
        }

        // Case: american_applyTo_all_replaced
        {
            final String name = "american_applyTo_all_replaced";
            final Uniform1dMesher m1d = new Uniform1dMesher(50.0, 150.0, 5);
            final FdmMesherComposite mesher = new FdmMesherComposite(m1d);
            final FdmAmericanStepCondition cond =
                    new FdmAmericanStepCondition(mesher, callCalc(mesher, 0.0), 0.0);
            final Array a = new Array(new double[]{1.0, 1.0, 1.0, 1.0, 1.0});
            cond.applyTo(a, 0.0);
            checkArrayCase(name, a, reader, failures);
        }

        // Case: american_applyTo_7cell
        {
            final String name = "american_applyTo_7cell";
            final Uniform1dMesher m1d = new Uniform1dMesher(0.0, 120.0, 7);
            final FdmMesherComposite mesher = new FdmMesherComposite(m1d);
            final FdmAmericanStepCondition cond =
                    new FdmAmericanStepCondition(mesher, callCalc(mesher, 40.0), 0.0);
            final Array a = new Array(new double[]{0.0, 0.0, 5.0, 10.0, 15.0, 20.0, 25.0});
            cond.applyTo(a, 1.0);
            checkArrayCase(name, a, reader, failures);
        }

        // ========== FdmBermudanStepCondition ==========

        {
            final Date refDate = new Date(15, Month.January, 2026);
            final DayCounter dc = new Actual365Fixed();
            final Date ex1 = new Date(15, Month.January, 2027);
            final Date ex2 = new Date(15, Month.January, 2028);
            final List<Date> exDates = Arrays.asList(ex1, ex2);
            final Uniform1dMesher m1d = new Uniform1dMesher(0.0, 100.0, 5);
            final FdmMesherComposite mesher = new FdmMesherComposite(m1d);
            final FdmBermudanStepCondition cond =
                    new FdmBermudanStepCondition(exDates, refDate, dc,
                            mesher, callCalc(mesher, 60.0));

            // Case: bermudan_exerciseTimes
            {
                final String name = "bermudan_exerciseTimes";
                final List<Double> times = cond.exerciseTimes();
                final Array aResult = new Array(times.size());
                for (int i = 0; i < times.size(); i++) {
                    aResult.set(i, times.get(i));
                }
                checkArrayCase(name, aResult, reader, failures);
            }

            final double t1 = dc.yearFraction(refDate, ex1);
            final double t2 = dc.yearFraction(refDate, ex2);
            final double tMid = 0.5 * (t1 + t2);

            // Case: bermudan_applyTo_at_t1
            {
                final String name = "bermudan_applyTo_at_t1";
                final Array a = new Array(new double[]{5.0, 10.0, 20.0, 10.0, 30.0});
                cond.applyTo(a, t1);
                checkArrayCase(name, a, reader, failures);
            }

            // Case: bermudan_applyTo_at_t2
            {
                final String name = "bermudan_applyTo_at_t2";
                final Array a = new Array(new double[]{5.0, 10.0, 20.0, 10.0, 30.0});
                cond.applyTo(a, t2);
                checkArrayCase(name, a, reader, failures);
            }

            // Case: bermudan_applyTo_at_tmid (non-exercise -> no change)
            {
                final String name = "bermudan_applyTo_at_tmid";
                final Array a = new Array(new double[]{5.0, 10.0, 20.0, 10.0, 30.0});
                cond.applyTo(a, tMid);
                checkArrayCase(name, a, reader, failures);
            }
        }

        // Case: bermudan_applyTo_3dates_at_t3
        {
            final String name = "bermudan_applyTo_3dates_at_t3";
            final Date refDate = new Date(1, Month.January, 2025);
            final DayCounter dc = new Actual365Fixed();
            final Date ex1 = new Date(1, Month.January, 2026);
            final Date ex2 = new Date(1, Month.January, 2027);
            final Date ex3 = new Date(1, Month.January, 2028);
            final List<Date> exDates = Arrays.asList(ex1, ex2, ex3);
            final Uniform1dMesher m1d = new Uniform1dMesher(0.0, 120.0, 7);
            final FdmMesherComposite mesher = new FdmMesherComposite(m1d);
            final FdmBermudanStepCondition cond =
                    new FdmBermudanStepCondition(exDates, refDate, dc,
                            mesher, callCalc(mesher, 60.0));
            final double t3 = dc.yearFraction(refDate, ex3);
            final Array a = new Array(new double[]{0.0, 0.0, 5.0, 10.0, 15.0, 20.0, 25.0});
            cond.applyTo(a, t3);
            checkArrayCase(name, a, reader, failures);
        }

        // ========== FdmDividendHandler ==========

        {
            final Date refDate = new Date(1, Month.January, 2026);
            final DayCounter dc = new Actual365Fixed();
            final Date divDate = new Date(1, Month.July, 2026);
            final double divTime = dc.yearFraction(refDate, divDate);

            final double logLo = Math.log(50.0);
            final double logHi = Math.log(150.0);
            final Uniform1dMesher m1d = new Uniform1dMesher(logLo, logHi, 5);
            final FdmMesherComposite mesher = new FdmMesherComposite(m1d);

            final DividendSchedule sched = new DividendSchedule();
            sched.add(new FixedDividend(5.0, divDate));

            final FdmDividendHandler handler =
                    new FdmDividendHandler(sched, mesher, refDate, dc, 0);

            // Case: dividendHandler_dividendTimes
            {
                final String name = "dividendHandler_dividendTimes";
                final Array aResult = new Array(1);
                aResult.set(0, handler.dividendTimes().get(0));
                checkArrayCase(name, aResult, reader, failures);
            }

            // Case: dividendHandler_dividends
            {
                final String name = "dividendHandler_dividends";
                final Array aResult = new Array(1);
                aResult.set(0, handler.dividends().get(0));
                checkArrayCase(name, aResult, reader, failures);
            }

            // Case: dividendHandler_applyTo_at_divTime
            {
                final String name = "dividendHandler_applyTo_at_divTime";
                final Array a = new Array(new double[]{0.0, 0.0, 0.0, 5.0, 10.0});
                handler.applyTo(a, divTime);
                checkArrayCase(name, a, reader, failures);
            }

            // Case: dividendHandler_applyTo_nonDiv_noChange
            {
                final String name = "dividendHandler_applyTo_nonDiv_noChange";
                final Array a2 = new Array(new double[]{0.0, 0.0, 0.0, 5.0, 10.0});
                handler.applyTo(a2, divTime + 0.1);
                checkArrayCase(name, a2, reader, failures);
            }
        }

        // Case: dividendHandler_2d_applyTo (2D mesher)
        {
            final String name = "dividendHandler_2d_applyTo";
            final Date refDate = new Date(1, Month.January, 2026);
            final DayCounter dc = new Actual365Fixed();
            final Date divDate = new Date(1, Month.April, 2026);
            final double divTime = dc.yearFraction(refDate, divDate);

            final double logLo = Math.log(80.0);
            final double logHi = Math.log(120.0);
            final Uniform1dMesher m0 = new Uniform1dMesher(logLo, logHi, 4);
            final Uniform1dMesher m1 = new Uniform1dMesher(0.0, 1.0, 3);
            final FdmMesherComposite mesher = new FdmMesherComposite(m0, m1);

            final DividendSchedule sched = new DividendSchedule();
            sched.add(new FixedDividend(10.0, divDate));

            final FdmDividendHandler handler =
                    new FdmDividendHandler(sched, mesher, refDate, dc, 0);

            final int n = mesher.layout().size();
            final Array a = new Array(n);
            for (int i = 0; i < n; i++) {
                a.set(i, i * 2.0);
            }
            handler.applyTo(a, divTime);
            checkArrayCase(name, a, reader, failures);
        }

        // Cases: dividendHandler_twoDivs_applyAt_t1 and _t2
        {
            final Date refDate = new Date(1, Month.January, 2026);
            final DayCounter dc = new Actual365Fixed();
            final Date div1Date = new Date(1, Month.April, 2026);
            final Date div2Date = new Date(1, Month.October, 2026);
            final double t1 = dc.yearFraction(refDate, div1Date);
            final double t2 = dc.yearFraction(refDate, div2Date);

            final double logLo = Math.log(50.0);
            final double logHi = Math.log(150.0);
            final Uniform1dMesher m1d = new Uniform1dMesher(logLo, logHi, 6);
            final FdmMesherComposite mesher = new FdmMesherComposite(m1d);

            final DividendSchedule sched = new DividendSchedule();
            sched.add(new FixedDividend(3.0, div1Date));
            sched.add(new FixedDividend(7.0, div2Date));

            final FdmDividendHandler handler =
                    new FdmDividendHandler(sched, mesher, refDate, dc, 0);

            final Array a = new Array(new double[]{0.0, 2.0, 4.0, 6.0, 8.0, 10.0});
            handler.applyTo(a, t1);
            checkArrayCase("dividendHandler_twoDivs_applyAt_t1", a, reader, failures);

            handler.applyTo(a, t2);
            checkArrayCase("dividendHandler_twoDivs_applyAt_t2", a, reader, failures);
        }

        if (!failures.isEmpty()) {
            fail(failures.size() + " failure(s):\n" + String.join("\n", failures));
        }
    }

    /**
     * Compare each element of {@code actual} against the JSON reference array
     * for the case named {@code name}, using TIGHT tolerance.
     */
    private static void checkArrayCase(
            final String name,
            final Array actual,
            final ReferenceReader reader,
            final List<String> failures) {
        try {
            final JSONArray expected = reader.getCase(name).expectedArray();
            if (expected.length() != actual.size()) {
                failures.add(name + ": array length mismatch expected="
                        + expected.length() + " got=" + actual.size());
                return;
            }
            for (int i = 0; i < expected.length(); i++) {
                final double expVal = expected.getDouble(i);
                final double actVal = actual.get(i);
                if (!Tolerance.tight(actVal, expVal)) {
                    failures.add(String.format(
                            "%s[%d]: expected=%.15g got=%.15g diff=%.3g",
                            name, i, expVal, actVal, actVal - expVal));
                }
            }
        } catch (final AssertionError | IllegalStateException ex) {
            failures.add(name + ": " + ex.getMessage());
        }
    }
}
