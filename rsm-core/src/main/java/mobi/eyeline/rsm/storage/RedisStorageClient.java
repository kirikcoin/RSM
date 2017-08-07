package mobi.eyeline.rsm.storage;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static redis.clients.util.SafeEncoder.encode;

public class RedisStorageClient implements StorageClient {

  private final Logger log = Logger.getLogger(getClass().getName());

  private final ExecutorService executor = Executors.newCachedThreadPool(
      new ThreadFactory() {
        private final AtomicInteger idx = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
          final Thread thread = new Thread(r);
          thread.setName("rsm-" + idx.getAndIncrement());
          return thread;
        }
      }
  );

  private final JedisPool jedisPool;

  public RedisStorageClient(String redisUrl, int timeout) {
    Objects.requireNonNull(redisUrl, "Redis database URL not set");

    timeout = timeout <= 0 ? Protocol.DEFAULT_TIMEOUT : timeout;

    try {
      final URI uri = new URI(redisUrl);
      jedisPool = new JedisPool(uri, timeout);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Future<Boolean> setIfAbsent(final String key, final int lifetimeSeconds, final byte[] payload) {
    if (log.isLoggable(Level.FINE)) {
      log.fine("setIfAbsent: key = [" + key + "], lifetimeSeconds = [" + lifetimeSeconds + "]");
    }

    return submit(jedis -> {
      if (jedis.setnx(encode(key), payload) != 1) {
        return false;
      }

      return lifetimeSeconds == 0 || jedis.expire(key, lifetimeSeconds) == 1;
    });
  }

  @Override
  public Future<Boolean> setIfAbsent(final String key, final byte[] payload) {
    if (log.isLoggable(Level.FINE)) {
      log.fine("setIfAbsent: key = [" + key + "]");
    }

    return submit(jedis -> jedis.setnx(encode(key), payload) == 1);
  }

  @Override
  public Future<Boolean> set(final String key, final int lifetimeSeconds, final byte[] payload) {
    if (log.isLoggable(Level.FINE)) {
      log.fine("set: key = [" + key + "], lifetimeSeconds = [" + lifetimeSeconds + "]");
    }

    return submit(jedis -> lifetimeSeconds == 0 ?
        jedis.set(encode(key), payload).equals("OK") :
        jedis.setex(encode(key), lifetimeSeconds, payload).equals("OK"));
  }

  @Override
  public Future<byte[]> get(final String key) {
    if (log.isLoggable(Level.FINE)) {
      log.fine("get: key = [" + key + "]");
    }

    return submit(jedis -> jedis.get(encode(key)));
  }

  @Override
  public Future<Boolean> delete(final String key) {
    if (log.isLoggable(Level.FINE)) {
      log.fine("delete: key = [" + key + "]");
    }

    return submit( jedis -> jedis.del(key) == 1);
  }

  @Override
  public void close() {
    jedisPool.close();
  }

  private <T> Future<T> submit(Function<Jedis, T> task) {
    return executor.submit(() -> {
      try (Jedis jedis = jedisPool.getResource()) {
        return task.apply(jedis);

      } catch (Exception e) {
        log.log(Level.WARNING, "Failed executing command", e);
        throw e;
      }
    });
  }
}
