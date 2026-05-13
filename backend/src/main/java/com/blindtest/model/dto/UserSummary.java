package com.blindtest.model.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Lightweight user projection returned inside login/refresh responses. */
@Getter
@AllArgsConstructor
public class UserSummary {

    private UUID id;
    private String username;
    private String email;
    private String role;
    private String avatarUrl;
}
