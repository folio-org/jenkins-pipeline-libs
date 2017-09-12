#!/usr/bin/env groovy


/*
 * Send notifications based on build status string
 */

def call(String name, String version) {

  def fatJar = "${name}-fat.jar"
  def dockerFile
  def dockerEntrypoint
  def buildArg

  echo "Fat Jar is: $fatJar"
 
  if (fileExists('.dockerignore')) {
      echo "Using existing .dockerignore"
  }
  else {
      echo "Creating .dockerignore"
      sh """
      cat > .dockerignore << EOF
*
!Dockerfile
!docker*
!target/*.jar
EOF
      """
  }

  if (fileExists('Dockerfile')) {
    echo "Found existing Dockerfile." 
    buildArg = 'no'
  }
  else {
    echo "Dockerfile not found.  Using Java template."
    dockerFile = libraryResource 'org/folio/Dockerfile.javaModule'
    writeFile file: 'Dockerfile', text: "$dockerFile"
    buildArg = 'yes'
    
  }

  if (fileExists('docker/docker-entrypoint.sh')) {
    echo "Found existing docker-entrypoint.sh script." 
  }
  else {
   echo "docker-entrypoint.sh not found. Using Java entrypoint template."
   dockerEntrypoint = libraryResource 'org/folio/docker-entrypoint.javaModule'
   writeFile file: 'docker-entrypoint.sh', text: "$dockerEntrypoint"
  }

  if (buildArg == 'yes') {
    sh "docker build --tag ${name}:${version} --build-arg='VERTICLE_FILE=${fatJar}' . "
  }
  else {
    sh "docker build --tag ${name}:${version} ."
  }
  
  sh "docker tag ${name}:${version} ${name}:latest"  
  
}
