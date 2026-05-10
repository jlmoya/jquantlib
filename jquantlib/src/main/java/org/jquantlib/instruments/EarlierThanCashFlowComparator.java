package org.jquantlib.instruments;

import java.io.Serializable;
import java.util.Comparator;

import org.jquantlib.cashflow.CashFlow;

/**
 * Comparator placing earlier cash flows before later ones.
 *
 * <p>Mirrors C++ v1.42.1 {@code earlier_than<CashFlow>::operator()}
 * (ql/cashflow.hpp:81-86), which is defined as strict less-than on the
 * cash-flow {@code date()}: {@code c1.date() < c2.date()}.
 *
 * <p>Java {@link Comparator} semantics are equivalent to C++'s
 * {@code std::sort} comparator: it must return a negative number for
 * "less than", positive for "greater than", and {@code 0} for "equal".
 * Equal dates must yield {@code 0} so {@link java.util.Collections#sort
 * Collections.sort} remains stable. The pre-Phase 5d.5-Bonds-b
 * implementation returned {@code -1} for equal dates (because of an early
 * {@code le} check covering both {@code <} and {@code ==}), violating the
 * comparator contract and corrupting bond cash-flow ordering when multiple
 * coupons / redemptions share a date.
 *
 * @author Ueli Hofstetter
 */
public class EarlierThanCashFlowComparator implements Comparator<CashFlow>, Serializable  {

    private static final long serialVersionUID = 1L;

    /**
     * Compares its two arguments for order.
     * Returns a negative integer, zero, or a positive integer as the
     * first argument is less than, equal to, or greater than the second.
     */
    @Override
    public int compare(final CashFlow o1, final CashFlow o2) {
        return o1.date().compareTo(o2.date());
    }


}
