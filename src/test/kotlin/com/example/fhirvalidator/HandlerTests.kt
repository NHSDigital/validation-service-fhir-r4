package com.example.fhirvalidator

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import com.amazonaws.serverless.proxy.model.AwsProxyRequest
import com.amazonaws.serverless.proxy.model.AwsProxyResponse
import com.amazonaws.serverless.proxy.internal.testutils.AwsProxyRequestBuilder
import com.amazonaws.serverless.proxy.internal.testutils.MockLambdaContext
import com.example.fhirvalidator.handler.LambdaHandler

@SpringBootTest
class ProfileIntegrationTest {
    private val lambdaContext = MockLambdaContext()

    @Test
    fun whenTheUsersPathIsInvokedViaLambda_thenShouldReturnAList() {
        val lambdaHandler = LambdaHandler()
        val req = AwsProxyRequestBuilder("_status", "GET").build()
        val resp = lambdaHandler.handleRequest(req, lambdaContext)
        Assertions.assertNotNull(resp.body)
        Assertions.assertEquals(200, resp.statusCode)
    }

    @Test
    fun whenWrongPathPathIsInvokedViaLambda_thenShouldNotFound() {
        val lambdaHandler = LambdaHandler()
        val req = AwsProxyRequestBuilder("/api/v1/users/plus-one-level", "GET").build()
        val resp = lambdaHandler.handleRequest(req, lambdaContext)
        Assertions.assertEquals(404, resp.statusCode)
    }
}