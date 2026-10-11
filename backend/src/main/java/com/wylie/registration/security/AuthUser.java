package com.wylie.registration.security;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.domain.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 当前登录用户信息（JWT 载荷） */
public record AuthUser(Long id, UserRole role, String name, String username) {

    /** 获取当前登录用户，未登录抛 401 */
    public static AuthUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "登录状态无效，请重新登录", HttpStatus.UNAUTHORIZED);
        }
        return user;
    }
}