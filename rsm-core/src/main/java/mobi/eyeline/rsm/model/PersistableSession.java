package mobi.eyeline.rsm.model;

public interface PersistableSession {

  PersistedSession asPersistedSession();
  void fromPersistedSession(PersistedSession session);

}
