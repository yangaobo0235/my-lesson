package com.yangaobo.ai.security;

public final class UserContext {

    private static final ThreadLocal<AuthenticatedUser> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(AuthenticatedUser user) {
        CURRENT_USER.set(user);
    }

    public static AuthenticatedUser get() {
        return CURRENT_USER.get();
    }

    public static AuthenticatedUser requireUser() {
        AuthenticatedUser user = CURRENT_USER.get();
        if (user == null) {
            throw new IllegalStateException("No authenticated user in current request");
        }
        return user;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
