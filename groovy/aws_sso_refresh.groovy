NODE_LABEL = ' yudongsi-mlt-ace'
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

firefox = '/home2/yudongsi/workspace/firefox/firefox'
if ('firefox' in params) {
    echo "firefox in params"
    if (params.firefox != '') {
        firefox = params.firefox
    }
}
echo "firefox: $firefox"

gd = '/home2/yudongsi/workspace/geckodriver'
if ('gd' in params) {
    echo "gd in params"
    if (params.gd != '') {
        gd = params.gd
    }
}
echo "gd: $gd"

env._FF = "$firefox"
env._GD = "$gd"

node(NODE_LABEL){
    stage("AWS SSO Refresh")
    {
        deleteDir()
        checkout scm
        try{
            withCredentials([usernamePassword(credentialsId: 'yudongsi_iap', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')])
            {        
                sh '''
                #!/usr/bin/env bash
                cd scripts/aws && bash refresh.sh ${_FF} ${_GD} $USERNAME $PASSWORD
                '''
            }
        }catch(err){
            echo err.getMessage()   
        }
    }    
    stage("Email"){
        if ("${debug}" == "true"){
            maillist="${debug_mail}"
        }else{
            maillist="yudong.si@intel.com;Chuanqi.Wang@intel.com;shufan.wu@intel.com;diwei.sun@intel.com"
        }
        if (currentBuild.currentResult == 'FAILURE'){
            emailext(
                subject: "AWS SSO Refresh JOB Build Failure",
                mimeType: "text/html",
                from: "pytorch_inductor_val@intel.com",
                to: maillist,
                body: 'Please double check in ${BUILD_URL}'
            )
        }
    }//email    
}