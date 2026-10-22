package com.example.upload.rpc;

import com.example.common.*;
import com.example.upload.service.FileService;
import java.util.*;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class UploadRpcServiceImpl implements UploadRpcService {
  private final FileService files;

  public UploadRpcServiceImpl(FileService files) {
    this.files = files;
  }

  public PresignedUrlResponse generatePresignedUrl(PresignedUrlRequest request) {
    return files.prepare(request);
  }

  public FileReference confirmedFile(String id, Long userId, Long noteId) {
    return files.confirmed(id, userId, noteId);
  }

  public Map<String, String> resolveFiles(List<String> keys) {
    return files.resolve(keys);
  }
}
