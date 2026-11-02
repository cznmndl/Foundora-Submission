package com.foundora.dsa;

import java.util.Comparator;
import java.util.List;

// Binary search (upper bound) for where a new post belongs in an already-sorted list.
// O(log n) comparisons to find the spot, instead of re-sorting the whole list for every new post.
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
