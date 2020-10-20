cd ./src/main/resources || exit
rm -f ./*.pkg
rm packages.txt
jq -r '.[] | ("https://packages.simplifier.net/" + .packageName + "/" + .version)' <./manifest.json >>packages.txt
while IFS="" read -r p || [ -n "$p" ]
do
  filename=$(echo "$p" | sed -e 's/https\:\/\/packages\.simplifier\.net\///' -e 's/\/.*//' -e 's/[\.\/]/-/g')
  curl -o "./$filename.pkg" "$p"
done < packages.txt
rm packages.txt