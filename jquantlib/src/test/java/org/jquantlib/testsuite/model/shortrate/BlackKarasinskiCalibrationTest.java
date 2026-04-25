/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Tests for BlackKarasinski arguments_-indirection. See phase2b-design §3.3 WI-3.
 */
package org.jquantlib.testsuite.model.shortrate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.model.Parameter;
import org.jquantlib.model.shortrate.onefactormodels.BlackKarasinski;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * Reflection-based test verifying that BlackKarasinski's Parameter
 * accessors route through {@code arguments_.get(i)}.
 * <p>
 * BlackKarasinski has no closed-form pricing path that would let us
 * cross-validate against a v1.42.1 fingerprint (its dynamics()
 * requires tree-based fitting via TermStructureFittingParameter), so
 * this test confirms the indirection structurally — {@code aParam()}
 * and {@code sigmaParam()} must return the live {@code arguments_[i]}
 * instances and reflect the ctor's parameter values.
 */
public class BlackKarasinskiCalibrationTest {

    @Test
    public void parameterAccessors_routeThroughArguments() throws Exception {
        final Date today = new Date(22, Month.April, 2026);
        new Settings().setEvaluationDate(today);
        final YieldTermStructure ts = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.04)), new Actual365Fixed());

        final double a = 0.1;
        final double sigma = 0.01;
        final BlackKarasinski model = new BlackKarasinski(
                new Handle<YieldTermStructure>(ts), a, sigma);

        final Method aParam = BlackKarasinski.class.getDeclaredMethod("aParam");
        final Method sigmaParam = BlackKarasinski.class.getDeclaredMethod("sigmaParam");
        aParam.setAccessible(true);
        sigmaParam.setAccessible(true);

        final Parameter aP = (Parameter) aParam.invoke(model);
        final Parameter sP = (Parameter) sigmaParam.invoke(model);
        assertNotNull("aParam() must not be null", aP);
        assertNotNull("sigmaParam() must not be null", sP);
        assertEquals("aParam value must match ctor a", a, aP.get(0.0), 0.0);
        assertEquals("sigmaParam value must match ctor sigma", sigma, sP.get(0.0), 0.0);

        // Walk the inheritance chain (BlackKarasinski -> OneFactorModel ->
        // ShortRateModel -> CalibratedModel) to find the inherited
        // arguments_ field; the accessor must return the SAME instance
        // that lives in the slot.
        Class<?> c = BlackKarasinski.class;
        while (c != null && !c.getSimpleName().equals("CalibratedModel")) {
            c = c.getSuperclass();
        }
        assertNotNull("CalibratedModel must be in the inheritance chain", c);
        final Field argsField = c.getDeclaredField("arguments_");
        argsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        final List<Parameter> args = (List<Parameter>) argsField.get(model);
        assertSame("aParam() must be same instance as arguments_.get(0)", aP, args.get(0));
        assertSame("sigmaParam() must be same instance as arguments_.get(1)", sP, args.get(1));
    }
}
