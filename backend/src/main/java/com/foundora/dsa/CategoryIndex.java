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

    public void addAllSorted(List<V> sorted) {
        for (V post : sorted) {
            buckets.get(categoryOf.apply(post)).add(post);
        }
    }

    public void add(V post) {
        SortedInsert.insert(buckets.get(categoryOf.apply(post)), post, order);
    }

    public boolean remove(K category, Predicate<V> match) {
        return buckets.get(category).removeIf(match);
    }

    public List<V> get(K category) {
        return Collections.unmodifiableList(buckets.get(category));
    }

    public int count(K category) {
        return buckets.get(category).size();
    }
}
