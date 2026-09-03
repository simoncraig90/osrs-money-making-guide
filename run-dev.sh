#!/bin/sh
# Launch RuneLite with this plugin loaded.
#
# Two obstacles the official RuneLite.app puts in the way, and how this gets past them:
#
# 1. Developer mode is only enabled when `runelite.launcher.version` is unset, and the
#    launcher always sets it. So the client is started directly, off the jars the
#    launcher already downloaded into ~/.runelite/repository2.
#
# 2. Side-loading is a dead end for anything using @Subscribe: the client's
#    PluginClassLoader does not implement ReflectUtil.PrivateLookupableClassLoader, so
#    event registration fails with LambdaConversionException. Instead the plugin goes on
#    the client's own classpath, built by `devJar` into net.runelite.client.plugins.* so
#    the core plugin scan finds it.
#
# This is a local convenience only. The real distribution route is the Plugin Hub.
set -e
cd "$(dirname "$0")"

REPO="$HOME/.runelite/repository2"
SIDELOAD="$HOME/.runelite/sideloaded-plugins"
VERSION=$(ls "$REPO" 2>/dev/null | sed -n 's/^client-\(.*\)\.jar$/\1/p' | sort -V | tail -1)

if [ -z "$VERSION" ]; then
	echo "No RuneLite client in $REPO -- run RuneLite.app once first." >&2
	exit 1
fi

./gradlew --quiet devJar
DEV_JAR=$(ls build/libs/*-dev.jar)

# A leftover side-loaded copy would be loaded a second time, under the broken loader.
rm -f "$SIDELOAD"/money-making-guide*.jar 2>/dev/null || true

# repository2 keeps old releases around, so pin to one version rather than globbing and
# letting classpath order decide.
CP=$(ls "$REPO"/*.jar | grep -vE 'client-|runelite-api-|injected-client-' | tr '\n' ':')
CP="$CP$REPO/client-$VERSION.jar:$REPO/injected-client-$VERSION.jar:$REPO/runelite-api-$VERSION-runtime.jar:$DEV_JAR"

echo "Starting RuneLite $VERSION with $DEV_JAR"

exec java \
	-ea \
	-Xmx768m -Xss2m -XX:CompileThreshold=1500 \
	--add-opens=java.base/java.net=ALL-UNNAMED \
	--add-opens=java.base/java.io=ALL-UNNAMED \
	--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED \
	-Dsun.java2d.metal=false -Dsun.java2d.opengl=true \
	-Dapple.awt.application.appearance=system \
	-cp "$CP" \
	net.runelite.client.RuneLite "$@"
