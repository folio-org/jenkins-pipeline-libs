#!/bin/bash

# Script Args: IMAGE VARIABLES
IMAGE=$1
REPO_TITLE=$(echo $2 | sed -e 's/-/ /g' -e 's/\b\(.\)/\u\1/g')
GITHUB_URL=$3
DOCKER_HUB_TOKEN=$(curl -s -X POST \
    -H "Content-Type: application/json" \
    -d '{"username": "'"$DOCKER_USERNAME"'", "password": "'"$DOCKER_PASSWORD"'"}' \
    https://hub.docker.com/v2/users/login/ | jq -r .token)

#Pull Description from Github API
GITHUB_API_URL=$(echo $GITHUB_URL | sed -e 's/github.com/api.github.com\/repos/g') 
GITHUB_API_METADATA=$(curl -s "$GITHUB_API_URL")
DESCRIPTION=$(echo $GITHUB_API_METADATA | jq '.description' | cut -d "\"" -f 2)

#PULL Metadata from Module Descriptor
MD_FILE="$WORKSPACE/descriptors/ModuleDescriptor-template.json"
METADATA=""
if test -f "$MD_FILE"; then
    CONTAINER_PORT=$(cat $MD_FILE | jq '.launchDescriptor.dockerArgs.HostConfig.PortBindings'| jq 'keys'[0] |  cut -c1-5 | cut -d "\"" -f 2)
    [ "$CONTAINER_PORT" == null ] && : || METADATA="${METADATA}1. Module Port: $CONTAINER_PORT\n"
    DB_CONNECTION=$(cat $MD_FILE | jq '.metadata.databaseConnection' | cut -d "\"" -f 2)
    [ "$DB_CONNECTION" == null ] && : || METADATA="${METADATA}1. Database Connection: $DB_CONNECTION\n"
    CONTAINER_MEMORY=$(cat $MD_FILE | jq '.metadata.containerMemory' | cut -d "\"" -f 2)
    [ "$CONTAINER_MEMORY" == null ] && : || METADATA="${METADATA}1. Minimum Memory (MiB): $CONTAINER_MEMORY\n"

    #Set Markdown Metadata Header if needed
    [ "$METADATA" == "" ] && : || METADATA="### Metadata\n\n${METADATA}"
    METADATA=$(echo -e $METADATA)
fi

# SET Docker Hub Markdown Snippet
read -r -d '' DH_MD_SNIPPIT <<- EOM
# FOLIO - $REPO_TITLE 

### Description

$DESCRIPTION 

Code Repository: [$GITHUB_URL]($GITHUB_URL) 

$METADATA 

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
