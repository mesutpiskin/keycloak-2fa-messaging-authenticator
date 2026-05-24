<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=message?has_content; section>
    <#if section="header">
        ${msg("messaging-authenticator-setup-title")}
    <#elseif section="form">
        <form id="kc-messaging-setup-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <p>${msg("messaging-authenticator-setup-description", channelDisplayName!"")}</p>
                </div>
            </div>
            <#if message?has_content>
                <div class="${properties.kcFormGroupClass!}">
                    <div class="${properties.kcAlertClass!} ${properties.kcAlertErrorClass!}">
                        <div class="${properties.kcAlertIconClass!}"><span class="${properties.kcFeedbackErrorIcon!}"></span></div>
                        <div class="${properties.kcAlertMessageClass!}">${kcSanitize(message.summary)?no_esc}</div>
                    </div>
                </div>
            </#if>
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <label for="contactAddress" class="${properties.kcLabelClass!}">
                        <#if isTelegram??>${msg("messaging-authenticator-setup-telegram-label")}<#else>${msg("messaging-authenticator-setup-phone-label")}</#if>
                    </label>
                </div>
                <div class="${properties.kcInputWrapperClass!}">
                    <input id="contactAddress" name="contactAddress" type="text" class="${properties.kcInputClass!}" autofocus/>
                </div>
            </div>
            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-buttons">
                    <div class="${properties.kcFormButtonsWrapperClass!}">
                        <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonLargeClass!}" type="submit" value="${msg("messaging-authenticator-setup-button")}"/>
                        <#if isAppInitiatedAction??>
                            <input class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonLargeClass!}" name="cancel-aia" type="submit" value="${msg("doCancel")}"/>
                        </#if>
                    </div>
                </div>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
