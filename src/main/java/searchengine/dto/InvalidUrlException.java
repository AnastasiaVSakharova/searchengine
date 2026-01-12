package searchengine.dto;

public class InvalidUrlException extends Exception{
    public InvalidUrlException(String message) {
        super(message);
    }
}
