package cn.edu.gdou.jingbanyou.tourist.graph.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 游客画像 POJO
 * 存储在 OverAllState 中（key: visitorProfile），会话结束后写入 Redis（TTL 24h）
 */
@Data
public class VisitorProfile {

    /** 游客唯一标识，由前端传入 */
    private String visitorId;

    /** 兴趣标签，最多保留 10 个，按会话累积合并去重 */
    private List<String> interestTags = new ArrayList<>();

    /** 出行类型：solo / couple / family / group */
    private String groupType;

    /** 已问过的景点，最多保留 20 个 */
    private List<String> visitedSpots = new ArrayList<>();

    /** 推断的偏好路线类型（如"历史"、"自然"、"美食"） */
    private String preferRouteType;

    /** 当前会话轮次 */
    private int turnCount = 0;
}
