package com.foundora.dsa;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class InvertedIndex<ID> {

    private final Map<String, Set<ID>> postings = new HashMap<>();
    private final Map<ID, Set<String>> wordsByPost = new HashMap<>();
    private final PrefixTrie vocabulary = new PrefixTrie();

    public static List<String> tokenize(String text) {
        List<String> words = new ArrayList<>();
        if (text == null) {
            return words;
        }
        for (String word : PrefixTrie.normalize(text).split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return words;
    }

    public void add(ID post, String... texts) {
        remove(post);
        Set<String> words = new HashSet<>();
        for (String text : texts) {
            words.addAll(tokenize(text));
        }
        for (String word : words) {
            Set<ID> list = postings.computeIfAbsent(word, w -> new HashSet<>());
            if (list.isEmpty()) {
                vocabulary.add(word, word);
            }
            list.add(post);
        }
        wordsByPost.put(post, words);
    }

    public void remove(ID post) {
        Set<String> words = wordsByPost.remove(post);
        if (words == null) {
            return;
        }
        for (String word : words) {
            Set<ID> list = postings.get(word);
            list.remove(post);
            if (list.isEmpty()) {
                postings.remove(word);
                vocabulary.remove(word, word);
            }
        }
    }

    public Set<ID> search(String query) {
        Set<String> queryWords = new LinkedHashSet<>(tokenize(query));
        if (queryWords.isEmpty()) {
            return Set.of();
        }

        List<Set<ID>> matchesPerWord = new ArrayList<>();
        for (String queryWord : queryWords) {
            Set<ID> matches = new HashSet<>();
            for (String indexedWord : vocabulary.keysWithPrefix(queryWord)) {
                matches.addAll(postings.get(indexedWord));
            }
            if (matches.isEmpty()) {
                return Set.of();
            }
            matchesPerWord.add(matches);
        }

        matchesPerWord.sort(Comparator.comparingInt(Set::size));
        Set<ID> result = new HashSet<>(matchesPerWord.get(0));
        for (int i = 1; i < matchesPerWord.size() && !result.isEmpty(); i++) {
            result.retainAll(matchesPerWord.get(i));
        }
        return result;
    }

    public List<String> suggest(String prefix, int limit) {
        return vocabulary.withPrefix(prefix, limit);
    }

    public int postCount() {
        return wordsByPost.size();
    }
}
