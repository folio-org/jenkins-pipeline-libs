#!/bin/bash

group_tag=$1
base_build=$(awk -F '_' '{ print $1 "_" $2}'<<< $group_tag)

aws="/usr/bin/aws --region us-east-1"


# Get instance IDs of previous instances tagged with 'Build:folio_BUILDNAME_latest'...
filter="Name=resource-type,Values=instance Name=key,Values=Build Name=value,Values=${base_build}_latest"
current_latest=$($aws ec2 describe-tags --filters $filter --query Tags[*].ResourceId)

# And re-tag those instances to Build:folio_BUILDNAME_old
if [ -n "$current_latest" ]; then 
  echo "Re-tagging previous builds to '${base_build}_old'"
  for i in $current_latest
  do
    $aws ec2 create-tags --resources $i --tags Key=Build,Value=${base_build}_old
  done
else
  echo "No matching tags for Build: '${base_build}_latest' found."
fi

# Get instance ID(s) of folio platform we just built...
filter="Name=resource-type,Values=instance Name=key,Values=Group Name=value,Values=${group_tag}"
new_latest=$($aws ec2 describe-tags --filters $filter --query Tags[*].ResourceId)


# And add 'Build:folio_BUILDNAME_latest' tag.
if [ -n "$new_latest" ]; then 
  echo "Adding tag 'Build:${base_build}_latest' to current build Group:${group_tag}."
  for i in $new_latest
  do
    $aws ec2 create-tags --resources $i --tags Key=Build,Value=${base_build}_latest
  done
else
  echo "No matching tags for Group:'${group_tag}' found."
  exit 1
fi

