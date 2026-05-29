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
 Copyright (C) 2007 Ferdinando Ametrano
*/

package org.jquantlib.quotes;

import org.jquantlib.Settings;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.LazyObject;

/**
 * Quote for a forward-starting swap.
 *
 * <p>Faithful port of {@code ql/quotes/forwardswapquote.hpp} +
 * {@code ql/quotes/forwardswapquote.cpp} v1.42.1
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>Given a {@link SwapIndex}, a spread {@link Handle Handle&lt;Quote&gt;} and a
 * forward-start {@link Period}, computes the fair fixed rate of the underlying
 * forward-starting swap (plus spread), as
 * {@code -(floatingLegNPV + spreadNPV) / (fixedLegBPS/bp)} — see C++
 * {@code performCalculations()}.
 *
 * <p>Unlike its C++ counterpart, this class does <b>not</b> extend {@link Quote}
 * — Java single inheritance forces a choice between {@link Quote} and
 * {@link LazyObject}. Following the convention used by {@link ImpliedStdDevQuote}
 * (and JQuantLib's broader migration pattern for {@code Quote}+{@code LazyObject}
 * classes), we extend {@link LazyObject} and expose the {@link Quote}-style API
 * ({@code value()}, {@code isValid()}) directly.
 *
 * <p>Registers with the swap index, the spread handle, and the evaluation date
 * (mirroring C++ {@code registerWith(...)}). When the evaluation date changes,
 * {@link #update()} rebuilds the swap dates before forwarding the lazy-object
 * notification.
 *
 * @see Quote
 * @see LazyObject
 * @see SwapIndex
 */
public class ForwardSwapQuote extends LazyObject {

    private static final double BASIS_POINT = 1.0e-4;

    private final SwapIndex swapIndex_;
    private final Handle<? extends Quote> spread_;
    private final Period fwdStart_;

    private Date evaluationDate_;
    private Date valueDate_;
    private Date startDate_;
    private Date fixingDate_;
    private VanillaSwap swap_;

    private double result_;

    public ForwardSwapQuote(final SwapIndex swapIndex,
                            final Handle<? extends Quote> spread,
                            final Period fwdStart) {
        super();
        this.swapIndex_ = swapIndex;
        this.spread_ = spread;
        this.fwdStart_ = fwdStart;
        // Mirror C++ registerWith(swapIndex_); registerWith(spread_);
        // registerWith(Settings::instance().evaluationDate());
        swapIndex_.addObserver(this);
        spread_.addObserver(this);
        final Date evalDate = new Settings().evaluationDate();
        evalDate.addObserver(this);
        // C++ (forwardswapquote.cpp:32) stores evaluationDate_ as a Date VALUE.
        // In JQuantLib, Settings.evaluationDate() returns the live thread-local
        // singleton (DateProxy), which setEvaluationDate(...) mutates IN PLACE.
        // Holding that reference would make the update() guard compare the proxy
        // against itself (always equal), so initializeDates() would never re-fire
        // on an eval-date change. Snapshot it by value (new Date(serialNumber()))
        // so the guard genuinely detects the change — matching C++ semantics.
        this.evaluationDate_ = new Date(evalDate.serialNumber());
        initializeDates();
    }

    protected void initializeDates() {
        valueDate_ = swapIndex_.fixingCalendar().advance(
                evaluationDate_,
                new Period(swapIndex_.fixingDays(), TimeUnit.Days),
                BusinessDayConvention.Following);
        startDate_ = swapIndex_.fixingCalendar().advance(
                valueDate_,
                fwdStart_,
                BusinessDayConvention.Following);
        fixingDate_ = swapIndex_.fixingDate(startDate_);
        swap_ = swapIndex_.underlyingSwap(fixingDate_);
    }

    @Override
    public void update() {
        final Date today = new Settings().evaluationDate();
        if (!evaluationDate_.eq(today)) {
            // Snapshot by value: 'today' is the live mutable singleton proxy
            // (see ctor). Storing a copy keeps the guard meaningful next time.
            evaluationDate_ = new Date(today.serialNumber());
            initializeDates();
        }
        super.update();
    }

    public Date valueDate() {
        calculate();
        return valueDate_;
    }

    public Date startDate() {
        calculate();
        return startDate_;
    }

    public Date fixingDate() {
        calculate();
        return fixingDate_;
    }

    public double value() {
        calculate();
        return result_;
    }

    public boolean isValid() {
        boolean swapIndexIsValid = true;
        try {
            swap_.recalculate();
        } catch (final RuntimeException e) {
            swapIndexIsValid = false;
        }
        final boolean spreadIsValid = spread_.empty() ? true : spread_.currentLink().isValid();
        return swapIndexIsValid && spreadIsValid;
    }

    @Override
    protected void performCalculations() {
        // we didn't register as observers - force calculation
        swap_.recalculate();
        // weak implementation... to be improved (mirrors C++ comment)
        final double floatingLegNPV = swap_.floatingLegNPV();
        final double spread = spread_.empty() ? 0.0 : spread_.currentLink().value();
        final double spreadNPV = swap_.floatingLegBPS() / BASIS_POINT * spread;
        final double totNPV = -(floatingLegNPV + spreadNPV);
        result_ = totNPV / (swap_.fixedLegBPS() / BASIS_POINT);
    }
}
