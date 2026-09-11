package com.bili.demo.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用分页返回结构，前端拿到的格式：
 * { "items": [...], "page": 1, "size": 24, "total": 80, "totalPages": 4, "hasMore": true }
 */
public class PageResult<T> {

    public List<T> items = new ArrayList<>();
    public int page;
    public int size;
    public int total;
    public int totalPages;
    public boolean hasMore;

    public static <T> PageResult<T> of(List<T> all, int page, int size) {
        PageResult<T> result = new PageResult<>();
        if (size <= 0) {
            size = 24;
        }
        if (page <= 0) {
            page = 1;
        }
        int total = all.size();
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);

        result.items = new ArrayList<>(all.subList(from, to));
        result.page = page;
        result.size = size;
        result.total = total;
        result.totalPages = (int) Math.ceil(total / (double) size);
        result.hasMore = to < total;
        return result;
    }
}
