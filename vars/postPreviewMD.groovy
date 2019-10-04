#!/usr/bin/env groovy


/*
 *  Deploy a tenant, enable modules, and load sample data on FOLIO backend 
 */

def call(String okapiUrl, String tenant) {

  dir("${env.WORKSPACE}") {
    checkout([$class: 'GitSCM',
        branches: [[name: 'refs/heads/FOLIO-2267']],
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
    dir("${env.WORKSPACE}/folio-infrastructure/CI/scripts") {
      script {
        def scriptPath="${env.WORKSPACE}/folio-infrastructure/CI/scripts"
        def modDescriptor="${env.WORKSPACE}/target/ModuleDescriptor.json"
        withCredentials([usernamePassword(credentialsId: 'okapi-preview-superuser', passwordVariable: 'pass', usernameVariable: 'user')]) {
          sh "${scriptPath}/postMDPreview.sh ${modDescriptor} $user $pass"
        }
      }
    }
    dir("${env.WORKSPACE}") {
      // change back to workspace and cleanup
      sh "rm -rf ${env.WORKSPACE}/folio-infrastructure
    }
  }
}
