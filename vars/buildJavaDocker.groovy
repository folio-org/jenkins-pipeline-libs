#!/usr/bin/env groovy


/*
 *  Build, Test and Publish Java-based docker images.
 *
 *  Configurable parameters:
 *
 *  dockerfile:     Name of dockerfile. Default: 'Dockerfile'
 *  buildContext:   Relative path to docker build context. Default: Jenkins $WORKSPACE
 *  overrideConfig: Override project Dockerfile and use template. Default: false
 *  publishMaster:  Publish image to Docker repo (mainline branch only): Default: true
 *  publishPreview: Publish image to Preview Docker repo (Pull Request only): Default: false
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
  def buildContext = config.dockerDir ?: env.WORKSPACE

  def overrideConfig = config.overrideConfig ?: false
  if (overrideConfig ==~ /(?i)(Y|YES|T|TRUE)/) { overrideConfig = true }
  if (overrideConfig ==~ /(?i)(N|NO|F|FALSE)/) { overrideConfig = false}

  def publishMaster = config.publishMaster ?: true
  if (publishMaster ==~ /(?i)(Y|YES|T|TRUE)/) { publishMaster = true }
  if (publishMaster ==~ /(?i)(N|NO|F|FALSE)/) { publishMaster = false}

  def publishPreview = config.publishPreview ?: false
  if (publishPreview ==~ /(?i)(Y|YES|T|TRUE)/) { publishPreview = true }
  if (publishPreview ==~ /(?i)(N|NO|F|FALSE)/) { publishPreview = false}

  def healthChk = config.healthChk ?: false
  if (healthChk ==~ /(?i)(Y|YES|T|TRUE)/) { healthChk = true }
  if (healthChk ==~ /(?i)(N|NO|F|FALSE)/) { healthChk = false}

  // default
  def Boolean buildArg = false

  try {
    dir("$buildContext") {

      // if 'overrideConfig' is true, create our own Dockerfile, otherwise
      // use project's Dockerfile
      if (overrideConfig) {
        def fatJar = "${env.name}-fat.jar"

        if (fileExists("target/$fatJar")) {
          echo "Fat jar appears to be: $fatJar"

          // use dockerfile template
          dockerFile = libraryResource 'org/folio/Dockerfile.javaModule'
          writeFile file: 'Dockerfile', text: "$dockerFile"
          buildArg = true
        }
        else {
          echo "Unable to locate Jar file for this project."
          echo "Trying fallback to local Dockerfile."
        }
      }
      else {
        if (fileExists(dockerfile)) {
          echo "Found existing Dockerfile."
        }
        else {
          echo "No Dockerfile called $dockerfile found in $buildContext"
        }
      }

      // create a .dockerignore file if one does not exist.

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

      // build docker image
      docker.withRegistry('https://docker.io/v2/', 'dockerhub-ci-pull-account') {
        if (buildArg) {
          sh "docker build --pull=true --no-cache=true -t ${env.name}:${env.version} --build-arg='VERTICLE_FILE=${fatJar}' . "
        }
        else {
          sh "docker build --pull=true --no-cache=true -t ${env.name}:${env.version} ."
        }
      }
      // Test container using container healthcheck
      if ( healthChk && config.healthChkCmd ) {

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

      // publish image if mainline branch
      if ( env.isRelease || (env.BRANCH_IS_PRIMARY && publishMaster) ) {
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
      } else if (env.CHANGE_ID && publishPreview) {
        echo "Publishing Preview Docker images"
        def previewId = "${env.bareVersion}.${env.CHANGE_ID}.${env.BUILD_NUMBER}"
        docker.withRegistry('https://docker-registry.ci.folio.org/v2/', 'jenkins-nexus')  {
          sh "docker tag ${env.name}:${env.version} docker-registry.ci.folio.org/${env.name}:${previewId}"
          sh "docker push docker-registry.ci.folio.org/${env.name}:${previewId}"
          sh "docker rmi docker-registry.ci.folio.org/${env.name}:${previewId}"
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
    updateEurekaFile.Info("${env.name}", "${env.version}")
  }

}
