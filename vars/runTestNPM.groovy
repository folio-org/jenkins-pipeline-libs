#!/usr/bin/env groovy


/*
 * Run Node/NPM-based "unit" tests.
 *
 * Example:
 *
 * buildNPM {
 *   runTest = 'yes'
 *   runTestOptions = '--karma.singleRun --karma.browsers=ChromeDocker'
 * }
 *
 */

def call(String runTestOptions = '') {

  stage('Run Local Tests') {

    def XVFB = 'xvfb-run --server-args="-screen 0 1024x768x24"'

    env.NODE_OPTIONS="--max-old-space-size=4096"
    sh "echo NODE_OPTIONS=$NODE_OPTIONS"

    withEnv([
      'CHROME_BIN=/usr/bin/google-chrome-stable',
      'DEBIAN_FRONTEND=noninteractive',
      'JEST_JUNIT_OUTPUT_DIR=./artifacts/jest-junit'
    ]) {

      // disabled since we build new build images every week.
      // get latest versions for browsers
      // sh 'sudo apt-get -q update'
      // sh 'sudo apt-get -y --no-install-recommends install google-chrome-stable'
      // sh 'sudo apt-get -y --no-install-recommends install firefox'

      // display available browsers/version
      sh "$CHROME_BIN --version"

      def testStatus = sh(returnStatus:true, script: "$XVFB yarn test $runTestOptions")

      if (testStatus != 0) {
        def message = "Test errors found. See ${env.BUILD_URL}"
        // PR
        if (env.CHANGE_ID) {
          // Requires https://github.com/jenkinsci/pipeline-github-plugin
          @NonCPS
          def comment = pullRequest.comment(message)
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
        error(message)
      }
      else {
        echo "All tests completed successfully."
      }
    }
  } // end stage
}
