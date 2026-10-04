package com.foundora.dsa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrefixTrieTest {

    @Test
    void findsKeysByPrefixAlphabeticallyIgnoringCaseAndAccents() {
        PrefixTrie trie = new PrefixTrie();
        trie.add("Library");
        trie.add("Lobby");
        trie.add("Café");
        trie.add("Canteen");

        assertThat(trie.withPrefix("l", 10)).containsExactly("Library", "Lobby");
        assertThat(trie.withPrefix("LIB", 10)).containsExactly("Library");
        assertThat(trie.withPrefix("cafe", 10)).containsExactly("Café");
        assertThat(trie.withPrefix("ca", 10)).containsExactly("Café", "Canteen");
        assertThat(trie.withPrefix("x", 10)).isEmpty();
        assertThat(trie.withPrefix("", 2)).hasSize(2);
    }

    @Test
    void oneKeyCanStandForSeveralPlaces() {
        PrefixTrie trie = new PrefixTrie();
        trie.add("building", "Main Building");
        trie.add("building", "Senior High Building");

        assertThat(trie.withPrefix("bui", 10)).containsExactly("Main Building", "Senior High Building");
    }

    @Test
    void countsUsesSoAKeyStaysUntilEveryUseIsRemoved() {
        PrefixTrie trie = new PrefixTrie();
        trie.add("gym", "Gym");
        trie.add("gym", "Gym");
        assertThat(trie.size()).isEqualTo(1);

        trie.remove("gym", "Gym");
        assertThat(trie.contains("gym")).isTrue();
        trie.remove("gym", "Gym");
        assertThat(trie.contains("gym")).isFalse();
        assertThat(trie.size()).isZero();
        assertThat(trie.withPrefix("g", 10)).isEmpty();
    }

    @Test
    void respectsTheLimitAndListsRawKeys() {
        PrefixTrie trie = new PrefixTrie();
        for (String word : new String[]{"umbrella", "umbrellas", "umpire", "under"}) {
            trie.add(word);
        }
        assertThat(trie.withPrefix("um", 2)).containsExactly("umbrella", "umbrellas");
        assertThat(trie.keysWithPrefix("umb")).containsExactly("umbrella", "umbrellas");
    }
}
