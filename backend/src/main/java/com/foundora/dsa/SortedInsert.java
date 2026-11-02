package com.foundora.dsa;

import java.util.Comparator;
import java.util.List;

// Binary search (upper bound) for where a new post belongs in an already-sorted list.
// O(log n) comparisons to find the spot, instead of re-sorting the whole list for every new post.
/**
 * Binary search insertion: adds one element to an already-sorted list without re-sorting it.
 * Used when an admin approves a single report and it joins the feed index.
 *
 * <p><b>How it works:</b> binary search halves the search range each step to find the first position whose
 * element is greater than the new one, then the element is inserted there.
 *
 * <p><b>Complexity:</b> O(log n) comparisons to find the position, plus O(n) to shift the later elements
 * in an array-backed list. Re-sorting the whole list instead would cost O(n log n).
 *
 * <p>The search finds the <i>upper bound</i> (after any equal elements), so equal elements keep their
 * arrival order, the same stability guarantee as {@link DateMergeSort}.
 */
public final class SortedInsert {

    private SortedInsert() {
    }

    /** Inserts {@code value} into {@code sorted} (an array-backed list) and returns the index it went to. */
    public static <T> int insert(List<T> sorted, T value, Comparator<? super T> order) {
        int position = upperBound(sorted, value, order);
        sorted.add(position, value);
        return position;
    }

    /** Index of the first element that sorts strictly after {@code value}, or size() if none. */
    public static <T> int upperBound(List<T> sorted, T value, Comparator<? super T> order) {
        int lo = 0;
        int hi = sorted.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (order.compare(sorted.get(mid), value) <= 0) {
                lo = mid + 1; // mid sorts at or before value: answer is to the right
            } else {
                hi = mid;     // mid sorts after value: answer is mid or to the left
            }
        }
        return lo;
    }
}
