#!/usr/bin/env groovy

def call(String apiTypes, String apiDirectories, String apiExcludes) {
  echo "Generating docs from API description files ..."
  sh 'mkdir -p ci'
  sh 'echo "<html><body><pre>" > ci/apiDoc.html'

  def apiDocStatus = 0
  def errorMessage = 'Jenkinsfile configuration errors for doApiDoc:'
  def types = apiTypes.replaceAll(/[, ]+/, " ").toUpperCase()
  def directories = apiDirectories.replaceAll(/[, ]+/, " ")
  def excludes = apiExcludes.replaceAll(/[, ]+/, " ")

  if (types == '') {
    apiDocStatus = 2
    errorMessage = "${errorMessage}\n" +
                   "apiTypes: Missing required property.\n" +
                   "    Space-separated list. One or more of: RAML OAS"
  }
  if (directories == '') {
    apiDocStatus = 2
    errorMessage = "${errorMessage}\n" +
                   "apiDirectories: Missing required property.\n" +
                   "    Space-separated list of directories to be searched."
  }

  if (apiDocStatus != 0) {
    sh "echo '${errorMessage}' >> ci/apiDoc.html"
  }
  else {
    def optionRelease = ''
    if (env.isRelease) {
      def version = env.version
      def versionMinor = version.replaceAll(/\.[0-9]+$/, '')
      optionRelease = "--version $versionMinor"
    }
    apiDocStatus = sh(script: "python3 /usr/local/bin/api_doc.py --loglevel info " +
                    "--types ${types} --directories ${directories} " +
                    "--excludes ${excludes} --output folio-api-docs ${optionRelease}" +
                    ">> ci/apiDoc.html",
                    returnStatus:true)
  }

  sh 'echo "</pre><body></html>" >> ci/apiDoc.html'

  def apiDocReport = readFile('ci/apiDoc.html')

  publishHTML([allowMissing: false, alwaysLinkToLastBuild: false,
               keepAll: true, reportDir: 'ci',
               reportFiles: 'apiDoc.html',
               reportName: 'ApiDocReport',
               reportTitles: 'API Doc Report'])

  sh 'rm -rf ci'

  if (apiDocStatus != 0) {
    echo "Issues detected:"
    echo "$apiDocReport"
  }
  else {
    echo "No issues detected. Publishing API docs."
    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                     accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                     credentialsId: 'jenkins-aws',
                     secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
      sh 'aws s3 sync folio-api-docs s3://foliodocs/api'
    }
  }
}
