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
 * Phase 2n A.0 — bit-exact validation of the package-private
 * {@code org.jquantlib.math.transcendental.Qint64} arithmetic against the
 * CORE-MATH {@code qint64_t} reference probe at
 * {@code migration-harness/references/math/transcendental/qint64.json}.
 *
 * <p>Each probe case specifies an operation plus inputs; the expected
 * output is the bit-exact {@code (ll, lh, hl, hh, ex, sgn)} tuple, the
 * {@code y_bits} of a resulting {@code double} (for {@code toDouble}),
 * a long {@code result} (for {@code toLong}), or a comparison
 * {@code result} (for {@code cmpQint*}).
 *
 * <p>Reflection is used to reach the package-private {@code Qint64} class
 * from the test-suite package without elevating its visibility — Qint64 is
 * an implementation detail and stays out of the public API surface.
 */
public class Qint64Test {

    private final Class<?> qintClass;
    private final Constructor<?> defaultCtor;
    private final Field fLl, fLh, fHl, fHh, fEx, fSgn;
    private final Method mFromDouble, mToDouble, mToLong, mCopyFrom;
    private final Method mAddAssign, mAddAssign22;
    private final Method mMulAssign, mMulAssign11, mMulAssign21, mMulAssign22,
                         mMulAssign31, mMulAssign33, mMulAssign41, mMulAssign2;
    private final Method mCmpQint, mCmpQint22;

    public Qint64Test() {
        try {
            this.qintClass = Class.forName("org.jquantlib.math.transcendental.Qint64");
            this.defaultCtor = qintClass.getDeclaredConstructor();
            this.defaultCtor.setAccessible(true);
            this.fLl = qintClass.getDeclaredField("ll");
            this.fLh = qintClass.getDeclaredField("lh");
            this.fHl = qintClass.getDeclaredField("hl");
            this.fHh = qintClass.getDeclaredField("hh");
            this.fEx = qintClass.getDeclaredField("ex");
            this.fSgn = qintClass.getDeclaredField("sgn");
            for (Field f : new Field[]{fLl, fLh, fHl, fHh, fEx, fSgn}) f.setAccessible(true);
            this.mFromDouble = qintClass.getDeclaredMethod("fromDouble", double.class);
            this.mToDouble = qintClass.getDeclaredMethod("toDouble");
            this.mToLong = qintClass.getDeclaredMethod("toLong");
            this.mCopyFrom = qintClass.getDeclaredMethod("copyFrom", qintClass);
            this.mAddAssign = qintClass.getDeclaredMethod("addAssign", qintClass, qintClass);
            this.mAddAssign22 = qintClass.getDeclaredMethod("addAssign22", qintClass, qintClass);
            this.mMulAssign = qintClass.getDeclaredMethod("mulAssign", qintClass, qintClass);
            this.mMulAssign11 = qintClass.getDeclaredMethod("mulAssign11", qintClass, qintClass);
            this.mMulAssign21 = qintClass.getDeclaredMethod("mulAssign21", qintClass, qintClass);
            this.mMulAssign22 = qintClass.getDeclaredMethod("mulAssign22", qintClass, qintClass);
            this.mMulAssign31 = qintClass.getDeclaredMethod("mulAssign31", qintClass, qintClass);
            this.mMulAssign33 = qintClass.getDeclaredMethod("mulAssign33", qintClass, qintClass);
            this.mMulAssign41 = qintClass.getDeclaredMethod("mulAssign41", qintClass, qintClass);
            this.mMulAssign2 = qintClass.getDeclaredMethod("mulAssign2", long.class, qintClass);
            this.mCmpQint = qintClass.getDeclaredMethod("cmpQint", qintClass, qintClass);
            this.mCmpQint22 = qintClass.getDeclaredMethod("cmpQint22", qintClass, qintClass);
            for (Method m : new Method[]{mFromDouble, mToDouble, mToLong, mCopyFrom,
                    mAddAssign, mAddAssign22,
                    mMulAssign, mMulAssign11, mMulAssign21, mMulAssign22,
                    mMulAssign31, mMulAssign33, mMulAssign41, mMulAssign2,
                    mCmpQint, mCmpQint22}) m.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Qint64 reflection setup failed", e);
        }
    }

    // ----- reflection helpers -----

