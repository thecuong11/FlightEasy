package com.fighteasy.exception.handler;

import com.fighteasy.exception.custom.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<?> handleEmailExists(EmailAlreadyExistsException ex ) {
        return ResponseEntity.status(409)
                .body(Map.of("code", "EMAIL_ALREADY_EXISTS","message", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<?> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(401)
                .body(Map.of("code", "INVALID_CREDENTIALS", "message", ex.getMessage()));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<?> handleLocked(AccountLockedException ex){
        return ResponseEntity.status(423)
                .body(Map.of("code", "ACCOUNT_LOCKED", "message", ex.getMessage()));
    }

    @ExceptionHandler({InvalidTokenException.class, TokenExpiredException.class})
    public ResponseEntity<?> handleInvalidToken(RuntimeException ex){
        return ResponseEntity.status(401)
                .body(Map.of("code", "INVALID_TOKEN", "message", ex.getMessage()));
    }

    @ExceptionHandler(TokenReuseException.class)
    public ResponseEntity<?> handleTokenReuse(TokenReuseException ex){
        return ResponseEntity.status(401)
                .body(Map.of("code", "REFRESH_TOKEN_REUSE", "message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex){
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));
        return ResponseEntity.status(400)
                .body(Map.of("code", "VALIDATION", "errors", errors));
    }

}
