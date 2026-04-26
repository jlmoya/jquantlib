/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 2d WI-3: HaltonRsg cross-validation against C++ v1.42.1.
 */
package org.jquantlib.testsuite.math.randomnumbers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.jquantlib.math.randomnumbers.HaltonRsg;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Bit-exact fingerprint test for {@link HaltonRsg}: the first 100
 * 4-dimensional Halton samples (seed=42, randomStart=false, randomShift=false)
 * must match the C++ v1.42.1 reference. With both random flags off the seed
 * is unused and every coordinate is a finite van-der-Corput rational, so the
 * exact tier (0.0 tolerance) is appropriate — any drift indicates a porting
 * bug, not floating-point noise.
 */
public class HaltonRsgTest {

    @Test
    public void testFirst100SequencesMatchCpp() {
        final ReferenceReader reader =
                ReferenceReader.load("math/randomnumbers/halton_rsg");
        final Case c = reader.getCase("first_100_dim4");

        final JSONObject in = c.inputs();
        final int dim = in.getInt("dimensionality");
        final long seed = in.getLong("seed");
        final boolean randomStart = in.getBoolean("randomStart");
        final boolean randomShift = in.getBoolean("randomShift");
        final int n = in.getInt("count");

        final HaltonRsg gen = new HaltonRsg(dim, seed, randomStart, randomShift);

        final JSONObject exp = (JSONObject) c.expectedRaw();
        final JSONArray sequence = exp.getJSONArray("sequence");
        assertEquals("reference sequence length", n, sequence.length());

        for (int i = 0; i < n; ++i) {
            final HaltonRsg.Sample s = gen.nextSequence();
            assertNotNull("sample[" + i + "] must not be null", s);
            assertEquals("sample[" + i + "].value.length", dim, s.value.length);
            final JSONArray row = sequence.getJSONArray(i);
            for (int j = 0; j < dim; ++j) {
                final double expected = row.getDouble(j);
                // Exact tier — van der Corput inversion is rational and fully
                // deterministic with randomStart/randomShift disabled.
                assertEquals("sample[" + i + "][" + j + "]",
                        expected, s.value[j], 0.0);
            }
        }
    }
}
