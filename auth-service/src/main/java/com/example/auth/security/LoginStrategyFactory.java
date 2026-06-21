package com.example.auth.security;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory pattern — holds all {@link LoginStrategy} implementations
 * and dispatches by {@link LoginType}.
 */
@Component
public class LoginStrategyFactory {

    private final Map<LoginType, LoginStrategy> strategyMap;

    public LoginStrategyFactory(List<LoginStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(LoginStrategy::getLoginType, Function.identity()));
    }

    public LoginStrategy getStrategy(LoginType type) {
        LoginStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported login type: " + type);
        }
        return strategy;
    }
}
