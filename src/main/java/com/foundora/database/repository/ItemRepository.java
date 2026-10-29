package com.foundora.database.repository;

import com.foundora.database.entity.Category;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.entity.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID>, JpaSpecificationExecutor<Item> {

    List<Item> findByStatusIn(Collection<ReportStatus> statuses);

    List<Item> findByStatusOrderByCreatedAtAsc(ReportStatus status);

    List<Item> findByReporterEmailOrderByCreatedAtDesc(String reporterEmail);

    List<Item> findByCategoryAndReportTypeAndStatusIn(Category category, ReportType type,
                                                      Collection<ReportStatus> statuses);

    long countByStatus(ReportStatus status);

    long countByStatusAndReportType(ReportStatus status, ReportType reportType);

    long countByStatusAndArchivedAtIsNull(ReportStatus status);

    long countByArchivedAtIsNotNull();

    /** Claimed reports due for archiving: claimed before the cutoff and not archived yet. */
    List<Item> findByStatusAndArchivedAtIsNullAndClaimedAtBefore(ReportStatus status, Instant cutoff);
}
