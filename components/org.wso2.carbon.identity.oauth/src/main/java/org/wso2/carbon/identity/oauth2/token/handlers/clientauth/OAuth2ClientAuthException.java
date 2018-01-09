package org.wso2.carbon.identity.oauth2.token.handlers.clientauth;

import org.wso2.carbon.identity.oauth2.IdentityOAuth2Exception;

public class OAuth2ClientAuthException extends IdentityOAuth2Exception{
    private String errorCode;

    public OAuth2ClientAuthException(String message) {
        super(message);
    }

    public OAuth2ClientAuthException(String message, Throwable e) {
        super(message, e);
    }

    public OAuth2ClientAuthException (String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public OAuth2ClientAuthException (String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

}
