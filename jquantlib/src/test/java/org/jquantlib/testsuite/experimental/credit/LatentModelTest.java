/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Cross-validation of the LatentModel + multidim integration backend ports
 against C++ QuantLib v1.42.1 reference values produced by the
 latent_model_probe. See phase 4m.6.5 design notes.
 */
package org.jquantlib.testsuite.experimental.credit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.experimental.credit.GaussianQuadLMIntegration;
import org.jquantlib.experimental.credit.LMIntegration;
import org.jquantlib.experimental.credit.LatentModel;
import org.jquantlib.experimental.credit.MultidimIntegralLMIntegration;
import org.jquantlib.experimental.math.GaussianCopulaPolicy;
import org.jquantlib.math.integrals.Integrator;
import org.jquantlib.math.integrals.MultidimIntegral;
import org.jquantlib.math.integrals.TrapezoidIntegral;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Phase 4m.6.5 cross-validation. One @Test method per group, collect-all-failures
 * pattern.
 *
 * <p>Tolerance tiers (per phase1-design §4.2):
 * <ul>
 *   <li><b>idiosyncFctrs / latentVariableCorrel / latentVarValue</b>: TIGHT —
 *       these are simple {@code sqrt}/{@code inner_product} computations.</li>
 *   <li><b>multidim_quadrature_*</b>: LOOSE — numerical integration tier;
 *       per-dimension floating-point cancellation accumulates.</li>
 *   <li><b>multidim_trapezoid</b>: LOOSE — same.</li>
 * </ul>
 */
public class LatentModelTest {

    private static final String TEST_GROUP = "experimental/latent-model/latent_model";

    @Test
    public void crossValidateMultidimQuadrature2dCases() {
        final ReferenceReader reader = ReferenceReader.load(TEST_GROUP);
        final List<String> failures = new ArrayList<>();

        // Case 1: f=1, expected ≈ 107.53
        checkQuadrature2d(reader, "multidim_quadrature_2d_unit",
                (x) -> 1.0, failures);
        // Case 2: f=x*y, expected ≈ 0
        checkQuadrature2d(reader, "multidim_quadrature_2d_xy",
                (x) -> x[0] * x[1], failures);
        // Case 3: f=x^2*y^2
        checkQuadrature2d(reader, "multidim_quadrature_2d_x2y2",
                (x) -> x[0] * x[0] * x[1] * x[1], failures);

        if (!failures.isEmpty()) {
            org.junit.Assert.fail("Cross-validation failures:\n" + String.join("\n", failures));
        }
    }

    @Test
    public void crossValidateMultidimTrapezoid() {
        final ReferenceReader reader = ReferenceReader.load(TEST_GROUP);
        final Case c = reader.getCase("multidim_trapezoid_2d_unit");
        final JSONObject exp = (JSONObject) c.expectedRaw();
        final double cppValue = exp.getDouble("value");

        final List<Integrator> integrators = new ArrayList<>();
        integrators.add(new TrapezoidIntegral<>(TrapezoidIntegral.Default.class, 1.0e-6, 200));
        integrators.add(new TrapezoidIntegral<>(TrapezoidIntegral.Default.class, 1.0e-6, 200));
        final MultidimIntegral mdi = new MultidimIntegral(integrators);
        final double[] a = {-2.0, -2.0};
        final double[] b = {2.0, 2.0};
        final double javaValue = mdi.op((x) -> 1.0, a, b);

        assertTrue("multidim_trapezoid_2d_unit: java=" + javaValue + " cpp=" + cppValue,
                Tolerance.loose(javaValue, cppValue));
    }

    @Test
    public void crossValidateLMIntegrationAdapters() {
        final ReferenceReader reader = ReferenceReader.load(TEST_GROUP);

        // Gaussian-quad adapter for f=1 case
        {
            final Case c = reader.getCase("multidim_quadrature_2d_unit");
            final JSONObject exp = (JSONObject) c.expectedRaw();
            final double cppValue = exp.getDouble("value");
            final LMIntegration lmi = new GaussianQuadLMIntegration(2, 16);
            final double javaValue = lmi.integrate((x) -> 1.0);
            assertTrue("LMI(GaussQuad,2,16) f=1: java=" + javaValue + " cpp=" + cppValue,
                    Tolerance.loose(javaValue, cppValue));
        }

        // Trapezoid adapter for the f=1 over [-2,2]^2 case
        {
            final Case c = reader.getCase("multidim_trapezoid_2d_unit");
            final JSONObject exp = (JSONObject) c.expectedRaw();
            final double cppValue = exp.getDouble("value");
            final List<Integrator> integrators = new ArrayList<>();
            integrators.add(new TrapezoidIntegral<>(TrapezoidIntegral.Default.class, 1.0e-6, 200));
            integrators.add(new TrapezoidIntegral<>(TrapezoidIntegral.Default.class, 1.0e-6, 200));
            final LMIntegration lmi = new MultidimIntegralLMIntegration(integrators, -2.0, 2.0);
            final double javaValue = lmi.integrate((x) -> 1.0);
            assertTrue("LMI(MultidimIntegral,2) f=1: java=" + javaValue + " cpp=" + cppValue,
                    Tolerance.loose(javaValue, cppValue));
        }
    }

