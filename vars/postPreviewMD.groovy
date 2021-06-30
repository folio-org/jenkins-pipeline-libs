#!/usr/bin/env groovy


/*
 *  Deploy a tenant, enable modules, and load sample data on FOLIO backend 
 */

def call() {

  dir("${env.WORKSPACE}") {
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
    dir("${env.WORKSPACE}/folio-infrastructure/CI/scripts") {
      def previewId = "${env.name}-${env.bareVersion}.${env.CHANGE_ID}.${env.BUILD_NUMBER}"
      def previewContainer = "${env.name}:${env.bareVersion}.${env.CHANGE_ID}.${env.BUILD_NUMBER}"
      script {
        def scriptPath="${env.WORKSPACE}/folio-infrastructure/CI/scripts"
        def modDescriptor="${env.WORKSPACE}/target/ModuleDescriptor.json"
        withCredentials([usernamePassword(credentialsId: 'okapi-preview-superuser', passwordVariable: 'pass', usernameVariable: 'user')]) {
          sh "${scriptPath}/postMDPreview.sh ${modDescriptor} ${previewId} ${previewContainer} $user $pass"
        }
      }
    }
    dir("${env.WORKSPACE}") {
      // change back to workspace and cleanup
      sh "rm -rf ${env.WORKSPACE}/folio-infrastructure"
    }
  }
}
