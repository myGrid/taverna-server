package org.taverna.server.master.scape.beanshells;

import java.util.List;

/**
 * Instances of this interface are used to help write the beanshell scripts
 * inside the outer workflows.
 * 
 * @author Donal Fellows
 */

public interface BeanshellSupport<T extends BeanshellSupport<?>> {
	void perform() throws Exception;

	T init(String name, String value);

	T init(String name, List<String> value);

	String getResult(String name);

	List<?> getResultList(String name);
}

abstract class Support<T extends BeanshellSupport<?>> implements
		BeanshellSupport<T> {
	@Override
	public T init(String name, String value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public T init(String name, List<String> value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getResult(String name) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<?> getResultList(String name) {
		throw new UnsupportedOperationException();
	}
}