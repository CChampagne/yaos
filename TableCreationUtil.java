/**
 * by Christophe Champagne (GII561)
 */
package com.ibm.next.mam.persistence;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.ibm.next.mam.errorframework.exceptions.persistence.AnnotationException;
import com.ibm.next.mam.errorframework.exceptions.persistence.PersistenceException;
import com.ibm.next.mam.persistence.annotations.DBField;
import com.ibm.next.mam.persistence.annotations.atk.EntityField;
import com.ibm.next.mam.persistence.annotations.atk.EntityHandler;
import com.ibm.next.mam.persistence.annotations.atk.EntityIndex;
import com.ibm.next.mam.persistence.entity.Persistable;
import com.sap.ip.me.api.logging.Severities;
import com.sap.ip.me.api.logging.Trace;

/**
 * @author Christophe Champagne (GII561)
 * 
 */
public class TableCreationUtil {
	private static Trace TRACE = Trace.getInstance(TableCreationUtil.class.getName());
	private JdbcDao dao;
	private SQLTableCreationScriptsGenerator generator;
	private ConnectionProvider connectionProvider;
	private EntityDaoFactory factory;
	private static final String JDBC_TABLE_TYPE_TABLE = "TABLE";// JDBC table
																// type of a
																// table

	public TableCreationUtil(EntityDaoFactory factory) {
		this(ConnectionProviderHelper.getConnectionProvider(factory.getClass()), factory);
	}

	public TableCreationUtil(ConnectionProvider connectionProvider, EntityDaoFactory factory) {
		this.connectionProvider = connectionProvider;
		this.dao = factory.getJdbcDao(connectionProvider);
		this.generator = new SQLTableCreationScriptsGenerator(factory);
		this.factory = factory;
	}

	public <E extends Persistable> void dropTable(Class<E> entityClass) throws PersistenceException, SQLException {
		String drop = generator.generateDropTable(entityClass);
		executeQuery(drop);
	}

	public <E extends Persistable> boolean canReadTable(Class<E> entityClass) throws AnnotationException {
		String query = generator.generateCanRead(entityClass);
		return executeCanRead(query);
	}

	public boolean canReadTable(String table) throws AnnotationException {
		String query = generator.generateCanRead(table);
		return executeCanRead(query);
	}

	public <E extends Persistable> boolean tableExists(Class<E> entityClass) throws AnnotationException, SQLException {
		String table = factory.getEntityHandler(entityClass).getTableName().toUpperCase();
		return getTableNames().contains(table);
	}

	/**
	 * 
	 * @param table
	 *            the name of the table
	 * @return
	 * @throws SQLException
	 */
	public boolean tableExists(String table) throws SQLException {
		return getTableNames().contains(table.toUpperCase());
	}

	/**
	 * Returns a set of table names in upper case.
	 * 
	 * @return a set of table names in upper case.
	 * @throws SQLException
	 */
	public Set<String> getTableNames() throws SQLException {
		Set<String> tables = new TreeSet<String>();
		try {
			Connection connection = connectionProvider.getConnection();
			DatabaseMetaData metaData = connection.getMetaData();
			ResultSet rs = metaData.getTables(null, null, "%", null);
			while (rs.next()) {
				String type = rs.getString("TABLE_TYPE");
				if (JDBC_TABLE_TYPE_TABLE.equalsIgnoreCase(type)) {
					String name = rs.getString("TABLE_NAME");
					tables.add(name.toUpperCase());
				}
			}
		} catch (SQLException e) {
			// The table does not exist
			TRACE.logException(Severities.ERROR, "Cannot get table names", e, false);
			throw e;
		}
		return tables;
	}

	private boolean executeCanRead(String query) throws AnnotationException {
		boolean canRead = false;
		PreparedStatement statement = null;
		try {
			Connection connection = connectionProvider.getConnection();
			statement = connection.prepareStatement(query);
			statement.executeQuery();
			canRead = true;
		} catch (SQLException e) {
			// The table does not exist
			TRACE.logException(Severities.INFO, "The query " + query
					+ " doesn't work meaning that the table cannot be read", e, false);
		} finally {
			if (statement != null) {
				try {
					statement.close();
				} catch (SQLException e) {
					TRACE.logException(Severities.ERROR, "Cannot close statement in 'executeCanRead'", e, false);
				}
			}
		}
		return canRead;
	}

	public <E extends Persistable> void createTable(Class<E> entityClass) throws PersistenceException, SQLException {
		String create = generator.generateCreateTable(entityClass);
		executeQuery(create);
		EntityHandler<E> entityHandler = factory.getEntityHandler(entityClass);
		for(EntityIndex index : entityHandler.getIndexes()){
			create = generator.generateIndex(index);
			executeQuery(create);
		}
	}

	public <E extends Persistable> void createTableIfNotExisting(Class<E> entityClass) throws PersistenceException,
			SQLException {
		if (!tableExists(entityClass)) {
			createTable(entityClass);
		} else {
			TRACE.log(Severities.INFO, "Table corresponding to " + entityClass.getName() + " already existing");
		}
	}

	public <E extends Persistable> void addField(Class<E> entityClass, String field) throws PersistenceException,
			SQLException {
		String addField = generator.generateAddField(entityClass, field);
		executeQuery(addField);
	} 

