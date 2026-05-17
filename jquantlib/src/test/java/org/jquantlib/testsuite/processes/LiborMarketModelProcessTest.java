/*
 Copyright (C) 2005, 2006 Klaus Spanderen (C++ original).
 Copyright (C) 2026 JQuantLib migration contributors (Java port).

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.processes;

import static org.junit.Assert.fail;

import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.Euribor1Y;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.legacy.libormarkets.LfmHullWhiteParameterization;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.processes.LfmCovarianceParameterization;
import org.jquantlib.processes.LiborForwardModelProcess;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.CapletVarianceCurve;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.termstructures.yieldcurves.InterpolatedZeroCurve;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.TimeGrid;
import org.jquantlib.time.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of {@code test-suite/libormarketmodelprocess.cpp} v1.42.1
 * (327 LOC, 3 test cases).
 *
 * <p>Status (Phase 5e.5b-CFC-d-138):
 * <ul>
 *   <li>{@code testInitialisation} — <strong>body-filled</strong>. Exercises
 *       {@link LiborForwardModelProcess#nextIndexReset(double)} as a
 *       std::upper_bound search on the fixing-time vector for every fixing
 *       index across a 5-year sweep of evaluation dates.</li>
 *   <li>{@code testLambdaBootstrapping} — <strong>body-filled</strong>.
 *       Verifies the {@link LfmHullWhiteParameterization} bootstrap reproduces
 *       the expected lambda sequence from C++
 *       test-suite/libormarketmodelprocess.cpp lines 148-191 to 1e-10
 *       tolerance.</li>
 *   <li>{@code testMonteCarloCapletPricing} — still deferred: the C++ test
 *       uses {@code LowDiscrepancy} (Sobol) RSG; the Java mirror would need
 *       {@code GenericLowDiscrepancy} wired into {@link
 *       org.jquantlib.methods.montecarlo.MultiPathGenerator}.</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/libormarketmodelprocess.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class LiborMarketModelProcessTest {

    private static final int LEN = 10;

    /** Mirror of C++ {@code makeIndex()} (libormarketmodelprocess.cpp:44-66). */
    private static IborIndex makeIndex() {
        final DayCounter dayCounter = new Actual360();
        Date[] dates = new Date[] {
                new Date(4, Month.September, 2005),
                new Date(4, Month.September, 2018)
        };
        final double[] rates = new double[] { 0.01, 0.08 };

        final RelinkableHandle<YieldTermStructure> termStructure =
                new RelinkableHandle<YieldTermStructure>(
                        new InterpolatedZeroCurve<Linear>(Linear.class,
                                dates, rates, dayCounter));

        final IborIndex index = new Euribor1Y(termStructure);

        final Date todaysDate = index.fixingCalendar().adjust(
                new Date(4, Month.September, 2005));
        new Settings().setEvaluationDate(todaysDate);

        dates[0] = index.fixingCalendar().advance(todaysDate,
                index.fixingDays(), TimeUnit.Days);

        termStructure.linkTo(new InterpolatedZeroCurve<Linear>(Linear.class,
                dates, rates, dayCounter));

        return index;
    }

    /** Mirror of C++ {@code makeCapVolCurve(todaysDate)}. */
    private static CapletVarianceCurve makeCapVolCurve(final Date todaysDate) {
        final double[] vols = { 14.40, 17.15, 16.81, 16.64, 16.17,
                                15.78, 15.40, 15.21, 14.86, 14.54 };

        final LiborForwardModelProcess process =
                new LiborForwardModelProcess(LEN + 1, makeIndex());

        final Date[] dates = new Date[LEN];
        final double[] capletVols = new double[LEN];
        for (int i = 0; i < LEN; ++i) {
            capletVols[i] = vols[i] / 100.0;
            dates[i] = process.fixingDates().get(i + 1);
        }

        return new CapletVarianceCurve(todaysDate, dates, capletVols,
                new ActualActual(ActualActual.Convention.ISDA));
    }

    /** Mirror of C++ {@code makeProcess(volaComp = Matrix())} (lines 87-104).
     *  The single-factor branch (used by testLambdaBootstrapping) leaves the
     *  correlation matrix empty so {@link LfmHullWhiteParameterization}
     *  defaults to {@code factors=1} and an all-ones sqrtCorr. */
    private static LiborForwardModelProcess makeProcess() {
        final IborIndex index = makeIndex();
        final LiborForwardModelProcess process =
                new LiborForwardModelProcess(LEN, index);

        final LfmCovarianceParameterization fct =
                new LfmHullWhiteParameterization(
                        process,
                        makeCapVolCurve(new Settings().evaluationDate()),
                        /* correlation */ null,
                        /* factors */ 1);
        process.setCovarParam(fct);
        return process;
    }

    @Test
    public void testInitialisation() {
        // Mirror of C++ BOOST_AUTO_TEST_CASE(testInitialisation) lines 107-146.
        final DayCounter dayCounter = new Actual360();
        final RelinkableHandle<YieldTermStructure> termStructure =
                new RelinkableHandle<YieldTermStructure>(
                        new FlatForward(Date.todaysDate(), 0.04, dayCounter));

        final IborIndex index = new Euribor6M(termStructure);

        // ConstantOptionletVolatility is constructed but never read by the
        // C++ test (lines 115-121); omitted here.

        final Calendar calendar = index.fixingCalendar();

        for (int daysOffset = 0; daysOffset < 1825 /* 5 year */; daysOffset += 8) {
            final Date todaysDate = calendar.adjust(Date.todaysDate().add(daysOffset));
            new Settings().setEvaluationDate(todaysDate);
            final Date settlementDate =
                    calendar.advance(todaysDate, index.fixingDays(), TimeUnit.Days);

            termStructure.linkTo(new FlatForward(settlementDate, 0.04, dayCounter));

            final LiborForwardModelProcess process = new LiborForwardModelProcess(60, index);

            final List<Double> fixings = process.fixingTimes();
            for (int i = 1; i < fixings.size() - 1; ++i) {
                final int ileft  = process.nextIndexReset(fixings.get(i) - 0.000001);
                final int iright = process.nextIndexReset(fixings.get(i) + 0.000001);
                final int ii     = process.nextIndexReset(fixings.get(i));

                if ((ileft != i) || (iright != i + 1) || (ii != i + 1)) {
                    fail("Failed to next index resets"
                            + "\n    daysOffset: " + daysOffset
                            + "\n    i:          " + i
                            + "\n    ileft:      " + ileft
                            + "\n    iright:     " + iright
                            + "\n    ii:         " + ii);
                }
            }
        }
    }

    @Test
    public void testLambdaBootstrapping() {
        // Mirror of C++ BOOST_AUTO_TEST_CASE(testLambdaBootstrapping)
        // (test-suite/libormarketmodelprocess.cpp:148-191).
        final double tolerance = 1.0e-10;
        // C++ lambdaExpected[] — Hull-White bootstrap lambdas reproduced
        // exactly from the C++ test source.
        final double[] lambdaExpected = {
                14.3010297550, 19.3821411939, 15.9816590141,
                15.9953118303, 14.0570815635, 13.5687599894,
                12.7477197786, 13.7056638165, 11.6191989567
        };

        final LiborForwardModelProcess process = makeProcess();

        final Matrix covar = process.covariance(0.0, new Array(0), 1.0);

        for (int i = 0; i < 9; ++i) {
            final double calculated = Math.sqrt(covar.get(i + 1, i + 1));
            final double expected = lambdaExpected[i] / 100.0;
            if (Math.abs(calculated - expected) > tolerance) {
                fail("Failed to reproduce expected lambda values"
                        + "\n    i:          " + i
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + expected);
            }
        }

        // Second block (C++ lines 170-191): verify the overridden
        // integratedCovariance(t) matches the slow base-class default
        // (numerical Gauss-Kronrod over diffusion). The Java base
        // implementation requires {@code x.empty() == false} to gate the
        // path, so we wrap the H-W parameterisation in a delegating subclass
        // that exposes only {@code diffusion(t, x)} and inherits the
        // numerical {@code integratedCovariance(t, x)} from the base.
        final LfmCovarianceParameterization param = process.covarParam();
        final List<Double> tmp = process.fixingTimes();
        final TimeGrid grid = new TimeGrid(tmp, 14);

        final BaseDelegating numericalRef = new BaseDelegating(param);
        for (int gi = 0; gi < grid.size(); ++gi) {
            final double t = grid.get(gi);
            final Matrix overridden = param.integratedCovariance(t);
            final Matrix base = numericalRef.integratedCovariance(t);

            final Matrix diff = overridden.sub(base);
            for (int i = 0; i < diff.rows(); ++i) {
                for (int j = 0; j < diff.columns(); ++j) {
                    if (Math.abs(diff.get(i, j)) > tolerance) {
                        fail("Failed to reproduce integrated covariance"
                                + "\n    t: " + t
                                + "\n    i: " + i
                                + "\n    j: " + j
                                + "\n    error: " + diff.get(i, j));
                    }
                }
            }
        }
    }

    /** Trivial wrapper that forwards {@code diffusion} but inherits the
     *  default Gauss-Kronrod-based {@code integratedCovariance(t, x)} from
     *  {@link LfmCovarianceParameterization} (it does NOT override the
     *  method). Used by {@link #testLambdaBootstrapping} to compare the
     *  Hull-White closed-form integral against the numerical baseline. */
    private static final class BaseDelegating extends LfmCovarianceParameterization {
        private final LfmCovarianceParameterization inner_;
        BaseDelegating(final LfmCovarianceParameterization inner) {
            super(inner.size(), inner.factors());
            this.inner_ = inner;
        }
        @Override
        public Matrix diffusion(final double t, final Array x) {
            return inner_.diffusion(t, x);
        }
    }

    @Ignore("Phase 5f.5 — LFM MC caplet pricing pipeline needs Sobol-based "
            + "LowDiscrepancy RSG factory wired into MultiPathGenerator")
    @Test
    public void testMonteCarloCapletPricing() { fail("not implemented"); }
}
