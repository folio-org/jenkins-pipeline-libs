#!/usr/bin/env groovy


/*
 *  git push via SSH step
 */


def call(Map config) { 
  withCredentials([sshUserPrivateKey(credentialsId: 'jenkins-github-sshkey', keyFileVariable: 'SSH_KEY')]) {
    withEnv(["GIT_SSH_COMMAND=ssh -i $SSH_KEY -o StrictHostKeyChecking=no"]) {
      sh "git push ssh://git@github.com/folio-org/${config.origin} $config.branch"
    }
  }
}

