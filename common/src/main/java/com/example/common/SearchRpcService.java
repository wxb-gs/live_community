package com.example.common;

public interface SearchRpcService {
    NoteSearchResponse searchNotes(NoteSearchRequest request);
    UserSearchResponse searchUsers(String q, int page, int size);
    SuggestResponse suggest(String q);
}
