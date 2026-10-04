package com.foundora.dsa;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An inverted index, the same idea search engines use: instead of storing "post -> its words", it stores
 * "word -> the set of posts containing it". The search bar uses it to find posts by item name, description
 * and location.
 *
 * <p><b>Data structure:</b> a {@link HashMap} from each word to a {@link HashSet} of post IDs (the word's
 * "posting list"), plus a {@link PrefixTrie} of all words so a search word also matches longer words that
 * start with it ("umb" finds "umbrella").
 *
 * <p><b>Search:</b> every search word must match (AND). For each search word we take the union of the
 * posting lists of all indexed words starting with it. Then we intersect those sets, <i>smallest first</i>:
 * the result can never be bigger than the smallest set, and each membership check in a hash set is O(1).
 *
 * <p><b>Complexity:</b> for k search words where the smallest matching set has m posts, the intersection is
 * O(k · m). Without the index, search would read every word of every post: O(n · total words).
 * Indexing one post with w words is O(w · L) for words of length L.
 *
 * @param <ID> the post identifier type
 */
public final class InvertedIndex<ID> {

    private final Map<String, Set<ID>> postings = new HashMap<>();
    private final Map<ID, Set<String>> wordsByPost = new HashMap<>(); // reverse map, so removal is cheap
    private final PrefixTrie vocabulary = new PrefixTrie();

    /** Splits text into normalized words: lower case, no accents, letters and digits only. */
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

    /** Indexes every word in {@code texts} for this post. Re-adding a post replaces its old words. */
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

    /** Removes a post from every posting list it is in: O(w) for its w words. */
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

    /** Posts containing every word of {@code query} (each as a whole word or a word prefix). */
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
                return Set.of(); // one word matches nothing, so nothing matches every word
            }
            matchesPerWord.add(matches);
        }

        // Intersect smallest-first: the answer can only shrink, and starting small keeps every step cheap.
        matchesPerWord.sort(Comparator.comparingInt(Set::size));
        Set<ID> result = new HashSet<>(matchesPerWord.get(0));
        for (int i = 1; i < matchesPerWord.size() && !result.isEmpty(); i++) {
            result.retainAll(matchesPerWord.get(i));
        }
        return result;
    }

    /** Indexed words starting with {@code prefix}, for search suggestions. */
    public List<String> suggest(String prefix, int limit) {
        return vocabulary.withPrefix(prefix, limit);
    }

    public int postCount() {
        return wordsByPost.size();
    }
}
