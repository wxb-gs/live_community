package com.example.common;

import java.util.List;
import java.util.Map;

public interface UploadRpcService {
  PresignedUrlResponse generatePresignedUrl(PresignedUrlRequest request);

  FileReference confirmedFile(String fileId, Long userId, Long noteId);

  Map<String, String> resolveFiles(List<String> objectKeys);
}
