package com.example.auth.security;

public enum LoginType {
    PASSWORD("password"),
    WECHAT("wechat"),
    TAOBAO("taobao"),
    PHONE("phone");

    private final String value;

    LoginType(String value) { this.value = value; }

    public String getValue() { return value; }

    public static LoginType fromValue(String value) {
        for (LoginType type : values()) {
            if (type.value.equals(value)) return type;
        }
        throw new IllegalArgumentException("Unknown login type: " + value);
    }
}
