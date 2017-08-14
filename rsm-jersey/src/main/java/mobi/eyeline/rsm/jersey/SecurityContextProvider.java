package mobi.eyeline.rsm.jersey;

import javax.ws.rs.core.SecurityContext;

public interface SecurityContextProvider {

  SecurityContext getSecurityContext();

}
