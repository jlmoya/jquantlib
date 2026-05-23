/*
 Copyright (C) 2026 Jose Moya

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

package org.jquantlib.testsuite.instruments.bonds;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.instruments.bonds.BTP;
import org.jquantlib.instruments.bonds.CCTEU;
import org.jquantlib.instruments.bonds.RendistatoBasket;
import org.jquantlib.instruments.bonds.RendistatoCalculator;
import org.jquantlib.instruments.bonds.RendistatoEquivalentSwapLengthQuote;
import org.jquantlib.instruments.bonds.RendistatoEquivalentSwapSpreadQuote;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

/**
 * Construction + structural smoke tests for the Italian-bond family ported
 * in Phase 2 L3-B (BTP, CCTEU, RendistatoBasket, RendistatoCalculator,
 * RendistatoEquivalentSwap{Length,Spread}Quote).
 *
 * <p>The C++ test-suite has no dedicated cases for these classes; smoke
 * coverage focuses on:
 * <ol>
 *   <li>cashflows non-empty / maturity alignment for BTP and CCTEU,</li>
 *   <li>Rendistato basket weight normalization and outstanding sum,</li>
 *   <li>Rendistato calculator wiring + quote adapter sanity (value()
 *       runs without throwing, isValid() returns true).</li>
 * </ol>
 */
public class ItalianBondsSmokeTest {

    @Test
    public void testBtpConstructAndStructure() {
        final Date issue = new Date(15, Month.January, 2020);
        final Date maturity = new Date(15, Month.January, 2030);

        final BTP btp = new BTP(maturity, /* fixedRate */ 0.03, /* startDate */ issue, issue);
        assertNotNull("cashflows must be non-null", btp.cashflows());
        assertTrue("cashflows must be non-empty", !btp.cashflows().isEmpty());
        assertEquals("maturity matches schedule", maturity, btp.maturityDate());

        // accruedAmount() at issue date returns 0 (or rounded-zero ≤ 1e-5)
        final double acc = btp.accruedAmount(issue);
        assertTrue("accrued at issue near zero", Math.abs(acc) < 1.0e-5);

        // yield() via overload — at clean price 100 with 3% coupon, yield is
        // ≈3% but not exactly so under BTP's ActualActual.ISMA day-count +
        // 2-day settlement offset. We assert only that the yield is in a
        // sane par-ish range; the C++ NewtonSafe-vs-Java Brent IRR converge
        // identically to the C++ value, but the absolute number is bond-
        // mechanics-dependent (not a regression).
        final double y = btp.yield(/* cleanPrice */ 100.0, issue, 1.0e-8, 100);
        assertTrue("BTP yield at par price within [0.025, 0.035]", y > 0.025 && y < 0.035);
    }

    @Test
    public void testBtpLegacyRedemption() {
        // Legacy non-par BTP IT123456789012 (99.999 redemption)
        final Date issue = new Date(15, Month.May, 2015);
        final Date maturity = new Date(15, Month.May, 2037);
        final BTP btp = new BTP(maturity, /* fixedRate */ 0.04, /* redemption */ 99.999, issue, issue);
        assertNotNull("cashflows must be non-null", btp.cashflows());
        assertTrue("cashflows must be non-empty", !btp.cashflows().isEmpty());
    }

    @Test
    public void testCcteuConstructAndStructure() {
        new Settings().setEvaluationDate(new Date(15, Month.January, 2020));
        final Date issue = new Date(15, Month.January, 2020);
        final Date maturity = new Date(15, Month.January, 2027);

        // Use a flat-forward forecast curve so the index can resolve forwards.
        final Handle< YieldTermStructure > fwd = new Handle< YieldTermStructure >(
                new FlatForward(issue, 0.01, new Actual360()));

        final CCTEU ccteu = new CCTEU(maturity, /* spread */ 0.001, fwd, issue, issue);
        assertNotNull("cashflows must be non-null", ccteu.cashflows());
        assertTrue("cashflows must be non-empty", !ccteu.cashflows().isEmpty());
        assertEquals("maturity matches schedule", maturity, ccteu.maturityDate());
    }

