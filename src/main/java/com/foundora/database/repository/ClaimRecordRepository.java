package com.foundora.database.repository;

import com.foundora.database.entity.ClaimRecord;
import com.foundora.database.entity.ClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimRecordRepository extends JpaRepository<ClaimRecord, UUID> {

    List<ClaimRecord> findByItemIdOrderByCreatedAtAsc(UUID itemId);

    Optional<ClaimRecord> findFirstByItemIdAndStatusNot(UUID itemId, ClaimStatus status);

    List<ClaimRecord> findByItemIdInAndStatusNot(Collection<UUID> itemIds, ClaimStatus status);
}
