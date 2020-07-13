#!/usr/bin/env groovy


/*
 * Run Node/NPM-based optional scripts 
 * 
 * Example:
 *
 * buildNPM { 
 *   runScripts [
 *      ['script1':'script1 args'],
 *      ['script2':'script2 args']
 *   ]
 * }
 * 
 */

def call(String scriptName, String scriptArgs) {

  def XVFB = 'xvfb-run -a --server-args="-screen 0 1024x768x24"'
  def scriptStatus
  def errorMessage

  stage("Run yarn $scriptName") {
    withEnv([ 
      'CHROME_BIN=/usr/bin/google-chrome-stable',
      'FIREFOX_BIN=/usr/bin/firefox',
      'DEBIAN_FRONTEND=noninteractive'
    ]) { 

      // display available browsers/version
      sh "$CHROME_BIN --version"
      sh "$FIREFOX_BIN --version"

      scriptStatus = sh(returnStatus:true, script: "$XVFB yarn ${scriptName} ${scriptArgs}")

      if (scriptStatus != 0) { 
        errorMessage = "Test errors found for ${scriptName}. See ${env.BUILD_URL}" 
        if (env.CHANGE_ID) {
          // Requires https://github.com/jenkinsci/pipeline-github-plugin
          @NonCPS
          comment = pullRequest.comment(errorMessage)
        }
        // archive cypress artifacts if they exist, and only when tests fail
        if (fileExists('cypress/videos')) {
          sh 'mkdir -p artifacts/cypress'
          sh 'cp -R cypress/videos artifacts/cypress'
        }
        if (fileExists('cypress/screenshots')) {
          sh 'mkdir -p artifacts/cypress'
          sh 'cp -R cypress/screenshots artifacts/cypress'
        }
        if (fileExists('artifacts/cypress')) {
          sh 'tar -zcf cypress.tar.gz --directory artifacts cypress'
          archiveArtifacts artifacts: 'cypress.tar.gz', allowEmptyArchive: true
          archiveArtifacts artifacts: 'cypress/**/*(failed).png', allowEmptyArchive: true
          sh 'rm -rf artifacts/cypress'
        }
        else {
          echo "No cypress artifacts to be archived."
        }
        error(errorMessage)
      }
    } 
  } // end stage 
}
