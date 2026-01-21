package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.IndexingResponse;
import searchengine.dto.CustomTextException;
import searchengine.model.IndexPages;
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

    public IndexingResponse indexPage(int pageId) {
        // Проверяем, что страница еще не проиндексирована
        List<IndexPages> indexPagesList = indexRepository.findByPageId(pageId);
        if (indexPagesList.size() > 0) {
            System.out.println("Страница уже проиндексирована");
            return new IndexingResponse(true);
        }
        Page page = pageRepository.findById(pageId).orElse(null);
        if (page == null) {
            return new IndexingResponse(false, " IndexPageServise.indexPage На индексацию передана страница,"
                    + " отсутствующая в pageRepository id = " + pageId);
        }

        try {
            HashMap<String, Integer> lemmaFrequency = TextAnalyzerService.getLemmaFrequency(page.getContent());
            saveLemmaInformation(lemmaFrequency, page.getSiteId(), pageId);
        } catch (IOException e) {
            String error = "Ошибка на этапе индексации страницы pageId = " + pageId +
                    ". " + e.getMessage();
            return new IndexingResponse(false, error);
        }

        return new IndexingResponse(true);
    }

    public IndexingResponse indexPage(String url) {

        checkURLFormat(url);
        String rootUrl = "";
        String pagePath = "";
        int siteId = 0;

        try {
            rootUrl = extractRootUrl(url);
            pagePath = new URL(url).getPath();
            pagePath = normalizePath(pagePath);

            searchengine.model.Site site = siteRepository.findByUrl(rootUrl);
            if (site == null) {
                String error = "Данная страница находится за пределами сайтов, " +
                        "указанных в конфигурационном файле";
                return new IndexingResponse(false, error);
            }

            siteId = site.getId();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }


        Page page = (pageRepository.findByPathAndSiteId(pagePath, siteId) != null) ? pageRepository.findByPathAndSiteId(pagePath, siteId) : new Page();

        int pageId = (page != null) ? page.getId() : 0;

        if (pageId != 0) {
            deleteLemmaInfoPage(pageId, siteId);
            return indexPage(pageId);
        }

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

            if (response.statusCode() == 200) {
                String content = doc.html();
                if (content.length() > MAX_CONTENT_LENGTH) {
                    content = content.substring(0, MAX_CONTENT_LENGTH);
                }
                page.setContent(content);

                page.setSiteId(siteId);
                page.setPath(pagePath);
                Page pageInsert = pageRepository.save(page);
                pageId = pageInsert.getId();

                HashMap<String, Integer> lemmaFrequency = TextAnalyzerService.getLemmaFrequency(content);
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

    private void checkURLFormat(String url) throws CustomTextException {
        if (url == null || url.trim().isEmpty()) {
            throw new CustomTextException("URL не может быть пустым");
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new CustomTextException("URL должен начинаться с http:// или https://");
        }

        if (url.length() > 2048) {
            throw new CustomTextException("URL слишком длинный");
        }
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
            try {
                int frequency = lemmaFrequency.get(key);
                lemmaRepository.upsertLemma(key, siteId);
                int lemmaId = lemmaRepository.findIdByLemmaAndSiteId(key, siteId);

                // Частота использование леммы на странице
                IndexPages indexPages = new IndexPages();
                indexPages.setPageId(pageId);
                indexPages.setLemmaId(lemmaId);
                indexPages.setMyRank((double) frequency);
                IndexPages foundIndexPage = indexRepository.findByPageIdAndLemmaId(pageId, lemmaId);
                if (foundIndexPage != null) {
                    continue;
                }
                indexRepository.save(indexPages);
            } catch (RuntimeException e) {
                System.out.println("saveLemmaInformation. " + e.getMessage());
                throw new RuntimeException(e);
            }
        }


    }


    private void deleteLemmaInfoPage(int pageId, int siteId) {
        // Удаляет информацию о леммах при переиндексации страницы
        indexRepository.deleteByPageId(pageId);
        lemmaRepository.decrementFrequencyForPageExists(siteId, pageId);
    }

}
