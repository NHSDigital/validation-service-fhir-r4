build:
	bash scripts/download-dependencies.sh
	mvn clean package

build-latest:
	bash scripts/download-latest-dependencies.sh
	mvn clean package