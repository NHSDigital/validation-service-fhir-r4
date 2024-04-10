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

## How to update validator in EPS
From repo root, run: 
- `make install`
- `make build-latest`

Commit and push changes to a branch. Get merged into main and reference new commit from EPS repo


## Run docker service locally
To run the docker image locally use
```
make docker-run
```
To run the docker image locally forcing a rebuild of the docker image use
```
make docker-rebuild-run
```
Note - the jar file is patched into the image at run time, and it is rebuilt before running the container, so you should not need to use docker-rebuild-run

To test it is running you can use the following
```
curl http://localhost:9001/_status
```
To validate a FHIR message you can use
```
curl -X POST "http://localhost:9001/\$validate" \
  -H "Content-Type: application/json" \
  -d "@1-Prepare-Request-200_OK.json"
```
