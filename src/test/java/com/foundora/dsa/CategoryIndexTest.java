package com.foundora.dsa;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryIndexTest {

    enum Kind { KEYS, BAGS, OTHERS }

    record Post(String name, Kind kind, int day) {
    }

    private static final Comparator<Post> NEWEST_FIRST = Comparator.comparingInt(Post::day).reversed();

    @Test
    void groupsPostsByCategoryKeepingOrder() {
        CategoryIndex<Kind, Post> index = new CategoryIndex<>(Kind.class, Post::kind, NEWEST_FIRST);
        index.addAllSorted(List.of(new Post("k3", Kind.KEYS, 3), new Post("b2", Kind.BAGS, 2), new Post("k1", Kind.KEYS, 1)));

        assertThat(index.get(Kind.KEYS)).extracting(Post::name).containsExactly("k3", "k1");
        assertThat(index.get(Kind.BAGS)).extracting(Post::name).containsExactly("b2");
        assertThat(index.get(Kind.OTHERS)).isEmpty();
        assertThat(index.count(Kind.KEYS)).isEqualTo(2);
    }

    @Test
    void addsAtTheSortedPositionAndRemoves() {
        CategoryIndex<Kind, Post> index = new CategoryIndex<>(Kind.class, Post::kind, NEWEST_FIRST);
        index.addAllSorted(List.of(new Post("k5", Kind.KEYS, 5), new Post("k1", Kind.KEYS, 1)));

        index.add(new Post("k3", Kind.KEYS, 3));
        assertThat(index.get(Kind.KEYS)).extracting(Post::name).containsExactly("k5", "k3", "k1");

        assertThat(index.remove(Kind.KEYS, p -> p.name().equals("k5"))).isTrue();
        assertThat(index.remove(Kind.KEYS, p -> p.name().equals("missing"))).isFalse();
        assertThat(index.get(Kind.KEYS)).extracting(Post::name).containsExactly("k3", "k1");
    }

    @Test
    void bucketsAreReadOnlyViews() {
        CategoryIndex<Kind, Post> index = new CategoryIndex<>(Kind.class, Post::kind, NEWEST_FIRST);
        assertThatThrownBy(() -> index.get(Kind.KEYS).add(new Post("x", Kind.KEYS, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
