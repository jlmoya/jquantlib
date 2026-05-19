/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008 Ralph Schreyer
 Copyright (C) 2008, 2009 Klaus Spanderen

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
package org.jquantlib.methods.finitedifferences.utilities;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.math.interpolations.LinearInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.methods.finitedifferences.StepCondition;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.time.Date;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dividend step condition for the FDM framework on a single equity direction.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/utilities/fdmdividendhandler.{hpp,cpp}}.
 * <p>
 * The grid is assumed to be in log-space for the equity direction. {@code x_[k] = exp(location[k * spacing])} are the
 * physical stock prices. At each dividend time the values in the FDM array are interpolated at the post-dividend stock
 * price {@code max(x_[0], x_[k] - dividend)}.
 * <p>
 * For a 1-D grid a single {@link LinearInterpolation} over all cells is used. For an N-D grid (N >= 2) the dividend
 * direction (equity) is held as strips while the other direction is iterated over.
 *
 * @author Phase 2l Track B port
 */
public class FdmDividendHandler implements StepCondition< Array > {

    /** Physical stock prices at each equity-direction node: x_[k] = exp(log-location). */
    private final double[] x_;

    private final List< Double > dividendTimes_;
    private final List< Date > dividendDates_;
    private final List< Double > dividends_;

    private final FdmMesher mesher_;
    private final int equityDirection_;

    /**
     * @param schedule        dividend schedule (list of {@link org.jquantlib.cashflow.Dividend})
     * @param mesher          the FDM mesh
     * @param referenceDate   reference date for year-fraction computation
     * @param dayCounter      day counter used for year fractions
     * @param equityDirection grid direction that corresponds to the equity
     */
    public FdmDividendHandler(final DividendSchedule schedule, final FdmMesher mesher, final Date referenceDate,
            final DayCounter dayCounter, final int equityDirection) {
        this.mesher_ = mesher;
        this.equityDirection_ = equityDirection;

        dividends_ = new ArrayList<>(schedule.size());
        dividendDates_ = new ArrayList<>(schedule.size());
        dividendTimes_ = new ArrayList<>(schedule.size());

        for ( final org.jquantlib.cashflow.Dividend div : schedule ) {
            dividends_.add(div.amount());
            dividendDates_.add(div.date());
            dividendTimes_.add(dayCounter.yearFraction(referenceDate, div.date()));
        }

        // Build x_: physical stock prices at the equity-direction nodes.
        // The equity direction has dim[equityDirection] nodes, and the
        // spacing in the flat layout is spacing[equityDirection].
        final int numEquity = mesher_.layout().dim()[equityDirection_];
        final int spacing = mesher_.layout().spacing()[equityDirection_];
        final Array locs = mesher_.locations(equityDirection_);
        x_ = new double[numEquity];
        for ( int i = 0; i < numEquity; ++i ) {
            x_[i] = JQuantMath.exp(locs.get(i * spacing));
        }
    }

    /** Sorted list of dividend times (year fractions from reference date). */
    public List< Double > dividendTimes() {
        return Collections.unmodifiableList(dividendTimes_);
    }

    /** Dividend dates from the schedule. */
    public List< Date > dividendDates() {
        return Collections.unmodifiableList(dividendDates_);
    }

    /** Dividend amounts from the schedule. */
    public List< Double > dividends() {
        return Collections.unmodifiableList(dividends_);
    }

    /**
     * Apply the dividend step condition at time {@code t}.
     * <p>
     * If {@code t} is not a dividend time the array is unchanged. Otherwise the dividend handler shifts each cell's
     * value by interpolating the option value at the post-dividend stock price.
     */
    @Override
    public void applyTo(final Array a, final double t) {
        // Find index of t in dividendTimes_; -1 if not found.
        final int divIdx = dividendTimes_.indexOf(t);
        if ( divIdx < 0 ) {
            return;
        }

        final double dividend = dividends_.get(divIdx);
        final int numEquity = x_.length;

        // Take a copy of the current array (mirrors C++ aCopy).
        final double[] aCopy = new double[a.size()];
        for ( int i = 0; i < a.size(); ++i ) {
            aCopy[i] = a.get(i);
        }

        final int dims = mesher_.layout().dim().length;

        if ( dims == 1 ) {
            // 1-D case: single linear interpolation over x_/aCopy.
            final Array xArr = new Array(x_);
            final Array vArr = new Array(aCopy);
            final LinearInterpolation interp = new LinearInterpolation(xArr, vArr);

            for ( int k = 0; k < numEquity; ++k ) {
                final double shiftedX = Math.max(x_[0], x_[k] - dividend);
                a.set(k, interp.op(shiftedX, true));
            }
        } else {
            // N-D case (N >= 2): for each "other" direction j, extract
            // the equity strip and interpolate.
            final int xSpacing = mesher_.layout().spacing()[equityDirection_];
            final Array tmp = new Array(numEquity);

            for ( int i = 0; i < dims; ++i ) {
                if ( i == equityDirection_ ) {
                    continue;
                }
                final int ySpacing = mesher_.layout().spacing()[i];
                final int dimI = mesher_.layout().dim()[i];
                for ( int j = 0; j < dimI; ++j ) {
                    // Extract the equity strip for this (i, j) combination.
                    for ( int k = 0; k < numEquity; ++k ) {
                        final int idx = j * ySpacing + k * xSpacing;
                        tmp.set(k, aCopy[idx]);
                    }
                    // Build local interpolation over x_ vs tmp.
                    final Array xArr = new Array(x_);
                    final LinearInterpolation interp = new LinearInterpolation(xArr, tmp);

                    // Write back interpolated values at shifted stock prices.
                    for ( int k = 0; k < numEquity; ++k ) {
                        final int idx = j * ySpacing + k * xSpacing;
                        final double shiftedX = Math.max(x_[0], x_[k] - dividend);
                        a.set(idx, interp.op(shiftedX, true));
                    }
                }
            }
        }
    }
}
