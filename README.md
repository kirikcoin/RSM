# Redis Session Manager

# Overview

Stores HTTP sessions in Redis. This results in making application:

- Fault-tolerant. 
As all the sessions are persisted, no changes will be lost on application server restarts.

- Scalable. 
Data can be shared across multiple web-servers (no sticky sessions),

- Distributable across different platforms. 
Internal session representation is completely platform-agnostic and 
serialized using [MessagePack](http://msgpack.org). This allows to share user sessions between 
different types of services (e.g. NodeJS site and Java REST APIs).

# Building

To build fat JAR with all the dependencies embedded, run

    ./gradlew shadowJar
    

# Configuration

## Tomcat v8.5+

- Copy resulting JAR, `rsm-tc8-<version>-all.jar` into `$CATALINA_BASE/lib`.
Note that it cannot be deployed with the web application itself and **has to** be placed along with 
Tomcat's internal libraries.
  
- Set up `context.xml` in web application to use persisted sessions:

    <Context>
    
      <Valve className="mobi.eyeline.rsm.tc8.RedisSessionHandlerValve"/>
      
      <Manager className="mobi.eyeline.rsm.tc8.RedisSessionManager"
               dbUrl="redis://localhost:6379"
               persistenceStrategy="always"
      />
    
    </Context>
    
### Manager options

- `dbUrl`, required. URL to the Redis instance. 
Sentinel and Redis Cluster are currently not supported.

- `persistenceStrategy`, optional. Defines if session should be saved back to Redis
on request completion. 
Possible values:

  - `always` -- save always,
  - `on_change` -- save only if the implementation determines the session contents have changed.

Defaults to `always`.