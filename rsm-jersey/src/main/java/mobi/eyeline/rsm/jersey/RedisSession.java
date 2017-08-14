package mobi.eyeline.rsm.jersey;

import mobi.eyeline.rsm.model.GenericSession;

import javax.ws.rs.core.SecurityContext;
import java.util.Enumeration;

public interface RedisSession extends GenericSession, SecurityContextProvider {

  RedisSession NULL_SESSION = new RedisSession() {

    @Override
    public SecurityContext getSecurityContext() {
      throw new IllegalStateException("Session is NULL");
    }

    @Override
    public String getId() {
      throw new IllegalStateException("Session is NULL");
    }

    @Override
    public long getCreationTime() {
      throw new IllegalStateException("Session is NULL");
    }

    @Override
    public long getLastAccessedTime() {
      throw new IllegalStateException("Session is NULL");
    }

    @Override
    public int getMaxInactiveInterval() {
      throw new IllegalStateException("Session is NULL");
    }

    @Override
    public boolean isNew() {
      throw new IllegalStateException("Session is NULL");
    }

    @Override
    public Object getAttribute(String name) {
      throw new IllegalStateException("Session is NULL");
    }

    @Override
    public Enumeration<String> getAttributeNames() {
      throw new IllegalStateException("Session is NULL");
    }

    @Override
    public void setAttribute(String name, Object value) {
      throw new IllegalStateException("Session is NULL");
    }

    @Override
    public void removeAttribute(String name) {
      throw new IllegalStateException("Session is NULL");
    }

    @Override
    public void invalidate() {
      throw new IllegalStateException("Session is NULL");
    }

    @Override
    public String toString() {
      return "NULL_SESSION";
    }
  };
}
