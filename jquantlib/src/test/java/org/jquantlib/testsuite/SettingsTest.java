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

package org.jquantlib.testsuite;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.testsuite.util.Flag;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/settings.cpp (Phase 5a).
 *
 * <p>The C++ file has a single BOOST_AUTO_TEST_CASE
 * ({@code testNotificationsOnDateChange}) that registers a Flag observer
 * with {@code Settings::evaluationDate()} and verifies notifications fire
 * only when the assigned date actually changes.
 *
 * <p>Phase 5 META design D1: the Java {@link Settings} class is a
 * ThreadLocal-backed singleton (not the C++ {@code Settings::instance()}
 * model). Functional notification semantics are still exercised via the
 * {@code DateProxy} observable returned by {@link Settings#evaluationDate()}.
 */
public class SettingsTest {

    public SettingsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testNotificationsOnDateChange() {
        QL.info("Testing notifications on evaluation-date change...");

        final Date d1 = new Date(11, Month.February, 2021);
        final Date d2 = new Date(12, Month.February, 2021);

        // Save and restore evaluation date to avoid side-effects
        final Date saved = new Settings().evaluationDate();
        try {
            new Settings().setEvaluationDate(d1);

            final Flag flag = new Flag();
            new Settings().evaluationDate().addObserver(flag);

            // Set to same date — no notification expected.
            new Settings().setEvaluationDate(d1);
            if (flag.isUp()) {
                fail("unexpected notification");
            }

            // Set to different date — notification expected.
            new Settings().setEvaluationDate(d2);
            if (!flag.isUp()) {
                fail("missing notification");
            }
        } finally {
            new Settings().setEvaluationDate(saved);
        }
    }
}
