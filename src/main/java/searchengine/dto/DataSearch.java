package searchengine.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DataSearch {
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private double relevance;

    // конструктор копирования
    public DataSearch(DataSearch data) {
        this.site = data.getSite();
        this.siteName = data.getSiteName();
        this.uri = data.getUri();
        this.title = data.getTitle();
        this.snippet = data.getSnippet();
        this.relevance = data.getRelevance();
    }

    public DataSearch(String site, String siteName, String uri, String title,  String snippet, double relevance) {
        this.relevance = relevance;
        this.site = site;
        this.siteName = siteName;
        this.snippet = snippet;
        this.title = title;
        this.uri = uri;
    }

}
