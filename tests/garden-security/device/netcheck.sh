# Network preflight inside a disposable guest: if this fails, every repository case
# would fail for a reason that is not the product's. gate.sh then stops with
# ENVIRONMENT BLOCKED rather than printing dozens of misleading FAILs.
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
for h in mirror.archlinuxarm.org blackarch.org; do
  getent hosts "$h" > /dev/null || { echo "NETWORK FAIL: cannot resolve $h"; exit 1; }
done
c=$(curl -sS -m 30 -o /dev/null -w '%{http_code}' https://blackarch.org/blackarch/blackarch/os/aarch64/blackarch.db.sig) || { echo "NETWORK FAIL: curl blackarch.org"; exit 1; }
[ "$c" = 200 ] || { echo "NETWORK FAIL: blackarch.db.sig HTTP $c"; exit 1; }
echo "network-ok"
