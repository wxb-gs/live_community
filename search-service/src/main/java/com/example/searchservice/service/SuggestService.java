package com.example.searchservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggestOption;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import com.example.common.SuggestResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SuggestService {

    private static final Logger log = LoggerFactory.getLogger(SuggestService.class);

    private final ElasticsearchClient esClient;

    public SuggestService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    public SuggestResponse suggest(String q) {
        List<SuggestResponse.Suggestion> results = new ArrayList<>();

        try {
            SearchResponse<Map> response = esClient.search(s -> s
                .index("notes", "users")
                .suggest(sug -> sug
                    .suggesters("note_suggest", ss -> ss
                        .prefix(q)
                        .completion(comp -> comp
                            .field("title.suggest")
                            .size(5)
                            .skipDuplicates(true)
                        )
                    )
                    .suggesters("user_suggest", ss -> ss
                        .prefix(q)
                        .completion(comp -> comp
                            .field("username.suggest")
                            .size(3)
                            .skipDuplicates(true)
                        )
                    )
                ),
                Map.class
            );

            if (response.suggest() != null) {
                List<Suggestion<Map>> noteSuggestions = response.suggest().get("note_suggest");
                if (noteSuggestions != null) {
                    for (Suggestion<Map> sug : noteSuggestions) {
                        if (sug.completion() != null) {
                            for (CompletionSuggestOption<Map> opt : sug.completion().options()) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> source = (Map<String, Object>) opt.source();
                                Long id = source != null && source.get("id") != null
                                    ? ((Number) source.get("id")).longValue() : null;
                                results.add(new SuggestResponse.Suggestion(opt.text(), "note", id));
                            }
                        }
                    }
                }

                List<Suggestion<Map>> userSuggestions = response.suggest().get("user_suggest");
                if (userSuggestions != null) {
                    for (Suggestion<Map> sug : userSuggestions) {
                        if (sug.completion() != null) {
                            for (CompletionSuggestOption<Map> opt : sug.completion().options()) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> source = (Map<String, Object>) opt.source();
                                Long id = source != null && source.get("id") != null
                                    ? ((Number) source.get("id")).longValue() : null;
                                results.add(new SuggestResponse.Suggestion(opt.text(), "user", id));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Suggest failed for q={}", q, e);
        }

        return new SuggestResponse(results);
    }
}
