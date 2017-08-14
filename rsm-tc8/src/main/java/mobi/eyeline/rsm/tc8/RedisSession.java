package mobi.eyeline.rsm.tc8;

import mobi.eyeline.rsm.DirtySessionTracker;
import mobi.eyeline.rsm.model.GenericSession;
import mobi.eyeline.rsm.model.PersistableSession;
import mobi.eyeline.rsm.model.PersistedSession;
import org.apache.catalina.Manager;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.session.StandardSession;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


public class RedisSession extends StandardSession implements PersistableSession, GenericSession {

  private final Log log = LogFactory.getLog(RedisSession.class);

  private final DirtySessionTracker dirtyTracker = new DirtySessionTracker();

  RedisSession(Manager manager) {
    super(manager);
    resetDirtyTracking();
  }

  boolean isDirty() {
    return dirtyTracker.isDirty();
  }

  void resetDirtyTracking() {
    dirtyTracker.reset();
  }

  @Override
  public void setAttribute(String key, Object value) {
    final RedisSessionManager manager = getManager();

    if (manager.getSkipAttributesPattern() != null &&
        manager.getSkipAttributesPattern().matcher(key).matches()) {
      if (log.isTraceEnabled()) {
        log.trace("Ignoring setAttribute, key = [" + key + "]");
      }
      return;
    }

    final Object prevValue = getAttribute(key);
    super.setAttribute(key, value);

    //
    //  Check if changed and persist if necessary.
    //
    if (dirtyTracker.isChanged(prevValue, value)) {
      if (manager.doSaveImmediate()) {
        try {
          manager.save(this, true);

        } catch (IOException e) {
          log.error("Error saving session on setAttribute", e);
        }

      } else {
        dirtyTracker.markDirty();
      }
    }
  }

  @Override
  public void removeAttribute(String name) {
    super.removeAttribute(name);

    final RedisSessionManager manager = getManager();
    if (manager.doSaveImmediate()) {
      try {
        manager.save(this, true);

      } catch (IOException e) {
        log.error("Error saving session on removeAttribute", e);
      }

    } else {
      dirtyTracker.markDirty();
    }
  }

  @Override
  public void setId(String id, boolean notify) {
    if (Objects.equals(this.id, id)) {
      // Avoid expensive save/delete operation if ID is unchanged.
      return;
    }

    if (this.id != null) {
      getManager().remove(this);
    }

    this.id = id;

    try {
      getManager().save(this, true);

    } catch (IOException e) {
      throw new RuntimeException("Failed saving session", e);
    }

    if (notify) {
      tellNew();
    }
  }

  @Override
  public void setPrincipal(Principal principal) {
    dirtyTracker.markDirty();
    super.setPrincipal(principal);
  }

  @Override
  public RedisSessionManager getManager() {
    return (RedisSessionManager) this.manager;
  }

  @Override
  public PersistedSession asPersistedSession() {
    final PersistedSession rc = new PersistedSession();

    rc.creationTime = getCreationTimeInternal();
    rc.lastAccessedTime = getLastAccessedTimeInternal();
    rc.maxInactiveInterval = getMaxInactiveInterval();
    rc.isNew = isNew();
    rc.thisAccessedTime = getThisAccessedTimeInternal();
    rc.isValid = isValidInternal();
    rc.id = getIdInternal();

    final GenericPrincipal principal = (GenericPrincipal) getPrincipal();
    if (principal != null) {
      rc.principalName = principal.getName();
      rc.principalRoles = principal.getRoles();
      rc.userPrincipal = principal.getUserPrincipal();
    }

    if (this.attributes != null) {
      rc.attributes = this.attributes
          .entrySet()
          .stream()
          .filter(attr ->
              attr.getValue() != null &&
                  isAttributeDistributable(attr.getKey(), attr.getValue()) &&
                  !exclude(attr.getKey(), attr.getValue())
          ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    return rc;
  }

  @Override
  public void fromPersistedSession(PersistedSession rc) {
    authType = null;
    creationTime = rc.creationTime;
    lastAccessedTime = rc.lastAccessedTime;
    maxInactiveInterval = rc.maxInactiveInterval;
    isNew = rc.isNew;
    isValid = rc.isValid;
    thisAccessedTime = rc.thisAccessedTime;
    principal = null;
    id = rc.id;

    rc.attributes.forEach((k, v) -> attributes.put(k, v));

    if (listeners == null) {
      listeners = new ArrayList<>();
    }

    if (notes == null) {
      notes = new Hashtable<>();
    }

    this.principal = rc.principalName != null ?
        new GenericPrincipal(rc.principalName, null, Arrays.asList(rc.principalRoles)) :
        null;
  }

  private void dump(StringBuilder buf) {
    buf.append("ID = [")
        .append(getIdInternal())
        .append("]");

    buf.append(", attributes = [")
        .append(attributes)
        .append("]");
  }

  String dump() {
    final StringBuilder buf = new StringBuilder();
    dump(buf);
    return buf.toString();
  }

  @Override
  public String toString() {
    return "RedisSession{" +
        "id='" + id + '\'' +
        '}';
  }
}
