#!/bin/bash

# Signs every jar under <unsignedDir> with the Eclipse CBI jar signing service and writes the
# results to <signedDir>, preserving the directory layout.
#
# The signing service is only reachable from the eclipse.org machine, so the jars have to make a
# round trip. The remote only accepts a single plain command per connection - no globs, no shell
# expansion - so the commands sent here are the same ones the rest of the build already uses:
# 'mkdir -p <dir>', 'rm -rf <dir>', a plain scp, and one curl per jar.
#
# Signing bundle by bundle used to cost six connections per jar. Here the upload happens once, each
# jar costs a single curl, and the download and cleanup happen once at the end.
#
# The password is read from SSHPASS so that it never shows up in the process list or in a log.

set -eo pipefail

if [ $# -ne 5 ]; then
    echo "Usage: SSHPASS=<password> ./sign-jars.sh <host> <username> <knownHostsFile> <unsignedDir> <signedDir>"
    exit 1
fi

if [ -z "$SSHPASS" ]; then
    echo "The SSHPASS environment variable holding the eclipse.org password is not set"
    exit 1
fi

host=$1
username=$2
knownHosts=$3
unsignedDir=${4%/}
signedDir=${5%/}

signingService="https://cbi.eclipse.org/jarsigner/sign"

# keep the connection alive while a jar is being signed
sshOpts=(-o "UserKnownHostsFile=$knownHosts" -o ConnectTimeout=30 -o ServerAliveInterval=30 -o ServerAliveCountMax=20)

# a unique folder so that two builds signing at the same time cannot wipe each other's jars
remoteFolder="tmp/signing-$$-$(date +%Y%m%d%H%M%S)"
localTmp=$(mktemp -d)

remoteSsh() {
    sshpass -e ssh "${sshOpts[@]}" "$username@$host" -C "$1"
}

cleanup() {
    echo "Removing $remoteFolder from $host"
    remoteSsh "rm -rf $remoteFolder" || echo "WARNING: could not remove $remoteFolder from $host"
    rm -rf "$localTmp"
}
trap cleanup EXIT

# the jars to sign, as paths relative to the unsigned directory
jars=()
while IFS= read -r jar; do
    jars+=("$jar")
done < <(cd "$unsignedDir" && find . -type f -name '*.jar' | sed 's|^\./||' | sort)

if [ ${#jars[@]} -eq 0 ]; then
    echo "No jars to sign in $unsignedDir"
    exit 0
fi

echo "Signing ${#jars[@]} jars from $unsignedDir"

echo "Creating $remoteFolder on $host"
remoteSsh "mkdir -p $remoteFolder"

# scp creates the unsigned tree, but the signed one has to exist before curl can write into it
for dir in $(for jar in "${jars[@]}"; do dirname "$jar"; done | sort -u); do
    remoteSsh "mkdir -p $remoteFolder/signed/$dir"
done

echo "Uploading $unsignedDir"
sshpass -e scp "${sshOpts[@]}" -r "$unsignedDir" "$username@$host:$remoteFolder/unsigned"

# one curl per jar, the same invocation the previous script used, plus --fail so that an error
# response is reported instead of being written over the jar
for jar in "${jars[@]}"; do
    echo "Signing $jar"
    remoteSsh "curl -sS --fail --retry 3 --retry-delay 5 -X POST -o $remoteFolder/signed/$jar -F file=@$remoteFolder/unsigned/$jar $signingService"
done

echo "Downloading signed jars"
sshpass -e scp "${sshOpts[@]}" -r "$username@$host:$remoteFolder/signed" "$localTmp/signed"

# the service can answer with something that is not a jar, so do not hand that to the p2 publisher
for jar in "${jars[@]}"; do
    signed="$localTmp/signed/$jar"
    if [ ! -s "$signed" ]; then
        echo "ERROR: $jar came back empty or missing"
        exit 1
    fi
    if [ "$(head -c 2 "$signed")" != "PK" ]; then
        echo "ERROR: $jar came back as something other than a jar:"
        head -c 500 "$signed"
        exit 1
    fi
done

mkdir -p "$signedDir"
cp -R "$localTmp/signed/." "$signedDir/"
echo "Signed ${#jars[@]} jars into $signedDir"
