#!/bin/bash

okapi_container=$(sudo docker ps -a | grep "okapi:" | awk '{print $1}')
sudo docker logs $okapi_container >& $HOME/okapi.log
bzip2 $HOME/okapi.log

exit 0
