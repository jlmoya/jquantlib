/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 */
package org.jquantlib.testsuite.methods.finitedifferences.operators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Uniform1dMesher;
import org.jquantlib.methods.finitedifferences.operators.Fdm2dBlackScholesOp;
import org.jquantlib.methods.finitedifferences.operators.FdmBatesOp;
import org.jquantlib.methods.finitedifferences.operators.FdmCIROp;
import org.jquantlib.methods.finitedifferences.operators.FdmHestonFwdOp;
import org.jquantlib.methods.finitedifferences.operators.FdmHestonHullWhiteOp;
import org.jquantlib.methods.finitedifferences.operators.FdmHestonOp;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.processes.BatesProcess;
import org.jquantlib.processes.CoxIngersollRossProcess;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.processes.HullWhiteProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Cross-validates {@code toMatrix()} behavior on the six FD composite
 * operators that previously threw
 * {@code UnsupportedOperationException("... not implemented")}. The
 * contract being verified mirrors C++ v1.42.1
 * {@code FdmLinearOpComposite::toMatrix()} which is
 * {@code std::accumulate(dcmp.begin()+1, dcmp.end(), SparseMatrix(dcmp.front()))}:
 * the assembled matrix must equal the element-wise sum of
 * {@code toMatrixDecomp()}, and applying it to an arbitrary vector must
 * reproduce {@code op.apply(.)} to within numerical-roundoff tolerance.
 *
 * <p>The one exception is {@link FdmBatesOp}: C++ explicitly
 * {@code QL_FAIL}s its {@code toMatrixDecomp()} because the Gauss-Hermite
 * jump integro term has no sparse-matrix representation, so the Java port
 * keeps the {@code UnsupportedOperationException} as design-intent. We
 * test that the throw fires with the expected message.
 *
 * <p>Tier: TIGHT — assembled-matrix vs. {@code apply(.)} difference must be
 * within 1e-12 absolute / 1e-12 relative (per the migration-design tight
 * tier specification for analytic linear operators).
 *
 * @author Phase 3-A toMatrix() impl
 */
public class FdmCompositeOpToMatrixTest {

    private static final double TOL_ABS = 1e-12;
    private static final double TOL_REL = 1e-12;

    private static final Date REF_DATE = new Date(15, Month.May, 2026);

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static FdmMesher mesher2D(final double xMin, final double xMax, final int nx,
                                      final double vMin, final double vMax, final int nv) {
        final List<Fdm1dMesher> ms = new ArrayList<Fdm1dMesher>(2);
        ms.add(new Uniform1dMesher(xMin, xMax, nx));
        ms.add(new Uniform1dMesher(vMin, vMax, nv));
        return new FdmMesherComposite(ms);
    }

    private static FdmMesher mesher3D(final double xMin, final double xMax, final int nx,
                                      final double vMin, final double vMax, final int nv,
                                      final double rMin, final double rMax, final int nr) {
        final List<Fdm1dMesher> ms = new ArrayList<Fdm1dMesher>(3);
        ms.add(new Uniform1dMesher(xMin, xMax, nx));
        ms.add(new Uniform1dMesher(vMin, vMax, nv));
        ms.add(new Uniform1dMesher(rMin, rMax, nr));
        return new FdmMesherComposite(ms);
    }

    private static Handle<YieldTermStructure> flatYield(final double r) {
        final DayCounter dc = new Actual365Fixed();
        return new Handle<YieldTermStructure>(new FlatForward(REF_DATE, r, dc));
    }

    private static Handle<BlackVolTermStructure> flatBlackVol(final double sigma) {
        final DayCounter dc = new Actual365Fixed();
        return new Handle<BlackVolTermStructure>(
                new BlackConstantVol(REF_DATE, new NullCalendar(), sigma, dc));
    }

    private static HestonProcess hestonProcess() {
        return new HestonProcess(
                flatYield(0.03),               // r
                flatYield(0.01),               // q
                new Handle<Quote>(new SimpleQuote(100.0)), // S0
                0.04,                          // v0
                1.5,                           // kappa
                0.04,                          // theta
                0.3,                           // sigma
                -0.7);                         // rho
    }

