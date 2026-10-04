package com.foundora.service;

import com.foundora.model.Item;
import com.foundora.repository.ItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Reported items, stored in the Supabase Postgres {@code items} table. */
@Service
public class ItemService {

    private final ItemRepository itemRepository;

    public ItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    public Item addItem(Item item) {
        item.setId(null);
        item.setApprovalStatus("PENDING");
        item.setCreatedAt(Instant.now());
        return itemRepository.save(item);
    }

    public List<Item> getPendingItems() {
        return itemRepository.findByApprovalStatusOrderByCreatedAtDesc("PENDING");
    }

    public List<Item> getApprovedItems() {
        return itemRepository.findByApprovalStatusOrderByCreatedAtDesc("APPROVED");
    }

    @Transactional
    public void updateItemStatus(UUID itemId, String status) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        item.setApprovalStatus(status);
    }

    public void deleteItem(UUID itemId) {
        itemRepository.deleteById(itemId);
    }
}
