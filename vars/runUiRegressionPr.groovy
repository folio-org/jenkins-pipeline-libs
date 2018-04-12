#!/usr/bin/env groovy


/*
 * Run UI Regression tests
 */

def call(String folioUser, String folioPassword, String folioUrl) {

  def testStatus = ''
  def status

  withCredentials([string(credentialsId: 'jenkins-npm-folioci',variable: 'NPM_TOKEN')]) {
    withNPM(npmrcConfig: 'jenkins-npm-folioci') {
      // I think we need to remove the yarn lock file?
      sh 'rm -f yarn.lock'
      sh 'yarn install' 
      sh '/usr/bin/Xvfb :2 &'
      sh 'sleep 1'

      env.FOLIO_UI_USERNAME = folioUser
      env.FOLIO_UI_PASSWORD = folioPassword
      env.FOLIO_UI_URL = folioUrl

      sh 'echo "<html><head><title>UI Regression Test Report</title></head>" > rtest.html'
      sh 'echo "<body><pre>" >> rtest.html'

      echo "Running UI Regression test against $folioUrl"
      status = sh(script: "DEBUG=* DISPLAY=:2 yarn test >> rtest.html 2>&1", returnStatus:true)

      sh 'echo "</pre><body></html>" >> rtest.html'
    }
  }
 
  // print test results to job console
  def testReport =  readFile('rtest.html')
  echo "$testReport"

  // publish results
  publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, 
               keepAll: true, reportDir: '.', 
               reportFiles: 'rtest.html', 
               reportName: 'UI Regression Test Report', 
               reportTitles: 'UI Regression Test Report'])

  // An exit code on non-zero indicates something failed
  echo "Test Result Status: $status"
  
  if (status != 0) { 
    testStatus = 'FAILED'
  }
  else {
    testStatus = 'SUCCESS'
  }
  
  return testStatus
}
