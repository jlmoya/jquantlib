// jquantlib/src/test/java/org/jquantlib/testsuite/util/ReferenceReader.java
package org.jquantlib.testsuite.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Reads reference JSON emitted by migration-harness probes in the schema
 * defined by docs/migration/phase1-design.md §5.4. Used from JUnit tests as
 * the source of cross-validation expected values.
 *
 * <p>Resolves paths relative to the repo root's {@code migration-harness/}
 * directory by walking up from the current working directory until it finds
 * a directory that contains a {@code migration-harness} child. Maven runs
 * tests from {@code jquantlib/} so the walk is needed.
 *
 * <p>Failure modes distinguish infrastructure errors (missing file, malformed
 * JSON, wrong working directory — {@link IllegalStateException}) from
 * schema violations (reference points to a different test_group than
 * requested — {@link AssertionError}). This lets CI distinguish test logic
 * failures from harness-setup failures.
 */
public final class ReferenceReader {

    private final String testGroup;
    private final String cppVersion;
    private final String cppCommit;
    private final String generatedBy;
    private final Map<String, Case> casesByName;

    private ReferenceReader(String testGroup, String cppVersion,
                            String cppCommit, String generatedBy,
                            Map<String, Case> cases) {
        this.testGroup = testGroup;
        this.cppVersion = cppVersion;
        this.cppCommit = cppCommit;
        this.generatedBy = generatedBy;
        this.casesByName = cases;
    }

    /** Load a reference file by test-group id (e.g., "math/bisection"). */
    public static ReferenceReader load(String testGroup) {
        final Path file = harnessRoot().resolve("references").resolve(testGroup + ".json");
        final String text;
        try {
            text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read reference file: " + file, e);
        }
        final JSONObject doc;
        try {
            doc = new JSONObject(text);
        } catch (RuntimeException e) {
            throw new IllegalStateException("malformed JSON in reference file: " + file, e);
        }
        final String actualGroup = doc.getString("test_group");
        if (!Objects.equals(actualGroup, testGroup)) {
            throw new AssertionError("test_group mismatch in " + file
                    + ": expected=" + testGroup + " found=" + actualGroup);
        }
        final Map<String, Case> cases = new LinkedHashMap<>();
        final JSONArray arr = doc.getJSONArray("cases");
        for (int i = 0; i < arr.length(); i++) {
            final JSONObject c = arr.getJSONObject(i);
            final String name = c.getString("name");
            cases.put(name, new Case(
                    name,
                    c.optJSONObject("inputs"),
                    c.opt("expected")));
        }
        return new ReferenceReader(
                testGroup,
                doc.getString("cpp_version"),
                doc.getString("cpp_commit"),
                doc.getString("generated_by"),
                cases);
    }

    public String testGroup() { return testGroup; }
    public String cppVersion() { return cppVersion; }
    public String cppCommit() { return cppCommit; }
    public String generatedBy() { return generatedBy; }

    public List<String> caseNames() {
        return List.copyOf(casesByName.keySet());
    }

    public Case getCase(String name) {
        final Case c = casesByName.get(name);
        if (c == null) {
            throw new AssertionError("no case named " + name + " in group " + testGroup
                    + "; available: " + casesByName.keySet());
        }
        return c;
    }

    /** A single case in a reference file. */
    public static final class Case {
        private final String name;
        private final JSONObject inputs;
        private final Object expected;

        Case(String name, JSONObject inputs, Object expected) {
            this.name = name;
            this.inputs = inputs;
            this.expected = expected;
        }

        public String name() { return name; }

        /** Returns inputs as a JSONObject. DO NOT mutate — shared reference. */
        public JSONObject inputs() { return inputs; }

        /**
         * Returns the expected value as a double. Handles the edge case of
         * {@code expected} being a JSON string (e.g. "NaN", "Infinity", "-Infinity",
         * which nlohmann/json emits as strings because JSON lacks native
         * representations of those values).
         */
        public double expectedDouble() {
            if (expected instanceof String) {
                // nlohmann/json emits NaN, Infinity, -Infinity as JSON strings
                // because JSON has no native representation for them.
                return Double.parseDouble((String) expected);
            }
            return ((Number) expected).doubleValue();
        }

        public long expectedLong() { return ((Number) expected).longValue(); }
        public String expectedString() { return (String) expected; }
        public JSONArray expectedArray() { return (JSONArray) expected; }
        public Object expectedRaw() { return expected; }
    }

    private static Path harnessRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            final Path h = p.resolve("migration-harness");
            if (Files.isDirectory(h)) {
                return h;
            }
            p = p.getParent();
        }
        throw new IllegalStateException("could not locate migration-harness/ above cwd="
                + Paths.get("").toAbsolutePath());
    }
}
