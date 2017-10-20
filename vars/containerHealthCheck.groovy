#!/usr/bin/env groovy

def call(String dockerImage, String checkCmd, String runArgs = '') {  
   
   def timeout = '2s'
   def retries = 2
   def cidFile = "/tmp/${dockerImage}-${env.BUILD_NUMBER}.cid"
   def startupWaitRange = 1..40 // seconds
   def health = ''

   try {
     echo "Testing $dockerImage image. Starting container.."
     sh """
        docker run -d --health-timeout=${timeout} --health-retries=${retries} \
         --health-cmd=${checkCmd} --cidfile $cidFile $dockerImage $runArgs"
        """

     while(startupWaitRange) {
       status = sh(returnStdout: true, script: "docker inspect `cat $cidFile` | jq -r \".[].State.Health.Status\"").trim()
       println "Current Status: $status"
       if (status == 'starting') {
         sleep 1
       }
       else {
         break
       } 
     }
   }
   catch(Exception err) {
       status = 'error'
       throw err
   }
   finally {
     sh "docker stop `cat $cidFile`" || exit 0
     sh "docker rm  `cat $cidFile`" || exit 0
     return health
   }
} 
   

