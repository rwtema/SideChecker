package sidechecker;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// ClientSafe suppresses side-checking on a method. Only do this if you are certain that any client-side code
// is unreachable by a dedicated server.

// Don't forget that the purpose of the SidedProxy is to handle situations where client/server behaviour is different
// If possible use this method instead.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface ClientSafe {

}
