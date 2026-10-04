package com.foundora.repository;

import com.foundora.model.Item;
import com.foundora.model.ReportStatus;
import com.foundora.model.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID>, JpaSpecificationExecutor<Item> {

    List<Item> findByStatusIn(Collection<ReportStatus> statuses);

    List<Item> findByStatusOrderByCreatedAtAsc(ReportStatus status);

    List<Item> findByReporterEmailOrderByCreatedAtDesc(String reporterEmail);

    long countByStatus(ReportStatus status);

    long countByStatusAndReportType(ReportStatus status, ReportType reportType);
}
