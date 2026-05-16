#!/bin/sh
# Athena2ME Docker build entrypoint — modes for docker-compose / `docker run`.
# Usage: first arg is the mode; remaining args go to `ant` when mode is `ant`.
# Env: BUILD_PROFILE=dev|release (passed to Ant as -Dbuild.profile=… for `preproc`).

set -e
cd /app

ensure_proguard() {
  mkdir -p tools
  if [ ! -f tools/proguard.jar ]; then
    cp /opt/proguard/proguard.jar tools/proguard.jar
  fi
}

MODE="${1:-all}"
if [ "$#" -gt 0 ]; then
  shift
fi

case "$MODE" in
  preproc)
    exec ant "-Dbuild.profile=${BUILD_PROFILE:-dev}" preproc
    ;;
  preproc-check)
    npm install --prefix tools/preproc
    npm test --prefix tools/preproc
    exec node tools/preproc/bin/a2m-preproc.mjs --check
    ;;
  compile)
    exec ant compile
    ;;
  jar)
    ensure_proguard
    exec ant jar-only
    ;;
  jar-release)
    ensure_proguard
    exec ant jar-release
    ;;
  all-preproc)
    ensure_proguard
    exec ant "-Dbuild.profile=${BUILD_PROFILE:-dev}" all-preproc
    ;;
  all)
    ensure_proguard
    exec ant all
    ;;
  release)
    ensure_proguard
    exec ant release
    ;;
  clean)
    exec ant clean
    ;;
  ant)
    ensure_proguard
    exec ant "$@"
    ;;
  shell|bash|sh)
    exec /bin/sh
    ;;
  help|-h|--help)
    echo "Athena2ME build modes (first argument):"
    echo "  preproc         — ant preproc (Node ES6→ES5 into build/res/, BUILD_PROFILE=dev|release)"
    echo "  preproc-check   — npm test + a2m-preproc --check (no JAR)"
    echo "  compile         — ant compile (Java only)"
    echo "  jar             — ant jar-only (needs compile; dev resources from res/)"
    echo "  jar-release     — ant jar-release (compile + preproc + slim JAR)"
    echo "  all-preproc     — ant all-preproc (dev resource set from build/res/ after preproc + preverify + jad)"
    echo "  all             — ant all (default: compile + jar + preverify + jad)"
    echo "  release         — ant release (strip-features + slim + ProGuard + jad)"
    echo "  clean           — ant clean"
    echo "  ant <targets>   — pass through to ant (ensures ProGuard jar in tools/)"
    echo "  shell           — interactive shell in /app"
    exit 0
    ;;
  *)
    echo "athena2me-entrypoint: unknown mode '$MODE'. Try: help" >&2
    exit 1
    ;;
esac
