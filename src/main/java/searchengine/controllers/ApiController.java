package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.IndexingResponse;
import searchengine.dto.InvalidUrlException;
import searchengine.dto.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.*;

import javax.persistence.criteria.CriteriaBuilder;
import java.io.IOException;
import java.net.MalformedURLException;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final SiteIndexingServise siteIndexingServise;
    private final IndexPageServise indexPage;
    private final SearchService searchService;

    public ApiController(StatisticsService statisticsService,
                         SiteIndexingServise siteIndexingServise, IndexPageServise indexPage, SearchService searchService) {
        this.statisticsService = statisticsService;
        this.siteIndexingServise = siteIndexingServise;
        this.indexPage = indexPage;
        this.searchService = searchService;
    }

    @GetMapping("/statistics")
    public StatisticsResponse statistics() {
        return statisticsService.getStatistics();
    }

    @GetMapping("/startIndexing")
    public IndexingResponse startIndexing() {
        return siteIndexingServise.startIndexing();
    }

    @GetMapping("/stopIndexing")
    public IndexingResponse stopIndexing() {
        return siteIndexingServise.stopIndexing();
    }

//    @PostMapping("/indexPage")
//    public ResponseEntity<IndexingResponse> indexPage(@RequestParam("url") String url) {
//        try {
//            return ResponseEntity.ok(indexPage.indexPage(url));
//        } catch (InvalidUrlException | MalformedURLException e) {
//            IndexingResponse response = new IndexingResponse(false, e.getMessage());
//            return ResponseEntity.badRequest().body(response);
//        }
//    }

    @PostMapping("/indexPage")
    public IndexingResponse indexPage(@RequestParam("url") String url) {
        return indexPage.indexPage(url);
    }

    @GetMapping("/search")
    public SearchResponse indexPage(@RequestParam("query") String query,
                                                    @RequestParam(value = "site", required = false) String site,
                                                    @RequestParam(value = "offset", defaultValue = "0") int offset,
                                                    @RequestParam(value = "limit", defaultValue = "20") int limit) {
            return searchService.search(query, site, offset, limit);
    }
}
