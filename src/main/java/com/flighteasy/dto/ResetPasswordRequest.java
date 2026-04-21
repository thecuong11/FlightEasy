package com.flighteasy.dto;

public record ResetPasswordRequest(String token, String newPassword) {
}
