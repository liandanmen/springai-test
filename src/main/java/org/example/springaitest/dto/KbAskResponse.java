package org.example.springaitest.dto;

import java.util.List;

public record KbAskResponse(
        boolean found,                 // 是否检索到依据
        String answer,
        List<KbSearchHit> sources
) {}
