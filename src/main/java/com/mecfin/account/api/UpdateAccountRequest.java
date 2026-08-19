package com.mecfin.account.api;

import com.mecfin.account.domain.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull AccountType type,
        boolean archived) {
}
