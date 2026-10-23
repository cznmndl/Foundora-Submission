package com.foundora.dsa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvertedIndexTest {

    private InvertedIndex<Integer> index() {
        InvertedIndex<Integer> index = new InvertedIndex<>();
        index.add(1, "Blue umbrella", "Folding, wooden handle", "Library");
        index.add(2, "Black umbrella", "Big golf umbrella", "Gym");
        index.add(3, "Blue Hydro Flask", "Stickers on the side", "Canteen");
        return index;
    }

    @Test
    void everyWordMustMatch() {
        assertThat(index().search("blue")).containsExactlyInAnyOrder(1, 3);
        assertThat(index().search("blue umbrella")).containsExactly(1);
        assertThat(index().search("umbrella gym")).containsExactly(2);
        assertThat(index().search("blue gym")).isEmpty();
    }

    @Test
    void wordsMatchByPrefixAndIgnoreCaseAndPunctuation() {
        assertThat(index().search("UMB")).containsExactlyInAnyOrder(1, 2);
        assertThat(index().search("hydro-flask!")).containsExactly(3);
        assertThat(index().search("  ")).isEmpty();
    }

    @Test
    void removingAndReAddingAPostUpdatesItsWords() {
        InvertedIndex<Integer> index = index();
        index.remove(1);
        assertThat(index.search("wooden")).isEmpty();
        assertThat(index.search("umbrella")).containsExactly(2);

        index.add(2, "Red umbrella");
        assertThat(index.search("golf")).isEmpty();
        assertThat(index.search("red")).containsExactly(2);
        assertThat(index.postCount()).isEqualTo(2);
    }

    @Test
    void suggestsIndexedWords() {
        assertThat(index().suggest("bl", 5)).containsExactly("black", "blue");
        assertThat(index().suggest("st", 5)).containsExactly("stickers");
    }
}
