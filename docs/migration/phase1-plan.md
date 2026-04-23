# Phase 1 Implementation Plan — JQuantLib Migration

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the migration infrastructure (C++ reference harness, Java test utilities, stub scanner), run the Observable/Handle infrastructure audit, then resolve every stub in the existing 61 `org.jquantlib.*` packages in dependency order — each stub verified by a Java test whose expected values come from running QuantLib v1.42.1 on identical inputs.

**Architecture:** Bottom-up dependency-first resolution. C++ QuantLib v1.42.1 is ground truth; Java updates to match on any divergence. Per-stub TDD red-green-refactor, plus cross-validation against a pinned live C++ run. Per-layer fast-forward merges to `main`; no PRs; direct push. See `docs/migration/phase1-design.md` for the binding design; §12 lists every operational decision.

**Tech Stack:** Java 11 / Maven / JUnit 4 (existing); C++17 / CMake 3.18+ / Boost / QuantLib v1.42.1 pinned via submodule (new); Python 3 for the stub scanner; nlohmann/json single-header for JSON I/O on the C++ side.

---

## Overview

The plan has five phases, three of which are fully enumerated as concrete bite-sized tasks and two of which are repeating procedures applied to each stub or layer from the scanner's output.

| Phase | Kind | Commits | Notes |
|-------|------|---------|-------|
| 0. Pre-flight | Verification (no commits) | 0 | Gates whether we can proceed |
| 1. Bootstrap | Concrete tasks | 6 | Harness, scanner, Java test utils |
| 2. Observable/Handle audit | Concrete tasks | 1–5 | First Layer 0 task; foundation for all pricing engines |
| 3. Per-stub procedure | Repeating template | hundreds | Applied in worklist order to every remaining stub |
| 4. Per-layer checkpoint | Repeating ritual | 1 merge per layer | Runs when a layer completes |
| 5. Phase 1 completion | Concrete tasks | 1 | Verify done-criteria; write completion report |

After Phase 1 is done, the whole `migration/phase1-finish-stubs` branch has been fully merged to `main`, `docs/migration/stub-inventory.json` is `[]`, and Phase 2 planning can begin.

**Non-goals reminder (from design §2.2):** no new top-level packages, no API preservation (C++ wins), no performance work, no dependency upgrades, no opportunistic refactoring, no CI, no pre-commit hooks.

---

## Phase 0 — Pre-flight checks (no commits)

Verify the local environment before any bootstrap work. If any fail, fix and re-verify before proceeding.

### Task 0.1: Verify Boost installed

- [ ] **Step 1:** Run and capture output.

```bash
brew list boost 2>&1 | head -5 && brew info boost | head -3
```

Expected: `boost` listed under brew. Note the version number for the harness README.

- [ ] **Step 2:** If Boost is missing, install it.

```bash
brew install boost
```

### Task 0.2: Verify CMake

- [ ] **Step 1:** Run.

```bash
cmake --version
```

Expected: version ≥ 3.18. If missing or older: `brew install cmake` or `brew upgrade cmake`.

### Task 0.3: Verify baseline `mvn test` is green

We need a known-green baseline so any regression during Phase 1 is unambiguously attributable to our changes.

- [ ] **Step 1:** Run.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
(cd jquantlib && mvn test) 2>&1 | tail -40
```

Expected: `BUILD SUCCESS`, tests pass. Note the count of tests run.

- [ ] **Step 2:** If there are pre-existing test failures, list them.

Write them to `/tmp/phase1-baseline-failures.txt`. These are a pre-existing concern that blocks the baseline assumption; if any exist, pause and flag to the user before continuing. Tests that fail here are not our regression; they are the starting state that we must either fix or carve out before starting stub work.

### Task 0.4: De-risk the C++ build

Before committing a submodule and a CMake file, prove locally that a QL build + probe link works.

- [ ] **Step 1:** Clone QuantLib at v1.42.1 in a throwaway dir.

```bash
mkdir -p /tmp/ql-smoke && cd /tmp/ql-smoke
git clone --depth=1 --branch v1.42.1 https://github.com/lballabio/QuantLib.git ql
cd ql
```

- [ ] **Step 2:** Build QL with CMake.

```bash
mkdir build && cd build
cmake -DCMAKE_BUILD_TYPE=Release -DQL_BUILD_EXAMPLES=OFF -DQL_BUILD_TEST_SUITE=OFF -DQL_BUILD_BENCHMARK=OFF ..
cmake --build . -j
```

Expected: successful build in ~5–10 min.

- [ ] **Step 3:** Verify the library was produced.

```bash
find . -name 'libQuantLib.*' 2>/dev/null | head -3
```

Expected: at least one `.a` or `.dylib`.

- [ ] **Step 4:** Write and compile a trivial probe to confirm linking works.

```bash
cat > /tmp/ql-smoke/probe.cpp <<'EOF'
#include <ql/version.hpp>
#include <iostream>
int main() { std::cout << "QuantLib " << QL_VERSION << std::endl; return 0; }
EOF
cd /tmp/ql-smoke
c++ -std=c++17 -I./ql -I./ql/build probe.cpp ./ql/build/ql/libQuantLib.a -o probe_test
./probe_test
```

Expected output: `QuantLib 1.42.1`.

- [ ] **Step 5:** Clean up.

```bash
rm -rf /tmp/ql-smoke
```

### Task 0.5: Rough stub count sanity-check

Before writing the proper scanner, confirm the 300–600 estimate isn't wildly off.

- [ ] **Step 1:** Run a grep-based rough count.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib/jquantlib/src/main/java
echo -n "Work in progress: " && grep -r 'UnsupportedOperationException("Work in progress")' --include='*.java' | wc -l
echo -n "not implemented: " && grep -rE 'LibraryException\("(not implemented|not yet implemented)"\)' --include='*.java' | wc -l
echo -n "TODO: code review :: please verify: " && grep -r 'TODO: code review :: please verify' --include='*.java' | wc -l
```

Expected: first two counts sum to ~200, third to ~many dozens. If any single count is >1000, trigger A1 — pause and report to the user before continuing bootstrap.

---

## Phase 1 — Bootstrap (6 commits)

These six tasks produce commits #2 through #7 of the Phase 1 opening sequence (design §6.9 — commit #1 "Phase 1 design doc" is already landed as `147614f`; the CLAUDE.md at `af269d1` is an unenumerated docs commit and is fine).

### Task 1: Harness scaffold + QuantLib submodule pinned to v1.42.1

**Files:**
- Create: `migration-harness/README.md`
- Create: `migration-harness/setup.sh`
- Create: `migration-harness/generate-references.sh`
- Create: `migration-harness/verify-harness.sh`
- Create: `migration-harness/references/.gitkeep`
- Create: `.gitignore` changes (append)
- Create: `.gitmodules` (via `git submodule add`)
- Create: `migration-harness/cpp/quantlib/` (submodule)

- [ ] **Step 1: Create directory structure.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
mkdir -p migration-harness/cpp/probes migration-harness/cpp/third_party/nlohmann migration-harness/references
touch migration-harness/references/.gitkeep
```

- [ ] **Step 2: Add QuantLib as a git submodule pinned to v1.42.1.**

```bash
git submodule add https://github.com/lballabio/QuantLib.git migration-harness/cpp/quantlib
cd migration-harness/cpp/quantlib
git checkout v1.42.1
cd ../../..
git submodule status
```

Expected: submodule entry shows `v1.42.1` with SHA `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`.

- [ ] **Step 3: Write `migration-harness/README.md`.**

```markdown
# Migration Harness

Cross-validation infrastructure for the JQuantLib C++ → Java migration.
Pins QuantLib C++ to **v1.42.1** as the reference implementation; Java tests
assert against values generated by probes that exercise the pinned C++.

## Structure

- `cpp/quantlib/` — git submodule, pinned to `v1.42.1`. Do not modify.
- `cpp/probes/` — C++17 programs that exercise QuantLib and emit JSON refs.
- `cpp/third_party/nlohmann/json.hpp` — single-header JSON library.
- `cpp/CMakeLists.txt` — builds QuantLib and all probes.
- `references/` — generated reference JSON files, committed to the repo.

## Prerequisites

- Boost (`brew install boost` on macOS — tested with 1.82+)
- CMake ≥ 3.18
- A C++17 compiler

## Commands

- `./setup.sh` — one-time: init submodule, build QuantLib, build all probes.
- `./generate-references.sh [probe_name]` — run one probe (or all) and emit
  its reference JSON to `references/`.
- `./verify-harness.sh` — regenerate a known probe, diff against committed
  reference; non-zero exit indicates non-determinism in a probe.

## Design reference

