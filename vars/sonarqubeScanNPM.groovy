#!/usr/bin/env groovy

/* 
 * Run the SonarQube scanner for JS-based projects
 * 
 */


def call(String lcovPath ) {
  withCredentials([[$class: 'StringBinding', 
                        credentialsId: 'folio-sonar-github-token', 
                        variable: 'GITHUB_ACCESS_TOKEN']]) {
    withSonarQubeEnv('SonarCloud') {
      echo "Performing SonarQube scan" 
      def scannerHome = tool 'SonarQube Scanner'
      sh """
      ${scannerHome}/bin/sonar-scanner \
          -Dsonar.projectKey=folio-org:${env.projectName} \
          -Dsonar.projectName=${env.projectName} \
          -Dsonar.projectVersion=${env.version} \
          -Dsonar.sources=. \
          -Dsonar.language=js \
          -Dsonar.exclusions=**/docs/**,**/node_modules/**,**/artifacts/**,**/ci/**,Jenkinsfile,**/LICENSE,**/*.css,**/*.md,**/*.json,**/tests/**/*-test.js,**/stories/*.js **/.stories.js \
          -Dsonar.javascript.lcov.reportPaths=${lcovPath}/lcov.info \
          -Dsonar.organization=folio-org 
      """
    }
  } 
}

