package com.xianjilijd.job.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class QueryOrderBackResp {
    private Long id;
    private String responseCode;
    private Integer isSync;
    private Integer syncTimes;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastModifyDate;

    private String orderType;
}
