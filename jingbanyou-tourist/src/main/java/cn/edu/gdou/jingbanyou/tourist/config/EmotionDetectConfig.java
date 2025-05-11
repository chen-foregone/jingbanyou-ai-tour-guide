package cn.edu.gdou.jingbanyou.tourist.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 情感分析异步线程池配置
 *
 * <p>为 EmotionDetectService 提供独立的线程池，避免 AI 调用阻塞主业务线程池。
 * 配置：核心线程=2，最大线程=5，队列容量=100，线程名称前缀=emotion-detection-
 *
 * @author jingbanyou
 */
@Slf4j
@Configuration
public class EmotionDetectConfig {

    /**
     * 情感分析专用线程池
     *
     * @return Executor
     */
    @Bean("emotionDetectionExecutor")
    public Executor emotionDetectionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("emotion-detection-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("[情感分析] 线程池初始化完成: core={}, max={}, queue={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        return executor;
    }
}
