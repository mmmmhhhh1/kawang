package org.example.kah.common;

public final class ErrorCode {

    public static final int BAD_REQUEST = 4000;
    public static final int UNAUTHORIZED = 4010;
    public static final int FORBIDDEN = 4030;
    public static final int NOT_FOUND = 4040;
    public static final int TOO_MANY_REQUESTS = 4290;
    public static final int INTERNAL_ERROR = 5000;

    private ErrorCode() {
    }
}
