<#-- hostily Captcha: page of the authenticator step. Uses only the layout macro and theme classes, so it works in keycloak, keycloak.v2 and themes derived from them. -->
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
    <#if section = "header">
        ${msg("hostilyCaptchaTitle")}
    <#elseif section = "form">
        <form id="kc-hostily-captcha-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <hostily-captcha data-site-key="${hostilyCaptcha.siteKey}" data-api="${hostilyCaptcha.api}" data-modus="${hostilyCaptcha.mode}"<#if hostilyCaptcha.language?has_content> data-sprache="${hostilyCaptcha.language}"</#if>></hostily-captcha>
            </div>
            <div id="kc-form-buttons" class="${properties.kcFormGroupClass!}">
                <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" name="continue" id="kc-continue" type="submit" value="${msg("hostilyCaptchaContinue")}"/>
            </div>
        </form>
        <script src="${hostilyCaptcha.service}/widget/hostily-captcha.js" defer></script>
    <#elseif section = "info">
        <span class="${properties.kcLoginMainFooterHelperText!}">${msg("hostilyCaptchaInfo")}</span>
    </#if>
</@layout.registrationLayout>
