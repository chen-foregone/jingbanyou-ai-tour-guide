package cn.edu.gdou.jingbanyou.tourist.service;

/**
 * 语音转文字服务接口
 *
 * @author jingbanyou
 */
public interface ITranscribeService {

    /**
     * 将音频转换为文字
     *
     * @param audioData 音频字节数据
     * @param fileName 文件名（用于推断格式）
     * @param language 语言提示
     * @return 识别的文字
     */
    String transcribe(byte[] audioData, String fileName, String language);
}
