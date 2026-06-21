package com.example.gateway.controller;

import com.example.common.*;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    @DubboReference(check = false)
    private SearchRpcService searchRpcService;

    @GetMapping("/note")
    public Result<NoteSearchResponse> searchNotes(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "relevance") String sort) {
        NoteSearchRequest req = new NoteSearchRequest(q, page, size, category, sort);
        NoteSearchResponse resp = searchRpcService.searchNotes(req);
        return Result.ok(resp);
    }

    @GetMapping("/user")
    public Result<UserSearchResponse> searchUsers(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        UserSearchResponse resp = searchRpcService.searchUsers(q, page, size);
        return Result.ok(resp);
    }

    @GetMapping("/all")
    public Result<Map<String, Object>> searchAll(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        NoteSearchRequest noteReq = new NoteSearchRequest(q, page, size, null, "relevance");
        NoteSearchResponse notes = searchRpcService.searchNotes(noteReq);
        UserSearchResponse users = searchRpcService.searchUsers(q, page, size);
        Map<String, Object> combined = new HashMap<>();
        combined.put("notes", notes);
        combined.put("users", users);
        return Result.ok(combined);
    }

    @GetMapping("/suggest")
    public Result<SuggestResponse> suggest(@RequestParam String q) {
        SuggestResponse resp = searchRpcService.suggest(q);
        return Result.ok(resp);
    }
}
