package com.example.searchservice.controller;

import com.example.common.*;
import com.example.searchservice.rpc.SearchRpcServiceImpl;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search/internal")
public class SearchController {

    private final SearchRpcServiceImpl rpcService;

    public SearchController(SearchRpcServiceImpl rpcService) {
        this.rpcService = rpcService;
    }

    @GetMapping("/note")
    public NoteSearchResponse searchNotes(@RequestParam String q,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return rpcService.searchNotes(new NoteSearchRequest(q, page, size, null, "relevance"));
    }

    @GetMapping("/suggest")
    public SuggestResponse suggest(@RequestParam String q) {
        return rpcService.suggest(q);
    }
}
