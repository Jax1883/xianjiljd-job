package com.xianjilijd.job.service;

import com.xianjilijd.job.dto.ActivateOrderBackReq;
import com.xianjilijd.job.dto.QueryOrderBackReq;
import com.xianjilijd.job.dto.QueryOrderBackResp;

import java.util.List;

public interface OrderBackService {

    List<QueryOrderBackResp> query(QueryOrderBackReq req);

    int activate(ActivateOrderBackReq req);
}
