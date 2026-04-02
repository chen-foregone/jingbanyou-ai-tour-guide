package cn.edu.gdou.jingbanyou.tourist.controller;

import cn.edu.gdou.jingbanyou.common.core.domain.R;
import cn.edu.gdou.jingbanyou.manage.entity.ScenicSpot;
import cn.edu.gdou.jingbanyou.manage.service.IScenicSpotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 景点讲解 Controller（游客交互侧核心功能）
 * 
 * 功能：提供景点智能讲解、语音导览、视频讲解
 */
@Slf4j
@RestController
@RequestMapping("/tourist/guide")
public class GuideController {

    @Autowired
    private IScenicSpotService scenicSpotService;

    /**
     * 获取景点详情及讲解词
     */
    @GetMapping("/spot/{id}")
    public R<ScenicSpot> getSpotGuide(@PathVariable Long id) {
        ScenicSpot spot = scenicSpotService.getById(id);
        return R.ok(spot);
    }

    /**
     * 获取景点列表（带讲解资源）
     */
    @GetMapping("/spots")
    public R<List<ScenicSpot>> getSpots(@RequestParam Long scenicId) {
        List<ScenicSpot> spots = scenicSpotService.list();
        return R.ok(spots);
    }

    /**
     * 获取景点语音讲解 URL
     */
    @GetMapping("/spot/{id}/audio")
    public R<String> getAudioGuide(@PathVariable Long id) {
        ScenicSpot spot = scenicSpotService.getById(id);
        return R.ok(spot.getAudioGuide());
    }

    /**
     * 获取景点视频讲解 URL
     */
    @GetMapping("/spot/{id}/video")
    public R<String> getVideoGuide(@PathVariable Long id) {
        ScenicSpot spot = scenicSpotService.getById(id);
        return R.ok(spot.getVideoGuide());
    }
}
