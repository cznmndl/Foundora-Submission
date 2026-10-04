package com.foundora.service;

import com.foundora.model.Item;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary in-memory item store; items are lost when the app restarts.
 * TODO: back this with Supabase.
 */
@Service
public class ItemService {

    private final Map<String, Item> items = new ConcurrentHashMap<>();

    public void addItem(Item item) {
        item.setId(UUID.randomUUID().toString());
        item.setApprovalStatus("PENDING");
        item.setCreatedAt(LocalDateTime.now());
        items.put(item.getId(), item);
    }

    public List<Item> getPendingItems() {
        return withApprovalStatus("PENDING");
    }

    public List<Item> getApprovedItems() {
        return withApprovalStatus("APPROVED");
    }

    public void updateItemStatus(String itemId, String status) {
        Item item = items.get(itemId);
        if (item != null) {
            item.setApprovalStatus(status);
        }
    }

    public void deleteItem(String itemId) {
        items.remove(itemId);
    }

    private List<Item> withApprovalStatus(String approvalStatus) {
        return items.values().stream()
                .filter(item -> approvalStatus.equals(item.getApprovalStatus()))
                .toList();
    }
}
