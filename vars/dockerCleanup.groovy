#!/usr/bin/env groovy


/*
 *  Clean up local Docker artifacts 
 */

def call() {
  echo "Cleaning up temporary docker artifacts"
  sh "docker rmi ${env.name}:${env.version} || exit 0"
  sh "docker rmi ${env.name}:latest || exit 0"
  sh "docker rmi ${env.dockerRepo}/${env.name}:${env.version} || exit 0"
  sh "docker rmi ${env.dockerRepo}/${env.name}:latest || exit 0"
}
