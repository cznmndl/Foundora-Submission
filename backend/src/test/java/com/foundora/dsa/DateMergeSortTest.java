package com.foundora.dsa;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class DateMergeSortTest {

    record Post(String name, int day) {
    }

    @Test
    void sortsLikeTheLibrarySortOnRandomInput() {
        Random random = new Random(42);
        for (int size = 0; size < 200; size += 7) {
            List<Integer> values = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                values.add(random.nextInt(50));
            }
            List<Integer> expected = new ArrayList<>(values);
            expected.sort(Comparator.naturalOrder());

            assertThat(DateMergeSort.sort(values, Comparator.<Integer>naturalOrder())).isEqualTo(expected);
        }
    }

    @Test
    void isStableForEqualDates() {
        List<Post> posts = List.of(new Post("a", 2), new Post("b", 1), new Post("c", 2), new Post("d", 1));

        List<Post> sorted = DateMergeSort.sort(posts, Comparator.comparingInt(Post::day));

        assertThat(sorted).extracting(Post::name).containsExactly("b", "d", "a", "c");
    }

    @Test
    void doesNotModifyTheInputAndHandlesEdgeCases() {
        List<Integer> input = new ArrayList<>(List.of(3, 1, 2));
        DateMergeSort.sort(input, Comparator.naturalOrder());
        assertThat(input).containsExactly(3, 1, 2);

        assertThat(DateMergeSort.sort(List.<Integer>of(), Comparator.naturalOrder())).isEmpty();
        assertThat(DateMergeSort.sort(List.of(5), Comparator.naturalOrder())).containsExactly(5);
        assertThat(DateMergeSort.sort(List.of(1, 2, 3), Comparator.<Integer>reverseOrder())).containsExactly(3, 2, 1);
    }
}
