/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.SimpleCashFlow;
import org.jquantlib.instruments.EarlierThanCashFlowComparator;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

/**
 * Unit tests for {@link EarlierThanCashFlowComparator}.
 *
 * <p>Mirrors C++ v1.42.1 {@code earlier_than<CashFlow>::operator()} from
 * {@code ql/cashflow.hpp:81-86} which is defined as
 * {@code c1.date() < c2.date()} i.e. strict less-than. The Java
 * {@link java.util.Comparator} contract requires:
 * <ul>
 *   <li>{@code compare(a,b) == 0} when dates are equal (reflexive equality)</li>
 *   <li>{@code sign(compare(a,b)) == -sign(compare(b,a))} (antisymmetric)</li>
 * </ul>
 * The previous implementation returned {@code -1} for equal dates because of
 * an early {@code le} check, which broke {@link Collections#sort} stability.
 */
public class EarlierThanCashFlowComparatorTest {

    @Test
    public void compareEqualDatesReturnsZero() {
        final CashFlow a = new SimpleCashFlow(1.0, new Date(1, Month.January, 2025));
        final CashFlow b = new SimpleCashFlow(2.0, new Date(1, Month.January, 2025));
        final EarlierThanCashFlowComparator cmp = new EarlierThanCashFlowComparator();

        assertEquals("equal dates → 0 (reflexive)", 0, cmp.compare(a, b));
        assertEquals("equal dates → 0 (symmetric)", 0, cmp.compare(b, a));
    }

    @Test
    public void compareEarlierDateReturnsNegative() {
        final CashFlow earlier = new SimpleCashFlow(1.0, new Date(1, Month.January, 2025));
        final CashFlow later = new SimpleCashFlow(2.0, new Date(1, Month.February, 2025));
        final EarlierThanCashFlowComparator cmp = new EarlierThanCashFlowComparator();

        assertTrue("earlier < later", cmp.compare(earlier, later) < 0);
        assertTrue("later > earlier", cmp.compare(later, earlier) > 0);
    }

    @Test
    public void antisymmetric() {
        final CashFlow a = new SimpleCashFlow(1.0, new Date(15, Month.March, 2025));
        final CashFlow b = new SimpleCashFlow(2.0, new Date(20, Month.June, 2025));
        final EarlierThanCashFlowComparator cmp = new EarlierThanCashFlowComparator();

        final int ab = cmp.compare(a, b);
        final int ba = cmp.compare(b, a);
        assertEquals("antisymmetric: sign(compare(a,b)) == -sign(compare(b,a))",
                Integer.signum(ab), -Integer.signum(ba));
    }

    @Test
    public void sortStableForEqualDates() {
        // Ensure Collections.sort doesn't reorder equal-date entries.
        // With the old broken comparator, equal-date elements were treated as
        // "a < b AND b < a", violating the comparator contract and triggering
        // "Comparison method violates its general contract!" on TimSort, OR
        // silently reordering equal elements (Java < 7).
        final Date d = new Date(10, Month.July, 2025);
        final List<CashFlow> cfs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            cfs.add(new SimpleCashFlow(i, d));
        }
        Collections.sort(cfs, new EarlierThanCashFlowComparator());

        // Stable sort: amounts should remain in insertion order 0..9 since
        // all dates are equal.
        for (int i = 0; i < 10; i++) {
            assertEquals("stable sort preserves order for equal dates",
                    (double) i, cfs.get(i).amount(), 0.0);
        }
    }

    @Test
    public void sortMixedDates() {
        final List<CashFlow> cfs = new ArrayList<>();
        cfs.add(new SimpleCashFlow(3.0, new Date(1, Month.March, 2025)));
        cfs.add(new SimpleCashFlow(1.0, new Date(1, Month.January, 2025)));
        cfs.add(new SimpleCashFlow(2.0, new Date(1, Month.February, 2025)));

        Collections.sort(cfs, new EarlierThanCashFlowComparator());

        assertEquals(1.0, cfs.get(0).amount(), 0.0);
        assertEquals(2.0, cfs.get(1).amount(), 0.0);
        assertEquals(3.0, cfs.get(2).amount(), 0.0);
    }
}
