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


def call() {

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
                  "-Dsonar.github.repository=folio-org/${env.projectName} " +
                  "-Dsonar.github.oauth=${GITHUB_ACCESS_TOKEN}"
        }
      }  
    }
  }
  else {  
    if (env.BRANCH_NAME == 'master') {
      stage('SonarQube Analysis') {
        withSonarQubeEnv('SonarCloud') {
          sh "mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:3.3.0.603:sonar " +
               "-Dsonar.organization=folio-org -Dsonar.verbose=true"
        }
      }
    }

    else { 
      stage('SonarQube Analysis') {
        withSonarQubeEnv('SonarCloud') {
          sh "mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:3.3.0.603:sonar " +
               "-Dsonar.organization=folio-org -Dsonar.verbose=true " +
               "-Dsonar.analysis.mode=preview -Dsonar.issueReport.html.enable=true"
        }
        // publish report
        publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, 
                     keepAll: true, reportDir: 'target/sonar/issues-report', 
                     reportFiles: 'issues-report.html', 
                     reportName: 'Sonarqube Issues Report (Full)', 
                     reportTitles: 'issues-report'])

        publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, 
                     keepAll: true, reportDir: 'target/sonar/issues-report', 
                     reportFiles: 'issues-report-light.html', 
                     reportName: 'Sonarqube Issues Report (Light)', 
                     reportTitles: 'issues-report-light'])
      }
    } 
  } // end if
}
