package com.xianjilijd.job.common;

import java.util.Arrays;

public enum OrderTypeEnum {
    RECEIPT("入库单回传"),
    OUT_ORDER("出库单回传");

    private final String label;

    OrderTypeEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static OrderTypeEnum fromLabel(String label) {
        if (label == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.label.equals(label))
                .findFirst()
                .orElse(null);
    }
}
