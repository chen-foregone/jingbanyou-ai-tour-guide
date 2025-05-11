package cn.edu.gdou.jingbanyou.tourist.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 对话专用线程池配置
 *
 * <p>为流式对话、TTS 等 AI 耗时操作提供独立线程池，与框架全局线程池隔离，
 * 避免 AI 调用阻塞日志、管理等其他异步任务。</p>
 *
 * <p>参数选取依据：AI 对话为 I/O 密集型（调用大模型 API），核心线程=10，
 * 最大线程=50，队列容量=200。拒绝策略使用 CallerRunsPolicy，线程池满载时
 * 由调用方线程执行，提供背压而非丢弃。</p>
 *
 * @author jingbanyou
 */
@Slf4j
@Configuration
public class AiExecutorConfig {

    @Bean("aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("ai-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("[AI线程池] 初始化完成: core={}, max={}, queue={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        return executor;
    }
}
