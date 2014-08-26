package org.taverna.server.master.scape.beanshells;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static java.lang.Thread.currentThread;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Instances of this interface are used to help write the beanshell scripts
 * inside the outer workflows.
 * 
 * @author Donal Fellows
 */

public interface BeanshellSupport<T extends BeanshellSupport<?>> {
	/**
	 * Carry out the operation defined by this object. The {@linkplain Input
	 * inputs} must be defined before this operation is called, and the
	 * {@linkplain Output outputs} will be readable afterwards. Should only be
	 * called once per <i>instance</i> of this class.
	 * 
	 * @throws IllegalStateException
	 *             If a required input has not set before calling this method.
	 * @throws Exception
	 *             If anything goes wrong.
	 */
	void perform() throws Exception;

	/**
	 * Write to the {@linkplain Input input} variable called <tt>name</tt>.
	 * 
	 * @param name
	 *            The input variable to write.
	 * @param value
	 *            The value to write. Must be of the right type for the variable
	 *            (or <tt>null</tt>, if permitted).
	 * @return <tt>this</tt>, to enable fluent use of this API.
	 * @throws UnsupportedOperationException
	 *             If that variable is not writable (e.g., if it doesn't exist
	 *             or the types don't match).
	 */
	@Nonnull
	T init(@Nonnull String name, @Nullable Object value);

	/**
	 * Read the {@linkplain Output output} variable called <tt>name</tt>.
	 * 
	 * @param name
	 *            The output variable to read.
	 * @return The value that was in the variable. This may be any type and may
	 *         be <tt>null</tt>.
	 * @throws UnsupportedOperationException
	 *             If that variable is not readable (e.g., if it doesn't exist).
	 */
	@Nullable
	Object getResult(@Nonnull String name);

	/**
	 * Indicates that a field corresponds to a beanshell input.
	 * 
	 * @author Donal Fellows
	 */
	@Target(FIELD)
	@Retention(RUNTIME)
	public @interface Input {
		/** Is this a required input? Defaults to yes. */
		boolean required() default true;

		/** Is this a nullable input? Defaults to no. */
		boolean nullable() default false;
	}

	/**
	 * Indicates that a field corresponds to a beanshell output.
	 * 
	 * @author Donal Fellows
	 */
	@Target(FIELD)
	@Retention(RUNTIME)
	public @interface Output {
	}

	/**
	 * What the beanshell ought to be called in the workflow.
	 * 
	 * @author Donal Fellows
	 */
	@Target(TYPE)
	@Retention(RUNTIME)
	public @interface Name {
		String value();
	}
}

/**
 * Micro-framework for implementing the support, which handles mapping access
 * from the beanshell to the annotated fields, and also enforces whether a
 * required parameter is supplied or not.
 * 
 * @param <T>
 *            Should be the subclass of this class.
 * @author Donal Fellows
 */
abstract class Support<T extends BeanshellSupport<?>> implements
		BeanshellSupport<T> {
	private Map<String, Field> inputs = new HashMap<>(),
			outputs = new HashMap<>();

	protected abstract void op() throws Exception;

	@Override
	public final void perform() throws Exception {
		// Enforce the required-ness of inputs
		for (Entry<String, Field> e : inputs.entrySet()) {
			Field f = e.getValue();
			Input i = f.getAnnotation(Input.class);
			if (i.required() && f.get(this) == null && !i.nullable())
				throw new IllegalStateException("input " + e.getKey()
						+ " was not supplied (or is null)");
		}
		try {
			op();
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	protected Support() {
		// Oh god! What a horrible hack! (Makes JAXB work in Taverna.)
		currentThread().setContextClassLoader(getClass().getClassLoader());
		for (Field f : getClass().getDeclaredFields()) {
			if (f.getAnnotation(Input.class) != null) {
				f.setAccessible(true);
				inputs.put(f.getName(), f);
			}
			if (f.getAnnotation(Output.class) != null) {
				f.setAccessible(true);
				outputs.put(f.getName(), f);
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public final T init(String name, Object value) {
		Field f = inputs.get(name);
		if (f == null)
			throw new UnsupportedOperationException("no field called " + name);
		try {
			if (f.getType().equals(Boolean.TYPE))
				f.setBoolean(this, parseBoolean(value.toString()));
			else if (f.getType().equals(Boolean.class))
				f.set(this, parseBoolean(value.toString()));
			else if (f.getType().equals(Integer.TYPE))
				f.setInt(this, parseInt(value.toString()));
			else if (f.getType().equals(Integer.class))
				f.set(this, parseInt(value.toString()));
			else
				f.set(this, value);
			return (T) this;
		} catch (NumberFormatException e) {
			throw e;
		} catch (Exception e) {
			throw new UnsupportedOperationException("could not write to field "
					+ name, e);
		}
	}

	@Override
	public final Object getResult(String name) {
		Field f = outputs.get(name);
		if (f == null)
			throw new UnsupportedOperationException("no field called " + name);
		try {
			if (f.getType().equals(Boolean.TYPE))
				return f.getBoolean(this);
			if (f.getType().equals(Integer.TYPE))
				return f.getInt(this);
			return f.get(this);
		} catch (Exception e) {
			throw new UnsupportedOperationException(
					"could not read from field " + name, e);
		}
	}
}
