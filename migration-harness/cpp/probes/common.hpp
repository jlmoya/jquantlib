// migration-harness/cpp/probes/common.hpp
// Helper for probes to emit reference JSON files in the canonical schema
// defined in docs/migration/phase1-design.md §5.4.

#ifndef JQUANTLIB_HARNESS_COMMON_HPP
#define JQUANTLIB_HARNESS_COMMON_HPP

#ifndef JQML_QL_COMMIT_SHA
#error "JQML_QL_COMMIT_SHA must be defined by the build system (see CMakeLists.txt)."
#endif

#include <nlohmann/json.hpp>
#include <chrono>
#include <fstream>
#include <sstream>
#include <string>
#include <filesystem>
#include <vector>

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
        cases_.push_back(Case{name, std::move(inputs), std::move(expected)});
    }

    // Write to <harness_root>/references/<test_group>.json
    // Assumes the process cwd is the harness root (setup.sh / generate-references.sh
    // always cd there before running probes).
    void write() const {
        namespace fs = std::filesystem;
        const fs::path out = fs::path("references") / (test_group_ + ".json");
        fs::create_directories(out.parent_path());

        json cases_array = json::array();
        for (const auto& c : cases_) {
            cases_array.push_back({
                {"name", c.name},
                {"inputs", c.inputs},
                {"expected", c.expected}
            });
        }

        json doc = {
            {"test_group", test_group_},
            {"cpp_version", cpp_version_},
            {"cpp_commit", JQML_QL_COMMIT_SHA},
            {"generated_at", utcNow()},
            {"generated_by", generated_by_},
            {"cases", cases_array}
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
    struct Case {
        std::string name;
        json inputs;
        json expected;
    };

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
    std::vector<Case> cases_;
};

} // namespace jqml_harness

#endif // JQUANTLIB_HARNESS_COMMON_HPP
