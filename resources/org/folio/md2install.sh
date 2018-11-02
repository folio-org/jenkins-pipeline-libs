#!/bin/bash
#
# Collect module descriptors from Stripes platform and generate a
# stripes-install json that can be posted to tenant okapi install endpoint.
#


# requirements
type jq >/dev/null 2>&1 || { echo >&2 "jq is required but it's not installed"; exit 1; }

if [ $# -lt 1 ]; then
    echo >&2 "Usage: $0 [--outputfile stripes-install.json] "
    exit 1
fi

while [[ $# -gt 0 ]]
do
   key="$1"

   case $key in
      -o|--outputfile)
         outputfile="$2"
         shift # past argument
         shift # past value
        ;;
      *) 
         mdPath="$key"
         shift
        ;;
   esac
done


modDescriptorList=$(ls $mdPath/*.json)
stripes_install_list="/tmp/stripes_install_list.$$"

# Initialize tenant module list
echo "[" > $stripes_install_list

for modDescriptor in $modDescriptorList 
do
   id=$(jq -r .id $modDescriptor)
   echo "{ \"id\":\"${id}\",\"action\":\"enable\"}," >> $stripes_install_list
done

# end stripes install list
sed -i -e '$s/,//2' $stripes_install_list
echo "]" >> $stripes_install_list

if [ -n "$outputfile" ]; then
  cat $stripes_install_list > "$outputfile"
else
  cat $stripes_install_list
fi

rm -f $stripes_install_list
