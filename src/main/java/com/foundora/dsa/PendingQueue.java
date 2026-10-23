package com.foundora.dsa;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class PendingQueue<T> {

    private final Deque<T> queue = new ArrayDeque<>();

    public static <T> PendingQueue<T> ofOldestFirst(List<T> oldestFirst) {
        PendingQueue<T> pending = new PendingQueue<>();
        for (T item : oldestFirst) {
            pending.enqueue(item);
        }
        return pending;
    }

    public void enqueue(T item) {
        queue.addLast(item);
    }

    public T peekNext() {
        return queue.peekFirst();
    }

    public T next() {
        return queue.pollFirst();
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public List<T> inOrder() {
        return new ArrayList<>(queue);
    }
}
