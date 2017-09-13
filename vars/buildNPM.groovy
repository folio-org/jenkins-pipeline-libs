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
        def package = readJSON file: 'package.json'
        env.name = package.name 
        env.version = package.version
        echo "Package Name: $env.name"
        echo "Package Version: $env.version"


        withNPM(npmrcConfig: 'npmrc-folioci') {
          // Artificially inflate the version of the npm package to designate 
          // it as a "snapshot"
          // sh 'npm version `/usr/local/bin/folioci_npmver.sh`'
          def folioci_npmver = libraryResource 'org/folio/folioci_npmver.sh'
          writeFile file: 'folioci_npmver.sh', text: folioci_npmver
          sh 'chmod +x folioci_npmver.sh'
          sh 'npm version `./folioci_npmver.sh`'
          

          // We should probably use the --production flag here, but we WANT more tests!
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
        if ( config.mvnDeploy ==~ /(?i)(Y|YES|T|TRUE)/ ) {
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

