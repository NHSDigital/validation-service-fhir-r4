# validation-service-fhir-r4

This service can be used to validate FHIR R4 messages against implementation guides on [Simplifier](https://simplifier.net/).

## Customising the validator
The Simplifier packages to be used for validation are declared in manifest.json. These are downloaded at build time and included in the jar.

## Known limitations
* Resources must use FHIR version R4
* The profiles from the Simplifier packages will only be used in one of the following cases:
  * The resource being validated specifies a profile in the meta field
  * The resource being validated is a message, and a matching message definition in the packages specifies which profile to use for a given resource type
  * A capability statement in the packages specifies which profile to use for a given resource type
* The validator does not use a terminology server, so some code systems, including SNOMED, cannot be validated