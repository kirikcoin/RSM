package mobi.eyeline.rsm.tc8;

import mobi.eyeline.rsm.model.PersistedSessionMetadata;

class DeserializedSessionContainer {

  final RedisSession session;
  final PersistedSessionMetadata metadata;

  DeserializedSessionContainer(RedisSession session, PersistedSessionMetadata metadata) {
    this.session = session;
    this.metadata = metadata;
  }
}
