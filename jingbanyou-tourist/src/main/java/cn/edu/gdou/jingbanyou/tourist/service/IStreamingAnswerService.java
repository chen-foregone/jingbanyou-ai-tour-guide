package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.tourist.dto.StreamAnswerContext;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

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
     * @param ctx 流式答案上下文，包含 sessionId、scenicId、intent 等全部参数
     * @return SSE 事件流：answer_fragment, audio, done/error
     */
    Flux<ServerSentEvent<String>> streamAnswer(StreamAnswerContext ctx);
}
