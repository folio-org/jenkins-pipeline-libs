#!/usr/bin/env groovy

/*
 * If the module declares doUploadApidocs, then it will also be providing
 * API documentation files that are generated during build-time.
 * Collect and upload to their foliodocs/api S3 space.
 */

def call(String buildType) {
  def inputDir = ''
  if (buildType == 'mvn') {
    inputDir = 'target/apidocs'
  }
  else {
    echo "ERROR: The buildType '$buildType' is not yet handled (FOLIO-3008)."
    return
  }
  def uploadApidocsBaseDir = "ci/uploadApidocs/${env.name}"

  if (env.isRelease) {
    def version = env.version
    def versionMinor = version.replaceAll(/\.[0-9]+$/, '')
    uploadApidocsBaseDir = "ci/uploadApidocs/${env.name}/$versionMinor"
  }

  if (fileExists("$inputDir")) {
    def uploadApidocsDir = "$uploadApidocsBaseDir/u"
    sh "mkdir -p $uploadApidocsDir"
    sh "ls $inputDir/*.* | sed 's|^.*/||' > $uploadApidocsDir/files-upload.txt"
    sh "cp $inputDir/*.* $uploadApidocsDir"
    sh "cat $uploadApidocsDir/files-upload.txt"
    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
      accessKeyVariable: 'AWS_ACCESS_KEY_ID',
      credentialsId: 'jenkins-aws',
      secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
        sh 'aws s3 sync ci/uploadApidocs s3://foliodocs/api'
    }
    sh 'rm -rf ci/uploadApidocs'
  }
  else {
    echo "ERROR: Specified build directory '$inputDir' does not exist."
  }
}
