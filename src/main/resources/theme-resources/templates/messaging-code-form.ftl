<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('messagingCode'); section>
    <#if section="header">
        ${msg("doLogIn")}
    <#elseif section="form">
        <form id="kc-otp-login-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <#assign otpLength = (codeLength!6)>
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <label for="messagingCode" class="${properties.kcLabelClass!}">
                        ${msg("messagingOtpForm", otpLength, channelDisplayName!"", contactMasked!"")}
                    </label>
                </div>
                <div class="${properties.kcInputWrapperClass!}">
                    <input id="messagingCode" name="messagingCode" autocomplete="off" type="text"
                           class="${properties.kcInputClass!}" autofocus
                           aria-invalid="<#if messagesPerField.existsError('messagingCode')>true</#if>"
                           <#if maxAttemptsReached?? && maxAttemptsReached>disabled</#if>/>
                    <#if messagesPerField.existsError('messagingCode')>
                        <span id="input-error-otp-code" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                            ${kcSanitize(messagesPerField.get('messagingCode'))?no_esc}
                        </span>
                    </#if>
                </div>
            </div>
            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-buttons">
                    <div class="${properties.kcFormButtonsWrapperClass!}">
                        <#if !(maxAttemptsReached?? && maxAttemptsReached)>
                            <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonLargeClass!}" name="login" type="submit" value="${msg("doLogIn")}"/>
                        </#if>
                        <input class="${properties.kcButtonClass!} <#if maxAttemptsReached?? && maxAttemptsReached>${properties.kcButtonPrimaryClass!}<#else>${properties.kcButtonDefaultClass!}</#if> ${properties.kcButtonLargeClass!}" name="resend" type="submit" value="${msg("resendCode")}"/>
                        <input class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonLargeClass!}" name="cancel" type="submit" value="${msg("doCancel")}"/>
                    </div>
                </div>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
