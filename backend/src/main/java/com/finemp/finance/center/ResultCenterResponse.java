package com.finemp.finance.center;

import java.util.UUID;

public record ResultCenterResponse(UUID id,UUID companyId,UUID parentId,String code,String name,boolean active,boolean hasChildren,long version) {
    static ResultCenterResponse from(ResultCenter row,boolean hasChildren) {
        return new ResultCenterResponse(row.getId(),row.getCompany().getId(),row.getParentId(),row.getCode(),row.getName(),row.isActive(),hasChildren,row.getVersion());
    }
}