    private static GeneralizedBlackScholesProcess gbsProcess(final double spot,
                                                             final double r,
                                                             final double q,
                                                             final double sigma) {
        return new GeneralizedBlackScholesProcess(
                new Handle<Quote>(new SimpleQuote(spot)),
                flatYield(q), flatYield(r), flatBlackVol(sigma));
    }

    /**
     * Verify {@code toMatrix() == sum(toMatrixDecomp())} entry-by-entry and
     * that {@code toMatrix() * v == apply(v)} for a non-trivial probe.
     */
    private static void verifyComposite(final String name,
                                        final FdmLinearOpComposite op,
                                        final int n) {
        final Matrix asm = op.toMatrix();
        assertNotNull(name + ".toMatrix() must not return null", asm);
        assertEquals(name + ".rows", n, asm.rows());
        assertEquals(name + ".cols", n, asm.cols());

        // toMatrix() == sum of toMatrixDecomp()
        final List<Matrix> decomp = op.toMatrixDecomp();
        assertTrue(name + ".decomp non-empty", decomp.size() >= 1);
        final Matrix expected = new Matrix(decomp.get(0));
        for (int k = 1; k < decomp.size(); ++k) {
            expected.addAssign(decomp.get(k));
        }
        for (int i = 0; i < n; ++i) {
            for (int j = 0; j < n; ++j) {
                final double e = expected.get(i, j);
                final double a = asm.get(i, j);
                final double diff = Math.abs(e - a);
                if (diff <= TOL_ABS) continue;
                final double rel = diff / Math.max(Math.abs(e), 1e-300);
                assertTrue(name + "[" + i + "," + j + "] expected=" + e + " actual=" + a
                                + " (rel=" + rel + ")",
                        rel <= TOL_REL);
            }
        }

        // toMatrix() * v == apply(v) for a non-trivial probe.
        final Array v = new Array(n);
        for (int i = 0; i < n; ++i) {
            v.set(i, Math.sin(0.37 * i + 0.11) + 0.5);
        }
        final Array applied = op.apply(v);
        final Array matVec = new Array(n);
        for (int i = 0; i < n; ++i) {
            double acc = 0.0;
            for (int j = 0; j < n; ++j) {
                acc += asm.get(i, j) * v.get(j);
            }
            matVec.set(i, acc);
        }
        for (int i = 0; i < n; ++i) {
            final double e = applied.get(i);
            final double a = matVec.get(i);
            final double diff = Math.abs(e - a);
            if (diff <= TOL_ABS) continue;
            final double rel = diff / Math.max(Math.abs(e), 1e-300);
            assertTrue(name + "*v[" + i + "] apply=" + e + " mat*v=" + a
                            + " (rel=" + rel + ")",
                    rel <= TOL_REL);
        }
    }

    // ------------------------------------------------------------------
    // 1. FdmHestonOp
    // ------------------------------------------------------------------

    @Test
    public void testFdmHestonOpToMatrix() {
        final int nx = 5;
        final int nv = 4;
        final FdmMesher mesher = mesher2D(Math.log(50.0), Math.log(200.0), nx,
                                          0.001, 0.2, nv);
        final FdmHestonOp op = new FdmHestonOp(mesher, hestonProcess());
        op.setTime(0.0, 1.0);

        verifyComposite("FdmHestonOp", op, nx * nv);
    }

    // ------------------------------------------------------------------
    // 2. Fdm2dBlackScholesOp
    // ------------------------------------------------------------------

    @Test
    public void testFdm2dBlackScholesOpToMatrix() {
        final int nx = 5;
        final int ny = 4;
        final FdmMesher mesher = mesher2D(Math.log(50.0), Math.log(200.0), nx,
                                          Math.log(50.0), Math.log(200.0), ny);

        final GeneralizedBlackScholesProcess p1 = gbsProcess(100.0, 0.03, 0.01, 0.20);
        final GeneralizedBlackScholesProcess p2 = gbsProcess(100.0, 0.03, 0.01, 0.25);

        final Fdm2dBlackScholesOp op = new Fdm2dBlackScholesOp(
                mesher, p1, p2, /*correlation*/ -0.4, /*maturity*/ 1.0);
        op.setTime(0.0, 1.0);

        verifyComposite("Fdm2dBlackScholesOp", op, nx * ny);
    }

    // ------------------------------------------------------------------
    // 3. FdmCIROp
    // ------------------------------------------------------------------

