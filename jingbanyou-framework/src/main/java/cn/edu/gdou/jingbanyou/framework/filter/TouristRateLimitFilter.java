package cn.edu.gdou.jingbanyou.framework.filter;

import java.io.IOException;
import java.util.Collections;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import cn.edu.gdou.jingbanyou.common.utils.StringUtils;
import cn.edu.gdou.jingbanyou.common.utils.ip.IpUtils;

/**
 * 游客端 API 限流过滤器
 * <p>
 * 基于 Redis + Lua 脚本实现限流，优先按 visitorId 限流（300次/分钟），
 * 无 visitorId 时兜底按 IP 限流（60次/分钟）。
 * 仅对 /tourist/* 路径生效。
 *
 * @author jingbanyou
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "tourist.rate-limit.enabled", havingValue = "true", matchIfMissing = false)
public class TouristRateLimitFilter extends OncePerRequestFilter
{
    /** 请求头中的 visitorId */
    private static final String HEADER_VISITOR_ID = "X-Visitor-Id";

    /** visitorId 提取优先级 */
    private static final String[] VISITOR_ID_KEYS = {
            "visitorId", "visitor_id", "visitorid"
    };

    /** 限流 key 前缀 */
    private static final String RATE_LIMIT_KEY_PREFIX = "tourist:rate:";

    /** visitorId 限流：每分钟 300 次（正常用户 5-10 次/分钟，远不会触及） */
    private static final int VISITOR_LIMIT = 300;

    /** IP 兜底限流：每分钟 60 次 */
    private static final int IP_LIMIT = 60;

    /** 时间窗口（秒） */
    private static final int WINDOW_SECONDS = 60;

    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Autowired
    private RedisScript<Long> limitScript;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException
    {
        try
        {
            String visitorId = extractVisitorId(request);
            String rateKey;
            int limit;

            if (StringUtils.isNotEmpty(visitorId))
            {
                rateKey = RATE_LIMIT_KEY_PREFIX + "visitor:" + visitorId;
                limit = VISITOR_LIMIT;
            }
            else
            {
                String clientIp = IpUtils.getIpAddr(request);
                rateKey = RATE_LIMIT_KEY_PREFIX + "ip:" + clientIp;
                limit = IP_LIMIT;
            }

            Long count = redisTemplate.execute(
                    limitScript,
                    Collections.singletonList(rateKey),
                    limit,
                    WINDOW_SECONDS
            );

            if (StringUtils.isNotNull(count) && count > limit)
            {
                log.warn("游客端限流触发: key={}, count={}", rateKey, count);
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":429,\"msg\":\"请求过于频繁，请稍后再试\"}");
                return;
            }

            filterChain.doFilter(request, response);
        }
        catch (Exception e)
        {
            log.error("游客端限流过滤器异常，放行请求", e);
            filterChain.doFilter(request, response);
        }
    }

    /**
     * 从请求中提取 visitorId
     * 优先级：请求头 > URL参数
     */
    private String extractVisitorId(HttpServletRequest request)
    {
        String visitorId = request.getHeader(HEADER_VISITOR_ID);
        if (StringUtils.isNotEmpty(visitorId))
        {
            return visitorId;
        }
        for (String key : VISITOR_ID_KEYS)
        {
            visitorId = request.getParameter(key);
            if (StringUtils.isNotEmpty(visitorId))
            {
                return visitorId;
            }
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        String path = request.getRequestURI();
        return !path.startsWith("/tourist");
    }
}
