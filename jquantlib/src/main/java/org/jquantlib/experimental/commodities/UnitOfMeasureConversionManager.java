/*
 Copyright (C) 2026 JQuantLib

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2008 J. Erik Radmall
 Copyright (C) 2009 StatPro Italia srl
*/

package org.jquantlib.experimental.commodities;

import org.jquantlib.lang.exceptions.LibraryException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Singleton repository of conversion factors between units of measure.
 * <p>
 * Java port of QuantLib v1.42.1 {@code unitofmeasureconversionmanager.{hpp,cpp}}.
 */
public final class UnitOfMeasureConversionManager {

    private static final UnitOfMeasureConversionManager INSTANCE = new UnitOfMeasureConversionManager();

    private final List< UnitOfMeasureConversion > data_ = new LinkedList<>();

    private UnitOfMeasureConversionManager() {
        addKnownConversionFactors();
    }

    public static UnitOfMeasureConversionManager getInstance() {
        return INSTANCE;
    }

    private static boolean matches(final UnitOfMeasureConversion c1, final UnitOfMeasureConversion c2) {
        return c1.commodityType().equals(c2.commodityType()) && (
                (c1.source().equals(c2.source()) && c1.target().equals(c2.target())) || (c1.source().equals(c2.target())
                        && c1.target().equals(c2.source())));
    }

    private static boolean matches(final UnitOfMeasureConversion c, final CommodityType commodityType,
            final UnitOfMeasure source, final UnitOfMeasure target) {
        return c.commodityType().equals(commodityType) && ((c.source().equals(source) && c.target().equals(target)) || (
                c.source().equals(target) && c.target().equals(source)));
    }

    private static boolean matches(final UnitOfMeasureConversion c, final CommodityType commodityType,
            final UnitOfMeasure source) {
        return c.commodityType().equals(commodityType) && (c.source().equals(source) || c.target().equals(source));
    }

    public void add(final UnitOfMeasureConversion c) {
        // mirror C++: drop any existing matching entry, then append
        final Iterator< UnitOfMeasureConversion > it = data_.iterator();
        while ( it.hasNext() ) {
            if ( matches(it.next(), c) ) {
                it.remove();
                break;
            }
        }
        data_.add(c);
    }

    public void clear() {
        data_.clear();
        addKnownConversionFactors();
    }

    /** Default-derived lookup. */
    public UnitOfMeasureConversion lookup(final CommodityType commodityType, final UnitOfMeasure source,
            final UnitOfMeasure target) {
        return lookup(commodityType, source, target, UnitOfMeasureConversion.Type.Derived);
    }

    public UnitOfMeasureConversion lookup(final CommodityType commodityType, final UnitOfMeasure source,
            final UnitOfMeasure target, final UnitOfMeasureConversion.Type type) {
        if ( type == UnitOfMeasureConversion.Type.Direct ) {
            return directLookup(commodityType, source, target);
        }
        if ( !source.triangulationUnitOfMeasure().empty() ) {
            final UnitOfMeasure link = source.triangulationUnitOfMeasure();
            if ( link.equals(target) ) {
                return directLookup(commodityType, source, link);
            }
            return UnitOfMeasureConversion.chain(directLookup(commodityType, source, link),
                    lookup(commodityType, link, target));
        }
        if ( !target.triangulationUnitOfMeasure().empty() ) {
            final UnitOfMeasure link = target.triangulationUnitOfMeasure();
            if ( source.equals(link) ) {
                return directLookup(commodityType, link, target);
            }
            return UnitOfMeasureConversion.chain(lookup(commodityType, source, link),
                    directLookup(commodityType, link, target));
        }
        return smartLookup(commodityType, source, target, new ArrayList<>());
    }

    // ---- match helpers ----

    private UnitOfMeasureConversion directLookup(final CommodityType commodityType, final UnitOfMeasure source,
            final UnitOfMeasure target) {
        for ( final UnitOfMeasureConversion c : data_ ) {
            if ( matches(c, commodityType, source, target) ) {
                return c;
            }
        }
        throw new LibraryException(
                "no direct conversion available from " + commodityType.code() + " " + source.code() + " to "
                        + target.code());
    }

    private UnitOfMeasureConversion smartLookup(final CommodityType commodityType, final UnitOfMeasure source,
            final UnitOfMeasure target, final List< String > forbidden) {
        try {
            return directLookup(commodityType, source, target);
        } catch ( final LibraryException e ) {
            // no direct conversion available; fall through to smart lookup.
        }
        forbidden.add(source.code());
        for ( final UnitOfMeasureConversion c : data_ ) {
            if ( matches(c, commodityType, source) ) {
                final UnitOfMeasure other = source.equals(c.source()) ? c.target() : c.source();
                if ( !forbidden.contains(other.code()) ) {
                    try {
                        final UnitOfMeasureConversion tail = smartLookup(commodityType, other, target, forbidden);
                        return UnitOfMeasureConversion.chain(c, tail);
                    } catch ( final LibraryException e ) {
                        // discard and try the next candidate
                    }
                }
            }
        }
        throw new LibraryException(
                "no conversion available for " + commodityType.code() + " from " + source.code() + " to "
                        + target.code());
    }

    private void addKnownConversionFactors() {
        add(new UnitOfMeasureConversion(new NullCommodityType(), new MBUnitOfMeasure(), new BarrelUnitOfMeasure(),
                1000));
        add(new UnitOfMeasureConversion(new NullCommodityType(), new BarrelUnitOfMeasure(), new GallonUnitOfMeasure(),
                42));
        add(new UnitOfMeasureConversion(new NullCommodityType(), new GallonUnitOfMeasure(), new MBUnitOfMeasure(),
                1000 * 42));
        add(new UnitOfMeasureConversion(new NullCommodityType(), new LitreUnitOfMeasure(), new GallonUnitOfMeasure(),
                3.78541));
        add(new UnitOfMeasureConversion(new NullCommodityType(), new BarrelUnitOfMeasure(), new LitreUnitOfMeasure(),
                158.987));
        add(new UnitOfMeasureConversion(new NullCommodityType(), new KilolitreUnitOfMeasure(),
                new BarrelUnitOfMeasure(), 6.28981));
        add(new UnitOfMeasureConversion(new NullCommodityType(), new TokyoKilolitreUnitOfMeasure(),
                new BarrelUnitOfMeasure(), 6.28981));
    }
}
