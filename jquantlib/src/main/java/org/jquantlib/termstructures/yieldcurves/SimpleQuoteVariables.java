/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

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

package org.jquantlib.termstructures.yieldcurves;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.quotes.SimpleQuote;

/**
 * Concrete {@link AdditionalBootstrapVariables} that exposes a vector of
 * {@link SimpleQuote}s as variables to be solved jointly with the curve data
 * during a {@link GlobalBootstrap}.
 *
 * <p>Faithful port of QuantLib v1.42.1
 * {@code ql/termstructures/globalbootstrapvars.{hpp,cpp}} (commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>For each quote {@code i}, optionally consult two constructor-supplied vectors:
 *       {@code initialGuesses[i]} (cold-start seed) and {@code lowerBounds[i]} (positivity
 *       transform anchor).</li>
 *   <li>{@link #initialize(boolean)} returns an {@link Array} of optimiser-space values:
 *       {@code transformInverse(guess, i)} where the guess is the quote's current value
 *       when {@code validData} is {@code true}, or the cold-start seed otherwise.</li>
 *   <li>{@link #update(Array)} writes each {@code transformDirect(x[i], i)} back into
 *       the corresponding {@link SimpleQuote}.</li>
 * </ol>
 *
 * <h3>Transform pair</h3>
 * <ul>
 *   <li>If {@code lowerBounds[i] != NULL_REAL}:
 *       {@code transformDirect(x) = exp(x) + lb}, {@code transformInverse(x) = log(x - lb)}.
 *       Enforces {@code quote > lb} for any optimiser-space {@code x}.</li>
 *   <li>Otherwise: identity in both directions.</li>
 * </ul>
 *
 * <p>The C++ {@code detail::get(vec, i, default)} helper is reproduced inline as
 * {@link #getOrDefault(List, int, double)}: returns the i-th element if the vector
 * is non-empty and {@code i &lt; size}, otherwise the back element if {@code i &gt;= size},
 * or the default if the vector is empty.
 */
public class SimpleQuoteVariables implements AdditionalBootstrapVariables {

    //
    // private fields
    //

    private final List< SimpleQuote > quotes_;
    private final List< Double > initialGuesses_;
    private final List< Double > lowerBounds_;

    //
    // public constructors
    //

    /** Convenience ctor — no initial guesses, no lower bounds (identity transform). */
    public SimpleQuoteVariables(final List< SimpleQuote > quotes) {
        this(quotes, new ArrayList< Double >(), new ArrayList< Double >());
    }

    /** Convenience ctor — no lower bounds. */
    public SimpleQuoteVariables(final List< SimpleQuote > quotes, final List< Double > initialGuesses) {
        this(quotes, initialGuesses, new ArrayList< Double >());
    }

    /**
     * Mirrors C++ ctor at {@code globalbootstrapvars.cpp:10}.
     *
     * @param quotes         the {@link SimpleQuote}s whose values are optimised.
     * @param initialGuesses optional cold-start seeds; must have size {@code <= quotes.size()}.
     *                       Shorter vectors default to {@code 0.0} for the trailing entries.
     * @param lowerBounds    optional lower-bound anchors; must have size {@code <= quotes.size()}.
     *                       Per-index: {@code NULL_REAL} (or missing) selects the identity
     *                       transform; any other value selects the {@code exp/log} transform.
     */
    public SimpleQuoteVariables(final List< SimpleQuote > quotes, final List< Double > initialGuesses,
            final List< Double > lowerBounds) {
        this.quotes_ = new ArrayList< SimpleQuote >(quotes);
        this.initialGuesses_ = initialGuesses == null ? new ArrayList< Double >()
                : new ArrayList< Double >(initialGuesses);
        this.lowerBounds_ = lowerBounds == null ? new ArrayList< Double >()
                : new ArrayList< Double >(lowerBounds);
        QL.require(this.initialGuesses_.size() <= this.quotes_.size(), "too many initialGuesses");
        QL.require(this.lowerBounds_.size() <= this.quotes_.size(), "too many lowerBounds");
    }

    //
    // implements AdditionalBootstrapVariables
    //

    @Override
    public Array initialize(final boolean validData) {
        final int size = quotes_.size();
        final Array guesses = new Array(size);
        for ( int i = 0; i < size; ++i ) {
            final double guess;
            if ( validData ) {
                guess = quotes_.get(i).value();
            } else {
                guess = getOrDefault(initialGuesses_, i, 0.0);
                quotes_.get(i).setValue(guess);
            }
            guesses.set(i, transformInverse(guess, i));
        }
        return guesses;
    }

    @Override
    public void update(final Array x) {
        final int size = x.size();
        for ( int i = 0; i < size; ++i ) {
            quotes_.get(i).setValue(transformDirect(x.get(i), i));
        }
    }

    //
    // private helpers
    //

    /**
     * Mirror C++ {@code detail::get(vec, i, default)} from {@code ql/utilities/vectors.hpp}.
     * Empty vector → default; in-range index → element; out-of-range → back element.
     */
    private static double getOrDefault(final List< Double > v, final int i, final double defaultValue) {
        if ( v.isEmpty() ) {
            return defaultValue;
        }
        if ( i < v.size() ) {
            return v.get(i);
        }
        return v.get(v.size() - 1);
    }

    /**
     * {@code x  ->  lb == NULL_REAL ? x : exp(x) + lb}.
     */
    private double transformDirect(final double x, final int i) {
        final double lb = getOrDefault(lowerBounds_, i, Constants.NULL_REAL);
        return lb == Constants.NULL_REAL ? x : Math.exp(x) + lb;
    }

    /**
     * {@code x  ->  lb == NULL_REAL ? x : log(x - lb)}.
     */
    private double transformInverse(final double x, final int i) {
        final double lb = getOrDefault(lowerBounds_, i, Constants.NULL_REAL);
        return lb == Constants.NULL_REAL ? x : Math.log(x - lb);
    }
}
