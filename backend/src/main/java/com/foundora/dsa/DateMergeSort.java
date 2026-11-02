package com.foundora.dsa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

// Merge sort: sorts each half, then merges them. O(n log n) in every case, and stable: equal dates keep their order.
// Sorts all feed posts newest first when the feed index is rebuilt; already-ordered halves skip the merge.
public final class DateMergeSort {

    private DateMergeSort() {
    }

    public static <T> List<T> sort(List<T> input, Comparator<? super T> order) {
        @SuppressWarnings("unchecked")
        T[] items = (T[]) input.toArray();
        T[] helper = Arrays.copyOf(items, items.length);
        sort(items, helper, 0, items.length, order);
        return new ArrayList<>(Arrays.asList(items));
    }

    private static <T> void sort(T[] items, T[] helper, int lo, int hi, Comparator<? super T> order) {
        if (hi - lo < 2) {
            return;
        }
        int mid = (lo + hi) >>> 1;
        sort(items, helper, lo, mid, order);
        sort(items, helper, mid, hi, order);
        if (order.compare(items[mid - 1], items[mid]) <= 0) {
            return;
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
                items[k] = helper[right++];
            } else {
                items[k] = helper[left++];
            }
        }
    }
}
