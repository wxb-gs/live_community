package com.example.searchservice.rpc;

import com.example.common.*;
import com.example.searchservice.service.SearchApplicationService;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class SearchRpcServiceImpl implements SearchRpcService {
  private final SearchApplicationService app;

  public SearchRpcServiceImpl(SearchApplicationService app) {
    this.app = app;
  }

  public NoteSearchResponse searchNotes(NoteSearchRequest r) {
    return app.searchNotes(r);
  }

  public UserSearchResponse searchUsers(String q, int p, int s) {
    return app.searchUsers(q, p, s);
  }

  public SuggestResponse suggest(String q) {
    return app.suggest(q);
  }
}
