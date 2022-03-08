#!/usr/bin/env groovy

/* 
 * Run the SonarQube scanner on Maven-based projects.
 *
 * Configurable parameters:
 *
 * (List) sqBranch: Branch names  we want to run SQ analysis (map option in BuildMvn.groovy).
 *
 * By default, analysis is run on GitHub PRs and mainline branch. 
 *
 */


def call() {

  def sonarMvnPluginVer = '3.9.1.2184'

  if (env.CHANGE_ID) {
    stage('SonarQube Analysis') {
      echo "PR request: $env.CHANGE_ID"
      withCredentials([[$class: 'StringBinding', 
                        credentialsId: 'id-jenkins-github-personal-token', 
                        variable: 'GITHUB_ACCESS_TOKEN']]) {
        withSonarQubeEnv('SonarCloud') {
          sh "mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:${sonarMvnPluginVer}:sonar " +
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
    if ( env.BRANCH_IS_PRIMARY ) {
      stage('SonarQube Analysis') {
        withSonarQubeEnv('SonarCloud') {
          sh "mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:${sonarMvnPluginVer}:sonar " +
               "-Dsonar.organization=folio-org -Dsonar.verbose=true"
        }
      }
    }

    else { 
      stage('SonarQube Analysis') {
        withSonarQubeEnv('SonarCloud') {
          sh "mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:${sonarMvnPluginVer}:sonar " +
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
