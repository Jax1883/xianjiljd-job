package com.xianjilijd.job.service;

import com.xianjilijd.job.common.PageResult;
import com.xianjilijd.job.dto.ActivateOrderBackReq;
import com.xianjilijd.job.dto.QueryOrderBackReq;
import com.xianjilijd.job.dto.QueryOrderBackResp;

public interface OrderBackService {

    PageResult<QueryOrderBackResp> query(QueryOrderBackReq req);

    int activate(ActivateOrderBackReq req);
}
