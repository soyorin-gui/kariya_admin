package org.lbl.system.log.risk.support;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 通用滑动窗口检测器。
 *
 * <p>返回的是相互重叠命中窗口的并集，因此连续 8 次失败只生成一个窗口，而不是在第 5、6、7、8
 * 次各生成一条重复风险。窗口边界为闭区间，恰好相差 window 的两个事件仍属于同一窗口。</p>
 */
public final class SlidingWindowDetector {
    private SlidingWindowDetector() {
    }

    public static <E, K> List<WindowMatch<E>> countBursts(
            List<E> events,
            Function<E, K> groupBy,
            Function<E, LocalDateTime> timestamp,
            Duration window,
            int threshold) {
        return detect(events, groupBy, timestamp, window, threshold, null);
    }

    public static <E, K, V> List<WindowMatch<E>> distinctBursts(
            List<E> events,
            Function<E, K> groupBy,
            Function<E, LocalDateTime> timestamp,
            Function<E, V> distinctBy,
            Duration window,
            int distinctThreshold) {
        return detect(events, groupBy, timestamp, window, distinctThreshold, distinctBy);
    }

    private static <E, K, V> List<WindowMatch<E>> detect(
            List<E> events,
            Function<E, K> groupBy,
            Function<E, LocalDateTime> timestamp,
            Duration window,
            int threshold,
            Function<E, V> distinctBy) {
        validate(window, threshold);
        Map<K, List<E>> groups = new LinkedHashMap<>();
        for (E event : events == null ? List.<E>of() : events) {
            if (event == null || timestamp.apply(event) == null) continue;
            K key = groupBy.apply(event);
            if (key != null) groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(event);
        }

        List<WindowMatch<E>> matches = new ArrayList<>();
        groups.forEach((key, grouped) -> {
            grouped.sort(Comparator.comparing(timestamp));
            List<Interval> intervals = distinctBy == null
                    ? countIntervals(grouped, timestamp, window, threshold)
                    : distinctIntervals(grouped, timestamp, distinctBy, window, threshold);
            for (Interval interval : intervals) {
                matches.add(new WindowMatch<>(key, List.copyOf(grouped.subList(interval.start(), interval.end() + 1))));
            }
        });
        matches.sort(Comparator.comparing(match -> timestamp.apply(match.events().get(0))));
        return List.copyOf(matches);
    }

    private static <E> List<Interval> countIntervals(List<E> events,
                                                      Function<E, LocalDateTime> timestamp,
                                                      Duration window,
                                                      int threshold) {
        List<Interval> intervals = new ArrayList<>();
        int left = 0;
        for (int right = 0; right < events.size(); right++) {
            while (left < right && outside(timestamp.apply(events.get(left)), timestamp.apply(events.get(right)), window)) {
                left++;
            }
            if (right - left + 1 >= threshold) merge(intervals, left, right);
        }
        return intervals;
    }

    private static <E, V> List<Interval> distinctIntervals(List<E> events,
                                                            Function<E, LocalDateTime> timestamp,
                                                            Function<E, V> distinctBy,
                                                            Duration window,
                                                            int threshold) {
        List<Interval> intervals = new ArrayList<>();
        Map<V, Integer> frequencies = new HashMap<>();
        int left = 0;
        for (int right = 0; right < events.size(); right++) {
            V added = distinctBy.apply(events.get(right));
            if (added != null) frequencies.merge(added, 1, Integer::sum);
            while (left < right && outside(timestamp.apply(events.get(left)), timestamp.apply(events.get(right)), window)) {
                decrement(frequencies, distinctBy.apply(events.get(left++)));
            }
            if (frequencies.size() >= threshold) merge(intervals, left, right);
        }
        return intervals;
    }

    private static boolean outside(LocalDateTime first, LocalDateTime last, Duration window) {
        return Duration.between(first, last).compareTo(window) > 0;
    }

    private static <V> void decrement(Map<V, Integer> frequencies, V value) {
        if (value == null) return;
        Integer count = frequencies.get(value);
        if (count == null || count <= 1) frequencies.remove(value);
        else frequencies.put(value, count - 1);
    }

    private static void merge(List<Interval> intervals, int start, int end) {
        if (intervals.isEmpty()) {
            intervals.add(new Interval(start, end));
            return;
        }
        Interval previous = intervals.get(intervals.size() - 1);
        if (start <= previous.end()) {
            intervals.set(intervals.size() - 1, new Interval(previous.start(), Math.max(previous.end(), end)));
        } else {
            intervals.add(new Interval(start, end));
        }
    }

    private static void validate(Duration window, int threshold) {
        Objects.requireNonNull(window, "window");
        if (window.isZero() || window.isNegative()) throw new IllegalArgumentException("window 必须大于 0");
        if (threshold < 1) throw new IllegalArgumentException("threshold 必须大于 0");
    }

    private record Interval(int start, int end) {
    }

    public record WindowMatch<E>(Object groupKey, List<E> events) {
        public WindowMatch {
            events = List.copyOf(events);
        }
    }
}
