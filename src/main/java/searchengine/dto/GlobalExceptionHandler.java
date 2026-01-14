package searchengine.dto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<IndexingResponse> handleResourceNotFoundException(InvalidUrlException e) {
        log.error(e.getMessage(), e);
        return ResponseEntity
                .badRequest()
                .body(new IndexingResponse(false, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<IndexingResponse> handleAllExceptions(Exception e) {
        log.error("Internal error: {}", e.getMessage(), e);
        return ResponseEntity
                .internalServerError()
                .body(new IndexingResponse(false, "Внутренняя ошибка сервера"));
    }
}
