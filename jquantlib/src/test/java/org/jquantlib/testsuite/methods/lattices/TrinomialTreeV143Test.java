/*
 Copyright (C) 2026 JQuantLib migration contributors.

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

package org.jquantlib.testsuite.methods.lattices;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.experimental.shortrate.GeneralizedOrnsteinUhlenbeckProcess;
import org.jquantlib.methods.lattices.TrinomialTree;
import org.jquantlib.processes.OrnsteinUhlenbeckProcess;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.TimeGrid;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validates the gated {@code dx} floor added to {@code TrinomialTree} in C++ QuantLib v1.43 against the
 * {@code methods/v143_trinomial_g2process} probe reference.
 * <p>
 * v1.43 caches the per-step variances, forms {@code dxFloor = sqrt(3 * max_i v2_i)} and, on steps with
 * {@code dt < 0.01 * dtMax}, widens {@code dx} to {@code max(dxNatural, dxFloor)}. Only when the floor
 * actually widened {@code dx} do the branching probabilities switch from the classical Hull-White / Clewlow
 * form to a general moment-matching form. Every case reports both the assigned {@code dx} and the unfloored
 * {@code dxNatural}, and the test asserts the floor's on/off state positively in each direction: strict
 * equality where the floor must be off, strict inequality where it must be on.
 * <p>
 * <b>What is and is not observable.</b> The two probability forms coincide algebraically whenever
 * {@code dx == v*sqrt(3)}, so the classical/general branch is only distinguishable on steps where the floor
 * genuinely widened {@code dx} ({@code tree_small_gap_ou}, {@code tree_threshold_below_ou}). On the
 * gate-fires-but-dx-unchanged step the two agree to a couple of ULP by construction -- see
 * {@link #testGateFiresButDxUnchanged()}. That case instead pins the {@code dx} value itself at the exact
 * tie, which is where {@code dxFloor}'s value and operation order become observable.
 * <p>
 * <b>Tolerances.</b> Everything here is TIGHT ({@code 1e-12} relative, {@code 1e-14} absolute near zero).
 * The tree construction is pure arithmetic plus {@code sqrt} (correctly rounded in both C++ and Java) and, for
 * the Ornstein-Uhlenbeck cases, {@code exp}; a 1-ULP {@code exp} disagreement between libm and the JVM
 * perturbs the probabilities at the {@code 1e-15} relative level, well inside the tight band. The
 * time-dependent-vol cases use {@code speed == 0}, which selects
 * {@code GeneralizedOrnsteinUhlenbeckProcess}'s algebraic-limit branch
 * ({@code variance == vol*vol*dt}, {@code expectation == x}), so they involve no transcendental function at
 * all and are bit-reproducible.
 *
 * @author JQuantLib migration contributors
 */
public class TrinomialTreeV143Test {

    private static final String GROUP = "methods/v143_trinomial_g2process";

    /** TIGHT tier: 1e-12 relative, 1e-14 absolute near zero (design doc quality gates). */
    private static final double TIGHT_REL = 1.0e-12;
    private static final double TIGHT_ABS = 1.0e-14;

    private static final double OU_SPEED = 0.1;
    private static final double OU_VOL = 0.01;

    private static JSONObject expected(final String caseName) {
        return (JSONObject) ReferenceReader.load(GROUP).getCase(caseName).expectedRaw();
    }

    private static void assertTight(final String what, final double expected, final double actual) {
        assertEquals(what, expected, actual, Math.max(TIGHT_ABS, TIGHT_REL * Math.abs(expected)));
    }

    private static StochasticProcess1D ouProcess() {
        return new OrnsteinUhlenbeckProcess(OU_SPEED, OU_VOL);
    }

