package com.foundora.dsa;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PendingQueueTest {

    @Test
    void firstInFirstOut() {
        PendingQueue<String> queue = PendingQueue.ofOldestFirst(List.of("oldest", "middle"));
        queue.enqueue("newest");

        assertThat(queue.size()).isEqualTo(3);
        assertThat(queue.peekNext()).isEqualTo("oldest");
        assertThat(queue.next()).isEqualTo("oldest");
        assertThat(queue.inOrder()).containsExactly("middle", "newest");
        assertThat(queue.next()).isEqualTo("middle");
        assertThat(queue.next()).isEqualTo("newest");
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.next()).isNull();
        assertThat(queue.peekNext()).isNull();
    }
}
