package com.community.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_package_feature")
public class SysPackageFeature {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long packageId;
    private String featureCode;
    private String featureName;
}
