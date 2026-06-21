package com.example.common;

import java.io.Serializable;
import java.util.List;

public class SuggestResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<Suggestion> suggestions;

    public SuggestResponse() {}

    public SuggestResponse(List<Suggestion> suggestions) { this.suggestions = suggestions; }

    public List<Suggestion> getSuggestions() { return suggestions; }
    public void setSuggestions(List<Suggestion> suggestions) { this.suggestions = suggestions; }

    public static class Suggestion implements Serializable {
        private static final long serialVersionUID = 1L;
        private String text;
        private String type;
        private Long id;
        public Suggestion() {}
        public Suggestion(String text, String type, Long id) { this.text = text; this.type = type; this.id = id; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }
}
