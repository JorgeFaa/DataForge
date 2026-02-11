package com.dataforge.dto;

import com.dataforge.validation.ValidPassword;

public record MasterPasswordRequest(
    @ValidPassword // Our custom validation annotation
    String password
) {}
