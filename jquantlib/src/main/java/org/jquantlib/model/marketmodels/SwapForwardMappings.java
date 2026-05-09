/*
Copyright (C) 2026 Jose Moya

This source code is release under the BSD License.

This file is part of JQuantLib, a free-software/open-source library
for financial quantitative analysts and developers - http://jquantlib.org/

JQuantLib is free software: you can redistribute it and/or modify it
under the terms of the JQuantLib license.  You should have received a
copy of the license along with this program; if not, please email
<jquant-devel@lists.sourceforge.net>. The license is also available online at
<http://www.jquantlib.org/index.php/LICENSE.TXT>.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE.  See the license for more details.

JQuantLib is based on QuantLib. http://quantlib.org/
When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2006 Ferdinando Ametrano
 Copyright (C) 2006 Marco Bianchetti
 Copyright (C) 2006 Giorgio Facchinetti
 Copyright (C) 2006, 2008 Mark Joshi
*/

package org.jquantlib.model.marketmodels;

import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;

/**
 * Utility functions for mapping between swap rates and forward rates.
 *
 * <p>Java port of {@code ql/models/marketmodels/swapforwardmappings.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>Phase 3j L0.5 — {@link #swaptionImpliedVolatility} now active (depends on
 * {@link MarketModel}, which lands in Phase 3i).
 */
public final class SwapForwardMappings {

    /** Prevent instantiation — all methods are static. */
    private SwapForwardMappings() {}

    /**
     * Computes the annuity of an arbitrary swap rate over {@code [startIndex, endIndex)},
     * discounted to the numeraire {@code numeraireIndex}.
     *
     * <p>Mirrors {@code SwapForwardMappings::annuity} in C++.
     *
     * @param cs             curve state
     * @param startIndex     first rate index of the swap
     * @param endIndex       one past last rate index of the swap
     * @param numeraireIndex numeraire index for discounting
     * @return annuity value in units of the numeraire bond
     */
    public static double annuity(final CurveState cs,
                                 final int startIndex,
                                 final int endIndex,
                                 final int numeraireIndex) {
        double ann = 0.0;
        for (int i = startIndex; i < endIndex; ++i) {
            ann += cs.rateTaus()[i] * cs.discountRatio(i + 1, numeraireIndex);
        }
        return ann;
    }

    /**
     * Computes the derivative {@code dsr/df[forwardIndex]} of the swap rate over
     * {@code [startIndex, endIndex)} with respect to the forward rate at
     * {@code forwardIndex}.
     *
     * <p>Returns 0 if {@code forwardIndex < startIndex} or
     * {@code forwardIndex >= endIndex}.
     *
     * <p>Mirrors {@code SwapForwardMappings::swapDerivative} in C++.
     *
     * @param cs           curve state
     * @param startIndex   first index of the swap
     * @param endIndex     one past last index of the swap
     * @param forwardIndex the forward rate to differentiate with respect to
     * @return partial derivative value
     */
    public static double swapDerivative(final CurveState cs,
                                        final int startIndex,
                                        final int endIndex,
                                        final int forwardIndex) {
        if (forwardIndex < startIndex) return 0.0;
        if (forwardIndex >= endIndex)  return 0.0;

        final double numerator  = cs.discountRatio(startIndex, endIndex) - 1.0;
        final double swapAnnuity = annuity(cs, startIndex, endIndex, endIndex);

        final double tau   = cs.rateTaus()[forwardIndex];
        final double f     = cs.forwardRate(forwardIndex);
        final double ratio = tau / (1.0 + tau * f);

        final double part1 = ratio * (numerator + 1.0) / swapAnnuity;
        final double part2;
        if (forwardIndex >= 1) {
            part2 = numerator / (swapAnnuity * swapAnnuity)
                    * ratio * annuity(cs, startIndex, forwardIndex, endIndex);
        } else {
            part2 = 0.0;
        }

        return part1 - part2;
    }

