/*
 Copyright (C) 2007 Richard Gomes

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

package org.jquantlib;

import java.util.Map;
import java.util.TreeMap;

import org.jquantlib.time.Date;

/**
 * Settings for the application.
 * <p>
 * This class aggregates mutable values which have life cycle of a certain operation or
 * sequence of operations defined by the enclosing thread.
 *
 * @see ThreadLocal
 *
 * @author Richard Gomes
 */
public class Settings {

    /**
     * Define this if negative yield rates should be allowed. This might not be safe.
     */
    private static final String NEGATIVE_RATES = "NEGATIVE_RATES";

    /**
     * Define this if extra safety checks should be performed. This can degrade performance.
     */
    private static final String EXTRA_SAFETY_CHECKS = "EXTRA_SAFETY_CHECKS";

    /**
     * Define this if payments occurring today should enter the NPV of an instrument.
     */
    private static final String TODAYS_PAYMENTS = "TODAYS_PAYMENTS";

    /**
     * Define this to use indexed coupons instead of par coupons in floating legs.
     */
    private static final String USE_INDEXED_COUPON = "USE_INDEXED_COUPON";


    /**
     * ENFORCE_TODAYS_HISTORIC_FIXINGS
     */
    private static final String ENFORCES_TODAYS_HISTORIC_FIXINGS = "ENFORCES_TODAYS_HISTORIC_FIXINGS";

    /**
     * Mirrors C++ {@code Settings::includeReferenceDateEvents()}
     * (settings.hpp v1.42.1 lines 95-96, 113). When {@code true}, an
     * {@link org.jquantlib.cashflow.Event} occurring on the reference
     * date is treated as <em>not yet</em> happened (i.e., still
     * pending). Default is {@code false}, matching C++.
     */
    private static final String INCLUDE_REFERENCE_DATE_EVENTS = "INCLUDE_REFERENCE_DATE_EVENTS";

    /**
     * Mirrors C++ {@code Settings::includeTodaysCashFlows()}
     * (settings.hpp v1.42.1 lines 105-106, 114). Java's nullable
     * {@link Boolean} corresponds to C++ {@code ext::optional<bool>}:
     * {@code null} means unset (no override), {@code true}/{@code false}
     * apply the override at the evaluation date. Default is {@code null}
     * (unset), matching C++ default-constructed {@code optional}.
     */
    private static final String INCLUDE_TODAYS_CASHFLOWS = "INCLUDE_TODAYS_CASHFLOWS";


    /**
     * The relative error of the approximation has absolute value less than 1.15e-9.
     * One iteration of Halley's rational method (third order) gives full machine precision.
     */
    private static final String REFINE_TO_FULL_MACHINE_PRECISION_USING_HALLEYS_METHOD = "REFINE_TO_FULL_MACHINE_PRECISION_USING_HALLEYS_METHOD";

    /**
     * Changes the value of field evaluationDate.
     * <p>
     * Notice that a successful change of evaluationDate notifies all its listeners.
     */
    private static final String EVALUATION_DATE = "EVALUATION_DATE";



    public boolean isNegativeRates() {
        final Object var = attrs.get().get(NEGATIVE_RATES);
        return var==null? false : (Boolean) var;
    }

    public boolean isExtraSafetyChecks() {
        final Object var = attrs.get().get(EXTRA_SAFETY_CHECKS);
        return var==null? false : (Boolean) var;
    }

    public boolean isTodaysPayments() {
        final Object var = attrs.get().get(TODAYS_PAYMENTS);
        return var==null? false : (Boolean) var;
    }

    public boolean isUseIndexedCoupon() {
        final Object var = attrs.get().get(USE_INDEXED_COUPON);
        return var==null? false : (Boolean) var;
    }

    public boolean isEnforcesTodaysHistoricFixings() {
        final Object var = attrs.get().get(ENFORCES_TODAYS_HISTORIC_FIXINGS);
        return var==null? false : (Boolean) var;
    }

    public boolean isRefineHighPrecisionUsingHalleysMethod() {
        final Object var = attrs.get().get(REFINE_TO_FULL_MACHINE_PRECISION_USING_HALLEYS_METHOD);
        return var==null? false : (Boolean) var;
    }

    public void setNegativeRates(final boolean negativeRates) {
        attrs.get().put(NEGATIVE_RATES, negativeRates);
    }

    public void setExtraSafetyChecks(final boolean extraSafetyChecks) {
        attrs.get().put(EXTRA_SAFETY_CHECKS, extraSafetyChecks);
    }

    public void setTodaysPayments(final boolean todaysPayments) {
        attrs.get().put(TODAYS_PAYMENTS, todaysPayments);
    }

    public void setUseIndexedCoupon(final boolean todaysPayments) {
        attrs.get().put(USE_INDEXED_COUPON, todaysPayments);
    }


    public void setEnforcesTodaysHistoricFixings(final boolean enforceTodaysHistoricFixings) {
        attrs.get().put(ENFORCES_TODAYS_HISTORIC_FIXINGS, enforceTodaysHistoricFixings);
    }

