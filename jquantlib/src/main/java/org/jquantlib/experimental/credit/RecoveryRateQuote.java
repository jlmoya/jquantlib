/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2009 Jose Aparicio
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Quote;

import java.util.EnumMap;
import java.util.Map;

/**
 * Stores a recovery-rate market quote and the associated seniority.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::RecoveryRateQuote}
 * ({@code ql/experimental/credit/recoveryratequote.{hpp,cpp}}).
 *
 * <p>Conventional ISDA recoveries (per the C++ static array
 * {@code IsdaConvRecoveries[]}):
 * <ul>
 *   <li>SECDOM = 0.65</li>
 *   <li>SNRFOR = 0.40</li>
 *   <li>SUBLT2 = 0.20</li>
 *   <li>JRSUBT2 = 0.20</li>
 *   <li>PREFT1 = 0.15</li>
 * </ul>
 *
 * <p>Phase 4m foundation.
 */
public class RecoveryRateQuote extends Quote {

    /** Conventional recoveries for ISDA seniorities (matches C++ static array). */
    public static final double[] ISDA_CONV_RECOVERIES = { 0.65, // SECDOM
            0.40, // SNRFOR
            0.20, // SUBLT2
            0.20, // JRSUBUT2
            0.15  // PREFT1
    };

    private Seniority seniority;
    private double recoveryRate;

    public RecoveryRateQuote() {
        this(Constants.NULL_REAL, Seniority.NoSeniority);
    }

    public RecoveryRateQuote(final double value) {
        this(value, Seniority.NoSeniority);
    }

    public RecoveryRateQuote(final double value, final Seniority seniority) {
        this.seniority = seniority;
        this.recoveryRate = value;
        // C++: value == Null<Real>() OR value in [0,1]. NULL_REAL == Double.MAX_VALUE.
        QL.require(value == Constants.NULL_REAL || Double.isNaN(value) || (value >= 0.0 && value <= 1.0),
                "Recovery value must be a fractional unit.");
    }

    /** Returns the ISDA conventional recovery rate for {@code sen}. */
    public static double conventionalRecovery(final Seniority sen) {
        return ISDA_CONV_RECOVERIES[sen.ordinal()];
    }

    /**
     * Helper: turn a set of recoveries into a seniority-recovery map. Mirrors the C++ template
     * {@code makeIsdaMap<Size N>(const Real (&)[N])}.
     */
    public static Map< Seniority, Double > makeIsdaMap(final double[] arrayIsdaRR) {
        final Map< Seniority, Double > isdaMap = new EnumMap<>(Seniority.class);
        final Seniority[] values = Seniority.values();
        for ( int i = 0; i < arrayIsdaRR.length && i < values.length; i++ ) {
            isdaMap.put(values[i], arrayIsdaRR[i]);
        }
        return isdaMap;
    }

    /** Helper for ISDA conventional recoveries. */
    public static Map< Seniority, Double > makeIsdaConvMap() {
        return makeIsdaMap(ISDA_CONV_RECOVERIES);
    }

    public Seniority seniority() {
        return seniority;
    }

    @Override
    public double value() {
        QL.ensure(isValid(), "invalid Recovery Quote");
        return recoveryRate;
    }

    @Override
    public boolean isValid() {
        // not to be confused with proper initialization [0-1]
        // C++: recoveryRate_ != Null<Real>(). NULL_REAL == Double.MAX_VALUE.
        return recoveryRate != Constants.NULL_REAL && !Double.isNaN(recoveryRate);
    }

    /** Returns the difference between the new value and the old value. */
    public double setValue(final double value) {
        final double diff = value - recoveryRate;
        if ( diff != 0.0 ) {
            recoveryRate = value;
            notifyObservers();
        }
        return diff;
    }

    public double setValue() {
        return setValue(Constants.NULL_REAL);
    }

    public void reset() {
        setValue(Constants.NULL_REAL);
        seniority = Seniority.NoSeniority;
    }
}
