package org.taverna.server.master.scape.beanshells;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Instances of this interface are used to help write the beanshell scripts
 * inside the outer workflows.
 * 
 * @author Donal Fellows
 */

public interface BeanshellSupport<T extends BeanshellSupport<?>> {
	void perform() throws Exception;

	T init(String name, Object value);

	Object getResult(String name);

	@Target(FIELD)
	@Retention(RUNTIME)
	public @interface Input {
	}

	@Target(FIELD)
	@Retention(RUNTIME)
	public @interface Output {
	}	
}

abstract class Support<T extends BeanshellSupport<?>> implements
		BeanshellSupport<T> {
	private Map<String,Field> inputs = new HashMap<>(), outputs = new HashMap<>();

	protected Support() {
		for (Field f : getClass().getDeclaredFields())
			if (f.getAnnotation(Input.class) != null) {
				f.setAccessible(true);
				inputs.put(f.getName(), f);
			} else if (f.getAnnotation(Output.class) != null) {
				f.setAccessible(true);
				outputs.put(f.getName(), f);
			}
	}

	@SuppressWarnings("unchecked")
	@Override
	public final T init(String name, Object value) {
		Field f = inputs.get(name);
		if (f == null)
			throw new UnsupportedOperationException();
		try {
			if (f.getType().equals(Boolean.TYPE))
				f.setBoolean(this, parseBoolean(value.toString()));
			else if (f.getType().equals(Integer.TYPE))
				f.setInt(this, parseInt(value.toString()));
			else
				f.set(this, value);
			return (T) this;
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new UnsupportedOperationException(e);
		}
	}

	@Override
	public final Object getResult(String name) {
		Field f = outputs.get(name);
		if (f == null)
			throw new UnsupportedOperationException();
		try {
			if (f.getType().equals(Boolean.TYPE))
				return f.getBoolean(this);
			return f.get(this);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new UnsupportedOperationException(e);
		}
	}
}