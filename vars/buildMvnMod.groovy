#!/usr/bin/groovy


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  node('folio-jenkins-slave-docker') {

    try {
      stage('Checkout') {
        deleteDir()
        currentBuild.displayName = "#${env.BUILD_NUMBER}-${env.JOB_BASE_NAME}"
        sendNotifications 'STARTED'

         checkout([
                 $class: 'GitSCM',
                 branches: scm.branches,
                 extensions: scm.extensions + [[$class: 'SubmoduleOption',
                                                       disableSubmodules: false,
                                                       parentCredentials: false,
                                                       recursiveSubmodules: true,
                                                       reference: '',
                                                       trackingSubmodules: false]],
                 userRemoteConfigs: scm.userRemoteConfigs
         ])

         echo "Checked out $env.BRANCH_NAME"
      }

      stage('Maven Build') {
        def mvn_artifact = readMavenPom().getArtifactId() 
        def mvn_version =  readMavenPom().getVersion()
        env.name = "$mvn_artifact"

        if (mvn_version ==~ /.*-SNAPSHOT$/) {
          echo "This is a snapshot"
          env.version = "${mvn_version}.${env.BUILD_NUMBER}"
        }
        else {
          env.version = "$mvn_version"
        }

        echo "Building Maven artifact: ${env.name} Version: ${env.version}"
            
        withMaven(jdk: 'OpenJDK 8 on Ubuntu Docker Slave Node',
                    maven: 'Maven on Ubuntu Docker Slave Node',
                    options: [junitPublisher(disabled: false,
                    ignoreAttachments: false),
                    artifactsPublisher(disabled: false)]) {

          sh 'mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent install'

        }
      }


      if (( env.BRANCH_NAME == 'master' ) ||     
         ( env.BRANCH_NAME == 'jenkins-test' )) {

        stage('SonarQube Scan') {
          withSonarQubeEnv('SonarCloud') {
            sh 'mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:3.3.0.603:sonar -Dsonar.organization=folio-org -Dsonar.verbose=true'
          }
        }
        if ( config.mvnDeploy ==~ /(?i)(Y|YES|T|TRUE)/ ) {
          echo "Deploying artifacts to Maven repository"
          withMaven(jdk: 'OpenJDK 8 on Ubuntu Docker Slave Node',
                    maven: 'Maven on Ubuntu Docker Slave Node',
                    options: [junitPublisher(disabled: true,
                    ignoreAttachments: false),
                    artifactsPublisher(disabled: true)]) {
            sh 'mvn -DskipTests deploy'
          }
        }
        if ( config.doDocker ==~ /(?i)(Y|YES|T|TRUE)/ ) {
          stage('Docker Build') {
            echo "Building Docker image $env.name:$env.version" 
            buildJavaModDocker("$env.name","$env.version") 
          }
          stage('Docker Publish') {
            echo "Publishing Docker"

            docker.withRegistry('https://index.docker.io/v1/', 'DockerHubIDJenkins') {
              if (version ==~ /.*-SNAPSHOT.*/) {
                env.repository = 'folioci'
              }
              else {
                env.repository = 'folioorg'
              }
              sh "docker tag ${env.name}:${env.version} ${env.repository}/${env.name}:${env.version}"
              sh "docker tag ${env.repository}/${env.name}:${env.version} ${env.repository}/${env.name}:latest"
              sh "docker push ${env.repository}/${env.name}:${env.version}"
              sh "docker push ${env.repository}/${env.name}:latest"
            } 
          }
          
        } 
        if (config.publishModDescriptor ==~ /(?i)(Y|YES|T|TRUE)/) {
          stage('Publish Module Descriptor') {
              echo "Publishing Module Descriptor to FOLIO registry"
              def modDescriptor = 'target/ModuleDescriptor.json'
              postModuleDescriptor("$modDescriptor","$env.name","$env.version") 
          }
        }
        if (config.publishAPI ==~ /(?i)(Y|YES|T|TRUE)/) {
          stage('Publish API Docs') {
          echo "Publishing API docs"
            sh "python3 /usr/local/bin/generate_api_docs.py -r $env.name -v -o folio-api-docs"
            sh 'aws s3 sync folio-api-docs s3://foliodocs/api'
          }
        }
      } 
    } // end try
    catch (Exception err) {
      currentBuild.result = 'FAILED'
      println(err.getMessage());
      echo "Build Result: $currentBuild.result"
      throw err
    
    }
    finally {
      echo "Clean up any temporary docker artifacts"
      sh "docker rmi ${env.name}:${env.version} || exit 0"
      sh "docker rmi ${env.name}:latest || exit 0"
      sh "docker rmi ${env.repository}/${env.name}:${env.version} || exit 0"
      sh "docker rmi ${env.repository}/${env.name}:latest || exit 0"

      sendNotifications currentBuild.result
      
    }
  } //end node
    
} 

