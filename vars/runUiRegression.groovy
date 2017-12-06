#!/usr/bin/env groovy


/*
 * Run UI Regression tests
 */

def call(String uitestVer, String folioUrl) {

  def uitestImage = "folioci/ui-testing:${uitestVer}"
  def testStatus = ''

  sh 'echo "<html><head><title>UI Regression Test Report</title></head>" > rtest.html'
  sh 'echo "<body><pre>" >> rtest.html'

  echo "Running UI Regression test image $uitestImage against $folioUrl"
  sh "docker pull $uitestImage"
  def returnStatus = sh(script: "docker run -i --rm -e \"FOLIO_UI_URL=${folioUrl}\" $uitestImage >> rtest.html 2>&1", returnStatus:true)

  sh 'echo "</pre><body></html>" >> rtest.html'
 
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
  if (returnStatus != 0) { 
    testStatus = 'FAILED'
  }
  else {
    testStatus = 'SUCCESS'
  }
  
  return testStatus
}
