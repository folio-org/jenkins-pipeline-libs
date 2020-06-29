#!/usr/bin/env groovy


/*
 *  Build platform FOLIO instance
 */


def call(String ec2Group, String folioHostname, String tenant) {

  env.ANSIBLE_CONFIG = "${env.WORKSPACE}/folio-infrastructure/CI/ansible/ansible.cfg"

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
                          disableHostKeyChecking: true,
                          installation: 'Ansible',
                          inventory: 'inventory',
                          playbook: 'platform-pr.yml',
                          sudoUser: null, 
                          vaultCredentialsId: 'ansible-vault-pass',
                          extraVars: [ ec2_group: "$ec2Group", 
                                       folio_hostname: "$folioHostname",
                                       tenant: "$tenant",
                                       build_module_list_files: "${env.WORKSPACE}",
                                       platform: "${env.folioPlatform}",
                                       pr: "${env.CHANGE_ID}" ]
    }
  }

  dir("${env.WORKSPACE}") { 
    // copy stripes bundle to folio instance
    sh "tar cf ${env.WORKSPACE}/stripes-platform.tar output install.json yarn.lock"
    sh "bzip2 ${env.WORKSPACE}/stripes-platform.tar"

    sshagent (credentials: [env.sshKeyId]) {
      sh "scp -o StrictHostKeyChecking=no ${env.WORKSPACE}/stripes-platform.tar.bz2 " +
         "ubuntu@${folioHostname}.indexdata.internal:/etc/folio/stripes"
  
      sh """
        ssh -o StrictHostKeyChecking=no ubuntu@${folioHostname}.indexdata.internal \
        'cd /etc/folio/stripes; bunzip2 stripes-platform.tar.bz2; tar xf stripes-platform.tar'
      """
    }
    sh "rm -f ${env.WORKSPACE}/stripes-platform.tar"
  }  

  dir("${env.WORKSPACE}/folio-infrastructure/CI/ansible") {

    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                           accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                           credentialsId: 'jenkins-aws',
                           secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {

          ansiblePlaybook credentialsId: '11657186-f4d4-4099-ab72-2a32e023cced',
                          disableHostKeyChecking: true,
                          installation: 'Ansible',
                          inventory: 'inventory',
                          playbook: 'stripes-docker.yml',
                          sudoUser: null,
                          vaultCredentialsId: 'ansible-vault-pass',
                          extraVars: [ ec2_group: "$ec2Group",
                                       folio_hostname: "$folioHostname" ]
    }
  }
} 
