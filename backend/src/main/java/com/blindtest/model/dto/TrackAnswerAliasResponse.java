package com.blindtest.model.dto;

import com.blindtest.model.entity.TrackAnswerAliasEntity;
import com.blindtest.model.entity.TrackAnswerType;
import java.util.UUID;

/** Safe administrator alias representation that excludes internal normalization. */
public record TrackAnswerAliasResponse(
        UUID id,
        TrackAnswerType type,
        String value) {

    /** Maps the persistent alias without exposing its normalized key. */
    public static TrackAnswerAliasResponse from(TrackAnswerAliasEntity alias) {
        return new TrackAnswerAliasResponse(
                alias.getId(), alias.getAnswerType(), alias.getValue());
    }
}
