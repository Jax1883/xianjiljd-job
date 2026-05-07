package com.xianjilijd.job.common;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PageResult<T> {
    private long total;
    private long pageNum;
    private long pageSize;
    private List<T> records;
}
