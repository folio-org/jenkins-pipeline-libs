#!/usr/bin/env groovy

def call(String apiDirectories, String apiExcludes) {
  echo "Assessing API schema files ..."
  sh 'mkdir -p ci'
  sh 'echo "<html><body><pre>" > ci/apiSchemaLint.html'

  def lintStatus = 0
  def errorMessage = 'Jenkinsfile configuration errors for doApiLint:'
  def directories = apiDirectories.replaceAll(/[, ]+/, " ")
  def excludes = apiExcludes.replaceAll(/[, ]+/, " ")

  if (directories == '') {
    lintStatus = 2
    errorMessage = "${errorMessage}\n" +
                   "apiDirectories: Missing required property.\n" +
                   "    Space-separated list of directories to be searched."
  }

  if (lintStatus != 0) {
    sh "echo '${errorMessage}' >> ci/apiSchemaLint.html"
  }
  else {
    lintStatus = sh(script: "python3 /usr/local/bin/api_schema_lint.py --loglevel info " +
                            "--directories ${directories} " +
                            "--excludes ${excludes} " +
                            ">> ci/apiSchemaLint.html", returnStatus:true)
  }

  sh 'echo "</pre><body></html>" >> ci/apiSchemaLint.html'

  def lintReport = readFile('ci/apiSchemaLint.html')

  publishHTML([allowMissing: false, alwaysLinkToLastBuild: false,
               keepAll: true, reportDir: 'ci',
               reportFiles: 'apiSchemaLint.html',
               reportName: 'ApiSchemaLintReport',
               reportTitles: 'API Schema Lint Report'])

  sh 'rm -rf ci'

  if (lintStatus != 0) {
    echo "Issues detected:"
    echo "$lintReport"
    def message = "$lintReport\n\nNote: When those ERRORs are fixed, then any INFO and WARNING messages will be gone too. See Jenkins \"Artifacts\" tab for ApiSchemaLintReport."
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
}
