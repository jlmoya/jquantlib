/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.math.statistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.math.statistics.Histogram;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validation of {@link Histogram} against C++ v1.42.1 references in
 * {@code migration-harness/references/credit-loss-models/histogram.json}.
 *
 * <p>Tolerance tier: TIGHT (1e-12 abs).
 */
public class HistogramTest {

    private static final String TEST_GROUP = "credit-loss-models/histogram";
    private static final ReferenceReader REF = ReferenceReader.load(TEST_GROUP);

    private static final double TIGHT = 1.0e-12;

    @Test
    public void allCasesMatchCppReferences() {
        for (final String caseName : REF.caseNames()) {
            final ReferenceReader.Case c = REF.getCase(caseName);
            final JSONObject inputs = c.inputs();
            final JSONArray dataArr = inputs.getJSONArray("data");
            final double[] data = new double[dataArr.length()];
            for (int i = 0; i < data.length; ++i) data[i] = dataArr.getDouble(i);

            final String algo = inputs.getString("algorithm");
            final Histogram h;
            if ("fixed".equals(algo)) {
                h = new Histogram(data, inputs.getInt("bins"));
            } else if ("Sturges".equals(algo)) {
                h = new Histogram(data, Histogram.Algorithm.Sturges);
            } else if ("FD".equals(algo)) {
                h = new Histogram(data, Histogram.Algorithm.FD);
            } else if ("Scott".equals(algo)) {
                h = new Histogram(data, Histogram.Algorithm.Scott);
            } else {
                throw new AssertionError("unknown algorithm in case " + caseName + ": " + algo);
            }

            final JSONObject exp = (JSONObject) c.expectedRaw();
            final int expBins = exp.getInt("bins");
            assertEquals(caseName + ": bins", expBins, h.bins());

            final JSONArray expBreaks = exp.getJSONArray("breaks");
            final double[] actualBreaks = h.breaks();
            assertEquals(caseName + ": breaks length", expBreaks.length(), actualBreaks.length);
            for (int i = 0; i < expBreaks.length(); ++i) {
                assertEquals(caseName + ": break[" + i + "]",
                        expBreaks.getDouble(i), actualBreaks[i], TIGHT);
            }

            final JSONArray expCounts = exp.getJSONArray("counts");
            assertEquals(caseName + ": counts length", expCounts.length(), h.bins());
            for (int i = 0; i < expCounts.length(); ++i) {
                assertEquals(caseName + ": counts[" + i + "]",
                        expCounts.getInt(i), h.counts(i));
            }

            final JSONArray expFreq = exp.getJSONArray("frequency");
            for (int i = 0; i < expFreq.length(); ++i) {
                assertEquals(caseName + ": frequency[" + i + "]",
                        expFreq.getDouble(i), h.frequency(i), TIGHT);
            }
        }
    }

    @Test
    public void emptyAndExplicitBreaks() {
        // Explicit-breaks ctor: bins = breaks.length + 1
        final double[] data = {0.5, 1.5, 2.5, 3.5, 4.5};
        final double[] breaks = {1.0, 3.0};
        final Histogram h = new Histogram(data, breaks);
        assertEquals(3, h.bins());
        assertEquals(1, h.counts(0));   // 0.5
        assertEquals(2, h.counts(1));   // 1.5, 2.5
        assertEquals(2, h.counts(2));   // 3.5, 4.5

        // Empty -> error
        try {
            new Histogram(new double[0], 4);
            assertTrue("expected exception on empty data", false);
        } catch (final RuntimeException expected) {
            // ok
        }
    }
}
