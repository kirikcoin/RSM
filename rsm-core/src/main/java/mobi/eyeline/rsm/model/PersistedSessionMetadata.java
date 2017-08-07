package mobi.eyeline.rsm.model;

import java.io.Serializable;

/**
 * Internal {@linkplain PersistedSession session} metadata.
 */
public class PersistedSessionMetadata implements Serializable {

  /** Attributes hash, for dirty checking. */
  private byte[] attrHash;

  public PersistedSessionMetadata() {
    this.attrHash = new byte[0];
  }

  public byte[] getAttrHash() {
    return attrHash;
  }

  public void setAttrHash(byte[] attrHash) {
    this.attrHash = attrHash;
  }

}
