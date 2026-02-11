package com.dataforge.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    // Regex to check for at least one digit
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*[0-9].*");
    // Regex to check for at least one lowercase letter
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    // Regex to check for at least one uppercase letter
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    // Regex to check for at least one special character
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false; // Or true if you want to allow nulls, but for passwords, false is better.
        }

        // 1. Check length
        if (password.length() < 8) {
            return false;
        }
        // 2. Check for at least one digit
        if (!DIGIT_PATTERN.matcher(password).matches()) {
            return false;
        }
        // 3. Check for at least one uppercase letter
        if (!UPPERCASE_PATTERN.matcher(password).matches()) {
            return false;
        }
        // 4. Check for at least one lowercase letter
        if (!LOWERCASE_PATTERN.matcher(password).matches()) {
            return false;
        }
        // 5. Check for at least one special character
        if (!SPECIAL_CHAR_PATTERN.matcher(password).matches()) {
            return false;
        }

        // If all checks pass, the password is valid
        return true;
    }
}