    @Test
    public void testRendistatoBasketWeightsAndOutstanding() {
        final Date issue = new Date(15, Month.January, 2020);
        final BTP btp1 = new BTP(new Date(15, Month.January, 2030), 0.03, issue, issue);
        final BTP btp2 = new BTP(new Date(15, Month.January, 2035), 0.04, issue, issue);
        final BTP btp3 = new BTP(new Date(15, Month.January, 2040), 0.05, issue, issue);

        final List< BTP > btps = new ArrayList<>();
        btps.add(btp1);
        btps.add(btp2);
        btps.add(btp3);

        final List< Double > outstandings = new ArrayList<>();
        outstandings.add(100.0);
        outstandings.add(200.0);
        outstandings.add(300.0);

        final List< Handle< Quote > > quotes = new ArrayList<>();
        quotes.add(new Handle< Quote >(new SimpleQuote(99.5)));
        quotes.add(new Handle< Quote >(new SimpleQuote(101.0)));
        quotes.add(new Handle< Quote >(new SimpleQuote(102.5)));

        final RendistatoBasket basket = new RendistatoBasket(btps, outstandings, quotes);

        assertEquals("size", 3, basket.size());
        assertEquals("outstanding", 600.0, basket.outstanding(), 1.0e-12);
        assertEquals("weight[0]", 100.0 / 600.0, basket.weights().get(0), 1.0e-12);
        assertEquals("weight[1]", 200.0 / 600.0, basket.weights().get(1), 1.0e-12);
        assertEquals("weight[2]", 300.0 / 600.0, basket.weights().get(2), 1.0e-12);

        // weights sum to 1
        double sum = 0.0;
        for (int i = 0; i < basket.weights().size(); ++i) {
            sum += basket.weights().get(i);
        }
        assertEquals("weights sum to 1", 1.0, sum, 1.0e-12);
    }

    @Test
    public void testRendistatoCalculatorAndQuoteAdapters() {
        // anchor evaluation date so the synthetic 1..15Y swaps schedule
        // forward of basket bonds
        new Settings().setEvaluationDate(new Date(15, Month.January, 2020));
        final Date issue = new Date(15, Month.January, 2020);

        final BTP btp1 = new BTP(new Date(15, Month.January, 2025), 0.03, issue, issue);
        final BTP btp2 = new BTP(new Date(15, Month.January, 2030), 0.04, issue, issue);

        final List< BTP > btps = new ArrayList<>();
        btps.add(btp1);
        btps.add(btp2);

        final List< Double > outstandings = new ArrayList<>();
        outstandings.add(500.0);
        outstandings.add(500.0);

        final List< Handle< Quote > > quotes = new ArrayList<>();
        quotes.add(new Handle< Quote >(new SimpleQuote(100.0)));
        quotes.add(new Handle< Quote >(new SimpleQuote(100.0)));

        final RendistatoBasket basket = new RendistatoBasket(btps, outstandings, quotes);

        // Discount + forecast curves
        final Handle< YieldTermStructure > curve = new Handle< YieldTermStructure >(
                new FlatForward(issue, 0.02, new Actual360()));
        final Euribor6M index = new Euribor6M(curve);

        final RendistatoCalculator calc = new RendistatoCalculator(basket, index, curve);

        // basket-weighted yield: each BTP @ par yields ≈ coupon → weighted ≈ 3.5%
        // (loose — not bit-exact since BTP yield ≠ coupon under ActualActual.ISMA)
        final double y = calc.yield();
        assertTrue("Rendistato yield within sane range [0.02, 0.06]", y > 0.02 && y < 0.06);

        // swap proxies must be addressable
        assertNotNull("equivalent swap", calc.equivalentSwap());
        assertTrue("equivalentSwapLength > 0", calc.equivalentSwapLength() > 0.0);

        // Quote adapters
        final RendistatoEquivalentSwapLengthQuote lenQuote = new RendistatoEquivalentSwapLengthQuote(calc);
        assertTrue("length quote valid", lenQuote.isValid());
        assertEquals("length quote == equivalentSwapLength", calc.equivalentSwapLength(), lenQuote.value(), 1.0e-12);

        final RendistatoEquivalentSwapSpreadQuote sprQuote = new RendistatoEquivalentSwapSpreadQuote(calc);
        assertTrue("spread quote valid", sprQuote.isValid());
        assertEquals("spread quote == equivalentSwapSpread", calc.equivalentSwapSpread(), sprQuote.value(), 1.0e-12);
    }
}
