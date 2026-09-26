export DEBIAN_FRONTEND=noninteractive
apt-get update -q 2>&1 | tail -2
apt-get -y -o Dpkg::Options::=--force-confold full-upgrade 2>&1 | grep -E "rust-coreutils|Setting up sudo|Security violation|error|Errors|^ sudo" | head -12
echo "--- after upgrade"
echo "rust-coreutils $(dpkg-query -W -f='${Version}' rust-coreutils)  sudo $(dpkg-query -W -f='${Status} ${Version}' sudo)"
echo "dpkg --audit: [$(dpkg --audit 2>&1 | head -3)]"
