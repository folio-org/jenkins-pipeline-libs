#!/bin/bash 

#set -x

type curl >/dev/null 2>&1 || { echo >&2 "$0: curl is required but it's not installed"; exit 1; }

usage() {
   cat << EOF
Usage: ${0##*/} -o OKAPI_URL -t TENANT -u USERNAME -p PASSWORD
EOF
}

while [[ $# -gt 0 ]]
do
   key="$1"

   case $key in
      -o|--okapiurl)
         okapiUrl="$2"
         shift # past argument
         shift # past value
        ;;
      -t|--tenant)
         tenant="$2"
         shift # past argument
         shift # past value
         ;;
      -u|--username)
         username="$2"
         shift # past argument
         shift # past value
         ;;
      -p|--password)
         password="$2"
         shift # past argument
         shift # past value
         ;;
      *) 
         usage >&2
         exit 1 
         ;;
esac
done

if [[ -z "$okapiUrl" || -z "$tenant" || -z "$username" || -z "$password" ]]; then
    echo "Missing required option(s)"
    usage >&2
    exit 1
fi


credentials="{\"username\":\"${username}\",\"password\":\"${password}\"}"
success='HTTP/1.1 201 Created'

while read -r l; 
do
  line=$(tr -d '\r' <<<$l)

  if [[ $line =~ ^HTTP/1.1 ]] && [[ ! $line =~ ^$success ]] ; then
    status=$(awk '{ print $2 }' <<<$line)
    echo "$0: Authentication failed: $status" >&2
    exit 1
  fi

  if [[ $line =~ ^x-okapi-token:* ]]; then
    token_x=$(awk -F ':' '{ print $2 }' <<<$line)
    token=$(sed -e 's/^[[:space:]]*//g' <<<$token_x)
  fi
done <<<"$(curl -s -D - -X POST -o /dev/null -d "$credentials" \
    --connect-timeout 10 \
    -H "X-Okapi-Tenant: $tenant" \
    -H 'Accept: application/json, text/plain' \
    -H 'Content-Type: application/json' \
    ${okapiUrl}/authn/login)"

if [[ -n "$token" ]]; then
  echo "$token"
  exit 0
else 
  echo "$0: An error occurred. Probably a connection error" >&2
  exit 1
fi
