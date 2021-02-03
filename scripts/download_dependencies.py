import json
import requests

with open("src/main/resources/manifest.json", "r") as manifestFile:
    manifestStr = manifestFile.read()
    manifest = json.loads(manifestStr)
    for entry in manifest:
        packageName = entry["packageName"]
        version = entry["version"]
        packageData = requests.get(f"https://packages.simplifier.net/{packageName}/{version}").content
        with open(f"src/main/resources/{packageName}-{version}.tgz", "wb") as packageFile:
            packageFile.write(packageData)
