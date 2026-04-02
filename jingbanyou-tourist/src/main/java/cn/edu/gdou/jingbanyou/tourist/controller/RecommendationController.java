package cn.edu.gdou.jingbanyou.tourist.controller;

import cn.edu.gdou.jingbanyou.common.core.domain.R;
import cn.edu.gdou.jingbanyou.manage.entity.TourRoute;
import cn.edu.gdou.jingbanyou.manage.service.ITourRouteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 个性化路线推荐 Controller（游客交互侧核心功能）
 * 
 * 功能：根据游客兴趣推荐不同的游览路线和讲解重点
 */
@Slf4j
@RestController
@RequestMapping("/tourist/recommend")
public class RecommendationController {

    @Autowired
    private ITourRouteService tourRouteService;

    /**
     * 获取所有路线类型
     */
    @GetMapping("/route-types")
    public R<List<String>> getRouteTypes() {
        // TODO: 返回路线类型列表
        return R.ok();
    }

    /**
     * 根据兴趣推荐路线
     */
    @GetMapping("/routes")
    public R<List<TourRoute>> recommendRoutes(
            @RequestParam Long scenicId,
            @RequestParam(required = false) String routeType,
            @RequestParam(required = false) Integer difficulty) {
        
        List<TourRoute> routes = tourRouteService.list();
        return R.ok(routes);
    }

    /**
     * 获取路线详情（含景点顺序）
     */
    @GetMapping("/route/{id}")
    public R<Map<String, Object>> getRouteDetail(@PathVariable Long id) {
        // TODO: 返回路线详情及景点列表
        return R.ok();
    }

    /**
     * AI 智能推荐（基于用户画像）
     */
    @PostMapping("/ai-recommend")
    public R<List<TourRoute>> aiRecommend(
            @RequestParam Long scenicId,
            @RequestBody Map<String, Object> userProfile) {
        
        log.info("AI 智能推荐：scenicId={}, userProfile={}", scenicId, userProfile);
        // TODO: 调用 AI 分析用户兴趣，推荐路线
        return R.ok();
    }
}
