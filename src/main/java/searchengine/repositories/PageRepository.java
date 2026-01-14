package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
   List<Page> findBySiteId(int id);
   Page findByPathAndSiteId(String path, int siteId);
   boolean existsByPathAndSiteId(String path, int siteId);

   @Modifying
   @Transactional
   @Query("UPDATE Page p SET p.code = :code, p.content = :content WHERE p.id = :pageId")
   int updateCodeAndContent(@Param("code") int code,
                              @Param("content") String content,
                              @Param("pageId") int pageId);


}
