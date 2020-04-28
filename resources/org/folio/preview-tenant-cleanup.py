import argparse
import boto3
from botocore.exceptions import ClientError
from collections import defaultdict
import json
import os
import re
import requests
import sys

ORGANIZATION = "folio-org"

def main():
    args = parse_command_line_args()
    token = okapi_auth(
                args.okapi_url, args.username, args.password, "supertenant"
            )
    # Find all tenants
    tenants = get_tenants(args.okapi_url)
    tenants_on_open_prs = defaultdict(list)
    print("Checking for tenants from closed PRs")
    for t in tenants:
        tenant_split = t.rsplit('_',2)
        if tenant_split[0][:9] == 'platform_':
            build_number = t.rsplit('_', 1)[1]
            pr_number = t.rsplit('_', 2)[1]
            pr_repo = t.rsplit('_', 2)[0].replace("_", "-")
            repo = "{}/{}".format(ORGANIZATION, pr_repo)
            print("Checking pr for tenant: {}".format(t))
            closed = check_pr(repo, pr_number)
            if closed == True:
                print("CLOSED " + t)
                if args.dry_run == False:
                    purge_modules(args.okapi_url, t, token)
                    delete_tenant(args.okapi_url, t, token)
                    delete_bucket("-".join([pr_repo, pr_number]), args.aws_id, args.aws_key)
                else:
                    print("Dry run, no action taken")
            elif closed == False:
                tenants_on_open_prs[pr_number].append(
                    {
                     'id':t,
                     'build':int(build_number)
                    }
                )
    
    if len(tenants_on_open_prs) > 0:
        print("Checking for expired tenants on open PRs")
        for pr in tenants_on_open_prs:
            latest_build = max([t['build'] for t in tenants_on_open_prs[pr]])
            for tenant in tenants_on_open_prs[pr]:
                if tenant['build'] != latest_build:
                    print("latest build is {}".format(str(latest_build)))
                    print(tenant['id'] + " is not the latest build.")
                    if args.dry_run == False:
                        purge_modules(args.okapi_url, t, token)
                        delete_tenant(args.okapi_url, tenant['id'], token)
                    else:
                        print("Dry run, no action taken")

def parse_command_line_args():
    parser = argparse.ArgumentParser()
    parser.add_argument('-d', '--dry-run', help='Dry run: do not do deletion, print app names to be deleted only',
                        action='store_true', required=False)
    parser.add_argument('-o', '--okapi-url', help='okapi url check for tenants and enabled modules',
                        default='https://okapi-preview.ci.folio.org', required=False)
    parser.add_argument('-u', '--username', help='Supertenant username', default="okapi_preview_admin", required=True)
    parser.add_argument('-p', '--password', help='supertenant password', required=True)
    parser.add_argument('-k', '--aws-key', help='aws access key', required=True)
    parser.add_argument('-i', '--aws-id', help='aws access key id', required=True)

    args = parser.parse_args()

    return args

def get_tenants(okapi_url):
    tenants = []
    r = _okapi_get(okapi_url + "/_/proxy/tenants")
    for tenant in r.json():
        tenants.append(tenant['id'])
    
    return tenants

def purge_modules(okapi_url, tenant, token):
    print("purging modules on {}".format(tenant))
    headers = {
        "x-okapi-tenant" : "supertenant",
        "x-okapi-token" : token,
    }
    params = {"purge": "true"}
    r = requests.get(okapi_url + '/_/proxy/tenants/{}/modules'.format(tenant),
                     headers=headers)
    r.raise_for_status()
    modules = r.json()
    for m in modules:
        m['action'] = 'disable'
    r = requests.post(okapi_url + '/_/proxy/tenants/{}/install'.format(tenant),
                      headers=headers,
                      params=params,
                      data=json.dumps(modules))
    return(r)

def delete_tenant(okapi_url, tenant, token):
    deleted = False
    headers = {
        "x-okapi-tenant" : "supertenant",
        "x-okapi-token" : token,
    }
    r = requests.delete(okapi_url + 
                        '/_/proxy/tenants/{}'.format(tenant),
                        headers=headers)
    
    if r.status_code == 204:
        print("sucessfully deleted {}".format(tenant))
        deleted = True
    else:
        r.raise_for_status()
        print("Failed to delete {} with status {}".format(
            tenant, str(r.status_code)
        ))
    
    return deleted

def delete_bucket(bucket_name, aws_id, aws_key):
    deleted = False
    s3 = boto3.resource('s3', 
                        aws_access_key_id=aws_id,
                        aws_secret_access_key=aws_key)
    bucket = s3.Bucket(bucket_name) 
    print(bucket.name)
    try:
        #check if bucket exists, and delete
        s3.meta.client.head_bucket(Bucket=bucket_name)
        for key in bucket.objects.all():
            key.delete()
        r = bucket.delete()
        deleted = r['ResponseMetadata']['HTTPStatusCode']
        print("deleted bucket: {}".format(bucket_name))
    except ClientError as e:
        # notify if bucket doesn't exist but don't exit
        error_code = e.response['Error']['Code']
        if error_code == '404':
            print("could not find bucket {}".format(bucket_name))

    return deleted

def check_pr(repository, pr_number):
    closed = False
    pr_number = str(pr_number)
    r = requests.get(
            "https://api.github.com/repos/{}/pulls/{}"
            .format(repository, pr_number)
        )
    r.raise_for_status()
    if r.json()['state'] == 'closed':
        closed = True
    else:
        print(
            "Pull request {} on {} is open, skipping..."
            .format(pr_number, repository))
            
    return closed

def okapi_auth(okapi, username, password, tenant='supertenant'):
    headers = {"X-Okapi-Tenant": tenant}
    payload = {
        "username" : username,
        "password" : password
    }
    r = requests.post(okapi + '/authn/login', 
                      headers=headers, json=payload)
    try:
        r.raise_for_status()
    except requests.exceptions.HTTPError as e:
        print("Error: " + str(e))

    return r.headers['x-okapi-token']


def _okapi_get(okapi_url, params=None,
              tenant="supertenant", token=""):
    params = params or {}
    headers = {
        "X-Okapi-Tenant" : tenant,
        "X-Okapi-Token" : token,
        "Accept" : "application/json"
    }
    r = requests.get(okapi_url,
                     headers=headers,
                     params=params)

    try:
        r.raise_for_status()
    except requests.exceptions.HTTPError as e:
        print("Error: " + str(e))

    return r

if __name__ == "__main__":
    main()
