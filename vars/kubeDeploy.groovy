#!/usr/bin/env groovy

/* 
 * deploy a module to kubernetes
 */

def call(String namespace, String targetModule, String okapiUrl = "http://okapi:9130") {
  echo "install ansible kubernetes deps"
  sh "pip -q install wheel"
  sh "pip -q install openshift==0.10.2"
  
  echo "clone folio-infrastructre"
  checkout([$class: 'GitSCM', 
            branches: [[name: '*/master']],
            doGenerateSubmoduleConfigurations: false, 
            extensions: [[$class: 'SubmoduleOption', 
            disableSubmodules: false, 
            parentCredentials: false, 
            recursiveSubmodules: true, 
            reference: '', 
            trackingSubmodules: true], 
            [$class: 'RelativeTargetDirectory', relativeTargetDir: 'folio-infrastructure']],
            submoduleCfg: [], 
            userRemoteConfigs: [[credentialsId: 'id-jenkins-github-personal-token-with-username', 
                                url: 'https://github.com/folio-org/folio-infrastructure']]
          ])
    


  dir("${env.WORKSPACE}/folio-infrastructure/CI/ansible") {
    sh 'printf "[ci]\nlocalhost\tansible_connection=local" > temp-inventory'
      ansiblePlaybook(credentialsId: '11657186-f4d4-4099-ab72-2a32e023cced',
                      disableHostKeyChecking: true,
                      installation: 'Ansible',
                      inventory: 'temp-inventory',
                      playbook: 'kube-module-deploy.yml',
                      sudoUser: null,
                      vaultCredentialsId: 'ansible-vault-pass',
                      extraVars: [
                        namespace: namespace,
                        okapi_url: okapiUrl,
                        target_module: targetModule,
                        folio_install_type: "kubernetes"
                      ])
  }
}
