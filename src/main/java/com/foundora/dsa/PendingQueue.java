package com.foundora.dsa;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A first-in, first-out (FIFO) queue of reports waiting for review: the report submitted first is reviewed
 * first, so nobody's report waits behind newer ones.
 *
 * <p><b>Data structure:</b> {@link ArrayDeque}, a resizable circular array. Adding at the back and taking
 * from the front just move an index, with no shifting.
 *
 * <p><b>Complexity:</b> {@code enqueue}, {@code peekNext} and {@code next} are O(1) (enqueue is amortized
 * O(1) because the array occasionally doubles). Building the queue from n reports is O(n).
 *
 * <p>The database stays the source of truth: the admin page builds this queue from the pending reports,
 * oldest first, on each request. It's a FIFO view of the data, not a second copy that could fall out of sync.
 *
 * @param <T> the queued item type
 */
public final class PendingQueue<T> {

    private final Deque<T> queue = new ArrayDeque<>();

    /** Builds a queue from items already ordered oldest first. */
    public static <T> PendingQueue<T> ofOldestFirst(List<T> oldestFirst) {
        PendingQueue<T> pending = new PendingQueue<>();
        for (T item : oldestFirst) {
            pending.enqueue(item);
        }
        return pending;
    }

    /** Adds to the back of the queue. */
    public void enqueue(T item) {
        queue.addLast(item);
    }

    /** The item that has waited longest, without removing it; null when empty. */
    public T peekNext() {
        return queue.peekFirst();
    }

    /** Removes and returns the item that has waited longest; null when empty. */
    public T next() {
        return queue.pollFirst();
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /** Snapshot in queue order (front first), for display. */
    public List<T> inOrder() {
        return new ArrayList<>(queue);
    }
}
