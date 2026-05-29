/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
/*
 Copyright (C) 2007 Marco Bianchetti
 Copyright (C) 2006, 2007 Giorgio Facchinetti
 Copyright (C) 2014 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.termstructures.volatilities.swaption;

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.math.optimization.Problem;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.time.Period;

/**
 * Calibrates the mean-reversion / SABR-beta of a {@link SabrSwaptionVolatilityCube} to a {@link CmsMarket}.
 *
 * <p>Java port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/swaption/cmsmarketcalibration.{hpp,cpp}} — class
 * {@code CmsMarketCalibration}.
 *
 * <p>The deterministic, cross-validated core is the four static transform functions
 * ({@link #betaTransformDirect}, {@link #betaTransformInverse}, {@link #reversionTransformDirect},
 * {@link #reversionTransformInverse}). The {@link #compute} / {@link #computeParametric} methods run a
 * Levenberg-Marquardt (or other) optimizer over a {@link CmsMarket} reprice loop and drive
 * {@link SabrSwaptionVolatilityCube#recalibration}; their numeric outputs are optimizer-path-dependent and
 * are <em>not</em> deterministically cross-validated against C++.
 */
public class CmsMarketCalibration {

    public enum CalibrationType {OnSpread, OnPrice, OnForwardCmsPrice}

    public final Handle< SwaptionVolatilityStructure > volCube_;
    public final CmsMarket cmsMarket_;
    public final Matrix weights_;
    public final CalibrationType calibrationType_;

    public Matrix sparseSabrParameters_;
    public Matrix denseSabrParameters_;
    public Matrix browseCmsMarket_;

    private double error_;
    private EndCriteria.Type endCriteria_;

    //
    // public constructor
    //

    public CmsMarketCalibration(final Handle< SwaptionVolatilityStructure > volCube, final CmsMarket cmsMarket,
            final Matrix weights, final CalibrationType calibrationType) {
        this.volCube_ = volCube;
        this.cmsMarket_ = cmsMarket;
        this.weights_ = weights;
        this.calibrationType_ = calibrationType;

        QL.require(weights.rows() == cmsMarket_.swapLengths().size(),
                "weights number of rows (" + weights.rows() + ") must be equal to number of swap lengths ("
                        + cmsMarket_.swapLengths().size() + ")");
        QL.require(weights.columns() == cmsMarket_.swapTenors().size(),
                "weights number of columns (" + weights.columns() + ") must be equal to number of swap indexes ("
                        + cmsMarket_.swapTenors().size() + ")");
    }

    public double error() {
        return error_;
    }

    public EndCriteria.Type endCriteria() {
        return endCriteria_;
    }

    //
    // static transform functions (deterministic — cross-validated TIGHT)
    //

    public static double betaTransformInverse(final double beta) {
        return Math.sqrt(-Math.log(beta));
    }

    public static double betaTransformDirect(final double y) {
        return Math.max(Math.min(Math.abs(y) < 10.0 ? Math.exp(-(y * y)) : 0.0, 0.999999), 0.000001);
    }

    public static double reversionTransformInverse(final double reversion) {
        return reversion * reversion;
    }

    public static double reversionTransformDirect(final double y) {
        return Math.sqrt(y);
    }

    //
    // compute (constant beta)
    //

