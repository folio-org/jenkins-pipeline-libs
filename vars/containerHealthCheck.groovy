#!/usr/bin/env groovy

def call(String dockerImage, String checkCmd, String runArgs) {  
   
   def timeout = '3s'
   def retries = 3
   def cidFile = "${dockerImage}-${env.BUILD_NUMBER}.cid"
   def maxStartupWait = 60
   def health = ''

   try {
     echo "Testing $dockerImage image. Starting container..."

     // exit 1 since 'docker run' can return a variety of non-zero status codes.
     sh """
      docker run -d --health-timeout=${timeout} --health-retries=${retries} \
             --health-cmd='${checkCmd}' --cidfile $cidFile $dockerImage $runArgs || exit 1
     """
      
     def cid = readFile(cidFile)

     for (i = 0; i <maxStartupWait; i++) {

       health = sh(returnStdout: true, 
                   script: "docker inspect $cid | jq -r \".[].State.Health.Status\"").trim()

       echo "Current Status: $health"

       if (health == 'starting') {
         sleep 1
       }
       else {
         break
       } 
     }
   }
   catch(Exception err) {
       throw err
   }
   finally {
     sh "docker stop `cat $cidFile` || exit 0"
     sh "docker rm  `cat $cidFile` || exit 0"
     sh "rm -f /tmp/${cidFile} || exit 0"
     return health
   }
} 
   

