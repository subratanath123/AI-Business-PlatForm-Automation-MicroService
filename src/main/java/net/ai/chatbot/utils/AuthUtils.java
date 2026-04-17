package net.ai.chatbot.utils;

import net.ai.chatbot.dto.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.List;

public class AuthUtils {

    /**
     * Get userId from JWT sub (Clerk user ID)
     */
    public static String getUserId() {
        var context = SecurityContextHolder.getContext();
        if (context == null) return null;
        var authentication = context.getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) return null;
        return (String) token.getTokenAttributes().get("sub");
    }

    public static String getEmail() {
        var context = SecurityContextHolder.getContext();
        if (context == null) {
            return null;
        }

        var authentication = context.getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken authenticationToken)) {
            return null;
        }

        String sub = (String) authenticationToken.getTokenAttributes().get("sub");

        return sub.contains("@")
                ? sub
                : (String) authenticationToken.getTokenAttributes().get("email");
    }

    /**
     * Get user email from JWT (alias for getEmail for consistency)
     */
    public static String getUserEmail() {
        return getEmail();
    }

    public static boolean isAdmin() {
        return "shuvra.dev9@gmail.com".equals(getEmail());
    }

    public static User getUser() {
        JwtAuthenticationToken authenticationToken = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();

        String sub = (String) authenticationToken.getTokenAttributes().get("sub");

        String email = sub.contains("@")
                ? sub
                : (String) authenticationToken.getTokenAttributes().get("email");

        String name = (String) authenticationToken.getTokenAttributes().get("name");
        String picture = (String) authenticationToken.getTokenAttributes().get("picture");

        return User.builder()
                .userName(name)
                .picture(picture)
                .email(email)
                .build();


    }

    /**
     * Clerk session claim {@code fva}: minutes since last first-factor verification,
     * then minutes since last second-factor verification ({@code -1} if no second factor or never verified).
     */
    public static List<Integer> getFactorVerificationAgeMinutes() {
        JwtAuthenticationToken authenticationToken = getJwtTokenOrNull();
        if (authenticationToken == null) {
            return List.of();
        }
        Object fva = authenticationToken.getTokenAttributes().get("fva");
        return coerceIntList(fva);
    }

    /**
     * Optional custom session claim (see {@link net.ai.chatbot.dto.MfaStatusResponse}).
     */
    public static Boolean getOptionalBooleanClaim(String claimName) {
        JwtAuthenticationToken authenticationToken = getJwtTokenOrNull();
        if (authenticationToken == null) {
            return null;
        }
        Object v = authenticationToken.getTokenAttributes().get(claimName);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            if ("true".equalsIgnoreCase(s)) {
                return true;
            }
            if ("false".equalsIgnoreCase(s)) {
                return false;
            }
        }
        return null;
    }

    private static JwtAuthenticationToken getJwtTokenOrNull() {
        var context = SecurityContextHolder.getContext();
        if (context == null || context.getAuthentication() == null) {
            return null;
        }
        if (!(context.getAuthentication() instanceof JwtAuthenticationToken token)) {
            return null;
        }
        return token;
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> coerceIntList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<Integer> out = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o instanceof Number n) {
                    out.add(n.intValue());
                } else {
                    out.add(-1);
                }
            }
            return List.copyOf(out);
        }
        if (raw instanceof int[] arr) {
            List<Integer> out = new ArrayList<>(arr.length);
            for (int v : arr) {
                out.add(v);
            }
            return out;
        }
        return List.of();
    }
}
