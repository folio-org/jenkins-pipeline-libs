#!/usr/bin/env groovy

/*
 * Assess the RAML JSON Schema "descriptions" for each property FOLIO-1447
 * For back-end modules that have declared RAML in api.yml
 */

def call() {
  echo "Assessing RAML JSON Schema descriptions ..."
  sh 'mkdir -p ci'
  sh 'echo "<html><body><pre>" > ci/lintRamlSchema.html'

  def lintStatus = sh(returnStatus:true, script: 'python3 /usr/local/bin/lint_raml_cop.py -l info --json-only >> ci/lintRamlSchema.html')

  sh 'echo "</pre><body></html>" >> ci/lintRamlSchema.html'

  def lintReport = readFile('ci/lintRamlSchema.html')

  publishHTML([allowMissing: false, alwaysLinkToLastBuild: false,
               keepAll: true, reportDir: 'ci',
               reportFiles: 'lintRamlSchema.html',
               reportName: 'LintRamlSchemaReport',
               reportTitles: 'Lint RAML JSON Schema Report'])

  sh 'rm -rf ci'

  if (lintStatus != 0) {
    echo "Issues detected:"
    echo "$lintReport"
    // PR
    if (env.CHANGE_ID) {
      // Requires https://github.com/jenkinsci/pipeline-github-plugin
      @NonCPS
      def comment = pullRequest.comment(lintReport)
    }
  }
  else {
    echo "No issues detected."
  }
}

