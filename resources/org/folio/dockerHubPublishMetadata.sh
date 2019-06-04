#!/bin/bash

# SET VARIABLES
IMAGE=$1
REPO_TITLE=$(echo $2 | sed -e 's/-/ /g' -e 's/\b\(.\)/\u\1/g')
GITHUB_URL=$3
DOCKER_HUB_TOKEN=$(curl -s -X POST \
    -H "Content-Type: application/json" \
    -d '{"username": "'"$DOCKER_USERNAME"'", "password": "'"$DOCKER_PASSWORD"'"}' \
    https://hub.docker.com/v2/users/login/ | jq -r .token)

#PULL MetaData from Mod Descriptor
MD_FILE="$WORKSPACE/descriptors/ModuleDescriptor-template.json"
if test -f "$MD_FILE"; then
    CONTAINER_MEMORY=$(cat $MD_FILE | jq '.metadata.containerMemory' | cut -d "\"" -f 2)
    DB_CONNECTION=$(cat $MD_FILE | jq '.metadata.databaseConnection' | cut -d "\"" -f 2)
else
    printf "Unable to find push ModuleDescriptor-template.json file"
    exit 1
fi

# SET Docker Hub Markdown Snippet
read -r -d '' DH_MD_SNIPPIT <<- EOM
# FOLIO $REPO_TITLE 

$DESCRIPTION 

Github Repository: [$GITHUB_URL]($GITHUB_URL). 

# Requirements 

1. Database Connection: $DB_CONNECTION 
2. Minimum Memory (MiB): $CONTAINER_MEMORY 

EOM

push_readme() {
  declare -r readme="${1}"
  declare -r image="${2}"
  declare -r token="${3}"

  local code=$(jq -n --arg msg "${readme}" \
    '{"registry":"registry-1.docker.io","full_description": $msg }' | \
        curl -s -o /dev/null  -L -w "%{http_code}" \
           https://cloud.docker.com/v2/repositories/"${image}"/ \
           -d @- -X PATCH \
           -H "Content-Type: application/json" \
           -H "Authorization: JWT ${token}")

  if [[ "${code}" = "200" ]]; then
    printf "Successfully pushed README to Docker Hub: ${image} \n"
  else
    printf "Unable to push README to Docker Hub, response code: %s\n" "${code}"
    exit 1
  fi
}

push_readme "${DH_MD_SNIPPIT}" "${IMAGE}" "${DOCKER_HUB_TOKEN}"
