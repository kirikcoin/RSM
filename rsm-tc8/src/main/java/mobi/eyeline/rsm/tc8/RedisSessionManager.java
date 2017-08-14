package mobi.eyeline.rsm.tc8;

import mobi.eyeline.rsm.GenericSessionManager;
import mobi.eyeline.rsm.PersistenceStrategy;
import mobi.eyeline.rsm.model.PersistedSessionMetadata;
import mobi.eyeline.rsm.storage.RedisStorageClient;
import mobi.eyeline.rsm.storage.StorageClient;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Session;
import org.apache.catalina.Valve;
import org.apache.catalina.session.ManagerBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;


public class RedisSessionManager
    extends ManagerBase
    implements Lifecycle, GenericSessionManager<RedisSession> {

  private final Log log = LogFactory.getLog(RedisSessionManager.class);

  private final byte[] NULL_SESSION = "null".getBytes();

  private StorageClient storageClient;
  private RedisSessionSerializer serializer;

  // Current request context.
  private final ThreadLocal<RedisSession> currentSession = new ThreadLocal<>();
  private final ThreadLocal<PersistedSessionMetadata> currentSessionSerializationMetadata = new ThreadLocal<>();
  private final ThreadLocal<String> currentSessionId = new ThreadLocal<>();
  private final ThreadLocal<Boolean> currentSessionIsPersisted = new ThreadLocal<>();


  //
  // BEGIN Manager configuration.
  //

  private String dbUrl;
  private int timeout;

  private PersistenceStrategy persistenceStrategy = PersistenceStrategy.ALWAYS;

  private Pattern skipUrls;
  private Pattern skipAttributes;

  @SuppressWarnings("unused")
  public String getDbUrl()            { return dbUrl; }
  @SuppressWarnings("unused")
  public void setDbUrl(String dbUrl)  { this.dbUrl = dbUrl; }

  @SuppressWarnings("unused")
  public int getTimeout()             { return timeout; }
  @SuppressWarnings("unused")
  public void setTimeout(int timeout) { this.timeout = timeout; }

  @SuppressWarnings("unused")
  public void setPersistenceStrategy(String strategy) { persistenceStrategy = PersistenceStrategy.fromName(strategy); }
  @SuppressWarnings("unused")
  public String getPersistenceStrategy()              { return persistenceStrategy.name(); }

  @SuppressWarnings("unused")
  public void setSkipUrls(String pattern)   { this.skipUrls = pattern == null ? null : Pattern.compile(pattern); }
  @SuppressWarnings("unused")
  public String getSkipUrls()               { return skipUrls == null ? null : skipUrls.pattern(); }

  @SuppressWarnings("unused")
  public void setSkipAttributes(String pattern)   { this.skipAttributes = pattern == null ? null : Pattern.compile(pattern); }
  @SuppressWarnings("unused")
  public String getSkipAttributes()               { return skipAttributes == null ? null : skipAttributes.pattern(); }

  //
  // END Manager configuration.
  //


  @Override
  public Pattern getSkipUrlsPattern()                { return skipUrls; }

  @Override
  public Pattern getSkipAttributesPattern()   { return skipAttributes; }

  @Override
  public boolean doSaveImmediate() {
    // TODO: implement this mode.
    return false;
  }

  @Override
  public boolean doSaveAlways() {
    return persistenceStrategy == PersistenceStrategy.ALWAYS;
  }

  @Override
  public int getRejectedSessions() {
    return 0;
  }

  @Override public void load() { /* Nothing here */ }
  @Override public void unload() { /* Nothing here */ }

  @Override
  protected synchronized void startInternal() throws LifecycleException {
    log.info("Session manager starting...");
    super.startInternal();
    setState(LifecycleState.STARTING);

    initValve();
    serializer = initializeSerializer();
    initializeDatabaseConnection();

    getContext().setDistributable(true);

    log.info("Session manager started OK");
  }

  private void initValve() {
    final Pipeline pipeline = getContext().getPipeline();

    for (Valve valve : pipeline.getValves()) {
      if (valve instanceof RedisSessionHandlerValve) {
        ((RedisSessionHandlerValve) valve).setManager(this);
        return;
      }
    }

    pipeline.addValve(new RedisSessionHandlerValve(this));
  }

  private int getSessionTimeoutSeconds() {
    return getContext().getSessionTimeout() * 60;
  }

  @Override
  protected synchronized void stopInternal() throws LifecycleException {
    if (log.isDebugEnabled()) {
      log.debug("Stopping");
    }

    setState(LifecycleState.STOPPING);

    try {
      if (storageClient != null) {
        storageClient.close();
      }

    } catch (Exception e) {
      if (log.isDebugEnabled()) {
        log.debug("Failed closing Redis connection", e);
      }
    }

    super.stopInternal();
  }

  @Override
  public Session createSession(String requestedSessionId) {
    if (RedisSessionHandlerValve.shouldSkipSession()) {
      return null;
    }

    RedisSession session = null;

    final String sessionId = generateSessionId(requestedSessionId);

    if (sessionId != null) {
      session = createEmptySession();
      session.setNew(true);
      session.setValid(true);
      session.setCreationTime(System.currentTimeMillis());
      session.setMaxInactiveInterval(getSessionTimeoutSeconds());
      session.setId(sessionId);
      session.tellNew();
    }

    currentSession.set(session);
    currentSessionId.set(sessionId);
    currentSessionIsPersisted.set(false);
    currentSessionSerializationMetadata.set(new PersistedSessionMetadata());

    if (session != null) {
      try {
        saveInternal(session, true);

      } catch (IOException e) {
        log.error("Error saving newly created session: " + e.getMessage());
        currentSession.set(null);
        currentSessionId.set(null);
        session = null;
      }
    }

    return session;
  }

  /**
   * Ensure generation of a unique session identifier.
   */
  private String generateSessionId(String requestedSessionId) {
    String sessionId;

    try {
      if (requestedSessionId != null) {
        sessionId = requestedSessionId;
        if (!storageClient.setIfAbsent(sessionId, NULL_SESSION).get()) {
          sessionId = null;
        }
      } else {
        do {
          sessionId = generateSessionId();
        } while (!storageClient.setIfAbsent(sessionId, NULL_SESSION).get());
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return sessionId;
  }

  @Override
  public RedisSession createEmptySession() {
    return new RedisSession(this);
  }

  @Override
  public void add(Session session) {
    try {
      save((RedisSession) session, false);

    } catch (IOException e) {
      throw new RuntimeException("Failed saving session", e);
    }
  }

  @Override
  public Session findSession(String id) throws IOException {
    if (RedisSessionHandlerValve.shouldSkipSession()) {
      return null;
    }

    if (id == null) {
      currentSessionIsPersisted.set(false);
      currentSession.set(null);
      currentSessionSerializationMetadata.set(null);
      currentSessionId.set(null);
      return null;

    } else if (id.equals(currentSessionId.get())) {
      return currentSession.get();

    } else {
      final byte[] data;
      try {
        data = loadSessionDataFromRedis(id);
      } catch (Exception e) {
        throw new IOException(e);
      }

      if (data != null) {
        final DeserializedSessionContainer container = sessionFromSerializedData(id, data);
        final RedisSession session = container.session;
        currentSessionIsPersisted.set(true);
        currentSession.set(session);
        currentSessionSerializationMetadata.set(container.metadata);
        currentSessionId.set(id);
        return session;

      } else {
        currentSessionIsPersisted.set(false);
        currentSession.set(null);
        currentSessionSerializationMetadata.set(null);
        currentSessionId.set(null);
        return null;
      }
    }
  }

  private byte[] loadSessionDataFromRedis(String id) throws Exception {
    if (log.isTraceEnabled()) {
      log.trace("Attempting to load session " + id + " from Redis");
    }

    final byte[] data = storageClient.get(id).get();
    if (data == null) {
      if (log.isTraceEnabled()) {
        log.trace("Session " + id + " not found in Redis");
      }
    }

    return data;
  }

  private DeserializedSessionContainer sessionFromSerializedData(
      String id,
      byte[] data) throws IOException {

    if (log.isTraceEnabled()) {
      log.trace("Reading session " + id + " from Redis");
    }

    if (Arrays.equals(NULL_SESSION, data)) {
      throw new IOException("Serialized session data was equal to NULL_SESSION");
    }

    final PersistedSessionMetadata metadata = new PersistedSessionMetadata();
    final RedisSession session = createEmptySession();

    serializer.deserialize(data, session, metadata);

    session.setId(id);
    session.setNew(false);
    session.setMaxInactiveInterval(getSessionTimeoutSeconds());
    session.access();
    session.setValid(true);
    session.resetDirtyTracking();

    if (log.isTraceEnabled()) {
      log.trace("Session contents: " + session.dump());
    }

    return new DeserializedSessionContainer(session, metadata);
  }

  @Override
  public void save(RedisSession session, boolean forceSave) throws IOException {
    saveInternal(session, forceSave);
  }

  private void saveInternal(Session session,
                            boolean forceSave) throws IOException {

    final RedisSession redisSession = (RedisSession) session;

    if (log.isTraceEnabled()) {
      log.trace("Persisting session:" +
          " ID = [" + session.getIdInternal() + "], contents: " + redisSession.dump());
    }

    final Boolean isCurrentSessionPersisted;

    // XXX
    final PersistedSessionMetadata metadata = Optional.ofNullable(currentSessionSerializationMetadata.get()).orElse(new PersistedSessionMetadata());
    final long originalSessionAttributesHash = metadata.getAttrHash();
    Long sessionAttributesHash = null;
    if (
        forceSave
            || redisSession.isDirty()
            || null == (isCurrentSessionPersisted = this.currentSessionIsPersisted.get())
            || !isCurrentSessionPersisted
            || (originalSessionAttributesHash != (sessionAttributesHash = serializer.getAttributesHash(redisSession)))
        ) {

      if (log.isTraceEnabled()) {
        log.trace("Save was determined to be necessary");
      }

      if (sessionAttributesHash == null) {
        sessionAttributesHash = serializer.getAttributesHash(redisSession);
      }

      final PersistedSessionMetadata updatedSerializationMetadata = new PersistedSessionMetadata();
      updatedSerializationMetadata.setAttrHash(sessionAttributesHash);

      try {
        storageClient.set(
            redisSession.getId(),
            getSessionTimeoutSeconds(),
            serializer.serialize(redisSession, updatedSerializationMetadata)
        ).get();

      } catch (InterruptedException | ExecutionException e) {
        throw new IOException(e);
      }

      redisSession.resetDirtyTracking();
      currentSessionSerializationMetadata.set(updatedSerializationMetadata);
      currentSessionIsPersisted.set(true);

    } else {
      // XXX: should we set entry lifetime in Redis here?
      log.trace("Save was determined to be unnecessary");
    }
  }

  @Override
  public void remove(Session session) {
    remove(session, false);
  }

  @Override
  public void remove(RedisSession session) {
    remove(session, false);
  }

  @Override
  public void remove(Session session, boolean update) {
    if (log.isTraceEnabled()) {
      log.trace("Removing session ID: " + session.getIdInternal());
    }

    storageClient.delete(session.getId());
  }

  void afterRequest() {
    try {
      final RedisSession redisSession = currentSession.get();
      if (redisSession == null) {
        return;
      }

      if (!RedisSessionHandlerValve.shouldSkipSession()) {
        try {
          if (redisSession.isValid()) {
            if (log.isTraceEnabled()) {
              log.trace("Request with session completed, saving session: " + redisSession.getId());
            }

            save(redisSession, doSaveAlways());

          } else {
            if (log.isTraceEnabled()) {
              log.trace("HTTP Session has been invalidated, removing: " + redisSession.getId());
            }

            remove(redisSession);
          }

        } catch (Exception e) {
          log.error("Error storing/removing session", e);
        }
      }

    } finally {
      currentSession.remove();
      currentSessionId.remove();
      currentSessionSerializationMetadata.remove();
      currentSessionIsPersisted.remove();
    }
  }

  @Override
  public void processExpires() {
    // Use Redis key expiration.
  }

  private void initializeDatabaseConnection() throws LifecycleException {
    log.info("Using Redis connection: dbUrl = [" + dbUrl + "], timeout = [" + timeout + "]");
    try {
      storageClient = new RedisStorageClient(dbUrl, timeout);

    } catch (Exception e) {
      throw new LifecycleException("Failed initializing Redis connection", e);
    }
  }

  private RedisSessionSerializer initializeSerializer() {
    ClassLoader classLoader = null;
    {
      Loader contextLoader = null;
      if (getContext() != null) {
        contextLoader = getContext().getLoader();
      }

      if (contextLoader != null) {
        classLoader = contextLoader.getClassLoader();
      }
    }

    return new RedisSessionSerializer(classLoader);
  }

}

