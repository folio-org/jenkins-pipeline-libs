#!/usr/bin/env groovy

/* 
 * cleanup modules from kubernetes
 */

def call(String namespace, String targetModule) {

  dir("${env.WORKSPACE}") {
    withCredentials([file(credentialsId: 'jenkins-folio-rancher', variable: 'KUBECONFIG')]) {
      sh "mkdir -p /home/jenkins/.kube"
      sh "cp \$$KUBECONFIG /home/jenkins/.kube/config"
      sh "head -n 3 /home/jenkins/.kube/config"
      echo "install deps"

      writeFile file: 'requirements.txt', text: libraryResource('org/folio/requirements.txt')
      sh "pip3 -q install wheel"
      sh "pip3 install -r requirements.txt"
      sh "pip3 freeze"
    }
  }
}
