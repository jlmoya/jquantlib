/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2008 Master IMAFA - Polytech'Nice Sophia - Universite de Nice Sophia Antipolis
*/

package org.jquantlib.pricingengines.asian;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathPricer;

/**
 * Path pricer for discrete arithmetic-average-strike Asian payoffs.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/asian/mc_discr_arith_av_strike.{hpp,cpp}}
 * {@code ArithmeticASOPathPricer} (Phase 5e.5b-CFC-d-243).
 *
 * <p>The strike of the {@link PlainVanillaPayoff} is the arithmetic
 * average of the path's fixing values (optionally seeded by a
 * {@code runningSum} from past fixings); the payoff is then evaluated
 * at the path's terminal spot.
 *
 * <p>When {@code fixingCount} is supplied (i.e., not {@link Integer#MAX_VALUE}),
 * the path may include an extra point past the last fixing date (the
 * exercise date — see Issue #646 in v1.41); the averaging window is
 * restricted to the first {@code fixingCount} path points and the terminal
 * spot used for payoff evaluation is taken from {@link Path#back()}.
 *
 * @author JQuantLib
 */
public final class ArithmeticASOPathPricer extends PathPricer<Path> {

    /** Sentinel for "fixingCount not set" (mirrors C++ {@code Null<Size>()}). */
    public static final int NULL_FIXING_COUNT = Integer.MAX_VALUE;

    private final Option.Type type_;
    private final double discount_;
    private final double runningSum_;
    private final int pastFixings_;
    private final int fixingCount_;

    public ArithmeticASOPathPricer(final Option.Type type,
                                   final double discount) {
        this(type, discount, 0.0, 0, NULL_FIXING_COUNT);
    }

    public ArithmeticASOPathPricer(final Option.Type type,
                                   final double discount,
                                   final double runningSum,
                                   final int pastFixings) {
        this(type, discount, runningSum, pastFixings, NULL_FIXING_COUNT);
    }

    public ArithmeticASOPathPricer(final Option.Type type,
                                   final double discount,
                                   final double runningSum,
                                   final int pastFixings,
                                   final int fixingCount) {
        this.type_ = type;
        this.discount_ = discount;
        this.runningSum_ = runningSum;
        this.pastFixings_ = pastFixings;
        this.fixingCount_ = fixingCount;
    }

    @Override
    public Double op(final Path path) {
        final int n = path.length();
        QL.require(n > 1, "the path cannot be empty");

        // When fixingCount_ is set, the path may include an extra point
        // at the exercise date beyond the last fixing date.  Average
        // only over the fixing points; use path.back() for the spot
        // at exercise.
        final int nFixings = (fixingCount_ != NULL_FIXING_COUNT) ? fixingCount_ : n;
        QL.require(nFixings <= n, "fixingCount (" + nFixings
                + ") exceeds path length (" + n + ")");

        double averageStrike;
        if (path.timeGrid().mandatoryTimes().get(0) == 0.0) {
            // include initial fixing (T=0 is a fixing date)
            double sum = runningSum_;
            for (int i = 0; i < nFixings; i++) {
                sum += path.get(i);
            }
            averageStrike = sum / (pastFixings_ + nFixings);
        } else {
            // first path point is T=0 (not a fixing), skip it
            double sum = runningSum_;
            for (int i = 1; i < nFixings; i++) {
                sum += path.get(i);
            }
            averageStrike = sum / (pastFixings_ + nFixings - 1);
        }

        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type_, averageStrike);
        return discount_ * payoff.get(path.back());
    }
}
