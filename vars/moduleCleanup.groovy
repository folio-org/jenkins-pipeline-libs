#!/usr/bin/env groovy

/* 
 * cleanup modules from kubernetes
 */

def call(String scriptArgs) {

  dir("${env.WORKSPACE}") {
    withCredentials([file(credentialsId: 'jenkins-folio-rancher', variable: 'KUBECONFIG')]) {
      sh "mkdir -p /home/jenkins/.kube"
      sh "cp $KUBECONFIG /home/jenkins/.kube/config"

      // set up python environment
      writeFile file: 'requirements.txt', text: libraryResource('org/folio/requirements.txt')
      writeFile file: 'module-cleanup.py', text: libraryResource('org/folio/module-cleanup.py')
      sh "pip3 -q install wheel"
      sh "pip3 install -r requirements.txt"

      // run script
      withCredentials([usernamePassword(credentialsId: 'okapi-default-superuser', passwordVariable: 'pass', usernameVariable: 'user')]) {
        sh "python3 preview-tenant-cleanup.py ${scriptArgs} -u $user -p $pass"
     }
    }
  }
}
