package org.cch.napa.entity.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be used on an attribute of a bean or a getter or a setter 
 * to signify it is a member of the index<br>
 * 
 * @author Christophe Champagne Christophe Champagne
 *
 */
@Target(value={ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Indexes {
	
	/**
	 * Indexes
	 * 
	 * @return
	 */
	Index[] indexes();	
}
