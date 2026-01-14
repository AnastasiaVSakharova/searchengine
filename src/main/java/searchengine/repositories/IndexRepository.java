package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexPages;

@Repository
public interface IndexRepository extends JpaRepository<IndexPages, Integer> {

    @Modifying
    @Transactional
    @Query("DELETE FROM IndexPages i WHERE i.pageId = :pageId")
    int deleteByPageId(@Param("pageId") int pageId);
}
