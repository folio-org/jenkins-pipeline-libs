#!/usr/bin/env groovy


/*
 * Send notifications based on build status string
 */

def call(String name, String version, String fatJar) {

  def dockerFile = libraryResource 'org.folio.Dockerfile.JavaModule'
  def dockerEntrypoint = libraryResource 'org.folio.dockerentrypoint.JavaModule'

  sh """
  cat > .dockerignore << EOF
*
!Dockerfile
!docker
!target/*.jar
EOF
  """

  sh "echo $dockerFile > ${env.WORKSPACE}/Dockerfile"
  sh "echo $dockerEntrypoint > ${env.WORKSPACE}/dockerentrypoint.sh"
  
  sh "docker build --tag ${name}:${version} --build-arg='VERTICLE_FILE=${fatJar}' ."
  sh "docker tag ${name}:${version} ${name}:latest"  
}
