#!/usr/bin/env groovy

def call(String dockerImage, String checkCmd, String runArgs) {  
   
   def timeout = '2s'
   def retries = 2
   def cidFile = "/tmp/${dockerImage}-${env.BUILD_NUMBER}.cid"
   def startupWaitRange = 1..40 // seconds
   def maxStartupWait = 40
   def health = ''

   try {
     echo "Testing $dockerImage image. Starting container.."
     sh """
     docker run -d --health-timeout=${timeout} --health-retries=${retries} \
       --health-cmd=${checkCmd} --cidfile $cidFile $dockerImage $runArgs
     """
   for (i = 0; i <maxStartupWait; i++) {
       health = sh(returnStdout: true, script: 'docker inspect `cat "$cidFile"` | jq -r \".[].State.Health.Status\"').trim()
       echo "Current Status: $status"
       if (health == 'starting') {
         sleep 1
       }
       else {
         break
       } 
     }
   }
   catch(Exception err) {
       health = 'error'
       throw err
   }
   finally {
     sh "docker stop `cat $cidFile` || exit 0"
     sh "docker rm  `cat $cidFile` || exit 0"
     return health
   }
} 
   

