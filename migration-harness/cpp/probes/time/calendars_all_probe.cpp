// migration-harness/cpp/probes/time/calendars_all_probe.cpp
//
// Emits the holiday set (2020-2030) for every sovereign/exchange calendar, so
// the Java port can cross-validate its calendar tables against C++ directly.
//
// WHY THIS EXISTS: JQuantLib previously had no calendar probe at all — its
// CalendarsTest asserts inline expectations ported from test-suite/calendars.cpp.
// That left the calendar tables uncovered, and when C++ v1.43 changed India,
// South Korea and Israel the Java port drifted SILENTLY: the suite stayed green
// while the data was wrong. The gap only surfaced because PQuantLib's probes do
// cover these calendars and the two ports disagreed. This probe closes that hole.
//
// One case per calendar, keyed by snake_case name; each case carries the
// calendar's name() and its holiday list with weekends excluded — matching the
// PQuantLib harness probe of the same name so both ports validate identically.

#include <ql/version.hpp>

#include <ql/time/calendar.hpp>
#include <ql/time/calendars/argentina.hpp>
#include <ql/time/calendars/australia.hpp>
#include <ql/time/calendars/austria.hpp>
#include <ql/time/calendars/brazil.hpp>
#include <ql/time/calendars/canada.hpp>
#include <ql/time/calendars/china.hpp>
#include <ql/time/calendars/czechrepublic.hpp>
#include <ql/time/calendars/denmark.hpp>
#include <ql/time/calendars/finland.hpp>
#include <ql/time/calendars/france.hpp>
#include <ql/time/calendars/germany.hpp>
#include <ql/time/calendars/hongkong.hpp>
#include <ql/time/calendars/hungary.hpp>
#include <ql/time/calendars/iceland.hpp>
#include <ql/time/calendars/india.hpp>
#include <ql/time/calendars/indonesia.hpp>
#include <ql/time/calendars/israel.hpp>
#include <ql/time/calendars/italy.hpp>
#include <ql/time/calendars/japan.hpp>
#include <ql/time/calendars/mexico.hpp>
#include <ql/time/calendars/newzealand.hpp>
#include <ql/time/calendars/norway.hpp>
#include <ql/time/calendars/poland.hpp>
#include <ql/time/calendars/russia.hpp>
#include <ql/time/calendars/singapore.hpp>
#include <ql/time/calendars/slovakia.hpp>
#include <ql/time/calendars/southafrica.hpp>
#include <ql/time/calendars/southkorea.hpp>
#include <ql/time/calendars/sweden.hpp>
#include <ql/time/calendars/switzerland.hpp>
#include <ql/time/calendars/taiwan.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/calendars/thailand.hpp>
#include <ql/time/calendars/turkey.hpp>
#include <ql/time/calendars/ukraine.hpp>
#include <ql/time/calendars/unitedkingdom.hpp>
#include <ql/time/calendars/unitedstates.hpp>
// new in v1.43
#include <ql/time/calendars/croatia.hpp>
#include <ql/time/calendars/malta.hpp>
#include <ql/time/calendars/montenegro.hpp>
#include <ql/time/calendars/northmacedonia.hpp>
#include <ql/time/calendars/serbia.hpp>
#include <ql/time/calendars/slovenia.hpp>
#include <ql/time/calendars/uzbekistan.hpp>

#include "common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

constexpr int kFromYear = 2020;
constexpr int kToYear = 2030;

void addCalendar(ReferenceWriter& out, const char* key, const Calendar& cal) {
    json holidays = json::array();
    const Date from(1, January, kFromYear);
    const Date to(31, December, kToYear);
    // includeWeekEnds=false: a holiday falling on what the calendar itself
    // considers a weekend is omitted, so the list also encodes the weekend rule.
    for (const auto& d : cal.holidayList(from, to, /*includeWeekEnds=*/false)) {
        holidays.push_back(json{{"y", d.year()},
                                {"m", static_cast<int>(d.month())},
                                {"d", d.dayOfMonth()}});
    }
    out.addCase(key,
                json{{"fromYear", kFromYear}, {"toYear", kToYear}},
                json{{"name", cal.name()}, {"holidays", holidays}});
}

} // namespace

int main() {
    ReferenceWriter out("time/calendars/all", QL_VERSION, "calendars_all_probe");

    addCalendar(out, "argentina", Argentina());
    addCalendar(out, "australia", Australia());
    addCalendar(out, "austria", Austria());
    addCalendar(out, "brazil", Brazil());
    addCalendar(out, "canada", Canada());
    addCalendar(out, "china", China());
    addCalendar(out, "czech_republic", CzechRepublic());
    addCalendar(out, "denmark", Denmark());
    addCalendar(out, "finland", Finland());
    addCalendar(out, "france", France());
    addCalendar(out, "germany", Germany());
    addCalendar(out, "hong_kong", HongKong());
    addCalendar(out, "hungary", Hungary());
    addCalendar(out, "iceland", Iceland());
    addCalendar(out, "india", India());
    addCalendar(out, "indonesia", Indonesia());
    addCalendar(out, "israel", Israel());
    addCalendar(out, "italy", Italy());
    addCalendar(out, "japan", Japan());
    addCalendar(out, "mexico", Mexico());
    addCalendar(out, "new_zealand", NewZealand());
    addCalendar(out, "norway", Norway());
    addCalendar(out, "poland", Poland());
    addCalendar(out, "russia", Russia());
    addCalendar(out, "singapore", Singapore());
    addCalendar(out, "slovakia", Slovakia());
    addCalendar(out, "south_africa", SouthAfrica());
    addCalendar(out, "south_korea", SouthKorea());
    addCalendar(out, "sweden", Sweden());
    addCalendar(out, "switzerland", Switzerland());
    addCalendar(out, "taiwan", Taiwan());
    addCalendar(out, "target", TARGET());
    addCalendar(out, "thailand", Thailand());
    addCalendar(out, "turkey", Turkey());
    addCalendar(out, "ukraine", Ukraine());
    addCalendar(out, "united_kingdom", UnitedKingdom());
    addCalendar(out, "united_states", UnitedStates(UnitedStates::Settlement));
    // new in v1.43
    addCalendar(out, "croatia", Croatia());
    addCalendar(out, "malta", Malta());
    addCalendar(out, "montenegro", Montenegro());
    addCalendar(out, "north_macedonia", NorthMacedonia());
    addCalendar(out, "serbia", Serbia());
    addCalendar(out, "slovenia", Slovenia());
    addCalendar(out, "uzbekistan", Uzbekistan());

    out.write();
    return 0;
}
