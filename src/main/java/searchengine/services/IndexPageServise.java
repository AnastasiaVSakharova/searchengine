package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.IndexingResponse;
import searchengine.dto.InvalidUrlException;
import searchengine.model.IndexPages;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Service
@Slf4j
@RequiredArgsConstructor
public class IndexPageServise {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    private static final int MAX_CONTENT_LENGTH = 500000; // ~500KB

    public IndexingResponse indexPage(String url)  {
        return indexPage(url, false);
    }

    public IndexingResponse indexPage(String url, boolean isIndexingOnePage)  {

        checkURLFormat(url);
        // проверим URL, что входит в список индексируемых сайтов
        String rootUrl = "";

        try {
            rootUrl = checkBaseURL(url);
        } catch (MalformedURLException e) {
            String error = "Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле";
            return new IndexingResponse(false, error);
        }

        if (rootUrl.isEmpty()) {
            String error = "Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле";
            return new IndexingResponse(false, error);
        }

        searchengine.model.Site site = new searchengine.model.Site();
        site = siteRepository.findByUrl(rootUrl);;
        int siteId = site.getId();

        String pagePath = "";
        try {
            pagePath = new URL(url).getPath();
        } catch (MalformedURLException e) {
            String error = "Внутренняя ошибка при обработке URL";
            return new IndexingResponse(false, error);
        }
        pagePath = normalizePath(pagePath);
        Page page = (pageRepository.findByPathAndSiteId(pagePath, siteId) != null) ? pageRepository.findByPathAndSiteId(pagePath, siteId) : new Page();

        int pageId = (page != null) ? page.getId() : 0;

        try {
            // Получить html код
            Connection.Response response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .referrer("http://www.google.com")
                    .timeout(3000)
                    .ignoreHttpErrors(true)  // Не бросать исключения при HTTP ошибках
                    .execute();
            page.setCode(response.statusCode());

            Document doc = response.parse();
            //String content = doc.html();

            if (response.statusCode() == 200) {
                String content = doc.html();
                if (content.length() > MAX_CONTENT_LENGTH) {
                    content = content.substring(0, MAX_CONTENT_LENGTH);
                }
                page.setContent(content);

                if (isIndexingOnePage || pageId == 0) {

                    if (pageId == 0) {
                        page.setSiteId(siteId);
                        page.setPath(pagePath);
                        Page pageInsert = pageRepository.save(page);
                        pageId = pageInsert.getId();
                    } else {
                        pageRepository.updateCodeAndContent(page.getCode(), page.getContent(), pageId);
                    }

                }

                HashMap<String, Integer> lemmaFrequency = TextAnalyzerService.getLemmaFrequency(content);
                deleteLemmaInfoPage(pageId, siteId);
                saveLemmaInformation(lemmaFrequency, siteId, pageId);

            } else {
                String error = response.statusCode() + ": " + response.statusMessage();
                System.out.println(error);
                return new IndexingResponse(false, error);
            }
        } catch (IOException ignored) {
            String error = "Ошибка при запросе кода страницы";
            System.out.println(error);
            return new IndexingResponse(false, error);
        }

        return new IndexingResponse(true);
    }

    private void checkURLFormat(String url) throws InvalidUrlException {
        if (url == null || url.trim().isEmpty()) {
            throw new InvalidUrlException("URL не может быть пустым");
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new InvalidUrlException("URL должен начинаться с http:// или https://");
        }

        if (url.length() > 2048) {
            throw new InvalidUrlException("URL слишком длинный");
        }
    }

    private String checkBaseURL(String url) throws MalformedURLException {
        String rootURL = extractRootUrl(url);
        List<Site> sites = sitesList.getSites();

        boolean found = false;
        for (Site site : sites) {
            if (extractRootUrl(site.getUrl()).equals(rootURL))
                return rootURL;
        }

        return "";
    }

    private static String extractRootUrl(String urlString) throws MalformedURLException {
        URL url = new URL(urlString);
        String scheme = url.getProtocol();
        String host = url.getHost();

        StringBuilder rootUrl = new StringBuilder();
        rootUrl.append(scheme).append("://").append(host);

        return rootUrl.toString();
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }

        // Убираем конечный слэш, если он есть и путь не равен "/"
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        // Если после обработки путь пустой, возвращаем "/"
        return path.isEmpty() ? "/" : path;
    }

    private void saveLemmaInformation(HashMap<String, Integer> lemmaFrequency, int siteId, int pageId) {
            for (String key : lemmaFrequency.keySet()) {
                //int lemmaId = 0;
                int frequency = lemmaFrequency.get(key);
                lemmaRepository.upsertLemma(key, siteId);
                int lemmaId = lemmaRepository.findIdByLemmaAndSiteId(key, siteId);

                // Частота использование леммы на странице
                IndexPages indexPages = new IndexPages();
                indexPages.setPageId(pageId);
                indexPages.setLemmaId(lemmaId);
                indexPages.setMyRank((double) frequency);
                indexRepository.save(indexPages);
            }


    }



    private void deleteLemmaInfoPage(int pageId, int siteId) {
        // Удаляет информацию о леммах при переиндексации страницы
        indexRepository.deleteByPageId(pageId);
        lemmaRepository.decrementFrequencyForPageExists(siteId, pageId);
    }
}
