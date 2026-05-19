/*
 Copyright (C) 2015 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

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
package org.jquantlib.termstructures.volatilities.equityfx;

import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.LocalVolSurface;

/**
 * Wrapper around {@link LocalVolSurface} that, instead of throwing when the Dupire formula yields a negative
 * local-variance, returns a user-supplied fallback volatility {@code illegalLocalVolOverwrite}.
 *
 * <p>Java port of v1.42.1
 * {@code ql/termstructures/volatility/equityfx/noexceptlocalvolsurface.hpp} (header-only). Used by the Heston-SLV
 * calibration test infrastructure (test-suite/hestonslvmodel.cpp) where the implied-vol surface is built from a sparse
 * grid and Dupire's denominator can occasionally turn negative on the coarse pre-calibration grid.
 *
 * <p><strong>Behavioural contract:</strong> mirrors the C++ override exactly —
 * if {@link LocalVolSurface#localVolImpl(double, double)} throws (any {@code RuntimeException} subclass; C++ catches
 * {@code Error&}), this class returns {@code illegalLocalVolOverwrite} as the volatility. All other behaviour is
 * inherited unchanged.
 *
 * <h3>Java port note</h3>
 * The existing Java {@link LocalVolSurface} lives in {@code org.jquantlib.termstructures.volatilities} (one level up),
 * but the C++ source places it under {@code .../equityfx}. This wrapper is placed under {@code .equityfx} to mirror the
 * C++ package layout.
 *
 * @author Phase 5e.5b-CFC-d-131 port
 */
public class NoExceptLocalVolSurface extends LocalVolSurface {

    private final double illegalLocalVolOverwrite;

    /**
     * @param blackTS                  the underlying Black-vol term structure
     * @param riskFreeTS               risk-free yield curve
     * @param dividendTS               dividend yield curve
     * @param underlying               spot quote
     * @param illegalLocalVolOverwrite fallback vol returned when Dupire yields a negative local variance
     */
    public NoExceptLocalVolSurface(final Handle< BlackVolTermStructure > blackTS,
            final Handle< YieldTermStructure > riskFreeTS, final Handle< YieldTermStructure > dividendTS,
            final Handle< ? extends Quote > underlying, final double illegalLocalVolOverwrite) {
        super(blackTS, riskFreeTS, dividendTS, underlying);
        this.illegalLocalVolOverwrite = illegalLocalVolOverwrite;
    }

    /** Overload taking a raw spot value — mirrors C++ second ctor. */
    public NoExceptLocalVolSurface(final Handle< BlackVolTermStructure > blackTS,
            final Handle< YieldTermStructure > riskFreeTS, final Handle< YieldTermStructure > dividendTS,
            final /*@Real*/ double underlying, final double illegalLocalVolOverwrite) {
        super(blackTS, riskFreeTS, dividendTS, underlying);
        this.illegalLocalVolOverwrite = illegalLocalVolOverwrite;
    }

    @Override
    protected /*@Volatility*/ double localVolImpl(final /*@Time*/ double t, final /*@Real*/ double s) {
        try {
            return super.localVolImpl(t, s);
        } catch ( final RuntimeException e ) {
            // C++ catches Error&. Java QL.require/QL.ensure throw
            // IllegalArgumentException / IllegalStateException
            // (both RuntimeException). Match the C++ swallow-and-replace
            // contract.
            return illegalLocalVolOverwrite;
        }
    }
}
