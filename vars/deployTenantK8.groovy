#!/usr/bin/env groovy


/*
 *  deploy tenant to Okapi on K8.
 */


def call() {

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

          ansiblePlaybook credentialsId: '11657186-f4d4-4099-ab72-2a32e023cced',
                          disableHostKeyChecking: true,
                          installation: 'Ansible',
                          inventory: 'inventory',
                          playbook: 'platform-pr-v2.yml',
                          sudoUser: null, 
                          vaultCredentialsId: 'ansible-vault-pass',
                          extraVars: [ okapi_url: "${env.okapiUrl}",
                                       tenant: "${env.tenant}",
                                       build_module_list_files: "${env.WORKSPACE}",
                                       platform: "${env.folioPlatform}" ]
  }
} 
