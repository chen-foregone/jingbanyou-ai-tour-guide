package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;

/**
 * 语音合成服务接口
 *
 * @author jingbanyou
 */
public interface ITtsService {

    /**
     * 流式合成语音
     *
     * @param text 合成文本
     * @param digitalHuman 数字人配置
     * @return 音频字节流
     */
    Flux<byte[]> streamAudio(String text, DigitalHumanConfig digitalHuman);

    /**
     * 合成语音（文件方式，异步）
     *
     * @param text 合成文本
     * @param digitalHuman 数字人配置
     * @return 音频文件访问路径的 CompletableFuture
     */
    CompletableFuture<String> synthesize(String text, DigitalHumanConfig digitalHuman);

    /**
     * 获取 TTS 音频文件存储目录
     *
     * @return 音频目录路径
     */
    String getAudioDir();
}
