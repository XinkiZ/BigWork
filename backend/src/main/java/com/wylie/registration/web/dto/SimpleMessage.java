package com.wylie.registration.web.dto;

public record SimpleMessage(String code, String message) {

    public static SimpleMessage ok(String message) {
        return new SimpleMessage("OK", message);
    }
}