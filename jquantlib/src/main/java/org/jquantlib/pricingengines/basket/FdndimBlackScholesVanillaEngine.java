/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2024 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.pricingengines.basket;

import org.jquantlib.QL;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.instruments.BasketOption;
import org.jquantlib.instruments.BasketPayoff;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.instruments.MultiAssetOption;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SymmetricSchurDecomposition;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Predefined1dMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FdmWienerOp;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.FdmNdimSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.methods.finitedifferences.utilities.VectorBsmProcessExtractor;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.Date;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * N-dimensional finite-differences Black-Scholes vanilla basket engine.
 *
 * <p>Java port of v1.42.1
 * {@code ql/pricingengines/basket/fdndimblackscholesvanillaengine.{hpp,cpp}}.
 *
 * <p>The engine performs a PCA on the asset covariance matrix and rolls
 * back the option value on the orthogonalised log-price grid using {@link FdmWienerOp} (an N-dim Wiener operator with
 * the eigenvalue variances as diffusion coefficients) and the {@link FdmNdimSolver} (an N-dim FD time-stepper with
 * multi-cubic-spline output).
 *
 * <p>Reference: Klaus Spanderen, "PCA-based Finite-Difference Basket Option
 * Pricing", QuantLib 1.34 (2024) — see C++ commit history of the same file.
 *
 * <h3>Per-asset grid sizing</h3>
 *
 * Two constructors: pass either an explicit {@code xGrids[i]} per asset, or a single {@code xGrid} that gets
 * auto-scaled by the eigenvalue ratio {@code (lambda_i / lambda_0)^0.1} (so the most-volatile principal component gets
 * the full grid; less volatile ones shrink toward a minimum of 4 points). This matches the C++ scaling exactly.
 *
 * <h3>Maximum supported dimension</h3>
 *
 * The C++ uses {@code BOOST_PP_LOCAL_ITERATE} to expand a {@code switch} with cases 1..{@code PDE_MAX_SUPPORTED_DIM}
 * (default 4); Java has no preprocessor but {@link FdmNdimSolver} is parametrised on the runtime dimension so this
 * engine supports the same range of dimensions (validated via the C++ test {@code testNdimPDEinDifferentDims}).
 *
 * @author Phase 5e.5b-CFC-d-281 port
 */
public class FdndimBlackScholesVanillaEngine extends BasketOption.Engine {

    /** Same limit as the C++ {@code PDE_MAX_SUPPORTED_DIM} default. */
    public static final int MAX_SUPPORTED_DIM = 4;

    private final List< GeneralizedBlackScholesProcess > processes;
    private final Matrix rho;
    private final int[] xGrids;
    private final int tGrid;
    private final int dampingSteps;
    private final FdmSchemeDesc schemeDesc;

    /** Explicit per-asset grid sizes. */
    public FdndimBlackScholesVanillaEngine(final List< GeneralizedBlackScholesProcess > processes, final Matrix rho,
            final int[] xGrids, final int tGrid, final int dampingSteps, final FdmSchemeDesc schemeDesc) {
        super();
        QL.require(processes != null && !processes.isEmpty(), "no Black-Scholes process is given.");
        QL.require(rho.rows() == rho.cols() && rho.rows() == processes.size(),
                "correlation matrix has the wrong size.");
        QL.require(xGrids.length == 1 || xGrids.length == processes.size(), "wrong number of xGrids is given.");

        this.processes = processes;
        this.rho = rho;
        this.xGrids = xGrids.clone();
        this.tGrid = tGrid;
        this.dampingSteps = dampingSteps;
        this.schemeDesc = schemeDesc;

        for ( final GeneralizedBlackScholesProcess p : processes ) {
            p.addObserver(this);
        }
    }

    /** Convenience: explicit per-asset grids; defaults tGrid=50, no damping, Douglas scheme. */
    public FdndimBlackScholesVanillaEngine(final List< GeneralizedBlackScholesProcess > processes, final Matrix rho,
            final int[] xGrids, final int tGrid) {
        this(processes, rho, xGrids, tGrid, 0, FdmSchemeDesc.Douglas());
    }

