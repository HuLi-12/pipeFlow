package com.example.pipeflowcheck.enums;

public enum ErrorCode {
    NODE_NOT_FOUND,
    CHANNEL_NOT_ALLOWED,
    INVALID_END,
    DEAD_END,
    CYCLE_FOUND,
    PATH_TOO_DEEP,
    TERMINAL_HAS_DOWNSTREAM,
    EXCEL_FORMAT_ERROR,
    MISSING_REQUIRED_FIELD,
    MULTI_PATH_ERROR
}
