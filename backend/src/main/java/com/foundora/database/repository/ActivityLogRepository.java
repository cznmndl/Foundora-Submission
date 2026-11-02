package com.foundora.database.repository;

import com.foundora.database.entity.ActivityAction;
import com.foundora.database.entity.ActivityLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLogEntry, Long> {

    Page<ActivityLogEntry> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<ActivityLogEntry> findByActionOrderByCreatedAtDescIdDesc(ActivityAction action, Pageable pageable);

    Page<ActivityLogEntry> findByActionInOrderByCreatedAtDescIdDesc(Collection<ActivityAction> actions, Pageable pageable);

    List<ActivityLogEntry> findTop10ByOrderByCreatedAtDescIdDesc();

    List<ActivityLogEntry> findByItemIdOrderByCreatedAtAscIdAsc(UUID itemId);
}