    /** Auto-scaled grid sizing: largest eigenvalue gets {@code xGrid}, others scale by {@code (l_i/l_0)^0.1}. */
    public FdndimBlackScholesVanillaEngine(final List< GeneralizedBlackScholesProcess > processes, final Matrix rho,
            final int xGrid, final int tGrid, final int dampingSteps, final FdmSchemeDesc schemeDesc) {
        this(processes, rho, new int[] { xGrid }, tGrid, dampingSteps, schemeDesc);
    }

    /** Auto-scaled grid sizing with default damping/scheme. */
    public FdndimBlackScholesVanillaEngine(final List< GeneralizedBlackScholesProcess > processes, final Matrix rho,
            final int xGrid, final int tGrid) {
        this(processes, rho, new int[] { xGrid }, tGrid, 0, FdmSchemeDesc.Douglas());
    }

    @Override
    public void calculate() {
        QL.require(processes.size() <= MAX_SUPPORTED_DIM,
                "This engine does not support " + processes.size() + " underlyings. " + "Max number of underlyings is "
                        + MAX_SUPPORTED_DIM + ".");

        final Date maturityDate = arguments_.exercise.lastDate();
        final double maturity = processes.get(0).time(maturityDate);
        final double sqrtT = Math.sqrt(maturity);

        final VectorBsmProcessExtractor pExtractor = new VectorBsmProcessExtractor(processes);
        final Array s = pExtractor.getSpot();
        final Array stdDev = pExtractor.getBlackVariance(maturityDate).sqrt();
        final Array vols = stdDev.mul(1.0 / sqrtT);

        // Compute the covariance matrix C[i,j] = vol_i * vol_j * 0.5 * (rho[i,j] + rho[j,i])
        final int n = processes.size();
        final Matrix cov = new Matrix(n, n);
        for ( int i = 0; i < n; ++i ) {
            for ( int j = 0; j <= i; ++j ) {
                if ( i == j ) {
                    QL.require(Math.abs(rho.get(i, i) - 1.0) <= 1e-12,
                            "invalid correlation matrix: diagonal must be 1");
                    cov.set(i, i, vols.get(i) * vols.get(i));
                } else {
                    QL.require(Math.abs(rho.get(i, j) - rho.get(j, i)) <= 1e-12, "correlation matrix not symmetric");
                    final double c = vols.get(i) * vols.get(j) * 0.5 * (rho.get(i, j) + rho.get(j, i));
                    cov.set(i, j, c);
                    cov.set(j, i, c);
                }
            }
        }

        final SymmetricSchurDecomposition schur = new SymmetricSchurDecomposition(cov);
        final Matrix Q = schur.eigenvectors();
        final Array l = schur.eigenvalues();

        // Build per-asset 1d meshers (each axis is a principal-component
        // variable, not a per-asset variable any more).
        final double eps = 1e-4;
        final List< Fdm1dMesher > meshers = new ArrayList<>(n);
        final InverseCumulativeNormal invN = new InverseCumulativeNormal();
        for ( int i = 0; i < n; ++i ) {
            final int xGrid = (xGrids.length > 1)
                    ? xGrids[i]
                    : Math.max(4, (int) (xGrids[0] * Math.pow(l.get(i) / l.get(0), 0.1)));
            QL.require(xGrid >= 4, "minimum grid size is four");

            final double xStepSize = (1.0 - 2 * eps) / (xGrid - 1);
            final double[] x = new double[xGrid];
            for ( int j = 0; j < xGrid; ++j ) {
                x[j] = 1.3 * Math.sqrt(l.get(i)) * sqrtT * invN.op(eps + j * xStepSize);
            }
            meshers.add(new Predefined1dMesher(x));
        }
        final FdmMesher mesher = new FdmMesherComposite(meshers);

        final BasketPayoff payoff = (BasketPayoff) arguments_.payoff;
        QL.require(payoff != null, "basket payoff expected");

        final org.jquantlib.termstructures.YieldTermStructure rTS = processes.get(0).riskFreeRate().currentLink();

        // Inner-value calculator: S = Exp(Q*x - 0.5*v*t + logS0)*qf(t)/rf(t)
        // — rf, qf are the discount factors at the *current* time step t,
        // not at maturity. This matches C++ fdndimblackscholesvanillaengine.cpp
        // lines 58-71 which caches discount(t) per time-step.
        final Array logS0 = s.log();
        final Array v = vols.mul(vols);                       // sigma_i^2
        final List< org.jquantlib.termstructures.YieldTermStructure > qTS = new ArrayList<>(n);
        for ( int i = 0; i < n; ++i ) {
            qTS.add(processes.get(i).dividendYield().currentLink());
        }
        final FdmInnerValueCalculator calculator = new FdmPCABasketInnerValue(payoff, mesher, n, logS0, v, qTS, rTS, Q);

        final FdmStepConditionComposite conditions = FdmStepConditionComposite.vanillaComposite(new DividendSchedule(),
                arguments_.exercise, mesher, calculator, rTS.referenceDate(), rTS.dayCounter());

        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();
        final FdmSolverDesc solverDesc = new FdmSolverDesc(mesher, boundaries, conditions, calculator, maturity, tGrid,
                dampingSteps);

        final boolean isEuropean = arguments_.exercise instanceof EuropeanExercise;
        final FdmWienerOp op = new FdmWienerOp(mesher, isEuropean ? null : rTS, l);

        final FdmNdimSolver solver = new FdmNdimSolver(solverDesc, schemeDesc, op);
        final double[] origin = new double[n];
        Arrays.fill(origin, 0.0);
        double value = solver.interpolateAt(origin);

        if ( isEuropean ) {
            value *= pExtractor.getInterestRateDf(maturityDate);
        }

        final MultiAssetOption.ResultsImpl r = results_;
        r.value = value;
    }

