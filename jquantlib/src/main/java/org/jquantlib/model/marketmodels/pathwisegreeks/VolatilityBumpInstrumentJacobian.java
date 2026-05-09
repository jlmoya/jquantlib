/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k.5 C.9.

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
 Copyright (C) 2008 Mark Joshi
*/

package org.jquantlib.model.marketmodels.pathwisegreeks;

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.OrthogonalProjections;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModel;

/**
 * Computes how each vega-bump cluster changes implied volatilities of a set
 * of swaptions and caps (the "Jacobian" mapping bump-space to vol-space).
 *
 * <p>Also contains {@link OrthogonalizedBumpFinder} (same C++ header):
 * given a {@code VegaBumpCollection}, instruments, and orthogonalization
 * parameters, returns bump matrices that shift exactly one instrument's
 * implied vol by 1% while leaving the others fixed.
 *
 * <p>Mirrors C++ {@code VolatilityBumpInstrumentJacobian} and
 * {@code OrthogonalizedBumpFinder} from
 * {@code ql/models/marketmodels/pathwisegreeks/bumpinstrumentjacobian.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * @author Jose Moya
 */
public class VolatilityBumpInstrumentJacobian {

    // ---- inner structs (mirror C++ nested structs) ----

    /** Identifies a co-terminal swaption by its start and end rate index. */
    public static final class Swaption {
        public final int startIndex;
        public final int endIndex;
        public Swaption(final int startIndex, final int endIndex) {
            this.startIndex = startIndex;
            this.endIndex   = endIndex;
        }
    }

    /** Identifies a cap by its start/end rate index and strike. */
    public static final class Cap {
        public final int startIndex;
        public final int endIndex;
        public final double strike;
        public Cap(final int startIndex, final int endIndex, final double strike) {
            this.startIndex = startIndex;
            this.endIndex   = endIndex;
            this.strike     = strike;
        }
    }

    // ---- fields ----

    private final VegaBumpCollection bumps_;
    private final List<Swaption>     swaptions_;
    private final List<Cap>          caps_;
    private final int                numberInstruments_;

    // mutable caches (lazy-computed per instrument)
    private final boolean[]   computed_;
    private boolean            allComputed_;
    private final double[][]  derivatives_;
    private final double[][]  onePercentBumps_;
    private final Matrix      bumpMatrix_;

    /**
     * @param bumps      the full vega-bump collection for the model
     * @param swaptions  list of swaption descriptors
     * @param caps       list of cap descriptors
     */
    public VolatilityBumpInstrumentJacobian(final VegaBumpCollection bumps,
                                             final List<Swaption> swaptions,
                                             final List<Cap> caps) {
        this.bumps_             = bumps;
        this.swaptions_         = swaptions;
        this.caps_              = caps;
        this.numberInstruments_ = swaptions.size() + caps.size();
        final int nb            = bumps.numberBumps();
        this.computed_          = new boolean[numberInstruments_];
        this.allComputed_       = false;
        this.derivatives_       = new double[numberInstruments_][nb];
        this.onePercentBumps_   = new double[numberInstruments_][nb];
        this.bumpMatrix_        = new Matrix(numberInstruments_, nb);
    }

    // ---- public API ----

    /** Returns the underlying bump collection. */
    public VegaBumpCollection getInputBumps() {
        return bumps_;
    }

    /**
     * Returns the derivative of instrument {@code j}'s implied vol with respect
     * to each bump cluster — a vector of length {@code bumps.numberBumps()}.
     *
     * <p>Results are cached after the first call for each instrument.
     */
    public double[] derivativesVolatility(final int j) {
        QL.require(j < numberInstruments_,
                "too high index passed to VolatilityBumpInstrumentJacobian::derivativesVolatility");

        if (computed_[j]) {
            return derivatives_[j].clone();
        }

        double sizesq = 0.0;
        computed_[j] = true;

        final int nb = bumps_.numberBumps();
        final List<VegaBumpCluster> bumpClusters = bumps_.allBumps();

        if (j < swaptions_.size()) {
            // Swaption
            final Swaption sw = swaptions_.get(j);
            final SwaptionPseudoDerivative thisPseudo =
                    new SwaptionPseudoDerivative(bumps_.associatedModel(),
                                                 sw.startIndex, sw.endIndex);

            for (int k = 0; k < nb; ++k) {
                double v = 0.0;
                final VegaBumpCluster cl = bumpClusters.get(k);
                for (int i = cl.stepBegin(); i < cl.stepEnd(); ++i) {
                    final Matrix fullDeriv = thisPseudo.volatilityDerivative(i);
                    for (int f = cl.factorBegin(); f < cl.factorEnd(); ++f) {
                        for (int r = cl.rateBegin(); r < cl.rateEnd(); ++r) {
                            v += fullDeriv.get(r, f);
                        }
                    }
                }
                derivatives_[j][k] = v;
                sizesq += v * v;
            }

        } else {
            // Cap
            final int capIdx = j - swaptions_.size();
            final Cap cap = caps_.get(capIdx);
            final CapPseudoDerivative thisPseudo =
                    new CapPseudoDerivative(bumps_.associatedModel(),
                                            cap.strike, cap.startIndex, cap.endIndex, 1.0);

            for (int k = 0; k < nb; ++k) {
                double v = 0.0;
                final VegaBumpCluster cl = bumpClusters.get(k);
                for (int i = cl.stepBegin(); i < cl.stepEnd(); ++i) {
                    final Matrix fullDeriv = thisPseudo.volatilityDerivative(i);
                    for (int f = cl.factorBegin(); f < cl.factorEnd(); ++f) {
                        for (int r = cl.rateBegin(); r < cl.rateEnd(); ++r) {
                            v += fullDeriv.get(r, f);
                        }
                    }
                }
                sizesq += v * v;
                derivatives_[j][k] = v;
            }
        }

        // onePercentBump[k] = 0.01 * deriv[k] / |deriv|^2
        for (int k = 0; k < nb; ++k) {
            final double val = 0.01 * derivatives_[j][k] / sizesq;
            bumpMatrix_.set(j, k, val);
            onePercentBumps_[j][k] = val;
        }

        return derivatives_[j].clone();
    }

    /**
     * Returns the 1%-bump vector for instrument {@code j}.
     * This is the smallest vector that changes instrument {@code j}'s implied
     * vol by 1% (= 0.01 * deriv / |deriv|^2).
     */
    public double[] onePercentBump(final int j) {
        derivativesVolatility(j);
        return onePercentBumps_[j].clone();
    }

    /**
     * Computes and returns the full bump matrix (instruments × bumps).
     * Row i is the 1%-bump vector for instrument i.
     */
    public Matrix getAllOnePercentBumps() {
        if (!allComputed_) {
            for (int i = 0; i < numberInstruments_; ++i) {
                derivativesVolatility(i);
            }
            allComputed_ = true;
        }
        return bumpMatrix_;
    }
}
