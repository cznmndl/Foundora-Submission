package com.foundora.dsa;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A trie (prefix tree): each node is one character, and a path from the root spells a key. All keys that
 * start with the same letters share the same path, so "every key starting with <i>umb</i>" is simply
 * "everything below the node reached by u-m-b".
 *
 * <p>Used for search-as-you-type (prefixes of words in reports) and location suggestions.
 *
 * <p><b>Complexity:</b> adding or removing a key of length L is O(L). Finding keys with a prefix of length L
 * is O(L) to walk down to the prefix node, then a depth-first walk below it that stops as soon as
 * {@code limit} results are found. Compare: checking every stored key would be O(n · L).
 *
 * <p>Children are kept in a {@link TreeMap}, so results come out in alphabetical order. A key can stand for
 * several display texts (the key "building" for both "Main Building" and "Senior High Building"), and each
 * display text counts how many times it was added, so a place used by two posts stays until both are removed.
 */
public final class PrefixTrie {

    private static final class Node {
        final Map<Character, Node> children = new TreeMap<>();
        /** Text shown for this key -> how many times it was added. Empty means this node is not a key. */
        final Map<String, Integer> displays = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        boolean isKey() {
            return !displays.isEmpty();
        }
    }

    private final Node root = new Node();
    private int size;

    /** Lower-cases and strips accents, so "Café" and "cafe" share a key. */
    public static String normalize(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    public void add(String word) {
        add(normalize(word), word);
    }

    /** Adds {@code key} (already normalized), shown to users as {@code display}. O(L). */
    public void add(String key, String display) {
        if (key.isEmpty()) {
            return;
        }
        Node node = root;
        for (int i = 0; i < key.length(); i++) {
            node = node.children.computeIfAbsent(key.charAt(i), c -> new Node());
        }
        if (!node.isKey()) {
            size++;
        }
        node.displays.merge(display, 1, Integer::sum);
    }

    /** Removes one use of {@code key} shown as {@code display}. O(L). */
    public void remove(String key, String display) {
        Node node = find(key);
        if (node == null || !node.displays.containsKey(display)) {
            return;
        }
        node.displays.computeIfPresent(display, (d, count) -> count > 1 ? count - 1 : null);
        if (!node.isKey()) {
            size--;
        }
    }

    public boolean contains(String key) {
        Node node = find(key);
        return node != null && node.isKey();
    }

    /** Up to {@code limit} display values of keys starting with {@code prefix}, alphabetically, no duplicates. */
    public List<String> withPrefix(String prefix, int limit) {
        Node start = find(normalize(prefix));
        Set<String> results = new LinkedHashSet<>();
        if (start != null) {
            collect(start, results, limit);
        }
        return new ArrayList<>(results);
    }

    /** Raw keys (not display values) starting with {@code prefix}, used to expand a search word. */
    public List<String> keysWithPrefix(String normalizedPrefix) {
        List<String> keys = new ArrayList<>();
        Node start = find(normalizedPrefix);
        if (start != null) {
            collectKeys(start, new StringBuilder(normalizedPrefix), keys);
        }
        return keys;
    }

    public int size() {
        return size;
    }

    private Node find(String key) {
        Node node = root;
        for (int i = 0; i < key.length() && node != null; i++) {
            node = node.children.get(key.charAt(i));
        }
        return node;
    }

    private static void collect(Node node, Set<String> results, int limit) {
        if (results.size() >= limit) {
            return;
        }
        for (String display : node.displays.keySet()) {
            if (results.size() >= limit) {
                return;
            }
            results.add(display);
        }
        for (Node child : node.children.values()) {
            if (results.size() >= limit) {
                return;
            }
            collect(child, results, limit);
        }
    }

    private static void collectKeys(Node node, StringBuilder path, List<String> keys) {
        if (node.isKey()) {
            keys.add(path.toString());
        }
        for (Map.Entry<Character, Node> child : node.children.entrySet()) {
            path.append(child.getKey());
            collectKeys(child.getValue(), path, keys);
            path.setLength(path.length() - 1);
        }
    }
}
