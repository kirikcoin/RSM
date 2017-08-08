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

- Copy resulting JAR, `rsm-tc8-<version>-all.jar` into `$CATALINA_HOME/lib`.
Note that it cannot be deployed with the web application itself and **has to** be placed along with 
Tomcat's internal libraries.
  
- Set up `context.xml` in web application to use persisted sessions:

      <Context>
        <Manager className="mobi.eyeline.rsm.tc8.RedisSessionManager"
                 dbUrl="redis://localhost:6379"
                 persistenceStrategy="always"
        />
      </Context>
      
### Optimizing for Tomcat authenticator valve

In case one of Tomcat's default authentication valves is used, it might be a good idea to 
check for ignored URLs (see `skipUrls` parameter below) before authentication checks.
This will reduce the number of unnecessary Redis calls.

To do this, register authentication and request filtering valves manually:
 
      <Context>      
        <Valve className="mobi.eyeline.rsm.tc8.RedisSessionHandlerValve"/>
        <Valve className="org.apache.catalina.authenticator.FormAuthenticator"/>
 
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

- `skipUrls`, optional. If set, should contain Java regular expression.
  For any request URIs matching this expression session won't be created and/or loaded, saved etc. 
  (just as if request contained no session cookie, even if it actually had). 
  This might be useful for serving static resources, performance-wise.
  
  By default no URIs are skipped.
  
  **Example**: making requests to `.js` and `.js.faces` resources 
  (possibly ending with `?ln=...` query string) session-less.
 
      .*\.(js|js\.faces)(\?ln=.*)?$
      
- `skipAttributes`, optional. If set, should contain Java regular expression.
  For matching attribute names, `session.setAttribute(key, value)` calls will be ignored.
   
  This might be used to filter out unnecessary attributes set by third-party libraries, which 
  otherwise will take space in Redis storage. 
  By default no attributes are filtered and `setAttribute` call behaves as usual.

# Debugging

## Show session contents

Thanks to embedded Lua `cmsgpack` module, use the following script in Redis CLI:

    eval "return {cmsgpack.unpack(redis.call('GET', ARGV[1]))};" 0 <Session ID>
    
Example:

    127.0.0.1:6379> eval "return {cmsgpack.unpack(redis.call('GET', ARGV[1]))};" 0 DBD6DD67314082987E33590E0EDB2B8C
     1) (integer) -3869785235283313664
     2) (integer) 1502092058281
     3) (integer) 1502092058281
     4) (integer) 3600
     5) (nil)
     6) (integer) 1
     7) (integer) 1502094647867
     8) "DBD6DD67314082987E33590E0EDB2B8C"
     9) "tester"
     10)  1) "ADMIN"
          2) "CLIENTS"
     ... output truncated ...
 
## Logs

To enable verbose logging, append the following line to `$CATALINA_HOME/logging.properties`:

    mobi.eyeline.rsm.level = FINEST
    
Note that for levels lower than `FINE` verbosity level of output handler should also be changed, e.g.

    java.util.logging.ConsoleHandler.level = FINEST
 