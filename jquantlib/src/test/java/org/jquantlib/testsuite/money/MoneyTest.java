/*
 Copyright (C) 2009 Ueli Hofstetter

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
package org.jquantlib.testsuite.money;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.currencies.Currency;
import org.jquantlib.currencies.ExchangeRate;
import org.jquantlib.currencies.ExchangeRateManager;
import org.jquantlib.currencies.Money;
import org.jquantlib.currencies.America.USDCurrency;
import org.jquantlib.currencies.Europe.EURCurrency;
import org.jquantlib.currencies.Europe.GBPCurrency;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.Rounding;
import org.junit.Test;

//FIXME: http://bugs.jquantlib.org/view.php?id=474
public class MoneyTest {

    public MoneyTest() {
        QL.info("::::: "+this.getClass().getSimpleName()+" :::::");
        QL.info("see testsuite.money.cpp/hpp");
    }

    @Test
    public void testBaseCurrency(){
        QL.info("Testing money arithmetic with conversion to base currency...");

        final Currency EUR = new EURCurrency();
        final Currency GBP = new GBPCurrency();
        final Currency USD = new USDCurrency();

        final Money m1 = Money.multiple(50000.0,GBP);
        final Money m2 = Money.multiple(100000.0 , EUR);
        final Money m3 = Money.multiple(500000.0 , USD);


        ExchangeRateManager.getInstance().clear();
        final ExchangeRate eur_usd = new  ExchangeRate(EUR, USD, 1.2042);
        final ExchangeRate eur_gbp = new ExchangeRate(EUR, GBP, 0.6612);
        ExchangeRateManager.getInstance().add(eur_usd);
        ExchangeRateManager.getInstance().add(eur_gbp);


        Money.conversionType = Money.ConversionType.BaseCurrencyConversion;
        Money.baseCurrency = EUR;

        //divided the steps for tracing...
        final Money calculated0 = m1.mul(3.0);
        final Money calculated1 = (m2.mul(2.5));
        final Money calculated2 = m3.div(5.0);

        final Money calculated3 = calculated0.add(calculated1).sub(calculated2);

        QL.info("Calculated value: " + calculated3.value());


        final Rounding round = Money.baseCurrency.rounding();
        /*@Decimal*/final double x = round.operator(m1.value()*3.0/eur_gbp.rate()) + 2.5*m2.value() -
        round.operator(m3.value()/(5.0*eur_usd.rate()));
        QL.info("Expected value: " + x);

        final Money expected = new Money(x, EUR);

        assertTrue(Closeness.isClose(calculated3.value(),expected.value()));
        if(!calculated3.equals(expected)) {
            fail("Wrong result: \n"
                    + "    expected:   " + expected + "\n"
                    + "    calculated: " + calculated3);
        }
        QL.info("testBaseCurrency done!");
    }


    @Test
    public void testNone() {
        QL.info("Testing money arithmetic without conversions...");
        final Currency EUR = new EURCurrency();
        final Money m1 = Money.multiple( 50000.0, EUR);
        final Money m2 = Money.multiple(100000.0, EUR);
        final Money m3 = Money.multiple(500000.0, EUR);

        Money.conversionType = Money.ConversionType.NoConversion;

        //divided the steps for tracing...
        final Money calculated0 = m1.mul(3.0);
        final Money calculated1 = (m2.mul(2.5));
        final Money calculated2 = m3.div(5.0);

        final Money calculated3 = calculated0.add(calculated1).sub(calculated2);

        QL.info("Calculated value: " + calculated3.value());

        /*Decimal*/final double x =  m1.value()*3.0 + 2.5*m2.value() - m3.value()/5.0;
        QL.info("Expected value: " + x);

        final Money expected = new Money(x, EUR);

        if(!calculated3.equals(expected)){
            fail("Wrong result: \n"
                    + "    expected:   " + expected + "\n"
                    + "    calculated: " + calculated3);
        }
        QL.info("testNone done!");
    }


    /**
     * Faithful port of test-suite/money.cpp:106 testAutomated.
     * <p>
     * Exercises {@link Money.ConversionType#AutomatedConversion}: when operands
     * carry different currencies, the right-hand operand is converted to the
     * left-hand operand's currency before the arithmetic is performed.  The
     * expected value is built by replicating the C++ closures EUR_to_GBP,
     * USD_to_EUR and USD_to_GBP that convert through the bilateral EUR/USD and
     * EUR/GBP rates.
     */
    @Test
    public void testAutomated() {
        QL.info("Testing money arithmetic with automated conversion...");

        final Currency EUR = new EURCurrency();
        final Currency GBP = new GBPCurrency();
        final Currency USD = new USDCurrency();

        final Money gbp = Money.multiple( 50000.0, GBP);
        final Money eur = Money.multiple(100000.0, EUR);
        final Money usd = Money.multiple(500000.0, USD);

        ExchangeRateManager.getInstance().clear();
        final ExchangeRate eur_usd = new ExchangeRate(EUR, USD, 1.2042);
        final ExchangeRate eur_gbp = new ExchangeRate(EUR, GBP, 0.6612);
        ExchangeRateManager.getInstance().add(eur_usd);
        ExchangeRateManager.getInstance().add(eur_gbp);

        Money.conversionType = Money.ConversionType.AutomatedConversion;

        // Mirrors C++ closures (money.cpp:117-120).
        // EUR_to_GBP(x) = x * eur_gbp_rate
        // USD_to_EUR(x) = x / eur_usd_rate
        // USD_to_GBP(x) = x * eur_gbp_rate / eur_usd_rate
        final double eurUsdRate = eur_usd.rate();
        final double eurGbpRate = eur_gbp.rate();

        // Java's Money does not overload + - * /; perform the same arithmetic
        // step by step.  Each binary op converts the right-hand operand to the
        // currency of the left under AutomatedConversion.
        final Money calculated0 = gbp.mul(3.0);                  // gbp*3.0 (GBP)
        final Money calculated1 = eur.mul(2.5);                  // 2.5*eur (EUR)
        final Money calculated2 = calculated0.add(calculated1);  // GBP + EUR -> GBP
        final Money calculated3 = usd.div(5.0);                  // usd/5.0 (USD)
        final Money calculated4 = calculated2.sub(calculated3);  // GBP - USD -> GBP
        // C++: gbp * (eur/usd).  Money/Money returns a Real after converting
        // the right operand to the left's currency under AutomatedConversion.
        // Java's Money.div(Money) implements the same; the ratio is a Real
        // suitable for Money.mul(double).
        final double eurOverUsd = eur.div(usd);
        final Money calculated5 = gbp.mul(eurOverUsd);
        final Money calculated = calculated4.add(calculated5);

        final double x = gbp.value() * 3.0
                + 2.5 * (eur.value() * eurGbpRate)
                - (usd.value() * eurGbpRate / eurUsdRate) / 5.0
                + gbp.value() * eur.value() / (usd.value() / eurUsdRate);

        final Money expected = new Money(x, GBP);

        ExchangeRateManager.getInstance().clear();
        Money.conversionType = Money.ConversionType.NoConversion;

        QL.info("Calculated value: " + calculated.value() + " " + calculated.currency().code());
        QL.info("Expected   value: " + expected.value() + " " + expected.currency().code());

        if (calculated.currency().ne(expected.currency())) {
            fail("currency mismatch: expected " + expected.currency().code()
                    + ", got " + calculated.currency().code());
        }
        // Tolerance mirrors the C++ {@code IsSameCurrencyAndValuesAreClose}
        // helper (test-suite/money.cpp:35-38) which uses an absolute
        // tolerance of 0.01 (one cent).  This is intentionally loose because
        // {@code gbp * (eur/usd)} and {@code gbp.value*eur.value/USD_to_EUR
        // (usd.value)} are algebraically equal but produce slightly different
        // floating-point results depending on the order of multiplications
        // and divisions.  CLAUDE.md inline-justification: same tolerance as
        // upstream C++ v1.42.1 test.
        final double absDiff = Math.abs(calculated.value() - expected.value());
        if (absDiff >= 0.01) {
            fail("Wrong result (abs err " + absDiff + ")\n"
                    + "    expected:   " + expected.value() + " " + expected.currency().code() + "\n"
                    + "    calculated: " + calculated.value() + " " + calculated.currency().code());
        }
    }


    /**
     * Faithful port of test-suite/money.cpp:143 testComparisons.
     * <p>
     * Sweeps the three {@link Money.ConversionType} modes and verifies the
     * comparison operators (==, !=, <, <=, >, >=, close, close_enough) behave
     * the same in Java as in C++ v1.42.1, including the cross-currency
     * conversions enabled under AutomatedConversion and BaseCurrencyConversion.
     */
    @Test
    public void testComparisons() {
        QL.info("Testing money comparisons...");

        final Currency EUR = new EURCurrency();
        final Currency USD = new USDCurrency();
        final Currency GBP = new GBPCurrency();

        final ExchangeRate eur_usd = new ExchangeRate(EUR, USD, 1.2042);
        final ExchangeRate eur_gbp = new ExchangeRate(EUR, GBP, 0.6612);

        final Money.ConversionType[] modes = new Money.ConversionType[] {
                Money.ConversionType.AutomatedConversion,
                Money.ConversionType.NoConversion,
                Money.ConversionType.BaseCurrencyConversion,
        };

        for (final Money.ConversionType conversionType : modes) {
            ExchangeRateManager.getInstance().clear();
            ExchangeRateManager.getInstance().add(eur_usd);
            ExchangeRateManager.getInstance().add(eur_gbp);
            Money.conversionType = conversionType;
            if (conversionType == Money.ConversionType.BaseCurrencyConversion) {
                Money.baseCurrency = EUR;
            }

            // equality
            assertTrue("eq same-currency", new Money(123.45, EUR).equals(new Money(123.45, EUR)));
            if (conversionType != Money.ConversionType.NoConversion) {
                assertTrue("eq cross-currency", new Money(1, EUR).equals(new Money(eur_usd.rate(), USD)));
            }

            // unequal
            assertTrue("ne same-currency", new Money(1, EUR).notEquals(new Money(2, EUR)));
            if (conversionType != Money.ConversionType.NoConversion) {
                assertTrue("ne cross-currency", new Money(1, EUR).notEquals(new Money(100, USD)));
            }

            // less than
            assertTrue("lt same-currency", new Money(1, EUR).less(new Money(2, EUR)));
            if (conversionType != Money.ConversionType.NoConversion) {
                assertTrue("lt cross-currency", new Money(1, EUR).less(new Money(100, USD)));
            }

            // less or equal than
            assertTrue("le strict",  new Money(1, EUR).lessEquals(new Money(2, EUR)));
            assertTrue("le equal",   new Money(2, EUR).lessEquals(new Money(2, EUR)));
            if (conversionType != Money.ConversionType.NoConversion) {
                assertTrue("le cross-currency", new Money(1, EUR).lessEquals(new Money(100, USD)));
            }

            // greater than
            assertTrue("gt same-currency", new Money(2, EUR).greater(new Money(1, EUR)));
            if (conversionType != Money.ConversionType.NoConversion) {
                assertTrue("gt cross-currency", new Money(100, EUR).greater(new Money(1, USD)));
            }

            // greater or equal than
            assertTrue("ge strict",  new Money(2, EUR).greaterEqual(new Money(1, EUR)));
            assertTrue("ge equal",   new Money(2, EUR).greaterEqual(new Money(2, EUR)));
            if (conversionType != Money.ConversionType.NoConversion) {
                assertTrue("ge cross-currency", new Money(100, EUR).greaterEqual(new Money(1, USD)));
            }

            // close (within 42 ulps; same default as C++ close())
            assertTrue("close exact",  new Money(1, EUR).close(new Money(1, EUR), 42));
            assertTrue("close eps",    new Money(1 + 1e-15, EUR).close(new Money(1, EUR), 42));
            if (conversionType != Money.ConversionType.NoConversion) {
                assertTrue("close cross-currency exact",
                        new Money(1, EUR).close(new Money(eur_usd.rate(), USD), 42));
                assertTrue("close cross-currency eps",
                        new Money(1 + 1e-15, EUR).close(new Money(eur_usd.rate(), USD), 42));
            }

            // close_enough
            assertTrue("close_enough exact", new Money(1, EUR).close_enough(new Money(1, EUR), 42));
            assertTrue("close_enough eps",   new Money(1 + 1e-15, EUR).close_enough(new Money(1, EUR), 42));
            if (conversionType != Money.ConversionType.NoConversion) {
                assertTrue("close_enough cross-currency exact",
                        new Money(1, EUR).close_enough(new Money(eur_usd.rate(), USD), 42));
                assertTrue("close_enough cross-currency eps",
                        new Money(1 + 1e-15, EUR).close_enough(new Money(eur_usd.rate(), USD), 42));
            }

            ExchangeRateManager.getInstance().clear();
            Money.conversionType = Money.ConversionType.NoConversion;
        }
    }

}
