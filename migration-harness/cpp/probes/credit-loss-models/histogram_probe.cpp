// migration-harness/cpp/probes/credit-loss-models/histogram_probe.cpp
// Phase 4m.7c — emit C++ v1.42.1 reference values for QuantLib::Histogram
// (declared in ql/math/statistics/histogram.hpp).
//
// Histogram is a thin classifier: given a data set + the number of bins
// (or an Algorithm to derive the count), it returns counts and frequencies
// per bin. We round-trip:
//   * fixed-bin construction:   Histogram(begin, end, breaks)
//   * Sturges algorithm:        Histogram(begin, end, Sturges)
//
// Output schema per case:
//   inputs:   { data: [...], bins: <int>, algorithm: "fixed" | "Sturges" }
//   expected: { bins: <int>, breaks: [...], counts: [...], frequency: [...] }

#include <ql/version.hpp>
#include <ql/math/statistics/histogram.hpp>
#include "../common.hpp"

#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

void emit_fixed(ReferenceWriter& w,
                const std::string& name,
                const std::vector<double>& data,
                std::size_t breaks_n) {
    Histogram h(data.begin(), data.end(), breaks_n);
    json counts = json::array();
    json freq   = json::array();
    json brks   = json::array();
    for (std::size_t i = 0; i < h.bins(); ++i) {
        counts.push_back(static_cast<int>(h.counts(i)));
        freq.push_back(h.frequency(i));
    }
    for (double b : h.breaks()) brks.push_back(b);
    w.addCase(name,
              json{{"data", data}, {"bins", static_cast<int>(breaks_n)},
                   {"algorithm", "fixed"}},
              json{{"bins", static_cast<int>(h.bins())},
                   {"breaks", brks},
                   {"counts", counts},
                   {"frequency", freq}});
}

void emit_sturges(ReferenceWriter& w,
                  const std::string& name,
                  const std::vector<double>& data) {
    Histogram h(data.begin(), data.end(), Histogram::Sturges);
    json counts = json::array();
    json freq   = json::array();
    json brks   = json::array();
    for (std::size_t i = 0; i < h.bins(); ++i) {
        counts.push_back(static_cast<int>(h.counts(i)));
        freq.push_back(h.frequency(i));
    }
    for (double b : h.breaks()) brks.push_back(b);
    w.addCase(name,
              json{{"data", data}, {"algorithm", "Sturges"}},
              json{{"bins", static_cast<int>(h.bins())},
                   {"breaks", brks},
                   {"counts", counts},
                   {"frequency", freq}});
}

} // namespace

int main() {
    ReferenceWriter w("credit-loss-models/histogram",
                      QL_VERSION,
                      "histogram_probe.cpp (Phase 4m.7c)");

    // --- fixed-bin tests ---------------------------------------------------
    emit_fixed(w, "fixed_uniform_5buckets_5",
               std::vector<double>{1, 2, 3, 4, 5}, 4);

    emit_fixed(w, "fixed_clustered_3buckets_10",
               std::vector<double>{1.0, 1.1, 1.2, 5.0, 5.1, 5.2,
                                   9.0, 9.1, 9.2, 9.3}, 2);

    emit_fixed(w, "fixed_singleton_bin",
               std::vector<double>{0.5, 1.5, 2.5, 3.5, 4.5,
                                   5.5, 6.5, 7.5, 8.5, 9.5}, 4);

    // --- Sturges-algorithm tests ------------------------------------------
    {
        // 32-point series: ceil(log2(32) + 1) = ceil(6.0) = 6 bins
        std::vector<double> data;
        for (int i = 0; i < 32; ++i) data.push_back(0.1 * i);
        emit_sturges(w, "sturges_32pts_linear", data);
    }
    {
        // 17-point series: ceil(log2(17) + 1) = ceil(5.087) = 6 bins
        std::vector<double> data;
        for (int i = 0; i < 17; ++i) data.push_back(static_cast<double>(i));
        emit_sturges(w, "sturges_17pts_linear", data);
    }

    w.write();
    return 0;
}
