package cn.edu.gdou.jingbanyou.framework.filter;

import cn.edu.gdou.jingbanyou.common.core.domain.VisitorSessionDTO;
import cn.edu.gdou.jingbanyou.common.core.service.TouristSessionService;
import cn.edu.gdou.jingbanyou.common.utils.StringUtils;
import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 游客端会话过滤器
 * <p>
 * 透明处理 visitorId 的校验、自动创建和续期。
 * 拦截所有 /api/tourist/* 请求，从请求中提取 visitorId，
 * 校验 Redis 中是否存在，不存在则自动创建，TTL=2小时。
 *
 * @author jingbanyou
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TouristSessionFilter extends OncePerRequestFilter {

    /**
     * 请求头中的 visitorId
     */
    private static final String HEADER_VISITOR_ID = "X-Visitor-Id";

    /**
     * request attribute 中存放会话信息的 key
     */
    public static final String REQUEST_ATTR_SESSION = "visitorSession";

    /**
     * visitorId 提取优先级
     */
    private static final String[] VISITOR_ID_KEYS = {
            "visitorId", "visitor_id", "visitorid"
    };

    private final TouristSessionService touristSessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // 1. 尝试从请求中提取 visitorId
            String visitorId = extractVisitorId(request);

            if (StringUtils.isNotEmpty(visitorId)) {
                // 2. 从 URL 参数获取 scenicId 和 entranceId
                String scenicIdStr = request.getParameter("scenicId");
                Long scenicId = parseLong(scenicIdStr);
                String entranceId = request.getParameter("entranceId");

                // 3. 校验或创建会话
                VisitorSessionDTO session = touristSessionService.getOrCreateSession(visitorId, scenicId, entranceId);

                // 记录在线心跳（按景区维度实时统计在线人数）
                touristSessionService.heartbeat(visitorId, scenicId);

                // 4. 将会话信息存入 request attribute，供后续 Controller 使用
                request.setAttribute(REQUEST_ATTR_SESSION, session);

                log.debug("[游客会话] 会话处理完成: visitorId={}, scenicId={}", visitorId, session.getSceneId());
            } else {
                log.debug("[游客会话] 未提取到 visitorId，跳过会话处理");
            }

            // 5. 继续执行 Filter 链
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("[游客会话] 会话过滤器异常，放行请求", e);
            // 异常时放行，不影响业务
            filterChain.doFilter(request, response);
        }
    }

    /**
     * 从请求中提取 visitorId
     * 优先级：请求头 > URL参数
     * 注意：POST 请求 body 中的 visitorId 由 Controller 直接处理，避免读取 body 导致 Controller @RequestBody 失效
     */
    private String extractVisitorId(HttpServletRequest request) {
        // 1. 优先从请求头获取
        String visitorId = request.getHeader(HEADER_VISITOR_ID);
        if (StringUtils.isNotEmpty(visitorId)) {
            return visitorId;
        }

        // 2. 从 URL 参数获取（GET 和 POST 的 query string 都适用）
        for (String key : VISITOR_ID_KEYS) {
            visitorId = request.getParameter(key);
            if (StringUtils.isNotEmpty(visitorId)) {
                return visitorId;
            }
        }

        return null;
    }

    private Long parseLong(String value) {
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 只对 /tourist/* 路径生效
        return !path.startsWith("/tourist");
    }
}
