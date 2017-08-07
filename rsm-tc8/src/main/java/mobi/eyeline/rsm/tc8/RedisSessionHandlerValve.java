package mobi.eyeline.rsm.tc8;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import javax.servlet.ServletException;
import java.io.IOException;


public class RedisSessionHandlerValve extends ValveBase {
  private final Log log = LogFactory.getLog(RedisSessionManager.class);

  private RedisSessionManager manager;

  void setRedisSessionManager(RedisSessionManager manager) {
    this.manager = manager;
  }

  @Override
  public void invoke(Request request, Response response) throws IOException, ServletException {
    if (log.isTraceEnabled()) {
      log.trace("Before request: [" + getRequestId(request) + "]");
    }

    try {
      getNext().invoke(request, response);

    } finally {
      manager.afterRequest();

      if (log.isTraceEnabled()) {
        log.trace("After request: [" + getRequestId(request) + "]");
      }

    }
  }

  private static String getRequestId(Request request) {
    final StringBuffer requestURL = request.getRequestURL();
    requestURL.insert(0, request.getMethod() + " ");

    final String queryString = request.getQueryString();
    return queryString == null ?
        requestURL.toString() :
        requestURL.append('?').append(queryString).toString();
  }
}
