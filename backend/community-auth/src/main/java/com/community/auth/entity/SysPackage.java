package com.community.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_package")
public class SysPackage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    private BigDecimal priceMonth;
    private BigDecimal priceQuarter;
    private BigDecimal priceYear;
    private Integer aiQuota;
    private Integer status;
    private Integer sortNo;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private List<String> featureCodes;
    @TableField(exist = false)
    private List<SysPackageFeature> featureList;
}