    public Array compute(final EndCriteria endCriteria, final OptimizationMethod method, final Array guess,
            final boolean isMeanReversionFixed) {
        final int nSwapTenors = cmsMarket_.swapTenors().size();
        QL.require(isMeanReversionFixed || guess.size() == nSwapTenors + 1,
                "if mean reversion is not fixed, a guess must be provided");
        QL.require(nSwapTenors == guess.size() || nSwapTenors == guess.size() - 1,
                "guess size (" + guess.size() + ") must be equal to swap tenors size (" + nSwapTenors
                        + ") or greater by one if mean reversion is given as last element");
        final boolean isMeanReversionGiven = (nSwapTenors == guess.size() - 1);
        final int nBeta = guess.size() - (isMeanReversionGiven ? 1 : 0);
        Array result;
        if ( isMeanReversionFixed ) {
            final NoConstraint constraint = new NoConstraint();
            final double fixedMeanReversion = isMeanReversionGiven ? guess.get(nBeta) : Constants.NULL_REAL;
            final Array betasGuess = new Array(nBeta);
            for ( int i = 0; i < nBeta; ++i ) {
                betasGuess.set(i, guess.get(i));
            }
            final ObjectiveFunction2 costFunction = new ObjectiveFunction2(this,
                    fixedMeanReversion == Constants.NULL_REAL ? Constants.NULL_REAL
                            : reversionTransformInverse(fixedMeanReversion));
            final Problem problem = new Problem(costFunction, constraint, betasGuess);
            endCriteria_ = method.minimize(problem, endCriteria);
            final Array tmp = problem.currentValue();
            error_ = costFunction.value(tmp);
            result = new Array(nBeta + (isMeanReversionGiven ? 1 : 0));
            for ( int i = 0; i < nBeta; ++i ) {
                result.set(i, betaTransformDirect(tmp.get(i)));
            }
            if ( isMeanReversionGiven ) {
                result.set(nBeta, fixedMeanReversion);
            }
        } else {
            final NoConstraint constraint = new NoConstraint();
            final ObjectiveFunction costFunction = new ObjectiveFunction(this);
            final Array betaReversionGuess = new Array(nBeta + 1);
            for ( int i = 0; i < nBeta; ++i ) {
                betaReversionGuess.set(i, betaTransformInverse(guess.get(i)));
            }
            betaReversionGuess.set(nBeta, reversionTransformInverse(guess.get(nBeta)));
            final Problem problem = new Problem(costFunction, constraint, betaReversionGuess);
            endCriteria_ = method.minimize(problem, endCriteria);
            result = problem.currentValue().clone();
            error_ = costFunction.value(result);
            for ( int i = 0; i < nBeta; ++i ) {
                result.set(i, betaTransformDirect(result.get(i)));
            }
            result.set(nBeta, reversionTransformDirect(result.get(nBeta)));
        }
        final SabrSwaptionVolatilityCube volCubeBySabr = (SabrSwaptionVolatilityCube) volCube_.currentLink();
        volCubeBySabr.updateAfterRecalibration();
        sparseSabrParameters_ = volCubeBySabr.sparseSabrParameters();
        denseSabrParameters_ = volCubeBySabr.denseSabrParameters();
        browseCmsMarket_ = cmsMarket_.browse();

        return result;
    }

    //
    // compute (beta termstructure)
    //

    public Matrix compute(final EndCriteria endCriteria, final OptimizationMethod method, final Matrix guess,
            final boolean isMeanReversionFixed) {
        return compute(endCriteria, method, guess, isMeanReversionFixed, Constants.NULL_REAL);
    }

