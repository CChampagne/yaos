/**
 * by Christophe Champagne
 */
package nanodb.annotations.generator.impl;

import java.util.List;

import nanodb.exceptions.PersistenceException;
import nanodb.annotations.GeneratedValue;
import nanodb.annotations.Parameter;
import nanodb.annotations.atk.EntityField;
import nanodb.annotations.atk.EntityHandler;
import nanodb.annotations.generator.Generator;
import nanodb.entity.Persistable;

/**
 * @author Christophe Champagne
 *
 */
public class PseudoSequenceGenerator extends AbstractSequenceGenerator{
	public final static String PARAM_NAME_CACHED = "cached";
	public final static String PARAM_VALUE_CACHED = "true";
	public final static String PARAM_VALUE_NOT_CACHED = "false";

	private String query;
	private SingleValueMapper mapper = new SingleValueMapper();
	private boolean cached = true;
	private Long lastValue;
	/**
	 * @see nanodb.annotations.generator.Generator#getNextValue()
	 */
	public Number getNextValue() throws PersistenceException {
		if(!cached  || lastValue == null){
			if(getFactory()!=null && getFactory().getJdbcDao() !=null){
				List<Long> values= getFactory().getJdbcDao().select(query, mapper);
				lastValue = values.get(0);
			}
		}
		if(lastValue != null){
			lastValue = new Long(lastValue.longValue() + getStep());
			if (isInt()){			
				return new Integer(lastValue.intValue());
			} 
		}
		return lastValue;
	}


	/**
	 * @see nanodb.annotations.generator.impl.AbstractSequenceGenerator#performInit(nanodb.annotations.GeneratedValue, nanodb.annotations.atk.EntityField, nanodb.annotations.atk.EntityHandler)
	 */
	@Override
	protected <E extends Persistable> Generator performInit(GeneratedValue annotation, EntityField field,
			EntityHandler<E> entity) throws PersistenceException {
		super.performInit(annotation, field, entity);
		query = "select max("+ field.getDBFieldName() + ") from " + entity.getTableName();
		Parameter[] params = annotation.parameters();
		for (Parameter param : params){
			if(PARAM_NAME_CACHED.equals(param)){
				cached = !PARAM_VALUE_NOT_CACHED.equals(param.value());
			}
		}
		return this;
	}

}
