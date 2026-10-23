package com.agentforge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "租户充值请求")
public class RechargeRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private BigDecimal amount;
}
