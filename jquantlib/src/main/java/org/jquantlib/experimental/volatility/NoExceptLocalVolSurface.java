/*
 Copyright (C) 2015 Klaus Spanderen
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

package org.jquantlib.experimental.volatility;

import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.LocalVolSurface;

/**
 * Wrapper around the Dupire {@link LocalVolSurface} that does not throw
 * when the local volatility derivation produces an invalid (e.g. negative
 * variance) intermediate value. Instead it substitutes a user-supplied
 * {@code illegalLocalVolOverwrite} value (typically the surface's at-the-money
 * level).
 *
 * <p>Java port of v1.42.1
 * {@code ql/termstructures/volatility/equityfx/noexceptlocalvolsurface.hpp}
 * (header-only in C++; the entire implementation is the wrapper class
 * below).
 *
 * <p>Use cases:
 * <ul>
 *   <li>Bootstrapping a local-vol surface from a Black-vol surface where
 *       arbitrage on the wings or the very short end produces locally
 *       non-smooth variance, which would otherwise cause
 *       {@link LocalVolSurface#localVolImpl} to throw.</li>
 *   <li>Path-generation on grids where transient negative-variance
 *       evaluations should be silently floored rather than aborting the
 *       simulation.</li>
 * </ul>
 *
 * @author Phase Production-Audit
 */
public class NoExceptLocalVolSurface extends LocalVolSurface {

    private final double illegalLocalVolOverwrite_;

    /**
     * Construct from a {@link Handle} underlying spot quote.
     *
     * @param blackTS                   Black-vol surface to differentiate
     * @param riskFreeTS                discounting curve
     * @param dividendTS                dividend curve
     * @param underlying                spot quote handle
     * @param illegalLocalVolOverwrite  vol value to return when the parent
     *                                  raises (e.g. negative variance)
     */
    public NoExceptLocalVolSurface(final Handle<BlackVolTermStructure> blackTS,
                                   final Handle<YieldTermStructure> riskFreeTS,
                                   final Handle<YieldTermStructure> dividendTS,
                                   final Handle<? extends Quote> underlying,
                                   final /*@Real*/ double illegalLocalVolOverwrite) {
        super(blackTS, riskFreeTS, dividendTS, underlying);
        this.illegalLocalVolOverwrite_ = illegalLocalVolOverwrite;
    }

    /**
     * Construct from a fixed underlying spot value (wrapped internally
     * in a {@code SimpleQuote}).
     */
    public NoExceptLocalVolSurface(final Handle<BlackVolTermStructure> blackTS,
                                   final Handle<YieldTermStructure> riskFreeTS,
                                   final Handle<YieldTermStructure> dividendTS,
                                   final /*@Real*/ double underlying,
                                   final /*@Real*/ double illegalLocalVolOverwrite) {
        super(blackTS, riskFreeTS, dividendTS, underlying);
        this.illegalLocalVolOverwrite_ = illegalLocalVolOverwrite;
    }

    /**
     * Returns the override value when the underlying Dupire derivation
     * fails, otherwise the parent class's value.
     *
     * <p>Mirrors C++ verbatim:
     * <pre>
     *   try   { vol = LocalVolSurface::localVolImpl(t, s); }
     *   catch (Error&amp;) { vol = illegalLocalVolOverwrite_; }
     * </pre>
     *
     * <p>Java catches all exceptions thrown by the parent (including
     * {@link RuntimeException} subclasses raised by
     * {@code QL.require}/{@code QL.ensure}). This is wider than the
     * C++ {@code catch (Error&)} but matches the only failure mode the
     * parent can produce in JQuantLib (a {@code LibraryException} from
     * the {@code QL.ensure(result &gt;= 0)} non-negativity check).
     */
    @Override
    protected /*@Volatility*/ double localVolImpl(final /*@Time*/ double t,
                                                  final /*@Real*/ double s) {
        try {
            return super.localVolImpl(t, s);
        } catch (final RuntimeException ex) {
            return illegalLocalVolOverwrite_;
        }
    }
}
