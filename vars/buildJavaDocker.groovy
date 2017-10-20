#!/usr/bin/env groovy


/*
 * Send notifications based on build status string
 */

def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def dockerRepo = 'folioci'
  def dockerImage = "${dockerRepo}/${env.name}:${env.version}"

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
        sh "docker build --tag ${dockerImage} --build-arg='VERTICLE_FILE=${fatJar}' . "
      }
      else {
        sh "docker build --tag ${dockerImage} ."
      }

      // Test container using container healthcheck
      if ((config.healthChk ==~ /(?i)(Y|YES|T|TRUE)/) && (config.healthChkCmd != null)) {
        def runArgs = config.runArgs ?: ' ' 
        def healthChkCmd = config.healthChkCmd
        def status  = containerHealthCheck(dockerImage,healthChkCmd,runArgs)
          
        if (status != 'healthly') {  
          echo "Container healthcheck failed"
          // throw error here 
        }
        else {
          echo "Container healthcheck passed"
        }
      }
      else {
        echo "No healthcheck configured. Skipping container healthcheck."
      }

      // publish image if master branch

      if ((env.BRANCH_NAME == 'master') && (publishMaster ==~ /(?i)(Y|YES|T|TRUE)/)) {
        // publish images to ci docker repo
        echo "Publishing Docker images"
        docker.withRegistry('https://index.docker.io/v1/', 'DockerHubIDJenkins') {
          sh "docker tag ${dockerRepo}/${env.name}:${env.version} ${dockerRepo}/${env.name}:latest"
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
