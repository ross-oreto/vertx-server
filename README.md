### Vertx Server
Http server that runs as a vertx verticle written in groovy

Features:
- Easily configurable and auto reloads when conf files are modified.
- Easy to start, stop, and run with hot swapping using ant tasks.
- Uses slf4j log4j2 implementation to allow easy swapping of the logging system.
- Easy to extend and override.
- Web page to view and modify the application configuration.
- Web page to view log files.

#### Requirements (sdkman can install and manage all these)
- jdk 8
- ant
- vertx

#### Running (access at http://localhost:9091/admin)
- run in redeploy mode
```
ant run
```
- start the standalone jar
```
ant start
```
- stop and re-run in redeploy mode
```
ant rerun
```
- stop and re-start the standalone jar
```
ant restart
```
- stop the running vert
```
ant stop
```
- run integration tests
```
ant test
```
- build the standalone jar
```
ant build-jar
```

application starts on the port specified in the configuration.


