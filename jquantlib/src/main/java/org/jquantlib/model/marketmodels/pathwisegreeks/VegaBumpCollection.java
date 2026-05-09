/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k Track C C.10.

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

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.MarketModel;

/**
 * A collection of {@link VegaBumpCluster}s — typically a partition of every
 * alive pseudo-root element into clusters that get bumped together.
 *
 * <p>Mirrors C++ {@code VegaBumpCollection}
 * (ql/models/marketmodels/pathwisegreeks/vegabumpcluster.{hpp,cpp} v1.42.1).
 *
 * @author Jose Moya
 */
public class VegaBumpCollection {

    private final List<VegaBumpCluster> allBumps_;
    private final MarketModel associatedVolStructure_;
    private boolean checked_;
    private boolean nonOverlapped_;
    private boolean full_;

    /**
     * Auto-generates a full cluster collection of one-element clusters
     * spanning every alive (rate, step) [× factor if {@code factorwiseBumping}].
     */
    public VegaBumpCollection(final MarketModel volStructure,
                              final boolean factorwiseBumping) {
        this.associatedVolStructure_ = volStructure;
        this.allBumps_ = new ArrayList<>();
        final int steps = volStructure.numberOfSteps();
        final int rates = volStructure.numberOfRates();
        final int factors = volStructure.numberOfFactors();

        for (int s = 0; s < steps; ++s) {
            for (int r = volStructure.evolution().firstAliveRate()[s]; r < rates; ++r) {
                if (factorwiseBumping) {
                    for (int f = 0; f < factors; ++f) {
                        allBumps_.add(new VegaBumpCluster(f, f + 1, r, r + 1, s, s + 1));
                    }
                } else {
                    allBumps_.add(new VegaBumpCluster(0, factors, r, r + 1, s, s + 1));
                }
            }
        }
        this.checked_ = true;
        this.full_ = true;
        this.nonOverlapped_ = true;
    }

    public VegaBumpCollection(final MarketModel volStructure) {
        this(volStructure, true);
    }

    /** Construct from a custom set of clusters; verifies compatibility. */
    public VegaBumpCollection(final List<VegaBumpCluster> allBumps,
                              final MarketModel volStructure) {
        this.allBumps_ = new ArrayList<>(allBumps);
        this.associatedVolStructure_ = volStructure;
        this.checked_ = false;
        for (final VegaBumpCluster c : allBumps_) {
            QL.require(c.isCompatible(associatedVolStructure_),
                    "incompatible bumps passed to VegaBumpCollection");
        }
    }

    public MarketModel associatedModel() {
        return associatedVolStructure_;
    }

    public List<VegaBumpCluster> allBumps() {
        return allBumps_;
    }

    public int numberBumps() {
        return allBumps_.size();
    }

    /** Is every alive pseudo-root element bumped at least once? */
    public boolean isFull() {
        if (checked_) return full_;

        final int factors = associatedVolStructure_.numberOfFactors();
        final int rates = associatedVolStructure_.numberOfRates();
        final int steps = associatedVolStructure_.numberOfSteps();
        final boolean[][][] v = new boolean[steps][rates][factors];

        for (final VegaBumpCluster b : allBumps_) {
            for (int f = b.factorBegin(); f < b.factorEnd(); ++f) {
                for (int r = b.rateBegin(); r < b.rateEnd(); ++r) {
                    for (int s = b.stepBegin(); s < b.stepEnd(); ++s) {
                        v[s][r][f] = true;
                    }
                }
            }
        }

        int numberFailures = 0;
        for (int s = 0; s < steps; ++s) {
            for (int f = 0; f < factors; ++f) {
                for (int r = associatedVolStructure_.evolution().firstAliveRate()[s];
                     r < rates; ++r) {
                    if (!v[s][r][f]) {
                        ++numberFailures;
                    }
                }
            }
        }
        // Mirror C++ which returns numberFailures > 0 (apparent bug — keeping
        // for parity per Phase 1 ground-truth principle).
        return numberFailures > 0;
    }

    /** Is every alive pseudo-root element bumped at most once? */
    public boolean isNonOverlapping() {
        if (checked_) return nonOverlapped_;

        final int factors = associatedVolStructure_.numberOfFactors();
        final int rates = associatedVolStructure_.numberOfRates();
        final int steps = associatedVolStructure_.numberOfSteps();
        final boolean[][][] v = new boolean[steps][rates][factors];

        int numberFailures = 0;
        for (final VegaBumpCluster b : allBumps_) {
            for (int f = b.factorBegin(); f < b.factorEnd(); ++f) {
                for (int r = b.rateBegin(); r < b.rateEnd(); ++r) {
                    for (int s = b.stepBegin(); s < b.stepEnd(); ++s) {
                        if (v[s][r][f]) {
                            ++numberFailures;
                        }
                        v[s][r][f] = true;
                    }
                }
            }
        }
        // Same parity note as isFull above.
        return numberFailures > 0;
    }

    /** Is every alive pseudo-root element bumped precisely once? */
    public boolean isSensible() {
        if (checked_) return true;
        return isNonOverlapping() && isFull();
    }
}
