package org.jquantlib.testsuite.math.transcendental;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.jquantlib.testsuite.util.MathTestSupport;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.junit.Test;

/**
 * Phase 2i.5 WI-1.0 — bit-exact validation of the package-private
 * {@code org.jquantlib.math.transcendental.Dint64} arithmetic against the
 * CORE-MATH {@code dint64_t} reference probe at
 * {@code migration-harness/references/math/transcendental/dint64.json}.
 *
 * <p>Each probe case specifies an operation ({@code fromDouble} /
 * {@code toDouble} / {@code add} / {@code mul} / {@code mul21} / {@code copy} /
 * {@code cmpAbs}) plus inputs; the expected output is the bit-exact
 * {@code (lo, hi, ex, sgn)} tuple (or for {@code toDouble}, the {@code y_bits}
 * of the resulting {@code double}; for {@code cmpAbs}, the integer result).
 *
 * <p>Reflection is used to reach the package-private {@code Dint64} class
 * from the test-suite package without elevating its visibility — Dint64 is
 * an implementation detail and stays out of the public API surface.
 */
public class Dint64Test {

    private final Class<?> dintClass;
    private final Constructor<?> defaultCtor;
    private final Field fLo, fHi, fEx, fSgn;
    private final Method mFromDouble, mToDouble, mCopyFrom, mAddAssign,
                         mMulAssign, mMul21Assign;
    private final Method mCmpAbs;

    public Dint64Test() {
        try {
            this.dintClass = Class.forName("org.jquantlib.math.transcendental.Dint64");
            this.defaultCtor = dintClass.getDeclaredConstructor();
            this.defaultCtor.setAccessible(true);
            this.fLo = dintClass.getDeclaredField("lo");
            this.fHi = dintClass.getDeclaredField("hi");
            this.fEx = dintClass.getDeclaredField("ex");
            this.fSgn = dintClass.getDeclaredField("sgn");
            for (Field f : new Field[]{fLo, fHi, fEx, fSgn}) f.setAccessible(true);
            this.mFromDouble = dintClass.getDeclaredMethod("fromDouble", double.class);
            this.mToDouble = dintClass.getDeclaredMethod("toDouble");
            this.mCopyFrom = dintClass.getDeclaredMethod("copyFrom", dintClass);
            this.mAddAssign = dintClass.getDeclaredMethod("addAssign", dintClass, dintClass);
            this.mMulAssign = dintClass.getDeclaredMethod("mulAssign", dintClass, dintClass);
            this.mMul21Assign = dintClass.getDeclaredMethod("mul21Assign", dintClass, dintClass);
            this.mCmpAbs = dintClass.getDeclaredMethod("cmpAbs", dintClass, dintClass);
            for (Method m : new Method[]{mFromDouble, mToDouble, mCopyFrom,
                    mAddAssign, mMulAssign, mMul21Assign, mCmpAbs}) m.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Dint64 reflection setup failed", e);
        }
    }

    // ----- reflection helpers -----

