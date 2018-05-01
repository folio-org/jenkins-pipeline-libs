#!/usr/bin/env groovy


/*
 * Run UI Regression tests on PRs
 */

def call(String runRegression, String folioUser, String folioPassword, String folioUrl) {

  // default to failed regression test
  def status = 1
  def testMessage
  def regressionReportUrl = "${env.BUILD_URL}UIRegressionTestReport/"
 
  stage('Run UI Regression Tests') {

    // clone ui-testing repo
    dir("$env.WORKSPACE") { 
      sh 'git clone https://github.com/folio-org/ui-testing'
    }

    dir ("${env.WORKSPACE}/ui-testing") { 

      sh "yarn link $env.npm_name"
      sh 'rm -f yarn.lock'
    
      withCredentials([string(credentialsId: 'jenkins-npm-folioci',variable: 'NPM_TOKEN')]) {
        withNPM(npmrcConfig: 'jenkins-npm-folioci') {
          sh 'yarn install' 
          sh 'sudo /usr/bin/Xvfb :2 &'
          sh 'sleep 1'

          env.FOLIO_UI_USERNAME = folioUser
          env.FOLIO_UI_PASSWORD = folioPassword
          env.FOLIO_UI_URL = folioUrl

          sh 'mkdir -p ci'
          sh 'echo "<html><head><title>UI Regression Test Report</title></head>" > ci/rtest.html'
          sh 'echo "<body><pre>" >> ci/rtest.html'

          if (runRegression == 'partial') {
            echo "Running partial UI Regression test against $folioUrl"
            status = sh(script: "DISPLAY=:2 yarn test-module -o --run=${env.npmShortName} " +
                          ">> ci/rtest.html 2>&1", returnStatus:true)
          } 
          else {
            // run 'full'
            echo "Running full UI Regression test against $folioUrl:="
            //status = sh(script: "DEBUG=* DISPLAY=:2 yarn test >> ci/rtest.html 2>&1", returnStatus:true)
            status = sh(script: "DISPLAY=:2 yarn test >> ci/rtest.html 2>&1", returnStatus:true)
          }
          sh 'echo "</pre><body></html>" >> ci/rtest.html'
        }
      }
 
      // print test results to job console
      def testReport =  readFile('ci/rtest.html')
      echo "$testReport"

      // publish results
      publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, 
                   keepAll: true, reportDir: 'ci', 
                   reportFiles: 'rtest.html', 
                   reportName: 'UIRegressionTestReport', 
                   reportTitles: 'UIRegressionTestReport'])

      // publish generated yarn.lock
      sh 'cat yarn.lock >> ci/uitest-yarnlock.html'
      publishHTML([allowMissing: false, alwaysLinkToLastBuild: false,
                   keepAll: true, reportDir: 'ci',
                   reportFiles: 'uitest-yarnlock.html',
                   reportName: 'UITestingYarnLock',
                   reportTitles: 'UITestingYarnLock'])
    
      if (status != 0) { 
        testMessage = "UI Regression Tests FAILURES. Details at:  $regressionReportUrl"
      }
      else {
        testMessage = "All UI Regression Tests PASSED. Details at:  $regressionReportUrl" 
      }
     
      echo "$testMessage"

      if (env.CHANGE_ID) { 
        @NonCPS
        def comment = pullRequest.comment(testMessage)
      }

    } // end dir
  } // end stage
}
