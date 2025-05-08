package cn.edu.gdou.jingbanyou.common.core.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 游客会话信息
 * 存储在 Redis 中，TTL=2小时
 *
 * @author jingbanyou
 */
public class VisitorSessionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 游客唯一标识
     */
    private String visitorId;

    /**
     * 景区ID
     */
    private Long sceneId;

    /**
     * 入口ID
     */
    private String entranceId;

    /**
     * 首次访问时间（毫秒时间戳）
     */
    private Long firstVisitTime;

    /**
     * 最后活跃时间（毫秒时间戳）
     */
    private Long lastActiveTime;

    public VisitorSessionDTO() {
    }

    public VisitorSessionDTO(String visitorId, Long sceneId, String entranceId) {
        this.visitorId = visitorId;
        this.sceneId = sceneId;
        this.entranceId = entranceId;
        long now = System.currentTimeMillis();
        this.firstVisitTime = now;
        this.lastActiveTime = now;
    }

    public String getVisitorId() {
        return visitorId;
    }

    public void setVisitorId(String visitorId) {
        this.visitorId = visitorId;
    }

    public Long getSceneId() {
        return sceneId;
    }

    public void setSceneId(Long sceneId) {
        this.sceneId = sceneId;
    }

    public String getEntranceId() {
        return entranceId;
    }

    public void setEntranceId(String entranceId) {
        this.entranceId = entranceId;
    }

    public Long getFirstVisitTime() {
        return firstVisitTime;
    }

    public void setFirstVisitTime(Long firstVisitTime) {
        this.firstVisitTime = firstVisitTime;
    }

    public Long getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(Long lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }
}
