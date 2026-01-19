package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexPages;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<IndexPages, Integer> {

    IndexPages findByPageIdAndLemmaId(int pageId, int lemmaId);

    List<IndexPages> findByPageId(int pageId);

    @Modifying
    @Transactional
    @Query("DELETE FROM IndexPages i WHERE i.pageId = :pageId")
    int deleteByPageId(@Param("pageId") int pageId);
}
