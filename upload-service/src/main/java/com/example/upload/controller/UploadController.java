package com.example.upload.controller;

import com.example.common.*;
import com.example.upload.service.FileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/upload")
public class UploadController {
  private final FileService files;

  public UploadController(FileService files) {
    this.files = files;
  }

  @PostMapping("/prepare")
  public Result<PresignedUrlResponse> prepare(
      @AuthenticationPrincipal Long userId, @RequestBody PresignedUrlRequest request) {
    request.setUserId(userId);
    return Result.ok(files.prepare(request));
  }

  @PostMapping("/confirm")
  public Result<FileReference> confirm(
      @AuthenticationPrincipal Long userId, @RequestBody Confirm request) {
    return Result.ok(files.confirm(request.fileId(), userId));
  }

  @GetMapping("/presigned")
  public Result<?> legacy() {
    throw new org.springframework.web.server.ResponseStatusException(
        org.springframework.http.HttpStatus.GONE, "Use prepare, upload, confirm, then publish");
  }

  public record Confirm(String fileId) {}
}
