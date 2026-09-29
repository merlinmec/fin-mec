package com.mecfin.tag.application;

import com.mecfin.tag.domain.Tag;

// usageCount derivado na leitura (quantos lançamentos usam a tag).
public record TagView(Tag tag, long usageCount) {
}
