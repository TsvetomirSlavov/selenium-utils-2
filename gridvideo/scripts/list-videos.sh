#!/usr/bin/env bash

# arg 1 is the IP of the hub
if [ -z "$1" ]
  then
  	HUB_URL=localhost
  else
    HUB_URL=$1
fi

curl http://${HUB_URL}:4444/grid/admin/FrontEndServlet?command=list | python -c '
import json, sys
obj=json.load(sys.stdin)
for item in obj["videos"]:
	print item["sessionId"]
'