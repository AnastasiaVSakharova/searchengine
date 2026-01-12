package searchengine.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexingResponse {
    private boolean result;
    private String error;

    // Конструктор для успешного ответа
    public IndexingResponse(boolean result) {
        this.result = result;
    }

    // Конструктор для ответа с ошибкой
    public IndexingResponse(boolean result, String error) {
        this.result = result;
        this.error = error;
    }

}