    public void setRefineHighPrecisionUsingHalleysMethod(final boolean refineToFullMachinePrecisionUsingHalleysMethod) {
        attrs.get().put(REFINE_TO_FULL_MACHINE_PRECISION_USING_HALLEYS_METHOD, refineToFullMachinePrecisionUsingHalleysMethod);
    }

    /**
     * Mirrors C++ {@code Settings::includeReferenceDateEvents() const}
     * (settings.hpp v1.42.1 line 96).
     *
     * @return whether events occurring on the reference date should
     *         be considered as not-yet-happened. Default is {@code false}.
     */
    public boolean includeReferenceDateEvents() {
        final Object v = attrs.get().get(INCLUDE_REFERENCE_DATE_EVENTS);
        return v == null ? false : (Boolean) v;
    }

    /**
     * Mirrors C++ {@code Settings::includeReferenceDateEvents()} (mutable
     * reference, settings.hpp v1.42.1 line 95). Java exposes this as a
     * fluent setter returning {@code this} for chaining.
     */
    public Settings setIncludeReferenceDateEvents(final boolean v) {
        attrs.get().put(INCLUDE_REFERENCE_DATE_EVENTS, v);
        return this;
    }

    /**
     * Mirrors C++ {@code Settings::includeTodaysCashFlows() const}
     * (settings.hpp v1.42.1 line 106). Returns {@code null} when unset
     * (Java's nullable {@link Boolean} = C++ {@code ext::optional<bool>}).
     */
    public Boolean includeTodaysCashFlows() {
        return (Boolean) attrs.get().get(INCLUDE_TODAYS_CASHFLOWS);
    }

    /**
     * Mirrors C++ {@code Settings::includeTodaysCashFlows()} (mutable
     * reference, settings.hpp v1.42.1 line 105). Pass {@code null} to
     * unset (equivalent to assigning {@code ext::nullopt} in C++).
     */
    public Settings setIncludeTodaysCashFlows(final Boolean v) {
        if (v == null) {
            attrs.get().remove(INCLUDE_TODAYS_CASHFLOWS);
        } else {
            attrs.get().put(INCLUDE_TODAYS_CASHFLOWS, v);
        }
        return this;
    }



    /**
     * @return the value of field evaluationDate
     */
    public Date evaluationDate() {
        return ((DateProxy) attrs.get().get(EVALUATION_DATE)).value();
    }

    /**
     * Changes the value of field evaluationDate.
     *
     * <p>
     * Notice that a successful change of evaluationDate notifies all its
     * listeners.
     */
    public Date setEvaluationDate(final Date evaluationDate) {
        final DateProxy proxy = (DateProxy) attrs.get().get(EVALUATION_DATE);
        proxy.assign(evaluationDate);
        return proxy;
    }



    //
    // private inner classes
    //




    private static final ThreadAttributes attrs = new ThreadAttributes();

    //
    // Settings employs a ThreadLocal object in order to keep thread dependent data.
    // In spite <code>attrs</code> seems to be static and, for this reason, contain the same contents whatever
    // thread employs it, actually what happens is that ThreadLocal internally organized data using a thread id
    // or something like this as a key, in order to obtain thread dependent data.
    // So, what we do below is the initialization of this map, which means to say we are assigning default
    // values to these attributes. Every thread has freedom to change these attributes and can be sure that
    // no other thread will be affected bythese changes.
    // [Richard Gomes]
    //
    private static class ThreadAttributes extends ThreadLocal<Map<String,Object>> {
        @Override
        public Map<String,Object> initialValue() {
            final Map<String, Object> map = new TreeMap<String, Object>();
            map.put(ENFORCES_TODAYS_HISTORIC_FIXINGS, false);
            map.put(NEGATIVE_RATES, false);
            map.put(EXTRA_SAFETY_CHECKS, true);
            map.put(TODAYS_PAYMENTS, true);
            map.put(USE_INDEXED_COUPON, false);
            map.put(REFINE_TO_FULL_MACHINE_PRECISION_USING_HALLEYS_METHOD, false);
            map.put(EVALUATION_DATE, new DateProxy());
            // C++-aligned defaults (settings.hpp v1.42.1 lines 113-114):
            //   includeReferenceDateEvents_ = false
            //   includeTodaysCashFlows_ = ext::optional<bool>{} (unset)
            map.put(INCLUDE_REFERENCE_DATE_EVENTS, false);
            // Note: INCLUDE_TODAYS_CASHFLOWS is intentionally NOT pre-set —
            // missing key represents the C++ "ext::nullopt" (unset) default.
            return map;
        }
    }


    //
    // private inner classes
    //

    private static class DateProxy extends Date {

        // outside world cannot instantiate
        private DateProxy() {
            super();
        }

        private DateProxy value() /* @ReadOnly */ {
            if (isNull()) {
                super.assign(todaysSerialNumber());
            }
            return this;
        }

        private Date assign(final Date date) {
            // Align with C++ QuantLib v1.42.1 (settings.hpp:141-145):
            // 'if (value() != d) ObservableValue<Date>::operator=(d);'
            // — suppress notifications when assigning the same date.
            if (super.serialNumber() == date.serialNumber()) {
                return this;
            }
            super.assign(date.serialNumber());
            super.notifyObservers();
            return this;
        }

    }

}
