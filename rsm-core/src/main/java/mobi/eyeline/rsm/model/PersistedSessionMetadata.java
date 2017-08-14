package mobi.eyeline.rsm.model;

import java.io.Serializable;

/**
 * Internal {@linkplain PersistedSession session} metadata.
 */
public class PersistedSessionMetadata implements Serializable {

  /** Attributes hash, for dirty checking. */
  private long attrHash;

  public PersistedSessionMetadata() {
    this(0);
  }

  public PersistedSessionMetadata(long attrHash) {
    this.attrHash = attrHash;
  }

  public long getAttrHash() {
    return attrHash;
  }

  public void setAttrHash(long attrHash) {
    this.attrHash = attrHash;
  }

}
