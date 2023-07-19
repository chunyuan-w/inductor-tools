export LD_PRELOAD=${CONDA_PREFIX:-"$(dirname $(which conda))/../"}/lib/libiomp5.so:${CONDA_PREFIX:-"$(dirname $(which conda))/../"}/lib/libjemalloc.so
export MALLOC_CONF="oversize_threshold:1,background_thread:true,metadata_thp:auto,dirty_decay_ms:-1,muzzy_decay_ms:-1"
export KMP_AFFINITY=granularity=fine,compact,1,0
export KMP_BLOCKTIME=1

# perf_mode="--performance"
perf_mode="--accuracy"

CORES=$(lscpu | grep Core | awk '{print $4}')
# CORES=1
end_core=$(expr $CORES - 1)
export OMP_NUM_THREADS=$CORES

thread=""
if [[ $CORES == 1 ]]; then
    echo "Testing with single thread."
    thread="--threads 1 "
fi


SUITE=${1:-huggingface}
MODEL=${2:-GoogleFnet}
DT=${3:-float32}
CHANNELS=${4:-first}
SHAPE=${5:-static}
BS=${6:-0}
MODE=${7:-inference}
# default / cpp
WRAPPER=${8:-default}

Mode_extra="--inference "
if [[ $MODE == "training" ]]; then
    echo "Testing with training mode."
    Mode_extra="--training "
fi

Shape_extra=""
if [[ $SHAPE == "dynamic" ]]; then
    echo "Testing with dynamic shapes."
    Shape_extra="--dynamic-shapes "
fi

Wrapper_extra=""
if [[ $WRAPPER == "cpp" ]]; then
    echo "Testing with cpp wrapper."
    Wrapper_extra="--cpp-wrapper "
fi

if [[ $SHAPE == "static" ]]; then
    export TORCHINDUCTOR_FREEZING=1
fi

Channels_extra=""
if [[ ${CHANNELS} == "last" ]]; then
    Channels_extra="--channels-last "
fi

BS_extra=""
if [[ ${BS} -gt 0 ]]; then
    BS_extra="--batch_size=${BS} "
fi

numactl -C 0-${end_core} --membind=0 python benchmarks/dynamo/${SUITE}.py ${perf_mode} --${DT} -dcpu -n50 --no-skip --dashboard --only "${MODEL}" ${Channels_extra} ${BS_extra} ${Shape_extra} ${Mode_extra} ${Wrapper_extra} ${thread} --backend=inductor  --output=/tmp/inductor_single_test.csv

cat /tmp/inductor_single_test.csv && rm /tmp/inductor_single_test.csv
