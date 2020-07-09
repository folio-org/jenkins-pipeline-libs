#!/usr/bin/env groovy

/* 
 * Run the SonarQube scanner for JS-based projects
 * 
 */


def call(String lcovPath = 'artifacts/coverage', String lcovPath2 = 'coverage/') {
  withCredentials([[$class: 'StringBinding', 
                        credentialsId: '6b0ebf62-3a12-4e6b-b77e-c45817b5791b', 
                        variable: 'GITHUB_ACCESS_TOKEN']]) {
    withSonarQubeEnv('SonarCloud') {
      def scannerHome = tool 'SonarQube Scanner'
      def excludeFiles = '**/docs/**,**/node_modules/**,**/artifacts/**,**/ci/**,Jenkinsfile,**/LICENSE,**/*.css,**/*.md,**/*.json,**/tests/**,**/stories/*.js,**/test/**,**/.stories.js,**/resources/bigtest/interactors/**,**/resources/bigtest/network/**,**/*-test.js,**/*.test.js,**/*-spec.js,**/karma.conf.js'

      if (env.CHANGE_ID) {
        sh "${scannerHome}/bin/sonar-scanner " +
          "-Dsonar.projectKey=org.folio:${env.projectName} " +
          "-Dsonar.projectName=${env.projectName} " +
          "-Dsonar.organization=folio-org " +
          "-Dsonar.sources=. " +
          "-Dsonar.language=js " +
          "-Dsonar.exclusions=${excludeFiles} " +
          "-Dsonar.javascript.lcov.reportPaths=${lcovPath}/lcov.info,${lcovPath2}/lcov.info " +
          "-Dsonar.pullrequest.base=master " +
          "-Dsonar.pullrequest.key=${env.CHANGE_ID} " +
          "-Dsonar.pullrequest.branch=${env.BRANCH_NAME} " +
          "-Dsonar.pullrequest.provider=github " + 
          "-Dsonar.pullrequest.github.repository=folio-org/${env.projectName} " 
          // "-Dsonar.pullrequest.github.endpoint=https://api.github.com"
      }
      else {  
        if (env.BRANCH_NAME != 'master' ) { 
          sh "git fetch --no-tags ${env.projUrl} +refs/heads/master:refs/remotes/origin/master"
          sh "${scannerHome}/bin/sonar-scanner " +
            "-Dsonar.organization=folio-org " +
            "-Dsonar.projectKey=org.folio:${env.projectName} " +
            "-Dsonar.projectName=${env.projectName} " +
            "-Dsonar.branch.name=${env.BRANCH_NAME} " +
            "-Dsonar.sources=. " +
            "-Dsonar.language=js " +
            "-Dsonar.exclusions=${excludeFiles} " +
            "-Dsonar.javascript.lcov.reportPaths=${lcovPath}/lcov.info,${lcovPath2}/lcov.info" 
        }
        else {
          sh "${scannerHome}/bin/sonar-scanner " +
            "-Dsonar.organization=folio-org " +
            "-Dsonar.projectKey=org.folio:${env.projectName} " +
            "-Dsonar.projectName=${env.projectName} " +
            "-Dsonar.sources=. " +
            "-Dsonar.language=js " +
            "-Dsonar.exclusions=${excludeFiles} " +
            "-Dsonar.javascript.lcov.reportPaths=${lcovPath}/lcov.info,${lcovPath2}/lcov.info"      
        }
      }

    } // end withSonarQubeenv
  } // end withCredentials
  // remove Sonar Scannor artifacts
  sh 'rm -rf .scannerwork'
}

