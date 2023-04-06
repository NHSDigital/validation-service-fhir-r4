package uk.nhs.nhsdigital.fhirvalidator.util

import ca.uhn.fhir.rest.client.api.IClientInterceptor
import ca.uhn.fhir.rest.client.api.IHttpRequest
import ca.uhn.fhir.rest.client.api.IHttpResponse
import uk.nhs.nhsdigital.fhirvalidator.configuration.REGISTRATION_ID
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.core.OAuth2AccessToken

class AccessTokenInterceptor(private val authorizedClientManager: OAuth2AuthorizedClientManager) : IClientInterceptor {
    override fun interceptRequest(request: IHttpRequest?) {
        val accessToken = getAccessToken()
        request?.addHeader("Authorization", "Bearer ${accessToken.tokenValue}")
    }

    private fun getAccessToken(): OAuth2AccessToken {
        val authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
            .principal("test")
            .build()
        val authorizedClient = authorizedClientManager.authorize(authorizeRequest)
            ?: throw Error("Authorization failed")
        return authorizedClient.accessToken
    }

    override fun interceptResponse(theResponse: IHttpResponse?) {/*No change to response needed*/}
}
