package org.lbl.system.log.risk.support;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowDetectorTest {
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 5, 9, 0);

    @Test
    void exactWindowBoundaryIsIncluded() {
        List<Event> events = List.of(event(1, 0), event(2, 2), event(3, 4), event(4, 6), event(5, 10));

        var matches = SlidingWindowDetector.countBursts(
                events, Event::group, Event::time, Duration.ofMinutes(10), 5);

        assertEquals(1, matches.size());
        assertEquals(5, matches.get(0).events().size());
    }

    @Test
    void eventOutsideWindowDoesNotTrigger() {
        List<Event> events = List.of(event(1, 0), event(2, 2), event(3, 4), event(4, 6), event(5, 11));

        var matches = SlidingWindowDetector.countBursts(
                events, Event::group, Event::time, Duration.ofMinutes(10), 5);

        assertTrue(matches.isEmpty());
    }

    @Test
    void overlappingMatchesAreMergedIntoOneBurst() {
        List<Event> events = new ArrayList<>();
        for (int index = 0; index < 8; index++) events.add(event(index + 1, index));

        var matches = SlidingWindowDetector.countBursts(
                events, Event::group, Event::time, Duration.ofMinutes(10), 5);

        assertEquals(1, matches.size());
        assertEquals(8, matches.get(0).events().size());
    }

    @Test
    void distinctWindowCountsDifferentValuesOnly() {
        List<Event> events = List.of(
                event(1, 0, "a"), event(2, 1, "a"), event(3, 2, "b"),
                event(4, 3, "c"), event(5, 4, "d"));

        var matches = SlidingWindowDetector.distinctBursts(
                events, Event::group, Event::time, Event::value, Duration.ofMinutes(10), 4);

        assertEquals(1, matches.size());
        assertEquals(5, matches.get(0).events().size());
    }

    private Event event(long id, long minute) {
        return event(id, minute, String.valueOf(id));
    }

    private Event event(long id, long minute, String value) {
        return new Event(id, "same", value, BASE.plusMinutes(minute));
    }

    private record Event(long id, String group, String value, LocalDateTime time) {
    }
}
