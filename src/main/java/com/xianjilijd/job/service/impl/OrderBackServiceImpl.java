package com.xianjilijd.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xianjilijd.job.common.BizException;
import com.xianjilijd.job.common.OrderTypeEnum;
import com.xianjilijd.job.dto.ActivateOrderBackReq;
import com.xianjilijd.job.dto.QueryOrderBackReq;
import com.xianjilijd.job.dto.QueryOrderBackResp;
import com.xianjilijd.job.entity.OutOrderBack;
import com.xianjilijd.job.entity.ReceiptNoteBack;
import com.xianjilijd.job.mapper.OutOrderBackMapper;
import com.xianjilijd.job.mapper.ReceiptNoteBackMapper;
import com.xianjilijd.job.service.OrderBackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderBackServiceImpl implements OrderBackService {

    private static final int MAX_QUERY_CODES = 500;
    private static final int MAX_ACTIVATE_IDS = 1000;

    private final OutOrderBackMapper outOrderBackMapper;
    private final ReceiptNoteBackMapper receiptNoteBackMapper;

    @Override
    public List<QueryOrderBackResp> query(QueryOrderBackReq req) {
        OrderTypeEnum type = parseOrderType(req.getOrderType());
        List<String> codes = sanitizeCodes(req.getCode());

        if (type == OrderTypeEnum.RECEIPT) {
            LambdaQueryWrapper<ReceiptNoteBack> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(ReceiptNoteBack::getReceiptCode, codes)
                   .orderByDesc(ReceiptNoteBack::getId);
            return receiptNoteBackMapper.selectList(wrapper).stream()
                    .map(r -> QueryOrderBackResp.builder()
                            .id(r.getId())
                            .responseCode(r.getReceiptCode())
                            .isSync(r.getIsSync())
                            .syncTimes(r.getSyncTimes())
                            .lastModifyDate(r.getLastModifyDate())
                            .orderType(req.getOrderType())
                            .build())
                    .collect(Collectors.toList());
        } else {
            LambdaQueryWrapper<OutOrderBack> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(OutOrderBack::getOutOrderCode, codes)
                   .orderByDesc(OutOrderBack::getId);
            return outOrderBackMapper.selectList(wrapper).stream()
                    .map(r -> QueryOrderBackResp.builder()
                            .id(r.getId())
                            .responseCode(r.getOutOrderCode())
                            .isSync(r.getIsSync())
                            .syncTimes(r.getSyncTimes())
                            .lastModifyDate(r.getLastModifyDate())
                            .orderType(req.getOrderType())
                            .build())
                    .collect(Collectors.toList());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int activate(ActivateOrderBackReq req) {
        OrderTypeEnum type = parseOrderType(req.getOrderType());
        List<Long> ids = sanitizeIds(req.getRequestIds());

        if (type == OrderTypeEnum.RECEIPT) {
            LambdaUpdateWrapper<ReceiptNoteBack> u = new LambdaUpdateWrapper<>();
            u.set(ReceiptNoteBack::getIsSync, 0)
             .set(ReceiptNoteBack::getSyncTimes, 0)
             .in(ReceiptNoteBack::getId, ids);
            return receiptNoteBackMapper.update(null, u);
        } else {
            LambdaUpdateWrapper<OutOrderBack> u = new LambdaUpdateWrapper<>();
            u.set(OutOrderBack::getIsSync, 0)
             .set(OutOrderBack::getSyncTimes, 0)
             .in(OutOrderBack::getId, ids);
            return outOrderBackMapper.update(null, u);
        }
    }

    private OrderTypeEnum parseOrderType(String label) {
        if (label == null) {
            throw new BizException("orderType 不能为空");
        }
        OrderTypeEnum type = OrderTypeEnum.fromLabel(label.trim());
        if (type == null) {
            throw new BizException("orderType 必须是 入库单回传 或 出库单回传");
        }
        return type;
    }

    private List<String> sanitizeCodes(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BizException("code 不能为空");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String c : raw) {
            if (c == null) continue;
            String s = stripQuotes(c.trim());
            if (!s.isEmpty()) seen.add(s);
        }
        if (seen.isEmpty()) {
            throw new BizException("code 不能为空");
        }
        if (seen.size() > MAX_QUERY_CODES) {
            throw new BizException("code 数量超过上限 " + MAX_QUERY_CODES);
        }
        return new ArrayList<>(seen);
    }

    private List<Long> sanitizeIds(List<Long> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BizException("requestIds 不能为空");
        }
        Set<Long> seen = new LinkedHashSet<>();
        for (Long id : raw) {
            if (id != null) seen.add(id);
        }
        if (seen.isEmpty()) {
            throw new BizException("requestIds 不能为空");
        }
        if (seen.size() > MAX_ACTIVATE_IDS) {
            throw new BizException("requestIds 数量超过上限 " + MAX_ACTIVATE_IDS);
        }
        return new ArrayList<>(seen);
    }

    private String stripQuotes(String s) {
        if (s.length() < 2) return s;
        char first = s.charAt(0);
        char last = s.charAt(s.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')
                || (first == '“' && last == '”')) {
            return s.substring(1, s.length() - 1).trim();
        }
        return s;
    }
}
