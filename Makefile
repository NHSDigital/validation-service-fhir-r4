install: install-python install-hooks

install-python:
	poetry install

install-hooks: install-python
	poetry run pre-commit install --install-hooks --overwrite

lint:
	poetry run flake8 scripts/*.py --config .flake8
	shellcheck scripts/*.sh

test:
	$(MAKE) run-tests || $(MAKE) show-test-error-log

run-tests:
	poetry run scripts/download_dependencies.py
	mkdir -p target
	mvn clean test jacoco:report > target/maven-test-output.txt 2>&1

check-licences:
	scripts/check_python_licenses.sh
	mvn validate

clean-packages:
	rm -f src/main/resources/*.tgz

clean: clean-packages
	rm -rf target
	mvn clean

update-manifest:
	poetry run scripts/update_manifest.py

build:
	$(MAKE) build-java || $(MAKE) show-build-error-log

build-java:
	mkdir -p target
	mvn package > target/maven-build-output.txt 2>&1
	docker build .

build-latest: clean-packages update-manifest build

show-build-error-log:
	echo "An error occured in build step"
	cat target/maven-build-output.txt
	exit 1

show-test-error-log:
	echo "An error occured in test step"
	cat target/maven-test-output.txt
	exit 1

run:
	mvn spring-boot:run

docker-run: build
	docker-compose up

docker-rebuild-run: build
	docker-compose up --no-deps --build fhir-validator
