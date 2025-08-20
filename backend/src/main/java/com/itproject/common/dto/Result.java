package com.itproject.common.dto;

import lombok.Data;

/**
 * Unified response wrapper for all API endpoints
 * Provides consistent response format for frontend integration
 */
@Data
public class Result<T> {
    private boolean success;
    private String message;
    private T data;
    private String errorCode;
    private long timestamp;

    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setData(data);
        result.setMessage("Success");
        return result;
    }

    public static <T> Result<T> success(T data, String message) {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setData(data);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> error(String message, String errorCode) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setMessage(message);
        result.setErrorCode(errorCode);
        return result;
    }
}
