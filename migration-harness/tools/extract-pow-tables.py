#!/usr/bin/env python3
"""Extract pow.h / pow_dint.h / qint.h table arrays into Java initializers.

Phase 2n A.1.b helper. Parses CORE-MATH C array literals and emits Java
`static final` declarations and per-element bit assignments suitable for
inclusion in PowKernel.java.

Tables emitted (in order):
  pow.h:
    _INVERSE [182]  (double)
    _LOG_INV [182][2]  (double pair)
    T1 [64][2]
    T2 [64][2]
    P_1 [6]
    Q_1 [5]
  pow_dint.h:
    _INVERSE_2_1, _INVERSE_2_2, _LOG_INV_2_1, _LOG_INV_2_2, T1_2, T2_2, P_2, Q_2
    plus dint64_t scalar constants ONE, M_ONE, LOG2, LOG2_INV, ZERO
  qint.h:
    _INVERSE_3_1, _INVERSE_3_2, _LOG_INV_3_1, _LOG_INV_3_2, T1_3, T2_3, P_3, Q_3
    plus qint64_t scalars ONE_Q, M_ONE_Q, LOG2_Q, LOG2_INV_Q, ZERO_Q

The script writes its output to stdout as Java fragments. Manually paste
the relevant fragments into PowKernel.java (or split across files if
needed). Re-run after CORE-MATH upstream updates.
"""

import re
import sys
import os
import struct

ROOT = os.path.dirname(os.path.abspath(__file__))
COREMATH = os.path.join(
    ROOT, "..", "cpp", "probes", "transcendental", "coremath"
)


def hexfloat_to_bits(s):
    """Convert a C hex-float literal like '0x1.62e42fefa39efp-1' (or '0' or
    '-0x1.5p+0') to its IEEE-754 raw long bits.

    Empirically Java's Double.parseDouble accepts hex-float strings, so we
    could let Java do the parsing — but we want stable bit patterns at
    extraction time rather than depending on Java float parsing across JVMs.
    Use Python's float.fromhex for simple hex floats."""
    s = s.strip()
    if s in ("0", "-0", "+0"):
        # Java's longBitsToDouble(0L) is +0.0; -0 is the sign bit set.
        return 0 if not s.startswith("-") else (1 << 63)
    # Python's float.fromhex doesn't like a leading + sign in some versions.
    if s.startswith("+"):
        s = s[1:]
    f = float.fromhex(s)
    return struct.unpack("<Q", struct.pack("<d", f))[0]


def parse_double_array_1d(text, start_idx):
    """Parse a `{ a, b, c, ... }` block starting at `start_idx` (offset of
    the opening brace in `text`). Returns (list_of_strings, end_idx).
    Each string is a single C-literal token (hex-float)."""
    assert text[start_idx] == "{"
    depth = 1
    i = start_idx + 1
    items = []
    cur = []
    while i < len(text) and depth > 0:
        c = text[i]
        if c == "{":
            depth += 1
            cur.append(c)
        elif c == "}":
            depth -= 1
            if depth == 0:
                tok = "".join(cur).strip()
                if tok:
                    items.append(tok)
                return items, i + 1
            cur.append(c)
        elif c == "," and depth == 1:
            tok = "".join(cur).strip()
            if tok:
                items.append(tok)
            cur = []
        elif c == "/" and i + 1 < len(text) and text[i + 1] == "*":
            # skip block comment
            j = text.find("*/", i + 2)
            if j == -1:
                raise ValueError("unterminated /* */")
            i = j + 1  # +1 for the loop's increment
        elif c == "/" and i + 1 < len(text) and text[i + 1] == "/":
            # skip line comment
            j = text.find("\n", i + 2)
            if j == -1:
                j = len(text)
            i = j  # newline becomes c in next iter
        else:
            cur.append(c)
        i += 1
    raise ValueError("unterminated brace block")


def find_static_const(text, kind, name):
    """Return (decl_match, post_idx) where decl_match is the re.Match for
    `static const <kind>* <name>[]\s*=\s*{` and post_idx points to the
    opening brace `{`. kind may be 'double', 'dint64_t', 'qint64_t'."""
    pat = re.compile(
        r"static\s+const\s+" + re.escape(kind)
        + r"\s+" + re.escape(name)
        + r"\s*(?:\[[^\]]*\])?(?:\s*\[[^\]]*\])?\s*=\s*"
    )
    m = pat.search(text)
    if not m:
        return None, None
    j = m.end()
    while j < len(text) and text[j].isspace():
        j += 1
    if text[j] != "{":
        return None, None
    return m, j


