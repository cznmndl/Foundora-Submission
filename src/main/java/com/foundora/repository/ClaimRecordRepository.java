package com.foundora.repository;

import com.foundora.model.ClaimRecord;
import com.foundora.model.ClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimRecordRepository extends JpaRepository<ClaimRecord, UUID> {

    List<ClaimRecord> findByItemIdOrderByCreatedAtAsc(UUID itemId);

    /** The item's current claim: the one that hasn't been reopened (at most one, enforced by V9's unique index). */
    Optional<ClaimRecord> findFirstByItemIdAndStatusNot(UUID itemId, ClaimStatus status);

    List<ClaimRecord> findByItemIdInAndStatusNot(Collection<UUID> itemIds, ClaimStatus status);
}
