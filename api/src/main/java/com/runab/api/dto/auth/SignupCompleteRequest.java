package com.runab.api.dto.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SignupCompleteRequest(
        @Valid @NotNull SignupRequest account,
        @Valid @NotNull SignupBusinessRequest business) {}
