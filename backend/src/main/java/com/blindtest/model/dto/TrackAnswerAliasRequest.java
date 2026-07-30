package com.blindtest.model.dto;

import com.blindtest.model.entity.TrackAnswerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Administrator request for creating or replacing a track answer alias. */
@Getter
@NoArgsConstructor
public class TrackAnswerAliasRequest {

    @NotNull
    private TrackAnswerType type;

    @NotBlank
    @Size(max = 255)
    private String value;
}