    public Matrix compute(final EndCriteria endCriteria, final OptimizationMethod method, final Matrix guess,
            final boolean isMeanReversionFixed, final double meanReversionGuess) {
        final int nSwapTenors = cmsMarket_.swapTenors().size();
        final int nSwapLengths = cmsMarket_.swapLengths().size();
        QL.require(isMeanReversionFixed || meanReversionGuess != Constants.NULL_REAL,
                "if mean reversion is not fixed, a guess must be provided");
        QL.require(nSwapTenors == guess.columns(),
                "number of swap tenors (" + nSwapTenors + ") must be equal to number of guess columns ("
                        + guess.columns() + ")");
        QL.require(nSwapLengths == guess.rows(),
                "number of swap lengths (" + nSwapLengths + ") must be equal to number of guess rows (" + guess.rows()
                        + ")");
        Matrix result;
        final int nBeta = nSwapTenors * nSwapLengths;
        if ( isMeanReversionFixed ) {
            final NoConstraint constraint = new NoConstraint();
            final Array betasGuess = new Array(nBeta);
            for ( int i = 0; i < nSwapTenors; ++i ) {
                for ( int j = 0; j < nSwapLengths; ++j ) {
                    betasGuess.set(i * nSwapLengths + j, betaTransformInverse(guess.get(j, i)));
                }
            }
            final ObjectiveFunction4 costFunction = new ObjectiveFunction4(this,
                    meanReversionGuess == Constants.NULL_REAL ? meanReversionGuess
                            : reversionTransformInverse(meanReversionGuess));
            final Problem problem = new Problem(costFunction, constraint, betasGuess);
            endCriteria_ = method.minimize(problem, endCriteria);
            final Array tmp = problem.currentValue();
            error_ = costFunction.value(tmp);
            result = new Matrix(nSwapLengths, nSwapTenors + (meanReversionGuess != Constants.NULL_REAL ? 1 : 0));
            for ( int i = 0; i < nSwapTenors; ++i ) {
                for ( int j = 0; j < nSwapLengths; ++j ) {
                    result.set(j, i, betaTransformDirect(tmp.get(i * nSwapLengths + j)));
                }
            }
            if ( meanReversionGuess != Constants.NULL_REAL ) {
                for ( int j = 0; j < nSwapLengths; ++j ) {
                    result.set(j, nSwapTenors, meanReversionGuess);
                }
            }
        } else {
            final NoConstraint constraint = new NoConstraint();
            final Array betasReversionGuess = new Array(nBeta + 1);
            for ( int i = 0; i < nSwapTenors; ++i ) {
                for ( int j = 0; j < nSwapLengths; ++j ) {
                    betasReversionGuess.set(i * nSwapLengths + j, betaTransformInverse(guess.get(j, i)));
                }
            }
            betasReversionGuess.set(nBeta, reversionTransformInverse(meanReversionGuess));
            final ObjectiveFunction3 costFunction = new ObjectiveFunction3(this);
            final Problem problem = new Problem(costFunction, constraint, betasReversionGuess);
            endCriteria_ = method.minimize(problem, endCriteria);
            final Array tmp = problem.currentValue();
            error_ = costFunction.value(tmp);
            result = new Matrix(nSwapLengths, nSwapTenors + 1);
            for ( int i = 0; i < nSwapTenors; ++i ) {
                for ( int j = 0; j < nSwapLengths; ++j ) {
                    result.set(j, i, betaTransformDirect(tmp.get(i * nSwapLengths + j)));
                }
            }
            for ( int j = 0; j < nSwapLengths; ++j ) {
                result.set(j, nSwapTenors, reversionTransformDirect(tmp.get(nBeta)));
            }
        }
        final SabrSwaptionVolatilityCube volCubeBySabr = (SabrSwaptionVolatilityCube) volCube_.currentLink();
        volCubeBySabr.updateAfterRecalibration();
        sparseSabrParameters_ = volCubeBySabr.sparseSabrParameters();
        denseSabrParameters_ = volCubeBySabr.denseSabrParameters();
        browseCmsMarket_ = cmsMarket_.browse();

        return result;
    }

    //
    // computeParametric (beta parametric termstructure)
    //

    public Matrix computeParametric(final EndCriteria endCriteria, final OptimizationMethod method, final Matrix guess,
            final boolean isMeanReversionFixed) {
        return computeParametric(endCriteria, method, guess, isMeanReversionFixed, Constants.NULL_REAL);
    }

