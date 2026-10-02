package vn.edu.medmaintenance.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.medmaintenance.persistence.entity.ApprovalRequest;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestStatus;
import vn.edu.medmaintenance.persistence.enums.PlanItemStatus;

@SpringBootTest
@Transactional
class RepositoryQueryAuditTest {
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private EquipmentRepository equipment;
    @Autowired private MaintenancePlanItemRepository items;
    @Autowired private ApprovalRequestRepository requests;

    private record Result(String scenario, int rows, long statements) { }

    @Test
    void selectedListQueriesAvoidObviousNPlusOneLoading() throws IOException {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        List<Result> results = new ArrayList<>();
        try {
            entityManager.clear();
            stats.clear();
            var equipmentPage = equipment.findAllBy(PageRequest.of(0, 20,
                    Sort.by("equipmentCode").ascending().and(Sort.by("id"))));
            assertThat(equipmentPage.getContent()).hasSize(20);
            equipmentPage.forEach(e -> {
                assertThat(Hibernate.isInitialized(e.getDepartment())).isTrue();
                assertThat(e.getDepartment().getName()).isNotBlank();
            });
            results.add(new Result("Equipment list + department", equipmentPage.getNumberOfElements(),
                    stats.getPrepareStatementCount()));

            entityManager.clear();
            stats.clear();
            var itemPage = items.findByStatus(PlanItemStatus.PLANNED,
                    PageRequest.of(0, 12, Sort.by("id")));
            assertThat(itemPage.getTotalElements()).isEqualTo(15);
            itemPage.forEach(i -> {
                assertThat(Hibernate.isInitialized(i.getEquipment())).isTrue();
                assertThat(i.getEquipment().getName()).isNotBlank();
            });
            results.add(new Result("Plan item list + equipment", itemPage.getNumberOfElements(),
                    stats.getPrepareStatementCount()));

            entityManager.clear();
            stats.clear();
            var queue = requests.findByStatus(ApprovalRequestStatus.PENDING,
                    PageRequest.of(0, 2, Sort.by("submittedAt").ascending().and(Sort.by("id"))));
            assertThat(queue.getTotalElements()).isEqualTo(3);
            queue.forEach(request -> {
                assertThat(Hibernate.isInitialized(request.getCreatedByUser())).isTrue();
                assertThat(request.getCreatedByUser().getDisplayName()).isNotBlank();
                if (request.getProposedProvider() != null) {
                    assertThat(Hibernate.isInitialized(request.getProposedProvider())).isTrue();
                    assertThat(request.getProposedProvider().getName()).isNotBlank();
                }
                if (request.getPlan() != null) {
                    assertThat(Hibernate.isInitialized(request.getPlan())).isTrue();
                    assertThat(request.getPlan().getTitle()).isNotBlank();
                } else {
                    assertThat(Hibernate.isInitialized(request.getPlanItem())).isTrue();
                    assertThat(Hibernate.isInitialized(request.getPlanItem().getEquipment())).isTrue();
                    assertThat(request.getPlanItem().getEquipment().getEquipmentCode()).isNotBlank();
                }
            });
            results.add(new Result("Pending approval + display relations", queue.getNumberOfElements(),
                    stats.getPrepareStatementCount()));
        } finally {
            stats.setStatisticsEnabled(false);
        }
        assertThat(results).hasSize(3);
        for (Result result : results) {
            assertThat(result.statements()).as(result.scenario()).isBetween(1L, 3L);
        }
        StringBuilder audit = new StringBuilder("# Phase 2.3 Query Audit\n\n"
                + "Measured with built-in Hibernate prepared-statement statistics on the Phase 1.3 "
                + "603-row PostgreSQL demo dataset. Each scenario starts with an empty persistence context "
                + "and cleared statistics. The count includes page/count SQL where Spring Data issues it.\n\n"
                + "| Query Scenario | Rows | SQL Statements | Result |\n"
                + "| --- | ---: | ---: | --- |\n");
        for (Result result : results) {
            audit.append("| ").append(result.scenario()).append(" | ").append(result.rows())
                    .append(" | ").append(result.statements()).append(" | PASS |\n");
        }
        audit.append("\nEach query uses a to-one `@EntityGraph` fetch plan. A small constant SQL count "
                + "for these list samples avoids the obvious one-query-per-row pattern; this is not "
                + "a production latency or scale benchmark.\n");
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path root = cwd.getFileName().toString().equals("backend") ? cwd.getParent() : cwd;

        // Audit assertions remain executable; no report artifact is emitted.
    }
}
