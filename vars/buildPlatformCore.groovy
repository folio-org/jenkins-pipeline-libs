#!/usr/bin/env groovy


/*
 *  Build platform-core FOLIO instance
 */


def call(String ec2_group, String tenant, ) {

  dir("${env.WORKSPACE}/folio-infrastructure") {
    checkout([$class: 'GitSCM', branches: [[name: '*/master']],
              doGenerateSubmoduleConfigurations: false,
              extensions: [[$class: 'SubmoduleOption',
                                     disableSubmodules: false,
                                     parentCredentials: false,
                                     recursiveSubmodules: true,
                                     reference: '',
                                     trackingSubmodules: true]],
              submoduleCfg: [],
              userRemoteConfigs: [[credentialsId: 'folio-jenkins-github-token',
                                    url: 'https://github.com/folio-org/folio-infrastructure']]])
  }

  dir("${env.WORKSPACE}/folio-infrastructure/CI/ansible") {

    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                           accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                           credentialsId: 'jenkins-aws',
                           secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {

          ansiblePlaybook credentialsId: '11657186-f4d4-4099-ab72-2a32e023cced',
                          installation: 'Ansible',
                          inventory: 'inventory',
                          playbook: 'platform-core.yml',
                          sudoUser: null, vaultCredentialsId: 'ansible-vault-pass',
                          extraVars: [ ec2_group: 
    
    }
  }    // end dir
} 
