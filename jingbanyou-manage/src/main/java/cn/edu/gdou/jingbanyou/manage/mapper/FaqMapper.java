package cn.edu.gdou.jingbanyou.manage.mapper;

import cn.edu.gdou.jingbanyou.manage.entity.Faq;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 常见问答 Mapper 接口
 *
 * @author jingbanyou
 */
@Mapper
public interface FaqMapper extends BaseMapper<Faq>
{
    /**
     * 咨询次数 +1
     *
     * @param id FAQ ID
     * @return 影响行数
     */
    @Update("UPDATE manage_faq SET click_count = click_count + 1 WHERE id = #{id}")
    int incrementClickCount(@Param("id") Long id);

    /**
     * 点赞数 +1
     *
     * @param id FAQ ID
     * @return 影响行数
     */
    @Update("UPDATE manage_faq SET helpful_count = helpful_count + 1 WHERE id = #{id}")
    int incrementHelpfulCount(@Param("id") Long id);

    /**
     * 踩数 +1
     *
     * @param id FAQ ID
     * @return 影响行数
     */
    @Update("UPDATE manage_faq SET unhelpful_count = unhelpful_count + 1 WHERE id = #{id}")
    int incrementUnhelpfulCount(@Param("id") Long id);
}
