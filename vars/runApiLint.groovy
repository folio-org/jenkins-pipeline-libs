#!/usr/bin/env groovy

def call(String apiTypes, String apiDirectories, String apiExcludes) {
  echo "Assessing API definition files ..."
  sh 'mkdir -p ci'
  sh 'echo "<html><body><pre>" > ci/apiLint.html'

  def lintStatus = sh(script: "python3 /usr/local/bin/api_lint.py --loglevel info " +
                              "--types ${apiTypes} --directories ${apiDirectories} " +
                              "--excludes ${apiExcludes} --output folio-api-docs " +
                              ">> ci/apiLint.html", returnStatus:true)

  sh 'echo "</pre><body></html>" >> ci/apiLint.html'

  def lintReport = readFile('ci/apiLint.html')

  publishHTML([allowMissing: false, alwaysLinkToLastBuild: false,
               keepAll: true, reportDir: 'ci',
               reportFiles: 'apiLint.html',
               reportName: 'ApiLintReport',
               reportTitles: 'API Lint Report'])

  sh 'rm -rf ci'

  if (lintStatus != 0) {
    echo "Issues detected:"
    echo "$lintReport"
    def message = "$lintReport\n\nNote: When those errors are fixed, then the INFO messages will be gone too. See Jenkins \"Artifacts\" tab."
    // PR
    if (env.CHANGE_ID) {
      // Requires https://github.com/jenkinsci/pipeline-github-plugin
      @NonCPS
      def comment = pullRequest.comment(message)
    }
  }
  else {
    echo "No issues detected."
  }

  // Publish the generated configuration file.
  if (env.BRANCH_NAME == 'master') {
    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                     accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                     credentialsId: 'jenkins-aws',
                     secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
      sh 'aws s3 sync folio-api-docs s3://foliodocs/api'
    }
  }
}
