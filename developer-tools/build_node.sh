#! /bin/bash
set -e
set -x

ELECTRON_VERSION=12.0.2
NODE_ADDON_API_VERSION=3.1.0

NUM_PROCS=$(getconf _NPROCESSORS_ONLN)
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
PLATFORM=`./depends/config.guess`
PLATFORM_VENDOR=`./depends/config.guess | cut -d- -f2`
PLATFORM_OS=`./depends/config.guess | cut -d- -f3`

if test -f developer-tools/private.conf; then
    source developer-tools/private.conf
fi

export CXXFLAGS="-fPIC -fdata-sections -ffunction-sections -fomit-frame-pointer -DNAPI_VERSION=5 -DDJINNI_NODEJS -D_HAS_EXCEPTIONS=1"
export CFLAGS=${CXXFLAGS}
if [ ${PLATFORM_VENDOR} = "apple" ]; then
    export LDFLAGS="-fPIC -Bsymbolic -Wl,-undefined -Wl,dynamic_lookup"
else
    if [ ${PLATFORM_OS} = "mingw32" ] ||  [ ${PLATFORM_OS} = "mingw64" ]; then
        export LDFLAGS="-fPIC -Bsymbolic -L${DIR}/../build_node -lnode -Wl,--gc-sections"
        export EXTRA_CXX_FLAGS="-DNODE_HOST_BINARY=node.exe -DUSING_UV_SHARED=1 -DUSING_V8_SHARED=1 -DV8_DEPRECATION_WARNINGS=1 -DV8_DEPRECATION_WARNINGS -DV8_IMMINENT_DEPRECATION_WARNINGS -DWIN32 -D_CRT_SECURE_NO_DEPRECATE -D_CRT_NONSTDC_NO_DEPRECATE -DBUILDING_NODE_EXTENSION -D_WINDLL"
    else
        export LDFLAGS="-fPIC -Bsymbolic -Wl,--gc-sections"
    fi
fi


cd depends
make NO_QT=1 NO_UPNP=1 EXTRA_PACKAGES='qrencode' -j ${NUM_PROCS}
cd ..

mkdir build_node | true
cd build_node

wget -c https://atom.io/download/electron/v${ELECTRON_VERSION}/iojs-v${ELECTRON_VERSION}.tar.gz
wget -c https://registry.npmjs.org/node-addon-api/-/node-addon-api-${NODE_ADDON_API_VERSION}.tgz

mkdir electron-${ELECTRON_VERSION} | true
tar -xvf iojs-v${ELECTRON_VERSION}.tar.gz -C electron-${ELECTRON_VERSION}
mkdir node-addon-api-${NODE_ADDON_API_VERSION} | true
tar -xvf node-addon-api-${NODE_ADDON_API_VERSION}.tgz -C node-addon-api-${NODE_ADDON_API_VERSION}

export CXXFLAGS="${CXXFLAGS} -I${DIR}/../build_node/electron-${ELECTRON_VERSION}/node_headers/include/node/ -I${DIR}/../build_node/node-addon-api-${NODE_ADDON_API_VERSION}/package/ ${EXTRA_CXX_FLAGS} -O2"
export CFLAGS=${CXXFLAGS}

if [ ${PLATFORM_OS} = "mingw32" ] || [ ${PLATFORM_OS} = "mingw64" ]; then
    dlltool -d ${DIR}/node/node.def -y libnode.a
fi

../autogen.sh
#NB! Important to pass exmpty config.site here; otherwise msys2 has a default config.site that messes with (overrides) our own from the depends directory...
CONFIG_SITE='' ../configure --prefix=$PWD/../depends/${PLATFORM} --disable-bench --disable-tests --disable-gui-tests --disable-man --disable-zmq --without-utils  --without-daemon --with-node-js-libs --with-qrencode --with-gui=no
make V=1 -j ${NUM_PROCS}
cd ..

if test -f "build_node/src/.libs/lib_unity_node_js-0.dll"; then
    cp build_node/src/.libs/lib_unity_node_js-0.dll src/frontend/electron_sample/libgulden_unity_node_js.node
    cp build_node/src/.libs/lib_unity_node_js-0.dll src/frontend/electron_vue/src/unity/lib_unity.node
    cp build_node/src/.libs/lib_unity_node_js-0.dll src/frontend/lite/src/unity/lib_unity.node
elif test -f "build_node/src/.libs/lib_unity_node_js.so"; then
    cp build_node/src/.libs/lib_unity_node_js.so src/frontend/electron_sample/libgulden_unity_node_js.node
    cp build_node/src/.libs/lib_unity_node_js.so src/frontend/electron_vue/src/unity/lib_unity.node
    cp build_node/src/.libs/lib_unity_node_js.so src/frontend/lite/src/unity/lib_unity.node
fi

