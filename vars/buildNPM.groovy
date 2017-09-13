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
      }

      stage('Prep') {
        // right now, all builds are snapshots
        def Boolean snapshot = true

        if (snapshot == true) {
          def folioci_npmver = libraryResource('org/folio/folioci_npmver.sh')
          writeFile file: 'folioci_npmver.sh', text: folioci_npmver
          sh 'chmod +x folioci_npmver.sh'
          sh 'npm version `./folioci_npmver.sh`'
        }

        def json = readJSON(file: 'package.json')
        env.name = json.name 
        env.version = json.version
        echo "Package Name: $env.name"
        echo "Package Version: $env.version"
      }

      stage('SonarQube Scan') {
        withSonarQubeEnv('SonarCloud') {
          echo "Performing SonarQube scan" 
          def scannerHome = tool 'SonarQube Scanner'
          if (env.BRANCH_NAME == 'master') {
            sh """
            ${scannerHome}/bin/sonar-scanner 
                            -Dsonar.projectKey=folio-org:${env.name}
                            -Dsonar.projectName=${env.name}
                            -Dsonar.projectVersion=${env.version}
                            -Dsonar.sources=.
                            -Dsonar.organization=folio-org
            """
          }
          else { 
            // need to add some github stuff here 
            sh """
            ${scannerHome}/bin/sonar-scanner 
                            -Dsonar.projectKey=folio-org:${env.name}
                            -Dsonar.projectName=${env.name}
                            -Dsonar.projectVersion=${env.version}
                            -Dsonar.sources=.
                            -Dsonar.organization=folio-org
                            -Dsonar.analysis.mode=preview
            """
          }
        }
      }
      stage('NPM Build') {
        // We should probably use the --production flag here for releases
        sh 'npm install' 
      }


      if (( env.BRANCH_NAME == 'master' ) ||     
         ( env.BRANCH_NAME == 'jenkins-test' )) {

        stage('NPM Deploy') {
          echo "Deploying NPM packages to Nexus repository"
          // sh 'npm publish'
        }

        if (config.publishModDescriptor ==~ /(?i)(Y|YES|T|TRUE)/) {
          stage('Publish Module Descriptor') {
              echo "Publishing Module Descriptor to FOLIO registry"
                //def modDescriptor = 'ModuleDescriptor.json'
              // postModuleDescriptor("$modDescriptor","$env.name","$env.version") 
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