    /**
     * Transcendental-free time-dependent-vol process: {@code speed == 0} takes
     * {@code GeneralizedOrnsteinUhlenbeckProcess}'s {@code speed < sqrt(QL_EPSILON)} algebraic-limit branch,
     * so {@code variance(t, ., dt) == vol(t)^2 * dt} and {@code expectation(t, x, dt) == x}. The vol spike on
     * [0.75, 1.0] makes the short step the grid's variance argmax, which is the only configuration in which
     * the gate can fire without the floor widening dx.
     */
    private static StochasticProcess1D spikeProcess(final double x0) {
        return new GeneralizedOrnsteinUhlenbeckProcess(
                t -> 0.0,
                t -> (t >= 0.75 && t <= 1.0) ? 0.5 : 0.01,
                x0, 0.0);
    }

    private static TimeGrid mandatoryGrid(final double... times) {
        final Double[] boxed = new Double[times.length];
        for ( int i = 0; i < times.length; i++ ) {
            boxed[i] = times[i];
        }
        final List< Double > list = Arrays.asList(boxed);
        return new TimeGrid(list);
    }

    /**
     * Rebuilds the tree and asserts every quantity the C++ constructor produces: the derived grid, the
     * per-step {@code dx} and its unfloored counterpart, the {@code jMin}/{@code jMax} extent, the node count
     * at every level, every node's underlying, and every node's three descendants and three probabilities.
     */
    private static void checkTree(final String caseName, final StochasticProcess1D process,
            final TimeGrid grid, final boolean isPositive) {
        final JSONObject e = expected(caseName);
        final TrinomialTree tree = new TrinomialTree(process, grid, isPositive);

        final int nSteps = e.getInt("nSteps");
        assertEquals(caseName + ": nSteps", nSteps, grid.size() - 1);
        assertTight(caseName + ": x0", e.getDouble("x0"), process.x0());

        // Grid: pinned so a mismatch in TimeGrid construction is reported here rather than showing up as an
        // unexplained probability difference deeper down.
        final JSONArray times = e.getJSONArray("times");
        assertEquals(caseName + ": grid size", times.length(), grid.size());
        for ( int i = 0; i < times.length(); i++ ) {
            assertTight(caseName + ": times[" + i + "]", times.getDouble(i), grid.at(i));
        }

        final JSONArray dx = e.getJSONArray("dx");
        final JSONArray dxNatural = e.getJSONArray("dxNatural");
        for ( int i = 0; i < dx.length(); i++ ) {
            assertTight(caseName + ": dx[" + i + "]", dx.getDouble(i), tree.dx(i));
        }

        final JSONArray sizes = e.getJSONArray("sizes");
        int maxNodes = 1;
        for ( int i = 0; i < sizes.length(); i++ ) {
            assertEquals(caseName + ": size(" + i + ")", sizes.getInt(i), tree.size(i));
            maxNodes = Math.max(maxNodes, tree.size(i));
        }
        assertEquals(caseName + ": maxNodes", e.getInt("maxNodes"), maxNodes);

        final JSONArray steps = e.getJSONArray("steps");
        assertEquals(caseName + ": step count", nSteps, steps.length());
        for ( int s = 0; s < steps.length(); s++ ) {
            final JSONObject step = steps.getJSONObject(s);
            final int i = step.getInt("i");
            final String at = caseName + " step " + i;

            assertTight(at + ": t", step.getDouble("t"), grid.at(i));
            assertTight(at + ": dt", step.getDouble("dt"), grid.dt(i));

            // Positively assert the floor's on/off state. C++ sets dx to exactly v*sqrt(3) when the floor is
            // inactive, so strict equality holds there; when the floor is active dx must be strictly larger.
            // Without both directions a kFloorThreshold that drifted either way could pass unnoticed.
            final double dxStep = dx.getDouble(i + 1);
            final double dxNat = dxNatural.getDouble(i + 1);
            if ( dxStep > dxNat ) {
                assertTrue(at + ": floor should have widened dx (expected dx=" + dxStep
                        + " > natural=" + dxNat + ") but got " + tree.dx(i + 1),
                        tree.dx(i + 1) > dxNat);
            } else {
                assertEquals(at + ": dx must equal the unfloored v*sqrt(3)", dxNat, tree.dx(i + 1), 0.0);
            }

            // jMin is recovered from the public surface: underlying(i+1, 0) == x0 + jMin * dx(i+1).
            final long jMin = Math.round((tree.underlying(i + 1, 0) - process.x0()) / tree.dx(i + 1));
            assertEquals(at + ": jMin", step.getLong("jMin"), jMin);
            assertEquals(at + ": jMax", step.getLong("jMax"), jMin + tree.size(i + 1) - 1);

            final JSONArray nodes = step.getJSONArray("nodes");
            assertEquals(at + ": node count", step.getInt("nodeCount"), tree.size(i));
            assertEquals(at + ": node count", nodes.length(), tree.size(i));
            for ( int n = 0; n < nodes.length(); n++ ) {
                final JSONObject node = nodes.getJSONObject(n);
                final int index = node.getInt("index");
                assertTight(at + " node " + index + ": underlying",
                        node.getDouble("underlying"), tree.underlying(i, index));
                final JSONArray desc = node.getJSONArray("descendants");
                final JSONArray probs = node.getJSONArray("probabilities");
                for ( int b = 0; b < 3; b++ ) {
                    assertEquals(at + " node " + index + ": descendant[" + b + "]",
                            desc.getLong(b), tree.descendant(i, index, b));
                    assertTight(at + " node " + index + ": probability[" + b + "]",
                            probs.getDouble(b), tree.probability(i, index, b));
                }
            }
        }

        final JSONArray terminal = e.getJSONArray("terminalUnderlying");
        for ( int index = 0; index < terminal.length(); index++ ) {
            assertTight(caseName + ": terminal underlying[" + index + "]",
                    terminal.getDouble(index), tree.underlying(nSteps, index));
        }
    }

