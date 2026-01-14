package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TextAnalyzerService {

    // убираем html теги
    private static String removeHtmlTags(String htmlText) {
        if (htmlText == null || htmlText.isEmpty()) {
            return htmlText;
        }
        return Jsoup.parse(htmlText).text();
    }

    private static String keepOnlyRussianLetters(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Оставляем только русские буквы, пробелы и дефисы для составных слов
        return text.replaceAll("[^а-яА-ЯёЁ\\s-]", " ")
                .replaceAll("\\s+", " ") // Заменяем множественные пробелы на один
                .trim();
    }

    // Разбиваем текст на слова
    private static List<String> splitIntoWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String cleanedText = keepOnlyRussianLetters(text)
                .toLowerCase(); // Приводим к нижнему регистру

        return Arrays.stream(cleanedText.split("\\s+"))
                .filter(word -> !word.isEmpty() && word.length() > 1)
                .collect(Collectors.toList());
    }

    private static boolean isContentWord(String word, LuceneMorphology luceneMorph) {

            //LuceneMorphology luceneMorph = new RussianLuceneMorphology();
            List<String> wordBaseForms = luceneMorph.getMorphInfo(word);
            if (wordBaseForms.size() != 1) return true;
            // Если слово служебное, то будет только 1 форма
            String firstWordBaseForms = wordBaseForms.get(0);
            String wordType = firstWordBaseForms.substring(firstWordBaseForms.indexOf(" ") + 1);

            Set<String> stopType = Set.of("ПРЕДЛ", "СОЮЗ", "ЧАСТ", "МЕЖД");

            return !stopType.contains(wordType);

    }

    // Считает частоту лем в тексте
    public static HashMap<String, Integer> getLemmaFrequency(String contex) {
        String text = removeHtmlTags(contex);
        text = keepOnlyRussianLetters(text);
        List<String> words = splitIntoWords(text);

        Map<String, Integer> wordFrequencyMap = new HashMap<>();

        for (String word : words) {
            wordFrequencyMap.put(word, wordFrequencyMap.getOrDefault(word, 0) + 1);
        }

        HashMap<String, Integer> lemmaFrequency = new HashMap<>();


        try {
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();

            for (Map.Entry<String, Integer> wordFrequency : wordFrequencyMap.entrySet()) {
                String word = wordFrequency.getKey();
                Integer frequency = wordFrequency.getValue();

                  if (!isContentWord(word, luceneMorph)) {
                    continue;
                }

                // Считаем леммы
                List<String> wordBaseForms = luceneMorph.getNormalForms(word);

                for (String lemma : wordBaseForms) {
                    lemmaFrequency.put(lemma, lemmaFrequency.getOrDefault(lemma, 0) + frequency);
                }

            }
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            System.err.println("Этап анализа текста: " + e.getMessage());
        }

        return lemmaFrequency;
    }

    public static String searchWordByLemma (String contex, String lemma) {
        String text = removeHtmlTags(contex);
        text = keepOnlyRussianLetters(text);
        List<String> words = splitIntoWords(text);


        String foundWord = "";
        try {
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();

            for (String word : words) {
                List<String> wordBaseForms = luceneMorph.getNormalForms(word);
                for (String wordForm : wordBaseForms) {
                    if (wordForm.equals(lemma)) {
                        // Возвращаем слово, по которому нашли лемму
                        return word;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("searchWordByLemma: " + e.getMessage());
        }
        return  foundWord;
    }

}
