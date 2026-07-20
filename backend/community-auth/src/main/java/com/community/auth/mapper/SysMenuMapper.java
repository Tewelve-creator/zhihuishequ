package com.community.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.community.auth.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    @Select("""
            SELECT DISTINCT m.* FROM sys_menu m
            INNER JOIN sys_role_menu rm ON m.id = rm.menu_id
            INNER JOIN sys_user_role ur ON ur.role_id = rm.role_id
            WHERE ur.user_id = #{userId} AND m.status = 1
            ORDER BY m.sort_no ASC, m.id ASC
            """)
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT DISTINCT m.perms FROM sys_menu m
            INNER JOIN sys_role_menu rm ON m.id = rm.menu_id
            INNER JOIN sys_user_role ur ON ur.role_id = rm.role_id
            WHERE ur.user_id = #{userId} AND m.status = 1 AND m.perms IS NOT NULL AND m.perms <> ''
            """)
    List<String> selectPermsByUserId(@Param("userId") Long userId);
}
