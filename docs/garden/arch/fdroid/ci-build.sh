#!/bin/bash
# The fdroiddata "fdroid build" CI job, for one app, in its own image.
set -e
APP="${APP:?}"; VC="${VC:?}"
export ANDROID_HOME=/opt/android-sdk
source /etc/profile.d/bsenv.sh
apt-get update -qq >/dev/null && apt-get install -y -qq sudo openjdk-21-jdk-headless >/dev/null
update-alternatives --set java /usr/lib/jvm/java-21-openjdk-amd64/bin/java
sdkmanager "platform-tools" "build-tools;31.0.0" >/dev/null
rm -rf $fdroidserver && mkdir $fdroidserver
git ls-remote https://gitlab.com/fdroid/fdroidserver.git master
curl --silent https://gitlab.com/fdroid/fdroidserver/-/archive/master/fdroidserver-master.tar.gz | tar -xz --directory=$fdroidserver --strip-components=1
export PATH="$fdroidserver:$PATH" PYTHONPATH="$fdroidserver:$fdroidserver/examples" PYTHONUNBUFFERED=true serverwebroot=/tmp
git -C $home_vagrant/gradlew-fdroid pull -q || true
cd /work
echo "== fdroid lint"; fdroid lint $APP && echo "lint: OK"
echo "== fdroid rewritemeta (must be a no-op)"; cp metadata/$APP.yml /tmp/before.yml; fdroid rewritemeta $APP; diff /tmp/before.yml metadata/$APP.yml && echo "rewritemeta: no change"
for d in logs tmp unsigned build $home_vagrant/.android $home_vagrant/.gradle $home_vagrant/metadata; do mkdir -p $d; chown -R vagrant $d; done
ln -sfn /work/tmp $home_vagrant/tmp
cp -R /work/build $home_vagrant/build; cp metadata/$APP.yml $home_vagrant/metadata/
chown -R vagrant $home_vagrant /work
cd $home_vagrant
fdroid="sudo --preserve-env --user vagrant env PATH=$fdroidserver:$PATH env PYTHONPATH=$fdroidserver:$fdroidserver/examples env PYTHONUNBUFFERED=true env TERM=$TERM env HOME=$home_vagrant fdroid"
chown -R vagrant $home_vagrant/.cache 2>/dev/null || true
echo "== fdroid fetchsrclibs (clones the app source into build/, as CI does)"
ln -sfn /work $home_vagrant/fdroiddata
$fdroid fetchsrclibs $APP:$VC --verbose
rm -f $home_vagrant/fdroiddata
echo "== fdroid build"
(unset CI; $fdroid build --verbose --test --refresh-scanner --on-server --no-tarball $APP:$VC)
echo "BUILD RESULT: $?"
ls -la /work/tmp/*.apk 2>/dev/null || ls -la $home_vagrant/tmp/ 2>/dev/null
