#!/usr/bin/env groovy


/*
 * Run Node/NPM-based optional scripts 
 * 
 * Example:
 *
 * buildNPM { 
 *   runScripts ['script name':'script args']
 * }
 * 
 */

def call(String scriptName, String scriptArgs) {

  def XVFB = 'xvfb-run --server-args="-screen 0 1024x768x24"'
  def status
  def message

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
      // publish junit tests if available
      junit allowEmptyResults: true, testResults: 'artifacts/runTest/*.xml'

      if (scriptStatus != 0) { 
        errorMessage = "Test errors found for ${scriptName}. See ${env.BUILD_URL}" 
        if (env.CHANGE_ID) {
          // Requires https://github.com/jenkinsci/pipeline-github-plugin
          @NonCPS
          comment = pullRequest.comment(errorMessage)
        }
        error(errorMessage)
      }
    } 
  } // end stage 
}
