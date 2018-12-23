#!/usr/bin/env groovy

/*
 * Run 'raml-cop' on back-end modules that have declared RAML in api.yml
 */

def call() {
  echo "Assessing RAML and running 'raml-cop' ..."
  sh 'mkdir -p ci'
  sh 'echo "<html><body><pre>" > ci/lintRamlCop.html'

  def lintStatus = sh(returnStatus:true, script: 'python3 /usr/local/bin/lint_raml_cop.py -l info --validate-only >> ci/lintRamlCop.html')

  sh 'echo "</pre><body></html>" >> ci/lintRamlCop.html'

  def lintReport = readFile('ci/lintRamlCop.html')

  publishHTML([allowMissing: false, alwaysLinkToLastBuild: false,
               keepAll: true, reportDir: 'ci',
               reportFiles: 'lintRamlCop.html',
               reportName: 'LintRamlCopReport',
               reportTitles: 'Lint raml-cop Report'])

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