    public Matrix computeParametric(final EndCriteria endCriteria, final OptimizationMethod method, final Matrix guess,
            final boolean isMeanReversionFixed, final double meanReversionGuess) {
        final int nSwapTenors = cmsMarket_.swapTenors().size();
        final int nSwapLengths = cmsMarket_.swapLengths().size();
        QL.require(isMeanReversionFixed || meanReversionGuess != Constants.NULL_REAL,
                "if mean reversion is not fixed, a guess must be provided");
        QL.require(nSwapTenors == guess.columns(),
                "number of swap tenors (" + nSwapTenors + ") must be equal to number of guess columns ("
                        + guess.columns() + ")");
        QL.require(3 == guess.rows(),
                "number of parameters (" + 3 + ") must be equal to number of guess rows (" + guess.rows() + ")");

        Matrix result;
        final int nParams = nSwapTenors * 3;
        if ( isMeanReversionFixed ) {
            final NoConstraint constraint = new NoConstraint();
            final Array betasGuess = new Array(nParams);
            for ( int i = 0; i < nSwapTenors; ++i ) {
                for ( int j = 0; j < nParams; ++j ) {
                    betasGuess.set(i * 3 + j, (j == 0 || j == 1) ? betaTransformInverse(guess.get(j, i))
                            : Math.sqrt(guess.get(j, i)));
                }
            }
            final ObjectiveFunction5 costFunction = new ObjectiveFunction5(this,
                    meanReversionGuess == Constants.NULL_REAL ? meanReversionGuess
                            : reversionTransformInverse(meanReversionGuess));
            final Problem problem = new Problem(costFunction, constraint, betasGuess);
            endCriteria_ = method.minimize(problem, endCriteria);
            final Array tmp = problem.currentValue();
            error_ = costFunction.value(tmp);
            result = new Matrix(3, nSwapTenors + (meanReversionGuess != Constants.NULL_REAL ? 1 : 0));
            for ( int i = 0; i < nSwapTenors; ++i ) {
                for ( int j = 0; j < 3; ++j ) {
                    result.set(j, i, (j == 0 || j == 1) ? betaTransformDirect(tmp.get(i * 3 + j))
                            : tmp.get(i * 3 + j) * tmp.get(i * 3 + j));
                }
            }
            if ( meanReversionGuess != Constants.NULL_REAL ) {
                for ( int j = 0; j < nSwapLengths; ++j ) {
                    result.set(j, nSwapTenors, meanReversionGuess);
                }
            }
        } else {
            final NoConstraint constraint = new NoConstraint();
            final Array betasReversionGuess = new Array(nParams + 1);
            for ( int i = 0; i < nSwapTenors; ++i ) {
                for ( int j = 0; j < nParams; ++j ) {
                    betasReversionGuess.set(i * nSwapLengths + j, (j == 0 || j == 1) ? betaTransformInverse(
                            guess.get(j, i)) : Math.sqrt(guess.get(j, i)));
                }
            }
            betasReversionGuess.set(nParams, reversionTransformInverse(meanReversionGuess));
            final ObjectiveFunction6 costFunction = new ObjectiveFunction6(this);
            final Problem problem = new Problem(costFunction, constraint, betasReversionGuess);
            endCriteria_ = method.minimize(problem, endCriteria);
            final Array tmp = problem.currentValue();
            error_ = costFunction.value(tmp);
            result = new Matrix(3, nSwapTenors + 1);
            for ( int i = 0; i < nSwapTenors; ++i ) {
                for ( int j = 0; j < 3; ++j ) {
                    result.set(j, i, (j == 0 || j == 1) ? betaTransformDirect(tmp.get(i * nSwapLengths + j))
                            : tmp.get(i * 3 + j) * tmp.get(i * 3 + j));
                }
            }
            for ( int j = 0; j < nSwapLengths; ++j ) {
                result.set(j, nSwapTenors, reversionTransformDirect(tmp.get(nParams)));
            }
        }

        final SabrSwaptionVolatilityCube volCubeBySabr = (SabrSwaptionVolatilityCube) volCube_.currentLink();
        volCubeBySabr.updateAfterRecalibration();
        sparseSabrParameters_ = volCubeBySabr.sparseSabrParameters();
        denseSabrParameters_ = volCubeBySabr.denseSabrParameters();
        browseCmsMarket_ = cmsMarket_.browse();

        return result;
    }

    //
    // Objective functions (inner classes mirroring the C++ anonymous-namespace cost functions)
    //

    private static class ObjectiveFunction extends CostFunction {
        protected final CmsMarketCalibration smileAndCms_;
        protected final Handle< SwaptionVolatilityStructure > volCube_;
        protected final CmsMarket cmsMarket_;
        protected final Matrix weights_;
        protected final CalibrationType calibrationType_;

        ObjectiveFunction(final CmsMarketCalibration smileAndCms) {
            this.smileAndCms_ = smileAndCms;
            this.volCube_ = smileAndCms.volCube_;
            this.cmsMarket_ = smileAndCms.cmsMarket_;
            this.weights_ = smileAndCms.weights_;
            this.calibrationType_ = smileAndCms.calibrationType_;
        }

        @Override
        public double value(final Array x) {
            updateVolatilityCubeAndCmsMarket(x);
            return switchErrorFunctionOnCalibrationType();
        }

        @Override
        public Array values(final Array x) {
            updateVolatilityCubeAndCmsMarket(x);
            return switchErrorsFunctionOnCalibrationType();
        }

        protected void updateVolatilityCubeAndCmsMarket(final Array x) {
            final List< Period > swapTenors = cmsMarket_.swapTenors();
            final int nSwapTenors = swapTenors.size();
            QL.require(nSwapTenors + 1 == x.size(), "bad calibration guess nSwapTenors+1 != x.size()");
            final SabrSwaptionVolatilityCube volCubeBySabr = (SabrSwaptionVolatilityCube) volCube_.currentLink();
            for ( int i = 0; i < nSwapTenors; ++i ) {
                volCubeBySabr.recalibration(betaTransformDirect(x.get(i)), swapTenors.get(i));
            }
            final double meanReversion = reversionTransformDirect(x.get(nSwapTenors));
            cmsMarket_.reprice(volCube_, meanReversion);
        }

