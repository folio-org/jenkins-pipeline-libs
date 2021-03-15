#!/bin/bash

identifier=logs-$(date +%Y-%m-%d-%H%m%S)
mkdir /tmp/${identifier}

for module in "$@"
do
  echo "Collecting logs for $module"
  container=$(sudo docker ps -a | grep "${module}:" | awk '{print $1}')
  sudo docker logs $container >& /tmp/${identifier}/${module}.log
done

# include latest okapi log for good measure
if [ -f /var/log/folio/okapi/okapi.log ]; then 
  echo "Collect latest Okapi log"
  cp /var/log/folio/okapi/okapi.log /tmp/${identifier}
fi

cd /tmp
tar cf ${HOME}/${identifier}.tar ${identifier} 
gzip ${HOME}/${identifier}.tar
rm -rf /tmp/${identifier}

exit $?
