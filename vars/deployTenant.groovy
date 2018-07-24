#!/usr/bin/env groovy


/*
 *  Deploy a tenant, enable modules, and load sample data on FOLIO backend 
 */



def call(String okapiUrl, String tenant) {

  stage('Enable Tenant') {
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

      def scriptPath="${env.WORKSPACE}/folio-infrastructure/CI/scripts"

      // create tenant
      sh "${scriptPath}/createTenant.sh $okapiUrl $tenant"

      // post MDs and enable tenant modules
      def mdStatus = sh(script: "${scriptPath}/createTenantModuleList.sh $okapiUrl " +
                        "$tenant ${env.WORKSPACE}/${env.stripesPlatform}/ModuleDescriptors " +
                        "> tenant_mod_list", returnStatus: true)

      if (mdStatus == 0)  { 
        sh "${scriptPath}/enableTenantModules.sh $okapiUrl $tenant < tenant_mod_list"

        // THE escape sequence from HELL
        def permissions = '\\\"\\\\\\\\\\\\\\\"perms.all\\\\\\\\\\\\\\\",\\\\\\\\\\\\\\\"login.all\\\\\\\\\\\\\\\",\\\\\\\\\\\\\\\"users.all\\\\\\\\\\\\\\\"\\\"'

        // set vars in include file
        sh "echo --- > vars_pr.yml"
        sh "echo okapi_url: $okapiUrl >> vars_pr.yml"
        sh "echo tenant: $tenant >> vars_pr.yml"
        sh "echo admin_user: { username: ${tenant}_admin, password: admin, " +
           "hash: 52DCA1934B2B32BEA274900A496DF162EC172C1E, " +
           "salt: 483A7C864569B90C24A0A6151139FF0B95005B16, " +
           "permissions: ${permissions}, " +
           "first_name: Admin, " +
           "last_name: ${tenant}, " +
           "email: admin@example.org } >> vars_pr.yml"

        // debug
        sh 'cat vars_pr.yml'

        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                           accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                           credentialsId: 'jenkins-aws',
                           secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {

          ansiblePlaybook credentialsId: '11657186-f4d4-4099-ab72-2a32e023cced',
                          installation: 'Ansible',
                          inventory: 'inventory',
                          playbook: 'folioci-pr.yml',
                          sudoUser: null, vaultCredentialsId: 'ansible-vault-pass'
        }

        return 0
      }
      else { 
        echo "Unable to enable all modules for tenant."
        return 1
      }
    }  // end dir
  } // end stage
} 
