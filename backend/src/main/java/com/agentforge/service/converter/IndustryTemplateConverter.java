package com.agentforge.service.converter;

import com.agentforge.service.market.api.TemplateMarketService;
import com.agentforge.vo.IndustryTemplateVo;

public final class IndustryTemplateConverter {

    private IndustryTemplateConverter() {}

    public static IndustryTemplateVo toVo(TemplateMarketService.IndustryTemplate t) {
        if (t == null) {
            return null;
        }
        return IndustryTemplateVo.builder()
                .templateId(t.getTemplateId())
                .name(t.getName())
                .category(t.getCategory())
                .icon(t.getIcon())
                .description(t.getDescription())
                .valueProposition(t.getValueProposition())
                .estimatedPrice(t.getEstimatedPrice())
                .dagJson(t.getDagJson())
                .defaultPrompt(t.getDefaultPrompt())
                .build();
    }
}
