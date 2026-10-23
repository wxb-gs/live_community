package com.example.searchservice.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.common.UserSearchResponse;
import com.example.common.UserSearchResponse.UserSearchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UserSearchService {

  private static final Logger log = LoggerFactory.getLogger(UserSearchService.class);
  private static final String USERS_INDEX = "users";

  private final ElasticsearchClient esClient;

  public UserSearchService(ElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  public UserSearchResponse search(String q, int page, int size) {
    if (page < 1 || page > 10000 || size < 1 || size > 100)
      throw new IllegalArgumentException("Invalid pagination");
    int from = (page - 1) * size;

    try {
      SearchResponse<Map> response =
          esClient.search(
              s ->
                  s.index(USERS_INDEX)
                      .query(
                          qq ->
                              qq.bool(
                                  b ->
                                      b.must(
                                              m ->
                                                  m.multiMatch(
                                                      mm ->
                                                          mm.fields("username", "nickname^2")
                                                              .query(q)))
                                          .filter(
                                              f -> f.term(t -> t.field("status").value("ACTIVE")))))
                      .from(from)
                      .size(size)
                      .trackTotalHits(th -> th.enabled(true)),
              Map.class);

      long total = response.hits().total() != null ? response.hits().total().value() : 0;
      List<UserSearchResult> results = new ArrayList<>();
      for (Hit<Map> hit : response.hits().hits()) {
        @SuppressWarnings("unchecked")
        Map<String, Object> source = (Map<String, Object>) hit.source();
        if (source != null) {
          UserSearchResult r = new UserSearchResult();
          r.setId(toLong(source.get("id")));
          r.setUsername((String) source.get("username"));
          r.setNickname((String) source.get("nickname"));
          r.setAvatar((String) source.get("avatar"));
          results.add(r);
        }
      }

      UserSearchResponse resp = new UserSearchResponse();
      resp.setTotal(total);
      resp.setPage(page);
      resp.setSize(size);
      resp.setResults(results);
      return resp;
    } catch (Exception e) {
      log.error("User search failed for q={}", q, e);
      UserSearchResponse empty = new UserSearchResponse();
      empty.setTotal(0);
      empty.setPage(page);
      empty.setSize(size);
      empty.setResults(List.of());
      return empty;
    }
  }

  private long toLong(Object val) {
    if (val instanceof Number n) return n.longValue();
    if (val instanceof String s) return Long.parseLong(s);
    return 0L;
  }
}
