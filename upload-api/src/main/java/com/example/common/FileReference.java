package com.example.common;

import java.io.Serializable;

public record FileReference(String fileId, String objectKey) implements Serializable {}
