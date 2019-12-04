#!/bin/bash

# Script Args: Docker Hub IMAGE, Module name,Github url
IMAGE=$1
REPO_TITLE=$2
GITHUB_URL=$(echo $3 | sed -e "s/.git$//")
DOCKER_HUB_TOKEN=$(curl -s -X POST \
    -H "Content-Type: application/json" \
    -d '{"username": "'"$DOCKER_USERNAME"'", "password": "'"$DOCKER_PASSWORD"'"}' \
    https://hub.docker.com/v2/users/login/ | jq -r .token)

#Pull Description from Github API
GITHUB_API_URL=$(echo $GITHUB_URL | sed -e 's/github.com/api.github.com\/repos/g')
GITHUB_API_METADATA=$(curl -s "$GITHUB_API_URL")
DESCRIPTION=$(echo $GITHUB_API_METADATA | jq '.description' | cut -d "\"" -f 2)

# Gather metadata from launchDescriptor
MD_FILE="$WORKSPACE/descriptors/ModuleDescriptor-template.json"
METADATA=""
if test -f "$MD_FILE"; then
    PB=$(cat $MD_FILE | jq '.launchDescriptor.dockerArgs.HostConfig.PortBindings')
    [ "$PB" == null ] && MODULE_PORT=null || MODULE_PORT=$(echo $PB | jq -r 'keys'[0] | cut -c1-4)
    [ "$MODULE_PORT" == null ] && : || METADATA="${METADATA}- Module port: $MODULE_PORT\n"
    CONTAINER_MEMORY=$(cat $MD_FILE | jq '.launchDescriptor.dockerArgs.HostConfig.Memory')
    [ "$CONTAINER_MEMORY" == null ] && : || METADATA="${METADATA}- Container memory (bytes): $CONTAINER_MEMORY\n"
    LD_ENV=$(cat $MD_FILE | jq '.launchDescriptor.env')
    if [ "$LD_ENV" != null ]; then
        DB=$(echo $LD_ENV | jq '.[] | select(.name == "DB_DATABASE") | .value')
        [ "$DB" == null ] || [ "$DB" == "" ] && DB_CONNECTION="false" || DB_CONNECTION="true"
        METADATA="${METADATA}- Database connection: $DB_CONNECTION\n"
        JO=$(echo $LD_ENV | jq -r '.[] | select(.name == "JAVA_OPTIONS") | .value')
        [ "$JO" == null ] || [ "$JO" == "" ] && : || METADATA="${METADATA}- JAVA_OPTIONS: $JO\n"
        env_entries=()
        env_show=()
        while IFS= read -r line; do
          env_entries+=("$line")
        done < <(echo $LD_ENV | jq -r '.[] | .name + "=" + .value')
        for entry in "${env_entries[@]}"; do
           if [[ ! ${entry} =~ ^DB_ ]] && [[ ! ${entry} =~ ^JAVA_OPTIONS= ]]; then
              env_show+=("$entry")
           fi
        done
        if (( ${#env_show[@]} )); then
            env_extra=$(printf '  - %s\n' "${env_show[@]}")
            METADATA="${METADATA}- Other environment variables:\n${env_extra}\n"
        fi
    fi

    #Set Metadata Header If Needed
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
    # just warn here for now
    #exit 1
    exit 0
  fi
}

push_readme "${DH_MD_SNIPPIT}" "${IMAGE}" "${DOCKER_HUB_TOKEN}"
