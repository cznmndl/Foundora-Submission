package com.foundora.dsa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

// Merge sort: sorts each half, then merges them. O(n log n) in every case, and stable: equal dates keep their order.
// Sorts all feed posts newest first when the feed index is rebuilt; already-ordered halves skip the merge.
/**
 * Merge sort, written out by hand. Used to put feed posts in date order when the feed index is rebuilt.
 *
 * <p><b>How it works:</b> split the array in half, sort each half recursively, then merge the two sorted
 * halves by repeatedly taking the smaller front element.
 *
 * <p><b>Complexity:</b> O(n log n) time in every case (the halving gives log n levels, and each level
 * merges n elements), O(n) extra space for the helper array.
 *
 * <p><b>Stable:</b> when two posts compare equal (same timestamp), the merge takes the one from the left half
 * first, so equal posts keep the order they arrived in.
 *
 * <p>Small optimisation: if the last element of the left half is already &lt;= the first of the right half,
 * the two halves are already in order and the merge is skipped. Already-sorted input then costs only O(n).
 */
public final class DateMergeSort {

    private DateMergeSort() {
    }

    /** Returns a new sorted list; the input is not modified. */
    public static <T> List<T> sort(List<T> input, Comparator<? super T> order) {
        @SuppressWarnings("unchecked")
        T[] items = (T[]) input.toArray();
        T[] helper = Arrays.copyOf(items, items.length);
        sort(items, helper, 0, items.length, order);
        return new ArrayList<>(Arrays.asList(items));
    }

    /** Sorts items[lo, hi). */
    private static <T> void sort(T[] items, T[] helper, int lo, int hi, Comparator<? super T> order) {
        if (hi - lo < 2) {
            return; // 0 or 1 element: already sorted
        }
        int mid = (lo + hi) >>> 1;
        sort(items, helper, lo, mid, order);
        sort(items, helper, mid, hi, order);
        if (order.compare(items[mid - 1], items[mid]) <= 0) {
            return; // halves already in order
        }
        merge(items, helper, lo, mid, hi, order);
    }

    private static <T> void merge(T[] items, T[] helper, int lo, int mid, int hi, Comparator<? super T> order) {
        System.arraycopy(items, lo, helper, lo, hi - lo);
        int left = lo;
        int right = mid;
        for (int k = lo; k < hi; k++) {
            if (left == mid) {
                items[k] = helper[right++];
            } else if (right == hi) {
                items[k] = helper[left++];
            } else if (order.compare(helper[right], helper[left]) < 0) {
                items[k] = helper[right++]; // strictly smaller: take from the right
            } else {
                items[k] = helper[left++];  // ties take from the left: this is what makes it stable
            }
        }
    }
}