    @Test
    public void testUniformGrid() {
        QL.info("Testing TrinomialTree on a uniform grid against C++ v1.43...");
        // dt == dtMax on every step, so the gate never fires: the tree must be identical to v1.42.1.
        checkTree("tree_uniform_ou", ouProcess(), new TimeGrid(3.0, 6), false);
    }

    @Test
    public void testWeekendRollGrid() {
        QL.info("Testing TrinomialTree on a weekend-roll grid against C++ v1.43...");
        // Shortest step is 3/365 against dtMax = 0.25, a ratio of 0.033 -- above the 0.01 gate, so the floor
        // must stay off. This is the "typical non-uniform grids untouched" claim in the upstream comment.
        checkTree("tree_weekend_roll_ou", ouProcess(),
                mandatoryGrid(0.25, 0.5, 0.5 + 3.0 / 365.0, 0.75, 1.0), false);
    }

    @Test
    public void testSmallMandatoryGap() {
        QL.info("Testing TrinomialTree small-mandatory-gap pathology against C++ v1.43...");
        // The case the fix exists for: a 1ms mandatory gap after t = 1.
        checkTree("tree_small_gap_ou", ouProcess(), mandatoryGrid(1.0, 1.0 + 1.0e-3, 2.0, 3.0), false);
    }

    @Test
    public void testSmallMandatoryGapWithShortTail() {
        QL.info("Testing TrinomialTree dx floor uses the grid-wide maximum variance against C++ v1.43...");
        // Same pathology, but the final step (dt = 0.5) is deliberately not the variance argmax. dxFloor is
        // built from the maximum variance over *all* steps; on every other grid here the last step happens
        // to attain that maximum, so a `dxFloorVar = v2_i` assignment where upstream writes
        // `max(dxFloorVar, v2_i)` would go unnoticed. Here the two differ visibly.
        checkTree("tree_small_gap_short_tail_ou", ouProcess(),
                mandatoryGrid(1.0, 1.0 + 1.0e-3, 2.0, 2.5), false);
    }

    @Test
    public void testFloorThresholdBelow() {
        QL.info("Testing TrinomialTree just below the dx-floor activation threshold against C++ v1.43...");
        checkTree("tree_threshold_below_ou", ouProcess(),
                mandatoryGrid(1.0, 1.0 + 0.0099, 2.0, 3.0), false);
    }

