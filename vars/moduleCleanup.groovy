#!/usr/bin/env groovy

/* 
 * cleanup modules from kubernetes
 */

def call(String namespace, String targetModule) {

  dir("${env.WORKSPACE}") {
    withCredentials([usernamePassword(credentialsId: 'jenkins-folio-rancher', variable: 'KUBECONFIG')]) {
      echo "$KUBECONFIG"
      echo "install deps"

      writeFile file: 'requirements.txt', text: libraryResource('org/folio/requirements.txt')
      sh "pip3 -q install wheel"
      sh "pip3 install -r requirements.txt"
      sh "pip3 freeze"
    }
  }
}
