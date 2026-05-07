package com.xianjilijd.job.controller;

import com.xianjilijd.job.common.ApiResponse;
import com.xianjilijd.job.common.PageResult;
import com.xianjilijd.job.dto.ActivateOrderBackReq;
import com.xianjilijd.job.dto.QueryOrderBackReq;
import com.xianjilijd.job.dto.QueryOrderBackResp;
import com.xianjilijd.job.service.OrderBackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/tools/xianjilijd")
@RequiredArgsConstructor
public class OrderBackController {

    private final OrderBackService orderBackService;

    @PostMapping("/queryOrderBack")
    public ApiResponse<PageResult<QueryOrderBackResp>> queryOrderBack(@Valid @RequestBody QueryOrderBackReq req) {
        PageResult<QueryOrderBackResp> result = orderBackService.query(req);
        if (result.getTotal() == 0) {
            return ApiResponse.success("无数据，请人工核实", result);
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/activateOrderBack")
    public ApiResponse<Map<String, Integer>> activateOrderBack(@Valid @RequestBody ActivateOrderBackReq req) {
        int updated = orderBackService.activate(req);
        Map<String, Integer> data = new HashMap<>();
        data.put("updated", updated);
        return ApiResponse.success(data);
    }
}
