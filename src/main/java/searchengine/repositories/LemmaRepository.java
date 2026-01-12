package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {

    @Modifying
    @Transactional
    @Query(value = "UPDATE lemma l " +
            "SET l.frequency = l.frequency - 1 " +
            "WHERE EXISTS (" +
            "    SELECT 1 FROM index_pages ip " +
            "    WHERE ip.lemma_id = l.id AND ip.page_id = :pageId" +
            ") " +
            "AND l.site_id = :siteId " +
            "AND l.frequency > 0",
            nativeQuery = true)
    int decrementFrequencyForPageExists(@Param("siteId") int siteId,
                                        @Param("pageId") int pageId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Lemma l WHERE l.siteId = :siteId")
    int deleteBySiteId(@Param("siteId") int siteId);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO lemma (lemma, site_id, frequency) " +
            "VALUES (:lemma, :siteId, 1) " +
            "ON DUPLICATE KEY UPDATE frequency = frequency + 1",
            nativeQuery = true)
    void upsertLemma(@Param("lemma") String lemma,
                     @Param("siteId") Integer siteId);

    // Поиск леммы по тексту и сайту
    @Query(value = "SELECT id FROM lemma WHERE lemma = :lemma AND site_id = :siteId",
            nativeQuery = true)
    Integer findIdByLemmaAndSiteId(@Param("lemma") String lemma,
                                   @Param("siteId") Integer siteId);

    Lemma findByLemma(String lemma);
    Lemma findByLemmaAndSiteId(String lemma, int siteId);
}
