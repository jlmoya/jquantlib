/*
Copyright (C) 2009 John Martin

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
 Copyright (C) 2005, 2006, 2007, 2008 StatPro Italia srl
 Copyright (C) 2007 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

package org.jquantlib.termstructures;

import org.jquantlib.QL;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.time.Date;
import org.jquantlib.util.*;

import java.util.List;

/**
 * Base helper class for bootstrapping
 * <p>
 * This class provides an abstraction for the instruments used to bootstrap a term structure.
 * <p>
 * It is advised that a bootstrap helper for an instrument contains an instance of the actual instrument class to ensure
 * consistancy between the algorithms used during bootstrapping and later instrument pricing. This is not yet fully
 * enforced in the available rate helpers.
 */
public abstract class BootstrapHelper< TS extends TermStructure >
        implements Observer, Observable, PolymorphicVisitable {

    /**
     * Implements multiple inheritance via delegate pattern to an inner class
     *
     * @see Observable
     */
    // Phase 2x A.4: WeakReferenceObservable to break cumulative
    // observer-list bleed across tests.
    private final Observable delegatedObservable = new org.jquantlib.util.WeakReferenceObservable(this);
    protected Handle< Quote > quote;
    protected TS termStructure;
    protected Date earliestDate;
    protected Date latestDate;
    /**
     * Instrument's maturity date. Unset ({@code null} / null date) means "same as {@link #latestRelevantDate()}".
     * <p>
     * Mirrors C++ v1.43 {@code BootstrapHelper::maturityDate_} ({@code ql/termstructures/bootstraphelper.hpp:112}).
     */
    protected Date maturityDate;
    /**
     * Latest date at which data are needed by the helper in order to provide a quote. It does <i>not</i> necessarily
     * equal the instrument's maturity, nor the pillar the curve node is placed at: an instrument whose last cashflow
     * pays after its pillar (payment lag, payment-calendar adjustment) still requires the curve to reach that far.
     * Unset means "same as {@link #latestDate()}".
     * <p>
     * Mirrors C++ v1.43 {@code BootstrapHelper::latestRelevantDate_}
     * ({@code ql/termstructures/bootstraphelper.hpp:112}).
     */
    protected Date latestRelevantDate;
    /**
     * Date the curve node for this helper is placed at. Unset means "same as {@link #latestDate()}".
     * <p>
     * Mirrors C++ v1.43 {@code BootstrapHelper::pillarDate_} ({@code ql/termstructures/bootstraphelper.hpp:112}).
     */
    protected Date pillarDate;

    public BootstrapHelper(final Handle< Quote > quote) {
        this.quote = quote;
        this.quote.addObserver(this);
    }

    public BootstrapHelper(final double quote) {
        this.quote = new Handle< Quote >(new SimpleQuote(quote));
    }

    public abstract double impliedQuote();

    public double quoteError() {
        return quote.currentLink().value() - impliedQuote();
    }

    public boolean quoteIsValid() {
        return quote.currentLink().isValid();
    }

    public void setTermStructure(final TS c) {
        QL.ensure(c != null, "TermStructure cannot be null");
        this.termStructure = c;
    }

    public Date earliestDate() {
        return this.earliestDate;
    }

    /**
     * Instrument's maturity date.
     * <p>
     * Mirrors C++ v1.43 {@code BootstrapHelper::maturityDate()}
     * ({@code ql/termstructures/bootstraphelper.hpp:168-173}): falls back to {@link #latestRelevantDate()} when the
     * helper did not set a distinct maturity.
     */
    public Date maturityDate() {
        if ( this.maturityDate == null || this.maturityDate.isNull() )
            return latestRelevantDate();
        return this.maturityDate;
    }

    /**
     * Latest date at which data are needed by the helper in order to provide a quote — i.e. how far the bootstrapped
     * curve must actually reach for this instrument, which may be past its {@link #pillarDate()}.
     * <p>
     * Mirrors C++ v1.43 {@code BootstrapHelper::latestRelevantDate()}
     * ({@code ql/termstructures/bootstraphelper.hpp:175-180}): defaults to {@link #latestDate()}, so helpers that do
     * not distinguish the two are unaffected.
     */
    public Date latestRelevantDate() {
        if ( this.latestRelevantDate == null || this.latestRelevantDate.isNull() )
            return latestDate();
        return this.latestRelevantDate;
    }

    /**
     * Date the curve node for this helper is placed at by the bootstrap.
     * <p>
     * Mirrors C++ v1.43 {@code BootstrapHelper::pillarDate()}
     * ({@code ql/termstructures/bootstraphelper.hpp:182-187}): defaults to {@link #latestDate()}, so helpers that do
     * not distinguish the two are unaffected.
     */
    public Date pillarDate() {
        if ( this.pillarDate == null || this.pillarDate.isNull() )
            return latestDate();
        return this.pillarDate;
    }

    //
    // implements Observer
    //

    /**
     * Equal to {@link #pillarDate()} when the helper only set a pillar.
     * <p>
     * Mirrors C++ v1.43 {@code BootstrapHelper::latestDate()}
     * ({@code ql/termstructures/bootstraphelper.hpp:189-194}).
     */
    public Date latestDate() {
        if ( this.latestDate == null || this.latestDate.isNull() )
            return this.pillarDate;
        return this.latestDate;
    }

    //
    // implements Observable
    //

    public void update() {
        this.notifyObservers();
    }

    @Override
    public final void addObserver(final Observer observer) {
        delegatedObservable.addObserver(observer);
    }

    @Override
    public final int countObservers() {
        return delegatedObservable.countObservers();
    }

    @Override
    public final void deleteObserver(final Observer observer) {
        delegatedObservable.deleteObserver(observer);
    }

    @Override
    public final void notifyObservers() {
        delegatedObservable.notifyObservers();
    }

    @Override
    public final void notifyObservers(final Object arg) {
        delegatedObservable.notifyObservers(arg);
    }

    @Override
    public final void deleteObservers() {
        delegatedObservable.deleteObservers();
    }

    @Override
    public final List< Observer > getObservers() {
        return delegatedObservable.getObservers();
    }

    //
    // implements PolymorphicVisitable
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< BootstrapHelper > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            throw new LibraryException("not a bootstrap helper visitor");
        }
    }

}
