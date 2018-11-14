#!/usr/bin/env groovy

/* 
 * Run the SonarQube scanner on Maven-based projects.
 *
 * Configurable parameters:
 *
 * (List) sqBranch: Branch names  we want to run SQ analysis (map option in BuildMvn.groovy).
 *
 * By default, analysis is run on GitHub PRs and 'master' branch. 
 *
 */


def call(String sqBranch = null) {

  def sonarMvnPluginVer = '3.5.0.1254' 
  // def sonarMvnPluginVer = '3.3.0.603'

  if (env.CHANGE_ID) {
    stage('SonarQube Analysis') {
      echo "PR request: $env.CHANGE_ID"
      withCredentials([[$class: 'StringBinding', 
                        credentialsId: '6b0ebf62-3a12-4e6b-b77e-c45817b5791b', 
                        variable: 'GITHUB_ACCESS_TOKEN']]) {
        withSonarQubeEnv('SonarCloud') {
          sh "mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:${sonarMvnPluginVer}:sonar " +
                  "-Dsonar.organization=folio-org -Dsonar.verbose=true " +
                  "-Dsonar.pullrequest.base=master " +
                  "-Dsonar.pullrequest.branch=${env.BRANCH_NAME} " +
                  "-Dsonar.pullrequest.key=${env.CHANGE_ID} " +
                  "-Dsonar.pullrequest.provider=github " + 
                  "-Dsonar.pullrequest.github.repository=folio-org/${env.projectName} " +
                  "-Dsonar.pullrequest.github.endpoint=https://api.github.com"
        }
      }  
    }
  }
  else {  
    if (env.BRANCH_NAME == 'master') {
      stage('SonarQube Analysis') {
        withSonarQubeEnv('SonarCloud') {
          sh "mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:${sonarMvnPluginVer}:sonar " +
               "-Dsonar.organization=folio-org -Dsonar.verbose=true"
        }
      }
    }
  } // end if
}
