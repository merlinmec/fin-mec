package com.mecfin.tag.api;

import com.mecfin.tag.application.TagView;
import java.util.UUID;

public record TagResponse(UUID id, String name, String color, long usageCount) {

    public static TagResponse from(TagView view) {
        return new TagResponse(view.tag().getId(), view.tag().getName(), view.tag().getColor(), view.usageCount());
    }
}