    /**
     * Inner-value calculator for the PCA-rotated grid: {@code S = exp(Q*x - 0.5*sigma^2*t + log(S0)) * qf(t) / rf(t)}
     * for each principal-component coordinate vector {@code x}, then evaluate the basket payoff at the asset prices
     * {@code S}. Discount factors are re-fetched at each new time step and cached.
     */
    private static final class FdmPCABasketInnerValue implements FdmInnerValueCalculator {
        private final BasketPayoff payoff;
        private final FdmMesher mesher;
        private final int n;
        private final Array logS0;
        private final Array v;       // sigma_i^2
        private final List< org.jquantlib.termstructures.YieldTermStructure > qTS;
        private final org.jquantlib.termstructures.YieldTermStructure rTS;
        private final Matrix Q;
        private final double[] qf;
        private double cachedT;
        private double rf;

        FdmPCABasketInnerValue(final BasketPayoff payoff, final FdmMesher mesher, final int n, final Array logS0,
                final Array v, final List< org.jquantlib.termstructures.YieldTermStructure > qTS,
                final org.jquantlib.termstructures.YieldTermStructure rTS, final Matrix Q) {
            this.payoff = payoff;
            this.mesher = mesher;
            this.n = n;
            this.logS0 = logS0;
            this.v = v;
            this.qTS = qTS;
            this.rTS = rTS;
            this.Q = Q;
            this.cachedT = Double.NaN;
            this.qf = new double[n];
        }

        @Override
        public double innerValue(final FdmLinearOpIterator iter, final double t) {
            if ( !Closeness.isCloseEnough(t, cachedT) ) {
                rf = rTS.discount(t);
                for ( int i = 0; i < n; ++i ) {
                    qf[i] = qTS.get(i).discount(t);
                }
                cachedT = t;
            }
            final Array x = new Array(n);
            for ( int i = 0; i < n; ++i ) {
                x.set(i, mesher.location(iter, i));
            }
            // S = exp(Q*x - 0.5*v*t + log(S0)) * qf / rf
            final Array Qx = Q.mul(x);
            final double[] S = new double[n];
            for ( int i = 0; i < n; ++i ) {
                final double e = Qx.get(i) - 0.5 * v.get(i) * t + logS0.get(i);
                S[i] = Math.exp(e) * qf[i] / rf;
            }
            return payoff.get(S);
        }

        @Override
        public double avgInnerValue(final FdmLinearOpIterator iter, final double t) {
            return innerValue(iter, t);
        }
    }

}