def emit_double_array_1d(out, name, items):
    out.append(f"    private static final double[] {name} = new double[" + str(len(items)) + "];")
    out.append("    static {")
    out.append(f"        long[] bits = {{")
    chunks = []
    for i, it in enumerate(items):
        bits = hexfloat_to_bits(it)
        chunks.append(f"0x{bits:016x}L")
    # 4 per line
    for i in range(0, len(chunks), 4):
        out.append("            " + ", ".join(chunks[i:i + 4]) + ",")
    out.append("        };")
    out.append(f"        for (int i = 0; i < bits.length; i++) {name}[i] = Double.longBitsToDouble(bits[i]);")
    out.append("    }")


def emit_double_array_2d_pair(out, prefix, items):
    """Each item is a brace-pair like '{0x1.69p+0, 0x...p-44}'. Emit two
    parallel arrays: <prefix>_H and <prefix>_L."""
    inner = []
    for it in items:
        # strip outer braces
        s = it.strip()
        if s.startswith("{") and s.endswith("}"):
            s = s[1:-1]
        parts = [p.strip() for p in s.split(",")]
        if len(parts) != 2:
            raise ValueError("expected exactly 2 elements in pair: " + it)
        inner.append((parts[0], parts[1]))
    out.append(f"    private static final double[] {prefix}_H = new double[" + str(len(inner)) + "];")
    out.append(f"    private static final double[] {prefix}_L = new double[" + str(len(inner)) + "];")
    out.append("    static {")
    out.append("        long[] hi = {")
    hi_bits = [f"0x{hexfloat_to_bits(p[0]):016x}L" for p in inner]
    for i in range(0, len(hi_bits), 4):
        out.append("            " + ", ".join(hi_bits[i:i + 4]) + ",")
    out.append("        };")
    out.append("        long[] lo = {")
    lo_bits = [f"0x{hexfloat_to_bits(p[1]):016x}L" for p in inner]
    for i in range(0, len(lo_bits), 4):
        out.append("            " + ", ".join(lo_bits[i:i + 4]) + ",")
    out.append("        };")
    out.append("        for (int i = 0; i < hi.length; i++) {")
    out.append(f"            {prefix}_H[i] = Double.longBitsToDouble(hi[i]);")
    out.append(f"            {prefix}_L[i] = Double.longBitsToDouble(lo[i]);")
    out.append("        }")
    out.append("    }")


# ---------------------------------------------------------------------------
# dint64_t parsing
# ---------------------------------------------------------------------------

def parse_dint_struct(s):
    """Parse a single dint64 initializer like '{.hi=0x...,.lo=0x...,.ex=N,.sgn=0xN}'.
    Returns (hi, lo, ex, sgn) as ints. Handles negative ex."""
    s = s.strip()
    if s.startswith("{") and s.endswith("}"):
        s = s[1:-1]
    fields = {}
    # Split by commas at top level
    parts = []
    cur = []
    depth = 0
    for c in s:
        if c == "{":
            depth += 1
            cur.append(c)
        elif c == "}":
            depth -= 1
            cur.append(c)
        elif c == "," and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(c)
    if cur:
        parts.append("".join(cur))
    for p in parts:
        m = re.match(r"\s*\.(\w+)\s*=\s*(.+?)\s*$", p, re.DOTALL)
        if not m:
            raise ValueError("can't parse dint field: " + repr(p))
        key = m.group(1)
        val = m.group(2).strip()
        # Strip trailing comma if any
        if val.endswith(","):
            val = val[:-1].strip()
        fields[key] = val
    return (
        parse_int_literal(fields["hi"]),
        parse_int_literal(fields["lo"]),
        parse_int_literal(fields["ex"]),
        parse_int_literal(fields["sgn"]),
    )


def parse_int_literal(s):
    """Parse a C integer literal: supports 0xHEX, decimal, with optional
    sign, optional ull/ll suffix."""
    s = s.strip()
    sign = 1
    if s.startswith("-"):
        sign = -1
        s = s[1:].strip()
    elif s.startswith("+"):
        s = s[1:].strip()
    # Strip suffix
    s = re.sub(r"[uUlL]+$", "", s)
    if s.startswith("0x") or s.startswith("0X"):
        return sign * int(s, 16)
    return sign * int(s)


