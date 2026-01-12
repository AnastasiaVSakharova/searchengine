package searchengine.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResponse {
    private boolean result;
    private int count;
    private List<DataSearch> data;
    private String error;

    // Конструктор для успешного ответа
    public SearchResponse(boolean result, int count, List<DataSearch> dataSearchList) {
        this.result = result;
        this.count = count;
        this.data = dataSearchList;
    }

    // Конструктор для ответа с ошибкой
    public SearchResponse(boolean result, String error) {
        this.result = result;
        this.error = error;
    }
}
