package com.example.searchservice.service;

import com.example.common.NoteSearchRequest;
import com.example.common.NoteSearchResponse;
import com.example.common.SearchRpcService;
import com.example.common.SuggestResponse;
import com.example.common.UserSearchResponse;
import org.springframework.stereotype.Service;

@Service
public class SearchApplicationService implements SearchRpcService {

  private final NoteSearchService noteSearchService;
  private final UserSearchService userSearchService;
  private final SuggestService suggestService;

  public SearchApplicationService(
      NoteSearchService noteSearchService,
      UserSearchService userSearchService,
      SuggestService suggestService) {
    this.noteSearchService = noteSearchService;
    this.userSearchService = userSearchService;
    this.suggestService = suggestService;
  }

  @Override
  public NoteSearchResponse searchNotes(NoteSearchRequest request) {
    return noteSearchService.search(request);
  }

  @Override
  public UserSearchResponse searchUsers(String q, int page, int size) {
    return userSearchService.search(q, page, size);
  }

  @Override
  public SuggestResponse suggest(String q) {
    return suggestService.suggest(q);
  }
}
