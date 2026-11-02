package com.foundora.dsa;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

// Trie (prefix tree): one node per letter, so every word that starts with what the user typed sits under one node.
// Used for search suggestions; finding a prefix of length m takes O(m), plus the matches it returns.
public final class PrefixTrie {

    private static final class Node {
        final Map<Character, Node> children = new TreeMap<>();
        final Map<String, Integer> displays = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        boolean isKey() {
            return !displays.isEmpty();
        }
    }

    private final Node root = new Node();
    private int size;

    public static String normalize(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    public void add(String word) {
        add(normalize(word), word);
    }

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

    public List<String> withPrefix(String prefix, int limit) {
        Node start = find(normalize(prefix));
        Set<String> results = new LinkedHashSet<>();
        if (start != null) {
            collect(start, results, limit);
        }
        return new ArrayList<>(results);
    }

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
