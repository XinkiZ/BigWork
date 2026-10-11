package com.wylie.registration.legacy;

import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.common.BusinessException;
import org.springframework.http.HttpStatus;

/** 遗留课程目录系统不可用 */
public class LegacyUnavailableException extends BusinessException {

    public LegacyUnavailableException() {
        super(ErrorCodes.CATALOG_UNAVAILABLE, "课程目录系统暂时不可用，请稍后再试", HttpStatus.SERVICE_UNAVAILABLE);
    }
}