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

      stage('NPM Build') {
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

        withNPM(npmrcConfig: 'npmrc-folioci') {
          // We should probably use the --production flag here for releases
          sh 'npm install' 
        }
      }


      if (( env.BRANCH_NAME == 'master' ) ||     
         ( env.BRANCH_NAME == 'jenkins-test' )) {

        stage('SonarQube Scan') {
          withSonarQubeEnv('SonarCloud') {
            echo "Performing SonarQube scan" 
          }
        }
        if ( config.npmDeploy ==~ /(?i)(Y|YES|T|TRUE)/ ) {
          stage('NPM Deploy') {
            echo "Deploying NPM packages to Nexus repository"
            // withNPM(npmrcConfig: 'npmrc-folioci') {
            //  sh 'npm publish'
            //}
          }
        }
        if (config.publishModDescriptor ==~ /(?i)(Y|YES|T|TRUE)/) {
          stage('Publish Module Descriptor') {
              echo "Publishing Module Descriptor to FOLIO registry"
              // def modDescriptor = 'target/ModuleDescriptor.json'
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

