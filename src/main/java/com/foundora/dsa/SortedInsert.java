package com.foundora.dsa;

import java.util.Comparator;
import java.util.List;

public final class SortedInsert {

    private SortedInsert() {
    }

    public static <T> int insert(List<T> sorted, T value, Comparator<? super T> order) {
        int position = upperBound(sorted, value, order);
        sorted.add(position, value);
        return position;
    }

    public static <T> int upperBound(List<T> sorted, T value, Comparator<? super T> order) {
        int lo = 0;
        int hi = sorted.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (order.compare(sorted.get(mid), value) <= 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
