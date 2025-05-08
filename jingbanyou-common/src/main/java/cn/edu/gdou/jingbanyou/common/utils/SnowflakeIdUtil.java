package cn.edu.gdou.jingbanyou.common.utils;

/**
 * 雪花 ID 工具类
 * 从雪花 ID 中提取时间戳等信息
 *
 * @author jingbanyou
 */
public final class SnowflakeIdUtil {

    private SnowflakeIdUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 从雪花 ID 提取时间戳
     * 雪花 ID 结构：1(符号位) + 41位时间戳 + 10位 workerId + 12位序列号
     *
     * @param snowflakeIdStr 雪花 ID 字符串
     * @return 时间戳（毫秒），解析失败返回 0
     */
    public static long extractTimestamp(String snowflakeIdStr) {
        try {
            long id = Long.parseLong(snowflakeIdStr);
            return extractTimestamp(id);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 从雪花 ID 提取时间戳
     * 雪花 ID 结构：1(符号位) + 41位时间戳 + 10位 workerId + 12位序列号
     *
     * @param snowflakeId 雪花 ID
     * @return 时间戳（毫秒）
     */
    public static long extractTimestamp(long snowflakeId) {
        // 去掉最高位（符号位），然后右移 22 位（workerId 10位 + sequence 12位）
        return (snowflakeId & 0x1FFFFFFFFFFFFFFFL) >> 22;
    }
}
