NODE_LABEL = 'yudongsi-mlt-ace'
if ('NODE_LABEL' in params) {
    echo "NODE_LABEL in params"
    if (params.NODE_LABEL != '') {
        NODE_LABEL = params.NODE_LABEL
    }
}
echo "NODE_LABEL: $NODE_LABEL"


debug = 'True'
if ('debug' in params) {
    echo "debug in params"
    if (params.debug != '') {
        debug = params.debug
    }
}
echo "debug: $debug"

debug_mail = 'yudong.si@intel.com'
if ('debug_mail' in params) {
    echo "debug_mail in params"
    if (params.debug_mail != '') {
        debug_mail = params.debug_mail
    }
}
echo "debug_mail: $debug_mail"

aws_hostname = 'ec2-34-207-213-120.compute-1.amazonaws.com'
if ('aws_hostname' in params) {
    echo "aws_hostname in params"
    if (params.aws_hostname != '') {
        aws_hostname = params.aws_hostname
    }
}
echo "aws_hostname: $aws_hostname"


target = 'WW17.2'
if ('target' in params) {
    echo "target in params"
    if (params.target != '') {
        target = params.target
    }
}
echo "target: $target"

precision = 'float32'
if ('precision' in params) {
    echo "precision in params"
    if (params.precision != '') {
        precision = params.precision
    }
}
echo "precision: $precision"

TORCH_REPO = 'https://github.com/pytorch/pytorch.git'
if ('TORCH_REPO' in params) {
    echo "TORCH_REPO in params"
    if (params.TORCH_REPO != '') {
        TORCH_REPO = params.TORCH_REPO
    }
}
echo "TORCH_REPO: $TORCH_REPO"

TORCH_BRANCH= 'nightly'
if ('TORCH_BRANCH' in params) {
    echo "TORCH_BRANCH in params"
    if (params.TORCH_BRANCH != '') {
        TORCH_BRANCH = params.TORCH_BRANCH
    }
}
echo "TORCH_BRANCH: $TORCH_BRANCH"

TORCH_COMMIT= 'nightly'
if ('TORCH_COMMIT' in params) {
    echo "TORCH_COMMIT in params"
    if (params.TORCH_COMMIT != '') {
        TORCH_COMMIT = params.TORCH_COMMIT
    }
}
echo "TORCH_COMMIT: $TORCH_COMMIT"

DYNAMO_BENCH= 'fea73cb'
if ('DYNAMO_BENCH' in params) {
    echo "DYNAMO_BENCH in params"
    if (params.DYNAMO_BENCH != '') {
        DYNAMO_BENCH = params.DYNAMO_BENCH
    }
}
echo "DYNAMO_BENCH: $DYNAMO_BENCH"

env._name = "$aws_hostname"
env._target = "$target"
env._precision = "$precision"
env._TORCH_REPO = "$TORCH_REPO"
env._TORCH_BRANCH = "$TORCH_BRANCH"
env._TORCH_COMMIT = "$TORCH_COMMIT"
env._DYNAMO_BENCH = "$DYNAMO_BENCH"
env._archive_dir = '/home2/yudongsi/inductor_dashboard/Train'

env._time = new Date().format('yyyy-MM-dd')
println(env._time)

node(NODE_LABEL){
    stage("login instance && launch benchmark") {
        deleteDir()
        retry(3){
            sh '''
            #!/usr/bin/env bash
            cd /home2/yudongsi/
            cat .ssh/config
            sed -i -e "/Host icx-train/{n;s/.*/    Hostname ${_name}/}" .ssh/config
            cat .ssh/config
            ssh ubuntu@icx-train "nohup bash entrance_train.sh ${_target} ${_precision} ${_TORCH_REPO} ${_TORCH_BRANCH} ${_TORCH_COMMIT} ${_DYNAMO_BENCH} &>/dev/null &" &
            '''
        }
    }
    stage("acquire logs"){
        retry(3){
            sh '''
            #!/usr/bin/env bash
            set +e
            for ((i=1;i<=5;i++))
            do
                ssh ubuntu@icx-train "test -f /home/ubuntu/docker/finished_train.txt"
                if [ $? -eq 0 ]; then
                    if [ -d ${_archive_dir}/${_target} ]; then
                        rm -rf ${_archive_dir}/${_target}
                    fi
                    mkdir -p ${_archive_dir}/${_target}
                    scp -r ubuntu@icx-train:/home/ubuntu/docker/inductor_log ${_archive_dir}/${_target}
                    break
                else
                    sleep 30m
                    echo $t
                fi
            done
            '''
        }
    }

    stage("generate report"){
        retry(3){
            sh '''
            #!/usr/bin/env bash          
            cd ${_archive_dir} && python report_train.py -t ${_target}
            '''
        }
    }    

    stage('archiveArtifacts') {
            sh '''
            #!/usr/bin/env bash
            cp -r ${_archive_dir}/${_target}/inductor_log ${WORKSPACE}
            '''        
        archiveArtifacts artifacts: "**/inductor_log/**", fingerprint: true
    }

    stage("Sent Email"){
        if ("${debug}" == "true"){
            maillist="${debug_mail}"
        }else{
            maillist="Chuanqi.Wang@intel.com;guobing.chen@intel.com;beilei.zheng@intel.com;xiaobing.zhang@intel.com;xuan.liao@intel.com;Chunyuan.Wu@intel.com;Haozhe.Zhu@intel.com;weiwen.xia@intel.com;jiong.gong@intel.com;eikan.wang@intel.com;fan.zhao@intel.com;shufan.wu@intel.com;weizhuo.zhang@intel.com;yudong.si@intel.com;diwei.sun@intel.com"
        }
        if (fileExists("${WORKSPACE}/inductor_log/inductor_model_training_bench.html") == true){
            emailext(
                subject: "Torchinductor Training Benchmark Report (AWS)",
                mimeType: "text/html",
                attachmentsPattern: "**/inductor_log/*.xlsx",
                from: "pytorch_inductor_val@intel.com",
                to: maillist,
                body: '${FILE,path="inductor_log/inductor_model_training_bench.html"}'
            )
        }else{
            emailext(
                subject: "Failure occurs in Torchinductor Training Benchmark (AWS)",
                mimeType: "text/html",
                from: "pytorch_inductor_val@intel.com",
                to: maillist,
                body: 'Job build failed, please double check in ${BUILD_URL}'
            )
        }
    }//email
}