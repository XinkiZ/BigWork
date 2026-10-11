package com.wylie.registration.common;

/** 统一错误码 */
public final class ErrorCodes {

    public static final String UNAUTHORIZED       = "UNAUTHORIZED";
    public static final String FORBIDDEN          = "FORBIDDEN";
    public static final String NOT_FOUND          = "NOT_FOUND";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String VALIDATION         = "VALIDATION";
    public static final String STATE_ERROR        = "STATE_ERROR";
    public static final String REGISTRATION_CLOSED = "REGISTRATION_CLOSED";
    public static final String PREREQUISITE_NOT_MET = "PREREQUISITE_NOT_MET";
    public static final String COURSE_FULL        = "COURSE_FULL";
    public static final String TIME_CONFLICT      = "TIME_CONFLICT";
    public static final String DUPLICATE_COURSE   = "DUPLICATE_COURSE";
    public static final String CATALOG_UNAVAILABLE = "CATALOG_UNAVAILABLE";
    public static final String BILLING_UNAVAILABLE = "BILLING_UNAVAILABLE";
    public static final String INTERNAL           = "INTERNAL";

    private ErrorCodes() {
    }
}