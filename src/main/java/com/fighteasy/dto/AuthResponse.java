package com.fighteasy.dto;

public record AuthResponse(String accessToken, String tokenType, UserInfo user) {
    public record UserInfo(Long id, String email, String fullName, String role) {}
}
