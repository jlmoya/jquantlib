/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.experimental.finitedifferences;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.methods.finitedifferences.StepCondition;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;

/**
 * Knock-out / auto-call step condition for Heston-style FDM problems pricing express certificates.
 * <p>
 * Java port of v1.42.1 {@code test-suite/fdmlinearop.cpp} test-local helper class {@code FdmHestonExpressCondition}
 * (lines 86-116). Although declared inline in the C++ test, the class is general-purpose enough to live in production
 * code under {@code org.jquantlib.experimental.finitedifferences} so other Heston FDM pricers can reuse it.
 * <p>
 * The condition is applied at each {@code exerciseTimes_[i]} (matched exactly — no tolerance). For every mesh cell
 * whose underlying spot {@code s = exp(location[0])} exceeds {@code triggerLevels[i]} the value is overwritten with
 * {@code redemptions[i]}, modelling early redemption when the underlying crosses the barrier on an observation date.
 *
 * @author Phase 5e.5b-CFC-d-239 port
 */
public class FdmHestonExpressCondition implements StepCondition< Array > {

    private final double[] redemptions_;
    private final double[] triggerLevels_;
    private final double[] exerciseTimes_;
    private final FdmMesher mesher_;

    /**
     * @param redemptions   one redemption value per observation date.
     * @param triggerLevels one spot trigger per observation date — the cell is knocked out when {@code s > trigger}.
     * @param exerciseTimes observation dates as year fractions from the reference date.
     * @param mesher        the FDM mesh; direction {@code 0} is assumed to be log-spot.
     */
    public FdmHestonExpressCondition(final double[] redemptions, final double[] triggerLevels,
            final double[] exerciseTimes, final FdmMesher mesher) {
        this.redemptions_ = redemptions.clone();
        this.triggerLevels_ = triggerLevels.clone();
        this.exerciseTimes_ = exerciseTimes.clone();
        this.mesher_ = mesher;
    }

    /**
     * Apply the knock-out at time {@code t}.
     * <p>
     * If {@code t} does not match any exercise time exactly, the array is left unchanged. Otherwise every cell whose
     * log-spot location places the underlying above {@code triggerLevels[index]} is overwritten with
     * {@code redemptions[index]}. Matches the C++ semantics verbatim (exact-equality match via {@code std::find}, no
     * tolerance).
     */
    @Override
    public void applyTo(final Array a, final double t) {
        int index = -1;
        for ( int i = 0; i < exerciseTimes_.length; ++i ) {
            if ( exerciseTimes_[i] == t ) {
                index = i;
                break;
            }
        }
        if ( index < 0 ) {
            return;
        }

        final double trigger = triggerLevels_[index];
        final double redemption = redemptions_[index];

        for ( final FdmLinearOpIterator iter : mesher_.layout() ) {
            final double s = JQuantMath.exp(mesher_.location(iter, 0));
            if ( s > trigger ) {
                a.set(iter.index(), redemption);
            }
        }
    }
}
