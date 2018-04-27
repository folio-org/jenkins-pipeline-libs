
def call() { 
  stage('Lint') { 
    echo "Running 'yarn lint...'"
    sh 'mkdir -p ci'
    def lintStatus = sh(returnStatus:true, script: 'yarn lint 2>/dev/null 1> ci/lint.html')
    def lintReport = readFile('lint.html')
    echo "$lintReport"
  
    publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, 
                 keepAll: true, reportDir: 'ci',
                 reportFiles: 'lint.html',
                 reportName: 'Lint',
                 reportTitles: 'Lint'])

    sh 'rm -rf ci'

    // PR
    if (env.CHANGE_ID) {
      if (lintStatus != 0) { 
        // Requires https://github.com/jenkinsci/pipeline-github-plugin
        pullRequest.comment(lintReport) 
      }
      else {
        pullRequest.commend('No lint errors detected')
      }
    }
  }
}

