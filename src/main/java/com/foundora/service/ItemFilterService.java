package com.foundora.service;

import com.foundora.model.Item;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ItemFilterService {

    public List<Item> filterByStatus(List<Item> items, String status) {
        return items.stream()
                .filter(item -> item.getStatus().equalsIgnoreCase(status))
                .collect(Collectors.toList());
    }

    public List<Item> filterByCategory(List<Item> items, String category) {
        return items.stream()
                .filter(item -> item.getCategory().equalsIgnoreCase(category))
                .collect(Collectors.toList());
    }

    public List<Item> sortByDate(List<Item> items) {
        return items.stream()
                .sorted(Comparator.comparing(Item::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<Item> sortByRelevance(List<Item> items, String searchTerm) {
        return items.stream()
                .filter(item -> item.getTitle().toLowerCase().contains(searchTerm.toLowerCase()) ||
                                item.getDescription().toLowerCase().contains(searchTerm.toLowerCase()))
                .sorted(Comparator.comparing(Item::getTitle))
                .collect(Collectors.toList());
    }

    public List<Item> filterItems(List<Item> items, String category, String status) {
        List<Item> result = items;
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("all")) {
            result = filterByStatus(result, status);
        }
        if (category != null && !category.isBlank()) {
            // Filter links use slugs like "school_supplies" for the "School Supplies" category.
            result = filterByCategory(result, category.replace('_', ' '));
        }
        return sortByDate(result);
    }

    public List<Item> searchItems(List<Item> items, String query) {
        return sortByRelevance(items, query);
    }
}