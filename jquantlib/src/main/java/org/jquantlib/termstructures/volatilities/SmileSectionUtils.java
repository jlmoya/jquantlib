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

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
*/
/*
 Copyright (C) 2013, 2018 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.volatilities;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.Constants;
import org.jquantlib.model.VolatilityType;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for smile-section operations.
 *
 * <p>Mirrors C++ QuantLib v1.42.1 {@code SmileSectionUtils} (smilesectionutils.hpp/.cpp).
 *
 * <p>Moneyness is expressed in:
 * <ul>
 *   <li>absolute terms for Normal volatility smile sections</li>
 *   <li>relative terms for ShiftedLognormal smile sections</li>
 * </ul>
 *
 * <p>Phase 2j WI-4.0b.
 *
 * @author JQuantLib migration contributors
 */
public class SmileSectionUtils {

    // Default moneyness grids (mirrors C++ static arrays)
    private static final double[] DEFAULT_MONEY = { 0.0, 0.01, 0.05, 0.10, 0.25, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90,
            1.0, 1.25, 1.5, 1.75, 2.0, 5.0, 7.5, 10.0, 15.0, 20.0 };

    private static final double[] DEFAULT_MONEY_NORMAL = { -0.20, -0.15, -0.10, -0.075, -0.05, -0.04, -0.03, -0.02,
            -0.015, -0.01, -0.0075, -0.0050, -0.0025, 0.0, 0.0025, 0.0050, 0.0075, 0.01, 0.015, 0.02, 0.03, 0.04, 0.05,
            0.075, 0.10, 0.15, 0.20 };

