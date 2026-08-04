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

package org.jquantlib.methods.finitedifferences.utilities;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.MonotonicNaturalCubicInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.termstructures.volatilities.SmileSection;

/**
 * Risk-neutral terminal density implied by a {@link SmileSection} through the Breeden-Litzenberger identity.
 * <p>
 * Ported from C++ QuantLib v1.43 {@code ql/methods/finitedifferences/utilities/smilesectionrndcalculator.{hpp,cpp}} —
 * new in that release.
 * <p>
 * Two behaviours are worth knowing before relying on this class:
 * <ul>
 * <li>{@link #pdf(double, double)} and {@link #cdf(double, double)} read the smile directly and never build the
 * quantile grid, so {@code nStrikes} and {@code nStd} affect {@link #invcdf(double, double)} only.</li>
 * <li>The grid is deduplicated after being made monotone, so it is normally shorter than {@code nStrikes}, and
 * {@code invcdf} clamps into the surviving CDF range rather than extrapolating.</li>
 * </ul>
 * All abscissae are in log-moneyness: {@code pdf}/{@code cdf} take {@code x = log(S)}, and {@code invcdf} returns
 * {@code log(K)}.
 *
 * @author Jose Moya
 */
public class SmileSectionRNDCalculator extends RiskNeutralDensityCalculator {

    private static final double DEDUP_TOL = 1.0e-12;

    private final SmileSection smile;
    private final int nStrikes;
    private final double nStd;

    private boolean initialized = false;
    private double[] strikes;
    private double[] cdfValues;
    private MonotonicNaturalCubicInterpolation quantileFn;

    //
    // public constructors
    //

    public SmileSectionRNDCalculator(final SmileSection smile) {
        this(smile, 200, 5.0);
    }

    /**
     * @param smile    the smile section the density is read off; it must supply an ATM level — wrap it in an
     *                 {@code AtmSmileSection} if it does not
     * @param nStrikes number of grid points laid out for the quantile function; at least 4
     * @param nStd     half-width of the strike grid, in ATM log-normal standard deviations; must be positive
     */
    public SmileSectionRNDCalculator(final SmileSection smile, final int nStrikes, final double nStd) {
        QL.require(smile != null, "null SmileSection");
        QL.require(nStrikes >= 4, "at least 4 strikes required, got " + nStrikes);
        QL.require(nStd > 0.0, "nStd must be positive, got " + nStd);
        this.smile = smile;
        this.nStrikes = nStrikes;
        this.nStd = nStd;
    }

    //
    // private methods
    //

    private void checkTime(final double t) {
        final double tRef = smile.exerciseTime();
        QL.require(Closeness.isCloseEnough(t, tRef),
                "SmileSectionRNDCalculator: requested t=" + t + " does not match smile exercise time " + tRef);
    }

    /**
     * Builds the CDF grid and the monotone spline that inverts it. Lazy, and idempotent after the first call — the
     * grid is a pure function of the smile and the constructor arguments.
     */
    private void initialize() {
        if ( initialized ) {
            return;
        }

        final double forward = smile.atmLevel();
        QL.require(forward != Constants.NULL_REAL && !Double.isNaN(forward),
                "SmileSectionRNDCalculator: smile.atmLevel() returned null; wrap with AtmSmileSection to supply one");

        final double t = smile.exerciseTime();
        final double sigmaAtm = smile.volatility(forward);
        final double logStd = sigmaAtm * Math.sqrt(t);
        final double kMin = Math.max(forward * Math.exp(-nStd * logStd), Constants.QL_EPSILON);
        final double kMax = forward * Math.exp(nStd * logStd);

        final List< Double > ks = new ArrayList<>(nStrikes);
        final List< Double > cs = new ArrayList<>(nStrikes);

        double lastCdf = -1.0;
        for ( int i = 0; i < nStrikes; ++i ) {
            final double k = kMin + (kMax - kMin) * i / (nStrikes - 1);
            final double c = Math.min(Math.max(1.0 - smile.digitalOptionPrice(k, Option.Type.Call, 1.0, 1.0e-5), 0.0),
                    1.0);
            // Enforce monotonicity, then drop points that add nothing: a spline through a flat run is not invertible.
            final double cMono = Math.max(c, lastCdf);
            if ( cMono - lastCdf > DEDUP_TOL ) {
                ks.add(k);
                cs.add(cMono);
                lastCdf = cMono;
            }
        }

        QL.require(cs.size() >= 4,
                "SmileSectionRNDCalculator: too few unique CDF points (" + cs.size() + ") after deduplication");

        strikes = new double[ks.size()];
        cdfValues = new double[cs.size()];
        for ( int i = 0; i < ks.size(); ++i ) {
            strikes[i] = ks.get(i);
            cdfValues[i] = cs.get(i);
        }

        // abscissae are the CDF values, ordinates the strikes — i.e. the quantile function
        quantileFn = new MonotonicNaturalCubicInterpolation(new Array(cdfValues), new Array(strikes));
        initialized = true;
    }

    //
    // implements RiskNeutralDensityCalculator
    //

    /**
     * Density of {@code log(S)} at {@code x}. Note this deliberately does <em>not</em> touch the quantile grid.
     */
    @Override
    public double pdf(final double x, final double t) {
        checkTime(t);
        final double s = Math.exp(x);
        return s * smile.density(s, 1.0, 1.0e-4);
    }

    /**
     * Cumulative probability that {@code log(S)} is at or below {@code x}.
     */
    @Override
    public double cdf(final double x, final double t) {
        checkTime(t);
        final double s = Math.exp(x);
        return 1.0 - smile.digitalOptionPrice(s, Option.Type.Call, 1.0, 1.0e-5);
    }

    /**
     * Quantile of {@code log(S)} at probability {@code p}.
     * <p>
     * The order of the checks matters and mirrors C++: the exercise time is validated first, then the grid is built
     * (which is where a missing ATM level is reported), and only then is {@code p} range-checked.
     */
    @Override
    public double invcdf(final double p, final double t) {
        checkTime(t);
        initialize();
        QL.require(p > 0.0 && p < 1.0, "p must be in (0, 1), got " + p);
        final double pClamped = Math.min(Math.max(p, cdfValues[0]), cdfValues[cdfValues.length - 1]);
        return Math.log(quantileFn.op(pClamped));
    }

    //
    // public convenience overloads at the smile's own exercise time
    //

    public double pdf(final double x) {
        return pdf(x, smile.exerciseTime());
    }

    public double cdf(final double x) {
        return cdf(x, smile.exerciseTime());
    }

    public double invcdf(final double p) {
        return invcdf(p, smile.exerciseTime());
    }
}
