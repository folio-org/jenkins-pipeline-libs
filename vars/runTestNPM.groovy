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

    // start Xvfb for tests that require browsers/displays
    sh 'sudo Xvfb :20 &'

    withEnv([ 
      'DISPLAY=:20',
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

      // install karma junit reporter
      sh 'yarn add --dev karma-junit-reporter'

      // inject karma config for karma testing
      def karmaConf = libraryResource('org/folio/karma.conf.js.ci')
      writeFile file: 'karma.conf.js', text: "$karmaConf"

      def testStatus = sh(returnStatus:true, script: "yarn test $runTestOptions")

      // publish junit tests if available
      junit allowEmptyResults: true, testResults: 'artifacts/runTest/*.xml'

      // cleanup CI stuff
      sh 'rm -rf runTest'
      sh 'rm -f karma.conf.js'
      sh 'yarn remove karma-junit-reporter'

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
