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
 Copyright (C) 2014 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */

package org.jquantlib.experimental.coupons;

import org.jquantlib.QL;
import org.jquantlib.indexes.InterestRateIndex;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Class for swap-rate spread indices.
 * <p>
 * Spread index defined as
 * {@latex$ I = g_1 \cdot S_1 + g_2 \cdot S_2 } where
 * {@latex$ S_1 } and {@latex$ S_2 } are two underlying {@link SwapIndex}
 * instances, and {@latex$ g_1, g_2 } are gearings (defaulting to
 * {@code +1.0} and {@code -1.0} so that the spread {@latex$ I = S_1 - S_2 }).
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/experimental/coupons/swapspreadindex.hpp/cpp}.
 *
 * @author Peter Caspers (C++ original)
 */
public class SwapSpreadIndex extends InterestRateIndex {

    private final SwapIndex swapIndex1_;
    private final SwapIndex swapIndex2_;
    private final double gearing1_;
    private final double gearing2_;
    /**
     * Custom display name for the spread, in the form
     * {@code "S1.name(g1) + S2.name(g2)"}, mirroring the C++
     * {@code SwapSpreadIndex::name_} override. Stored separately from
     * {@link #name()} (which falls through to the auto-composed
     * {@link InterestRateIndex#name()} so that the {@code IndexManager}
     * registration in the {@code InterestRateIndex} ctor — which runs
     * <em>before</em> {@code displayName_} is assigned — does not NPE).
     */
    private final String displayName_;


    //
    // public constructors
    //

    /** Default constructor: gearings = +1, -1 so I = S1 - S2. */
    public SwapSpreadIndex(final String familyName,
                           final SwapIndex swapIndex1,
                           final SwapIndex swapIndex2) {
        this(familyName, swapIndex1, swapIndex2, 1.0, -1.0);
    }

    public SwapSpreadIndex(final String familyName,
                           final SwapIndex swapIndex1,
                           final SwapIndex swapIndex2,
                           final double gearing1,
                           final double gearing2) {
        super(familyName,
              swapIndex1.tenor(),
              swapIndex1.fixingDays(),
              swapIndex1.currency(),
              swapIndex1.fixingCalendar(),
              swapIndex1.dayCounter());
        this.swapIndex1_ = swapIndex1;
        this.swapIndex2_ = swapIndex2;
        this.gearing1_ = gearing1;
        this.gearing2_ = gearing2;

        this.swapIndex1_.addObserver(this);
        this.swapIndex2_.addObserver(this);

        // Build the synthetic display name as in C++ swapspreadindex.cpp:
        //   name_ = swapIndex1->name() + "(g1) + " + swapIndex2->name() + "(g2)"
        // Mirror C++ printf-style formatting (4 digits after decimal, fixed).
        // NOTE: this is only used by displayName(); we deliberately do NOT
        // override name() here (see displayName_ field comment).
        final StringBuilder n = new StringBuilder();
        n.append(swapIndex1_.name())
                .append("(").append(formatGearing(gearing1)).append(") + ")
                .append(swapIndex2_.name())
                .append("(").append(formatGearing(gearing2)).append(")");
        this.displayName_ = n.toString();

        QL.require(swapIndex1_.fixingDays() == swapIndex2_.fixingDays(),
                "index1 fixing days (" + swapIndex1_.fixingDays() + ") "
                + "must be equal to index2 fixing days (" + swapIndex2_.fixingDays() + ")");

        QL.require(org.jquantlib.time.Calendar.eq(swapIndex1_.fixingCalendar(), swapIndex2_.fixingCalendar()),
                "index1 fixingCalendar (" + swapIndex1_.fixingCalendar() + ") "
                + "must be equal to index2 fixingCalendar (" + swapIndex2_.fixingCalendar() + ")");

        QL.require(swapIndex1_.currency().equals(swapIndex2_.currency()),
                "index1 currency (" + swapIndex1_.currency() + ") "
                + "must be equal to index2 currency (" + swapIndex2_.currency() + ")");

        QL.require(swapIndex1_.dayCounter().equals(swapIndex2_.dayCounter()),
                "index1 dayCounter (" + swapIndex1_.dayCounter() + ") "
                + "must be equal to index2 dayCounter (" + swapIndex2_.dayCounter() + ")");

        QL.require(swapIndex1_.fixedLegTenor().equals(swapIndex2_.fixedLegTenor()),
                "index1 fixedLegTenor (" + swapIndex1_.fixedLegTenor() + ") "
                + "must be equal to index2 fixedLegTenor (" + swapIndex2_.fixedLegTenor() + ")");

        QL.require(swapIndex1_.fixedLegConvention() == swapIndex2_.fixedLegConvention(),
                "index1 fixedLegConvention (" + swapIndex1_.fixedLegConvention() + ") "
                + "must be equal to index2 fixedLegConvention (" + swapIndex2_.fixedLegConvention() + ")");
    }


    //
    // public inspectors / overrides InterestRateIndex
    //

    /**
     * Custom display name in the form {@code "S1.name(g1) + S2.name(g2)"}.
     * Mirrors the C++ {@code SwapSpreadIndex::name_} composite. Use this for
     * UI / logging; the {@link #name()} method (inherited from
     * {@link InterestRateIndex}) is what's used for {@code IndexManager}
     * history lookups, and is the auto-composed
     * {@code familyName + tenor + dayCounter.name()} form.
     */
    public String displayName() {
        return displayName_;
    }