def emit_dint_array(out, name, items):
    """Emit four parallel long[] arrays — name_HI, name_LO, name_EX, name_SGN."""
    parsed = [parse_dint_struct(it) for it in items]
    n = len(parsed)
    out.append(f"    private static final long[] {name}_HI = new long[" + str(n) + "];")
    out.append(f"    private static final long[] {name}_LO = new long[" + str(n) + "];")
    out.append(f"    private static final long[] {name}_EX = new long[" + str(n) + "];")
    out.append(f"    private static final long[] {name}_SGN = new long[" + str(n) + "];")
    out.append("    static {")
    out.append("        long[][] data = {")
    for hi, lo, ex, sgn in parsed:
        # Java doesn't allow 0x8000... as a literal positive long; use casts.
        out.append(
            f"            {{ {hex_long(hi)}, {hex_long(lo)}, {ex}L, {hex_long(sgn)} }},"
        )
    out.append("        };")
    out.append("        for (int i = 0; i < data.length; i++) {")
    out.append(f"            {name}_HI[i]  = data[i][0];")
    out.append(f"            {name}_LO[i]  = data[i][1];")
    out.append(f"            {name}_EX[i]  = data[i][2];")
    out.append(f"            {name}_SGN[i] = data[i][3];")
    out.append("        }")
    out.append("    }")


def hex_long(v):
    """Return a Java long-literal string for an int v (which may be
    interpreted as unsigned 64-bit)."""
    if v < 0:
        # Sign-extend, Java accepts signed-style decimal long literals
        return str(v) + "L"
    if v == 0:
        return "0L"
    # For values <= 0x7fffffffffffffff, Java accepts 0x... directly.
    # For values >= 0x8000000000000000, must wrap to signed: use cast pattern.
    if v >= 0x8000000000000000:
        # Java: 0x8000000000000000L → unrepresentable as positive literal,
        # but accepts as signed: just emit the hex form of the signed
        # interpretation.
        # Equivalent signed value:
        signed = v - (1 << 64)
        # Java 7+: long hex literals with the high bit are accepted as-is
        # via hex (e.g. 0x8000000000000000L). Verify: actually Java DOES
        # accept 0x8000000000000000L as a long literal even though it
        # represents Long.MIN_VALUE.
        return f"0x{v:016x}L"
    return f"0x{v:x}L"


# ---------------------------------------------------------------------------
# qint64_t parsing  (six fields: hh, hl, lh, ll, ex, sgn)
# ---------------------------------------------------------------------------

def parse_qint_struct(s):
    s = s.strip()
    if s.startswith("{") and s.endswith("}"):
        s = s[1:-1]
    fields = {}
    parts = []
    cur = []
    depth = 0
    for c in s:
        if c == "{":
            depth += 1
            cur.append(c)
        elif c == "}":
            depth -= 1
            cur.append(c)
        elif c == "," and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(c)
    if cur:
        parts.append("".join(cur))
    for p in parts:
        m = re.match(r"\s*\.(\w+)\s*=\s*(.+?)\s*$", p, re.DOTALL)
        if not m:
            raise ValueError("can't parse qint field: " + repr(p))
        fields[m.group(1)] = m.group(2).strip().rstrip(",").strip()
    # CORE-MATH stores qint64_t with hh, hl, lh, ll, ex, sgn fields.
    # Some struct entries omit zero fields — default missing to 0.
    return (
        parse_int_literal(fields.get("hh", "0")),
        parse_int_literal(fields.get("hl", "0")),
        parse_int_literal(fields.get("lh", "0")),
        parse_int_literal(fields.get("ll", "0")),
        parse_int_literal(fields["ex"]),
        parse_int_literal(fields["sgn"]),
    )


