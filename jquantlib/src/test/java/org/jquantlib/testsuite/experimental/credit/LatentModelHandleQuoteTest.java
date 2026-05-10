/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Phase 4m.7c-b — Handle<Quote> reactive constructor for LatentModel.

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
 */
package org.jquantlib.testsuite.experimental.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.jquantlib.experimental.credit.LatentModel;
import org.jquantlib.experimental.math.GaussianCopulaPolicy;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.util.Observer;
import org.junit.Test;

/**
 * Phase 4m.7c-b unit tests for {@link LatentModel}'s reactive single-factor
 * {@code Handle<Quote>} constructor and the corresponding observer wiring.
 *
 * <p>Cross-validation strategy: tests verify the quote-driven idiosyncratic
 * factor and factor-weight derivation against the closed-form formulas in
 * C++ {@code latentmodel.hpp::update}: {@code β = √(quote.value())} and
 * {@code idiosync = √(1 − quote.value())}.
 *
 * <p>Tolerance tier: TIGHT (1e-12 abs) — pure scalar arithmetic on the quote
 * value, no integration noise.
 */
public class LatentModelHandleQuoteTest {

    /**
     * Build a copula factory that constructs a fresh {@link GaussianCopulaPolicy}
     * for the given factor weights.
     */
    private static Function<List<List<Double>>, GaussianCopulaPolicy> gaussianFactory() {
        return GaussianCopulaPolicy::new;
    }

    private static Handle<Quote> quoteHandle(final double v) {
        final SimpleQuote q = new SimpleQuote(v);
        return new Handle<Quote>(q);
    }

    @Test
    public void constructionFromQuote_initialFactorsMatch() {
        final double rho = 0.36;  // correlation
        final Handle<Quote> h = quoteHandle(rho);
        final GaussianCopulaPolicy seedCopula = new GaussianCopulaPolicy(
                singleFactorWeights(Math.sqrt(rho), 5));
        final LatentModel<GaussianCopulaPolicy> m = new LatentModel<>(
                h, 5, seedCopula, gaussianFactory());

        assertNotNull(m);
        assertEquals(5, m.size());
        assertEquals(1, m.numFactors());
        // factorWeights[i].get(0) should be √rho
        for (int i = 0; i < 5; ++i) {
            assertEquals("factor weight at " + i,
                    Math.sqrt(rho), m.factorWeights().get(i).get(0), 1.0e-12);
        }
        // idiosyncratic: √(1 − rho)
        for (final double f : m.idiosyncFctrs()) {
            assertEquals("idiosync factor", Math.sqrt(1.0 - rho), f, 1.0e-12);
        }
    }

    @Test
    public void quoteUpdate_triggersFactorRecompute() {
        final double rhoInit = 0.20;
        final SimpleQuote q = new SimpleQuote(rhoInit);
        final Handle<Quote> h = new Handle<Quote>(q);
        final GaussianCopulaPolicy seedCopula = new GaussianCopulaPolicy(
                singleFactorWeights(Math.sqrt(rhoInit), 4));
        final LatentModel<GaussianCopulaPolicy> m = new LatentModel<>(
                h, 4, seedCopula, gaussianFactory());

        // Initial: idiosync = √(1 − 0.20) = √0.80 ≈ 0.8944
        assertEquals(Math.sqrt(0.80), m.idiosyncFctrs()[0], 1.0e-12);

        // Tick the quote.
        final double rhoNew = 0.50;
        q.setValue(rhoNew);

        // After update: idiosync = √(1 − 0.50) = √0.50 ≈ 0.7071
        assertEquals(Math.sqrt(0.50), m.idiosyncFctrs()[0], 1.0e-12);
        // and factor weights should be √0.50.
        for (int i = 0; i < 4; ++i) {
            assertEquals("factor weight after tick at " + i,
                    Math.sqrt(rhoNew), m.factorWeights().get(i).get(0), 1.0e-12);
        }
    }

    @Test
    public void quoteUpdate_notifiesDownstreamObservers() {
        final SimpleQuote q = new SimpleQuote(0.20);
        final Handle<Quote> h = new Handle<Quote>(q);
        final GaussianCopulaPolicy seedCopula = new GaussianCopulaPolicy(
                singleFactorWeights(Math.sqrt(0.20), 3));
        final LatentModel<GaussianCopulaPolicy> m = new LatentModel<>(
                h, 3, seedCopula, gaussianFactory());

        final AtomicInteger notifyCount = new AtomicInteger(0);
        // CRITICAL: keep a strong reference to the observer (WeakReference
        // observable contract — see project memory).
        final Observer obs = () -> notifyCount.incrementAndGet();
        m.addObserver(obs);

        q.setValue(0.30);
        assertTrue("observer fired at least once: " + notifyCount.get(),
                notifyCount.get() >= 1);

        q.setValue(0.40);
        assertTrue("observer fired again: " + notifyCount.get(),
                notifyCount.get() >= 2);
    }

    @Test
    public void emptyHandle_rejected() {
        final Handle<Quote> emptyHandle = new Handle<>();
        try {
            new LatentModel<>(emptyHandle, 3, new GaussianCopulaPolicy(),
                    gaussianFactory());
            fail("expected RuntimeException for empty handle");
        } catch (final RuntimeException e) {
            // expected
        }
    }

    @Test
    public void nullCopulaFactory_rejected() {
        final Handle<Quote> h = quoteHandle(0.20);
        final GaussianCopulaPolicy seedCopula = new GaussianCopulaPolicy(
                singleFactorWeights(Math.sqrt(0.20), 3));
        try {
            new LatentModel<>(h, 3, seedCopula, null);
            fail("expected RuntimeException for null copulaFactory");
        } catch (final RuntimeException e) {
            // expected
        }
    }

    @Test
    public void cachedMktFactor_accessible() {
        final SimpleQuote q = new SimpleQuote(0.25);
        final Handle<Quote> h = new Handle<Quote>(q);
        final GaussianCopulaPolicy seedCopula = new GaussianCopulaPolicy(
                singleFactorWeights(Math.sqrt(0.25), 2));
        final LatentModel<GaussianCopulaPolicy> m = new LatentModel<>(
                h, 2, seedCopula, gaussianFactory());

        assertNotNull(m.cachedMktFactor());
        assertEquals(0.25, m.cachedMktFactor().currentLink().value(), 1.0e-12);
    }

    @Test
    public void noQuoteCtor_cachedMktFactorIsNull() {
        // A LatentModel built without the Handle<Quote> ctor has no cached
        // quote — the field stays null.
        final LatentModel<GaussianCopulaPolicy> m = new LatentModel<>(
                singleFactorWeights(Math.sqrt(0.20), 3),
                new GaussianCopulaPolicy(singleFactorWeights(Math.sqrt(0.20), 3)));
        assertEquals(null, m.cachedMktFactor());
    }

    private static List<List<Double>> singleFactorWeights(final double w, final int n) {
        final List<List<Double>> rows = new ArrayList<>(n);
        for (int i = 0; i < n; ++i) {
            final List<Double> row = new ArrayList<>(1);
            row.add(w);
            rows.add(row);
        }
        return rows;
    }
}
