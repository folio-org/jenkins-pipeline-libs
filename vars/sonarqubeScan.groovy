#!/usr/bin/env groovy

/* 
 * Run the SonarQube scanner.   Useful for non-Maven-based projects
 * 
 */


def call() {
  stage('SonarQube Scan') {
    withSonarQubeEnv('SonarCloud') {
      echo "Performing SonarQube scan" 
      def scannerHome = tool 'SonarQube Scanner'
      sh """
      ${scannerHome}/bin/sonar-scanner \
            -Dsonar.projectKey=folio-org:${env.projectName} \
            -Dsonar.projectName=${env.projectName} \
            -Dsonar.projectVersion=${env.version} \
            -Dsonar.sources=. \
            -Dsonar.organization=folio-org 
      """
    } 
  }
}

