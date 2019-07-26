#!/usr/bin/env groovy

/* 
 * deploy a module to kubernetes
 */

def call() {
  echo "install ansible kubernetes deps"
  sh "pip -q install openshift psycopg2-binary"
  
  echo "clone folio-infrastructre"
  checkout([$class: 'GitSCM', 
            //branches: [[name: '*/master']],
            branches: [[name: 'refs/heads/FOLIO-2183']],
            doGenerateSubmoduleConfigurations: false, 
            extensions: [[$class: 'SubmoduleOption', 
            disableSubmodules: false, 
            parentCredentials: false, 
            recursiveSubmodules: true, 
            reference: '', 
            trackingSubmodules: true], 
            [$class: 'RelativeTargetDirectory', relativeTargetDir: 'folio-infrastructure']],
            submoduleCfg: [], 
            userRemoteConfigs: [[credentialsId: 'cd96210b-c06f-4f09-a836-f992a685a97a', 
                                url: 'https://github.com/folio-org/folio-infrastructure']]
          ])
    


  dir("${env.WORKSPACE}/folio-infrastructure/CI/ansible") {
    sh 'printf "[ci]\nlocalhost\tansible_connection=local" > temp-inventory'
      ansiblePlaybook(credentialsId: '11657186-f4d4-4099-ab72-2a32e023cced',
                      disableHostKeyChecking: true,
                      installation: 'Ansible',
                      inventory: 'temp-inventory',
                      playbook: 'folio-kubernetes.yml',
                      sudoUser: null,
                      vaultCredentialsId: 'ansible-vault-pass')
  }
}