package searchengine.services;

import searchengine.config.SitesList;
import searchengine.dto.model.SiteStatus;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

public class SiteProcessor extends Thread {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SitesList sitesList;


    private final String url;
    private final String siteName;

    private ForkJoinPool forkJoinPool;

    public SiteProcessor(SiteRepository siteRepository, PageRepository pageRepository, LemmaRepository lemmaRepository, IndexRepository indexRepository, SitesList sitesList, String url, String name) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.sitesList = sitesList;
        this.url = url;
        this.siteName = name;
    }

    public void run() {
        // Удаляем старые данные
        Site site = siteRepository.findByUrl(url);
        if (!Objects.isNull(site))
        {
            System.out.println("Удаление старых данных о странице");
            Set<Page> pageList = site.getPageList();
            for (Page page : pageList) {
                indexRepository.deleteByPageId(page.getId());
            }
            lemmaRepository.deleteBySiteId(site.getId());
            siteRepository.deleteById(site.getId());
        }

        // начинаем индексацию
        site = new Site();
        site.setName(siteName);
        site.setUrl(url);
        site.setStatus(SiteStatus.INDEXING);
        siteRepository.save(site);

        site = siteRepository.findByUrl(url);

        try {
            PageParserService parserService = new PageParserService(siteRepository, pageRepository, lemmaRepository, indexRepository, site.getId(), url, "/", sitesList);
            this.forkJoinPool = new ForkJoinPool();
            this.forkJoinPool.invoke(parserService);
            if (!PageParserService.isStopRequested()) {
                // Выполняется после завершения всех потоков
                siteRepository.updateStatus(SiteStatus.INDEXED, site.getId());
            }

        } catch (Exception e) {
            if (!PageParserService.isStopRequested()) {
                System.out.println("Ошибка на этапе индексации страницы " + url + " Message " + e.getMessage() + e.getStackTrace().toString());
                siteRepository.updateErrorDescription(e.getMessage(), SiteStatus.FAILED, site.getId());
            }
        }
    }

    // Метод для остановки индексации этого сайта
    public void stopIndexing() {
        if (forkJoinPool != null) {
            forkJoinPool.shutdownNow(); // Принудительно останавливаем pool
        }
        PageParserService.stopAll(); // Останавливаем все задачи
    }

}
