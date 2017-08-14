package mobi.eyeline.rsm.jersey;

import mobi.eyeline.rsm.GenericSessionManager;
import mobi.eyeline.rsm.PersistenceStrategy;
import mobi.eyeline.rsm.model.PersistableSession;
import mobi.eyeline.rsm.model.PersistedSessionMetadata;
import mobi.eyeline.rsm.pack.SessionSerializer;
import mobi.eyeline.rsm.storage.RedisStorageClient;
import mobi.eyeline.rsm.storage.StorageClient;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

class RedisSessionManager implements GenericSessionManager<RedisSessionImpl> {

  private final Logger log = Logger.getLogger(RedisSessionManager.class.getName());

  //
  // BEGIN Manager configuration.
  //

  private String dbUrl;
  private int timeout;

  private PersistenceStrategy persistenceStrategy = PersistenceStrategy.ALWAYS;

  private Pattern skipUrls;
  private Pattern skipAttributes;

  private int sessionTimeoutSeconds;

  private String sessionCookieName = "session_id";

  @SuppressWarnings("unused")
  public String getDbUrl()     { return dbUrl; }
  void setDbUrl(String dbUrl)  { this.dbUrl = dbUrl; }

  @SuppressWarnings("unused")
  public int getTimeout()      { return timeout; }
  void setTimeout(int timeout) { this.timeout = timeout; }

  void setPersistenceStrategy(String strategy)        { persistenceStrategy = PersistenceStrategy.fromName(strategy); }
  @SuppressWarnings("unused")
  public String getPersistenceStrategy()              { return persistenceStrategy.name(); }

  void setSkipUrls(String pattern)          { this.skipUrls = pattern == null ? null : Pattern.compile(pattern); }
  @SuppressWarnings("unused")
  public String getSkipUrls()               { return skipUrls == null ? null : skipUrls.pattern(); }

  void setSkipAttributes(String pattern)    { this.skipAttributes = pattern == null ? null : Pattern.compile(pattern); }
  @SuppressWarnings("unused")
  public String getSkipAttributes()         { return skipAttributes == null ? null : skipAttributes.pattern(); }

  void setSessionTimeoutSeconds(int sessionTimeoutSeconds) { this.sessionTimeoutSeconds = sessionTimeoutSeconds; }

  String getSessionCookieName() { return sessionCookieName; }
  void setSessionCookieName(String sessionCookieName) { this.sessionCookieName = sessionCookieName; }

  //
  // END Manager configuration.
  //

  private StorageClient storageClient;
  private SessionSerializer serializer;

  RedisSessionManager() {
    if (log.isLoggable(Level.FINE)) {
      log.fine("RedisSessionManager initialized");
    }
  }

  void initialize() {
    storageClient = new RedisStorageClient(dbUrl, timeout);
    serializer = new SessionSerializer(null);
  }

  @PreDestroy
  public void destroy() {
    if (log.isLoggable(Level.FINE)) {
      log.fine("RedisSessionManager destroyed");
    }

    storageClient.close();
  }

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
  public void save(RedisSessionImpl session,
                   boolean force) throws IOException {

    if (force || session.isDirty()) {
      final PersistableSession impl = session.asPersistableSession();

      try {
        storageClient.set(
            session.getId(),
            sessionTimeoutSeconds,
            serializer.serialize(
                impl,
                new PersistedSessionMetadata(
                    serializer.attributesHashFrom(impl.asPersistedSession().attributes)
                )
            )
        ).get();
      } catch (InterruptedException | ExecutionException e) {
        throw new IOException(e);
      }

    } else {
      if (log.isLoggable(Level.FINE)) {
        log.fine("Save was determined to be unnecessary");
      }
    }
  }

  @Override
  public void remove(RedisSessionImpl session) {
    if (log.isLoggable(Level.FINE)) {
      log.fine("Removing session ID: " + session.getId());
    }

    storageClient.delete(session.getId());
  }

  void afterRequest(RedisSessionImpl session) {
    try {
      if (session.asPersistableSession().asPersistedSession().isValid) {
        if (log.isLoggable(Level.FINE)) {
          log.fine("Request with session completed, saving session: " + session.getId());
        }

        save(session, doSaveAlways());

      } else {
        if (log.isLoggable(Level.FINE)) {
          log.fine("HTTP Session has been invalidated, removing: " + session.getId());
        }

        remove(session);
      }

    } catch (Exception e) {
      log.log(Level.SEVERE, "Error storing/removing session", e);
    }
  }

  RedisSessionImpl findSession(String id) throws IOException {

    final byte[] data;
    try {
      data = storageClient.get(id).get();

    } catch (Exception e) {
      throw new IOException(e);
    }

    if (data == null) {
      return null;
    }

    final PersistedSessionMetadata metadata = new PersistedSessionMetadata();
    final RedisSessionImpl session = new RedisSessionImpl(this);

    serializer.deserialize(data, session.asPersistableSession(), metadata);

    return session;
  }

}
