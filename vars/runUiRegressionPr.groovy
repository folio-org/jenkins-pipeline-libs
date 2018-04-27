#!/usr/bin/env groovy


/*
 * Run UI Regression tests
 */

def call(String folioUser, String folioPassword, String folioUrl) {

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

      sh 'mkdir -p ci_reports'
      sh 'echo "<html><head><title>UI Regression Test Report</title></head>" > ci_reports/rtest.html'
      sh 'echo "<body><pre>" >> ci_reports/rtest.html'

      echo "Running UI Regression test against $folioUrl"
      // status = sh(script: "DEBUG=* DISPLAY=:2 yarn test >> ci_reports/rtest.html 2>&1", returnStatus:true)
     
      status = sh(script: "DISPLAY=:2 yarn test >> ci_reports/rtest.html 2>&1", returnStatus:true)

      sh 'echo "</pre><body></html>" >> ci_reports/rtest.html'
    }
  }
 
  // print test results to job console
  def testReport =  readFile('ci_reports/rtest.html')
  echo "$testReport"

  // publish results
  publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, 
               keepAll: true, reportDir: 'ci_reports', 
               reportFiles: 'rtest.html', 
               reportName: 'UI_Regression_Test_Report', 
               reportTitles: 'UI_Regression_Test_Report'])

  // An exit code on non-zero indicates something failed
  echo "Test Result Status: $status"
  return status
}
