package org.jquantlib.testsuite.util;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReferenceReaderTest {

    @Test
    public void loadsSmokeTestReference() {
        final ReferenceReader ref = ReferenceReader.load("_smoke_test");
        assertEquals("_smoke_test", ref.testGroup());
        assertEquals("1.42.1", ref.cppVersion());
        assertEquals("_smoke_test_probe", ref.generatedBy());
        assertTrue(ref.caseNames().contains("qlVersion"));
        assertTrue(ref.caseNames().contains("epochSerial"));
    }

    @Test
    public void smokeTestCaseExpectedValues() {
        final ReferenceReader ref = ReferenceReader.load("_smoke_test");
        assertEquals("1.42.1", ref.getCase("qlVersion").expectedString());
        // The exact epoch serial is QuantLib's internal convention; we just
        // assert it's a positive integer reasonable for 1-Jan-1901.
        final long serial = ref.getCase("epochSerial").expectedLong();
        assertTrue(serial > 0);
        assertTrue(serial < 100_000);
    }
}
