/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.methods.finitedifferences.meshers;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.methods.finitedifferences.meshers.FdmHestonLocalVolatilityVarianceMesher;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.LocalConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Cross-validation tests for the gap-fdm port of {@link FdmHestonLocalVolatilityVarianceMesher} against C++ QuantLib
 * v1.42.1.
 *
 * <p>Reference data:
 * {@code migration-harness/references/methods/finitedifferences/meshers/fdm_heston_localvol_variance_mesher.json}.
 *
 * <p>Mesh locations / spacings are inherited verbatim from {@code FdmHestonVarianceMesher} (TIGHT). The
 * leverage-averaged {@code volaEstimate} is the genuinely new quantity: with a constant leverage L the running-mean
 * stays L and the Gauss-Lobatto integral of a constant integrand is exact, so the final estimate is
 * {@code plainVolaEstimate * L} — validating the whole averaging loop arithmetic.
 *
 * <p>Tier: TIGHT. The {@code volaEstimate} is computed via {@code GaussLobattoIntegral} + chi-square inversion, so it
 * inherits the LOOSE-ish slack of those primitives; we use 1e-8 relative.
 *
 * @author JQuantLib gap-fdm port
 */
public class FdmHestonLocalVolatilityVarianceMesherTest {

    /** Locations / spacings are exact copies of the (separately cross-validated) Heston variance mesh. */
    private static final double TIGHT = 1.0e-10;
    /** volaEstimate goes through GaussLobatto + chi-square inversion. */
    private static final double VOLA_REL = 1.0e-8;
    /** C++ Null<Real>() sentinel serialises as FLT_MAX; Java uses NaN. */
    private static final double NULL_SENTINEL = 1.0e30;

    private static final String GROUP =
            "methods/finitedifferences/meshers/fdm_heston_localvol_variance_mesher";

    public FdmHestonLocalVolatilityVarianceMesherTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    private HestonProcess buildProcess() {
        // Match the probe: S0=100, v0=0.04, kappa=2.0, theta=0.04, sigma=0.30, rho=-0.5; r=0.05, q=0.02
        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(15, Month.January, 2026);
        final Handle< Quote > spot = new Handle<>(new SimpleQuote(100.0));
        final Handle< YieldTermStructure > rTS =
                new Handle<>(new FlatForward(today, new Handle<>(new SimpleQuote(0.05)), dc));
        final Handle< YieldTermStructure > qTS =
                new Handle<>(new FlatForward(today, new Handle<>(new SimpleQuote(0.02)), dc));
        return new HestonProcess(rTS, qTS, spot, 0.04, 2.0, 0.04, 0.30, -0.5);
    }

    private void assertMeshMatches(final String name, final FdmHestonLocalVolatilityVarianceMesher m) {
        final ReferenceReader ref = ReferenceReader.load(GROUP);
        final ReferenceReader.Case rc = ref.getCase(name);
        final JSONObject exp = (JSONObject) rc.expectedRaw();

        final JSONArray locs = exp.getJSONArray("locations");
        final JSONArray dplus = exp.getJSONArray("dplus");
        final JSONArray dminus = exp.getJSONArray("dminus");
        final int size = locs.length();

        for ( int i = 0; i < size; ++i ) {
            assertEquals(name + " loc[" + i + "]", locs.getDouble(i), m.location(i), TIGHT);

            // dplus: last cell is the Null sentinel (NaN in Java)
            if ( i == size - 1 || dplus.getDouble(i) >= NULL_SENTINEL ) {
                assertTrue(name + " dplus[" + i + "] sentinel", Double.isNaN(m.dplus(i)));
            } else {
                assertEquals(name + " dplus[" + i + "]", dplus.getDouble(i), m.dplus(i), TIGHT);
            }

            // dminus: first cell is the Null sentinel (NaN in Java)
            if ( i == 0 || dminus.getDouble(i) >= NULL_SENTINEL ) {
                assertTrue(name + " dminus[" + i + "] sentinel", Double.isNaN(m.dminus(i)));
            } else {
                assertEquals(name + " dminus[" + i + "]", dminus.getDouble(i), m.dminus(i), TIGHT);
            }
        }

        final double expVola = exp.getDouble("volaEstimate");
        assertEquals(name + " volaEstimate", expVola, m.volaEstimate(),
                Math.abs(expVola) * VOLA_REL);
    }

    @Test
    public void testNoLeverage() {
        final FdmHestonLocalVolatilityVarianceMesher m =
                new FdmHestonLocalVolatilityVarianceMesher(10, buildProcess(), null, 1.0, 10, 1e-4, 1.0);
        assertMeshMatches("no_leverage_size10_T1", m);
    }

    @Test
    public void testConstantLeverage2() {
        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(15, Month.January, 2026);
        final LocalVolTermStructure lev = new LocalConstantVol(today, 2.0, dc);

        final FdmHestonLocalVolatilityVarianceMesher m =
                new FdmHestonLocalVolatilityVarianceMesher(10, buildProcess(), lev, 1.0, 10, 1e-4, 1.0);
        assertMeshMatches("const_leverage_2_size10_T1", m);
    }

    @Test
    public void testConstantLeverageHalf() {
        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(15, Month.January, 2026);
        final LocalVolTermStructure lev = new LocalConstantVol(today, 0.5, dc);

        final FdmHestonLocalVolatilityVarianceMesher m =
                new FdmHestonLocalVolatilityVarianceMesher(10, buildProcess(), lev, 1.0, 10, 1e-4, 1.0);
        assertMeshMatches("const_leverage_0_5_size10_T1", m);
    }
}
