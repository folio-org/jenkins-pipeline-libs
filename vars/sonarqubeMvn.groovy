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

  if (env.CHANGE_ID) {
    stage('SonarQube Analysis') {
      echo "PR request: $env.CHANGE_ID"
      withCredentials([[$class: 'StringBinding', 
                        credentialsId: '6b0ebf62-3a12-4e6b-b77e-c45817b5791b', 
                        variable: 'GITHUB_ACCESS_TOKEN']]) {
        withSonarQubeEnv('SonarCloud') {
          sh "mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:3.3.0.603:sonar " +
                  "-Dsonar.organization=folio-org -Dsonar.verbose=true " +
                  "-Dsonar.analysis.mode=preview " +
                  "-Dsonar.github.pullRequest=${env.CHANGE_ID} " +
                  "-Dsonar.github.repository=folio-org/${env.project_name} " +
                  "-Dsonar.github.oauth=${GITHUB_ACCESS_TOKEN}"
        }
      }  
    }
  }
  else {  
    //if (env.BRANCH_NAME == 'master') {
    if (env.BRANCH_NAME == 'folio-918-test') {
      stage('SonarQube Analysis') {
        withSonarQubeEnv('SonarCloud') {
          sh "mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:3.3.0.603:sonar" +
               "-Dsonar.organization=folio-org -Dsonar.verbose=true -Dsonar.branch=test"
        }
      }
    }

    if (sqBranch) { 
      stage('SonarQube Analysis') {
        withSonarQubeEnv('SonarCloud') {
          sh "mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:3.3.0.603:sonar " +
               "-Dsonar.organization=folio-org -Dsonar.verbose=true -Dsonar.branch=$sqBranch"
        }
      }
    } 
  } // end if
}
