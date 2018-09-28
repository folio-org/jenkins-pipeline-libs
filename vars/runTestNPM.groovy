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

    withEnv([ 
      'CHROME_BIN=/usr/bin/google-chrome-stable',
      'FIREFOX_BIN=/usr/bin/firefox',
      'DEBIAN_FRONTEND=noninteractive'
    ]) { 

      // disabled since we build new build images every week. 
      // get latest versions for browsers
      // sh 'sudo apt-get -q update'
      // sh 'sudo apt-get -y --no-install-recommends install google-chrome-stable'
      // sh 'sudo apt-get -y --no-install-recommends install firefox'

      // display available browsers/version
      sh "$CHROME_BIN --version"
      sh "$FIREFOX_BIN --version"

      def testStatus = sh(returnStatus:true, script: "$XVFB yarn test $runTestOptions")

      // publish junit tests if available
      junit allowEmptyResults: true, testResults: 'artifacts/runTest/*.xml'

      // publish lcov report in Jenkins if available
      publishHTML([allowMissing: true, alwaysLinkToLastBuild: false, 
                   keepAll: true, reportDir: 'artifacts/coverage/lcov-report', 
                   reportFiles: 'index.html', 
                   reportName: 'LCov Coverage Report', 
                   reportTitles: 'LCov Coverage Report'])


      if (testStatus != 0) { 
        def message = "Test errors found. See ${env.BUILD_URL}"
        // PR
        if (env.CHANGE_ID) {
          // Requires https://github.com/jenkinsci/pipeline-github-plugin
          @NonCPS
          def comment = pullRequest.comment(message) 
        }
        error(message)
      }
      else {
        echo "All tests completed successfully."
      }
    } 
  } // end stage 
}
