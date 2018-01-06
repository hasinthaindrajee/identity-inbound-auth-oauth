/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.oauth2.token.handlers.clientauth;

import org.apache.axiom.util.base64.Base64Utils;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.io.Charsets;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.oltu.oauth2.common.OAuth;
import org.wso2.carbon.identity.oauth.IdentityOAuthAdminException;
import org.wso2.carbon.identity.oauth.common.OAuthConstants;
import org.wso2.carbon.identity.oauth.common.exception.InvalidOAuthClientException;
import org.wso2.carbon.identity.oauth.common.exception.OAuthClientException;
import org.wso2.carbon.identity.oauth2.IdentityOAuth2Exception;
import org.wso2.carbon.identity.oauth2.dto.OAuth2AccessTokenReqDTO;
import org.wso2.carbon.identity.oauth2.model.HttpRequestHeader;
import org.wso2.carbon.identity.oauth2.model.RequestParameter;
import org.wso2.carbon.identity.oauth2.token.OAuthTokenReqMessageContext;
import org.wso2.carbon.identity.oauth2.util.OAuth2Util;

public class BasicAuthClientAuthHandler extends AbstractClientAuthHandler {

    private static Log log = LogFactory.getLog(BasicAuthClientAuthHandler.class);

    @Override
    public boolean authenticateClient(OAuthTokenReqMessageContext tokReqMsgCtx)
            throws IdentityOAuth2Exception {

        if (isAuthorizationHeaderExists(tokReqMsgCtx)) {
            validateAuthorizationHeader(tokReqMsgCtx);
            try {
                extractCredentialsFromAuthzHeader(getAuthorizationHeader(tokReqMsgCtx));
            } catch (OAuthClientException e) {
                String errorMessage = "Error while extracting client id and secret from authoriztion header";
                log.error(errorMessage);
                if (log.isDebugEnabled()) {
                    log.error(errorMessage, e);
                }
            }
            return false;
        } else {
            setClientCredentialsFromParam(tokReqMsgCtx);
        }

        if (StringUtils.isEmpty(tokReqMsgCtx.getOauth2AccessTokenReqDTO().getClientId())) {
            return false;
        } else {
            OAuth2AccessTokenReqDTO oAuth2AccessTokenReqDTO =
                    tokReqMsgCtx.getOauth2AccessTokenReqDTO();
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Authenticating client: " + oAuth2AccessTokenReqDTO.getClientId() + " with client " +
                            "secret.");
                }
                return OAuth2Util.authenticateClient(oAuth2AccessTokenReqDTO.getClientId(),
                        oAuth2AccessTokenReqDTO.getClientSecret());
            } catch (IdentityOAuthAdminException e) {
                throw new IdentityOAuth2Exception("Error while authenticating client", e);
            } catch (InvalidOAuthClientException e) {
                throw new IdentityOAuth2Exception("Invalid Client : " + oAuth2AccessTokenReqDTO.getClientId(), e);
            }
        }
    }

    @Override
    public boolean canAuthenticate(OAuthTokenReqMessageContext tokReqMsgCtx) throws IdentityOAuth2Exception {
        if (isClientCredentialsExistsAsParams(tokReqMsgCtx)) {
            return true;
        } else if (isAuthorizationHeaderExists(tokReqMsgCtx)) {
            return true;
        }
        return false;
    }

    public String getClientId(OAuthTokenReqMessageContext tokReqMsgCtx) throws IdentityOAuth2Exception {
        String clientId = super.getClientId(tokReqMsgCtx);
        if (StringUtils.isNotEmpty(super.getClientId(tokReqMsgCtx))) {
            return clientId;
        } else {
            if (isAuthorizationHeaderExists(tokReqMsgCtx)) {
                validateAuthorizationHeader(tokReqMsgCtx);
                try {
                    extractCredentialsFromAuthzHeader(getAuthorizationHeader(tokReqMsgCtx));
                } catch (OAuthClientException e) {
                    String errorMessage = "Error while extracting client id and secret from authoriztion header";
                    throw new IdentityOAuth2Exception(errorMessage, e);
                }
            } else {
                setClientCredentialsFromParam(tokReqMsgCtx);
            }
            return tokReqMsgCtx.getOauth2AccessTokenReqDTO().getClientId();
        }
    }

    private void validateAuthorizationHeader(OAuthTokenReqMessageContext oAuthTokenReqMessageContext) throws
            IdentityOAuth2Exception {

        // The client MUST NOT use more than one authentication method in each request
        if (isClientCredentialsExistsAsParams(oAuthTokenReqMessageContext)) {
            if (log.isDebugEnabled()) {
                log.debug("Client Id and Client Secret found in request body and Authorization header" +
                        ". Credentials should be sent in either request body or Authorization header, not both");
            }
            throw new IdentityOAuth2Exception("Client Authentication failed");
        }
    }

    private boolean isAuthorizationHeaderExists(OAuthTokenReqMessageContext tokenReqMessageContext) {
        return StringUtils.isNotEmpty(getAuthorizationHeader(tokenReqMessageContext));
    }

    private String getAuthorizationHeader(OAuthTokenReqMessageContext oAuthTokenReqMessageContext) {
        HttpRequestHeader[] httpRequestHeaders = oAuthTokenReqMessageContext.getOauth2AccessTokenReqDTO()
                .getHttpRequestHeaders();
        if (httpRequestHeaders != null) {
            for (HttpRequestHeader header : httpRequestHeaders) {
                if (HTTPConstants.HEADER_AUTHORIZATION.equalsIgnoreCase(header.getName()) && header.getValue()
                        .length > 0 && StringUtils.isNotEmpty(header.getValue()[0]) && header.getValue()[0]
                        .contains("Basic")) {
                    return header.getValue()[0];
                }
            }
        }
        return null;
    }

    private boolean isClientCredentialsExistsAsParams(OAuthTokenReqMessageContext tokenReqMessageContext) {
        boolean clientIdExists = false;
        boolean clientSecretExists = false;
        if (tokenReqMessageContext.getOauth2AccessTokenReqDTO().getRequestParameters() != null) {
            for (RequestParameter requestParameter : tokenReqMessageContext.getOauth2AccessTokenReqDTO()
                    .getRequestParameters()) {
                if (OAuth.OAUTH_CLIENT_ID.equalsIgnoreCase(requestParameter.getKey())) {
                    clientIdExists = true;
                } else if (OAuth.OAUTH_CLIENT_SECRET.equalsIgnoreCase(requestParameter.getKey())) {
                    clientSecretExists = true;
                }
            }
        }
        return clientIdExists && clientSecretExists;
    }

    private static String[] extractCredentialsFromAuthzHeader(String authorizationHeader)
            throws OAuthClientException {

        if (authorizationHeader == null) {
            throw new OAuthClientException("Authorization header value is null");
        }
        String[] splitValues = authorizationHeader.trim().split(" ");
        if (splitValues.length == 2) {
            byte[] decodedBytes = Base64Utils.decode(splitValues[1].trim());
            String userNamePassword = new String(decodedBytes, Charsets.UTF_8);
            String[] credentials = userNamePassword.split(":");
            if (credentials.length == 2) {
                return credentials;
            }
        }
        String errMsg = "Error decoding authorization header. Space delimited \"<authMethod> <base64Hash>\" format " +
                "violated.";
        throw new OAuthClientException(errMsg);
    }

    private void setClientCredentialsFromParam(OAuthTokenReqMessageContext tokenReqMessageContext) {
        if (tokenReqMessageContext.getOauth2AccessTokenReqDTO().getRequestParameters() != null) {
            for (RequestParameter requestParameter : tokenReqMessageContext.getOauth2AccessTokenReqDTO()
                    .getRequestParameters()) {
                if (OAuth.OAUTH_CLIENT_ID.equalsIgnoreCase(requestParameter.getKey()) && requestParameter.getValue() !=
                        null && requestParameter.getValue().length > 0) {
                    tokenReqMessageContext.getOauth2AccessTokenReqDTO().setClientId(requestParameter.getValue()[0]);
                } else if (OAuth.OAUTH_CLIENT_SECRET.equalsIgnoreCase(requestParameter.getKey()) && requestParameter.getValue() !=
                        null && requestParameter.getValue().length > 0) {
                    tokenReqMessageContext.getOauth2AccessTokenReqDTO().setClientSecret(requestParameter.getValue()[0]);
                }
            }
        }
    }
}
