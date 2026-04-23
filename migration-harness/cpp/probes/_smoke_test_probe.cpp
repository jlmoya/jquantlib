// migration-harness/cpp/probes/_smoke_test_probe.cpp
// Smoke test -- computes a trivial constant via QuantLib and emits a reference.
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

    // Case 2: A known date serial -- epoch of QL's Date class.
    Date epoch(1, January, 1901);
    out.addCase("epochSerial",
                json{{"year", 1901}, {"month", 1}, {"day", 1}},
                json(epoch.serialNumber()));

    out.write();
    return 0;
}
