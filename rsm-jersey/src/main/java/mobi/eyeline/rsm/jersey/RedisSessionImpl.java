package mobi.eyeline.rsm.jersey;

import mobi.eyeline.rsm.DirtySessionTracker;
import mobi.eyeline.rsm.model.PersistableSession;
import mobi.eyeline.rsm.model.PersistedSession;

import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;

public class RedisSessionImpl implements RedisSession {

  private final Logger log = Logger.getLogger(RedisSessionImpl.class.getName());

  private final DirtySessionTracker dirtyTracker = new DirtySessionTracker();

  private PersistedSession impl;
  private final RedisSessionManager manager;

  private RedisSessionImpl(PersistedSession impl, RedisSessionManager manager) {
    this.impl = impl;
    this.manager = manager;
  }

  RedisSessionImpl(RedisSessionManager manager) {
    this(null, manager);
  }

  boolean isDirty() {
    return dirtyTracker.isDirty();
  }

  public String getId() {
    return impl.id;
  }

  @Override
  public long getCreationTime() {
    return impl.creationTime;
  }

  @Override
  public long getLastAccessedTime() {
    return impl.lastAccessedTime;
  }

  @Override
  public int getMaxInactiveInterval() {
    return impl.maxInactiveInterval;
  }

  @Override
  public boolean isNew() {
    return impl.isNew;
  }

  @Override
  public Object getAttribute(String name) {
    return impl.attributes.get(name);
  }

  @Override
  public Enumeration<String> getAttributeNames() {
    return Collections.enumeration(impl.attributes.keySet());
  }

  @Override
  public void setAttribute(String name, Object value) {

    if (manager.getSkipAttributesPattern() != null &&
        manager.getSkipAttributesPattern().matcher(name).matches()) {
      if (log.isLoggable(Level.FINE)) {
        log.fine("Ignoring setAttribute, key = [" + name + "]");
      }
      return;
    }

    final Object prevValue = getAttribute(name);
    impl.attributes.put(name, value);

    if (dirtyTracker.isChanged(prevValue, value)) {
      if (manager.doSaveImmediate()) {
        try {
          manager.save(this, true);

        } catch (IOException e) {
          log.log(Level.SEVERE, "Error saving session on setAttribute", e);
        }

      } else {
        dirtyTracker.markDirty();
      }
    }
  }

  @Override
  public void removeAttribute(String name) {
    impl.attributes.remove(name);

    if (manager.doSaveImmediate()) {
      try {
        manager.save(this, true);

      } catch (IOException e) {
        log.log(Level.SEVERE, "Error saving session on removeAttribute", e);
      }

    } else {
      dirtyTracker.markDirty();
    }
  }

  @Override
  public void invalidate() {
    impl.isValid = false;
    manager.remove(this);
  }

  @Override
  public SecurityContext getSecurityContext() {
    return impl.principalName == null ?
        null :
        new SecurityContextImpl(impl);
  }

  PersistableSession asPersistableSession() {
    return new PersistableSession() {
      @Override
      public PersistedSession asPersistedSession() {
        return impl;
      }

      @Override
      public void fromPersistedSession(PersistedSession session) {
        RedisSessionImpl.this.impl = session;
      }
    };
  }

  @Override
  public String toString() {
    return "RedisSession{" +
        "id='" + getId() + '\'' +
        '}';
  }


  //
  //
  //

  private static class SecurityContextImpl implements SecurityContext {

    private final PersistedSession impl;

    SecurityContextImpl(PersistedSession impl) {
      this.impl = impl;
    }

    @Override
    public Principal getUserPrincipal() {
      return () -> impl.principalName;
    }

    @Override
    public boolean isUserInRole(String role) {
      requireNonNull(role);
      return impl.principalRoles != null &&
          Arrays.stream(impl.principalRoles).anyMatch(s -> s.equals(role));
    }

    @Override
    public boolean isSecure() {
      // Seems to be a safe guess.
      return false;
    }

    @Override
    public String getAuthenticationScheme() {
      // Just a guess.
      return "FORM_AUTH";
    }
  }
}
