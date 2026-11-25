package com.finemp.people;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobPositionRepository extends JpaRepository<JobPosition, UUID> {

    Optional<JobPosition> findByIdAndCompanyId(UUID id, UUID companyId);

    @Query("""
            select position from JobPosition position
             where position.company.id = :companyId
               and (:search = '' or lower(position.title) like lower(concat('%', :search, '%')))
               and (:active is null or position.active = :active)
            """)
    Page<JobPosition> search(
            @Param("companyId") UUID companyId,
            @Param("search") String search,
            @Param("active") Boolean active,
            Pageable pageable);

    @Query("""
            select count(position) > 0 from JobPosition position
             where position.company.id = :companyId
               and lower(trim(position.title)) = lower(trim(:title))
               and (:excludeId is null or position.id <> :excludeId)
            """)
    boolean existsTitle(
            @Param("companyId") UUID companyId,
            @Param("title") String title,
            @Param("excludeId") UUID excludeId);
}
