package cn.edu.gdou.jingbanyou.manage.mapper;

import cn.edu.gdou.jingbanyou.manage.entity.VisitorConversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 游客会话记录 Mapper
 *
 * @author jingbanyou
 */
@Mapper
public interface VisitorConversationMapper extends BaseMapper<VisitorConversation> {

    /**
     * 分页查询会话列表
     *
     * @param visitorId 游客ID
     * @param scenicId 景区ID
     * @param offset 偏移量
     * @param limit 每页条数
     * @return 会话列表
     */
    List<VisitorConversation> selectConversationList(
            @Param("visitorId") String visitorId,
            @Param("scenicId") Long scenicId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * 统计会话总数
     *
     * @param visitorId 游客ID
     * @param scenicId 景区ID
     * @return 总数
     */
    long countConversationList(
            @Param("visitorId") String visitorId,
            @Param("scenicId") Long scenicId
    );
}