    /**
     * Cross-consistency: integrateV must agree component-wise with integrate
     * called separately on each component. Phase 4m.7b WI-2 (integrateV port).
     *
     * <p>Validates the GaussianQuadMultidimIntegrator vector overload against
     * its own scalar overload across three integrand components defined over
     * R^2 with Gauss-Hermite weight {@code e^{-x²-y²}}: the constant function,
     * a polynomial {@code x²·y²}, and {@code x·y}. Tier: TIGHT — the vector
     * path is a deterministic re-summation of the same node/weight tables,
     * so component-wise differences should be at the floating-point noise
     * floor.
     */
    @Test
    public void integrateVMatchesScalarComponentwise() {
        final org.jquantlib.math.integrals.GaussianQuadMultidimIntegrator integ =
                new org.jquantlib.math.integrals.GaussianQuadMultidimIntegrator(2, 16, 0.0);
        final double s0 = integ.integrate((x) -> 1.0);
        final double s1 = integ.integrate((x) -> x[0] * x[0] * x[1] * x[1]);
        final double s2 = integ.integrate((x) -> x[0] * x[1]);
        final double[] v = integ.integrateV((x) -> new double[] {
                1.0,
                x[0] * x[0] * x[1] * x[1],
                x[0] * x[1]
        });
        assertTrue("integrateV[0] (const 1): vec=" + v[0] + " scalar=" + s0,
                Tolerance.tight(v[0], s0));
        assertTrue("integrateV[1] (x²y²): vec=" + v[1] + " scalar=" + s1,
                Tolerance.tight(v[1], s1));
        assertTrue("integrateV[2] (xy): vec=" + v[2] + " scalar=" + s2,
                Tolerance.tight(v[2], s2));
    }

    /** Same cross-consistency test for the LMIntegration adapter. */
    @Test
    public void integrateVOnLMIntegrationMatchesScalarComponentwise() {
        final LMIntegration lmi = new GaussianQuadLMIntegration(2, 16);
        final double s0 = lmi.integrate((x) -> 1.0);
        final double s1 = lmi.integrate((x) -> x[0] * x[0] * x[1] * x[1]);
        final double[] v = lmi.integrateV((x) -> new double[] {
                1.0,
                x[0] * x[0] * x[1] * x[1]
        });
        assertTrue("LMI integrateV[0] (const 1): vec=" + v[0] + " scalar=" + s0,
                Tolerance.tight(v[0], s0));
        assertTrue("LMI integrateV[1] (x²y²): vec=" + v[1] + " scalar=" + s1,
                Tolerance.tight(v[1], s1));
    }

    @Test
    public void crossValidateLatentModel2var2fact() {
        final ReferenceReader reader = ReferenceReader.load(TEST_GROUP);
        final Case c = reader.getCase("latent_model_2var_2fact");
        final JSONObject exp = (JSONObject) c.expectedRaw();
        final List<String> failures = new ArrayList<>();

        final List<List<Double>> weights = Arrays.asList(
                Arrays.asList(0.3, 0.4),
                Arrays.asList(0.5, 0.2));
        final GaussianCopulaPolicy copula = new GaussianCopulaPolicy(weights);
        final var lm = new LatentModel<GaussianCopulaPolicy>(weights, copula);

        if (!Tolerance.exact(lm.size(), exp.getInt("size"))) {
            failures.add("size: java=" + lm.size() + " cpp=" + exp.getInt("size"));
        }
        if (!Tolerance.exact(lm.numFactors(), exp.getInt("numFactors"))) {
            failures.add("numFactors: java=" + lm.numFactors() + " cpp=" + exp.getInt("numFactors"));
        }
        if (!Tolerance.exact(lm.numTotalFactors(), exp.getInt("numTotalFactors"))) {
            failures.add("numTotalFactors: java=" + lm.numTotalFactors()
                    + " cpp=" + exp.getInt("numTotalFactors"));
        }

        // idiosyncFctrs[]
        final JSONArray idi = exp.getJSONArray("idiosyncFctrs");
        for (int i = 0; i < idi.length(); ++i) {
            final double javaV = lm.idiosyncFctrs()[i];
            final double cppV = idi.getDouble(i);
            if (!Tolerance.tight(javaV, cppV)) {
                failures.add("idiosyncFctrs[" + i + "]: java=" + javaV + " cpp=" + cppV);
            }
        }

        // correls[]
        final JSONArray correls = exp.getJSONArray("correls");
        for (int k = 0; k < correls.length(); ++k) {
            final JSONObject cell = correls.getJSONObject(k);
            final int i = cell.getInt("i");
            final int j = cell.getInt("j");
            final double javaV = lm.latentVariableCorrel(i, j);
            final double cppV = cell.getDouble("value");
            if (!Tolerance.tight(javaV, cppV)) {
                failures.add("correl[" + i + "," + j + "]: java=" + javaV + " cpp=" + cppV);
            }
        }

        if (!failures.isEmpty()) {
            org.junit.Assert.fail("LatentModel 2var2fact failures:\n" + String.join("\n", failures));
        }
    }