    private Object newQint() {
        try {
            return defaultCtor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Object newQintFromDouble(double a) {
        try {
            final Object q = newQint();
            mFromDouble.invoke(q, a);
            return q;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private long getLl(Object q) { try { return fLl.getLong(q); } catch (IllegalAccessException e) { throw new IllegalStateException(e); } }
    private long getLh(Object q) { try { return fLh.getLong(q); } catch (IllegalAccessException e) { throw new IllegalStateException(e); } }
    private long getHl(Object q) { try { return fHl.getLong(q); } catch (IllegalAccessException e) { throw new IllegalStateException(e); } }
    private long getHh(Object q) { try { return fHh.getLong(q); } catch (IllegalAccessException e) { throw new IllegalStateException(e); } }
    private long getEx(Object q) { try { return fEx.getLong(q); } catch (IllegalAccessException e) { throw new IllegalStateException(e); } }
    private long getSgn(Object q) { try { return fSgn.getLong(q); } catch (IllegalAccessException e) { throw new IllegalStateException(e); } }

    private void setFields(Object q, long ll, long lh, long hl, long hh, long ex, long sgn) {
        try {
            fLl.setLong(q, ll);
            fLh.setLong(q, lh);
            fHl.setLong(q, hl);
            fHh.setLong(q, hh);
            fEx.setLong(q, ex);
            fSgn.setLong(q, sgn);
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
    public void qint64_bitExactAgainstCoreMathProbe() {
        final ReferenceReader ref = ReferenceReader.load("math/transcendental/qint64");
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
                    case "toLong":
                        checkToLong(name, inputs, c, mismatches);
                        break;
                    case "copy":
                        checkCopy(name, inputs, c, mismatches);
                        break;
                    case "cmpQint":
                        checkCmp(name, inputs, c, mismatches, mCmpQint);
                        break;
                    case "cmpQint22":
                        checkCmp(name, inputs, c, mismatches, mCmpQint22);
                        break;
                    case "add":
                        checkBinary(name, inputs, c, mismatches, mAddAssign);
                        break;
                    case "add22":
                        checkBinary(name, inputs, c, mismatches, mAddAssign22);
                        break;
                    case "mul":
                        checkBinary(name, inputs, c, mismatches, mMulAssign);
                        break;
                    case "mul_11":
                        checkBinary(name, inputs, c, mismatches, mMulAssign11);
                        break;
                    case "mul_21":
                        checkBinary(name, inputs, c, mismatches, mMulAssign21);
                        break;
                    case "mul_22":
                        checkBinary(name, inputs, c, mismatches, mMulAssign22);
                        break;
                    case "mul_31":
                        checkBinary(name, inputs, c, mismatches, mMulAssign31);
                        break;
                    case "mul_33":
                        checkBinary(name, inputs, c, mismatches, mMulAssign33);
                        break;
                    case "mul_41":
                        checkBinary(name, inputs, c, mismatches, mMulAssign41);
                        break;
                    case "mul2":
                        checkMul2(name, inputs, c, mismatches);
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
            sb.append(mismatches.size()).append(" qint64 mismatch(es)");
            sb.append(" (showing first ").append(Math.min(15, mismatches.size())).append("):\n");
            for (int i = 0; i < Math.min(15, mismatches.size()); i++) {
                sb.append("  ").append(mismatches.get(i)).append('\n');
            }
            throw new AssertionError(sb.toString());
        }
    }

    private void checkFromDouble(String name, JSONObject inputs,
                                 ReferenceReader.Case c, List<String> mismatches)
            throws ReflectiveOperationException {
        final double a = readDouble(inputs.get("a"));
        final Object q = newQintFromDouble(a);
        verifyQintFields(name, c, q, mismatches);
    }

    private void checkToDouble(String name, JSONObject inputs,
                               ReferenceReader.Case c, List<String> mismatches)
            throws ReflectiveOperationException {
        final long ll = MathTestSupport.parseHexBits(inputs.getString("ll"));
        final long lh = MathTestSupport.parseHexBits(inputs.getString("lh"));
        final long hl = MathTestSupport.parseHexBits(inputs.getString("hl"));
        final long hh = MathTestSupport.parseHexBits(inputs.getString("hh"));
        final long ex = inputs.getLong("ex");
        final long sgn = inputs.getLong("sgn");
        final Object q = newQint();
        setFields(q, ll, lh, hl, hh, ex, sgn);
        final double y = (double) mToDouble.invoke(q);
        final long expectedBits = MathTestSupport.parseHexBits(
            ((JSONObject) c.expectedRaw()).getString("y_bits"));
        if (!MathTestSupport.bitsEqual(expectedBits, y)) {
            mismatches.add(String.format(
                "case=%s op=toDouble expected=0x%016x actual=0x%016x (%s)",
                name, expectedBits, Double.doubleToRawLongBits(y), y));
        }
    }

    private void checkToLong(String name, JSONObject inputs,
                             ReferenceReader.Case c, List<String> mismatches)
            throws ReflectiveOperationException {
        final long ll = MathTestSupport.parseHexBits(inputs.getString("ll"));
        final long lh = MathTestSupport.parseHexBits(inputs.getString("lh"));
        final long hl = MathTestSupport.parseHexBits(inputs.getString("hl"));
        final long hh = MathTestSupport.parseHexBits(inputs.getString("hh"));
        final long ex = inputs.getLong("ex");
        final long sgn = inputs.getLong("sgn");
        final Object q = newQint();
        setFields(q, ll, lh, hl, hh, ex, sgn);
        final long actual = (long) mToLong.invoke(q);
        final long expected = ((JSONObject) c.expectedRaw()).getLong("result");
        if (actual != expected) {
            mismatches.add(String.format(
                "case=%s op=toLong expected=%d actual=%d",
                name, expected, actual));
        }
    }

    private void checkCopy(String name, JSONObject inputs,
                           ReferenceReader.Case c, List<String> mismatches)
            throws ReflectiveOperationException {
        final double a = readDouble(inputs.get("a"));
        final Object qa = newQintFromDouble(a);
        final Object qr = newQint();
        mCopyFrom.invoke(qr, qa);
        verifyQintFields(name, c, qr, mismatches);
    }

    private void checkCmp(String name, JSONObject inputs,
                          ReferenceReader.Case c, List<String> mismatches,
                          Method method) throws ReflectiveOperationException {
        final double a = readDouble(inputs.get("a"));
        final double b = readDouble(inputs.get("b"));
        final Object qa = newQintFromDouble(a);
        final Object qb = newQintFromDouble(b);
        final int actual = (int) method.invoke(null, qa, qb);
        final int expected = ((JSONObject) c.expectedRaw()).getInt("result");
        // Normalise to {-1, 0, +1} for parity with the C reference.
        final int nActual = Integer.signum(actual);
        final int nExpected = Integer.signum(expected);
        if (nActual != nExpected) {
            mismatches.add(String.format(
                "case=%s op=cmp expected=%d actual=%d (raw)",
                name, expected, actual));
        }
    }

    private void checkBinary(String name, JSONObject inputs,
                             ReferenceReader.Case c, List<String> mismatches,
                             Method method) throws ReflectiveOperationException {
        final double a = readDouble(inputs.get("a"));
        final double b = readDouble(inputs.get("b"));
        final Object qa = newQintFromDouble(a);
        final Object qb = newQintFromDouble(b);
        final Object qr = newQint();
        method.invoke(qr, qa, qb);
        verifyQintFields(name, c, qr, mismatches);
    }

    private void checkMul2(String name, JSONObject inputs,
                           ReferenceReader.Case c, List<String> mismatches)
            throws ReflectiveOperationException {
        final long b = inputs.getLong("b");
        final double a = readDouble(inputs.get("a"));
        final Object qa = newQintFromDouble(a);
        final Object qr = newQint();
        mMulAssign2.invoke(qr, b, qa);
        verifyQintFields(name, c, qr, mismatches);
    }

    private void verifyQintFields(String name, ReferenceReader.Case c, Object q,
                                  List<String> mismatches) {
        final JSONObject exp = (JSONObject) c.expectedRaw();
        final long expLl = MathTestSupport.parseHexBits(exp.getString("ll"));
        final long expLh = MathTestSupport.parseHexBits(exp.getString("lh"));
        final long expHl = MathTestSupport.parseHexBits(exp.getString("hl"));
        final long expHh = MathTestSupport.parseHexBits(exp.getString("hh"));
        final long expEx = exp.getLong("ex");
        final long expSgn = exp.getLong("sgn");
        final long actLl = getLl(q), actLh = getLh(q);
        final long actHl = getHl(q), actHh = getHh(q);
        final long actEx = getEx(q), actSgn = getSgn(q);
        if (actLl != expLl || actLh != expLh || actHl != expHl || actHh != expHh
                || actEx != expEx || actSgn != expSgn) {
            mismatches.add(String.format(
                "case=%s expected{hh=0x%016x,hl=0x%016x,lh=0x%016x,ll=0x%016x,ex=%d,sgn=%d} "
                + "actual{hh=0x%016x,hl=0x%016x,lh=0x%016x,ll=0x%016x,ex=%d,sgn=%d}",
                name, expHh, expHl, expLh, expLl, expEx, expSgn,
                actHh, actHl, actLh, actLl, actEx, actSgn));
        }
    }
}
