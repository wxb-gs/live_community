package com.example.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "sms")
public class SmsConfig {
    private String provider = "mock";
    private int codeLength = 6;
    private long codeTtl = 300;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public int getCodeLength() { return codeLength; }
    public void setCodeLength(int codeLength) { this.codeLength = codeLength; }
    public long getCodeTtl() { return codeTtl; }
    public void setCodeTtl(long codeTtl) { this.codeTtl = codeTtl; }
}
