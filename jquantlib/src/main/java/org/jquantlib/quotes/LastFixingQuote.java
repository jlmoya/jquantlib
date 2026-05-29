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
 Copyright (C) 2008, 2014 Ferdinando Ametrano
*/

package org.jquantlib.quotes;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.indexes.Index;
import org.jquantlib.time.Date;
import org.jquantlib.util.Observer;

/**
 * Quote adapter for the last fixing available of a given {@link Index}.
 *
 * <p>Faithful port of {@code ql/quotes/lastfixingquote.hpp} +
 * {@code ql/quotes/lastfixingquote.cpp} v1.42.1
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>Wraps an {@link Index}; {@link #value()} returns the index fixing at
 * {@link #referenceDate()}, which is the earlier of the index's last stored
 * fixing date and the current evaluation date (C++
 * {@code std::min<Date>(index_->timeSeries().lastDate(), Settings::instance().evaluationDate())}).
 * Registers itself as an observer of the underlying index so downstream
 * observers fire whenever the index's fixing history changes.
 *
 * @see Quote
 * @see Index
 */
public class LastFixingQuote extends Quote implements Observer {

    protected final Index index_;

    public LastFixingQuote(final Index index) {
        this.index_ = index;
        // Mirror C++ registerWith(index_).
        index_.addObserver(this);
    }

    @Override
    public double value() {
        QL.ensure(isValid(), index_.name() + " has no fixing");
        return index_.fixing(referenceDate());
    }

    @Override
    public boolean isValid() {
        return !index_.timeSeries().isEmpty();
    }

    @Override
    public void update() {
        notifyObservers();
    }

    /** Mirrors C++ {@code LastFixingQuote::index()}. */
    public Index index() {
        return index_;
    }

    /**
     * Mirrors C++ {@code LastFixingQuote::referenceDate()}:
     * {@code std::min<Date>(index_->timeSeries().lastDate(), Settings::instance().evaluationDate())}.
     */
    public Date referenceDate() {
        return Date.min(index_.timeSeries().lastKey(),
                        new Settings().evaluationDate());
    }
}
