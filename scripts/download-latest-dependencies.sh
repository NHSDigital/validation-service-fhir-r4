cd ./src/main/resources || exit
rm -f ./*.pkg
rm packages.txt
jq -r '.[] | ("https://packages.simplifier.net/" + .packageName)' <./manifest.json >>packages.txt
while IFS="" read -r p || [ -n "$p" ]
do
  version=$(curl "$p" | jq '.["dist-tags"].latest' | sed -e 's/\"//g')
  filename=$(echo "$p" | sed -e 's/https\:\/\/packages\.simplifier\.net\///' -e 's/[\.\/]/-/g')
  curl -o "./$filename.pkg" "$p/$version"
done < packages.txt
rm packages.txt