        protected double switchErrorFunctionOnCalibrationType() {
            switch ( calibrationType_ ) {
                case OnSpread:
                    return cmsMarket_.weightedSpreadError(weights_);
                case OnPrice:
                    return cmsMarket_.weightedSpotNpvError(weights_);
                case OnForwardCmsPrice:
                    return cmsMarket_.weightedFwdNpvError(weights_);
                default:
                    throw new IllegalArgumentException("unknown/illegal calibration type");
            }
        }

        protected Array switchErrorsFunctionOnCalibrationType() {
            switch ( calibrationType_ ) {
                case OnSpread:
                    return cmsMarket_.weightedSpreadErrors(weights_);
                case OnPrice:
                    return cmsMarket_.weightedSpotNpvErrors(weights_);
                case OnForwardCmsPrice:
                    return cmsMarket_.weightedFwdNpvErrors(weights_);
                default:
                    throw new IllegalArgumentException("unknown/illegal calibration type");
            }
        }
    }

    /** Constant beta, fixed mean reversion. */
    private static final class ObjectiveFunction2 extends ObjectiveFunction {
        private final double fixedMeanReversion_;

        ObjectiveFunction2(final CmsMarketCalibration smileAndCms, final double fixedMeanReversion) {
            super(smileAndCms);
            this.fixedMeanReversion_ = fixedMeanReversion;
        }

        @Override
        protected void updateVolatilityCubeAndCmsMarket(final Array x) {
            final List< Period > swapTenors = cmsMarket_.swapTenors();
            final int nSwapTenors = swapTenors.size();
            QL.require(nSwapTenors == x.size(), "bad calibration guess nSwapTenors != x.size()");
            final SabrSwaptionVolatilityCube volCubeBySabr = (SabrSwaptionVolatilityCube) volCube_.currentLink();
            for ( int i = 0; i < nSwapTenors; ++i ) {
                volCubeBySabr.recalibration(betaTransformDirect(x.get(i)), swapTenors.get(i));
            }
            cmsMarket_.reprice(volCube_, fixedMeanReversion_ == Constants.NULL_REAL ? Constants.NULL_REAL
                    : reversionTransformDirect(fixedMeanReversion_));
        }
    }

    /** Beta termstructure, free mean reversion. */
    private static final class ObjectiveFunction3 extends ObjectiveFunction {
        ObjectiveFunction3(final CmsMarketCalibration smileAndCms) {
            super(smileAndCms);
        }

        @Override
        protected void updateVolatilityCubeAndCmsMarket(final Array x) {
            final List< Period > swapTenors = cmsMarket_.swapTenors();
            final List< Period > swapLengths = cmsMarket_.swapLengths();
            final int nSwapTenors = swapTenors.size();
            final int nSwapLengths = swapLengths.size();
            QL.require((nSwapLengths * nSwapTenors) + 1 == x.size(),
                    "bad calibration guess (nSwapLengths*nSwapTenors)+1 != x.size()");
            final SabrSwaptionVolatilityCube volCubeBySabr = (SabrSwaptionVolatilityCube) volCube_.currentLink();
            for ( int i = 0; i < nSwapTenors; ++i ) {
                final double[] beta = new double[nSwapLengths];
                for ( int j = 0; j < nSwapLengths; ++j ) {
                    beta[j] = betaTransformDirect(x.get(i * nSwapLengths + j));
                }
                volCubeBySabr.recalibration(swapLengths, beta, swapTenors.get(i));
            }
            final double meanReversion = reversionTransformDirect(x.get(nSwapLengths + nSwapTenors));
            cmsMarket_.reprice(volCube_, meanReversion);
        }
    }

    /** Beta termstructure, fixed mean reversion. */
    private static final class ObjectiveFunction4 extends ObjectiveFunction {
        private final double fixedMeanReversion_;

        ObjectiveFunction4(final CmsMarketCalibration smileAndCms, final double fixedMeanReversion) {
            super(smileAndCms);
            this.fixedMeanReversion_ = fixedMeanReversion;
        }

        @Override
        protected void updateVolatilityCubeAndCmsMarket(final Array x) {
            final List< Period > swapTenors = cmsMarket_.swapTenors();
            final List< Period > swapLengths = cmsMarket_.swapLengths();
            final int nSwapTenors = swapTenors.size();
            final int nSwapLengths = swapLengths.size();
            QL.require((nSwapLengths * nSwapTenors) == x.size(),
                    "bad calibration guess (nSwapLengths*nSwapTenors) != x.size()");
            final SabrSwaptionVolatilityCube volCubeBySabr = (SabrSwaptionVolatilityCube) volCube_.currentLink();
            for ( int i = 0; i < nSwapTenors; ++i ) {
                final double[] beta = new double[nSwapLengths];
                for ( int j = 0; j < nSwapLengths; ++j ) {
                    beta[j] = betaTransformDirect(x.get(i * nSwapLengths + j));
                }
                volCubeBySabr.recalibration(swapLengths, beta, swapTenors.get(i));
            }
            cmsMarket_.reprice(volCube_, fixedMeanReversion_ == Constants.NULL_REAL ? Constants.NULL_REAL
                    : reversionTransformDirect(fixedMeanReversion_));
        }
    }

