#!/usr/bin/env groovy


/*
 * Send notifications based on build status string
 */

def call(String name, String version) {

  def fatJar = "${name}-fat.jar"
  def dockerFile = libraryResource 'org/folio/Dockerfile.javaModule'
  def dockerEntrypoint = libraryResource 'org/folio/docker-entrypoint.javaModule'

  sh """
  cat > .dockerignore << EOF
*
!Dockerfile
!docker*
!target/*.jar
EOF
  """

  echo "Fat Jar is: $fatJar"
  writeFile file: 'Dockerfile', text: "$dockerFile"
  writeFile file: 'docker-entrypoint.sh', text: "$dockerEntrypoint"
  
  sh "docker build --tag ${name}:${version} --build-arg='VERTICLE_FILE=${fatJar}' ."
  sh "docker tag ${name}:${version} ${name}:latest"  
  
}
