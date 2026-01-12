package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.model.SiteStatus;
import searchengine.model.Site;


@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {
    Site findByUrl(String url);

    @Modifying
    @Transactional
    @Query("UPDATE Site s SET s.statusTime = CURRENT_TIMESTAMP WHERE s.id = :siteId")
    int updateStatusTime(@Param("siteId") int siteId);

    @Modifying
    @Transactional
    @Query("UPDATE Site s SET s.statusTime = CURRENT_TIMESTAMP, s.status = :status WHERE s.id = :siteId")
    int updateStatus(@Param("status") SiteStatus status, @Param("siteId") int siteId);

    // Базовый метод с возможностью передачи статуса
    @Modifying
    @Transactional
    @Query("UPDATE Site s SET s.statusTime = CURRENT_TIMESTAMP, s.status = :status, s.lastError = :error WHERE s.id = :siteId")
    int updateErrorDescription(@Param("error") String lastError,
                               @Param("status") SiteStatus status,
                               @Param("siteId") int siteId);

    // Default метод с дефолтным статусом
    default int updateErrorDescription(String lastError, int siteId) {
        return updateErrorDescription(lastError, SiteStatus.FAILED, siteId);
    }

}
