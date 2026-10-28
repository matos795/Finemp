package com.finemp.finance.center;

public enum ResultCenterType {
    GROUP, ANALYTICAL;

    @com.fasterxml.jackson.annotation.JsonCreator
    public static ResultCenterType parse(String value) {
        return value==null?null:ResultCenterType.valueOf(value);
    }
}