    @Test
    public void crossValidateLatentVarValue() {
        final ReferenceReader reader = ReferenceReader.load(TEST_GROUP);
        final Case c = reader.getCase("latent_model_latentVarValue");
        final JSONObject exp = (JSONObject) c.expectedRaw();

        final List<List<Double>> weights = Arrays.asList(
                Arrays.asList(0.3, 0.4),
                Arrays.asList(0.5, 0.2));
        final var lm = new LatentModel<GaussianCopulaPolicy>(weights, new GaussianCopulaPolicy(weights));
        final double[] allFactors = {0.5, -0.3, 1.2, -0.8};

        final double y0 = lm.latentVarValue(allFactors, 0);
        final double y1 = lm.latentVarValue(allFactors, 1);
        assertTrue("y0: java=" + y0 + " cpp=" + exp.getDouble("y0"),
                Tolerance.tight(y0, exp.getDouble("y0")));
        assertTrue("y1: java=" + y1 + " cpp=" + exp.getDouble("y1"),
                Tolerance.tight(y1, exp.getDouble("y1")));
    }

    @Test
    public void crossValidateLatentModelCorrelSqrCtor() {
        final ReferenceReader reader = ReferenceReader.load(TEST_GROUP);
        final Case c = reader.getCase("latent_model_correlSqr_ctor");
        final JSONObject exp = (JSONObject) c.expectedRaw();
        final List<String> failures = new ArrayList<>();

        final double correlSqr = 0.6;
        final int nVariables = 3;

        // Build the same factor weights matrix and copula the LatentModel ctor would.
        final List<List<Double>> weights = new ArrayList<>(nVariables);
        for (int i = 0; i < nVariables; ++i) {
            weights.add(new ArrayList<>(Arrays.asList(correlSqr)));
        }
        final GaussianCopulaPolicy copula = new GaussianCopulaPolicy(weights);
        final var lm = new LatentModel<GaussianCopulaPolicy>(correlSqr, nVariables, copula);

        if (!Tolerance.exact(lm.size(), exp.getInt("size"))) {
            failures.add("size: java=" + lm.size() + " cpp=" + exp.getInt("size"));
        }
        if (!Tolerance.exact(lm.numFactors(), exp.getInt("numFactors"))) {
            failures.add("numFactors: java=" + lm.numFactors() + " cpp=" + exp.getInt("numFactors"));
        }

        final JSONArray idi = exp.getJSONArray("idiosyncFctrs");
        for (int i = 0; i < idi.length(); ++i) {
            final double javaV = lm.idiosyncFctrs()[i];
            final double cppV = idi.getDouble(i);
            if (!Tolerance.tight(javaV, cppV)) {
                failures.add("idiosyncFctrs[" + i + "]: java=" + javaV + " cpp=" + cppV);
            }
        }

        if (!failures.isEmpty()) {
            org.junit.Assert.fail("LatentModel correlSqr ctor failures:\n" + String.join("\n", failures));
        }
    }

    // -- helpers --

    @FunctionalInterface
    private interface Func2D {
        double apply(double[] x);
    }

    private static void checkQuadrature2d(final ReferenceReader reader,
                                          final String name,
                                          final Func2D f,
                                          final List<String> failures) {
        final Case c = reader.getCase(name);
        final JSONObject exp = (JSONObject) c.expectedRaw();
        final double cppValue = exp.getDouble("value");
        // Use order 16 to match probe
        final org.jquantlib.math.integrals.GaussianQuadMultidimIntegrator integ =
                new org.jquantlib.math.integrals.GaussianQuadMultidimIntegrator(2, 16, 0.0);
        final double javaValue = integ.integrate((x) -> f.apply(x));
        if (!Tolerance.loose(javaValue, cppValue)) {
            failures.add(name + ": java=" + javaValue + " cpp=" + cppValue);
        }
    }
}
