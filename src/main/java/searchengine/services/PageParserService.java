package searchengine.services;

import lombok.Getter;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.dto.IndexingResponse;
import searchengine.dto.InvalidUrlException;
import searchengine.dto.model.SiteStatus;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.net.URL;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;

import searchengine.repositories.PageRepository;

public class PageParserService extends RecursiveAction {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final String baseUrl;
    private final int siteId;
    private final String path;
    private final String url;
    private final SitesList sitesList;


    // статические поля для контроля рекурсии и дубликатов
    private static final Set<String> processedUrls = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger taskCounter = new AtomicInteger(0);
    private static final int MAX_TASKS = 50; //
    private static final int MAX_PAGES_PER_SITE = 500; //
    private static final int MAX_CONTENT_LENGTH = 500000; // ~500KB

    // public
    // Для остановки потоков
    @Getter
    private static volatile boolean stopRequested = false;


    public PageParserService(SiteRepository siteRepository, PageRepository pageRepository, LemmaRepository lemmaRepository, IndexRepository indexRepository,
                             int siteId, String baseUrl, String path,
                             SitesList sitesList) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;

        // для главной страницы
        this.path = path;
        this.siteId = siteId;
        this.baseUrl = baseUrl;
        this.sitesList = sitesList;
        this.url = baseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW) // Новая транзакция для каждой задачи
    protected void compute() {

        // проверяем лимит и флаг остановки
        if (stopRequested || taskCounter.get() >= MAX_TASKS) {
            return;

        }


        // проверяем уникальность url
        if (!processedUrls.add(url)) {
            return; // Уже обрабатывается
        }

        Page page = new Page();
        page.setPath(path);
        page.setSiteId(siteId);

        // загрузка и парсинг страницы
        try {

            if (stopRequested) return;

            taskCounter.incrementAndGet();

            if (pageRepository.existsByPathAndSiteId(path, siteId)) {
                return;
            }

            if (stopRequested) return;

            Connection.Response response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .referrer("http://www.google.com")
                    .timeout(3000)
                    .ignoreHttpErrors(true)  // Не бросать исключения при HTTP ошибках
                    .execute();
            page.setCode(response.statusCode());


            Site site = siteRepository.findById(siteId).orElse(null);
            assert site != null;

            if (response.statusCode() == 200) {

                Document doc = response.parse();

                // Ограничиваем размер контента
                String content = doc.html();
                if (content.length() > MAX_CONTENT_LENGTH) {
                    content = content.substring(0, MAX_CONTENT_LENGTH);
                }
                page.setContent(content);


                // СОХРАНЯЕМ страницу в репозиторий
                pageRepository.save(page);
                site.setStatusTime(new Timestamp(System.currentTimeMillis()));
                siteRepository.save(site);

                // запускаем индексацию страницы
                System.out.println("Запуск индексации для " + url);
                IndexPageServise indexPageServise = new IndexPageServise(sitesList, siteRepository, pageRepository, lemmaRepository, indexRepository);
                IndexingResponse indexingResponse = indexPageServise.indexPage(url);
                // Работа с дочерними ссылками
                processChildLinks(doc);

            } else {
                response.statusMessage();
                page.setContent(response.statusMessage());
                pageRepository.save(page);
                site.setStatus(SiteStatus.INDEXING);
                site.setStatusTime(new Timestamp(System.currentTimeMillis()));
                site.setLastError(response.statusMessage());
                siteRepository.save(site);
            }


        } catch (IOException e) {
            // Сохраняем страницу с информацией об ошибке
            if (!stopRequested) {
                page.setCode(500);
                page.setContent("Error: " + e.getMessage());
                pageRepository.save(page);
            }
        } catch (InvalidUrlException e) {
            throw new RuntimeException(e);
        } finally {
                taskCounter.decrementAndGet();
        }
    }

    private void processChildLinks(Document doc) {
        HashSet<String> pageLinks = extractLinks(doc, baseUrl);
        //System.out.println("Найдено ссылок: " + pageLinks.size());

        if (pageLinks.isEmpty()) {
            return;
        }

        List<PageParserService> tasks = new ArrayList<>();
        int childCount = 0;

        for (String link : pageLinks) {
            if (childCount >= 10) break; // Уменьшили параллелизм
            if (taskCounter.get() >= MAX_TASKS) break;
            if (processedUrls.size() >= MAX_PAGES_PER_SITE) break;

            String fullUrl = baseUrl+link;
            if (!processedUrls.contains(fullUrl)) {

                PageParserService task = new PageParserService(siteRepository, pageRepository, lemmaRepository, indexRepository,
                        siteId, baseUrl, link, sitesList);
                task.fork();
                tasks.add(task);
                childCount++;
            }
        }

        // Ждем завершения
        for (PageParserService task : tasks) {
            task.join();
        }
    }

    private HashSet<String> extractLinks(Document doc, String baseUrl) {
        // Извлечение ссылок со страницы
        HashSet<String> links = new HashSet<>();
        Elements paths = doc.select("a[href]");

        for (Element path : paths) {
            String childLink = path.attr("abs:href");
            if (isValidUrl(childLink, baseUrl) && !childLink.equals(baseUrl)) {
                Optional<String> pathLink = getPathFromUrl(childLink);
                pathLink.ifPresent(links::add);
            }

        }
        return links;
    }

    private boolean isValidUrl(String url, String baseURL) {
        return url.startsWith(baseURL) &&
                !url.contains("#") &&
                !url.contains("mailto:") &&
                !url.contains("javascript:") &&
                !url.contains(".pdf") &&
                !url.contains(".jpg") &&
                !url.contains(".png") &&
                !url.contains(".doc") &&
                !url.contains(".zip") &&
                !url.contains(".exe");
    }

    public static Optional<String> getPathFromUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            String path = url.getPath();
            return Optional.of(path.isEmpty() ? "/" : path);
        } catch (Exception e) {
            //System.err.println("Ошибка парсинга URL: " + urlString + " - " + e.getMessage());
            return Optional.empty();
        }
    }

    // Метод для остановки потока
    public static void stopAll() {
        stopRequested = true;
        processedUrls.clear();
        taskCounter.set(0);
    }

    // Метод для сброса флага (при новом запуске)
    public static void resetStopFlag() {
        stopRequested = false;
    }
}
