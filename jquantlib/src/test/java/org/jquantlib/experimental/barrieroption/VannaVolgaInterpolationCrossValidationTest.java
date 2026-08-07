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

package org.jquantlib.experimental.barrieroption;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validates {@link VannaVolgaInterpolation} against the
 * {@code experimental/barrieroption/vanna_volga_interpolation} probe reference.
 * <p>
 * C++ hides the Castagna-Mercurio vega-weighted smile arithmetic in
 * {@code detail::VannaVolgaInterpolationImpl}
 * ({@code ql/experimental/barrieroption/vannavolgainterpolation.hpp:82}),
 * behind the public {@code VannaVolgaInterpolation} shell and the
 * {@code VannaVolga} traits tag. JQuantLib folds all three into this one class.
 * <p>
 * Before this test the only Java coverage of that arithmetic was indirect —
 * {@code BarrierOptionTest#testVannaVolgaSimpleBarrierValues} and
 * {@code DoubleBarrierOptionTest#testVannaVolgaValues} assert barrier-option
 * NPVs at 1e-4, several transformations downstream (smile vol to Black price to
 * vanna/volga survival-weighted correction to barrier price), from literals
 * transcribed out of the upstream C++ test suite. A sign error in one of the
 * three Lagrange weights can hide under that much smoothing. Here the
 * interpolated volatility itself is the assertion.
 * <p>
 * The strike grid runs from -2 to +2 standard deviations of log-moneyness
 * about the forward, which overshoots the three quoted strikes in every case,
 * so the extrapolated branch is exercised — both Java engines and both C++
 * engines call {@code enableExtrapolation()} on this interpolation
 * ({@code VannaVolgaBarrierEngine.java:154}, C++
 * {@code vannavolgabarrierengine.cpp:125}), so extrapolation is production
 * behaviour rather than an edge case.
 * <p>
 * <b>Tolerance tier: loose, 1e-8 relative.</b> Derivation, since this is not
 * the default tight tier:
 * <ul>
 * <li>{@code value(k)} ends in {@code blackFormulaImpliedStdDev}, a
 * {@code NewtonSafe} solve that stops at {@code accuracy = 1e-6} <i>on the
 * option price</i>. The recovered standard deviation is therefore only
 * determined to {@code 1e-6/vega}, and two implementations that stop at
 * different iterates may legitimately differ by that much. Inside |z| &lt;= 2,
 * {@code phi(d1) >= ~0.05}, so vega is bounded away from zero and that window
 * is ~1e-5 in volatility; beyond ~3 standard deviations it grows without
 * bound, which is exactly why the grid stops at 2.</li>
 * <li>Empirically both ports execute the <i>same</i> Newton iterations — same
 * seed from {@code blackFormulaImpliedStdDevApproximation}, same arithmetic,
 * same doubles — and agree to ~1e-12 across this grid.</li>
 * <li>1e-8 sits between the two: it leaves headroom for platform differences
 * in the normal CDF nudging the iteration, while remaining three orders
 * tighter than the solver's own guarantee and six orders tighter than any
 * plausible error in the vanna-volga weights, which would move the volatility
 * by O(1e-2).</li>
 * </ul>
 *
 * @author Jose Moya
 */
public class VannaVolgaInterpolationCrossValidationTest {

    private static final String GROUP = "experimental/barrieroption/vanna_volga_interpolation";
    private static final double REL = 1.0e-8;

    public VannaVolgaInterpolationCrossValidationTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static double[] toArray(final JSONArray a) {
        final double[] out = new double[a.length()];
        for (int i = 0; i < a.length(); i++) {
            out[i] = a.getDouble(i);
        }
        return out;
    }

    private static void checkCase(final String caseName) {
        final ReferenceReader ref = ReferenceReader.load(GROUP);
        final ReferenceReader.Case c = ref.getCase(caseName);
        final JSONObject in = c.inputs();

        final VannaVolgaInterpolation interp = new VannaVolgaInterpolation(
                toArray(in.getJSONArray("strikes")),
                toArray(in.getJSONArray("vols")),
                in.getDouble("spot"),
                in.getDouble("dDiscount"),
                in.getDouble("fDiscount"),
                in.getDouble("T"));

        final JSONArray rows = ((JSONObject) c.expectedRaw()).getJSONArray("rows");
        assertTrue(caseName + ": probe produced no rows", rows.length() > 0);
        for (int r = 0; r < rows.length(); r++) {
            final JSONObject row = rows.getJSONObject(r);
            final double k = row.getDouble("k");
            final double expected = row.getDouble("vol");
            assertEquals(caseName + " row " + r + " k=" + k,
                    expected, interp.value(k), REL * Math.abs(expected));

            // At the three quoted strikes the smile must return the quote it
            // was built from. C++ only reproduces it to the implied-vol
            // solver's own 1e-6 accuracy, so that is the bound here — this
            // assertion is about the construction being right, and the row
            // above already pins the exact number.
            if (row.has("quotedVol")) {
                assertEquals(caseName + " row " + r + ": quoted strike k=" + k
                        + " must reproduce its quote", row.getDouble("quotedVol"),
                        interp.value(k), 1.0e-5);
            }
        }
    }

    /** Symmetric 6-month FX smile. */
    @Test
    public void testSymmetricSixMonthSmile() {
        QL.info("Testing VannaVolgaInterpolation on a symmetric 6M FX smile against C++ v1.43...");
        checkCase("fx_symmetric_6m");
    }

    /** Risk-reversal-skewed 1-year smile: an asymmetric weighting of the three quotes. */
    @Test
    public void testSkewedOneYearSmile() {
        QL.info("Testing VannaVolgaInterpolation on a skewed 1Y FX smile against C++ v1.43...");
        checkCase("fx_skewed_1y");
    }

    /** One-month expiry: the vega weighting scales with sqrt(T), so short T needs its own case. */
    @Test
    public void testShortExpirySmile() {
        QL.info("Testing VannaVolgaInterpolation at a 1M expiry against C++ v1.43...");
        checkCase("fx_short_1m");
    }

    /** Five-year expiry, wide strike spread. */
    @Test
    public void testLongExpirySmile() {
        QL.info("Testing VannaVolgaInterpolation at a 5Y expiry against C++ v1.43...");
        checkCase("fx_long_5y");
    }
}
