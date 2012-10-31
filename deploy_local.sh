#!/bin/bash
# see DCC-499
#
# usage: ./deploy_local.sh my.dcc.server
#
# notes:
# - this script is based on former https://wiki.oicr.on.ca/display/DCCSOFT/Standard+operating+procedures#Standardoperatingprocedures-SOPforDeployingtheserver (which also links to this script now)
# - assumptions:
#   - must be in data-submission
#   - must have checked out wanted branch
#   - must have run "npm install" in ./client
#   - tests must run (else jar creation will fail)
#   - on the remote server, /var/lib/hdfs/log and /var/lib/hdfs/realm.ini already exist (the latter is the reference file)
# - convention: server expects client files under ../client (content should be that of ./client/public after build with brunch). this script takes care of building the appropriate directoy structure.

# ===========================================================================

dev_dir="."
dev_server_dir="${dev_dir?}/server"
dev_target_dir="${dev_server_dir?}/target"
dev_client_dir="${dev_dir?}/client"
dev_public_dir="${dev_client_dir?}/public"

dev_server_deploy_script_name="deploy_server.sh"
dev_server_deploy_script="${dev_dir?}/${dev_server_deploy_script_name?}"
parent_pom_file="${dev_dir?}/pom.xml"
server_pom_file="${dev_server_dir?}/pom.xml"

# ===========================================================================
# basic checks

function get_xml_value() {
 file=${1?}
 path=${2?}
 xpath -e "${path?}" "${file?}" 2>&-
}

# check mvn
hash mvn 2>&- || { echo "ERROR: mvn is not available"; exit 1; }

# check xpath
hash xpath 2>&- || { echo "ERROR: xpath is not available"; exit 1; }

# check in data-submission directory
[ -f "${parent_pom_file?}" ] && [ "$(get_xml_value ${parent_pom_file?} '//project/artifactId/text()')" == "dcc-parent" ] || { echo "ERROR: must be in data-submission dir"; exit 1; }

# ===========================================================================

server=${1?}
echo "server=\"${server?}\""

timestamp=$(date "+%y%m%d%H%M%S")
echo "timestamp=\"${timestamp?}\""

artifact_id=$(get_xml_value ${server_pom_file?} '//project/artifactId/text()')
echo "artifact_id=\"${artifact_id?}\""
version=$(get_xml_value ${server_pom_file?} '//project/parent/version/text()')
echo "version=\"${version?}\""
jar_file_name="${artifact_id?}-${version?}.jar"
jar_file="${dev_target_dir?}/${jar_file_name?}"
echo "jar_file=\"${jar_file?}\""

# ===========================================================================

local_working_dir_name="dist_${timestamp?}"
local_working_dir="./${local_working_dir_name?}" # careful about changing that (rm -rf further down)
echo "local_working_dir=\"${local_working_dir?}\""

local_server_dir="${local_working_dir?}/server"
local_client_dir="${local_working_dir?}/client"

# ===========================================================================

# "build" project (mostly manual for now)
echo && read -p "create dist? [press key]"
mkdir -p ${local_working_dir?} ${local_server_dir?} ${local_working_dir?}/log && { rm -rf "${local_client_dir?}" 2>&- || : ; }
{ cd ${dev_server_dir?} && mvn assembly:assembly && cd .. ; } || { echo "ERROR: failed to build project (server)"; exit 1; } # critical
{ cd "${dev_client_dir?}" && brunch build && cd .. ; } || { echo "ERROR: failed to build project (client)"; exit 1; } # critical
cp "${jar_file?}" "${local_server_dir?}/"
cp -r "${dev_public_dir?}" "${local_client_dir?}"
cp "${dev_server_deploy_script?}" "${local_working_dir?}/"

echo -e "content:\n"
find ${local_working_dir?}
echo

# ===========================================================================
# copy files to server

echo && read -p "copy to server - please enter OICR username: " username
echo "username=\"${username?}\""

remote_working_dir="/tmp/${local_working_dir_name?}"
echo "remote_working_dir=\"${remote_working_dir?}\""

echo "scp ${local_working_dir?} to server"
scp -r ${local_working_dir?} ${username?}@${server?}:${remote_working_dir?} # must use /tmp for now (permission problems)
rm -rf ${local_working_dir?} && echo "${local_working_dir?} deleted" # remove working directory

# ===========================================================================
# start server remotely

hdfs_dir="/var/lib/hdfs"
remote_realm_file="${hdfs_dir?}/realm.ini" # must exist already
remote_log_dir="${hdfs_dir?}/log" # must exist already
remote_dir="${hdfs_dir?}/dcc" # must be absolute path
remote_server_dir="${remote_dir?}/server"
remote_client_dir="${remote_dir?}/client"
echo "remote_server_dir=\"${remote_server_dir?}\""
echo "remote_client_dir=\"${remote_client_dir?}\""
echo "remote_log_dir=\"${remote_log_dir?}\""

log_base="${artifact_id?}-${timestamp?}"
log_file="${remote_log_dir?}/${log_base?}.log"

server_command="./${dev_server_deploy_script_name?} ${server?} ${jar_file?} ${log_file?}"
echo "server_command=\"${server_command?}\""
sudo_command="sudo -u hdfs -i \"${server_command?}\""
echo "sudo_command=\"${sudo_command?}\""

echo && read -p "start server? [press key]"
#echo "enter password to ssh to start server" && ssh ${username?}@${server?} "${sudo_command?}"
echo "# WIP (must nohup/screen), for now use screen (as hdfs) and roughly:"
echo " cp -r ${remote_working_dir?} ${remote_dir?} # consider backing up existing ${remote_dir?} first"
echo " cp ${remote_realm_file?} ${remote_server_dir?}/"
echo " cd ${remote_server_dir?}"
echo " java -cp ${jar_file?} org.icgc.dcc.Main prod > ${log_file?} 2>&1"
echo " tail -f ${log_file?} # elsewhere"

# ===========================================================================

exit

