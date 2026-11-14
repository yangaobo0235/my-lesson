package com.yangaobo.servicedesk.user.mapper;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.yangaobo.servicedesk.user.model.dto.account.AuditEventRowDTO;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 不可变管理审计的业务边界。
 *
 * @author yangaobo
 */
public interface AdminEventMapper {
    /** 与管理变更同事务追加不可变审计，不含口令。 */
    @Insert(
            """
            INSERT INTO sys_admin_event(id, actor_type, actor_user_id, target_user_id,
                target_organization_id, event_type, details)
            VALUES(#{id}, #{actorType}, #{actor}, #{user}, #{organization}, #{type}, #{details})
            """)
    int append(
            @Param("id") String id,
            @Param("actorType") String actorType,
            @Param("actor") String actor,
            @Param("user") String user,
            @Param("organization") String organization,
            @Param("type") String type,
            @Param("details") String details);

    /** 按目标稳定分页读取不可变审计，查询前由服务验证管理员。 */
    @Select(
            """
<script>SELECT id, actor_type, actor_user_id, target_user_id, target_organization_id, event_type, details, created_at FROM sys_admin_event
<where><if test="user != null">target_user_id = #{user}</if>
<if test="organization != null">AND target_organization_id = #{organization}</if></where>
ORDER BY created_at DESC, id LIMIT #{limit} OFFSET #{offset}</script>
""")
    @Results(
            @Result(
                    column = "details",
                    property = "details",
                    typeHandler = JacksonTypeHandler.class))
    List<AuditEventRowDTO> list(
            @Param("user") String user,
            @Param("organization") String organization,
            @Param("limit") int limit,
            @Param("offset") long offset);

    /** 复用相同目标条件统计总数。 */
    @Select(
            """
<script>SELECT COUNT(*) FROM sys_admin_event
<where><if test="user != null">target_user_id = #{user}</if>
<if test="organization != null">AND target_organization_id = #{organization}</if></where></script>
""")
    long count(@Param("user") String user, @Param("organization") String organization);
}
