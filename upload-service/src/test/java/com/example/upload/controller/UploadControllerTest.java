package com.example.upload.controller;

import com.example.upload.UploadServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = UploadServiceApplication.class,
    properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "dubbo.registry.address=N/A",
        "minio.endpoint=http://localhost:9000",
        "minio.access-key=minioadmin",
        "minio.secret-key=minioadmin",
        "minio.bucket=uploads"
    })
@AutoConfigureMockMvc
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void presignedUrl_shouldReturn400_whenMissingParams() throws Exception {
        mockMvc.perform(get("/api/upload/presigned"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void presignedUrl_shouldReturn200_whenValidParams() throws Exception {
        mockMvc.perform(get("/api/upload/presigned")
                        .param("fileName", "test.png")
                        .param("contentType", "image/png"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.uploadUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.objectKey").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());
    }
}
