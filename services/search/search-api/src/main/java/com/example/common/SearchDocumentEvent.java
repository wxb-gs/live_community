package com.example.common;

import java.util.Map;

public record SearchDocumentEvent(
    int schemaVersion,
    String entityType,
    long entityId,
    long version,
    boolean deleted,
    Map<String, Object> document) {}
