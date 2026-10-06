package com.yuan.demo.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.LinkedHashMap;
import java.util.Map;

/** 当前登录会话的统一读写工具。 */
public final class SessionAuth {
    public static final String USER = "LOGIN_USER";
    public static final String ROLE = "LOGIN_ROLE";
    public static final String SUBJECT_ID = "LOGIN_SUBJECT_ID";

    private SessionAuth() {
    }

    public static void login(HttpServletRequest request, String role, String subjectId,
                             Map<String, Object> loginInfo) {
        HttpSession old = request.getSession(false);
        if (old != null) old.invalidate();
        HttpSession session = request.getSession(true);
        session.setAttribute(ROLE, role);
        session.setAttribute(SUBJECT_ID, subjectId);
        session.setAttribute(USER, new LinkedHashMap<>(loginInfo));
    }

    public static String role(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (String) session.getAttribute(ROLE);
    }

    public static String subjectId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(SUBJECT_ID);
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> user(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(USER);
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
    }

    public static boolean hasRole(HttpServletRequest request, String... roles) {
        String current = role(request);
        if (current == null) return false;
        for (String role : roles) if (current.equals(role)) return true;
        return false;
    }
}
