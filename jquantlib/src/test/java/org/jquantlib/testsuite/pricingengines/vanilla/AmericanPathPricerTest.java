/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Cross-validation of the {@link AmericanPathPricer} port against C++
 QuantLib v1.42.1 reference values produced by the
 {@code american_path_pricer_probe}. See Phase 5h.5-MC.
 */
package org.jquantlib.testsuite.pricingengines.vanilla;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.List;

import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.Ops;
import org.jquantlib.methods.montecarlo.LsmBasisSystem.PolynomialType;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.pricingengines.vanilla.AmericanPathPricer;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.TimeGrid;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Verifies that {@link AmericanPathPricer} produces per-step state values,
 * per-step path payoffs, and basis-system values matching C++
 * {@code QuantLib::AmericanPathPricer} for several option type / strike /
 * polynomial type / order combinations.
 *
 * <p>Tier: TIGHT (1e-12 rel / 1e-14 abs). All arithmetic in the path-pricer
 * itself is plain double multiplication / payoff evaluation; the only
 * source of drift is the underlying GaussianOrthogonalPolynomial weighted
 * value, which is &lt;1e-15 in tested ranges.
 */
public class AmericanPathPricerTest {

    private static final String GROUP = "methods/montecarlo/american_path_pricer";

    @Test
    public void allCasesMatchCpp() {
        final ReferenceReader reader = ReferenceReader.load(GROUP);
        for (final String name : reader.caseNames()) {
            final Case c = reader.getCase(name);
            final JSONObject in = c.inputs();
            final Option.Type optType = Option.Type.valueOf(in.getString("optionType"));
            final double strike = in.getDouble("strike");
            final PolynomialType polyType = PolynomialType.valueOf(in.getString("polyType"));
            final int order = in.getInt("order");
            final JSONArray pathArr = in.getJSONArray("path");
            final double[] pathValues = new double[pathArr.length()];
            for (int i = 0; i < pathArr.length(); ++i) pathValues[i] = pathArr.getDouble(i);

            final JSONObject exp = (JSONObject) c.expectedRaw();

            final PlainVanillaPayoff payoff = new PlainVanillaPayoff(optType, strike);
            final AmericanPathPricer p = new AmericanPathPricer(payoff, order, polyType);

            // scaling
            assertEquals(c.name() + ": scaling",
                         exp.getDouble("scalingValue"), p.scalingValue(), 1e-15);

            // build Path with TimeGrid 0..n-1
            final int n = pathValues.length;
            final TimeGrid grid = new TimeGrid((double) (n - 1), n - 1);
            final Path path = new Path(grid, pathValues.clone());

            // states
            final JSONArray expStates = exp.getJSONArray("states");
            for (int t = 0; t < n; ++t) {
                final double got = p.state(path, t);
                if (!Tolerance.tight(got, expStates.getDouble(t))) {
                    fail(c.name() + ": state[" + t + "]: got=" + got + " want=" + expStates.getDouble(t));
                }
            }

            // path payoffs (operator(path, t))
            final JSONArray expOps = exp.getJSONArray("pathPayoffs");
            for (int t = 0; t < n; ++t) {
                final double got = p.operator(path, t);
                if (!Tolerance.tight(got, expOps.getDouble(t))) {
                    fail(c.name() + ": op[" + t + "]: got=" + got + " want=" + expOps.getDouble(t));
                }
            }

            // basis system size
            final List<Ops.DoubleOp> basis = p.basisSystemDouble();
            assertEquals(c.name() + ": basis size", exp.getInt("basisSize"), basis.size());

            // basis values
            final JSONArray basisRows = exp.getJSONArray("basisRows");
            for (int r = 0; r < basisRows.length(); ++r) {
                final JSONObject row = basisRows.getJSONObject(r);
                final double state = row.getDouble("state");
                final JSONArray vals = row.getJSONArray("values");
                for (int i = 0; i < basis.size(); ++i) {
                    final double got = basis.get(i).op(state);
                    if (!Tolerance.tight(got, vals.getDouble(i))) {
                        fail(c.name() + ": basis[" + i + "] @state=" + state
                                + ": got=" + got + " want=" + vals.getDouble(i));
                    }
                }
            }
        }
    }

    @Test
    public void rejectsLegendrePolynomial() {
        try {
            new AmericanPathPricer(new PlainVanillaPayoff(Option.Type.Put, 100.0),
                                   2, PolynomialType.Legendre);
            fail("expected exception for Legendre");
        } catch (final RuntimeException expected) {
            // ok
        }
    }

    @Test
    public void rejectsChebyshevPolynomial() {
        try {
            new AmericanPathPricer(new PlainVanillaPayoff(Option.Type.Put, 100.0),
                                   2, PolynomialType.Chebyshev);
            fail("expected exception for Chebyshev");
        } catch (final RuntimeException expected) {
            // ok
        }
    }
}
