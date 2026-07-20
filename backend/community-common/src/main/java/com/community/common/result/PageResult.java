package com.community.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页返回体（不依赖 MyBatis 类型，避免网关等模块类加载问题）
 */
@Data
public class PageResult<T> implements Serializable {
    private long total;
    private long page;
    private long size;
    private List<T> records;

    public static <T> PageResult<T> of(long page, long size, long total, List<T> records) {
        PageResult<T> p = new PageResult<>();
        p.page = page;
        p.size = size;
        p.total = total;
        p.records = records == null ? Collections.emptyList() : records;
        return p;
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return of(page, size, 0, Collections.emptyList());
    }
}
