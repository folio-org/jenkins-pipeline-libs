#!/usr/bin/env groovy


/*
 * Run UI Regression tests 
 */

def call(Boolean regressionDebugMode = false, String okapiUrl, String tenant, String folioUser, String folioPassword) {

  // default to failed regression test
  def status = 1
  def testMessage
  def regressionReportUrl = "${env.BUILD_URL}UI_20Regression_20Test_20Report/"
  def XVFB = 'xvfb-run --server-args="-screen 0 1024x768x24"'
  def testCmd
  def uitestOpts = "--uiTest.username $folioUser --uiTest.password $folioPassword"
 
  // Determine if this is an app context or platform context
  def context = sh(returnStdout: true, 
    script: 'stripes status | grep context | awk -F \':\' \'{ print $2 }\' | tr -d \'[:space:]\'')

  if (context == 'platform') { 
    // start simple webserver to serve webpack created by buildStripesPlatform
    withEnv(['JENKINS_NODE_COOKIE=dontkill']) {
      sh 'yarn stripes serve --existing-build ./bundle &'
    }
    // use 'platform' context
    testCmd = "yarn test-integration $uitestOpts --show --local"
  }  
  else { 
    // assume 'app' context. run module tests
    testCmd = "yarn test-int $uitestOpts --show --okapi $okapiUrl --tenant $tenant"
  }
  
  sh 'mkdir -p ci'
  sh 'echo "<html><head><title>UI Regression Test Report</title></head>" > ci/rtest.html'
  sh 'echo "<body><pre>" >> ci/rtest.html'

  if (regressionDebugMode) {
    status = sh(script: "DEBUG=* $XVFB $testCmd >> ci/rtest.html 2>&1", 
                returnStatus:true)
  }
  else {
    status = sh(script: "$XVFB $testCmd >> ci/rtest.html 2>&1", returnStatus:true)
  } 

  sh 'echo "</pre><body></html>" >> ci/rtest.html'
 
  // print test results to job console
  def testReport =  readFile('ci/rtest.html')
  echo "$testReport"

  // publish results
  publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, 
               keepAll: true, reportDir: 'ci', 
                 reportFiles: 'rtest.html', 
                 reportName: 'UI Regression Test Report', 
                 reportTitles: 'UI Regression Test Report'])

  if (status != 0) { 
    testMessage = "UI Regression Tests FAILURES. Details at:  $regressionReportUrl"
  }
  else {
    testMessage = "All UI Regression Tests PASSED. Details at:  $regressionReportUrl" 
  }
     
  if (env.CHANGE_ID) { 
    @NonCPS
    def comment = pullRequest.comment(testMessage)
  }

  // temporarily disable tests failing the build
  // if (status != 0) {
  //   // fail the build
  //   error(testMessage)
  // }
  // else {
  //   echo "$testMessage"
  // }
  echo "$testMessage"
}
