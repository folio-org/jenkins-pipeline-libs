#!/usr/bin/groovy


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  node('jenkins-slave-folio-testing') {

    try {
      stage('Checkout') {
        deleteDir()
        currentBuild.displayName = "#${env.BUILD_NUMBER}-${env.JOB_BASE_NAME}"
        // sendNotifications 'STARTED'

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

        def proj_name = sh(returnStdout: true, script: 'git config remote.origin.url | awk -F \'/\' \'{print $5}\' | sed -e \'s/\\.git//\'').trim()
        env.project_name = proj_name
        echo "$env.project_name"

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

          sh 'mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent install -DskipTests'

        }
      }

      if (config.doDocker) {
        stage('Docker Build') {
          echo "Building Docker image for $env.name:$env.version" 
          config.doDocker.delegate = this
          config.doDocker.resolveStrategy = Closure.DELEGATE_FIRST
	  config.doDocker.call()
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
          stage('Maven Deploy') {
            echo "Deploying artifacts to Maven repository"
            withMaven(jdk: 'OpenJDK 8 on Ubuntu Docker Slave Node',
                      maven: 'Maven on Ubuntu Docker Slave Node',
                      options: [junitPublisher(disabled: true,
                      ignoreAttachments: false),
                      artifactsPublisher(disabled: true)]) {
              sh 'mvn -DskipTests deploy'
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
            sh "python3 /usr/local/bin/generate_api_docs.py -r $env.project_name -v -o folio-api-docs"
            sh 'aws s3 sync folio-api-docs s3://foliodocs/api'
          }
        }
      } 
      else {
        if (env.CHANGE_ID) {
          echo "PR request: $env.CHANGE_ID"
          stage('SonarQube Scan') {
            withCredentials([[$class: 'StringBinding', 
                              credentialsId: '6b0ebf62-3a12-4e6b-b77e-c45817b5791b', 
                              variable: 'GITHUB_ACCESS_TOKEN']]) {
              withSonarQubeEnv('SonarCloud') {
                sh "mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:3.3.0.603:sonar " +
                       "-Dsonar.organization=folio-org -Dsonar.verbose=true " +
                       "-Dsonar.analysis.mode=preview " +
                       "-Dsonar.github.pullRequest=${env.CHANGE_ID} " +
                       "-Dsonar.github.repository=folio-org/${env.project_name} " +
                       "-Dsonar.github.oauth=${GITHUB_ACCESS_TOKEN}"
              }
            }
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
      // sendNotifications currentBuild.result
    }
  } //end node
    
} 

