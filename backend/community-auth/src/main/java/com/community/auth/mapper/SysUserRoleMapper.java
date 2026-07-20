package com.community.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.community.auth.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @Select("""
            SELECT r.role_code FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.status = 1
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
