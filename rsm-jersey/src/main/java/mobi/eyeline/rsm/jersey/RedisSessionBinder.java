package mobi.eyeline.rsm.jersey;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.process.internal.RequestScoped;

public class RedisSessionBinder {

  private final RedisSessionManager manager;

  private RedisSessionBinder(RedisSessionManager manager) {
    this.manager = manager;
  }

  public static RedisSessionBinder builder(String dbUrl) {
    final RedisSessionManager manager = new RedisSessionManager();
    manager.setDbUrl(dbUrl);

    return new RedisSessionBinder(manager);
  }

  public RedisSessionBinder setTimeout(int timeout) {
    manager.setTimeout(timeout);
    return this;
  }

  public RedisSessionBinder setPersistenceStrategy(String strategy) {
    manager.setPersistenceStrategy(strategy);
    return this;
  }

  public RedisSessionBinder setSkipUrls(String pattern) {
    manager.setSkipUrls(pattern);
    return this;
  }

  public RedisSessionBinder setSkipAttributes(String pattern) {
    manager.setSkipAttributes(pattern);
    return this;
  }

  public RedisSessionBinder setSessionTimeoutSeconds(int timeoutSeconds) {
    manager.setSessionTimeoutSeconds(timeoutSeconds);
    return this;
  }

  public RedisSessionBinder setSessionCookieName(String sessionCookieName) {
    manager.setSessionCookieName(sessionCookieName);
    return this;
  }

  public AbstractBinder build() {
    manager.initialize();

    return new AbstractBinder() {

      @Override
      protected void configure() {
        bind(manager).to(RedisSessionManager.class);
        bindFactory(RedisSessionFactory.class)
            .to(RedisSession.class)
            .in(RequestScoped.class)
            .proxy(true)
            .proxyForSameScope(true);
      }
    };
  }
}
