#!/usr/bin/env groovy

def call(String apiTypes, String apiDirectories, String apiExcludes) {
  echo "Assessing API description files ..."
  sh 'mkdir -p ci'
  sh 'echo "<html><body><pre>" > ci/apiLint.html'

  def lintStatus = 0
  def errorMessage = 'Jenkinsfile configuration errors for doApiLint:'
  def types = apiTypes.replaceAll(/[, ]+/, " ").toUpperCase()
  def directories = apiDirectories.replaceAll(/[, ]+/, " ")
  def excludes = apiExcludes.replaceAll(/[, ]+/, " ")

  if (types == '') {
    lintStatus = 2
    errorMessage = "${errorMessage}\n" +
                   "apiTypes: Missing required property.\n" +
                   "    Space-separated list. One or more of: RAML OAS"
  }
  if (directories == '') {
    lintStatus = 2
    errorMessage = "${errorMessage}\n" +
                   "apiDirectories: Missing required property.\n" +
                   "    Space-separated list of directories to be searched."
  }

  if (lintStatus != 0) {
    sh "echo '${errorMessage}' >> ci/apiLint.html"
  }
  else {
    lintStatus = sh(script: "python3 /usr/local/bin/api_lint.py --loglevel info " +
                            "--types ${types} --directories ${directories} " +
                            "--excludes ${excludes} --output folio-api-docs " +
                            ">> ci/apiLint.html", returnStatus:true)
  }

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
    def message = "$lintReport\n\nNote: When those ERRORs are fixed, then any INFO and WARNING messages will be gone too. See Jenkins \"Artifacts\" tab for ApiLintReport."
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
