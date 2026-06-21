package com.example.searchservice.rpc;

import com.example.common.NoteSearchRequest;
import com.example.common.NoteSearchResponse;
import com.example.common.SearchRpcService;
import com.example.common.SuggestResponse;
import com.example.common.UserSearchResponse;
import com.example.searchservice.service.NoteSearchService;
import com.example.searchservice.service.SuggestService;
import com.example.searchservice.service.UserSearchService;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class SearchRpcServiceImpl implements SearchRpcService {

    private final NoteSearchService noteSearchService;
    private final UserSearchService userSearchService;
    private final SuggestService suggestService;

    public SearchRpcServiceImpl(NoteSearchService noteSearchService, UserSearchService userSearchService, SuggestService suggestService) {
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
