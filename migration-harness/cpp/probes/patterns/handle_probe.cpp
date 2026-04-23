// migration-harness/cpp/probes/patterns/handle_probe.cpp
// Reference values for Handle/RelinkableHandle behaviour vs QuantLib C++ v1.42.1.
#include <ql/version.hpp>
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
        auto q = ext::make_shared<SimpleQuote>(1.23);
        Handle<Quote> h(q);
        out.addCase("basicValue",
                    json{{"initialValue", 1.23}},
                    json(h->value()));
    }

    // Case 2: Relinkable handle tracks relinking.
    {
        auto q1 = ext::make_shared<SimpleQuote>(1.00);
        auto q2 = ext::make_shared<SimpleQuote>(2.00);
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
