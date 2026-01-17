package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.dto.IndexingResponse;
import searchengine.dto.model.SiteStatus;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.config.SitesList;
import searchengine.model.Site;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SiteIndexingServise {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SitesList sitesList;

    // Храним запущенные процессоры
    private final List<SiteProcessor> runningProcessors = Collections.synchronizedList(new ArrayList<>());

    public IndexingResponse startIndexing() {
        //StartIndexingResponse startIndexingResponse = new StartIndexingResponse();

        if (isIndexingRunning()) {
            return new IndexingResponse(false, "Индексация уже запущена");
        }


        try {
            //System.out.println(" step 1");
            PageParserService.resetStopFlag();
            runningProcessors.clear();

            //System.out.println("Step 2");

            for (searchengine.config.Site site : sitesList.getSites()) {
                System.out.println("Начало индексации сайта: " + site.getName());
                Site siteForDel = siteRepository.findByUrl(site.getUrl());
                if (siteForDel != null) {
                    System.out.println("Удаляем данные для " + site.getName());
                    siteRepository.delete(siteForDel);
                }
                SiteProcessor siteProcessor = new SiteProcessor(siteRepository, pageRepository, lemmaRepository, indexRepository, sitesList, site.getUrl(), site.getName());
                runningProcessors.add(siteProcessor);

                siteProcessor.start();
            }
            return new IndexingResponse(true);
        } catch (Exception e) {
            return new IndexingResponse(false, "Ошибка при запуске индексации: " + e.getMessage());
        }

    }

    public IndexingResponse stopIndexing() {

        //StopIndexingResponse stopIndexingResponse = new StopIndexingResponse();

        if (!isIndexingRunning()) {
            return new IndexingResponse(false, "Индексация не запущена");
        }

        try {
            synchronized (runningProcessors) {
                for (SiteProcessor processor : runningProcessors) {
                    processor.stopIndexing();
                }
                runningProcessors.clear();
            }


            List<Site> siteRepositoryAll = siteRepository.findAll();
            for (Site site : siteRepositoryAll) {
                if (site.getStatus() == SiteStatus.INDEXING) {
                    site.setStatusTime(new Timestamp(System.currentTimeMillis()));
                    site.setLastError("Индексация остановлена пользователем");
                    site.setStatus(SiteStatus.FAILED);
                    siteRepository.save(site);
                }
            }
            return new IndexingResponse(true);
        } catch (Exception e) {
            return new IndexingResponse(false, "Ошибка при остановке индексации: " + e.getMessage());
        }


    }

    private boolean isIndexingRunning() {
        List<Site> siteRepositoryAll = siteRepository.findAll();
        for (Site site : siteRepositoryAll) {
            if (site.getStatus() == SiteStatus.INDEXING) {
                return true;
            }
        }
        return false;
    }
}

