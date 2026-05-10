// migration-harness/cpp/probes/methods/montecarlo/brownian_bridge_transform_probe.cpp
// Phase MC-extras — emit C++ v1.42.1 BrownianBridge::transform reference values
// across multiple time grids and known input variates, so the Java port's
// existing transform() implementation can be cross-validated bit-exactly.
//
// The C++ test-suite case (testVariates) requires SequenceStatistics
// covariance and remains @Ignore'd as a separate carry-forward; this
// probe targets the lower-level transform() method itself, which is
// what AmericanMaxPathPricer + LongstaffSchwartzPathPricer rely on.

#include <ql/version.hpp>
#include <ql/methods/montecarlo/brownianbridge.hpp>
#include <ql/timegrid.hpp>
#include "../../common.hpp"

#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// Probe BrownianBridge::transform with a deterministic input vector
// (sequential 0.1, 0.2, ... values stand in for any known sequence) on
// a uniform 5-step time grid spanning [0, 1].
void emitUniform5Step(ReferenceWriter& out) {
    const Size n = 5;
    TimeGrid tg(1.0, n);
    BrownianBridge bb(tg);

    std::vector<Real> in = {0.1, 0.2, 0.3, 0.4, 0.5};
    std::vector<Real> tmp(n);
    bb.transform(in.begin(), in.end(), tmp.begin());

    json input = json::array();
    for (Real v : in) input.push_back(v);
    json times = json::array();
    for (Size i = 1; i <= n; ++i) times.push_back(tg[i]);
    json output = json::array();
    for (Real v : tmp) output.push_back(v);

    json inputs = json::object();
    inputs["n"] = n;
    inputs["length"] = 1.0;
    inputs["input_variates"] = input;
    inputs["times"] = times;

    json expected = json::object();
    expected["output_variates"] = output;

    // Inspectors for additional structural verification
    json bridgeIdx = json::array();
    for (Size i = 0; i < bb.bridgeIndex().size(); ++i) bridgeIdx.push_back(bb.bridgeIndex()[i]);
    json leftIdx = json::array();
    for (Size i = 0; i < bb.leftIndex().size(); ++i) leftIdx.push_back(bb.leftIndex()[i]);
    json rightIdx = json::array();
    for (Size i = 0; i < bb.rightIndex().size(); ++i) rightIdx.push_back(bb.rightIndex()[i]);
    json leftWeight = json::array();
    for (Size i = 0; i < bb.leftWeight().size(); ++i) leftWeight.push_back(bb.leftWeight()[i]);
    json rightWeight = json::array();
    for (Size i = 0; i < bb.rightWeight().size(); ++i) rightWeight.push_back(bb.rightWeight()[i]);
    json stdDev = json::array();
    for (Size i = 0; i < bb.stdDeviation().size(); ++i) stdDev.push_back(bb.stdDeviation()[i]);

    expected["bridge_index"] = bridgeIdx;
    expected["left_index"] = leftIdx;
    expected["right_index"] = rightIdx;
    expected["left_weight"] = leftWeight;
    expected["right_weight"] = rightWeight;
    expected["std_deviation"] = stdDev;

    out.addCase("uniform_5step_unit_length", inputs, expected);
}

// Probe with a non-uniform time grid (the canonical times from
// test-suite/brownianbridge.cpp::testVariates).
void emitNonUniformGrid(ReferenceWriter& out) {
    std::vector<Time> times = {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 2.0, 5.0};
    const Size n = times.size();
    BrownianBridge bb(times);

    // deterministic input variates (alternating pattern for variety)
    std::vector<Real> in(n);
    for (Size i = 0; i < n; ++i) {
        in[i] = (i % 2 == 0) ? 0.5 : -0.5;
    }
    std::vector<Real> tmp(n);
    bb.transform(in.begin(), in.end(), tmp.begin());

    json input = json::array();
    for (Real v : in) input.push_back(v);
    json times_json = json::array();
    for (Real t : times) times_json.push_back(t);
    json output = json::array();
    for (Real v : tmp) output.push_back(v);

    json inputs = json::object();
    inputs["n"] = n;
    inputs["input_variates"] = input;
    inputs["times"] = times_json;

    json expected = json::object();
    expected["output_variates"] = output;

    json bridgeIdx = json::array();
    for (Size i = 0; i < bb.bridgeIndex().size(); ++i) bridgeIdx.push_back(bb.bridgeIndex()[i]);
    json leftIdx = json::array();
    for (Size i = 0; i < bb.leftIndex().size(); ++i) leftIdx.push_back(bb.leftIndex()[i]);
    json rightIdx = json::array();
    for (Size i = 0; i < bb.rightIndex().size(); ++i) rightIdx.push_back(bb.rightIndex()[i]);
    json stdDev = json::array();
    for (Size i = 0; i < bb.stdDeviation().size(); ++i) stdDev.push_back(bb.stdDeviation()[i]);

    expected["bridge_index"] = bridgeIdx;
    expected["left_index"] = leftIdx;
    expected["right_index"] = rightIdx;
    expected["std_deviation"] = stdDev;

    out.addCase("nonuniform_12step_canonical_times", inputs, expected);
}

// Probe with the Size-only (unit-time) constructor.
void emitUnitTimeSteps(ReferenceWriter& out) {
    const Size n = 8;
    BrownianBridge bb(n);

    std::vector<Real> in(n);
    for (Size i = 0; i < n; ++i) in[i] = 0.1 * (i + 1);
    std::vector<Real> tmp(n);
    bb.transform(in.begin(), in.end(), tmp.begin());

    json input = json::array();
    for (Real v : in) input.push_back(v);
    json output = json::array();
    for (Real v : tmp) output.push_back(v);

    json inputs = json::object();
    inputs["n"] = n;
    inputs["input_variates"] = input;
    inputs["constructor"] = "size_only_unit_times";

    json expected = json::object();
    expected["output_variates"] = output;

    out.addCase("size_only_8step", inputs, expected);
}

} // namespace

int main() {
    ReferenceWriter out("methods/montecarlo/brownian_bridge_transform", QL_VERSION,
                        "brownian_bridge_transform_probe");

    emitUniform5Step(out);
    emitNonUniformGrid(out);
    emitUnitTimeSteps(out);

    out.write();
    return 0;
}
