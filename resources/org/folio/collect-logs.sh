#!/bin/bash

identifier=logs-$(date +%Y-%m-%d-%H%m%S)
mkdir /tmp/${identifier}

for module in "$@"
do
  container=$(sudo docker ps -a | grep "${module}:" | awk '{print $1}')
  sudo docker logs $container >& /tmp/${identifier}/${module}.log
done

cd /tmp
tar cf ${HOME}/${identifier}.tar ${identifier} 
gzip ${HOME}/${identifier}.tar
rm -rf /tmp/${identifier}

echo "${identifier}.tar.gz"

