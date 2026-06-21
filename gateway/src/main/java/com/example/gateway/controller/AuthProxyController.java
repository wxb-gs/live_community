package com.example.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.util.Enumeration;

@RestController
@RequestMapping("/api/auth")
public class AuthProxyController {

    private final RestTemplate restTemplate;
    private static final String AUTH_SERVICE = "http://auth-service:8084";

    public AuthProxyController() {
        CloseableHttpClient httpClient = HttpClientBuilder.create()
                .disableAuthCaching()
                .disableAutomaticRetries()
                .build();
        this.restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
        this.restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) { return false; }
            @Override
            public void handleError(ClientHttpResponse response) {}
        });
    }

    @RequestMapping("/**")
    public void proxy(HttpServletRequest request, HttpServletResponse response,
                      @RequestBody(required = false) String body) {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String url = AUTH_SERVICE + path + (query != null ? "?" + query : "");

        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (!"host".equalsIgnoreCase(name)) {
                headers.add(name, request.getHeader(name));
            }
        }

        HttpEntity<String> entity = body != null ? new HttpEntity<>(body, headers) : new HttpEntity<>(headers);
        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.valueOf(request.getMethod()), entity, String.class);

        response.setStatus(resp.getStatusCode().value());
        resp.getHeaders().forEach((key, values) -> {
            if (!"transfer-encoding".equalsIgnoreCase(key) && !"content-length".equalsIgnoreCase(key)) {
                values.forEach(v -> response.addHeader(key, v));
            }
        });

        String respBody = resp.getBody();
        if (respBody != null) {
            response.setContentType("application/json;charset=UTF-8");
            byte[] bytes = respBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            response.setContentLength(bytes.length);
            try {
                response.getOutputStream().write(bytes);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