    private Object newDint() {
        try {
            return defaultCtor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Object newDintFromDouble(double a) {
        try {
            final Object d = newDint();
            mFromDouble.invoke(d, a);
            return d;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private long getLo(Object d) { try { return fLo.getLong(d); } catch (IllegalAccessException e) { throw new IllegalStateException(e); } }
    private long getHi(Object d) { try { return fHi.getLong(d); } catch (IllegalAccessException e) { throw new IllegalStateException(e); } }
    private long getEx(Object d) { try { return fEx.getLong(d); } catch (IllegalAccessException e) { throw new IllegalStateException(e); } }
    private long getSgn(Object d) { try { return fSgn.getLong(d); } catch (IllegalAccessException e) { throw new IllegalStateException(e); } }

    private void setFields(Object d, long lo, long hi, long ex, long sgn) {
        try {
            fLo.setLong(d, lo);
            fHi.setLong(d, hi);
            fEx.setLong(d, ex);
            fSgn.setLong(d, sgn);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    // ----- helpers for parsing probe inputs -----

    private static double readDouble(Object raw) {
        if (raw instanceof String) return Double.parseDouble((String) raw);
        return ((Number) raw).doubleValue();
    }

    @Test
    public void dint64_bitExactAgainstCoreMathProbe() {
        final ReferenceReader ref = ReferenceReader.load("math/transcendental/dint64");
        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            final ReferenceReader.Case c = ref.getCase(name);
            final JSONObject inputs = c.inputs();
            final String op = inputs.getString("op");
            try {
                switch (op) {
                    case "fromDouble":
                        checkFromDouble(name, inputs, c, mismatches);
                        break;
                    case "toDouble":
                        checkToDouble(name, inputs, c, mismatches);
                        break;
                    case "add":
                        checkBinaryDintOp(name, inputs, c, mismatches, mAddAssign);
                        break;
                    case "mul":
                        checkBinaryDintOp(name, inputs, c, mismatches, mMulAssign);
                        break;
                    case "mul21":
                        checkBinaryDintOp(name, inputs, c, mismatches, mMul21Assign);
                        break;
                    case "copy":
                        checkCopy(name, inputs, c, mismatches);
                        break;
                    case "cmpAbs":
                        checkCmpAbs(name, inputs, c, mismatches);
                        break;
                    default:
                        throw new IllegalStateException("unknown op in probe case "
                            + name + ": " + op);
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("reflection failure on case " + name, e);
            }
        }

        if (!mismatches.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            sb.append(mismatches.size()).append(" dint64 mismatch(es)");
            sb.append(" (showing first ").append(Math.min(10, mismatches.size())).append("):\n");
            for (int i = 0; i < Math.min(10, mismatches.size()); i++) {
                sb.append("  ").append(mismatches.get(i)).append('\n');
            }
            throw new AssertionError(sb.toString());
        }
    }

    private void checkFromDouble(String name, JSONObject inputs,
                                 ReferenceReader.Case c, List<String> mismatches)
            throws ReflectiveOperationException {
        final double a = readDouble(inputs.get("a"));
        final Object d = newDintFromDouble(a);
        verifyDintFields(name, c, d, mismatches);
    }

    private void checkToDouble(String name, JSONObject inputs,
                               ReferenceReader.Case c, List<String> mismatches)
            throws ReflectiveOperationException {
        final long lo = MathTestSupport.parseHexBits(inputs.getString("lo"));
        final long hi = MathTestSupport.parseHexBits(inputs.getString("hi"));
        final long ex = inputs.getLong("ex");
        final long sgn = inputs.getLong("sgn");
        final Object d = newDint();
        setFields(d, lo, hi, ex, sgn);
        final double y = (double) mToDouble.invoke(d);
        final long expectedBits = MathTestSupport.parseHexBits(
            ((JSONObject) c.expectedRaw()).getString("y_bits"));
        if (!MathTestSupport.bitsEqual(expectedBits, y)) {
            mismatches.add(String.format(
                "case=%s op=toDouble expected=0x%016x actual=0x%016x (%s)",
                name, expectedBits, Double.doubleToRawLongBits(y), y));
        }
    }

    private void checkBinaryDintOp(String name, JSONObject inputs,
                                   ReferenceReader.Case c, List<String> mismatches,
                                   Method method) throws ReflectiveOperationException {
        final double a = readDouble(inputs.get("a"));
        final double b = readDouble(inputs.get("b"));
        final Object da = newDintFromDouble(a);
        final Object db = newDintFromDouble(b);
        final Object dr = newDint();
        method.invoke(dr, da, db);
        verifyDintFields(name, c, dr, mismatches);
    }

    private void checkCopy(String name, JSONObject inputs,
                           ReferenceReader.Case c, List<String> mismatches)
            throws ReflectiveOperationException {
        final double a = readDouble(inputs.get("a"));
        final Object da = newDintFromDouble(a);
        final Object dr = newDint();
        mCopyFrom.invoke(dr, da);
        verifyDintFields(name, c, dr, mismatches);
    }

    private void checkCmpAbs(String name, JSONObject inputs,
                             ReferenceReader.Case c, List<String> mismatches)
            throws ReflectiveOperationException {
        final double a = readDouble(inputs.get("a"));
        final double b = readDouble(inputs.get("b"));
        final Object da = newDintFromDouble(a);
        final Object db = newDintFromDouble(b);
        final int actual = (int) mCmpAbs.invoke(null, da, db);
        final int expected = ((JSONObject) c.expectedRaw()).getInt("result");
        // Normalise to {-1, 0, +1} for parity with the C reference's
        // signed-char result.
        final int nActual = Integer.signum(actual);
        final int nExpected = Integer.signum(expected);
        if (nActual != nExpected) {
            mismatches.add(String.format(
                "case=%s op=cmpAbs expected=%d actual=%d (raw)",
                name, expected, actual));
        }
    }

    private void verifyDintFields(String name, ReferenceReader.Case c, Object d,
                                  List<String> mismatches) {
        final JSONObject exp = (JSONObject) c.expectedRaw();
        final long expLo = MathTestSupport.parseHexBits(exp.getString("lo"));
        final long expHi = MathTestSupport.parseHexBits(exp.getString("hi"));
        final long expEx = exp.getLong("ex");
        final long expSgn = exp.getLong("sgn");
        final long actLo = getLo(d), actHi = getHi(d);
        final long actEx = getEx(d), actSgn = getSgn(d);
        if (actLo != expLo || actHi != expHi || actEx != expEx || actSgn != expSgn) {
            mismatches.add(String.format(
                "case=%s expected{lo=0x%016x,hi=0x%016x,ex=%d,sgn=%d} "
                + "actual{lo=0x%016x,hi=0x%016x,ex=%d,sgn=%d}",
                name, expLo, expHi, expEx, expSgn,
                actLo, actHi, actEx, actSgn));
        }
    }
}
