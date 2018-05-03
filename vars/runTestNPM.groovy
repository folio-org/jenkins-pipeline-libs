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

    def testReportUrl = "${env.BUILD_URL}Yarn_20Test_20Report/"

    // start Xvfb for tests that require browsers/displays
    sh 'sudo Xvfb :20 &'

    withEnv([ 
      'DISPLAY=:20',
      'CHROME_BIN=/usr/bin/google-chrome-stable',
      'FIREFOX_BIN=/usr/bin/firefox'
    ]) { 

      sh "$CHROME_BIN --version"
      sh "$FIREFOX_BIN --version"


      def testStatus = sh(returnStatus:true, script: "yarn test $runTestOptions 2>&1> test.out")

      // encode markup in output that will mess up rendering
      sh """
        sed -i -e 's/</\\&lt;/g' -e 's/>/\\&gt;/g' test.out
      """

      def testOut = readFile('test.out').trim()
      echo "$testOut"

      sh 'mkdir -p ci'
      sh 'echo "<html><body><pre>" > ci/test.html'
      sh 'cat test.out >> ci/test.html'
      sh 'echo "</pre><body></html>" >> ci/test.html'
    
      publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, 
                 keepAll: true, reportDir: 'ci',
                 reportFiles: 'test.html',
                 reportName: 'Yarn Test Report',
                 reportTitles: 'Yarn Test Report'])

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