    @Test
    public void testFdmCIROpToMatrix() {
        final int nx = 5;
        final int nr = 4;
        final FdmMesher mesher = mesher2D(Math.log(50.0), Math.log(200.0), nx,
                                          0.005, 0.10, nr);

        final CoxIngersollRossProcess cir = new CoxIngersollRossProcess(
                /*speed*/ 0.5, /*vol*/ 0.10, /*x0*/ 0.03, /*level*/ 0.04);
        final GeneralizedBlackScholesProcess bs = gbsProcess(100.0, 0.03, 0.01, 0.20);

        final FdmCIROp op = new FdmCIROp(mesher, cir, bs,
                /*rho*/ -0.3, /*strike*/ 100.0);
        op.setTime(0.0, 1.0);

        verifyComposite("FdmCIROp", op, nx * nr);
    }

    // ------------------------------------------------------------------
    // 4. FdmHestonHullWhiteOp
    // ------------------------------------------------------------------

    @Test
    public void testFdmHestonHullWhiteOpToMatrix() {
        final int nx = 4;
        final int nv = 3;
        final int nr = 3;
        final FdmMesher mesher = mesher3D(Math.log(50.0), Math.log(200.0), nx,
                                          0.001, 0.2, nv,
                                          -0.05, 0.10, nr);

        final HullWhiteProcess hw = new HullWhiteProcess(
                flatYield(0.03), /*a*/ 0.1, /*sigma*/ 0.01);
        final FdmHestonHullWhiteOp op = new FdmHestonHullWhiteOp(
                mesher, hestonProcess(), hw,
                /*equityShortRateCorrelation*/ -0.2);
        op.setTime(0.0, 1.0);

        verifyComposite("FdmHestonHullWhiteOp", op, nx * nv * nr);
    }

    // ------------------------------------------------------------------
    // 5. FdmHestonFwdOp
    // ------------------------------------------------------------------

    @Test
    public void testFdmHestonFwdOpToMatrix() {
        final int nx = 5;
        final int nv = 4;
        final FdmMesher mesher = mesher2D(Math.log(50.0), Math.log(200.0), nx,
                                          0.005, 0.2, nv);
        final FdmHestonFwdOp op = new FdmHestonFwdOp(mesher, hestonProcess());
        op.setTime(0.0, 1.0);

        verifyComposite("FdmHestonFwdOp", op, nx * nv);
    }

    // ------------------------------------------------------------------
    // 6. FdmBatesOp — DESIGN-INTENT throw (matches C++ QL_FAIL)
    // ------------------------------------------------------------------

    @Test
    public void testFdmBatesOpToMatrixThrows() {
        final int nx = 5;
        final int nv = 4;
        final FdmMesher mesher = mesher2D(Math.log(50.0), Math.log(200.0), nx,
                                          0.005, 0.2, nv);

        final BatesProcess bp = new BatesProcess(
                flatYield(0.03), flatYield(0.01),
                new Handle<Quote>(new SimpleQuote(100.0)),
                /*v0*/ 0.04, /*kappa*/ 1.5, /*theta*/ 0.04,
                /*sigma*/ 0.3, /*rho*/ -0.7,
                /*lambda*/ 0.1, /*nu*/ -0.05, /*delta*/ 0.10);

        final FdmBatesOp op = new FdmBatesOp(mesher, bp,
                new FdmBoundaryConditionSet());
        op.setTime(0.0, 1.0);

        // toMatrix() must throw (DESIGN-INTENT, matches C++ v1.42.1).
        try {
            op.toMatrix();
            fail("FdmBatesOp.toMatrix() should throw "
                    + "UnsupportedOperationException (DESIGN-INTENT)");
        } catch (final UnsupportedOperationException ex) {
            assertTrue("Message should call out design-intent / C++ parity. Got: "
                            + ex.getMessage(),
                    ex.getMessage() != null
                            && ex.getMessage().toLowerCase().contains("not implemented"));
        }

        // Same for toMatrixDecomp() — mirrors C++ QL_FAIL("not implemented").
        try {
            op.toMatrixDecomp();
            fail("FdmBatesOp.toMatrixDecomp() should throw (matches C++ QL_FAIL)");
        } catch (final UnsupportedOperationException ex) {
            assertEquals("not implemented", ex.getMessage());
        }
    }
}