    /** Beta parametric termstructure, fixed mean reversion. */
    private static final class ObjectiveFunction5 extends ObjectiveFunction {
        private final double fixedMeanReversion_;

        ObjectiveFunction5(final CmsMarketCalibration smileAndCms, final double fixedMeanReversion) {
            super(smileAndCms);
            this.fixedMeanReversion_ = fixedMeanReversion;
        }

        @Override
        protected void updateVolatilityCubeAndCmsMarket(final Array x) {
            final List< Period > swapTenors = cmsMarket_.swapTenors();
            final List< Period > swapLengths = cmsMarket_.swapLengths();
            final int nSwapTenors = swapTenors.size();
            final int nSwapLengths = swapLengths.size();
            QL.require((3 * nSwapTenors) == x.size(), "bad calibration guess (3*nSwapTenors) != x.size()");
            final SabrSwaptionVolatilityCube volCubeBySabr = (SabrSwaptionVolatilityCube) volCube_.currentLink();
            for ( int i = 0; i < nSwapTenors; ++i ) {
                final double betaInf = betaTransformDirect(x.get(0 + 3 * i));
                final double beta0 = betaTransformDirect(x.get(1 + 3 * i));
                final double decay = x.get(2 + 3 * i) * x.get(2 + 3 * i);
                final double[] beta = new double[nSwapLengths];
                for ( int j = 0; j < beta.length; ++j ) {
                    final double t = smileAndCms_.volCube_.currentLink()
                            .timeFromReference(smileAndCms_.volCube_.currentLink().optionDateFromTenor(
                                    swapLengths.get(j)));
                    beta[j] = betaInf + (beta0 - betaInf) * Math.exp(-decay * t);
                }
                volCubeBySabr.recalibration(swapLengths, beta, swapTenors.get(i));
            }
            cmsMarket_.reprice(volCube_, fixedMeanReversion_ == Constants.NULL_REAL ? Constants.NULL_REAL
                    : reversionTransformDirect(fixedMeanReversion_));
        }
    }

    /** Beta parametric termstructure, free mean reversion. */
    private static final class ObjectiveFunction6 extends ObjectiveFunction {
        ObjectiveFunction6(final CmsMarketCalibration smileAndCms) {
            super(smileAndCms);
        }

        @Override
        protected void updateVolatilityCubeAndCmsMarket(final Array x) {
            final List< Period > swapTenors = cmsMarket_.swapTenors();
            final List< Period > swapLengths = cmsMarket_.swapLengths();
            final int nSwapTenors = swapTenors.size();
            final int nSwapLengths = swapLengths.size();
            QL.require((3 * nSwapTenors) == x.size(), "bad calibration guess (3*nSwapTenors) != x.size()");
            final SabrSwaptionVolatilityCube volCubeBySabr = (SabrSwaptionVolatilityCube) volCube_.currentLink();
            for ( int i = 0; i < nSwapTenors; ++i ) {
                final double betaInf = betaTransformDirect(x.get(0 + 3 * i));
                final double beta0 = betaTransformDirect(x.get(1 + 3 * i));
                final double decay = x.get(2 + 3 * i) * x.get(2 + 3 * i);
                final double[] beta = new double[nSwapLengths];
                for ( int j = 0; j < beta.length; ++j ) {
                    final double t = smileAndCms_.volCube_.currentLink()
                            .timeFromReference(smileAndCms_.volCube_.currentLink().optionDateFromTenor(
                                    swapLengths.get(j)));
                    beta[j] = betaInf + (beta0 - betaInf) * Math.exp(-decay * t);
                }
                volCubeBySabr.recalibration(swapLengths, beta, swapTenors.get(i));
            }
            final double meanReversion = reversionTransformDirect(x.get(3 * nSwapTenors));
            cmsMarket_.reprice(volCube_, meanReversion);
        }
    }
}
