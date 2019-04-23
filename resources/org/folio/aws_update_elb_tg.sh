#!/bin/bash

# Update AWS elb target group based on tag:Build

region=us-east-1
vpc_id=vpc-f9c16d9d

while [[ $# -gt 0 ]]
do
   arg="$1"

   case $arg in
      --include-edge)
         include_edge=true
         shift # past argument
        ;;
      *) 
         build="$arg"
         shift
        ;;
   esac
done

if [ -z "$build" ]; then
  echo "Value of tag:Build required"
  exit 1
fi

export PATH=/usr/local/bin:$PATH

# get current 'stable'
current_instance=$(aws --output text --region $region ec2 describe-instances --filters \
                     "Name=tag:Build,Values=${build}" \
                     'Name=instance-state-name,Values=running' \
                      --query 'Reservations[*].Instances[*].[InstanceId]')

echo "Instance ID of build: $current_instance"

tg_basename=$(sed -e 's/_/-/g' <<< $build)

# create stripes 'stable' target group
stripes_tg=$(aws --output text --region $region elbv2 create-target-group \
                 --name ${tg_basename}-stripes --protocol HTTP --port 80 --vpc-id $vpc_id \
                 --health-check-protocol HTTP --health-check-path / \
                 --health-check-enabled --target-type instance \
                 --query 'TargetGroups[*].[TargetGroupArn]' ) 

okapi_tg=$(aws --output text --region $region elbv2 create-target-group \
                 --name ${tg_basename}-okapi --protocol HTTP --port 9130 --vpc-id $vpc_id \
                 --health-check-protocol HTTP --health-check-path /_/proxy/health \
                 --health-check-enabled --target-type instance \
                 --query 'TargetGroups[*].[TargetGroupArn]' ) 

all_tg=("$stripes_tg" "$okapi_tg")


if [ "$include_edge" ]; then 
  edge_tg=$(aws --output text --region $region elbv2 create-target-group \
                 --name ${tg_basename}-edge --protocol HTTP --port 8000 --vpc-id $vpc_id \
                 --health-check-protocol HTTP --health-check-path / \
                 --health-check-enabled --target-type instance \
                 --query 'TargetGroups[*].[TargetGroupArn]' ) 
  all_tg+=("$edge_tg")
fi

for tg in "${all_tg[@]}"
do
  echo "ARN of target group: $tg"

  # get existing instance in target group 
  previous_instance=$(aws --output text --region $region elbv2 describe-target-health \
                        --target-group-arn $tg  \
                        --query 'TargetHealthDescriptions[*].Target.Id')
                    
  # deregister existing instance from target group 
  if [ -n "$previous_instance" ]; then
    aws --output text --region $region elbv2 deregister-targets \
        --target-group-arn $tg --targets Id=${previous_instance}
  fi

  # register new target
  aws --output text --region $region elbv2 register-targets \
      --target-group-arn $tg --targets Id=${current_instance}
done 


