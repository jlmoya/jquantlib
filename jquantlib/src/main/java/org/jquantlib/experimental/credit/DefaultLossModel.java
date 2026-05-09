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
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2014 Jose Aparicio
*/

package org.jquantlib.experimental.credit;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Date;
import org.jquantlib.util.DefaultObservable;
import org.jquantlib.util.Observable;
import org.jquantlib.util.Observer;

/**
 * Default loss model interface.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::DefaultLossModel}
 * ({@code ql/experimental/credit/defaultlossmodel.hpp}). Concrete loss
 * models (Gaussian LHP, saddlepoint, binomial, etc.) extend this base and
 * override the statistics they support; unsupported queries throw a
 * {@link LibraryException}, mirroring the C++ {@code QL_FAIL} default
 * implementations.
 *
 * <p>The C++ class uses a {@code RelinkableHandle<Basket>} and the
 * {@link Basket} is friended so it can call {@link #setBasket(Basket)}.
 * The Java port keeps the basket reference package-private (effectively
 * the same access discipline) and exposes the protected query methods
 * publicly so {@link Basket} can call them across package boundaries
 * within the same package.
 *
 * <p>Phase 4m.5.
 */
public abstract class DefaultLossModel implements Observable, Observer {

    /** The basket this model is currently attached to. */
    protected Basket basket;

    protected DefaultLossModel() {
        // empty
    }

    /** Default implementation using the {@link #expectedTrancheLoss(Date)} method. */
    public double expectedTrancheLoss(final Date d) {
        throw new LibraryException("expectedTrancheLoss not implemented for this model.");
    }

    /**
     * Probability of the tranche losing the same or more than the
     * fractional amount given. The passed {@code lossFraction} is a
     * fraction of losses over the tranche notional (not the portfolio).
     */
    public double probOverLoss(final Date d, final double lossFraction) {
        throw new LibraryException("probOverLoss not implemented for this model.");
    }

    /** Value at Risk given a default loss percentile. */
    public double percentile(final Date d, final double percentile) {
        throw new LibraryException("percentile not implemented for this model.");
    }

    /** Expected shortfall given a default loss percentile. */
    public double expectedShortfall(final Date d, final double percentile) {
        throw new LibraryException("expectedShortfall not implemented for this model.");
    }

    /** Associated VaR fraction to each counterparty. */
    public List<Double> splitVaRLevel(final Date d, final double loss) {
        throw new LibraryException("splitVaRLevel not implemented for this model.");
    }

    /** Associated ESF fraction to each counterparty. */
    public List<Double> splitESFLevel(final Date d, final double loss) {
        throw new LibraryException("splitESFLevel not implemented for this model.");
    }

    /** Full loss distribution (loss amount → cumulative probability). */
    public Map<Double, Double> lossDistribution(final Date d) {
        throw new LibraryException("lossDistribution not implemented for this model.");
    }

    /** Probability density of a given loss fraction of the basket notional. */
    public double densityTrancheLoss(final Date d, final double lossFraction) {
        throw new LibraryException("densityTrancheLoss not implemented for this model.");
    }

    /**
     * Probabilities for each (remaining) basket element to have
     * defaulted by time {@code d} and at the same time be the Nth
     * defaulting name. Vector ordering matches pool ordering.
     */
    public List<Double> probsBeingNthEvent(final int n, final Date d) {
        throw new LibraryException("probsBeingNthEvent not implemented for this model.");
    }

    /** Pearson default-probability correlation. */
    public double defaultCorrelation(final Date d, final int iName, final int jName) {
        throw new LibraryException("defaultCorrelation not implemented for this model.");
    }

    /** Probability of a given or larger number of defaults in the basket portfolio at a given time. */
    public double probAtLeastNEvents(final int n, final Date d) {
        throw new LibraryException("probAtLeastNEvents not implemented for this model.");
    }

    /** Expected recovery rate for a name conditional to default by that date. */
    public double expectedRecovery(final Date d, final int iName, final DefaultProbKey key) {
        throw new LibraryException("expectedRecovery not implemented for this model.");
    }

    /**
     * Helper used internally by {@link Basket#setLossModel(DefaultLossModel)}
     * and {@link Basket#performCalculations()}. Concrete implementations
     * propagate the assignment via {@link #resetModel()}.
     */
    void setBasket(final Basket basket) {
        this.basket = basket;
        resetModel();
    }

    /** Reset / re-initialise on basket reassignment. Sole caller is {@link #setBasket(Basket)}. */
    protected abstract void resetModel();

    //
    // implements Observable / Observer
    //

    @Override
    public void update() {
        notifyObservers();
    }

    private final Observable delegatedObservable = new DefaultObservable(this);

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
    public final List<Observer> getObservers() {
        return delegatedObservable.getObservers();
    }

    /** Convenience helper used by tests / models requiring sorted results. */
    protected static Map<Double, Double> sortedDistribution(final Map<Double, Double> in) {
        return new TreeMap<>(in);
    }
}
