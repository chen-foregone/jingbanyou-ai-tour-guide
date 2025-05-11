package cn.edu.gdou.jingbanyou.tourist.controller;

import cn.edu.gdou.jingbanyou.common.core.controller.BaseController;
import cn.edu.gdou.jingbanyou.common.core.domain.AjaxResult;
import cn.edu.gdou.jingbanyou.common.core.service.TouristSessionService;
import cn.edu.gdou.jingbanyou.common.enums.TouristErrorCode;
import cn.edu.gdou.jingbanyou.manage.dto.ScenicAreaVO;
import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import cn.edu.gdou.jingbanyou.manage.entity.Faq;
import cn.edu.gdou.jingbanyou.manage.service.IDigitalHumanConfigService;
import cn.edu.gdou.jingbanyou.manage.service.IScenicAreaService;
import cn.edu.gdou.jingbanyou.tourist.service.IBootstrapCacheService;
import cn.edu.gdou.jingbanyou.tourist.service.IFaqCacheService;
import cn.hutool.core.bean.BeanUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首屏初始化
 *
 * @author jingbanyou
 */
@Slf4j
@RestController
@RequestMapping("/tourist")
@RequiredArgsConstructor
@Tag(name = "游客端-首屏", description = "景区信息、数字人配置、欢迎语和热门FAQ")
public class BootstrapController extends BaseController {

    private final IBootstrapCacheService bootstrapCacheService;
    private final IScenicAreaService scenicAreaService;
    private final IDigitalHumanConfigService digitalHumanConfigService;
    private final IFaqCacheService faqCacheService;
    private final TouristSessionService touristSessionService;

    /**
     * 前台首屏初始化
     *
     * @param scenicId 景区ID
     * @return 景区信息、数字人配置、欢迎语
     */
    @Operation(summary = "前台首屏初始化", description = "获取景区信息、数字人配置、欢迎语和热门FAQ")
    @GetMapping("/bootstrap")
    public AjaxResult bootstrap(@RequestParam(required = false) Long scenicId) {
        if (scenicId == null) {
            return error(TouristErrorCode.T001);
        }

        // 优先从缓存获取
        Map<String, Object> cached = bootstrapCacheService.getBootstrapData(scenicId);
        if (cached != null) {
            log.debug("[首屏] 缓存命中，scenicId={}", scenicId);
            // 实时在线人数不缓存，每次注入最新值
            cached.put("onlineCount", touristSessionService.getOnlineCount(scenicId));
            return success(cached);
        }

        ScenicAreaVO scenic = BeanUtil.copyProperties(
                scenicAreaService.getById(scenicId), ScenicAreaVO.class);
        if (scenic == null) {
            return error(TouristErrorCode.T002);
        }

        DigitalHumanConfig digitalHuman = digitalHumanConfigService.getDefaultByScenicId(scenicId);

        String greeting = (digitalHuman != null && digitalHuman.getDefaultGreeting() != null)
                ? digitalHuman.getDefaultGreeting()
                : "欢迎来到" + scenic.getScenicName() + "，我是您的专属 AI 导游，可以为您介绍景点、规划路线、解答疑问。";

        // 从缓存获取热门 FAQ（缓存 miss 时自动从数据库加载并回填）
        List<Faq> hotFaqs = faqCacheService.getOrLoadHotFaqs(scenicId, 5);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenic", scenic);
        result.put("digitalHuman", digitalHuman);
        result.put("conversation", List.of(Map.of(
                "id", "assistant-welcome",
                "role", "assistant",
                "content", greeting
        )));
        result.put("hotFaqs", hotFaqs);

        // 注入实时在线人数
        result.put("onlineCount", touristSessionService.getOnlineCount(scenicId));

        // 回填引导数据缓存
        bootstrapCacheService.cacheBootstrapData(scenicId, result);

        return success(result);
    }
}