    /**
     * SwapSpreadIndex does not provide a single maturity date — the spread is
     * defined on two different swap indices with possibly different maturities.
     * Mirrors the C++ behavior of throwing.
     */
    @Override
    public Date maturityDate(final Date valueDate) {
        throw new UnsupportedOperationException(
                "SwapSpreadIndex does not provide a single maturity date");
    }

    /**
     * Forecast fixing as the linear combination of the two swap-index fixings.
     * Note: this also handles the case when one of indices has a historic
     * fixing on the evaluation date — see C++ swapspreadindex.hpp:64.
     */
    @Override
    protected double forecastFixing(final Date fixingDate) {
        return gearing1_ * swapIndex1_.fixing(fixingDate, false)
             + gearing2_ * swapIndex2_.fixing(fixingDate, false);
    }

    /**
     * Past fixing as the linear combination of the two swap-index past fixings.
     * If either component is missing returns {@link Constants#NULL_REAL}.
     */
    public double pastFixing(final Date fixingDate) {
        // Use Index.pastFixing if available; else use IndexManager directly.
        final double f1 = pastFixingOfIndex(swapIndex1_, fixingDate);
        final double f2 = pastFixingOfIndex(swapIndex2_, fixingDate);
        if (f1 == Constants.NULL_REAL || f2 == Constants.NULL_REAL) {
            return Constants.NULL_REAL;
        }
        return gearing1_ * f1 + gearing2_ * f2;
    }

    private static double pastFixingOfIndex(final SwapIndex idx, final Date fixingDate) {
        // Mirrors C++ Index::pastFixing(): look up in IndexManager history.
        final org.jquantlib.indexes.IndexManager mgr =
                org.jquantlib.indexes.IndexManager.getInstance();
        try {
            final Double v = mgr.getHistory(idx.name()).get(fixingDate);
            return (v == null) ? Constants.NULL_REAL : v.doubleValue();
        } catch (final Exception e) {
            return Constants.NULL_REAL;
        }
    }

    /**
     * Spread indices do not allow native fixings — fixings must be inferred
     * from the two component {@link SwapIndex} histories.
     * Mirrors C++ {@code SwapSpreadIndex::allowsNativeFixings()}.
     */
    public boolean allowsNativeFixings() {
        return false;
    }


    //
    // overrides Index.fixing — uses past/forecast logic that combines components
    //

    /**
     * Fixing of the spread index. If {@code fixingDate < today} or
     * {@code fixingDate == today} with historic-fixings enforced, returns the
     * combination of the two component past fixings (throwing if either is
     * missing). Otherwise forecasts the spread.
     * <p>
     * Mirrors C++ {@code SwapSpreadIndex::pastFixing} and {@code forecastFixing}
     * via the inherited {@code InterestRateIndex::fixing} dispatch in v1.42.1
     * (see ql/indexes/interestrateindex.cpp:73).
     */
    @Override
    public double fixing(final Date fixingDate, final boolean forecastTodaysFixing) {
        QL.require(isValidFixingDate(fixingDate),
                "Fixing date " + fixingDate + " is not valid");
        final org.jquantlib.Settings s = new org.jquantlib.Settings();
        final Date today = s.evaluationDate();
        final boolean enforceTodaysHistoricFixings = s.isEnforcesTodaysHistoricFixings();

        if (fixingDate.lt(today)
                || (fixingDate.equals(today) && enforceTodaysHistoricFixings && !forecastTodaysFixing)) {
            // must have been fixed (historic). For SwapSpreadIndex we reconstruct
            // the spread from the two component past fixings (see C++
            // SwapSpreadIndex::pastFixing). If either is missing, this throws.
            final double f1 = swapIndex1_.fixing(fixingDate, false);
            final double f2 = swapIndex2_.fixing(fixingDate, false);
            return gearing1_ * f1 + gearing2_ * f2;
        }

        if (fixingDate.equals(today) && !forecastTodaysFixing) {
            // might have been fixed (today). Same combination logic.
            try {
                final double f1 = swapIndex1_.fixing(fixingDate, false);
                final double f2 = swapIndex2_.fixing(fixingDate, false);
                return gearing1_ * f1 + gearing2_ * f2;
            } catch (final Exception e) {
                // fall through and forecast
            }
        }
        // forecast
        return forecastFixing(fixingDate);
    }


    //
    // overrides InterestRateIndex.termStructure — no single curve, throw
    //

    @Override
    public Handle<YieldTermStructure> termStructure() {
        throw new UnsupportedOperationException(
                "SwapSpreadIndex does not provide a single termStructure");
    }


    //
    // public inspectors
    //

    public SwapIndex swapIndex1() {
        return swapIndex1_;
    }

    public SwapIndex swapIndex2() {
        return swapIndex2_;
    }

    public double gearing1() {
        return gearing1_;
    }

    public double gearing2() {
        return gearing2_;
    }


    //
    // private helpers
    //

    private static String formatGearing(final double g) {
        // Match C++ {@code std::setprecision(4) << std::fixed << g}: 4 digits
        // after the decimal point, fixed notation.
        return String.format(java.util.Locale.US, "%.4f", g);
    }
}
