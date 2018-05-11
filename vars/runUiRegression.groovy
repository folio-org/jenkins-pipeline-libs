#!/usr/bin/env groovy


/*
 * Run UI Regression tests
 */

def call(String uitestVer, String folioUrl) {

  def uitestImage = "folioci/ui-testing:${uitestVer}"
  def testStatus = ''

  sh 'mkdir -p ci'
  sh 'echo "<html><head><title>UI Regression Test Report</title></head>" > ci/rtest.html'
  sh 'echo "<body><pre>" >> ci/rtest.html'

  echo "Running UI Regression test image $uitestImage against $folioUrl"
  sh "docker pull $uitestImage"
  def returnStatus = sh(script: "docker run -i --rm -e \"FOLIO_UI_URL=${folioUrl}\" $uitestImage >> ci/rtest.html 2>&1", returnStatus:true)

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

  // An exit code on non-zero indicates something failed
  if (returnStatus != 0) { 
    testStatus = 'FAILED'
  }
  else {
    testStatus = 'SUCCESS'
  }
  
  return testStatus
}
