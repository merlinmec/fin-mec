package com.mecfin.tag.infra;

// Projeção de TagRepository.countUsage. tagId volta como texto (CAST na query nativa) para não
// depender da conversão de tipo UUID em projeção de query nativa.
public interface TagUsageProjection {

    String getTagId();

    Long getTotal();
}
