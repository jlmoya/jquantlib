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
 Copyright (C) 2006, 2007 Ferdinando Ametrano
 Copyright (C) 2006 François du Vignaud
*/

package org.jquantlib.quotes;

import org.jquantlib.indexes.Index;
import org.jquantlib.time.Date;
import org.jquantlib.util.Observer;

/**
 * Quote for the forward value of an index.
 *
 * <p>Faithful port of {@code ql/quotes/forwardvaluequote.hpp} +
 * {@code ql/quotes/forwardvaluequote.cpp} v1.42.1
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>Holds an {@link Index} and a fixing date; {@link #value()} returns
 * {@code index.fixing(fixingDate)}. Registers itself as observer on the
 * underlying index so downstream observers are notified whenever the index's
 * underlying term structure changes.
 *
 * @see Quote
 * @see Index
 */
public class ForwardValueQuote extends Quote implements Observer {

    private final Index index_;
    private final Date fixingDate_;

    public ForwardValueQuote(final Index index, final Date fixingDate) {
        this.index_ = index;
        this.fixingDate_ = fixingDate;
        index_.addObserver(this);
    }

    @Override
    public double value() {
        return index_.fixing(fixingDate_);
    }

    @Override
    public boolean isValid() {
        // C++ comment: "not sure this is the best approach..." — but the
        // contract is unconditional true. Mirror v1.42.1 exactly.
        return true;
    }

    @Override
    public void update() {
        notifyObservers();
    }
}
