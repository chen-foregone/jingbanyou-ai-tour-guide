package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 流式答案服务接口
 *
 * <p>职责：
 * <ol>
 *   <li>token 级流式输出 AI 回答</li>
 *   <li>按段落触发 TTS 语音合成</li>
 *   <li>生成 SSE 事件流</li>
 *   <li>流结束后写入 Redis ChatMemory</li>
 * </ol>
 *
 * @author jingbanyou
 */
public interface IStreamingAnswerService {

    /**
     * 流式生成并发送答案（SSE）
     *
     * @param sessionId    会话ID
     * @param scenicId     景区ID
     * @param humanId      数字人配置ID
     * @param visitorId    游客ID
     * @param intent       意图类型
     * @param userMessage  用户原始消息
     * @param finalPrompt  最终用户 prompt（由调用方构造好）
     * @param digitalHuman 数字人配置（用于 TTS）
     * @param rawRoutes    路线数据（route_plan intent 专用，可为 null）
     * @param intentType   意图类型字符串
     * @param graphCostMs  Graph 执行耗时
     * @param startTimestamp SSE 开始时间戳
     * @return SSE 事件流：answer_fragment, audio, done/error
     */
    Flux<ServerSentEvent<String>> streamAnswer(
            String sessionId,
            Long scenicId,
            Long humanId,
            String visitorId,
            String intent,
            String userMessage,
            DigitalHumanConfig digitalHuman,
            List<?> rawRoutes,
            String intentType,
            int graphCostMs,
            long startTimestamp
    );
}
