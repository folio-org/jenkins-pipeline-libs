#!/usr/bin/env groovy

/*
 * Run the SonarQube scanner for JS-based projects
 *
 */


def call(String lcovPath = 'artifacts/coverage-jest', String sonarScanDirs, String defaultBranch) {
  withCredentials([[$class: 'StringBinding',
                        credentialsId: 'id-jenkins-github-personal-token',
                        variable: 'GITHUB_ACCESS_TOKEN']]) {
    withSonarQubeEnv('SonarCloud') {
      def scannerHome = tool 'SonarQube-Scanner-4'
      def excludeFiles = '**/platform/alias-service.js,**/docs/**,**/node_modules/**,**/examples/**,**/artifacts/**,**/ci/**,Jenkinsfile,**/LICENSE,**/*.css,**/*.md,**/*.json,**/tests/**,**/stories/*.js,**/test/**,**/.stories.js,**/resources/bigtest/interactors/**,**/resources/bigtest/network/**,**/*-test.js,**/*.test.js,**/*-spec.js,**/karma.conf.js,**/jest.config.js'

      if (env.CHANGE_ID) {
        sh "${scannerHome}/bin/sonar-scanner " +
          "-Dsonar.projectKey=org.folio:${env.projectName} " +
          "-Dsonar.projectName=${env.projectName} " +
          "-Dsonar.organization=folio-org " +
          "-Dsonar.sources=${sonarScanDirs} " +
          "-Dsonar.language=js " +
          "-Dsonar.exclusions=${excludeFiles} " +
          "-Dsonar.javascript.lcov.reportPaths=${lcovPath}/lcov.info " +
          "-Dsonar.pullrequest.base=${defaultBranch} " +
          "-Dsonar.pullrequest.key=${env.CHANGE_ID} " +
          "-Dsonar.pullrequest.branch=${env.BRANCH_NAME} " +
          "-Dsonar.pullrequest.provider=github " +
          "-Dsonar.pullrequest.github.repository=folio-org/${env.projectName} "
          // "-Dsonar.pullrequest.github.endpoint=https://api.github.com"
      }
      else {
        if ( !env.BRANCH_IS_PRIMARY ) {
          sh "git fetch --no-tags ${env.projUrl} +refs/heads/${defaultBranch}:refs/remotes/origin/${defaultBranch}"
          sh "${scannerHome}/bin/sonar-scanner " +
            "-Dsonar.organization=folio-org " +
            "-Dsonar.projectKey=org.folio:${env.projectName} " +
            "-Dsonar.projectName=${env.projectName} " +
            "-Dsonar.branch.name=${env.BRANCH_NAME} " +
            "-Dsonar.sources=${sonarScanDirs} " +
            "-Dsonar.language=js " +
            "-Dsonar.exclusions=${excludeFiles} " +
            "-Dsonar.javascript.lcov.reportPaths=${lcovPath}/lcov.info"
        }
        else {
          sh "${scannerHome}/bin/sonar-scanner " +
            "-Dsonar.organization=folio-org " +
            "-Dsonar.projectKey=org.folio:${env.projectName} " +
            "-Dsonar.projectName=${env.projectName} " +
            "-Dsonar.sources=${sonarScanDirs} " +
            "-Dsonar.language=js " +
            "-Dsonar.exclusions=${excludeFiles} " +
            "-Dsonar.javascript.lcov.reportPaths=${lcovPath}/lcov.info"
        }
      }

    } // end withSonarQubeenv
  } // end withCredentials
  // remove Sonar Scannor artifacts
  sh 'rm -rf .scannerwork'
}