	public <E extends Persistable> void addMissingFields(Class<E> entityClass) throws PersistenceException,
			SQLException {
		EntityHandler<E> handler = factory.getEntityHandler(entityClass);
		Map<String, FieldMetaData>fieldsInDB = getTableMetaDataFromDB(handler.getTableName());
		for(EntityField field : handler.getEntityFields()){
			if(!fieldsInDB.containsKey(field.getDBFieldName().toUpperCase())){
				addField(entityClass, field.getDBFieldName());
			}
		}
	} 

	private void executeQuery(String query) throws PersistenceException, SQLException {
		PreparedStatement statement = null;
		try {
			statement = dao.prepareStatement(query);
			statement.execute();
			if (!statement.getConnection().getAutoCommit()) {
				statement.getConnection().commit();
			}
		} finally {
			if (statement != null) {
				if (!statement.getConnection().getAutoCommit()) {
					try {
						statement.getConnection().rollback();
					} catch (Exception ex) {
						TRACE.logException(Severities.ERROR, "Could not rollback drop table", ex, true);
					}
				}
				try {
					statement.close();
				} catch (Exception e) {
					TRACE.logException(Severities.ERROR, "Could not close statement", e, true);
				}
			}
		}
	}
	//TODO get complete metadata in the future
	public Map<String, FieldMetaData> getTableMetaDataFromDB(String table) throws AnnotationException {
		PreparedStatement statement = null;
		String query = "select * from " + table + " where 1=0";
		ResultSetMetaData rsMetaData = null;
		Map<String, FieldMetaData> metaData = new TreeMap<String, FieldMetaData>();
		try {
			Connection connection = connectionProvider.getConnection();
			statement = connection.prepareStatement(query);
			ResultSet resultSet = statement.executeQuery();
			rsMetaData = resultSet.getMetaData();
			for(int i =1; i<=rsMetaData.getColumnCount(); i++){
				DBFieldMetaData field = new DBFieldMetaData();
				field.setFieldName(rsMetaData.getColumnName(i));
				field.setNullable(rsMetaData.isNullable(i)!=ResultSetMetaData.columnNoNulls);
				//field.setDefaultValue("TODO");
				field.setPrecision(rsMetaData.getPrecision(i));
				field.setReadOnly(rsMetaData.isReadOnly(i));
				field.setSize(rsMetaData.getScale(i));//TODO:seems to be incorrect
				field.setSqlType(rsMetaData.getColumnType(i));
				metaData.put(field.getFieldName().toUpperCase(), field);
			}
		} catch (SQLException e) {
			// The table does not exist
			TRACE.logException(Severities.INFO, "The query " + query
					+ " doesn't work meaning that the table cannot be read", e, false);
		} finally {
			if (statement != null) {
				try {
					statement.close();
				} catch (SQLException e) {
					TRACE.logException(Severities.ERROR, "Cannot close statement in 'executeCanRead'", e, false);
				}
			}
		}
		return metaData;
	}
	/**
	 * 
	 * @param table
	 * @param field
	 * @return true if the field exists on the table
	 * @throws AnnotationException 
	 */
	public boolean fieldExistsInTable(String table, String field) throws AnnotationException{
		//TODO optimize this
		Map<String, FieldMetaData> metaDatas = getTableMetaDataFromDB(table);
		return metaDatas.containsKey(field.toUpperCase());
	}
	private class DBFieldMetaData implements FieldMetaData {
		//Attributes
		private String fieldName;
		private boolean nullable = true;
		private boolean readOnly;
		private int sqlType = Types.NULL;
		private int size = DBField.DEFAULT;
		private int precision = DBField.DEFAULT;
		private String defaultValue;
		/**
		 * @return the fieldName
		 */
		public String getFieldName() {
			return fieldName;
		}
		/**
		 * @param fieldName the fieldName to set
		 */
		public void setFieldName(String fieldName) {
			this.fieldName = fieldName;
		}
		/**
		 * @return the nullable
		 */
		public boolean isNullable() {
			return nullable;
		}
		/**
		 * @param nullable the nullable to set
		 */
		public void setNullable(boolean nullable) {
			this.nullable = nullable;
		}
		/**
		 * @return the readOnly
		 */
		public boolean isReadOnly() {
			return readOnly;
		}
		/**
		 * @param readOnly the readOnly to set
		 */
		public void setReadOnly(boolean readOnly) {
			this.readOnly = readOnly;
		}
		/**
		 * @return the sqlType
		 */
		public int getSqlType() {
			return sqlType;
		}
		/**
		 * @param sqlType the sqlType to set
		 */
		public void setSqlType(int sqlType) {
			this.sqlType = sqlType;
		}
		/**
		 * @return the size
		 */
		public int getSize() {
			return size;
		}
		/**
		 * @param size the size to set
		 */
		public void setSize(int size) {
			this.size = size;
		}
		/**
		 * @return the precision
		 */
		public int getPrecision() {
			return precision;
		}
		/**
		 * @param precision the precision to set
		 */
		public void setPrecision(int precision) {
			this.precision = precision;
		}
		/**
		 * @return the defaultValue
		 */
		public String getDefaultValue() {
			return defaultValue;
		}
//NOT used yet
//		/** 
//		 * @param defaultValue the defaultValue to set
//		 */
//		public void setDefaultValue(String defaultValue) {
//			this.defaultValue = defaultValue;
//		}

	}
}
