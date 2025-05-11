package cn.edu.gdou.jingbanyou.tourist.service.impl;

import cn.edu.gdou.jingbanyou.tourist.service.IChatMetadataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 对话元数据服务实现
 * 负责对话元数据的 Redis 读写操作
 *
 * @author jingbanyou
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMetadataServiceImpl implements IChatMetadataService {

    private static final String KEY_PREFIX = "chat:meta:";
    private static final long TTL_HOURS = 1;

    private final RedisTemplate<String, Object> chatMetadataRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void saveMetadata(String sessionId, String intent, Integer tokensUsed,
                              String modelUsed, int responseTimeMs, String ragDocs) {
        try {
            Map<String, Object> meta = new HashMap<>();
            meta.put("intent", intent);
            meta.put("tokensUsed", tokensUsed);
            meta.put("modelUsed", modelUsed);
            meta.put("responseTimeMs", responseTimeMs);
            meta.put("ragDocs", ragDocs);
            String json = objectMapper.writeValueAsString(meta);
            chatMetadataRedisTemplate.opsForValue().set(KEY_PREFIX + sessionId, json, TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("保存对话元数据失败 sessionId={}", sessionId, e);
        }
    }

    @Override
    public Map<String, Object> getMetadata(String sessionId) {
        try {
            Object raw = chatMetadataRedisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
            if (raw == null) {
                return null;
            }
            if (raw instanceof String) {
                return objectMapper.readValue((String) raw, Map.class);
            }
        } catch (Exception e) {
            log.warn("获取对话元数据失败 sessionId={}", sessionId, e);
        }
        return null;
    }

    @Override
    public void deleteMetadata(String sessionId) {
        try {
            chatMetadataRedisTemplate.delete(KEY_PREFIX + sessionId);
        } catch (Exception e) {
            log.warn("删除对话元数据失败 sessionId={}", sessionId, e);
        }
    }
}
