package com.mesutpiskin.keycloak.auth.messaging;

import static org.keycloak.models.utils.KeycloakModelUtils.getRoleFromString;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;

import jakarta.ws.rs.core.MultivaluedMap;

public class ConditionalMessagingAuthenticatorForm extends MessagingAuthenticatorForm {

    public static final String SKIP = "skip";
    public static final String FORCE = "force";
    public static final String OTP_CONTROL_USER_ATTRIBUTE = "otpControlAttribute";
    public static final String SKIP_OTP_ROLE = "skipOtpRole";
    public static final String FORCE_OTP_ROLE = "forceOtpRole";
    public static final String SKIP_OTP_FOR_HTTP_HEADER = "noOtpRequiredForHeaderPattern";
    public static final String FORCE_OTP_FOR_HTTP_HEADER = "forceOtpForHeaderPattern";
    public static final String DEFAULT_OTP_OUTCOME = "defaultOtpOutcome";

    private static final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    enum OtpDecision { SKIP_OTP, SHOW_OTP, ABSTAIN }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        AuthenticatorConfigModel model = context.getAuthenticatorConfig();
        Map<String, String> config = model != null ? model.getConfig() : Collections.emptyMap();

        if (tryConclude(voteForUserAttr(context.getUser(), config), context)) return;
        if (tryConclude(voteForUserRole(context.getRealm(), context.getUser(), config), context)) return;
        if (tryConclude(voteForHttpHeader(context.getHttpRequest().getHttpHeaders().getRequestHeaders(), config), context)) return;
        if (tryConclude(voteForDefault(config), context)) return;
        super.authenticate(context);
    }

    private boolean tryConclude(OtpDecision d, AuthenticationFlowContext context) {
        switch (d) {
            case SHOW_OTP: super.authenticate(context); return true;
            case SKIP_OTP: context.success(); return true;
            default: return false;
        }
    }

    private OtpDecision voteForUserAttr(UserModel user, Map<String, String> cfg) {
        String attr = cfg.get(OTP_CONTROL_USER_ATTRIBUTE);
        if (attr == null) return OtpDecision.ABSTAIN;
        Optional<String> v = user.getAttributeStream(attr).findFirst();
        if (v.isEmpty()) return OtpDecision.ABSTAIN;
        return switch (v.get().trim()) {
            case SKIP -> OtpDecision.SKIP_OTP;
            case FORCE -> OtpDecision.SHOW_OTP;
            default -> OtpDecision.ABSTAIN;
        };
    }

    private OtpDecision voteForUserRole(RealmModel realm, UserModel user, Map<String, String> cfg) {
        if (!cfg.containsKey(SKIP_OTP_ROLE) && !cfg.containsKey(FORCE_OTP_ROLE)) return OtpDecision.ABSTAIN;
        if (userHasRole(realm, user, cfg.get(SKIP_OTP_ROLE))) return OtpDecision.SKIP_OTP;
        if (userHasRole(realm, user, cfg.get(FORCE_OTP_ROLE))) return OtpDecision.SHOW_OTP;
        return OtpDecision.ABSTAIN;
    }

    private boolean userHasRole(RealmModel realm, UserModel user, String roleName) {
        if (roleName == null) return false;
        RoleModel role = getRoleFromString(realm, roleName);
        return role != null && user.hasRole(role);
    }

    private OtpDecision voteForHttpHeader(MultivaluedMap<String, String> headers, Map<String, String> cfg) {
        if (!cfg.containsKey(FORCE_OTP_FOR_HTTP_HEADER) && !cfg.containsKey(SKIP_OTP_FOR_HTTP_HEADER)) return OtpDecision.ABSTAIN;
        if (matches(headers, cfg.get(SKIP_OTP_FOR_HTTP_HEADER))) return OtpDecision.SKIP_OTP;
        if (matches(headers, cfg.get(FORCE_OTP_FOR_HTTP_HEADER))) return OtpDecision.SHOW_OTP;
        return OtpDecision.ABSTAIN;
    }

    private boolean matches(MultivaluedMap<String, String> headers, String pattern) {
        if (pattern == null) return false;
        Pattern p;
        try { p = patternCache.computeIfAbsent(pattern, x -> Pattern.compile(x, Pattern.DOTALL | Pattern.CASE_INSENSITIVE)); }
        catch (PatternSyntaxException e) { logger.errorf("Invalid header pattern: %s", pattern); return false; }
        for (var e : headers.entrySet()) {
            for (String v : e.getValue()) {
                if (p.matcher(e.getKey().trim() + ": " + v.trim()).matches()) return true;
            }
        }
        return false;
    }

    private OtpDecision voteForDefault(Map<String, String> cfg) {
        String v = cfg.get(DEFAULT_OTP_OUTCOME);
        if (v == null) return OtpDecision.ABSTAIN;
        return switch (v) {
            case SKIP -> OtpDecision.SKIP_OTP;
            case FORCE -> OtpDecision.SHOW_OTP;
            default -> OtpDecision.ABSTAIN;
        };
    }
}
