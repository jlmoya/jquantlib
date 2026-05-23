/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.commodities;

import static org.junit.Assert.assertTrue;

import org.jquantlib.experimental.commodities.BarrelUnitOfMeasure;
import org.jquantlib.experimental.commodities.GallonUnitOfMeasure;
import org.jquantlib.experimental.commodities.KilolitreUnitOfMeasure;
import org.jquantlib.experimental.commodities.LitreUnitOfMeasure;
import org.jquantlib.experimental.commodities.MBUnitOfMeasure;
import org.jquantlib.experimental.commodities.NullCommodityType;
import org.jquantlib.experimental.commodities.Quantity;
import org.jquantlib.experimental.commodities.UnitOfMeasureConversion;
import org.jquantlib.experimental.commodities.UnitOfMeasureConversionManager;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 {@code test-suite/commodityunitofmeasure.cpp}
 * (142 LOC). Mirrors {@code BOOST_AUTO_TEST_SUITE(CommodityUnitOfMeasureTests)}
 * cases verbatim.
 *
 * <p>Each conversion uses an inline factor from the C++ test literal, then
 * compares against {@link UnitOfMeasureConversionManager#lookup} with the
 * {@link UnitOfMeasureConversion.Type#Direct} request. The reference values
 * live in {@link UnitOfMeasureConversionManager#addKnownConversionFactors()},
 * so both sides should produce identical quantities by construction.
 *
 * <p><strong>Tolerance tier</strong> — exact / bit-identical. The C++ test
 * uses {@code close(calc, actual)}, which is {@link Quantity#close(Quantity,
 * Quantity)} with the default 42-ULP closeness. The Java port mirrors that.
 */
public class CommodityUnitOfMeasureTest {

    private static final UnitOfMeasureConversion.Type DIRECT = UnitOfMeasureConversion.Type.Direct;

    /** Mirrors C++ {@code testDirect()}. */
    @Test
    public void testDirect() {
        final UnitOfMeasureConversionManager uomManager = UnitOfMeasureConversionManager.getInstance();

        // -------- MB to BBL --------
        var actual = new UnitOfMeasureConversion(
                new NullCommodityType(), new MBUnitOfMeasure(),
                new BarrelUnitOfMeasure(), 1000)
                .convert(new Quantity(new NullCommodityType(), new MBUnitOfMeasure(), 1000));
        var calc = uomManager.lookup(
                new NullCommodityType(), new BarrelUnitOfMeasure(),
                new MBUnitOfMeasure(), DIRECT)
                .convert(new Quantity(new NullCommodityType(), new MBUnitOfMeasure(), 1000));
        assertTrue("Wrong result for MB to BBL Conversion: actual=" + actual + " calc=" + calc,
                Quantity.close(calc, actual));

        // -------- BBL to Gallon --------
        actual = new UnitOfMeasureConversion(
                new NullCommodityType(), new BarrelUnitOfMeasure(),
                new GallonUnitOfMeasure(), 42)
                .convert(new Quantity(new NullCommodityType(), new GallonUnitOfMeasure(), 1000));
        calc = uomManager.lookup(
                new NullCommodityType(), new BarrelUnitOfMeasure(),
                new GallonUnitOfMeasure(), DIRECT)
                .convert(new Quantity(new NullCommodityType(), new GallonUnitOfMeasure(), 1000));
        assertTrue("Wrong result for BBL to Gallon Conversion: actual=" + actual + " calc=" + calc,
                Quantity.close(calc, actual));

        // -------- BBL to Litre --------
        actual = new UnitOfMeasureConversion(
                new NullCommodityType(), new BarrelUnitOfMeasure(),
                new LitreUnitOfMeasure(), 158.987)
                .convert(new Quantity(new NullCommodityType(), new LitreUnitOfMeasure(), 1000));
        calc = uomManager.lookup(
                new NullCommodityType(), new BarrelUnitOfMeasure(),
                new LitreUnitOfMeasure(), DIRECT)
                .convert(new Quantity(new NullCommodityType(), new LitreUnitOfMeasure(), 1000));
        assertTrue("Wrong result for BBL to Litre Conversion: actual=" + actual + " calc=" + calc,
                Quantity.close(calc, actual));

        // -------- BBL to KL --------
        actual = new UnitOfMeasureConversion(
                new NullCommodityType(), new KilolitreUnitOfMeasure(),
                new BarrelUnitOfMeasure(), 6.28981)
                .convert(new Quantity(new NullCommodityType(), new KilolitreUnitOfMeasure(), 1000));
        calc = uomManager.lookup(
                new NullCommodityType(), new BarrelUnitOfMeasure(),
                new KilolitreUnitOfMeasure(), DIRECT)
                .convert(new Quantity(new NullCommodityType(), new KilolitreUnitOfMeasure(), 1000));
        assertTrue("Wrong result for BBL to KiloLitre Conversion: actual=" + actual + " calc=" + calc,
                Quantity.close(calc, actual));

        // -------- MB to Gallon --------
        actual = new UnitOfMeasureConversion(
                new NullCommodityType(), new GallonUnitOfMeasure(),
                new MBUnitOfMeasure(), 42000)
                .convert(new Quantity(new NullCommodityType(), new MBUnitOfMeasure(), 1000));
        calc = uomManager.lookup(
                new NullCommodityType(), new GallonUnitOfMeasure(),
                new MBUnitOfMeasure(), DIRECT)
                .convert(new Quantity(new NullCommodityType(), new MBUnitOfMeasure(), 1000));
        assertTrue("Wrong result for MB to Gallon Conversion: actual=" + actual + " calc=" + calc,
                Quantity.close(calc, actual));

        // -------- Gallon to Litre --------
        actual = new UnitOfMeasureConversion(
                new NullCommodityType(), new LitreUnitOfMeasure(),
                new GallonUnitOfMeasure(), 3.78541)
                .convert(new Quantity(new NullCommodityType(), new LitreUnitOfMeasure(), 1000));
        calc = uomManager.lookup(
                new NullCommodityType(), new GallonUnitOfMeasure(),
                new LitreUnitOfMeasure(), DIRECT)
                .convert(new Quantity(new NullCommodityType(), new LitreUnitOfMeasure(), 1000));
        assertTrue("Wrong result for Gallon to Litre Conversion: actual=" + actual + " calc=" + calc,
                Quantity.close(calc, actual));
    }
}
