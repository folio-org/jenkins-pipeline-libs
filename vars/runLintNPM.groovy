#!/usr/bin/env groovy


/*
 * Run 'yarn lint' on UI modules
 */


def call() { 
  stage('Lint') { 
    echo "Running 'yarn lint...'"
    sh 'mkdir -p ci'
    sh 'echo "<html><head><title>Lint Report</title></head>" > ci/lint.html'
    sh 'echo "<body><pre>" >> ci/lint.html'

    def lintStatus = sh(returnStatus:true, script: 'yarn lint 2>/dev/null 1>> ci/lint.html')

    sh 'echo "</pre><body></html>" >> ci/lint.html'
 
    def lintReport = readFile('ci/lint.html')
    

    publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, 
                 keepAll: true, reportDir: 'ci',
                 reportFiles: 'lint.html',
                 reportName: 'Lint',
                 reportTitles: 'Lint'])

    sh 'rm -rf ci'

    if (lintStatus != 0) { 
      echo "Lint errors detected:"
      echo "$lintReport"
      // PR
      if (env.CHANGE_ID) {
        // Requires https://github.com/jenkinsci/pipeline-github-plugin
        @NonCPS
        def comment = pullRequest.comment(lintReport) 
      }
    }
    else {
      echo "No lint errors detected."
    }
  }
}