See `docs/migration/phase1-design.md` §5 for the contract this harness honors.
```

- [ ] **Step 4: Write `migration-harness/setup.sh`.**

```bash
#!/usr/bin/env bash
# One-time setup: init submodule, build QuantLib, build all probes.
# Idempotent — safe to re-run.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== 1/3 ensuring QuantLib submodule is at v1.42.1 ==="
git submodule update --init --recursive
(cd cpp/quantlib && git fetch --tags --depth=1 origin v1.42.1 && git checkout v1.42.1)

echo "=== 2/3 configuring and building QuantLib + probes ==="
cmake -S cpp -B cpp/build \
  -DCMAKE_BUILD_TYPE=Release \
  -DQL_BUILD_EXAMPLES=OFF \
  -DQL_BUILD_TEST_SUITE=OFF \
  -DQL_BUILD_BENCHMARK=OFF
cmake --build cpp/build -j

echo "=== 3/3 setup complete ==="
echo "Run ./generate-references.sh to produce reference JSONs."
```

Make it executable:

```bash
chmod +x migration-harness/setup.sh
```

- [ ] **Step 5: Write `migration-harness/generate-references.sh`.**

```bash
#!/usr/bin/env bash
# Build probes (incrementally) and run one or all of them, emitting JSON to references/.
# Usage: ./generate-references.sh [probe_name]   (no arg = run all)

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ ! -d cpp/build ]]; then
  echo "cpp/build missing; run ./setup.sh first" >&2
  exit 1
fi

cmake --build cpp/build -j

