/*
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jasig.cas.support.pac4j.authentication.handler.support;

import java.security.GeneralSecurityException;

import javax.security.auth.login.FailedLoginException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.jasig.cas.authentication.BasicCredentialMetaData;
import org.jasig.cas.authentication.Credential;
import org.jasig.cas.authentication.HandlerResult;
import org.jasig.cas.authentication.PreventedException;
import org.jasig.cas.authentication.handler.support.AbstractPreAndPostProcessingAuthenticationHandler;
import org.jasig.cas.authentication.principal.SimplePrincipal;
import org.jasig.cas.support.pac4j.authentication.principal.ClientCredential;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.context.J2EContext;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.profile.UserProfile;
import org.springframework.webflow.context.ExternalContextHolder;
import org.springframework.webflow.context.servlet.ServletExternalContext;

/**
 * This handler authenticates the client credentials : it uses them to get the user profile returned by the provider
 * for an authenticated user.
 *
 * @author Jerome Leleu
 * @since 3.5.0
 */
@SuppressWarnings("unchecked")
public final class ClientAuthenticationHandler extends AbstractPreAndPostProcessingAuthenticationHandler {

    /**
     * The clients for authentication.
     */
    @NotNull
    private final Clients clients;

    /**
     * Whether to use the typed identifier (by default) or just the identifier.
     */
    private boolean typedIdUsed = true;

    /**
     * Define the clients.
     *
     * @param theClients The clients for authentication
     */
    public ClientAuthenticationHandler(final Clients theClients) {
        this.clients = theClients;
    }

    @Override
    public boolean supports(final Credential credential) {
        return credential != null && ClientCredential.class.isAssignableFrom(credential.getClass());
    }

    @Override
    protected HandlerResult doAuthentication(final Credential credential) throws GeneralSecurityException, PreventedException {
        final ClientCredential clientCredentials = (ClientCredential) credential;
        logger.debug("clientCredentials : {}", clientCredentials);

        final String clientName = clientCredentials.getCredentials().getClientName();
        logger.debug("clientName : {}", clientName);

        // get client
        final Client<Credentials, UserProfile> client = this.clients.findClient(clientName);
        logger.debug("client : {}", client);

        // web context
        final ServletExternalContext servletExternalContext = (ServletExternalContext) ExternalContextHolder.getExternalContext();
        final HttpServletRequest request = (HttpServletRequest) servletExternalContext.getNativeRequest();
        final HttpServletResponse response = (HttpServletResponse) servletExternalContext.getNativeResponse();
        final WebContext webContext = new J2EContext(request, response);
        
        // get user profile
        final UserProfile userProfile = client.getUserProfile(clientCredentials.getCredentials(), webContext);
        logger.debug("userProfile : {}", userProfile);

        if (userProfile != null) {
            final String id;
            if (typedIdUsed) {
                id = userProfile.getTypedId();
            } else {
                id = userProfile.getId();
            }
            if (StringUtils.isNotBlank(id)) {
              clientCredentials.setUserProfile(userProfile);
              return new HandlerResult(
                      this,
                      new BasicCredentialMetaData(credential),
                      new SimplePrincipal(id, userProfile.getAttributes()));
            }
        }

        throw new FailedLoginException("Provider did not produce profile for " + clientCredentials);
    }

    public boolean isTypedIdUsed() {
        return typedIdUsed;
    }

    public void setTypedIdUsed(final boolean typedIdUsed) {
        this.typedIdUsed = typedIdUsed;
    }
}
