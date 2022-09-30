package uk.nhs.nhsdigital.fhirvalidator.configuration

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration



@Configuration
class OpenApiConfig {
    @Bean
    fun customOpenAPI(
        fhirServerProperties: FHIRServerProperties,
       // restfulServer: FHIRR4RestfulServer
    ): OpenAPI? {
        // This is just an example, need to be able to get the real one
        return OpenAPI()
            .info(
                Info()
                    .title("sample application API")
                    .version(fhirServerProperties.server.version)
                    .description(fhirServerProperties.server.name)
                    .termsOfService("http://swagger.io/terms/")
                    .license(License().name("Apache 2.0").url("http://springdoc.org"))
            )
            .path("FHIR/R4/\$validate", PathItem().post(Operation().description("FHIR Validation")))
    }
}
