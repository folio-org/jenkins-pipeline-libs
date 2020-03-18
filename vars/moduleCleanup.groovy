#!/usr/bin/env groovy

/* 
 * cleanup modules from kubernetes
 */

def call(String namespace, String targetModule) {

  dir("${env.WORKSPACE}") {
    withCredentials([file(credentialsId: 'jenkins-folio-rancher', variable: 'KUBECONFIG')]) {
      sh "mkdir -p /home/jenkins/.kube"
      sh "cp $KUBECONFIG /home/jenkins/.kube/config"
      sh "head -n 3 /home/jenkins/.kube/config"
      echo "install deps"

      // set up python environment
      writeFile file: 'requirements.txt', text: libraryResource('org/folio/requirements.txt')
      writeFile file: 'module-cleanup.py', text: libraryResource('org/folio/module-cleanup.py')
      sh "pip3 -q install wheel"
      sh "pip3 install -r requirements.txt"
      sh "pip3 freeze"

      // run script
      sh "python3 module-cleanup.py --dry-run -r 3 -s 2 -u $OKAPI_USER -p $OKAPI_PASS -o https://okapi-default.ci.folio.org"
    }
  }
}