    /**
     * Returns the {@code dsr[i]/df[j]} Jacobian between coterminal swap rates
     * and forward rates.
     *
     * <p>Result is an {@code n x n} lower-triangular matrix (upper triangle is
     * non-zero as well; see C++ for details).
     *
     * <p>Mirrors {@code SwapForwardMappings::coterminalSwapForwardJacobian}.
     *
     * @param cs curve state
     * @return n&times;n Jacobian matrix
     */
    public static Matrix coterminalSwapForwardJacobian(final CurveState cs) {
        final int n = cs.numberOfRates();
        final double[] f   = cs.forwardRates();
        final double[] tau = cs.rateTaus();

        // coterminal floating leg values: a[k] = discountRatio(k,n) - 1
        final double[] a = new double[n];
        for (int k = 0; k < n; ++k) {
            a[k] = cs.discountRatio(k, n) - 1.0;
        }

        final Matrix jacobian = new Matrix(n, n);
        for (int row = 0; row < n; ++row)
            for (int col = 0; col < n; ++col)
                jacobian.set(row, col, 0.0);

        for (int i = 0; i < n; ++i) {       // i = swap rate index
            for (int j = i; j < n; ++j) {   // j = forward rate index
                final double bi = cs.coterminalSwapAnnuity(n, i);
                final double bj = cs.coterminalSwapAnnuity(n, j);
                final double val =
                        tau[j] / cs.coterminalSwapAnnuity(j + 1, i)
                        + tau[j] / (1.0 + f[j] * tau[j])
                                * (-a[j] * bi + a[i] * bj) / (bi * bi);
                jacobian.set(i, j, val);
            }
        }
        return jacobian;
    }

    /**
     * Returns the Z matrix to switch base from forward rates to coterminal swap
     * rates, applying a uniform displacement.
     *
     * <p>Mirrors {@code SwapForwardMappings::coterminalSwapZedMatrix}.
     *
     * @param cs           curve state
     * @param displacement common displacement for all rates
     * @return n&times;n Z matrix
     */
    public static Matrix coterminalSwapZedMatrix(final CurveState cs,
                                                 final double displacement) {
        final int n = cs.numberOfRates();
        final Matrix zMatrix = coterminalSwapForwardJacobian(cs);
        final double[] f  = cs.forwardRates();
        final double[] sr = cs.coterminalSwapRates();
        for (int i = 0; i < n; ++i) {
            for (int j = i; j < n; ++j) {
                zMatrix.set(i, j, zMatrix.get(i, j) * (f[j] + displacement) / (sr[i] + displacement));
            }
        }
        return zMatrix;
    }

    /**
     * Returns the {@code dsr[i]/df[j]} Jacobian between coinitial swap rates
     * and forward rates.
     *
     * <p>Mirrors {@code SwapForwardMappings::coinitialSwapForwardJacobian}.
     *
     * @param cs curve state
     * @return n&times;n Jacobian matrix
     */
    public static Matrix coinitialSwapForwardJacobian(final CurveState cs) {
        final int n = cs.numberOfRates();
        final Matrix jacobian = new Matrix(n, n);
        for (int row = 0; row < n; ++row)
            for (int col = 0; col < n; ++col)
                jacobian.set(row, col, 0.0);

        for (int i = 0; i < n; ++i) {       // i = swap rate index
            for (int j = 0; j < n; ++j) {   // j = forward rate index
                jacobian.set(i, j, swapDerivative(cs, 0, i + 1, j));
            }
        }
        return jacobian;
    }

    /**
     * Returns the Z matrix to switch base from forward rates to coinitial swap
     * rates, applying a uniform displacement.
     *
     * <p>Mirrors {@code SwapForwardMappings::coinitialSwapZedMatrix}.
     *
     * @param cs           curve state
     * @param displacement common displacement for all rates
     * @return n&times;n Z matrix
     */
    public static Matrix coinitialSwapZedMatrix(final CurveState cs,
                                                final double displacement) {
        final int n = cs.numberOfRates();
        final Matrix zMatrix = coinitialSwapForwardJacobian(cs);
        final double[] f = cs.forwardRates();

        // coinitial swap rates: sr[i] = cs.cmSwapRate(0, i+1)
        final double[] sr = new double[n];
        for (int i = 0; i < n; ++i) {
            sr[i] = cs.cmSwapRate(0, i + 1);
        }

        for (int i = 0; i < n; ++i) {
            for (int j = i; j < n; ++j) {
                zMatrix.set(i, j, zMatrix.get(i, j) * (f[j] + displacement) / (sr[i] + displacement));
            }
        }
        return zMatrix;
    }

