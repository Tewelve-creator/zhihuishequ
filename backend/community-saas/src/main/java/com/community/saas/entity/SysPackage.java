package com.community.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("sys_package")
public class SysPackage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    private String features;
    private BigDecimal priceMonth;
    private BigDecimal priceQuarter;
    private BigDecimal priceYear;
    private Integer aiQuota;
    private Integer status;
    private String remark;
}
