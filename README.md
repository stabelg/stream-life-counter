Before running check the configuration of the path:

life.counter.path=//Users//{YOUR USERNAME}//Stream//

Inside src/main/resources/application.properties

This is where the files inside the Drive should be:

How to run:

Open terminal and run

1 - cd {FOLDER}/stream-life-counter/

2 - ./gradlew bootRun

Wait until it finishes running, check your local IP sample: 192.168.1.146
Now on your life app, CLICK AND HOLD "SETTINGS" button until a pop up shows Stream mode enabled
Go to settings, put your IP + the port that the terminal said usually ":8080"
in case the port was busy check the terminal for this line:

- Tomcat initialized with port 8080 (http)

This is the port number you should use, try changing the life, heroes and starting the timer.
