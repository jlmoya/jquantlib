/*
 Copyright (C) 2005, 2006 Klaus Spanderen (C++ original).
 Copyright (C) 2026 JQuantLib migration contributors (Java port).

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.processes;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.Euribor1Y;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.legacy.libormarkets.LfmHullWhiteParameterization;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.LowDiscrepancy;
import org.jquantlib.math.randomnumbers.SobolRsg;
import org.jquantlib.math.statistics.GeneralStatistics;
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.MultiPathGenerator;
import org.jquantlib.methods.montecarlo.Sample;
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
import org.junit.Test;

/**
 * Java port of {@code test-suite/libormarketmodelprocess.cpp} v1.42.1
 * (327 LOC, 3 test cases).
 *
 * <p>Status (Phase 5e.5b-CFC-d-228):
 * <ul>
 *   <li>{@code testInitialisation} — body-filled. Exercises
 *       {@link LiborForwardModelProcess#nextIndexReset(double)} as a
 *       std::upper_bound search on the fixing-time vector for every fixing
 *       index across a 5-year sweep of evaluation dates.</li>
 *   <li>{@code testLambdaBootstrapping} — body-filled. Verifies the
 *       {@link LfmHullWhiteParameterization} bootstrap reproduces the
 *       expected lambda sequence from C++
 *       test-suite/libormarketmodelprocess.cpp lines 148-191 to 1e-10
 *       tolerance.</li>
 *   <li>{@code testMonteCarloCapletPricing} — body-filled. Wires
 *       {@link LowDiscrepancy} (Sobol + inverse normal CDF) into a
 *       {@link MultiPathGenerator} and prices a strip of caplets and
 *       ratchet caps under both the 1-factor and 3-factor LFM, comparing
 *       the Monte-Carlo NPVs against C++ cached expectations within the
 *       generator's reported {@code errorEstimate()} tolerance (plus a
 *       1e-5 reference error for the ratchet leg, matching C++).</li>
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

    /** Mirror of C++ {@code makeProcess(volaComp = Matrix())} single-factor
     *  (lines 87-104). The single-factor branch (used by
     *  testLambdaBootstrapping) leaves the correlation matrix empty so
     *  {@link LfmHullWhiteParameterization} defaults to {@code factors=1} and
     *  an all-ones sqrtCorr. */
    private static LiborForwardModelProcess makeProcess() {
        return makeProcess(null);
    }

    /** Mirror of C++ {@code makeProcess(const Matrix& volaComp)}
     *  (lines 87-104). When {@code volaComp} is non-null/non-empty,
     *  factors = volaComp.columns() and the correlation matrix is
     *  {@code volaComp * transpose(volaComp)}. */
    private static LiborForwardModelProcess makeProcess(final Matrix volaComp) {
        final boolean hasVolaComp = (volaComp != null) && !volaComp.empty();
        final int factors = hasVolaComp ? volaComp.columns() : 1;

        final IborIndex index = makeIndex();
        final LiborForwardModelProcess process =
                new LiborForwardModelProcess(LEN, index);

        final Matrix correlation = hasVolaComp
                ? volaComp.mul(volaComp.transpose())
                : null;

        final LfmCovarianceParameterization fct =
                new LfmHullWhiteParameterization(
                        process,
                        makeCapVolCurve(new Settings().evaluationDate()),
                        correlation,
                        factors);
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

    /**
     * Mirror of C++ BOOST_AUTO_TEST_CASE(testMonteCarloCapletPricing)
     * (test-suite/libormarketmodelprocess.cpp:193-323).
     *
     * <p>Pipeline:
     * <ol>
     *   <li>Build a 9x3 vola-component matrix (Hull-White factor loadings,
     *       hard-coded values from C++ source, lines 199-207).</li>
     *   <li>Construct a 1-factor and a 3-factor {@link
     *       LiborForwardModelProcess} via {@link #makeProcess(Matrix)}.</li>
     *   <li>Build a {@link TimeGrid} with 12 intermediate steps over the
     *       fixing-times vector and locate each fixing within the grid.</li>
     *   <li>Wire {@link LowDiscrepancy#makeSequenceGenerator(int, long)}
     *       (Sobol + inverse normal CDF, seed=42) into a
     *       {@link MultiPathGenerator}, with dimension {@code factors *
     *       (grid.size()-1)}.</li>
     *   <li>Draw {@code nrTrails} (250 000 in C++) MultiPaths, extract
     *       LIBOR rates at each fixing date, price caplets (cap rate 4%)
     *       and ratchet caps (previous + 25bp), accumulate into
     *       {@link GeneralStatistics}.</li>
     *   <li>Compare MC mean against C++ cached expectations within the
     *       generator-reported {@code errorEstimate()} (plus 1e-5 reference
     *       error for the ratchet leg).</li>
     * </ol>
     */
    @Test
    public void testMonteCarloCapletPricing() {
        // factor loadings from C++ test (lines 199-207) - Hull-White
        // article, orthogonal-eigenvector normalisation.
        final double[] compValues = {
                0.85549771,  0.46707264,  0.22353259,
                0.91915359,  0.37716089,  0.11360610,
                0.96438280,  0.26413316, -0.01412414,
                0.97939148,  0.13492952, -0.15028753,
                0.95970595, -0.00000000, -0.28100621,
                0.97939148, -0.13492952, -0.15028753,
                0.96438280, -0.26413316, -0.01412414,
                0.91915359, -0.37716089,  0.11360610,
                0.85549771, -0.46707264,  0.22353259
        };
        final Matrix volaComp = new Matrix(9, 3);
        for (int i = 0; i < 9; ++i) {
            for (int j = 0; j < 3; ++j) {
                volaComp.set(i, j, compValues[i * 3 + j]);
            }
        }

        final LiborForwardModelProcess process1 = makeProcess();
        final LiborForwardModelProcess process2 = makeProcess(volaComp);

        final List<Double> tmp = process1.fixingTimes();
        final TimeGrid grid = new TimeGrid(tmp, 12);

        // location[i] = index in grid of fixing-time tmp[i].
        final int[] location = new int[tmp.size()];
        for (int i = 0; i < tmp.size(); ++i) {
            location[i] = -1;
            for (int g = 0; g < grid.size(); ++g) {
                if (grid.get(g) == tmp.get(i).doubleValue()) {
                    location[i] = g;
                    break;
                }
            }
            if (location[i] < 0) {
                fail("fixing time " + tmp.get(i)
                        + " not found in TimeGrid (i=" + i + ")");
            }
        }

        // C++: LowDiscrepancy::rsg_type = InverseCumulativeRsg<SobolRsg,
        //                                  InverseCumulativeNormal>
        // make_sequence_generator(dimension = factors * (grid.size()-1),
        //                         seed = 42).
        final long seed = 42L;
        final InverseCumulativeRsg<SobolRsg, InverseCumulativeNormal> rsg1 =
                LowDiscrepancy.makeSequenceGenerator(
                        process1.factors() * (grid.size() - 1), seed);
        final InverseCumulativeRsg<SobolRsg, InverseCumulativeNormal> rsg2 =
                LowDiscrepancy.makeSequenceGenerator(
                        process2.factors() * (grid.size() - 1), seed);

        final MultiPathGenerator<InverseCumulativeRsg<SobolRsg,
                InverseCumulativeNormal>> generator1 =
                new MultiPathGenerator<InverseCumulativeRsg<SobolRsg,
                        InverseCumulativeNormal>>(process1, grid, rsg1, false);
        final MultiPathGenerator<InverseCumulativeRsg<SobolRsg,
                InverseCumulativeNormal>> generator2 =
                new MultiPathGenerator<InverseCumulativeRsg<SobolRsg,
                        InverseCumulativeNormal>>(process2, grid, rsg2, false);

        // C++ runs nrTrails = 250 000. We honour that to keep the MC error
        // bars (errorEstimate()) tight enough to match the C++ cached
        // expectations within their reported tolerance.
        final int nrTrails = 250000;
        final List<GeneralStatistics> stat1 = new ArrayList<>(process1.size());
        final List<GeneralStatistics> stat2 = new ArrayList<>(process2.size());
        final List<GeneralStatistics> stat3 = new ArrayList<>(process2.size() - 1);
        for (int j = 0; j < process1.size(); ++j) {
            stat1.add(new GeneralStatistics());
        }
        for (int j = 0; j < process2.size(); ++j) {
            stat2.add(new GeneralStatistics());
        }
        for (int j = 0; j < process2.size() - 1; ++j) {
            stat3.add(new GeneralStatistics());
        }

        for (int trial = 0; trial < nrTrails; ++trial) {
            final Sample<MultiPath> path1 = generator1.next();
            final Sample<MultiPath> path2 = generator2.next();

            final double[] rates1 = new double[LEN];
            final double[] rates2 = new double[LEN];
            for (int j = 0; j < process1.size(); ++j) {
                rates1[j] = path1.value().get(j).get(location[j]);
                rates2[j] = path2.value().get(j).get(location[j]);
            }

            final double[] dis1 = process1.discountBond(rates1);
            final double[] dis2 = process2.discountBond(rates2);

            for (int k = 0; k < process1.size(); ++k) {
                final double accrualPeriod = process1.accrualEndTimes().get(k)
                                           - process1.accrualStartTimes().get(k);
                // caplet payoff, cap rate at 4%
                final double payoff1 = Math.max(rates1[k] - 0.04, 0.0) * accrualPeriod;
                final double payoff2 = Math.max(rates2[k] - 0.04, 0.0) * accrualPeriod;
                stat1.get(k).add(dis1[k] * payoff1);
                stat2.get(k).add(dis2[k] * payoff2);

                if (k != 0) {
                    // ratchet cap payoff
                    final double payoff3 = Math.max(
                            rates2[k] - (rates2[k - 1] + 0.0025), 0.0)
                            * accrualPeriod;
                    stat3.get(k - 1).add(dis2[k] * payoff3);
                }
            }
        }

        // C++ cached expectations (lines 276-283).
        final double[] capletNpv = {
                0.000000000000, 0.000002841629, 0.002533279333,
                0.009577143571, 0.017746502618, 0.025216116835,
                0.031608230268, 0.036645683881, 0.039792254012,
                0.041829864365
        };
        final double[] ratchetNpv = {
                0.0082644895, 0.0082754754, 0.0082159966,
                0.0082982822, 0.0083803357, 0.0084366961,
                0.0084173270, 0.0081803406, 0.0079533814
        };

        for (int k = 0; k < process1.size(); ++k) {
            final double calculated1 = stat1.get(k).mean();
            final double tolerance1  = stat1.get(k).errorEstimate();
            final double expected    = capletNpv[k];

            if (Math.abs(calculated1 - expected) > tolerance1) {
                fail("Failed to reproduce expected caplet NPV (1-factor)"
                        + "\n    k:          " + k
                        + "\n    calculated: " + calculated1
                        + "\n    error int:  " + tolerance1
                        + "\n    expected:   " + expected);
            }

            final double calculated2 = stat2.get(k).mean();
            final double tolerance2  = stat2.get(k).errorEstimate();

            if (Math.abs(calculated2 - expected) > tolerance2) {
                fail("Failed to reproduce expected caplet NPV (3-factor)"
                        + "\n    k:          " + k
                        + "\n    calculated: " + calculated2
                        + "\n    error int:  " + tolerance2
                        + "\n    expected:   " + expected);
            }

            if (k != 0) {
                final double calculated3 = stat3.get(k - 1).mean();
                final double tolerance3  = stat3.get(k - 1).errorEstimate();
                final double refError    = 1e-5; // 1e-5 error bars on the reference
                final double expectedRat = ratchetNpv[k - 1];

                if (Math.abs(calculated3 - expectedRat) > tolerance3 + refError) {
                    fail("Failed to reproduce expected ratchet NPV"
                            + "\n    k:          " + k
                            + "\n    calculated: " + calculated3
                            + "\n    error int:  " + (tolerance3 + refError)
                            + "\n    expected:   " + expectedRat);
                }
            }
        }
    }
}
