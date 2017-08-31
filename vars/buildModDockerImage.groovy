#!/usr/bin/env groovy


/*
 * Send notifications based on build status string
 */

def call(String name, String version) {
  
  sh """
  cat > .dockerignore << EOF
*
!Dockerfile
!docker
!target/*.jar
EOF
  """
  sh "docker build -t ${name}:$version} ."
  sh "docker tag ${name}:${version} ${name}:latest"  
}