def emit_qint_array(out, name, items):
    parsed = [parse_qint_struct(it) for it in items]
    n = len(parsed)
    for suffix in ("HH", "HL", "LH", "LL", "EX", "SGN"):
        out.append(f"    private static final long[] {name}_{suffix} = new long[" + str(n) + "];")
    out.append("    static {")
    out.append("        long[][] data = {")
    for hh, hl, lh, ll, ex, sgn in parsed:
        out.append(
            f"            {{ {hex_long(hh)}, {hex_long(hl)}, {hex_long(lh)}, {hex_long(ll)}, {ex}L, {hex_long(sgn)} }},"
        )
    out.append("        };")
    out.append("        for (int i = 0; i < data.length; i++) {")
    out.append(f"            {name}_HH[i]  = data[i][0];")
    out.append(f"            {name}_HL[i]  = data[i][1];")
    out.append(f"            {name}_LH[i]  = data[i][2];")
    out.append(f"            {name}_LL[i]  = data[i][3];")
    out.append(f"            {name}_EX[i]  = data[i][4];")
    out.append(f"            {name}_SGN[i] = data[i][5];")
    out.append("        }")
    out.append("    }")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    out = []
    pow_h = open(os.path.join(COREMATH, "pow.h")).read()
    pow_dint_h = open(os.path.join(COREMATH, "pow_dint.h")).read()
    qint_h = open(os.path.join(COREMATH, "qint.h")).read()

    # ---- pow.h ----
    for name in ("_INVERSE",):
        m, brace = find_static_const(pow_h, "double", name)
        if not m:
            raise ValueError("not found: " + name)
        items, _ = parse_double_array_1d(pow_h, brace)
        out.append(f"\n    // ===== pow.h: {name}[{len(items)}] =====")
        emit_double_array_1d(out, "_" + name.lstrip("_"), items)

    for name in ("_LOG_INV", "T1", "T2"):
        m, brace = find_static_const(pow_h, "double", name)
        if not m:
            raise ValueError("not found: " + name)
        items, _ = parse_double_array_1d(pow_h, brace)
        out.append(f"\n    // ===== pow.h: {name}[{len(items)}][2] =====")
        java_name = name if not name.startswith("_") else name[1:]
        emit_double_array_2d_pair(out, "_" + java_name.lstrip("_"), items)

    for name in ("P_1", "Q_1"):
        m, brace = find_static_const(pow_h, "double", name)
        if not m:
            raise ValueError("not found: " + name)
        items, _ = parse_double_array_1d(pow_h, brace)
        out.append(f"\n    // ===== pow.h: {name}[{len(items)}] =====")
        emit_double_array_1d(out, name, items)

    # ---- pow_dint.h scalars ----
    out.append("\n    // ===== pow_dint.h: dint64_t scalar constants =====")
    for name in ("ONE", "M_ONE", "LOG2", "LOG2_INV", "ZERO"):
        # Find as dint64_t
        m, brace = find_static_const(pow_dint_h, "dint64_t", name)
        if not m:
            raise ValueError("not found: " + name)
        # Parse single dint struct
        items, _ = parse_double_array_1d(pow_dint_h, brace)
        # parse_double_array_1d treats this as a block of comma-separated
        # tokens (the inner .field=val). Reassemble.
        single = "{" + ",".join(items) + "}"
        hi, lo, ex, sgn = parse_dint_struct(single)
        suffix = "_D" if name in ("ONE", "M_ONE", "ZERO") else ""
        # Stay close to the C name
        java_name = name + suffix
        out.append(f"    private static final long {java_name}_HI  = {hex_long(hi)};")
        out.append(f"    private static final long {java_name}_LO  = {hex_long(lo)};")
        out.append(f"    private static final long {java_name}_EX  = {ex}L;")
        out.append(f"    private static final long {java_name}_SGN = {hex_long(sgn)};")

    # ---- pow_dint.h arrays ----
    for name in (
        "_INVERSE_2_1", "_INVERSE_2_2",
        "_LOG_INV_2_1", "_LOG_INV_2_2",
        "T1_2", "T2_2", "P_2", "Q_2",
    ):
        m, brace = find_static_const(pow_dint_h, "dint64_t", name)
        if not m:
            raise ValueError("not found: " + name)
        items, _ = parse_double_array_1d(pow_dint_h, brace)
        out.append(f"\n    // ===== pow_dint.h: {name}[{len(items)}] =====")
        java_name = name.lstrip("_")
        emit_dint_array(out, "_" + java_name, items)

    # ---- qint.h scalars ----
    out.append("\n    // ===== qint.h: qint64_t scalar constants =====")
    for name in ("ONE_Q", "M_ONE_Q", "LOG2_Q", "LOG2_INV_Q", "ZERO_Q"):
        m, brace = find_static_const(qint_h, "qint64_t", name)
        if not m:
            raise ValueError("not found: " + name)
        items, _ = parse_double_array_1d(qint_h, brace)
        single = "{" + ",".join(items) + "}"
        hh, hl, lh, ll, ex, sgn = parse_qint_struct(single)
        out.append(f"    private static final long {name}_HH  = {hex_long(hh)};")
        out.append(f"    private static final long {name}_HL  = {hex_long(hl)};")
        out.append(f"    private static final long {name}_LH  = {hex_long(lh)};")
        out.append(f"    private static final long {name}_LL  = {hex_long(ll)};")
        out.append(f"    private static final long {name}_EX  = {ex}L;")
        out.append(f"    private static final long {name}_SGN = {hex_long(sgn)};")

    # ---- qint.h arrays ----
    for name in (
        "_INVERSE_3_1", "_INVERSE_3_2",
        "_LOG_INV_3_1", "_LOG_INV_3_2",
        "T1_3", "T2_3", "P_3", "Q_3",
    ):
        m, brace = find_static_const(qint_h, "qint64_t", name)
        if not m:
            raise ValueError("not found: " + name)
        items, _ = parse_double_array_1d(qint_h, brace)
        out.append(f"\n    // ===== qint.h: {name}[{len(items)}] =====")
        java_name = name.lstrip("_")
        emit_qint_array(out, "_" + java_name, items)

    sys.stdout.write("\n".join(out) + "\n")


if __name__ == "__main__":
    main()
