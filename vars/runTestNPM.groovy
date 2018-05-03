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

    def testReportUrl = "${env.BUILD_URL}YarnTestReport/"

    // start Xvfb for tests that require browsers/displays
    sudo Xvfb :20 &

    withEnv([ 
      'DISPLAY=:20',
      'CHROME_BIN=/usr/bin/google-chrome-stable',
      'FIREFOX_BIN=/usr/bin/google-chrome-stable'
    ]) { 

      echo "Local browsers available:"
      sh "$CHROME_BIN --version"
      sh "$FIREFOX_BIN --version"

      sh 'echo "<html><body><pre>" > ci/test.html'

      def testStatus = sh(returnStatus:true, script: "yarn run $runTestOptions 2>/dev/null 1>> ci/test.html")

      sh 'echo "</pre><body></html>" >> ci/test.html'
 
      def testReport = readFile('ci/test.html')
      echo "$testReport"
    
      publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, 
                 keepAll: true, reportDir: 'ci',
                 reportFiles: 'test.html',
                 reportName: 'Test',
                 reportTitles: 'Test'])

      sh 'rm -rf ci'

      if (testStatus != 0) { 
        def message = "Test errors found. $testReportUrl"
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
