package mobi.eyeline.rsm.model;

import java.io.Serializable;
import java.util.Map;

/**
 * Platform-agnostic session representation.
 */
public class PersistedSession implements Serializable, PersistableSession {

  public long creationTime;
  public long lastAccessedTime;
  public int maxInactiveInterval;
  public boolean isNew;
  public boolean isValid;
  public long thisAccessedTime;
  public String id;

  public String principalName;
  public String[] principalRoles;
  public Object userPrincipal;

  /**
   * Session attributes.
   *
   * <p>Note: only a subset of types is supported.
   */
  public Map<String, Object> attributes;

  @Override
  public PersistedSession asPersistedSession() {
    return this;
  }

  @Override
  public void fromPersistedSession(PersistedSession session) {
    this.creationTime = session.creationTime;
    this.lastAccessedTime = session.lastAccessedTime;
    this.maxInactiveInterval = session.maxInactiveInterval;
    this.isNew = session.isNew;
    this.isValid = session.isValid;
    this.thisAccessedTime = session.thisAccessedTime;
    this.id = session.id;

    this.principalName = session.principalName;
    this.principalRoles = session.principalRoles;
    this.userPrincipal = session.userPrincipal;

    this.attributes = session.attributes;
  }
}