    /**
     * Returns the {@code dsr[i]/df[j]} Jacobian between constant-maturity swap
     * (CMS) rates and forward rates.
     *
     * <p>Mirrors {@code SwapForwardMappings::cmSwapForwardJacobian}.
     *
     * @param cs               curve state
     * @param spanningForwards number of consecutive forwards spanned by each CMS rate
     * @return n&times;n Jacobian matrix
     */
    public static Matrix cmSwapForwardJacobian(final CurveState cs,
                                               final int spanningForwards) {
        final int n = cs.numberOfRates();
        final Matrix jacobian = new Matrix(n, n);
        for (int row = 0; row < n; ++row)
            for (int col = 0; col < n; ++col)
                jacobian.set(row, col, 0.0);

        for (int i = 0; i < n; ++i) {       // i = swap rate index
            for (int j = 0; j < n; ++j) {   // j = forward rate index
                jacobian.set(i, j, swapDerivative(cs, i, Math.min(n, i + spanningForwards), j));
            }
        }
        return jacobian;
    }

    /**
     * Returns the Z matrix to switch base from forward rates to CMS rates,
     * applying a uniform displacement.
     *
     * <p>Mirrors {@code SwapForwardMappings::cmSwapZedMatrix}.
     *
     * @param cs               curve state
     * @param spanningForwards number of consecutive forwards spanned by each CMS rate
     * @param displacement     common displacement for all rates
     * @return n&times;n Z matrix
     */
    public static Matrix cmSwapZedMatrix(final CurveState cs,
                                         final int spanningForwards,
                                         final double displacement) {
        final int n = cs.numberOfRates();
        final Matrix zMatrix = cmSwapForwardJacobian(cs, spanningForwards);
        final double[] f = cs.forwardRates();

        // CMS rates: sr[i] = cs.cmSwapRate(i, spanningForwards)
        final double[] sr = new double[n];
        for (int i = 0; i < n; ++i) {
            sr[i] = cs.cmSwapRate(i, spanningForwards);
        }

        for (int i = 0; i < n; ++i) {
            for (int j = i; j < n; ++j) {
                zMatrix.set(i, j, zMatrix.get(i, j) * (f[j] + displacement) / (sr[i] + displacement));
            }
        }
        return zMatrix;
    }

    /**
     * Computes the implied volatility of a swaption using the freezing-coefficients
     * methodology of Brace-Gatarek-Musiela.
     *
     * <p>Mirrors {@code SwapForwardMappings::swaptionImpliedVolatility} in C++.
     *
     * @param volStructure market model providing the pseudo-root volatility structure
     * @param startIndex   start index of the underlying swap
     * @param endIndex     end index (one past last forward) of the underlying swap
     * @return implied Black volatility
     */
    public static double swaptionImpliedVolatility(final MarketModel volStructure,
                                                   final int startIndex,
                                                   final int endIndex) {
        if (startIndex >= endIndex) {
            throw new IllegalArgumentException(
                    "start index must be before end index in swaptionImpliedVolatility");
        }

        final LMMCurveState cs = new LMMCurveState(
                volStructure.evolution().rateTimes());
        cs.setOnForwardRates(volStructure.initialRates());
        final double displacement = volStructure.displacements()[0];

        final Matrix cmsZed = cmSwapZedMatrix(cs, endIndex - startIndex, displacement);

        double variance = 0.0;
        int index = 0;

        final EvolutionDescription evolution = volStructure.evolution();
        final int factors = volStructure.numberOfFactors();
        final int[] firstAlive = evolution.firstAliveRate();

        while (index < evolution.numberOfSteps()
                && startIndex >= firstAlive[index]) {
            final Matrix thisPseudo = volStructure.pseudoRoot(index);
            double thisVariance = 0.0;
            for (int f = 0; f < factors; ++f) {
                double sum = 0.0;
                for (int j = startIndex; j < endIndex; ++j) {
                    sum += cmsZed.get(startIndex, j) * thisPseudo.get(j, f);
                }
                thisVariance += sum * sum;
            }
            variance += thisVariance;
            ++index;
        }

        final double expiry = evolution.rateTimes()[startIndex];
        return Math.sqrt(variance / expiry);
    }
}
