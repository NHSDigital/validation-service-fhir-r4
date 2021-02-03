clean-packages:
	rm src/main/resources/*.tgz

update-manifest:
	python scripts/update_manifest.py

build:
	python scripts/download_dependencies.py
	mvn clean package
