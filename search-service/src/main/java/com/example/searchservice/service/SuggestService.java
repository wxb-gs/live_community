package com.example.searchservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
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
            SearchResponse<Map> noteResp = esClient.search(s -> s
                .index("notes")
                .query(qq -> qq
                    .matchPhrasePrefix(mpp -> mpp
                        .field("title")
                        .query(q)
                    )
                )
                .source(src -> src.filter(f -> f.includes("id", "title")))
                .size(5),
                Map.class
            );
            for (Hit<Map> hit : noteResp.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source != null) {
                    String title = (String) source.get("title");
                    Long id = source.get("id") != null ? ((Number) source.get("id")).longValue() : null;
                    if (title != null && !title.isEmpty()) {
                        results.add(new SuggestResponse.Suggestion(title, "note", id));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Note suggest failed for q={}", q, e);
        }

        try {
            SearchResponse<Map> userResp = esClient.search(s -> s
                .index("users")
                .query(qq -> qq
                    .matchPhrasePrefix(mpp -> mpp
                        .field("username")
                        .query(q)
                    )
                )
                .source(src -> src.filter(f -> f.includes("id", "username")))
                .size(3),
                Map.class
            );
            for (Hit<Map> hit : userResp.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source != null) {
                    String username = (String) source.get("username");
                    Long id = source.get("id") != null ? ((Number) source.get("id")).longValue() : null;
                    if (username != null && !username.isEmpty()) {
                        results.add(new SuggestResponse.Suggestion(username, "user", id));
                    }
                }
            }
        } catch (Exception e) {
            log.error("User suggest failed for q={}", q, e);
        }

        return new SuggestResponse(results);
    }
}
