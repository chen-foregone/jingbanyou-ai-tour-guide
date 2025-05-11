package cn.edu.gdou.jingbanyou.tourist.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j 熔断配置
 *
 * <p>为外部 API 调用（DashScope LLM/TTS/ASR、高德地图）配置熔断器。
 * 熔断器会在失败率超过阈值时打开，后续请求直接降级，防止级联失败。
 *
 * @author jingbanyou
 */
@Slf4j
@Configuration
public class Resilience4jConfig {

    @Value("${jingbanyou.tourist.circuit-breaker.failure-rate-threshold:50}")
    private int failureRateThreshold;

    @Value("${jingbanyou.tourist.circuit-breaker.wait-duration-in-open-state:30}")
    private int waitDurationInOpenState;

    @Value("${jingbanyou.tourist.circuit-breaker.slow-call-duration-threshold:10}")
    private int slowCallDurationThreshold;

    /**
     * 全局熔断器注册表
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(Duration.ofSeconds(waitDurationInOpenState))
                .slowCallDurationThreshold(Duration.ofSeconds(slowCallDurationThreshold))
                .slowCallRateThreshold(80)
                .minimumNumberOfCalls(5)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        return CircuitBreakerRegistry.of(defaultConfig);
    }

    /**
     * DashScope LLM 熔断器
     */
    @Bean
    public CircuitBreaker dashscopeLlmCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("dashscope-llm");
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.info("[熔断器] dashscope-llm 状态变更: {} -> {}",
                        event.getStateTransition().getFromState(), event.getStateTransition().getToState()))
                .onFailureRateExceeded(event -> log.warn("[熔断器] dashscope-llm 失败率超限: {}%", event.getFailureRate()));
        return circuitBreaker;
    }

    /**
     * DashScope TTS 熔断器
     */
    @Bean
    public CircuitBreaker dashscopeTtsCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("dashscope-tts");
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.info("[熔断器] dashscope-tts 状态变更: {} -> {}",
                        event.getStateTransition().getFromState(), event.getStateTransition().getToState()))
                .onFailureRateExceeded(event -> log.warn("[熔断器] dashscope-tts 失败率超限: {}%", event.getFailureRate()));
        return circuitBreaker;
    }

    /**
     * DashScope ASR 熔断器
     */
    @Bean
    public CircuitBreaker dashscopeAsrCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("dashscope-asr");
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.info("[熔断器] dashscope-asr 状态变更: {} -> {}",
                        event.getStateTransition().getFromState(), event.getStateTransition().getToState()))
                .onFailureRateExceeded(event -> log.warn("[熔断器] dashscope-asr 失败率超限: {}%", event.getFailureRate()));
        return circuitBreaker;
    }

    /**
     * 高德地图路线规划熔断器
     */
    @Bean
    public CircuitBreaker amapRouteCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("amap-route");
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.info("[熔断器] amap-route 状态变更: {} -> {}",
                        event.getStateTransition().getFromState(), event.getStateTransition().getToState()))
                .onFailureRateExceeded(event -> log.warn("[熔断器] amap-route 失败率超限: {}%", event.getFailureRate()));
        return circuitBreaker;
    }
}
