package org.jquantlib.showcase.service;

import java.util.List;

import org.jquantlib.time.Calendar;
import org.jquantlib.time.calendars.Japan;
import org.jquantlib.time.calendars.Target;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.jquantlib.time.calendars.UnitedStates;

/**
 * Maps friendly calendar names to JQuantLib {@link Calendar} instances, shared
 * by the bond and calendar demos.
 */
public final class ShowcaseCalendars {

    private ShowcaseCalendars() {
    }

    public static List<String> names() {
        return List.of("TARGET", "US-Settlement", "US-NYSE", "US-Government", "UK-Exchange", "Japan");
    }

    public static Calendar byName(final String name) {
        if (name == null) {
            return new Target();
        }
        return switch (name.trim().toLowerCase()) {
            case "us-settlement", "us", "unitedstates" -> new UnitedStates(UnitedStates.Market.SETTLEMENT);
            case "us-nyse", "nyse" -> new UnitedStates(UnitedStates.Market.NYSE);
            case "us-government", "us-govt", "governmentbond" -> new UnitedStates(UnitedStates.Market.GOVERNMENTBOND);
            case "uk-exchange", "uk", "unitedkingdom" -> new UnitedKingdom(UnitedKingdom.Market.Exchange);
            case "japan", "jp" -> new Japan();
            default -> new Target();
        };
    }
}
