package com.example.fhirvalidator.handler

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.serverless.proxy.model.AwsProxyRequest
import com.amazonaws.serverless.proxy.model.AwsProxyResponse
import com.amazonaws.serverless.exceptions.ContainerInitializationException
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler
import com.example.fhirvalidator.FhirValidatorApplication

class LambdaHandler : RequestHandler<AwsProxyRequest, AwsProxyResponse> {
    companion object {
        private lateinit var handler: SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse>
        init {
            try {
                handler = SpringBootLambdaContainerHandler.getAwsProxyHandler(FhirValidatorApplication::class.java)
            } catch (ex: ContainerInitializationException) {
                throw RuntimeException("Unable to load spring boot application", ex)
            }
        }
    }

    override fun handleRequest(input: AwsProxyRequest, context: Context): AwsProxyResponse {
        return handler.proxy(input, context)
    }
}