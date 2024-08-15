install:
	poetry install

lint:
	poetry run flake8 scripts/*.py --config .flake8
	shellcheck scripts/*.sh

test:
	poetry run scripts/download_dependencies.py
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

build: test
	mvn package
	docker build .

build-latest: clean-packages update-manifest build

run:
	mvn spring-boot:run

docker-run: build
	docker-compose up

docker-rebuild-run: build
	docker-compose up --no-deps --build fhir-validator
