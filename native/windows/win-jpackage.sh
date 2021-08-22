#!/bin/bash

## Expected environment, passed from GitHub secrets:
# https://docs.github.com/en/free-pro-team@latest/actions/reference/encrypted-secrets
# SIGN_CERT       PKCS12 certificate keystore used for code signing, base64 encoded
# SIGN_STOREPASS  Password for that keystore
# SIGN_TSA        URL of Time Stamping Authority to use

set -Eeo pipefail

# Don't show one time passwords
set +x

if [ -z "${1-}" ]
then
    echo "Usage: $0 josm_revision"
    exit 1
fi

echo "Building JOSM Windows Installer packages"

mkdir app

if [ -z "$SIGN_CERT" ] || [ -z "$SIGN_STOREPASS" ] || [ -z "$SIGN_TSA" ]
then
    echo "SIGN_CERT, SIGN_STOREPASS and SIGN_TSA are not set in the environment."
    echo "A JOSM.exe and JOSM.msi will be created but not signed."
    SIGNAPP=false
else
    SIGNAPP=true
fi

set -u

JPACKAGEOPTIONS=""

echo "Building EXE and MSI"
for type in exe msi
do
    jpackage $JPACKAGEOPTIONS -n "JOSM" --input dist --main-jar josm-custom.jar \
    --main-class org.openstreetmap.josm.gui.MainApplication \
    --icon ./native/windows/logo.ico --type $type --dest app \
    --java-options "--add-modules java.scripting,java.sql,javafx.controls,javafx.media,javafx.swing,javafx.web" \
    --java-options "--add-exports=java.base/sun.security.action=ALL-UNNAMED" \
    --java-options "--add-exports=java.desktop/com.sun.imageio.plugins.jpeg=ALL-UNNAMED" \
    --java-options "--add-exports=java.desktop/com.sun.imageio.spi=ALL-UNNAMED" \
    --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" \
    --java-options "--add-opens=java.base/java.nio=ALL-UNNAMED" \
    --java-options "--add-opens=java.base/jdk.internal.loader=ALL-UNNAMED" \
    --java-options "--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED" \
    --java-options "--add-opens=java.desktop/javax.imageio.spi=ALL-UNNAMED" \
    --java-options "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED" \
    --java-options "--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED" \
    --app-version "1.5.$1" \
    --copyright "JOSM, and all its integral parts, are released under the GNU General Public License v2 or later" \
    --vendor "JOSM" \
    --win-per-user-install \
    --win-dir-chooser \
    --win-shortcut \
    --win-menu \
    --file-associations native/file-associations/bz2.properties \
    --file-associations native/file-associations/geojson.properties \
    --file-associations native/file-associations/gpx.properties \
    --file-associations native/file-associations/gz.properties \
    --file-associations native/file-associations/jos.properties \
    --file-associations native/file-associations/joz.properties \
    --file-associations native/file-associations/osm.properties \
    --file-associations native/file-associations/zip.properties \
    --add-launcher HWConsole=native/windows/MLConsole.properties \
    --add-modules java.base,java.datatransfer,java.desktop,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.sql,java.transaction.xa,java.xml,jdk.crypto.ec,jdk.jfr,jdk.jsobject,jdk.unsupported,jdk.unsupported.desktop,jdk.xml.dom,javafx.controls,javafx.media,javafx.swing,javafx.web
done

mv app/JOSM-1.5.$1.exe app/JOSM.exe
mv app/JOSM-1.5.$1.msi app/JOSM.msi

# Workaround to https://bugs.openjdk.java.net/browse/JDK-8261845
# to remove after we switch to Java 17+ for jpackage builds
chmod u+w app/JOSM.exe

echo "Building done."

if $SIGNAPP; then
    CERTIFICATE_P12=certificate.p12
    echo "$SIGN_CERT" | base64 --decode > $CERTIFICATE_P12
    for ext in exe msi
    do
        signtool.exe sign //f $CERTIFICATE_P12 //d "Java OpenStreetMap Editor" //du "https://josm.openstreetmap.de" //p "$SIGN_STOREPASS" //v //fd SHA256 //tr "$SIGN_TSA" //td SHA256 "app/JOSM.$ext"
    done
    rm $CERTIFICATE_P12
fi