if [[ $# -eq 0 ]]; then
  # Run every probe binary under cpp/build/probes/
  for probe in cpp/build/probes/*_probe; do
    [[ -x "$probe" ]] || continue
    echo "=== running $(basename "$probe") ==="
    "$probe"
  done
else
  probe="cpp/build/probes/$1"
  [[ -x "$probe" ]] || { echo "no such probe: $1" >&2; exit 1; }
  echo "=== running $1 ==="
  "$probe"
fi

echo "=== generation complete; see references/ for output ==="
```

Make it executable: `chmod +x migration-harness/generate-references.sh`.

- [ ] **Step 6: Write `migration-harness/verify-harness.sh`.**

```bash
#!/usr/bin/env bash
# Regenerate all references in a temp dir; diff against committed.
# Non-zero exit = a probe is non-deterministic or references are stale.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Save current references
cp -R references "$tmp/committed"

# Regenerate in place
./generate-references.sh

# Diff — ignore generated_at timestamp field
diff_output=$(diff -r "$tmp/committed" references || true)
# Strip lines that differ only because of timestamps
filtered=$(echo "$diff_output" | grep -vE '"generated_at"' || true)

if [[ -n "$filtered" ]]; then
  echo "=== FAIL: references drifted beyond timestamp ==="
  echo "$filtered" | head -40
  # Restore committed references so the working tree isn't dirty
  cp -Rf "$tmp/committed"/* references/
  exit 1
fi

# Restore the timestamp-only-changed files too (no real drift)
cp -Rf "$tmp/committed"/* references/
echo "=== OK: harness deterministic, references match ==="
```

Make it executable: `chmod +x migration-harness/verify-harness.sh`.

- [ ] **Step 7: Append to `.gitignore` to exclude harness build artifacts.**

Append these lines to the existing `.gitignore`:

```
# Migration harness C++ build artifacts
migration-harness/cpp/build/
migration-harness/cpp/build-*/
```

- [ ] **Step 8: Verify working tree is sane.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git status --short
```

Expected: new files/dirs under `migration-harness/`, `.gitmodules`, `.gitignore` modified, no stray files.

- [ ] **Step 9: Commit.**

```bash
git add migration-harness/ .gitmodules .gitignore
git -c commit.gpgsign=false commit -s -m "infra(harness): add migration-harness scaffold with QuantLib submodule at v1.42.1" -m "Scaffolds migration-harness/ with setup/generate/verify scripts and pins
QuantLib C++ to v1.42.1 (SHA 099987f0ca2c11c505dc4348cdb9ce01a598e1e5) as a
submodule. No probes yet — those land in a subsequent commit. Implements
design §5.1-5.2."
git log -1 --stat | head -20
```

---

### Task 2: CMake build for probes + nlohmann/json

**Files:**
- Create: `migration-harness/cpp/CMakeLists.txt`
- Create: `migration-harness/cpp/third_party/nlohmann/json.hpp` (downloaded)

- [ ] **Step 1: Download nlohmann/json single-header to `cpp/third_party/nlohmann/json.hpp`.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
curl -L -o migration-harness/cpp/third_party/nlohmann/json.hpp \
  https://github.com/nlohmann/json/releases/download/v3.11.3/json.hpp
head -5 migration-harness/cpp/third_party/nlohmann/json.hpp
wc -l migration-harness/cpp/third_party/nlohmann/json.hpp
```

Expected: First 5 lines include `/* JSON for Modern C++ version 3.11.3 */` or similar banner. Line count ~24000.

- [ ] **Step 2: Write `migration-harness/cpp/CMakeLists.txt`.**

```cmake
cmake_minimum_required(VERSION 3.18)
project(jquantlib-migration-harness CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# --- QuantLib (as a subdirectory submodule) ---
# Disable QL's own examples/tests/benchmarks to keep our build lean.
set(QL_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(QL_BUILD_TEST_SUITE OFF CACHE BOOL "" FORCE)
set(QL_BUILD_BENCHMARK OFF CACHE BOOL "" FORCE)
add_subdirectory(quantlib EXCLUDE_FROM_ALL)

# --- Probes ---
# Each probe is a self-contained .cpp under probes/ (may be nested in subdirs).
# Output binaries go to build/probes/<name>_probe for easy scripting.

file(GLOB_RECURSE PROBE_SOURCES CONFIGURE_DEPENDS probes/*_probe.cpp)

foreach(src IN LISTS PROBE_SOURCES)
    get_filename_component(probe_name "${src}" NAME_WE)   # strips .cpp
    add_executable(${probe_name} "${src}")
    target_link_libraries(${probe_name} PRIVATE QuantLib::QuantLib)
    target_include_directories(${probe_name} PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party"
        "${CMAKE_CURRENT_SOURCE_DIR}/probes")
    set_target_properties(${probe_name} PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/probes")
endforeach()

message(STATUS "Migration harness: ${CMAKE_CURRENT_SOURCE_DIR}")
message(STATUS "Probes discovered: ${PROBE_SOURCES}")
```

- [ ] **Step 3: Do a dry-run CMake configure to confirm syntax is valid.**

With no probes yet, the probe loop is empty but CMake should still configure successfully, finding QuantLib.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
cmake -S migration-harness/cpp -B migration-harness/cpp/build -DCMAKE_BUILD_TYPE=Release 2>&1 | tail -20
```

Expected: `-- Configuring done`. May take a minute the first time as QL configures.

- [ ] **Step 4: Quick-build QuantLib target only to ensure linkage will work.**

```bash
cmake --build migration-harness/cpp/build --target QuantLib -j 2>&1 | tail -5
```

Expected: `Build complete` with no errors (5–10 minutes first time).

- [ ] **Step 5: Verify `git status` is clean except for the new CMake + json.hpp.**

```bash
git status --short
```

Expected: only `migration-harness/cpp/CMakeLists.txt` and `migration-harness/cpp/third_party/nlohmann/json.hpp` as untracked. Build dir is gitignored.

- [ ] **Step 6: Commit.**

```bash
git add migration-harness/cpp/CMakeLists.txt migration-harness/cpp/third_party/nlohmann/json.hpp
git -c commit.gpgsign=false commit -s -m "infra(harness): add CMake build for probes; add nlohmann/json third-party" -m "CMakeLists glob-discovers probes under probes/ matching *_probe.cpp, links
each against QuantLib, outputs to build/probes/. Uses nlohmann/json v3.11.3
single-header for JSON output from probes. Implements design §5.3."
```

---

### Task 3: `common.hpp` ReferenceWriter + smoke-test probe

**Files:**
- Create: `migration-harness/cpp/probes/common.hpp`
- Create: `migration-harness/cpp/probes/_smoke_test_probe.cpp`
- Create: `migration-harness/references/_smoke_test.json` (generated)

- [ ] **Step 1: Write `common.hpp` with `ReferenceWriter`.**

```cpp
// migration-harness/cpp/probes/common.hpp
// Helper for probes to emit reference JSON files in the canonical schema
// defined in docs/migration/phase1-design.md §5.4.

#ifndef JQUANTLIB_HARNESS_COMMON_HPP
#define JQUANTLIB_HARNESS_COMMON_HPP

#include <nlohmann/json.hpp>
#include <chrono>
#include <fstream>
#include <sstream>
#include <string>
#include <filesystem>

namespace jqml_harness {

using json = nlohmann::json;

class ReferenceWriter {
public:
    // test_group: e.g., "math/bisection"  → written to references/math/bisection.json
    ReferenceWriter(std::string test_group,
                    std::string cpp_version,
                    std::string generated_by)
        : test_group_(std::move(test_group)),
          cpp_version_(std::move(cpp_version)),
          generated_by_(std::move(generated_by)) {}

    // Add a case. inputs is arbitrary JSON object. expected is either a number,
    // a JSON array, or a JSON object — whatever the consuming Java test expects.
    void addCase(const std::string& name, json inputs, json expected) {
        cases_.push_back({
            {"name", name},
            {"inputs", std::move(inputs)},
            {"expected", std::move(expected)}
        });
    }

    // Write to <harness_root>/references/<test_group>.json
    // Assumes the process cwd is the harness root (setup.sh / generate-references.sh
    // always cd there before running probes).
    void write() const {
        namespace fs = std::filesystem;
        const fs::path out = fs::path("references") / (test_group_ + ".json");
        fs::create_directories(out.parent_path());

        json doc = {
            {"test_group", test_group_},
            {"cpp_version", cpp_version_},
            {"cpp_commit", "099987f0ca2c11c505dc4348cdb9ce01a598e1e5"},
            {"generated_at", utcNow()},
            {"generated_by", generated_by_},
            {"cases", cases_}
        };

        std::ofstream f(out);
        if (!f) {
            std::ostringstream err;
            err << "ReferenceWriter: cannot open " << out << " for write";
            throw std::runtime_error(err.str());
        }
        f << doc.dump(2) << '\n';
    }

private:
    static std::string utcNow() {
        using namespace std::chrono;
        auto now = system_clock::now();
        auto sec = time_point_cast<seconds>(now);
        std::time_t tt = system_clock::to_time_t(sec);
        std::tm tm_utc{};
        gmtime_r(&tt, &tm_utc);
        char buf[32];
        std::strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%SZ", &tm_utc);
        return std::string(buf);
    }

    std::string test_group_;
    std::string cpp_version_;
    std::string generated_by_;
    std::vector<json> cases_;
};

} // namespace jqml_harness

#endif // JQUANTLIB_HARNESS_COMMON_HPP
```

- [ ] **Step 2: Write `_smoke_test_probe.cpp` — a minimal probe to prove the writer works.**

```cpp
// migration-harness/cpp/probes/_smoke_test_probe.cpp
// Smoke test — computes a trivial constant via QuantLib and emits a reference.
// Leading underscore distinguishes this scaffold-era probe from real ones.
// Once we have at least one real probe committed, this can be removed or kept
// as a harness self-test.

#include <ql/version.hpp>
#include <ql/time/date.hpp>
#include "common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("_smoke_test", QL_VERSION, "_smoke_test_probe");

    // Case 1: QL version string.
    out.addCase("qlVersion", json{{"request", "version"}}, json(QL_VERSION));

    // Case 2: A known date serial — epoch of QL's Date class.
    Date epoch(1, January, 1901);
    out.addCase("epochSerial",
                json{{"year", 1901}, {"month", 1}, {"day", 1}},
                json(epoch.serialNumber()));

    out.write();
    return 0;
}
```

- [ ] **Step 3: Build the smoke-test probe.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
cmake --build migration-harness/cpp/build -j 2>&1 | tail -10
```

Expected: `_smoke_test_probe` target builds and is placed at `migration-harness/cpp/build/probes/_smoke_test_probe`.

- [ ] **Step 4: Run the smoke-test probe and inspect its output.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib/migration-harness
./cpp/build/probes/_smoke_test_probe
cat references/_smoke_test.json
```

Expected: `references/_smoke_test.json` exists with 2 cases — `qlVersion` = `"1.42.1"` and `epochSerial` an integer (QuantLib's internal epoch serial).

- [ ] **Step 5: Confirm `verify-harness.sh` works.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib/migration-harness
./verify-harness.sh
```

Expected: `=== OK: harness deterministic, references match ===`.

- [ ] **Step 6: Commit.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git add migration-harness/cpp/probes/common.hpp \
        migration-harness/cpp/probes/_smoke_test_probe.cpp \
        migration-harness/references/_smoke_test.json
git -c commit.gpgsign=false commit -s -m "infra(harness): add common.hpp ReferenceWriter and smoke-test probe" -m "ReferenceWriter emits JSON in the design §5.4 schema. _smoke_test_probe
exercises it end-to-end and proves the CMake build + linkage + file I/O
all work. The committed _smoke_test.json doubles as a harness self-test
via verify-harness.sh. Implements design §5.3-5.4."
```

---

### Task 4: Stub scanner (Python 3)

**Files:**
- Create: `tools/stub-scanner/scan_stubs.py`
- Create: `tools/stub-scanner/README.md`

- [ ] **Step 1: Create the scanner directory.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
mkdir -p tools/stub-scanner
```

- [ ] **Step 2: Write `tools/stub-scanner/scan_stubs.py`.**

```python
#!/usr/bin/env python3
"""Scan JQuantLib for open stubs. Emits docs/migration/stub-inventory.json
and docs/migration/worklist.md in the schema defined by
docs/migration/phase1-design.md §3.2 and §3.4.

Not a full Java AST parser — uses line-based heuristics tailored to the five
specific stub patterns in design §2.3. Fragile if the codebase departs from
its current style; re-check the match logic if the Java tree is refactored.
"""

from __future__ import annotations

import json
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field, asdict
from pathlib import Path
from typing import Iterable


REPO_ROOT = Path(__file__).resolve().parents[2]
JAVA_ROOT = REPO_ROOT / "jquantlib" / "src" / "main" / "java" / "org" / "jquantlib"
INVENTORY_PATH = REPO_ROOT / "docs" / "migration" / "stub-inventory.json"
WORKLIST_PATH = REPO_ROOT / "docs" / "migration" / "worklist.md"

# --- patterns --------------------------------------------------------------

WORK_IN_PROGRESS = re.compile(
    r'throw\s+new\s+UnsupportedOperationException\s*\(\s*"Work in progress"'
)
NOT_IMPLEMENTED = re.compile(
    r'throw\s+new\s+(LibraryException|UnsupportedOperationException)\s*\(\s*"(not implemented|not yet implemented)"'
)
NUMERICAL_SUSPECT = re.compile(
    r'TODO:\s*code review\s*::\s*please verify against QL/C\+\+ code'
)

# method-signature detector (rough; extracts name + signature for the
# nearest enclosing method declaration)
METHOD_SIG = re.compile(
    r'^\s*(public|protected|private|static|final|abstract|synchronized|\s)+'
    r'(?P<ret>[\w<>,?\[\]\s]+?)\s+(?P<name>\w+)\s*\('
)

PACKAGE_DECL = re.compile(r'^\s*package\s+([\w.]+)\s*;')


@dataclass
class Stub:
    id: str
    file: str
    line: int
    kind: str
    method_signature: str
    cpp_counterpart: str = ""
    depends_on: list = field(default_factory=list)
    existing_test: str | None = None
    cpp_tests: list = field(default_factory=list)
    notes: str = ""


def find_enclosing_method(lines: list[str], idx: int) -> tuple[str, str]:
    """Walk backward from line idx to find the enclosing method declaration.
    Returns (class_name_guess, method_signature). Class name is empty if
    we can't determine it heuristically."""
    for i in range(idx, -1, -1):
        m = METHOD_SIG.match(lines[i])
        if m:
            # Reassemble signature up through the first ')' on or after this line
            sig = lines[i].strip()
            j = i
            while ')' not in sig and j + 1 < len(lines):
                j += 1
                sig += ' ' + lines[j].strip()
            # Trim at ')' for cleanliness
            paren = sig.find(')')
            if paren >= 0:
                sig = sig[:paren + 1]
            return (m.group('name'), sig)
    return ("", "")


def package_of(lines: list[str]) -> str:
    for ln in lines[:30]:
        m = PACKAGE_DECL.match(ln)
        if m:
            return m.group(1)
    return ""


def class_name_from_path(path: Path) -> str:
    return path.stem


def scan_file(path: Path) -> Iterable[Stub]:
    try:
        text = path.read_text(encoding='utf-8')
    except UnicodeDecodeError:
        return
    lines = text.splitlines()
    pkg = package_of(lines)
    cls = class_name_from_path(path)
    pkg_short = pkg[len("org.jquantlib."):] if pkg.startswith("org.jquantlib.") else pkg

    for idx, line in enumerate(lines):
        kind = None
        if WORK_IN_PROGRESS.search(line):
            kind = 'work_in_progress'
        elif NOT_IMPLEMENTED.search(line):
            kind = 'not_implemented'
        elif NUMERICAL_SUSPECT.search(line):
            kind = 'numerical_suspect'
        if not kind:
            continue

        method_name, method_sig = find_enclosing_method(lines, idx)
        stub_id = f"{pkg_short}.{cls}#{method_name or f'line{idx+1}'}"
        rel = path.relative_to(REPO_ROOT).as_posix()
        yield Stub(
            id=stub_id,
            file=rel,
            line=idx + 1,
            kind=kind,
            method_signature=method_sig,
        )


def scan_tree() -> list[Stub]:
    stubs: list[Stub] = []
    for java in JAVA_ROOT.rglob("*.java"):
        if "/test/" in java.as_posix() or java.stem.endswith("Test"):
            continue  # test files are never stubs
        stubs.extend(scan_file(java))
    stubs.sort(key=lambda s: (s.file, s.line))
    return stubs


def write_inventory(stubs: list[Stub]) -> None:
    INVENTORY_PATH.parent.mkdir(parents=True, exist_ok=True)
    INVENTORY_PATH.write_text(
        json.dumps([asdict(s) for s in stubs], indent=2) + "\n",
        encoding='utf-8'
    )


def write_worklist(stubs: list[Stub]) -> None:
    """First-pass worklist — groups by package, not yet by dependency layer.
    Pass 2 of the ordering (design §3.3) is manual/scripted later; this file
    is regenerated by the scanner every time and the layer grouping lives in
    a separate committed file once computed."""
    by_pkg: dict[str, list[Stub]] = defaultdict(list)
    for s in stubs:
        pkg = s.id.rsplit('.', 1)[0].rsplit('#', 1)[0].split('#')[0]
        pkg = pkg.rsplit('.', 1)[0]
        by_pkg[pkg].append(s)

    lines = [
        "# Stub Worklist (first-pass, by package)",
        "",
        "Generated by `tools/stub-scanner/scan_stubs.py`. Do not edit by hand —",
        "re-run the scanner to regenerate.",
        "",
        f"**Total open stubs:** {len(stubs)}",
        "",
        "Layer-based ordering (design §3.3 pass 2) is applied on top of this",
        "during Phase 1 execution and lives in `docs/migration/worklist-layers.md`.",
        "",
    ]
    for pkg in sorted(by_pkg):
        lines.append(f"## {pkg} ({len(by_pkg[pkg])} stubs)")
        lines.append("")
        for s in by_pkg[pkg]:
            lines.append(f"- [ ] `{s.id}` ({s.kind}) — `{s.file}:{s.line}`")
        lines.append("")

    WORKLIST_PATH.parent.mkdir(parents=True, exist_ok=True)
    WORKLIST_PATH.write_text("\n".join(lines), encoding='utf-8')


def main() -> int:
    stubs = scan_tree()
    write_inventory(stubs)
    write_worklist(stubs)
    print(f"wrote {INVENTORY_PATH.relative_to(REPO_ROOT)} ({len(stubs)} stubs)")
    print(f"wrote {WORKLIST_PATH.relative_to(REPO_ROOT)}")
    by_kind: dict[str, int] = defaultdict(int)
    for s in stubs:
        by_kind[s.kind] += 1
    for k, n in sorted(by_kind.items()):
        print(f"  {k}: {n}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 3: Make executable and write `tools/stub-scanner/README.md`.**

```bash
chmod +x tools/stub-scanner/scan_stubs.py
```

README contents:

```markdown
# Stub Scanner

Finds open stubs in the Java tree and emits:
- `docs/migration/stub-inventory.json` — structured list per design §3.2
- `docs/migration/worklist.md` — human-readable checklist by package

## Run

```
python3 tools/stub-scanner/scan_stubs.py
```

Deterministic: given the same Java tree, always produces identical output.
Safe to re-run after each stub resolution.

## What it finds

Five patterns, per `docs/migration/phase1-design.md` §2.3. The current
implementation handles `work_in_progress`, `not_implemented`, and
`numerical_suspect` automatically. `todo_stub` and `fixme_defect` require
manual curation; the scanner does NOT emit them on its own.

## Limitations

- Line-based regex, not a full Java AST. Assumes JQuantLib's current
  formatting conventions. If the codebase is reformatted heavily, re-check
  the match logic.
- Does not (yet) populate `depends_on` / `cpp_counterpart` / `cpp_tests` —
  those are filled during per-stub work.
```

- [ ] **Step 4: Do a dry-run to confirm the scanner is syntactically OK (doesn't have to produce useful output yet — the next task does that).**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
python3 -c "import ast; ast.parse(open('tools/stub-scanner/scan_stubs.py').read()); print('syntax OK')"
```

Expected: `syntax OK`.

- [ ] **Step 5: Commit (scanner tool only; actual inventory/worklist generation is the next task).**

```bash
git add tools/stub-scanner/
git -c commit.gpgsign=false commit -s -m "infra(scanner): add stub inventory scanner tool" -m "tools/stub-scanner/scan_stubs.py walks the Java tree looking for the three
automatically-detectable stub kinds from design §2.3 (work_in_progress,
not_implemented, numerical_suspect) and writes inventory JSON + worklist
markdown under docs/migration/. todo_stub and fixme_defect require manual
curation and are not auto-emitted."
```

---

### Task 5: Generate initial inventory + worklist

**Files:**
- Create: `docs/migration/stub-inventory.json` (generated)
- Create: `docs/migration/worklist.md` (generated)
- Create: `docs/migration/followups.md` (empty placeholder)
- Create: `docs/migration/phase1-carveouts.md` (empty placeholder)

- [ ] **Step 1: Run the scanner.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
python3 tools/stub-scanner/scan_stubs.py
```

Expected output:

```
wrote docs/migration/stub-inventory.json (XXX stubs)
wrote docs/migration/worklist.md
  not_implemented: ~23
  numerical_suspect: ~650
  work_in_progress: ~179
```

- [ ] **Step 2: Sanity-check the counts.**

If the total is > 1000 (say, >1100 to allow margin), **this is trigger A1** — pause and flag to the user. Do not proceed to commit. Report the breakdown and wait.

If the total is within the 300–1000 band, proceed.

Note: `numerical_suspect` counts are recorded but not active work (design §2.3). The actual work volume is `work_in_progress` + `not_implemented` ≈ 200 plus whatever `fixme_defect`/`todo_stub` manual curation surfaces.

- [ ] **Step 3: Inspect a few inventory entries.**

```bash
head -40 docs/migration/stub-inventory.json
head -60 docs/migration/worklist.md
```

Expected: well-formed JSON array, worklist grouped by package with checkboxes.

- [ ] **Step 4: Create empty followups and carve-outs files.**

```bash
cat > docs/migration/followups.md <<'EOF'
# Phase 1 Follow-ups

Deferred items discovered during Phase 1 — out of scope for the current stub
being resolved but worth tracking. Each entry has a date, context, and a
hint at what prompted the note.

Format: `YYYY-MM-DD — <area>: <note>. Context: <which stub uncovered it>.`

---
EOF

cat > docs/migration/phase1-carveouts.md <<'EOF'
# Phase 1 Carve-outs

Stubs explicitly excluded from Phase 1 with user sign-off. See design §7.5
for acceptable carve-out conditions.

Each entry: stub id, reason, date, user sign-off (commit sha or message).

---
EOF
```

- [ ] **Step 5: Commit.**

```bash
git add docs/migration/stub-inventory.json \
        docs/migration/worklist.md \
        docs/migration/followups.md \
        docs/migration/phase1-carveouts.md
git -c commit.gpgsign=false commit -s -m "infra(inventory): generate initial stub-inventory.json and worklist.md" -m "First scanner run against the current Java tree. Counts:

Breakdown is (will be filled from step 1 output — paste exact numbers
before committing).

followups.md and phase1-carveouts.md are empty placeholders; entries are
appended as Phase 1 proceeds."
```

Before running the commit command, edit the `-m` body to paste the actual stub counts from Step 1.

---

### Task 6: Java test utilities — Tolerance + ReferenceReader

**Files:**
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/util/Tolerance.java`
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/util/ToleranceTest.java`
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/util/ReferenceReader.java`
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/util/ReferenceReaderTest.java`

- [ ] **Step 1: Confirm the testsuite util directory structure.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
ls jquantlib/src/test/java/org/jquantlib/testsuite/util/ 2>&1 || mkdir -p jquantlib/src/test/java/org/jquantlib/testsuite/util/
```

If an existing `util` dir already has files, do not replace them — add new files alongside.

- [ ] **Step 2: Check whether Maven pulls in any JSON dependency we can use.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
grep -l 'json' jquantlib/pom.xml jquantlib-parent/pom.xml 2>&1 | head
```

If no JSON library is on the classpath, we'll use `org.json:json:20231013` (minimal, mature, small footprint). If it's already there, use what's available.

- [ ] **Step 3: If needed, add `org.json:json` as a test-scope dependency.**

Only if the grep in Step 2 showed nothing useful. Edit `jquantlib/pom.xml`, inside the `<dependencies>` block:

```xml
<dependency>
  <groupId>org.json</groupId>
  <artifactId>json</artifactId>
  <version>20231013</version>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 4: Write `Tolerance.java`.**

```java
// jquantlib/src/test/java/org/jquantlib/testsuite/util/Tolerance.java
package org.jquantlib.testsuite.util;

/**
 * Tolerance tiers for Phase 1 cross-validation checks, per
 * docs/migration/phase1-design.md §4.2. Use the static helpers from JUnit
 * tests to assert Java-vs-C++ agreement.
 *
 * <p>Tiers:
 * <ul>
 *   <li>{@link #exact} — integer/date arithmetic, calendar logic. Bit-exact.</li>
 *   <li>{@link #tight} — closed-form formulas. 1e-12 relative; 1e-14 absolute
 *       when the reference is below 1e-2.</li>
 *   <li>{@link #loose} — Monte Carlo, numerical integration, root-finding,
 *       PDE solvers. 1e-8 relative.</li>
 * </ul>
 *
 * <p>Per-test exceptions are permitted but must be inline-justified with a
 * code comment citing the reason (per design G1 quality gate).
 */
public final class Tolerance {

    public static final double TIGHT_REL = 1.0e-12;
    public static final double TIGHT_ABS_NEAR_ZERO = 1.0e-14;
    public static final double TIGHT_NEAR_ZERO_THRESHOLD = 1.0e-2;
    public static final double LOOSE_REL = 1.0e-8;

    private Tolerance() {}

    /** Bit-exact check — for integers, dates, enums, and anything Boolean. */
    public static boolean exact(long javaValue, long cppValue) {
        return javaValue == cppValue;
    }

    /** Bit-exact double check — only for values that are mathematically exact. */
    public static boolean exact(double javaValue, double cppValue) {
        return Double.compare(javaValue, cppValue) == 0;
    }

    /** Tight tier: 1e-12 relative, 1e-14 absolute when reference near zero. */
    public static boolean tight(double javaValue, double cppValue) {
        final double absRef = Math.abs(cppValue);
        if (absRef < TIGHT_NEAR_ZERO_THRESHOLD) {
            return Math.abs(javaValue - cppValue) < TIGHT_ABS_NEAR_ZERO;
        }
        return Math.abs(javaValue - cppValue) / absRef < TIGHT_REL;
    }

    /** Loose tier: 1e-8 relative (with absolute fallback at the same level). */
    public static boolean loose(double javaValue, double cppValue) {
        final double absRef = Math.abs(cppValue);
        if (absRef < LOOSE_REL) {
            return Math.abs(javaValue - cppValue) < LOOSE_REL;
        }
        return Math.abs(javaValue - cppValue) / absRef < LOOSE_REL;
    }

    /**
     * Per-test tier loosening — use when an algorithm's inherent error
     * forces a tolerance weaker than {@link #loose}. The justification string
     * is not enforced at runtime; it exists to remind test authors that
     * looser tolerances require an inline explanation.
     */
    public static boolean within(double javaValue, double cppValue,
                                 double relTol, String justification) {
        final double absRef = Math.abs(cppValue);
        if (absRef < relTol) {
            return Math.abs(javaValue - cppValue) < relTol;
        }
        return Math.abs(javaValue - cppValue) / absRef < relTol;
    }
}
```

- [ ] **Step 5: Write `ToleranceTest.java`.**

```java
// jquantlib/src/test/java/org/jquantlib/testsuite/util/ToleranceTest.java
package org.jquantlib.testsuite.util;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ToleranceTest {

    @Test
    public void tightAcceptsTinyRelativeError() {
        assertTrue(Tolerance.tight(1.0 + 1e-13, 1.0));
        assertTrue(Tolerance.tight(1.0 - 1e-13, 1.0));
    }

    @Test
    public void tightRejectsLooseRelativeError() {
        assertFalse(Tolerance.tight(1.0 + 1e-11, 1.0));
    }

    @Test
    public void tightUsesAbsoluteNearZero() {
        // Reference is below 1e-2 threshold, so 1e-14 absolute applies.
        assertTrue(Tolerance.tight(1e-15, 0.0));
        assertTrue(Tolerance.tight(1e-20 + 1e-15, 1e-20));
        assertFalse(Tolerance.tight(1e-12, 0.0));
    }

    @Test
    public void looseAccepts1e9RelativeError() {
        assertTrue(Tolerance.loose(1.0 + 1e-9, 1.0));
    }

    @Test
    public void looseRejects1e7RelativeError() {
        assertFalse(Tolerance.loose(1.0 + 1e-7, 1.0));
    }

    @Test
    public void exactIntRequiresEquality() {
        assertTrue(Tolerance.exact(42L, 42L));
        assertFalse(Tolerance.exact(42L, 43L));
    }

    @Test
    public void exactDoubleRequiresBitEquality() {
        assertTrue(Tolerance.exact(1.5, 1.5));
        assertFalse(Tolerance.exact(1.5 + 1e-16, 1.5));
    }

    @Test
    public void withinHonorsCustomTolerance() {
        assertTrue(Tolerance.within(1.0 + 1e-5, 1.0, 1e-4, "demo"));
        assertFalse(Tolerance.within(1.0 + 1e-3, 1.0, 1e-4, "demo"));
    }
}
```

- [ ] **Step 6: Write `ReferenceReader.java`.**

```java
// jquantlib/src/test/java/org/jquantlib/testsuite/util/ReferenceReader.java
package org.jquantlib.testsuite.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
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
            throw new AssertionError("cannot read reference file: " + file, e);
        }
        final JSONObject doc = new JSONObject(text);
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
        return Collections.unmodifiableList(new java.util.ArrayList<>(casesByName.keySet()));
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
        public JSONObject inputs() { return inputs; }

        public double expectedDouble() { return ((Number) expected).doubleValue(); }
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
        throw new AssertionError("could not locate migration-harness/ above cwd="
                + Paths.get("").toAbsolutePath());
    }
}
```

- [ ] **Step 7: Write `ReferenceReaderTest.java`.**

```java
// jquantlib/src/test/java/org/jquantlib/testsuite/util/ReferenceReaderTest.java
package org.jquantlib.testsuite.util;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReferenceReaderTest {

    @Test
    public void loadsSmokeTestReference() {
        final ReferenceReader ref = ReferenceReader.load("_smoke_test");
        assertEquals("_smoke_test", ref.testGroup());
        assertEquals("1.42.1", ref.cppVersion());
        assertEquals("_smoke_test_probe", ref.generatedBy());
        assertTrue(ref.caseNames().contains("qlVersion"));
        assertTrue(ref.caseNames().contains("epochSerial"));
    }

    @Test
    public void smokeTestCaseExpectedValues() {
        final ReferenceReader ref = ReferenceReader.load("_smoke_test");
        assertEquals("1.42.1", ref.getCase("qlVersion").expectedString());
        // The exact epoch serial is QuantLib's internal convention; we just
        // assert it's a positive integer reasonable for 1-Jan-1901.
        final long serial = ref.getCase("epochSerial").expectedLong();
        assertTrue(serial > 0);
        assertTrue(serial < 100_000);
    }
}
```

- [ ] **Step 8: Run the new tests.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
(cd jquantlib && mvn -Dtest='ToleranceTest,ReferenceReaderTest' test) 2>&1 | tail -20
```

Expected: both tests green.

- [ ] **Step 9: Run the full test suite to confirm no regressions.**

```bash
(cd jquantlib && mvn test) 2>&1 | tail -10
```

Expected: same or greater test count than baseline from Task 0.3, no failures.

- [ ] **Step 10: Commit.**

```bash
git add jquantlib/src/test/java/org/jquantlib/testsuite/util/ \
        jquantlib/pom.xml
git -c commit.gpgsign=false commit -s -m "infra(testutil): add Tolerance and ReferenceReader test utilities" -m "Tolerance encapsulates the exact/tight/loose tier policy from design §4.2.
ReferenceReader loads migration-harness/references/<group>.json and exposes
per-case inputs + expected values to JUnit tests. Both have their own unit
tests; the ReferenceReader test exercises the smoke-test reference from the
previous commit, closing the harness-to-Java loop end-to-end."
```

---

### Bootstrap complete — what's done

After Task 6 commits, the following is in place on `migration/phase1-finish-stubs`:

- `migration-harness/` with pinned QuantLib, CMake build, writer helper, smoke-test probe, and a verified deterministic reference.
- `tools/stub-scanner/scan_stubs.py` with a generated `stub-inventory.json` and `worklist.md`.
- `org.jquantlib.testsuite.util.Tolerance` and `ReferenceReader` (tested).
- All passing `(cd jquantlib && mvn test)`.

Next: merge Layer 0 *prep* to `main`. Since bootstrap produces no stub resolutions, this merge is really "layer negative-one" — infrastructure. I propose we merge these 6 bootstrap commits to `main` before starting Task 7 (Observable/Handle audit), so the subsequent audit work has a clean base to rebase onto when Layer 0 completes. Per design §6.5:

- [ ] **Bootstrap merge to main.**

```bash
git checkout main
git pull origin main
git checkout migration/phase1-finish-stubs
git rebase main
git checkout main
git merge --ff-only migration/phase1-finish-stubs
git push origin main
git checkout migration/phase1-finish-stubs
```

Expected: main now contains all six bootstrap commits plus the earlier design + CLAUDE.md.

---

## Phase 2 — Observable/Handle audit (Layer 0, first task)

This is the first piece of substantive work. Every downstream pricing engine and term-structure observer depends on these classes behaving correctly; a bug here compounds across hundreds of stubs. Design §8.R3 mandates this before any stub resolution.

### Task 7: Audit `org.jquantlib.util.Observable` / `WeakReferenceObservable` / `DefaultObservable` and `org.jquantlib.quotes.Handle`

**Files potentially touched:**
- `jquantlib/src/main/java/org/jquantlib/util/Observable.java` (read, possibly align)
- `jquantlib/src/main/java/org/jquantlib/util/Observer.java` (read, possibly align)
- `jquantlib/src/main/java/org/jquantlib/util/DefaultObservable.java` (read, possibly align)
- `jquantlib/src/main/java/org/jquantlib/util/WeakReferenceObservable.java` (read, possibly align)
- `jquantlib/src/main/java/org/jquantlib/quotes/Handle.java` (read, possibly align)

**Create:**
- `migration-harness/cpp/probes/patterns/observable_probe.cpp`
- `migration-harness/cpp/probes/patterns/handle_probe.cpp`
- `migration-harness/references/patterns/observable.json` (generated)
- `migration-harness/references/patterns/handle.json` (generated)
- `jquantlib/src/test/java/org/jquantlib/testsuite/patterns/ObservableBehaviorTest.java`
- `jquantlib/src/test/java/org/jquantlib/testsuite/patterns/HandleBehaviorTest.java`

- [ ] **Step 1: Read the C++ reference.**

```bash
less /Users/josemoya/eclipse-workspace/jquantlib/migration-harness/cpp/quantlib/ql/patterns/observable.hpp
less /Users/josemoya/eclipse-workspace/jquantlib/migration-harness/cpp/quantlib/ql/handle.hpp
less /Users/josemoya/eclipse-workspace/jquantlib/migration-harness/cpp/quantlib/test-suite/observable.cpp
```

Build mental model: How does C++ notify observers? What's the ordering? What happens when an observer is GC'd mid-notification? What does a relinkable handle do?

- [ ] **Step 2: Read the current Java implementations.**

```bash
less jquantlib/src/main/java/org/jquantlib/util/Observable.java
less jquantlib/src/main/java/org/jquantlib/util/DefaultObservable.java
less jquantlib/src/main/java/org/jquantlib/util/WeakReferenceObservable.java
less jquantlib/src/main/java/org/jquantlib/quotes/Handle.java
```

Identify divergences vs the C++ model. Write notes to `/tmp/observable-audit-notes.md` — not committed, just working notes.

- [ ] **Step 3: Write `observable_probe.cpp` porting the key behaviors from the C++ test-suite.**

The C++ test-suite has scenarios like: basic notification, deregistering observers, notification with locked handle, observer destruction during notification. Port the three or four most load-bearing into the probe, emitting expected outcomes as reference cases.

```cpp
// migration-harness/cpp/probes/patterns/observable_probe.cpp
#include <ql/patterns/observable.hpp>
#include "../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

struct Flag {
    std::string name;
    int count = 0;
};

class CountingObserver : public Observer {
public:
    CountingObserver(Flag* f) : flag_(f) {}
    void update() override { ++flag_->count; }
private:
    Flag* flag_;
};

} // namespace

int main() {
    ReferenceWriter out("patterns/observable", QL_VERSION, "observable_probe");

    // Case 1: single notify → single update
    {
        ext::shared_ptr<Observable> obs(new Observable);
        Flag f{"single", 0};
        CountingObserver observer(&f);
        observer.registerWith(obs);
        obs->notifyObservers();
        out.addCase("singleNotify",
                    json{{"observers", 1}, {"notifies", 1}},
                    json(f.count));
    }

    // Case 2: deregister then notify → no update
    {
        ext::shared_ptr<Observable> obs(new Observable);
        Flag f{"deregistered", 0};
        CountingObserver observer(&f);
        observer.registerWith(obs);
        observer.unregisterWith(obs);
        obs->notifyObservers();
        out.addCase("deregisteredThenNotify",
                    json{{"observers_registered", 1}, {"observers_unregistered", 1}, {"notifies", 1}},
                    json(f.count));
    }

    // Case 3: multiple notifies accumulate
    {
        ext::shared_ptr<Observable> obs(new Observable);
        Flag f{"multiple", 0};
        CountingObserver observer(&f);
        observer.registerWith(obs);
        for (int i = 0; i < 5; ++i) obs->notifyObservers();
        out.addCase("multipleNotify",
                    json{{"observers", 1}, {"notifies", 5}},
                    json(f.count));
    }

    out.write();
    return 0;
}
```

- [ ] **Step 4: Write `handle_probe.cpp` exercising `Handle<T>` relink semantics.**

```cpp
// migration-harness/cpp/probes/patterns/handle_probe.cpp
#include <ql/handle.hpp>
#include <ql/quote.hpp>
#include <ql/quotes/simplequote.hpp>
#include "../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("patterns/handle", QL_VERSION, "handle_probe");

    // Case 1: Handle to a SimpleQuote returns its value.
    {
        ext::shared_ptr<SimpleQuote> q(new SimpleQuote(1.23));
        Handle<Quote> h(q);
        out.addCase("basicValue",
                    json{{"initialValue", 1.23}},
                    json(h->value()));
    }

    // Case 2: Relinkable handle tracks relinking.
    {
        ext::shared_ptr<SimpleQuote> q1(new SimpleQuote(1.00));
        ext::shared_ptr<SimpleQuote> q2(new SimpleQuote(2.00));
        RelinkableHandle<Quote> h(q1);
        const double before = h->value();
        h.linkTo(q2);
        const double after = h->value();
        out.addCase("relinkChangesValue",
                    json{{"v1", 1.00}, {"v2", 2.00}},
                    json::array({before, after}));
    }

    out.write();
    return 0;
}
```

- [ ] **Step 5: Build + run the probes.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
cmake --build migration-harness/cpp/build -j 2>&1 | tail -5
cd migration-harness
./cpp/build/probes/observable_probe
./cpp/build/probes/handle_probe
cat references/patterns/observable.json references/patterns/handle.json
```

Expected: two new JSON files under `references/patterns/`, each with the cases above.

- [ ] **Step 6: Write `ObservableBehaviorTest.java` mirroring the probe scenarios.**

```java
// jquantlib/src/test/java/org/jquantlib/testsuite/patterns/ObservableBehaviorTest.java
package org.jquantlib.testsuite.patterns;

import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.util.DefaultObservable;
import org.jquantlib.util.Observable;
import org.jquantlib.util.Observer;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class ObservableBehaviorTest {

    private static final ReferenceReader REF = ReferenceReader.load("patterns/observable");

    @Test
    public void singleNotify() {
        final Observable obs = new DefaultObservable(this);
        final AtomicInteger count = new AtomicInteger();
        final Observer obsr = (source, arg) -> count.incrementAndGet();
        obs.addObserver(obsr);
        obs.notifyObservers();
        assertEquals(REF.getCase("singleNotify").expectedLong(), count.get());
    }

    @Test
    public void deregisteredThenNotify() {
        final Observable obs = new DefaultObservable(this);
        final AtomicInteger count = new AtomicInteger();
        final Observer obsr = (source, arg) -> count.incrementAndGet();
        obs.addObserver(obsr);
        obs.deleteObserver(obsr);
        obs.notifyObservers();
        assertEquals(REF.getCase("deregisteredThenNotify").expectedLong(), count.get());
    }

    @Test
    public void multipleNotify() {
        final Observable obs = new DefaultObservable(this);
        final AtomicInteger count = new AtomicInteger();
        final Observer obsr = (source, arg) -> count.incrementAndGet();
        obs.addObserver(obsr);
        for (int i = 0; i < 5; i++) obs.notifyObservers();
        assertEquals(REF.getCase("multipleNotify").expectedLong(), count.get());
    }
}
```

NOTE: the `Observer` interface shape may differ between what this test assumes and what the existing Java actually has. If that's the case, adapt the test to the real shape, and if the real shape is wrong vs C++ (lambda-compatible single-method interface), that's an `align(...)` candidate.

- [ ] **Step 7: Write `HandleBehaviorTest.java`.**

```java
// jquantlib/src/test/java/org/jquantlib/testsuite/patterns/HandleBehaviorTest.java
package org.jquantlib.testsuite.patterns;

import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.Tolerance;
import org.junit.Test;
import org.json.JSONArray;

import static org.junit.Assert.assertTrue;

public class HandleBehaviorTest {

    private static final ReferenceReader REF = ReferenceReader.load("patterns/handle");

    @Test
    public void basicValue() {
        final SimpleQuote q = new SimpleQuote(1.23);
        final Handle<Quote> h = new Handle<Quote>(q);
        assertTrue(Tolerance.tight(h.currentLink().value(),
                                   REF.getCase("basicValue").expectedDouble()));
    }

    @Test
    public void relinkChangesValue() {
        // adapt if the existing Java API uses different method names
        final SimpleQuote q1 = new SimpleQuote(1.00);
        final SimpleQuote q2 = new SimpleQuote(2.00);
        final Handle<Quote> h = new Handle<Quote>(q1);
        final double before = h.currentLink().value();
        h.linkTo(q2);
        final double after = h.currentLink().value();

        final JSONArray expected = REF.getCase("relinkChangesValue").expectedArray();
        assertTrue(Tolerance.tight(before, expected.getDouble(0)));
        assertTrue(Tolerance.tight(after, expected.getDouble(1)));
    }
}
```

- [ ] **Step 8: Run the new tests.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
(cd jquantlib && mvn -Dtest='ObservableBehaviorTest,HandleBehaviorTest' test) 2>&1 | tail -30
```

- [ ] **Step 9: Triage the result.**

| Outcome | Action |
|---------|--------|
| All green on first run | Excellent — Observable/Handle implementations agree with v1.42.1. Skip to Step 11. |
| Red on one or more | Expected — this is the audit. Investigate each failure: read the Java code, compare to C++, determine whether the Java is wrong. If yes, write an `align(util): reconcile Observable with v1.42.1` commit before the test commit. If the test is wrong (API shape assumption was off), fix the test. |
| Compilation failure | The Java API for Observable/Observer/Handle differs from what the tests assume. Adjust the test to match the real Java shape first, then re-run Step 8. Note any differences from C++ for potential align commits. |

For each divergence found:
1. Write the `align(util)` or `align(quotes)` commit first (fix, include a test proving the fix, run full suite).
2. Then re-run the ported test; it should now pass.
3. Commit the test as a separate `stub(util)` commit referencing the audit.

Record every alignment made to `docs/migration/followups.md` — not because they're incomplete, but because they're significant deviations from the pre-Phase-1 Java shape and will matter for understanding the diff later.

- [ ] **Step 10: If alignments were needed, run the full test suite to confirm no regressions anywhere else.**

```bash
(cd jquantlib && mvn test) 2>&1 | tail -10
```

Any pre-existing test that started failing indicates we broke something. Investigate and fix before proceeding.

- [ ] **Step 11: Commit the audit outputs.**

One commit for the probes + references + tests (assuming no alignment needed). If alignment commits preceded this, they landed separately. Final commit:

```bash
git add migration-harness/cpp/probes/patterns/ \
        migration-harness/references/patterns/ \
        jquantlib/src/test/java/org/jquantlib/testsuite/patterns/
git -c commit.gpgsign=false commit -s -m "stub(util): add Observable and Handle behavior audit tests" -m "Layer 0 first task per design §8.R3. Ports three Observable scenarios
(singleNotify, deregisteredThenNotify, multipleNotify) and two Handle
scenarios (basicValue, relinkChangesValue) from QL C++ v1.42.1. Reference
values generated by observable_probe and handle_probe; Java tests assert
via ReferenceReader + Tolerance.tight.

Audit findings: <paste 1-3 sentences about what was found — 'no divergences
observed' if clean; else 'aligned X and Y, see commits ABCD and EFGH'>."
```

Edit the commit body to reflect actual findings before running the command.

- [ ] **Step 12: Merge Layer 0 audit to main.**

Per the per-layer merge discipline:

```bash
git checkout main && git pull origin main
git checkout migration/phase1-finish-stubs && git rebase main
git checkout main && git merge --ff-only migration/phase1-finish-stubs
git push origin main
git checkout migration/phase1-finish-stubs
```

- [ ] **Step 13: Layer 0 audit heads-up (trigger A6).**

Report to user with a summary: what files were audited, what divergences (if any) were found and aligned, total commits landed, any follow-ups logged. Wait for acknowledgment before proceeding to Phase 3 (regular stub work).

---

## Phase 3 — Per-stub procedure template (applied to every remaining stub in worklist order)

This is the loop that runs for each stub after the audit. It is **the** workflow from design §4, made concrete.

### Per-stub procedure (repeat for every stub in `docs/migration/worklist.md`, layer by layer, leaves first)

For stub with id `<stub-id>` (e.g., `math.solvers1d.Bisection#solveImpl`):

- [ ] **Step 1: Open the C++ counterpart.**

From `stub-inventory.json`, find the `cpp_counterpart` field (may be empty on first encounter — in that case, locate it manually by mirroring the Java package path to `migration-harness/cpp/quantlib/ql/<...>.hpp`). Read both `.hpp` and `.cpp` if present. Note boost-isms, numerical choices, branch cuts.

- [ ] **Step 2: Identify or author the reference test.**

Three sub-cases (design §4.1.2):

**2a — C++ test exists:** Search `migration-harness/cpp/quantlib/test-suite/` for tests covering this class/method. Pick one or two representative scenarios and port them as probe cases.

**2b — No C++ test but method is pure/deterministic:** Author 2–3 input/output pairs run through v1.42.1 via a probe.

**2c — Stateful cycle:** Author an integration test exercising the cycle end-to-end against a C++-computed reference.

Create or extend `migration-harness/cpp/probes/<package>/<class>_probe.cpp`. Emit reference JSON to `migration-harness/references/<package>/<class>.json`.

- [ ] **Step 3: Write the Java test at `jquantlib/src/test/java/org/jquantlib/testsuite/<package>/<ClassName>Test.java`.**

Mirrors the C++ probe's cases. Uses `ReferenceReader` + `Tolerance`. Follow existing test style (JUnit 4 `@Test`, static asserts, no Hamcrest). Tolerance tier per §4.2:

- `exact` for integer/date arithmetic
- `tight` for closed-form formulas
- `loose` for MC / numerical methods

- [ ] **Step 4: Build probe. Run test. Confirm RED.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
cmake --build migration-harness/cpp/build -j
./migration-harness/cpp/build/probes/<probe-name>
(cd jquantlib && mvn -Dtest='<TestClassName>' test) 2>&1 | tail -20
```

Expected: test fails — either compile error (stub throws), `UnsupportedOperationException`, or wrong value.

If the test passes unexpectedly: **stop**. Either the stub isn't a stub (update inventory) or the test is tautological (tighten it).

- [ ] **Step 5: Port the implementation.**

Edit `jquantlib/src/main/java/org/jquantlib/<package>/<ClassName>.java`. Replace the stub body with a faithful Java port of the C++ `.cpp`/`.hpp` implementation. Preserve the C++ method signature comment as a `//--` block above the Java method. Follow JQuantLib's existing conventions (Appendix A of the design doc).

Do not add features beyond C++. Do not simplify unless provably equivalent.

- [ ] **Step 6: Run the single test. Confirm GREEN.**

```bash
(cd jquantlib && mvn -Dtest='<TestClassName>' test) 2>&1 | tail -10
```

If still failing: **do not loosen tolerance**. Investigate: is the port algorithm-faithful? Are all branches handled? Does the harness probe compute what the test asserts?

- [ ] **Step 7: Run full test suite.**

```bash
(cd jquantlib && mvn test) 2>&1 | tail -10
```

Expected: all green, no regressions in pre-existing tests.

If a pre-existing test regressed: examine. This may reveal a pre-existing bug in code we touched transitively. If so, **stop the stub commit**, write an `align(<package>): reconcile <ClassName> with v1.42.1` commit first (its own test + cross-validation), and then resume the stub.

- [ ] **Step 8: Re-run the scanner.**

```bash
python3 tools/stub-scanner/scan_stubs.py
```

Expected: the resolved stub disappears from `stub-inventory.json`; counts decrement. Check the worklist entry is still findable (its checkbox will be ticked in the next step).

- [ ] **Step 9: Tick the worklist checkbox.**

Edit `docs/migration/worklist.md`: change the line for this stub from `- [ ]` to `- [x]`. The scanner won't do this automatically; it's manual and atomic with the commit.

Actually — simpler: since `worklist.md` is regenerated by the scanner, its "done" state is implicit (the stub just isn't listed anymore). So **do not** edit the worklist manually; let the scanner regenerate it. The scanner run in Step 8 already updated it.

- [ ] **Step 10: Commit.**

```bash
git add jquantlib/src/main/java/org/jquantlib/<package>/<ClassName>.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/<package>/<ClassName>Test.java \
        migration-harness/cpp/probes/<package>/<class>_probe.cpp \
        migration-harness/references/<package>/<class>.json \
        docs/migration/stub-inventory.json \
        docs/migration/worklist.md
git -c commit.gpgsign=false commit -s -m "stub(<package-short>): implement <ClassName>.<method>" -m "Worklist: <stub-id>
C++ ref: ql/<package>/<class>.hpp:<line>
Test: <TestClassName>.<testMethodName>
Cross-validation: migration-harness/cpp/probes/<package>/<class>_probe.cpp,
                  migration-harness/references/<package>/<class>.json
Tolerance tier: <exact|tight|loose>

<brief notes if needed>"
```

- [ ] **Step 11: Proceed to the next stub.**

Pull the next item from the top of `worklist.md`. Re-enter this procedure at Step 1.

### Cycle-batch variant (when two or more stubs have mutual dependencies)

If the next N stubs on the worklist form a cycle (Handle ↔ Observable, or a mutual-recursion of helper classes), batch them:

1. Repeat Steps 1–3 for each cycle member — probes, references, tests — without yet implementing.
2. Confirm all tests RED.
3. Implement all cycle members (Step 5 for each).
4. Confirm all tests GREEN (Step 6 for each).
5. Step 7 (full suite) once.
6. Steps 8 and 9.
7. Step 10 — **one commit** for the whole batch. Commit body names every cycle member.

If the cycle has more than ~5 members, pause and flag to the user before committing — the batch may need to be split along a weaker boundary.

### `align(...)` variant (when cross-validation reveals a pre-existing bug)

If during Step 6 the test fails because a **pre-existing non-stub Java class** that the stub transitively depends on is wrong vs v1.42.1:

1. Stop the current stub.
2. Identify the defective class.
3. Write a failing test in a new test file demonstrating the defect via cross-validation.
4. Fix the pre-existing class to match v1.42.1.
5. Confirm the test passes; run the full suite.
6. Commit as `align(<package>): reconcile <ClassName> with v1.42.1` (include test + cross-validation in the same commit).
7. Resume the original stub from Step 5 (implementation).

---

## Phase 4 — Per-layer checkpoint ritual

After the last stub in a layer is resolved, before starting the next layer:

- [ ] **Step 1: Verify the layer is actually done.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
python3 tools/stub-scanner/scan_stubs.py
```

Expected: no stubs remaining in the layer's packages.

- [ ] **Step 2: Clean test from fresh state.**

```bash
(cd jquantlib && mvn clean test) 2>&1 | tail -10
```

Expected: all green from a clean start (no cached state masking an issue).

- [ ] **Step 3: Harness determinism check.**

```bash
cd migration-harness && ./verify-harness.sh
cd ..
```

Expected: `OK: harness deterministic, references match`.

- [ ] **Step 4: Merge layer to main.**

```bash
git checkout main && git pull origin main
git checkout migration/phase1-finish-stubs
git rebase main
git checkout main
git merge --ff-only migration/phase1-finish-stubs
git push origin main
git checkout migration/phase1-finish-stubs
```

Expected: fast-forward merge succeeds. If it doesn't (unexpected divergence on main), investigate — main should be untouched aside from our merges.

- [ ] **Step 5: Layer heads-up report (trigger A6).**

Draft and post to the user a summary including:

- Layer name and scope
- Number of stubs closed (+ breakdown by kind)
- Number of `align(...)` commits (divergences corrected in pre-existing code)
- Any tolerance exceptions granted, with justifications
- Any follow-ups logged to `docs/migration/followups.md`
- Any surprises that might warrant scope revision
- Commit range that landed (e.g., `abc123..def456`)

Wait for user acknowledgment before starting the next layer.

---

## Phase 5 — Phase 1 completion

Triggered when the scanner reports zero open stubs.

### Task 8: Phase 1 completion verification and report

- [ ] **Step 1: Final scanner run — confirm zero stubs.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
python3 tools/stub-scanner/scan_stubs.py
cat docs/migration/stub-inventory.json
```

Expected: `[]` and "0 stubs" counts across all kinds.

- [ ] **Step 2: Full clean test from fresh checkout.**

```bash
(cd jquantlib && mvn clean test) 2>&1 | tail -10
```

All green. Note the total test count — it should far exceed the pre-Phase-1 baseline.

- [ ] **Step 3: Verify harness from scratch.**

```bash
cd migration-harness
rm -rf cpp/build   # force full rebuild
./setup.sh
./verify-harness.sh
cd ..
```

Expected: setup completes, verify-harness succeeds, every reference JSON re-generates byte-identical (excluding timestamp field).

- [ ] **Step 4: Write `docs/migration/phase1-completion.md`.**

Template:

```markdown
# Phase 1 Completion Report

**Completed:** YYYY-MM-DD
**Branch merged:** `migration/phase1-finish-stubs` → `main` (final commit: <sha>)

## Summary

- Stubs resolved: N (work_in_progress: X, not_implemented: Y, fixme_defect: Z, todo_stub: W)
- Alignments performed: M (list each `align(...)` commit sha + 1-line summary)
- Tolerance exceptions granted: K (list each test + tolerance used + justification)
- New Java test files: T
- New C++ probes: P
- New reference JSON files: R

## Carve-outs

(List each entry from docs/migration/phase1-carveouts.md, or "None" if empty.)

## Follow-ups

See docs/migration/followups.md (N entries).

## Surprises during Phase 1

(Prose — 100-300 words. What took longer than expected, what was easier,
any patterns that emerged in the alignment commits, any recommendations.)

## Recommended Phase 2 scope

- (Ranked list of the unmigrated top-level packages: experimental/,
  models/marketmodels/, etc. with commentary on which to tackle first.)

## Acknowledgments

- C++ reference: QuantLib v1.42.1 (commit 41b0e14)
- Original JQuantLib contributors whose work formed the Phase 1 starting material
```

- [ ] **Step 5: Commit the completion report.**

```bash
git add docs/migration/phase1-completion.md
git -c commit.gpgsign=false commit -s -m "docs(migration): Phase 1 completion report"
```

- [ ] **Step 6: Final merge to main.**

```bash
git checkout main && git pull origin main
git checkout migration/phase1-finish-stubs
git rebase main
git checkout main
git merge --ff-only migration/phase1-finish-stubs
git push origin main
```

- [ ] **Step 7: Optional — tag the Phase 1 completion.**

```bash
git tag -a jquantlib-phase1-complete -m "JQuantLib Phase 1 complete: all stubs in existing 61 packages resolved, pinned to QuantLib C++ v1.42.1"
git push origin jquantlib-phase1-complete
```

- [ ] **Step 8: Final heads-up to user.**

Report Phase 1 is complete, summarize the completion report, and ask whether to begin Phase 2 planning (`superpowers:brainstorming` for Phase 2 scope) or pause.

---

## Appendix — Troubleshooting

**Submodule update fails with "server certificate verification failed":** Run `git config --global http.sslBackend openssl` and retry. Common on macOS with older git.

**CMake can't find Boost:** `brew info boost` to confirm install path, then set `-DBOOST_ROOT=$(brew --prefix boost)` on the CMake configure step.

**QuantLib build too slow:** Use `-DCMAKE_BUILD_TYPE=Debug` for iteration (skips optimization). For final references, always rebuild with `Release`.

**Probe output is non-deterministic:** Usually caused by uninitialized memory or thread-race in the probe. Check `Settings::instance()` references, global RNG state. Run under Valgrind/ASan if needed.

**`mvn test` intermittently fails:** Existing Java test suite has some flakiness (this pre-existed Phase 1). Document the flaky test in `docs/migration/followups.md`; don't attempt to fix unless it blocks a stub.

**Scanner emits odd `depends_on`:** The first-pass scanner uses import-based heuristics, not a full AST. Manual tiebreak via C++ `#include` list (design §3.3) applies.

**`git merge --ff-only` fails:** Someone else (or your other machine) pushed to `main` between your rebase and your merge. Re-run the rebase step and try again. Nothing destructive — just a race.

---

*Plan produced 2026-04-22, based on `docs/migration/phase1-design.md` (commit `147614f`). Pinned against QuantLib C++ v1.42.1 @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`.*
