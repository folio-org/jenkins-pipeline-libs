#!/usr/bin/env groovy

/* 
 * cleanup modules from kubernetes
 */

def call(String scriptArgs) {

  dir("${env.WORKSPACE}") {
      // set up python environment
      writeFile file: 'requirements.txt', text: libraryResource('org/folio/requirements.txt')
      writeFile file: 'module-cleanup.py', text: libraryResource('org/folio/module-cleanup.py')
      sh "pip3 -q install wheel"
      sh "pip3 install -r requirements.txt"

      // run script
      withCredentials([usernamePassword(credentialsId: 'okapi-preview-superuser', passwordVariable: 'pass', usernameVariable: 'user')],
                      [$class: 'AmazonWebServicesCredentialsBinding',
                       accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                       credentialsId: 'jenkins-aws',
                       secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']) {
        echo "$user"
        sh "python3 module-cleanup.py ${scriptArgs} -u $user -p $pass"
     }
    }
  }
}
