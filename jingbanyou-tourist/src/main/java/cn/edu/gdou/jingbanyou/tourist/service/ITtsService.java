package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import reactor.core.publisher.Flux;

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
     * 合成语音（文件方式）
     *
     * @param text 合成文本
     * @param digitalHuman 数字人配置
     * @return 音频文件访问路径
     */
    String synthesize(String text, DigitalHumanConfig digitalHuman);
}
