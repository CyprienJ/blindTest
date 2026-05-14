package com.blindtest.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** Request body for partial metadata update on a track. Null fields are left unchanged. */
@Getter
@NoArgsConstructor
public class UpdateTrackRequest {

    private String title;
    private String artist;
    private String composer;
    private String work;
    private String category;
    private Short decade;
    private String imageUrl;
}
