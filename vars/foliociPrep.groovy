#!/usr/bin/groovy

//import org.folio.foliociCommands

def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  //def s = new org.folio.foliociCommands() 
  //s.setBuildDisplayName()
  sendNotifications 'STARTED'

}
 
  

  
