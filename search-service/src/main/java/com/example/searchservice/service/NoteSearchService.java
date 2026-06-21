package com.example.searchservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.common.NoteSearchRequest;
import com.example.common.NoteSearchResponse;
import com.example.common.NoteSearchResponse.NoteSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class NoteSearchService {

    private static final Logger log = LoggerFactory.getLogger(NoteSearchService.class);
    private static final String NOTES_INDEX = "notes";

    private final ElasticsearchClient esClient;

    public NoteSearchService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    public NoteSearchResponse search(NoteSearchRequest request) {
        int from = (request.getPage() - 1) * request.getSize();
        int size = request.getSize();

        try {
            SearchResponse<Map> response = esClient.search(s -> s
                .index(NOTES_INDEX)
                .query(q -> q
                    .functionScore(fs -> fs
                        .query(inner -> inner
                            .bool(b -> {
                                b.must(m -> m.multiMatch(mm -> mm
                                    .fields("title^3", "content", "summary^2")
                                    .query(request.getQ())
                                ));
                                if (request.getCategory() != null && !request.getCategory().isEmpty()) {
                                    b.filter(f -> f.term(t -> t.field("category").value(request.getCategory())));
                                }
                                b.filter(f -> f.term(t -> t.field("status").value("PUBLISHED")));
                                return b;
                            })
                        )
                        .functions(fn -> fn
                            .scriptScore(ss -> ss
                                .script(sc -> sc
                                    .inline(in -> in
                                        .source("_score * Math.log(1 + doc['view_count'].value * 0.01 + doc['like_count'].value * 0.5 + 2)")
                                    )
                                )
                            )
                        )
                        .scoreMode(FunctionScoreMode.Multiply)
                        .boostMode(FunctionBoostMode.Replace)
                    )
                )
                .from(from)
                .size(size)
                .sort(sort -> {
                    if ("views".equals(request.getSort())) {
                        sort.field(f -> f.field("view_count").order(SortOrder.Desc));
                    } else if ("likes".equals(request.getSort())) {
                        sort.field(f -> f.field("like_count").order(SortOrder.Desc));
                    } else if ("time".equals(request.getSort())) {
                        sort.field(f -> f.field("created_at").order(SortOrder.Desc));
                    }
                    return sort;
                })
                .trackTotalHits(th -> th.enabled(true)),
                Map.class
            );

            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            List<NoteSearchResult> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> source = (Map<String, Object>) hit.source();
                if (source != null) {
                    NoteSearchResult r = new NoteSearchResult();
                    r.setId(toLong(source.get("id")));
                    r.setUserId(toLong(source.get("user_id")));
                    r.setTitle((String) source.get("title"));
                    r.setSummary((String) source.get("summary"));
                    r.setTags((String) source.get("tags"));
                    r.setCategory((String) source.get("category"));
                    r.setViewCount(toInt(source.get("view_count")));
                    r.setLikeCount(toInt(source.get("like_count")));
                    r.setCreatedAt(toLong(source.get("created_at")));
                    results.add(r);
                }
            }

            return new NoteSearchResponse(total, request.getPage(), request.getSize(), results);
        } catch (Exception e) {
            log.error("Note search failed for q={}", request.getQ(), e);
            return new NoteSearchResponse(0, request.getPage(), request.getSize(), List.of());
        }
    }

    private long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) return Long.parseLong(s);
        return 0L;
    }

    private int toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) return Integer.parseInt(s);
        return 0;
    }
}
