#!/usr/bin/env groovy


/*
 *  Build, Test and Publish Java-based docker images.
 *
 *  Configurable parameters: 
 *
 *  dockerfile:     Name of dockerfile. Default: 'Dockerfile'
 *  buildContext:   Relative path to docker build context. Default: Jenkins $WORKSPACE
 *  overrideConfig: Override project Dockerfile and use template. Default: false
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

  def dockerRepo = 'folioci'

  // Defaults if not defined. 
  def dockerfile = config.dockerfile ?: 'Dockerfile'
  def buildContext = config.dockerDir ?: env.WORKSPACE
  def overrideConfig = config.overrideConfig ?: 'no'
  def publishMaster = config.publishMaster ?: 'yes'

  // default
  def buildArg = 'no'

  try { 
    dir("$buildContext") {
   
      // if 'override' is 'yes', create our own Dockerfile, otherwise
      // use project's Dockerfile

      if (overrideConfig ==~ /(?i)(Y|YES|T|TRUE)/) {
        def fatJar = "${env.name}-fat.jar"

        if (fileExists("target/$fatJar")) {
          echo "Fat jar appears to be: $fatJar"

          // use dockerfile template
          dockerFile = libraryResource 'org/folio/Dockerfile.javaModule'
          writeFile file: 'Dockerfile', text: "$dockerFile"
          buildArg = 'yes'
        }
        else {
          echo "Unable to locate Jar file for this project."
          echo "Trying fallback to local Dockerfile." 
        }
      }    
      else {
        if (fileExists("$dockerfile")) {
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

      if (buildArg == 'yes') {
        sh "docker build -t ${env.name}:${env.version} --build-arg='VERTICLE_FILE=${fatJar}' . "
      }
      else {
        sh "docker build -t ${env.name}:${env.version} ."
      }

      // Test container using container healthcheck
      if ((config.healthChk ==~ /(?i)(Y|YES|T|TRUE)/) && (config.healthChkCmd) {
        def runArgs = config.runArgs ?: ' ' 
        def healthChkCmd = config.healthChkCmd
        def dockerImage = "${env.name}:${env.version}"
        def health  = containerHealthCheck(dockerImage,healthChkCmd,runArgs)
          
        if (health != 'healthly') {  
          echo "Container health check failed."
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

      if ((env.BRANCH_NAME == 'master') && (publishMaster ==~ /(?i)(Y|YES|T|TRUE)/)) {
        // publish images to ci docker repo
        echo "Publishing Docker images"
        docker.withRegistry('https://index.docker.io/v1/', 'DockerHubIDJenkins') {
          sh "docker tag ${env.name}:${env.version} ${dockerRepo}/${env.name}:${env.version}"
          sh "docker tag ${env.name}:${env.version} ${dockerRepo}/${env.name}:latest"
          sh "docker push ${dockerRepo}/${env.name}:${env.version}"
          sh "docker push ${dockerRepo}/${env.name}:latest"
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
    sh "docker rmi ${dockerRepo}/${env.name}:${env.version} || exit 0"
    sh "docker rmi ${dockerRepo}/${env.name}:latest || exit 0"
  }

}
