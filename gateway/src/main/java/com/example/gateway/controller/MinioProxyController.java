package com.example.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
public class MinioProxyController {

    @Value("${minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    @RequestMapping("/minio/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
        try {
            String path = request.getRequestURI().substring("/minio".length());
            String query = request.getQueryString();
            String url = minioEndpoint + path + (query != null ? "?" + query : "");

            byte[] body = request.getInputStream().readAllBytes();

            HttpHeaders headers = new HttpHeaders();
            String contentType = request.getContentType();
            if (contentType != null) headers.setContentType(MediaType.parseMediaType(contentType));

            var rt = new org.springframework.web.client.RestTemplate();
            ResponseEntity<byte[]> resp = rt.exchange(
                URI.create(url),
                HttpMethod.valueOf(request.getMethod()),
                new HttpEntity<>(body, headers),
                byte[].class
            );

            HttpHeaders respHeaders = new HttpHeaders();
            MediaType respCt = resp.getHeaders().getContentType();
            if (respCt != null) respHeaders.setContentType(respCt);
            if (resp.getBody() != null) respHeaders.setContentLength(resp.getBody().length);
            return new ResponseEntity<>(resp.getBody(), respHeaders, resp.getStatusCode());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