    private final List< Double > m_;  // moneyness grid
    private final List< Double > c_;  // call prices (including special first entry for SL)
    private final List< Double > k_;  // strike grid
    private final double f_;
    private int leftIndex_;
    private int rightIndex_;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Constructs a SmileSectionUtils from a smile section with default settings.
     *
     * @param section               the source smile section
     * @param moneynessGrid         moneyness grid; empty = use default
     * @param atm                   ATM override; {@link Constants#NULL_REAL} = use section's atmLevel
     * @param deleteArbitragePoints whether to remove arbitrage points iteratively
     */
    public SmileSectionUtils(final SmileSection section, final double[] moneynessGrid, final double atm,
            final boolean deleteArbitragePoints) {

        m_ = new ArrayList<>();
        c_ = new ArrayList<>();
        k_ = new ArrayList<>();

        // Validate moneyness grid
        if ( moneynessGrid != null && moneynessGrid.length > 0 ) {
            QL.require(section.volatilityType() == VolatilityType.Normal || moneynessGrid[0] >= 0.0,
                    "moneyness grid should only contain non-negative values (" + moneynessGrid[0] + ")");
            for ( int i = 0; i < moneynessGrid.length - 1; i++ ) {
                QL.require(moneynessGrid[i] < moneynessGrid[i + 1],
                        "moneyness grid should contain strictly increasing values (" + moneynessGrid[i] + ","
                                + moneynessGrid[i + 1] + " at indices " + i + ", " + (i + 1) + ")");
            }
        }

        // Determine ATM level
        if ( atm == Constants.NULL_REAL ) {
            f_ = section.atmLevel();
            QL.require(f_ != Constants.NULL_REAL,
                    "atm level must be provided by source section or given in the constructor");
        } else {
            f_ = atm;
        }

        // Choose working moneyness array
        final double[] tmp;
        if ( moneynessGrid == null || moneynessGrid.length == 0 ) {
            tmp = section.volatilityType() == VolatilityType.Normal ? DEFAULT_MONEY_NORMAL : DEFAULT_MONEY;
        } else {
            tmp = moneynessGrid;
        }

        final double shift = section.shift();

        // For ShiftedLognormal: if first moneyness > QL_EPSILON add the at-barrier point
        if ( section.volatilityType() == VolatilityType.ShiftedLognormal && tmp[0] > Constants.QL_EPSILON ) {
            m_.add(0.0);
            k_.add(-shift);
        }

        boolean minStrikeAdded = false;
        boolean maxStrikeAdded = false;

        for ( double moneyness : tmp ) {
            // Compute strike from moneyness
            final double k;
            if ( section.volatilityType() == VolatilityType.Normal ) {
                k = f_ + moneyness;
            } else {
                k = moneyness * (f_ + shift) - shift;
            }

            if ( (section.volatilityType() == VolatilityType.ShiftedLognormal && moneyness <= Constants.QL_EPSILON) || (
                    k >= section.minStrike() && k <= section.maxStrike()) ) {
                if ( !minStrikeAdded || !close(k, section.minStrike()) ) {
                    m_.add(moneyness);
                    k_.add(k);
                }
                if ( close(k, section.maxStrike()) ) {
                    maxStrikeAdded = true;
                }
            } else {
                // Limited strike range: insert endpoint to preserve information
                if ( k < section.minStrike() && !minStrikeAdded ) {
                    if ( section.volatilityType() == VolatilityType.Normal ) {
                        m_.add(section.minStrike() - f_);
                    } else {
                        m_.add((section.minStrike() + shift) / (f_ + shift));
                    }
                    k_.add(section.minStrike());
                    minStrikeAdded = true;
                }
                if ( k > section.maxStrike() && !maxStrikeAdded ) {
                    if ( section.volatilityType() == VolatilityType.Normal ) {
                        m_.add(section.maxStrike() - f_);
                    } else {
                        m_.add((section.maxStrike() + shift) / (f_ + shift));
                    }
                    k_.add(section.maxStrike());
                    maxStrikeAdded = true;
                }
            }
        }

        // Build call prices
        // For ShiftedLognormal: first entry is undiscounted forward (f+shift)
        if ( section.volatilityType() == VolatilityType.ShiftedLognormal ) {
            c_.add(f_ + shift);
        }

        final int startIdx = section.volatilityType() == VolatilityType.Normal ? 0 : 1;
        for ( int i = startIdx; i < k_.size(); i++ ) {
            c_.add(section.optionPrice(k_.get(i), Option.Type.Call, 1.0));
        }

        // Find central index: first m_ value > (0.0 for Normal, 1.0 for SL) - QL_EPSILON
        final double centralThreshold =
                (section.volatilityType() == VolatilityType.Normal ? 0.0 : 1.0) - Constants.QL_EPSILON;

        int centralIndex = 0;
        for ( ; centralIndex < m_.size(); centralIndex++ ) {
            if ( m_.get(centralIndex) > centralThreshold ) {
                break;
            }
        }

        QL.require(centralIndex < k_.size() - 1 && centralIndex > 1,
                "Atm point in moneyness grid (" + centralIndex + ") too close to boundary.");

        // Shift central index to the right if needed (atm in arbitrageable area)
        while ( centralIndex < k_.size() - 1 && !af(centralIndex, centralIndex, centralIndex + 1) ) {
            centralIndex++;
        }

        QL.require(centralIndex < k_.size(), "central index is at right boundary");

        leftIndex_ = centralIndex;
        rightIndex_ = centralIndex;

        boolean done = false;
        while ( !done ) {
            boolean isAf = true;
            done = true;

            // Expand right
            while ( isAf && rightIndex_ < k_.size() - 1 ) {
                rightIndex_++;
                isAf = af(leftIndex_, rightIndex_, rightIndex_) && af(leftIndex_, rightIndex_ - 1, rightIndex_);
            }
            if ( !isAf )
                rightIndex_--;

            // Expand left
            isAf = true;
            while ( isAf && leftIndex_ > 1 ) {
                leftIndex_--;
                isAf = af(leftIndex_, leftIndex_, rightIndex_) && af(leftIndex_, leftIndex_ + 1, rightIndex_);
            }
            if ( !isAf )
                leftIndex_++;

            if ( rightIndex_ < leftIndex_ )
                rightIndex_ = leftIndex_;

            if ( deleteArbitragePoints && leftIndex_ > 1 ) {
                m_.remove(leftIndex_ - 1);
                k_.remove(leftIndex_ - 1);
                c_.remove(leftIndex_ - 1);
                leftIndex_--;
                if ( rightIndex_ > 0 )
                    rightIndex_--;
                done = false;
            }
            if ( deleteArbitragePoints && rightIndex_ < k_.size() - 1 ) {
                m_.remove(rightIndex_ + 1);
                k_.remove(rightIndex_ + 1);
                c_.remove(rightIndex_ + 1);
                if ( rightIndex_ > 0 )
                    rightIndex_--;
                done = false;
            }
        }

        QL.require(rightIndex_ > leftIndex_,
                "arbitrage free region must at least contain two points (only index is " + leftIndex_ + ")");
    }

    /**
     * Convenience constructor with empty moneyness grid and no ATM override.
     */
    public SmileSectionUtils(final SmileSection section) {
        this(section, new double[0], Constants.NULL_REAL, false);
    }

    /**
     * Convenience constructor with no deleteArbitragePoints flag.
     */
    public SmileSectionUtils(final SmileSection section, final double[] moneynessGrid, final double atm) {
        this(section, moneynessGrid, atm, false);
    }

    // -----------------------------------------------------------------------
    // Public accessors
    // -----------------------------------------------------------------------

    /**
     * Mirrors C++ QuantLib {@code close(a, b)}: relative tolerance comparison at 42 ULPs.
     */
    private static boolean close(final double a, final double b) {
        final double diff = Math.abs(a - b);
        final double tol = 42.0 * Constants.QL_EPSILON * Math.max(Math.abs(a), Math.max(Math.abs(b), 1.0));
        return diff <= tol;
    }

    private static double[] toArray(final List< Double > list) {
        final double[] arr = new double[list.size()];
        for ( int i = 0; i < arr.length; i++ ) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    /**
     * Returns the arbitrage-free region as [kLeft, kRight] strike pair.
     */
    public double[] arbitragefreeRegion() {
        return new double[] { k_.get(leftIndex_), k_.get(rightIndex_) };
    }

    /**
     * Returns the arbitrage-free index pair [leftIndex, rightIndex].
     */
    public int[] arbitragefreeIndices() {
        return new int[] { leftIndex_, rightIndex_ };
    }

    /** Returns the moneyness grid. */
    public double[] moneyGrid() {
        return toArray(m_);
    }

    /** Returns the strike grid. */
    public double[] strikeGrid() {
        return toArray(k_);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /** Returns the call prices array. */
    public double[] callPrices() {
        return toArray(c_);
    }

    /** Returns the ATM level. */
    public double atmLevel() {
        return f_;
    }

    /**
     * Arbitrage-free check for three indices (mirrors C++ {@code af()}). Returns true if the call price slope between
     * im=(i-1 or i0) and i is in [-1, 0] and convex toward i1.
     */
    private boolean af(final int i0, final int i, final int i1) {
        if ( i == 0 )
            return true;
        final int im = (i - 1 >= i0) ? (i - 1) : 0;
        final double q1 = (c_.get(i) - c_.get(im)) / (k_.get(i) - k_.get(im));
        if ( q1 < -1.0 || q1 > 0.0 )
            return false;
        if ( i >= i1 )
            return true;
        final double q2 = (c_.get(i + 1) - c_.get(i)) / (k_.get(i + 1) - k_.get(i));
        return q1 <= q2 && q2 <= 0.0;
    }
}
