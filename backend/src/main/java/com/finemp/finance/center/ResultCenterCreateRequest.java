package com.finemp.finance.center;

import jakarta.validation.constraints.*;

public record ResultCenterCreateRequest(@NotBlank @Size(max=100) String code,@NotBlank @Size(max=80) String name,@NotNull ResultCenterType type) {
    public ResultCenterCreateRequest(String code,String name) {this(code,name,ResultCenterType.ANALYTICAL);}
}
