#!/bin/bash

if [ $# != 2 ]; then
    echo "Usage: $0 okapi_URL tenant"
    exit 1
fi

type curl >/dev/null 2>&1 || { echo >&2 "curl is required but it's not installed"; exit 1; }

okapiUrl=$1
tenant=$2

# add tenant
curl -s -w '\n' -X POST \
  -H 'Content-type: application/json' \
  -H 'X-Okapi-Tenant: supertenant' \
  -H "X-Okapi-Token: $OKAPI_TOKEN" \
  -d "{\"id\":\"${tenant}\"}" ${okapiUrl}/_/proxy/tenants

# enable okapi internal module for tenant
curl -s -S -w '\n' -X POST \
  -H 'Content-type: application/json' \
  -H 'X-Okapi-Tenant: supertenant' \
  -H "X-Okapi-Token: $OKAPI_TOKEN" \
  -d '{"id":"okapi"}' ${okapiUrl}/_/proxy/tenants/${tenant}/modules
