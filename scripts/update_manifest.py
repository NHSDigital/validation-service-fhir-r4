import json
import requests

with open("src/main/resources/manifest.json", "r") as manifestFile:
    manifestStr = manifestFile.read()
    manifest = json.loads(manifestStr)
    for entry in manifest:
        packageName = entry["packageName"]
        currentVersion = entry["version"]
        packageInfo = requests.get(f"https://packages.simplifier.net/{packageName}").json()
        latestVersion = packageInfo["dist-tags"]["latest"]
        print(f"{packageName}: {currentVersion} -> {latestVersion}")
        entry["version"] = latestVersion
with open("src/main/resources/manifest.json", "w") as manifestFile:
    manifestStr = json.dumps(manifest, indent=2)
    manifestFile.write(manifestStr)