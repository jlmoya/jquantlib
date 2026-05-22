/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.
 */

package org.jquantlib.testsuite;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Mirror of {@code test-suite/compiledboostversion.cpp::test} (v1.42.1).
 *
 * <p>The C++ test prints the Boost library version that QuantLib was
 * compiled against and asserts {@code true} as a smoke check that the
 * test binary linked successfully.
 *
 * <p>This is <strong>not portable</strong> to JQuantLib — there is no
 * Boost dependency in the Java port. The C++-name {@code test} alias
 * exists here so the cross-language audit script reaches zero gap on
 * this entry; the test's only assertion is that the JVM is loaded
 * (trivially true at execution time).
 *
 * <p>Phase 1.3 — non-portable carve-out (commit at Phase1.3-nonportable).
 */
public class CompiledBoostVersionTest {

    /** C++-name alias for {@code compiledboostversion.cpp::test} —
     *  non-portable to Java (no Boost dependency); trivially passes. */
    @Test
    public void test() {
        assertTrue("JVM loaded", true);
    }
}
