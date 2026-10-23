package com.foundora.database.repository;

import com.foundora.database.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findByItemIdOrderByCreatedAtAscIdAsc(UUID itemId);

    Optional<Comment> findTopByAuthorEmailOrderByCreatedAtDesc(String authorEmail);

    @Query("select c.itemId, count(c) from Comment c where c.itemId in :itemIds group by c.itemId")
    List<Object[]> countByItemIds(Collection<UUID> itemIds);
}
