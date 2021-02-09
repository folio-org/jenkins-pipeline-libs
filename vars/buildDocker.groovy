#!/usr/bin/env groovy


/*
 *  Build, Test and Publish docker images.
 *
 *  Configurable parameters: 
 *
 *  dockerfile:     Name of dockerfile. Default: 'Dockerfile'
 *  buildContext:   Relative path to docker build context. Default: Jenkins $WORKSPACE
 *  publishMaster:  Publish image to Docker repo (master branch only): Default: true
 *  healthChk:      Perform container health check with 'healthChkCmd' Default: false
 *  healthChkCmd:   Specify health check command.  Default:  none
 *  runArgs:  	    Additional container runtime arguments.  Default: none
 *     
 */

def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()


  // Defaults if not defined. 
  def dockerfile = config.dockerfile ?: 'Dockerfile'
  def buildContext = config.dockerDir ?: '.'
  def publishMaster = config.publishMaster ?: 'yes'

  try { 
    dir("${env.WORKSPACE}/${buildContext}") {
         
      // build docker image
      docker.withRegistry('https://docker.io/v2/', 'dockerhub-ci-pull-account') {
        sh "docker build --no-cache=true --pull=true -t ${env.name}:${env.version} ."
      }

      // Test container using container healthcheck
      if ((config.healthChk ==~ /(?i)(Y|YES|T|TRUE)/) && (config.healthChkCmd)) {

        def runArgs = config.runArgs ?: ' ' 
        def healthChkCmd = config.healthChkCmd
        def dockerImage = "${env.name}:${env.version}"
        def health = containerHealthCheck(dockerImage,healthChkCmd,runArgs)
          
        if (health != 'healthy') {  
          echo "Container health check failed: $health"
          sh 'exit 1' 
        }
        else {
          echo "Container health check passed."
        }
      }
      else {
        echo "No health check configured. Skipping container health check."
      }

      // publish image if master branch
      if ((env.BRANCH_NAME == 'master') ||
          (env.isRelease) &&
          (publishMaster ==~ /(?i)(Y|YES|T|TRUE)/)) {
        // publish images to ci docker repo
        echo "Publishing Docker images"
        docker.withRegistry('https://index.docker.io/v1/', 'DockerHubIDJenkins') {
          sh "docker tag ${env.name}:${env.version} ${env.dockerRepo}/${env.name}:${env.version}"
          sh "docker tag ${env.name}:${env.version} ${env.dockerRepo}/${env.name}:latest"
          sh "docker push ${env.dockerRepo}/${env.name}:${env.version}"
          sh "docker push ${env.dockerRepo}/${env.name}:latest"
        }
         // publish readme
        echo "Publish Readme Docker Hub"
        withCredentials([usernamePassword(credentialsId: 'DockerHubIDJenkins', passwordVariable: 'DOCKER_PASSWORD', usernameVariable: 'DOCKER_USERNAME')]) {
          writeFile file: 'dockerHubPublishMetadata.sh', text: libraryResource('org/folio/dockerHubPublishMetadata.sh')
          sh 'chmod +x dockerHubPublishMetadata.sh'
          sh "./dockerHubPublishMetadata.sh ${env.dockerRepo}/${env.name} ${env.projectName} ${env.projUrl}"
        }
      }

    } // end dir()

  } // end try

  catch (Exception err) {
    println(err.getMessage());
    throw err
  } 

  finally {
    echo "Clean up any temporary docker artifacts"
    sh "docker rmi ${env.name}:${env.version} || exit 0"
    sh "docker rmi ${env.name}:latest || exit 0"
    sh "docker rmi ${env.dockerRepo}/${env.name}:${env.version} || exit 0"
    sh "docker rmi ${env.dockerRepo}/${env.name}:latest || exit 0"
  }

}
