/**
 * by Christophe Champagne
 */
package org.cch.nanodb;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * The Connection provider is intended to create a connection or reset it
 * (close the current connection and then recreate a new one)
 * @author Christophe Champagne
 *
 */
public interface ConnectionProvider {
	/**
	 * This method returns a connectionObject
	 * @return an instance of Connection
	 */
	public Connection getConnection() throws SQLException;
	/**
	 * Closes the current connection(s) and creates an new one;
	 * @return the new Connection
	 */
	public Connection resetConnection() throws SQLException;
	/**
	 * Closes any current connection;
	 */
	public void close() throws SQLException;

}