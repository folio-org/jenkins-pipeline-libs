#!/usr/bin/env groovy

/* 
 * cleanup modules from kubernetes
 */

def call(String namespace, String targetModule) {

  dir("${env.WORKSPACE}") {
    echo "clone tools"
    echo "install deps"

    writeFile file: 'requirements.txt', text: libraryResource('org/folio/requirements.txt')
    sh "pip3 -q install wheel"
    sh "pip3 -r install requirements.txt"
    sh "pip3 freeze"

  }
}
