#!/usr/bin/env groovy


/*
 * Run UI Regression tests
 */

def call(String uitestVer, String folioUrl) {

  def uitestImage = "folioci/ui-testing:${uitestVer}"

  echo "<html><head><title>UI Regression Test Report</title></head>" > rtest.out
  echo "<body><pre>" >> rtest.html

  sh "docker pull $uitestImage"
  def testStatus = sh(script: "docker run -i --rm -e \"FOLIO_UI_URL=${folioUrl}\" $uitestImage >> rtest.html 2>&1", returnStatus:true)

  echo "</pre><body></html>" >> rtest.html

  publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, 
               keepAll: true, reportDir: '.', 
               reportFiles: 'rtest.html', 
               reportName: 'UI Regression Test Report', 
               reportTitles: 'UI Regression Test Report'])

  return testStatus
}
