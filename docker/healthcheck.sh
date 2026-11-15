#!/bin/bash
set -eu
# bash /dev/tcp avoids installing curl in the runtime image.
exec 3<>"/dev/tcp/127.0.0.1/${SERVER_PORT:-8888}"
printf 'GET /actuator/health HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3
IFS= read -r -t 4 status <&3
[[ "$status" == *" 200 "* ]]
