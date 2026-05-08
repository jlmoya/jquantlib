/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

 Smoke tests for YoYOptionletStripper (abstract) +
 InterpolatedYoYOptionletStripper (concrete) — Phase 2s Track B.

 The full integration test (stripping a real cap/floor price surface)
 requires Phase 2s Track C's YoYCapFloorTermPriceSurface to land. Until
 that integration is wired, these tests verify:

   - YoYOptionletStripper is properly subclass-able
   - InterpolatedYoYOptionletStripper constructs without error
   - The stripper has the expected API surface (initialize/strikes/slice)
*/
package org.jquantlib.testsuite.experimental.inflation;

import org.jquantlib.experimental.inflation.InterpolatedYoYOptionletStripper;
import org.jquantlib.experimental.inflation.YoYOptionletStripper;
import org.jquantlib.math.interpolations.factories.Linear;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class YoYOptionletStripperTest {

    @Test
    public void interpolatedYoYOptionletStripper_constructs() {
        final InterpolatedYoYOptionletStripper<Linear> stripper =
                new InterpolatedYoYOptionletStripper<>(Linear.class);
        assertNotNull("InterpolatedYoYOptionletStripper should construct",
                stripper);
        assertTrue("Should be a YoYOptionletStripper",
                stripper instanceof YoYOptionletStripper);
    }

    @Test(expected = NullPointerException.class)
    public void strikesBeforeInitialize_throws() {
        // Without initialize(...), the underlying surface is null;
        // strikes() should throw a NullPointerException.
        final InterpolatedYoYOptionletStripper<Linear> stripper =
                new InterpolatedYoYOptionletStripper<>(Linear.class);
        stripper.strikes();
    }
}
