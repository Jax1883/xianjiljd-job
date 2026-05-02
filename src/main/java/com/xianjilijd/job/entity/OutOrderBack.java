package com.xianjilijd.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("out_order_back")
public class OutOrderBack {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("out_order_code")
    private String outOrderCode;

    @TableField("is_sync")
    private Integer isSync;

    @TableField("sync_times")
    private Integer syncTimes;

    @TableField("last_modify_date")
    private LocalDateTime lastModifyDate;
}
