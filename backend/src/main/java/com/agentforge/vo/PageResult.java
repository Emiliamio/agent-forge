package com.agentforge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页响应包装类
 *
 * @param <T> 列表项数据泛型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "全局分页数据包装")
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "当前页码", example = "1")
    private long pageNum;

    @Schema(description = "每页大小", example = "10")
    private long pageSize;

    @Schema(description = "总记录数", example = "100")
    private long total;

    @Schema(description = "总页数", example = "10")
    private long totalPages;

    @Schema(description = "数据列表")
    private List<T> list;

    public static <T> PageResult<T> of(long pageNum, long pageSize, long total, List<T> list) {
        long totalPages = (total + pageSize - 1) / pageSize;
        return PageResult.<T>builder()
                .pageNum(pageNum)
                .pageSize(pageSize)
                .total(total)
                .totalPages(totalPages)
                .list(list != null ? list : Collections.emptyList())
                .build();
    }

    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return of(pageNum, pageSize, 0, Collections.emptyList());
    }
}
