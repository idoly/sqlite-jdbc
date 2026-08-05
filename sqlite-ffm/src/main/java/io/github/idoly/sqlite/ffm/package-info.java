/**
 * Low-level SQLite native boundary implemented with the JDK Foreign Function and Memory API.
 *
 * <p>Application code should normally depend on the JDBC module instead of calling this package.
 * Handle records intentionally expose addresses as longs so FFM memory types never cross the module
 * boundary.
 */
package io.github.idoly.sqlite.ffm;
