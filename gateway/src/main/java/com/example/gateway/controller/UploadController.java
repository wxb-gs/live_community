package com.example.gateway.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.example.common.PresignedUrlRequest;
import com.example.common.PresignedUrlResponse;
import com.example.common.Result;
import com.example.common.UploadRpcService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    @DubboReference(check = false)
    private UploadRpcService uploadRpcService;

    @GetMapping("/presigned")
    @SentinelResource(value = "upload-presigned", blockHandler = "rateLimitFallback")
    public Result<PresignedUrlResponse> presignedUrl(
            @AuthenticationPrincipal Long userId,
            @RequestParam String fileName,
            @RequestParam String contentType) {
        PresignedUrlRequest req = new PresignedUrlRequest(fileName, contentType);
        PresignedUrlResponse resp = uploadRpcService.generatePresignedUrl(req);
        return Result.ok(resp);
    }

    public Result<?> rateLimitFallback(String fileName, String contentType, BlockException ex) {
        return Result.error(429, "Too many requests, please try again later");
    }
}
