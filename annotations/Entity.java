/**
 * 
 */
package nanodb.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * @author Christophe Champagne
 *
 */
@Target(value={ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Entity {
	String table() default "";
	boolean considerAllAttributesAsDBFields() default true;
}
