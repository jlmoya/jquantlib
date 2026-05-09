/*
 Copyright (C) 2026 JQuantLib migration

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

/*
 Copyright (C) 2008 Roland Lichters
*/

package org.jquantlib.experimental.credit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jquantlib.QL;
import org.jquantlib.currencies.Currency;
import org.jquantlib.time.Period;

/**
 * Pool of issuers.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::Pool}
 * ({@code ql/experimental/credit/pool.{hpp,cpp}}).
 *
 * <p>Each entry holds an {@link Issuer}, a default-prob {@link DefaultProbKey}
 * (the seniority/currency under which this name enters the basket), and a
 * scalar simulation time. Insertion preserves order via a parallel name
 * vector for stable iteration matching the C++ {@code names_} field.
 *
 * <p>Phase 4m foundation.
 */
public class Pool {

    private final Map<String, Issuer> data = new HashMap<>();
    private final Map<String, Double> time = new HashMap<>();
    private final List<String> names = new ArrayList<>();
    /*
     * LinkedHashMap preserves insertion order so {@code defaultKeys()} aligns
     * 1:1 with {@code names()} — required by basket / latent-model consumers
     * that index both with the same {@code iName}. (C++ uses {@code std::map}
     * which is alphabetical-by-key; in Java a {@code HashMap} would yield
     * non-deterministic order, so we use {@code LinkedHashMap} for explicit
     * insertion-order semantics matching the {@code names_} vector.)
     */
    private final Map<String, DefaultProbKey> defaultKeys = new LinkedHashMap<>();

    public Pool() {
        clear();
    }

    public int size() {
        return names.size();
    }

    public final void clear() {
        data.clear();
        time.clear();
        names.clear();
        defaultKeys.clear();
    }

    public boolean has(final String name) {
        return data.containsKey(name);
    }

    /** Default contract trigger: NA-Corp default key with empty currency, SeniorSec, zero grace, $1 amount. */
    public void add(final String name, final Issuer issuer) {
        add(name, issuer, new NorthAmericaCorpDefaultKey(
                new Currency(), Seniority.SeniorSec, new Period(), 1.0));
    }

    public void add(final String name, final Issuer issuer,
                    final DefaultProbKey contractTrigger) {
        if (!has(name)) {
            data.put(name, issuer);
            time.put(name, 0.0);
            names.add(name);
            defaultKeys.put(name, contractTrigger);
        }
    }

    public Issuer get(final String name) {
        QL.require(has(name), name + " not found");
        return data.get(name);
    }

    public DefaultProbKey defaultKey(final String name) {
        QL.require(has(name), name + " not found");
        return defaultKeys.get(name);
    }

    public double getTime(final String name) {
        QL.require(has(name), name + " not found");
        return time.get(name);
    }

    public void setTime(final String name, final double t) {
        time.put(name, t);
    }

    public List<String> names() {
        return Collections.unmodifiableList(names);
    }

    public List<DefaultProbKey> defaultKeys() {
        final List<DefaultProbKey> result = new ArrayList<>(defaultKeys.size());
        result.addAll(defaultKeys.values());
        return result;
    }
}
