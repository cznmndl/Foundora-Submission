package com.foundora.dsa;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SortedInsertTest {

    @Test
    void insertsAtTheRightPlace() {
        List<Integer> list = new ArrayList<>(List.of(1, 3, 5, 7));

        assertThat(SortedInsert.insert(list, 4, Comparator.naturalOrder())).isEqualTo(2);
        assertThat(SortedInsert.insert(list, 0, Comparator.naturalOrder())).isEqualTo(0);
        assertThat(SortedInsert.insert(list, 9, Comparator.naturalOrder())).isEqualTo(6);
        assertThat(list).containsExactly(0, 1, 3, 4, 5, 7, 9);
    }

    @Test
    void putsEqualElementsAfterExistingOnes() {
        record Post(String name, int day) {
        }
        List<Post> list = new ArrayList<>(List.of(new Post("a", 1), new Post("b", 2), new Post("c", 2)));

        SortedInsert.insert(list, new Post("new", 2), Comparator.comparingInt(Post::day));

        assertThat(list).extracting(Post::name).containsExactly("a", "b", "c", "new");
    }

    @Test
    void worksOnAnEmptyList() {
        List<Integer> list = new ArrayList<>();
        SortedInsert.insert(list, 1, Comparator.naturalOrder());
        assertThat(list).containsExactly(1);
    }
}
