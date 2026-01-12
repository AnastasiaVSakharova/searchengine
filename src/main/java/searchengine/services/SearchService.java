package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.DataSearch;
import searchengine.dto.SearchResponse;
import searchengine.model.IndexPages;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchService {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    public SearchResponse search(String query, String siteUrl, int offset, int limit) {

        List<Site> sitesSearchList = checkSiteUrl(siteUrl);

        if (sitesSearchList.isEmpty()) {
            return new SearchResponse(false, "Указанный сайт не проиндексирован");
        }

        List<DataSearch> dataSearchList = new ArrayList<>();


        for (Site site : sitesSearchList) {
            dataSearchList.addAll(dataSearchListForSite(query, site));
        }

        // Коллекция dataSearchList сейчас содержит абсолютную релевантность для каждой страницы
        // Считаем относительную релевантность и сортируем коллекцию

        if (dataSearchList.size()>0) {
            dataSearchList = calculateRelativeRelevanceAndSort(dataSearchList);
        }

        int count = dataSearchList.size();
        dataSearchList = cutOffsetAndLimit(dataSearchList, offset, limit);

        return new SearchResponse(true, count, dataSearchList);

    }

    private List<DataSearch> cutOffsetAndLimit(List<DataSearch> dataSearchList, int offset, int limit) {
        if (offset > 0 && offset < dataSearchList.size()) {
            dataSearchList = dataSearchList.subList(offset, dataSearchList.size());
        }

        if (limit > 0 && limit < dataSearchList.size()) {
            dataSearchList = dataSearchList.subList(0, limit);
        }

        return dataSearchList;
    }

    private List<Site> checkSiteUrl(String siteUrl) {
        List<Site> siteSearchList = new ArrayList<>();

        if (siteUrl == null || siteUrl.isEmpty()) {
            for (int i = 0; i < sitesList.getSites().size(); i++) {
                String url = sitesList.getSites().get(i).getUrl();
                Site site = siteRepository.findByUrl(url);
                if (site != null && !site.getPageList().isEmpty()) {
                    siteSearchList.add(site);
                }
            }
            return siteSearchList;
        }

        Site site = siteRepository.findByUrl(siteUrl);
        if (site != null && !site.getPageList().isEmpty()) {
            siteSearchList.add(site);
        }
        return siteSearchList;
    }

    private List<Lemma> sortedLemmas(String query, int siteId) {
        HashMap<String, Integer> queryFrequency = TextAnalyzerService.getLemmaFrequency(query);

        // Все леммы, по которым ищем
        HashSet<String> queryLemmas = new HashSet<>(queryFrequency.keySet());
        List<Lemma> queryLemmasFrequency = new ArrayList<>();


        // Определяем леммы, которые встречаются на страницах и как часто встречаются
        int summPages = pageRepository.findBySiteId(siteId).size();
        for (String queryLemma : queryLemmas) {
            Lemma lemma = lemmaRepository.findByLemmaAndSiteId(queryLemma, siteId);
            if (lemma != null) {
                if ((double) lemma.getFrequency() / summPages < 0.5) {
                    queryLemmasFrequency.add(lemma);
                }
            }
        }

        queryLemmasFrequency.sort(Comparator.comparingInt(Lemma::getFrequency));
        return queryLemmasFrequency;
    }

    private HashSet<Integer> filterPages(List<Lemma> sortedLemmaFrequency) {
        HashSet<Integer> pageIdSet = new HashSet<>(); // финальный сет с id страниц

        for (int i = 0; i < sortedLemmaFrequency.size(); i++) {
            Lemma lemma = sortedLemmaFrequency.get(i);
            Set<IndexPages> indexPages = lemma.getIndexPagesList();
            if (i == 0) {
                for (IndexPages indexPage : indexPages) {
                    pageIdSet.add(indexPage.getPageId());
                }
            } else {
                // При последующих заходах считаем, какие id надо исключить
                HashSet<Integer> pageIdSetToCompare = new HashSet<>();
                for (IndexPages indexPage : indexPages) {
                    pageIdSetToCompare.add(indexPage.getPageId());
                }
                pageIdSet.retainAll(pageIdSetToCompare);
            }
        }
        return pageIdSet;
    }

    private List<DataSearch> dataSearchListForSite(String query, Site site) {
        List<DataSearch> dataSearchList = new ArrayList<>();

        List<Lemma> sortedLemmaFrequency = sortedLemmas(query, site.getId());
        HashSet<Integer> pageIdSet = filterPages(sortedLemmaFrequency); // финальный сет с id страниц

//        Пустой результат запроса
        if (pageIdSet.isEmpty()) return new ArrayList<>();

        List<Double> relevanceList = new ArrayList<>();

        for (Integer pageId : pageIdSet) {
            Page page = pageRepository.findById(pageId).orElse(null);
            if (page == null) {
                continue;
            }

            String content = page.getContent();
            Document doc = Jsoup.parse(content);
            String title = doc.title();
            String snippet = getSnippet(page,sortedLemmaFrequency.get(0).getLemma());

            double relevance = absoluteRelevance(page);

            relevanceList.add(relevance);
            DataSearch dataSearch = new DataSearch(site.getUrl(), site.getName(), page.getPath(), title, snippet, relevance);
            dataSearchList.add(dataSearch);
        }

        return dataSearchList;
    }

    private List<DataSearch> calculateRelativeRelevanceAndSort(List<DataSearch> dataSearchList) {

        double maxRelevance = Collections.max(dataSearchList,
                Comparator.comparingDouble(DataSearch::getRelevance)).getRelevance();
        dataSearchList.forEach(dataSearch ->
                dataSearch.setRelevance(dataSearch.getRelevance() / maxRelevance)
        );
        Collections.sort(dataSearchList, Comparator.comparingDouble(DataSearch::getRelevance).reversed());

        return dataSearchList;
    }

    private String getSnippet(Page page, String lemma) {
        String content = page.getContent();
        Document doc = Jsoup.parse(content);
        String snippet = "";

        Element element = findFirstOccurrence(content, lemma);
        if (element != null) {
            snippet = extractSnippetAroundWord(element, lemma, 200);
        } else {
            String foundWord = TextAnalyzerService.searchWordByLemma(content, lemma);
            element = doc.selectFirst(":contains(" + foundWord + ")");
            element = findFirstOccurrence(content, foundWord);
            if (element != null) {
                snippet = extractSnippetAroundWord(element, foundWord, 200);
            }
        }
        return snippet;
    }

    public Element findFirstOccurrence(String content, String searchText) {
        Document doc = Jsoup.parse(content);

        // Все нужные теги одним селектором
        Elements elements = doc.select("p, h1, h2, h3, h4, h5, h6, article, section, main, div, span");

        // Ищем первое вхождение
        for (Element element : elements) {
            if (element.text().toLowerCase().contains(searchText)) {
                return element;
            }
        }

        return null;
    }


    private static String extractSnippetAroundWord(Element element, String searchWord, int charsAround) {
        String fullText = element.text();
        String lowerText = fullText.toLowerCase();
        String lowerWord = searchWord.toLowerCase();

        int wordIndex = lowerText.indexOf(lowerWord);
        if (wordIndex == -1) {
            return fullText.substring(0, Math.min(200, fullText.length()));
        }

        int start = Math.max(0, wordIndex - charsAround);
        int end = Math.min(fullText.length(), wordIndex + searchWord.length() + charsAround);

        String snippet = fullText.substring(start, end);

        // Добавляем теги <b> </b>

        int wordStartInSnippet = wordIndex - start;
        int wordEndInSnippet = wordStartInSnippet + searchWord.length();


        String originalWord = fullText.substring(wordIndex, wordIndex + searchWord.length());

        snippet = snippet.substring(0, wordStartInSnippet)
                + "<b>" + originalWord + "</b>"
                + snippet.substring(wordEndInSnippet);

        if (start > 0) snippet = "..." + snippet;
        if (end < fullText.length()) snippet = snippet + "...";

        return snippet;
    }

    // Сумма всех rank всех лемм для этой страницы
    private double absoluteRelevance(Page page) {
        double absoluteRelevance = 0;
        Set<IndexPages> indexPagesList = page.getIndexPagesList();
        for (IndexPages indexPages : indexPagesList) {
            absoluteRelevance += indexPages.getMyRank();
        }
        return absoluteRelevance;
    }

}
