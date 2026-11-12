package com.finemp.planning.goal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalMeasurementRepository
        extends JpaRepository<GoalMeasurement, UUID> {

    Page<GoalMeasurement> findByCompanyIdAndGoalId(
            UUID companyId,
            UUID goalId,
            Pageable pageable);

    Optional<GoalMeasurement>
            findFirstByCompanyIdAndGoalIdOrderByMeasuredOnDescCreatedAtDescIdDesc(
                    UUID companyId,
                    UUID goalId);

    List<GoalMeasurement>
            findFirst100ByCompanyIdAndGoalIdOrderByMeasuredOnDescCreatedAtDescIdDesc(
                    UUID companyId,
                    UUID goalId);
}
