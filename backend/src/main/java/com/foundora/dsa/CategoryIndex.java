package com.foundora.dsa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

// One list per category (an EnumMap of lists), each kept newest first, so a category filter is an O(1) lookup.
// A new post goes into its list by binary search (SortedInsert): O(log n) to find the spot, O(n) to shift.
/**
 * A hash-style index from category to the posts in that category, so the category filter doesn't scan
 * every post.
 *
 * <p><b>Data structure:</b> an {@link EnumMap}, a map whose keys are enum constants. Internally it is an array
 * indexed by the enum's ordinal, so a lookup is a direct array access: no hashing and no collisions.
 * Each bucket is a list kept in feed order (newest first).
 *
 * <p><b>Complexity:</b> finding a category's posts is O(1); reading them is O(k) for the k posts in that
 * category, instead of O(n) to scan all n posts. Adding one post is O(log k + k) via {@link SortedInsert};
 * removing one is O(k).
 *
 * @param <K> the category enum
 * @param <V> the post type
 */
public final class CategoryIndex<K extends Enum<K>, V> {

    private final Map<K, List<V>> buckets;
    private final Function<V, K> categoryOf;
    private final Comparator<? super V> order;

    public CategoryIndex(Class<K> keyType, Function<V, K> categoryOf, Comparator<? super V> order) {
        this.buckets = new EnumMap<>(keyType);
        for (K key : keyType.getEnumConstants()) {
            buckets.put(key, new ArrayList<>());
        }
        this.categoryOf = categoryOf;
        this.order = order;
    }

    /**
     * Fills the index from posts that are already sorted. Appending each post to its bucket in turn keeps
     * every bucket sorted too, so the whole build is O(n), with no sorting per bucket.
     */
    public void addAllSorted(List<V> sorted) {
        for (V post : sorted) {
            buckets.get(categoryOf.apply(post)).add(post);
        }
    }

    /** Adds one post at its sorted position in its category's bucket. */
    public void add(V post) {
        SortedInsert.insert(buckets.get(categoryOf.apply(post)), post, order);
    }

    /** Removes the first post in {@code category} that matches; returns whether one was removed. */
    public boolean remove(K category, Predicate<V> match) {
        return buckets.get(category).removeIf(match);
    }

    /** The posts in one category, in feed order. O(1) to find; the returned view is read-only. */
    public List<V> get(K category) {
        return Collections.unmodifiableList(buckets.get(category));
    }

    public int count(K category) {
        return buckets.get(category).size();
    }
}