    @Test
    public void testFloorThresholdAbove() {
        QL.info("Testing TrinomialTree just above the dx-floor activation threshold against C++ v1.43...");
        checkTree("tree_threshold_above_ou", ouProcess(),
                mandatoryGrid(1.0, 1.0 + 0.0101, 2.0, 3.0), false);
    }

    @Test
    public void testGateFiresButDxUnchanged() {
        QL.info("Testing TrinomialTree gate-fires-but-dx-unchanged case against C++ v1.43...");
        // dt = 0.005 < 0.01 * dtMax so the gate fires, but the short step carries the grid's largest
        // variance, so max(dxNatural, dxFloor) must return dxNatural and leave dx alone.
        //
        // What this case does and does not prove. dxFloor is built from the maximum variance over all
        // steps, so dxNatural_i <= dxFloor for every i, with equality exactly when step i is the argmax.
        // "Gate fires but dx unchanged" is therefore precisely that tie -- and at the tie dx^2 == 3*v^2,
        // which makes the general moment-matching form algebraically identical to the classical one
        // (p2 = 1 - (v2+e2)/3v2 = (2 - e2/v2)/3, and likewise for p1/p3). So this case cannot distinguish
        // the two probability branches: they agree to a couple of ULP. What it does pin, tightly, is that
        // dx is left at exactly v*sqrt(3) -- verified by the strict-equality assertion in checkTree, which
        // catches any perturbation of dxFloor's value or operation order.
        checkTree("tree_gate_fires_dx_unchanged", spikeProcess(0.0),
                mandatoryGrid(1.0, 1.005, 2.0, 3.0), false);
    }

    @Test
    public void testIsPositiveBumpKeepsSignedWeights() {
        QL.info("Testing TrinomialTree isPositive temp-bump exemption against C++ v1.43...");
        // At the spiked step dx (0.0612) exceeds x0 (0.05), so isPositive bumps `temp` upward, |e| exceeds
        // dx/2 and p2 comes out negative. v1.43 exempts bumped nodes from its new non-negativity assertion;
        // a port that asserts unconditionally throws instead of producing these values.
        checkTree("tree_is_positive_bump", spikeProcess(0.05),
                mandatoryGrid(1.0, 1.005, 2.0, 3.0), true);
    }

    @Test
    public void testNegativeProbabilityOccursOnlyWhereUpstreamAllowsIt() {
        QL.info("Testing TrinomialTree signed-weight regimes against C++ v1.43...");
        // Sanity cross-check on the reference itself, independent of the port: negative weights must appear
        // only where upstream documents them -- in the floored regime, or on an isPositive-bumped node.
        // Every unfloored, unbumped node must have non-negative probabilities by construction.
        for ( final String caseName : new String[] { "tree_uniform_ou", "tree_weekend_roll_ou",
                "tree_threshold_above_ou", "tree_gate_fires_dx_unchanged" } ) {
            final JSONObject e = expected(caseName);
            final JSONArray dx = e.getJSONArray("dx");
            final JSONArray dxNatural = e.getJSONArray("dxNatural");
            final JSONArray steps = e.getJSONArray("steps");
            for ( int s = 0; s < steps.length(); s++ ) {
                final JSONObject step = steps.getJSONObject(s);
                final int i = step.getInt("i");
                if ( dx.getDouble(i + 1) > dxNatural.getDouble(i + 1) ) {
                    continue; // floored regime: signed weights are accepted upstream
                }
                final JSONArray nodes = step.getJSONArray("nodes");
                for ( int n = 0; n < nodes.length(); n++ ) {
                    final JSONArray probs = nodes.getJSONObject(n).getJSONArray("probabilities");
                    for ( int b = 0; b < 3; b++ ) {
                        assertTrue(caseName + " step " + i + " node " + n + ": probability[" + b
                                + "] must be non-negative in the unfloored, unbumped regime but is "
                                + probs.getDouble(b), probs.getDouble(b) >= 0.0);
                    }
                }
            }
        }
    }
}
