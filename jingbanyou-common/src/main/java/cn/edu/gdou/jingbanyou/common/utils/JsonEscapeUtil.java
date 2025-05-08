package cn.edu.gdou.jingbanyou.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 转义工具类
 * 使用 Jackson ObjectMapper.writeValueAsString 替代手动转义
 * 正确处理所有需要转义的字符
 *
 * @author jingbanyou
 */
public final class JsonEscapeUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonEscapeUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 将对象序列化为 JSON 字符串（用于嵌入到 JSON 字符串值中）
     *
     * @param value 任意对象
     * @return JSON 字符串，如果序列化失败返回空字符串
     */
    public static String escape(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "";
        }
    }
}
