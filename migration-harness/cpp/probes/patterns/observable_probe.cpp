// migration-harness/cpp/probes/patterns/observable_probe.cpp
// Reference values for Observable/Observer notification behaviour vs QuantLib C++ v1.42.1.
#include <ql/version.hpp>
#include <ql/patterns/observable.hpp>
#include <memory>
#include "../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

struct Flag {
    int count = 0;
};

class CountingObserver : public Observer {
public:
    explicit CountingObserver(Flag* f) : flag_(f) {}
    void update() override { ++flag_->count; }
private:
    Flag* flag_;
};

} // namespace

int main() {
    ReferenceWriter out("patterns/observable", QL_VERSION, "observable_probe");

    // Case 1: single notify -> single update
    {
        auto obs = ext::make_shared<Observable>();
        Flag f{0};
        auto observer = std::make_shared<CountingObserver>(&f);
        observer->registerWith(obs);
        obs->notifyObservers();
        out.addCase("singleNotify",
                    json{{"observers", 1}, {"notifies", 1}},
                    json(f.count));
    }

    // Case 2: deregister then notify -> no update
    {
        auto obs = ext::make_shared<Observable>();
        Flag f{0};
        auto observer = std::make_shared<CountingObserver>(&f);
        observer->registerWith(obs);
        observer->unregisterWith(obs);
        obs->notifyObservers();
        out.addCase("deregisteredThenNotify",
                    json{{"observers_registered", 1}, {"observers_unregistered", 1}, {"notifies", 1}},
                    json(f.count));
    }

    // Case 3: multiple notifies accumulate
    {
        auto obs = ext::make_shared<Observable>();
        Flag f{0};
        auto observer = std::make_shared<CountingObserver>(&f);
        observer->registerWith(obs);
        for (int i = 0; i < 5; ++i) obs->notifyObservers();
        out.addCase("multipleNotify",
                    json{{"observers", 1}, {"notifies", 5}},
                    json(f.count));
    }

    out.write();
    return 0;
}
