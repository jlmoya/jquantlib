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

package org.jquantlib.termstructures.credit;

import org.jquantlib.Settings;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.time.Date;

/**
 * Bootstrap helper with date schedule relative to the global evaluation date, specialised to default-probability term
 * structures.
 *
 * <p>Java port of QuantLib v1.42.1 typedef
 * {@code typedef RelativeDateBootstrapHelper<DefaultProbabilityTermStructure> RelativeDateDefaultProbabilityHelper}
 * ({@code ql/termstructures/credit/defaultprobabilityhelpers.hpp:44-45}).
 *
 * <p>Mirrors {@link org.jquantlib.termstructures.yieldcurves.RelativeDateRateHelper}
 * for the yield-curve case: rebuilds the helper's schedule when the evaluation date changes. Subclasses (currently
 * {@link CdsHelper} and its descendants) implement {@link #initializeDates()}.
 */
public abstract class RelativeDateDefaultProbabilityHelper extends DefaultProbabilityHelper {

    protected Date evaluationDate_;

    public RelativeDateDefaultProbabilityHelper(final Handle< Quote > quote) {
        super(quote);
        this.evaluationDate_ = new Settings().evaluationDate();
        this.evaluationDate_.addObserver(this);
    }

    public RelativeDateDefaultProbabilityHelper(final double quote) {
        super(quote);
        this.evaluationDate_ = new Settings().evaluationDate();
        this.evaluationDate_.addObserver(this);
    }

    /**
     * Subclasses populate {@code earliestDate} / {@code latestDate} (and any internal schedule) from the current
     * {@code evaluationDate_}.
     */
    protected abstract void initializeDates();

    @Override
    public void update() {
        final Date newEval = new Settings().evaluationDate();
        if ( !evaluationDate_.equals(newEval) ) {
            evaluationDate_ = newEval;
            initializeDates();
        }
        super.update();
    }
}
