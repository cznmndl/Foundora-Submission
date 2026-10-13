package com.foundora.repository;

import com.foundora.model.ClaimRequest;
import com.foundora.model.ClaimRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ClaimRequestRepository extends JpaRepository<ClaimRequest, UUID> {

    List<ClaimRequest> findByItemIdOrderByCreatedAtAsc(UUID itemId);

    List<ClaimRequest> findByItemIdAndStatusIn(UUID itemId, Collection<ClaimRequestStatus> statuses);

    List<ClaimRequest> findByItemIdInAndStatusIn(Collection<UUID> itemIds, Collection<ClaimRequestStatus> statuses);

    List<ClaimRequest> findByRequesterEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    List<ClaimRequest> findByItemIdAndRequesterEmailIgnoreCaseOrderByCreatedAtDesc(UUID itemId, String email);

    /** The admin queue: every open request, oldest first. */
    List<ClaimRequest> findByStatusInOrderByCreatedAtAsc(Collection<ClaimRequestStatus> statuses);

    long countByStatus(ClaimRequestStatus status);

    List<ClaimRequest> findByStatusAndExpiresAtBefore(ClaimRequestStatus status, Instant before);
}
