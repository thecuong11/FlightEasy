package com.fighteasy.dto;

public record ResetPasswordRequest(String token, String newPassword) {
}
